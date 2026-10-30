package com.xlntsmmr.xlntgallery.data.index

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.xlntsmmr.xlntgallery.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/**
 * 사진 분석 작업 (WorkManager) — 앱을 닫아도 계속 실행.
 *
 * 구글 플레이 정책·Android 제한 준수:
 *  - 가능하면 "장기 작업"(dataSync 포그라운드, 알림 표시)으로 실행.
 *    Google 문서가 "로컬 ML 모델 처리"를 장기 작업의 예로 들고, dataSync 용도에 "로컬 파일 처리"가 포함됨.
 *    → Play Console > 앱 콘텐츠 > 포그라운드 서비스에 dataSync 사용 목적 신고 필요
 *  - Android 15+: dataSync는 하루 6시간 제한. 시스템이 포그라운드 실행을 막으면(백그라운드 시작 제한·시간 소진)
 *    일반 작업으로 약 8분만 하고 다음 작업을 예약해 이어감 (일반 작업의 실행 시간 제한을 넘지 않게)
 *  - 배터리 부족 시 쉼. 설정에 따라 충전 중에만 실행
 *  - Gemini Nano는 Android가 백그라운드 실행을 막으므로 여기서 돌리지 않음 (앱을 열었을 때만)
 */
class IndexingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val foreground = try {
            setForeground(createForegroundInfo(0))
            true
        } catch (e: Exception) {
            false
        }
        // 포그라운드면 제한 없이 끝까지, 아니면 8분까지만
        val deadline = if (foreground) Long.MAX_VALUE else System.currentTimeMillis() + SLICE_MILLIS

        return try {
            val finished = PhotoIndexer(applicationContext).run(deadline) { done ->
                setProgress(workDataOf(KEY_DONE to done))
                if (foreground && done % 10 == 0) runCatching { setForeground(createForegroundInfo(done)) }
            }
            if (!finished) IndexingScheduler.continueLater(applicationContext)   // 남은 사진은 다음 작업에서
            Result.success()
        } catch (e: CancellationException) {
            throw e   // 시스템이 멈춤(시간 초과·조건 불만족) → WorkManager가 나중에 다시 실행
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun createForegroundInfo(done: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "사진 분석", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("사진 분석 중")
            .setContentText(if (done > 0) "${done}장 분석함 · 기기 안에서만 처리돼요" else "검색할 수 있도록 사진을 분석하고 있어요")
            .setProgress(0, 0, true)
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
        const val KEY_DONE = "done"
        private const val CHANNEL_ID = "indexing"
        private const val NOTIFICATION_ID = 1001
        private const val SLICE_MILLIS = 8 * 60 * 1000L
    }
}

/** 새 사진이 추가되면 깨어나서 분석을 예약하는 작업 (MediaStore 변경 감지) */
class NewPhotoTriggerWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        IndexingScheduler.continueLater(applicationContext)
        IndexingScheduler.watchNewPhotos(applicationContext)   // 감지는 1회용이라 다시 등록
        return Result.success()
    }
}

object IndexingScheduler {
    private const val WORK_NAME = "photo-indexing"
    private const val PERIODIC_NAME = "photo-indexing-periodic"
    private const val TRIGGER_NAME = "photo-indexing-new-photos"

    /** 앱 실행 시: 바로 분석 시작 (사용자가 앱을 쓰는 중이라 충전 조건 없음) + 앱을 닫은 뒤를 위한 예약 */
    suspend fun start(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<IndexingWorker>().build()
        )
        schedulePeriodic(context)
        watchNewPhotos(context)
    }

    /** 새로 시작 (AI 모델을 받은 직후 등) */
    fun restart(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<IndexingWorker>().build()
        )
    }

    /** 이어서 할 작업 예약 (앱이 닫혀 있을 때 → 배터리·충전 조건 적용) */
    suspend fun continueLater(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<IndexingWorker>().setConstraints(backgroundConstraints(context)).build()
        )
    }

    /** 1시간마다 남은 분석이 있으면 이어서 (시스템이 배터리 상황에 맞춰 실행 시점을 조정) */
    private suspend fun schedulePeriodic(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<IndexingWorker>(1, TimeUnit.HOURS)
                .setConstraints(backgroundConstraints(context))
                .build()
        )
    }

    /** 새 사진 감지 (설정 "새 사진 자동 분석"이 켜져 있을 때) */
    suspend fun watchNewPhotos(context: Context) {
        val manager = WorkManager.getInstance(context)
        if (!SettingsRepository(context).autoAnalyzeNewPhotos.first()) {
            manager.cancelUniqueWork(TRIGGER_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
            .setTriggerContentUpdateDelay(30, TimeUnit.SECONDS)   // 연속 촬영 등은 모아서 한 번에
            .build()
        manager.enqueueUniqueWork(
            TRIGGER_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<NewPhotoTriggerWorker>().setConstraints(constraints).build()
        )
    }

    private suspend fun backgroundConstraints(context: Context): Constraints {
        val chargingOnly = SettingsRepository(context).analyzeChargingOnly.first()
        return Constraints.Builder()
            .setRequiresBatteryNotLow(true)        // 배터리 부족하면 쉼
            .setRequiresCharging(chargingOnly)     // 설정 "충전 중 분석"
            .build()
    }

    /** 작업이 돌고 있는지 */
    fun isRunning(context: Context): Flow<Boolean> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WORK_NAME).map { infos ->
            infos.any { it.state == WorkInfo.State.RUNNING }
        }
}
