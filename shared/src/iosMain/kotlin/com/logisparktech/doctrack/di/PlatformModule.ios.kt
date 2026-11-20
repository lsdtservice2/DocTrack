package com.logisparktech.doctrack.di

import com.logisparktech.doctrack.core.database.DatabaseDriverFactory
import com.logisparktech.doctrack.core.notification.NotificationScheduler
import com.logisparktech.doctrack.core.platform.BiometricAuthenticator
import com.logisparktech.doctrack.core.platform.PlatformActions
import com.logisparktech.doctrack.core.storage.FileStorage
import com.logisparktech.doctrack.platform.IosBiometricAuthenticator
import com.logisparktech.doctrack.platform.IosFileStorage
import com.logisparktech.doctrack.platform.IosNotificationScheduler
import com.logisparktech.doctrack.platform.IosPlatformActions
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single { DatabaseDriverFactory() }
    single<FileStorage> { IosFileStorage() }
    single<NotificationScheduler> { IosNotificationScheduler() }
    single<PlatformActions> { IosPlatformActions() }
    single<BiometricAuthenticator> { IosBiometricAuthenticator() }
}
