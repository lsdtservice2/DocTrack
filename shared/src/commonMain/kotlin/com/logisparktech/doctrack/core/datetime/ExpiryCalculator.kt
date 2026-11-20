package com.logisparktech.doctrack.core.datetime

import com.logisparktech.doctrack.domain.model.Document
import com.logisparktech.doctrack.domain.model.DocumentStatus
import com.logisparktech.doctrack.domain.model.DocumentWithExpiry
import com.logisparktech.doctrack.domain.model.ExpiryInfo
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/** Status is always derived from dates; it is never stored. */
object ExpiryCalculator {

    fun daysRemaining(expiryDate: LocalDate, today: LocalDate): Int = today.daysUntil(expiryDate)

    fun calculate(
        expiryDate: LocalDate?,
        today: LocalDate,
        warningDays: Int,
        renewalInProgress: Boolean = false,
    ): ExpiryInfo {
        val days = expiryDate?.let { daysRemaining(it, today) }
        val status = when {
            renewalInProgress -> DocumentStatus.RENEWAL_IN_PROGRESS
            days == null -> DocumentStatus.NO_EXPIRY
            days < 0 -> DocumentStatus.EXPIRED
            days <= warningDays -> DocumentStatus.EXPIRING_SOON
            else -> DocumentStatus.ACTIVE
        }
        return ExpiryInfo(status, days)
    }

    fun withExpiry(document: Document, today: LocalDate, warningDays: Int) = DocumentWithExpiry(
        document,
        calculate(document.expiryDate, today, warningDays, document.renewalInProgress),
    )
}
