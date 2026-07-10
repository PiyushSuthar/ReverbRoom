package com.example.reverbroom.audio.effects

/**
 * Schroeder reverb implementation.
 *
 * Architecture:
 *   Input ──┬── CombFilter 0 ──┐
 *           ├── CombFilter 1 ──┤
 *           ├── CombFilter 2 ──┼── sum ── AllPassFilter 0 ── AllPassFilter 1 ── wet
 *           └── CombFilter 3 ──┘
 *
 *   Output = dry * (1 - mix) + wet * mix
 *
 * @param sampleRate audio sample rate in Hz
 */
class ReverbEffect(private val sampleRate: Int = 44100) {

    // --- Tuneable parameters (updated from UI thread) -------
    @Volatile var decay: Float = 0.5f
    @Volatile var mix: Float = 0.3f

    // --- Comb filter delay lengths (in samples) chosen to be mutually prime ---
    private val combDelays = intArrayOf(
        (0.0297 * sampleRate).toInt(),  // ~1310 @ 44100
        (0.0371 * sampleRate).toInt(),  // ~1636
        (0.0411 * sampleRate).toInt(),  // ~1813
        (0.0437 * sampleRate).toInt()   // ~1927
    )

    // --- All-pass filter delay lengths ---
    private val allPassDelays = intArrayOf(
        (0.005  * sampleRate).toInt(),  // ~220
        (0.0017 * sampleRate).toInt()   // ~75
    )

    private val allPassGain = 0.7f

    // --- Internal delay-line buffers ---
    private val combBuffers = Array(4) { FloatArray(combDelays[it]) }
    private val combIndices = IntArray(4)

    private val apBuffers = Array(2) { FloatArray(allPassDelays[it]) }
    private val apIndices = IntArray(2)

    /**
     * Process a block of mono float samples **in-place**.
     *
     * @param buffer mono samples in the range [-1f, 1f]
     */
    fun process(buffer: FloatArray) {
        val currentDecay = decay
        val currentMix = mix

        for (i in buffer.indices) {
            val dry = buffer[i]

            // ---- 4 parallel comb filters ----
            var combSum = 0f
            for (c in 0 until 4) {
                val buf = combBuffers[c]
                val idx = combIndices[c]
                val delayed = buf[idx]
                val newVal = dry + delayed * currentDecay
                buf[idx] = newVal
                combSum += delayed
                combIndices[c] = (idx + 1) % buf.size
            }
            combSum *= 0.25f  // average the four comb outputs

            // ---- 2 series all-pass filters ----
            var apOut = combSum
            for (a in 0 until 2) {
                val buf = apBuffers[a]
                val idx = apIndices[a]
                val delayed = buf[idx]
                val input = apOut
                apOut = -input * allPassGain + delayed
                buf[idx] = input + delayed * allPassGain
                apIndices[a] = (idx + 1) % buf.size
            }

            // ---- Mix dry/wet ----
            buffer[i] = dry * (1f - currentMix) + apOut * currentMix
        }
    }

    /** Reset all internal delay-line buffers to zero. */
    fun reset() {
        combBuffers.forEach { it.fill(0f) }
        combIndices.fill(0)
        apBuffers.forEach { it.fill(0f) }
        apIndices.fill(0)
    }
}
