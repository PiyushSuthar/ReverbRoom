package com.example.reverbroom.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NoiseControlOff
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.reverbroom.audio.EffectParams

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { AudioSettingsStore.load(context) }
    val state by AudioSettingsStore.state.collectAsStateWithLifecycle()
    val params = state.effectParams
    var presetName by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsCard("Audio Quality", Icons.Filled.GraphicEq) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioQuality.entries.forEach { quality ->
                        FilterChip(
                            selected = state.audioQuality == quality,
                            onClick = {
                                AudioSettingsStore.update(context) {
                                    it.copy(audioQuality = quality)
                                }
                            },
                            label = { Text("${quality.label} ${quality.bitDepth}-bit") }
                        )
                    }
                }
            }

            SettingsCard("Reverb", Icons.Filled.Tune) {
                SettingsSlider(
                    label = "Decay Time",
                    value = params.reverbDecay,
                    valueLabel = "${(params.reverbDecay * 5f).format(1)}s",
                    onValueChange = { updateParams(context, params.copy(reverbDecay = it)) }
                )
                SettingsSlider(
                    label = "Wet/Dry Mix",
                    value = params.reverbMix,
                    valueLabel = "${(params.reverbMix * 100).toInt()}%",
                    onValueChange = { updateParams(context, params.copy(reverbMix = it)) }
                )
                SettingsSlider(
                    label = "Room Size",
                    value = params.reverbRoomSize,
                    valueLabel = "${(params.reverbRoomSize * 100).toInt()}%",
                    onValueChange = { updateParams(context, params.copy(reverbRoomSize = it)) }
                )
                SettingsSlider(
                    label = "Width",
                    value = params.reverbWidth,
                    valueLabel = "${(params.reverbWidth * 100).toInt()}%",
                    onValueChange = { updateParams(context, params.copy(reverbWidth = it)) }
                )
                SettingsSlider(
                    label = "Damp",
                    value = params.reverbDamp,
                    valueLabel = "${(params.reverbDamp * 100).toInt()}%",
                    onValueChange = { updateParams(context, params.copy(reverbDamp = it)) }
                )
            }

            SettingsCard("Echo", Icons.Filled.GraphicEq) {
                SettingsSlider(
                    label = "Delay",
                    value = params.echoDelay,
                    valueLabel = "${(params.echoDelay * 1000).toInt()}ms",
                    onValueChange = { updateParams(context, params.copy(echoDelay = it)) }
                )
                SettingsSlider(
                    label = "Feedback",
                    value = params.echoFeedback,
                    valueLabel = "${(params.echoFeedback * 100).toInt()}%",
                    onValueChange = { updateParams(context, params.copy(echoFeedback = it)) }
                )
                SettingsSlider(
                    label = "Wet/Dry Mix",
                    value = params.echoMix,
                    valueLabel = "${(params.echoMix * 100).toInt()}%",
                    onValueChange = { updateParams(context, params.copy(echoMix = it)) }
                )
                SettingsSlider(
                    label = "Beats",
                    value = params.echoBeats,
                    valueLabel = "${(0.125f + params.echoBeats * 0.875f).format(2)}x",
                    onValueChange = { updateParams(context, params.copy(echoBeats = it)) }
                )
                SettingsSlider(
                    label = "Decay",
                    value = params.echoDecay,
                    valueLabel = "${(params.echoDecay * 100).toInt()}%",
                    onValueChange = { updateParams(context, params.copy(echoDecay = it)) }
                )
            }

            SettingsCard("Noise Reduction", Icons.Filled.NoiseControlOff) {
                ListItem(
                    headlineContent = { Text("Enable noise reduction") },
                    supportingContent = { Text("Reduces low-level background noise before effects") },
                    trailingContent = {
                        Switch(
                            checked = params.noiseReductionEnabled,
                            onCheckedChange = {
                                updateParams(context, params.copy(noiseReductionEnabled = it))
                            }
                        )
                    }
                )
                SettingsSlider(
                    label = "Gate Threshold",
                    value = params.noiseGateThreshold,
                    valueRange = 0.005f..0.08f,
                    valueLabel = "${(params.noiseGateThreshold * 1000).toInt()}",
                    onValueChange = { updateParams(context, params.copy(noiseGateThreshold = it)) }
                )
                SettingsSlider(
                    label = "Reduction Strength",
                    value = params.noiseReductionStrength,
                    valueLabel = "${(params.noiseReductionStrength * 100).toInt()}%",
                    onValueChange = { updateParams(context, params.copy(noiseReductionStrength = it)) }
                )
            }

            SettingsCard("Presets", Icons.Filled.Save) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { Text("Preset name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            AudioSettingsStore.savePreset(context, presetName)
                            presetName = ""
                        }
                    ) {
                        Text("Save")
                    }
                }
                if (state.presets.isEmpty()) {
                    Text(
                        "No presets saved",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    state.presets.forEach { preset ->
                        ListItem(
                            headlineContent = { Text(preset.name) },
                            supportingContent = { Text(preset.params.summary()) },
                            trailingContent = {
                                Row {
                                    TextButton(onClick = { AudioSettingsStore.loadPreset(context, preset) }) {
                                        Text("Load")
                                    }
                                    IconButton(onClick = { AudioSettingsStore.deletePreset(context, preset) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete preset")
                                    }
                                }
                            }
                        )
                    }
                }
            }

            SettingsCard("Preferences", Icons.Filled.Mic) {
                ListItem(
                    headlineContent = { Text("Auto-start microphone") },
                    trailingContent = {
                        Switch(
                            checked = state.autoStartMic,
                            onCheckedChange = { checked ->
                                AudioSettingsStore.update(context) { it.copy(autoStartMic = checked) }
                            }
                        )
                    }
                )
                ListItem(
                    headlineContent = { Text("Keep screen on") },
                    trailingContent = {
                        Switch(
                            checked = state.keepScreenOn,
                            onCheckedChange = { checked ->
                                AudioSettingsStore.update(context) { it.copy(keepScreenOn = checked) }
                            }
                        )
                    }
                )
            }
        }
    }
}

private fun updateParams(context: android.content.Context, params: EffectParams) {
    AudioSettingsStore.updateEffectParams(context, params)
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
private fun SettingsSlider(
    label: String,
    value: Float,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(valueLabel, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
        Spacer(Modifier.height(2.dp))
    }
}

private fun Float.format(decimals: Int): String = "%.${decimals}f".format(this)

private fun EffectParams.summary(): String {
    return "Rev ${(reverbMix * 100).toInt()}%, Echo ${(echoMix * 100).toInt()}%, NR ${(noiseReductionStrength * 100).toInt()}%"
}
