package com.logisparktech.doctrack.feature.renewal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logisparktech.doctrack.core.common.DateProvider
import com.logisparktech.doctrack.domain.model.Document
import com.logisparktech.doctrack.domain.repository.DocumentRepository
import com.logisparktech.doctrack.domain.usecase.RenewDocumentUseCase
import com.logisparktech.doctrack.domain.usecase.RenewalValidationError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

data class RenewalState(
    val loading: Boolean = true,
    val document: Document? = null,
    val newIssueDate: LocalDate? = null,
    val newExpiryDate: LocalDate? = null,
    val renewalDate: LocalDate? = null,
    val notes: String = "",
    val errors: Set<RenewalValidationError> = emptySet(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val failed: Boolean = false,
)

class RenewalViewModel(
    private val documentId: Long,
    private val documents: DocumentRepository,
    private val renew: RenewDocumentUseCase,
    private val dateProvider: DateProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(RenewalState())
    val state: StateFlow<RenewalState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val doc = documents.getDocument(documentId)
            val today = dateProvider.today()
            // Suggest the same validity period as the previous term.
            val suggestedExpiry = if (doc?.issueDate != null && doc.expiryDate != null) {
                val term = doc.issueDate.daysUntil(doc.expiryDate)
                if (term > 0) today.plus(DatePeriod(days = term)) else null
            } else null
            _state.value = RenewalState(
                loading = false,
                document = doc,
                newIssueDate = today,
                newExpiryDate = suggestedExpiry,
                renewalDate = today,
            )
        }
    }

    fun setIssueDate(v: LocalDate?) = _state.update { it.copy(newIssueDate = v, errors = emptySet()) }
    fun setExpiryDate(v: LocalDate?) = _state.update { it.copy(newExpiryDate = v, errors = emptySet()) }
    fun setRenewalDate(v: LocalDate?) = _state.update { it.copy(renewalDate = v) }
    fun setNotes(v: String) = _state.update { it.copy(notes = v) }

    fun save() {
        val s = _state.value
        val doc = s.document ?: return
        val errors = renew.validate(doc.expiryDate, s.newIssueDate, s.newExpiryDate)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        _state.update { it.copy(saving = true, failed = false) }
        viewModelScope.launch {
            runCatching {
                renew(documentId, s.newIssueDate, s.newExpiryDate!!, s.renewalDate ?: dateProvider.today(), s.notes)
            }.onSuccess { _state.update { it.copy(saving = false, saved = true) } }
                .onFailure { _state.update { it.copy(saving = false, failed = true) } }
        }
    }
}
