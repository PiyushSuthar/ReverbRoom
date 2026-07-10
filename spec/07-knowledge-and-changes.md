# 07 - Knowledge and Changes (Audio Optimization & CI Fixes)

## Summary of Recent Changes

### 1. GitHub Action CI Pipeline Fixes
* **Issue**: The GitHub action for building the Release APK was failing. Initially, it lacked execution permissions for `gradlew`. Furthermore, enabling `minify` caused ProGuard-related build errors. Finally, the generated unsigned release APK couldn't be installed on devices because Android requires a valid signature.
* **Resolution**: 
  * Updated `.github/workflows/release-apk.yml` to run `chmod +x gradlew` before building.
  * Reverted `isMinifyEnabled` to `false` and removed `isShrinkResources` in `app/build.gradle.kts`.
  * Added `signingConfig = signingConfigs.getByName("debug")` to the `release` build type so that the resulting APK is automatically signed using the default Android debug key, allowing easy testing out-of-the-box.

### 2. Audio DSP Filter Optimizations
* **Issue**: The original audio filtering logic (especially `ReverbEffect` and `NoiseReductionEffect`) exhibited higher CPU usage and potential latency spikes due to nested loops, multi-dimensional array indirections, and expensive math operations during the per-sample processing loop.
* **Resolution**:
  * **ReverbEffect.kt**: Unrolled the loops for the Schroeder reverb. Replaced the `Array<FloatArray>` implementations (4 comb filters, 2 all-pass filters) with direct, flat `FloatArray` properties (`combBuf0`, `combBuf1`, etc.). Swapped expensive modulo operations with conditional wraps.
  * **NoiseReductionEffect.kt**: Replaced the expensive `sqrt()` operation used in downward expansion with a fast linear approximation.
  * **Result**: The core DSP algorithms now run substantially faster in the hot path, effectively reducing potential buffer underruns and maintaining a stable audio loop.
