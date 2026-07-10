# ReverbRoom Current State

This file captures the implementation status after the fixes and new features requested in `spec/02-fixes-new-features.md`.

## Summary

ReverbRoom is a Kotlin Android app for real-time microphone or file-based audio processing. It applies noise reduction, reverb, and echo, plays the processed signal through `AudioTrack`, records the post-effects output, and saves WAV files.

The app now has three user-facing screens:

- Main studio screen: live visualizer, transport controls, effect sliders, recording, save/export.
- Saved files screen: app-library WAV recordings with play, delete, and share actions.
- Settings screen: audio quality, reverb, echo, noise reduction, and preference controls.

## Implemented From `02-fixes-new-features.md`

- Saved files page:
  - Added `SavedFilesScreen`.
  - Reused and completed `SavedFilesViewModel`.
  - Supports playback with `MediaPlayer`.
  - Supports delete from app external files storage.
  - Supports sharing WAV files through `FileProvider`.

- Settings page:
  - Added `SettingsScreen`.
  - Added `AudioSettingsStore` backed by `SharedPreferences`.
  - Persists audio quality, reverb, echo, noise reduction, and basic preferences.
  - Main screen observes persisted effect settings and applies them to `AudioEngine`.

- Noise reduction:
  - `NoiseReductionEffect` is in the DSP chain before reverb and echo.
  - Main and Settings screens expose enable/disable and gate threshold controls.
  - Settings persist across app launches.

- Header spacing fix:
  - Replaced the large collapsing top app bar on the main screen with a compact `TopAppBar`.
  - Reduced main content vertical padding and item spacing.

## Important Files

```text
app/src/main/java/com/example/reverbroom/
├── MainActivity.kt
├── Navigation.kt
├── NavigationKeys.kt
├── audio/
│   ├── AudioEngine.kt
│   ├── AudioFileHandler.kt
│   └── effects/
│       ├── EchoEffect.kt
│       ├── NoiseReductionEffect.kt
│       └── ReverbEffect.kt
├── ui/
│   ├── files/
│   │   ├── SavedFilesScreen.kt
│   │   └── SavedFilesViewModel.kt
│   ├── main/
│   │   ├── MainScreen.kt
│   │   └── MainScreenViewModel.kt
│   └── settings/
│       ├── AudioSettings.kt
│       └── SettingsScreen.kt
└── theme/
```

## Navigation

Navigation uses AndroidX Navigation3.

Routes are defined in `NavigationKeys.kt`:

- `Main`
- `SavedFiles`
- `Settings`

`Navigation.kt` wires these screens through `NavDisplay`. The main top bar exposes folder and settings actions to push the saved files and settings destinations.

## Audio Pipeline

`AudioEngine` supports two modes:

- `MICROPHONE`: `AudioRecord` -> float conversion -> noise reduction -> reverb -> echo -> `AudioTrack`
- `FILE`: decoded PCM samples -> noise reduction -> reverb -> echo -> `AudioTrack`

Current engine characteristics:

- Sample rate: 44100 Hz
- Channels: mono
- Encoding: 16-bit PCM
- DSP thread priority: `THREAD_PRIORITY_URGENT_AUDIO`
- Recording captures post-effects output samples.

`EffectParams` currently contains:

- `reverbDecay`
- `reverbMix`
- `echoDelay`
- `echoFeedback`
- `echoMix`
- `noiseReductionEnabled`
- `noiseGateThreshold`

## Saving And Sharing

There are two save paths:

- Save to Library:
  - `MainScreenViewModel.saveToLibrary()`
  - Writes a WAV file to `context.getExternalFilesDir(null)`.
  - These files appear in `SavedFilesScreen`.

- Export WAV:
  - Uses Android Storage Access Framework through `CreateDocument("audio/wav")`.
  - Lets the user choose a destination outside the app library.

Sharing uses:

- Manifest provider: `androidx.core.content.FileProvider`
- Paths file: `app/src/main/res/xml/file_paths.xml`
- Authority: `${applicationId}.fileprovider`

## Settings Persistence

`AudioSettingsStore` is a process-wide store backed by `SharedPreferences`.

Persisted values:

- Audio quality enum (`Standard`, `High`)
- All `EffectParams`
- Auto-start microphone preference
- Keep-screen-on preference

Current note: audio quality is stored and represented in UI, but the core WAV export remains 16-bit PCM because `AudioFileHandler.encodeToWav()` currently writes 16-bit samples.

## Android Manifest

Important manifest entries:

- `RECORD_AUDIO`
- legacy read/write external storage permissions for older Android versions
- `READ_MEDIA_AUDIO`
- optional microphone feature
- `FileProvider` for sharing app-library WAV files

## Tests And Verification

The following commands passed after implementation:

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:compileDebugAndroidTestKotlin :app:compileDebugUnitTestKotlin
.\gradlew.bat :app:testDebugUnitTest
```

Test cleanup performed:

- `MainScreenTest` now checks the real main screen title instead of old template sample data.
- `MainScreenViewModelTest` now verifies current effect state behavior instead of removed template repository code.

## Current Limitations

- File decode does not resample input; non-44100 Hz sources may play at the wrong pitch.
- Processing is mono only; multi-channel files are downmixed.
- Export is WAV only.
- `AudioQuality.High` is currently a preference placeholder and not yet connected to 24-bit export or a different processing path.
- Auto-start microphone and keep-screen-on preferences are persisted, but additional lifecycle/UI wiring may be needed if those preferences should actively change runtime behavior.
- No physical-device audio latency validation was performed in this environment.

## Recommended Next Steps

- Add sample-rate conversion after decode.
- Connect `AudioQuality.High` to an actual export or processing option.
- Add Android instrumentation coverage for the saved files and settings screens.
- Validate microphone latency and feedback behavior on a physical device with headphones.
- Consider saving metadata for library recordings instead of relying only on filename, size, and modification date.
