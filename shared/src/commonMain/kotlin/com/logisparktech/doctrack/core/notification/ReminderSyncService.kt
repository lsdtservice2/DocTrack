package com.logisparktech.doctrack.core.notification

import com.logisparktech.doctrack.core.common.DateProvider
import com.logisparktech.doctrack.core.localization.stringsFor
import com.logisparktech.doctrack.domain.repository.DocumentRepository
import com.logisparktech.doctrack.domain.repository.ReminderRepository
import com.logisparktech.doctrack.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps OS-scheduled notifications in sync with the database. Any change to documents,
 * reminders or settings (add, edit, delete, renew, language, reminder time) triggers a full,
 * idempotent re-plan: everything previously scheduled is replaced, so duplicates cannot occur.
 */
class ReminderSyncService(
    private val documents: DocumentRepository,
    private val reminders: ReminderRepository,
    private val settings: SettingsRepository,
    private val scheduler: NotificationScheduler,
    private val dateProvider: DateProvider,
) {
    private val mutex = Mutex()
    private var observeJob: Job? = null

    /** Reminders that are still in the future, for the Notifications screen. */
    fun upcoming(): Flow<List<PlannedReminder>> =
        combine(
            documents.observeDocuments(),
            reminders.observeAll(),
            settings.settings,
            dateProvider.todayFlow(),
        ) { docs, rems, s, _ ->
            ReminderPlanner.plan(docs, rems, s, dateProvider.nowMillis(), dateProvider.timeZone())
        }

    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        if (observeJob?.isActive == true) return
        observeJob = scope.launch {
            combine(documents.observeDocuments(), reminders.observeAll(), settings.settings) { _, _, _ -> }
                .debounce(500)
                .collect { runCatching { resync() } }
        }
    }

    suspend fun resync() = mutex.withLock {
        val s = settings.current()
        val strings = stringsFor(s.language)
        val planned = ReminderPlanner.plan(
            documents.observeDocuments().first(),
            reminders.observeAll().first(),
            s,
            dateProvider.nowMillis(),
            dateProvider.timeZone(),
        ).take(scheduler.maxPending)
        scheduler.replaceAll(
            planned.map {
                ScheduledNotification(
                    id = it.id,
                    documentId = it.documentId,
                    triggerAtMillis = it.triggerAtMillis,
                    title = strings.notificationTitle(it.documentName),
                    body = strings.notificationBody(it.documentName, it.daysBefore),
                )
            }
        )
    }
}
