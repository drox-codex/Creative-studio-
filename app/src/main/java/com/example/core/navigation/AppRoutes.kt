package com.example.core.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object ProjectsRoute

@Serializable
data object AssetsRoute

@Serializable
data object ProfileRoute

@Serializable
data object SettingsRoute

@Serializable
data class PhotoEditorRoute(val projectId: String)

@Serializable
data class DesignEditorRoute(val projectId: String)

@Serializable
data class VideoEditorRoute(val projectId: String)

@Serializable
data class AiAssistantRoute(
    val projectId: String? = null,
    val selectedLayerId: String? = null
)
