package com.xlntsmmr.xlntgallery.data.ai

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

/** 내려받을 모델 파일 하나 */
data class ModelFile(val fileName: String, val url: String, val approxBytes: Long)

/**
 * 여러 모델 파일을 한 묶음으로 관리 (설치 확인 / 다운로드).
 * filesDir 아래에 저장 → 앱 삭제 시 같이 지워지고, 다른 앱은 접근 불가.
 */
class ModelPack(context: Context, folderName: String, private val files: List<ModelFile>) {

    private val dir = File(context.filesDir, folderName)

    val totalBytes: Long get() = files.sumOf { it.approxBytes }

    fun file(name: String) = File(dir, name)

    fun isReady(): Boolean = files.all { file(it.fileName).let { f -> f.exists() && f.length() > 0 } }

    /** 다운로드. 진행률(0~1)을 차례로 내보냄. 이미 받은 파일은 건너뜀 */
    fun download(): Flow<Float> = flow {
        dir.mkdirs()
        val total = totalBytes.toFloat()
        var finished = 0L

        for (model in files) {
            val target = file(model.fileName)
            if (target.exists() && target.length() > 0) {
                finished += model.approxBytes
                emit(finished / total)
                continue
            }
            // ".part"로 받다가 끝까지 받으면 이름 변경 → 중간에 끊겨도 깨진 파일이 "설치됨"으로 보이지 않음
            val partial = File(dir, model.fileName + ".part")
            val already = if (partial.exists()) partial.length() else 0L
            val connection = (URL(model.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                // 이어받기: 이미 받은 만큼은 건너뛰고 나머지만 요청 (네트워크가 끊겨도 처음부터 다시 받지 않음)
                if (already > 0) setRequestProperty("Range", "bytes=$already-")
            }
            try {
                connection.connect()
                val code = connection.responseCode
                if (code !in 200..299) throw IOException("HTTP $code")
                // 206 = 이어받기 수락 → 뒤에 덧붙임 / 200 = 서버가 처음부터 보냄 → 덮어씀
                val append = code == HttpURLConnection.HTTP_PARTIAL
                connection.inputStream.use { input ->
                    java.io.FileOutputStream(partial, append).use { output ->
                        val buffer = ByteArray(128 * 1024)
                        var bytes = if (append) already else 0L
                        var lastEmitted = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            bytes += read
                            if (bytes - lastEmitted > 1_000_000) {
                                lastEmitted = bytes
                                emit((finished + min(bytes, model.approxBytes)) / total)
                            }
                        }
                    }
                }
                if (!partial.renameTo(target)) throw IOException("파일 저장 실패")
            } catch (e: IOException) {
                throw e   // 네트워크 문제: .part는 남겨 둠 → 다음에 이어받기
            } finally {
                connection.disconnect()
            }
            finished += model.approxBytes
            emit(finished / total)
        }
    }.flowOn(Dispatchers.IO)
}
