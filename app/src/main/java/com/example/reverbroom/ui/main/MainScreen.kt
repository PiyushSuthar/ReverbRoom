package com.example.reverbroom.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.NoiseControlOff
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reverbroom.audio.AudioMode
import com.example.reverbroom.audio.EffectParams
import com.example.reverbroom.theme.AudioAmber
import com.example.reverbroom.theme.AudioGreen
import com.example.reverbroom.theme.AudioRed
import com.example.reverbroom.theme.GlowPurple
import com.example.reverbroom.theme.GlowPurpleAlpha
import com.example.reverbroom.theme.WaveformPurple
import com.example.reverbroom.theme.WaveformPurpleLight
import com.example.reverbroom.ui.settings.AudioSettingsStore
import kotlin.math.PI
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenFiles: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel { MainScreenViewModel() },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by AudioSettingsStore.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startMicrophone()
        } else {
            Toast.makeText(context, "Microphone permission required", Toast.LENGTH_SHORT).show()
        }
    }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadFile(context, it) }
    }

    // Save file launcher
    val saveFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/wav")
    ) { uri: Uri? ->
        uri?.let { viewModel.saveProcessedFile(context, it) }
    }

    // Show save message
    LaunchedEffect(state.saveMessage) {
        state.saveMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSaveMessage()
        }
    }

    LaunchedEffect(Unit) {
        AudioSettingsStore.load(context)
    }

    LaunchedEffect(settings.effectParams) {
        viewModel.updateEffectParams(settings.effectParams)
    }

    var showSaveDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("ReverbRoom")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenFiles) {
                        Icon(Icons.Filled.Folder, contentDescription = "Saved Files")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // === Audio Visualizer ===
            AudioVisualizerCard(
                inputLevel = state.audioState.inputLevel,
                outputLevel = state.audioState.outputLevel,
                isPlaying = state.audioState.isPlaying,
                isRecording = state.audioState.isRecording,
                mode = state.audioState.mode,
                fileName = state.audioState.fileName
            )

            // === Transport Controls ===
            TransportControlsCard(
                isPlaying = state.audioState.isPlaying,
                isRecording = state.audioState.isRecording,
                mode = state.audioState.mode,
                hasFile = state.audioState.fileName != null,
                onMicToggle = {
                    if (state.audioState.isPlaying && state.audioState.mode == AudioMode.MICROPHONE) {
                        viewModel.stopPlayback()
                    } else {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) {
                            viewModel.startMicrophone()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                onFileOpen = { filePickerLauncher.launch("audio/*") },
                onFilePlayPause = {
                    if (state.audioState.isPlaying && state.audioState.mode == AudioMode.FILE) {
                        viewModel.stopPlayback()
                    } else {
                        viewModel.playFile()
                    }
                },
                onRecordToggle = {
                    if (state.audioState.isRecording) {
                        viewModel.stopRecording()
                    } else {
                        viewModel.startRecording()
                    }
                },
                onSave = { showSaveDialog = true }
            )

            // === Reverb Controls ===
            EffectCard(
                title = "Reverb",
                icon = Icons.Outlined.Waves,
                iconTint = WaveformPurple
            ) {
                LabeledSlider(
                    label = "Decay Time",
                    value = state.effectParams.reverbDecay,
                    onValueChange = {
                        viewModel.updateEffectParams(state.effectParams.copy(reverbDecay = it))
                        AudioSettingsStore.updateEffectParams(context, state.effectParams.copy(reverbDecay = it))
                    },
                    valueLabel = "${(state.effectParams.reverbDecay * 5).format(1)}s"
                )
                Spacer(Modifier.height(8.dp))
                LabeledSlider(
                    label = "Wet/Dry Mix",
                    value = state.effectParams.reverbMix,
                    onValueChange = {
                        viewModel.updateEffectParams(state.effectParams.copy(reverbMix = it))
                        AudioSettingsStore.updateEffectParams(context, state.effectParams.copy(reverbMix = it))
                    },
                    valueLabel = "${(state.effectParams.reverbMix * 100).toInt()}%"
                )
            }

            // === Echo Controls ===
            EffectCard(
                title = "Echo",
                icon = Icons.Outlined.GraphicEq,
                iconTint = AudioGreen
            ) {
                LabeledSlider(
                    label = "Delay",
                    value = state.effectParams.echoDelay,
                    onValueChange = {
                        viewModel.updateEffectParams(state.effectParams.copy(echoDelay = it))
                        AudioSettingsStore.updateEffectParams(context, state.effectParams.copy(echoDelay = it))
                    },
                    valueLabel = "${(state.effectParams.echoDelay * 1000).toInt()}ms"
                )
                Spacer(Modifier.height(8.dp))
                LabeledSlider(
                    label = "Feedback",
                    value = state.effectParams.echoFeedback,
                    onValueChange = {
                        viewModel.updateEffectParams(state.effectParams.copy(echoFeedback = it))
                        AudioSettingsStore.updateEffectParams(context, state.effectParams.copy(echoFeedback = it))
                    },
                    valueLabel = "${(state.effectParams.echoFeedback * 100).toInt()}%"
                )
                Spacer(Modifier.height(8.dp))
                LabeledSlider(
                    label = "Wet/Dry Mix",
                    value = state.effectParams.echoMix,
                    onValueChange = {
                        viewModel.updateEffectParams(state.effectParams.copy(echoMix = it))
                        AudioSettingsStore.updateEffectParams(context, state.effectParams.copy(echoMix = it))
                    },
                    valueLabel = "${(state.effectParams.echoMix * 100).toInt()}%"
                )
            }

            EffectCard(
                title = "Noise Reduction",
                icon = Icons.Filled.NoiseControlOff,
                iconTint = AudioAmber
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enabled", style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.material3.Switch(
                        checked = state.effectParams.noiseReductionEnabled,
                        onCheckedChange = {
                            val params = state.effectParams.copy(noiseReductionEnabled = it)
                            viewModel.updateEffectParams(params)
                            AudioSettingsStore.updateEffectParams(context, params)
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
                LabeledSlider(
                    label = "Gate Threshold",
                    value = state.effectParams.noiseGateThreshold,
                    onValueChange = {
                        val params = state.effectParams.copy(noiseGateThreshold = it)
                        viewModel.updateEffectParams(params)
                        AudioSettingsStore.updateEffectParams(context, params)
                    },
                    valueRange = 0.005f..0.08f,
                    valueLabel = "${(state.effectParams.noiseGateThreshold * 1000).toInt()}"
                )
            }

            // === Reset Button ===
            OutlinedButton(
                onClick = {
                    viewModel.resetEffects()
                    AudioSettingsStore.updateEffectParams(context, EffectParams())
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.RestartAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Reset All Effects")
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // Save dialog
    if (showSaveDialog) {
        SaveFormatDialog(
            onDismiss = { showSaveDialog = false },
            onSaveLibrary = {
                showSaveDialog = false
                viewModel.saveToLibrary(context)
            },
            onExport = { format ->
                showSaveDialog = false
                saveFileLauncher.launch("ReverbRoom_${System.currentTimeMillis()}.$format")
            }
        )
    }
}

// === Audio Visualizer Card ===
@Composable
private fun AudioVisualizerCard(
    inputLevel: Float,
    outputLevel: Float,
    isPlaying: Boolean,
    isRecording: Boolean,
    mode: AudioMode,
    fileName: String?
) {
    val infiniteTransition = rememberInfiniteTransition(label = "viz")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 0.6f else 0.0f,
        animationSpec = tween(500),
        label = "glowAlpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .drawBehind {
                    // Subtle glow background when playing
                    if (isPlaying) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    GlowPurpleAlpha.copy(alpha = glowAlpha * 0.3f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = size.minDimension * 0.7f
                            )
                        )
                    }
                }
        ) {
            // Waveform canvas
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                val width = size.width
                val height = size.height
                val centerY = height / 2f

                if (isPlaying) {
                    // Animated waveform
                    val path = Path()
                    val amplitude = outputLevel.coerceIn(0.05f, 1f) * (height * 0.35f)
                    val points = 100

                    path.moveTo(0f, centerY)
                    for (i in 0..points) {
                        val x = (i / points.toFloat()) * width
                        val angle = (i / points.toFloat()) * 4f * PI.toFloat() + wavePhase
                        val y = centerY + sin(angle) * amplitude *
                                (1f - 0.3f * sin((i / points.toFloat()) * PI.toFloat()))
                        path.lineTo(x, y)
                    }

                    drawPath(
                        path = path,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                WaveformPurple.copy(alpha = 0.8f),
                                WaveformPurpleLight,
                                WaveformPurple.copy(alpha = 0.8f)
                            )
                        ),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Secondary wave (echo effect visualization)
                    val echoPath = Path()
                    val echoAmplitude = amplitude * 0.5f
                    echoPath.moveTo(0f, centerY)
                    for (i in 0..points) {
                        val x = (i / points.toFloat()) * width
                        val angle = (i / points.toFloat()) * 4f * PI.toFloat() + wavePhase + 1.5f
                        val y = centerY + sin(angle) * echoAmplitude
                        echoPath.lineTo(x, y)
                    }
                    drawPath(
                        path = echoPath,
                        color = WaveformPurple.copy(alpha = 0.25f),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                } else {
                    // Flat line when idle
                    drawLine(
                        color = WaveformPurple.copy(alpha = 0.3f),
                        start = Offset(0f, centerY),
                        end = Offset(width, centerY),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // Status indicators
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusDot(
                        color = if (isPlaying) AudioGreen else MaterialTheme.colorScheme.outline,
                        pulsate = isPlaying
                    )
                    Text(
                        text = when {
                            isRecording -> "● Recording"
                            isPlaying && mode == AudioMode.MICROPHONE -> "Live Mic"
                            isPlaying && mode == AudioMode.FILE -> "Playing File"
                            else -> "Idle"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (fileName != null) {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }

            // Level meters
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LevelMeter(label = "IN", level = inputLevel)
                LevelMeter(label = "OUT", level = outputLevel)
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color, pulsate: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (pulsate) 0.4f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
private fun LevelMeter(label: String, level: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(60.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            val animatedLevel by animateFloatAsState(
                targetValue = level.coerceIn(0f, 1f),
                animationSpec = tween(100),
                label = "level"
            )
            val meterColor by animateColorAsState(
                targetValue = when {
                    level > 0.85f -> AudioRed
                    level > 0.6f -> AudioAmber
                    else -> AudioGreen
                },
                animationSpec = tween(200),
                label = "meterColor"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp * animatedLevel)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(4.dp))
                    .background(meterColor)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// === Transport Controls Card ===
@Composable
private fun TransportControlsCard(
    isPlaying: Boolean,
    isRecording: Boolean,
    mode: AudioMode,
    hasFile: Boolean,
    onMicToggle: () -> Unit,
    onFileOpen: () -> Unit,
    onFilePlayPause: () -> Unit,
    onRecordToggle: () -> Unit,
    onSave: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Controls",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mic button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledIconButton(
                        onClick = onMicToggle,
                        modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isPlaying && mode == AudioMode.MICROPHONE)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            if (isPlaying && mode == AudioMode.MICROPHONE) Icons.Filled.MicOff
                            else Icons.Filled.Mic,
                            contentDescription = "Toggle Microphone",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isPlaying && mode == AudioMode.MICROPHONE) "Stop" else "Mic",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                // File open button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledTonalIconButton(
                        onClick = onFileOpen,
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            Icons.Filled.FileOpen,
                            contentDescription = "Open File",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("File", style = MaterialTheme.typography.labelSmall)
                }

                // Play/Pause file button
                AnimatedVisibility(visible = hasFile) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledIconButton(
                            onClick = onFilePlayPause,
                            modifier = Modifier.size(56.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (isPlaying && mode == AudioMode.FILE)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.tertiaryContainer
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                if (isPlaying && mode == AudioMode.FILE) Icons.Filled.Stop
                                else Icons.Filled.PlayArrow,
                                contentDescription = "Play/Stop File",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (isPlaying && mode == AudioMode.FILE) "Stop" else "Play",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // Record button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val recordColor by animateColorAsState(
                        targetValue = if (isRecording) AudioRed else MaterialTheme.colorScheme.surfaceContainerHighest,
                        label = "recordColor"
                    )
                    FilledIconButton(
                        onClick = onRecordToggle,
                        modifier = Modifier.size(56.dp),
                        enabled = isPlaying,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = recordColor,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            if (isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                            contentDescription = "Record",
                            tint = if (isRecording) Color.White
                            else if (isPlaying) AudioRed
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isRecording) "Stop Rec" else "Record",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                // Save button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledTonalIconButton(
                        onClick = onSave,
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            Icons.Filled.Save,
                            contentDescription = "Save",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Save", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// === Effect Card ===
@Composable
private fun EffectCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

// === Labeled Slider ===
@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueLabel: String,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        )
    }
}

// === Save Format Dialog ===
@Composable
private fun SaveFormatDialog(
    onDismiss: () -> Unit,
    onSaveLibrary: () -> Unit,
    onExport: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Save Audio", fontWeight = FontWeight.SemiBold)
        },
        text = {
            Text("Choose an output format for your processed audio.")
        },
        confirmButton = {
            Button(
                onClick = onSaveLibrary,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save to Library")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onExport("wav") }) {
                    Text("Export WAV")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

private fun Float.format(decimals: Int): String = "%.${decimals}f".format(this)
