package com.xlntsmmr.xlntgallery.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min

/**
 * PaddleOCR 모델 파일 관리 (다운로드 / 설치 여부 확인).
 * 모델이 커서(약 18MB) 앱에 넣지 않고, 사용자가 PaddleOCR을 고를 때 한 번만 내려받음.
 * 받은 뒤에는 인터넷 없이 기기 안에서만 동작.
 */
class PaddleModelManager(context: Context) {

    private val dir = File(context.filesDir, "paddleocr")

    val detFile get() = File(dir, DET.fileName)
    val recFile get() = File(dir, REC.fileName)
    val dictFile get() = File(dir, DICT.fileName)

    fun isReady(): Boolean = listOf(detFile, recFile, dictFile).all { it.exists() && it.length() > 0 }

    /** 다운로드. 진행률(0~1)을 차례로 내보냄 */
    fun download(): Flow<Float> = flow {
        dir.mkdirs()
        val total = FILES.sumOf { it.approxBytes }.toFloat()
        var finishedBytes = 0L

        for (model in FILES) {
            val target = File(dir, model.fileName)
            if (target.exists() && target.length() > 0) {   // 이미 받은 파일은 건너뜀
                finishedBytes += model.approxBytes
                emit(finishedBytes / total)
                continue
            }

            // ".part"로 받다가 끝까지 받으면 이름 변경 → 중간에 끊겨도 깨진 파일이 "설치됨"으로 보이지 않음
            val partial = File(dir, model.fileName + ".part")
            val connection = (URL(model.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true   // Hugging Face는 실제 파일 위치로 리다이렉트함
            }
            try {
                connection.connect()
                if (connection.responseCode !in 200..299) {
                    throw IOException("HTTP ${connection.responseCode}")
                }
                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var fileBytes = 0L
                        var lastEmitted = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            fileBytes += read
                            if (fileBytes - lastEmitted > 256 * 1024) {   // 너무 자주 갱신하지 않게
                                lastEmitted = fileBytes
                                emit((finishedBytes + min(fileBytes, model.approxBytes)) / total)
                            }
                        }
                    }
                }
                if (!partial.renameTo(target)) throw IOException("파일 저장 실패")
            } catch (e: Exception) {
                partial.delete()
                throw e
            } finally {
                connection.disconnect()
            }

            finishedBytes += model.approxBytes
            emit(finishedBytes / total)
        }
    }.flowOn(Dispatchers.IO)   // 네트워크·디스크 작업은 IO 스레드에서

    private data class ModelFile(val fileName: String, val url: String, val approxBytes: Long)

    private companion object {
        // PP-OCRv5 모바일 검출 모델 (언어 공통, Apache-2.0)
        val DET = ModelFile(
            "det.onnx",
            "https://huggingface.co/ilaylow/PP_OCRv5_mobile_onnx/resolve/main/ppocrv5_det.onnx",
            4_830_000
        )
        // PP-OCRv5 한국어 인식 모델 (Apache-2.0)
        val REC = ModelFile(
            "rec.onnx",
            "https://huggingface.co/monkt/paddleocr-onnx/resolve/main/languages/korean/rec.onnx",
            13_400_000
        )
        // 인식 모델이 내놓는 번호 → 글자 대응표
        val DICT = ModelFile(
            "dict.txt",
            "https://huggingface.co/monkt/paddleocr-onnx/resolve/main/languages/korean/dict.txt",
            47_500
        )
        val FILES = listOf(DET, REC, DICT)
    }
}
