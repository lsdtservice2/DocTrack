package com.logisparktech.doctrack.platform

import android.content.Context
import com.logisparktech.doctrack.core.database.DatabaseDriverFactory
import com.logisparktech.doctrack.core.localization.stringsFor
import com.logisparktech.doctrack.core.notification.NotificationScheduler
import com.logisparktech.doctrack.core.platform.BiometricAuthenticator
import com.logisparktech.doctrack.core.platform.PlatformActions
import com.logisparktech.doctrack.core.storage.FileStorage
import com.logisparktech.doctrack.di.APP_SCOPE
import com.logisparktech.doctrack.di.initKoin
import com.logisparktech.doctrack.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext

/** Starts DocTrack's dependency graph. Call from Application.onCreate. */
fun startDocTrack(context: Context) {
    ensureKoin(context.applicationContext)
}

internal fun ensureKoin(context: Context) {
    val app = initKoin { androidContext(context) } ?: return
    val koin = app.koin
    // Keep the notification channel name in the selected language.
    koin.get<CoroutineScope>(APP_SCOPE).launch {
        koin.get<SettingsRepository>().settings.map { it.language }.distinctUntilChanged().collect { lang ->
            val s = stringsFor(lang)
            ensureNotificationChannel(context, s.notificationChannelName, s.notificationChannelDescription)
        }
    }
}


internal fun androidPlatformModule() = org.koin.dsl.module {
    single { DatabaseDriverFactory(get<Context>()) }
    single<FileStorage> { AndroidFileStorage(get()) }
    single<NotificationScheduler> { AndroidNotificationScheduler(get()) }
    single<PlatformActions> { AndroidPlatformActions(get()) }
    single<BiometricAuthenticator> { AndroidBiometricAuthenticator(get()) }
}
