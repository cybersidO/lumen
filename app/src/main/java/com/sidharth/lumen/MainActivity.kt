package com.sidharth.lumen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidharth.lumen.ui.LumenApp
import com.sidharth.lumen.ui.theme.LumenTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            // Draw nothing until the persisted theme has been read. The window keeps its
            // Theme.Lumen.Starting background (which tracks system light/dark) for that
            // frame or two, so the user never sees a light app flash into dark.
            val loaded = settings ?: return@setContent

            LumenTheme(themeMode = loaded.themeMode) {
                LumenApp()
            }
        }
    }
}
