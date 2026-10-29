package com.xlntsmmr.xlntgallery.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// data/PhotoRepository.kt
class PhotoRepository(private val context: Context) {

    // suspend + withContext(IO): 메인 스레드를 막지 않고 디스크 조회
    suspend fun loadPhotos(): List<Photo> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Photo>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN
        )
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC"   // 최신순
        )?.use { cursor ->                                  // use: 끝나면 cursor 자동 close
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                result += Photo(
                    id = id,
                    uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                    name = cursor.getString(nameCol),
                    dateTaken = cursor.getLong(dateCol)
                )
            }
        }
        result
    }
}