<p align="center">
  <img src="docs/logo.svg" alt="ReverbRoom logo" width="120" height="120">
</p>

# ReverbRoom

ReverbRoom is an Android app for live audio effects. It can process microphone input or imported audio files with noise reduction, reverb, and echo, then record and save the processed output as WAV.

## Features

- Live microphone processing with low-latency `AudioRecord` and `AudioTrack`
- Audio file import through Android media codecs
- Reverb, echo, and noise reduction controls
- Expanded reverb controls for decay, mix, room size, width, and damp
- Expanded echo controls for delay, feedback, mix, beat feel, and decay
- Preset save/load/delete for favorite effect configurations
- Animated waveform and spectrum visualizer with input/output level meters
- Record post-effects output
- Save recordings to the in-app library
- Export WAV files through Android's document picker
- Saved files screen with play, delete, and share
- Settings screen for audio quality, effects, noise reduction, and preferences
- Material 3 / Material You Compose UI

## Requirements

- Windows, macOS, or Linux with JDK 17
- Android SDK installed
- Android device or emulator running Android 7.0 or newer
- Microphone permission for live input

Project defaults:

- Min SDK: 24
- Target SDK: 36
- Application ID: `com.reverbroom.app`
- Main package namespace: `com.example.reverbroom`

## Build

From the project root:

```powershell
.\gradlew.bat :app:assembleDebug
```

On macOS or Linux:

```bash
./gradlew :app:assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install

With Android Debug Bridge:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

On macOS or Linux:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

You can also install from Android Studio by opening the project and running the `app` configuration on a connected device.

## Usage

1. Launch ReverbRoom.
2. Tap the microphone button and grant microphone permission for live effects.
3. Adjust Reverb, Echo, and Noise Reduction controls.
4. Tap Record while audio is playing to capture the processed output.
5. Tap Save, then choose either Save to Library or Export WAV.
6. Open the folder button to view saved library files, play them back, share them, or delete them.
7. Open Settings to persist preferred effect and audio settings.

Use headphones when monitoring the microphone to avoid acoustic feedback.

## Development Commands

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:compileDebugAndroidTestKotlin
```

## Releases

Version values live in `gradle.properties`:

```properties
VERSION_CODE=1
VERSION_NAME=1.0.0
```

Create a release with:

```bash
scripts/release.sh 1.1.0 "Describe the user-facing changes"
```

The script updates `gradle.properties`, prepends `CHANGELOG.md`, runs tests/build, commits the release, and tags it. Pushing a `v*` tag triggers `.github/workflows/release-apk.yml`, which builds an APK and attaches it to the GitHub release.

## Project Structure

```text
app/src/main/java/com/example/reverbroom/
├── audio/          # Audio engine, file decode/encode, DSP effects
├── theme/          # Material theme and colors
├── ui/files/       # Saved recordings list
├── ui/main/        # Main studio screen and ViewModel
├── ui/settings/    # Settings persistence and UI
├── MainActivity.kt
├── Navigation.kt
└── NavigationKeys.kt
```

## Notes

- WAV export is currently 16-bit PCM.
- File playback assumes 44100 Hz PCM after decode; files with other sample rates may need future resampling support.
- The app stores library recordings in app external files storage and shares them through `FileProvider`.
- See `spec/03-current-state.md` for the latest implementation knowledge and known limitations.
- See `spec/05-new-features-implementation.md` for the latest feature expansion notes.
- See `spec/06-optimization.md` for details on DSP performance optimizations and latency reduction.
- See `spec/07-knowledge-and-changes.md` for a summary of GitHub CI fixes and audio filter optimizations.
