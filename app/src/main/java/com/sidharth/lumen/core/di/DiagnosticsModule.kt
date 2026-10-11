package com.sidharth.lumen.core.di

import android.content.Context
import com.sidharth.lumen.core.diagnostics.CrashLog
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DiagnosticsModule {

    /**
     * The log lives in the app's private files directory, which is excluded from backup
     * along with everything else (`allowBackup="false"`), so a stack trace never leaves
     * the phone by accident.
     */
    @Provides
    @Singleton
    fun provideCrashLog(@ApplicationContext context: Context): CrashLog =
        CrashLog(File(context.filesDir, CrashLog.FILE_NAME))
}
