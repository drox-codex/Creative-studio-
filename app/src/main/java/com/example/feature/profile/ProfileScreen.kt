package com.example.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.core.common.UiState
import com.example.core.database.ProjectType
import com.example.core.database.UserProfile
import com.example.core.designsystem.StudioColors
import com.example.core.designsystem.StudioPrimaryButton
import com.example.core.designsystem.StudioRadii
import com.example.core.designsystem.StudioSpacing
import com.example.core.designsystem.StudioSurfaceCard
import com.example.core.designsystem.StudioTopBar
import com.example.feature.projects.ProjectsViewModel

private val BrandPaletteSwatches = listOf("#090A0C", "#111317", "#191C21", "#C4C8CE", "#E1E4E8", "#F5F5F5")

@Composable
fun ProfileScreen(
    userProfile: UserProfile,
    projectsViewModel: ProjectsViewModel,
    isGuest: Boolean = false,
    onSignInWithGoogle: () -> Unit = {},
    onSaveBrandKit: (displayName: String, brandName: String, primaryHex: String, accentHex: String, onDone: (String) -> Unit) -> Unit,
    onOpenProjects: () -> Unit,
    onOpenAiAssistant: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val projectsState by projectsViewModel.projectsState.collectAsStateWithLifecycle()

    var displayNameDraft by remember(userProfile.displayName) { mutableStateOf(userProfile.displayName) }
    var brandNameDraft by remember(userProfile.brandName) { mutableStateOf(userProfile.brandName) }
    var primaryHex by remember(userProfile.brandPrimaryHex) { mutableStateOf(userProfile.brandPrimaryHex) }
    var accentHex by remember(userProfile.brandAccentHex) { mutableStateOf(userProfile.brandAccentHex) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val allProjects = (projectsState as? UiState.Success)?.data.orEmpty()
    val photoCount = allProjects.count { it.projectType == ProjectType.PHOTO }
    val designCount = allProjects.count { it.projectType == ProjectType.DESIGN }
    val videoCount = allProjects.count { it.projectType == ProjectType.VIDEO }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(StudioSpacing.md),
        verticalArrangement = Arrangement.spacedBy(StudioSpacing.md)
    ) {
        item {
            StudioTopBar(
                title = stringResource(R.string.profile_title),
                actions = {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("profile_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Open Settings",
                            tint = StudioColors.SubtleHighlight
                        )
                    }
                }
            )
        }

        // Google Identity Header Card
        item {
            StudioSurfaceCard(elevated = true, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (userProfile.photoUrl.isNotBlank()) {
                        AsyncImage(
                            model = userProfile.photoUrl,
                            contentDescription = userProfile.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, StudioColors.MetallicSilver, CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(StudioColors.SurfaceInteractive)
                                .border(1.5.dp, StudioColors.MetallicSilver, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = userProfile.displayName.take(1).uppercase().ifBlank { "C" },
                                style = MaterialTheme.typography.headlineLarge,
                                color = StudioColors.SubtleHighlight
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(StudioSpacing.sm))

                    Text(
                        text = userProfile.displayName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("profile_display_name")
                    )
                    if (userProfile.email.isNotBlank()) {
                        Text(
                            text = userProfile.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (isGuest) {
                        Text(
                            text = "Local Guest Workspace · Device Only",
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioColors.MetallicSilver
                        )
                    }

                    if (isGuest) {
                        Spacer(modifier = Modifier.height(StudioSpacing.md))
                        StudioPrimaryButton(
                            text = "Sign in with Google",
                            icon = Icons.Rounded.AccountCircle,
                            onClick = onSignInWithGoogle,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "guest_upgrade_google_button"
                        )
                    }

                    Spacer(modifier = Modifier.height(StudioSpacing.md))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ProfileStatColumn(label = "Projects", count = allProjects.size)
                        ProfileStatColumn(label = "Photo", count = photoCount)
                        ProfileStatColumn(label = "Design", count = designCount)
                        ProfileStatColumn(label = "Video", count = videoCount)
                    }
                }
            }
        }

        // Brand Kit Configuration Card
        item {
            StudioSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "BRAND KIT IDENTITY",
                    style = MaterialTheme.typography.labelMedium,
                    color = StudioColors.MetallicSilver
                )
                Spacer(modifier = Modifier.height(StudioSpacing.sm))

                OutlinedTextField(
                    value = displayNameDraft,
                    onValueChange = { displayNameDraft = it },
                    label = { Text("Creator Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = StudioColors.MetallicSilver,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(StudioSpacing.sm))

                OutlinedTextField(
                    value = brandNameDraft,
                    onValueChange = { brandNameDraft = it },
                    label = { Text("Brand / Studio Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = StudioColors.MetallicSilver,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(StudioSpacing.md))

                Text(
                    text = "Primary Surface Swatch ($primaryHex)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(StudioSpacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(StudioSpacing.xs)) {
                    BrandPaletteSwatches.forEach { hex ->
                        SwatchCircle(
                            hex = hex,
                            selected = primaryHex.equals(hex, true),
                            onClick = { primaryHex = hex }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(StudioSpacing.sm))

                Text(
                    text = "Accent Swatch ($accentHex)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(StudioSpacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(StudioSpacing.xs)) {
                    BrandPaletteSwatches.forEach { hex ->
                        SwatchCircle(
                            hex = hex,
                            selected = accentHex.equals(hex, true),
                            onClick = { accentHex = hex }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(StudioSpacing.md))

                StudioPrimaryButton(
                    text = if (isSaving) "Saving…" else "Save Brand Kit",
                    enabled = !isSaving,
                    onClick = {
                        isSaving = true
                        onSaveBrandKit(displayNameDraft, brandNameDraft, primaryHex, accentHex) { msg ->
                            isSaving = false
                            feedbackMessage = msg
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "save_brand_kit_button"
                )

                if (isSaving) {
                    Spacer(modifier = Modifier.height(StudioSpacing.xs))
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = StudioColors.MetallicSilver,
                        strokeWidth = 2.dp
                    )
                }
                if (feedbackMessage != null) {
                    Spacer(modifier = Modifier.height(StudioSpacing.xs))
                    Text(
                        text = feedbackMessage!!,
                        style = MaterialTheme.typography.labelMedium,
                        color = StudioColors.MetallicSilver
                    )
                }
            }
        }

        // Workspace Navigation Shortcuts
        item {
            StudioSurfaceCard(
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ProfileMenuRow(
                    icon = Icons.Rounded.FolderOpen,
                    title = "My Projects",
                    subtitle = if (isGuest) "${allProjects.size} local studio projects" else "${allProjects.size} synced studio projects",
                    onClick = onOpenProjects,
                    testTag = "profile_projects_row"
                )
                ProfileDivider()
                ProfileMenuRow(
                    icon = Icons.Rounded.AutoAwesome,
                    title = "AI Creative Assistant",
                    subtitle = "Gemini multimodal editing & layout direction",
                    onClick = onOpenAiAssistant,
                    testTag = "profile_ai_row"
                )
                ProfileDivider()
                ProfileMenuRow(
                    icon = Icons.Rounded.Settings,
                    title = "Workspace Settings",
                    subtitle = "Theme, export presets, cache & account security",
                    onClick = onOpenSettings,
                    testTag = "profile_settings_row"
                )
            }
        }
    }
}

@Composable
private fun ProfileStatColumn(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = StudioColors.PureWhite
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SwatchCircle(hex: String, selected: Boolean, onClick: () -> Unit) {
    val parsed = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(StudioColors.BackgroundPrimary)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(parsed)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) StudioColors.PureWhite else StudioColors.Border,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

@Composable
private fun ProfileMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = StudioSpacing.md, vertical = StudioSpacing.md)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = StudioColors.MetallicSilver,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(StudioSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProfileDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline)
    )
}
