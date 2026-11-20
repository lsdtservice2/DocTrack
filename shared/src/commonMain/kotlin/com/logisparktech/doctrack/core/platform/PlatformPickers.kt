package com.logisparktech.doctrack.core.platform

import androidx.compose.runtime.Composable
import com.logisparktech.doctrack.domain.model.PickedFile

enum class PickType { IMAGE, PDF, BACKUP }

sealed interface PickResult {
    data class Success(val file: PickedFile) : PickResult
    data object Cancelled : PickResult
    data object TooLarge : PickResult
    data object Failed : PickResult
}

/** Returns a launcher that opens the native picker. Images are downscaled/compressed before returning. */
@Composable
expect fun rememberFilePicker(type: PickType, onResult: (PickResult) -> Unit): () -> Unit

/**
 * Returns a launcher that lets the user save a file to a location of their choice
 * (Android: system "Save as" dialog; iOS: share sheet, which includes "Save to Files").
 */
@Composable
expect fun rememberFileSaver(onResult: (Boolean) -> Unit): (suggestedName: String, bytes: ByteArray, mimeType: String) -> Unit

/** Returns a launcher that requests notification permission. */
@Composable
expect fun rememberNotificationPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit
