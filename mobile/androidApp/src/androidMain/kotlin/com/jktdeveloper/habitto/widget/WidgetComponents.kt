package com.jktdeveloper.habitto.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as GlanceColorProvider
import com.habittracker.domain.model.StreakDay
import com.habittracker.domain.model.StreakDayState
import com.habittracker.domain.usecase.WidgetHabitItem
import com.habittracker.domain.usecase.WidgetWantItem
import com.jktdeveloper.habitto.MainActivity
import com.jktdeveloper.habitto.ui.theme.FlameOrange
import com.jktdeveloper.habitto.ui.theme.FlameOrangeDark
import com.jktdeveloper.habitto.ui.theme.HeatL0
import com.jktdeveloper.habitto.ui.theme.HeatL0Dark
import com.jktdeveloper.habitto.ui.theme.HeatL1
import com.jktdeveloper.habitto.ui.theme.HeatL1Dark
import com.jktdeveloper.habitto.ui.theme.HeatL2
import com.jktdeveloper.habitto.ui.theme.HeatL2Dark
import com.jktdeveloper.habitto.ui.theme.HeatL3
import com.jktdeveloper.habitto.ui.theme.HeatL3Dark
import com.jktdeveloper.habitto.ui.theme.HeatL4
import com.jktdeveloper.habitto.ui.theme.HeatL4Dark
import com.jktdeveloper.habitto.ui.theme.StreakBroken
import com.jktdeveloper.habitto.ui.theme.StreakBrokenDark
import com.jktdeveloper.habitto.ui.theme.StreakFrozen
import com.jktdeveloper.habitto.ui.theme.StreakFrozenDark

/**
 * The card every widget draws inside.
 *
 * ponytail: cornerRadius is API 31+ and a silent no-op on 26–30, where the widget
 * renders square. Accepted — most launchers mask widget corners themselves. Upgrade
 * path if it looks wrong on a real API-28 device: a shape drawable in res/drawable
 * plus res/drawable-night, applied via background(ImageProvider(...)).
 */
@Composable
fun WidgetSurface(
    modifier: GlanceModifier = GlanceModifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .cornerRadius(24.dp)
            .padding(SURFACE_PADDING),
        horizontalAlignment = horizontalAlignment,
        verticalAlignment = verticalAlignment,
    ) {
        content()
    }
}

/** Padding [WidgetSurface] applies on every edge. Widgets subtract it when sizing their content. */
val SURFACE_PADDING = 12.dp

/** Shown when a widget has nothing to render. Tapping it opens the app. */
@Composable
fun WidgetEmpty(message: String) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            message,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
        )
    }
}

/**
 * Balance numeral plus streak flame. `compact` drops to a single small line for the
 * quick-log widgets, which need the vertical space for rows.
 *
 * The flame is an emoji rendered as text — Glance cannot draw the app's ImageVector
 * icons, and text sidesteps that entirely.
 */
@Composable
fun BalanceHeader(balance: Int, currentStreak: Int, compact: Boolean = false) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            balance.toString(),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = if (compact) 20.sp else 40.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            " pts",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = if (compact) 12.sp else 14.sp,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        if (currentStreak > 0) {
            Text(
                "🔥 $currentStreak",
                style = TextStyle(
                    color = ColorProvider(day = FlameOrange, night = FlameOrangeDark),
                    fontSize = if (compact) 12.sp else 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

/** Deep link to the want-timer screen; intent-filter registered in AndroidManifest.xml:36. */
fun wantTimerIntent(context: Context, activityId: String): Intent =
    Intent(
        Intent.ACTION_VIEW,
        Uri.parse("com.jktdeveloper.habitto://want-timer/$activityId"),
    ).apply {
        setPackage(context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

/** One habit row. Tapping it logs a single point. 48dp tall to meet the tap-target floor. */
@Composable
fun HabitRow(item: WidgetHabitItem) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(
                actionRunCallback<LogHabitAction>(
                    actionParametersOf(
                        LogHabitAction.habitIdKey to item.habitId,
                        LogHabitAction.quantityKey to item.logQuantity,
                    ),
                ),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.name,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            item.progressText,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = if (item.isGoalMet) FontWeight.Bold else FontWeight.Normal,
            ),
        )
        Text(
            "  +1",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        )
    }
}

/**
 * One want. A minute-unit want opens the want-timer screen — the widget has no
 * sensible way to invent a duration, and an unattended drain started by a mis-tap is
 * not recoverable from the home screen.
 *
 * Disabled state is a dimmer color, not opacity: Glance has no opacity modifier on a
 * Row. An unaffordable want still taps through to the app rather than going inert.
 */
@Composable
fun WantRow(item: WidgetWantItem) {
    val context = LocalContext.current
    val action = when {
        !item.enabled -> actionStartActivity<MainActivity>()
        item.isTimed -> actionStartActivity(wantTimerIntent(context, item.activityId))
        else -> actionRunCallback<LogWantAction>(
            actionParametersOf(LogWantAction.activityIdKey to item.activityId),
        )
    }
    val nameColor =
        if (item.enabled) GlanceTheme.colors.onSurface else GlanceTheme.colors.onSurfaceVariant
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(action),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.name,
            maxLines = 1,
            style = TextStyle(color = nameColor, fontSize = 14.sp),
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            item.rateText,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        )
        Text(
            if (item.enabled) "  −1 pt" else "  no pts",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        )
    }
}

/**
 * One icon-sized tile carrying the same glyph the Home list shows for the item.
 * [iconBitmap] rasterises it — Glance draws bitmaps, not ImageVectors — and the tint
 * follows the theme. [hue] is [HABIT_HUE] or [WANT_HUE]; the circle mirrors `HabitGlyph`.
 *
 * The tile fills whatever square its caller sized for it; the caller owns both the
 * tile's dimensions and the gutter between tiles (Glance has no aspectRatio, and
 * padding applied here would land inside the background rather than between tiles).
 * [tileSize] is that square's edge: the glyph scales with it and the text lines drop
 * out on tiles too small to hold them legibly.
 */
@Composable
fun GridTile(
    icon: ImageVector,
    hue: Float,
    label: String,
    caption: String,
    enabled: Boolean,
    action: Action,
    tileSize: Dp,
    modifier: GlanceModifier = GlanceModifier,
) {
    val fg = if (enabled) GlanceTheme.colors.onSurface else GlanceTheme.colors.onSurfaceVariant
    val glyph = (tileSize * 0.46f).coerceIn(20.dp, 44.dp)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(12.dp)
            .padding(4.dp)
            .clickable(action),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(glyph)
                .background(if (enabled) glyphBackground(hue) else GlanceTheme.colors.background)
                .cornerRadius(glyph / 2),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(iconBitmap(icon)),
                contentDescription = label,
                colorFilter = ColorFilter.tint(
                    if (enabled) glyphForeground(hue) else GlanceTheme.colors.onSurfaceVariant,
                ),
                modifier = GlanceModifier.size(glyph * 0.58f),
            )
        }
        if (tileSize >= 62.dp) {
            Text(
                label,
                maxLines = 1,
                style = TextStyle(color = fg, fontSize = 10.sp),
            )
        }
        if (tileSize >= 80.dp) {
            Text(
                caption,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
            )
        }
    }
}

/**
 * Heat-grid cell color, reusing the Phase 4 palette so the widget and the in-app
 * streak history read identically for the same day.
 */
fun heatColor(day: StreakDay): GlanceColorProvider = when (day.state) {
    StreakDayState.FROZEN -> ColorProvider(day = StreakFrozen, night = StreakFrozenDark)
    StreakDayState.BROKEN -> ColorProvider(day = StreakBroken, night = StreakBrokenDark)
    StreakDayState.COMPLETE -> when (day.heatLevel) {
        1 -> ColorProvider(day = HeatL1, night = HeatL1Dark)
        2 -> ColorProvider(day = HeatL2, night = HeatL2Dark)
        3 -> ColorProvider(day = HeatL3, night = HeatL3Dark)
        else -> ColorProvider(day = HeatL4, night = HeatL4Dark)
    }
    StreakDayState.EMPTY,
    StreakDayState.TODAY_PENDING,
    StreakDayState.FUTURE,
    -> ColorProvider(day = HeatL0, night = HeatL0Dark)
}
