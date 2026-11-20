package com.logisparktech.doctrack.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.logisparktech.doctrack.app.theme.CompactLabel
import com.logisparktech.doctrack.app.theme.LocalStatusColors
import com.logisparktech.doctrack.app.theme.StatusColors
import com.logisparktech.doctrack.core.localization.dateFormatter
import com.logisparktech.doctrack.core.localization.strings
import com.logisparktech.doctrack.ui.components.CategoryBadge
import com.logisparktech.doctrack.ui.components.EmptyState
import com.logisparktech.doctrack.ui.components.InlineEmpty
import com.logisparktech.doctrack.ui.components.MonthGrid
import com.logisparktech.doctrack.ui.components.MonthNavigator
import com.logisparktech.doctrack.ui.components.SectionHeader
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel

fun EventUrgency.color(colors: StatusColors): Color = when (this) {
    EventUrgency.EXPIRED -> colors.expired
    EventUrgency.URGENT -> colors.expiringSoon
    EventUrgency.UPCOMING -> colors.active
    EventUrgency.RENEWAL -> colors.renewal
}

@Composable
fun CalendarScreen(
    contentPadding: PaddingValues,
    onOpenDocument: (Long) -> Unit,
    onAddForDate: (LocalDate) -> Unit,
    viewModel: CalendarViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = strings
    val colors = LocalStatusColors.current
    val month = state.month ?: return
    val today = state.today ?: return

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.calendar, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.goToToday(today) }) { Icon(Icons.Outlined.Today, s.today) }
            }
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                CalendarViewMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.mode == mode,
                        onClick = { viewModel.setMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, CalendarViewMode.entries.size),
                    ) { Text(if (mode == CalendarViewMode.MONTH) s.monthView else s.agendaView) }
                }
            }
        }

        if (state.mode == CalendarViewMode.MONTH) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(Modifier.padding(8.dp)) {
                        MonthNavigator(month, viewModel::showMonth)
                        MonthGrid(
                            month = month,
                            today = today,
                            selected = state.selected,
                            onDateClick = viewModel::select,
                            markers = state.eventsByDate.mapValues { (_, events) -> events.map { it.urgency.color(colors) } },
                        )
                        Legend(colors)
                    }
                }
            }
            val selected = state.selected ?: today
            val dayEvents = state.eventsByDate[selected].orEmpty()
            item {
                SectionHeader(
                    dateFormatter.format(selected, withSuffix = true),
                    actionLabel = s.add,
                    onAction = { onAddForDate(selected) },
                )
            }
            if (dayEvents.isEmpty()) {
                item { InlineEmpty(s.noEventsOnDate) }
            } else {
                items(dayEvents, key = { "${it.kind}_${it.item.document.id}" }) { EventRow(it, colors, onOpenDocument) }
            }
        } else {
            if (state.upcoming.isEmpty()) {
                item { EmptyState(Icons.Outlined.EventAvailable, s.noUpcomingEvents) }
            } else {
                state.upcoming.groupBy { it.date }.forEach { (date, events) ->
                    item(key = "h_$date") { SectionHeader(dateFormatter.format(date, withSuffix = true)) }
                    items(events, key = { "${date}_${it.kind}_${it.item.document.id}" }) { EventRow(it, colors, onOpenDocument) }
                }
            }
        }
    }
}

@Composable
private fun Legend(colors: StatusColors) {
    val s = strings
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        listOf(
            EventUrgency.EXPIRED to s.legendExpired,
            EventUrgency.URGENT to s.legendUrgent,
            EventUrgency.UPCOMING to s.legendUpcoming,
            EventUrgency.RENEWAL to s.legendRenewal,
        ).forEach { (urgency, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(urgency.color(colors), CircleShape))
                Spacer(Modifier.width(4.dp))
                Text(label, style = CompactLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EventRow(event: CalendarEvent, colors: StatusColors, onOpen: (Long) -> Unit) {
    val s = strings
    val color = event.urgency.color(colors)
    Card(
        onClick = { onOpen(event.item.document.id) },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).size(width = 4.dp, height = 40.dp).background(color, MaterialTheme.shapes.small))
            Spacer(Modifier.width(12.dp))
            CategoryBadge(event.item.document.category, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(event.item.document.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (event.kind == EventKind.EXPIRY) "${s.expiryEvent} · ${dateFormatter.expiryText(event.item.expiry)}" else s.renewalEvent,
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                )
            }
        }
    }
}

