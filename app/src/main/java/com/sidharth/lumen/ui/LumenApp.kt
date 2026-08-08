package com.sidharth.lumen.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sidharth.lumen.core.settings.ResolvedTheme
import com.sidharth.lumen.feature.library.LibraryScreen
import com.sidharth.lumen.feature.notes.NotesScreen
import com.sidharth.lumen.feature.reader.ReaderScreen
import com.sidharth.lumen.feature.settings.SettingsScreen

/**
 * Single-activity host. Routes are plain strings; the reader's `bookId` is declared as a
 * typed nav argument so `SavedStateHandle` hands the ViewModel a Long, not a String.
 */
object Routes {
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val NOTES = "notes"
    const val READER = "reader/{bookId}?highlightId={highlightId}"

    /** [highlightId] is set when arriving from the notes list, to jump to that spot. */
    fun reader(bookId: Long, highlightId: Long? = null) =
        "reader/$bookId?highlightId=${highlightId ?: -1L}"
}

@Composable
fun LumenApp(resolvedTheme: ResolvedTheme) {
    val navController = rememberNavController()

    Surface(color = MaterialTheme.colorScheme.background) {
        NavHost(navController = navController, startDestination = Routes.LIBRARY) {
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenNotes = { navController.navigate(Routes.NOTES) },
                    onOpenBook = { bookId -> navController.navigate(Routes.reader(bookId)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.NOTES) {
                NotesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenBook = { bookId, highlightId ->
                        navController.navigate(Routes.reader(bookId, highlightId))
                    },
                )
            }
            composable(
                route = Routes.READER,
                arguments = listOf(
                    navArgument("bookId") { type = NavType.LongType },
                    navArgument("highlightId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                ),
            ) {
                ReaderScreen(
                    resolvedTheme = resolvedTheme,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
