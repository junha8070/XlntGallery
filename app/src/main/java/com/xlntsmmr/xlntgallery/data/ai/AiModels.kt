package com.xlntsmmr.xlntgallery.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

/** 사진 → 벡터 */
interface ImageEmbedder : AutoCloseable {
    val backend: String                     // 실제 실행 장치
    fun encode(bitmap: Bitmap): FloatArray  // 길이 1로 정규화된 벡터
    fun endProfiling(): String              // 프로파일 파일 경로 (성능 테스트용)

    fun encode(context: Context, uri: Uri): FloatArray? {
        val bitmap = BitmapLoader.loadUpright(context, uri, minShortSide = 224) ?: return null
        try {
            return encode(bitmap)
        } finally {
            bitmap.recycle()
        }
    }
}

/** 검색어 → 벡터 */
interface TextEmbedder : AutoCloseable {
    val backend: String
    fun encode(text: String): FloatArray
}

// 설정에서 고를 수 있는 AI 검색 모델
enum class AiModelType(val displayName: String, val description: String) {
    CLIP("다국어 CLIP", "약 225MB · 가볍고 빠름"),
    SIGLIP2("SigLIP 2", "약 382MB · 더 정확함 (Google, 2025)")
}

/**
 * 모델별 정보를 한 곳에 모음. 새 모델을 추가할 때 여기만 늘리면 됨.
 * versionCode: photos.embeddingVersion에 저장되는 값. 모델마다 달라야 함
 *              → 모델을 바꾸면 "현재 모델 값과 다른 사진" = 다시 분석할 사진
 */
object AiModels {

    /** 함께 쓰는 이미지 AI 모델들 (앙상블). 앞쪽이 주 모델 */
    val ALL = listOf(AiModelType.SIGLIP2, AiModelType.CLIP)

    fun allReady(context: Context) = ALL.all { pack(context, it).isReady() }

    fun modelId(type: AiModelType) = when (type) {
        AiModelType.CLIP -> ClipModels.MODEL_ID
        AiModelType.SIGLIP2 -> SiglipModels.MODEL_ID
    }

    fun versionCode(type: AiModelType) = when (type) {
        AiModelType.CLIP -> ClipModels.EMBEDDING_VERSION          // 1
        AiModelType.SIGLIP2 -> SiglipModels.EMBEDDING_VERSION     // 101
    }

    fun pack(context: Context, type: AiModelType): ModelPack = when (type) {
        AiModelType.CLIP -> ClipModels.pack(context)
        AiModelType.SIGLIP2 -> SiglipModels.pack(context)
    }

    fun imageEmbedder(
        context: Context,
        type: AiModelType,
        accelerator: HardwareAccelerator,
        profilingPrefix: String? = null
    ): ImageEmbedder = when (type) {
        AiModelType.CLIP -> ClipImageEncoder(context, ClipModels.pack(context), accelerator, profilingPrefix)
        AiModelType.SIGLIP2 -> SiglipImageEncoder(SiglipModels.pack(context), accelerator, profilingPrefix)
    }

    fun textEmbedder(context: Context, type: AiModelType, accelerator: HardwareAccelerator): TextEmbedder =
        when (type) {
            AiModelType.CLIP -> ClipTextEncoder(ClipModels.pack(context), accelerator)
            AiModelType.SIGLIP2 -> SiglipTextEncoder(SiglipModels.pack(context), accelerator)
        }

    /**
     * ③ 프롬프트 앙상블: 한 단어 검색어를 여러 문장으로 감싸서 벡터를 평균냄.
     * 모델은 "○○ 사진" 같은 문장으로 학습돼서, 단어 하나보다 문장일 때 더 정확함.
     */
    fun queryTemplates(type: AiModelType): List<String> = when (type) {
        AiModelType.CLIP -> listOf("{q}", "{q} 사진", "a photo of {q}")
        AiModelType.SIGLIP2 -> listOf("{q}", "{q} 사진", "this is a photo of {q}.")
    }

    /** ① 허브 보정용 "아무 뜻 없는" 문장들 */
    val neutralPrompts = listOf("사진", "이미지", "a photo", "an image")
}
