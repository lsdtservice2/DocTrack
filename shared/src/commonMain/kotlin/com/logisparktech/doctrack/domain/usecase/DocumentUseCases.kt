package com.logisparktech.doctrack.domain.usecase

import com.logisparktech.doctrack.core.storage.FileStorage
import com.logisparktech.doctrack.domain.model.AttachmentType
import com.logisparktech.doctrack.domain.model.DocumentInput
import com.logisparktech.doctrack.domain.model.PickedFile
import com.logisparktech.doctrack.domain.repository.AttachmentRepository
import com.logisparktech.doctrack.domain.repository.DocumentRepository
import kotlinx.datetime.LocalDate

enum class DocumentValidationError { NAME_REQUIRED, CATEGORY_REQUIRED, EXPIRY_BEFORE_ISSUE }

fun validateDocument(input: DocumentInput): Set<DocumentValidationError> = buildSet {
    if (input.name.isBlank()) add(DocumentValidationError.NAME_REQUIRED)
    if (input.categoryId <= 0) add(DocumentValidationError.CATEGORY_REQUIRED)
    if (input.issueDate != null && input.expiryDate != null && input.expiryDate < input.issueDate) {
        add(DocumentValidationError.EXPIRY_BEFORE_ISSUE)
    }
}

/** A new file to attach when the document is saved. */
class PendingAttachment(val file: PickedFile, val type: AttachmentType)

class AttachmentManager(
    private val attachments: AttachmentRepository,
    private val storage: FileStorage,
) {
    /** Stores the file privately and records it. Front/back images replace the previous one. */
    suspend fun add(documentId: Long, file: PickedFile, type: AttachmentType): Long {
        val extension = file.name.substringAfterLast('.', "").ifEmpty {
            if (type == AttachmentType.PDF) "pdf" else "jpg"
        }
        val path = storage.saveAttachment(file.bytes, extension)
        try {
            if (type == AttachmentType.FRONT_IMAGE || type == AttachmentType.BACK_IMAGE) {
                attachments.getAttachments(documentId).filter { it.type == type }.forEach { delete(it.id) }
            }
            return attachments.add(documentId, file.name, path, type)
        } catch (e: Throwable) {
            storage.deleteAttachment(path)
            throw e
        }
    }

    suspend fun delete(attachmentId: Long) {
        val attachment = attachments.getAttachment(attachmentId) ?: return
        attachments.delete(attachmentId)
        storage.deleteAttachment(attachment.filePath)
    }

    /** Removes files in storage that no database row references (e.g. after a crash). */
    suspend fun cleanupOrphans() {
        val referenced = attachments.allPaths()
        storage.listAttachments().filter { it !in referenced }.forEach { storage.deleteAttachment(it) }
    }
}

class SaveDocumentUseCase(
    private val documents: DocumentRepository,
    private val attachmentManager: AttachmentManager,
) {
    /** Returns the document id. Throws [IllegalArgumentException] when validation fails. */
    suspend operator fun invoke(
        id: Long?,
        input: DocumentInput,
        reminderDays: List<Int>,
        newAttachments: List<PendingAttachment>,
        removedAttachmentIds: List<Long>,
    ): Long {
        require(validateDocument(input).isEmpty()) { "Invalid document" }
        val days = if (input.expiryDate == null) emptyList() else reminderDays
        val documentId = if (id == null) {
            documents.insert(input, days)
        } else {
            documents.update(id, input, days)
            id
        }
        removedAttachmentIds.forEach { attachmentManager.delete(it) }
        newAttachments.forEach { attachmentManager.add(documentId, it.file, it.type) }
        return documentId
    }
}

class DeleteDocumentUseCase(
    private val documents: DocumentRepository,
    private val storage: FileStorage,
) {
    suspend operator fun invoke(id: Long) {
        // Database rows first (cascade), then the files they referenced.
        val paths = documents.delete(id)
        paths.forEach { storage.deleteAttachment(it) }
    }
}

enum class RenewalValidationError { NEW_EXPIRY_REQUIRED, EXPIRY_BEFORE_ISSUE, NOT_AFTER_PREVIOUS }

class RenewDocumentUseCase(private val documents: DocumentRepository) {

    fun validate(
        previousExpiry: LocalDate?,
        newIssueDate: LocalDate?,
        newExpiryDate: LocalDate?,
    ): Set<RenewalValidationError> = buildSet {
        if (newExpiryDate == null) add(RenewalValidationError.NEW_EXPIRY_REQUIRED)
        if (newIssueDate != null && newExpiryDate != null && newExpiryDate < newIssueDate) {
            add(RenewalValidationError.EXPIRY_BEFORE_ISSUE)
        }
        if (previousExpiry != null && newExpiryDate != null && newExpiryDate <= previousExpiry) {
            add(RenewalValidationError.NOT_AFTER_PREVIOUS)
        }
    }

    suspend operator fun invoke(
        documentId: Long,
        newIssueDate: LocalDate?,
        newExpiryDate: LocalDate,
        renewalDate: LocalDate,
        notes: String,
    ) {
        documents.renew(documentId, newIssueDate, newExpiryDate, renewalDate, notes)
    }
}
