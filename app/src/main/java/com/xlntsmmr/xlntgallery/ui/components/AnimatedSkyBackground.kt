package com.xlntsmmr.xlntgallery.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.draw.clipToBounds
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnimatedSkyBackground(
    modifier: Modifier = Modifier
) {
    // 1. 무한 반복 애니메이션
    val transition = rememberInfiniteTransition(
        label = "SkyAnimation"
    )

    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 12000,
                easing = LinearEasing
            )
        ),
        label = "SkyMovement"
    )

    // 2. 배경 그리기
    Canvas(
        modifier = modifier.clipToBounds()
    ) {
        val w = size.width
        val h = size.height

        // 기본 하늘색 → 흰색 그라데이션
        drawRect(
            brush = Brush.verticalGradient(
                0.0f to Color(0xFFD5ECFF),
                0.5f to Color(0xFFE8F5FF),
                1.0f to Color.White
            )
        )

        // 3. 첫 번째 블롭 - 파란색
        drawSoftBlob(
            center = Offset(
                x = w * (0.20f + sin(phase) * 0.12f),
                y = h * (0.20f + cos(phase) * 0.08f)
            ),
            radius = w * 0.70f,
            color = Color(0x8874B9FF)
        )

        // 4. 두 번째 블롭 - 연보라색
        drawSoftBlob(
            center = Offset(
                x = w * (0.85f + cos(phase) * 0.10f),
                y = h * (0.40f + sin(phase) * 0.12f)
            ),
            radius = w * 0.65f,
            color = Color(0x668DAEFF)
        )

        // 5. 세 번째 블롭 - 민트색
        drawSoftBlob(
            center = Offset(
                x = w * (0.50f + sin(phase + 1.5f) * 0.14f),
                y = h * (0.65f + cos(phase + 1.5f) * 0.08f)
            ),
            radius = w * 0.60f,
            color = Color(0x55A1E8E3)
        )
    }
}

// 부드러운 원형 그라데이션을 그리는 함수
private fun DrawScope.drawSoftBlob(
    center: Offset,
    radius: Float,
    color: Color
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color,
                color.copy(alpha = color.alpha * 0.4f),
                Color.Transparent
            ),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}
