package com.logisparktech.doctrack.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.logisparktech.doctrack.core.calendar.CalendarMonth
import com.logisparktech.doctrack.core.calendar.NepaliDateConverter
import com.logisparktech.doctrack.core.localization.LocalAppSettings
import com.logisparktech.doctrack.core.localization.dateFormatter
import com.logisparktech.doctrack.core.localization.strings
import com.logisparktech.doctrack.domain.model.CalendarType
import kotlinx.datetime.LocalDate

/**
 * Date picker that works in the user's calendar system (AD or BS). The selected value is always
 * returned as a Gregorian [LocalDate], which is how dates are stored.
 */
@Composable
fun AppDatePickerDialog(
    initial: LocalDate?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val s = strings
    val calendarType = LocalAppSettings.current.calendarType
    var selected by remember { mutableStateOf(initial ?: today) }
    var month by remember { mutableStateOf(CalendarMonth.of(calendarType, initial ?: today)) }
    var pickingYear by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.padding(16.dp)) {
                Text(s.selectDate, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(dateFormatter.format(selected, withSuffix = true), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { pickingYear = !pickingYear }) {
                    Text(monthTitle(month))
                }
                if (pickingYear) {
                    YearGrid(
                        type = calendarType,
                        currentYear = month.year,
                        onYearSelected = { year ->
                            month = month.copy(year = year).plusMonths(0)
                            pickingYear = false
                        },
                    )
                } else {
                    MonthNavigator(month = month, onMonthChange = { month = it })
                    MonthGrid(
                        month = month,
                        today = today,
                        selected = selected,
                        onDateClick = { selected = it },
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        selected = today
                        month = CalendarMonth.of(calendarType, today)
                    }) { Text(s.today) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(s.cancel) }
                    TextButton(onClick = { onConfirm(selected) }) { Text(s.ok) }
                }
            }
        }
    }
}

@Composable
private fun YearGrid(type: CalendarType, currentYear: Int, onYearSelected: (Int) -> Unit) {
    val s = strings
    val years = if (type == CalendarType.BS) {
        (NepaliDateConverter.minBsYear..NepaliDateConverter.maxBsYear).toList()
    } else {
        (1950..2100).toList()
    }
    val state = rememberLazyGridState()
    LaunchedEffect(Unit) { state.scrollToItem((years.indexOf(currentYear) - 6).coerceAtLeast(0)) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        state = state,
        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
    ) {
        items(years) { year ->
            FilterChip(
                selected = year == currentYear,
                onClick = { onYearSelected(year) },
                label = { Text(s.num(year)) },
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}

/** Read-only text field that opens [AppDatePickerDialog]. */
@Composable
fun DateField(
    label: String,
    value: LocalDate?,
    today: LocalDate,
    onValueChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value?.let { dateFormatter.format(it, withSuffix = true) } ?: "",
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text(label) },
                isError = isError,
                supportingText = supportingText?.let { { Text(it) } },
                leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null) },
                trailingIcon = if (value != null) {
                    {
                        IconButton(onClick = { onValueChange(null) }) {
                            Icon(Icons.Outlined.Close, contentDescription = strings.clear)
                        }
                    }
                } else null,
                colors = disabledAsEnabledColors(isError),
                modifier = Modifier
                    .weight(1f)
                    .clickable(interactionSource = interaction, indication = null) { open = true },
            )
        }
    }
    if (open) {
        AppDatePickerDialog(
            initial = value,
            today = today,
            onDismiss = { open = false },
            onConfirm = {
                onValueChange(it)
                open = false
            },
        )
    }
}

@Composable
private fun disabledAsEnabledColors(isError: Boolean) = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    disabledTextColor = MaterialTheme.colorScheme.onSurface,
    disabledBorderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
    disabledLabelColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledSupportingTextColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
)
