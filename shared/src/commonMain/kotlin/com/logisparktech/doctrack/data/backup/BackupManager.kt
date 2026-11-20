package com.logisparktech.doctrack.data.backup

import com.logisparktech.doctrack.core.common.DateProvider
import com.logisparktech.doctrack.core.storage.Crc32
import com.logisparktech.doctrack.core.storage.FileStorage
import com.logisparktech.doctrack.core.storage.ZipArchive
import com.logisparktech.doctrack.core.storage.isSafeRelativeName
import com.logisparktech.doctrack.db.DocTrackDatabase
import com.logisparktech.doctrack.db.DocumentAttachments
import com.logisparktech.doctrack.db.DocumentReminders
import com.logisparktech.doctrack.db.Documents
import com.logisparktech.doctrack.db.RenewalHistory
import com.logisparktech.doctrack.domain.usecase.AttachmentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val BACKUP_FORMAT = "doctrack-backup"
const val BACKUP_VERSION = 1
const val BACKUP_MIME = "application/zip"

@Serializable
data class BackupFileEntry(val name: String, val size: Long, val crc32: Long)

@Serializable
data class BackupManifest(
    val format: String,
    val version: Int,
    val appVersion: String,
    val createdAt: Long,
    val documentCount: Int,
    val attachmentCount: Int,
    val files: List<BackupFileEntry>,
)

@Serializable
data class CategoryRow(val id: Long, val name: String, val icon: String, val color: Long, val isDefault: Long)

@Serializable
data class DocumentRow(
    val id: Long, val name: String, val documentNumber: String?, val categoryId: Long,
    val issuingAuthority: String?, val issueDate: String?, val expiryDate: String?, val renewalDate: String?,
    val description: String, val notes: String, val renewalInProgress: Long,
    val createdAt: Long, val updatedAt: Long, val isArchived: Long,
)

@Serializable
data class AttachmentRow(
    val id: Long, val documentId: Long, val fileName: String, val filePath: String,
    val fileType: String, val createdAt: Long,
)

@Serializable
data class ReminderRow(val id: Long, val documentId: Long, val reminderDaysBefore: Long, val notificationEnabled: Long)

@Serializable
data class RenewalRow(
    val id: Long, val documentId: Long, val previousIssueDate: String?, val previousExpiryDate: String?,
    val newIssueDate: String?, val newExpiryDate: String?, val renewalDate: String, val notes: String,
)

@Serializable
data class SettingsRow(
    val language: String, val calendarType: String, val theme: String, val defaultReminderDays: String,
    val expiryWarningDays: Long, val biometricLockEnabled: Long, val reminderHour: Long,
)

@Serializable
data class BackupData(
    val categories: List<CategoryRow>,
    val documents: List<DocumentRow>,
    val attachments: List<AttachmentRow>,
    val reminders: List<ReminderRow>,
    val renewals: List<RenewalRow>,
    val settings: SettingsRow?,
)

/** A validated backup ready to be restored after the user confirms. */
class BackupPreview internal constructor(
    val manifest: BackupManifest,
    internal val data: BackupData,
    internal val files: Map<String, ByteArray>,
)

sealed class BackupException(message: String) : Exception(message) {
    class InvalidFormat(message: String) : BackupException(message)
    class Corrupted(message: String) : BackupException(message)
    class UnsupportedVersion(val version: Int) : BackupException("Unsupported version $version")
}

class BackupManager(
    private val db: DocTrackDatabase,
    private val storage: FileStorage,
    private val attachmentManager: AttachmentManager,
    private val dateProvider: DateProvider,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false; encodeDefaults = true }

    /** Builds a complete ZIP backup of all local data and attachments. */
    suspend fun createBackup(): ByteArray = withContext(Dispatchers.IO) {
        val data = db.transactionWithResult {
            BackupData(
                categories = db.documentCategoryQueries.selectAll().executeAsList().map {
                    CategoryRow(it.id, it.name, it.icon, it.color, it.isDefault)
                },
                documents = db.documentQueries.selectEverything().executeAsList().map {
                    DocumentRow(
                        it.id, it.name, it.documentNumber, it.categoryId, it.issuingAuthority, it.issueDate,
                        it.expiryDate, it.renewalDate, it.description, it.notes, it.renewalInProgress,
                        it.createdAt, it.updatedAt, it.isArchived,
                    )
                },
                attachments = db.documentAttachmentQueries.selectAll().executeAsList().map {
                    AttachmentRow(it.id, it.documentId, it.fileName, it.filePath, it.fileType, it.createdAt)
                },
                reminders = db.documentReminderQueries.selectAll().executeAsList().map {
                    ReminderRow(it.id, it.documentId, it.reminderDaysBefore, it.notificationEnabled)
                },
                renewals = db.renewalHistoryQueries.selectAll().executeAsList().map {
                    RenewalRow(
                        it.id, it.documentId, it.previousIssueDate, it.previousExpiryDate,
                        it.newIssueDate, it.newExpiryDate, it.renewalDate, it.notes,
                    )
                },
                settings = db.appSettingsQueries.select().executeAsOneOrNull()?.let {
                    SettingsRow(
                        it.language, it.calendarType, it.theme, it.defaultReminderDays,
                        it.expiryWarningDays, it.biometricLockEnabled, it.reminderHour,
                    )
                },
            )
        }

        val fileEntries = data.attachments.mapNotNull { row ->
            storage.readAttachment(row.filePath)?.let { ZipArchive.Entry("attachments/${row.filePath}", it) }
        }
        val presentPaths = fileEntries.map { it.name.removePrefix("attachments/") }.toSet()
        // Never export rows whose file has gone missing; the backup must be self-consistent.
        val consistent = data.copy(attachments = data.attachments.filter { it.filePath in presentPaths })
        val dataEntry = ZipArchive.Entry("data.json", json.encodeToString(BackupData.serializer(), consistent).encodeToByteArray())
        val contentEntries = listOf(dataEntry) + fileEntries

        val manifest = BackupManifest(
            format = BACKUP_FORMAT,
            version = BACKUP_VERSION,
            appVersion = "1.0",
            createdAt = dateProvider.nowMillis(),
            documentCount = consistent.documents.size,
            attachmentCount = consistent.attachments.size,
            files = contentEntries.map { BackupFileEntry(it.name, it.bytes.size.toLong(), Crc32.compute(it.bytes)) },
        )
        val manifestEntry = ZipArchive.Entry("manifest.json", json.encodeToString(BackupManifest.serializer(), manifest).encodeToByteArray())
        ZipArchive.write(listOf(manifestEntry) + contentEntries)
    }

    /** Fully validates a backup without touching existing data. */
    suspend fun inspect(bytes: ByteArray): BackupPreview = withContext(Dispatchers.Default) {
        val entries = try {
            ZipArchive.read(bytes)
        } catch (e: ZipArchive.ZipFormatException) {
            if (e.message?.startsWith("CRC") == true || e.message?.startsWith("Truncated") == true) {
                throw BackupException.Corrupted(e.message ?: "")
            }
            throw BackupException.InvalidFormat(e.message ?: "")
        }
        val byName = entries.associateBy { it.name }
        val manifestBytes = byName["manifest.json"]?.bytes ?: throw BackupException.InvalidFormat("Missing manifest")
        val manifest = runCatching { json.decodeFromString(BackupManifest.serializer(), manifestBytes.decodeToString()) }
            .getOrElse { throw BackupException.InvalidFormat("Bad manifest") }
        if (manifest.format != BACKUP_FORMAT) throw BackupException.InvalidFormat("Wrong format")
        if (manifest.version > BACKUP_VERSION) throw BackupException.UnsupportedVersion(manifest.version)

        for (file in manifest.files) {
            val entry = byName[file.name] ?: throw BackupException.Corrupted("Missing ${file.name}")
            if (entry.bytes.size.toLong() != file.size || Crc32.compute(entry.bytes) != file.crc32) {
                throw BackupException.Corrupted("Checksum mismatch ${file.name}")
            }
        }
        val dataBytes = byName["data.json"]?.bytes ?: throw BackupException.InvalidFormat("Missing data")
        val data = runCatching { json.decodeFromString(BackupData.serializer(), dataBytes.decodeToString()) }
            .getOrElse { throw BackupException.Corrupted("Bad data") }

        // Referential integrity checks.
        val categoryIds = data.categories.map { it.id }.toSet()
        val documentIds = data.documents.map { it.id }.toSet()
        if (data.categories.isEmpty()) throw BackupException.Corrupted("No categories")
        if (data.documents.any { it.categoryId !in categoryIds }) throw BackupException.Corrupted("Dangling category")
        if (data.attachments.any { it.documentId !in documentIds }) throw BackupException.Corrupted("Dangling attachment")
        if (data.reminders.any { it.documentId !in documentIds }) throw BackupException.Corrupted("Dangling reminder")
        if (data.renewals.any { it.documentId !in documentIds }) throw BackupException.Corrupted("Dangling renewal")

        val files = HashMap<String, ByteArray>()
        for (attachment in data.attachments) {
            if (!isSafeRelativeName(attachment.filePath)) throw BackupException.Corrupted("Unsafe path")
            val entry = byName["attachments/${attachment.filePath}"] ?: throw BackupException.Corrupted("Missing file")
            files[attachment.filePath] = entry.bytes
        }
        BackupPreview(manifest, data, files)
    }

    /**
     * Replaces all local data with the backup. A safety copy of the current data is written first.
     * The database part runs in a single transaction, so a failure leaves existing data untouched.
     */
    suspend fun restore(preview: BackupPreview) = withContext(Dispatchers.IO) {
        storage.writeSafetyBackup(createBackup())

        val existing = storage.listAttachments().toSet()
        val written = mutableListOf<String>()
        try {
            for ((path, bytes) in preview.files) {
                if (path !in existing) {
                    storage.writeAttachment(path, bytes)
                    written += path
                }
            }
            val data = preview.data
            db.transaction {
                db.documentQueries.deleteAll() // cascades attachments, reminders, history
                db.documentCategoryQueries.deleteAll()
                data.categories.forEach {
                    db.documentCategoryQueries.insertWithId(it.id, it.name, it.icon, it.color, it.isDefault)
                }
                data.documents.forEach {
                    db.documentQueries.insertWithId(
                        Documents(
                            it.id, it.name, it.documentNumber, it.categoryId, it.issuingAuthority, it.issueDate,
                            it.expiryDate, it.renewalDate, it.description, it.notes, it.renewalInProgress,
                            it.createdAt, it.updatedAt, it.isArchived,
                        )
                    )
                }
                data.attachments.forEach {
                    db.documentAttachmentQueries.insertWithId(
                        DocumentAttachments(it.id, it.documentId, it.fileName, it.filePath, it.fileType, it.createdAt)
                    )
                }
                data.reminders.forEach {
                    db.documentReminderQueries.insertWithId(
                        DocumentReminders(it.id, it.documentId, it.reminderDaysBefore, it.notificationEnabled)
                    )
                }
                data.renewals.forEach {
                    db.renewalHistoryQueries.insertWithId(
                        RenewalHistory(
                            it.id, it.documentId, it.previousIssueDate, it.previousExpiryDate,
                            it.newIssueDate, it.newExpiryDate, it.renewalDate, it.notes,
                        )
                    )
                }
                data.settings?.let {
                    // Biometric lock state is kept from the current device for safety.
                    val currentBiometric = db.appSettingsQueries.select().executeAsOneOrNull()?.biometricLockEnabled ?: 0L
                    db.appSettingsQueries.update(
                        it.language, it.calendarType, it.theme, it.defaultReminderDays,
                        it.expiryWarningDays, currentBiometric, it.reminderHour,
                    )
                }
            }
        } catch (e: Throwable) {
            written.forEach { storage.deleteAttachment(it) }
            throw e
        }
        // Remove files that belonged only to the replaced data.
        attachmentManager.cleanupOrphans()
    }

}
