package com.xlntsmmr.xlntgallery.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * ── DB 설계 ─────────────────────────────────────────────
 *
 *  photos               사진 1장 = 1행. MediaStore의 사진 목록을 복사해 둔 것 + 분석 진행 상태
 *    └ photo_text       OCR로 읽은 글자 원문 (사진과 1:1)
 *    └ photo_text_fts   글자 검색용 전문 검색(FTS) 색인. 한글은 2글자씩 쪼개서 저장
 *    └ photo_embeddings AI 검색용 이미지 벡터. 사진 1장 × 모델별로 1개씩 (SigLIP 2, CLIP)  ← DB 버전 3에서 변경
 *
 *  - 사진이 지워지면 photo_text, photo_embeddings는 외래키(CASCADE)로 같이 지워짐
 *    └ photo_tags       Gemini Nano가 사진을 보고 만든 종류·키워드·요약 (사진과 1:1)  ← DB 버전 2에서 추가
 *
 *  - *Version 컬럼: 분석 방식(모델)이 바뀌면 버전을 올려서 다시 분석할 사진을 골라냄
 */

@Entity(
    tableName = "photos",
    indices = [Index("dateTaken")]   // 최신순 정렬이 잦으므로 색인
)
data class PhotoEntity(
    @PrimaryKey val id: Long,          // MediaStore _ID 그대로 사용
    val uri: String,
    val displayName: String,
    val dateTaken: Long,
    val dateModified: Long,            // 이 값이 바뀌면 사진이 수정된 것 → 다시 분석
    val ocrVersion: Int = 0,           // 0 = 아직 분석 안 함
    val embeddingVersion: Int = 0,     // (버전 3부터 사용 안 함: 모델별 벡터 존재 여부로 판단)
    // 버전 2에서 추가된 컬럼. 기존 DB에 추가할 때 기본값이 있어야 해서 defaultValue 지정
    @ColumnInfo(defaultValue = "0") val tagVersion: Int = 0
)

@Entity(
    tableName = "photo_text",
    foreignKeys = [
        ForeignKey(
            entity = PhotoEntity::class,
            parentColumns = ["id"],
            childColumns = ["photoId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PhotoTextEntity(
    @PrimaryKey val photoId: Long,
    val text: String,          // 화면에 보여줄 원문
    val engine: String         // 어떤 OCR 엔진으로 읽었는지
)

/**
 * 전문 검색(Full-Text Search) 가상 테이블.
 * rowid = 사진 ID. tokens = SearchTokenizer로 만든 "검색용 조각"들.
 * LIKE '%신분증%'는 모든 행을 다 훑지만, FTS는 색인을 써서 사진이 많아도 빠름.
 */
@Fts4
@Entity(tableName = "photo_text_fts")
data class PhotoTextFts(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val tokens: String
)

@Entity(
    tableName = "photo_embeddings",
    primaryKeys = ["photoId", "model"],   // 사진 + 모델 조합이 하나의 행 → 한 사진에 모델별 벡터 여러 개
    foreignKeys = [
        ForeignKey(
            entity = PhotoEntity::class,
            parentColumns = ["id"],
            childColumns = ["photoId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
class PhotoEmbeddingEntity(
    val photoId: Long,
    val model: String,         // 어떤 모델로 만든 벡터인지 (모델이 다르면 비교 불가)
    val vector: ByteArray      // CLIP: float 512개 / SigLIP 2: 768개. 빈 배열 = 분석 실패(다시 시도 안 함)
)

/** Gemini Nano 태그 */
@Entity(
    tableName = "photo_tags",
    foreignKeys = [
        ForeignKey(
            entity = PhotoEntity::class,
            parentColumns = ["id"],
            childColumns = ["photoId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PhotoTagEntity(
    @PrimaryKey val photoId: Long,
    val category: String,      // "계약서", "영수증", "음식" …
    val keywords: String,      // 쉼표로 구분 "임대차,계약기간,서명"
    val summary: String,       // 한 문장 설명
    val model: String          // 어떤 모델로 만든 태그인지
)

// ── 쿼리 결과용 (테이블 아님) ─────────────────────────────

data class PhotoStamp(val id: Long, val dateModified: Long)

data class PhotoWithText(
    @androidx.room.Embedded val photo: PhotoEntity,
    val text: String
)

class EmbeddingRow(val photoId: Long, val vector: ByteArray)

data class IndexStats(
    val total: Int,
    val ocrDone: Int,
    val siglipDone: Int,
    val clipDone: Int
)

data class TaggedPhoto(val photoId: Long, val category: String)

data class TagStats(val total: Int, val tagged: Int)

data class TagMatch(val photoId: Long, val category: String, val keywords: String, val summary: String)

data class PendingTag(
    @androidx.room.Embedded val photo: PhotoEntity,
    val text: String?          // OCR 글자 (Gemini에게 참고로 줌)
)
