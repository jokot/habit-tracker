package com.jktdeveloper.habitto.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LabelImportant
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NoFood
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Unsubscribe
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps Material icon name strings (as used by Identity.icon and HabitTemplate.iconName,
 * sourced from canvas) to Compose ImageVectors. Falls back to LabelImportant for
 * unmapped names — safe placeholder rather than a crash.
 */
fun materialIconFor(name: String?): ImageVector = when (name) {
    "menu_book" -> Icons.Default.MenuBook
    "code" -> Icons.Default.Code
    "directions_run" -> Icons.Default.DirectionsRun
    "edit_note" -> Icons.Default.EditNote
    "school" -> Icons.Default.School
    "eco" -> Icons.Default.Eco
    "self_improvement" -> Icons.Default.SelfImprovement
    "favorite" -> Icons.Default.Favorite
    "palette" -> Icons.Default.Palette
    "savings" -> Icons.Default.Savings
    "handshake" -> Icons.Default.Handshake
    "explore" -> Icons.Default.Explore
    "restaurant" -> Icons.Default.Restaurant
    "article" -> Icons.Default.Article
    "description" -> Icons.Default.Description
    "headphones" -> Icons.Default.Headphones
    "bug_report" -> Icons.Default.BugReport
    "rate_review" -> Icons.Default.RateReview
    "groups" -> Icons.Default.Groups
    "extension" -> Icons.Default.Extension
    "fitness_center" -> Icons.Default.FitnessCenter
    "directions_walk" -> Icons.Default.DirectionsWalk
    "directions_bike" -> Icons.Default.DirectionsBike
    "pool" -> Icons.Default.Pool
    "accessibility_new" -> Icons.Default.AccessibilityNew
    "timer" -> Icons.Default.Timer
    "whatshot" -> Icons.Default.Whatshot
    "rss_feed" -> Icons.Default.RssFeed
    "create" -> Icons.Default.Create
    "edit" -> Icons.Default.Edit
    "spellcheck" -> Icons.Default.Spellcheck
    "email" -> Icons.Default.Email
    "smart_display" -> Icons.Default.SmartDisplay
    "cast" -> Icons.Default.Cast
    "language" -> Icons.Default.Language
    "style" -> Icons.Default.Style
    "podcasts" -> Icons.Default.Podcasts
    "calculate" -> Icons.Default.Calculate
    "movie" -> Icons.Default.Movie
    "cleaning_services" -> Icons.Default.CleaningServices
    "inventory" -> Icons.Default.Inventory
    "delete_sweep" -> Icons.Default.DeleteSweep
    "volunteer_activism" -> Icons.Default.VolunteerActivism
    "inbox" -> Icons.Default.Inbox
    "unsubscribe" -> Icons.Default.Unsubscribe
    "swap_horiz" -> Icons.Default.SwapHoriz
    "spa" -> Icons.Default.Spa
    "psychology" -> Icons.Default.Psychology
    "water_drop" -> Icons.Default.WaterDrop
    "bedtime" -> Icons.Default.Bedtime
    "kitchen" -> Icons.Default.Kitchen
    "no_food" -> Icons.Default.NoFood
    "medication" -> Icons.Default.Medication
    "brush" -> Icons.Default.Brush
    "music_note" -> Icons.Default.MusicNote
    "photo_camera" -> Icons.Default.PhotoCamera
    "share" -> Icons.Default.Share
    "block" -> Icons.Default.Block
    "account_balance" -> Icons.Default.AccountBalance
    "price_check" -> Icons.Default.PriceCheck
    "money_off" -> Icons.Default.MoneyOff
    "phone" -> Icons.Default.Phone
    "chat" -> Icons.Default.Chat
    "event_note" -> Icons.Default.EventNote
    "mail_outline" -> Icons.Default.MailOutline
    "hearing" -> Icons.Default.Hearing
    "place" -> Icons.Default.Place
    "lightbulb" -> Icons.Default.Lightbulb
    "park" -> Icons.Default.Park
    else -> Icons.Default.LabelImportant
}
