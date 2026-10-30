package com.xlntsmmr.xlntgallery.data.gemini

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.xlntsmmr.xlntgallery.data.SettingsRepository
import com.xlntsmmr.xlntgallery.data.ai.BitmapLoader
import com.xlntsmmr.xlntgallery.data.db.GalleryDatabase
import com.xlntsmmr.xlntgallery.data.db.PhotoTagEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

// Gemini Nano 태깅 상태 (설정·홈 화면 표시용)
sealed interface GeminiStatus {
    data object Idle : GeminiStatus
    data object Checking : GeminiStatus
    data object Disabled : GeminiStatus                       // 설정에서 끔
    data object Unsupported : GeminiStatus                    // 이 기기는 지원 안 함
    data class Downloading(val progress: Float?) : GeminiStatus
    data class Running(val done: Int) : GeminiStatus          // 이번 실행에서 처리한 수
    data object Finished : GeminiStatus                       // 대기열 비었음
    data class Waiting(val reason: String) : GeminiStatus     // 사용량 제한 등으로 잠시 멈춤
    data class Error(val message: String) : GeminiStatus
}

/**
 * Gemini Nano 사진 태깅 실행기.
 * 앱이 화면에 떠 있는 동안만 MainActivity가 run()을 부르고, 화면을 벗어나면 코루틴이 취소됨
 * → "앱이 맨 앞에 있을 때만 실행" 제약을 지킴. 사진마다 완료 표시가 저장돼서 다음에 이어서 함.
 */
object GeminiTaggingRunner {

    private const val TAG = "GeminiTagging"
    private val _status = MutableStateFlow<GeminiStatus>(GeminiStatus.Idle)
    val status: StateFlow<GeminiStatus> = _status.asStateFlow()

    suspend fun run(context: Context) {
        val settings = SettingsRepository(context)
        if (!settings.geminiTagging.first()) {
            _status.value = GeminiStatus.Disabled
            return
        }

        _status.value = GeminiStatus.Checking
        val tagger = try {
            GeminiNanoTagger()
        } catch (e: Exception) {
            _status.value = GeminiStatus.Unsupported
            return
        }

        try {
            // 1) 모델 준비 확인 → 필요하면 시스템에 다운로드 요청
            when (tagger.checkStatus()) {
                FeatureStatus.UNAVAILABLE -> {
                    _status.value = GeminiStatus.Unsupported
                    return
                }
                FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                    var total = 0L
                    tagger.download().collect { s ->
                        when (s) {
                            is DownloadStatus.DownloadStarted -> {
                                total = s.bytesToDownload
                                _status.value = GeminiStatus.Downloading(0f)
                            }
                            is DownloadStatus.DownloadProgress -> _status.value = GeminiStatus.Downloading(
                                if (total > 0) s.totalBytesDownloaded.toFloat() / total else null
                            )
                            is DownloadStatus.DownloadFailed -> throw s.e
                            is DownloadStatus.DownloadCompleted -> Unit
                        }
                    }
                }
            }

            // 2) 대기열 처리
            val dao = GalleryDatabase.get(context).galleryDao()
            var done = 0
            var failures = 0
            _status.value = GeminiStatus.Running(done)

            while (true) {
                val batch = dao.pendingTags(GeminiNanoTagger.TAG_VERSION, limit = 10)
                if (batch.isEmpty()) {
                    _status.value = GeminiStatus.Finished
                    return
                }
                for (item in batch) {
                    val photo = item.photo
                    // Gemini에 넣을 사진: 짧은 변 512px 정도면 충분 (크면 느리고 사용량↑)
                    val bitmap = withContext(Dispatchers.IO) {
                        BitmapLoader.loadUpright(context, Uri.parse(photo.uri), minShortSide = 512)
                    }
                    if (bitmap == null) {
                        dao.setTagVersion(photo.id, GeminiNanoTagger.TAG_VERSION)   // 읽을 수 없는 사진은 건너뜀
                        continue
                    }

                    try {
                        val tag = tagger.tag(bitmap, item.text)
                        dao.saveTag(
                            PhotoTagEntity(
                                photoId = photo.id,
                                category = tag?.category.orEmpty(),
                                keywords = tag?.keywords?.joinToString(",").orEmpty(),
                                summary = tag?.summary.orEmpty(),
                                model = GeminiNanoTagger.MODEL_ID
                            ),
                            GeminiNanoTagger.TAG_VERSION
                        )
                        done++
                        failures = 0
                        _status.value = GeminiStatus.Running(done)
                    } catch (e: CancellationException) {
                        throw e   // 앱이 화면을 벗어나 취소됨 → 그대로 멈춤 (다음에 이어서)
                    } catch (e: GenAiException) {
                        // 사용량 제한(BUSY)·일시 오류 등: 점점 길게 쉬었다가 같은 사진 재시도
                        failures++
                        Log.w(TAG, "Gemini 오류 (code=${e.errorCode}), ${failures}번째", e)
                        if (failures >= MAX_FAILURES) {
                            _status.value = GeminiStatus.Waiting("사용량 제한에 걸렸어요. 나중에 이어서 할게요 (코드 ${e.errorCode})")
                            return
                        }
                        val waitMs = min(60_000L, 2_000L shl max(0, failures - 1))
                        _status.value = GeminiStatus.Waiting("잠시 쉬는 중… (${waitMs / 1000}초)")
                        delay(waitMs)
                        _status.value = GeminiStatus.Running(done)
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        } catch (e: CancellationException) {
            if (_status.value is GeminiStatus.Running) _status.value = GeminiStatus.Idle
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini 태깅 중단", e)
            _status.value = GeminiStatus.Error(e.message ?: e.javaClass.simpleName)
        } finally {
            tagger.close()
        }
    }

    private const val MAX_FAILURES = 6
}
