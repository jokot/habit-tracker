package com.habittracker.data.repository

sealed class SignUpResult {
    data class SignedIn(val session: UserSession) : SignUpResult()
    data class ConfirmationRequired(val email: String) : SignUpResult()
}
