package com.example.feature.projects

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.common.UiState
import com.example.core.database.CreativeProject
import com.example.core.database.ProjectType
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectsViewModel(
    private val repository: ProjectsRepository,
    private val currentUserId: String
) : ViewModel() {

    val projectsState: StateFlow<UiState<List<CreativeProject>>> =
        repository.observeProjects(currentUserId)
            .map<List<CreativeProject>, UiState<List<CreativeProject>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing projects", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load projects"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private val _selectedFilter = MutableStateFlow<ProjectType?>(null)
    val selectedFilter: StateFlow<ProjectType?> = _selectedFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    fun setFilter(type: ProjectType?) {
        _selectedFilter.value = type
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearError() {
        _actionError.value = null
    }

    fun createProject(
        project: CreativeProject,
        onCreated: (String, ProjectType) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.createProject(currentUserId, project)
            result.fold(
                onSuccess = { projectId ->
                    onCreated(projectId, project.projectType)
                },
                onFailure = { err ->
                    _actionError.value = err.localizedMessage ?: "Failed to create project."
                }
            )
        }
    }

    fun renameProject(projectId: String, newName: String) {
        viewModelScope.launch {
            val res = repository.renameProject(currentUserId, projectId, newName)
            if (res.isFailure) {
                _actionError.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to rename project."
            }
        }
    }

    fun duplicateProject(project: CreativeProject) {
        viewModelScope.launch {
            val res = repository.duplicateProject(currentUserId, project)
            if (res.isFailure) {
                _actionError.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to duplicate project."
            }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            val res = repository.deleteProject(currentUserId, projectId)
            if (res.isFailure) {
                _actionError.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to delete project."
            }
        }
    }

    companion object {
        private const val TAG = "ProjectsVM"
        private const val STOP_TIMEOUT_MILLIS = 5000L

        fun provideFactory(
            repository: ProjectsRepository,
            currentUserId: String
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ProjectsViewModel(repository, currentUserId)
            }
        }
    }
}
