package com.habittracker.domain.model

data class Identity(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,   // Material icon name (e.g. "menu_book"); semantic flipped from emoji.
    val hue: Int = 142, // OKLCH hue 0..360 for color tint.
)
