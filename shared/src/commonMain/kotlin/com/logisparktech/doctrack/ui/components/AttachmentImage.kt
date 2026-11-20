package com.logisparktech.doctrack.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.logisparktech.doctrack.core.storage.FileStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.koin.compose.koinInject

private sealed interface ImageLoad {
    data object Loading : ImageLoad
    data class Loaded(val bitmap: ImageBitmap) : ImageLoad
    data object Failed : ImageLoad
}

/** Decodes an image stored in app-private storage. Decoding happens off the main thread. */
@Composable
fun AttachmentImage(
    relativePath: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val storage = koinInject<FileStorage>()
    val state by produceState<ImageLoad>(ImageLoad.Loading, relativePath) {
        value = withContext(Dispatchers.Default) {
            runCatching { storage.readAttachment(relativePath)?.decodeToImageBitmap() }.getOrNull()
        }?.let { ImageLoad.Loaded(it) } ?: ImageLoad.Failed
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        when (val s = state) {
            ImageLoad.Loading -> CircularProgressIndicator()
            ImageLoad.Failed -> Icon(Icons.Outlined.BrokenImage, contentDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            is ImageLoad.Loaded -> Image(s.bitmap, contentDescription, contentScale = contentScale, modifier = Modifier.matchParentSize())
        }
    }
}

/** Decodes raw bytes (used for files picked but not saved yet). */
@Composable
fun BytesImage(bytes: ByteArray, contentDescription: String?, modifier: Modifier = Modifier) {
    val state by produceState<ImageLoad>(ImageLoad.Loading, bytes) {
        value = withContext(Dispatchers.Default) { runCatching { bytes.decodeToImageBitmap() }.getOrNull() }
            ?.let { ImageLoad.Loaded(it) } ?: ImageLoad.Failed
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        when (val s = state) {
            ImageLoad.Loading -> CircularProgressIndicator()
            ImageLoad.Failed -> Icon(Icons.Outlined.BrokenImage, contentDescription)
            is ImageLoad.Loaded -> Image(s.bitmap, contentDescription, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}
