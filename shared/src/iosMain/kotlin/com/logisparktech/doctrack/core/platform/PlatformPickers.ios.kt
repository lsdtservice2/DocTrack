package com.logisparktech.doctrack.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.logisparktech.doctrack.core.storage.FileStorage
import com.logisparktech.doctrack.domain.model.PickedFile
import com.logisparktech.doctrack.platform.presentShareSheet
import com.logisparktech.doctrack.platform.requestIosNotificationPermission
import com.logisparktech.doctrack.platform.toByteArray
import com.logisparktech.doctrack.platform.topViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.UniformTypeIdentifiers.UTTypeZIP
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private const val MAX_IMAGE_DIMENSION = 2048.0
private const val MAX_BACKUP_BYTES = 1024L * 1024 * 1024

/** Holds delegates strongly while a picker is on screen (UIKit keeps only weak references). */
private class PickerHost(var onResult: (PickResult) -> Unit) {
    var documentDelegate: DocumentDelegate? = null
    var photoDelegate: PhotoDelegate? = null
}

private class DocumentDelegate(private val type: PickType, private val deliver: (PickResult) -> Unit) :
    NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return deliver(PickResult.Cancelled)
        val access = url.startAccessingSecurityScopedResource()
        val result = try {
            val data = NSData.dataWithContentsOfURL(url)
            val limit = if (type == PickType.BACKUP) MAX_BACKUP_BYTES else MAX_ATTACHMENT_BYTES
            when {
                data == null -> PickResult.Failed
                data.length.toLong() > limit -> PickResult.TooLarge
                else -> {
                    val name = url.lastPathComponent ?: "file"
                    val mime = if (type == PickType.PDF) "application/pdf" else "application/zip"
                    PickResult.Success(PickedFile(name, data.toByteArray(), mime))
                }
            }
        } finally {
            if (access) url.stopAccessingSecurityScopedResource()
        }
        deliver(result)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = deliver(PickResult.Cancelled)
}

private class PhotoDelegate(private val deliver: (PickResult) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: return deliver(PickResult.Cancelled)
        val provider = result.itemProvider
        val name = (provider.suggestedName ?: "image") + ".jpg"
        provider.loadDataRepresentationForTypeIdentifier(UTTypeImage.identifier) { data, _ ->
            val picked = data?.let { compressImage(it) }
            dispatch_async(dispatch_get_main_queue()) {
                deliver(
                    when {
                        picked == null -> PickResult.Failed
                        picked.size > MAX_ATTACHMENT_BYTES -> PickResult.TooLarge
                        else -> PickResult.Success(PickedFile(name, picked, "image/jpeg"))
                    }
                )
            }
        }
    }
}

/** Downscales large photos and re-encodes as JPEG. */
@OptIn(ExperimentalForeignApi::class)
private fun compressImage(data: NSData): ByteArray? {
    val image = UIImage.imageWithData(data) ?: return null
    val (width, height) = image.size.useContents { width to height }
    if (width <= 0.0 || height <= 0.0) return null
    val scale = minOf(1.0, MAX_IMAGE_DIMENSION / maxOf(width, height))
    val output = if (scale < 1.0) {
        val w = width * scale
        val h = height * scale
        UIGraphicsImageRenderer(size = CGSizeMake(w, h)).imageWithActions { _ ->
            image.drawInRect(CGRectMake(0.0, 0.0, w, h))
        }
    } else image
    return UIImageJPEGRepresentation(output, 0.85)?.toByteArray()
}

@Composable
actual fun rememberFilePicker(type: PickType, onResult: (PickResult) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onResult)
    val host = remember { PickerHost { } }
    host.onResult = { callback(it) }
    return launch@{
        val top = topViewController() ?: return@launch callback(PickResult.Failed)
        when (type) {
            PickType.IMAGE -> {
                val config = PHPickerConfiguration().apply {
                    setFilter(PHPickerFilter.imagesFilter)
                    setSelectionLimit(1)
                }
                val delegate = PhotoDelegate { result -> host.photoDelegate = null; host.onResult(result) }
                host.photoDelegate = delegate
                val picker = PHPickerViewController(configuration = config)
                picker.delegate = delegate
                top.presentViewController(picker, animated = true, completion = null)
            }
            PickType.PDF, PickType.BACKUP -> {
                val types = if (type == PickType.PDF) listOf(UTTypePDF) else listOf(UTTypeZIP)
                val delegate = DocumentDelegate(type) { result -> host.documentDelegate = null; host.onResult(result) }
                host.documentDelegate = delegate
                val picker = UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = true)
                picker.delegate = delegate
                picker.allowsMultipleSelection = false
                top.presentViewController(picker, animated = true, completion = null)
            }
        }
    }
}

@Composable
actual fun rememberFileSaver(onResult: (Boolean) -> Unit): (suggestedName: String, bytes: ByteArray, mimeType: String) -> Unit {
    val callback by rememberUpdatedState(onResult)
    val storage = koinInject<FileStorage>()
    val scope = rememberCoroutineScope()
    return { name, bytes, _ ->
        scope.launch {
            val path = storage.writeTempFile(name, bytes)
            presentShareSheet(path) { completed -> callback(completed) }
        }
    }
}

@Composable
actual fun rememberNotificationPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onResult)
    return { requestIosNotificationPermission { callback(it) } }
}
