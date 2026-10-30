package com.xlntsmmr.xlntgallery.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

object BitmapLoader {

    /**
     * 사진을 "짧은 변이 minShortSide 이상"이 되는 선에서 최대한 작게 읽고(메모리 절약),
     * EXIF 회전을 반영해 똑바로 세움.
     */
    fun loadUpright(context: Context, uri: Uri, minShortSide: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // inSampleSize: 2의 거듭제곱 배로 줄여서 읽기 (예: 4 → 가로세로 1/4)
        var sampleSize = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= minShortSide) sampleSize *= 2

        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } ?: return null

        val rotation = try {
            resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        } catch (e: Exception) {
            0
        }
        if (rotation == 0) return decoded
        val rotated = Bitmap.createBitmap(
            decoded, 0, 0, decoded.width, decoded.height,
            Matrix().apply { postRotate(rotation.toFloat()) }, true
        )
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }
}
