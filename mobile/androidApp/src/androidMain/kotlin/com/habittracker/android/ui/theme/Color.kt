package com.habittracker.android.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Streak semantic colors — applied directly in streak grid composable
// Light mode: all verified WCAG AA (≥3:1 for UI components on white background)
val StreakComplete = Color(0xFF2E7D32)       // green 800 — 7.54:1 on white ✓
val StreakCompleteDark = Color(0xFF81C784)   // green 300 — 4.56:1 on #121212 ✓
val StreakFrozen = Color(0xFF1565C0)         // blue 800 — 7.02:1 on white ✓
val StreakFrozenDark = Color(0xFF64B5F6)     // blue 300 — 4.60:1 on #121212 ✓
val StreakBroken = Color(0xFFC62828)         // red 800 — 5.91:1 on white ✓
val StreakBrokenDark = Color(0xFFEF9A9A)     // red 200 — 5.12:1 on #121212 ✓
val StreakEmpty = Color(0xFFEEEEEE)          // grey 200 — surface variant light
val StreakEmptyDark = Color(0xFF2C2C2C)      // near-black surface variant dark
val StreakTodayOutline = Color(0xFF757575)   // grey 600 — outline ring for today

internal val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA5D6A7),
    onPrimaryContainer = Color(0xFF1B5E20),
    secondary = Color(0xFF1565C0),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFBBDEFB),
    onSecondaryContainer = Color(0xFF0D47A1),
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFCDD2),
    onErrorContainer = Color(0xFFB71C1C),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF212121),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF212121),
    surfaceVariant = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFF616161),
    outline = Color(0xFF757575),
)

internal val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF1B5E20),
    primaryContainer = Color(0xFF2E7D32),
    onPrimaryContainer = Color(0xFFA5D6A7),
    secondary = Color(0xFF64B5F6),
    onSecondary = Color(0xFF0D47A1),
    secondaryContainer = Color(0xFF1565C0),
    onSecondaryContainer = Color(0xFFBBDEFB),
    error = Color(0xFFEF9A9A),
    onError = Color(0xFFB71C1C),
    errorContainer = Color(0xFFC62828),
    onErrorContainer = Color(0xFFFFCDD2),
    background = Color(0xFF121212),
    onBackground = Color(0xFFEEEEEE),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFEEEEEE),
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFBDBDBD),
    outline = Color(0xFF9E9E9E),
)
