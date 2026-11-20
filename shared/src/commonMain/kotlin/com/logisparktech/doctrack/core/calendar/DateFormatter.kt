package com.logisparktech.doctrack.core.calendar

import com.logisparktech.doctrack.core.localization.Strings
import com.logisparktech.doctrack.domain.model.CalendarType
import com.logisparktech.doctrack.domain.model.DocumentStatus
import com.logisparktech.doctrack.domain.model.ExpiryInfo
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/**
 * Formats internally stored Gregorian dates for display in the chosen calendar system and language.
 * Language and calendar are independent (EN+BS, NE+AD, ...).
 */
class DateFormatter(private val strings: Strings, val calendarType: CalendarType) {

    /** e.g. "14 April 2025" / "१ बैशाख २०८२". Falls back to AD if outside the BS table range. */
    fun format(date: LocalDate, withSuffix: Boolean = false): String {
        if (calendarType == CalendarType.BS) {
            val bs = NepaliDateConverter.adToBs(date)
            if (bs != null) {
                val text = "${strings.num(bs.day)} ${strings.bsMonths[bs.month - 1]} ${strings.num(bs.year)}"
                return if (withSuffix) "$text ${strings.bsSuffix}" else text
            }
            return "${formatAd(date)} ${strings.adSuffix}"
        }
        val text = formatAd(date)
        return if (withSuffix) "$text ${strings.adSuffix}" else text
    }

    fun formatShort(date: LocalDate): String {
        if (calendarType == CalendarType.BS) {
            NepaliDateConverter.adToBs(date)?.let { bs ->
                return "${strings.num(bs.day)} ${strings.bsMonths[bs.month - 1]}"
            }
        }
        return "${strings.num(date.day)} ${strings.adMonths[date.month.number - 1].take(3)}"
    }

    fun formatOrNull(date: LocalDate?): String = date?.let { format(it) } ?: strings.notSet

    private fun formatAd(date: LocalDate) =
        "${strings.num(date.day)} ${strings.adMonths[date.month.number - 1]} ${strings.num(date.year)}"

    /** Human-readable relative expiry text, computed from the date rather than stored. */
    fun expiryText(info: ExpiryInfo): String {
        val days = info.daysRemaining ?: return strings.noExpiryDate
        return when {
            days > 1 -> strings.expiresInDays(days)
            days == 1 -> strings.expiresTomorrow
            days == 0 -> strings.expiresToday
            days == -1 -> strings.expiredYesterday
            else -> strings.expiredDaysAgo(-days)
        }
    }

    fun statusLabel(status: DocumentStatus): String = when (status) {
        DocumentStatus.ACTIVE -> strings.statusActive
        DocumentStatus.EXPIRING_SOON -> strings.statusExpiringSoon
        DocumentStatus.EXPIRED -> strings.statusExpired
        DocumentStatus.RENEWAL_IN_PROGRESS -> strings.statusRenewalInProgress
        DocumentStatus.NO_EXPIRY -> strings.statusNoExpiry
    }
}
