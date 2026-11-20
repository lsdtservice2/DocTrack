package com.logisparktech.doctrack.core.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.logisparktech.doctrack.core.calendar.DateFormatter
import com.logisparktech.doctrack.domain.model.AppSettings
import com.logisparktech.doctrack.domain.model.CalendarType

val LocalStrings = staticCompositionLocalOf<Strings> { EnStrings }
val LocalDateFormatter = staticCompositionLocalOf { DateFormatter(EnStrings, CalendarType.AD) }
val LocalAppSettings = staticCompositionLocalOf { AppSettings() }

val strings: Strings
    @Composable @ReadOnlyComposable get() = LocalStrings.current

val dateFormatter: DateFormatter
    @Composable @ReadOnlyComposable get() = LocalDateFormatter.current
