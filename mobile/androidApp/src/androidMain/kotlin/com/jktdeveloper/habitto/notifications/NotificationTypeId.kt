package com.jktdeveloper.habitto.notifications

/**
 * Canvas v5 catalog (11 types ship in Phase 9; bar_raised/dropped deferred to Phase 10).
 * hasTime = true means user picks a daily LocalTime for it in NotificationsSettings.
 */
enum class NotificationTypeId(
    val key: String,
    val category: NotificationCategory,
    val defaultEnabled: Boolean,
    val defaultMinutesOfDay: Int? = null,
) {
    DAILY_REMINDER("daily_reminder", NotificationCategory.REMINDER, true, 9 * 60),
    DAILY_REMINDER_PER_IDENTITY("daily_reminder_per_identity", NotificationCategory.REMINDER, false, 17 * 60 + 30),
    STREAK_RISK("streak_risk", NotificationCategory.ALERT, true, 21 * 60),
    WANT_TIMER_END("want_timer_end", NotificationCategory.ALERT, true),
    STREAK_FROZEN("streak_frozen", NotificationCategory.STATUS, true),
    STREAK_RESET("streak_reset", NotificationCategory.STATUS, true),
    TIER_ADVANCED("tier_advanced", NotificationCategory.STATUS, true),
    MILESTONE_STREAK("milestone_streak", NotificationCategory.STATUS, true),
    SESSION_EXPIRED("session_expired", NotificationCategory.SYSTEM, true),
    CLOUD_RESTORE_COMPLETE("cloud_restore_complete", NotificationCategory.SYSTEM, true),
    SYNC_FAILED_PERSISTENT("sync_failed_persistent", NotificationCategory.SYSTEM, true);

    val hasTime: Boolean get() = defaultMinutesOfDay != null
}

enum class NotificationCategory(val displayName: String) {
    REMINDER("Reminders"),
    ALERT("Alerts"),
    STATUS("Status updates"),
    SYSTEM("System"),
}
