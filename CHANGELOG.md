# Changelog

## 0.5.0 (unreleased)
- In-game assistant: foreground service, floating edge handle and panel (Monitor, Control, Quick, Dock tabs), draggable with edge snapping, closes on outside tap.
- Game sessions from UsageStatsManager events, saved in Room, with crash recovery. Per-game profiles and game modes are now applied: brightness, Do Not Disturb, media volume, keep screen awake. Originals are saved first and restored afterwards.
- Game mode screen (Power saving, Balanced, Performance, Custom), Panel editor, Game dock (9 apps, drag to reorder), app picker.
- Toolbox: controls, screenshot (accessibility service), screen recording (MediaProjection), shortcuts, gaming timer.
- Statistics screen with play time, sessions, per-game totals and a daily chart.
- Database version 2 with a tested migration from version 1.
- Home shows assistant status, current game and profile.
- New permissions: FOREGROUND_SERVICE (+ special use, media projection), WRITE_SETTINGS, RECEIVE_BOOT_COMPLETED.
- Unit tests for sessions, configuration rules, overlay geometry, dock rules and statistics; instrumented tests for the new tables and the migration.

## 0.3.0 (unreleased)
- Device monitor rebuilt with CPU, GPU, memory (with history graph), storage, battery and thermal sections. Every row is a real reading or "not available".
- Network assistant: connection details, Wi-Fi link speed, signal, device-wide traffic rate and history, TCP-connect latency test with jitter and lost attempts.
- FPS monitor: display test (Choreographer) and an experimental per-game source (SurfaceFlinger, needs DUMP granted over ADB).
- Native Compose sparkline and meter components.
- New permissions: INTERNET, ACCESS_WIFI_STATE, DUMP (declared only, granted over ADB).
- Unit tests for the monitoring maths, FPS maths and the SurfaceFlinger sampler.

## 0.2.0 (unreleased)
- Room database with games and per-game profiles.
- Game detection through PackageManager, with Android 11+ package visibility handled by a `<queries>` entry.
- Game library: search, sort, filter, rescan, manual add, remove without uninstalling, launch, real icons.
- Game details screen and per-game settings screen (stored, not yet applied to games).
- Home shows game count and recently launched games.
- Unit tests for library sync and query logic; instrumented tests for the DAOs.

## 0.1.0 (unreleased)
- Project scaffold: Hilt, Compose, Material 3, DataStore, navigation.
- Splash, onboarding, permission setup, home, device monitor (partial), settings, about.
- English and Indonesian strings.
- GitHub Actions build with tests, checksums and optional signed release.
