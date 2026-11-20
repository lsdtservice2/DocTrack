package com.logisparktech.doctrack.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logisparktech.doctrack.core.common.DateProvider
import com.logisparktech.doctrack.core.datetime.ExpiryCalculator
import com.logisparktech.doctrack.domain.model.Document
import com.logisparktech.doctrack.domain.model.DocumentStatus
import com.logisparktech.doctrack.domain.model.DocumentWithExpiry
import com.logisparktech.doctrack.domain.repository.DocumentRepository
import com.logisparktech.doctrack.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

data class DashboardState(
    val loading: Boolean = true,
    val total: Int = 0,
    val active: Int = 0,
    val expiringSoon: Int = 0,
    val expired: Int = 0,
    val upcomingRenewals: Int = 0,
    val expiringSoonList: List<DocumentWithExpiry> = emptyList(),
    val todayList: List<DocumentWithExpiry> = emptyList(),
    val upcomingList: List<DocumentWithExpiry> = emptyList(),
    val recentList: List<DocumentWithExpiry> = emptyList(),
)

/** Pure aggregation so it can be unit tested. */
fun buildDashboard(documents: List<Document>, today: LocalDate, warningDays: Int): DashboardState {
    val items = documents.map { ExpiryCalculator.withExpiry(it, today, warningDays) }
    fun count(status: DocumentStatus) = items.count { it.expiry.status == status }
    val withDays = items.filter { it.expiry.daysRemaining != null }
    return DashboardState(
        loading = false,
        total = items.size,
        active = count(DocumentStatus.ACTIVE),
        expiringSoon = count(DocumentStatus.EXPIRING_SOON),
        expired = count(DocumentStatus.EXPIRED),
        upcomingRenewals = items.count { isUpcomingRenewal(it.document, today) },
        expiringSoonList = withDays
            .filter { (it.expiry.daysRemaining ?: -1) in 1..warningDays }
            .sortedBy { it.expiry.daysRemaining },
        todayList = withDays.filter { it.expiry.daysRemaining == 0 },
        upcomingList = withDays
            .filter { (it.expiry.daysRemaining ?: 0) > warningDays }
            .sortedBy { it.expiry.daysRemaining }
            .take(5),
        recentList = items.sortedByDescending { it.document.createdAt }.take(5),
    )
}

fun isUpcomingRenewal(document: Document, today: LocalDate): Boolean =
    document.renewalInProgress || (document.renewalDate?.let { it >= today } == true)

class DashboardViewModel(
    documents: DocumentRepository,
    settings: SettingsRepository,
    dateProvider: DateProvider,
) : ViewModel() {
    val state: StateFlow<DashboardState> =
        combine(documents.observeDocuments(), settings.settings, dateProvider.todayFlow()) { docs, s, today ->
            buildDashboard(docs, today, s.expiryWarningDays)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())
}
