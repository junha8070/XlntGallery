package com.xlntsmmr.xlntgallery.data.ai

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object VectorMath {

    /** 길이를 1로 맞춤 → 이후 내적(dot) = 코사인 유사도 */
    fun normalize(v: FloatArray): FloatArray {
        var sum = 0f
        for (x in v) sum += x * x
        val norm = sqrt(sum)
        if (norm == 0f) return v
        for (i in v.indices) v[i] /= norm
        return v
    }

    // DB(BLOB) 저장용 변환
    fun toBytes(v: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(v)
        return buffer.array()
    }

    fun fromBytes(bytes: ByteArray): FloatArray {
        val v = FloatArray(bytes.size / 4)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(v)
        return v
    }
}
