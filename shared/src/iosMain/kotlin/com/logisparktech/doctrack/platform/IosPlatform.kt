package com.logisparktech.doctrack.platform

import com.logisparktech.doctrack.core.platform.BiometricAuthenticator
import com.logisparktech.doctrack.core.platform.PlatformActions
import com.logisparktech.doctrack.core.storage.FileStorage
import com.logisparktech.doctrack.core.storage.isSafeRelativeName
import com.logisparktech.doctrack.core.storage.newAttachmentName
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionComplete
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIDocumentInteractionController
import platform.UIKit.UIDocumentInteractionControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

private fun directory(kind: ULong): String =
    (NSSearchPathForDirectoriesInDomains(kind, NSUserDomainMask, true).first() as String)

/**
 * Attachments live in Application Support with complete data protection (encrypted while the
 * device is locked) and are excluded from iCloud/iTunes backups to keep them on this device.
 */
@OptIn(ExperimentalForeignApi::class)
class IosFileStorage : FileStorage {
    private val fm = NSFileManager.defaultManager

    private val attachmentsDir: String by lazy { ensureDir("${directory(NSApplicationSupportDirectory)}/attachments") }
    private val shareDir: String by lazy { ensureDir("${directory(NSCachesDirectory)}/shared") }
    private val safetyDir: String by lazy { ensureDir("${directory(NSApplicationSupportDirectory)}/safety") }

    private fun ensureDir(path: String): String {
        if (!fm.fileExistsAtPath(path)) {
            fm.createDirectoryAtPath(path, true, mapOf<Any?, Any?>(NSFileProtectionKey to NSFileProtectionComplete), null)
        }
        NSURL.fileURLWithPath(path).setResourceValue(NSNumber(bool = true), NSURLIsExcludedFromBackupKey, null)
        return path
    }

    private fun pathFor(relativePath: String): String {
        require(isSafeRelativeName(relativePath)) { "Unsafe attachment path" }
        return "$attachmentsDir/$relativePath"
    }

    private fun write(path: String, bytes: ByteArray) {
        val ok = bytes.toNSData().writeToFile(path, true)
        check(ok) { "Write failed" }
        fm.setAttributes(mapOf<Any?, Any?>(NSFileProtectionKey to NSFileProtectionComplete), path, null)
    }

    override suspend fun saveAttachment(bytes: ByteArray, extension: String): String = withContext(Dispatchers.IO) {
        val name = newAttachmentName(extension)
        write(pathFor(name), bytes)
        name
    }

    override suspend fun writeAttachment(relativePath: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        write(pathFor(relativePath), bytes)
    }

    override suspend fun readAttachment(relativePath: String): ByteArray? = withContext(Dispatchers.IO) {
        NSData.dataWithContentsOfFile(pathFor(relativePath))?.toByteArray()
    }

    override suspend fun deleteAttachment(relativePath: String) {
        withContext(Dispatchers.IO) {
            if (isSafeRelativeName(relativePath)) fm.removeItemAtPath(pathFor(relativePath), null)
        }
    }

    override suspend fun listAttachments(): List<String> = withContext(Dispatchers.IO) {
        fm.contentsOfDirectoryAtPath(attachmentsDir, null)?.filterIsInstance<String>().orEmpty()
    }

    override fun absoluteAttachmentPath(relativePath: String): String = pathFor(relativePath)

    override suspend fun writeTempFile(name: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        fm.contentsOfDirectoryAtPath(shareDir, null)?.filterIsInstance<String>()?.forEach {
            fm.removeItemAtPath("$shareDir/$it", null)
        }
        val safe = name.filter { it.isLetterOrDigit() || it in "-_." }.ifEmpty { "file" }
        val path = "$shareDir/$safe"
        write(path, bytes)
        path
    }

    override suspend fun writeSafetyBackup(bytes: ByteArray) = withContext(Dispatchers.IO) {
        write("$safetyDir/before-restore.zip", bytes)
    }
}

class IosPlatformActions : PlatformActions {
    private var previewDelegate: PreviewDelegate? = null
    private var previewController: UIDocumentInteractionController? = null

    override fun shareFile(absolutePath: String, mimeType: String, title: String): Boolean {
        presentShareSheet(absolutePath, null)
        return true
    }

    override fun openFile(absolutePath: String, mimeType: String): Boolean {
        val top = topViewController() ?: return false
        val delegate = PreviewDelegate(top)
        val controller = UIDocumentInteractionController.interactionControllerWithURL(NSURL.fileURLWithPath(absolutePath))
        controller.delegate = delegate
        // Keep strong references while the preview is visible.
        previewDelegate = delegate
        previewController = controller
        return controller.presentPreviewAnimated(true)
    }

    override fun openAppSettings() {
        NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let {
            UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any?>(), null)
        }
    }

    private class PreviewDelegate(private val presenter: UIViewController) : NSObject(), UIDocumentInteractionControllerDelegateProtocol {
        override fun documentInteractionControllerViewControllerForPreview(controller: UIDocumentInteractionController): UIViewController = presenter
    }
}

/** Presents the native share sheet (which also offers "Save to Files"). */
fun presentShareSheet(absolutePath: String, onComplete: ((Boolean) -> Unit)?) {
    val top = topViewController() ?: run { onComplete?.invoke(false); return }
    val controller = UIActivityViewController(listOf(NSURL.fileURLWithPath(absolutePath)), null)
    controller.popoverPresentationController?.sourceView = top.view
    controller.completionWithItemsHandler = { _, completed, _, _ -> onComplete?.invoke(completed) }
    top.presentViewController(controller, animated = true, completion = null)
}

class IosBiometricAuthenticator : BiometricAuthenticator {
    // Device-owner authentication allows Face ID / Touch ID with passcode fallback.
    override fun isAvailable(): Boolean = LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, null)

    override suspend fun authenticate(title: String, subtitle: String, cancelLabel: String): Boolean =
        suspendCancellableCoroutine { cont ->
            val context = LAContext()
            context.localizedCancelTitle = cancelLabel
            context.evaluatePolicy(LAPolicyDeviceOwnerAuthentication, subtitle.ifBlank { title }) { success, _ ->
                dispatch_async(dispatch_get_main_queue()) { if (cont.isActive) cont.resume(success) }
            }
            cont.invokeOnCancellation { context.invalidate() }
        }
}
