package com.sidharth.lumen.core.diagnostics

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * The last few crashes, on this phone only (M15).
 *
 * Lumen sends nothing anywhere — no analytics, no crash reporting, no INTERNET
 * permission — which has the quiet cost that a crash is invisible to the one person who
 * could act on it. This is the smallest honest answer: the stack trace is written to the
 * app's private storage and shown in Settings, where it can be read, copied, or cleared.
 * Nothing leaves the device unless the reader copies it out deliberately.
 *
 * Writing happens on a thread that is already dying, so it is deliberately plain: open,
 * append, trim, close. No coroutines, no injection of anything that might itself be in a
 * bad state, and every failure swallowed — a crash log that crashes while recording a
 * crash would replace a useful stack trace with a useless one.
 */
class CrashLog(
    private val file: File,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    /** Appends one crash, keeping only the newest [maxEntries]. */
    fun record(throwable: Throwable, meta: String, atMillis: Long) {
        runCatching {
            val entry = format(throwable, meta, atMillis)
            val kept = (readEntries() + entry).takeLast(maxEntries)
            file.parentFile?.mkdirs()
            file.writeText(kept.joinToString(SEPARATOR))
        }
    }

    /** The whole log as it should be shown, newest last; null when nothing is recorded. */
    fun read(): String? = runCatching {
        file.takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun entryCount(): Int = readEntries().size

    fun clear() {
        runCatching { file.delete() }
    }

    private fun readEntries(): List<String> = runCatching {
        file.takeIf { it.exists() }
            ?.readText()
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            .orEmpty()
    }.getOrDefault(emptyList())

    /**
     * One entry: when, on what, and the trace.
     *
     * The trace is truncated because a deep Compose recomposition stack can run to tens
     * of kilobytes, and the top of it is the part that says anything.
     */
    fun format(throwable: Throwable, meta: String, atMillis: Long): String {
        val trace = StringWriter().also { writer ->
            PrintWriter(writer).use { throwable.printStackTrace(it) }
        }.toString()
        return buildString {
            append("--- crash at ")
            append(atMillis)
            append('\n')
            append(meta)
            append('\n')
            append(trace.take(MAX_TRACE_CHARS))
            if (trace.length > MAX_TRACE_CHARS) append("\n… trace truncated\n")
        }
    }

    companion object {
        const val FILE_NAME = "crash-log.txt"
        const val DEFAULT_MAX_ENTRIES = 5
        const val MAX_TRACE_CHARS = 8_000
        private const val SEPARATOR = "\n===== \n"
    }
}
