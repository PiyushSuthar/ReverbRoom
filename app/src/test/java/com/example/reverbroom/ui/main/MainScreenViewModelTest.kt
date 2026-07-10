package com.example.reverbroom.ui.main

import com.example.reverbroom.audio.EffectParams
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MainScreenViewModelTest {
  @Test
  fun uiState_hasDefaultEffectParams() = runTest {
    val viewModel = MainScreenViewModel()
    assertEquals(EffectParams(), viewModel.uiState.first().effectParams)
  }

  @Test
  fun updateEffectParams_updatesUiState() = runTest {
    val viewModel = MainScreenViewModel()
    val params = EffectParams(reverbDecay = 0.8f, noiseReductionEnabled = false)
    viewModel.updateEffectParams(params)
    assertEquals(params, viewModel.uiState.drop(1).first().effectParams)
  }
}
