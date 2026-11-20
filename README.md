# DocTrack — Never Miss a Renewal.

Version 1.0 · Offline-first, local-only document expiry & renewal reminder app for **Android and iOS**,
built with Kotlin Multiplatform and Compose Multiplatform.

There is no backend, no login, no cloud and no network permission. All documents, attachments,
reminders, renewal history and settings stay on the device.

## Features

* Track documents (passport, licence, insurance, …) with categories, notes and attachments
  (photos, PDFs, files).
* Dashboard with expired / expiring-soon / valid counts and upcoming renewals.
* Calendar view with **AD and BS (Bikram Sambat)** date display and input.
* Configurable reminders delivered as local notifications.
* One-tap renewal that keeps a full renewal history.
* ZIP backup & restore with integrity checks.
* English and Nepali UI.
* Optional biometric / device-credential app lock.

## Stack (stable versions only)

Kotlin 2.4.21 · Compose Multiplatform 1.12.2 · Material 3 1.9.0 · SQLDelight 2.4.1 · Koin 4.2.2 ·
kotlinx-coroutines 1.11.0 · kotlinx-datetime 0.8.0 · kotlinx-serialization 1.13.0 ·
Navigation Compose 2.10.0 · AGP 9.5.1 · Gradle 9.9.2

All versions are managed in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

| Platform | Minimum      | Target / Compile |
|----------|--------------|------------------|
| Android  | API 24 (7.0) | API 37           |
| iOS      | 18.2         | arm64 device + arm64 simulator |

## Requirements

* JDK 21 (the Gradle daemon toolchain is pinned to Azul 21 via `gradle/gradle-daemon-jvm.properties`;
  app bytecode targets JVM 11)
* Android Studio (recent stable) with Android SDK 37
* For iOS: a Mac with Xcode 16+ and the Kotlin Multiplatform plugin

## Project layout

```
shared/src/commonMain/kotlin/com/logisparktech/doctrack/
  app/            App.kt, navigation/, theme/, AppLockGate
  core/           calendar/ (exact AD<->BS), datetime/, localization/ (EN/NE), storage/ (ZIP),
                  notification/ (planner + sync), platform/ (expect pickers), database/, common/
  data/           repository/ (SQLDelight), mapper/, backup/
  domain/         model/, repository/, usecase/
  feature/        dashboard, documents, document_details, add_document, calendar, reminders,
                  renewal, settings, categories, backup, attachments
  di/             Koin modules
shared/src/commonMain/sqldelight/   schema (.sq) + databases/1.db snapshot for migration checks
shared/src/androidMain/             AlarmManager notifications, boot restore, pickers, FileProvider, BiometricPrompt
shared/src/iosMain/                 UNUserNotificationCenter, PHPicker/UIDocumentPicker, share sheet, LocalAuthentication
shared/src/commonTest/              AD/BS conversion, expiry & reminder rules, ZIP integrity
shared/src/androidHostTest/         database cascades, renewal history, backup/restore round-trip
androidApp/                         Application + MainActivity
iosApp/                             SwiftUI host (calls initDocTrack() at launch)
```

> The module is still called `shared` (rather than `composeApp`) to keep the existing project setup intact.

## Local database

All app data is stored in an on-device **SQLite** database file named `doctrack.db`, accessed through
**[SQLDelight](https://github.com/sqldelight/sqldelight) 2.4.1** (`app.cash.sqldelight`).

### Packages used

| Package | Where | Purpose |
|---------|-------|---------|
| `app.cash.sqldelight` (Gradle plugin) | build | Generates the type-safe `DocTrackDatabase` Kotlin API from the `.sq` files |
| `app.cash.sqldelight:android-driver` | `androidMain` | `AndroidSqliteDriver`: uses Android's built-in SQLite |
| `app.cash.sqldelight:native-driver` | `iosMain` | `NativeSqliteDriver`: uses iOS's system SQLite (via SQLiter) |
| `app.cash.sqldelight:coroutines-extensions` | `commonMain` | `asFlow()` / `mapToList()`: queries become `Flow`s so the UI updates automatically |
| `app.cash.sqldelight:sqlite-driver` | `androidHostTest` | JDBC in-memory SQLite for running database tests on the JVM |

### Why SQLDelight

* **Kotlin Multiplatform support.** One schema and one set of queries live in `commonMain` and are shared
  by Android and iOS. Only the small driver factory (`DatabaseDriverFactory`) is platform-specific.
* **SQL first, type-safe Kotlin out.** Tables and queries are written in plain SQL (`.sq` files), and
  SQLDelight generates Kotlin data classes and query functions. Bad SQL, wrong column names and type
  mismatches are caught **at compile time**, not at runtime.
* **Uses the platform's own SQLite.** There's no extra native engine to bundle, so the app stays small, and
  SQLite is proven, transactional and fully offline. That suits an app with no backend.
* **Reactive queries.** With the coroutines extensions, dashboard, list and calendar screens observe
  queries as `Flow`s and refresh whenever data changes.
* **Real relational features.** Foreign keys (`ON DELETE CASCADE` / `RESTRICT`), unique indexes and
  transactions keep documents, reminders, attachments and history consistent. Backup restore runs in a
  single transaction.
* **Safe schema evolution.** `verifyMigrations = true`, and a schema snapshot (`databases/1.db`) lets
  Gradle check future migrations against the shipped schema.

### Schema

Defined in `shared/src/commonMain/sqldelight/com/logisparktech/doctrack/db/`:

| Table | File | Contents |
|-------|------|----------|
| `DocumentCategories` | `DocumentCategory.sq` | Category name, icon, colour. 14 default categories are seeded (passport, driving licence, citizenship, insurance, …) |
| `Documents` | `Document.sq` | Name, number, category, issuing authority, issue/expiry/renewal dates, notes, archive flag |
| `DocumentReminders` | `DocumentReminder.sq` | "Days before expiry" reminders per document (unique per document + day) |
| `DocumentAttachments` | `DocumentAttachment.sq` | Metadata and relative path of attached files |
| `RenewalHistory` | `RenewalHistory.sq` | Previous and new issue/expiry dates recorded on each renewal |
| `AppSettings` | `AppSettings.sq` | Single row: language, calendar type (AD/BS), theme, default reminder days, warning threshold, reminder hour, biometric lock |

Relationships: deleting a document cascades to its reminders, attachments and renewal history. A
category that still has documents can't be deleted (`RESTRICT`).

### Storage details

* **Foreign keys are switched on explicitly** on both platforms (SQLite leaves them off by default):
  `setForeignKeyConstraintsEnabled(true)` on Android and `foreignKeyConstraints = true` on iOS.
* **Dates** are stored as ISO-8601 `TEXT` (`yyyy-MM-dd`, Gregorian) so they sort and index correctly.
  Timestamps (`createdAt`, `updatedAt`) are epoch-millis `INTEGER`s.
* **Attachment files are not stored in the database.** They are copied into an app-private `attachments/`
  folder, and the table keeps only their relative path. This keeps the database small and fast.
* **Location**: the app's private data directory (Android `databases/`, iOS app sandbox). No other app,
  and no cloud backup, can read it.

## Key behaviours

* **Status is always computed** from the expiry date and the configurable "expiring soon" threshold.
* **Dates are stored as ISO Gregorian dates** and converted for display/input. BS conversion uses a
  month-length table (BS 1970–2090 ≈ AD 1913–Apr 2034) cross-checked against two independent datasets;
  dates outside that range are shown in AD.
* **Reminders**: any change to documents, reminders or settings triggers an idempotent re-plan that
  replaces all pending notifications (no duplicates). Android uses doze-friendly inexact alarms and
  restores them after reboot/update/time change. iOS schedules the soonest 60 (system limit is 64).
* **Renewal** writes a `RenewalHistory` row with the previous dates before updating the document.
* **Backup** is a ZIP (manifest with CRC-32 per file + `data.json` + attachments). Restore validates
  everything first, asks for confirmation, writes a safety copy of current data, then replaces data in
  one transaction.
* **Security**: app-private storage (iOS: complete file protection, excluded from iCloud backup;
  Android: `allowBackup=false`), optional biometric/device-credential lock.

## Getting started

```bash
git clone <repo-url>
cd DocTrack
```

Android Studio creates `local.properties` (pointing at your Android SDK) on first sync. It is
machine-specific and git-ignored — don't commit it.

### Android

```bash
./gradlew :androidApp:assembleDebug      # build a debug APK
./gradlew :androidApp:installDebug       # build and install on a connected device/emulator
```

On Windows use `gradlew.bat` instead of `./gradlew`. The APK is written to
`androidApp/build/outputs/apk/debug/`.

### iOS

1. Open `iosApp/iosApp.xcodeproj` in Xcode on a Mac.
2. Set your `TEAM_ID` in `iosApp/Configuration/Config.xcconfig` (needed for device builds).
3. Select a simulator or device and Run. Xcode builds the `Shared` framework through Gradle automatically.

### Tests

```bash
./gradlew :shared:testAndroidHostTest     # common + Android host tests
./gradlew :shared:iosSimulatorArm64Test   # common tests on the iOS simulator (macOS only)
```

Coverage: AD/BS conversion, expiry rules, reminder planning, dashboard stats, ZIP integrity,
database cascades, renewal history and backup/restore round-trip.

## Release builds

Signing configuration is not committed. Keep keystores (`*.jks`, `*.keystore`) and any
`keystore.properties` outside version control — they are covered by `.gitignore`.

## Privacy

DocTrack requests no network permission and collects no data. Everything lives in app-private
storage on the device; the only way data leaves the device is a backup ZIP the user exports explicitly.
