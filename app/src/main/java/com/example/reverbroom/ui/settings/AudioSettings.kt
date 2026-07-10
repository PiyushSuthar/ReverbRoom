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
    val presets: List<EffectPreset> = emptyList(),
    val autoStartMic: Boolean = false,
    val keepScreenOn: Boolean = true
)

data class EffectPreset(
    val name: String,
    val params: EffectParams
)

object AudioSettingsStore {
    private const val PREFS = "audio_settings"
    private const val KEY_QUALITY = "quality"
    private const val KEY_AUTO_START_MIC = "auto_start_mic"
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    private const val KEY_REVERB_DECAY = "reverb_decay"
    private const val KEY_REVERB_MIX = "reverb_mix"
    private const val KEY_REVERB_ROOM_SIZE = "reverb_room_size"
    private const val KEY_REVERB_WIDTH = "reverb_width"
    private const val KEY_REVERB_DAMP = "reverb_damp"
    private const val KEY_ECHO_DELAY = "echo_delay"
    private const val KEY_ECHO_FEEDBACK = "echo_feedback"
    private const val KEY_ECHO_MIX = "echo_mix"
    private const val KEY_ECHO_BEATS = "echo_beats"
    private const val KEY_ECHO_DECAY = "echo_decay"
    private const val KEY_NOISE_ENABLED = "noise_enabled"
    private const val KEY_NOISE_THRESHOLD = "noise_threshold"
    private const val KEY_NOISE_STRENGTH = "noise_strength"
    private const val KEY_PRESETS = "presets"

    private val _state = MutableStateFlow(AudioSettingsState())
    val state: StateFlow<AudioSettingsState> = _state.asStateFlow()

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val params = EffectParams(
            reverbDecay = prefs.getFloat(KEY_REVERB_DECAY, EffectParams().reverbDecay),
            reverbMix = prefs.getFloat(KEY_REVERB_MIX, EffectParams().reverbMix),
            reverbRoomSize = prefs.getFloat(KEY_REVERB_ROOM_SIZE, EffectParams().reverbRoomSize),
            reverbWidth = prefs.getFloat(KEY_REVERB_WIDTH, EffectParams().reverbWidth),
            reverbDamp = prefs.getFloat(KEY_REVERB_DAMP, EffectParams().reverbDamp),
            echoDelay = prefs.getFloat(KEY_ECHO_DELAY, EffectParams().echoDelay),
            echoFeedback = prefs.getFloat(KEY_ECHO_FEEDBACK, EffectParams().echoFeedback),
            echoMix = prefs.getFloat(KEY_ECHO_MIX, EffectParams().echoMix),
            echoBeats = prefs.getFloat(KEY_ECHO_BEATS, EffectParams().echoBeats),
            echoDecay = prefs.getFloat(KEY_ECHO_DECAY, EffectParams().echoDecay),
            noiseReductionEnabled = prefs.getBoolean(KEY_NOISE_ENABLED, EffectParams().noiseReductionEnabled),
            noiseGateThreshold = prefs.getFloat(KEY_NOISE_THRESHOLD, EffectParams().noiseGateThreshold),
            noiseReductionStrength = prefs.getFloat(KEY_NOISE_STRENGTH, EffectParams().noiseReductionStrength)
        )
        _state.value = AudioSettingsState(
            audioQuality = runCatching {
                AudioQuality.valueOf(prefs.getString(KEY_QUALITY, AudioQuality.Standard.name) ?: AudioQuality.Standard.name)
            }.getOrDefault(AudioQuality.Standard),
            effectParams = params,
            presets = decodePresets(prefs.getString(KEY_PRESETS, null)),
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

    fun savePreset(context: Context, name: String) {
        val cleanName = name.trim().ifEmpty { "Preset ${_state.value.presets.size + 1}" }
        update(context) { current ->
            val preset = EffectPreset(cleanName, current.effectParams)
            current.copy(presets = (current.presets.filterNot { it.name == cleanName } + preset).takeLast(12))
        }
    }

    fun loadPreset(context: Context, preset: EffectPreset) {
        update(context) { it.copy(effectParams = preset.params) }
    }

    fun deletePreset(context: Context, preset: EffectPreset) {
        update(context) { current ->
            current.copy(presets = current.presets.filterNot { it.name == preset.name })
        }
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
            .putFloat(KEY_REVERB_ROOM_SIZE, params.reverbRoomSize)
            .putFloat(KEY_REVERB_WIDTH, params.reverbWidth)
            .putFloat(KEY_REVERB_DAMP, params.reverbDamp)
            .putFloat(KEY_ECHO_DELAY, params.echoDelay)
            .putFloat(KEY_ECHO_FEEDBACK, params.echoFeedback)
            .putFloat(KEY_ECHO_MIX, params.echoMix)
            .putFloat(KEY_ECHO_BEATS, params.echoBeats)
            .putFloat(KEY_ECHO_DECAY, params.echoDecay)
            .putBoolean(KEY_NOISE_ENABLED, params.noiseReductionEnabled)
            .putFloat(KEY_NOISE_THRESHOLD, params.noiseGateThreshold)
            .putFloat(KEY_NOISE_STRENGTH, params.noiseReductionStrength)
            .putString(KEY_PRESETS, encodePresets(value.presets))
            .apply()
    }

    private fun encodePresets(presets: List<EffectPreset>): String {
        return presets.joinToString("\n") { preset ->
            listOf(
                preset.name.escape(),
                preset.params.reverbDecay,
                preset.params.reverbMix,
                preset.params.reverbRoomSize,
                preset.params.reverbWidth,
                preset.params.reverbDamp,
                preset.params.echoDelay,
                preset.params.echoFeedback,
                preset.params.echoMix,
                preset.params.echoBeats,
                preset.params.echoDecay,
                preset.params.noiseReductionEnabled,
                preset.params.noiseGateThreshold,
                preset.params.noiseReductionStrength
            ).joinToString("|")
        }
    }

    private fun decodePresets(raw: String?): List<EffectPreset> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lines().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size < 14) return@mapNotNull null
            runCatching {
                EffectPreset(
                    name = parts[0].unescape(),
                    params = EffectParams(
                        reverbDecay = parts[1].toFloat(),
                        reverbMix = parts[2].toFloat(),
                        reverbRoomSize = parts[3].toFloat(),
                        reverbWidth = parts[4].toFloat(),
                        reverbDamp = parts[5].toFloat(),
                        echoDelay = parts[6].toFloat(),
                        echoFeedback = parts[7].toFloat(),
                        echoMix = parts[8].toFloat(),
                        echoBeats = parts[9].toFloat(),
                        echoDecay = parts[10].toFloat(),
                        noiseReductionEnabled = parts[11].toBoolean(),
                        noiseGateThreshold = parts[12].toFloat(),
                        noiseReductionStrength = parts[13].toFloat()
                    )
                )
            }.getOrNull()
        }
    }

    private fun String.escape(): String = replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n")

    private fun String.unescape(): String = replace("\\n", "\n").replace("\\p", "|").replace("\\\\", "\\")
}
