package com.example.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioDataStore: DataStore<Preferences> by preferencesDataStore(name = "creative_studio_prefs")

data class StudioSettings(
    val themeVariant: String = "obsidian",
    val language: String = "English",
    val defaultExportFormat: String = "PNG",
    val defaultExportQuality: Int = 95,
    val defaultVideoResolution: String = "1080p",
    val autosaveEnabled: Boolean = true,
    val reducedMotion: Boolean = false
)

class SettingsPreferencesRepository(private val context: Context) {
    private object Keys {
        val THEME_VARIANT = stringPreferencesKey("theme_variant")
        val LANGUAGE = stringPreferencesKey("language")
        val EXPORT_FORMAT = stringPreferencesKey("export_format")
        val EXPORT_QUALITY = intPreferencesKey("export_quality")
        val VIDEO_RESOLUTION = stringPreferencesKey("video_resolution")
        val AUTOSAVE = booleanPreferencesKey("autosave")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
    }

    val settingsFlow: Flow<StudioSettings> = context.studioDataStore.data.map { prefs ->
        StudioSettings(
            themeVariant = prefs[Keys.THEME_VARIANT] ?: "obsidian",
            language = prefs[Keys.LANGUAGE] ?: "English",
            defaultExportFormat = prefs[Keys.EXPORT_FORMAT] ?: "PNG",
            defaultExportQuality = prefs[Keys.EXPORT_QUALITY] ?: 95,
            defaultVideoResolution = prefs[Keys.VIDEO_RESOLUTION] ?: "1080p",
            autosaveEnabled = prefs[Keys.AUTOSAVE] ?: true,
            reducedMotion = prefs[Keys.REDUCED_MOTION] ?: false
        )
    }

    suspend fun setThemeVariant(theme: String) {
        context.studioDataStore.edit { it[Keys.THEME_VARIANT] = theme }
    }

    suspend fun setLanguage(language: String) {
        context.studioDataStore.edit { it[Keys.LANGUAGE] = language }
    }

    suspend fun setExportFormat(format: String) {
        context.studioDataStore.edit { it[Keys.EXPORT_FORMAT] = format }
    }

    suspend fun setExportQuality(quality: Int) {
        context.studioDataStore.edit { it[Keys.EXPORT_QUALITY] = quality.coerceIn(70, 100) }
    }

    suspend fun setVideoResolution(resolution: String) {
        context.studioDataStore.edit { it[Keys.VIDEO_RESOLUTION] = resolution }
    }

    suspend fun setAutosave(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.AUTOSAVE] = enabled }
    }

    suspend fun setReducedMotion(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.REDUCED_MOTION] = enabled }
    }
}
