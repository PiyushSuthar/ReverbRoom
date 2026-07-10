package com.example.reverbroom.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Process
import com.example.reverbroom.audio.effects.EchoEffect
import com.example.reverbroom.audio.effects.NoiseReductionEffect
import com.example.reverbroom.audio.effects.ReverbEffect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.max

// ── Public data models ──────────────────────────────────────────────────

enum class AudioMode { MICROPHONE, FILE }

data class AudioEngineState(
    val isPlaying: Boolean = false,
    val mode: AudioMode = AudioMode.MICROPHONE,
    val inputLevel: Float = 0f,
    val outputLevel: Float = 0f,
    val isRecording: Boolean = false,
    val fileName: String? = null
)

data class EffectParams(
    val reverbDecay: Float = 0.5f,
    val reverbMix: Float = 0.3f,
    val echoDelay: Float = 0.3f,
    val echoFeedback: Float = 0.3f,
    val echoMix: Float = 0.3f,
    val noiseReductionEnabled: Boolean = true,
    val noiseGateThreshold: Float = 0.02f
)

// ── AudioEngine ─────────────────────────────────────────────────────────

/**
 * Central audio engine that captures audio from the microphone or plays back
 * pre-decoded file samples, runs them through a real-time DSP pipeline
 * (reverb → echo), and outputs via [AudioTrack].
 *
 * All heavy work runs on a dedicated high-priority audio thread.
 */
class AudioEngine {

    companion object {
        const val SAMPLE_RATE = 44100
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    // ── Effects ─────────────────────────────────────────────────────────
    private val noiseReduction = NoiseReductionEffect(SAMPLE_RATE)
    private val reverb = ReverbEffect(SAMPLE_RATE)
    private val echo = EchoEffect(SAMPLE_RATE)

    // ── State ───────────────────────────────────────────────────────────
    private val _state = MutableStateFlow(AudioEngineState())
    val state: StateFlow<AudioEngineState> = _state.asStateFlow()

    // ── Audio primitives ────────────────────────────────────────────────
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var processingThread: Thread? = null

    @Volatile private var isRunning = false

    // ── File-mode state ─────────────────────────────────────────────────
    private var fileSamples: ShortArray? = null
    private var filePlaybackPosition = 0

    // ── Recording state ─────────────────────────────────────────────────
    @Volatile private var isRecordingOutput = false
    private val recordedSamples = mutableListOf<Short>()

    // ── Buffer size (determined at runtime) ─────────────────────────────
    private val minBufSize: Int by lazy {
        val minRec = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
        val minPlay = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING)
        max(minRec, minPlay)
    }

    // ────────────────────────────────────────────────────────────────────
    // Public API
    // ────────────────────────────────────────────────────────────────────

    /** Update DSP parameters at any time (thread-safe). */
    fun updateParams(params: EffectParams) {
        noiseReduction.enabled = params.noiseReductionEnabled
        noiseReduction.threshold = params.noiseGateThreshold
        reverb.decay = params.reverbDecay
        reverb.mix = params.reverbMix
        echo.delay = params.echoDelay
        echo.feedback = params.echoFeedback
        echo.mix = params.echoMix
    }

    /** Start capturing from the microphone and playing through effects. */
    fun startMicrophone() {
        if (isRunning) stop()
        resetEffects()

        _state.update {
            it.copy(
                isPlaying = true,
                mode = AudioMode.MICROPHONE,
                fileName = null
            )
        }

        isRunning = true
        processingThread = Thread(::microphoneLoop, "AudioEngine-Mic").apply {
            start()
        }
    }

    /**
     * Start playing pre-decoded [samples] through the effects pipeline.
     *
     * @param samples mono 16-bit PCM samples (e.g. from [AudioFileHandler.decodeAudioFile])
     * @param fileName optional display name for UI
     */
    fun startFile(samples: ShortArray, fileName: String? = null) {
        if (isRunning) stop()
        resetEffects()

        fileSamples = samples
        filePlaybackPosition = 0

        _state.update {
            it.copy(
                isPlaying = true,
                mode = AudioMode.FILE,
                fileName = fileName
            )
        }

        isRunning = true
        processingThread = Thread(::filePlaybackLoop, "AudioEngine-File").apply {
            start()
        }
    }

    /** Stop any active playback or capture. */
    fun stop() {
        isRunning = false
        processingThread?.join(2000)
        processingThread = null

        audioRecord?.let {
            try { it.stop() } catch (_: IllegalStateException) {}
            it.release()
        }
        audioRecord = null

        audioTrack?.let {
            try { it.stop() } catch (_: IllegalStateException) {}
            it.release()
        }
        audioTrack = null

        _state.update {
            it.copy(
                isPlaying = false,
                inputLevel = 0f,
                outputLevel = 0f
            )
        }
    }

    /** Start capturing post-effects output to an internal buffer. */
    fun startRecording() {
        synchronized(recordedSamples) {
            recordedSamples.clear()
        }
        isRecordingOutput = true
        _state.update { it.copy(isRecording = true) }
    }

    /** Stop capturing and return recorded samples. */
    fun stopRecording(): ShortArray {
        isRecordingOutput = false
        _state.update { it.copy(isRecording = false) }
        synchronized(recordedSamples) {
            return recordedSamples.toShortArray()
        }
    }

    /** Release all resources. Call when the engine is no longer needed. */
    fun release() {
        stop()
        fileSamples = null
        synchronized(recordedSamples) {
            recordedSamples.clear()
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // Private – processing loops
    // ────────────────────────────────────────────────────────────────────

    /** Microphone capture → effects → playback loop. */
    @SuppressLint("MissingPermission") // Permission is checked in ViewModel / UI layer
    private fun microphoneLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        val bufferSize = max(minBufSize, 4096)
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE, CHANNEL_IN, ENCODING, bufferSize
        )
        val track = createAudioTrack(bufferSize)

        audioRecord = record
        audioTrack = track

        record.startRecording()
        track.play()

        val shortBuf = ShortArray(bufferSize / 2)
        val floatBuf = FloatArray(shortBuf.size)

        while (isRunning) {
            val read = record.read(shortBuf, 0, shortBuf.size)
            if (read > 0) {
                // Short → Float
                for (i in 0 until read) {
                    floatBuf[i] = shortBuf[i] / 32768f
                }

                val inLevel = peakLevel(floatBuf, read)

                // DSP: noise gate → reverb → echo
                noiseReduction.process(floatBuf)
                reverb.process(floatBuf)
                echo.process(floatBuf)

                val outLevel = peakLevel(floatBuf, read)

                // Float → Short (with clamp)
                for (i in 0 until read) {
                    floatBuf[i] = floatBuf[i].coerceIn(-1f, 1f)
                    shortBuf[i] = (floatBuf[i] * 32767f).toInt().toShort()
                }

                track.write(shortBuf, 0, read)
                captureIfRecording(shortBuf, read)

                _state.update {
                    it.copy(inputLevel = inLevel, outputLevel = outLevel)
                }
            }
        }
    }

    /** File playback → effects → output loop. */
    private fun filePlaybackLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        val bufferSize = max(minBufSize, 4096)
        val track = createAudioTrack(bufferSize)
        audioTrack = track
        track.play()

        val chunkSize = bufferSize / 2
        val shortBuf = ShortArray(chunkSize)
        val floatBuf = FloatArray(chunkSize)
        val samples = fileSamples ?: return

        while (isRunning && filePlaybackPosition < samples.size) {
            val remaining = samples.size - filePlaybackPosition
            val toRead = minOf(chunkSize, remaining)

            System.arraycopy(samples, filePlaybackPosition, shortBuf, 0, toRead)
            filePlaybackPosition += toRead

            // Pad rest with silence if partial chunk
            for (i in toRead until chunkSize) shortBuf[i] = 0

            // Short → Float
            for (i in 0 until toRead) {
                floatBuf[i] = shortBuf[i] / 32768f
            }
            for (i in toRead until chunkSize) floatBuf[i] = 0f

            val inLevel = peakLevel(floatBuf, toRead)

            // DSP: noise gate → reverb → echo
            noiseReduction.process(floatBuf)
            reverb.process(floatBuf)
            echo.process(floatBuf)

            val outLevel = peakLevel(floatBuf, toRead)

            // Float → Short (with clamp)
            for (i in 0 until chunkSize) {
                floatBuf[i] = floatBuf[i].coerceIn(-1f, 1f)
                shortBuf[i] = (floatBuf[i] * 32767f).toInt().toShort()
            }

            track.write(shortBuf, 0, toRead)
            captureIfRecording(shortBuf, toRead)

            _state.update {
                it.copy(inputLevel = inLevel, outputLevel = outLevel)
            }
        }

        // File finished
        if (isRunning) {
            isRunning = false
            _state.update {
                it.copy(
                    isPlaying = false,
                    inputLevel = 0f,
                    outputLevel = 0f
                )
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // Private – helpers
    // ────────────────────────────────────────────────────────────────────

    private fun createAudioTrack(bufferSize: Int): AudioTrack {
        return AudioTrack.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_OUT)
                    .setEncoding(ENCODING)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun peakLevel(buffer: FloatArray, count: Int): Float {
        var peak = 0f
        for (i in 0 until count) {
            val v = abs(buffer[i])
            if (v > peak) peak = v
        }
        return peak.coerceIn(0f, 1f)
    }

    private fun captureIfRecording(buffer: ShortArray, count: Int) {
        if (!isRecordingOutput) return
        synchronized(recordedSamples) {
            for (i in 0 until count) {
                recordedSamples.add(buffer[i])
            }
        }
    }

    private fun resetEffects() {
        noiseReduction.reset()
        reverb.reset()
        echo.reset()
    }
}
