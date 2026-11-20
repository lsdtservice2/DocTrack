package com.logisparktech.doctrack.domain.repository

import com.logisparktech.doctrack.domain.model.AppSettings
import com.logisparktech.doctrack.domain.model.Attachment
import com.logisparktech.doctrack.domain.model.AttachmentType
import com.logisparktech.doctrack.domain.model.Category
import com.logisparktech.doctrack.domain.model.Document
import com.logisparktech.doctrack.domain.model.DocumentInput
import com.logisparktech.doctrack.domain.model.Reminder
import com.logisparktech.doctrack.domain.model.RenewalRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface DocumentRepository {
    fun observeDocuments(): Flow<List<Document>>
    fun observeDocument(id: Long): Flow<Document?>
    suspend fun getDocument(id: Long): Document?
    suspend fun insert(input: DocumentInput, reminderDays: List<Int>): Long
    suspend fun update(id: Long, input: DocumentInput, reminderDays: List<Int>)
    /** Deletes the row (attachments, reminders and history cascade). Returns attachment paths to remove. */
    suspend fun delete(id: Long): List<String>
    suspend fun setRenewalInProgress(id: Long, inProgress: Boolean)
    suspend fun renew(
        id: Long,
        newIssueDate: LocalDate?,
        newExpiryDate: LocalDate?,
        renewalDate: LocalDate,
        notes: String,
    )
    suspend fun countDocuments(): Int
}

interface AttachmentRepository {
    fun observeAttachments(documentId: Long): Flow<List<Attachment>>
    suspend fun getAttachment(id: Long): Attachment?
    suspend fun getAttachments(documentId: Long): List<Attachment>
    suspend fun add(documentId: Long, fileName: String, filePath: String, type: AttachmentType): Long
    suspend fun delete(id: Long)
    suspend fun allPaths(): Set<String>
}

interface ReminderRepository {
    fun observeReminders(documentId: Long): Flow<List<Reminder>>
    fun observeAll(): Flow<List<Reminder>>
    suspend fun getReminders(documentId: Long): List<Reminder>
    suspend fun setReminders(documentId: Long, days: List<Int>)
    suspend fun addReminder(documentId: Long, days: Int)
}

interface RenewalRepository {
    fun observeHistory(documentId: Long): Flow<List<RenewalRecord>>
}

interface CategoryRepository {
    fun observeCategories(): Flow<List<Category>>
    suspend fun getCategories(): List<Category>
    suspend fun add(name: String, icon: String, color: Long): Long
    suspend fun update(id: Long, name: String, icon: String, color: Long)
    suspend fun documentCount(id: Long): Int
    suspend fun delete(id: Long)
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
