package com.logisparktech.doctrack.core.platform

interface PlatformActions {
    /** Opens the native share sheet for a local file. */
    fun shareFile(absolutePath: String, mimeType: String, title: String): Boolean

    /** Opens a local file (e.g. a PDF) in a viewer. Returns false when no viewer is available. */
    fun openFile(absolutePath: String, mimeType: String): Boolean

    /** Opens this app's page in system settings (used after a permission is permanently denied). */
    fun openAppSettings()
}

interface BiometricAuthenticator {
    fun isAvailable(): Boolean
    suspend fun authenticate(title: String, subtitle: String, cancelLabel: String): Boolean
}

/** Maximum file size accepted for attachments. */
const val MAX_ATTACHMENT_BYTES = 25L * 1024 * 1024
