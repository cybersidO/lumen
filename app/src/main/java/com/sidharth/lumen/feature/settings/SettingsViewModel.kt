package com.sidharth.lumen.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidharth.lumen.core.settings.AppSettings
import com.sidharth.lumen.core.settings.GestureSettings
import com.sidharth.lumen.core.settings.SettingsRepository
import com.sidharth.lumen.core.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppSettings.Defaults,
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun updateGestures(transform: (GestureSettings) -> GestureSettings) {
        viewModelScope.launch { settingsRepository.updateGestures(transform) }
    }
}
