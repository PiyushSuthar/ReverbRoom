package com.example.reverbroom.audio.effects

class ReverbEffect(private val sampleRate: Int = 44100) {

    @Volatile var decay: Float = 0.5f
    @Volatile var mix: Float = 0.3f
    @Volatile var roomSize: Float = 0.5f
    @Volatile var width: Float = 0.7f
    @Volatile var damp: Float = 0.35f

    private val combDelay0 = (0.0297 * sampleRate).toInt()
    private val combDelay1 = (0.0371 * sampleRate).toInt()
    private val combDelay2 = (0.0411 * sampleRate).toInt()
    private val combDelay3 = (0.0437 * sampleRate).toInt()

    private val apDelay0 = (0.005  * sampleRate).toInt()
    private val apDelay1 = (0.0017 * sampleRate).toInt()

    private val allPassGain = 0.7f

    private val combBuf0 = FloatArray(combDelay0)
    private val combBuf1 = FloatArray(combDelay1)
    private val combBuf2 = FloatArray(combDelay2)
    private val combBuf3 = FloatArray(combDelay3)
    private var combIdx0 = 0
    private var combIdx1 = 0
    private var combIdx2 = 0
    private var combIdx3 = 0
    private var combFlt0 = 0f
    private var combFlt1 = 0f
    private var combFlt2 = 0f
    private var combFlt3 = 0f

    private val apBuf0 = FloatArray(apDelay0)
    private val apBuf1 = FloatArray(apDelay1)
    private var apIdx0 = 0
    private var apIdx1 = 0

    fun process(buffer: FloatArray) {
        val currentDecay = decay
        val currentMix = mix
        val currentRoomSize = roomSize
        val currentWidth = width
        val currentDamp = damp.coerceIn(0f, 0.95f)
        val feedback = (0.25f + currentDecay * 0.45f + currentRoomSize * 0.25f).coerceIn(0f, 0.95f)
        val stereoWidthTone = 0.8f + currentWidth * 0.4f

        for (i in buffer.indices) {
            val dry = buffer[i]

            // Unrolled Comb 0
            val delayed0 = combBuf0[combIdx0]
            combFlt0 = delayed0 * (1f - currentDamp) + combFlt0 * currentDamp
            combBuf0[combIdx0] = (dry + combFlt0 * feedback).coerceIn(-1.2f, 1.2f)
            combIdx0 = if (combIdx0 + 1 >= combDelay0) 0 else combIdx0 + 1

            // Unrolled Comb 1
            val delayed1 = combBuf1[combIdx1]
            combFlt1 = delayed1 * (1f - currentDamp) + combFlt1 * currentDamp
            combBuf1[combIdx1] = (dry + combFlt1 * feedback).coerceIn(-1.2f, 1.2f)
            combIdx1 = if (combIdx1 + 1 >= combDelay1) 0 else combIdx1 + 1

            // Unrolled Comb 2
            val delayed2 = combBuf2[combIdx2]
            combFlt2 = delayed2 * (1f - currentDamp) + combFlt2 * currentDamp
            combBuf2[combIdx2] = (dry + combFlt2 * feedback).coerceIn(-1.2f, 1.2f)
            combIdx2 = if (combIdx2 + 1 >= combDelay2) 0 else combIdx2 + 1

            // Unrolled Comb 3
            val delayed3 = combBuf3[combIdx3]
            combFlt3 = delayed3 * (1f - currentDamp) + combFlt3 * currentDamp
            combBuf3[combIdx3] = (dry + combFlt3 * feedback).coerceIn(-1.2f, 1.2f)
            combIdx3 = if (combIdx3 + 1 >= combDelay3) 0 else combIdx3 + 1

            val combSum = (combFlt0 + combFlt1 + combFlt2 + combFlt3) * 0.25f * stereoWidthTone

            // Unrolled All-Pass 0
            val apDelayed0 = apBuf0[apIdx0]
            var apOut = -combSum * allPassGain + apDelayed0
            apBuf0[apIdx0] = combSum + apDelayed0 * allPassGain
            apIdx0 = if (apIdx0 + 1 >= apDelay0) 0 else apIdx0 + 1

            // Unrolled All-Pass 1
            val apDelayed1 = apBuf1[apIdx1]
            val input1 = apOut
            apOut = -input1 * allPassGain + apDelayed1
            apBuf1[apIdx1] = input1 + apDelayed1 * allPassGain
            apIdx1 = if (apIdx1 + 1 >= apDelay1) 0 else apIdx1 + 1

            buffer[i] = dry * (1f - currentMix) + apOut * currentMix
        }
    }

    fun reset() {
        combBuf0.fill(0f)
        combBuf1.fill(0f)
        combBuf2.fill(0f)
        combBuf3.fill(0f)
        combIdx0 = 0
        combIdx1 = 0
        combIdx2 = 0
        combIdx3 = 0
        combFlt0 = 0f
        combFlt1 = 0f
        combFlt2 = 0f
        combFlt3 = 0f
        apBuf0.fill(0f)
        apBuf1.fill(0f)
        apIdx0 = 0
        apIdx1 = 0
    }
}
