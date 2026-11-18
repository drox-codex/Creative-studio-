package com.example.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.common.UiState
import com.example.core.database.CreativeProject
import com.example.core.database.ProjectType
import com.example.core.database.UserProfile
import com.example.core.designsystem.CreativeStudioMark
import com.example.core.designsystem.StudioColors
import com.example.core.designsystem.StudioEmptyState
import com.example.core.designsystem.StudioRadii
import com.example.core.designsystem.StudioSectionHeader
import com.example.core.designsystem.StudioSpacing
import com.example.core.designsystem.StudioSurfaceCard
import com.example.feature.assets.AssetCatalog
import com.example.feature.assets.DesignTemplateAsset
import com.example.feature.assets.TemplatePreviewCard
import com.example.feature.projects.ProjectThumbnailPreview
import com.example.feature.projects.ProjectsViewModel
import com.example.feature.projects.formatProjectTimestamp

@Composable
fun HomeScreen(
    userProfile: UserProfile,
    projectsViewModel: ProjectsViewModel,
    onCreateNewProject: () -> Unit,
    onQuickStartStudio: (ProjectType) -> Unit,
    onOpenAiAssistant: () -> Unit,
    onOpenProject: (CreativeProject) -> Unit,
    onSeeAllProjects: () -> Unit,
    onSeeAllTemplates: () -> Unit,
    onUseTemplate: (DesignTemplateAsset) -> Unit,
    onOpenSettings: () -> Unit
) {
    val projectsState by projectsViewModel.projectsState.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    val firstName = userProfile.displayName.trim().substringBefore(' ').ifBlank { "Creator" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = StudioSpacing.md,
            end = StudioSpacing.md,
            top = StudioSpacing.sm,
            bottom = StudioSpacing.xxl
        ),
        verticalArrangement = Arrangement.spacedBy(StudioSpacing.lg)
    ) {
        // Brand & Personalized Greeting Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    CreativeStudioMark(size = 34.dp)
                    Spacer(modifier = Modifier.width(StudioSpacing.sm))
                    Column {
                        Text(
                            text = "Hello, $firstName",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("home_greeting_text")
                        )
                        Text(
                            text = "Create something refined today",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onOpenAiAssistant,
                        modifier = Modifier.testTag("home_ai_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Open AI Assistant",
                            tint = StudioColors.MetallicSilver
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("home_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Search Field
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(StudioRadii.md),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = StudioColors.MetallicSilver,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_search_field")
            )
        }

        // Hero Create New Project Card
        item {
            StudioSurfaceCard(
                onClick = onCreateNewProject,
                elevated = true,
                cornerRadius = StudioRadii.lg,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_new_project_card")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    StudioColors.SurfaceElevated,
                                    Color(0xFF232832),
                                    StudioColors.BackgroundSecondary
                                )
                            )
                        )
                        .padding(vertical = StudioSpacing.lg, horizontal = StudioSpacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(StudioColors.SubtleHighlight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = StudioColors.BackgroundPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(StudioSpacing.sm))
                        Text(
                            text = stringResource(R.string.new_project),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Photo · Graphic Design · Video Timeline",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Quick Access Studios + AI Studio
        item {
            Column(verticalArrangement = Arrangement.spacedBy(StudioSpacing.sm)) {
                StudioSectionHeader(title = stringResource(R.string.quick_access))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(StudioSpacing.sm)
                ) {
                    QuickAccessTile(
                        title = stringResource(R.string.studio_photo),
                        icon = Icons.Rounded.Image,
                        onClick = { onQuickStartStudio(ProjectType.PHOTO) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_photo_button")
                    )
                    QuickAccessTile(
                        title = stringResource(R.string.studio_design),
                        icon = Icons.Rounded.Brush,
                        onClick = { onQuickStartStudio(ProjectType.DESIGN) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_design_button")
                    )
                    QuickAccessTile(
                        title = stringResource(R.string.studio_video),
                        icon = Icons.Rounded.Videocam,
                        onClick = { onQuickStartStudio(ProjectType.VIDEO) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_video_button")
                    )
                    QuickAccessTile(
                        title = stringResource(R.string.studio_ai),
                        icon = Icons.Rounded.AutoAwesome,
                        onClick = onOpenAiAssistant,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_ai_button")
                    )
                }
            }
        }

        // Continue Editing / Recent Projects
        item {
            Column(verticalArrangement = Arrangement.spacedBy(StudioSpacing.sm)) {
                StudioSectionHeader(
                    title = stringResource(R.string.recent_projects),
                    actionLabel = "See all",
                    onActionClick = onSeeAllProjects
                )

                when (val ui = projectsState) {
                    is UiState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(88.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = StudioColors.MetallicSilver,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                    is UiState.Error -> {
                        Text(
                            text = ui.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioColors.Error
                        )
                    }
                    is UiState.Success -> {
                        val query = searchQuery.trim().lowercase()
                        val recent = ui.data
                            .filter { query.isEmpty() || it.name.lowercase().contains(query) }
                            .take(4)

                        if (recent.isEmpty()) {
                            StudioEmptyState(
                                icon = Icons.Rounded.Brush,
                                title = "Start your first project",
                                subtitle = "Pick a studio above or choose a curated template below.",
                                modifier = Modifier
                                    .clip(RoundedCornerShape(StudioRadii.md))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(StudioRadii.md))
                            )
                        } else {
                            // Continue Editing highlight for top project
                            val top = recent.first()
                            StudioSurfaceCard(
                                onClick = { onOpenProject(top) },
                                elevated = true
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ProjectThumbnailPreview(
                                        project = top,
                                        modifier = Modifier.size(58.dp)
                                    )
                                    Spacer(modifier = Modifier.width(StudioSpacing.md))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "CONTINUE EDITING",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = StudioColors.MetallicSilver
                                        )
                                        Text(
                                            text = top.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${top.projectType.name} · ${formatProjectTimestamp(top)}",
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

                            recent.drop(1).forEach { project ->
                                StudioSurfaceCard(
                                    onClick = { onOpenProject(project) },
                                    contentPadding = PaddingValues(StudioSpacing.sm)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ProjectThumbnailPreview(
                                            project = project,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.width(StudioSpacing.sm))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = project.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${project.projectType.name} · ${formatProjectTimestamp(project)}",
                                                style = MaterialTheme.typography.labelSmall,
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
                            }
                        }
                    }
                }
            }
        }

        // Curated Templates
        item {
            Column(verticalArrangement = Arrangement.spacedBy(StudioSpacing.sm)) {
                StudioSectionHeader(
                    title = stringResource(R.string.curated_templates),
                    actionLabel = "Browse",
                    onActionClick = onSeeAllTemplates
                )
                val q = searchQuery.trim().lowercase()
                val templates = AssetCatalog.templates.filter {
                    q.isEmpty() || it.title.lowercase().contains(q)
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(StudioSpacing.sm)
                ) {
                    items(templates, key = { it.id }) { tpl ->
                        TemplatePreviewCard(
                            template = tpl,
                            onClick = { onUseTemplate(tpl) },
                            modifier = Modifier.width(156.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAccessTile(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    StudioSurfaceCard(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = StudioSpacing.md, horizontal = StudioSpacing.xs)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = StudioColors.SubtleHighlight,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(StudioSpacing.xs))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
