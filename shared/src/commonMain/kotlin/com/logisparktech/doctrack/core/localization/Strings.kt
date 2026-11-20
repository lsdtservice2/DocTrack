package com.logisparktech.doctrack.core.localization

import com.logisparktech.doctrack.domain.model.AppLanguage

/**
 * Centralized, type-safe localization. Every user-visible string lives in [EnStrings] / [NeStrings].
 * Language is chosen in-app, independent of the device locale and of the calendar system.
 */
interface Strings {
    val language: AppLanguage

    /** Formats a number using the language's digits (Devanagari for Nepali). */
    fun num(value: Number): String = localizeDigits(value.toString(), language)

    // General
    val appName: String
    val tagline: String
    val save: String
    val cancel: String
    val delete: String
    val edit: String
    val add: String
    val ok: String
    val close: String
    val back: String
    val clear: String
    val confirm: String
    val optional: String
    val seeAll: String
    val search: String
    val working: String
    val unknownError: String

    // Navigation
    val navHome: String
    val navDocuments: String
    val navCalendar: String
    val navNotifications: String
    val navSettings: String

    // Dashboard
    val dashboard: String
    val totalDocuments: String
    val activeDocuments: String
    val expiringSoon: String
    val expiredDocuments: String
    val upcomingRenewals: String
    val todaysExpirations: String
    val upcomingDocuments: String
    val recentlyAdded: String
    val addDocument: String
    val welcomeTitle: String
    val welcomeMessage: String
    val nothingExpiringSoon: String
    val nothingExpiringToday: String
    val noUpcomingDocuments: String

    // Status
    val statusActive: String
    val statusExpiringSoon: String
    val statusExpired: String
    val statusRenewalInProgress: String
    val statusNoExpiry: String

    // Expiry descriptions
    fun expiresInDays(days: Int): String
    val expiresTomorrow: String
    val expiresToday: String
    val expiredYesterday: String
    fun expiredDaysAgo(days: Int): String
    val noExpiryDate: String
    fun daysCount(days: Int): String

    // Documents list
    val myDocuments: String
    val searchDocuments: String
    val allCategories: String
    val allStatuses: String
    val noDocumentsFound: String
    val noDocumentsMatch: String

    // Document form
    val addDocumentTitle: String
    val editDocumentTitle: String
    val documentName: String
    val documentNumber: String
    val category: String
    val issuingAuthority: String
    val issueDate: String
    val expiryDate: String
    val renewalDate: String
    val description: String
    val notes: String
    val frontImage: String
    val backImage: String
    val pdfAttachments: String
    val otherImages: String
    val addPdf: String
    val addImage: String
    val replaceImage: String
    val removeImage: String
    val attachments: String
    val reminders: String
    val reminderSettings: String
    val remindersEnabled: String
    fun daysBefore(days: Int): String
    val onExpiryDate: String
    val addCustomReminder: String
    val customReminderDays: String
    val selectDate: String
    val markRenewalInProgress: String
    val basicInformation: String
    val dates: String
    val noReminderWithoutExpiry: String
    val errorNameRequired: String
    val errorCategoryRequired: String
    val errorExpiryBeforeIssue: String
    fun errorReminderDaysRange(max: Int): String
    val errorSaveFailed: String
    val errorFileRead: String
    val errorFileTooLarge: String
    val documentSaved: String
    val discardChangesTitle: String
    val discardChangesMessage: String
    val discard: String

    // Document details
    val documentDetails: String
    val remainingDays: String
    val status: String
    val renew: String
    val addReminder: String
    val viewAttachment: String
    val renewalHistory: String
    val noRenewalHistory: String
    val noAttachments: String
    val noReminders: String
    val deleteDocumentTitle: String
    val deleteDocumentMessage: String
    val documentDeleted: String
    val documentNotFound: String
    val previousDates: String
    val newDates: String
    fun renewedOn(date: String): String
    val createdOn: String
    val updatedOn: String
    val notSet: String

    // Renewal
    val renewDocument: String
    val currentDetails: String
    val newDetails: String
    val newIssueDate: String
    val newExpiryDate: String
    val renewalDateLabel: String
    val renewalNotes: String
    val renewalSaved: String
    val errorNewExpiryRequired: String
    val errorNewExpiryNotAfterOld: String

    // Calendar
    val calendar: String
    val monthView: String
    val agendaView: String
    val today: String
    val previousMonth: String
    val nextMonth: String
    val previousYear: String
    val nextYear: String
    val noEventsOnDate: String
    val noUpcomingEvents: String
    val expiryEvent: String
    val renewalEvent: String
    val legendExpired: String
    val legendUrgent: String
    val legendUpcoming: String
    val legendRenewal: String
    val weekdaysShort: List<String>
    val adMonths: List<String>
    val bsMonths: List<String>
    val adSuffix: String
    val bsSuffix: String
    val dateOutOfBsRange: String

    // Notifications
    val notifications: String
    val upcomingReminders: String
    val noUpcomingReminders: String
    val notificationsDisabledTitle: String
    val notificationsDisabledMessage: String
    val enableNotifications: String
    val notificationsPermissionDenied: String
    val notificationsDeliveryNote: String
    fun reminderScheduledFor(date: String): String
    fun notificationTitle(documentName: String): String
    fun notificationBody(documentName: String, daysBefore: Int): String
    val notificationChannelName: String
    val notificationChannelDescription: String

    // Settings
    val settings: String
    val general: String
    val appearance: String
    val remindersSection: String
    val security: String
    val data: String
    val languageLabel: String
    val calendarSystem: String
    val theme: String
    val themeSystem: String
    val themeLight: String
    val themeDark: String
    val english: String
    val nepali: String
    val gregorianAd: String
    val bikramSambatBs: String
    val expiryWarningThreshold: String
    fun expiryWarningDescription(days: Int): String
    val defaultReminders: String
    val reminderTime: String
    fun formatHour(hour: Int): String
    val biometricLock: String
    val biometricLockDescription: String
    val biometricUnavailable: String
    val manageCategories: String
    val backupAndRestore: String
    val about: String
    val version: String
    val aboutDescription: String

    // Categories
    val categories: String
    val addCategory: String
    val editCategory: String
    val categoryName: String
    val categoryIcon: String
    val categoryColor: String
    val defaultCategories: String
    val customCategories: String
    val noCustomCategories: String
    val deleteCategoryTitle: String
    fun deleteCategoryMessage(name: String): String
    fun categoryInUse(count: Int): String
    val errorCategoryNameRequired: String
    val errorCategoryExists: String
    fun categoryDisplayName(key: String): String

    // Backup
    val backupTitle: String
    val createBackup: String
    val createBackupDescription: String
    val saveToDevice: String
    val shareBackup: String
    val restoreBackup: String
    val restoreBackupDescription: String
    val selectBackupFile: String
    val restoreConfirmTitle: String
    fun restoreConfirmMessage(documents: Int, attachments: Int, currentDocuments: Int): String
    val restore: String
    val restoreSuccess: String
    val backupCreated: String
    val backupSaved: String
    val errorInvalidBackup: String
    val errorBackupCorrupted: String
    val errorBackupVersion: String
    val errorRestoreFailed: String
    val errorBackupFailed: String
    fun backupSummary(documents: Int, attachments: Int, createdAt: String): String
    val backupOfflineNote: String

    // Lock
    val appLocked: String
    val unlock: String
    val unlockReason: String
    val authenticationFailed: String

    // Attachments
    val attachment: String
    val openWithApp: String
    val cannotOpenFile: String
    val deleteAttachmentTitle: String
    val deleteAttachmentMessage: String
    val imageLoadFailed: String
    val pdfDocument: String
}

private const val DEVANAGARI_DIGITS = "०१२३४५६७८९"

fun localizeDigits(text: String, language: AppLanguage): String =
    if (language == AppLanguage.NE) buildString(text.length) {
        for (c in text) append(if (c in '0'..'9') DEVANAGARI_DIGITS[c - '0'] else c)
    } else text

fun stringsFor(language: AppLanguage): Strings = when (language) {
    AppLanguage.EN -> EnStrings
    AppLanguage.NE -> NeStrings
}
