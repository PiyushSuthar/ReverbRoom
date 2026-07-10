package com.example.reverbroom.ui.files

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

// ── Data models ─────────────────────────────────────────────────────────

data class SavedFileItem(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isPlaying: Boolean = false
)

data class SavedFilesUiState(
    val files: List<SavedFileItem> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null
)

// ── ViewModel ───────────────────────────────────────────────────────────

class SavedFilesViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SavedFilesUiState())
    val uiState: StateFlow<SavedFilesUiState> = _uiState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlayingPath: String? = null

    // ── File loading ────────────────────────────────────────────────────

    fun loadFiles(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }

            val dir = context.getExternalFilesDir(null)
            val wavFiles = dir?.listFiles { file ->
                file.isFile && file.extension.equals("wav", ignoreCase = true)
            }

            val items = wavFiles
                ?.sortedByDescending { it.lastModified() }
                ?.map { file ->
                    SavedFileItem(
                        name = file.name,
                        path = file.absolutePath,
                        sizeBytes = file.length(),
                        lastModified = file.lastModified()
                    )
                } ?: emptyList()

            _uiState.update {
                it.copy(
                    files = items,
                    isLoading = false,
                    message = null
                )
            }
        }
    }

    // ── Playback ────────────────────────────────────────────────────────

    fun playFile(context: Context, file: SavedFileItem) {
        // If already playing this file, stop it
        if (currentlyPlayingPath == file.path) {
            stopPlayback()
            return
        }

        // Stop any current playback first
        releaseMediaPlayer()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.path)
                prepare()
                setOnCompletionListener {
                    // Reset playing state when playback completes naturally
                    currentlyPlayingPath = null
                    _uiState.update { state ->
                        state.copy(
                            files = state.files.map { f ->
                                f.copy(isPlaying = false)
                            }
                        )
                    }
                }
                start()
            }
            currentlyPlayingPath = file.path

            _uiState.update { state ->
                state.copy(
                    files = state.files.map { f ->
                        f.copy(isPlaying = f.path == file.path)
                    }
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(message = "Playback failed: ${e.message}") }
        }
    }

    fun stopPlayback() {
        releaseMediaPlayer()
        currentlyPlayingPath = null
        _uiState.update { state ->
            state.copy(
                files = state.files.map { f ->
                    f.copy(isPlaying = false)
                }
            )
        }
    }

    // ── Delete ──────────────────────────────────────────────────────────

    fun deleteFile(context: Context, file: SavedFileItem) {
        // Stop playback if deleting the currently playing file
        if (currentlyPlayingPath == file.path) {
            stopPlayback()
        }

        viewModelScope.launch(Dispatchers.IO) {
            val deleted = File(file.path).delete()
            if (deleted) {
                _uiState.update { state ->
                    state.copy(
                        files = state.files.filter { it.path != file.path },
                        message = "${file.name} deleted"
                    )
                }
            } else {
                _uiState.update { it.copy(message = "Failed to delete ${file.name}") }
            }
        }
    }

    // ── Share ───────────────────────────────────────────────────────────

    fun shareFile(context: Context, file: SavedFileItem) {
        try {
            val fileObj = File(file.path)
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                fileObj
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/wav"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share ${file.name}")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            // Fallback: share with direct file Uri
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/wav"
                    putExtra(Intent.EXTRA_STREAM, Uri.fromFile(File(file.path)))
                }
                val chooser = Intent.createChooser(shareIntent, "Share ${file.name}")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            } catch (e2: Exception) {
                _uiState.update { it.copy(message = "Share failed: ${e2.message}") }
            }
        }
    }

    // ── Message dismissal ───────────────────────────────────────────────

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    // ── Lifecycle ───────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        releaseMediaPlayer()
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (_: Exception) { }
        }
        mediaPlayer = null
    }
}
