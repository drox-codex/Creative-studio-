package com.example.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.common.MediaStorageHelper
import com.example.core.storage.SettingsPreferencesRepository
import com.example.core.storage.StudioSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val appContext: Context,
    private val prefsRepo: SettingsPreferencesRepository,
    private val currentUserId: String
) : ViewModel() {

    val settings: StateFlow<StudioSettings> = prefsRepo.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = StudioSettings()
        )

    private val _storageBytes = MutableStateFlow(0L)
    val storageBytes: StateFlow<Long> = _storageBytes.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        refreshStorageUsage()
    }

    fun refreshStorageUsage() {
        viewModelScope.launch {
            _storageBytes.value = MediaStorageHelper.computeStorageBytes(appContext, currentUserId)
        }
    }

    fun setThemeVariant(theme: String) {
        viewModelScope.launch { prefsRepo.setThemeVariant(theme) }
    }

    fun setLanguage(language: String) {
        viewModelScope.launch { prefsRepo.setLanguage(language) }
    }

    fun setExportFormat(format: String) {
        viewModelScope.launch { prefsRepo.setExportFormat(format) }
    }

    fun setExportQuality(quality: Int) {
        viewModelScope.launch { prefsRepo.setExportQuality(quality) }
    }

    fun setVideoResolution(resolution: String) {
        viewModelScope.launch { prefsRepo.setVideoResolution(resolution) }
    }

    fun setAutosave(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutosave(enabled) }
    }

    fun setReducedMotion(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setReducedMotion(enabled) }
    }

    fun clearExportCache() {
        viewModelScope.launch {
            MediaStorageHelper.clearExportCache(appContext)
            refreshStorageUsage()
            _statusMessage.value = "Export cache cleared"
        }
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    companion object {
        fun provideFactory(
            appContext: Context,
            prefsRepo: SettingsPreferencesRepository,
            currentUserId: String
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    appContext = appContext.applicationContext,
                    prefsRepo = prefsRepo,
                    currentUserId = currentUserId
                )
            }
        }
    }
}
