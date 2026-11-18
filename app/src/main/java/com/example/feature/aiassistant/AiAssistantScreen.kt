package com.example.feature.aiassistant

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircleOutline
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.designsystem.StudioColors
import com.example.core.designsystem.StudioRadii
import com.example.core.designsystem.StudioSpacing
import com.example.core.designsystem.StudioSurfaceCard
import com.example.core.designsystem.StudioTopBar

private val SuggestedPrompts = listOf(
    "Remove the background",
    "Make the image look cinematic",
    "Change the background to black",
    "Make the text metallic silver",
    "Improve image quality",
    "Suggest a better composition",
    "Create a visual identity for my brand"
)

@Composable
fun AiAssistantScreen(
    viewModel: AiAssistantViewModel,
    selectedLayerId: String?,
    onNavigateBack: () -> Unit
) {
    val activeProject by viewModel.activeProject.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        StudioTopBar(
            title = stringResource(R.string.ai_assistant_title),
            subtitle = activeProject?.let { "Context: ${it.name} (${it.projectType.name})" } ?: "Global Creative Studio",
            onNavigateBack = onNavigateBack,
            actions = {
                IconButton(
                    onClick = { viewModel.undoLastAiEdit() },
                    enabled = canUndo && !isProcessing,
                    modifier = Modifier.testTag("ai_undo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Undo,
                        contentDescription = "Undo AI edit",
                        tint = if (canUndo) StudioColors.SubtleHighlight else StudioColors.TextTertiary
                    )
                }
                IconButton(
                    onClick = { viewModel.redoLastAiEdit() },
                    enabled = canRedo && !isProcessing,
                    modifier = Modifier.testTag("ai_redo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Redo,
                        contentDescription = "Redo AI edit",
                        tint = if (canRedo) StudioColors.SubtleHighlight else StudioColors.TextTertiary
                    )
                }
            }
        )

        // Selected element context pill
        if (activeProject != null && selectedLayerId != null) {
            val layer = activeProject?.layers?.firstOrNull { it.layerId == selectedLayerId }
            if (layer != null) {
                Surface(
                    shape = RoundedCornerShape(StudioRadii.full),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.padding(horizontal = StudioSpacing.md, vertical = StudioSpacing.xxs)
                ) {
                    Text(
                        text = "Selected element: ${layer.name} (${layer.type.name})",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioColors.MetallicSilver,
                        modifier = Modifier.padding(horizontal = StudioSpacing.sm, vertical = 6.dp)
                    )
                }
            }
        }

        if (messages.isEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(StudioSpacing.md),
                verticalArrangement = Arrangement.spacedBy(StudioSpacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Spacer(modifier = Modifier.height(StudioSpacing.md))
                    // Monochrome metallic silver spark emblem (no purple/blue gradients)
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(StudioColors.SurfaceElevated)
                            .border(1.dp, StudioColors.MetallicSilver.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "AI Assistant Emblem",
                            tint = StudioColors.SubtleHighlight,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(StudioSpacing.md))
                    Text(
                        text = "What would you like to refine?",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(StudioSpacing.xs))
                    Text(
                        text = "Executes targeted edits on your active project while preserving unrelated layers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(StudioSpacing.md))
                }

                items(SuggestedPrompts) { suggestion ->
                    StudioSurfaceCard(
                        onClick = { viewModel.sendPrompt(suggestion) },
                        contentPadding = PaddingValues(horizontal = StudioSpacing.md, vertical = StudioSpacing.sm),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(StudioSpacing.sm)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = StudioColors.MetallicSilver,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(StudioSpacing.md),
                verticalArrangement = Arrangement.spacedBy(StudioSpacing.sm)
            ) {
                items(messages, key = { it.id }) { msg ->
                    AiMessageBubble(message = msg)
                }
                if (isProcessing) {
                    item {
                        Row(
                            modifier = Modifier.padding(vertical = StudioSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(StudioSpacing.sm)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = StudioColors.MetallicSilver,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Analyzing project & applying operation…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Bottom prompt bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline)
                .padding(horizontal = StudioSpacing.md, vertical = StudioSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = {
                    Text(
                        "Ask anything about this project…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                maxLines = 3,
                shape = RoundedCornerShape(StudioRadii.md),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                    focusedBorderColor = StudioColors.MetallicSilver,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_prompt_input")
            )
            Spacer(modifier = Modifier.width(StudioSpacing.xs))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (input.isNotBlank() && !isProcessing) StudioColors.SubtleHighlight
                        else StudioColors.SurfaceElevated
                    )
                    .clickable(enabled = input.isNotBlank() && !isProcessing) {
                        val toSend = input
                        input = ""
                        viewModel.sendPrompt(toSend)
                    }
                    .testTag("ai_send_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Send prompt",
                    tint = if (input.isNotBlank() && !isProcessing) StudioColors.BackgroundPrimary else StudioColors.TextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AiMessageBubble(message: AiMessageItem) {
    val align = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bgColor = when {
        message.isError -> StudioColors.ErrorSurface
        message.isUser -> StudioColors.SurfaceInteractive
        else -> StudioColors.BackgroundSecondary
    }
    val borderColor = when {
        message.isError -> StudioColors.Error.copy(alpha = 0.4f)
        message.isUser -> StudioColors.BorderFocused
        else -> StudioColors.Border
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = align) {
        Surface(
            shape = RoundedCornerShape(StudioRadii.md),
            color = bgColor,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Column(modifier = Modifier.padding(StudioSpacing.md)) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isError) StudioColors.Error else MaterialTheme.colorScheme.onBackground
                )
                if (!message.actionApplied.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(StudioSpacing.xs))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircleOutline,
                            contentDescription = null,
                            tint = StudioColors.Success,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Executed: ${message.actionApplied}",
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioColors.Success
                        )
                    }
                }
            }
        }
    }
}
