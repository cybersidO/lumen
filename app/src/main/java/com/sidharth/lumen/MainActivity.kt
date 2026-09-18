package com.sidharth.lumen

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidharth.lumen.feature.reader.input.VolumeKeyDispatcher
import com.sidharth.lumen.ui.LumenApp
import com.sidharth.lumen.ui.theme.LumenTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * A [FragmentActivity] rather than a plain ComponentActivity: Readium's EPUB renderer is a
 * Fragment, so the app needs a FragmentManager to host it. The architecture is still
 * single-activity — there is exactly one.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var volumeKeys: VolumeKeyDispatcher

    // Volume keys turn pages while a book is open (M10). The dispatcher only claims a
    // press when a reader has registered for it, so everywhere else volume stays volume.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        volumeKeys.onKeyDown(keyCode) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
        volumeKeys.onKeyUp(keyCode) || super.onKeyUp(keyCode, event)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Drop any saved fragment state before the FragmentManager can act on it.
        //
        // The navigator fragment can only be constructed by a FragmentFactory that already
        // holds an open Publication, and opening a publication is asynchronous file I/O —
        // so after process death there is nothing to restore it *with*, and letting the
        // FragmentManager try produces a crash on relaunch.
        //
        // Discarding it costs nothing, because fragment state was never the source of
        // truth for where the reader was: the locator in the database is, and that is what
        // §M2 requires to survive a process kill.
        savedInstanceState?.remove(SAVED_FRAGMENTS_KEY)

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            // Draw nothing until the persisted theme has been read. The window keeps its
            // Theme.Lumen.Starting background (which tracks system light/dark) for that
            // frame or two, so the user never sees a light app flash into dark.
            val loaded = settings ?: return@setContent

            val systemDark = isSystemInDarkTheme()
            val resolved = remember(loaded.themeMode, systemDark) {
                loaded.themeMode.resolve(systemDark)
            }

            LumenTheme(themeMode = loaded.themeMode) {
                LumenApp(resolvedTheme = resolved, volumeKeys = volumeKeys)
            }
        }
    }

    private companion object {
        const val SAVED_FRAGMENTS_KEY = "android:support:fragments"
    }
}
