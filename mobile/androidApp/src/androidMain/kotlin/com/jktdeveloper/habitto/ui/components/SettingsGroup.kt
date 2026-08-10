package com.jktdeveloper.habitto.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jktdeveloper.habitto.ui.theme.Spacing

/**
 * Section label plus a bordered card holding its rows. Shared by the You hub,
 * Settings, and notification settings so all three read as one surface. Rows separate
 * themselves with a [androidx.compose.material3.HorizontalDivider] in `outlineVariant`.
 *
 * [prominent] promotes the label to a sentence-case section heading; the default small
 * uppercase style then reads as a subgroup under one. [dimmed] fades the card — not its
 * label — for groups whose controls are inert.
 */
@Composable
fun SettingsGroup(
    title: String,
    prominent: Boolean = false,
    dimmed: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.padding(
            start = Spacing.xl,
            end = Spacing.xl,
            bottom = Spacing.xxl,
        ),
    ) {
        if (prominent) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = Spacing.sm, bottom = Spacing.md),
            )
        } else {
            Text(
                text = title.uppercase(),
                fontSize = 11.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.sm, bottom = Spacing.md),
            )
        }
        Surface(
            modifier = Modifier.alpha(if (dimmed) 0.55f else 1f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(content = content)
        }
    }
}
