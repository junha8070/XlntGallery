package com.xlntsmmr.xlntgallery.data.ai

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import android.util.Log
import java.util.EnumSet

// 설정 화면의 "AI 분석 설정" (자동 / NPU / GPU / CPU)
enum class HardwareAccelerator {
    AUTO, NPU, GPU, CPU
}

/** 모델 세션 + 실제로 어떤 장치로 돌고 있는지 */
class OrtModel(val session: OrtSession, val backend: String) : AutoCloseable {
    override fun close() = session.close()
}

/**
 * ONNX Runtime 세션 만들기. "실행 공급자(Execution Provider)"로 연산 장치를 고름.
 *  - NNAPI: Android의 신경망 가속 API → 기기 드라이버가 NPU/GPU/DSP에 연산을 배분
 *  - XNNPACK: ARM CPU에 최적화된 연산 라이브러리
 * 원하는 가속기를 쓸 수 없으면(지원 안 하는 기기·연산) CPU로 자동 대체.
 */
object OrtSessionFactory {

    private const val TAG = "OrtSessionFactory"

    /**
     * @param modelLabel 설정 화면 "AI 실행 상태"에 표시할 이름
     * @param profilingPrefix 지정하면 연산마다 어느 장치에서 몇 µs 걸렸는지 JSON 파일로 기록 (성능 테스트용)
     */
    fun create(
        modelPath: String,
        accelerator: HardwareAccelerator,
        modelLabel: String,
        profilingPrefix: String? = null
    ): OrtModel {
        val env = OrtEnvironment.getEnvironment()

        // 원하는 순서대로 시도
        val attempts: List<Pair<String, (OrtSession.SessionOptions) -> Unit>> = when (accelerator) {
            HardwareAccelerator.NPU, HardwareAccelerator.AUTO -> listOf(
                // CPU_DISABLED: NNAPI 내부의 CPU 대체 구현을 끔 → 가속기(NPU 등)에 배분되게 유도
                "NPU (NNAPI)" to { o: OrtSession.SessionOptions -> o.addNnapi(EnumSet.of(NNAPIFlags.CPU_DISABLED)) },
                "CPU (XNNPACK)" to { o: OrtSession.SessionOptions -> o.addXnnpack(mapOf("intra_op_num_threads" to "4")) }
            )
            HardwareAccelerator.GPU -> listOf(
                // USE_FP16: 반정밀도 연산 허용 → GPU가 처리하기 좋은 형태
                "GPU (NNAPI FP16)" to { o: OrtSession.SessionOptions -> o.addNnapi(EnumSet.of(NNAPIFlags.USE_FP16)) },
                "CPU (XNNPACK)" to { o: OrtSession.SessionOptions -> o.addXnnpack(mapOf("intra_op_num_threads" to "4")) }
            )
            HardwareAccelerator.CPU -> listOf(
                "CPU (XNNPACK)" to { o: OrtSession.SessionOptions -> o.addXnnpack(mapOf("intra_op_num_threads" to "4")) }
            )
        }

        attempts.forEachIndexed { index, (label, configure) ->
            try {
                val options = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(4)
                    if (profilingPrefix != null) enableProfiling(profilingPrefix)
                    configure(this)
                }
                val model = OrtModel(env.createSession(modelPath, options), label)
                // 첫 번째 시도가 아니면 = 원하는 가속기가 실패해서 대체된 것
                AiRuntimeStatus.report(RuntimeRecord(modelLabel, accelerator, label, fellBack = index > 0))
                return model
            } catch (e: Exception) {
                Log.w(TAG, "$label 사용 불가, 다음 방식 시도", e)
            }
        }
        // 마지막 안전장치: 기본 CPU
        val options = OrtSession.SessionOptions().apply {
            if (profilingPrefix != null) enableProfiling(profilingPrefix)
        }
        AiRuntimeStatus.report(RuntimeRecord(modelLabel, accelerator, "CPU", fellBack = true))
        return OrtModel(env.createSession(modelPath, options), "CPU")
    }
}
