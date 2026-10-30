package com.xlntsmmr.xlntgallery.data

import android.net.Uri

// 설정에서 고를 수 있는 OCR 엔진 종류
enum class OcrEngineType(val displayName: String) {
    ML_KIT("ML Kit"),
    PADDLE("PaddleOCR")
}

/**
 * OCR 엔진 공통 규격(인터페이스).
 * 화면·ViewModel은 이 규격만 알고, 실제로 ML Kit인지 PaddleOCR인지는 몰라도 됨
 * → 엔진을 추가·교체해도 화면 코드는 그대로.
 */
interface OcrEngine {
    val type: OcrEngineType

    /** 1단계: 사진 전체 인식 */
    suspend fun recognize(uri: Uri): OcrResult

    /** 2단계(선택): 더 정밀하게 다듬기. 기본은 아무것도 안 함 */
    suspend fun refine(uri: Uri, first: OcrResult): OcrResult = first

    fun close()
}
