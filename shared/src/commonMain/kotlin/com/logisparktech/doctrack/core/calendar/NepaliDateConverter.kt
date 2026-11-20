package com.logisparktech.doctrack.core.calendar

import kotlinx.datetime.LocalDate

/** A date in the Bikram Sambat calendar. [month] is 1..12 (1 = Baisakh). */
data class BsDate(val year: Int, val month: Int, val day: Int) : Comparable<BsDate> {
    override fun compareTo(other: BsDate): Int =
        compareValuesBy(this, other, BsDate::year, BsDate::month, BsDate::day)
}

/**
 * Exact AD <-> BS conversion using the published month-length table (no approximate offsets).
 * Supported range: BS 1970-01-01 (AD 1913-04-13) to the last day of BS 2090.
 */
object NepaliDateConverter {
    /** AD date corresponding to BS FIRST_YEAR-01-01. */
    private val EPOCH_AD = LocalDate(1913, 4, 13)
    private val epochDays: Long = EPOCH_AD.toEpochDays()

    /** Cumulative day offset of each BS year's first day from the epoch. */
    private val yearStartOffsets: LongArray = LongArray(BsCalendarData.monthLengths.size + 1).also { arr ->
        for (i in BsCalendarData.monthLengths.indices) {
            arr[i + 1] = arr[i] + BsCalendarData.monthLengths[i].sum()
        }
    }

    val minBsYear: Int get() = BsCalendarData.FIRST_YEAR
    val maxBsYear: Int get() = BsCalendarData.LAST_YEAR
    val minAdDate: LocalDate get() = EPOCH_AD
    val maxAdDate: LocalDate get() = LocalDate.fromEpochDays(epochDays + yearStartOffsets.last() - 1)

    fun isSupported(date: LocalDate): Boolean = date in minAdDate..maxAdDate

    fun isSupported(year: Int): Boolean = year in minBsYear..maxBsYear

    /** Number of days in the given BS month, or null if the year is outside the supported range. */
    fun daysInMonth(year: Int, month: Int): Int? {
        require(month in 1..12) { "Invalid BS month $month" }
        if (!isSupported(year)) return null
        return BsCalendarData.monthLengths[year - BsCalendarData.FIRST_YEAR][month - 1]
    }

    /** Converts an AD date to BS. Returns null when outside the supported range. */
    fun adToBs(date: LocalDate): BsDate? {
        if (!isSupported(date)) return null
        var remaining = date.toEpochDays() - epochDays
        // Binary search the year.
        var lo = 0
        var hi = BsCalendarData.monthLengths.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (yearStartOffsets[mid] <= remaining) lo = mid else hi = mid - 1
        }
        remaining -= yearStartOffsets[lo]
        val months = BsCalendarData.monthLengths[lo]
        var month = 0
        while (remaining >= months[month]) {
            remaining -= months[month]
            month++
        }
        return BsDate(BsCalendarData.FIRST_YEAR + lo, month + 1, remaining.toInt() + 1)
    }

    /** Converts a BS date to AD. Returns null for invalid or unsupported dates. */
    fun bsToAd(date: BsDate): LocalDate? {
        if (!isValid(date)) return null
        val yearIndex = date.year - BsCalendarData.FIRST_YEAR
        var offset = yearStartOffsets[yearIndex]
        val months = BsCalendarData.monthLengths[yearIndex]
        for (m in 0 until date.month - 1) offset += months[m]
        offset += date.day - 1
        return LocalDate.fromEpochDays(epochDays + offset)
    }

    fun isValid(date: BsDate): Boolean {
        if (date.month !in 1..12) return false
        val dim = daysInMonth(date.year, date.month) ?: return false
        return date.day in 1..dim
    }
}
