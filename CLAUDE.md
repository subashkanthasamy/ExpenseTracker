# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Expense Tracker is a **Kotlin Multiplatform (KMP)** app targeting Android and iOS. The Android app is built with Kotlin and Jetpack Compose (Material 3). The iOS app is SwiftUI with its own Firebase integration.

**Important:** Android is the reference implementation and is far ahead in *features*. Both
platforms now share the **same domain models** from `shared/` — iOS consumes them through
`Shared.framework` (see `iosApp/iosApp/Models/SharedBridge.swift`). iOS still has its own
SwiftUI views and ViewModels. See "Platform parity" below; treat any behaviour difference as
a real gap, not an assumption.

- **Package namespace:** `com.bose.expensetracker`
- **Min SDK (Android):** 24 | **Target/Compile SDK:** 36
- **Java compatibility:** 11
- **Build system:** Gradle with Kotlin DSL (`.kts`) and version catalog (`gradle/libs.versions.toml`)
- **KMP targets:** Android, iosX64, iosArm64, iosSimulatorArm64

## Project Structure

```
├── app/                    # Android app module (Jetpack Compose UI, Hilt DI, Firebase)
├── shared/                 # KMP shared module
│   ├── src/commonMain/     # Shared code (domain, data interfaces, UI state, utils)
│   ├── src/androidMain/    # Android platform code
│   └── src/iosMain/        # iOS platform code
├── iosApp/                 # iOS app (SwiftUI + Firebase)
│   ├── iosApp.xcodeproj    # Open this (no workspace — Firebase comes via SPM, not CocoaPods)
│   ├── Info.plist          # CFBundleURLTypes for the Google Sign-In callback
│   └── iosApp/             # Swift sources (auto-included via synchronized folder)
└── gradle/libs.versions.toml
```

### Shared Module (`shared/`)
- **domain/model/** — All domain models (Expense, Category, Budget, etc.)
- **domain/repository/** — Repository interfaces (Auth, Expense, Category, etc.)
- **domain/usecase/** — Shared use cases (SMS parsing, export interface)
  (`smsimport/` is exported to Swift and usable there; only SMS *input* is Android-only)
- **data/local/** — Local data source interfaces + SyncStatus
- **data/remote/** — Remote data source interfaces
- **data/preferences/** — Preference interfaces (Theme, Biometric, SMS)
- **data/sync/** — Background sync scheduler interface
- **ui/state/** — All UiState data classes shared across platforms
- **ui/theme/** — Color constants as Long values
- **ui/navigation/** — Route definitions (@Serializable)
- **util/** — Currency formatting, VoiceExpenseParser, category emojis
- **di/** — Koin shared module

### App Module (`app/`)
- **data/** — Room DB, Firebase, DataStore, repository implementations
- **ui/** — Jetpack Compose screens, ViewModels (Hilt), navigation
- **di/** — Hilt DI modules

## Build & Test Commands

```bash
./gradlew :shared:allTests                        # Shared unit tests (ReceiptTextParser etc.)
./gradlew assembleDebug                           # Build debug Android APK
./gradlew :shared:allMetadataJar                  # Compile shared commonMain
./gradlew :shared:compileKotlinIosArm64           # Compile shared for iOS
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64  # Build iOS framework
./gradlew test                                    # Run unit tests
./gradlew connectedAndroidTest                    # Run instrumented tests
./gradlew clean                                   # Clean build outputs
```

## Architecture

- **KMP shared module** contains domain layer, data interfaces, UI state models
- **Android app** uses Hilt for DI, Firebase for backend, Room for local DB
- **iOS app** SwiftUI + Firebase (Auth + Firestore) directly, using **shared domain models**
  via `Shared.framework`. Swift-side views/ViewModels remain iOS-specific.
- **DI:** Koin (shared module), Hilt (Android app module). iOS constructs services by hand.

### Using shared models from Swift (`Models/SharedBridge.swift`)

Kotlin/Native's Obj-C export has three sharp edges the bridge smooths over. **Read the
bridge before touching model code.**

1. **Dates are `Int64` epoch millis, not `Date`.** Use the `…Value` accessors:
   `expense.dateValue`, `household.createdAtValue`, `goal.targetDateValue`. Passing `.date`
   to a `DateFormatter` is a compile error, which is the good outcome.
2. **Kotlin default parameter values are NOT exported.** The bridge adds `convenience init`s
   that restore them (and take `Date` instead of millis), e.g. `Expense(… date: Date …)`.
3. **Everything is read-only** — Kotlin data classes export with `let` properties and a
   full-argument `doCopy(...)`. The bridge provides `with(...)` / `addingHousehold(...)`
   helpers; prefer those over `doCopy`.

Other gotchas:
- `Shared.Category` **must be qualified** — bare `Category` is ambiguous in Swift.
- `AppUser` is `typealias AppUser = Shared.User`, avoiding a clash with `FirebaseAuth.User`.
- Kotlin enums export as NSObject singletons, so `budget.status == .exceeded` works but
  Swift `switch` pattern-matching does not.
- Shared models already provide computed properties — `Budget.percentage`/`.status`,
  `SavingsGoal.progress`/`.remaining`/`.monthlyNeeded`. Don't reimplement them in Swift.
- Encoding to Firestore is now a **pass-through**: the model already holds millis, so write
  `e.date` directly. Do not wrap it in a Date conversion.
- Types that legitimately stay Swift-only live in `Models/AppModels.swift`: `Reminder` (no
  shared counterpart), and `ChatMessage`/`InlineStat`/`CategoryBreakdown` (carry SwiftUI
  presentation state such as `Color`).

### iOS ↔ shared framework wiring

`iosApp.xcodeproj` builds the KMP framework itself via a pre-Sources run-script phase:

```
cd "$SRCROOT/.."
./gradlew :shared:embedAndSignAppleFrameworkForXcode
```

- `FRAMEWORK_SEARCH_PATHS = $(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`
- `OTHER_LDFLAGS` includes `-framework Shared`
- `ENABLE_USER_SCRIPT_SANDBOXING = NO` — **required**, Gradle cannot run in the sandbox
- `embedAndSignAppleFrameworkForXcode` logs **SKIPPED** and that is correct: the framework is
  `isStatic = true`, so there is nothing to embed or sign. Don't "fix" it.
- Top-level Kotlin functions are exported per *file*, e.g. `getPlatformName()` in
  `Platform.ios.kt` is `Platform_iosKt.getPlatformName()` in Swift. Check
  `shared/build/xcode-frameworks/**/Shared.framework/Headers/Shared.h` for real Swift names.

## Firestore wire format

**Time fields are epoch milliseconds (`Long`/`Int64`) — never Firestore `Timestamp`.**

This bit us hard: iOS used to write `Timestamp`, which crashed Android with
`Field 'date' is not a java.lang.Number`, and in reverse silently decoded every
Android-written date as "now". Affected fields: `date`, `createdAt`, `updatedAt`, `targetDate`.

Both platforms now *read* either representation for backward compatibility
(`FirestoreDataSource.getEpochMillis()`, `FirestoreService.decodeMillis()`), but both must
only ever *write* millis. `tools/migrate-timestamps.js` normalises legacy documents.

## Error handling rules (learned the hard way)

- **Never catch `CancellationException`.** In Kotlin it extends `RuntimeException`, so a bare
  `catch (e: Exception)` swallows it and turns "coroutine cancelled" into a wrong answer.
  This made a cancelled household lookup look like "user has no household" and bounced
  signed-in users to household setup. `FirestoreDataSource` rethrows it explicitly.
- **Don't put a non-Compose-state guard in the `if` around a `LaunchedEffect`.** A
  recomposition re-evaluates the guard, the effect leaves the composition, and its in-flight
  work is cancelled. Keep once-only guards *inside* the effect.
- **A failed lookup is not an empty result.** Don't drive navigation off a `null` that could
  mean either.
- On iOS, Credential Manager reports provider-side refusals as *cancellations* — log
  `e.type`/`e.errorMessage` rather than assuming the user dismissed the sheet.

## Key Dependencies

### Shared (KMP)
- Koin (DI), Ktor (HTTP), kotlinx-serialization, kotlinx-datetime
- kotlinx-coroutines, multiplatform-settings
- Room runtime + SQLite bundled (for future KMP Room migration)

### Android-only
- Jetpack Compose (Material 3), Navigation Compose
- Hilt, Room, Firebase (Auth + Firestore)
- CameraX, ML Kit, WorkManager, Biometric, DataStore, Coil

## Platform parity (Android = reference)

Rough scale: ~11.7k lines of Android UI vs ~2.8k lines of Swift, so even the shipped iOS
screens are thinner than their Android counterparts.

Domain models are shared; the gap is features and platform plumbing.

| Area | Android | iOS |
|---|---|---|
| Dashboard, Expenses, Categories, Budgets, Savings, Net Worth, Household, Insights, Coach | ✅ | ✅ (thinner) |
| Email/password auth | ✅ | ✅ |
| Google Sign-In | ✅ | ✅ |
| Phone (OTP) auth | ✅ | ❌ |
| Settings (theme, biometric, export, toggles) | ✅ | ✅ theme, biometric, export/import, reset |
| Notifications / reminders | ✅ | ✅ daily + bill (local notifications) |
| Recurring expenses | ✅ WorkManager (today-only) | ✅ launch catch-up (better) |
| Receipt scanner (OCR) | ✅ CameraX + ML Kit | ✅ Vision (shared heuristics) |
| Voice expense entry | ✅ | ✅ SFSpeechRecognizer (shared parser) |
| Export / import | ✅ | ✅ CSV + PDF export, CSV import |
| Offline cache | ✅ Room | ❌ Firestore only |
| Domain models | ✅ `shared/` | ✅ `shared/` via Shared.framework |
| Sandbox / demo mode | ✅ | ❌ |
| **SMS transaction import** | ✅ | **impossible — see below** |

### SMS import cannot be ported

iOS gives apps no API to read the SMS inbox. `ILMessageFilterExtension` only sees messages
from *unknown senders* and is explicitly designed so it cannot return content to the app.
Note the parsing logic itself (`SmsTransactionParser`, `SmsCategoryMatcher`) lives in
`shared/commonMain` and **is** callable from Swift today as
`SmsTransactionParser().parse(sender:body:receivedTimestamp:)` — only the input is
unavailable. Viable iOS substitutes: a Share Extension, a paste-to-parse field, or a bank
aggregator API (RBI Account Aggregator).


### Divergences to be aware of

- **Recurring rules do not sync across platforms.** Android stores them in Room only; iOS
  stores them in `households/{id}/recurring`. Moving Android onto that collection would fix
  it.
- **Reminders are device-local on both platforms** by design — a notification schedule
  belongs to the device showing it.
- **Recurring generation differs deliberately.** Android's worker checks only "due today", so
  a missed day is lost permanently. iOS catches up from the last generated date on launch.
  Prefer the iOS behaviour if unifying.
- **Budget alerts are dead code on Android** — `NotificationHelper.showBudgetAlertNotification`
  has no callers. Nothing to port until it is built or specified. On iOS a spending-threshold
  check cannot run in the background; it would fire on app open, or need Cloud Functions + FCM.


- **OCR text differs by engine.** ML Kit returns block text with rows intact; Vision returns
  one observation per text region, so `ReceiptScanService.reconstructLines` regroups them
  into visual rows. `ReceiptTextParser` assumes the label and amount share a line — keep that
  invariant if you touch either side.

### Android → iOS platform mappings

| Android | iOS equivalent |
|---|---|
| CameraX + ML Kit text recognition | VisionKit `DataScannerViewController` / Vision |
| SpeechRecognizer | Speech framework (`SFSpeechRecognizer`) |
| WorkManager / AlarmManager | `BGTaskScheduler` + `UNUserNotificationCenter` |
| DataStore | `UserDefaults` (or multiplatform-settings via shared) |
| Room | SwiftData, or move Room into `shared` for both |
| Hilt | manual construction / a small factory |

## Notes
- Firebase is on **both** platforms now (Auth + Firestore). iOS uses firebase-ios-sdk via SPM.
- Config files are **untracked**: `app/google-services.json`, `iosApp/iosApp/GoogleService-Info.plist`.
  A fresh clone cannot run either platform without obtaining both.
- Android debug builds must have the debug keystore SHA-1 registered in Firebase or Google
  Sign-In fails with a misleading "cancelled".
- Room stays in `app/` due to AGP 9.x KSP compatibility issues with KMP plugin.
- Compose Multiplatform not yet integrated (AGP 9.x compatibility). Shared module is pure Kotlin.
- `System.currentTimeMillis()` replaced with `kotlinx.datetime.Clock` in shared code.
- `String.format()` not available in Kotlin/Native; use manual formatting in shared code.
