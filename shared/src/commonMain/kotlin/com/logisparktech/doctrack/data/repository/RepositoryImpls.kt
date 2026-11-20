package com.logisparktech.doctrack.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.logisparktech.doctrack.core.common.DateProvider
import com.logisparktech.doctrack.data.mapper.toDb
import com.logisparktech.doctrack.data.mapper.toDbString
import com.logisparktech.doctrack.data.mapper.toDomain
import com.logisparktech.doctrack.db.DocTrackDatabase
import com.logisparktech.doctrack.domain.model.AppSettings
import com.logisparktech.doctrack.domain.model.Attachment
import com.logisparktech.doctrack.domain.model.AttachmentType
import com.logisparktech.doctrack.domain.model.Category
import com.logisparktech.doctrack.domain.model.Document
import com.logisparktech.doctrack.domain.model.DocumentInput
import com.logisparktech.doctrack.domain.model.Reminder
import com.logisparktech.doctrack.domain.model.RenewalRecord
import com.logisparktech.doctrack.domain.repository.AttachmentRepository
import com.logisparktech.doctrack.domain.repository.CategoryRepository
import com.logisparktech.doctrack.domain.repository.DocumentRepository
import com.logisparktech.doctrack.domain.repository.ReminderRepository
import com.logisparktech.doctrack.domain.repository.RenewalRepository
import com.logisparktech.doctrack.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

private val io = Dispatchers.IO

class SqlCategoryRepository(private val db: DocTrackDatabase) : CategoryRepository {
    private val q get() = db.documentCategoryQueries

    override fun observeCategories(): Flow<List<Category>> =
        q.selectAll().asFlow().mapToList(io).map { list -> list.map { it.toDomain() } }

    override suspend fun getCategories(): List<Category> = withContext(io) {
        q.selectAll().executeAsList().map { it.toDomain() }
    }

    override suspend fun add(name: String, icon: String, color: Long): Long = withContext(io) {
        q.transactionWithResult {
            q.insert(name.trim(), icon, color)
            q.lastInsertId().executeAsOne()
        }
    }

    override suspend fun update(id: Long, name: String, icon: String, color: Long) {
        withContext(io) { q.update(name.trim(), icon, color, id) }
    }

    override suspend fun documentCount(id: Long): Int = withContext(io) {
        q.countDocuments(id).executeAsOne().toInt()
    }

    override suspend fun delete(id: Long) {
        withContext(io) { q.delete(id) }
    }
}

class SqlDocumentRepository(
    private val db: DocTrackDatabase,
    private val dateProvider: DateProvider,
) : DocumentRepository {
    private val q get() = db.documentQueries

    private val categoryMap: Flow<Map<Long, Category>> =
        db.documentCategoryQueries.selectAll().asFlow().mapToList(io)
            .map { list -> list.associate { it.id to it.toDomain() } }

    override fun observeDocuments(): Flow<List<Document>> =
        combine(q.selectAll().asFlow().mapToList(io), categoryMap) { docs, cats ->
            docs.map { it.toDomain(cats) }
        }

    override fun observeDocument(id: Long): Flow<Document?> =
        combine(q.selectById(id).asFlow().mapToOneOrNull(io), categoryMap) { doc, cats ->
            doc?.toDomain(cats)
        }.distinctUntilChanged()

    override suspend fun getDocument(id: Long): Document? = withContext(io) {
        val cats = db.documentCategoryQueries.selectAll().executeAsList().associate { it.id to it.toDomain() }
        q.selectById(id).executeAsOneOrNull()?.toDomain(cats)
    }

    override suspend fun insert(input: DocumentInput, reminderDays: List<Int>): Long = withContext(io) {
        val now = dateProvider.nowMillis()
        q.transactionWithResult {
            q.insert(
                name = input.name.trim(),
                documentNumber = input.documentNumber?.trim()?.ifEmpty { null },
                categoryId = input.categoryId,
                issuingAuthority = input.issuingAuthority?.trim()?.ifEmpty { null },
                issueDate = input.issueDate.toDbString(),
                expiryDate = input.expiryDate.toDbString(),
                renewalDate = input.renewalDate.toDbString(),
                description = input.description.trim(),
                notes = input.notes.trim(),
                renewalInProgress = input.renewalInProgress.toDb(),
                createdAt = now,
                updatedAt = now,
            )
            val id = q.lastInsertId().executeAsOne()
            reminderDays.distinct().forEach { db.documentReminderQueries.insert(id, it.toLong(), 1L) }
            id
        }
    }

    override suspend fun update(id: Long, input: DocumentInput, reminderDays: List<Int>) = withContext(io) {
        q.transaction {
            q.update(
                name = input.name.trim(),
                documentNumber = input.documentNumber?.trim()?.ifEmpty { null },
                categoryId = input.categoryId,
                issuingAuthority = input.issuingAuthority?.trim()?.ifEmpty { null },
                issueDate = input.issueDate.toDbString(),
                expiryDate = input.expiryDate.toDbString(),
                renewalDate = input.renewalDate.toDbString(),
                description = input.description.trim(),
                notes = input.notes.trim(),
                renewalInProgress = input.renewalInProgress.toDb(),
                updatedAt = dateProvider.nowMillis(),
                id = id,
            )
            db.documentReminderQueries.deleteByDocument(id)
            reminderDays.distinct().forEach { db.documentReminderQueries.insert(id, it.toLong(), 1L) }
        }
    }

    override suspend fun delete(id: Long): List<String> = withContext(io) {
        q.transactionWithResult {
            val paths = db.documentAttachmentQueries.selectByDocument(id).executeAsList().map { it.filePath }
            q.delete(id)
            paths
        }
    }

    override suspend fun setRenewalInProgress(id: Long, inProgress: Boolean) {
        withContext(io) { q.setRenewalInProgress(inProgress.toDb(), dateProvider.nowMillis(), id) }
    }

    override suspend fun renew(
        id: Long,
        newIssueDate: LocalDate?,
        newExpiryDate: LocalDate?,
        renewalDate: LocalDate,
        notes: String,
    ) = withContext(io) {
        q.transaction {
            val current = q.selectById(id).executeAsOneOrNull() ?: throw IllegalStateException("Document $id not found")
            // History keeps the previous dates forever; the document row holds only the current ones.
            db.renewalHistoryQueries.insert(
                documentId = id,
                previousIssueDate = current.issueDate,
                previousExpiryDate = current.expiryDate,
                newIssueDate = newIssueDate.toDbString(),
                newExpiryDate = newExpiryDate.toDbString(),
                renewalDate = renewalDate.toString(),
                notes = notes.trim(),
            )
            q.updateDates(
                issueDate = newIssueDate.toDbString() ?: current.issueDate,
                expiryDate = newExpiryDate.toDbString(),
                // The planned renewal is fulfilled; the actual date is kept in RenewalHistory.
                renewalDate = null,
                updatedAt = dateProvider.nowMillis(),
                id = id,
            )
        }
    }

    override suspend fun countDocuments(): Int = withContext(io) {
        q.selectAll().executeAsList().size
    }
}

class SqlAttachmentRepository(
    private val db: DocTrackDatabase,
    private val dateProvider: DateProvider,
) : AttachmentRepository {
    private val q get() = db.documentAttachmentQueries

    override fun observeAttachments(documentId: Long): Flow<List<Attachment>> =
        q.selectByDocument(documentId).asFlow().mapToList(io).map { list -> list.map { it.toDomain() } }

    override suspend fun getAttachment(id: Long): Attachment? = withContext(io) {
        q.selectById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun getAttachments(documentId: Long): List<Attachment> = withContext(io) {
        q.selectByDocument(documentId).executeAsList().map { it.toDomain() }
    }

    override suspend fun add(documentId: Long, fileName: String, filePath: String, type: AttachmentType): Long =
        withContext(io) {
            q.transactionWithResult {
                q.insert(documentId, fileName, filePath, type.name, dateProvider.nowMillis())
                db.documentQueries.lastInsertId().executeAsOne()
            }
        }

    override suspend fun delete(id: Long) {
        withContext(io) { q.delete(id) }
    }

    override suspend fun allPaths(): Set<String> = withContext(io) {
        q.selectAll().executeAsList().map { it.filePath }.toSet()
    }
}

class SqlReminderRepository(private val db: DocTrackDatabase) : ReminderRepository {
    private val q get() = db.documentReminderQueries

    override fun observeReminders(documentId: Long): Flow<List<Reminder>> =
        q.selectByDocument(documentId).asFlow().mapToList(io).map { list -> list.map { it.toDomain() } }

    override fun observeAll(): Flow<List<Reminder>> =
        q.selectAll().asFlow().mapToList(io).map { list -> list.map { it.toDomain() } }

    override suspend fun getReminders(documentId: Long): List<Reminder> = withContext(io) {
        q.selectByDocument(documentId).executeAsList().map { it.toDomain() }
    }

    override suspend fun setReminders(documentId: Long, days: List<Int>) = withContext(io) {
        q.transaction {
            q.deleteByDocument(documentId)
            days.distinct().forEach { q.insert(documentId, it.toLong(), 1L) }
        }
    }

    override suspend fun addReminder(documentId: Long, days: Int) {
        withContext(io) { q.insert(documentId, days.toLong(), 1L) }
    }
}

class SqlRenewalRepository(private val db: DocTrackDatabase) : RenewalRepository {
    override fun observeHistory(documentId: Long): Flow<List<RenewalRecord>> =
        db.renewalHistoryQueries.selectByDocument(documentId).asFlow().mapToList(io)
            .map { list -> list.map { it.toDomain() } }
}

class SqlSettingsRepository(private val db: DocTrackDatabase) : SettingsRepository {
    private val q get() = db.appSettingsQueries

    override val settings: Flow<AppSettings> =
        q.select().asFlow().mapToOneOrNull(io).map { it?.toDomain() ?: AppSettings() }.distinctUntilChanged()

    override suspend fun current(): AppSettings = withContext(io) {
        q.select().executeAsOneOrNull()?.toDomain() ?: AppSettings()
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings) = withContext(io) {
        q.transaction {
            val current = q.select().executeAsOneOrNull()?.toDomain() ?: AppSettings()
            val s = transform(current)
            q.update(
                language = s.language.code,
                calendarType = s.calendarType.name,
                theme = s.theme.name,
                defaultReminderDays = s.defaultReminderDays.joinToString(","),
                expiryWarningDays = s.expiryWarningDays.toLong(),
                biometricLockEnabled = s.biometricLockEnabled.toDb(),
                reminderHour = s.reminderHour.toLong(),
            )
        }
    }
}
