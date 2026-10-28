package com.xlntsmmr.xlntgallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xlntsmmr.xlntgallery.ui.navigation.AppNavigation
import com.xlntsmmr.xlntgallery.ui.theme.XlntGalleryTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            XlntGalleryTheme {
                AppNavigation()
            }
        }
    }
}