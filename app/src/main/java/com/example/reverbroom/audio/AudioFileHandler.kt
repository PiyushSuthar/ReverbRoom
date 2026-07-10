package com.example.reverbroom.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Handles audio file import (decode) and export (WAV encode).
 *
 * Decoding uses [MediaExtractor] + [MediaCodec] to support WAV, MP3, AAC, and
 * any format that the device's media framework supports.
 *
 * Encoding writes a raw PCM WAV file with a manually-constructed header.
 */
object AudioFileHandler {

    private const val TIMEOUT_US = 10_000L

    /**
     * Decode an audio file pointed to by [uri] into a mono 16-bit PCM [ShortArray].
     *
     * The output is resampled to [targetSampleRate] only conceptually – the raw
     * decoded PCM is returned at whatever rate the codec produces (which for most
     * Android codecs matches the source). For reliable 44 100 Hz output the caller
     * should ensure the source file is 44 100 Hz or do post-decode resampling.
     *
     * @param context Android context for content-resolver access
     * @param uri     content:// or file:// URI of the source audio
     * @param targetSampleRate desired sample rate (informational; see note above)
     * @return decoded PCM samples as a [ShortArray]
     * @throws IllegalArgumentException if no audio track is found
     * @throws IllegalStateException    on codec errors
     */
    fun decodeAudioFile(
        context: Context,
        uri: Uri,
        targetSampleRate: Int = AudioEngine.SAMPLE_RATE
    ): ShortArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)

            // Find the first audio track
            val audioTrackIndex = findAudioTrack(extractor)
            require(audioTrackIndex >= 0) { "No audio track found in the provided file." }
            extractor.selectTrack(audioTrackIndex)

            val format = extractor.getTrackFormat(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalStateException("MIME type missing from media format.")
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(format, null, null, 0)
                codec.start()

                val output = decodeLoop(extractor, codec, channelCount)
                return output
            } finally {
                codec.stop()
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    /**
     * Encode a mono 16-bit PCM [ShortArray] as a WAV file written to [outputStream].
     *
     * @param samples    PCM samples
     * @param sampleRate sample rate in Hz
     * @param outputStream destination stream
     */
    fun encodeToWav(
        samples: ShortArray,
        sampleRate: Int,
        outputStream: OutputStream
    ) {
        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val blockAlign = numChannels * bitsPerSample / 8
        val dataSize = samples.size * 2  // 2 bytes per 16-bit sample
        val chunkSize = 36 + dataSize

        // Write RIFF / WAV header (44 bytes)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            // RIFF chunk descriptor
            put('R'.code.toByte()); put('I'.code.toByte())
            put('F'.code.toByte()); put('F'.code.toByte())
            putInt(chunkSize)
            put('W'.code.toByte()); put('A'.code.toByte())
            put('V'.code.toByte()); put('E'.code.toByte())

            // "fmt " sub-chunk
            put('f'.code.toByte()); put('m'.code.toByte())
            put('t'.code.toByte()); put(' '.code.toByte())
            putInt(16)                 // sub-chunk size (PCM)
            putShort(1)                // audio format = PCM
            putShort(numChannels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())

            // "data" sub-chunk
            put('d'.code.toByte()); put('a'.code.toByte())
            put('t'.code.toByte()); put('a'.code.toByte())
            putInt(dataSize)
        }

        outputStream.write(header.array())

        // Write PCM sample data in little-endian order
        val sampleBuffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) {
            sampleBuffer.putShort(s)
        }
        outputStream.write(sampleBuffer.array())
        outputStream.flush()
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /** Find the index of the first audio track in the extractor. Returns -1 if none. */
    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return -1
    }

    /**
     * Main decode loop – feeds input buffers to the codec and drains decoded
     * output into a [ShortArray].
     *
     * If the source is stereo (or multi-channel) the output is down-mixed to mono
     * by averaging channels.
     */
    private fun decodeLoop(
        extractor: MediaExtractor,
        codec: MediaCodec,
        channelCount: Int
    ): ShortArray {
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        val pcmBytes = ByteArrayOutputStream()

        while (true) {
            // ---- Feed input ----
            if (!inputDone) {
                val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inIdx >= 0) {
                    val inputBuffer = codec.getInputBuffer(inIdx)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(
                            inIdx, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputDone = true
                    } else {
                        val pts = extractor.sampleTime
                        codec.queueInputBuffer(inIdx, 0, sampleSize, pts, 0)
                        extractor.advance()
                    }
                }
            }

            // ---- Drain output ----
            val outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            if (outIdx >= 0) {
                val outputBuffer = codec.getOutputBuffer(outIdx)!!
                val chunk = ByteArray(info.size)
                outputBuffer.get(chunk)
                outputBuffer.clear()
                codec.releaseOutputBuffer(outIdx, false)
                pcmBytes.write(chunk)

                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    break
                }
            } else if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                // Codec may not have flushed the last buffer yet; keep draining
                continue
            }
        }

        // Convert raw bytes to ShortArray, down-mixing to mono if necessary
        val rawBytes = pcmBytes.toByteArray()
        val shortBuffer = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val totalShorts = shortBuffer.remaining()

        return if (channelCount == 1) {
            ShortArray(totalShorts).also { shortBuffer.get(it) }
        } else {
            // Down-mix to mono by averaging channels
            val monoCount = totalShorts / channelCount
            val mono = ShortArray(monoCount)
            for (i in 0 until monoCount) {
                var sum = 0
                for (ch in 0 until channelCount) {
                    sum += shortBuffer.get()
                }
                mono[i] = (sum / channelCount).toShort()
            }
            mono
        }
    }
}
