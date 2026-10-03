package com.jktdeveloper.habitto.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jktdeveloper.habitto.ui.theme.Spacing

/** False while the sync that would fill the skeletons has failed: they stop moving. */
val LocalSkeletonAnimated = staticCompositionLocalOf { true }

/** Placeholder fill with a light band that sweeps left to right, once every 1200 ms. */
fun Modifier.shimmer(): Modifier = composed {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    if (!LocalSkeletonAnimated.current || animationsOff()) return@composed background(base)
    val progress by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1200, easing = LinearEasing)),
        label = "shimmer",
    )
    drawBehind {
        drawRect(base)
        val band = size.width
        val start = -band + progress * (size.width + band)
        drawRect(
            Brush.horizontalGradient(
                colors = listOf(Color.Transparent, highlight, Color.Transparent),
                startX = start,
                endX = start + band,
            ),
        )
    }
}

/** True when the user turned system animations off. */
@Composable
private fun animationsOff(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

@Composable
fun SkeletonBlock(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
) {
    Box(modifier.size(width, height).clip(shape).shimmer())
}

/** A full-width bar, for the habit card's progress line. */
@Composable
private fun SkeletonBar(height: Dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .shimmer(),
    )
}

/** A text line placeholder: a [blockHeight] block centred in a [lineHeight] slot. */
@Composable
private fun SkeletonLine(width: Dp, blockHeight: Dp, lineHeight: Dp) {
    Box(Modifier.height(lineHeight), contentAlignment = Alignment.CenterStart) {
        SkeletonBlock(width, blockHeight)
    }
}

/** Placeholder for [IdentityStrip]: the label and three chip-sized pills. */
@Composable
fun IdentityStripSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
            .semantics { contentDescription = "Loading identities" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "I AM",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp),
            letterSpacing = 0.3.sp,
        )
        listOf(84.dp, 72.dp, 92.dp).forEach { width ->
            SkeletonBlock(width, 30.dp, shape = RoundedCornerShape(999.dp))
        }
    }
}

/** Placeholder for a section's one-line subtitle, such as "2 of 3 goals met". */
@Composable
fun SectionSubtitleSkeleton(width: Dp) {
    Spacer(Modifier.height(Spacing.xs))
    SkeletonLine(width, blockHeight = 12.dp, lineHeight = 16.dp)
}

/** Placeholder for a habit card: glyph, name, subtitle and progress bar. */
@Composable
fun HabitCardSkeleton(titleWidth: Dp, subtitleWidth: Dp, modifier: Modifier = Modifier) {
    SkeletonCard(modifier.semantics { contentDescription = "Loading habit" }) {
        Column {
            SkeletonLine(titleWidth, blockHeight = 14.dp, lineHeight = 20.dp)
            Spacer(Modifier.height(Spacing.xs))
            SkeletonLine(subtitleWidth, blockHeight = 10.dp, lineHeight = 16.dp)
            Spacer(Modifier.height(Spacing.md))
            SkeletonBar(4.dp)
        }
    }
}

/** Placeholder for a want card: icon, name and subtitle. */
@Composable
fun WantCardSkeleton(titleWidth: Dp, subtitleWidth: Dp, modifier: Modifier = Modifier) {
    SkeletonCard(modifier.semantics { contentDescription = "Loading want" }) {
        Column {
            SkeletonLine(titleWidth, blockHeight = 14.dp, lineHeight = 20.dp)
            Spacer(Modifier.height(Spacing.xs))
            SkeletonLine(subtitleWidth, blockHeight = 10.dp, lineHeight = 16.dp)
        }
    }
}

/** The shell of a Today card, with the 44 dp icon block. Not clickable. */
@Composable
private fun SkeletonCard(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBlock(44.dp, 44.dp, shape = RoundedCornerShape(12.dp))
            Box(Modifier.weight(1f)) { content() }
        }
    }
}
