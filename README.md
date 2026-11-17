# PHONE GUARD PRO

Native Android device-diagnostics, battery-analysis, benchmark and safe-optimization app.
Kotlin · Jetpack Compose · Material 3 · MVVM · minSdk 29 (Android 10) · targetSdk 35.

Every number on screen is either read from an Android API or measured by the app, and is
labelled with its source: **System-reported**, **Measured**, **Calculated**, **Estimated** or
**Unavailable**. Nothing is simulated.

---

## 1. Project structure

```
PhoneGuardPro/
├── settings.gradle.kts, build.gradle.kts, gradle.properties
├── gradle/wrapper/gradle-wrapper.properties      (Gradle 8.9)
└── app/
    ├── build.gradle.kts                          (AGP 8.6.1, Kotlin 2.0.21, Compose BOM 2024.10)
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/ (theme, adaptive icon, FileProvider paths)
        └── java/com/phoneguard/pro/
            ├── PhoneGuardApp.kt, MainActivity.kt
            ├── di/AppContainer.kt                 manual DI container
            ├── util/Format.kt                     formatting + safe sysfs reads
            ├── data/
            │   ├── model/Models.kt                all data classes, DataSource labels
            │   ├── diagnostics/                   CPU, RAM, storage, display, device,
            │   │                                  sensors (+live readings), network
            │   │                                  (+latency test), thermal
            │   ├── battery/BatteryAnalyzer.kt     battery snapshot + discharge analysis
            │   ├── benchmark/                     BenchmarkEngine, scoring refs, history
            │   ├── optimization/                  OptimizationAnalyzer, RecommendationEngine
            │   └── report/                        HealthScorer, HealthReportBuilder, PDF
            └── ui/
                ├── theme/Theme.kt                 black / blue / cyan Material 3 theme
                ├── components/Components.kt       cards, animated gauge, line chart, bars
                ├── navigation/                    NavHost, bottom bar, system intents
                ├── viewmodel/                     one ViewModel per screen (StateFlow)
                └── screens/                       Dashboard, Diagnostics, TouchTest,
                                                   Battery, Benchmark, Optimization, Report
```

Architecture: **UI (Compose) → ViewModel (StateFlow) → data providers (Android APIs)**.
Live screens use `stateIn(WhileSubscribed)` so polling stops automatically when a screen is not visible.

## 2. Features

| Module | What it does (real implementation) |
|---|---|
| Dashboard | Live health score, RAM/storage/battery/thermal tiles, live RAM chart, per-core frequency bars |
| Diagnostics | Device/build, CPU model/arch/cores/clusters/frequencies/governor, RAM, storage, display (resolution, refresh rates, HDR), network (type, Wi-Fi signal/link/band/standard, IP, DNS), DNS + TCP latency test, thermal status/headroom/zones, all sensors with live readings |
| Touch test | Full-screen grid; marks every cell the digitizer reports, shows coverage %, max simultaneous touches, dead zones in red |
| Battery | Level, status, plug type, chemistry, health flag, temperature, voltage, current, power, charge counter, energy, design capacity, estimated full capacity, estimated health %, cycle count (Android 14+), charging class, time-to-full, discharge rate & time-left analysis with charts, per-app activity |
| Benchmark | CPU single/multi integer (sieve) + floating point (matrix multiply), memory copy bandwidth + random-access latency, storage sequential R/W + random 4K R/W (fsync), temperature/thermal monitoring with automatic abort, score /100, history chart |
| Optimization | Rule-based recommendations, most-used apps, background (foreground-service) time, largest apps, large caches, unused 30+ days apps, large media files with system-confirmed deletion, own-cache cleaning, shortcuts to the right system settings |
| Report | Overall + per-component scores (CPU, RAM, storage, battery, temperature), issues, recommendations, full device data; export as PDF (share or save to Downloads) |

## 3. Build & install

### Requirements
- Android Studio Ladybug (2024.2) or newer, with Android SDK 35 installed
- JDK 17 (bundled with Android Studio)

### Android Studio (recommended)
1. **File → Open** → select the `PhoneGuardPro` folder.
2. Let Gradle sync. If Studio asks to create the Gradle wrapper, accept (the wrapper JAR is not
   included in this source package; Studio downloads Gradle 8.9 automatically).
3. Connect a phone with USB debugging enabled (Settings → About phone → tap *Build number* 7×,
   then Developer options → USB debugging), or start an emulator (API 29+).
4. Press **Run ▶**.

### Command line
```bash
cd PhoneGuardPro
gradle wrapper --gradle-version 8.9      # once, if gradlew is missing (needs Gradle installed)
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 4. Generating a release APK

1. Create a signing key (once):
   ```bash
   keytool -genkeypair -v -keystore phoneguard-release.jks -keyalg RSA -keysize 2048 \
           -validity 10000 -alias phoneguard
   ```
2. Create `keystore.properties` in the project root (it is git-ignored):
   ```properties
   storeFile=phoneguard-release.jks
   storePassword=YOUR_STORE_PASSWORD
   keyAlias=phoneguard
   keyPassword=YOUR_KEY_PASSWORD
   ```
3. Build:
   ```bash
   ./gradlew assembleRelease          # → app/build/outputs/apk/release/app-release.apk
   ./gradlew bundleRelease            # → .aab for Google Play
   ```
   Or in Android Studio: **Build → Generate Signed App Bundle / APK…**

Release builds are minified with R8 and resources are shrunk.

## 5. Permissions & privacy

| Permission | Why | When |
|---|---|---|
| `INTERNET` | Connectivity/latency test only | Only when the user taps the test |
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Network diagnostics | Normal permissions |
| `PACKAGE_USAGE_STATS` | Per-app usage, background time, app sizes | User grants manually in *Usage access* settings |
| `READ_MEDIA_*` / `READ_EXTERNAL_STORAGE` (≤ API 32) | Find large media files | Requested only from the Optimize tab |

- No location permission (so Wi-Fi SSID is hidden by design).
- No `QUERY_ALL_PACKAGES` — uses a `<queries>` launcher intent instead.
- `allowBackup=false`; benchmark history stays in private SharedPreferences.
- No analytics, no network upload. PDFs leave the device only if the user shares them.
- No background services: monitoring happens only while a screen is open.

## 6. Android API restrictions (documented honestly)

| Wanted | Reality on Android 10+ | What the app does |
|---|---|---|
| System-wide CPU usage % | `/proc/stat` blocked for apps since Android 8 (SELinux) | Shows per-core current ÷ max frequency, labelled as a frequency indicator, **not** usage |
| CPU frequencies, thermal zones | sysfs readable on many devices, blocked on others | Shown when readable; otherwise "not exposed" |
| Battery state-of-health % | `BATTERY_PROPERTY_STATE_OF_HEALTH` is system-only | Estimate = charge counter ÷ level vs design capacity, labelled **Estimated** |
| Design capacity | No public API | Read from the framework power profile via reflection (unofficial; may be generic or blocked) |
| Cycle count | Public only from Android 14 and only if the HAL reports it | Shown when available |
| Charge current | Units/sign vary by vendor | Normalised to absolute mA; heuristic documented in code |
| Fast-charge protocol (PD/QC/VOOC…) | Not exposed | Charging *class* estimated from measured watts |
| Per-app battery drain (mAh) | `BATTERY_STATS` is a system permission | Shows screen time + foreground-service time as a proxy, and opens the system battery-usage screen |
| Clearing other apps' cache | Impossible for normal apps since Android 6 | Clears own cache; opens each app's settings page where the user can clear it |
| Killing background apps | Not allowed / harmful | Never done; recommendations explain why |
| "Boosting" CPU | Not possible without root | Never claimed. Animation-scale tip is explicitly labelled as perceived speed only |
| Deleting user media | Requires user consent on Android 11+ | Uses `MediaStore.createDeleteRequest` (system confirmation dialog) |
| Storage read benchmark | Apps cannot drop the OS page cache | Read results labelled "may include OS cache" |
| Wi-Fi SSID | Requires location permission | Not requested; shown as hidden |

## 7. Benchmark scoring

Scores are computed on a logarithmic scale against reference values of a typical 2022 mid-range
phone (`BenchmarkReferences` in `BenchmarkEngine.kt`): equal to reference = 50, 2× = 75, 4× = 100,
½× = 25. Overall = 30% CPU single + 25% CPU multi + 20% memory + 25% storage.
Raw measurements are always shown. Scores are internal and not comparable with Geekbench/AnTuTu.
To calibrate, run on devices you know and adjust the reference constants.

## 8. Health score

Weighted average (CPU 20%, RAM 20%, storage 20%, battery 25%, temperature 15%) of the components
that have data; components without data (e.g. CPU before the first benchmark) are excluded rather
than guessed. Rules are in `HealthScorer.kt`.

## 9. Testing checklist

- Dashboard updates every 2 s; leaving the tab stops polling.
- Diagnostics → tap a sensor (e.g. accelerometer) and tilt the phone: values change live.
- Run the touch test with several fingers; untouched cells turn red after *Finish*.
- Battery: plug/unplug a charger and watch status, current direction and charging class.
- Benchmark: run twice; the history chart appears; cancel works mid-run.
- Optimize: grant Usage Access → app lists populate; grant media → large files list; delete shows system dialog.
- Report: Export PDF & share → open in a PDF viewer; Save to Downloads → file appears in Files app.
