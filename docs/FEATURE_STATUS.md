# Feature status (phase 1–2)

Nothing has been compiled or run on a device yet. The build is verified by GitHub Actions on first push.
"Tested" below will only be set after a green CI run and a manual check on a phone.

| Feature | Status | Notes | Min Android |
|---|---|---|---|
| Splash | Implemented, untested | Reads onboarding flag from DataStore | 10 |
| Onboarding | Implemented, untested | Flag saved before navigating | 10 |
| Permission setup | Implemented, untested | Real status checks for usage access, overlay, notifications, DND; refreshes on resume | 10 (notification prompt 13+) |
| Home dashboard | Partial | Real battery, RAM, storage, cores. No gaming profile, recent games or mode yet | 10 |
| Device monitor | Partial | Model, ABI, cores, refresh rate, battery, RAM, storage. CPU load, GPU, thermal: phase 4 | 10 |
| Settings | Implemented, untested | Keep screen on is applied to the window | 10 |
| About | Implemented, untested | Version from BuildConfig | 10 |
| EN / ID language | Implemented, untested | Follows system; per-app language on Android 13+ | 10 |
| Unit tests | Written, not run | `core/common/MetricsTest` | n/a |
| Game library, details, per-game settings | Not built | Phase 3 | |
| FPS monitor, network assistant | Not built | Phase 4 | |
| Game mode, toolbox, floating panel, statistics | Not built | Phase 5 | |
| Root integration | Not built | Phase 6 | |

## Known limitations

- Android does not let ordinary apps set CPU or GPU frequency; Game Master will not claim to.
- Targets SDK 35. Raise when a newer stable SDK is available to the build.
- No Gradle wrapper jar is included; use Gradle 8.9 or Android Studio.
