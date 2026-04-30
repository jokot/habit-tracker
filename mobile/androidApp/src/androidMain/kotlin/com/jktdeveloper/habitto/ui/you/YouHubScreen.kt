package com.jktdeveloper.habitto.ui.you

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jktdeveloper.habitto.ui.components.IdentityHubCard
import com.jktdeveloper.habitto.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouHubScreen(
    viewModel: YouHubViewModel,
    onOpenSettings: () -> Unit,
    onSignIn: () -> Unit,
    onSignOutComplete: () -> Unit,
    onOpenIdentities: () -> Unit,
) {
    val authState by viewModel.authState.collectAsState()
    val isSigningOut by viewModel.isSigningOut.collectAsState()
    val email = viewModel.currentEmail()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "You",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                windowInsets = WindowInsets(0.dp),
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            item {
                val identities by viewModel.userIdentities.collectAsState()
                if (identities.isNotEmpty()) {
                    IdentityHubCard(identities = identities, onClick = onOpenIdentities)
                }
            }
            item { SectionHeader("Account") }
            if (authState.isAuthenticated) {
                if (email != null) {
                    item {
                        ListItem(
                            headlineContent = {
                                Text(email, style = MaterialTheme.typography.bodyLarge)
                            },
                        )
                    }
                }
                item {
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isSigningOut) {
                                viewModel.signOut(onSignOutComplete)
                            },
                        headlineContent = {
                            Text(
                                if (isSigningOut) "Signing out…" else "Sign out",
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                    )
                }
            } else {
                item {
                    ListItem(
                        modifier = Modifier.fillMaxWidth().clickable { onSignIn() },
                        headlineContent = { Text("Sign in to sync") },
                        trailingContent = {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                            )
                        },
                    )
                }
            }

            item { SectionHeader("App") }
            item {
                ListItem(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenSettings() },
                    headlineContent = { Text("Settings") },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = Spacing.xl,
            top = Spacing.xxl,
            bottom = Spacing.md,
        ),
    )
}
