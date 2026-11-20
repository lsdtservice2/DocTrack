package com.logisparktech.doctrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.logisparktech.doctrack.app.theme.CompactLabel
import com.logisparktech.doctrack.core.calendar.CalendarMonth
import com.logisparktech.doctrack.core.localization.dateFormatter
import com.logisparktech.doctrack.core.localization.strings
import com.logisparktech.doctrack.domain.model.CalendarType
import kotlinx.datetime.LocalDate

@Composable
fun monthTitle(month: CalendarMonth): String {
    val s = strings
    val name = if (month.type == CalendarType.BS) s.bsMonths[month.month - 1] else s.adMonths[month.month - 1]
    val suffix = if (month.type == CalendarType.BS) s.bsSuffix else s.adSuffix
    return "$name ${s.num(month.year)} $suffix"
}

@Composable
fun MonthNavigator(
    month: CalendarMonth,
    onMonthChange: (CalendarMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onMonthChange(month.plusYears(-1)) }) {
            Icon(Icons.Filled.KeyboardDoubleArrowLeft, s.previousYear)
        }
        IconButton(onClick = { onMonthChange(month.plusMonths(-1)) }, enabled = !month.isFirstSupported) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, s.previousMonth)
        }
        Text(
            monthTitle(month),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onMonthChange(month.plusMonths(1)) }, enabled = !month.isLastSupported) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, s.nextMonth)
        }
        IconButton(onClick = { onMonthChange(month.plusYears(1)) }) {
            Icon(Icons.Filled.KeyboardDoubleArrowRight, s.nextYear)
        }
    }
}

/**
 * A Sunday-first month grid for either calendar system.
 * [markers] maps dates to indicator dot colors (at most three are drawn).
 */
@Composable
fun MonthGrid(
    month: CalendarMonth,
    today: LocalDate,
    selected: LocalDate?,
    onDateClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    markers: Map<LocalDate, List<Color>> = emptyMap(),
) {
    val s = strings
    val formatter = dateFormatter
    val cells: List<LocalDate?> = List(month.leadingBlanks) { null } + month.days
    val rows = cells.chunked(7)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            s.weekdaysShort.forEachIndexed { index, day ->
                Text(
                    day,
                    style = CompactLabel,
                    color = if (index == 6) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                )
            }
        }
        rows.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                for (i in 0 until 7) {
                    val date = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            DayCell(
                                label = s.num(month.dayNumber(date)),
                                isToday = date == today,
                                isSelected = date == selected,
                                isSaturday = i == 6,
                                dots = markers[date].orEmpty(),
                                description = formatter.format(date),
                                onClick = { onDateClick(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    label: String,
    isToday: Boolean,
    isSelected: Boolean,
    isSaturday: Boolean,
    dots: List<Color>,
    description: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background = when {
        isSelected -> colors.primary
        isToday -> colors.primaryContainer
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected -> colors.onPrimary
        isToday -> colors.onPrimaryContainer
        isSaturday -> colors.error
        else -> colors.onSurface
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(background, CircleShape)
            .then(if (isToday && !isSelected) Modifier.border(1.dp, colors.primary, CircleShape) else Modifier)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
            )
            if (dots.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    dots.distinct().take(3).forEach { dot ->
                        Box(Modifier.size(5.dp).background(if (isSelected) colors.onPrimary else dot, CircleShape))
                    }
                }
            } else {
                Spacer(Modifier.height(5.dp))
            }
        }
    }
}
