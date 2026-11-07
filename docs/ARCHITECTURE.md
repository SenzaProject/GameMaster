# Architecture

Kotlin, Jetpack Compose, Material 3, MVVM, Hilt, Coroutines/Flow, DataStore. Room is used from phase 3. WorkManager arrives with session tracking.

## Layout

One Gradle module (`:app`) with packages that mirror the planned modules:

```
core/common        pure Kotlin helpers (unit-tested)
core/database      Room entities, DAOs, database and Hilt module
core/gaming        game detection, library sync, query, launcher, repository, icon loader
core/designsystem  theme and reusable components
core/monitoring    device readings from documented Android APIs
core/permissions   permission state and settings intents
core/settings      DataStore-backed preferences
feature/*          one package per screen group
navigation         routes, feature catalog, root scaffold
```

The packages are split by responsibility so they can be promoted to separate Gradle modules later.
Splitting now would add build complexity without any current benefit.

## Rules

- No simulated data. A missing reading is `null` and the UI says "not available".
- `FeatureCatalog` is the single source of truth for what is built. Unbuilt screens show a plain status page.
- Sampling flows use `WhileSubscribed(5s)` so monitoring stops when the screen is not visible.
- Only permissions used by the Permission Setup screen are declared.

## Phase 3 data flow

```
PackageManager -> InstalledAppSource -> LibrarySync.plan (pure) -> Room (games)
Room Flow -> GameRepository -> ViewModel (applyQuery, pure) -> Compose
```

- `LibrarySync` and `applyQuery` have no Android dependencies and are unit-tested on the JVM.
- Games use `@Upsert`, never REPLACE, because REPLACE deletes the row and cascades to its profile.
- "Remove from library" sets `hidden = true`, so a rescan does not bring the game back and its profile survives.
- An empty scan result is treated as a failed scan and never deletes games.
- There is no destructive migration fallback. A schema change needs a real migration.

## Phase 4 monitoring

```
Android APIs / files -> *Repository (Flow, flowOn IO) -> ViewModel (stateIn WhileSubscribed) -> Compose
```

- `core/monitoring/MonitoringMath.kt` and `FpsMath.kt` are pure Kotlin (parsers, percentages, ping statistics, frame windows) and are unit-tested on the JVM.
- Each reading has its own repository and flow, so a screen only pays for what it shows. Sampling stops 5 seconds after the screen leaves the foreground.
- `historyOf` keeps a bounded list of recent values for graphs. History is in memory only.
- `ShellRunner` is the single place a command is executed. `ProcessShellRunner` runs as the app's own user. A root runner can replace it in phase 6.
- `SurfaceFlingerSampler` depends only on `ShellRunner`, so it is tested with a fake.
- FPS and latency measurements stop when the screen is left or the app goes to the background.

## Phase 5: in-game assistant

```
FloatingPanelService (foreground, started by the user)
 ├─ ForegroundAppDetector  UsageStatsManager events -> foreground package   (core/assistant)
 ├─ SessionTracker         pure state machine: start, grace period, end      (core/assistant)
 ├─ GameSessionManager     writes sessions to Room, applies and restores settings
 │     └─ SystemSettingsController   the only code that writes real system settings
 ├─ AssistantPrefsRepository  DataStore: overlay layout, mode configs, restore snapshot
 └─ OverlayController      one WindowManager window, always as small as what it shows
       ├─ OverlayRootView        raw-coordinate drag for the handle, outside-touch to close
       └─ OverlayContent         Compose UI: handle + panel (Monitor, Control, Quick, Dock)
```

- Settings are read and saved to disk **before** they are changed, and restored when the session ends, when the assistant stops, or on the next start after a crash.
- The overlay window is sized to its content, so touches elsewhere go to the game and Android's untrusted-touch blocking never applies.
- Panel readers are started per visible widget inside a `LaunchedEffect`, so they stop when the panel or tab closes.
- Pure logic (`SessionTracker`, `resolveEffective`, `OverlayGeometry`, dock rules, `summarizeSessions`) has no Android dependency and is unit-tested.
- Screenshot goes through `GameMasterAccessibilityService` (system action only). Recording uses `ScreenCaptureConsentActivity` then `ScreenRecordService` (MediaProjection + MediaRecorder).
