package com.xlntsmmr.xlntgallery.data.ai

import android.content.Context

/**
 * SigLIP 2 (Google, 2025) base / 224px.
 * CLIP과 같은 원리(사진·문장을 같은 공간의 벡터로)지만 학습 방식(sigmoid 손실)과
 * 다국어 데이터가 개선된 후속 모델. Gemma 토크나이저(약 25.6만 토큰)로 한국어를 직접 처리.
 * 출처: onnx-community/siglip2-base-patch16-224-ONNX (Apache-2.0)
 */
object SiglipModels {
    const val MODEL_ID = "siglip2-base-224"
    const val EMBEDDING_VERSION = 101

    const val VISION = "vision.onnx"
    const val TEXT = "text.onnx"
    const val TOKENIZER = "tokenizer.model"

    private const val BASE = "https://huggingface.co/onnx-community/siglip2-base-patch16-224-ONNX/resolve/main"

    fun pack(context: Context) = ModelPack(
        context = context,
        folderName = "siglip2",
        files = listOf(
            ModelFile(VISION, "$BASE/onnx/vision_model_quantized.onnx", 94_600_000),
            ModelFile(TEXT, "$BASE/onnx/text_model_quantized.onnx", 283_000_000),
            ModelFile(TOKENIZER, "$BASE/tokenizer.model", 4_240_000)
        )
    )
}
