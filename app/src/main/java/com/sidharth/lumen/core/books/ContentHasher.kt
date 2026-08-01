package com.sidharth.lumen.core.books

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * A book's identity (§2.2: "match by content hash, not filename").
 *
 * Streamed rather than read whole: a 40MB EPUB must not be held in memory, and the same
 * pass is reused while copying so the file is only read once.
 */
object ContentHasher {

    private const val ALGORITHM = "SHA-256"
    const val BUFFER_SIZE = 64 * 1024

    fun hash(stream: InputStream): String {
        val digest = MessageDigest.getInstance(ALGORITHM)
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = stream.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().toHexString()
    }

    fun hash(file: File): String = file.inputStream().buffered().use { hash(it) }

    /**
     * Copies [source] into [destination] and returns the hash of what was written, so the
     * bytes are read exactly once. [onProgress] receives bytes copied so far;
     * [totalBytes] is -1 when the provider won't say how big the file is.
     */
    fun copyAndHash(
        source: InputStream,
        destination: File,
        onProgress: (copied: Long) -> Unit = {},
    ): String {
        val digest = MessageDigest.getInstance(ALGORITHM)
        val buffer = ByteArray(BUFFER_SIZE)
        var copied = 0L
        destination.outputStream().buffered().use { out ->
            while (true) {
                val read = source.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
                out.write(buffer, 0, read)
                copied += read
                onProgress(copied)
            }
            out.flush()
        }
        return digest.digest().toHexString()
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { byte -> "%02x".format(byte) }
}
