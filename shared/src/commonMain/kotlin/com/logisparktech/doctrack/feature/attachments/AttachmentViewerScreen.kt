package com.logisparktech.doctrack.feature.attachments

import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.logisparktech.doctrack.core.localization.strings
import com.logisparktech.doctrack.core.platform.PlatformActions
import com.logisparktech.doctrack.core.storage.FileStorage
import com.logisparktech.doctrack.domain.model.Attachment
import com.logisparktech.doctrack.domain.model.AttachmentType
import com.logisparktech.doctrack.domain.repository.AttachmentRepository
import com.logisparktech.doctrack.domain.usecase.AttachmentManager
import com.logisparktech.doctrack.ui.components.AttachmentImage
import com.logisparktech.doctrack.ui.components.BackButton
import com.logisparktech.doctrack.ui.components.ConfirmDialog
import com.logisparktech.doctrack.ui.components.EmptyState
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private fun mimeFor(attachment: Attachment) = when {
    attachment.type == AttachmentType.PDF -> "application/pdf"
    attachment.filePath.endsWith(".png") -> "image/png"
    else -> "image/jpeg"
}

@Composable
fun AttachmentViewerScreen(attachmentId: Long, onBack: () -> Unit) {
    val s = strings
    val repository = koinInject<AttachmentRepository>()
    val storage = koinInject<FileStorage>()
    val actions = koinInject<PlatformActions>()
    val manager = koinInject<AttachmentManager>()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }

    val loaded by produceState<Pair<Boolean, Attachment?>>(false to null, attachmentId) {
        value = true to repository.getAttachment(attachmentId)
    }
    val attachment = loaded.second

    fun openExternally() {
        val a = attachment ?: return
        if (!actions.openFile(storage.absoluteAttachmentPath(a.filePath), mimeFor(a))) {
            scope.launch { snackbar.showSnackbar(s.cannotOpenFile) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(attachment?.fileName ?: s.attachment, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (attachment != null) {
                        IconButton(onClick = {
                            actions.shareFile(storage.absoluteAttachmentPath(attachment.filePath), mimeFor(attachment), attachment.fileName)
                        }) { Icon(Icons.Outlined.Share, s.shareBackup) }
                        IconButton(onClick = ::openExternally) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, s.openWithApp) }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, s.delete, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                !loaded.first -> CircularProgressIndicator()
                attachment == null -> EmptyState(Icons.Outlined.SearchOff, s.documentNotFound)
                attachment.type == AttachmentType.PDF -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.PictureAsPdf, null, Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text(attachment.fileName, style = MaterialTheme.typography.titleMedium)
                    Text(s.pdfDocument, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = ::openExternally) { Text(s.viewAttachment) }
                }
                else -> ZoomableImage(attachment.filePath, attachment.fileName)
            }
        }
    }

    if (confirmDelete && attachment != null) {
        ConfirmDialog(
            title = s.deleteAttachmentTitle,
            message = s.deleteAttachmentMessage,
            confirmLabel = s.delete,
            destructive = true,
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    manager.delete(attachment.id)
                    onBack()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun ZoomableImage(path: String, description: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { _, zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    AttachmentImage(
        relativePath = path,
        contentDescription = description,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .transformable(transform)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}
