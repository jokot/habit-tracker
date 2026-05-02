package com.jktdeveloper.habitto.ui.identity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
fun AddIdentityScreen(
    viewModel: AddIdentityViewModel,
    onClose: () -> Unit,
    onCommitSuccess: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.commitSuccess.collect { onCommitSuccess() }
    }
    when (state.step) {
        1 -> AddIdentityStep1Screen(
            state = state,
            onClose = onClose,
            onSelect = viewModel::selectIdentity,
            onContinue = viewModel::advanceToStep2,
        )
        2 -> AddIdentityStep2Screen(
            state = state,
            onBack = viewModel::goBackToStep1,
            onToggle = viewModel::toggleHabit,
            onCommit = viewModel::commit,
        )
    }
}
