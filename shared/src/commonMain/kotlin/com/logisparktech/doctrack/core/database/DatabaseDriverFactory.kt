package com.logisparktech.doctrack.core.database

import app.cash.sqldelight.db.SqlDriver

const val DATABASE_NAME = "doctrack.db"

/** Creates a platform SQLite driver with foreign keys enabled. */
expect class DatabaseDriverFactory {
    fun create(): SqlDriver
}
