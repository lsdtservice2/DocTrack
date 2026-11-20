package com.logisparktech.doctrack.feature.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logisparktech.doctrack.core.common.DateProvider
import com.logisparktech.doctrack.core.datetime.ExpiryCalculator
import com.logisparktech.doctrack.domain.model.Category
import com.logisparktech.doctrack.domain.model.DocumentStatus
import com.logisparktech.doctrack.domain.model.DocumentWithExpiry
import com.logisparktech.doctrack.domain.repository.CategoryRepository
import com.logisparktech.doctrack.domain.repository.DocumentRepository
import com.logisparktech.doctrack.domain.repository.SettingsRepository
import com.logisparktech.doctrack.feature.dashboard.UPCOMING_RENEWAL_FILTER
import com.logisparktech.doctrack.feature.dashboard.isUpcomingRenewal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Status filter: a [DocumentStatus] or the upcoming-renewal pseudo filter. */
data class StatusFilter(val status: DocumentStatus? = null, val upcomingRenewal: Boolean = false) {
    companion object {
        fun parse(value: String): StatusFilter = when {
            value == UPCOMING_RENEWAL_FILTER -> StatusFilter(upcomingRenewal = true)
            else -> StatusFilter(DocumentStatus.entries.firstOrNull { it.name == value })
        }
    }
}

data class DocumentsState(
    val loading: Boolean = true,
    val items: List<DocumentWithExpiry> = emptyList(),
    val totalCount: Int = 0,
    val categories: List<Category> = emptyList(),
    val query: String = "",
    val categoryId: Long? = null,
    val statusFilter: StatusFilter = StatusFilter(),
)

class DocumentsViewModel(
    initialStatus: String,
    documents: DocumentRepository,
    categories: CategoryRepository,
    settings: SettingsRepository,
    dateProvider: DateProvider,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val categoryId = MutableStateFlow<Long?>(null)
    private val statusFilter = MutableStateFlow(StatusFilter.parse(initialStatus))

    private val filters = combine(query, categoryId, statusFilter) { q, c, st -> Triple(q, c, st) }

    val state: StateFlow<DocumentsState> = combine(
        documents.observeDocuments(),
        categories.observeCategories(),
        settings.settings,
        dateProvider.todayFlow(),
        filters,
    ) { docs, cats, s, today, (q, c, st) ->
        val all = docs.map { ExpiryCalculator.withExpiry(it, today, s.expiryWarningDays) }
        val needle = q.trim().lowercase()
        val filtered = all.filter { item ->
            val d = item.document
            (needle.isEmpty() || d.name.lowercase().contains(needle) ||
                d.documentNumber?.lowercase()?.contains(needle) == true ||
                d.issuingAuthority?.lowercase()?.contains(needle) == true ||
                d.notes.lowercase().contains(needle)) &&
                (c == null || d.category.id == c) &&
                (st.status == null || item.expiry.status == st.status) &&
                (!st.upcomingRenewal || isUpcomingRenewal(d, today))
        }
        DocumentsState(false, filtered, all.size, cats, q, c, st)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentsState())

    fun setQuery(value: String) { query.value = value }
    fun setCategory(id: Long?) { categoryId.value = id }
    fun setStatus(filter: StatusFilter) { statusFilter.value = filter }
}
