package com.example.reverbroom.ui.settings

import android.content.Context
import com.example.reverbroom.audio.EffectParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class AudioQuality(val label: String, val bitDepth: Int) {
    Standard("Standard", 16),
    High("High", 24)
}

data class AudioSettingsState(
    val audioQuality: AudioQuality = AudioQuality.Standard,
    val effectParams: EffectParams = EffectParams(),
    val autoStartMic: Boolean = false,
    val keepScreenOn: Boolean = true
)

object AudioSettingsStore {
    private const val PREFS = "audio_settings"
    private const val KEY_QUALITY = "quality"
    private const val KEY_AUTO_START_MIC = "auto_start_mic"
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    private const val KEY_REVERB_DECAY = "reverb_decay"
    private const val KEY_REVERB_MIX = "reverb_mix"
    private const val KEY_ECHO_DELAY = "echo_delay"
    private const val KEY_ECHO_FEEDBACK = "echo_feedback"
    private const val KEY_ECHO_MIX = "echo_mix"
    private const val KEY_NOISE_ENABLED = "noise_enabled"
    private const val KEY_NOISE_THRESHOLD = "noise_threshold"

    private val _state = MutableStateFlow(AudioSettingsState())
    val state: StateFlow<AudioSettingsState> = _state.asStateFlow()

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val params = EffectParams(
            reverbDecay = prefs.getFloat(KEY_REVERB_DECAY, EffectParams().reverbDecay),
            reverbMix = prefs.getFloat(KEY_REVERB_MIX, EffectParams().reverbMix),
            echoDelay = prefs.getFloat(KEY_ECHO_DELAY, EffectParams().echoDelay),
            echoFeedback = prefs.getFloat(KEY_ECHO_FEEDBACK, EffectParams().echoFeedback),
            echoMix = prefs.getFloat(KEY_ECHO_MIX, EffectParams().echoMix),
            noiseReductionEnabled = prefs.getBoolean(KEY_NOISE_ENABLED, EffectParams().noiseReductionEnabled),
            noiseGateThreshold = prefs.getFloat(KEY_NOISE_THRESHOLD, EffectParams().noiseGateThreshold)
        )
        _state.value = AudioSettingsState(
            audioQuality = runCatching {
                AudioQuality.valueOf(prefs.getString(KEY_QUALITY, AudioQuality.Standard.name) ?: AudioQuality.Standard.name)
            }.getOrDefault(AudioQuality.Standard),
            effectParams = params,
            autoStartMic = prefs.getBoolean(KEY_AUTO_START_MIC, false),
            keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        )
        loaded = true
    }

    fun update(context: Context, transform: (AudioSettingsState) -> AudioSettingsState) {
        load(context)
        _state.update(transform)
        save(context)
    }

    fun updateEffectParams(context: Context, params: EffectParams) {
        update(context) { it.copy(effectParams = params) }
    }

    private fun save(context: Context) {
        val value = _state.value
        val params = value.effectParams
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_QUALITY, value.audioQuality.name)
            .putBoolean(KEY_AUTO_START_MIC, value.autoStartMic)
            .putBoolean(KEY_KEEP_SCREEN_ON, value.keepScreenOn)
            .putFloat(KEY_REVERB_DECAY, params.reverbDecay)
            .putFloat(KEY_REVERB_MIX, params.reverbMix)
            .putFloat(KEY_ECHO_DELAY, params.echoDelay)
            .putFloat(KEY_ECHO_FEEDBACK, params.echoFeedback)
            .putFloat(KEY_ECHO_MIX, params.echoMix)
            .putBoolean(KEY_NOISE_ENABLED, params.noiseReductionEnabled)
            .putFloat(KEY_NOISE_THRESHOLD, params.noiseGateThreshold)
            .apply()
    }
}
