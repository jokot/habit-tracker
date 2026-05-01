package com.jktdeveloper.habitto.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habittracker.domain.model.Identity

@Composable
fun IdentityHubCard(identities: List<Identity>, onClick: () -> Unit) {
    if (identities.isEmpty()) return
    val firstHue = IdentityHue.forIdentityId(identities.first().name.lowercase())
    val isDark = isSystemInDarkTheme()
    val gradStart = if (isDark) Color.hsl(firstHue, 0.30f, 0.18f) else Color.hsl(firstHue, 0.30f, 0.92f)
    val gradEnd = MaterialTheme.colorScheme.surface

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(colorStops = arrayOf(0f to gradStart, 0.8f to gradEnd)),
            ),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "IDENTITIES · ${identities.size}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.4.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                StackedAvatars(identities)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = buildIAmCopy(identities),
                    style = MaterialTheme.typography.titleMedium.copy(lineHeight = 22.sp),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Tap to manage",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StackedAvatars(identities: List<Identity>) {
    val visible = identities.take(4)
    Box(modifier = Modifier.height(40.dp)) {
        visible.forEachIndexed { i, identity ->
            Box(
                modifier = Modifier
                    .offset(x = (16 * i).dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
            ) {
                HabitGlyph(
                    icon = identityIcon(identity.name),
                    hue = IdentityHue.forIdentityId(identity.name.lowercase()),
                    size = 40.dp,
                )
            }
        }
    }
}

@Composable
private fun buildIAmCopy(identities: List<Identity>) = buildAnnotatedString {
    append("I am a ")
    identities.forEachIndexed { i, identity ->
        val hue = IdentityHue.forIdentityId(identity.name.lowercase())
        val tint = Color.hsl(hue, 0.50f, 0.32f)
        if (i > 0) {
            append(if (i == identities.lastIndex) " & a " else ", a ")
        }
        withStyle(SpanStyle(color = tint)) { append(identity.name.lowercase()) }
    }
    append(".")
}
