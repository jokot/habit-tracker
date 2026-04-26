package com.jktdeveloper.habitto.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun HabitTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HabitTypography,
        content = content,
    )
}

@Composable
fun streakCompleteColor(): Color =
    if (isSystemInDarkTheme()) StreakCompleteDark else StreakComplete

@Composable
fun streakFrozenColor(): Color =
    if (isSystemInDarkTheme()) StreakFrozenDark else StreakFrozen

@Composable
fun streakBrokenColor(): Color =
    if (isSystemInDarkTheme()) StreakBrokenDark else StreakBroken

@Composable
fun streakEmptyColor(): Color =
    if (isSystemInDarkTheme()) StreakEmptyDark else StreakEmpty

@Composable
fun streakTodayOutlineColor(): Color = StreakTodayOutline
