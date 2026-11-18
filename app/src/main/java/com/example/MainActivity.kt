package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.auth.AuthViewModel
import com.example.core.database.configureAppCheck
import com.example.core.designsystem.CreativeStudioTheme
import com.example.core.navigation.CreativeStudioApp
import com.example.core.storage.SettingsPreferencesRepository
import com.example.core.storage.StudioSettings

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModel.provideFactory(applicationContext)
    }

    private val settingsRepository by lazy {
        SettingsPreferencesRepository(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        // App Check MUST be configured before rendering Compose UI or initializing ViewModels
        configureAppCheck(this, intent)

        setContent {
            val settings by settingsRepository.settingsFlow.collectAsStateWithLifecycle(
                initialValue = StudioSettings()
            )
            CreativeStudioTheme(
                themeVariant = settings.themeVariant,
                reducedMotion = settings.reducedMotion
            ) {
                CreativeStudioApp(
                    authViewModel = authViewModel,
                    settingsRepository = settingsRepository,
                    settings = settings
                )
            }
        }
    }
}
