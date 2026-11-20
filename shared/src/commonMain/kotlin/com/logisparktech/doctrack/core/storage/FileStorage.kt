package com.logisparktech.doctrack.core.storage

/**
 * App-private file storage. Attachments live in a directory only this app can access
 * (Android internal storage / iOS Application Support with complete file protection).
 * Paths stored in the database are relative to the attachment directory.
 */
interface FileStorage {
    suspend fun saveAttachment(bytes: ByteArray, extension: String): String
    suspend fun writeAttachment(relativePath: String, bytes: ByteArray)
    suspend fun readAttachment(relativePath: String): ByteArray?
    suspend fun deleteAttachment(relativePath: String)
    suspend fun listAttachments(): List<String>
    fun absoluteAttachmentPath(relativePath: String): String

    /** Writes a temporary file (e.g. a backup to share) and returns its absolute path. */
    suspend fun writeTempFile(name: String, bytes: ByteArray): String

    /** Keeps the most recent safety copy taken automatically before a restore. */
    suspend fun writeSafetyBackup(bytes: ByteArray)
}

/** Generates a unique relative attachment file name. */
@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
fun newAttachmentName(extension: String): String {
    val ext = extension.lowercase().filter { it.isLetterOrDigit() }.ifEmpty { "bin" }
    return "att_${kotlin.uuid.Uuid.random().toHexString()}.$ext"
}

/** Only accept simple file names (no directories) to prevent path traversal from restored backups. */
fun isSafeRelativeName(name: String): Boolean =
    name.isNotEmpty() && name.length <= 128 && !name.startsWith(".") &&
        name.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' }
