package com.xlntsmmr.xlntgallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * ML Kit 한국어 OCR 엔진 (기기 안에서만 동작, 인터넷 불필요). 한국어 모델은 영어·숫자도 인식함.
 *
 * 2단계로 동작:
 *  1) recognize(): 사진 전체를 한 번 인식 → 빠르게 결과 표시
 *  2) refine(): 글자 덩어리(블록)마다 원본 해상도로 잘라서, 확대·기울기 보정·대비 강화한
 *     여러 버전을 다시 인식 → 확신도(confidence)가 가장 높은 결과로 교체
 */
class MlKitOcrEngine(private val context: Context) : OcrEngine {

    override val type = OcrEngineType.ML_KIT

    private val recognizer =
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

    // ── 1단계: 전체 인식 ─────────────────────────────────────

    override suspend fun recognize(uri: Uri): OcrResult {
        // fromFilePath는 EXIF 회전을 반영해 똑바로 세운 이미지를 만듦
        val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }
        val text = process(image)

        // 박스 좌표의 기준 크기. 회전 정보가 남아 있으면 가로·세로가 뒤바뀜
        val rotated = image.rotationDegrees % 180 != 0
        val width = (if (rotated) image.height else image.width).toFloat()
        val height = (if (rotated) image.width else image.height).toFloat()

        val blocks = text.textBlocks.mapNotNull { block ->
            val lines = block.lines.mapNotNull { line ->
                val lineBox = line.boundingBox?.toOcrBox(width, height) ?: return@mapNotNull null
                // ML Kit은 줄 안의 단어(element) 위치도 줌 → 길게 눌러 단어 단위로 선택 가능
                val words = line.elements.mapNotNull { element ->
                    val box = element.boundingBox?.toOcrBox(width, height) ?: return@mapNotNull null
                    OcrWord(text = element.text, box = box)
                }
                OcrLine(
                    text = line.text,
                    box = lineBox,
                    words = words.ifEmpty { listOf(OcrWord(line.text, lineBox)) }
                )
            }
            if (lines.isEmpty()) return@mapNotNull null
            OcrBlock(
                text = block.text,
                lines = lines,
                confidence = block.lines.averageConfidence(),
                angle = block.lines.map { it.angle }.average().toFloat()
            )
        }
        return OcrResult(text = text.text, blocks = blocks)
    }

    private fun Rect.toOcrBox(width: Float, height: Float) = OcrBox(
        left = (left / width).coerceIn(0f, 1f),
        top = (top / height).coerceIn(0f, 1f),
        right = (right / width).coerceIn(0f, 1f),
        bottom = (bottom / height).coerceIn(0f, 1f)
    )

    // ── 2단계: 블록별 정밀 재인식 ─────────────────────────────

    /**
     * @param maxBlocks 시간이 오래 걸리지 않도록, 확신도가 낮은 블록부터 최대 이 개수만 다시 인식
     */
    override suspend fun refine(uri: Uri, first: OcrResult): OcrResult = refine(uri, first, maxBlocks = 8)

    private suspend fun refine(uri: Uri, first: OcrResult, maxBlocks: Int): OcrResult =
        withContext(Dispatchers.Default) {
            if (first.blocks.isEmpty()) return@withContext first

            val rotation = readRotation(uri)
            // 원본 파일에서 "필요한 부분만" 원래 해상도로 읽어오는 도구 (전체를 메모리에 올리지 않음)
            val decoder = openRegionDecoder(uri) ?: return@withContext first

            try {
                val targets = first.blocks.indices
                    .sortedBy { first.blocks[it].confidence }   // 자신 없는 블록 먼저
                    .take(maxBlocks)
                    .toSet()

                val refinedBlocks = first.blocks.mapIndexed { index, block ->
                    if (index in targets) refineBlock(decoder, rotation, block) ?: block else block
                }
                OcrResult(
                    text = refinedBlocks.joinToString("\n") { it.text },
                    blocks = refinedBlocks
                )
            } finally {
                decoder.recycle()
            }
        }

    /** 블록 하나를 잘라서 여러 버전으로 다시 인식. 더 나은 결과가 없으면 null */
    private suspend fun refineBlock(
        decoder: BitmapRegionDecoder,
        rotation: Int,
        block: OcrBlock
    ): OcrBlock? {
        val rawWidth = decoder.width
        val rawHeight = decoder.height

        // 1. "똑바로 세운 사진" 기준 좌표 → "원본 파일" 기준 픽셀 좌표
        val raw = uprightToRaw(block.bounds, rotation)
        val region = Rect(
            (raw.left * rawWidth).roundToInt(),
            (raw.top * rawHeight).roundToInt(),
            (raw.right * rawWidth).roundToInt(),
            (raw.bottom * rawHeight).roundToInt()
        )
        if (region.width() < 4 || region.height() < 4) return null

        // 2. 주변 여백 추가 (글자가 가장자리에 딱 붙으면 인식률이 떨어짐)
        val padding = max(8, (min(region.width(), region.height()) * 0.3f).roundToInt())
        region.inset(-padding, -padding)
        region.intersect(0, 0, rawWidth, rawHeight)

        // 3. 확대 배율: 글자 높이가 약 64px가 되도록 (ML Kit 권장: 글자당 16px 이상, 클수록 유리)
        val lineHeightPx = block.lines.map { line ->
            val b = uprightToRaw(line.box, rotation)
            min((b.right - b.left) * rawWidth, (b.bottom - b.top) * rawHeight)  // 세로 글자도 대응
        }.sorted()[block.lines.size / 2]   // 중앙값
        var scale = (TARGET_LINE_HEIGHT_PX / lineHeightPx.coerceAtLeast(1f)).coerceIn(1f, 4f)
        scale = min(scale, MAX_CROP_SIDE_PX / max(region.width(), region.height()).toFloat())

        val crop = try {
            decoder.decodeRegion(region, null) ?: return null
        } catch (e: Exception) {
            return null
        }

        // 4. 여러 버전 만들기 → 각각 인식 → 가장 확신도 높은 것 선택
        val angles = buildList {
            add(0f)
            if (abs(block.angle) > 5f) {   // 기울어진 글자: 양방향 보정 둘 다 시도 (더 나은 쪽이 뽑힘)
                add(-block.angle)
                add(block.angle)
            }
        }

        var best: Pair<String, Float>? = null
        try {
            for (angle in angles) {
                val variants = buildList {
                    add(transform(crop, rotation + angle, scale, highContrast = false))
                    if (angle == 0f) add(transform(crop, rotation.toFloat(), scale, highContrast = true))
                }
                for (bitmap in variants) {
                    try {
                        val text = process(InputImage.fromBitmap(bitmap, 0))
                        val candidateText = text.text.trim()
                        val confidence = text.textBlocks.flatMap { it.lines }.averageConfidence()
                        if (candidateText.length >= 2 && (best == null || confidence > best.second)) {
                            best = candidateText to confidence
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        } finally {
            crop.recycle()
        }

        // 5. 원래 결과보다 확실히 나을 때만 교체
        val (bestText, bestConfidence) = best ?: return null
        if (bestConfidence <= block.confidence + 0.02f) return null

        // 다시 읽은 줄 수가 같으면 줄마다 글자도 교체 (길게 눌러 선택할 때도 정확한 글자가 복사되게)
        val newLineTexts = bestText.lines()
        val newLines = if (newLineTexts.size == block.lines.size) {
            block.lines.zip(newLineTexts) { line, text ->
                line.copy(text = text, words = listOf(OcrWord(text, line.box)))
            }
        } else block.lines
        return block.copy(text = bestText, confidence = bestConfidence, lines = newLines)
    }

    /** 회전(세우기 + 기울기 보정) → 확대 → (선택) 흑백 대비 강화. 빈 모서리는 흰색으로 채움 */
    private fun transform(src: Bitmap, degrees: Float, scale: Float, highContrast: Boolean): Bitmap {
        val matrix = Matrix().apply {
            postRotate(degrees)
            postScale(scale, scale)
        }
        val bounds = RectF(0f, 0f, src.width.toFloat(), src.height.toFloat())
        matrix.mapRect(bounds)
        matrix.postTranslate(-bounds.left, -bounds.top)

        val out = Bitmap.createBitmap(
            bounds.width().roundToInt().coerceAtLeast(1),
            bounds.height().roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)   // 부드럽게 확대
        if (highContrast) {
            val contrast = 1.6f
            val translate = (1f - contrast) * 128f
            val colorMatrix = ColorMatrix().apply {
                setSaturation(0f)   // 흑백
                postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            contrast, 0f, 0f, 0f, translate,
                            0f, contrast, 0f, 0f, translate,
                            0f, 0f, contrast, 0f, translate,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(src, matrix, paint)
        }
        return out
    }

    /**
     * 좌표 변환: 똑바로 세운 사진의 비율 좌표 → 원본 파일(회전 전)의 비율 좌표.
     * 휴대폰 사진은 파일 자체는 눕혀 저장하고 EXIF에 "90도 돌려서 보여줘"라고 적어 두는 경우가 많음.
     */
    private fun uprightToRaw(box: OcrBox, rotation: Int): OcrBox {
        fun map(x: Float, y: Float): Pair<Float, Float> = when (rotation) {
            90 -> y to 1f - x
            180 -> 1f - x to 1f - y
            270 -> 1f - y to x
            else -> x to y
        }
        val (x1, y1) = map(box.left, box.top)
        val (x2, y2) = map(box.right, box.bottom)
        return OcrBox(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2))
    }

    private fun readRotation(uri: Uri): Int = try {
        context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
    } catch (e: Exception) {
        0
    }

    private fun openRegionDecoder(uri: Uri): BitmapRegionDecoder? = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BitmapRegionDecoder.newInstance(stream)
            } else {
                @Suppress("DEPRECATION")
                BitmapRegionDecoder.newInstance(stream, false)
            }
        }
    } catch (e: Exception) {
        null   // 지원하지 않는 형식 등 → 2단계 생략
    }

    // ML Kit은 콜백(Task) 방식 → suspend 함수로 감싸서 코루틴에서 기다릴 수 있게
    private suspend fun process(image: InputImage): Text =
        suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result -> cont.resume(result) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }

    // 줄들의 확신도 평균 (긴 줄일수록 가중치↑)
    private fun List<Text.Line>.averageConfidence(): Float {
        val totalLength = sumOf { it.text.length }
        if (totalLength == 0) return 0f
        return sumOf { (it.confidence * it.text.length).toDouble() }.toFloat() / totalLength
    }

    // 모델이 메모리를 잡고 있으므로 다 쓰면 닫아야 함
    override fun close() = recognizer.close()

    private companion object {
        const val TARGET_LINE_HEIGHT_PX = 64f
        const val MAX_CROP_SIDE_PX = 2400f
    }
}
