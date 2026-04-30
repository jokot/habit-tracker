package com.jktdeveloper.habitto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Circular tinted icon container — used on habit cards, want cards, and onboarding steps.
 * Tint comes from the identity's hue (degrees on color wheel).
 *
 * Mirrors the JSX HabitGlyph from /tmp/habitto-design/habitto/project/shared.jsx.
 * oklch(0.92 0.04 hue) bg  → HSL approx: hsl(hue, 30%, 90%)
 * oklch(0.35 0.10 hue) fg  → HSL approx: hsl(hue, 50%, 32%)
 */
@Composable
fun HabitGlyph(
    icon: ImageVector,
    hue: Float = 142f,
    size: Dp = 44.dp,
    contentDescription: String? = null,
) {
    val bg = Color.hsl(hue = hue, saturation = 0.30f, lightness = 0.90f)
    val fg = Color.hsl(hue = hue, saturation = 0.50f, lightness = 0.32f)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = fg,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/** Map identity name → Material icon. Identity seed stores emoji in `icon` field;
 *  this resolver picks an appropriate vector icon for in-app rendering. */
fun identityIcon(name: String): ImageVector = when (name.lowercase()) {
    "reader" -> Icons.AutoMirrored.Filled.MenuBook
    "builder", "maker" -> Icons.Default.Build
    "athlete" -> Icons.AutoMirrored.Filled.DirectionsRun
    "writer" -> Icons.Default.Edit
    "learner" -> Icons.Default.School
    "minimalist" -> Icons.Default.Spa
    "devotee", "calm" -> Icons.Default.SelfImprovement
    "health-conscious", "healthy" -> Icons.Default.FitnessCenter
    "sleeper" -> Icons.Default.Bedtime
    "parent" -> Icons.Default.FamilyRestroom
    else -> Icons.Default.Person
}

/** Identity-hue mapping. Matches IDENTITY_HUE in shared.jsx. */
object IdentityHue {
    const val HEALTHY = 142f
    const val READER = 28f
    const val MAKER = 222f
    const val CALM = 268f
    const val LEARNER = 188f
    const val ATHLETE = 8f
    const val SLEEPER = 252f
    const val PARENT = 320f
    const val DEFAULT = 142f

    fun forIdentityId(id: String?): Float = when (id) {
        "healthy" -> HEALTHY
        "reader" -> READER
        "maker" -> MAKER
        "calm" -> CALM
        "learner" -> LEARNER
        "athlete" -> ATHLETE
        "sleeper" -> SLEEPER
        "parent" -> PARENT
        else -> DEFAULT
    }
}
