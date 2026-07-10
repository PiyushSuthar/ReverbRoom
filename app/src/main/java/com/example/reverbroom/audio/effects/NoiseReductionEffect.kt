package com.example.reverbroom.audio.effects

import kotlin.math.abs
import kotlin.math.exp

/**
 * Simple noise reduction / noise gate effect.
 *
 * Uses a combination of:
 * 1. **Noise gate**: Attenuates signal below a configurable threshold
 * 2. **Smoothed envelope**: Prevents harsh gating artefacts with attack/release
 *
 * Parameters:
 *  - [enabled]   : whether the noise gate is active
 *  - [threshold] : 0f..0.1f — signal level below which gating kicks in
 *
 * @param sampleRate audio sample rate in Hz
 */
class NoiseReductionEffect(private val sampleRate: Int = 44100) {

    // --- Tuneable parameters (updated from UI thread) -------
    @Volatile var enabled: Boolean = true
    @Volatile var threshold: Float = 0.02f   // 0..0.1

    // --- Envelope follower state ---
    private var envelope: Float = 0f

    // Attack/release time constants (in seconds)
    private val attackTime = 0.005f   // 5ms - fast attack
    private val releaseTime = 0.05f   // 50ms - slower release to avoid chatter

    private val attackCoeff: Float = exp(-1f / (sampleRate * attackTime))
    private val releaseCoeff: Float = exp(-1f / (sampleRate * releaseTime))

    /**
     * Process a block of mono float samples **in-place**.
     *
     * Samples below the threshold are smoothly attenuated toward zero.
     * Samples above the threshold pass through unmodified.
     *
     * @param buffer mono samples in the range [-1f, 1f]
     */
    fun process(buffer: FloatArray) {
        if (!enabled) return

        val currentThreshold = threshold

        for (i in buffer.indices) {
            val inputAbs = abs(buffer[i])

            // Smooth envelope follower
            envelope = if (inputAbs > envelope) {
                // Attack: fast rise
                attackCoeff * envelope + (1f - attackCoeff) * inputAbs
            } else {
                // Release: slow decay
                releaseCoeff * envelope + (1f - releaseCoeff) * inputAbs
            }

            // Compute gain based on envelope vs threshold
            val gain = if (envelope < currentThreshold) {
                // Smoothly reduce gain as signal drops below threshold
                // Maps [0, threshold] → [0, 1] with a soft knee
                val ratio = envelope / currentThreshold.coerceAtLeast(0.0001f)
                ratio * ratio  // Quadratic curve for smooth transition
            } else {
                1f
            }

            buffer[i] *= gain
        }
    }

    /** Reset envelope state. */
    fun reset() {
        envelope = 0f
    }
}
