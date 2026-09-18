package com.sidharth.lumen.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sidharth.lumen.R
import com.sidharth.lumen.core.settings.ResolvedTheme
import com.sidharth.lumen.feature.library.LibraryScreen
import com.sidharth.lumen.feature.notes.NotesScreen
import com.sidharth.lumen.feature.reader.ReaderScreen
import com.sidharth.lumen.feature.reader.input.VolumeKeyDispatcher
import com.sidharth.lumen.feature.review.ReviewScreen
import com.sidharth.lumen.feature.settings.SettingsScreen
import com.sidharth.lumen.feature.stats.HomeScreen

/**
 * Single-activity host. Two top-level tabs — Home (the reading dashboard) and Library —
 * sit behind a bottom bar; everything else (reader, notes, review, settings) is a
 * full-screen destination pushed on top, where the bar would only steal height.
 *
 * Routes are plain strings; the reader's `bookId` is declared as a typed nav argument
 * so `SavedStateHandle` hands the ViewModel a Long, not a String.
 */
object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val NOTES = "notes"
    const val REVIEW = "review"
    const val READER = "reader/{bookId}?highlightId={highlightId}"

    /** [highlightId] is set when arriving from the notes list, to jump to that spot. */
    fun reader(bookId: Long, highlightId: Long? = null) =
        "reader/$bookId?highlightId=${highlightId ?: -1L}"

    val tabs = listOf(HOME, LIBRARY)
}

const val TabHomeTag = "tab_home"
const val TabLibraryTag = "tab_library"

private data class Tab(
    val route: String,
    val label: Int,
    val selected: ImageVector,
    val unselected: ImageVector,
    val tag: String,
)

private val Tabs = listOf(
    Tab(Routes.HOME, R.string.tab_home, Icons.Filled.Home, Icons.Outlined.Home, TabHomeTag),
    Tab(Routes.LIBRARY, R.string.tab_library, Icons.Filled.MenuBook, Icons.Outlined.MenuBook, TabLibraryTag),
)

@Composable
fun LumenApp(resolvedTheme: ResolvedTheme, volumeKeys: VolumeKeyDispatcher) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.hierarchy?.firstOrNull { it.route in Routes.tabs }?.route
    val showBar = currentRoute != null

    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            // The tab screens carry their own top bars, which already inset for the status
            // bar; letting this outer Scaffold inset too doubles the gap. Only the bottom
            // bar's height should reach them through `padding`.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showBar) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        Tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        navController.navigate(tab.route) {
                                            // Standard tab behaviour: one copy of each tab, its
                                            // scroll position kept while you are on the other.
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { Icon(if (selected) tab.selected else tab.unselected, contentDescription = null) },
                                label = { Text(stringResource(tab.label)) },
                                modifier = Modifier.testTag(tab.tag),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                // Only the tabbed screens sit above the bar; full-screen destinations use
                // the whole window, so the padding is applied per-screen below.
                modifier = Modifier,
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        modifier = Modifier.padding(padding),
                    )
                }
                composable(Routes.LIBRARY) {
                    LibraryScreen(
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onOpenNotes = { navController.navigate(Routes.NOTES) },
                        onOpenBook = { bookId -> navController.navigate(Routes.reader(bookId)) },
                        modifier = Modifier.padding(padding),
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.NOTES) {
                    NotesScreen(
                        onBack = { navController.popBackStack() },
                        onOpenReview = { navController.navigate(Routes.REVIEW) },
                        onOpenBook = { bookId, highlightId ->
                            navController.navigate(Routes.reader(bookId, highlightId))
                        },
                    )
                }
                composable(Routes.REVIEW) {
                    ReviewScreen(onBack = { navController.popBackStack() })
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
                        volumeKeys = volumeKeys,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
