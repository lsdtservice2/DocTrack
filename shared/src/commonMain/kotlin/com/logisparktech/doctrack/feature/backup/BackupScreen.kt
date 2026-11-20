package com.logisparktech.doctrack.feature.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.logisparktech.doctrack.core.localization.Strings
import com.logisparktech.doctrack.core.localization.dateFormatter
import com.logisparktech.doctrack.core.localization.strings
import com.logisparktech.doctrack.core.platform.PickResult
import com.logisparktech.doctrack.core.platform.PickType
import com.logisparktech.doctrack.core.platform.PlatformActions
import com.logisparktech.doctrack.core.platform.rememberFilePicker
import com.logisparktech.doctrack.core.platform.rememberFileSaver
import com.logisparktech.doctrack.core.storage.FileStorage
import com.logisparktech.doctrack.data.backup.BACKUP_MIME
import com.logisparktech.doctrack.data.backup.BackupException
import com.logisparktech.doctrack.data.backup.BackupManager
import com.logisparktech.doctrack.data.backup.BackupPreview
import com.logisparktech.doctrack.domain.repository.DocumentRepository
import com.logisparktech.doctrack.feature.document_details.DetailCard
import com.logisparktech.doctrack.ui.components.BackButton
import com.logisparktech.doctrack.ui.components.ConfirmDialog
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import org.koin.compose.koinInject

enum class BackupMessage { CREATED, SAVED, RESTORED, INVALID, CORRUPTED, VERSION, RESTORE_FAILED, BACKUP_FAILED, READ_FAILED }

data class BackupState(
    val working: Boolean = false,
    val backupBytes: ByteArray? = null,
    val backupName: String = "",
    val preview: BackupPreview? = null,
    val currentDocuments: Int = 0,
)

class BackupViewModel(
    private val manager: BackupManager,
    private val documents: DocumentRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BackupState())
    val state: StateFlow<BackupState> = _state.asStateFlow()
    private val _messages = MutableSharedFlow<BackupMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<BackupMessage> = _messages.asSharedFlow()

    fun create(fileName: String) {
        if (_state.value.working) return
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            runCatching { manager.createBackup() }
                .onSuccess { bytes ->
                    _state.update { it.copy(working = false, backupBytes = bytes, backupName = fileName) }
                    _messages.emit(BackupMessage.CREATED)
                }
                .onFailure {
                    _state.update { it.copy(working = false) }
                    _messages.emit(BackupMessage.BACKUP_FAILED)
                }
        }
    }

    fun inspect(bytes: ByteArray) {
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            try {
                val preview = manager.inspect(bytes)
                val count = documents.countDocuments()
                _state.update { it.copy(working = false, preview = preview, currentDocuments = count) }
            } catch (e: BackupException) {
                _state.update { it.copy(working = false) }
                _messages.emit(
                    when (e) {
                        is BackupException.Corrupted -> BackupMessage.CORRUPTED
                        is BackupException.UnsupportedVersion -> BackupMessage.VERSION
                        is BackupException.InvalidFormat -> BackupMessage.INVALID
                    }
                )
            } catch (e: Exception) {
                _state.update { it.copy(working = false) }
                _messages.emit(BackupMessage.INVALID)
            }
        }
    }

    fun cancelRestore() = _state.update { it.copy(preview = null) }

    fun confirmRestore() {
        val preview = _state.value.preview ?: return
        _state.update { it.copy(working = true, preview = null) }
        viewModelScope.launch {
            runCatching { manager.restore(preview) }
                .onSuccess { _messages.emit(BackupMessage.RESTORED) }
                .onFailure { _messages.emit(BackupMessage.RESTORE_FAILED) }
            _state.update { it.copy(working = false) }
        }
    }

    fun notify(message: BackupMessage) { _messages.tryEmit(message) }
}

private fun BackupMessage.text(s: Strings) = when (this) {
    BackupMessage.CREATED -> s.backupCreated
    BackupMessage.SAVED -> s.backupSaved
    BackupMessage.RESTORED -> s.restoreSuccess
    BackupMessage.INVALID -> s.errorInvalidBackup
    BackupMessage.CORRUPTED -> s.errorBackupCorrupted
    BackupMessage.VERSION -> s.errorBackupVersion
    BackupMessage.RESTORE_FAILED -> s.errorRestoreFailed
    BackupMessage.BACKUP_FAILED -> s.errorBackupFailed
    BackupMessage.READ_FAILED -> s.errorFileRead
}

@Composable
fun BackupScreen(viewModel: BackupViewModel, onBack: () -> Unit) {
    val s = strings
    val f = dateFormatter
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val storage = koinInject<FileStorage>()
    val actions = koinInject<PlatformActions>()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it.text(s)) } }

    val saver = rememberFileSaver { ok -> if (ok) viewModel.notify(BackupMessage.SAVED) }
    val picker = rememberFilePicker(PickType.BACKUP) { result ->
        when (result) {
            is PickResult.Success -> viewModel.inspect(result.file.bytes)
            PickResult.Failed, PickResult.TooLarge -> viewModel.notify(BackupMessage.READ_FAILED)
            PickResult.Cancelled -> Unit
        }
    }
    val fileName = remember {
        val now = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val pad = { n: Int -> n.toString().padStart(2, '0') }
        "DocTrack-backup-${now.date}-${pad(now.hour)}${pad(now.minute)}.zip"
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(s.backupTitle) }, navigationIcon = { BackButton(onBack) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(s.backupOfflineNote, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DetailCard(s.createBackup) {
                Text(s.createBackupDescription, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { viewModel.create(fileName) }, enabled = !state.working, modifier = Modifier.fillMaxWidth()) {
                    if (state.working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else {
                        Icon(Icons.Outlined.FileDownload, null)
                        Spacer(Modifier.width(8.dp))
                        Text(s.createBackup)
                    }
                }
                val bytes = state.backupBytes
                if (bytes != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(state.backupName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { saver(state.backupName, bytes, BACKUP_MIME) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.SaveAlt, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(s.saveToDevice)
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val path = storage.writeTempFile(state.backupName, bytes)
                                    actions.shareFile(path, BACKUP_MIME, state.backupName)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.Share, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(s.shareBackup)
                        }
                    }
                }
            }
            DetailCard(s.restoreBackup) {
                Text(s.restoreBackupDescription, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = picker, enabled = !state.working, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Restore, null)
                    Spacer(Modifier.width(8.dp))
                    Text(s.selectBackupFile)
                }
            }
        }
    }

    state.preview?.let { preview ->
        val created = Instant.fromEpochMilliseconds(preview.manifest.createdAt).toLocalDateTime(TimeZone.currentSystemDefault()).date
        ConfirmDialog(
            title = s.restoreConfirmTitle,
            message = s.backupSummary(preview.manifest.documentCount, preview.manifest.attachmentCount, f.format(created)) +
                "\n\n" + s.restoreConfirmMessage(preview.manifest.documentCount, preview.manifest.attachmentCount, state.currentDocuments),
            confirmLabel = s.restore,
            destructive = true,
            onConfirm = viewModel::confirmRestore,
            onDismiss = viewModel::cancelRestore,
        )
    }
}
