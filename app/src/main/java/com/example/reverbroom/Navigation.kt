package com.example.reverbroom

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.reverbroom.ui.files.SavedFilesScreen
import com.example.reverbroom.ui.main.MainScreen
import com.example.reverbroom.ui.settings.SettingsScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          MainScreen(
            onOpenFiles = { backStack.add(SavedFiles) },
            onOpenSettings = { backStack.add(Settings) }
          )
        }
        entry<SavedFiles> {
          SavedFilesScreen(onBack = { backStack.removeLastOrNull() })
        }
        entry<Settings> {
          SettingsScreen(onBack = { backStack.removeLastOrNull() })
        }
      },
  )
}
