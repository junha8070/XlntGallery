package com.xlntsmmr.xlntgallery.data.ai

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.MediaStore
import com.xlntsmmr.xlntgallery.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import kotlin.system.measureNanoTime

/** 장치 설정 하나에 대한 측정 결과 */
data class BenchmarkResult(
    val accelerator: HardwareAccelerator,
    val backend: String,             // 실제로 만들어진 실행 방식 ("NPU (NNAPI)" / "CPU (XNNPACK)")
    val fellBack: Boolean,           // 원하는 가속기를 못 써서 CPU로 대체됐는지
    val firstRunMs: Double,          // 첫 실행 (가속기용 컴파일 시간 포함)
    val averageMs: Double,           // 이후 평균 (실제 체감 속도)
    val acceleratorShare: Float,     // 연산 시간 중 NNAPI 가속기가 처리한 비율 0~1
    val error: String? = null
)

/**
 * AI 성능 테스트: 같은 사진을 NPU 우선 / GPU 우선 / CPU로 각각 돌려서
 *  1) 속도 (장당 ms)
 *  2) ONNX Runtime 프로파일 기록으로 "연산 시간 중 몇 %를 NNAPI(가속기)가 처리했는지"
 * 를 측정. 설정에 "NPU"라고 써 있어도 실제로는 대부분 CPU가 처리하는 경우를 잡아냄.
 */
class AiBenchmark(private val context: Context) {

    suspend fun run(onStep: (String) -> Unit): List<BenchmarkResult> = withContext(Dispatchers.Default) {
        // 준비된 모델 중 주 모델(SigLIP 2) 우선으로 측정
        val type = AiModels.ALL.firstOrNull { AiModels.pack(context, it).isReady() }
            ?: error("AI 검색 모델을 받는 중이에요. 다운로드가 끝나면 테스트할 수 있어요")

        val sample = loadSampleImage()
        try {
            listOf(HardwareAccelerator.NPU, HardwareAccelerator.GPU, HardwareAccelerator.CPU).map { accelerator ->
                onStep("${label(accelerator)} 측정 중…")
                try {
                    measure(type, accelerator, sample)
                } catch (e: Exception) {
                    BenchmarkResult(accelerator, "-", true, 0.0, 0.0, 0f, error = e.message ?: "실패")
                }
            }
        } finally {
            sample.recycle()
        }
    }

    private fun measure(type: AiModelType, accelerator: HardwareAccelerator, sample: Bitmap): BenchmarkResult {
        val prefix = File(context.cacheDir, "ort_profile_${accelerator.name}").absolutePath
        AiModels.imageEmbedder(context, type, accelerator, profilingPrefix = prefix).use { encoder ->
            // 첫 실행은 가속기용 준비(컴파일)가 섞여서 느림 → 따로 기록
            val firstNs = measureNanoTime { encoder.encode(sample) }
            val times = (1..RUNS).map { measureNanoTime { encoder.encode(sample) } }

            val profileFile = File(encoder.endProfiling())
            val share = try {
                acceleratorShare(profileFile)
            } finally {
                profileFile.delete()
            }

            return BenchmarkResult(
                accelerator = accelerator,
                backend = encoder.backend,
                // CPU가 아닌 걸 원했는데 CPU로 만들어졌으면 = 가속기 사용 실패로 대체됨
                fellBack = accelerator != HardwareAccelerator.CPU && encoder.backend.startsWith("CPU"),
                firstRunMs = firstNs / 1_000_000.0,
                averageMs = times.average() / 1_000_000.0,
                acceleratorShare = share
            )
        }
    }

    /**
     * 프로파일 JSON: 연산(Node)마다 {"cat":"Node","dur":걸린 µs,"args":{"provider":"CPUExecutionProvider" 등}}
     * provider가 NNAPI인 연산 시간 / 전체 연산 시간 = 가속기 처리 비율
     */
    private fun acceleratorShare(file: File): Float {
        val events = JSONArray(file.readText())
        var total = 0L
        var accelerated = 0L
        for (i in 0 until events.length()) {
            val event = events.optJSONObject(i) ?: continue
            if (event.optString("cat") != "Node") continue
            val provider = event.optJSONObject("args")?.optString("provider").orEmpty()
            val duration = event.optLong("dur")
            total += duration
            if (provider.contains("Nnapi", ignoreCase = true)) accelerated += duration
        }
        return if (total == 0L) 0f else accelerated.toFloat() / total
    }

    /** 갤러리의 최신 사진 1장 (없으면 회색 이미지) */
    private fun loadSampleImage(): Bitmap {
        val uri = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID), null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { c ->
            if (c.moveToFirst()) ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0)) else null
        }
        return uri?.let { BitmapLoader.loadUpright(context, it, minShortSide = 224) }
            ?: Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
    }

    companion object {
        private const val RUNS = 5

        fun label(accelerator: HardwareAccelerator) = when (accelerator) {
            HardwareAccelerator.AUTO -> "자동"
            HardwareAccelerator.NPU -> "NPU 우선"
            HardwareAccelerator.GPU -> "GPU 우선"
            HardwareAccelerator.CPU -> "CPU"
        }
    }
}
