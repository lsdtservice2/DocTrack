package com.logisparktech.doctrack.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.logisparktech.doctrack.core.localization.Strings
import com.logisparktech.doctrack.core.localization.strings
import com.logisparktech.doctrack.core.platform.BiometricAuthenticator
import com.logisparktech.doctrack.domain.model.AppLanguage
import com.logisparktech.doctrack.domain.model.AppSettings
import com.logisparktech.doctrack.domain.model.CalendarType
import com.logisparktech.doctrack.domain.model.ThemeMode
import com.logisparktech.doctrack.domain.repository.SettingsRepository
import com.logisparktech.doctrack.feature.add_document.CustomReminderDialog
import com.logisparktech.doctrack.ui.components.ClickableRow
import com.logisparktech.doctrack.ui.components.SectionHeader
import com.logisparktech.doctrack.ui.components.SettingsCard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.update(transform) }
    }
}

private enum class SettingsDialog { LANGUAGE, CALENDAR, THEME, THRESHOLD, REMINDERS, HOUR }

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    snackbar: SnackbarHostState,
    onOpenCategories: () -> Unit,
    onOpenBackup: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val current = settings ?: return
    val s = strings
    val biometric = koinInject<BiometricAuthenticator>()
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { Text(s.settings, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp)) }

        item { SectionHeader(s.general) }
        item {
            SettingsCard {
                ClickableRow(s.languageLabel, if (current.language == AppLanguage.EN) s.english else s.nepali, Icons.Outlined.Language, { dialog = SettingsDialog.LANGUAGE }, chevron())
                Divider()
                ClickableRow(s.calendarSystem, calendarLabel(s, current.calendarType), Icons.Outlined.CalendarMonth, { dialog = SettingsDialog.CALENDAR }, chevron())
            }
        }

        item { SectionHeader(s.appearance) }
        item {
            SettingsCard {
                ClickableRow(s.theme, themeLabel(s, current.theme), Icons.Outlined.Palette, { dialog = SettingsDialog.THEME }, chevron())
            }
        }

        item { SectionHeader(s.remindersSection) }
        item {
            SettingsCard {
                ClickableRow(
                    s.expiryWarningThreshold, s.expiryWarningDescription(current.expiryWarningDays),
                    Icons.Outlined.Timelapse, { dialog = SettingsDialog.THRESHOLD }, chevron(),
                )
                Divider()
                ClickableRow(
                    s.defaultReminders,
                    current.defaultReminderDays.joinToString(", ") { s.daysBefore(it) }.ifEmpty { s.notSet },
                    Icons.Outlined.NotificationsActive, { dialog = SettingsDialog.REMINDERS }, chevron(),
                )
                Divider()
                ClickableRow(s.reminderTime, s.formatHour(current.reminderHour), Icons.Outlined.Schedule, { dialog = SettingsDialog.HOUR }, chevron())
            }
        }

        item { SectionHeader(s.security) }
        item {
            SettingsCard {
                val available = remember { biometric.isAvailable() }
                ClickableRow(
                    title = s.biometricLock,
                    subtitle = if (available) s.biometricLockDescription else s.biometricUnavailable,
                    icon = Icons.Outlined.Fingerprint,
                    onClick = {},
                ) {
                    Switch(
                        checked = current.biometricLockEnabled,
                        enabled = available,
                        onCheckedChange = { enable ->
                            scope.launch {
                                // Require a successful authentication before changing the lock.
                                if (biometric.authenticate(s.biometricLock, s.unlockReason, s.cancel)) {
                                    viewModel.update { it.copy(biometricLockEnabled = enable) }
                                } else {
                                    snackbar.showSnackbar(s.authenticationFailed)
                                }
                            }
                        },
                    )
                }
            }
        }

        item { SectionHeader(s.data) }
        item {
            SettingsCard {
                ClickableRow(s.manageCategories, null, Icons.Outlined.Category, onOpenCategories, chevron())
                Divider()
                ClickableRow(s.backupAndRestore, s.backupOfflineNote, Icons.Outlined.Backup, onOpenBackup, chevron())
            }
        }

        item { SectionHeader(s.about) }
        item {
            SettingsCard {
                ClickableRow("${s.appName} · ${s.version} ${s.num(1)}.${s.num(0)}", s.aboutDescription, Icons.Outlined.Info, {})
            }
        }
    }

    when (dialog) {
        SettingsDialog.LANGUAGE -> ChoiceDialog(
            s.languageLabel, listOf(AppLanguage.EN to s.english, AppLanguage.NE to s.nepali), current.language,
            onSelect = { lang -> viewModel.update { it.copy(language = lang) }; dialog = null },
            onDismiss = { dialog = null },
        )
        SettingsDialog.CALENDAR -> ChoiceDialog(
            s.calendarSystem, CalendarType.entries.map { it to calendarLabel(s, it) }, current.calendarType,
            onSelect = { type -> viewModel.update { it.copy(calendarType = type) }; dialog = null },
            onDismiss = { dialog = null },
        )
        SettingsDialog.THEME -> ChoiceDialog(
            s.theme, ThemeMode.entries.map { it to themeLabel(s, it) }, current.theme,
            onSelect = { mode -> viewModel.update { it.copy(theme = mode) }; dialog = null },
            onDismiss = { dialog = null },
        )
        SettingsDialog.THRESHOLD -> ChoiceDialog(
            s.expiryWarningThreshold,
            (listOf(7, 14, 15, 30, 45, 60, 90) + current.expiryWarningDays).distinct().sorted().map { it to s.daysCount(it) },
            current.expiryWarningDays,
            onSelect = { days -> viewModel.update { it.copy(expiryWarningDays = days) }; dialog = null },
            onDismiss = { dialog = null },
        )
        SettingsDialog.HOUR -> ChoiceDialog(
            s.reminderTime, (0..23).map { it to s.formatHour(it) }, current.reminderHour,
            onSelect = { hour -> viewModel.update { it.copy(reminderHour = hour) }; dialog = null },
            onDismiss = { dialog = null },
        )
        SettingsDialog.REMINDERS -> DefaultRemindersDialog(
            initial = current.defaultReminderDays,
            onSave = { days -> viewModel.update { it.copy(defaultReminderDays = days) }; dialog = null },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

private fun calendarLabel(s: Strings, type: CalendarType) = if (type == CalendarType.AD) s.gregorianAd else s.bikramSambatBs

private fun themeLabel(s: Strings, mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> s.themeSystem
    ThemeMode.LIGHT -> s.themeLight
    ThemeMode.DARK -> s.themeDark
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)

private fun chevron(): @Composable () -> Unit = {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(options) { (value, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = value == selected, role = Role.RadioButton, onClick = { onSelect(value) })
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(strings.close) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DefaultRemindersDialog(initial: List<Int>, onSave: (List<Int>) -> Unit, onDismiss: () -> Unit) {
    val s = strings
    var selected by remember { mutableStateOf(initial.toSet()) }
    var options by remember { mutableStateOf((AppSettings.DEFAULT_REMINDER_DAYS + initial).distinct().sortedDescending()) }
    var custom by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.defaultReminders) },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { days ->
                        FilterChip(
                            selected = days in selected,
                            onClick = { selected = if (days in selected) selected - days else selected + days },
                            label = { Text(s.daysBefore(days)) },
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = { custom = true },
                        label = { Text(s.addCustomReminder) },
                        leadingIcon = { Icon(Icons.Outlined.Add, null) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selected.sortedDescending()) }) { Text(s.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
    if (custom) {
        CustomReminderDialog(
            onDismiss = { custom = false },
            onConfirm = { days ->
                options = (options + days).distinct().sortedDescending()
                selected = selected + days
                custom = false
            },
        )
    }
}

