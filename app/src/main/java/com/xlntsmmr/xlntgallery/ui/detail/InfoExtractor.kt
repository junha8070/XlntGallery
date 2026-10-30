package com.xlntsmmr.xlntgallery.ui.detail

import com.xlntsmmr.xlntgallery.data.OcrBlock
import com.xlntsmmr.xlntgallery.data.OcrBox
import com.xlntsmmr.xlntgallery.data.OcrLine
import com.xlntsmmr.xlntgallery.data.OcrResult

// 복사 버튼이 붙는 항목 1개
data class CopyableInfo(
    val label: String,                 // "전화번호"
    val value: String,                 // 실제로 복사될 값
    val isSensitive: Boolean = false,  // 주민번호 등: 화면엔 가리고, 클립보드에 "민감 정보" 표시
    val boxes: List<OcrBox> = emptyList()  // 사진 속 이 값이 있는 위치 (OCR 항목만)
) {
    // 화면에 보여줄 값 (민감 정보는 뒷자리 가림)
    val displayValue: String
        get() = if (isSensitive) value.replace(Regex("""(?<=-\s?\d)\d{6}"""), "******") else value
}

/**
 * OCR로 뽑은 전체 글자에서 "복사할 만한" 항목을 정규식으로 찾아냄.
 * 지금은 규칙 기반. 나중에 문서 종류(신분증/영수증)별 파싱이나 AI로 고도화할 수 있음.
 */
object InfoExtractor {

    private val rules: List<Pair<String, Regex>> = listOf(
        "차량 번호" to Regex("""(?<!\d)\d{2,3}\s?[가-힣]\s?\d{4}(?!\d)"""),
        "주민등록번호" to Regex("""(?<!\d)\d{6}\s?-\s?[1-4][\d*]{6}(?![\d*])"""),
        "전화번호" to Regex("""(?<!\d)(01[016789]|0[2-6]\d?|070)[-.\s]?\d{3,4}[-.\s]?\d{4}(?!\d)"""),
        "이메일" to Regex("""[\w.+-]+@[\w-]+(\.[\w-]+)+"""),
        "웹 주소" to Regex("""(https?://|www\.)\S+"""),
        "날짜" to Regex("""(?<!\d)(19|20)\d{2}\s?[./\-년]\s?\d{1,2}\s?[./\-월]\s?\d{1,2}일?"""),
        "금액" to Regex("""(?<![\d,])\d{1,3}(,\d{3})+\s?원|(?<![\d,])\d+\s?원"""),
    )

    fun extract(result: OcrResult): List<CopyableInfo> {
        val fullText = result.text
        if (fullText.isBlank()) return emptyList()

        val found = rules.flatMap { (label, regex) ->
            regex.findAll(fullText).map { match ->
                val value = match.value.trim()
                CopyableInfo(
                    label = label,
                    value = value,
                    isSensitive = label == "주민등록번호",
                    boxes = findBoxes(value, result.lines)
                )
            }
        }.distinctBy { it.value }   // 같은 값 중복 제거

        // 사진 속 글자 덩어리(블록)마다 따로 항목으로.
        // 이미 위에서 "차량 번호" 등으로 찾은 값이 블록의 거의 전부라면 중복이라 뺌
        // (예: 차량 번호 "151우1237" ↔ 블록 "국151우1237"은 같은 번호판)
        val foundValues = found.map { normalize(it.value) }
        val blockItems = result.blocks
            .filter { normalize(it.text).length >= 2 }                     // 한두 글자 잡음 제거
            .filterNot { block -> isDuplicateOf(normalize(block.text), foundValues) }
            .sortedWith(compareBy({ it.top() }, { it.left() }))            // 위→아래, 왼쪽→오른쪽 순
            .mapIndexed { index, block ->
                CopyableInfo(
                    label = "텍스트 ${index + 1}",
                    value = block.text.trim(),
                    boxes = block.lines.map { it.box }
                )
            }

        return found + blockItems
    }

    // 블록 글자가 찾은 값을 포함하고, 나머지가 2글자 이하면 같은 것으로 봄
    private fun isDuplicateOf(blockText: String, foundValues: List<String>): Boolean =
        foundValues.any { value -> blockText.contains(value) && blockText.length - value.length <= 2 }

    private fun OcrBlock.top() = lines.minOf { it.box.top }
    private fun OcrBlock.left() = lines.minOf { it.box.left }

    // 값이 들어 있는 줄의 위치 찾기. 공백·하이픈·점 차이는 무시하고 비교
    private fun findBoxes(value: String, lines: List<OcrLine>): List<OcrBox> {
        val target = normalize(value)
        return lines.filter { line ->
            val text = normalize(line.text)
            text.contains(target) || (text.length >= 3 && target.contains(text))
        }.map { it.box }
    }

    private fun normalize(s: String): String =
        s.filterNot { it.isWhitespace() || it == '-' || it == '.' }
}
