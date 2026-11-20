package com.logisparktech.doctrack.feature.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.logisparktech.doctrack.core.localization.dateFormatter
import com.logisparktech.doctrack.core.localization.strings
import com.logisparktech.doctrack.core.notification.NotificationScheduler
import com.logisparktech.doctrack.core.notification.PlannedReminder
import com.logisparktech.doctrack.core.notification.ReminderSyncService
import com.logisparktech.doctrack.core.platform.PlatformActions
import com.logisparktech.doctrack.core.platform.rememberNotificationPermissionRequester
import com.logisparktech.doctrack.ui.components.EmptyState
import com.logisparktech.doctrack.ui.components.SectionHeader
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

class RemindersViewModel(syncService: ReminderSyncService) : ViewModel() {
    val upcoming: StateFlow<List<PlannedReminder>?> =
        syncService.upcoming().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun RemindersScreen(
    contentPadding: PaddingValues,
    onOpenDocument: (Long) -> Unit,
    viewModel: RemindersViewModel = koinViewModel(),
) {
    val upcoming by viewModel.upcoming.collectAsStateWithLifecycle()
    val s = strings
    val f = dateFormatter

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text(s.notifications, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
        }
        item { NotificationPermissionBanner() }
        item {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(s.notificationsDeliveryNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { SectionHeader(s.upcomingReminders) }
        val list = upcoming
        when {
            list == null -> Unit
            list.isEmpty() -> item { EmptyState(Icons.Outlined.NotificationsNone, s.noUpcomingReminders) }
            else -> items(list, key = { it.id }) { reminder ->
                Card(
                    onClick = { onOpenDocument(reminder.documentId) },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(reminder.documentName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                s.reminderScheduledFor(f.format(reminder.triggerDate)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(s.daysBefore(reminder.daysBefore), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/** Shows only when notification permission is missing; re-checks every time the screen resumes. */
@Composable
fun NotificationPermissionBanner() {
    val s = strings
    val scheduler = koinInject<NotificationScheduler>()
    val actions = koinInject<PlatformActions>()
    val sync = koinInject<ReminderSyncService>()
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(true) }
    var denied by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        val job = scope.launch { granted = scheduler.hasPermission() }
        onPauseOrDispose { job.cancel() }
    }
    val request = rememberNotificationPermissionRequester { ok ->
        granted = ok
        denied = !ok
        if (ok) scope.launch { runCatching { sync.resync() } }
    }
    if (granted) return

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.NotificationsOff, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(10.dp))
                Text(s.notificationsDisabledTitle, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (denied) s.notificationsPermissionDenied else s.notificationsDisabledMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(10.dp))
            Row {
                Button(onClick = request) { Text(s.enableNotifications) }
                if (denied) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = actions::openAppSettings) { Text(s.settings) }
                }
            }
        }
    }
}
