package com.xlntsmmr.xlntgallery.data

import android.net.Uri

// 상세 화면용 사진 정보 (MediaStore + EXIF 메타데이터)
data class PhotoDetail(
    val id: Long,
    val uri: Uri,
    val name: String,
    val dateTaken: Long,       // 밀리초
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val latitude: Double? = null,   // EXIF GPS. 없으면 null
    val longitude: Double? = null,
    val camera: String? = null,     // 예: "samsung SM-S948N"
    val address: String? = null     // 좌표 → 주소 변환 결과 (나중에 채워짐)
)
