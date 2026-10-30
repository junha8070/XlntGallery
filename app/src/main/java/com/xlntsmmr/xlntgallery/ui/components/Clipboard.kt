package com.xlntsmmr.xlntgallery.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle

/**
 * 클립보드에 복사.
 * isSensitive = true 이면 Android 13+ 키보드·클립보드 미리보기에서 내용이 가려짐.
 */
fun copyToClipboard(
    context: Context,
    label: String,
    text: String,
    isSensitive: Boolean = false
) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(label, text)

    if (isSensitive) {
        clip.description.extras = PersistableBundle().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            } else {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
    }
    clipboard.setPrimaryClip(clip)

    // Android 13+는 시스템이 "복사됨" 알림을 직접 띄우므로 중복 토스트를 띄우지 않음
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        showToast(context, "$label 복사됨")
    }
}
