package com.xlntsmmr.xlntgallery.data.ai

import java.io.File

/**
 * SentencePiece(BPE) 토크나이저 — Gemma / SigLIP 2용.
 * tokenizer.model 파일(protobuf 형식)에서 "조각(piece) 목록 + 점수"를 읽고,
 * 글자 단위에서 시작해 "합쳤을 때 사전에 있고 점수가 가장 높은 이웃 쌍"을 반복해서 합침.
 *   "강아지 사진" → ▁ 대신 공백 → [강][아][지][▁][사][진] → [강아지][▁사진] → 번호
 * 사전에 없는 글자는 UTF-8 바이트 조각(<0xEA> 등)으로 표현 (byte fallback).
 */
class SentencePieceTokenizer(modelFile: File) {

    private class Piece(val text: String, val score: Float, val type: Int)

    private val pieces: List<Piece> = parseModel(modelFile.readBytes())
    private val idOf: Map<String, Int> = HashMap<String, Int>(pieces.size * 2).apply {
        pieces.forEachIndexed { id, p -> if (p.type == NORMAL || p.type == USER_DEFINED) putIfAbsent(p.text, id) }
    }
    private val byteIds: IntArray = IntArray(256) { b ->
        pieces.indexOfFirst { it.type == BYTE && it.text == "<0x%02X>".format(b) }
    }

    val padId: Int = pieces.indexOfFirst { it.text == "<pad>" }.coerceAtLeast(0)
    val eosId: Int = pieces.indexOfFirst { it.text == "<eos>" }.coerceAtLeast(1)
    private val unkId: Int = pieces.indexOfFirst { it.type == UNKNOWN }.coerceAtLeast(0)

    fun encode(text: String): List<Int> {
        if (text.isEmpty()) return emptyList()
        val normalized = text.replace(' ', '▁')

        // 1) 글자(코드포인트) 단위로 시작. 사전에 없으면 바이트 조각으로
        val symbols = mutableListOf<Pair<String, Int>>()   // (글자, id) / 바이트 조각은 합치지 않도록 글자를 빈 값으로
        var i = 0
        while (i < normalized.length) {
            val cp = normalized.codePointAt(i)
            val ch = String(Character.toChars(cp))
            val id = idOf[ch]
            if (id != null) {
                symbols += ch to id
            } else {
                for (b in ch.toByteArray(Charsets.UTF_8)) {
                    val byteId = byteIds[b.toInt() and 0xFF]
                    symbols += "" to (if (byteId >= 0) byteId else unkId)
                }
            }
            i += Character.charCount(cp)
        }

        // 2) 점수가 가장 높은 쌍부터 계속 합치기
        while (true) {
            var bestScore = Float.NEGATIVE_INFINITY
            var bestIndex = -1
            var bestId = -1
            for (k in 0 until symbols.size - 1) {
                val left = symbols[k].first
                val right = symbols[k + 1].first
                if (left.isEmpty() || right.isEmpty()) continue
                val id = idOf[left + right] ?: continue
                val score = pieces[id].score
                if (score > bestScore) {
                    bestScore = score
                    bestIndex = k
                    bestId = id
                }
            }
            if (bestIndex < 0) break
            symbols[bestIndex] = (symbols[bestIndex].first + symbols[bestIndex + 1].first) to bestId
            symbols.removeAt(bestIndex + 1)
        }
        return symbols.map { it.second }
    }

    // ── protobuf 직접 읽기 (라이브러리 없이) ─────────────────────
    // ModelProto { repeated SentencePiece pieces = 1; ... }
    // SentencePiece { string piece = 1; float score = 2; Type type = 3; }

    private fun parseModel(bytes: ByteArray): List<Piece> {
        val reader = ProtoReader(bytes, 0, bytes.size)
        val result = ArrayList<Piece>(262_144)
        while (reader.hasMore()) {
            val key = reader.varint().toInt()
            val field = key ushr 3
            val wire = key and 7
            if (field == 1 && wire == 2) {
                val length = reader.varint().toInt()
                result += parsePiece(ProtoReader(bytes, reader.position, reader.position + length))
                reader.position += length
            } else {
                reader.skip(wire)
            }
        }
        return result
    }

    private fun parsePiece(reader: ProtoReader): Piece {
        var text = ""
        var score = 0f
        var type = NORMAL
        while (reader.hasMore()) {
            val key = reader.varint().toInt()
            when (key ushr 3) {
                1 -> {
                    val length = reader.varint().toInt()
                    text = String(reader.bytes, reader.position, length, Charsets.UTF_8)
                    reader.position += length
                }
                2 -> score = Float.fromBits(reader.fixed32())
                3 -> type = reader.varint().toInt()
                else -> reader.skip(key and 7)
            }
        }
        return Piece(text, score, type)
    }

    private class ProtoReader(val bytes: ByteArray, var position: Int, private val end: Int) {
        fun hasMore() = position < end

        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val b = bytes[position++].toInt()
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
        }

        fun fixed32(): Int {
            val v = (bytes[position].toInt() and 0xFF) or
                ((bytes[position + 1].toInt() and 0xFF) shl 8) or
                ((bytes[position + 2].toInt() and 0xFF) shl 16) or
                ((bytes[position + 3].toInt() and 0xFF) shl 24)
            position += 4
            return v
        }

        fun skip(wire: Int) {
            when (wire) {
                0 -> varint()
                1 -> position += 8
                2 -> { val length = varint().toInt(); position += length }
                5 -> position += 4
                else -> error("알 수 없는 protobuf 형식: $wire")
            }
        }
    }

    private companion object {
        const val NORMAL = 1
        const val UNKNOWN = 2
        const val USER_DEFINED = 4
        const val BYTE = 6
    }
}
