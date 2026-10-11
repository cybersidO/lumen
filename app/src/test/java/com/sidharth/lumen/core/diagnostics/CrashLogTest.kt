package com.sidharth.lumen.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CrashLogTest {

    @get:Rule val folder = TemporaryFolder()

    private fun log(maxEntries: Int = 5) =
        CrashLog(File(folder.root, CrashLog.FILE_NAME), maxEntries = maxEntries)

    @Test
    fun `nothing recorded reads as nothing, not as an empty string`() {
        assertNull(log().read())
        assertEquals(0, log().entryCount())
    }

    @Test
    fun `a crash is recorded with its message, its trace and the context`() {
        val crash = log()
        crash.record(
            IllegalStateException("book closed under me"),
            meta = "1.7.0 · Android 15",
            atMillis = 1_700_000,
        )
        val text = crash.read()!!
        assertTrue(text.contains("book closed under me"))
        assertTrue(text.contains("IllegalStateException"))
        assertTrue(
            "the build has to be in there or the trace is unreadable later",
            text.contains("1.7.0 · Android 15"),
        )
        assertTrue(text.contains("1700000"))
        assertEquals(1, crash.entryCount())
    }

    @Test
    fun `only the newest few are kept`() {
        val crash = log(maxEntries = 3)
        repeat(5) { i ->
            crash.record(RuntimeException("crash $i"), meta = "m", atMillis = i.toLong())
        }
        val text = crash.read()!!
        assertEquals(3, crash.entryCount())
        assertFalse("the oldest must have been dropped", text.contains("crash 0"))
        assertFalse(text.contains("crash 1"))
        assertTrue(text.contains("crash 4"))
    }

    @Test
    fun `clearing removes the log entirely`() {
        val crash = log()
        crash.record(RuntimeException("boom"), meta = "m", atMillis = 1)
        crash.clear()
        assertNull(crash.read())
        assertEquals(0, crash.entryCount())
    }

    @Test
    fun `a huge trace is truncated rather than filling the disk`() {
        val deep = generateSequence(RuntimeException("root") as Throwable) {
            RuntimeException("wrapped", it)
        }.take(400).last()
        val entry = log().format(deep, meta = "m", atMillis = 1)
        assertTrue(entry.length < CrashLog.MAX_TRACE_CHARS + 500)
        assertTrue(entry.contains("trace truncated"))
    }

    @Test
    fun `an unwritable location is survived silently`() {
        // The handler runs on a thread that is already dying; a crash while recording a
        // crash would replace a useful stack trace with a useless one.
        val unwritable = CrashLog(File(folder.root, "x\u0000/y.txt"))
        unwritable.record(RuntimeException("boom"), meta = "m", atMillis = 1)
        assertNull(unwritable.read())
    }
}
