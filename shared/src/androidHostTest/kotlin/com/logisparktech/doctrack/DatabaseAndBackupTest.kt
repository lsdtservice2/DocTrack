package com.logisparktech.doctrack

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.logisparktech.doctrack.core.common.DateProvider
import com.logisparktech.doctrack.core.storage.FileStorage
import com.logisparktech.doctrack.core.storage.ZipArchive
import com.logisparktech.doctrack.core.storage.newAttachmentName
import com.logisparktech.doctrack.data.backup.BackupException
import com.logisparktech.doctrack.data.backup.BackupManager
import com.logisparktech.doctrack.data.repository.SqlAttachmentRepository
import com.logisparktech.doctrack.data.repository.SqlCategoryRepository
import com.logisparktech.doctrack.data.repository.SqlDocumentRepository
import com.logisparktech.doctrack.data.repository.SqlReminderRepository
import com.logisparktech.doctrack.db.DocTrackDatabase
import com.logisparktech.doctrack.domain.model.AttachmentType
import com.logisparktech.doctrack.domain.model.DocumentInput
import com.logisparktech.doctrack.domain.model.PickedFile
import com.logisparktech.doctrack.domain.usecase.AttachmentManager
import com.logisparktech.doctrack.domain.usecase.DeleteDocumentUseCase
import com.logisparktech.doctrack.domain.usecase.PendingAttachment
import com.logisparktech.doctrack.domain.usecase.SaveDocumentUseCase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeDates : DateProvider {
    var millis = 1_750_000_000_000L
    override fun today() = LocalDate(2025, 6, 15)
    override fun nowMillis() = millis++
    override fun timeZone() = TimeZone.UTC
}

private class MemoryStorage : FileStorage {
    val files = linkedMapOf<String, ByteArray>()
    var safety: ByteArray? = null
    override suspend fun saveAttachment(bytes: ByteArray, extension: String) = newAttachmentName(extension).also { files[it] = bytes }
    override suspend fun writeAttachment(relativePath: String, bytes: ByteArray) { files[relativePath] = bytes }
    override suspend fun readAttachment(relativePath: String) = files[relativePath]
    override suspend fun deleteAttachment(relativePath: String) { files.remove(relativePath) }
    override suspend fun listAttachments() = files.keys.toList()
    override fun absoluteAttachmentPath(relativePath: String) = "/mem/$relativePath"
    override suspend fun writeTempFile(name: String, bytes: ByteArray) = "/tmp/$name"
    override suspend fun writeSafetyBackup(bytes: ByteArray) { safety = bytes }
}

private class Env {
    val db = DocTrackDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") }, DocTrackDatabase.Schema)
    )
    val dates = FakeDates()
    val storage = MemoryStorage()
    val documents = SqlDocumentRepository(db, dates)
    val attachments = SqlAttachmentRepository(db, dates)
    val reminders = SqlReminderRepository(db)
    val categories = SqlCategoryRepository(db)
    val attachmentManager = AttachmentManager(attachments, storage)
    val save = SaveDocumentUseCase(documents, attachmentManager)
    val delete = DeleteDocumentUseCase(documents, storage)
    val backup = BackupManager(db, storage, attachmentManager, dates)
}

private fun input(name: String, categoryId: Long, expiry: LocalDate?) = DocumentInput(
    name = name, documentNumber = "N-1", categoryId = categoryId, issuingAuthority = "DoTM",
    issueDate = LocalDate(2020, 1, 1), expiryDate = expiry, renewalDate = null,
    description = "desc", notes = "note", renewalInProgress = false,
)

class DatabaseAndBackupTest {

    @Test
    fun defaultCategoriesAndSettingsAreSeeded() = runTest {
        val env = Env()
        assertEquals(14, env.categories.getCategories().size)
        assertEquals("30,15,7,3,1,0", env.db.appSettingsQueries.select().executeAsOne().defaultReminderDays)
    }

    @Test
    fun renewalPreservesHistoryAndUpdatesDates() = runTest {
        val env = Env()
        val cat = env.categories.getCategories().first().id
        val id = env.save(null, input("Licence", cat, LocalDate(2025, 7, 1)), listOf(30, 7), emptyList(), emptyList())

        env.documents.renew(id, LocalDate(2025, 6, 20), LocalDate(2030, 6, 20), LocalDate(2025, 6, 20), "renewed at DoTM")
        env.documents.renew(id, LocalDate(2030, 6, 1), LocalDate(2035, 6, 1), LocalDate(2030, 6, 1), "")

        val doc = env.documents.getDocument(id)!!
        assertEquals(LocalDate(2035, 6, 1), doc.expiryDate)
        val history = env.db.renewalHistoryQueries.selectByDocument(id).executeAsList()
        assertEquals(2, history.size)
        assertEquals("2030-06-20", history[0].previousExpiryDate)
        assertEquals("2025-07-01", history[1].previousExpiryDate)
        assertEquals("2020-01-01", history[1].previousIssueDate)
        assertEquals(listOf(30, 7), env.reminders.getReminders(id).map { it.daysBefore })
    }

    @Test
    fun deletingDocumentCascadesRowsAndFiles() = runTest {
        val env = Env()
        val cat = env.categories.getCategories().first().id
        val id = env.save(
            null, input("Passport", cat, LocalDate(2026, 1, 1)), listOf(7),
            listOf(
                PendingAttachment(PickedFile("front.jpg", byteArrayOf(1, 2, 3), "image/jpeg"), AttachmentType.FRONT_IMAGE),
                PendingAttachment(PickedFile("scan.pdf", byteArrayOf(4, 5), "application/pdf"), AttachmentType.PDF),
            ),
            emptyList(),
        )
        env.documents.renew(id, null, LocalDate(2031, 1, 1), LocalDate(2025, 6, 15), "")
        assertEquals(2, env.storage.files.size)

        env.delete(id)

        assertNull(env.documents.getDocument(id))
        assertTrue(env.storage.files.isEmpty())
        assertEquals(0, env.db.documentAttachmentQueries.selectAll().executeAsList().size)
        assertEquals(0, env.db.documentReminderQueries.selectAll().executeAsList().size)
        assertEquals(0, env.db.renewalHistoryQueries.selectAll().executeAsList().size)
    }

    @Test
    fun frontImageReplacementRemovesOldFile() = runTest {
        val env = Env()
        val cat = env.categories.getCategories().first().id
        val id = env.save(null, input("ID", cat, null), emptyList(), emptyList(), emptyList())
        env.attachmentManager.add(id, PickedFile("a.jpg", byteArrayOf(1), "image/jpeg"), AttachmentType.FRONT_IMAGE)
        env.attachmentManager.add(id, PickedFile("b.jpg", byteArrayOf(2), "image/jpeg"), AttachmentType.FRONT_IMAGE)
        val rows = env.attachments.getAttachments(id)
        assertEquals(listOf("b.jpg"), rows.map { it.fileName })
        assertEquals(1, env.storage.files.size)
    }

    @Test
    fun backupRestoresEverythingIntoAnotherDevice() = runTest {
        val source = Env()
        val catId = source.categories.add("Gym", "star", 0xFF059669)
        val id = source.save(
            null, input("Membership", catId, LocalDate(2025, 12, 31)), listOf(15, 1),
            listOf(PendingAttachment(PickedFile("card.jpg", ByteArray(5000) { it.toByte() }, "image/jpeg"), AttachmentType.FRONT_IMAGE)),
            emptyList(),
        )
        source.documents.renew(id, LocalDate(2025, 6, 1), LocalDate(2026, 12, 31), LocalDate(2025, 6, 1), "yearly")
        source.db.appSettingsQueries.update("ne", "BS", "DARK", "10,0", 20, 0, 8)
        val zip = source.backup.createBackup()

        val target = Env()
        val otherCat = target.categories.getCategories().first().id
        target.save(null, input("Old local doc", otherCat, null), emptyList(),
            listOf(PendingAttachment(PickedFile("old.pdf", byteArrayOf(9), "application/pdf"), AttachmentType.PDF)), emptyList())

        val preview = target.backup.inspect(zip)
        assertEquals(1, preview.manifest.documentCount)
        assertEquals(1, preview.manifest.attachmentCount)
        target.backup.restore(preview)

        assertTrue(target.storage.safety != null, "safety copy must be written before restore")
        val docs = target.db.documentQueries.selectEverything().executeAsList()
        assertEquals(listOf("Membership"), docs.map { it.name })
        assertEquals("2026-12-31", docs.single().expiryDate)
        assertEquals(1, target.db.renewalHistoryQueries.selectAll().executeAsList().size)
        assertEquals(listOf(15, 1), target.reminders.getReminders(id).map { it.daysBefore })
        assertTrue(target.categories.getCategories().any { it.name == "Gym" })
        val attachment = target.attachments.getAttachments(id).single()
        assertContentEquals(source.storage.files[attachment.filePath], target.storage.files[attachment.filePath])
        assertEquals(1, target.storage.files.size, "files of replaced data are cleaned up")
        val settings = target.db.appSettingsQueries.select().executeAsOne()
        assertEquals("ne", settings.language)
        assertEquals("BS", settings.calendarType)
    }

    @Test
    fun invalidOrTamperedBackupsAreRejectedWithoutChanges() = runTest {
        val env = Env()
        val cat = env.categories.getCategories().first().id
        env.save(null, input("Keep me", cat, null), emptyList(), emptyList(), emptyList())
        val zip = env.backup.createBackup()

        assertFailsWith<BackupException.InvalidFormat> { env.backup.inspect("garbage".encodeToByteArray()) }
        val entries = ZipArchive.read(zip).map {
            if (it.name == "data.json") ZipArchive.Entry(it.name, it.bytes.decodeToString().replace("Keep me", "Hacked!").encodeToByteArray()) else it
        }
        assertFailsWith<BackupException.Corrupted> { env.backup.inspect(ZipArchive.write(entries)) }
        val withoutManifest = ZipArchive.read(zip).filter { it.name != "manifest.json" }
        assertFailsWith<BackupException.InvalidFormat> { env.backup.inspect(ZipArchive.write(withoutManifest)) }

        assertEquals(listOf("Keep me"), env.db.documentQueries.selectEverything().executeAsList().map { it.name })
    }
}
