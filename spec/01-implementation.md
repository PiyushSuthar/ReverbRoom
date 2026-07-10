# ReverbRoom – Implementation Spec & Status

## App Overview

**ReverbRoom** is a real-time audio reverb and echo Android app. It captures audio from the microphone or imports audio files, processes them through a custom DSP pipeline (reverb + echo), and outputs the processed audio in real-time. Users can record the processed output and save it as WAV.

- **Package**: `com.example.reverbroom`
- **Application ID**: `com.reverbroom.app`
- **Version**: `1.0.0` (versionCode 1)
- **Min SDK**: 24 (Android 7.0)
- **Target/Compile SDK**: 36

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 (Material You) |
| Navigation | AndroidX Navigation3 |
| Architecture | MVVM (ViewModel + StateFlow) |
| Audio Capture | `AudioRecord` (mic input) |
| Audio Output | `AudioTrack` (real-time playback) |
| DSP Effects | Custom Schroeder reverb + delay-line echo (pure Kotlin, no native) |
| File Decode | `MediaExtractor` + `MediaCodec` (WAV, MP3, AAC) |
| File Encode | Manual WAV header writing |
| Build | Gradle with Kotlin DSL, version catalog (`libs.versions.toml`) |

---

## Project Structure

```
app/src/main/
├── AndroidManifest.xml          # Permissions: RECORD_AUDIO, storage, media
├── java/com/example/reverbroom/
│   ├── MainActivity.kt          # Entry point, edge-to-edge, theme wrapper
│   ├── Navigation.kt            # Navigation3 setup with NavDisplay
│   ├── NavigationKeys.kt        # @Serializable NavKey objects
│   ├── audio/
│   │   ├── AudioEngine.kt       # Central engine: mic/file → DSP → AudioTrack
│   │   ├── AudioFileHandler.kt  # Decode (MediaCodec) + encode (WAV)
│   │   └── effects/
│   │       ├── ReverbEffect.kt  # Schroeder reverb (4 comb + 2 allpass)
│   │       └── EchoEffect.kt    # Circular buffer delay line
│   ├── theme/
│   │   ├── Color.kt             # Extended palette (purple, audio greens, reds)
│   │   ├── Theme.kt             # Material You dynamic color + fallback
│   │   └── Type.kt              # Typography (default from template)
│   └── ui/main/
│       ├── MainScreen.kt        # Full Compose UI (visualizer, controls, sliders)
│       └── MainScreenViewModel.kt # MVVM: combines engine state + params + save
└── res/
    ├── drawable/
    │   ├── ic_launcher_background.xml  # Dark purple vector background
    │   └── ic_launcher_foreground.xml  # Concentric rings + sound wave vector
    ├── mipmap-anydpi-v26/
    │   ├── ic_launcher.xml             # Adaptive icon config
    │   └── ic_launcher_round.xml
    ├── values/
    │   ├── strings.xml                 # App name + permission rationale
    │   └── themes.xml
    └── xml/
        ├── backup_rules.xml
        └── data_extraction_rules.xml
```

---

## Architecture Details

### Audio Engine (`AudioEngine.kt`)

The engine operates in two modes:

1. **MICROPHONE**: `AudioRecord` → DSP pipeline → `AudioTrack` (real-time loop)
2. **FILE**: Decoded `ShortArray` → DSP pipeline → `AudioTrack` (chunked playback)

**Key specs**:
- Sample rate: 44100 Hz, mono, 16-bit PCM
- Processing thread priority: `THREAD_PRIORITY_URGENT_AUDIO`
- Buffer: `max(AudioRecord.getMinBufferSize(), 4096)`
- DSP pipeline: `short→float` → reverb → echo → `float→short` (with clamping)
- State exposed via `StateFlow<AudioEngineState>` (isPlaying, mode, levels, recording status)
- Recording captures post-effects buffer into a synchronized `MutableList<Short>`

### Effects

**ReverbEffect** (Schroeder reverb):
- 4 parallel comb filters (delays: ~1310, ~1636, ~1813, ~1927 samples — mutually prime)
- 2 series all-pass filters (delays: ~220, ~75 samples, gain 0.7)
- `decay` (0–1): controls comb filter feedback
- `mix` (0–1): dry/wet blend
- All parameters `@Volatile` for thread-safe real-time updates

**EchoEffect** (delay line):
- Circular buffer sized for max 1000ms (44100 samples)
- `delay` (0–1): maps to 0–1000ms
- `feedback` (0–1): feedback amount
- `mix` (0–1): dry/wet blend

### ViewModel (`MainScreenViewModel.kt`)

- Holds `AudioEngine` instance
- `MainUiState` combines: `AudioEngineState` + `EffectParams` + save status
- Uses `combine()` to merge three `StateFlow`s
- Caches decoded file samples (`cachedFileSamples`) for replay
- Caches recorded samples (`lastRecordedSamples`) for save flow
- File saving uses SAF (Storage Access Framework) via `CreateDocument` contract
- All IO work on `Dispatchers.IO`
- Releases engine in `onCleared()`

### UI (`MainScreen.kt`)

- **Audio Visualizer Card**: Animated sine waveform (phase-shifting), secondary echo wave, glow effect when playing, level meters (IN/OUT) with color-coded bars (green/amber/red)
- **Transport Controls Card**: Mic toggle, file open, play/stop file, record toggle, save button
- **Reverb Card**: Decay time slider (0–5s), wet/dry mix slider (0–100%)
- **Echo Card**: Delay slider (0–1000ms), feedback slider (0–100%), wet/dry mix slider (0–100%)
- **Reset button**: Resets all effects to defaults
- **Save dialog**: Offers WAV export via system file picker
- **Permission handling**: Runtime `RECORD_AUDIO` permission via `ActivityResultContracts`

---

## Permissions (AndroidManifest.xml)

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
<uses-feature android:name="android.hardware.microphone" android:required="false" />
```

---

## Dependencies (beyond template)

Added in `app/build.gradle.kts`:
```kotlin
implementation("androidx.compose.material:material-icons-extended")  // Rich icon set
implementation("androidx.compose.animation:animation")               // Animated transitions
```

All other deps come from the template's version catalog (`gradle/libs.versions.toml`).

---

## Build & Deploy

```bash
# Build debug APK
.\gradlew.bat assembleDebug

# APK location
app\build\outputs\apk\debug\app-debug.apk

# Deploy to connected device
android run --apks=app\build\outputs\apk\debug\app-debug.apk

# Describe project (find APK paths)
android describe --project_dir=.
```

---

## Current Status

- [x] Project scaffolded via `android create empty-activity`
- [x] Audio engine with real-time mic processing
- [x] Schroeder reverb effect (4 comb + 2 allpass)
- [x] Delay-line echo effect
- [x] Audio file decode (WAV, MP3, AAC via MediaCodec)
- [x] WAV export with manual header
- [x] Material You UI with animated visualizer
- [x] Transport controls (mic, file, play, record, save)
- [x] Effect parameter sliders with live labels
- [x] Custom vector app icon (reverb rings motif)
- [x] Build passes clean (0 errors, 0 warnings)
- [ ] Deploy & test on physical device (no emulator/device was connected during initial build)

## Known Considerations

1. **No resampling**: Decoded files are used at their native sample rate. If a file isn't 44100 Hz, playback pitch may differ. A proper resampler could be added.
2. **Mono only**: Stereo files are down-mixed to mono during decode.
3. **WAV-only export**: Only WAV (lossless) export is implemented. AAC/M4A encoding via MediaCodec could be added for compressed output.
4. **Old template cleanup**: The original `data/DataRepository.kt` was deleted. The `data/` directory no longer exists.
5. **Deprecated API fixed**: `TopAppBarDefaults.largeTopAppBarColors` → `topAppBarColors`.
