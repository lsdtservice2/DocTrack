package com.logisparktech.doctrack.core.notification

import com.logisparktech.doctrack.domain.model.AppSettings
import com.logisparktech.doctrack.domain.model.Document
import com.logisparktech.doctrack.domain.model.Reminder
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant

data class PlannedReminder(
    val id: Int,
    val documentId: Long,
    val documentName: String,
    val daysBefore: Int,
    val expiryDate: LocalDate,
    val triggerDate: LocalDate,
    val triggerAtMillis: Long,
)

/** Pure planning logic: which reminders should be pending right now. */
object ReminderPlanner {

    fun notificationId(documentId: Long, daysBefore: Int): Int = (documentId * 1000 + daysBefore).toInt()

    fun plan(
        documents: List<Document>,
        reminders: List<Reminder>,
        settings: AppSettings,
        nowMillis: Long,
        timeZone: TimeZone,
    ): List<PlannedReminder> {
        val byDocument = reminders.filter { it.enabled }.groupBy { it.documentId }
        val time = LocalTime(settings.reminderHour.coerceIn(0, 23), 0)
        return documents.asSequence()
            .filter { it.expiryDate != null }
            .flatMap { doc ->
                val expiry = doc.expiryDate!!
                byDocument[doc.id].orEmpty()
                    .map { it.daysBefore }
                    .distinct()
                    .filter { it in 0..AppSettings.MAX_REMINDER_DAYS }
                    .map { days ->
                        val triggerDate = expiry.minus(DatePeriod(days = days))
                        PlannedReminder(
                            id = notificationId(doc.id, days),
                            documentId = doc.id,
                            documentName = doc.name,
                            daysBefore = days,
                            expiryDate = expiry,
                            triggerDate = triggerDate,
                            triggerAtMillis = triggerDate.atTime(time).toInstant(timeZone).toEpochMilliseconds(),
                        )
                    }
            }
            .filter { it.triggerAtMillis > nowMillis }
            .sortedWith(compareBy({ it.triggerAtMillis }, { it.id }))
            .toList()
    }
}
