package com.jktdeveloper.habitto.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.jktdeveloper.habitto.MainActivity

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
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .cornerRadius(24.dp)
            .padding(12.dp),
    ) {
        content()
    }
}

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
