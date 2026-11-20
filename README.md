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
