# 🏠 Smrdici — Family Organizer App
Smrdici is a single-module Android application designed to streamline family management and household organization. Built using Jetpack Compose and Firebase, it offers real-time synchronization across devices for shared events, task lists, account finances, and scheduled notifications.

## 📸 Screenshots

| Home (Light) | Home (Dark) | Finance (Light) | Finance (Dark) |
| :---: | :---: | :---: | :---: |
| <img height="360" src="https://github.com/user-attachments/assets/c4a4c7ad-b6f6-42b6-b4c1-e9b25e8a66d0" /> | <img height="360" src="https://github.com/user-attachments/assets/c4eed612-1c31-4a4f-9de9-e01866df127f" /> | <img height="360" src="https://github.com/user-attachments/assets/886a847f-911b-4d93-854a-0736632035c6" /> | <img height="360" src="https://github.com/user-attachments/assets/30b53484-d0a1-4e4b-a6d0-ead43a88a741" /> |

| Calendar (Light) | Calendar (Dark) | Lists (Light) | Lists (Dark) |
| :---: | :---: | :---: | :---: |
| <img height="360" src="https://github.com/user-attachments/assets/13d06bdd-49b3-46b3-b07b-023569dcf9d3" /> | <img height="360" src="https://github.com/user-attachments/assets/a9f7792e-1020-4456-a39d-fca0766f7860" /> | <img height="360" src="https://github.com/user-attachments/assets/561cafcf-8c8f-41b9-9165-3b587436fae5" /> | <img height="360" src="https://github.com/user-attachments/assets/f977c171-df59-467d-bb10-8b6063822d5e" /> |

## ✨ Features

### 🏠 Home Dashboard: 

Immediate overview of active and upcoming events, with one-tap access to main modules. Automatically filters out past events.

### 📅 Calendar:
Monthly view supporting all-day or timed events, custom color tags, date-based event details, and full CRUD operations.

### 📝 Shared Lists:
Interactive task and shopping lists with reorderable drag-and-drop items, item completion tracking, and management capabilities.

### 💰 Finance Tracker & OCR:
Expense and income management across multiple accounts.
Account-to-account transfers and category customization.
Receipt Scanning: Built-in MLKit OCR engine for scanning receipts into structured expenses.

### 👤 Profile & Preferences:
Account details, notification toggles, and dynamic dark/light theme switching.

### 🔔 Smart Notifications:
Local scheduled reminders (daily summary at 8:00 AM), boot receivers for persistent alarms, and FCM push notifications.

## 🛠️ Tech Stack & Libraries
Language: Kotlin
UI & Architecture: Jetpack Compose (Material 3), MVVM, StateFlow, Navigation Compose
Backend & Sync: Firebase Firestore (Real-time DB), Firebase Authentication, Firebase Cloud Messaging (FCM)
Background Tasks & Scheduling: Android WorkManager, AlarmManager, Broadcast Receivers
Text Recognition: MLKit Text Recognition (Receipt OCR)
Utilities: ThreeTenABP (Backported Java Time API), Reorderable (Drag-and-drop UI)

### 🔄 App Architecture & Lifecycle
The app initializes global dependencies, Firebase services, and repository singletons at startup through SmrdiciApplication before routing through MainActivity.
```
[ AndroidManifest.xml ]
          │
          ▼
[ SmrdiciApplication.onCreate() ]
   ├── 1️⃣ AndroidThreeTen.init()           ──► Java Time API support
   ├── 2️⃣ initLogging()                    ──► Categorized debug logging
   ├── 3️⃣ initializeFirebase()             ──► FirebaseApp & Auth initialization
   ├── 4️⃣ initGooglePlayServices()        ──► Availability checks
   ├── 5️⃣ initializeRepositories()        ──► Singleton repos with Firestore listeners
   └── 6️⃣ initializeNotifications()       ──► Daily 8:00 AM local reminder setup
          │
          ▼
[ MainActivity ]
   ├── Request runtime permissions (Android 13+ POST_NOTIFICATIONS)
   ├── AuthViewModel setup
   └── NavGraph initialization
          │
          ▼
[ Feature Screens ] ◄── StateFlow ──► [ ViewModels ] ◄──► [ RepositoryManager ] ◄──► [ Firestore ]
```
## 📁 Repository Structure
```
app/src/main/
├── AndroidManifest.xml           # App manifest, receivers, FCM service, permissions
│
├── java/com/petar/smrdici/
│   ├── SmrdiciApplication.kt    # Application entry point & service initialization
│   │
│   ├── data/                    # Data layer
│   │   ├── model/               # Data classes (Event, Transaction, Account, Categories)
│   │   └── repository/          # Singleton repositories (Firestore listeners + StateFlow)
│   │
│   ├── notification/            # FCM service, daily notification workers & receivers
│   │
│   ├── ui/                      # Presentation layer
│   │   ├── MainActivity.kt      # Main host activity
│   │   ├── components/          # Reusable Compose UI widgets & layouts
│   │   ├── navigation/          # Sealed screen routes & NavGraph
│   │   ├── screens/             # Feature UI (Auth, Home, Calendar, Finance, Lists, Settings)
│   │   └── theme/               # Material3 theme setup & ThemeViewModel
│   │
│   └── util/ / utils/           # Global helpers, currency/date utilities, logging
│
└── res/                         # Drawables, values, and XML security configs
```
### 🚀 Getting Started
#### Prerequisites

Android Studio (Jellyfish / Koala or newer recommended)
JDK 17 or higher
Android API Level 24+ (Android 7.0 Nougat)

#### Setup Instructions
Clone the repository:

```
git clone https://github.com/your-username/smrdici.git
cd smrdici
```

#### Add Firebase Configuration:

Create a project on the Firebase Console.
Register an Android app with package name com.petar.smrdici.
Download the generated google-services.json and place it in the app/ directory (app/google-services.json).

#### Build & Run:

Open the project in Android Studio.
Let Gradle sync dependencies.
Run the :app module on an emulator or physical device.

### ⚙️ Build Commands
Execute these commands from the root directory:

Linux / macOS:

```
./gradlew assembleDebug    # Build Debug APK
./gradlew installDebug     # Build and install Debug APK on connected device
./gradlew lint             # Run Android Lint analysis
```

Windows (Command Prompt / PowerShell):
```
DOS
gradlew.bat assembleDebug
gradlew.bat installDebug
```
