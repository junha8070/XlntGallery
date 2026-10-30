package com.xlntsmmr.xlntgallery.data.db

/**
 * FTS 검색용 토큰 만들기.
 *
 * 문제: SQLite FTS는 "띄어쓰기 단위"로 단어를 나눔.
 *   "신분증을 찍은 사진"을 저장하면 "신분증"으로 검색해도 "신분증을"과 달라서 못 찾음 (조사 때문).
 * 해결: 모든 단어를 2글자씩 겹쳐서 쪼갬 (bigram).
 *   저장: "신분증을" → "신분 분증 증을"
 *   검색: "신분증"   → "신분 분증"  → 둘 다 들어 있는 사진 = 찾음
 */
object SearchTokenizer {

    private val wordSplitter = Regex("[^\\p{L}\\p{N}]+")   // 글자·숫자가 아닌 것 기준으로 나눔

    /** 저장용: 공백으로 이어 붙인 토큰 문자열 */
    fun indexTokens(text: String): String =
        words(text).flatMap { bigrams(it) }.distinct().joinToString(" ")

    /** 검색용: FTS MATCH 문법 문자열. 검색할 게 없으면 null */
    fun matchQuery(query: String): String? {
        val terms = words(query).flatMap { word ->
            // 한 글자 검색어는 "그 글자로 시작하는 토큰" (접두사 검색)
            if (word.length == 1) listOf("$word*") else bigrams(word)
        }.distinct()
        // 띄어쓰기로 이으면 AND (모두 포함)
        return terms.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    private fun words(text: String): List<String> =
        text.lowercase().split(wordSplitter).filter { it.isNotEmpty() }

    private fun bigrams(word: String): List<String> =
        if (word.length < 2) listOf(word) else word.windowed(2)
}
