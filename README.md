# Expense Tracker

A feature-rich Android expense tracking app built with Kotlin and Jetpack Compose (Material 3). Designed for household-level collaborative budgeting with smart automation features like SMS parsing, receipt scanning, and voice input.

## Features

### Expense Management
- Add, edit, and delete expenses with category, date, and notes
- Filter expenses by household member
- Voice input — say *"Spent 50 on groceries"* and it auto-fills
- Receipt scanning via camera with ML Kit OCR (extracts amount, date, merchant)
- Automatic SMS bank transaction import with duplicate detection

### Budgets & Savings
- Set monthly spending limits per category
- Budget status indicators (OK / Warning at 80% / Exceeded)
- Budget alert notifications
- Savings goals with target amount, target date, and progress tracking

### Recurring Expenses
- Configure daily, weekly, monthly, or yearly recurring transactions
- Automatic expense generation via WorkManager
- Active/inactive toggle with optional end date

### Insights & Analytics
- Spending trends by week, month, or year
- Category breakdown with charts (Vico)
- Daily spending timeline
- Average daily spend, top category, and spending anomaly detection
- Period-over-period comparison with percentage change

### Net Worth
- Track assets (property, investments, savings, vehicles)
- Track liabilities (loans, credit cards, mortgages)
- Net worth calculation and historical view

### Household Collaboration
- Create or join households via invite codes
- Shared expenses, budgets, and categories across members
- Switch between multiple households
- Per-member expense filtering

### Authentication & Security
- Email/password sign-in
- Google Sign-In (Credential Manager with One Tap + fallback)
- Phone OTP authentication (requires Firebase Blaze plan)
- Biometric lock (fingerprint / face unlock)

### Notifications & Reminders
- Daily expense logging reminders
- Bill due date reminders (weekly, bi-weekly, monthly)
- Budget overspend alerts
- SMS import notifications with confirm/dismiss actions

### Data Export & Import
- Export expenses to CSV or PDF
- Import expenses from CSV with validation
- Date range and member filtering

### UI / UX
- Material 3 with Material You dynamic colors (Android 12+)
- Light / Dark / System theme
- Predictive back gesture (Android 14+)
- Edge-to-edge display

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.2 |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Clean Architecture |
| DI | Hilt |
| Local DB | Room |
| Backend | Firebase Auth + Cloud Firestore |
| Navigation | Compose Navigation (type-safe) |
| Camera | CameraX |
| OCR | ML Kit Text Recognition |
| Charts | Vico |
| Images | Coil |
| Background | WorkManager |
| Security | AndroidX Biometric + DataStore |
| Build | Gradle KTS + Version Catalog |

## Project Structure

```
com.bose.expensetracker/
├── data/
│   ├── local/          # Room database, DAOs, entities
│   ├── remote/         # Firebase Auth & Firestore data sources
│   ├── repository/     # Repository implementations
│   ├── receiver/       # SMS broadcast receiver
│   ├── sync/           # RecurringExpenseWorker
│   └── preferences/    # DataStore (biometric, theme, SMS toggle)
├── domain/
│   ├── model/          # Domain models
│   ├── repository/     # Repository interfaces
│   └── usecase/        # Business logic (SMS import, export)
├── di/                 # Hilt modules
├── ui/
│   ├── navigation/     # Routes & NavGraph
│   ├── screen/         # All screens & ViewModels
│   └── theme/          # Material 3 theme, colors, typography
└── util/               # Notification helpers
```

## Build & Run

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumented tests
./gradlew clean                  # Clean build outputs
```

**Requirements:**
- Android Studio Ladybug or newer
- JDK 11+
- Min SDK 24 | Target SDK 36
- A `google-services.json` file in `app/` (from your Firebase project)

## Firebase Setup

1. Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com)
2. Enable **Authentication** providers: Email/Password, Google, Phone
3. Enable **Cloud Firestore**
4. Download `google-services.json` and place it in the `app/` directory
5. Add your debug SHA-1 fingerprint to the Firebase project settings
6. For Phone Auth: upgrade to the **Blaze plan** (free tier: 10 SMS/day)

## Permissions

| Permission | Purpose |
|-----------|---------|
| `INTERNET` | Firebase sync |
| `CAMERA` | Receipt scanning |
| `RECORD_AUDIO` | Voice expense input |
| `RECEIVE_SMS` / `READ_SMS` | Bank SMS auto-import |
| `POST_NOTIFICATIONS` | Reminders & alerts |

## License

All rights reserved.
