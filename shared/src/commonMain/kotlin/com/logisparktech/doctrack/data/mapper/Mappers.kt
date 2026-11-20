package com.logisparktech.doctrack.data.mapper

import com.logisparktech.doctrack.db.DocumentAttachments
import com.logisparktech.doctrack.db.DocumentCategories
import com.logisparktech.doctrack.db.DocumentReminders
import com.logisparktech.doctrack.db.Documents
import com.logisparktech.doctrack.db.RenewalHistory
import com.logisparktech.doctrack.domain.model.AppLanguage
import com.logisparktech.doctrack.domain.model.AppSettings
import com.logisparktech.doctrack.domain.model.Attachment
import com.logisparktech.doctrack.domain.model.AttachmentType
import com.logisparktech.doctrack.domain.model.CalendarType
import com.logisparktech.doctrack.domain.model.Category
import com.logisparktech.doctrack.domain.model.Document
import com.logisparktech.doctrack.domain.model.Reminder
import com.logisparktech.doctrack.domain.model.RenewalRecord
import com.logisparktech.doctrack.domain.model.ThemeMode
import kotlinx.datetime.LocalDate
import com.logisparktech.doctrack.db.AppSettings as DbAppSettings

fun String?.toLocalDateOrNull(): LocalDate? = this?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

fun LocalDate?.toDbString(): String? = this?.toString()

fun Boolean.toDb(): Long = if (this) 1L else 0L

fun DocumentCategories.toDomain() = Category(
    id = id,
    name = name,
    icon = icon,
    color = color,
    isDefault = isDefault != 0L,
)

/** Categories are looked up by id; unknown ids fall back to a placeholder to stay robust. */
fun Documents.toDomain(categories: Map<Long, Category>) = Document(
    id = id,
    name = name,
    documentNumber = documentNumber,
    category = categories[categoryId] ?: Category(categoryId, "custom_documents", "folder", 0xFF6B7280, true),
    issuingAuthority = issuingAuthority,
    issueDate = issueDate.toLocalDateOrNull(),
    expiryDate = expiryDate.toLocalDateOrNull(),
    renewalDate = renewalDate.toLocalDateOrNull(),
    description = description,
    notes = notes,
    renewalInProgress = renewalInProgress != 0L,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun DocumentAttachments.toDomain() = Attachment(
    id = id,
    documentId = documentId,
    fileName = fileName,
    filePath = filePath,
    type = AttachmentType.entries.firstOrNull { it.name == fileType } ?: AttachmentType.IMAGE,
    createdAt = createdAt,
)

fun DocumentReminders.toDomain() = Reminder(
    id = id,
    documentId = documentId,
    daysBefore = reminderDaysBefore.toInt(),
    enabled = notificationEnabled != 0L,
)

fun RenewalHistory.toDomain() = RenewalRecord(
    id = id,
    documentId = documentId,
    previousIssueDate = previousIssueDate.toLocalDateOrNull(),
    previousExpiryDate = previousExpiryDate.toLocalDateOrNull(),
    newIssueDate = newIssueDate.toLocalDateOrNull(),
    newExpiryDate = newExpiryDate.toLocalDateOrNull(),
    renewalDate = renewalDate.toLocalDateOrNull() ?: LocalDate(1970, 1, 1),
    notes = notes,
)

fun parseReminderDays(value: String): List<Int> =
    value.split(',').mapNotNull { it.trim().toIntOrNull() }
        .filter { it in 0..AppSettings.MAX_REMINDER_DAYS }
        .distinct()
        .sortedDescending()

fun DbAppSettings.toDomain() = AppSettings(
    language = AppLanguage.fromCode(language),
    calendarType = CalendarType.fromName(calendarType),
    theme = ThemeMode.fromName(theme),
    defaultReminderDays = parseReminderDays(defaultReminderDays),
    expiryWarningDays = expiryWarningDays.toInt(),
    biometricLockEnabled = biometricLockEnabled != 0L,
    reminderHour = reminderHour.toInt(),
)
