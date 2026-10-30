package com.xlntsmmr.xlntgallery.data.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.LongBuffer

/**
 * 검색어 → 512차원 벡터 (다국어 CLIP 텍스트 인코더).
 * 순서: 토큰화 → DistilBERT(ONNX) → 평균 풀링(768) → Dense(768→512) → 길이 1로 정규화
 */
class ClipTextEncoder(pack: ModelPack, accelerator: HardwareAccelerator) : TextEmbedder {

    private val env = OrtEnvironment.getEnvironment()
    private val model = OrtSessionFactory.create(pack.file(ClipModels.TEXT).absolutePath, accelerator, "검색어 AI (텍스트 분석)")
    private val session: OrtSession = model.session
    override val backend: String get() = model.backend
    private val tokenizer = WordPieceTokenizer(pack.file(ClipModels.VOCAB))
    private val dense: FloatArray = loadDenseWeight(pack.file(ClipModels.DENSE))   // [512 × 768]

    override fun encode(text: String): FloatArray {
        val ids = tokenizer.encode(text)
        val length = ids.size.toLong()
        val mask = LongArray(ids.size) { 1L }

        // 모델이 요구하는 입력만 골라서 넣음 (input_ids, attention_mask, 필요하면 token_type_ids)
        val tensors = session.inputNames.associateWith { name ->
            val data = when {
                name.contains("mask") -> mask
                name.contains("type") -> LongArray(ids.size)
                else -> ids
            }
            OnnxTensor.createTensor(env, LongBuffer.wrap(data), longArrayOf(1, length))
        }

        try {
            session.run(tensors).use { output ->
                val result = output.get(0) as OnnxTensor
                val shape = result.info.shape
                val values = FloatArray(shape.fold(1L) { acc, d -> acc * d }.toInt())
                result.floatBuffer.get(values)

                // 출력이 [1, 토큰수, 768]이면 토큰 평균(mean pooling), [1, 768]이면 그대로
                val hidden = HIDDEN
                val pooled = FloatArray(hidden)
                if (shape.size == 3) {
                    val tokens = shape[1].toInt()
                    for (t in 0 until tokens) for (h in 0 until hidden) pooled[h] += values[t * hidden + h]
                    for (h in 0 until hidden) pooled[h] /= tokens
                } else {
                    values.copyInto(pooled, endIndex = hidden)
                }

                // Dense: 512 × 768 행렬 곱 (bias, 활성화 함수 없음)
                val out = FloatArray(ClipModels.DIMENSION)
                for (o in 0 until ClipModels.DIMENSION) {
                    var sum = 0f
                    val row = o * hidden
                    for (h in 0 until hidden) sum += dense[row + h] * pooled[h]
                    out[o] = sum
                }
                return VectorMath.normalize(out)
            }
        } finally {
            tensors.values.forEach { it.close() }
        }
    }

    override fun close() = model.close()

    /**
     * safetensors 파일 읽기. 구조: [8바이트: 헤더 길이][JSON 헤더][숫자 데이터]
     * 헤더에 텐서 이름·자료형·모양·데이터 위치가 적혀 있음.
     */
    private fun loadDenseWeight(file: File): FloatArray {
        RandomAccessFile(file, "r").use { raf ->
            val lengthBytes = ByteArray(8).also { raf.readFully(it) }
            val headerLength = ByteBuffer.wrap(lengthBytes).order(ByteOrder.LITTLE_ENDIAN).long.toInt()
            val header = JSONObject(String(ByteArray(headerLength).also { raf.readFully(it) }, Charsets.UTF_8))

            val key = header.keys().asSequence().first { it.endsWith("weight") }
            val tensor = header.getJSONObject(key)
            check(tensor.getString("dtype") == "F32") { "지원하지 않는 자료형: ${tensor.getString("dtype")}" }
            val offsets = tensor.getJSONArray("data_offsets")
            val start = offsets.getLong(0)
            val end = offsets.getLong(1)

            val data = ByteArray((end - start).toInt())
            raf.seek(8 + headerLength + start)
            raf.readFully(data)
            val floats = FloatArray(data.size / 4)
            ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats)
            check(floats.size == ClipModels.DIMENSION * HIDDEN) { "Dense 크기가 예상과 다름: ${floats.size}" }
            return floats
        }
    }

    private companion object {
        const val HIDDEN = 768
    }
}
