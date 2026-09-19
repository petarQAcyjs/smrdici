# Smrdici

## What this is

Smrdici is a single-module Android family organizer app for tracking household
events, lists, finances, profiles, and notifications. It's built around Jetpack
Compose and Firebase, with a strong MVVM structure and Firestore-backed
repositories that keep the app state synced across screens.

## Features

### Home
- Overview of today's activities
- Quick access to the main sections of the app (Lists, Calendar, Finance)
- Filters out past events, showing only active and upcoming ones

### Calendar
- Monthly calendar view
- Add, edit, and delete events
- Detailed view of events for a selected date
- Support for all-day events
- Custom event colors
- Configurable start and end times

### Lists
- Create different kinds of lists (shopping, chores, etc.)
- Add, edit, and delete list items
- Mark items as completed

### Finance
- Track household expenses and income
- Manage multiple accounts
- Add and edit transactions (income and expenses)
- Categorize expenses and income
- Transfers between accounts
- Visual overview of financial data

### Profile & Settings
- Manage the user profile
- Customize the app
- Configure notifications

## Stack

- **Language(s):** Kotlin
- **Framework / runtime:** Android app with Jetpack Compose + MVVM, targeting Android API 24+
- **Notable libraries:** Firebase Firestore/Auth/FCM, Compose Navigation, WorkManager, ThreeTenABP, Material3, Reorderable drag-and-drop

## How it's organized

```
README.md               app overview and feature list
CLAUDE.md               repo-specific agent guidance
build.gradle.kts        top-level Gradle config, Java home setup
settings.gradle.kts     root project includes :app
app/
  build.gradle.kts      app module, Compose, Firebase, Android dependencies
  google-services.json  Firebase config
  src/main/
    AndroidManifest.xml
    java/com/petar/smrdici/
      data/             model classes and Firestore repositories
        model/          Event, Transaction, Account, categories, etc.
        repository/     RepositoryManager, singleton repos, sync listeners
      notification/     FCM, notification helpers, receivers, workers
      ui/               Compose screens and navigation
        navigation/     NavGraph.kt, Screen.kt
        screens/        home, calendar, finance, lists, profile, settings
        components/     reusable UI pieces
      util/             API keys, currency/date helpers
      utils/            logging and global app helpers
      SmrdiciApplication.kt app startup and initialization
    res/                drawables/icons/resources, theme assets
```

## How it fits together

The app boots from `SmrdiciApplication.kt`, initializes Firebase and
supporting services, and then renders screens through
`ui/navigation/NavGraph.kt`. Feature screens live under `ui/screens/...` and
connect to singleton repositories under `data/repository/`, which use
Firestore listeners to feed `StateFlow`-based ViewModels. Notifications and
background work are handled separately under `notification/`, while reusable
UI and helper code sits in `ui/components/` and `util/` + `utils/`.

## Building & running

```
./gradlew assembleDebug        # Build debug APK
./gradlew assembleRelease      # Build release APK (minified, proguard)
./gradlew installDebug         # Build + install on connected device/emulator
./gradlew lint                 # Run Android lint
```

On Windows, use `gradlew.bat` instead of `./gradlew` when running outside a
POSIX shell.

1. Clone the repository
2. Open the project in Android Studio
3. Sync Gradle files
4. Run the app on an emulator or physical device (or use the Gradle commands above)

## Roadmap

- Richer finance reports and charts
- Recurring events in the calendar
- Sharing lists and events with family members
- Improved notifications and event reminders
- Sync with Google Calendar and other calendar apps
- Themes and app appearance customization
- Performance and memory usage optimization
