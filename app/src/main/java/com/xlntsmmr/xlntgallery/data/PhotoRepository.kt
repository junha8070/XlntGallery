package com.xlntsmmr.xlntgallery.data

import android.content.ContentUris
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

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

    // ── 상세 화면용 ─────────────────────────────────────

    /** 사진 1장의 상세 정보 (MediaStore 기본 정보 + EXIF) */
    suspend fun loadPhotoDetail(id: Long): PhotoDetail? = withContext(Dispatchers.IO) {
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        val projection = arrayOf(
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,   // 초 단위
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.ORIENTATION   // 0/90/180/270
        )

        var orientation = 0
        val base = context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            val dateTaken = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN))
            val dateAdded = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
            orientation = c.getInt(c.getColumnIndexOrThrow(MediaStore.Images.Media.ORIENTATION))
            PhotoDetail(
                id = id,
                uri = uri,
                name = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)) ?: "",
                // 스크린샷 등은 DATE_TAKEN이 0인 경우가 있어서 추가된 시각으로 대체
                dateTaken = if (dateTaken > 0) dateTaken else dateAdded * 1000,
                sizeBytes = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)),
                width = c.getInt(c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)),
                height = c.getInt(c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT))
            )
        } ?: return@withContext null

        val exif = readExif(uri)

        // WIDTH/HEIGHT는 "회전 전" 원본 크기. 세로로 찍은 사진은 4000×2252처럼 가로로 나옴
        // → 90°/270° 회전된 사진이면 가로·세로를 바꿔서 "보이는 크기"로 맞춤
        val rotation = if (orientation != 0) orientation else (exif?.rotationDegrees ?: 0)
        val upright = if (rotation % 180 != 0) {
            base.copy(width = base.height, height = base.width)
        } else base

        if (exif == null) return@withContext upright
        val latLong = exif.latLong   // [위도, 경도] 또는 null
        val camera = listOfNotNull(
            exif.getAttribute(ExifInterface.TAG_MAKE),
            exif.getAttribute(ExifInterface.TAG_MODEL)
        ).joinToString(" ").trim().ifBlank { null }

        upright.copy(
            latitude = latLong?.get(0),
            longitude = latLong?.get(1),
            camera = camera
        )
    }

    /**
     * EXIF 읽기.
     * Android 10+에서는 개인정보 보호를 위해 위치(GPS) 정보가 지워진 채로 열림.
     * ACCESS_MEDIA_LOCATION 권한이 있을 때 setRequireOriginal()로 열어야 원본 위치가 보임.
     */
    private fun readExif(uri: Uri): ExifInterface? {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                resolver.openInputStream(MediaStore.setRequireOriginal(uri))?.use { return ExifInterface(it) }
            } catch (e: SecurityException) {
                // 위치 권한 없음 → 아래에서 위치 없는 EXIF라도 읽음
            } catch (e: Exception) {
                return null
            }
        }
        return try {
            resolver.openInputStream(uri)?.use { ExifInterface(it) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 좌표 → 주소 ("서울특별시 강남구 ...").
     * 주의: Geocoder는 기기 안이 아니라 Google 위치 서비스(네트워크)를 사용함.
     * 인터넷이 없거나 서비스가 없는 기기면 null.
     */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.KOREA)

        val address: Address? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+: 콜백 방식 → suspend로 감쌈
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            cont.resume(addresses.firstOrNull())
                        }
                        override fun onError(errorMessage: String?) {
                            cont.resume(null)
                        }
                    })
                }
            } else {
                // Android 12 이하: 동기(블로킹) 방식 → IO 스레드에서
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
                }
            }
        } catch (e: Exception) {
            null
        }

        return address?.getAddressLine(0)?.removePrefix("대한민국 ")
    }
}
