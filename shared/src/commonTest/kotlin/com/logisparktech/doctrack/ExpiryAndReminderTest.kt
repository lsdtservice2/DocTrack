package com.logisparktech.doctrack

import com.logisparktech.doctrack.core.calendar.DateFormatter
import com.logisparktech.doctrack.core.datetime.ExpiryCalculator
import com.logisparktech.doctrack.core.localization.EnStrings
import com.logisparktech.doctrack.core.localization.NeStrings
import com.logisparktech.doctrack.core.notification.ReminderPlanner
import com.logisparktech.doctrack.domain.model.AppSettings
import com.logisparktech.doctrack.domain.model.CalendarType
import com.logisparktech.doctrack.domain.model.Category
import com.logisparktech.doctrack.domain.model.Document
import com.logisparktech.doctrack.domain.model.DocumentStatus
import com.logisparktech.doctrack.domain.model.Reminder
import com.logisparktech.doctrack.feature.dashboard.buildDashboard
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val today = LocalDate(2025, 6, 15)
private val category = Category(1, "passport", "passport", 0xFF4F46E5, true)

private fun doc(id: Long, expiry: LocalDate?, renewal: Boolean = false, created: Long = id, renewalDate: LocalDate? = null) = Document(
    id = id, name = "Doc $id", documentNumber = null, category = category, issuingAuthority = null,
    issueDate = null, expiryDate = expiry, renewalDate = renewalDate, description = "", notes = "",
    renewalInProgress = renewal, createdAt = created, updatedAt = created,
)

private fun inDays(n: Int) = today.plus(DatePeriod(days = n))

class ExpiryCalculatorTest {
    private fun status(days: Int?, warning: Int = 30, renewal: Boolean = false) =
        ExpiryCalculator.calculate(days?.let { inDays(it) }, today, warning, renewal)

    @Test
    fun defaultRules() {
        assertEquals(DocumentStatus.ACTIVE, status(31).status)
        assertEquals(DocumentStatus.EXPIRING_SOON, status(30).status)
        assertEquals(DocumentStatus.EXPIRING_SOON, status(1).status)
        assertEquals(DocumentStatus.EXPIRING_SOON, status(0).status)
        assertEquals(DocumentStatus.EXPIRED, status(-1).status)
        assertEquals(DocumentStatus.NO_EXPIRY, status(null).status)
    }

    @Test
    fun customThresholdAndRenewalOverride() {
        assertEquals(DocumentStatus.ACTIVE, status(10, warning = 7).status)
        assertEquals(DocumentStatus.EXPIRING_SOON, status(7, warning = 7).status)
        assertEquals(DocumentStatus.RENEWAL_IN_PROGRESS, status(-3, renewal = true).status)
        assertEquals(-3, status(-3, renewal = true).daysRemaining)
    }

    @Test
    fun daysRemainingAcrossMonthAndLeapYear() {
        assertEquals(366, ExpiryCalculator.daysRemaining(LocalDate(2025, 1, 1), LocalDate(2024, 1, 1)))
        assertEquals(1, ExpiryCalculator.daysRemaining(LocalDate(2024, 3, 1), LocalDate(2024, 2, 29)))
    }

    @Test
    fun humanReadableTextEnglish() {
        val f = DateFormatter(EnStrings, CalendarType.AD)
        assertEquals("Expires in 30 days", f.expiryText(status(30)))
        assertEquals("Expires in 7 days", f.expiryText(status(7)))
        assertEquals("Expires tomorrow", f.expiryText(status(1)))
        assertEquals("Expires today", f.expiryText(status(0)))
        assertEquals("Expired yesterday", f.expiryText(status(-1)))
        assertEquals("Expired 5 days ago", f.expiryText(status(-5)))
        assertEquals("No expiry date", f.expiryText(status(null)))
    }

    @Test
    fun nepaliTextUsesDevanagariDigitsAndBsDates() {
        val f = DateFormatter(NeStrings, CalendarType.BS)
        assertEquals("७ दिनमा म्याद सकिन्छ", f.expiryText(status(7)))
        assertEquals("१ बैशाख २०८१", f.format(LocalDate(2024, 4, 13)))
        assertEquals("1 Baisakh 2081 BS", DateFormatter(EnStrings, CalendarType.BS).format(LocalDate(2024, 4, 13), withSuffix = true))
        assertEquals("13 April 2024", DateFormatter(EnStrings, CalendarType.AD).format(LocalDate(2024, 4, 13)))
        assertEquals("१३ अप्रिल २०२४", DateFormatter(NeStrings, CalendarType.AD).format(LocalDate(2024, 4, 13)))
    }

    @Test
    fun bsFormatterFallsBackToAdOutsideTable() {
        val f = DateFormatter(EnStrings, CalendarType.BS)
        assertEquals("1 January 2040 AD", f.format(LocalDate(2040, 1, 1)))
    }
}

class ReminderPlannerTest {
    private val tz = TimeZone.UTC
    private val now = today.atTime(LocalTime(12, 0)).toInstant(tz).toEpochMilliseconds()

    @Test
    fun plansOnlyFutureEnabledRemindersSortedWithStableIds() {
        val documents = listOf(doc(1, inDays(10)), doc(2, inDays(0)), doc(3, null))
        val reminders = listOf(
            Reminder(1, 1, 30, true), // already passed
            Reminder(2, 1, 7, true),
            Reminder(3, 1, 1, false), // disabled
            Reminder(4, 1, 0, true),
            Reminder(5, 2, 0, true), // 09:00 today, already passed at 12:00
            Reminder(6, 3, 7, true), // no expiry date
        )
        val planned = ReminderPlanner.plan(documents, reminders, AppSettings(reminderHour = 9), now, tz)
        assertEquals(listOf(7, 0), planned.map { it.daysBefore })
        assertEquals(listOf(1007, 1000), planned.map { it.id })
        assertEquals(inDays(3), planned.first().triggerDate)
        assertTrue(planned.zipWithNext().all { (a, b) -> a.triggerAtMillis <= b.triggerAtMillis })
    }

    @Test
    fun duplicateReminderRowsProduceOneNotification() {
        val planned = ReminderPlanner.plan(
            listOf(doc(5, inDays(20))),
            listOf(Reminder(1, 5, 7, true), Reminder(2, 5, 7, true)),
            AppSettings(), now, tz,
        )
        assertEquals(1, planned.size)
    }
}

class DashboardTest {
    @Test
    fun statisticsAndSections() {
        val docs = listOf(
            doc(1, inDays(100), created = 10),
            doc(2, inDays(20), created = 20),
            doc(3, inDays(0), created = 30),
            doc(4, inDays(-2), created = 40),
            doc(5, null, created = 50),
            doc(6, inDays(5), renewal = true, created = 60),
            doc(7, inDays(200), renewalDate = inDays(3), created = 70),
        )
        val d = buildDashboard(docs, today, 30)
        assertEquals(7, d.total)
        assertEquals(2, d.active)
        assertEquals(2, d.expiringSoon)
        assertEquals(1, d.expired)
        assertEquals(2, d.upcomingRenewals)
        assertEquals(listOf(3L), d.todayList.map { it.document.id })
        assertEquals(listOf(6L, 2L), d.expiringSoonList.map { it.document.id })
        assertEquals(listOf(1L, 7L), d.upcomingList.map { it.document.id })
        assertEquals(7L, d.recentList.first().document.id)
    }
}
