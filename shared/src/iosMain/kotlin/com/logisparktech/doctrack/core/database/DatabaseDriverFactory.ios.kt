package com.logisparktech.doctrack.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import com.logisparktech.doctrack.db.DocTrackDatabase

actual class DatabaseDriverFactory {
    actual fun create(): SqlDriver = NativeSqliteDriver(
        schema = DocTrackDatabase.Schema,
        name = DATABASE_NAME,
        onConfiguration = { config ->
            config.copy(extendedConfig = DatabaseConfiguration.Extended(foreignKeyConstraints = true))
        },
    )
}
