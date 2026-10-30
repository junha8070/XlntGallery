package com.xlntsmmr.xlntgallery.data.gemini

import android.graphics.Bitmap
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

/** Gemini Nano가 만든 사진 설명 */
data class PhotoTag(
    val category: String,
    val keywords: List<String>,
    val summary: String
)

/**
 * Gemini Nano (Google 온디바이스 AI) — ML Kit GenAI Prompt API.
 * 모델은 우리 앱이 아니라 Android 시스템(AICore)에 있고, 앱은 빌려 쓰기만 함.
 *
 * 제약 (공식 문서):
 *  - 앱이 화면 맨 앞에 있을 때만 실행 가능 (백그라운드·포그라운드 서비스 불가)
 *  - 앱별 사용량 제한 (너무 자주 부르면 BUSY, 하루 사용량 초과 시 거부)
 *  - 지원 기기: Galaxy S25/S26, 최신 Pixel 등
 */
class GeminiNanoTagger : AutoCloseable {

    private val model: GenerativeModel = Generation.getClient()

    /** FeatureStatus.AVAILABLE / DOWNLOADABLE / DOWNLOADING / UNAVAILABLE */
    suspend fun checkStatus(): Int = model.checkStatus()

    /** 시스템에 모델 다운로드 요청 (모델은 시스템이 관리) */
    fun download(): Flow<DownloadStatus> = model.download()

    suspend fun tag(bitmap: Bitmap, ocrText: String?): PhotoTag? {
        val hint = ocrText?.trim()?.take(400)?.takeIf { it.isNotEmpty() }
        val prompt = buildString {
            appendLine("이 사진을 사진 검색용으로 분류해줘.")
            if (hint != null) {
                appendLine("참고: 사진 속 글자를 OCR로 읽은 결과 (틀릴 수 있음):")
                appendLine(hint)
            }
            appendLine("반드시 아래 JSON 형식 하나만 답해. 설명이나 코드블록 없이.")
            appendLine("""{"종류": "$CATEGORIES 중 하나", "키워드": ["검색에 쓸 한국어 단어 5~8개"], "요약": "한국어 한 문장"}""")
            append("키워드에는 사진 속 핵심 물건·문서 이름과 비슷한 말(예: 영수증이면 결제, 구매)도 넣어줘.")
        }

        val response = model.generateContent(
            generateContentRequest(ImagePart(bitmap), TextPart(prompt)) {
                temperature = 0f        // 같은 사진엔 같은 답 (무작위성 제거)
                topK = 1
                maxOutputTokens = 200
            }
        )
        val text = response.candidates.firstOrNull()?.text ?: return null
        return parse(text)
    }

    /** 응답에서 JSON 부분만 꺼내 읽기 (모델이 앞뒤에 다른 말을 붙여도 견디게) */
    private fun parse(text: String): PhotoTag? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            val json = JSONObject(text.substring(start, end + 1))
            val keywordsArray = json.optJSONArray("키워드")
            val keywords = buildList {
                if (keywordsArray != null) {
                    for (i in 0 until keywordsArray.length()) {
                        keywordsArray.optString(i).trim().takeIf { it.isNotEmpty() }?.let { add(it) }
                    }
                }
            }
            PhotoTag(
                category = json.optString("종류").trim(),
                keywords = keywords,
                summary = json.optString("요약").trim()
            )
        } catch (e: Exception) {
            null
        }
    }

    override fun close() = model.close()

    companion object {
        const val MODEL_ID = "gemini-nano"
        const val TAG_VERSION = 1   // 질문(프롬프트)을 바꾸면 올려서 다시 태깅

        const val CATEGORIES =
            "계약서, 영수증, 신분증, 카드, 문서, 메뉴판, 제품 라벨, 안내문, 화면 캡처, 책, " +
                "인물, 음식, 풍경, 동물, 사물, 기타"

        fun isUsable(status: Int) = status == FeatureStatus.AVAILABLE
    }
}
