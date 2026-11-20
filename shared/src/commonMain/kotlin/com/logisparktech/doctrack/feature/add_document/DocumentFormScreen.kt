package com.logisparktech.doctrack.feature.add_document

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.logisparktech.doctrack.core.common.DateProvider
import com.logisparktech.doctrack.core.localization.strings
import com.logisparktech.doctrack.core.platform.PickResult
import com.logisparktech.doctrack.core.platform.PickType
import com.logisparktech.doctrack.core.platform.rememberFilePicker
import com.logisparktech.doctrack.core.platform.rememberNotificationPermissionRequester
import com.logisparktech.doctrack.domain.model.AppSettings
import com.logisparktech.doctrack.domain.model.AttachmentType
import com.logisparktech.doctrack.domain.model.Category
import com.logisparktech.doctrack.domain.usecase.DocumentValidationError
import com.logisparktech.doctrack.ui.components.AttachmentImage
import com.logisparktech.doctrack.ui.components.BackButton
import com.logisparktech.doctrack.ui.components.BytesImage
import com.logisparktech.doctrack.ui.components.CategoryBadge
import com.logisparktech.doctrack.ui.components.ConfirmDialog
import com.logisparktech.doctrack.ui.components.DateField
import com.logisparktech.doctrack.ui.components.EmptyState
import com.logisparktech.doctrack.ui.components.categoryLabel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun DocumentFormScreen(
    viewModel: DocumentFormViewModel,
    onClose: () -> Unit,
    onSaved: (Long, Boolean) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = strings
    val today = koinInject<DateProvider>().today()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmDiscard by remember { mutableStateOf(false) }
    var pickTarget by remember { mutableStateOf(AttachmentType.FRONT_IMAGE) }

    fun handlePick(result: PickResult, type: AttachmentType) {
        when (result) {
            is PickResult.Success -> viewModel.addPicked(result.file, type)
            PickResult.TooLarge -> scope.launch { snackbar.showSnackbar(s.errorFileTooLarge) }
            PickResult.Failed -> scope.launch { snackbar.showSnackbar(s.errorFileRead) }
            PickResult.Cancelled -> Unit
        }
    }

    val pickImage = rememberFilePicker(PickType.IMAGE) { handlePick(it, pickTarget) }
    val pickPdf = rememberFilePicker(PickType.PDF) { handlePick(it, AttachmentType.PDF) }
    val requestNotifications = rememberNotificationPermissionRequester { viewModel.save() }

    LaunchedEffect(state.savedId) {
        state.savedId?.let { onSaved(it, state.isEdit) }
    }
    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) snackbar.showSnackbar(s.errorSaveFailed)
    }

    val close = { if (state.dirty) confirmDiscard = true else onClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEdit) s.editDocumentTitle else s.addDocumentTitle) },
                navigationIcon = { BackButton(close) },
                actions = {
                    TextButton(
                        enabled = !state.saving && !state.loading && !state.notFound,
                        onClick = {
                            if (state.remindersEnabled && state.expiryDate != null && state.reminderDays.isNotEmpty()) {
                                requestNotifications()
                            } else viewModel.save()
                        },
                    ) { Text(s.save) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.notFound -> EmptyState(Icons.Outlined.Close, s.documentNotFound, modifier = Modifier.padding(padding))
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FormSection(s.basicInformation) {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = viewModel::setName,
                        label = { Text(s.documentName) },
                        isError = DocumentValidationError.NAME_REQUIRED in state.errors,
                        supportingText = if (DocumentValidationError.NAME_REQUIRED in state.errors) {
                            { Text(s.errorNameRequired) }
                        } else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CategoryField(
                        categories = state.categories,
                        selectedId = state.categoryId,
                        isError = DocumentValidationError.CATEGORY_REQUIRED in state.errors,
                        onSelect = viewModel::setCategory,
                    )
                    OutlinedTextField(
                        value = state.documentNumber,
                        onValueChange = viewModel::setNumber,
                        label = { Text("${s.documentNumber} (${s.optional})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.issuingAuthority,
                        onValueChange = viewModel::setAuthority,
                        label = { Text("${s.issuingAuthority} (${s.optional})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                FormSection(s.dates) {
                    DateField(s.issueDate, state.issueDate, today, viewModel::setIssueDate, Modifier.fillMaxWidth())
                    DateField(
                        s.expiryDate, state.expiryDate, today, viewModel::setExpiryDate, Modifier.fillMaxWidth(),
                        isError = DocumentValidationError.EXPIRY_BEFORE_ISSUE in state.errors,
                        supportingText = if (DocumentValidationError.EXPIRY_BEFORE_ISSUE in state.errors) s.errorExpiryBeforeIssue else null,
                    )
                    DateField(s.renewalDate, state.renewalDate, today, viewModel::setRenewalDate, Modifier.fillMaxWidth())
                    SwitchRow(s.markRenewalInProgress, state.renewalInProgress, viewModel::setRenewalInProgress)
                }

                ReminderSection(state, viewModel)

                FormSection(s.attachments) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ImageSlot(
                            title = s.frontImage, state = state, type = AttachmentType.FRONT_IMAGE,
                            onPick = { pickTarget = AttachmentType.FRONT_IMAGE; pickImage() },
                            onRemove = { viewModel.removeImageSlot(AttachmentType.FRONT_IMAGE) },
                            modifier = Modifier.weight(1f),
                        )
                        ImageSlot(
                            title = s.backImage, state = state, type = AttachmentType.BACK_IMAGE,
                            onPick = { pickTarget = AttachmentType.BACK_IMAGE; pickImage() },
                            onRemove = { viewModel.removeImageSlot(AttachmentType.BACK_IMAGE) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(s.pdfAttachments + " / " + s.otherImages, style = MaterialTheme.typography.labelLarge)
                    state.visibleAttachments.filter { it.type == AttachmentType.PDF || it.type == AttachmentType.IMAGE }.forEach { a ->
                        FileRow(a.fileName, a.type == AttachmentType.PDF) { viewModel.removeExisting(a.id) }
                    }
                    state.pending.filter { it.type == AttachmentType.PDF || it.type == AttachmentType.IMAGE }.forEach { p ->
                        FileRow(p.file.name, p.type == AttachmentType.PDF) { viewModel.removePending(p.key) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = pickPdf) {
                            Icon(Icons.Outlined.PictureAsPdf, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(s.addPdf)
                        }
                        OutlinedButton(onClick = { pickTarget = AttachmentType.IMAGE; pickImage() }) {
                            Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(s.addImage)
                        }
                    }
                }

                FormSection(s.notes) {
                    OutlinedTextField(
                        value = state.description,
                        onValueChange = viewModel::setDescription,
                        label = { Text(s.description) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.notes,
                        onValueChange = viewModel::setNotes,
                        label = { Text(s.notes) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Button(
                    onClick = {
                        if (state.remindersEnabled && state.expiryDate != null && state.reminderDays.isNotEmpty()) {
                            requestNotifications()
                        } else viewModel.save()
                    },
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    if (state.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(s.save)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = s.discardChangesTitle,
            message = s.discardChangesMessage,
            confirmLabel = s.discard,
            destructive = true,
            onConfirm = { confirmDiscard = false; onClose() },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun FormSection(title: String, content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderSection(state: DocumentFormState, viewModel: DocumentFormViewModel) {
    val s = strings
    var showCustom by remember { mutableStateOf(false) }
    FormSection(s.reminderSettings) {
        if (state.expiryDate == null) {
            Text(s.noReminderWithoutExpiry, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SwitchRow(s.remindersEnabled, state.remindersEnabled, viewModel::setRemindersEnabled)
        if (state.remindersEnabled) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.reminderOptions.forEach { days ->
                    FilterChip(
                        selected = days in state.reminderDays,
                        onClick = { viewModel.toggleReminder(days) },
                        label = { Text(s.daysBefore(days)) },
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = { showCustom = true },
                    label = { Text(s.addCustomReminder) },
                    leadingIcon = { Icon(Icons.Outlined.Add, null, Modifier.size(16.dp)) },
                )
            }
        }
    }
    if (showCustom) {
        CustomReminderDialog(
            onDismiss = { showCustom = false },
            onConfirm = { viewModel.addCustomReminder(it); showCustom = false },
        )
    }
}

@Composable
fun CustomReminderDialog(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val s = strings
    var text by remember { mutableStateOf("") }
    val value = text.toIntOrNull()
    val valid = value != null && value in 0..AppSettings.MAX_REMINDER_DAYS
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.addCustomReminder) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() }.take(3) },
                label = { Text(s.customReminderDays) },
                isError = text.isNotEmpty() && !valid,
                supportingText = if (text.isNotEmpty() && !valid) {
                    { Text(s.errorReminderDaysRange(AppSettings.MAX_REMINDER_DAYS)) }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onConfirm(value!!) }) { Text(s.add) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}

@Composable
private fun CategoryField(categories: List<Category>, selectedId: Long, isError: Boolean, onSelect: (Long) -> Unit) {
    val s = strings
    var open by remember { mutableStateOf(false) }
    val selected = categories.firstOrNull { it.id == selectedId }
    Surface(
        onClick = { open = true },
        shape = MaterialTheme.shapes.extraSmall,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected != null) CategoryBadge(selected, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(selected?.let { categoryLabel(it) } ?: s.errorCategoryRequired, style = MaterialTheme.typography.bodyLarge)
            }
            Icon(Icons.Outlined.ArrowDropDown, null)
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(s.category) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(categories, key = { it.id }) { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(c.id); open = false }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryBadge(c, size = 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(categoryLabel(c), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (c.id == selectedId) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(s.close) } },
        )
    }
}

@Composable
private fun ImageSlot(
    title: String,
    state: DocumentFormState,
    type: AttachmentType,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier,
) {
    val s = strings
    val (pending, existing) = state.imageFor(type)
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Surface(
            onClick = onPick,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().aspectRatio(1.4f),
        ) {
            when {
                pending != null -> BytesImage(pending.file.bytes, title, Modifier.fillMaxSize())
                existing != null -> AttachmentImage(existing.filePath, title, Modifier.fillMaxSize())
                else -> Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.primary)
                        Text(s.addImage, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if (pending != null || existing != null) {
            Row {
                TextButton(onClick = onPick) { Text(s.replaceImage) }
                TextButton(onClick = onRemove) { Text(s.removeImage, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun FileRow(name: String, isPdf: Boolean, onRemove: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isPdf) Icons.Outlined.PictureAsPdf else Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, strings.removeImage) }
        }
    }
}
