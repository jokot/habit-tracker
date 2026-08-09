package com.jktdeveloper.habitto.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.glance.color.ColorProvider
import java.util.concurrent.ConcurrentHashMap
import android.graphics.Color as AndroidColor
import androidx.glance.unit.ColorProvider as GlanceColorProvider

/**
 * Glance renders `ImageProvider`, not `ImageVector`, so the app's Material icons have to be
 * rasterised before a widget can show them. Every icon the app uses is a fill-only Material
 * vector on a 24×24 viewport, which makes this a path walk and a `drawPath`.
 *
 * ponytail: group transforms, strokes, clip paths and gradients are ignored — none of the
 * app's icons use them. An icon that did would render as an untransformed filled silhouette;
 * the fix would be applying `VectorGroup`'s translate/scale/rotation before recursing.
 */
private const val ICON_PX = 84

// Bitmaps are cached by icon name and reused across widgets and updates. They travel inside
// RemoteViews, which has a hard payload ceiling — a full grid that each rasterised its own
// copy would push against it, and most tiles share a handful of glyphs anyway.
private val iconCache = ConcurrentHashMap<String, Bitmap>()

/**
 * The icon as an opaque white silhouette. White so the caller can recolour it per theme with
 * Glance's `ColorFilter.tint`; a colour baked into the bitmap could not follow dark mode, and
 * one cached bitmap would become two.
 */
fun iconBitmap(vector: ImageVector): Bitmap = iconCache.getOrPut(vector.name) {
    val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.scale(ICON_PX / vector.viewportWidth, ICON_PX / vector.viewportHeight)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.FILL
    }
    vector.root.drawInto(canvas, paint)
    bitmap
}

private fun VectorGroup.drawInto(canvas: Canvas, paint: Paint) {
    forEach { node ->
        when (node) {
            is VectorPath -> canvas.drawPath(
                PathParser().addPathNodes(node.pathData).toPath().asAndroidPath(),
                paint,
            )
            is VectorGroup -> node.drawInto(canvas, paint)
        }
    }
}

/** Habits are green, wants are red. 142 is the app's default identity hue (`IdentityHue.DEFAULT`). */
const val HABIT_HUE = 142f
const val WANT_HUE = 6f

/** Glyph foreground, matching `HabitGlyph`'s `hsl(hue, 50%, 32%)` in light mode. */
fun glyphForeground(hue: Float): GlanceColorProvider = ColorProvider(
    day = Color.hsl(hue = hue, saturation = 0.50f, lightness = 0.32f),
    night = Color.hsl(hue = hue, saturation = 0.55f, lightness = 0.72f),
)

/** Glyph background, matching `HabitGlyph`'s `hsl(hue, 30%, 90%)` in light mode. */
fun glyphBackground(hue: Float): GlanceColorProvider = ColorProvider(
    day = Color.hsl(hue = hue, saturation = 0.30f, lightness = 0.90f),
    night = Color.hsl(hue = hue, saturation = 0.35f, lightness = 0.22f),
)
