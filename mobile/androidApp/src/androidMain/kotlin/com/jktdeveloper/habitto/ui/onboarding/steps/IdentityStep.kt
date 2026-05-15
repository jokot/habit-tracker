package com.jktdeveloper.habitto.ui.onboarding.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.habittracker.domain.model.Identity
import com.jktdeveloper.habitto.ui.components.IdentityAvatar

@Composable
fun IdentityStep(
    identities: List<Identity>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            InfoCard()
        }
        items(identities, key = { it.id }) { identity ->
            IdentityCard(
                identity = identity,
                selected = identity.id in selectedIds,
                onClick = { onToggle(identity.id) },
            )
        }
    }
}

@Composable
private fun InfoCard() {
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) Color(0xFF0E3A47) else Color(0xFFE0F7FA)
    val fg = if (isDark) Color(0xFF4DD0E1) else Color(0xFF00838F)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bg,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            val annotated = buildAnnotatedString {
                append("Most people pick ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("1–3") }
                append(" to start. You can add more later.")
            }
            Text(
                text = annotated,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private data class IdentityCardColors(
    val bg: Color,
    val border: Color,
    val title: Color,
    val desc: Color,
    val badge: Color,
)

@Composable
private fun cardColors(hue: Float, selected: Boolean): IdentityCardColors {
    if (!selected) {
        return IdentityCardColors(
            bg = MaterialTheme.colorScheme.surface,
            border = MaterialTheme.colorScheme.outlineVariant,
            title = MaterialTheme.colorScheme.onSurface,
            desc = MaterialTheme.colorScheme.onSurfaceVariant,
            badge = Color.Transparent,
        )
    }
    return IdentityCardColors(
        bg = Color.hsl(hue = hue, saturation = 0.30f, lightness = 0.92f),
        border = Color.hsl(hue = hue, saturation = 0.70f, lightness = 0.50f),
        title = Color.hsl(hue = hue, saturation = 0.55f, lightness = 0.18f),
        desc = Color.hsl(hue = hue, saturation = 0.40f, lightness = 0.30f),
        badge = Color.hsl(hue = hue, saturation = 0.70f, lightness = 0.50f),
    )
}

@Composable
private fun IdentityCard(
    identity: Identity,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = cardColors(hue = identity.hue.toFloat(), selected = selected)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.bg,
        border = BorderStroke(width = 2.dp, color = colors.border),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(colors.badge),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Column {
                IdentityAvatar(
                    iconName = identity.icon,
                    hue = identity.hue.toFloat(),
                    size = 36.dp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    identity.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.title,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    identity.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.desc,
                )
            }
        }
    }
}
