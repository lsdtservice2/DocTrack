package com.logisparktech.doctrack.feature.document_details

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.logisparktech.doctrack.app.theme.LocalStatusColors
import com.logisparktech.doctrack.core.localization.dateFormatter
import com.logisparktech.doctrack.core.localization.strings
import com.logisparktech.doctrack.domain.model.Attachment
import com.logisparktech.doctrack.domain.model.AttachmentType
import com.logisparktech.doctrack.domain.model.DocumentWithExpiry
import com.logisparktech.doctrack.domain.model.RenewalRecord
import com.logisparktech.doctrack.feature.add_document.CustomReminderDialog
import com.logisparktech.doctrack.ui.components.AttachmentImage
import com.logisparktech.doctrack.ui.components.BackButton
import com.logisparktech.doctrack.ui.components.CategoryBadge
import com.logisparktech.doctrack.ui.components.ConfirmDialog
import com.logisparktech.doctrack.ui.components.EmptyState
import com.logisparktech.doctrack.ui.components.InfoRow
import com.logisparktech.doctrack.ui.components.StatusChip
import com.logisparktech.doctrack.ui.components.categoryLabel
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun DocumentDetailScreen(
    viewModel: DocumentDetailViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onRenew: (Long) -> Unit,
    onOpenAttachment: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val s = strings
    var confirmDelete by remember { mutableStateOf(false) }
    var addReminder by remember { mutableStateOf(false) }

    LaunchedEffect(deleted) { if (deleted) onBack() }

    val item = state.item
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.documentDetails) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (item != null) {
                        IconButton(onClick = { onEdit(item.document.id) }) { Icon(Icons.Outlined.Edit, s.edit) }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, s.delete, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            item == null -> EmptyState(Icons.Outlined.SearchOff, s.documentNotFound, modifier = Modifier.padding(padding))
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                HeaderCard(item)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { onRenew(item.document.id) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Icon(Icons.Outlined.Autorenew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(s.renew)
                    }
                    OutlinedButton(
                        onClick = { addReminder = true },
                        enabled = item.document.expiryDate != null,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Outlined.NotificationAdd, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(s.addReminder, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                InfoCard(item, onRenewalInProgress = viewModel::setRenewalInProgress)
                AttachmentsCard(state.attachments, onOpenAttachment)
                RemindersCard(item, state.reminders.map { it.daysBefore }, viewModel::removeReminder)
                if (item.document.description.isNotBlank() || item.document.notes.isNotBlank()) {
                    DetailCard(s.notes) {
                        if (item.document.description.isNotBlank()) {
                            Text(s.description, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(item.document.description, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                        }
                        if (item.document.notes.isNotBlank()) {
                            Text(s.notes, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(item.document.notes, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                HistoryCard(state.history)
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = s.deleteDocumentTitle,
            message = s.deleteDocumentMessage,
            confirmLabel = s.delete,
            destructive = true,
            onConfirm = { confirmDelete = false; viewModel.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
    if (addReminder) {
        CustomReminderDialog(
            onDismiss = { addReminder = false },
            onConfirm = { viewModel.addReminder(it); addReminder = false },
        )
    }
}

@Composable
private fun HeaderCard(item: DocumentWithExpiry) {
    val s = strings
    val doc = item.document
    val color = LocalStatusColors.current.forStatus(item.expiry.status)
    DetailCard(null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(doc.category, size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(doc.name, style = MaterialTheme.typography.titleLarge)
                Text(categoryLabel(doc.category), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.remainingDays, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val days = item.expiry.daysRemaining
                Text(
                    if (days != null && days >= 0) s.daysCount(days) else dateFormatter.expiryText(item.expiry),
                    style = MaterialTheme.typography.headlineSmall,
                    color = color,
                    fontWeight = FontWeight.Bold,
                )
                if (days != null && days >= 0) {
                    Text(dateFormatter.expiryText(item.expiry), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            StatusChip(item.expiry.status)
        }
    }
}

@Composable
private fun InfoCard(item: DocumentWithExpiry, onRenewalInProgress: (Boolean) -> Unit) {
    val s = strings
    val f = dateFormatter
    val doc = item.document
    DetailCard(s.basicInformation) {
        InfoRow(s.documentNumber, doc.documentNumber ?: s.notSet)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        InfoRow(s.category, categoryLabel(doc.category))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        InfoRow(s.issuingAuthority, doc.issuingAuthority ?: s.notSet)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        InfoRow(s.issueDate, f.formatOrNull(doc.issueDate))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        InfoRow(s.expiryDate, f.formatOrNull(doc.expiryDate))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        InfoRow(s.renewalDate, f.formatOrNull(doc.renewalDate))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        InfoRow(s.status, f.statusLabel(item.expiry.status))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        InfoRow(s.createdOn, f.format(millisToDate(doc.createdAt)))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        InfoRow(s.updatedOn, f.format(millisToDate(doc.updatedAt)))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(s.markRenewalInProgress, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = doc.renewalInProgress, onCheckedChange = onRenewalInProgress)
        }
    }
}

private fun millisToDate(millis: Long) =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date

@Composable
private fun AttachmentsCard(attachments: List<Attachment>, onOpen: (Long) -> Unit) {
    val s = strings
    DetailCard(s.attachments) {
        if (attachments.isEmpty()) {
            Text(s.noAttachments, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@DetailCard
        }
        val front = attachments.lastOrNull { it.type == AttachmentType.FRONT_IMAGE }
        val back = attachments.lastOrNull { it.type == AttachmentType.BACK_IMAGE }
        if (front != null || back != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(front to s.frontImage, back to s.backImage).forEach { (att, label) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        if (att != null) {
                            Surface(onClick = { onOpen(att.id) }, shape = MaterialTheme.shapes.medium) {
                                AttachmentImage(att.filePath, label, Modifier.fillMaxWidth().aspectRatio(1.4f))
                            }
                        } else {
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(1.4f)
                                    .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium),
                                contentAlignment = Alignment.Center,
                            ) { Text(s.notSet, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        attachments.filter { it.type == AttachmentType.PDF || it.type == AttachmentType.IMAGE }.forEach { att ->
            Surface(
                onClick = { onOpen(att.id) },
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (att.type == AttachmentType.PDF) Icons.Outlined.PictureAsPdf else Icons.Outlined.Image,
                        null, tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(att.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(s.viewAttachment, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemindersCard(item: DocumentWithExpiry, days: List<Int>, onRemove: (Int) -> Unit) {
    val s = strings
    DetailCard(s.reminders) {
        when {
            item.document.expiryDate == null ->
                Text(s.noReminderWithoutExpiry, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            days.isEmpty() ->
                Text(s.noReminders, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                days.sortedDescending().forEach { d ->
                    InputChip(
                        selected = false,
                        onClick = { onRemove(d) },
                        label = { Text(s.daysBefore(d)) },
                        leadingIcon = { Icon(Icons.Outlined.NotificationsActive, null, Modifier.size(16.dp)) },
                        trailingIcon = { Icon(Icons.Outlined.Close, s.delete, Modifier.size(16.dp)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(history: List<RenewalRecord>) {
    val s = strings
    val f = dateFormatter
    DetailCard(s.renewalHistory) {
        if (history.isEmpty()) {
            Text(s.noRenewalHistory, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@DetailCard
        }
        history.forEachIndexed { index, record ->
            Row {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    if (index != history.lastIndex) {
                        Box(Modifier.width(2.dp).height(84.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).padding(bottom = 12.dp)) {
                    Text(s.renewedOn(f.format(record.renewalDate)), style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${s.previousDates}: ${f.formatOrNull(record.previousIssueDate)} → ${f.formatOrNull(record.previousExpiryDate)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${s.newDates}: ${f.formatOrNull(record.newIssueDate)} → ${f.formatOrNull(record.newExpiryDate)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (record.notes.isNotBlank()) {
                        Text(record.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun DetailCard(title: String?, content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
            }
            content()
        }
    }
}
