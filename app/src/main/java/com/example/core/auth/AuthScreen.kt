package com.example.core.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.CreativeStudioMark
import com.example.core.designsystem.StudioColors
import com.example.core.designsystem.StudioErrorBanner
import com.example.core.designsystem.StudioRadii
import com.example.core.designsystem.StudioSpacing

@Composable
fun AuthScreen(
    authState: AuthState,
    onGoogleSignIn: () -> Unit,
    onDismissError: () -> Unit
) {
    val isLoading = authState is AuthState.Initializing || authState is AuthState.Authenticating
    val errorMessage = (authState as? AuthState.Error)?.message

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioColors.BackgroundPrimary)
            .safeDrawingPadding()
    ) {
        // Subtle monochrome silk-wave studio backdrop matching the luxury dark identity
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF171A21), StudioColors.BackgroundPrimary),
                    center = Offset(w * 0.5f, h * 0.28f),
                    radius = w * 0.9f
                )
            )
            val wave1 = Path().apply {
                moveTo(0f, h * 0.72f)
                cubicTo(w * 0.35f, h * 0.60f, w * 0.65f, h * 0.86f, w, h * 0.68f)
            }
            val wave2 = Path().apply {
                moveTo(0f, h * 0.78f)
                cubicTo(w * 0.38f, h * 0.66f, w * 0.68f, h * 0.92f, w, h * 0.74f)
            }
            drawPath(wave1, color = Color(0x18E1E4E8), style = Stroke(width = 2f))
            drawPath(wave2, color = Color(0x10C4C8CE), style = Stroke(width = 1.5f))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 460.dp)
                .align(Alignment.Center)
                .padding(horizontal = StudioSpacing.lg, vertical = StudioSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(StudioSpacing.xl))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CreativeStudioMark(size = 64.dp)
                Spacer(modifier = Modifier.height(StudioSpacing.md))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(StudioSpacing.xxs))
                Text(
                    text = stringResource(R.string.tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.welcome_back),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(StudioSpacing.xs))
                Text(
                    text = stringResource(R.string.sign_in_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(StudioSpacing.lg))

                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    if (errorMessage != null) {
                        Column {
                            StudioErrorBanner(
                                message = errorMessage,
                                onDismiss = onDismissError,
                                modifier = Modifier.testTag("auth_error_banner")
                            )
                            Spacer(modifier = Modifier.height(StudioSpacing.md))
                        }
                    }
                }

                Button(
                    onClick = onGoogleSignIn,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("google_sign_in_button"),
                    shape = RoundedCornerShape(StudioRadii.md),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioColors.PureWhite,
                        contentColor = StudioColors.BackgroundPrimary,
                        disabledContainerColor = StudioColors.SurfaceElevated,
                        disabledContentColor = StudioColors.TextSecondary
                    ),
                    contentPadding = PaddingValues(horizontal = StudioSpacing.lg, vertical = StudioSpacing.sm)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .testTag("auth_loading_indicator"),
                            color = StudioColors.MetallicSilver,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(StudioSpacing.sm))
                        Text(
                            text = if (authState is AuthState.Initializing) {
                                "Restoring workspace…"
                            } else {
                                "Signing in…"
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(StudioSpacing.sm))
                        Text(
                            text = stringResource(R.string.sign_in_google),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                Spacer(modifier = Modifier.height(StudioSpacing.md))

                Surface(
                    shape = RoundedCornerShape(StudioRadii.sm),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(StudioSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Private Cloud Isolation",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Projects, brand assets, and AI history stay isolated to your Google account.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Text(
                text = "Photo · Design · Video · AI Studio",
                style = MaterialTheme.typography.labelMedium,
                color = StudioColors.TextTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun AuthScreenRoute(authViewModel: AuthViewModel, authState: AuthState) {
    val context = LocalContext.current
    AuthScreen(
        authState = authState,
        onGoogleSignIn = { authViewModel.signInWithGoogle(context) },
        onDismissError = { authViewModel.dismissError() }
    )
}
