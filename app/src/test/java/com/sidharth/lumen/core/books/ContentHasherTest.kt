package com.sidharth.lumen.core.books

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import kotlin.random.Random

class ContentHasherTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** Known-answer test against the published SHA-256 vector for "abc". */
    @Test
    fun `matches the published SHA-256 vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ContentHasher.hash(ByteArrayInputStream("abc".toByteArray())),
        )
    }

    @Test
    fun `empty input hashes to the empty digest`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ContentHasher.hash(ByteArrayInputStream(ByteArray(0))),
        )
    }

    @Test
    fun `hash is stable across identical content in different files`() {
        val a = tmp.newFile("a.epub").apply { writeBytes("same bytes".toByteArray()) }
        val b = tmp.newFile("b.epub").apply { writeBytes("same bytes".toByteArray()) }
        assertEquals(ContentHasher.hash(a), ContentHasher.hash(b))
    }

    @Test
    fun `a single changed byte changes the hash`() {
        val a = tmp.newFile("a.epub").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val b = tmp.newFile("b.epub").apply { writeBytes(byteArrayOf(1, 2, 4)) }
        assertNotEquals(ContentHasher.hash(a), ContentHasher.hash(b))
    }

    /**
     * The import path hashes while copying to avoid a second read of a 40MB file. That
     * shortcut is only safe if it agrees with hashing the written file afterwards.
     */
    @Test
    fun `copyAndHash writes the bytes and agrees with hashing the result`() {
        // Deliberately not a multiple of the buffer size, to exercise a partial last read.
        val payload = Random(7).nextBytes(ContentHasher.BUFFER_SIZE * 3 + 517)
        val destination = tmp.newFile("copied.epub")

        val hash = ContentHasher.copyAndHash(ByteArrayInputStream(payload), destination)

        assertTrue(payload.contentEquals(destination.readBytes()))
        assertEquals(ContentHasher.hash(destination), hash)
        assertEquals(ContentHasher.hash(ByteArrayInputStream(payload)), hash)
    }

    @Test
    fun `copyAndHash reports monotonic progress ending at the total size`() {
        val payload = Random(3).nextBytes(ContentHasher.BUFFER_SIZE * 2 + 10)
        val reported = mutableListOf<Long>()

        ContentHasher.copyAndHash(
            ByteArrayInputStream(payload),
            tmp.newFile("progress.epub"),
        ) { reported += it }

        assertEquals(reported, reported.sorted())
        assertEquals(payload.size.toLong(), reported.last())
    }
}
