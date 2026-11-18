package com.example.core.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.core.auth.AuthScreenRoute
import com.example.core.auth.AuthState
import com.example.core.auth.AuthViewModel
import com.example.core.common.MediaStorageHelper
import com.example.core.database.CreativeProject
import com.example.core.database.ProjectType
import com.example.core.database.TimelineClip
import com.example.core.database.UserProfile
import com.example.core.designsystem.StudioColors
import com.example.core.designsystem.StudioFilterChip
import com.example.core.designsystem.StudioMotion
import com.example.core.designsystem.StudioPrimaryButton
import com.example.core.designsystem.StudioRadii
import com.example.core.designsystem.StudioSecondaryButton
import com.example.core.designsystem.StudioSpacing
import com.example.core.storage.SettingsPreferencesRepository
import com.example.core.storage.StudioSettings
import com.example.feature.aiassistant.AiAssistantScreen
import com.example.feature.aiassistant.AiAssistantViewModel
import com.example.feature.assets.AssetsScreen
import com.example.feature.designeditor.DesignEditorScreen
import com.example.feature.designeditor.DesignEditorViewModel
import com.example.feature.home.HomeScreen
import com.example.feature.photoeditor.PhotoEditorScreen
import com.example.feature.photoeditor.PhotoEditorViewModel
import com.example.feature.profile.ProfileScreen
import com.example.feature.projects.FirestoreProjectsRepository
import com.example.feature.projects.ProjectsScreen
import com.example.feature.projects.ProjectsViewModel
import com.example.feature.settings.SettingsScreen
import com.example.feature.settings.SettingsViewModel
import com.example.feature.videoeditor.VideoEditorScreen
import com.example.feature.videoeditor.VideoEditorViewModel
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun CreativeStudioApp(
    authViewModel: AuthViewModel,
    settingsRepository: SettingsPreferencesRepository,
    settings: StudioSettings
) {
    val authState by authViewModel.authState.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = authState,
        transitionSpec = {
            fadeIn(animationSpec = StudioMotion.quickTween()) togetherWith
                fadeOut(animationSpec = StudioMotion.quickTween())
        },
        contentKey = { state ->
            when (state) {
                is AuthState.Authenticated -> "auth_${state.uid}"
                else -> "unauth"
            }
        },
        label = "auth_root_transition"
    ) { state ->
        when (state) {
            is AuthState.Authenticated -> {
                key(state.uid) {
                    AuthenticatedStudioHost(
                        uid = state.uid,
                        userProfile = state.profile,
                        authViewModel = authViewModel,
                        settingsRepository = settingsRepository,
                        settings = settings
                    )
                }
            }
            else -> {
                AuthScreenRoute(authViewModel = authViewModel, authState = state)
            }
        }
    }
}

@Composable
private fun AuthenticatedStudioHost(
    uid: String,
    userProfile: UserProfile,
    authViewModel: AuthViewModel,
    settingsRepository: SettingsPreferencesRepository,
    settings: StudioSettings
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val projectsRepository = remember(uid) { FirestoreProjectsRepository(context.applicationContext) }
    val projectsViewModel: ProjectsViewModel = viewModel(
        key = "projects_$uid",
        factory = ProjectsViewModel.provideFactory(projectsRepository, uid)
    )

    var showNewProjectSheet by remember { mutableStateOf(false) }
    var preselectedProjectType by remember { mutableStateOf(ProjectType.DESIGN) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination
    val isTopLevel = currentDest == null ||
        currentDest.hasRoute<HomeRoute>() ||
        currentDest.hasRoute<ProjectsRoute>() ||
        currentDest.hasRoute<AssetsRoute>() ||
        currentDest.hasRoute<ProfileRoute>()

    fun openProjectInEditor(project: CreativeProject) {
        if (project.id.isBlank()) return
        when (project.projectType) {
            ProjectType.PHOTO -> navController.navigate(PhotoEditorRoute(project.id))
            ProjectType.DESIGN -> navController.navigate(DesignEditorRoute(project.id))
            ProjectType.VIDEO -> navController.navigate(VideoEditorRoute(project.id))
        }
    }

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun createAndOpen(project: CreativeProject) {
        projectsViewModel.createProject(project) { newId, type ->
            when (type) {
                ProjectType.PHOTO -> navController.navigate(PhotoEditorRoute(newId))
                ProjectType.DESIGN -> navController.navigate(DesignEditorRoute(newId))
                ProjectType.VIDEO -> navController.navigate(VideoEditorRoute(newId))
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (isTopLevel && !useRail) {
                    StudioBottomBar(
                        navController = navController,
                        onNavigate = { navigateTopLevel(it) },
                        onNewClick = {
                            preselectedProjectType = ProjectType.DESIGN
                            showNewProjectSheet = true
                        }
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTopLevel && useRail) {
                    StudioNavigationRail(
                        navController = navController,
                        onNavigate = { navigateTopLevel(it) },
                        onNewClick = {
                            preselectedProjectType = ProjectType.DESIGN
                            showNewProjectSheet = true
                        }
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    NavHost(
                        navController = navController,
                        startDestination = HomeRoute
                    ) {
                        composable<HomeRoute> {
                            HomeScreen(
                                userProfile = userProfile,
                                projectsViewModel = projectsViewModel,
                                onCreateNewProject = {
                                    preselectedProjectType = ProjectType.DESIGN
                                    showNewProjectSheet = true
                                },
                                onQuickStartStudio = { type ->
                                    preselectedProjectType = type
                                    showNewProjectSheet = true
                                },
                                onOpenAiAssistant = {
                                    navController.navigate(AiAssistantRoute())
                                },
                                onOpenProject = { project -> openProjectInEditor(project) },
                                onSeeAllProjects = { navigateTopLevel(ProjectsRoute) },
                                onSeeAllTemplates = { navigateTopLevel(AssetsRoute) },
                                onUseTemplate = { template ->
                                    createAndOpen(template.toProject())
                                },
                                onOpenSettings = {
                                    navController.navigate(SettingsRoute)
                                }
                            )
                        }

                        composable<ProjectsRoute> {
                            ProjectsScreen(
                                viewModel = projectsViewModel,
                                onOpenProject = { project -> openProjectInEditor(project) },
                                onNewProjectClick = {
                                    preselectedProjectType = ProjectType.DESIGN
                                    showNewProjectSheet = true
                                }
                            )
                        }

                        composable<AssetsRoute> {
                            AssetsScreen(
                                onUseTemplate = { template ->
                                    createAndOpen(template.toProject())
                                },
                                onCreatePhotoWithFilter = { filter ->
                                    createAndOpen(
                                        CreativeProject(
                                            name = "${filter.name} Photo",
                                            projectType = ProjectType.PHOTO,
                                            canvasWidth = 1080,
                                            canvasHeight = 1350,
                                            adjustments = filter.adjustments
                                        )
                                    )
                                },
                                onCreateDesignWithBackground = { bg ->
                                    createAndOpen(
                                        CreativeProject(
                                            name = "${bg.name} Canvas",
                                            projectType = ProjectType.DESIGN,
                                            canvasWidth = 1080,
                                            canvasHeight = 1080,
                                            backgroundColorHex = bg.hex
                                        )
                                    )
                                }
                            )
                        }

                        composable<ProfileRoute> {
                            ProfileScreen(
                                userProfile = userProfile,
                                projectsViewModel = projectsViewModel,
                                onSaveBrandKit = { dName, bName, primaryHex, accentHex, onDone ->
                                    authViewModel.updateBrandAndProfile(
                                        displayName = dName,
                                        brandName = bName,
                                        brandPrimaryHex = primaryHex,
                                        brandAccentHex = accentHex,
                                        preferredExportQuality = settings.defaultExportQuality
                                    ) { result ->
                                        onDone(
                                            if (result.isSuccess) "Brand kit synced to cloud"
                                            else result.exceptionOrNull()?.localizedMessage ?: "Update failed"
                                        )
                                    }
                                },
                                onOpenProjects = { navigateTopLevel(ProjectsRoute) },
                                onOpenAiAssistant = { navController.navigate(AiAssistantRoute()) },
                                onOpenSettings = { navController.navigate(SettingsRoute) }
                            )
                        }

                        composable<SettingsRoute> {
                            val settingsVm: SettingsViewModel = viewModel(
                                key = "settings_$uid",
                                factory = SettingsViewModel.provideFactory(
                                    appContext = context.applicationContext,
                                    prefsRepo = settingsRepository,
                                    currentUserId = uid
                                )
                            )
                            SettingsScreen(
                                viewModel = settingsVm,
                                userProfile = userProfile,
                                onNavigateBack = { navController.popBackStack() },
                                onSignOut = { authViewModel.signOut() },
                                onDeleteAccount = { onErr -> authViewModel.deleteAccount(onErr) }
                            )
                        }

                        composable<PhotoEditorRoute> { entry ->
                            val route = entry.toRoute<PhotoEditorRoute>()
                            val photoVm: PhotoEditorViewModel = viewModel(
                                key = "photo_${uid}_${route.projectId}",
                                factory = PhotoEditorViewModel.provideFactory(
                                    appContext = context.applicationContext,
                                    repository = projectsRepository,
                                    currentUserId = uid,
                                    projectId = route.projectId
                                )
                            )
                            PhotoEditorScreen(
                                viewModel = photoVm,
                                defaultExportFormat = settings.defaultExportFormat,
                                defaultExportQuality = settings.defaultExportQuality,
                                onNavigateBack = { navController.popBackStack() },
                                onOpenAiAssistant = { pid, layerId ->
                                    navController.navigate(AiAssistantRoute(pid, layerId))
                                }
                            )
                        }

                        composable<DesignEditorRoute> { entry ->
                            val route = entry.toRoute<DesignEditorRoute>()
                            val designVm: DesignEditorViewModel = viewModel(
                                key = "design_${uid}_${route.projectId}",
                                factory = DesignEditorViewModel.provideFactory(
                                    appContext = context.applicationContext,
                                    repository = projectsRepository,
                                    currentUserId = uid,
                                    projectId = route.projectId
                                )
                            )
                            DesignEditorScreen(
                                viewModel = designVm,
                                userProfile = userProfile,
                                defaultExportQuality = settings.defaultExportQuality,
                                onNavigateBack = { navController.popBackStack() },
                                onOpenAiAssistant = { pid, layerId ->
                                    navController.navigate(AiAssistantRoute(pid, layerId))
                                }
                            )
                        }

                        composable<VideoEditorRoute> { entry ->
                            val route = entry.toRoute<VideoEditorRoute>()
                            val videoVm: VideoEditorViewModel = viewModel(
                                key = "video_${uid}_${route.projectId}",
                                factory = VideoEditorViewModel.provideFactory(
                                    appContext = context.applicationContext,
                                    repository = projectsRepository,
                                    currentUserId = uid,
                                    projectId = route.projectId
                                )
                            )
                            VideoEditorScreen(
                                viewModel = videoVm,
                                defaultVideoResolution = settings.defaultVideoResolution,
                                onNavigateBack = { navController.popBackStack() },
                                onOpenAiAssistant = { pid ->
                                    navController.navigate(AiAssistantRoute(pid, null))
                                }
                            )
                        }

                        composable<AiAssistantRoute> { entry ->
                            val route = entry.toRoute<AiAssistantRoute>()
                            val aiVm: AiAssistantViewModel = viewModel(
                                key = "ai_${uid}_${route.projectId ?: "global"}",
                                factory = AiAssistantViewModel.provideFactory(
                                    appContext = context.applicationContext,
                                    projectsRepository = projectsRepository,
                                    currentUserId = uid,
                                    projectId = route.projectId,
                                    selectedLayerId = route.selectedLayerId
                                )
                            )
                            AiAssistantScreen(
                                viewModel = aiVm,
                                selectedLayerId = route.selectedLayerId,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewProjectSheet) {
        NewProjectBottomSheet(
            userId = uid,
            initialType = preselectedProjectType,
            onDismiss = { showNewProjectSheet = false },
            onCreateProject = { project ->
                showNewProjectSheet = false
                createAndOpen(project)
            }
        )
    }
}

@Composable
private fun StudioBottomBar(
    navController: NavHostController,
    onNavigate: (Any) -> Unit,
    onNewClick: () -> Unit
) {
    val backStack by navController.currentBackStackEntryAsState()
    val dest = backStack?.destination

    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.navigationBarsPadding()
        ) {
            NavigationBarItem(
                selected = dest?.hasRoute<HomeRoute>() == true,
                onClick = { onNavigate(HomeRoute) },
                icon = {
                    Icon(
                        imageVector = if (dest?.hasRoute<HomeRoute>() == true) Icons.Rounded.Home else Icons.Outlined.Home,
                        contentDescription = "Home"
                    )
                },
                label = { Text("Home") },
                colors = navigationItemColors(),
                modifier = Modifier.testTag("nav_home_tab")
            )
            NavigationBarItem(
                selected = dest?.hasRoute<ProjectsRoute>() == true,
                onClick = { onNavigate(ProjectsRoute) },
                icon = {
                    Icon(
                        imageVector = if (dest?.hasRoute<ProjectsRoute>() == true) Icons.Rounded.FolderOpen else Icons.Outlined.FolderOpen,
                        contentDescription = "Projects"
                    )
                },
                label = { Text("Projects") },
                colors = navigationItemColors(),
                modifier = Modifier.testTag("nav_projects_tab")
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(StudioColors.SubtleHighlight)
                    .clickable(onClick = onNewClick)
                    .testTag("nav_new_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "New Project",
                    tint = StudioColors.BackgroundPrimary
                )
            }
            NavigationBarItem(
                selected = dest?.hasRoute<AssetsRoute>() == true,
                onClick = { onNavigate(AssetsRoute) },
                icon = {
                    Icon(
                        imageVector = if (dest?.hasRoute<AssetsRoute>() == true) Icons.Rounded.Brush else Icons.Outlined.Brush,
                        contentDescription = "Assets"
                    )
                },
                label = { Text("Assets") },
                colors = navigationItemColors(),
                modifier = Modifier.testTag("nav_assets_tab")
            )
            NavigationBarItem(
                selected = dest?.hasRoute<ProfileRoute>() == true,
                onClick = { onNavigate(ProfileRoute) },
                icon = {
                    Icon(
                        imageVector = if (dest?.hasRoute<ProfileRoute>() == true) Icons.Rounded.Person else Icons.Outlined.Person,
                        contentDescription = "Profile"
                    )
                },
                label = { Text("Profile") },
                colors = navigationItemColors(),
                modifier = Modifier.testTag("nav_profile_tab")
            )
        }
    }
}

@Composable
private fun StudioNavigationRail(
    navController: NavHostController,
    onNavigate: (Any) -> Unit,
    onNewClick: () -> Unit
) {
    val backStack by navController.currentBackStackEntryAsState()
    val dest = backStack?.destination

    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        header = {
            Box(
                modifier = Modifier
                    .padding(vertical = StudioSpacing.sm)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(StudioColors.SubtleHighlight)
                    .clickable(onClick = onNewClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "New Project",
                    tint = StudioColors.BackgroundPrimary
                )
            }
        }
    ) {
        NavigationRailItem(
            selected = dest?.hasRoute<HomeRoute>() == true,
            onClick = { onNavigate(HomeRoute) },
            icon = { Icon(Icons.Rounded.Home, contentDescription = "Home") },
            label = { Text("Home") },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = StudioColors.PureWhite,
                selectedTextColor = StudioColors.PureWhite,
                indicatorColor = StudioColors.SurfaceInteractive
            )
        )
        NavigationRailItem(
            selected = dest?.hasRoute<ProjectsRoute>() == true,
            onClick = { onNavigate(ProjectsRoute) },
            icon = { Icon(Icons.Rounded.FolderOpen, contentDescription = "Projects") },
            label = { Text("Projects") },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = StudioColors.PureWhite,
                selectedTextColor = StudioColors.PureWhite,
                indicatorColor = StudioColors.SurfaceInteractive
            )
        )
        NavigationRailItem(
            selected = dest?.hasRoute<AssetsRoute>() == true,
            onClick = { onNavigate(AssetsRoute) },
            icon = { Icon(Icons.Rounded.Brush, contentDescription = "Assets") },
            label = { Text("Assets") },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = StudioColors.PureWhite,
                selectedTextColor = StudioColors.PureWhite,
                indicatorColor = StudioColors.SurfaceInteractive
            )
        )
        NavigationRailItem(
            selected = dest?.hasRoute<ProfileRoute>() == true,
            onClick = { onNavigate(ProfileRoute) },
            icon = { Icon(Icons.Rounded.Person, contentDescription = "Profile") },
            label = { Text("Profile") },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = StudioColors.PureWhite,
                selectedTextColor = StudioColors.PureWhite,
                indicatorColor = StudioColors.SurfaceInteractive
            )
        )
    }
}

@Composable
private fun navigationItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = StudioColors.PureWhite,
    selectedTextColor = StudioColors.PureWhite,
    unselectedIconColor = StudioColors.TextSecondary,
    unselectedTextColor = StudioColors.TextSecondary,
    indicatorColor = StudioColors.SurfaceInteractive
)

private data class CanvasDimensionPreset(
    val label: String,
    val width: Int,
    val height: Int
)

private val DesignPresets = listOf(
    CanvasDimensionPreset("Social 1:1", 1080, 1080),
    CanvasDimensionPreset("Poster 4:5", 1080, 1350),
    CanvasDimensionPreset("Presentation 16:9", 1920, 1080),
    CanvasDimensionPreset("Thumbnail 16:9", 1280, 720),
    CanvasDimensionPreset("Business Card", 1050, 600),
    CanvasDimensionPreset("Custom", 1080, 1080)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewProjectBottomSheet(
    userId: String,
    initialType: ProjectType,
    onDismiss: () -> Unit,
    onCreateProject: (CreativeProject) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedType by remember(initialType) { mutableStateOf(initialType) }
    var projectName by remember { mutableStateOf("") }
    var selectedDesignPreset by remember { mutableStateOf(DesignPresets.first()) }
    var customWidth by remember { mutableStateOf("1080") }
    var customHeight by remember { mutableStateOf("1080") }
    var videoDimensions by remember { mutableStateOf(1920 to 1080) }

    val importPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val res = MediaStorageHelper.importMediaToUserStorage(context, userId, uri, "jpg")
                res.onSuccess { localUri ->
                    val bmp = MediaStorageHelper.decodeBitmapSafely(context, localUri, 1600)
                    onCreateProject(
                        CreativeProject(
                            name = projectName.ifBlank { "Photo Edit" },
                            projectType = ProjectType.PHOTO,
                            canvasWidth = bmp?.width?.coerceIn(64, 8192) ?: 1080,
                            canvasHeight = bmp?.height?.coerceIn(64, 8192) ?: 1350,
                            mediaUri = localUri
                        )
                    )
                }
            }
        }
    }

    val importVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val res = MediaStorageHelper.importMediaToUserStorage(context, userId, uri, "mp4")
                res.onSuccess { localUri ->
                    val clip = TimelineClip(
                        clipId = UUID.randomUUID().toString().take(8),
                        trackType = "VIDEO",
                        title = "Clip 1",
                        mediaUri = localUri,
                        sourceDurationMs = 10_000L,
                        trimStartMs = 0L,
                        trimEndMs = 10_000L
                    )
                    onCreateProject(
                        CreativeProject(
                            name = projectName.ifBlank { "Video Sequence" },
                            projectType = ProjectType.VIDEO,
                            canvasWidth = videoDimensions.first,
                            canvasHeight = videoDimensions.second,
                            mediaUri = localUri,
                            clips = listOf(clip)
                        )
                    )
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = StudioSpacing.lg, vertical = StudioSpacing.md),
            verticalArrangement = Arrangement.spacedBy(StudioSpacing.md)
        ) {
            Text(
                text = "New Creative Project",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            OutlinedTextField(
                value = projectName,
                onValueChange = { projectName = it },
                label = { Text("Project Name") },
                placeholder = { Text("e.g., Autumn Campaign") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = StudioColors.MetallicSilver,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("new_project_name_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(StudioSpacing.xs)
            ) {
                StudioFilterChip(
                    label = "Photo",
                    icon = Icons.Rounded.Image,
                    selected = selectedType == ProjectType.PHOTO,
                    onClick = { selectedType = ProjectType.PHOTO },
                    modifier = Modifier.weight(1f)
                )
                StudioFilterChip(
                    label = "Design",
                    icon = Icons.Rounded.Brush,
                    selected = selectedType == ProjectType.DESIGN,
                    onClick = { selectedType = ProjectType.DESIGN },
                    modifier = Modifier.weight(1f)
                )
                StudioFilterChip(
                    label = "Video",
                    icon = Icons.Rounded.Videocam,
                    selected = selectedType == ProjectType.VIDEO,
                    onClick = { selectedType = ProjectType.VIDEO },
                    modifier = Modifier.weight(1f)
                )
            }

            when (selectedType) {
                ProjectType.PHOTO -> {
                    Text(
                        text = "Import an image from your library or launch an empty photo studio canvas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(StudioSpacing.sm)
                    ) {
                        StudioSecondaryButton(
                            text = "Import Photo",
                            onClick = {
                                importPhotoLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        StudioPrimaryButton(
                            text = "Blank Studio",
                            onClick = {
                                onCreateProject(
                                    CreativeProject(
                                        name = projectName.ifBlank { "Untitled Photo" },
                                        projectType = ProjectType.PHOTO,
                                        canvasWidth = 1080,
                                        canvasHeight = 1350
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                            testTag = "create_photo_project_button"
                        )
                    }
                }

                ProjectType.DESIGN -> {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(StudioSpacing.xs)) {
                        items(DesignPresets) { preset ->
                            StudioFilterChip(
                                label = preset.label,
                                selected = selectedDesignPreset.label == preset.label,
                                onClick = { selectedDesignPreset = preset }
                            )
                        }
                    }

                    if (selectedDesignPreset.label == "Custom") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(StudioSpacing.sm)
                        ) {
                            OutlinedTextField(
                                value = customWidth,
                                onValueChange = { customWidth = it.filter(Char::isDigit).take(4) },
                                label = { Text("Width px") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = customHeight,
                                onValueChange = { customHeight = it.filter(Char::isDigit).take(4) },
                                label = { Text("Height px") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    StudioPrimaryButton(
                        text = "Create Design Canvas",
                        onClick = {
                            val w = if (selectedDesignPreset.label == "Custom") {
                                (customWidth.toIntOrNull() ?: 1080).coerceIn(200, 4096)
                            } else selectedDesignPreset.width
                            val h = if (selectedDesignPreset.label == "Custom") {
                                (customHeight.toIntOrNull() ?: 1080).coerceIn(200, 4096)
                            } else selectedDesignPreset.height
                            onCreateProject(
                                CreativeProject(
                                    name = projectName.ifBlank { "${selectedDesignPreset.label} Design" },
                                    projectType = ProjectType.DESIGN,
                                    canvasWidth = w,
                                    canvasHeight = h
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "create_design_project_button"
                    )
                }

                ProjectType.VIDEO -> {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(StudioSpacing.xs)) {
                        items(
                            listOf(
                                "16:9 Landscape" to (1920 to 1080),
                                "9:16 Vertical" to (1080 to 1920),
                                "1:1 Square" to (1080 to 1080)
                            )
                        ) { (label, dim) ->
                            StudioFilterChip(
                                label = label,
                                selected = videoDimensions == dim,
                                onClick = { videoDimensions = dim }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(StudioSpacing.sm)
                    ) {
                        StudioSecondaryButton(
                            text = "Import Video",
                            onClick = {
                                importVideoLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        StudioPrimaryButton(
                            text = "Empty Timeline",
                            onClick = {
                                onCreateProject(
                                    CreativeProject(
                                        name = projectName.ifBlank { "Video Sequence" },
                                        projectType = ProjectType.VIDEO,
                                        canvasWidth = videoDimensions.first,
                                        canvasHeight = videoDimensions.second
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                            testTag = "create_video_project_button"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(StudioSpacing.md))
        }
    }
}
