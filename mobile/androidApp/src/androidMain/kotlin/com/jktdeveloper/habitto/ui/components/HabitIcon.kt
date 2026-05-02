package com.jktdeveloper.habitto.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector

fun habitIcon(name: String?): ImageVector {
    if (name == null) return Icons.Default.CheckCircle
    return when {
        name.contains("read", ignoreCase = true) -> Icons.AutoMirrored.Filled.MenuBook
        name.contains("water", ignoreCase = true) || name.contains("drink", ignoreCase = true) -> Icons.Default.WaterDrop
        name.contains("sleep", ignoreCase = true) || name.contains("bedtime", ignoreCase = true) -> Icons.Default.Bedtime
        name.contains("run", ignoreCase = true) || name.contains("walk", ignoreCase = true)
            || name.contains("cycl", ignoreCase = true) || name.contains("push", ignoreCase = true)
            || name.contains("squat", ignoreCase = true) || name.contains("plank", ignoreCase = true)
            || name.contains("stretch", ignoreCase = true) -> Icons.AutoMirrored.Filled.DirectionsRun
        name.contains("meditat", ignoreCase = true) || name.contains("pray", ignoreCase = true)
            || name.contains("journal", ignoreCase = true) -> Icons.Default.SelfImprovement
        name.contains("school", ignoreCase = true) || name.contains("course", ignoreCase = true)
            || name.contains("flash", ignoreCase = true) || name.contains("language", ignoreCase = true)
            || name.contains("learn", ignoreCase = true) -> Icons.Default.School
        name.contains("video", ignoreCase = true) || name.contains("watch", ignoreCase = true) -> Icons.Default.SmartDisplay
        name.contains("write", ignoreCase = true) || name.contains("blog", ignoreCase = true)
            || name.contains("draft", ignoreCase = true) -> Icons.Default.Forum
        name.contains("code", ignoreCase = true) || name.contains("build", ignoreCase = true)
            || name.contains("test", ignoreCase = true) || name.contains("refactor", ignoreCase = true) -> Icons.Default.Forum
        else -> Icons.Default.CheckCircle
    }
}
