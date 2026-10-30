package com.xlntsmmr.xlntgallery.data.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.nio.FloatBuffer
import kotlin.math.roundToInt

/**
 * 사진 → 512차원 벡터 (CLIP 이미지 인코더, ONNX Runtime으로 기기 안에서 실행)
 */
class ClipImageEncoder(
    private val context: Context,
    pack: ModelPack,
    accelerator: HardwareAccelerator,
    profilingPrefix: String? = null
) : ImageEmbedder {

    private val env = OrtEnvironment.getEnvironment()
    private val model = OrtSessionFactory.create(
        pack.file(ClipModels.VISION).absolutePath, accelerator, MODEL_LABEL, profilingPrefix
    )
    private val session: OrtSession = model.session
    override val backend: String get() = model.backend   // 실제 실행 장치 (화면 표시용)

    fun encode(uri: Uri): FloatArray? {
        val bitmap = BitmapLoader.loadUpright(context, uri, minShortSide = SIZE) ?: return null
        try {
            return encode(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    override fun encode(bitmap: Bitmap): FloatArray {
        // CLIP 전처리: 짧은 변을 224로 맞추고 → 가운데 224×224 자르기 → 정규화
        val scale = SIZE / minOf(bitmap.width, bitmap.height).toFloat()
        val scaledW = (bitmap.width * scale).roundToInt().coerceAtLeast(SIZE)
        val scaledH = (bitmap.height * scale).roundToInt().coerceAtLeast(SIZE)
        val scaled = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
        val cropped = Bitmap.createBitmap(scaled, (scaledW - SIZE) / 2, (scaledH - SIZE) / 2, SIZE, SIZE)

        val pixels = IntArray(SIZE * SIZE)
        cropped.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        if (cropped !== scaled) cropped.recycle()
        if (scaled !== bitmap) scaled.recycle()

        // [1, 3, 224, 224], RGB 순서, CLIP 학습 때 쓴 평균·표준편차로 정규화
        val area = SIZE * SIZE
        val input = FloatArray(3 * area)
        for (i in 0 until area) {
            val p = pixels[i]
            input[i] = ((p shr 16 and 0xFF) / 255f - MEAN[0]) / STD[0]
            input[area + i] = ((p shr 8 and 0xFF) / 255f - MEAN[1]) / STD[1]
            input[2 * area + i] = ((p and 0xFF) / 255f - MEAN[2]) / STD[2]
        }

        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())).use { tensor ->
            session.run(mapOf(session.inputNames.first() to tensor)).use { output ->
                // 출력 중 "image_embeds"(512차원)를 사용
                val name = session.outputNames.firstOrNull { it.contains("image_embeds") } ?: session.outputNames.first()
                val result = output.get(name).get() as OnnxTensor
                val vector = FloatArray(ClipModels.DIMENSION)
                result.floatBuffer.get(vector)
                return VectorMath.normalize(vector)
            }
        }
    }

    /** 프로파일링 종료 → 기록 파일 경로 */
    override fun endProfiling(): String = session.endProfiling()

    override fun close() = model.close()

    companion object {
        const val MODEL_LABEL = "사진 AI (이미지 분석)"
        private const val SIZE = 224
        private val MEAN = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
        private val STD = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)
    }
}
