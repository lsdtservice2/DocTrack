package com.logisparktech.doctrack.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logisparktech.doctrack.core.calendar.CalendarMonth
import com.logisparktech.doctrack.core.common.DateProvider
import com.logisparktech.doctrack.core.datetime.ExpiryCalculator
import com.logisparktech.doctrack.domain.model.CalendarType
import com.logisparktech.doctrack.domain.model.Document
import com.logisparktech.doctrack.domain.model.DocumentStatus
import com.logisparktech.doctrack.domain.model.DocumentWithExpiry
import com.logisparktech.doctrack.domain.repository.DocumentRepository
import com.logisparktech.doctrack.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

enum class EventKind { EXPIRY, RENEWAL }

/** Urgency drives the indicator color. */
enum class EventUrgency { EXPIRED, URGENT, UPCOMING, RENEWAL }

data class CalendarEvent(
    val date: LocalDate,
    val kind: EventKind,
    val item: DocumentWithExpiry,
    val urgency: EventUrgency,
)

enum class CalendarViewMode { MONTH, AGENDA }

data class CalendarState(
    val calendarType: CalendarType = CalendarType.AD,
    val month: CalendarMonth? = null,
    val today: LocalDate? = null,
    val selected: LocalDate? = null,
    val mode: CalendarViewMode = CalendarViewMode.MONTH,
    val eventsByDate: Map<LocalDate, List<CalendarEvent>> = emptyMap(),
    val upcoming: List<CalendarEvent> = emptyList(),
)

fun buildEvents(documents: List<Document>, today: LocalDate, warningDays: Int): List<CalendarEvent> =
    documents.flatMap { doc ->
        val item = ExpiryCalculator.withExpiry(doc, today, warningDays)
        buildList {
            doc.expiryDate?.let { date ->
                val urgency = when (item.expiry.status) {
                    DocumentStatus.EXPIRED -> EventUrgency.EXPIRED
                    DocumentStatus.EXPIRING_SOON -> EventUrgency.URGENT
                    DocumentStatus.RENEWAL_IN_PROGRESS ->
                        if ((item.expiry.daysRemaining ?: 0) < 0) EventUrgency.EXPIRED else EventUrgency.RENEWAL
                    else -> EventUrgency.UPCOMING
                }
                add(CalendarEvent(date, EventKind.EXPIRY, item, urgency))
            }
            doc.renewalDate?.let { add(CalendarEvent(it, EventKind.RENEWAL, item, EventUrgency.RENEWAL)) }
        }
    }.sortedWith(compareBy({ it.date }, { it.item.document.name }))

class CalendarViewModel(
    documents: DocumentRepository,
    settings: SettingsRepository,
    dateProvider: DateProvider,
) : ViewModel() {
    /** The anchor date determines which month is shown, so switching AD/BS keeps the same period. */
    private val anchor = MutableStateFlow<LocalDate?>(null)
    private val selected = MutableStateFlow<LocalDate?>(null)
    private val mode = MutableStateFlow(CalendarViewMode.MONTH)
    private val monthOverride = MutableStateFlow<CalendarMonth?>(null)

    private val ui = combine(anchor, selected, mode, monthOverride) { a, s, m, o -> UiInputs(a, s, m, o) }

    val state: StateFlow<CalendarState> = combine(
        documents.observeDocuments(), settings.settings, dateProvider.todayFlow(), ui,
    ) { docs, s, today, inputs ->
        val type = s.calendarType
        val month = inputs.month?.takeIf { it.type == type } ?: CalendarMonth.of(type, inputs.anchor ?: today)
        val events = buildEvents(docs, today, s.expiryWarningDays)
        CalendarState(
            calendarType = type,
            month = month,
            today = today,
            selected = inputs.selected ?: today,
            mode = inputs.mode,
            eventsByDate = events.groupBy { it.date },
            upcoming = events.filter { it.date >= today },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarState())

    fun showMonth(month: CalendarMonth) {
        monthOverride.value = month
        month.days.firstOrNull()?.let { anchor.value = it }
    }

    fun select(date: LocalDate) {
        selected.value = date
        anchor.value = date
    }

    fun goToToday(today: LocalDate) {
        monthOverride.value = null
        anchor.value = today
        selected.value = today
    }

    fun setMode(value: CalendarViewMode) { mode.value = value }

    private data class UiInputs(
        val anchor: LocalDate?,
        val selected: LocalDate?,
        val mode: CalendarViewMode,
        val month: CalendarMonth?,
    )
}
