package com.logisparktech.doctrack

import com.logisparktech.doctrack.core.calendar.BsDate
import com.logisparktech.doctrack.core.calendar.CalendarMonth
import com.logisparktech.doctrack.core.calendar.NepaliDateConverter
import com.logisparktech.doctrack.domain.model.CalendarType
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NepaliDateConverterTest {

    /** Well-known published equivalences (Nepali New Year and Constitution Day). */
    private val knownPairs = listOf(
        LocalDate(1913, 4, 13) to BsDate(1970, 1, 1),
        LocalDate(1943, 4, 14) to BsDate(2000, 1, 1),
        LocalDate(2015, 9, 20) to BsDate(2072, 6, 3),
        LocalDate(2020, 4, 13) to BsDate(2077, 1, 1),
        LocalDate(2021, 4, 14) to BsDate(2078, 1, 1),
        LocalDate(2022, 4, 14) to BsDate(2079, 1, 1),
        LocalDate(2023, 4, 14) to BsDate(2080, 1, 1),
        LocalDate(2024, 4, 13) to BsDate(2081, 1, 1),
        LocalDate(2025, 4, 14) to BsDate(2082, 1, 1),
    )

    @Test
    fun knownDatesConvertBothWays() {
        for ((ad, bs) in knownPairs) {
            assertEquals(bs, NepaliDateConverter.adToBs(ad), "AD $ad")
            assertEquals(ad, NepaliDateConverter.bsToAd(bs), "BS $bs")
        }
    }

    @Test
    fun everySupportedDayRoundTripsAndIsContiguous() {
        var ad = NepaliDateConverter.minAdDate
        var previous: BsDate? = null
        var count = 0
        while (ad <= NepaliDateConverter.maxAdDate) {
            val bs = NepaliDateConverter.adToBs(ad)!!
            assertEquals(ad, NepaliDateConverter.bsToAd(bs))
            if (previous != null) assertEquals(next(previous), bs, "after $previous")
            previous = bs
            ad = ad.plus(DatePeriod(days = 1))
            count++
        }
        assertTrue(count > 44_000)
        assertEquals(BsDate(NepaliDateConverter.maxBsYear, 12, NepaliDateConverter.daysInMonth(NepaliDateConverter.maxBsYear, 12)!!), previous)
    }

    private fun next(d: BsDate): BsDate {
        val dim = NepaliDateConverter.daysInMonth(d.year, d.month)!!
        return when {
            d.day < dim -> d.copy(day = d.day + 1)
            d.month < 12 -> BsDate(d.year, d.month + 1, 1)
            else -> BsDate(d.year + 1, 1, 1)
        }
    }

    @Test
    fun monthLengthsAreValid() {
        for (year in NepaliDateConverter.minBsYear..NepaliDateConverter.maxBsYear) {
            val days = (1..12).map { NepaliDateConverter.daysInMonth(year, it)!! }
            assertTrue(days.all { it in 29..32 }, "year $year")
            assertTrue(days.sum() in 365..366, "year $year")
        }
    }

    @Test
    fun outOfRangeAndInvalidDatesReturnNull() {
        assertNull(NepaliDateConverter.adToBs(LocalDate(1913, 4, 12)))
        assertNull(NepaliDateConverter.adToBs(NepaliDateConverter.maxAdDate.plus(DatePeriod(days = 1))))
        assertNull(NepaliDateConverter.bsToAd(BsDate(1969, 12, 30)))
        assertNull(NepaliDateConverter.bsToAd(BsDate(2081, 13, 1)))
        assertNull(NepaliDateConverter.bsToAd(BsDate(2081, 1, 0)))
        assertNull(NepaliDateConverter.bsToAd(BsDate(2081, 1, 32)))
        assertFalse(NepaliDateConverter.isValid(BsDate(2081, 9, 30)))
        assertNull(NepaliDateConverter.daysInMonth(2091, 1))
    }

    @Test
    fun bsCalendarMonthGridStartsOnCorrectWeekday() {
        // BS 2081-01-01 = 2024-04-13, a Saturday (index 6 with Sunday-first weeks).
        val month = CalendarMonth(CalendarType.BS, 2081, 1)
        assertEquals(LocalDate(2024, 4, 13), month.days.first())
        assertEquals(31, month.days.size)
        assertEquals(6, month.leadingBlanks)
        assertEquals(CalendarMonth(CalendarType.BS, 2080, 12), month.plusMonths(-1))
        assertEquals(CalendarMonth(CalendarType.BS, 2082, 1), month.plusYears(1))
    }

    @Test
    fun adCalendarMonthHandlesLeapYears() {
        val feb = CalendarMonth(CalendarType.AD, 2024, 2)
        assertEquals(29, feb.days.size)
        assertEquals(4, feb.leadingBlanks) // 1 Feb 2024 was a Thursday
        assertEquals(CalendarMonth(CalendarType.AD, 2023, 12), CalendarMonth(CalendarType.AD, 2024, 1).plusMonths(-1))
    }
}
