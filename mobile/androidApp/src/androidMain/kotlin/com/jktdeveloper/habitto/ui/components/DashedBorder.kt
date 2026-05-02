package com.jktdeveloper.habitto.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.dashedBorder(
    color: Color,
    shape: Shape = RoundedCornerShape(0.dp),
    strokeWidth: Dp = 2.dp,
    dashLength: Dp = 6.dp,
    gapLength: Dp = 4.dp,
): Modifier = composed {
    drawWithContent {
        drawContent()
        val strokeWidthPx = strokeWidth.toPx()
        val dashPx = dashLength.toPx()
        val gapPx = gapLength.toPx()
        val outline = shape.createOutline(size, layoutDirection, this)
        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, gapPx))
        when (outline) {
            is Outline.Generic -> drawPath(
                outline.path,
                color,
                style = Stroke(strokeWidthPx, pathEffect = pathEffect),
            )
            is Outline.Rounded -> {
                val path = androidx.compose.ui.graphics.Path().apply {
                    addRoundRect(outline.roundRect)
                }
                drawPath(path, color, style = Stroke(strokeWidthPx, pathEffect = pathEffect))
            }
            is Outline.Rectangle -> drawRect(
                color,
                topLeft = androidx.compose.ui.geometry.Offset.Zero,
                size = size,
                style = Stroke(strokeWidthPx, pathEffect = pathEffect),
            )
        }
    }
}
