package com.xlntsmmr.xlntgallery.data.ai

import android.content.Context

/**
 * AI 검색 모델 묶음 (다국어 CLIP).
 *
 * CLIP = 사진과 문장을 "같은 공간의 숫자 벡터(512개)"로 바꾸는 모델.
 * 사진 벡터와 검색어 벡터가 가까울수록(코사인 유사도↑) 내용이 비슷한 것.
 *  - 이미지 인코더: OpenAI CLIP ViT-B/32 (원본 그대로)
 *  - 텍스트 인코더: clip-ViT-B-32-multilingual-v1
 *      원본 CLIP 텍스트 인코더를 선생님으로 삼아 50개+ 언어(한국어 포함)로 학습시킨 모델
 *      → "강아지"와 "dog"가 거의 같은 벡터가 되어, 한국어로 검색해도 사진과 비교 가능
 */
object ClipModels {
    // 모델이 바뀌면 이 값을 바꾸고 EMBEDDING_VERSION을 올려서 전체 재분석
    const val MODEL_ID = "clip-b32-multilingual-v1"
    const val EMBEDDING_VERSION = 1
    const val DIMENSION = 512

    const val VISION = "vision.onnx"
    const val TEXT = "text.onnx"
    const val VOCAB = "vocab.txt"
    const val DENSE = "dense.safetensors"

    fun pack(context: Context) = ModelPack(
        context = context,
        folderName = "clip",
        files = listOf(
            // 이미지 인코더 (int8 양자화: 원본 352MB → 89MB)
            ModelFile(
                VISION,
                "https://huggingface.co/Xenova/clip-vit-base-patch32/resolve/main/onnx/vision_model_quantized.onnx",
                89_100_000
            ),
            // 다국어 텍스트 인코더 (ARM용 int8 양자화)
            ModelFile(
                TEXT,
                "https://huggingface.co/sentence-transformers/clip-ViT-B-32-multilingual-v1/resolve/main/onnx/model_qint8_arm64.onnx",
                135_000_000
            ),
            // 텍스트 → 토큰 번호 사전
            ModelFile(
                VOCAB,
                "https://huggingface.co/sentence-transformers/clip-ViT-B-32-multilingual-v1/resolve/main/vocab.txt",
                996_000
            ),
            // 텍스트 벡터 768개 → 512개로 바꾸는 마지막 층 (ONNX에 빠져 있어서 따로 받음)
            ModelFile(
                DENSE,
                "https://huggingface.co/sentence-transformers/clip-ViT-B-32-multilingual-v1/resolve/main/2_Dense/model.safetensors",
                1_570_000
            )
        )
    )
}
