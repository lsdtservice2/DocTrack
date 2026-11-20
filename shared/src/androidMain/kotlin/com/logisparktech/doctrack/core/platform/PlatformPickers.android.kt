package com.logisparktech.doctrack.core.platform

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import com.logisparktech.doctrack.domain.model.PickedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val MAX_IMAGE_DIMENSION = 2048
private const val MAX_BACKUP_BYTES = 1024L * 1024 * 1024

@Composable
actual fun rememberFilePicker(type: PickType, onResult: (PickResult) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            callback(PickResult.Cancelled)
        } else {
            scope.launch {
                val result = withContext(Dispatchers.IO) { readPicked(context, uri, type) }
                callback(result)
            }
        }
    }
    val mimeTypes = when (type) {
        PickType.IMAGE -> arrayOf("image/*")
        PickType.PDF -> arrayOf("application/pdf")
        PickType.BACKUP -> arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
    }
    return { launcher.launch(mimeTypes) }
}

private fun readPicked(context: Context, uri: Uri, type: PickType): PickResult = try {
    val resolver = context.contentResolver
    var name = "file"
    var size = -1L
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            c.getString(0)?.let { name = it }
            if (!c.isNull(1)) size = c.getLong(1)
        }
    }
    val limit = if (type == PickType.BACKUP) MAX_BACKUP_BYTES else MAX_ATTACHMENT_BYTES
    if (size > limit) {
        PickResult.TooLarge
    } else {
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("No stream")
        if (bytes.size > limit) PickResult.TooLarge
        else when (type) {
            PickType.IMAGE -> {
                val compressed = compressImage(bytes) ?: throw IllegalStateException("Not an image")
                PickResult.Success(PickedFile(name.substringBeforeLast('.') + ".jpg", compressed, "image/jpeg"))
            }
            PickType.PDF -> PickResult.Success(PickedFile(if (name.endsWith(".pdf", true)) name else "$name.pdf", bytes, "application/pdf"))
            PickType.BACKUP -> PickResult.Success(PickedFile(name, bytes, "application/zip"))
        }
    }
} catch (e: Exception) {
    PickResult.Failed
}

/** Downscales large photos and re-encodes them as JPEG to keep storage and memory small. */
private fun compressImage(bytes: ByteArray): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= MAX_IMAGE_DIMENSION || bounds.outHeight / (sample * 2) >= MAX_IMAGE_DIMENSION) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return null
    val scale = minOf(1f, MAX_IMAGE_DIMENSION.toFloat() / maxOf(decoded.width, decoded.height))
    val bitmap = if (scale < 1f) {
        Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
    } else decoded
    return ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        out.toByteArray()
    }
}

@Composable
actual fun rememberFileSaver(onResult: (Boolean) -> Unit): (suggestedName: String, bytes: ByteArray, mimeType: String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onResult)
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val bytes = pending
        pending = null
        if (uri == null || bytes == null) {
            callback(false)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } != null
                }.getOrDefault(false)
            }
            callback(ok)
        }
    }
    return { name, bytes, _ ->
        pending = bytes
        launcher.launch(name)
    }
}

@Composable
actual fun rememberNotificationPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        callback(granted && NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    return {
        if (Build.VERSION.SDK_INT >= 33) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            callback(NotificationManagerCompat.from(context).areNotificationsEnabled())
        }
    }
}
