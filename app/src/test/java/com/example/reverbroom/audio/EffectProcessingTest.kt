package com.example.reverbroom.audio

import com.example.reverbroom.audio.effects.EchoEffect
import com.example.reverbroom.audio.effects.NoiseReductionEffect
import com.example.reverbroom.audio.effects.ReverbEffect
import junit.framework.TestCase.assertTrue
import org.junit.Test
import kotlin.math.abs

class EffectProcessingTest {
    @Test
    fun reverb_withExpandedParams_keepsSamplesFinite() {
        val effect = ReverbEffect()
        effect.decay = 0.85f
        effect.mix = 0.7f
        effect.roomSize = 0.9f
        effect.width = 0.8f
        effect.damp = 0.4f

        val samples = impulse()
        effect.process(samples)

        assertTrue(samples.all { it.isFinite() })
        assertTrue(samples.any { abs(it) > 0f })
    }

    @Test
    fun echo_withBeatAndDecayParams_keepsSamplesFinite() {
        val effect = EchoEffect()
        effect.delay = 0.5f
        effect.feedback = 0.8f
        effect.mix = 0.6f
        effect.beats = 0.75f
        effect.decay = 0.7f

        val samples = impulse(4096)
        effect.process(samples)

        assertTrue(samples.all { it.isFinite() })
        assertTrue(samples.any { abs(it) > 0f })
    }

    @Test
    fun noiseReduction_atHighStrength_attenuatesQuietInput() {
        val effect = NoiseReductionEffect()
        effect.enabled = true
        effect.threshold = 0.04f
        effect.strength = 1f

        val samples = FloatArray(512) { 0.01f }
        effect.process(samples)

        assertTrue(samples.average() < 0.01)
    }

    private fun impulse(size: Int = 2048): FloatArray {
        return FloatArray(size).also { it[0] = 0.8f }
    }
}
