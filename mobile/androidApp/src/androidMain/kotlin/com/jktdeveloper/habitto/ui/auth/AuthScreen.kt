package com.jktdeveloper.habitto.ui.auth

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jktdeveloper.habitto.ui.theme.FlameOrange
import com.jktdeveloper.habitto.ui.theme.FlameSoft
import com.jktdeveloper.habitto.ui.theme.NumeralStyle
import com.jktdeveloper.habitto.ui.theme.Spacing
import com.habittracker.data.remote.GoogleSignInLauncher
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    launcher: GoogleSignInLauncher,
    onSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // When ConfirmationEmailSent fires, show the check variant instead of navigating back
    var checkEmail by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AuthEvent.Success -> onSuccess()
                is AuthEvent.ConfirmationEmailSent -> {
                    checkEmail = event.email
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
                windowInsets = WindowInsets(0.dp),
                modifier = Modifier.statusBarsPadding(),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.xxl)
                .verticalScroll(rememberScrollState())
                .imePadding(),
        ) {
            Spacer(Modifier.height(Spacing.xxl))

            // --- Hero block ---
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = FlameSoft,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocalFireDepartment,
                    contentDescription = null,
                    tint = FlameOrange,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(32.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = when {
                    checkEmail != null -> "Check your email"
                    uiState.isSignUp -> "Create account"
                    else -> "Welcome back"
                },
                style = NumeralStyle.copy(fontSize = 44.sp, lineHeight = 44.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = when {
                    checkEmail != null ->
                        "We sent a sign-in link to $checkEmail. Tap it to continue."
                    else ->
                        "Sync your habits and streak across devices."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Spacing.xxl))

            // --- Body block ---
            if (checkEmail != null) {
                // Check variant: email card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MarkEmailRead,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Link sent",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Text(
                                text = "Expires in 15 minutes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            } else {
                // signin / signup inputs
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AuthInput(
                        value = uiState.email,
                        onValueChange = viewModel::onEmailChange,
                        label = "Email",
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                    )

                    AuthInput(
                        value = uiState.password,
                        onValueChange = viewModel::onPasswordChange,
                        label = "Password",
                        visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = if (uiState.password.isNotEmpty()) {
                            {
                                VisibilityToggle(
                                    visible = uiState.isPasswordVisible,
                                    onClick = viewModel::togglePasswordVisibility,
                                )
                            }
                        } else null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = if (uiState.isSignUp) ImeAction.Next else ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
                    )

                    if (uiState.isSignUp) {
                        AuthInput(
                            value = uiState.confirmPassword,
                            onValueChange = viewModel::onConfirmPasswordChange,
                            label = "Confirm password",
                            visualTransformation = if (uiState.isConfirmPasswordVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                            trailingIcon = if (uiState.confirmPassword.isNotEmpty()) {
                                {
                                    VisibilityToggle(
                                        visible = uiState.isConfirmPasswordVisible,
                                        onClick = viewModel::toggleConfirmPasswordVisibility,
                                    )
                                }
                            } else null,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
                        )

                        // 8-char min validation hint
                        if (uiState.password.isNotEmpty() && uiState.password.length < 8) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = "Password must be at least 8 characters.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }

                    // Server-side error row
                    uiState.error?.let { errorMsg ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = errorMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            // --- Bottom action block (32dp top padding) ---
            Spacer(Modifier.height(32.dp))

            // Primary filled button
            Button(
                onClick = {
                    if (checkEmail != null) {
                        // Open mail app
                        val intent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_APP_EMAIL)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(intent) }.onFailure {
                            scope.launch {
                                snackbarHostState.showSnackbar("No email app found.")
                            }
                        }
                    } else {
                        viewModel.submit()
                    }
                },
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (uiState.isLoading && checkEmail == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        text = when {
                            checkEmail != null -> "Open mail app"
                            uiState.isSignUp -> "Create account"
                            else -> "Sign in"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            if (checkEmail == null) {
                // "or" divider
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        text = "  or  ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                // Continue with Google
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            launcher.requestIdToken()
                                .onSuccess { token -> viewModel.signInWithGoogle(token) }
                                .onFailure { e ->
                                    snackbarHostState.showSnackbar(
                                        e.message ?: "Google sign-in failed",
                                    )
                                }
                        }
                    },
                    enabled = !uiState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    GoogleLogo(modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Continue with Google",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                // Toggle sign-in / sign-up
                TextButton(
                    onClick = viewModel::toggleSignUp,
                    enabled = !uiState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                ) {
                    Text(
                        text = if (uiState.isSignUp) "I have an account" else "Create account",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

/** OutlinedTextField with primary-colored border (resting + focused) and 8dp corner radius. */
@Composable
private fun AuthInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.primary,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.primary,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    )
}

/**
 * 4-color Google "G" logo drawn via Canvas.
 * No `Icons.Outlined.Google` exists in material-icons-extended 1.7.6 — inline Canvas used instead.
 */
@Composable
private fun GoogleLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) / 2f

        // Clip to circle
        val circlePath = Path().apply { addOval(Rect(center = Offset(cx, cy), radius = r)) }
        clipPath(circlePath) {
            // Background white
            drawRect(Color.White, size = Size(w, h))

            // Blue segment (right, ~270° arc) — simplified as a filled circle then overlaid wedges
            // We'll draw the 4 colored path segments approximated from the SVG viewBox (18x18):
            val scale = r / 9f  // SVG uses 0-18 space; center at 9,9

            // Blue path: right portion
            drawPath(
                path = Path().apply {
                    moveTo(cx + 8.64f * scale, cy)
                    arcTo(
                        rect = Rect(
                            left = cx - r,
                            top = cy - r,
                            right = cx + r,
                            bottom = cy + r,
                        ),
                        startAngleDegrees = -15f,
                        sweepAngleDegrees = 95f,
                        forceMoveTo = false,
                    )
                    lineTo(cx, cy)
                    close()
                },
                color = Color(0xFF4285F4),
            )

            // Green path: bottom portion
            drawPath(
                path = Path().apply {
                    moveTo(cx, cy + r)
                    arcTo(
                        rect = Rect(
                            left = cx - r,
                            top = cy - r,
                            right = cx + r,
                            bottom = cy + r,
                        ),
                        startAngleDegrees = 80f,
                        sweepAngleDegrees = 100f,
                        forceMoveTo = false,
                    )
                    lineTo(cx, cy)
                    close()
                },
                color = Color(0xFF34A853),
            )

            // Yellow path: left-bottom portion
            drawPath(
                path = Path().apply {
                    moveTo(cx - r, cy)
                    arcTo(
                        rect = Rect(
                            left = cx - r,
                            top = cy - r,
                            right = cx + r,
                            bottom = cy + r,
                        ),
                        startAngleDegrees = 160f,
                        sweepAngleDegrees = 100f,
                        forceMoveTo = false,
                    )
                    lineTo(cx, cy)
                    close()
                },
                color = Color(0xFFFBBC05),
            )

            // Red path: top portion
            drawPath(
                path = Path().apply {
                    moveTo(cx, cy - r)
                    arcTo(
                        rect = Rect(
                            left = cx - r,
                            top = cy - r,
                            right = cx + r,
                            bottom = cy + r,
                        ),
                        startAngleDegrees = 250f,
                        sweepAngleDegrees = 110f,
                        forceMoveTo = false,
                    )
                    lineTo(cx, cy)
                    close()
                },
                color = Color(0xFFEA4335),
            )

            // White inner circle to create the "G" cutout
            drawCircle(Color.White, radius = r * 0.55f, center = Offset(cx, cy))

            // Blue rectangle for the horizontal bar of the G
            drawRect(
                color = Color(0xFF4285F4),
                topLeft = Offset(cx, cy - r * 0.18f),
                size = Size(r * 0.95f, r * 0.36f),
            )
        }
    }
}

@Composable
private fun VisibilityToggle(visible: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            contentDescription = if (visible) "Hide password" else "Show password",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
