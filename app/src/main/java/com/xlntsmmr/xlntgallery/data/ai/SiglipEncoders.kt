package com.xlntsmmr.xlntgallery.data.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import android.graphics.Bitmap
import java.nio.FloatBuffer
import java.nio.LongBuffer

/** SigLIP 2 이미지 인코더: 224×224로 늘려서(자르지 않음) 정규화 → 벡터(768) */
class SiglipImageEncoder(
    pack: ModelPack,
    accelerator: HardwareAccelerator,
    profilingPrefix: String? = null
) : ImageEmbedder {

    private val env = OrtEnvironment.getEnvironment()
    private val model = OrtSessionFactory.create(
        pack.file(SiglipModels.VISION).absolutePath, accelerator, MODEL_LABEL, profilingPrefix
    )
    override val backend: String get() = model.backend

    override fun encode(bitmap: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(bitmap, SIZE, SIZE, true)
        val pixels = IntArray(SIZE * SIZE)
        resized.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        if (resized !== bitmap) resized.recycle()

        // [1, 3, 224, 224], RGB, (x/255 - 0.5) / 0.5  → -1 ~ 1
        val area = SIZE * SIZE
        val input = FloatArray(3 * area)
        for (i in 0 until area) {
            val p = pixels[i]
            input[i] = (p shr 16 and 0xFF) / 127.5f - 1f
            input[area + i] = (p shr 8 and 0xFF) / 127.5f - 1f
            input[2 * area + i] = (p and 0xFF) / 127.5f - 1f
        }

        val session = model.session
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())).use { tensor ->
            session.run(mapOf(session.inputNames.first() to tensor)).use { output ->
                return readPooled(output, session.outputNames)
            }
        }
    }

    override fun endProfiling(): String = model.session.endProfiling()
    override fun close() = model.close()

    companion object {
        const val MODEL_LABEL = "사진 AI (SigLIP 2 이미지)"
        private const val SIZE = 224
    }
}

/** SigLIP 2 텍스트 인코더: 소문자 → SentencePiece → <eos> → 64토큰까지 <pad>로 채움 → 벡터(768) */
class SiglipTextEncoder(pack: ModelPack, accelerator: HardwareAccelerator) : TextEmbedder {

    private val env = OrtEnvironment.getEnvironment()
    private val model = OrtSessionFactory.create(
        pack.file(SiglipModels.TEXT).absolutePath, accelerator, "검색어 AI (SigLIP 2 텍스트)"
    )
    private val tokenizer = SentencePieceTokenizer(pack.file(SiglipModels.TOKENIZER))
    override val backend: String get() = model.backend

    override fun encode(text: String): FloatArray {
        // SigLIP은 학습 때 항상 64토큰 길이로 채워서 넣었음 → 똑같이 맞춰야 결과가 맞음
        val tokens = (tokenizer.encode(text.lowercase()).take(MAX_LENGTH - 1) + tokenizer.eosId)
        val ids = LongArray(MAX_LENGTH) { i -> (tokens.getOrNull(i) ?: tokenizer.padId).toLong() }

        val session = model.session
        val tensors = session.inputNames.associateWith { name ->
            val data = if (name.contains("mask")) LongArray(MAX_LENGTH) { 1L } else ids
            OnnxTensor.createTensor(env, LongBuffer.wrap(data), longArrayOf(1, MAX_LENGTH.toLong()))
        }
        try {
            session.run(tensors).use { output ->
                return readPooled(output, session.outputNames)
            }
        } finally {
            tensors.values.forEach { it.close() }
        }
    }

    override fun close() = model.close()

    private companion object {
        const val MAX_LENGTH = 64
    }
}

/** 출력 중 문장/사진 전체를 대표하는 벡터(pooler_output 또는 *_embeds)를 꺼내 정규화 */
private fun readPooled(output: ai.onnxruntime.OrtSession.Result, names: Set<String>): FloatArray {
    val name = names.firstOrNull { it.contains("pooler") }
        ?: names.firstOrNull { it.contains("embeds") }
        ?: names.first()
    val tensor = output.get(name).get() as OnnxTensor
    val shape = tensor.info.shape
    val values = FloatArray(shape.last().toInt())
    tensor.floatBuffer.get(values)
    return VectorMath.normalize(values)
}
