package com.jktdeveloper.habitto.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ─── Streak heat ramp (locked from Phase 4 design — GitHub-style 5 levels) ───
val HeatL0 = Color(0xFFD0D7DE)
val HeatL1 = Color(0xFF9BE9A8)
val HeatL2 = Color(0xFF40C463)
val HeatL3 = Color(0xFF30A14E)
val HeatL4 = Color(0xFF216E39)
val HeatL0Dark = Color(0xFF161B22)
val HeatL1Dark = Color(0xFF0E4429)
val HeatL2Dark = Color(0xFF006D32)
val HeatL3Dark = Color(0xFF26A641)
val HeatL4Dark = Color(0xFF39D353)
val HeatCellBorder = Color(0xFFC8C8C8)
val HeatCellBorderDark = Color(0xFF30363D)

// ─── Streak status overrides (heat does NOT apply) ───
val StreakFrozen = Color(0xFF00838F)         // cyan 800 — light mode ice blue
val StreakFrozenDark = Color(0xFF4DD0E1)     // cyan 300 — dark mode ice blue (brighter per design)
val StreakFrozenBg = Color(0xFFE0F7FA)
val StreakFrozenBgDark = Color(0xFF0E3A47)
val StreakBroken = Color(0xFFC62828)
val StreakBrokenDark = Color(0xFFEF9A9A)
val StreakBrokenBg = Color(0xFFFFEBEE)
val StreakBrokenBgDark = Color(0xFF3A1414)
val StreakTodayOutline = Color(0xFF757575)
val StreakTodayOutlineDark = Color(0xFFB5B0A6)

// ─── Brand accent ───
val FlameOrange = Color(0xFFFF6F00)
val FlameOrangeDark = Color(0xFFFFA040)
val FlameSoft = Color(0xFFFFE6CC)
val FlameSoftDark = Color(0xFF4A2D0A)

// ─── Sync chip tokens ───
val SyncIdleBg = Color(0xFFECE7DF)
val SyncIdleFg = Color(0xFF5C5A55)
val SyncRunningBg = Color(0xFFFFF3C4)
val SyncRunningFg = Color(0xFF7A4F01)
val SyncSyncedBg = Color(0xFFD7E8FF)
val SyncSyncedFg = Color(0xFF0D47A1)
val SyncErrorBg = Color(0xFFFFD9D9)
val SyncErrorFg = Color(0xFFB71C1C)

val SyncIdleBgDark = Color(0xFF2C2823)
val SyncIdleFgDark = Color(0xFFB5B0A6)
val SyncRunningBgDark = Color(0xFF4A3000)
val SyncRunningFgDark = Color(0xFFFFE9A8)
val SyncSyncedBgDark = Color(0xFF0D3464)
val SyncSyncedFgDark = Color(0xFFD7E8FF)
val SyncErrorBgDark = Color(0xFF5A1A1A)
val SyncErrorFgDark = Color(0xFFFFD9D9)

// ─── Custom surface ramp (warm-calm direction) ───
val Surface1Light = Color(0xFFF4F1EC)
val Surface2Light = Color(0xFFECE7DF)
val Surface1Dark = Color(0xFF221F1A)
val Surface2Dark = Color(0xFF2C2823)

// ─── Material 3 — Light ───
internal val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF0F3D14),
    secondary = Color(0xFF1565C0),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7E8FF),
    onSecondaryContainer = Color(0xFF0D47A1),
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFD9D9),
    onErrorContainer = Color(0xFFB71C1C),
    background = Color(0xFFFAF8F4),
    onBackground = Color(0xFF1B1A17),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1A17),
    surfaceVariant = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFF5C5A55),
    outline = Color(0xFFBFB9AE),
    outlineVariant = Color(0xFFDCD7CD),
)

// ─── Material 3 — Dark ───
internal val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF003912),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFF64B5F6),
    onSecondary = Color(0xFF002B5E),
    secondaryContainer = Color(0xFF0D3464),
    onSecondaryContainer = Color(0xFFD7E8FF),
    error = Color(0xFFEF9A9A),
    onError = Color(0xFF5C0000),
    errorContainer = Color(0xFF5A1A1A),
    onErrorContainer = Color(0xFFFFD9D9),
    background = Color(0xFF121210),
    onBackground = Color(0xFFECE9E2),
    surface = Color(0xFF181816),
    onSurface = Color(0xFFECE9E2),
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFB5B0A6),
    outline = Color(0xFF6E6A60),
    outlineVariant = Color(0xFF3E3A33),
)
