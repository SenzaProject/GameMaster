# Feature status (phase 1-5)

Nothing has been compiled or run on a device yet. This environment had no Android SDK, Kotlin compiler or network,
so the code was written and checked by reading only. The pure logic was also cross-checked against an independent
re-calculation of the numbers used in the unit tests. The build is verified by GitHub Actions on first push.
"Tested" below will only be set after a green CI run and a manual check on a phone.

| Feature | Status | Notes | Min Android |
|---|---|---|---|
| Splash, onboarding, permission setup | Implemented, untested | From phase 2 | 10 |
| Home dashboard | Implemented, untested | Real battery, RAM, storage, cores, game count, 3 most recently launched games, assistant status, running game and profile | 10 |
| Device monitor: CPU | Partial | Processor name, ABI, core count, Game Master's own CPU use (always). Whole-device load and per-core frequency only where Android lets apps read `/proc/stat` and cpufreq; otherwise "not available" | 10 |
| Device monitor: GPU | Partial | Renderer, vendor, OpenGL ES, Vulkan version from an off-screen EGL context and system features. Load and frequency only on some Qualcomm Adreno phones | 10 |
| Device monitor: RAM, storage | Implemented, untested | `ActivityManager` and `StatFs`; RAM history graph (in memory) | 10 |
| Device monitor: battery | Implemented, untested | Level, charging, power source, health, technology, temperature, voltage, current (sign differs per device) | 10 |
| Device monitor: thermal | Partial | Android thermal status and headroom (headroom needs Android 11). `/sys/class/thermal` zones only if readable | 10 (headroom 11) |
| Network assistant | Partial | Type, validation, metered, signal, Wi-Fi link speed and frequency, capacity estimate, device-wide traffic rate, TCP-connect latency test with min/avg/max/jitter and lost attempts. History is in memory only (not persisted) | 10 |
| FPS monitor: display test | Implemented, untested | Counts frames delivered to Game Master's own screen via `Choreographer`. Not a game measurement | 10 |
| FPS monitor: per-game | Partial, experimental | Reads `dumpsys SurfaceFlinger --latency` for a game chosen from the library. Needs the DUMP permission granted once over ADB. Not verified on any device or Android version | 10 |
| FPS in the floating panel and on the handle | Partial, experimental | Same SurfaceFlinger source as the FPS monitor, so it also needs DUMP granted over ADB. Without it the panel says so instead of showing a number | 10 |
| Game detection | Implemented, untested | PackageManager launcher query. Finds apps that declare themselves as games (category or legacy flag). Games that do not declare it must be added manually | 10 |
| Game library | Implemented, untested | Search, sort (4), filter (4), rescan, manual add, remove without uninstalling, launch, real icons | 10 |
| Game launch | Implemented, untested | Uses the app's launch intent. Failure is reported in a snackbar. Count and time recorded only on success | 10 |
| Game details | Implemented, untested | Real version and install dates from PackageManager, launch count, profile summary | 10 |
| Per-game settings | Implemented, untested | Profile is stored in Room and editable. Applied when the game comes to the front while the assistant runs: brightness, Do Not Disturb, preferred mode, session tracking, floating panel switch | 10 |
| Room database | Implemented, untested | `games`, `game_profiles`, `game_sessions`, `dock_apps` (version 2, migration 1 to 2, no destructive fallback). Schema exported to `app/schemas` on first build: commit it | 10 |
| Settings, About, EN/ID language | Implemented, untested | | 10 |
| Unit tests | Written, not run | `MetricsTest`, `LibrarySyncTest`, `GameQueryTest`, `MonitoringMathTest`, `FpsMathTest`, `SurfaceFlingerSamplerTest`, and for phase 5 `SessionTrackerTest`, `AssistantModelsTest`, `OverlayGeometryTest`, `StatsMathTest` | n/a |
| Instrumented DB tests | Written, not run | `GameDaoTest`, `AssistantDatabaseTest`, `MigrationV1ToV2Test` | 10 |
| Instrumented tests for monitoring repositories | Not written | CPU, GPU, thermal, network and ping repositories call Android APIs and need a device | |
| Game session detection | Implemented, untested | Foreground app from `UsageStatsManager` events every 2 to 3 s while the assistant runs. Needs Usage access. A break under 30 s does not split a session | 10 |
| Game modes (Game mode screen) | Implemented, untested | Power saving, Balanced, Performance, Custom. Each applies only: brightness, Do Not Disturb, media volume, keep screen awake, panel refresh interval. Originals saved to disk first and restored when the session ends or after a crash | 10 |
| Floating panel | Partial | Edge handle (drag, snap, style, size, opacity), panel with Monitor, Control, Quick and Dock tabs, closes on outside tap. FPS needs ADB. Nothing verified on a device. Behaviour over games with special window handling is unknown | 10 |
| Panel editor | Implemented, untested | Position, handle, panel size and opacity, readings, shortcut order and visibility, behaviour. Saved at once and picked up live | 10 |
| Game dock | Implemented, untested | Up to 9 apps, no duplicates, drag to reorder, saved in Room | 10 |
| Quick controls | Partial | Wi-Fi and display settings, Do Not Disturb, brightness, volume, network test (TCP connect time), game mode and toolbox shortcuts: implemented. Screenshot needs the accessibility service the user switches on. Screen recording is video only and needs a confirmation each time | 10 |
| Toolbox | Implemented, untested | Brightness, volume, Do Not Disturb, touch vibration, screenshot, screen recording, shortcuts, gaming timer (inexact alarm, lost on reboot) | 10 |
| Statistics | Implemented, untested | Play time, sessions, average, per game, daily chart for 7 or 30 days or all time, clear. Counts only time with the assistant running | 10 |
| Start after reboot | Implemented, untested | Optional. Restarts the assistant if it was on | 10 |
| Root integration (root FPS, GPU load, kernel readings) | Not built | Phase 6 | |

## Where each reading comes from

| Reading | Source | Limitation |
|---|---|---|
| CPU load, device | `/proc/stat` | Blocked for apps on Android 8+ on most phones, shown as not available |
| CPU load, app | `Process.getElapsedCpuTime()` | Only Game Master itself |
| Core frequency | `/sys/devices/system/cpu/cpuN/cpufreq/scaling_cur_freq` | Often unreadable |
| GPU model | EGL off-screen context, `glGetString` | None known |
| GPU load, frequency | `/sys/class/kgsl/kgsl-3d0/` | Qualcomm Adreno only, often unreadable |
| RAM, storage | `ActivityManager.MemoryInfo`, `StatFs` | None |
| Battery | `ACTION_BATTERY_CHANGED`, `BatteryManager` | Current sign differs between devices |
| Thermal status, headroom | `PowerManager` | Headroom needs Android 11 and may be NaN |
| Network state, signal | `ConnectivityManager`, `NetworkCapabilities`, `WifiManager` | Capacity is an estimate; SSID is not read |
| Traffic rate | `TrafficStats` | May be unsupported |
| Latency | TCP connect time to 1.1.1.1:443 or 8.8.8.8:443 | Not ICMP, not in-game ping |
| FPS (display test) | `Choreographer` | Own screen only |
| FPS (game) | `dumpsys SurfaceFlinger --latency` | DUMP over ADB, experimental |

## Known limitations

- Android does not let ordinary apps set CPU or GPU frequency; Game Master will not claim to.
- Without root or ADB there is no way for an ordinary app to read another app's frame rate. The display test is the only
  FPS reading available to everyone and it measures Game Master's own screen.
- "Recently launched" and launch counts only include launches made through Game Master. Play time in Statistics comes from detected sessions and needs Usage access and a running assistant.
- Auto-detection depends on the app or its installer declaring the game category. Undeclared games are added manually.
- The app sees launchable apps through a `<queries>` entry. It does not request QUERY_ALL_PACKAGES.
- New permissions in phase 4: INTERNET (latency test), ACCESS_WIFI_STATE (Wi-Fi link speed) and DUMP (declared only; it is never requested at runtime and is granted over ADB).
- Targets SDK 35. Raise when a newer stable SDK is available to the build.
- No Gradle wrapper jar is included; use Gradle 8.9 or Android Studio.

## Phase 5 limitations

- No mode changes CPU or GPU speed, and none claims to. Modes remove interruptions and manage brightness, volume and screen timeout.
- Brightness and touch vibration need "Modify system settings". Do Not Disturb needs Do Not Disturb access. If a permission is missing, that one setting is skipped and the panel says so.
- In "Only in games" mode the handle appears for every library game unless its per-game "Floating panel" switch was turned off. Without Usage access the handle stays visible all the time and no sessions run.
- The screenshot shortcut works through an accessibility service that only presses the system screenshot action. Android 13+ can block it for apps installed outside a store until "Allow restricted settings" is chosen in App info.
- Screen recording has no sound and Android asks for confirmation every time. The handle or an open panel may appear in the video.
- The latency figure is TCP connect time to 1.1.1.1:443, not in-game ping.
- Keep screen awake is a window flag on the overlay, so it only works while the overlay window is on screen.
- Game launches from the panel rely on the overlay permission exempting the app from background-activity-start limits. Android versions differ here.
- Gaming timer uses an inexact alarm (no exact-alarm permission): late in Doze, lost on reboot.
- New permissions in phase 5: FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE, FOREGROUND_SERVICE_MEDIA_PROJECTION, WRITE_SETTINGS, RECEIVE_BOOT_COMPLETED.
