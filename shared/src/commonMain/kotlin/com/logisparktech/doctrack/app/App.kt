package com.logisparktech.doctrack.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.logisparktech.doctrack.app.navigation.AppNavigation
import com.logisparktech.doctrack.app.theme.DocTrackTheme
import com.logisparktech.doctrack.core.calendar.DateFormatter
import com.logisparktech.doctrack.core.localization.LocalAppSettings
import com.logisparktech.doctrack.core.localization.LocalDateFormatter
import com.logisparktech.doctrack.core.localization.LocalStrings
import com.logisparktech.doctrack.core.localization.stringsFor
import com.logisparktech.doctrack.domain.model.AppSettings
import com.logisparktech.doctrack.domain.repository.SettingsRepository
import org.koin.compose.koinInject

/** Root composable shared by Android and iOS. Opens directly to the dashboard (no login). */
@Composable
fun App() {
    val settingsRepository = koinInject<SettingsRepository>()
    val settings by settingsRepository.settings.collectAsState(initial = null)
    val current: AppSettings = settings ?: run {
        // First frame while the database loads.
        DocTrackTheme(com.logisparktech.doctrack.domain.model.ThemeMode.SYSTEM) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        }
        return
    }
    val strings = stringsFor(current.language)
    val formatter = remember(strings, current.calendarType) { DateFormatter(strings, current.calendarType) }

    CompositionLocalProvider(
        LocalStrings provides strings,
        LocalDateFormatter provides formatter,
        LocalAppSettings provides current,
    ) {
        DocTrackTheme(current.theme) {
            AppLockGate(enabled = current.biometricLockEnabled) {
                AppNavigation()
            }
        }
    }
}
