package com.xlntsmmr.xlntgallery.data

import android.net.Uri

// MediaStore에서 읽어온 사진 1장의 정보 (이미지 자체가 아니라 "정보"만)
data class Photo(
    val id: Long,          // MediaStore의 _ID. 고유값 → LazyGrid의 key로 사용
    val uri: Uri,          // content://media/external/images/media/123 형태. Coil에 이걸 넘김
    val name: String,      // 파일명 (IMG_20240812.jpg). "파일 이름" 필터 검색에 사용
    val dateTaken: Long    // 촬영 시각 (1970년부터의 밀리초). 화면에 보여줄 땐 "24.08.12"로 변환
)