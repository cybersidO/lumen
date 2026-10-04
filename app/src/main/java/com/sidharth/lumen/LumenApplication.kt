package com.sidharth.lumen

import android.app.Application
import com.sidharth.lumen.core.di.ApplicationScope
import com.sidharth.lumen.core.settings.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LumenApplication : Application() {

    @Inject lateinit var settings: SettingsRepository

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Two defaults changed in 1.4.0. Applied here rather than at the first read so an
        // existing install gets them once, before any screen has shown the old values.
        scope.launch { settings.applyReadingDefaultsV2() }
    }
}
