package com.logisparktech.doctrack.app.navigation

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute

/** [status] optionally pre-filters the list (a DocumentStatus name or "UPCOMING_RENEWAL"). */
@Serializable data class DocumentsRoute(val status: String = "")

@Serializable data object CalendarRoute
@Serializable data object NotificationsRoute
@Serializable data object SettingsRoute

@Serializable data class DocumentDetailRoute(val id: Long)

/** [id] = -1 creates a new document. */
@Serializable data class DocumentFormRoute(val id: Long = -1, val presetExpiry: String = "")

@Serializable data class RenewRoute(val id: Long)
@Serializable data class AttachmentRoute(val id: Long)
@Serializable data object CategoriesRoute
@Serializable data object BackupRoute
