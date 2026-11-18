package com.example.feature.projects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.core.common.UiState
import com.example.core.database.CreativeProject
import com.example.core.database.LayerType
import com.example.core.database.ProjectType
import com.example.core.designsystem.StudioColors
import com.example.core.designsystem.StudioEmptyState
import com.example.core.designsystem.StudioErrorBanner
import com.example.core.designsystem.StudioFilterChip
import com.example.core.designsystem.StudioRadii
import com.example.core.designsystem.StudioSpacing
import com.example.core.designsystem.StudioSurfaceCard
import com.example.core.designsystem.StudioTopBar
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel,
    onOpenProject: (CreativeProject) -> Unit,
    onNewProjectClick: () -> Unit
) {
    val state by viewModel.projectsState.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()

    var renameTarget by remember { mutableStateOf<CreativeProject?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<CreativeProject?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        StudioTopBar(
            title = stringResource(R.string.projects_title),
            subtitle = "Synced privately to your workspace",
            actions = {
                IconButton(
                    onClick = onNewProjectClick,
                    modifier = Modifier.testTag("projects_new_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "New project",
                        tint = StudioColors.SubtleHighlight
                    )
                }
            }
        )

        if (actionError != null) {
            StudioErrorBanner(
                message = actionError!!,
                onDismiss = { viewModel.clearError() },
                modifier = Modifier.padding(horizontal = StudioSpacing.md, vertical = StudioSpacing.xs)
            )
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = {
                Text(
                    "Search projects…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search projects",
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
                .padding(horizontal = StudioSpacing.md, vertical = StudioSpacing.xs)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = StudioSpacing.md, vertical = StudioSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(StudioSpacing.xs)
        ) {
            item {
                StudioFilterChip(
                    label = "All",
                    selected = selectedFilter == null,
                    onClick = { viewModel.setFilter(null) }
                )
            }
            items(ProjectType.entries.size) { idx ->
                val type = ProjectType.entries[idx]
                val label = when (type) {
                    ProjectType.PHOTO -> "Photo"
                    ProjectType.DESIGN -> "Design"
                    ProjectType.VIDEO -> "Video"
                }
                StudioFilterChip(
                    label = label,
                    selected = selectedFilter == type,
                    onClick = { viewModel.setFilter(type) }
                )
            }
        }

        when (val ui = state) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = StudioColors.MetallicSilver)
                }
            }
            is UiState.Error -> {
                StudioErrorBanner(
                    message = ui.message,
                    modifier = Modifier.padding(StudioSpacing.md)
                )
            }
            is UiState.Success -> {
                val query = searchQuery.trim().lowercase()
                val filtered = ui.data.filter { project ->
                    (selectedFilter == null || project.projectType == selectedFilter) &&
                        (query.isEmpty() || project.name.lowercase().contains(query))
                }
                if (filtered.isEmpty()) {
                    StudioEmptyState(
                        icon = Icons.Rounded.FolderOpen,
                        title = if (ui.data.isEmpty()) "No projects yet" else "No matching projects",
                        subtitle = if (ui.data.isEmpty()) {
                            "Create a Photo, Design, or Video project to start building."
                        } else {
                            "Try clearing your filter or search query."
                        },
                        actionLabel = if (ui.data.isEmpty()) "Create Project" else null,
                        onAction = if (ui.data.isEmpty()) onNewProjectClick else null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(162.dp),
                        contentPadding = PaddingValues(StudioSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(StudioSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(StudioSpacing.sm),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filtered, key = { it.id }) { project ->
                            ProjectGridCard(
                                project = project,
                                onOpen = { onOpenProject(project) },
                                onRename = {
                                    renameTarget = project
                                    renameText = project.name
                                },
                                onDuplicate = { viewModel.duplicateProject(project) },
                                onDelete = { deleteTarget = project }
                            )
                        }
                    }
                }
            }
        }
    }

    if (renameTarget != null) {
        val target = renameTarget!!
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Rename Project", style = MaterialTheme.typography.titleLarge) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Project name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameProject(target.id, renameText)
                        renameTarget = null
                    }
                ) {
                    Text("Save", color = StudioColors.SubtleHighlight)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel", color = StudioColors.TextSecondary)
                }
            }
        )
    }

    if (deleteTarget != null) {
        val target = deleteTarget!!
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Delete Project", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "Permanently delete \"${target.name}\"? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProject(target.id)
                        deleteTarget = null
                    }
                ) {
                    Text("Delete", color = StudioColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel", color = StudioColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
fun ProjectGridCard(
    project: CreativeProject,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    StudioSurfaceCard(
        onClick = onOpen,
        contentPadding = PaddingValues(StudioSpacing.sm),
        modifier = Modifier.testTag("project_card_${project.id}")
    ) {
        ProjectThumbnailPreview(
            project = project,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.1f)
        )
        Spacer(modifier = Modifier.height(StudioSpacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Project options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Rounded.Title, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        leadingIcon = { Icon(Icons.Rounded.FileCopy, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDuplicate()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = StudioColors.Error) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = StudioColors.Error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ProjectThumbnailPreview(
    project: CreativeProject,
    modifier: Modifier = Modifier
) {
    val bg = runCatching { Color(android.graphics.Color.parseColor(project.backgroundColorHex)) }
        .getOrDefault(StudioColors.BackgroundPrimary)
    val typeIcon = when (project.projectType) {
        ProjectType.PHOTO -> Icons.Rounded.Image
        ProjectType.DESIGN -> Icons.Rounded.Brush
        ProjectType.VIDEO -> Icons.Rounded.Videocam
    }

    Surface(
        modifier = modifier.clip(RoundedCornerShape(StudioRadii.sm)),
        shape = RoundedCornerShape(StudioRadii.sm),
        color = bg,
        border = BorderStroke(1.dp, StudioColors.Border)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val imageSource = project.mediaUri.takeIf { it.isNotBlank() }
                ?: project.layers.firstOrNull { it.type == LayerType.IMAGE && it.imageUri.isNotBlank() }?.imageUri
            if (!imageSource.isNullOrBlank() && project.projectType != ProjectType.VIDEO) {
                AsyncImage(
                    model = imageSource,
                    contentDescription = project.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val firstText = project.layers.firstOrNull { it.type == LayerType.TEXT && it.text.isNotBlank() }?.text
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(StudioSpacing.xs)
                ) {
                    Icon(
                        imageVector = typeIcon,
                        contentDescription = null,
                        tint = StudioColors.MetallicSilver,
                        modifier = Modifier.size(24.dp)
                    )
                    if (!firstText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = firstText.replace('\n', ' '),
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioColors.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${project.canvasWidth}×${project.canvasHeight}",
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioColors.TextSecondary
                        )
                    }
                }
            }
        }
    }
}

fun formatProjectTimestamp(project: CreativeProject): String {
    val ts = project.updatedAt ?: project.createdAt ?: return "Just now"
    val diffSec = ((System.currentTimeMillis() / 1000L) - ts.seconds).coerceAtLeast(0L)
    return when {
        diffSec < 60 -> "Just now"
        diffSec < 3600 -> "${diffSec / 60}m ago"
        diffSec < 86400 -> "${diffSec / 3600}h ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(ts.toDate())
    }
}
