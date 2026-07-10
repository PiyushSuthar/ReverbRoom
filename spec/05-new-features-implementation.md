# New Features Implementation Notes

This file documents the work completed for `spec/04-new-features.md`, along with current knowledge and limitations.

## Implemented Features

### Expanded Reverb Controls

`EffectParams` now includes:

- `reverbDecay`
- `reverbMix`
- `reverbRoomSize`
- `reverbWidth`
- `reverbDamp`

`ReverbEffect` now uses the new values in the DSP path:

- `roomSize` contributes to comb-filter feedback for larger or smaller perceived spaces.
- `damp` low-pass filters comb feedback to reduce high-frequency ringing.
- `width` changes wet signal tone/energy. The engine is still mono, so width is a mono-compatible spatial color control rather than true stereo spread.

The controls are exposed in:

- `MainScreen`
- `SettingsScreen`
- `AudioSettingsStore` persistence
- preset serialization

### Expanded Echo Controls

`EffectParams` now includes:

- `echoDelay`
- `echoFeedback`
- `echoMix`
- `echoBeats`
- `echoDecay`

`EchoEffect` now uses:

- `beats` to scale the base delay length.
- `decay` to shape the effective feedback gain.

This keeps the current circular-buffer echo design while making the control surface more musical and easier to recall through presets.

### Presets

Presets were added to `AudioSettingsStore`.

Data model:

```kotlin
data class EffectPreset(
    val name: String,
    val params: EffectParams
)
```

Supported actions:

- Save the current effect settings as a named preset.
- Load a preset into the active settings.
- Delete a preset.

Storage:

- Presets are persisted in `SharedPreferences` using a compact line-based encoding.
- The store keeps the latest 12 presets.
- Saving with an existing name replaces that preset.

UI:

- `SettingsScreen` has a Presets card with a name field, Save button, Load actions, and Delete actions.

### Better Noise Reduction

`NoiseReductionEffect` was upgraded from a simple gate to a gate plus adaptive downward expansion:

- Existing smoothed envelope gate remains.
- A smoothed noise floor estimate was added.
- `noiseReductionStrength` controls how strongly quiet material is attenuated.
- A floor gain prevents extreme hard-gating artifacts.

This is still intentionally lightweight enough for real-time mobile processing.

### Real-Time Visualization

`AudioEngineState` now exposes:

```kotlin
val spectrumBands: List<Float> = List(8) { 0f }
```

The engine calculates eight low-cost amplitude bands from the current processed float buffer. This is not a true FFT spectrum; it is a real-time banded amplitude visualization designed to be cheap and stable in the existing audio loop.

`MainScreen` renders the bands as live bars over the visualizer card, alongside the existing animated waveform and level meters.

### Release Automation

Added GitHub Actions workflow:

```text
.github/workflows/release-apk.yml
```

Behavior:

- Runs on `v*` tags and manual workflow dispatch.
- Sets up JDK 17 and Android SDK.
- Builds `:app:assembleDebug`.
- Uploads the APK as a workflow artifact.
- For tag builds, attaches the APK to the GitHub release.

Added release script:

```text
scripts/release.sh
```

Behavior:

- Requires a clean working tree.
- Updates `VERSION_NAME` and increments `VERSION_CODE` in `gradle.properties`.
- Prepends `CHANGELOG.md`.
- Runs unit tests and debug APK build.
- Commits release files and creates a `vX.Y.Z` tag.
- If GitHub CLI is installed, creates a GitHub release with the debug APK.

Version source of truth:

```text
gradle.properties
```

`app/build.gradle.kts` now reads `VERSION_CODE` and `VERSION_NAME` from Gradle properties.

## Tests Added

Added `EffectProcessingTest` for pure JVM DSP coverage:

- Reverb with expanded params keeps samples finite and non-silent.
- Echo with beat/decay params keeps samples finite and non-silent.
- Noise reduction at high strength attenuates quiet input.

Existing ViewModel and Compose test sources still compile.

## Verification

These commands passed:

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:compileDebugAndroidTestKotlin
```

Known warning:

- `Icons.Filled.ArrowBack` is deprecated in favor of the auto-mirrored icon variant. This warning existed in settings/back navigation and does not block the build.

## Current Limitations

- Reverb width is mono-compatible and not true stereo width because the engine still processes mono buffers.
- The visualization bars are amplitude bands, not FFT frequency bins.
- The release workflow currently builds and attaches the debug APK. A signed release APK or AAB would require keystore secrets and a release signing config.
- The bash release script is designed for Git Bash/macOS/Linux. On Windows, run it from Git Bash or WSL.
- Presets are stored in `SharedPreferences`; a future data store or JSON serializer would be better if preset metadata grows.

## Recommended Next Steps

- Add true stereo processing if width should become spatial stereo width.
- Add an FFT implementation if the visualizer should show real frequency-domain spectrum.
- Add release signing with GitHub Actions secrets.
- Add UI tests for saving/loading presets.
- Consider migrating settings/presets to DataStore for typed persistence and easier migrations.
