package com.logisparktech.doctrack

import com.logisparktech.doctrack.core.storage.Crc32
import com.logisparktech.doctrack.core.storage.ZipArchive
import com.logisparktech.doctrack.core.storage.isSafeRelativeName
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZipArchiveTest {
    @Test
    fun crc32MatchesReferenceValue() {
        assertEquals(0xCBF43926L, Crc32.compute("123456789".encodeToByteArray()))
        assertEquals(0L, Crc32.compute(ByteArray(0)))
    }

    @Test
    fun roundTripPreservesEntries() {
        val entries = listOf(
            ZipArchive.Entry("manifest.json", """{"a":1}""".encodeToByteArray()),
            ZipArchive.Entry("attachments/att_x.jpg", ByteArray(70_000) { (it % 251).toByte() }),
            ZipArchive.Entry("empty.txt", ByteArray(0)),
            ZipArchive.Entry("नेपाली.txt", "नमस्ते".encodeToByteArray()),
        )
        val read = ZipArchive.read(ZipArchive.write(entries))
        assertEquals(entries.map { it.name }, read.map { it.name })
        entries.zip(read).forEach { (a, b) -> assertContentEquals(a.bytes, b.bytes) }
    }

    @Test
    fun corruptedDataIsRejected() {
        val zip = ZipArchive.write(listOf(ZipArchive.Entry("data.json", "hello world".encodeToByteArray())))
        val dataOffset = 30 + "data.json".length // local header + file name
        val damaged = zip.copyOf().also { it[dataOffset + 2] = (it[dataOffset + 2] + 1).toByte() }
        assertFailsWith<ZipArchive.ZipFormatException> { ZipArchive.read(damaged) }
        assertFailsWith<ZipArchive.ZipFormatException> { ZipArchive.read("not a zip".encodeToByteArray()) }
        assertFailsWith<ZipArchive.ZipFormatException> { ZipArchive.read(zip.copyOf(zip.size - 30)) }
    }

    @Test
    fun attachmentNamesCannotEscapeStorage() {
        assertTrue(isSafeRelativeName("att_0123abcd.jpg"))
        assertFalse(isSafeRelativeName("../db.sqlite"))
        assertFalse(isSafeRelativeName("dir/file.pdf"))
        assertFalse(isSafeRelativeName(".hidden"))
        assertFalse(isSafeRelativeName(""))
    }
}
