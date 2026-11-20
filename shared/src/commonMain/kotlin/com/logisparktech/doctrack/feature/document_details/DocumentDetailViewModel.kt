package com.logisparktech.doctrack.feature.document_details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logisparktech.doctrack.core.common.DateProvider
import com.logisparktech.doctrack.core.datetime.ExpiryCalculator
import com.logisparktech.doctrack.domain.model.Attachment
import com.logisparktech.doctrack.domain.model.DocumentWithExpiry
import com.logisparktech.doctrack.domain.model.Reminder
import com.logisparktech.doctrack.domain.model.RenewalRecord
import com.logisparktech.doctrack.domain.repository.AttachmentRepository
import com.logisparktech.doctrack.domain.repository.DocumentRepository
import com.logisparktech.doctrack.domain.repository.ReminderRepository
import com.logisparktech.doctrack.domain.repository.RenewalRepository
import com.logisparktech.doctrack.domain.repository.SettingsRepository
import com.logisparktech.doctrack.domain.usecase.AttachmentManager
import com.logisparktech.doctrack.domain.usecase.DeleteDocumentUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DocumentDetailState(
    val loading: Boolean = true,
    val item: DocumentWithExpiry? = null,
    val attachments: List<Attachment> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val history: List<RenewalRecord> = emptyList(),
)

class DocumentDetailViewModel(
    private val documentId: Long,
    private val documents: DocumentRepository,
    attachments: AttachmentRepository,
    private val reminders: ReminderRepository,
    renewals: RenewalRepository,
    settings: SettingsRepository,
    dateProvider: DateProvider,
    private val deleteDocument: DeleteDocumentUseCase,
    private val attachmentManager: AttachmentManager,
) : ViewModel() {

    private val documentWithExpiry = combine(
        documents.observeDocument(documentId), settings.settings, dateProvider.todayFlow(),
    ) { doc, s, today -> doc?.let { ExpiryCalculator.withExpiry(it, today, s.expiryWarningDays) } }

    val state: StateFlow<DocumentDetailState> = combine(
        documentWithExpiry,
        attachments.observeAttachments(documentId),
        reminders.observeReminders(documentId),
        renewals.observeHistory(documentId),
    ) { item, atts, rems, history ->
        DocumentDetailState(false, item, atts, rems.filter { it.enabled }, history)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentDetailState())

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    fun delete() = viewModelScope.launch {
        deleteDocument(documentId)
        _deleted.value = true
    }

    fun setRenewalInProgress(value: Boolean) = viewModelScope.launch {
        documents.setRenewalInProgress(documentId, value)
    }

    fun addReminder(days: Int) = viewModelScope.launch { reminders.addReminder(documentId, days) }

    fun removeReminder(days: Int) = viewModelScope.launch {
        val remaining = state.value.reminders.map { it.daysBefore }.filter { it != days }
        reminders.setReminders(documentId, remaining)
    }

    fun deleteAttachment(id: Long) = viewModelScope.launch { attachmentManager.delete(id) }
}
