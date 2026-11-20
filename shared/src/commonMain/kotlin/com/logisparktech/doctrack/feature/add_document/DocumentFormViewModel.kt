package com.logisparktech.doctrack.feature.add_document

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logisparktech.doctrack.domain.model.Attachment
import com.logisparktech.doctrack.domain.model.AttachmentType
import com.logisparktech.doctrack.domain.model.Category
import com.logisparktech.doctrack.domain.model.DocumentInput
import com.logisparktech.doctrack.domain.model.PickedFile
import com.logisparktech.doctrack.domain.repository.AttachmentRepository
import com.logisparktech.doctrack.domain.repository.CategoryRepository
import com.logisparktech.doctrack.domain.repository.DocumentRepository
import com.logisparktech.doctrack.domain.repository.ReminderRepository
import com.logisparktech.doctrack.domain.repository.SettingsRepository
import com.logisparktech.doctrack.domain.usecase.DocumentValidationError
import com.logisparktech.doctrack.domain.usecase.PendingAttachment
import com.logisparktech.doctrack.domain.usecase.SaveDocumentUseCase
import com.logisparktech.doctrack.domain.usecase.validateDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** A picked file waiting to be saved, with a local key for list identity. */
data class PendingItem(val key: Int, val file: PickedFile, val type: AttachmentType)

data class DocumentFormState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val notFound: Boolean = false,
    val name: String = "",
    val documentNumber: String = "",
    val categoryId: Long = 0,
    val issuingAuthority: String = "",
    val issueDate: LocalDate? = null,
    val expiryDate: LocalDate? = null,
    val renewalDate: LocalDate? = null,
    val description: String = "",
    val notes: String = "",
    val renewalInProgress: Boolean = false,
    val remindersEnabled: Boolean = true,
    val reminderDays: List<Int> = emptyList(),
    val defaultReminderDays: List<Int> = emptyList(),
    val categories: List<Category> = emptyList(),
    val existingAttachments: List<Attachment> = emptyList(),
    val removedAttachmentIds: Set<Long> = emptySet(),
    val pending: List<PendingItem> = emptyList(),
    val errors: Set<DocumentValidationError> = emptySet(),
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val savedId: Long? = null,
    val dirty: Boolean = false,
) {
    val visibleAttachments: List<Attachment> get() = existingAttachments.filter { it.id !in removedAttachmentIds }

    fun imageFor(type: AttachmentType): Pair<PendingItem?, Attachment?> {
        val pendingImage = pending.lastOrNull { it.type == type }
        val existing = if (pendingImage == null) visibleAttachments.lastOrNull { it.type == type } else null
        return pendingImage to existing
    }

    /** All reminder chips to show: defaults plus any custom values. */
    val reminderOptions: List<Int> get() = (defaultReminderDays + reminderDays).distinct().sortedDescending()
}

class DocumentFormViewModel(
    private val documentId: Long?,
    presetExpiry: LocalDate?,
    private val documents: DocumentRepository,
    private val attachments: AttachmentRepository,
    private val reminders: ReminderRepository,
    private val categories: CategoryRepository,
    private val settings: SettingsRepository,
    private val saveDocument: SaveDocumentUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(DocumentFormState())
    val state: StateFlow<DocumentFormState> = _state.asStateFlow()
    private var nextKey = 0

    init {
        viewModelScope.launch {
            val s = settings.current()
            val cats = categories.getCategories()
            if (documentId == null) {
                _state.value = DocumentFormState(
                    loading = false,
                    categories = cats,
                    categoryId = cats.firstOrNull { it.name == "custom_documents" }?.id ?: cats.firstOrNull()?.id ?: 0,
                    expiryDate = presetExpiry,
                    reminderDays = s.defaultReminderDays,
                    defaultReminderDays = s.defaultReminderDays,
                )
            } else {
                val doc = documents.getDocument(documentId)
                if (doc == null) {
                    _state.value = DocumentFormState(loading = false, notFound = true)
                    return@launch
                }
                val rems = reminders.getReminders(documentId)
                _state.value = DocumentFormState(
                    loading = false,
                    isEdit = true,
                    name = doc.name,
                    documentNumber = doc.documentNumber.orEmpty(),
                    categoryId = doc.category.id,
                    issuingAuthority = doc.issuingAuthority.orEmpty(),
                    issueDate = doc.issueDate,
                    expiryDate = doc.expiryDate,
                    renewalDate = doc.renewalDate,
                    description = doc.description,
                    notes = doc.notes,
                    renewalInProgress = doc.renewalInProgress,
                    remindersEnabled = rems.any { it.enabled } || doc.expiryDate == null,
                    reminderDays = rems.filter { it.enabled }.map { it.daysBefore }
                        .ifEmpty { if (doc.expiryDate == null) s.defaultReminderDays else emptyList() },
                    defaultReminderDays = s.defaultReminderDays,
                    categories = cats,
                    existingAttachments = attachments.getAttachments(documentId),
                )
            }
        }
    }

    private fun edit(block: (DocumentFormState) -> DocumentFormState) {
        _state.update { block(it).copy(dirty = true, saveFailed = false) }
    }

    fun setName(v: String) = edit { it.copy(name = v, errors = it.errors - DocumentValidationError.NAME_REQUIRED) }
    fun setNumber(v: String) = edit { it.copy(documentNumber = v) }
    fun setCategory(id: Long) = edit { it.copy(categoryId = id, errors = it.errors - DocumentValidationError.CATEGORY_REQUIRED) }
    fun setAuthority(v: String) = edit { it.copy(issuingAuthority = v) }
    fun setIssueDate(v: LocalDate?) = edit { it.copy(issueDate = v, errors = it.errors - DocumentValidationError.EXPIRY_BEFORE_ISSUE) }
    fun setExpiryDate(v: LocalDate?) = edit { it.copy(expiryDate = v, errors = it.errors - DocumentValidationError.EXPIRY_BEFORE_ISSUE) }
    fun setRenewalDate(v: LocalDate?) = edit { it.copy(renewalDate = v) }
    fun setDescription(v: String) = edit { it.copy(description = v) }
    fun setNotes(v: String) = edit { it.copy(notes = v) }
    fun setRenewalInProgress(v: Boolean) = edit { it.copy(renewalInProgress = v) }
    fun setRemindersEnabled(v: Boolean) = edit { it.copy(remindersEnabled = v) }

    fun toggleReminder(days: Int) = edit {
        it.copy(reminderDays = if (days in it.reminderDays) it.reminderDays - days else (it.reminderDays + days).sortedDescending())
    }

    fun addCustomReminder(days: Int) = edit {
        it.copy(reminderDays = (it.reminderDays + days).distinct().sortedDescending(), remindersEnabled = true)
    }

    fun addPicked(file: PickedFile, type: AttachmentType) = edit { state ->
        val withoutSameSlot = if (type == AttachmentType.FRONT_IMAGE || type == AttachmentType.BACK_IMAGE) {
            state.pending.filterNot { it.type == type }
        } else state.pending
        state.copy(pending = withoutSameSlot + PendingItem(nextKey++, file, type))
    }

    fun removePending(key: Int) = edit { it.copy(pending = it.pending.filterNot { p -> p.key == key }) }

    fun removeExisting(id: Long) = edit { it.copy(removedAttachmentIds = it.removedAttachmentIds + id) }

    /** Removes whichever image currently fills the front/back slot. */
    fun removeImageSlot(type: AttachmentType) {
        val (pending, existing) = _state.value.imageFor(type)
        when {
            pending != null -> removePending(pending.key)
            existing != null -> removeExisting(existing.id)
        }
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val input = DocumentInput(
            name = s.name,
            documentNumber = s.documentNumber,
            categoryId = s.categoryId,
            issuingAuthority = s.issuingAuthority,
            issueDate = s.issueDate,
            expiryDate = s.expiryDate,
            renewalDate = s.renewalDate,
            description = s.description,
            notes = s.notes,
            renewalInProgress = s.renewalInProgress,
        )
        val errors = validateDocument(input)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            // Front/back replacements: existing slot images are removed when a new one is pending.
            val replacedSlots = s.pending.map { it.type }.filter { it == AttachmentType.FRONT_IMAGE || it == AttachmentType.BACK_IMAGE }.toSet()
            val removed = s.removedAttachmentIds + s.visibleAttachments.filter { it.type in replacedSlots }.map { it.id }
            runCatching {
                saveDocument(
                    id = documentId,
                    input = input,
                    reminderDays = if (s.remindersEnabled) s.reminderDays else emptyList(),
                    newAttachments = s.pending.map { PendingAttachment(it.file, it.type) },
                    removedAttachmentIds = removed.toList(),
                )
            }.onSuccess { id ->
                _state.update { it.copy(saving = false, savedId = id, dirty = false) }
            }.onFailure {
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }
}
