package com.anitail.music.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun CircularProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = Color.LightGray.copy(alpha = 0.3f),
    strokeCap: StrokeCap = StrokeCap.Round
) {
    val stroke = with(LocalDensity.current) {
        Stroke(width = strokeWidth.toPx(), cap = strokeCap)
    }

    Canvas(modifier) {
        val diameter = size.minDimension
        val radius = diameter / 2
        val centerX = size.width / 2
        val centerY = size.height / 2
        val startAngle = -90f  // Start from top center
        val sweepAngle = 360 * progress

        // Background circle
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(centerX - radius, centerY - radius),
            size = Size(diameter, diameter),
            style = stroke
        )

        // Progress arc
        drawArc(
            color = color,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(centerX - radius, centerY - radius),
            size = Size(diameter, diameter),
            style = stroke
        )
    }
}
