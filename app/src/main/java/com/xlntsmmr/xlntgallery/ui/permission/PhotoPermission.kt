package com.xlntsmmr.xlntgallery.ui.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 사진 권한은 Android 버전마다 이름이 다르다.
 *  - Android 12 이하 (API 32-)  : READ_EXTERNAL_STORAGE
 *  - Android 13      (API 33)   : READ_MEDIA_IMAGES
 *  - Android 14 이상 (API 34+)  : READ_MEDIA_IMAGES + READ_MEDIA_VISUAL_USER_SELECTED
 *    (14부터는 사용자가 "일부 사진만 허용"을 고를 수 있음)
 */
fun photoPermissions(): Array<String> {
    val permissions = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES
        )
        else -> listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }
    // Android 10+: 사진 속 촬영 위치(GPS)를 읽으려면 별도 권한 필요
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        (permissions + Manifest.permission.ACCESS_MEDIA_LOCATION).toTypedArray()
    } else {
        permissions.toTypedArray()
    }
}

/** 사진을 하나라도 읽을 수 있는 상태인지 (전체 허용 or 일부 허용) */
fun hasPhotoPermission(context: Context): Boolean {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            granted(Manifest.permission.READ_MEDIA_IMAGES) ||
                granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)   // 일부만 허용
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            granted(Manifest.permission.READ_MEDIA_IMAGES)
        else ->
            granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

/** 사진 속 촬영 위치를 읽을 수 있는지 (Android 9 이하는 권한 개념이 없어서 항상 true) */
fun hasMediaLocationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
