package com.logisparktech.doctrack.di

import com.logisparktech.doctrack.core.common.DateProvider
import com.logisparktech.doctrack.core.common.SystemDateProvider
import com.logisparktech.doctrack.core.database.DatabaseDriverFactory
import com.logisparktech.doctrack.core.notification.ReminderSyncService
import com.logisparktech.doctrack.data.backup.BackupManager
import com.logisparktech.doctrack.data.repository.SqlAttachmentRepository
import com.logisparktech.doctrack.data.repository.SqlCategoryRepository
import com.logisparktech.doctrack.data.repository.SqlDocumentRepository
import com.logisparktech.doctrack.data.repository.SqlReminderRepository
import com.logisparktech.doctrack.data.repository.SqlRenewalRepository
import com.logisparktech.doctrack.data.repository.SqlSettingsRepository
import com.logisparktech.doctrack.db.DocTrackDatabase
import com.logisparktech.doctrack.domain.repository.AttachmentRepository
import com.logisparktech.doctrack.domain.repository.CategoryRepository
import com.logisparktech.doctrack.domain.repository.DocumentRepository
import com.logisparktech.doctrack.domain.repository.ReminderRepository
import com.logisparktech.doctrack.domain.repository.RenewalRepository
import com.logisparktech.doctrack.domain.repository.SettingsRepository
import com.logisparktech.doctrack.domain.usecase.AttachmentManager
import com.logisparktech.doctrack.domain.usecase.DeleteDocumentUseCase
import com.logisparktech.doctrack.domain.usecase.RenewDocumentUseCase
import com.logisparktech.doctrack.domain.usecase.SaveDocumentUseCase
import com.logisparktech.doctrack.feature.backup.BackupViewModel
import com.logisparktech.doctrack.feature.calendar.CalendarViewModel
import com.logisparktech.doctrack.feature.categories.CategoriesViewModel
import com.logisparktech.doctrack.feature.dashboard.DashboardViewModel
import com.logisparktech.doctrack.feature.add_document.DocumentFormViewModel
import com.logisparktech.doctrack.feature.document_details.DocumentDetailViewModel
import com.logisparktech.doctrack.feature.documents.DocumentsViewModel
import com.logisparktech.doctrack.feature.reminders.RemindersViewModel
import com.logisparktech.doctrack.feature.renewal.RenewalViewModel
import com.logisparktech.doctrack.feature.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

/** Platform services: database driver, file storage, notifications, biometrics, share/open. */
expect val platformModule: Module

val APP_SCOPE = named("appScope")

val dataModule = module {
    single<CoroutineScope>(APP_SCOPE) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single<DateProvider> { SystemDateProvider() }
    single { DocTrackDatabase(get<DatabaseDriverFactory>().create()) }

    single<CategoryRepository> { SqlCategoryRepository(get()) }
    single<DocumentRepository> { SqlDocumentRepository(get(), get()) }
    single<AttachmentRepository> { SqlAttachmentRepository(get(), get()) }
    single<ReminderRepository> { SqlReminderRepository(get()) }
    single<RenewalRepository> { SqlRenewalRepository(get()) }
    single<SettingsRepository> { SqlSettingsRepository(get()) }

    single { AttachmentManager(get(), get()) }
    factory { SaveDocumentUseCase(get(), get()) }
    factory { DeleteDocumentUseCase(get(), get()) }
    factory { RenewDocumentUseCase(get()) }
    single { ReminderSyncService(get(), get(), get(), get(), get()) }
    single { BackupManager(get(), get(), get(), get()) }
}

val viewModelModule = module {
    viewModel { DashboardViewModel(get(), get(), get()) }
    viewModel { params -> DocumentsViewModel(params.get(), get(), get(), get(), get()) }
    viewModel { params ->
        val id: Long = params.get()
        val preset: String = params.get()
        DocumentFormViewModel(
            documentId = id.takeIf { it > 0 },
            presetExpiry = preset.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            documents = get(), attachments = get(), reminders = get(), categories = get(), settings = get(),
            saveDocument = get(),
        )
    }
    viewModel { params -> DocumentDetailViewModel(params.get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { params -> RenewalViewModel(params.get(), get(), get(), get()) }
    viewModel { CalendarViewModel(get(), get(), get()) }
    viewModel { RemindersViewModel(get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { CategoriesViewModel(get()) }
    viewModel { BackupViewModel(get(), get()) }
}

/** Starts Koin once and kicks off background housekeeping. Safe to call more than once. */
fun initKoin(config: KoinAppDeclaration? = null): KoinApplication? {
    if (KoinPlatform.getKoinOrNull() != null) return null
    val app = startKoin {
        config?.invoke(this)
        modules(platformModule, dataModule, viewModelModule)
    }
    val koin = app.koin
    val scope = koin.get<CoroutineScope>(APP_SCOPE)
    koin.get<ReminderSyncService>().start(scope)
    scope.launch { runCatching { koin.get<AttachmentManager>().cleanupOrphans() } }
    return app
}
