package com.xlntsmmr.xlntgallery.data.index

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.xlntsmmr.xlntgallery.data.MlKitOcrEngine
import com.xlntsmmr.xlntgallery.data.OcrEngine
import com.xlntsmmr.xlntgallery.data.OcrEngineType
import com.xlntsmmr.xlntgallery.data.PaddleModelManager
import com.xlntsmmr.xlntgallery.data.PaddleOcrEngine
import com.xlntsmmr.xlntgallery.data.SettingsRepository
import com.xlntsmmr.xlntgallery.data.ai.AiModelType
import com.xlntsmmr.xlntgallery.data.ai.AiModels
import com.xlntsmmr.xlntgallery.data.ai.BitmapLoader
import com.xlntsmmr.xlntgallery.data.ai.VectorMath
import com.xlntsmmr.xlntgallery.data.db.GalleryDatabase
import com.xlntsmmr.xlntgallery.data.db.PhotoEmbeddingEntity
import com.xlntsmmr.xlntgallery.data.db.PhotoEntity
import com.xlntsmmr.xlntgallery.data.db.SearchTokenizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 사진 분석(인덱싱) 본체.
 *  1) MediaStore ↔ DB 동기화 (새 사진 추가, 수정된 사진 재분석 표시, 지워진 사진 삭제)
 *  2) 아직 분석 안 된 사진마다: OCR 글자 저장 + AI 벡터 저장
 * 중간에 멈춰도 분석 완료 표시(*Version)가 사진마다 저장되므로, 다시 시작하면 남은 것부터 이어서 함.
 */
class PhotoIndexer(private val context: Context) {

    private val dao = GalleryDatabase.get(context).galleryDao()
    private val settings = SettingsRepository(context)

    /**
     * @param deadlineMillis 이 시각(System.currentTimeMillis)이 지나면 멈추고 false 반환 → 다음 작업에서 이어서.
     *                      시스템의 백그라운드 작업 시간 제한(약 10분)을 넘기지 않기 위함
     * @return true = 남은 사진 없이 끝까지 완료
     */
    suspend fun run(deadlineMillis: Long = Long.MAX_VALUE, onProgress: suspend (done: Int) -> Unit = {}): Boolean {
        syncWithMediaStore()

        // 준비된 이미지 AI 모델 전부 (SigLIP 2 + CLIP 앙상블)
        val accelerator = settings.accelerator.first()
        val readyModels = AiModels.ALL.filter { AiModels.pack(context, it).isReady() }
        val encoders = readyModels.associateWith { AiModels.imageEmbedder(context, it, accelerator) }
        val siglipId = AiModels.modelId(AiModelType.SIGLIP2)
        val clipId = AiModels.modelId(AiModelType.CLIP)
        val ocrEngine = createOcrEngine()

        try {
            var done = 0
            while (true) {
                if (System.currentTimeMillis() > deadlineMillis) return false

                val batch = dao.pendingPhotos(
                    ocrVersion = OCR_VERSION,
                    needSiglip = AiModelType.SIGLIP2 in encoders,
                    siglipModel = siglipId,
                    needClip = AiModelType.CLIP in encoders,
                    clipModel = clipId,
                    limit = BATCH_SIZE
                )
                if (batch.isEmpty()) return true

                for (photo in batch) {
                    currentCoroutineContext().ensureActive()   // 작업이 취소됐으면 여기서 멈춤
                    val uri = Uri.parse(photo.uri)

                    if (photo.ocrVersion < OCR_VERSION) {
                        val text = runCatchingNotCancel { ocrEngine.recognize(uri).text } ?: ""
                        // 실패해도 버전은 저장 → 같은 사진에서 무한 반복하지 않게
                        dao.saveOcr(photo.id, text, SearchTokenizer.indexTokens(text), ocrEngine.type.displayName, OCR_VERSION)
                    }

                    // 아직 벡터가 없는 모델만 실행. 사진은 한 번만 읽어서 두 모델이 같이 씀
                    val existing = dao.embeddedModels(photo.id).toSet()
                    val missing = encoders.filterKeys { AiModels.modelId(it) !in existing }
                    if (missing.isNotEmpty()) {
                        val bitmap = runCatchingNotCancel {
                            withContext(Dispatchers.IO) { BitmapLoader.loadUpright(context, uri, minShortSide = 224) }
                        }
                        try {
                            for ((type, encoder) in missing) {
                                val vector = bitmap?.let { b ->
                                    runCatchingNotCancel { withContext(Dispatchers.Default) { encoder.encode(b) } }
                                }
                                // 실패하면 빈 벡터로 저장 → "분석 완료(실패)"로 표시해 무한 반복 방지
                                dao.upsertEmbedding(
                                    PhotoEmbeddingEntity(
                                        photo.id,
                                        AiModels.modelId(type),
                                        vector?.let { VectorMath.toBytes(it) } ?: ByteArray(0)
                                    )
                                )
                            }
                        } finally {
                            bitmap?.recycle()
                        }
                    }

                    done++
                    onProgress(done)
                }
            }
        } finally {
            ocrEngine.close()
            encoders.values.forEach { it.close() }
        }
    }

    private suspend fun createOcrEngine(): OcrEngine {
        val paddleModels = PaddleModelManager(context)
        return if (settings.ocrEngine.first() == OcrEngineType.PADDLE && paddleModels.isReady()) {
            PaddleOcrEngine(context, paddleModels, settings.accelerator.first())
        } else {
            MlKitOcrEngine(context)
        }
    }

    /** MediaStore 사진 목록과 DB를 맞춤 */
    private suspend fun syncWithMediaStore() = withContext(Dispatchers.IO) {
        val existing = dao.allPhotoStamps().associate { it.id to it.dateModified }
        val seen = HashSet<Long>()
        val changed = mutableListOf<PhotoEntity>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED
        )
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, null
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val modifiedCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val modified = c.getLong(modifiedCol)
                seen += id
                if (existing[id] == modified) continue   // 그대로인 사진은 건너뜀

                val taken = c.getLong(takenCol)
                changed += PhotoEntity(
                    id = id,
                    uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id).toString(),
                    displayName = c.getString(nameCol) ?: "",
                    dateTaken = if (taken > 0) taken else c.getLong(addedCol) * 1000,
                    dateModified = modified
                    // ocrVersion·embeddingVersion = 0 → 새 사진이거나 수정된 사진은 다시 분석
                )
            }
        }

        changed.chunked(500).forEach { chunk ->
            // 수정된 사진은 예전 분석 결과(벡터·태그)를 지움 → 다시 분석됨
            val modifiedIds = chunk.map { it.id }.filter { it in existing }
            if (modifiedIds.isNotEmpty()) {
                dao.deleteEmbeddings(modifiedIds)
                dao.deleteTags(modifiedIds)
            }
            dao.upsertPhotos(chunk)
        }
        val removed = (existing.keys - seen).toList()
        removed.chunked(500).forEach {
            dao.deleteFts(it)
            dao.deletePhotos(it)   // photo_text, photo_embeddings는 CASCADE로 같이 삭제
        }
    }

    private suspend fun <T> runCatchingNotCancel(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null   // 아주 큰 사진 등: 이 사진만 건너뜀
    }

    companion object {
        const val OCR_VERSION = 1
        private const val BATCH_SIZE = 20
    }
}
