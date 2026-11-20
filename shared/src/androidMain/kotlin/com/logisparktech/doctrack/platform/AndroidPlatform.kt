package com.logisparktech.doctrack.platform

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.logisparktech.doctrack.core.notification.NotificationNavigator
import com.logisparktech.doctrack.core.platform.BiometricAuthenticator
import com.logisparktech.doctrack.core.platform.PlatformActions
import com.logisparktech.doctrack.core.storage.FileStorage
import com.logisparktech.doctrack.core.storage.isSafeRelativeName
import com.logisparktech.doctrack.core.storage.newAttachmentName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

/** Tracks the foreground activity for APIs that need one (biometric prompt). */
object ActivityHolder {
    private var ref: WeakReference<Activity>? = null
    var current: Activity?
        get() = ref?.get()
        set(value) { ref = value?.let { WeakReference(it) } }
}

const val EXTRA_DOCUMENT_ID = "doctrack_document_id"

/** Call from the activity's onCreate/onNewIntent to route notification taps. */
fun handleNotificationIntent(intent: Intent?) {
    val id = intent?.getLongExtra(EXTRA_DOCUMENT_ID, -1L) ?: -1L
    if (id > 0) {
        NotificationNavigator.openDocument(id)
        intent?.removeExtra(EXTRA_DOCUMENT_ID)
    }
}

class AndroidFileStorage(private val context: Context) : FileStorage {
    private val attachmentsDir: File get() = File(context.filesDir, "attachments").apply { mkdirs() }
    private val shareDir: File get() = File(context.cacheDir, "shared").apply { mkdirs() }

    private fun fileFor(relativePath: String): File {
        require(isSafeRelativeName(relativePath)) { "Unsafe attachment path" }
        return File(attachmentsDir, relativePath)
    }

    override suspend fun saveAttachment(bytes: ByteArray, extension: String): String = withContext(Dispatchers.IO) {
        val name = newAttachmentName(extension)
        fileFor(name).writeBytes(bytes)
        name
    }

    override suspend fun writeAttachment(relativePath: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        fileFor(relativePath).writeBytes(bytes)
    }

    override suspend fun readAttachment(relativePath: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { fileFor(relativePath).takeIf { it.exists() }?.readBytes() }.getOrNull()
    }

    override suspend fun deleteAttachment(relativePath: String) {
        withContext(Dispatchers.IO) { runCatching { fileFor(relativePath).delete() } }
    }

    override suspend fun listAttachments(): List<String> = withContext(Dispatchers.IO) {
        attachmentsDir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
    }

    override fun absoluteAttachmentPath(relativePath: String): String = fileFor(relativePath).absolutePath

    override suspend fun writeTempFile(name: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        // Old shared files are removed so personal data doesn't linger in the cache.
        shareDir.listFiles()?.forEach { it.delete() }
        val safe = name.filter { it.isLetterOrDigit() || it in "-_." }.ifEmpty { "file" }
        File(shareDir, safe).apply { writeBytes(bytes) }.absolutePath
    }

    override suspend fun writeSafetyBackup(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "safety").apply { mkdirs() }
        File(dir, "before-restore.zip").writeBytes(bytes)
    }
}

class AndroidPlatformActions(private val context: Context) : PlatformActions {
    private val authority get() = "${context.packageName}.doctrack.fileprovider"

    private fun uriFor(path: String): Uri = FileProvider.getUriForFile(context, authority, File(path))

    override fun shareFile(absolutePath: String, mimeType: String, title: String): Boolean = runCatching {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uriFor(absolutePath))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)

    override fun openFile(absolutePath: String, mimeType: String): Boolean = try {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFor(absolutePath), mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(view)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: IllegalArgumentException) {
        false
    }

    override fun openAppSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        }
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

class AndroidBiometricAuthenticator(private val context: Context) : BiometricAuthenticator {
    // BIOMETRIC_WEAK | DEVICE_CREDENTIAL is supported on every API level the app runs on.
    private val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    override fun isAvailable(): Boolean =
        BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    override suspend fun authenticate(title: String, subtitle: String, cancelLabel: String): Boolean =
        withContext(Dispatchers.Main) {
            val activity = ActivityHolder.current as? FragmentActivity ?: return@withContext false
            suspendCancellableCoroutine { cont ->
                val prompt = BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            if (cont.isActive) cont.resume(false)
                        }
                        // onAuthenticationFailed: a single failed attempt; the prompt stays open.
                    },
                )
                val info = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setAllowedAuthenticators(authenticators)
                    .build()
                prompt.authenticate(info)
                cont.invokeOnCancellation { prompt.cancelAuthentication() }
            }
        }
}
