package com.jktdeveloper.habitto.ui.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jktdeveloper.habitto.AppContainer
import com.jktdeveloper.habitto.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class YouHubViewModel(
    private val container: AppContainer,
) : ViewModel() {

    val authState: StateFlow<AuthState> = container.authState

    fun currentEmail(): String? = container.currentAccountEmail()

    private val _isSigningOut = MutableStateFlow(false)
    val isSigningOut: StateFlow<Boolean> = _isSigningOut.asStateFlow()

    fun signOut(onComplete: () -> Unit) {
        if (_isSigningOut.value) return
        viewModelScope.launch {
            _isSigningOut.value = true
            try {
                container.signOutFromSettings()
                onComplete()
            } finally {
                _isSigningOut.value = false
            }
        }
    }
}
