
package com.xlntsmmr.xlntgallery.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val MainBlue = Color(0xFF247BFA)

private val AppColorScheme = lightColorScheme(
    primary = MainBlue,
    onPrimary = Color.White,
    secondary = MainBlue,
    surface = Color.White,
    background = Color(0xFFF7F9FC)
)

@Composable
fun XLNTGalleryTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppColorScheme
    ) {
        CompositionLocalProvider(
            LocalRippleConfiguration provides RippleConfiguration(
                color = MainBlue
            )
        ) {
            content()
        }
    }
}
