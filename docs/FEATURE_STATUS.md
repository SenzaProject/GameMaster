# Feature status (phase 1-4)

Nothing has been compiled or run on a device yet. This environment had no Android SDK, Kotlin compiler or network,
so the code was written and checked by reading only. The pure logic was also cross-checked against an independent
re-calculation of the numbers used in the unit tests. The build is verified by GitHub Actions on first push.
"Tested" below will only be set after a green CI run and a manual check on a phone.

| Feature | Status | Notes | Min Android |
|---|---|---|---|
| Splash, onboarding, permission setup | Implemented, untested | From phase 2 | 10 |
| Home dashboard | Partial | Real battery, RAM, storage, cores, game count, 3 most recently launched games. No gaming profile or active mode yet | 10 |
| Device monitor: CPU | Partial | Processor name, ABI, core count, Game Master's own CPU use (always). Whole-device load and per-core frequency only where Android lets apps read `/proc/stat` and cpufreq; otherwise "not available" | 10 |
| Device monitor: GPU | Partial | Renderer, vendor, OpenGL ES, Vulkan version from an off-screen EGL context and system features. Load and frequency only on some Qualcomm Adreno phones | 10 |
| Device monitor: RAM, storage | Implemented, untested | `ActivityManager` and `StatFs`; RAM history graph (in memory) | 10 |
| Device monitor: battery | Implemented, untested | Level, charging, power source, health, technology, temperature, voltage, current (sign differs per device) | 10 |
| Device monitor: thermal | Partial | Android thermal status and headroom (headroom needs Android 11). `/sys/class/thermal` zones only if readable | 10 (headroom 11) |
| Network assistant | Partial | Type, validation, metered, signal, Wi-Fi link speed and frequency, capacity estimate, device-wide traffic rate, TCP-connect latency test with min/avg/max/jitter and lost attempts. History is in memory only (not persisted) | 10 |
| FPS monitor: display test | Implemented, untested | Counts frames delivered to Game Master's own screen via `Choreographer`. Not a game measurement | 10 |
| FPS monitor: per-game | Partial, experimental | Reads `dumpsys SurfaceFlinger --latency` for a game chosen from the library. Needs the DUMP permission granted once over ADB. Not verified on any device or Android version | 10 |
| FPS overlay | Not built | Part of the floating panel (phase 5) | |
| Game detection | Implemented, untested | PackageManager launcher query. Finds apps that declare themselves as games (category or legacy flag). Games that do not declare it must be added manually | 10 |
| Game library | Implemented, untested | Search, sort (4), filter (4), rescan, manual add, remove without uninstalling, launch, real icons | 10 |
| Game launch | Implemented, untested | Uses the app's launch intent. Failure is reported in a snackbar. Count and time recorded only on success | 10 |
| Game details | Implemented, untested | Real version and install dates from PackageManager, launch count, profile summary | 10 |
| Per-game settings | Partial | Profile is stored in Room and editable. **Not applied to games yet**: applying it needs session detection (phase 5) | 10 |
| Room database | Implemented, untested | `games` and `game_profiles`; schema exported to `app/schemas` on first build | 10 |
| Settings, About, EN/ID language | Implemented, untested | | 10 |
| Unit tests | Written, not run | `MetricsTest`, `LibrarySyncTest`, `GameQueryTest`, `MonitoringMathTest`, `FpsMathTest`, `SurfaceFlingerSamplerTest` | n/a |
| Instrumented DB tests | Written, not run | `GameDaoTest` | 10 |
| Instrumented tests for monitoring repositories | Not written | CPU, GPU, thermal, network and ping repositories call Android APIs and need a device | |
| Game mode, toolbox, floating panel, statistics | Not built | Phase 5 | |
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
- "Recently launched" and launch counts only include launches made through Game Master. Real play time needs Usage access and arrives in phase 5.
- Auto-detection depends on the app or its installer declaring the game category. Undeclared games are added manually.
- The app sees launchable apps through a `<queries>` entry. It does not request QUERY_ALL_PACKAGES.
- New permissions in phase 4: INTERNET (latency test), ACCESS_WIFI_STATE (Wi-Fi link speed) and DUMP (declared only; it is never requested at runtime and is granted over ADB).
- Targets SDK 35. Raise when a newer stable SDK is available to the build.
- No Gradle wrapper jar is included; use Gradle 8.9 or Android Studio.
