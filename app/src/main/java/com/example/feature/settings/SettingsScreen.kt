package com.example.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.database.UserProfile
import com.example.core.designsystem.StudioColors
import com.example.core.designsystem.StudioFilterChip
import com.example.core.designsystem.StudioPrimaryButton
import com.example.core.designsystem.StudioSecondaryButton
import com.example.core.designsystem.StudioSpacing
import com.example.core.designsystem.StudioSurfaceCard
import com.example.core.designsystem.StudioTopBar
import java.util.Locale

private val SupportedLanguages = listOf("English", "Spanish", "French", "German", "Japanese")

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    userProfile: UserProfile,
    isGuest: Boolean = false,
    onNavigateBack: () -> Unit,
    onSignInWithGoogle: () -> Unit = {},
    onSignOut: () -> Unit,
    onDeleteAccount: (onError: (String) -> Unit) -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val storageBytes by viewModel.storageBytes.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        if (!statusMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(statusMessage!!)
            viewModel.clearStatus()
        }
    }

    LaunchedEffect(deleteError) {
        if (!deleteError.isNullOrBlank()) {
            snackbarHostState.showSnackbar(deleteError!!)
            deleteError = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            StudioTopBar(
                title = stringResource(R.string.settings_title),
                onNavigateBack = onNavigateBack
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(StudioSpacing.md),
                verticalArrangement = Arrangement.spacedBy(StudioSpacing.md)
            ) {
                // Account Category
                item {
                    SettingsSectionTitle("ACCOUNT")
                    StudioSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = StudioColors.MetallicSilver
                            )
                            Spacer(modifier = Modifier.width(StudioSpacing.md))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = userProfile.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = if (isGuest) "Local Guest Mode · Stored on device" else userProfile.email.ifBlank { "Google Authenticated Account" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (isGuest) {
                            Spacer(modifier = Modifier.height(StudioSpacing.md))
                            StudioPrimaryButton(
                                text = stringResource(R.string.sign_in_google),
                                icon = Icons.Rounded.AccountCircle,
                                onClick = onSignInWithGoogle,
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "settings_guest_google_sign_in_button"
                            )
                        }
                        Spacer(modifier = Modifier.height(StudioSpacing.md))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(StudioSpacing.sm)
                        ) {
                            StudioSecondaryButton(
                                text = if (isGuest) "Exit Guest" else stringResource(R.string.sign_out),
                                icon = Icons.AutoMirrored.Rounded.Logout,
                                onClick = onSignOut,
                                modifier = Modifier.weight(1f),
                                testTag = "settings_sign_out_button"
                            )
                            StudioSecondaryButton(
                                text = if (isGuest) "Clear Data" else stringResource(R.string.delete_account),
                                icon = Icons.Rounded.DeleteForever,
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier.weight(1f),
                                testTag = "settings_delete_account_button"
                            )
                        }
                    }
                }

                // Appearance & Motion Category
                item {
                    SettingsSectionTitle("APPEARANCE & ACCESSIBILITY")
                    StudioSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Studio Surface Theme",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(StudioSpacing.xs))
                        Row(horizontalArrangement = Arrangement.spacedBy(StudioSpacing.xs)) {
                            StudioFilterChip(
                                label = "Obsidian (#090A0C)",
                                selected = settings.themeVariant == "obsidian",
                                onClick = { viewModel.setThemeVariant("obsidian") }
                            )
                            StudioFilterChip(
                                label = "Deep Slate",
                                selected = settings.themeVariant == "slate",
                                onClick = { viewModel.setThemeVariant("slate") }
                            )
                        }
                        Spacer(modifier = Modifier.height(StudioSpacing.md))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Reduced Motion",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Minimize spring & scale animations",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.reducedMotion,
                                onCheckedChange = { viewModel.setReducedMotion(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = StudioColors.BackgroundPrimary,
                                    checkedTrackColor = StudioColors.SubtleHighlight
                                )
                            )
                        }
                    }
                }

                // Language Category
                item {
                    SettingsSectionTitle("LANGUAGE")
                    StudioSurfaceCard(
                        onClick = { showLanguageDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Language,
                                    contentDescription = null,
                                    tint = StudioColors.MetallicSilver
                                )
                                Spacer(modifier = Modifier.width(StudioSpacing.md))
                                Text(
                                    text = "Interface Language",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Text(
                                text = settings.language,
                                style = MaterialTheme.typography.labelLarge,
                                color = StudioColors.MetallicSilver
                            )
                        }
                    }
                }

                // Export Preferences Category
                item {
                    SettingsSectionTitle("EXPORT PREFERENCES")
                    StudioSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                tint = StudioColors.MetallicSilver
                            )
                            Spacer(modifier = Modifier.width(StudioSpacing.sm))
                            Text(
                                text = "Default Image Format & Quality (${settings.defaultExportQuality}%)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Spacer(modifier = Modifier.height(StudioSpacing.xs))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(StudioSpacing.xs)) {
                            items(listOf("PNG", "JPEG")) { fmt ->
                                StudioFilterChip(
                                    label = fmt,
                                    selected = settings.defaultExportFormat == fmt,
                                    onClick = { viewModel.setExportFormat(fmt) }
                                )
                            }
                            items(listOf("1080p", "720p")) { res ->
                                StudioFilterChip(
                                    label = "Video $res",
                                    selected = settings.defaultVideoResolution == res,
                                    onClick = { viewModel.setVideoResolution(res) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(StudioSpacing.xs))
                        Slider(
                            value = settings.defaultExportQuality.toFloat(),
                            onValueChange = { viewModel.setExportQuality(it.toInt()) },
                            valueRange = 70f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = StudioColors.PureWhite,
                                activeTrackColor = StudioColors.MetallicSilver
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isGuest) "Local Autosave" else "Cloud Autosave",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = if (isGuest) "Save layer & timeline edits automatically on device" else "Sync layer & timeline edits automatically",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.autosaveEnabled,
                                onCheckedChange = { viewModel.setAutosave(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = StudioColors.BackgroundPrimary,
                                    checkedTrackColor = StudioColors.SubtleHighlight
                                )
                            )
                        }
                    }
                }

                // Storage Category
                item {
                    SettingsSectionTitle("STORAGE")
                    StudioSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Workspace Media & Cache",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = formatBytes(storageBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StudioColors.MetallicSilver
                                )
                            }
                            StudioSecondaryButton(
                                text = "Clear Cache",
                                icon = Icons.Rounded.CleaningServices,
                                onClick = { viewModel.clearExportCache() },
                                testTag = "settings_clear_cache_button"
                            )
                        }
                    }
                }

                // About & Privacy Category
                item {
                    SettingsSectionTitle("ABOUT & PRIVACY")
                    StudioSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.PrivacyTip,
                                contentDescription = null,
                                tint = StudioColors.MetallicSilver
                            )
                            Spacer(modifier = Modifier.width(StudioSpacing.md))
                            Column {
                                Text(
                                    text = if (isGuest) "Local Guest Isolation" else "Zero-Trust Account Isolation",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = if (isGuest) {
                                        "All local projects, media assets, and AI edits remain on this device until you sign in with Google."
                                    } else {
                                        "All projects, media assets, and AI edits are strictly scoped to your authenticated Google UID."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(StudioSpacing.md))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = StudioColors.MetallicSilver
                            )
                            Spacer(modifier = Modifier.width(StudioSpacing.md))
                            Column {
                                Text(
                                    text = "Creative Studio",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Version 1.0.0 · Unified Photo, Design, Video & AI Suite",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp)
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Select Language") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(StudioSpacing.xs)) {
                    SupportedLanguages.forEach { lang ->
                        Text(
                            text = lang,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (settings.language == lang) StudioColors.PureWhite else StudioColors.TextSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setLanguage(lang)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = StudioSpacing.xs)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text("Close") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text(if (isGuest) "Clear Local Data" else "Delete Account", color = StudioColors.Error) },
            text = {
                Text(
                    if (isGuest) {
                        "This permanently deletes all local guest projects, AI history, and imported media from this device. Continue?"
                    } else {
                        "This permanently deletes your profile, all cloud projects, AI history, and local media for this account. Continue?"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteAccount { err -> deleteError = err }
                    }
                ) {
                    Text(if (isGuest) "Clear Permanently" else "Delete Permanently", color = StudioColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = StudioColors.MetallicSilver,
        modifier = Modifier.padding(bottom = StudioSpacing.xs)
    )
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb < 0.1) "${bytes / 1024L} KB used"
    else String.format(Locale.US, "%.2f MB used", mb)
}
