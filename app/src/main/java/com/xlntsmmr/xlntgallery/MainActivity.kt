package com.xlntsmmr.xlntgallery

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.RoundedCorner
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.xlntsmmr.xlntgallery.data.gemini.GeminiTaggingRunner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.xlntsmmr.xlntgallery.ui.components.display_bottom_left_radius
import com.xlntsmmr.xlntgallery.ui.components.display_bottom_right_radius
import com.xlntsmmr.xlntgallery.ui.components.display_top_left_radius
import com.xlntsmmr.xlntgallery.ui.components.display_top_right_radius
import com.xlntsmmr.xlntgallery.ui.navigation.AppNavigation
import com.xlntsmmr.xlntgallery.ui.theme.XLNTGalleryTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // 상태바 시계, 배터리, 와이파이 아이콘을 검은색으로
        WindowCompat.getInsetsController(
            window,
            window.decorView
        ).isAppearanceLightStatusBars = true

        setContent {
            XLNTGalleryTheme {
                AppNavigation()
            }
        }

        printScreenCornerRadius()

        // Gemini Nano 사진 태깅: 앱이 화면에 보이는(RESUMED) 동안만 실행.
        // 화면을 벗어나면 repeatOnLifecycle이 코루틴을 자동 취소 → 다시 돌아오면 이어서 실행
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    GeminiTaggingRunner.run(applicationContext)
                    delay(30_000)   // 새로 OCR이 끝난 사진이 생겼을 수 있으니 30초 뒤 다시 확인
                }
            }
        }
    }

    private fun printScreenCornerRadius() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Log.d("ScreenCorner", "Android 12 미만: 조회 불가")
            return
        }

        window.decorView.post {

            val insets = window.decorView.rootWindowInsets

            display_top_left_radius =
                insets?.getRoundedCorner(
                    RoundedCorner.POSITION_TOP_LEFT
                )?.radius ?: 0

            display_top_right_radius =
                insets?.getRoundedCorner(
                    RoundedCorner.POSITION_TOP_RIGHT
                )?.radius ?: 0

            display_bottom_left_radius =
                insets?.getRoundedCorner(
                    RoundedCorner.POSITION_BOTTOM_LEFT
                )?.radius ?: 0

            display_bottom_right_radius =
                insets?.getRoundedCorner(
                    RoundedCorner.POSITION_BOTTOM_RIGHT
                )?.radius ?: 0
        }
    }

}
