package com.xlntsmmr.xlntgallery.data

/**
 * 글자 영역. 사진 크기에 대한 비율(0~1)로 저장.
 * 픽셀 대신 비율을 쓰면 화면에 어떤 크기로 그리든 그대로 곱하기만 하면 됨.
 */
data class OcrBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

// 단어 하나 (글자 + 위치). 사진에서 길게 눌러 선택할 때의 최소 단위
data class OcrWord(
    val text: String,
    val box: OcrBox
)

// OCR이 찾은 한 줄 (글자 + 위치)
data class OcrLine(
    val text: String,
    val box: OcrBox,
    // 단어별 위치. 엔진이 단어 위치를 안 주면 줄 전체를 단어 하나로 취급
    val words: List<OcrWord> = listOf(OcrWord(text, box))
)

/**
 * 블록 = 사진 속에서 서로 붙어 있는 글자 덩어리 (간판 하나, 번호판 하나, 문단 하나).
 * ML Kit이 위치를 보고 나눠 줌. 블록 안에 여러 줄(OcrLine)이 들어 있음.
 */
data class OcrBlock(
    val text: String,
    val lines: List<OcrLine>,
    val confidence: Float = 0f,   // ML Kit이 매긴 확신도 0~1 (높을수록 정확할 가능성↑)
    val angle: Float = 0f         // 글자 기울기(도). 세로 글자는 ±90 근처
) {
    // 블록 전체를 감싸는 영역
    val bounds: OcrBox
        get() = OcrBox(
            left = lines.minOf { it.box.left },
            top = lines.minOf { it.box.top },
            right = lines.maxOf { it.box.right },
            bottom = lines.maxOf { it.box.bottom }
        )
}

data class OcrResult(
    val text: String,            // 전체 글자
    val blocks: List<OcrBlock>   // 덩어리별 글자 + 위치
) {
    // 사진 위에 박스를 그릴 때 쓰는 모든 줄
    val lines: List<OcrLine> get() = blocks.flatMap { it.lines }
}
