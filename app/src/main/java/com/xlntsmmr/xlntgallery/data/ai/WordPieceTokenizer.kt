package com.xlntsmmr.xlntgallery.data.ai

import java.io.File

/**
 * BERT 계열(DistilBERT 다국어) 토크나이저.
 * 문장 → 단어 → 사전에 있는 조각(WordPiece)들의 번호 목록.
 *   "강아지 사진" → [CLS] 강 ##아지 사진 [SEP] → [101, 8900, 10345, ...]
 * (번호는 예시. 실제 값은 vocab.txt의 줄 번호)
 */
class WordPieceTokenizer(vocabFile: File) {

    // vocab.txt의 n번째 줄 = 토큰 번호 n
    private val vocab: Map<String, Int> =
        vocabFile.readLines(Charsets.UTF_8).withIndex().associate { (index, token) -> token to index }

    private val clsId = vocab.getValue("[CLS]")
    private val sepId = vocab.getValue("[SEP]")
    private val unkId = vocab.getValue("[UNK]")

    fun encode(text: String, maxLength: Int = 128): LongArray {
        val ids = mutableListOf(clsId)
        for (word in basicTokenize(text)) {
            ids += wordPiece(word)
            if (ids.size >= maxLength - 1) break
        }
        val trimmed = ids.take(maxLength - 1) + sepId
        return trimmed.map { it.toLong() }.toLongArray()
    }

    /** 공백·문장부호 기준으로 나누기. 한자는 글자마다 분리 (BERT 규칙). 대소문자는 유지(cased 모델) */
    private fun basicTokenize(text: String): List<String> {
        val builder = StringBuilder()
        for (ch in text) {
            when {
                ch.code == 0 || ch.code == 0xFFFD || Character.isISOControl(ch) && !ch.isWhitespace() -> Unit
                ch.isWhitespace() -> builder.append(' ')
                isCjk(ch) || isPunctuation(ch) -> builder.append(' ').append(ch).append(' ')
                else -> builder.append(ch)
            }
        }
        return builder.split(' ').filter { it.isNotEmpty() }
    }

    /** 사전에서 가장 긴 조각부터 맞춰 나감. 두 번째 조각부터는 "##" 접두사 */
    private fun wordPiece(word: String): List<Int> {
        if (word.length > 100) return listOf(unkId)
        val pieces = mutableListOf<Int>()
        var start = 0
        while (start < word.length) {
            var end = word.length
            var found: Int? = null
            while (start < end) {
                val piece = (if (start > 0) "##" else "") + word.substring(start, end)
                val id = vocab[piece]
                if (id != null) {
                    found = id
                    break
                }
                end--
            }
            if (found == null) return listOf(unkId)   // 하나라도 못 맞추면 단어 전체를 [UNK]
            pieces += found
            start = end
        }
        return pieces
    }

    private fun isPunctuation(ch: Char): Boolean {
        val code = ch.code
        if (code in 33..47 || code in 58..64 || code in 91..96 || code in 123..126) return true
        return when (Character.getType(ch).toByte()) {
            Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
            Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
            Character.OTHER_PUNCTUATION -> true
            else -> false
        }
    }

    private fun isCjk(ch: Char): Boolean {
        val c = ch.code
        return c in 0x4E00..0x9FFF || c in 0x3400..0x4DBF || c in 0xF900..0xFAFF || c in 0x2F800..0x2FA1F
    }
}
