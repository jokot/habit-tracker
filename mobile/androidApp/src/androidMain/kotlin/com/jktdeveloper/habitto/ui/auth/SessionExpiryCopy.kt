package com.jktdeveloper.habitto.ui.auth

// Text for a session expiry that kept unsynced changes. A null count means the count failed.

fun sessionExpiredToast(unsyncedCount: Int?): String = when (unsyncedCount) {
    0 -> "Session expired. Sign in again."
    null -> "Session expired. Sign in again to save your changes."
    1 -> "Session expired. Sign in again to save 1 change."
    else -> "Session expired. Sign in again to save $unsyncedCount changes."
}

fun heldNoticeTitle(unsyncedCount: Int?): String = when (unsyncedCount) {
    null -> "Changes not synced"
    1 -> "1 change not synced"
    else -> "$unsyncedCount changes not synced"
}

fun heldNoticeBody(email: String?): String {
    val who = if (email != null) "Sign in as $email" else "Sign in with the account you used before"
    return "$who to save them. If you use another account, they are deleted from this phone."
}
