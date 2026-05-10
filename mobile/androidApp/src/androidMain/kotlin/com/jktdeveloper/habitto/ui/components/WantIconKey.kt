package com.jktdeveloper.habitto.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.ui.graphics.vector.ImageVector

/** Curated 13-glyph picker set. Stable string keys persist on WantActivity.iconKey. */
val WANT_ICON_KEYS: List<String> = listOf(
    "play_circle", "smart_display", "local_movies",
    "chat_bubble", "photo_camera", "forum", "sports_esports",
    "shopping_bag", "restaurant", "cake", "local_drink",
    "local_cafe", "more_horiz",
)

fun wantIconForKey(key: String?): ImageVector = when (key) {
    "play_circle" -> Icons.Default.PlayCircle
    "smart_display" -> Icons.Default.SmartDisplay
    "local_movies" -> Icons.Default.LocalMovies
    "chat_bubble" -> Icons.Default.ChatBubble
    "photo_camera" -> Icons.Default.PhotoCamera
    "forum" -> Icons.Default.Forum
    "sports_esports" -> Icons.Default.SportsEsports
    "shopping_bag" -> Icons.Default.ShoppingBag
    "restaurant" -> Icons.Default.Restaurant
    "cake" -> Icons.Default.Cake
    "local_drink" -> Icons.Default.LocalDrink
    "local_cafe" -> Icons.Default.LocalCafe
    else -> Icons.Default.MoreHoriz
}

/** Resolve icon for a WantActivity. Prefer explicit key; fallback to legacy name match. */
fun resolveWantIcon(iconKey: String?, name: String): ImageVector {
    if (iconKey != null) return wantIconForKey(iconKey)
    return legacyWantIconByName(name)
}

private fun legacyWantIconByName(name: String): ImageVector = when {
    name.contains("twitter", ignoreCase = true) || name.contains("/x", ignoreCase = true) -> Icons.Default.ChatBubble
    name.contains("instagram", ignoreCase = true) -> Icons.Default.PhotoCamera
    name.contains("tiktok", ignoreCase = true) || name.contains("scroll", ignoreCase = true)
        || name.contains("reel", ignoreCase = true) || name.contains("short", ignoreCase = true) -> Icons.Default.PlayCircle
    name.contains("youtube", ignoreCase = true) -> Icons.Default.SmartDisplay
    name.contains("netflix", ignoreCase = true) || name.contains("stream", ignoreCase = true) -> Icons.Default.LocalMovies
    name.contains("reddit", ignoreCase = true) -> Icons.Default.Forum
    name.contains("game", ignoreCase = true) || name.contains("valorant", ignoreCase = true) -> Icons.Default.SportsEsports
    name.contains("snack", ignoreCase = true) || name.contains("food", ignoreCase = true)
        || name.contains("junk", ignoreCase = true) -> Icons.Default.Restaurant
    name.contains("donut", ignoreCase = true) || name.contains("dessert", ignoreCase = true)
        || name.contains("sweet", ignoreCase = true) -> Icons.Default.Cake
    name.contains("shop", ignoreCase = true) || name.contains("purchase", ignoreCase = true) -> Icons.Default.ShoppingBag
    name.contains("drink", ignoreCase = true) || name.contains("sugary", ignoreCase = true) -> Icons.Default.LocalDrink
    name.contains("coffee", ignoreCase = true) -> Icons.Default.LocalCafe
    else -> Icons.Default.MoreHoriz
}
