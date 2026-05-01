package com.jktdeveloper.habitto.ui.streak

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap

/** Diagonal X overlay for FROZEN cells (canvas tokens.css spec). */
@Composable
fun FrozenOverlay(color: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pad = minOf(w, h) * 0.15f
        val stroke = minOf(w, h) * 0.12f
        drawLine(
            color = color,
            start = Offset(pad, pad),
            end = Offset(w - pad, h - pad),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(w - pad, pad),
            end = Offset(pad, h - pad),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** Centered horizontal dash overlay for BROKEN cells. */
@Composable
fun BrokenOverlay(color: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val length = w * 0.5f
        val stroke = (h * 0.18f).coerceAtLeast(1.5f)
        drawLine(
            color = color,
            start = Offset((w - length) / 2f, h / 2f),
            end = Offset((w + length) / 2f, h / 2f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
