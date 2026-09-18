# CLAUDE.md

Guidance for AI agents working in this repository.

## Build & Test Commands

```
./gradlew assembleDebug       # Build debug APK
./gradlew assembleRelease     # Build release APK (minified, proguard)
./gradlew installDebug        # Build + install on connected device/emulator
./gradlew test                # Run JVM unit tests (module currently has none)
./gradlew connectedAndroidTest # Run instrumented tests (module currently has none)
./gradlew lint                # Run Android lint
```

On Windows, `gradlew.bat` should be used instead of `./gradlew` when running outside
a POSIX shell. Java home is auto-detected in `build.gradle.kts` (Android Studio JBR).

## Architecture

- **Single-module app** (`:app`, package `com.petar.smrdici`), no `feature`/`core`
  modules — everything lives under `app/src/main/java/com/petar/smrdici`.
- **MVVM**: Jetpack Compose screens (`ui/screens/<feature>/XyzScreen.kt`) paired with a
  `ViewModel` (`XyzViewModel.kt`) exposing `StateFlow` for UI state.
- **No DI framework** (no Hilt/Koin). Repositories are hand-rolled singletons
  (`private constructor()` + `companion object { getInstance() }`), and ViewModels that
  need constructor args use a manual `ViewModelProvider.Factory` nested class. A few
  ViewModels have unused `@Inject`-annotated constructors left over — don't assume Hilt
  is wired up anywhere; it isn't.
- **Backend**: Firebase Firestore (primary data store, via `addSnapshotListener` real-time
  listeners) + Firebase Auth + FCM. There is no Room/local DB and no working Retrofit
  usage despite the dependency being declared (`WeatherService` uses raw
  `HttpURLConnection`).
- Navigation is a single `NavGraph.kt` using Compose Navigation (`NavHost`/`composable`),
  with routes centralized in `Screen.kt`.

## Code Style & State Management Conventions

- Comments and log messages are frequently in **Serbian/Cyrillic** — match the existing
  language when editing nearby code rather than switching everything to English.
- State exposure pattern: private `MutableStateFlow` + public `StateFlow` (or
  `Flow`/`asStateFlow()`) property of the same name without the leading underscore.
  **`LiveData` is not used anywhere** — stick to `StateFlow`.
- Repositories are Firestore-backed singletons (`getInstance()`), not injected — obtain
  them via `RepositoryManager` or the repository's own `getInstance()`, not `new`/direct
  construction, so app-wide listeners aren't duplicated.
- Logging goes through `utils/LogUtils` (categorized, level-aware) rather than raw
  `Log.d`/`Log.e` in feature code — pass a `category` (e.g. `"expense"`, `"finance"`).
- `ThreeTenABP` is used for `java.time` backport (minSdk 24) — don't add `java.time`
  usage assuming desugaring; use ThreeTen types where date/time handling matters.

## Safety Rules

- Never commit real API keys/secrets into `util/ApiKeys.kt` beyond what's already there;
  flag any new key requirement to the user instead of hardcoding it silently.
- Don't introduce Room, Hilt, or Koin without discussing it — the app intentionally
  avoids a DI framework and local persistence today.
- Don't replace `StateFlow` usages with `LiveData`, and don't add new `LiveData` state.
- Be careful editing `SmrdiciApplication.kt` init order — Firebase, ThreeTenABP,
  repositories, and notifications initialize in a specific sequence other code depends on.
- There are currently no unit/instrumented tests in `app/src/test` or
  `app/src/androidTest` — don't assume test coverage exists for a feature before changing it.
