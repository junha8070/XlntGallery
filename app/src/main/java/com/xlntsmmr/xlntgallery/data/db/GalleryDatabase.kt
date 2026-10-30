package com.xlntsmmr.xlntgallery.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PhotoEntity::class,
        PhotoTextEntity::class,
        PhotoTextFts::class,
        PhotoEmbeddingEntity::class,
        PhotoTagEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class GalleryDatabase : RoomDatabase() {

    abstract fun galleryDao(): GalleryDao

    companion object {
        @Volatile private var instance: GalleryDatabase? = null

        /**
         * 버전 1 → 2: Gemini Nano 태그 추가.
         * 기존 테이블·데이터(2만 장 OCR·AI 분석 결과)는 그대로 두고 컬럼·테이블만 추가.
         * SQL은 Room이 엔티티로부터 기대하는 구조와 정확히 같아야 함 (다르면 앱 시작 시 오류)
         */
        /**
         * 버전 2 → 3: 사진 1장에 모델별 벡터를 따로 저장 (SigLIP 2 + CLIP 동시 사용).
         * SQLite는 기본키를 바로 바꿀 수 없어서: 새 테이블 생성 → 데이터 복사 → 옛 테이블 삭제 → 이름 변경
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `photo_embeddings_new` (" +
                        "`photoId` INTEGER NOT NULL, `model` TEXT NOT NULL, `vector` BLOB NOT NULL, " +
                        "PRIMARY KEY(`photoId`, `model`), " +
                        "FOREIGN KEY(`photoId`) REFERENCES `photos`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "INSERT INTO `photo_embeddings_new` (`photoId`, `model`, `vector`) " +
                        "SELECT `photoId`, `model`, `vector` FROM `photo_embeddings`"
                )
                db.execSQL("DROP TABLE `photo_embeddings`")
                db.execSQL("ALTER TABLE `photo_embeddings_new` RENAME TO `photo_embeddings`")
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `photos` ADD COLUMN `tagVersion` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `photo_tags` (" +
                        "`photoId` INTEGER NOT NULL, `category` TEXT NOT NULL, `keywords` TEXT NOT NULL, " +
                        "`summary` TEXT NOT NULL, `model` TEXT NOT NULL, PRIMARY KEY(`photoId`), " +
                        "FOREIGN KEY(`photoId`) REFERENCES `photos`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
            }
        }

        // DB 연결은 무거우므로 앱 전체에서 하나만 (싱글턴)
        fun get(context: Context): GalleryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    GalleryDatabase::class.java,
                    "gallery.db"
                )
                    // 구조 변경 시 기존 분석 결과를 보존하며 바꾸는 방법
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    // 마이그레이션이 없는 경우만 최후 수단으로 DB 재생성
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
