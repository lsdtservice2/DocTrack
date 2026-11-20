package com.logisparktech.doctrack.core.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

interface DateProvider {
    fun today(): LocalDate
    fun nowMillis(): Long
    fun timeZone(): TimeZone

    /** Emits today's date and re-emits when the day changes, so "days remaining" stays current. */
    fun todayFlow(): Flow<LocalDate> = flow {
        while (true) {
            emit(today())
            delay(60_000)
        }
    }.distinctUntilChanged()
}

class SystemDateProvider : DateProvider {
    override fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
    override fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
    override fun timeZone(): TimeZone = TimeZone.currentSystemDefault()
}
