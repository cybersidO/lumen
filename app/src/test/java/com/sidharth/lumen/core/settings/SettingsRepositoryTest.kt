package com.sidharth.lumen.core.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** DataStore refuses two live instances over one file, so each store gets its own. */
    private fun store(scope: CoroutineScope, file: File) =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    private fun freshFile(name: String) = File(tmp.root, "$name.preferences_pb")

    @Test
    fun `defaults to follow-system before anything is written`() = runTest {
        val repo = SettingsRepository(store(backgroundScope, freshFile("defaults")))
        repo.settings.test {
            assertEquals(ThemeMode.SYSTEM, awaitItem().themeMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `writing a theme emits it`() = runTest {
        val repo = SettingsRepository(store(backgroundScope, freshFile("write")))
        repo.settings.test {
            assertEquals(ThemeMode.SYSTEM, awaitItem().themeMode)
            repo.setThemeMode(ThemeMode.AMOLED)
            assertEquals(ThemeMode.AMOLED, awaitItem().themeMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The M0 "done when": the choice survives the process. A second repository over the
     * same file, after the first is fully torn down, is the unit-test stand-in for a
     * cold start.
     */
    @Test
    fun `theme survives a new repository over the same file`() = runTest {
        val file = freshFile("persist")

        // DataStore releases its per-file lock from a job completion handler, so the
        // first instance has to be fully torn down before the second can open the file.
        val writerJob = Job()
        val writerScope = CoroutineScope(writerJob + StandardTestDispatcher(testScheduler))
        SettingsRepository(store(writerScope, file)).setThemeMode(ThemeMode.SEPIA)
        writerJob.cancel()
        advanceUntilIdle()
        writerJob.join()

        val second = SettingsRepository(store(backgroundScope, file))
        second.settings.test {
            assertEquals(ThemeMode.SEPIA, awaitItem().themeMode)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
