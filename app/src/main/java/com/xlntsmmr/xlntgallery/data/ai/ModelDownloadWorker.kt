package com.xlntsmmr.xlntgallery.data.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.xlntsmmr.xlntgallery.data.SettingsRepository
import com.xlntsmmr.xlntgallery.data.index.IndexingScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * AI 모델(SigLIP 2 + CLIP, 약 600MB) 자동 다운로드 작업.
 *
 * 구글 플레이 정책·가이드 준수:
 *  - 내려받는 것은 모델 "데이터" 파일뿐 (실행 코드 dex/jar/.so 아님 → 외부 코드 다운로드 금지 조항 해당 없음)
 *  - 기본은 Wi-Fi(비종량제 네트워크)에서만. 모바일 데이터는 사용자가 설정에서 켠 경우만
 *    (Play for On-device AI도 200MB 넘는 모바일 데이터 다운로드는 사용자 동의를 요구)
 *  - 다운로드는 dataSync 포그라운드 작업 → 진행 알림이 보이고, 사용자가 언제든 확인 가능
 *  - 끊기면 이어받기, 실패하면 점점 간격을 늘려 재시도
 */
class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val packs = AiModels.ALL.map { AiModels.pack(applicationContext, it) }
        if (packs.all { it.isReady() }) return Result.success()

        runCatching { setForeground(foregroundInfo(0)) }   // 시스템이 거부하면 일반 작업으로 진행

        return try {
            val total = packs.sumOf { it.totalBytes }.toFloat()
            var finishedBefore = 0L
            for (pack in packs) {
                pack.download().collect { progress ->
                    val overall = (finishedBefore + pack.totalBytes * progress) / total
                    setProgress(workDataOf(KEY_PROGRESS to overall))
                    runCatching { setForeground(foregroundInfo((overall * 100).toInt())) }
                }
                finishedBefore += pack.totalBytes
            }
            // 모델이 생겼으니 사진 분석을 다시 시작 (AI 벡터 생성 포함)
            IndexingScheduler.restart(applicationContext)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Result.retry()   // 네트워크 문제 → WorkManager가 나중에 다시 실행 (이어받기)
        } catch (e: Exception) {
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    private fun foregroundInfo(percent: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "AI 모델 다운로드", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("AI 검색 모델 받는 중")
            .setContentText("$percent% · 사진 검색에 쓰는 모델이에요 (기기 안에서만 사용)")
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_PROGRESS = "progress"
        private const val CHANNEL_ID = "model_download"
        private const val NOTIFICATION_ID = 1002
        private const val WORK_NAME = "model-download"

        /** 앱 실행 시 호출: 모델이 없으면 다운로드 예약 (이미 예약/진행 중이면 그대로) */
        suspend fun ensure(context: Context, replace: Boolean = false) {
            if (AiModels.allReady(context)) return
            val mobileData = SettingsRepository(context).modelsOnMobileData.first()
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (mobileData) NetworkType.CONNECTED else NetworkType.UNMETERED)
                        .setRequiresStorageNotLow(true)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request
            )
        }

        /** 다운로드 상태: null = 작업 없음 / 진행률 0~1 / -1 = 네트워크 조건 대기 */
        fun progress(context: Context): Flow<Float?> =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WORK_NAME).map { infos ->
                val info = infos.firstOrNull() ?: return@map null
                when (info.state) {
                    WorkInfo.State.RUNNING -> info.progress.getFloat(KEY_PROGRESS, 0f)
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> -1f
                    else -> null
                }
            }
    }
}
