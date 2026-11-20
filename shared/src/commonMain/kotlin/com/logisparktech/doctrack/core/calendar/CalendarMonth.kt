package com.logisparktech.doctrack.core.calendar

import com.logisparktech.doctrack.domain.model.CalendarType
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

/** A month in either calendar system. [month] is 1..12 in that system. */
data class CalendarMonth(val type: CalendarType, val year: Int, val month: Int) {

    /** Gregorian dates of every day in this month, in order. */
    val days: List<LocalDate> by lazy {
        when (type) {
            CalendarType.AD -> {
                val first = LocalDate(year, month, 1)
                val next = first.plus(DatePeriod(months = 1))
                generateSequence(first) { it.plus(DatePeriod(days = 1)) }.takeWhile { it < next }.toList()
            }
            CalendarType.BS -> {
                val first = NepaliDateConverter.bsToAd(BsDate(year, month, 1)) ?: return@lazy emptyList()
                val count = NepaliDateConverter.daysInMonth(year, month) ?: 0
                List(count) { first.plus(DatePeriod(days = it)) }
            }
        }
    }

    /** Number of empty cells before day 1 when weeks start on Sunday. */
    val leadingBlanks: Int get() = days.firstOrNull()?.dayOfWeek?.sundayIndex() ?: 0

    /** Day-of-month number in this calendar system for a Gregorian date in the month. */
    fun dayNumber(date: LocalDate): Int = when (type) {
        CalendarType.AD -> date.day
        CalendarType.BS -> NepaliDateConverter.adToBs(date)?.day ?: date.day
    }

    fun plusMonths(delta: Int): CalendarMonth {
        val total = year * 12 + (month - 1) + delta
        val candidate = copy(year = total.floorDiv(12), month = total.mod(12) + 1)
        return candidate.coerceSupported()
    }

    fun plusYears(delta: Int): CalendarMonth = copy(year = year + delta).coerceSupported()

    val isFirstSupported: Boolean get() = type == CalendarType.BS && year == NepaliDateConverter.minBsYear && month == 1
    val isLastSupported: Boolean get() = type == CalendarType.BS && year == NepaliDateConverter.maxBsYear && month == 12

    private fun coerceSupported(): CalendarMonth = when {
        type == CalendarType.AD -> copy(year = year.coerceIn(1900, 2200))
        year < NepaliDateConverter.minBsYear -> copy(year = NepaliDateConverter.minBsYear, month = 1)
        year > NepaliDateConverter.maxBsYear -> copy(year = NepaliDateConverter.maxBsYear, month = 12)
        else -> this
    }

    operator fun contains(date: LocalDate): Boolean = of(type, date) == this

    companion object {
        /** The month (in [type]) containing [date]. Falls back to the nearest supported BS month. */
        fun of(type: CalendarType, date: LocalDate): CalendarMonth = when (type) {
            CalendarType.AD -> CalendarMonth(type, date.year, date.month.number)
            CalendarType.BS -> {
                val bs = NepaliDateConverter.adToBs(date)
                when {
                    bs != null -> CalendarMonth(type, bs.year, bs.month)
                    date < NepaliDateConverter.minAdDate -> CalendarMonth(type, NepaliDateConverter.minBsYear, 1)
                    else -> CalendarMonth(type, NepaliDateConverter.maxBsYear, 12)
                }
            }
        }
    }
}

/** 0 = Sunday ... 6 = Saturday. */
fun DayOfWeek.sundayIndex(): Int = isoDayNumber % 7

fun LocalDate.minusDays(days: Int): LocalDate = minus(DatePeriod(days = days))
fun LocalDate.plusDays(days: Int): LocalDate = plus(DatePeriod(days = days))
