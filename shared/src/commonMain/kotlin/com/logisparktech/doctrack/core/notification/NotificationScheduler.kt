package com.logisparktech.doctrack.core.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScheduledNotification(
    /** Stable id derived from document and reminder offset, which prevents duplicates. */
    val id: Int,
    val documentId: Long,
    val triggerAtMillis: Long,
    val title: String,
    val body: String,
)

interface NotificationScheduler {
    /** Upper bound of pending notifications the OS allows (iOS keeps only 64). */
    val maxPending: Int

    suspend fun hasPermission(): Boolean

    /** Cancels everything previously scheduled by the app and schedules [notifications]. */
    suspend fun replaceAll(notifications: List<ScheduledNotification>)
}

/** Bridges notification taps from platform code to navigation. */
object NotificationNavigator {
    private val _pendingDocumentId = MutableStateFlow<Long?>(null)
    val pendingDocumentId: StateFlow<Long?> = _pendingDocumentId.asStateFlow()

    fun openDocument(documentId: Long) { _pendingDocumentId.value = documentId }
    fun consume() { _pendingDocumentId.value = null }
}
