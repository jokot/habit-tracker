package com.jktdeveloper.habitto.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.TimerOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.jktdeveloper.habitto.notifications.NotificationCategory
import com.jktdeveloper.habitto.notifications.NotificationTypeId
import com.jktdeveloper.habitto.ui.theme.SystemPurple

/**
 * How one notification type presents itself in settings: what it's called and what it
 * looks like. Kept off [NotificationTypeId] so the domain enum stays free of Compose,
 * but exhaustive over it — a type without an entry fails `NotificationTypeUiTest`
 * rather than rendering its raw enum key, which is what the screen used to do.
 *
 * [label] describes the type in the abstract ("Streak at risk"), never a sample of what
 * it will say ("Your 14-day streak is at risk") — a settings row outlives any one
 * notification.
 */
private data class TypeUi(
    val label: String,
    val filled: ImageVector,
    val outlined: ImageVector,
)

private val TYPE_UI: Map<NotificationTypeId, TypeUi> = mapOf(
    NotificationTypeId.DAILY_REMINDER to TypeUi(
        "Log today's habits",
        Icons.Filled.NotificationsActive,
        Icons.Outlined.NotificationsActive,
    ),
    NotificationTypeId.DAILY_REMINDER_PER_IDENTITY to TypeUi(
        "Per-identity reminder",
        Icons.AutoMirrored.Filled.MenuBook,
        Icons.AutoMirrored.Outlined.MenuBook,
    ),
    NotificationTypeId.STREAK_RISK to TypeUi(
        "Streak at risk",
        Icons.Filled.WarningAmber,
        Icons.Outlined.WarningAmber,
    ),
    NotificationTypeId.WANT_TIMER_END to TypeUi(
        "Want timer finished",
        Icons.Filled.TimerOff,
        Icons.Outlined.TimerOff,
    ),
    NotificationTypeId.STREAK_FROZEN to TypeUi(
        "Streak frozen",
        Icons.Filled.AcUnit,
        Icons.Outlined.AcUnit,
    ),
    NotificationTypeId.STREAK_RESET to TypeUi(
        "Streak reset",
        Icons.Filled.RestartAlt,
        Icons.Outlined.RestartAlt,
    ),
    NotificationTypeId.TIER_ADVANCED to TypeUi(
        "Tier unlocked",
        Icons.AutoMirrored.Filled.TrendingUp,
        Icons.AutoMirrored.Outlined.TrendingUp,
    ),
    NotificationTypeId.MILESTONE_STREAK to TypeUi(
        "Streak milestone",
        Icons.Filled.LocalFireDepartment,
        Icons.Outlined.LocalFireDepartment,
    ),
    NotificationTypeId.SESSION_EXPIRED to TypeUi(
        "Sign in again",
        Icons.Filled.Lock,
        Icons.Outlined.Lock,
    ),
    NotificationTypeId.CLOUD_RESTORE_COMPLETE to TypeUi(
        "Backup restored",
        Icons.Filled.CloudDone,
        Icons.Outlined.CloudDone,
    ),
    NotificationTypeId.SYNC_FAILED_PERSISTENT to TypeUi(
        "Sync paused",
        Icons.Filled.CloudOff,
        Icons.Outlined.CloudOff,
    ),
)

/** Human name for a notification type. */
val NotificationTypeId.uiLabel: String
    get() = TYPE_UI.getValue(this).label

/** Filled while the type is on, outlined once it's off. */
fun NotificationTypeId.uiIcon(enabled: Boolean): ImageVector =
    TYPE_UI.getValue(this).let { if (enabled) it.filled else it.outlined }

/** Every type carries a usable label — the invariant `NotificationTypeUiTest` checks. */
internal fun hasUiMetadata(type: NotificationTypeId): Boolean =
    TYPE_UI[type]?.label?.isNotBlank() == true

/** Tint every icon in a category shares, so a row's role reads before its label does. */
@Composable
fun NotificationCategory.accent(): Color = when (this) {
    NotificationCategory.REMINDER -> MaterialTheme.colorScheme.primary
    NotificationCategory.ALERT -> MaterialTheme.colorScheme.error
    NotificationCategory.STATUS -> MaterialTheme.colorScheme.onSurfaceVariant
    // ponytail: one purple for both themes, as the design specifies
    NotificationCategory.SYSTEM -> SystemPurple
}
