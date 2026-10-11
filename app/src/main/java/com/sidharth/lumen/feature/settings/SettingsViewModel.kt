package com.sidharth.lumen.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidharth.lumen.core.diagnostics.CrashLog
import com.sidharth.lumen.core.settings.AppSettings
import com.sidharth.lumen.core.settings.GestureSettings
import com.sidharth.lumen.core.settings.ReadingSettings
import com.sidharth.lumen.core.settings.SettingsRepository
import com.sidharth.lumen.core.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val crashLog: CrashLog,
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

    fun updateReading(transform: (ReadingSettings) -> ReadingSettings) {
        viewModelScope.launch { settingsRepository.updateReading(transform) }
    }

    // --- diagnostics (M15) -------------------------------------------------------------

    /**
     * What the crash log holds, re-read rather than observed.
     *
     * A file written by a dying process has no change notification to subscribe to, and
     * the only moments it can have changed are a restart and a clear — both of which pass
     * through here anyway.
     */
    private val _crashReport = MutableStateFlow(readCrashReport())
    val crashReport: StateFlow<CrashReport> = _crashReport

    data class CrashReport(val count: Int, val text: String?)

    private fun readCrashReport() = CrashReport(
        count = crashLog.entryCount(),
        text = crashLog.read(),
    )

    fun refreshCrashReport() {
        _crashReport.value = readCrashReport()
    }

    fun clearCrashReport() {
        crashLog.clear()
        _crashReport.value = readCrashReport()
    }
}
