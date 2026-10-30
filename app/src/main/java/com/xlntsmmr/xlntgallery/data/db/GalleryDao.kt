package com.xlntsmmr.xlntgallery.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// DAO = DB에 무엇을 할 수 있는지 정의. 구현은 Room이 컴파일할 때 자동으로 만들어 줌
@Dao
interface GalleryDao {

    // ── 사진 목록 동기화 ─────────────────────────────

    @Query("SELECT id, dateModified FROM photos")
    suspend fun allPhotoStamps(): List<PhotoStamp>

    @Upsert
    suspend fun upsertPhotos(photos: List<PhotoEntity>)

    @Query("DELETE FROM photos WHERE id IN (:ids)")
    suspend fun deletePhotos(ids: List<Long>)

    // FTS 테이블은 외래키를 못 걸어서 직접 지워야 함
    @Query("DELETE FROM photo_text_fts WHERE rowid IN (:ids)")
    suspend fun deleteFts(ids: List<Long>)

    // ── 분석 대기열 ─────────────────────────────────

    /** 분석할 사진: OCR이 안 됐거나, 준비된 AI 모델의 벡터가 아직 없는 사진 */
    @Query(
        """
        SELECT * FROM photos
        WHERE ocrVersion < :ocrVersion
           OR (:needSiglip AND NOT EXISTS (
                SELECT 1 FROM photo_embeddings e WHERE e.photoId = photos.id AND e.model = :siglipModel))
           OR (:needClip AND NOT EXISTS (
                SELECT 1 FROM photo_embeddings e WHERE e.photoId = photos.id AND e.model = :clipModel))
        ORDER BY dateTaken DESC
        LIMIT :limit
        """
    )
    suspend fun pendingPhotos(
        ocrVersion: Int,
        needSiglip: Boolean,
        siglipModel: String,
        needClip: Boolean,
        clipModel: String,
        limit: Int
    ): List<PhotoEntity>

    @Query("SELECT model FROM photo_embeddings WHERE photoId = :photoId")
    suspend fun embeddedModels(photoId: Long): List<String>

    @Query("DELETE FROM photo_embeddings WHERE photoId IN (:ids)")
    suspend fun deleteEmbeddings(ids: List<Long>)

    @Query("DELETE FROM photo_tags WHERE photoId IN (:ids)")
    suspend fun deleteTags(ids: List<Long>)

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM photos) AS total,
            (SELECT COUNT(*) FROM photos WHERE ocrVersion >= :ocrVersion) AS ocrDone,
            (SELECT COUNT(*) FROM photo_embeddings WHERE model = :siglipModel) AS siglipDone,
            (SELECT COUNT(*) FROM photo_embeddings WHERE model = :clipModel) AS clipDone
        """
    )
    fun observeStats(ocrVersion: Int, siglipModel: String, clipModel: String): Flow<IndexStats>   // 값이 바뀔 때마다 새로 흘러나옴

    // ── OCR 결과 저장 ───────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertText(text: PhotoTextEntity)

    @Query("INSERT INTO photo_text_fts(rowid, tokens) VALUES(:photoId, :tokens)")
    suspend fun insertFts(photoId: Long, tokens: String)

    @Query("UPDATE photos SET ocrVersion = :version WHERE id = :photoId")
    suspend fun setOcrVersion(photoId: Long, version: Int)

    // 여러 작업을 하나로 묶음 → 중간에 실패하면 전부 취소 (반쯤 저장된 상태 방지)
    @Transaction
    suspend fun saveOcr(photoId: Long, text: String, tokens: String, engine: String, version: Int) {
        insertText(PhotoTextEntity(photoId, text, engine))
        deleteFts(listOf(photoId))
        if (tokens.isNotBlank()) insertFts(photoId, tokens)
        setOcrVersion(photoId, version)
    }

    // ── AI 벡터 저장 ────────────────────────────────

    @Upsert
    suspend fun upsertEmbedding(embedding: PhotoEmbeddingEntity)

    // 빈 벡터(분석 실패 표시)는 검색에서 제외
    @Query("SELECT photoId, vector FROM photo_embeddings WHERE model = :model AND LENGTH(vector) > 0")
    suspend fun allEmbeddings(model: String): List<EmbeddingRow>

    @Query("SELECT COUNT(*) FROM photo_embeddings WHERE model = :model AND LENGTH(vector) > 0")
    suspend fun embeddingCount(model: String): Int

    // ── 검색 ────────────────────────────────────────

    @Query(
        """
        SELECT * FROM photos
        WHERE displayName LIKE '%' || :query || '%'
        ORDER BY dateTaken DESC LIMIT :limit
        """
    )
    suspend fun searchByName(query: String, limit: Int = 300): List<PhotoEntity>

    @Query(
        """
        SELECT photos.*, photo_text.text AS text
        FROM photos
        JOIN photo_text ON photo_text.photoId = photos.id
        WHERE photos.id IN (SELECT rowid FROM photo_text_fts WHERE photo_text_fts MATCH :match)
        ORDER BY photos.dateTaken DESC LIMIT :limit
        """
    )
    suspend fun searchByText(match: String, limit: Int = 300): List<PhotoWithText>

    // ── Gemini Nano 태그 ────────────────────────────

    /** 태그 대기열: OCR이 끝난 사진 중 글자가 많은 사진(문서류) 먼저, 그다음 최신순 */
    @Query(
        """
        SELECT photos.*, photo_text.text AS text
        FROM photos
        LEFT JOIN photo_text ON photo_text.photoId = photos.id
        WHERE photos.tagVersion != :tagVersion AND photos.ocrVersion > 0
        ORDER BY (LENGTH(COALESCE(photo_text.text, '')) >= 20) DESC, photos.dateTaken DESC
        LIMIT :limit
        """
    )
    suspend fun pendingTags(tagVersion: Int, limit: Int): List<PendingTag>

    @Upsert
    suspend fun upsertTag(tag: PhotoTagEntity)

    @Query("UPDATE photos SET tagVersion = :version WHERE id = :photoId")
    suspend fun setTagVersion(photoId: Long, version: Int)

    @Transaction
    suspend fun saveTag(tag: PhotoTagEntity, version: Int) {
        upsertTag(tag)
        setTagVersion(tag.photoId, version)
    }

    @Query(
        """
        SELECT (SELECT COUNT(*) FROM photos) AS total,
               (SELECT COUNT(*) FROM photos WHERE tagVersion = :tagVersion) AS tagged
        """
    )
    fun observeTagStats(tagVersion: Int): Flow<TagStats>

    /** 태그 검색 (태그는 짧아서 LIKE로 충분히 빠름) */
    @Query(
        """
        SELECT photoId, category, keywords, summary FROM photo_tags
        WHERE category LIKE '%' || :query || '%'
           OR keywords LIKE '%' || :query || '%'
           OR summary LIKE '%' || :query || '%'
        LIMIT :limit
        """
    )
    suspend fun searchTags(query: String, limit: Int = 300): List<TagMatch>

    /** 이 사진들 중 Gemini가 이미 본(태그가 있는) 사진 */
    @Query("SELECT photoId, category FROM photo_tags WHERE photoId IN (:ids) AND category != ''")
    suspend fun taggedAmong(ids: List<Long>): List<TaggedPhoto>

    @Query("SELECT * FROM photos WHERE id IN (:ids)")
    suspend fun photosByIds(ids: List<Long>): List<PhotoEntity>
}
