package com.xlntsmmr.xlntgallery.ui.components

import android.content.Context
import android.widget.Toast

fun ShowToast(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(
        context,
        message,
        duration
    ).show()
}