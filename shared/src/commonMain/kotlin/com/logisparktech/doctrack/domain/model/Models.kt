package com.logisparktech.doctrack.domain.model

import kotlinx.datetime.LocalDate

data class Category(
    val id: Long,
    /** For default categories this is a stable key (e.g. "passport"), translated for display. */
    val name: String,
    val icon: String,
    val color: Long,
    val isDefault: Boolean,
)

data class Document(
    val id: Long,
    val name: String,
    val documentNumber: String?,
    val category: Category,
    val issuingAuthority: String?,
    val issueDate: LocalDate?,
    val expiryDate: LocalDate?,
    val renewalDate: LocalDate?,
    val description: String,
    val notes: String,
    val renewalInProgress: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Values entered in the add/edit form. */
data class DocumentInput(
    val name: String,
    val documentNumber: String?,
    val categoryId: Long,
    val issuingAuthority: String?,
    val issueDate: LocalDate?,
    val expiryDate: LocalDate?,
    val renewalDate: LocalDate?,
    val description: String,
    val notes: String,
    val renewalInProgress: Boolean,
)

enum class AttachmentType { FRONT_IMAGE, BACK_IMAGE, IMAGE, PDF;
    val isImage: Boolean get() = this != PDF
}

data class Attachment(
    val id: Long,
    val documentId: Long,
    val fileName: String,
    /** Relative to the app attachment directory. */
    val filePath: String,
    val type: AttachmentType,
    val createdAt: Long,
)

/** A file picked by the user that has not been stored yet. */
class PickedFile(val name: String, val bytes: ByteArray, val mimeType: String)

data class Reminder(
    val id: Long,
    val documentId: Long,
    val daysBefore: Int,
    val enabled: Boolean,
)

data class RenewalRecord(
    val id: Long,
    val documentId: Long,
    val previousIssueDate: LocalDate?,
    val previousExpiryDate: LocalDate?,
    val newIssueDate: LocalDate?,
    val newExpiryDate: LocalDate?,
    val renewalDate: LocalDate,
    val notes: String,
)

enum class DocumentStatus { ACTIVE, EXPIRING_SOON, EXPIRED, RENEWAL_IN_PROGRESS, NO_EXPIRY }

data class ExpiryInfo(
    val status: DocumentStatus,
    /** Days from today until expiry (negative when expired), null without an expiry date. */
    val daysRemaining: Int?,
)

data class DocumentWithExpiry(val document: Document, val expiry: ExpiryInfo)

enum class AppLanguage(val code: String) { EN("en"), NE("ne");
    companion object { fun fromCode(code: String) = entries.firstOrNull { it.code == code } ?: EN }
}

enum class CalendarType { AD, BS;
    companion object { fun fromName(name: String) = entries.firstOrNull { it.name == name } ?: AD }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK;
    companion object { fun fromName(name: String) = entries.firstOrNull { it.name == name } ?: SYSTEM }
}

data class AppSettings(
    val language: AppLanguage = AppLanguage.EN,
    val calendarType: CalendarType = CalendarType.AD,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val defaultReminderDays: List<Int> = DEFAULT_REMINDER_DAYS,
    val expiryWarningDays: Int = 30,
    val biometricLockEnabled: Boolean = false,
    /** Hour of day (0-23) at which reminders fire. */
    val reminderHour: Int = 9,
) {
    companion object {
        val DEFAULT_REMINDER_DAYS = listOf(30, 15, 7, 3, 1, 0)
        const val MAX_REMINDER_DAYS = 365
    }
}
