package com.xlntsmmr.xlntgallery.data

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.xlntsmmr.xlntgallery.data.ai.HardwareAccelerator
import com.xlntsmmr.xlntgallery.data.ai.OrtModel
import com.xlntsmmr.xlntgallery.data.ai.OrtSessionFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * PaddleOCR (PP-OCRv5) 엔진. ONNX Runtime으로 기기 안에서 실행.
 *
 * ML Kit과 달리 "검출"과 "인식"이 별도 모델:
 *  1) 검출(det): 사진에서 글자가 있는 영역(박스)을 찾음
 *  2) 인식(rec): 박스마다 잘라서 글자를 읽음 (CTC 방식)
 */
class PaddleOcrEngine(
    private val context: Context,
    private val models: PaddleModelManager,
    private val accelerator: HardwareAccelerator = HardwareAccelerator.AUTO
) : OcrEngine {

    override val type = OcrEngineType.PADDLE

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    // 모델 로딩은 무거우므로 처음 쓸 때 한 번만
    private var detModel: OrtModel? = null
    private var recModel: OrtModel? = null
    private val detSession: OrtSession? get() = detModel?.session
    private val recSession: OrtSession? get() = recModel?.session
    private var dictionary: List<String> = emptyList()
    private var recHeight = 48     // 인식 모델 입력 높이 (모델 정보에서 읽음)
    private var recFixedWidth = -1 // 모델이 너비를 고정했으면 그 값, 아니면 -1

    private fun ensureLoaded() {
        if (detModel != null) return
        check(models.isReady()) { "PaddleOCR 모델이 아직 다운로드되지 않았어요" }

        // 설정의 장치(자동/NPU/GPU/CPU)로 실행
        detModel = OrtSessionFactory.create(models.detFile.absolutePath, accelerator, "PaddleOCR 검출")
        val recLoaded = OrtSessionFactory.create(models.recFile.absolutePath, accelerator, "PaddleOCR 인식")
        recModel = recLoaded
        val rec = recLoaded.session

        // 입력 모양 [배치, 3, 높이, 너비]. -1은 "아무 크기나 가능"
        val shape = (rec.inputInfo.values.first().info as TensorInfo).shape
        if (shape.size == 4) {
            if (shape[2] > 0) recHeight = shape[2].toInt()
            if (shape[3] > 0) recFixedWidth = shape[3].toInt()
        }
        dictionary = models.dictFile.readLines(Charsets.UTF_8)
    }

    override suspend fun recognize(uri: Uri): OcrResult = withContext(Dispatchers.Default) {
        ensureLoaded()
        val bitmap = loadUprightBitmap(uri)
        try {
            val width = bitmap.width.toFloat()
            val height = bitmap.height.toFloat()

            val blocks = detect(bitmap).mapNotNull { rect ->
                val (text, confidence) = recognizeLine(bitmap, rect) ?: return@mapNotNull null
                val box = OcrBox(rect.left / width, rect.top / height, rect.right / width, rect.bottom / height)
                // Paddle은 줄 단위 결과 → 줄 하나를 블록 하나로
                OcrBlock(
                    text = text,
                    lines = listOf(OcrLine(text = text, box = box, words = estimateWords(text, box))),
                    confidence = confidence
                )
            }
            OcrResult(text = blocks.joinToString("\n") { it.text }, blocks = blocks)
        } finally {
            bitmap.recycle()
        }
    }

    // ── 1) 검출 ─────────────────────────────────────

    private fun detect(bitmap: Bitmap): List<RectF> {
        val session = detSession!!

        // 긴 변 960px 이하, 가로·세로는 32의 배수 (모델 구조상 요구)
        val scale = min(1f, DET_MAX_SIDE / max(bitmap.width, bitmap.height).toFloat())
        val detW = max(32, ((bitmap.width * scale) / 32f).roundToInt() * 32)
        val detH = max(32, ((bitmap.height * scale) / 32f).roundToInt() * 32)
        val resized = Bitmap.createScaledBitmap(bitmap, detW, detH, true)

        // 픽셀 → [1, 3, H, W] 실수 배열. Paddle 규칙: BGR 순서, ImageNet 평균·표준편차로 정규화
        val area = detW * detH
        val pixels = IntArray(area)
        resized.getPixels(pixels, 0, detW, 0, 0, detW, detH)
        if (resized !== bitmap) resized.recycle()
        val input = FloatArray(3 * area)
        for (i in 0 until area) {
            val p = pixels[i]
            val r = (p shr 16 and 0xFF) / 255f
            val g = (p shr 8 and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            input[i] = (b - 0.485f) / 0.229f
            input[area + i] = (g - 0.456f) / 0.224f
            input[2 * area + i] = (r - 0.406f) / 0.225f
        }

        // 출력: 픽셀마다 "글자일 확률" 지도 [1, 1, H, W]
        val prob = FloatArray(area)
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 3, detH.toLong(), detW.toLong())).use { tensor ->
            session.run(mapOf(session.inputNames.first() to tensor)).use { output ->
                (output[0] as OnnxTensor).floatBuffer.get(prob)
            }
        }

        // 확률 지도에서 붙어 있는 글자 픽셀 덩어리를 찾아 박스로 (연결 요소 탐색, BFS)
        val visited = BooleanArray(area)
        val queue = IntArray(area)
        val boxes = mutableListOf<RectF>()
        val toOriginalX = bitmap.width / detW.toFloat()
        val toOriginalY = bitmap.height / detH.toFloat()

        for (start in 0 until area) {
            if (visited[start] || prob[start] < BINARY_THRESHOLD) continue

            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            var minX = detW; var minY = detH; var maxX = 0; var maxY = 0
            var sum = 0f; var count = 0

            while (head < tail) {
                val index = queue[head++]
                val x = index % detW
                val y = index / detW
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                sum += prob[index]
                count++

                // 상하좌우 이웃
                if (x > 0) tail = visit(index - 1, prob, visited, queue, tail)
                if (x < detW - 1) tail = visit(index + 1, prob, visited, queue, tail)
                if (y > 0) tail = visit(index - detW, prob, visited, queue, tail)
                if (y < detH - 1) tail = visit(index + detW, prob, visited, queue, tail)
            }

            val boxW = (maxX - minX + 1).toFloat()
            val boxH = (maxY - minY + 1).toFloat()
            if (count < 4 || min(boxW, boxH) < 3f) continue      // 너무 작은 잡음
            if (sum / count < BOX_THRESHOLD) continue             // 확신 낮은 영역

            // DB 모델은 글자보다 살짝 작은 영역을 내놓으므로 바깥으로 넓혀 줌 (unclip)
            val distance = boxW * boxH * UNCLIP_RATIO / (2f * (boxW + boxH))
            boxes += RectF(
                ((minX - distance) * toOriginalX).coerceAtLeast(0f),
                ((minY - distance) * toOriginalY).coerceAtLeast(0f),
                ((maxX + 1 + distance) * toOriginalX).coerceAtMost(bitmap.width.toFloat()),
                ((maxY + 1 + distance) * toOriginalY).coerceAtMost(bitmap.height.toFloat())
            )
        }
        // 읽는 순서: 위 → 아래, 왼쪽 → 오른쪽
        return boxes.sortedWith(compareBy({ it.top }, { it.left }))
    }

    private fun visit(index: Int, prob: FloatArray, visited: BooleanArray, queue: IntArray, tail: Int): Int {
        if (visited[index] || prob[index] < BINARY_THRESHOLD) return tail
        visited[index] = true
        queue[tail] = index
        return tail + 1
    }

    // ── 2) 인식 ─────────────────────────────────────

    /** 박스 하나를 잘라서 글자 읽기. (글자, 확신도) 또는 null */
    private fun recognizeLine(bitmap: Bitmap, rect: RectF): Pair<String, Float>? {
        val session = recSession!!
        val left = rect.left.toInt()
        val top = rect.top.toInt()
        val cropW = (rect.right.toInt() - left).coerceAtMost(bitmap.width - left)
        val cropH = (rect.bottom.toInt() - top).coerceAtMost(bitmap.height - top)
        if (cropW < 4 || cropH < 4) return null

        var crop = Bitmap.createBitmap(bitmap, left, top, cropW, cropH)
        // 세로로 긴 영역(세로쓰기)은 눕혀서 읽음 (Paddle 기본 동작과 동일)
        if (cropH >= cropW * 1.5f) {
            val rotated = Bitmap.createBitmap(crop, 0, 0, cropW, cropH, Matrix().apply { postRotate(-90f) }, true)
            crop.recycle()
            crop = rotated
        }

        // 높이를 모델 입력 높이(보통 48)에 맞추고, 너비는 비율대로
        val ratio = crop.width / crop.height.toFloat()
        val targetW = if (recFixedWidth > 0) recFixedWidth
        else ceil(recHeight * ratio).toInt().coerceIn(recHeight / 2, MAX_REC_WIDTH)
        val contentW = min(targetW, ceil(recHeight * ratio).toInt().coerceAtLeast(1))
        val resized = Bitmap.createScaledBitmap(crop, contentW, recHeight, true)
        if (resized !== crop) crop.recycle()

        // [1, 3, H, W], BGR, (x/255 - 0.5) / 0.5. 남는 너비는 0(회색)으로 채움
        val area = targetW * recHeight
        val input = FloatArray(3 * area)
        val pixels = IntArray(contentW * recHeight)
        resized.getPixels(pixels, 0, contentW, 0, 0, contentW, recHeight)
        resized.recycle()
        for (y in 0 until recHeight) {
            for (x in 0 until contentW) {
                val p = pixels[y * contentW + x]
                val i = y * targetW + x
                input[i] = ((p and 0xFF) / 255f - 0.5f) / 0.5f
                input[area + i] = ((p shr 8 and 0xFF) / 255f - 0.5f) / 0.5f
                input[2 * area + i] = ((p shr 16 and 0xFF) / 255f - 0.5f) / 0.5f
            }
        }

        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 3, recHeight.toLong(), targetW.toLong())).use { tensor ->
            session.run(mapOf(session.inputNames.first() to tensor)).use { output ->
                val result = output[0] as OnnxTensor
                val shape = result.info.shape            // [1, 시간축 T, 글자 종류 C]
                val steps = shape[1].toInt()
                val classes = shape[2].toInt()
                val scores = FloatArray(steps * classes)
                result.floatBuffer.get(scores)
                return ctcDecode(scores, steps, classes)
            }
        }
    }

    /**
     * CTC 디코딩: 시간축마다 가장 확률 높은 글자를 고르고,
     * 연속 중복을 합치고, 0번(공백 토큰 blank)은 버림. "ㄱㄱ_ㄴ" → "ㄱㄴ"
     */
    private fun ctcDecode(scores: FloatArray, steps: Int, classes: Int): Pair<String, Float>? {
        val text = StringBuilder()
        var confidenceSum = 0f
        var kept = 0
        var previous = -1
        for (t in 0 until steps) {
            var bestIndex = 0
            var bestScore = scores[t * classes]
            for (c in 1 until classes) {
                val s = scores[t * classes + c]
                if (s > bestScore) { bestScore = s; bestIndex = c }
            }
            if (bestIndex != 0 && bestIndex != previous) {
                // 1번부터 사전 글자, 사전 범위를 넘는 마지막 번호는 띄어쓰기
                text.append(dictionary.getOrNull(bestIndex - 1) ?: " ")
                confidenceSum += bestScore
                kept++
            }
            previous = bestIndex
        }
        val result = text.toString().trim()
        if (result.isEmpty() || kept == 0) return null
        val confidence = confidenceSum / kept
        return if (confidence >= MIN_CONFIDENCE) result to confidence else null
    }

    /** Paddle은 단어 위치를 안 줌 → 글자 수 비율로 단어 위치를 추정 (가로쓰기 기준) */
    private fun estimateWords(text: String, box: OcrBox): List<OcrWord> {
        val words = text.split(' ').filter { it.isNotEmpty() }
        if (words.size <= 1) return listOf(OcrWord(text, box))
        val totalChars = text.length.toFloat()
        val width = box.right - box.left
        var cursor = 0
        return words.map { word ->
            val start = text.indexOf(word, cursor)
            cursor = start + word.length
            OcrWord(
                text = word,
                box = box.copy(
                    left = box.left + width * (start / totalChars),
                    right = box.left + width * (cursor / totalChars)
                )
            )
        }
    }

    // ── 사진 읽기 ─────────────────────────────────────

    /** 긴 변 약 2048px 이하로 읽고, EXIF 회전을 반영해 똑바로 세움 */
    private fun loadUprightBitmap(uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_IMAGE_SIDE) sampleSize *= 2

        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } ?: error("사진을 읽을 수 없어요")

        val rotation = try {
            resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        } catch (e: Exception) {
            0
        }
        if (rotation == 0) return decoded
        val rotated = Bitmap.createBitmap(
            decoded, 0, 0, decoded.width, decoded.height,
            Matrix().apply { postRotate(rotation.toFloat()) }, true
        )
        decoded.recycle()
        return rotated
    }

    override fun close() {
        detModel?.close()
        recModel?.close()
        detModel = null
        recModel = null
    }

    private companion object {
        const val DET_MAX_SIDE = 960f
        const val MAX_IMAGE_SIDE = 2048
        const val MAX_REC_WIDTH = 1600
        const val BINARY_THRESHOLD = 0.3f   // 이 확률 이상이면 글자 픽셀
        const val BOX_THRESHOLD = 0.6f      // 박스 평균 확률이 이 이상이어야 채택
        const val UNCLIP_RATIO = 1.5f       // 박스를 바깥으로 넓히는 정도
        const val MIN_CONFIDENCE = 0.5f     // 인식 확신도가 이보다 낮으면 버림
    }
}
