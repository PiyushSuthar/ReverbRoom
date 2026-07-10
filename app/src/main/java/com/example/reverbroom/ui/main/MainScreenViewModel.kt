package com.example.reverbroom.ui.main

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.reverbroom.audio.AudioEngine
import com.example.reverbroom.audio.AudioEngineState
import com.example.reverbroom.audio.AudioFileHandler
import com.example.reverbroom.audio.AudioMode
import com.example.reverbroom.audio.EffectParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── UI State ────────────────────────────────────────────────────────────

data class MainUiState(
    val audioState: AudioEngineState = AudioEngineState(),
    val effectParams: EffectParams = EffectParams(),
    val isSaving: Boolean = false,
    val saveMessage: String? = null
)

// ── ViewModel ───────────────────────────────────────────────────────────

class MainScreenViewModel : ViewModel() {

    private val audioEngine = AudioEngine()

    private val _effectParams = MutableStateFlow(EffectParams())
    private val _saveState = MutableStateFlow<Pair<Boolean, String?>>(false to null)

    // Cached decoded file samples for replay
    private var cachedFileSamples: ShortArray? = null
    private var cachedFileName: String? = null

    // Cached recorded samples for save-to-file flow
    private var lastRecordedSamples: ShortArray? = null

    /**
     * Combined UI state that merges the audio engine's reactive state with
     * the current effect parameters and save status.
     */
    val uiState: StateFlow<MainUiState> = combine(
        audioEngine.state,
        _effectParams,
        _saveState
    ) { audioState, params, (isSaving, saveMsg) ->
        MainUiState(
            audioState = audioState,
            effectParams = params,
            isSaving = isSaving,
            saveMessage = saveMsg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainUiState())

    init {
        // Apply default params to the engine
        audioEngine.updateParams(_effectParams.value)
    }

    // ── Playback controls ───────────────────────────────────────────────

    /** Start capturing from the microphone with real-time effects. */
    fun startMicrophone() {
        audioEngine.startMicrophone()
    }

    /** Stop any active playback or capture. */
    fun stopPlayback() {
        audioEngine.stop()
    }

    /**
     * Load and decode an audio file, then start playing it through the
     * effects pipeline.
     */
    fun loadFile(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileName = resolveFileName(context, uri)
                val samples = AudioFileHandler.decodeAudioFile(context, uri)
                cachedFileSamples = samples
                cachedFileName = fileName
                audioEngine.startFile(samples, fileName)
            } catch (e: Exception) {
                _saveState.update { false to "Failed to load file: ${e.message}" }
            }
        }
    }

    /** Replay the currently loaded file from the beginning. */
    fun playFile() {
        val samples = cachedFileSamples ?: return
        audioEngine.startFile(samples, cachedFileName)
    }

    // ── Effect parameter updates ────────────────────────────────────────

    /** Update all effect parameters at once. */
    fun updateEffectParams(params: EffectParams) {
        _effectParams.value = params
        audioEngine.updateParams(params)
    }

    /** Reset all effect parameters to their defaults. */
    fun resetEffects() {
        val defaults = EffectParams()
        _effectParams.value = defaults
        audioEngine.updateParams(defaults)
    }

    // ── Recording ───────────────────────────────────────────────────────

    /** Start capturing post-effects audio to an internal buffer. */
    fun startRecording() {
        audioEngine.startRecording()
    }

    /** Stop recording. Captured samples are cached for the next save operation. */
    fun stopRecording() {
        val samples = audioEngine.stopRecording()
        if (samples.isNotEmpty()) {
            lastRecordedSamples = samples
            _saveState.update { false to "Recording stopped – ${samples.size / AudioEngine.SAMPLE_RATE}s captured. Tap Save to export." }
        }
    }

    /**
     * Save recorded or processed audio to the URI chosen by the user
     * via the system file picker (SAF).
     */
    fun saveProcessedFile(context: Context, outputUri: Uri) {
        val data = lastRecordedSamples ?: cachedFileSamples
        if (data == null) {
            _saveState.update { false to "Nothing to save – record or load a file first." }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _saveState.update { true to null }
            try {
                context.contentResolver.openOutputStream(outputUri)?.use { os ->
                    AudioFileHandler.encodeToWav(data, AudioEngine.SAMPLE_RATE, os)
                } ?: throw IllegalStateException("Could not open output stream")

                _saveState.update { false to "Audio saved successfully!" }
                lastRecordedSamples = null
            } catch (e: Exception) {
                _saveState.update { false to "Save failed: ${e.message}" }
            }
        }
    }

    fun saveToLibrary(context: Context) {
        val data = lastRecordedSamples ?: cachedFileSamples
        if (data == null) {
            _saveState.update { false to "Nothing to save - record or load a file first." }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _saveState.update { true to null }
            try {
                val dir = context.getExternalFilesDir(null)
                    ?: throw IllegalStateException("Saved files directory is unavailable")
                if (!dir.exists()) dir.mkdirs()
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val file = java.io.File(dir, "ReverbRoom_$stamp.wav")
                file.outputStream().use { os ->
                    AudioFileHandler.encodeToWav(data, AudioEngine.SAMPLE_RATE, os)
                }
                lastRecordedSamples = null
                _saveState.update { false to "Saved to library: ${file.name}" }
            } catch (e: Exception) {
                _saveState.update { false to "Save failed: ${e.message}" }
            }
        }
    }

    /** Dismiss the current save success / error message. */
    fun dismissSaveMessage() {
        _saveState.update { false to null }
    }

    // ── Lifecycle ───────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun resolveFileName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
