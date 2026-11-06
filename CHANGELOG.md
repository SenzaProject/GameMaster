# Changelog

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
