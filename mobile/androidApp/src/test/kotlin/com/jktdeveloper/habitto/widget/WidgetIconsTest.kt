package com.jktdeveloper.habitto.widget

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The rasteriser's failure mode is silent: a mis-walked vector still returns a bitmap,
 * just an empty one, and the widget shows a blank circle. So the check is "did anything
 * get drawn", not "does it look right".
 *
 * NATIVE graphics is required, not a preference — Robolectric's default LEGACY mode
 * stubs out `Canvas.drawPath`, so every bitmap comes back transparent and the assertion
 * would fail on a rasteriser that works perfectly on a device.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetIconsTest {

    @Test
    fun `rasterises a material icon to non-blank pixels`() {
        val bitmap = iconBitmap(Icons.Default.CheckCircle)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue(pixels.any { it != 0 }, "icon rasterised to a fully transparent bitmap")
    }

    @Test
    fun `caches by icon, so a grid of repeats costs one bitmap`() {
        assertSame(iconBitmap(Icons.Default.MoreHoriz), iconBitmap(Icons.Default.MoreHoriz))
        assertNotSame(iconBitmap(Icons.Default.MoreHoriz), iconBitmap(Icons.Default.CheckCircle))
    }
}
