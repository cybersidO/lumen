package com.sidharth.lumen

import android.app.Application
import android.os.Build
import com.sidharth.lumen.core.di.ApplicationScope
import com.sidharth.lumen.core.diagnostics.CrashLog
import com.sidharth.lumen.core.settings.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LumenApplication : Application() {

    @Inject lateinit var settings: SettingsRepository

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    @Inject lateinit var crashLog: CrashLog

    override fun onCreate() {
        super.onCreate()
        installCrashLog()
        // Defaults changed in 1.4.0 and again in 1.6.0. Applied here rather than at the
        // first read so an existing install gets them once, before any screen has shown
        // the old values.
        scope.launch {
            settings.applyReadingDefaultsV2()
            settings.applyGestureDefaultsV3()
        }
    }

    /**
     * Records crashes to the app's own storage, then lets Android do what it would have
     * done anyway.
     *
     * Lumen reports nothing to anyone, which also means a crash would otherwise be
     * invisible to the one person who could act on it. The previous handler is always
     * called: swallowing it would turn a crash into a freeze, which is worse than a
     * crash. Settings → Diagnostics shows whatever is in here.
     */
    private fun installCrashLog() {
        // Read once, here, rather than inside the handler: by then the process is dying
        // and the PackageManager is one more thing that could fail.
        val versionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "unknown"
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            crashLog.record(
                throwable = error,
                meta = "Lumen $versionName · Android ${Build.VERSION.RELEASE} " +
                    "· ${Build.MANUFACTURER} ${Build.MODEL} · thread ${thread.name}",
                atMillis = System.currentTimeMillis(),
            )
            previous?.uncaughtException(thread, error)
        }
    }
}
