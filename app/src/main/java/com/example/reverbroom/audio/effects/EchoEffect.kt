package com.example.reverbroom.audio.effects

/**
 * Delay-line echo effect with a circular buffer.
 *
 * Parameters:
 *  - [delay]    : 0f..1f  → maps to 0..1000 ms of delay
 *  - [feedback] : 0f..1f  → how much of the delayed signal is fed back
 *  - [mix]      : 0f..1f  → dry/wet blend
 *
 * @param sampleRate audio sample rate in Hz
 */
class EchoEffect(private val sampleRate: Int = 44100) {

    companion object {
        /** Maximum delay in seconds. */
        private const val MAX_DELAY_SECONDS = 1.0f
    }

    // --- Tuneable parameters (updated from UI thread) -------
    @Volatile var delay: Float = 0.3f
    @Volatile var feedback: Float = 0.3f
    @Volatile var mix: Float = 0.3f

    /** Circular buffer sized for the maximum possible delay. */
    private val maxDelaySamples = (MAX_DELAY_SECONDS * sampleRate).toInt()
    private val delayBuffer = FloatArray(maxDelaySamples)
    private var writeIndex = 0

    /**
     * Process a block of mono float samples **in-place**.
     *
     * @param buffer mono samples in the range [-1f, 1f]
     */
    fun process(buffer: FloatArray) {
        val currentDelay = delay
        val currentFeedback = feedback
        val currentMix = mix

        // Current delay length in samples (at least 1 to avoid division by zero)
        val delaySamples = (currentDelay * maxDelaySamples).toInt().coerceIn(1, maxDelaySamples)

        for (i in buffer.indices) {
            val dry = buffer[i]

            // Read from the circular buffer
            val readIndex = ((writeIndex - delaySamples + maxDelaySamples) % maxDelaySamples)
            val delayed = delayBuffer[readIndex]

            // Write current input + feedback into the buffer
            delayBuffer[writeIndex] = dry + delayed * currentFeedback

            // Advance write head
            writeIndex = (writeIndex + 1) % maxDelaySamples

            // Mix dry/wet
            buffer[i] = dry * (1f - currentMix) + delayed * currentMix
        }
    }

    /** Reset the internal delay buffer to zero. */
    fun reset() {
        delayBuffer.fill(0f)
        writeIndex = 0
    }
}
