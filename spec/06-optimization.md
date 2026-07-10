# 06 - Audio Filter Optimization

## Problem
The previous implementation of the audio filters (specifically the Schroeder Reverb and Noise Gate) resulted in high latency and heavy CPU usage on the Android audio thread. The `ReverbEffect` relied heavily on multidimensional arrays (`Array<FloatArray>`) and nested loops inside the DSP hot-path, causing significant overhead from array indirection and bounds checking. The `NoiseReductionEffect` used an expensive `sqrt` operation per sample.

## Solution Implemented

### 1. Loop Unrolling & Array Flattening (`ReverbEffect.kt`)
- **Flattened Arrays**: Replaced the nested `Array<FloatArray>` for the 4 parallel comb filters and 2 series all-pass filters with direct, flat `FloatArray` properties (`combBuf0`, `combBuf1`, etc.).
- **Unrolled Loops**: Removed the `for (c in 0 until 4)` and `for (a in 0 until 2)` loops inside the per-sample processing block. 
- **Direct Indexing**: Replaced modulo arithmetic with simple `if-else` wraps for ring buffer index advances, saving clock cycles.
- **Result**: Drastically reduced memory latency and loop overhead, ensuring the Reverb DSP runs significantly faster.

### 2. Fast Math Approximations (`NoiseReductionEffect.kt`)
- **Replaced `sqrt`**: Replaced the computationally expensive `sqrt` calculation used for downward expansion with a much faster linear ratio approach (`inputAbs / adaptiveThreshold`). This provides nearly identical auditory results for noise gating but is vastly cheaper to compute per-sample.

### Conclusion
These changes ensure the DSP loop executes much faster, preventing buffer underruns on the `AudioTrack` and reducing the effective audio latency caused by processing delays.
