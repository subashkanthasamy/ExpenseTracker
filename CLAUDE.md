# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Expense Tracker is a **Kotlin Multiplatform (KMP)** app targeting Android and iOS. The Android app is built with Kotlin and Jetpack Compose (Material 3). The iOS app is in early setup with SwiftUI.

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
├── iosApp/                 # iOS app (SwiftUI entry point)
└── gradle/libs.versions.toml
```

### Shared Module (`shared/`)
- **domain/model/** — All domain models (Expense, Category, Budget, etc.)
- **domain/repository/** — Repository interfaces (Auth, Expense, Category, etc.)
- **domain/usecase/** — Shared use cases (SMS parsing, export interface)
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
- **iOS app** will use SwiftUI + Shared.framework (in progress)
- **DI:** Koin (shared module), Hilt (Android app module)

## Key Dependencies

### Shared (KMP)
- Koin (DI), Ktor (HTTP), kotlinx-serialization, kotlinx-datetime
- kotlinx-coroutines, multiplatform-settings
- Room runtime + SQLite bundled (for future KMP Room migration)

### Android-only
- Jetpack Compose (Material 3), Navigation Compose
- Hilt, Room, Firebase (Auth + Firestore)
- CameraX, ML Kit, WorkManager, Biometric, DataStore, Coil

## Notes
- Firebase is currently Android-only. iOS backend TBD.
- Room stays in `app/` due to AGP 9.x KSP compatibility issues with KMP plugin.
- Compose Multiplatform not yet integrated (AGP 9.x compatibility). Shared module is pure Kotlin.
- `System.currentTimeMillis()` replaced with `kotlinx.datetime.Clock` in shared code.
- `String.format()` not available in Kotlin/Native; use manual formatting in shared code.
