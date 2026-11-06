# Architecture

Kotlin, Jetpack Compose, Material 3, MVVM, Hilt, Coroutines/Flow, DataStore. Room and WorkManager arrive in phase 3.

## Layout

One Gradle module (`:app`) with packages that mirror the planned modules:

```
core/common        pure Kotlin helpers (unit-tested)
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
