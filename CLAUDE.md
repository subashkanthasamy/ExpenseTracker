# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Expense Tracker is an Android app built with Kotlin and Jetpack Compose (Material 3). It is a single-module project in early stage, using a single Activity with Compose UI.

- **Package namespace:** `com.bose.expensetracker`
- **Min SDK:** 24 | **Target/Compile SDK:** 36
- **Java compatibility:** 11
- **Build system:** Gradle with Kotlin DSL (`.kts`) and version catalog (`gradle/libs.versions.toml`)

## Build & Test Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumented tests (requires device/emulator)
./gradlew clean                  # Clean build outputs
```

## Architecture

- **Single Activity:** `MainActivity.kt` — entry point using `setContent` with Compose
- **UI layer:** `ui/theme/` contains Material 3 theme (`Theme.kt`), colors (`Color.kt`), and typography (`Type.kt`)
- **Compose BOM:** `2024.09.00` — all Compose dependencies are version-aligned via BOM
- Dynamic color support is enabled for Android 12+ devices

## Key Dependencies

- AndroidX Core KTX, Lifecycle Runtime KTX, Activity Compose
- Jetpack Compose (UI, Material 3, Tooling)
- Testing: JUnit 4, Espresso, Compose UI Test

No data layer (Room, Retrofit), DI (Hilt), navigation, or ViewModel libraries are currently integrated.