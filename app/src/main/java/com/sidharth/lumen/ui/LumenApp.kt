package com.sidharth.lumen.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sidharth.lumen.feature.library.LibraryScreen
import com.sidharth.lumen.feature.settings.SettingsScreen

/**
 * Single-activity host. Routes are plain strings for now; they become type-safe once
 * arguments appear (book id in M2) and the serialization plugin earns its place.
 */
object Routes {
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
}

@Composable
fun LumenApp() {
    val navController = rememberNavController()

    Surface(color = MaterialTheme.colorScheme.background) {
        NavHost(navController = navController, startDestination = Routes.LIBRARY) {
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
