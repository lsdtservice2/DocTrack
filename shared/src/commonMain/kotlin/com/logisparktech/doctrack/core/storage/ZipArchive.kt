package com.logisparktech.doctrack.core.storage

/**
 * Minimal, dependency-free ZIP support shared by Android and iOS.
 * Entries are written uncompressed ("stored"); attachments (JPEG/PDF) are already compressed.
 * The reader verifies the CRC-32 of every entry, so damaged archives are rejected.
 */
object ZipArchive {

    class Entry(val name: String, val bytes: ByteArray)

    class ZipFormatException(message: String) : Exception(message)

    fun write(entries: List<Entry>): ByteArray {
        val out = ByteSink()
        val central = ByteSink()
        for (entry in entries) {
            val name = entry.name.encodeToByteArray()
            val crc = Crc32.compute(entry.bytes)
            val offset = out.size
            // Local file header
            out.int(0x04034b50); out.short(20); out.short(0x0800); out.short(0)
            out.short(0); out.short(0x21) // DOS time 00:00, date 1980-01-01
            out.int(crc.toInt()); out.int(entry.bytes.size); out.int(entry.bytes.size)
            out.short(name.size); out.short(0)
            out.bytes(name); out.bytes(entry.bytes)
            // Central directory record
            central.int(0x02014b50); central.short(20); central.short(20); central.short(0x0800); central.short(0)
            central.short(0); central.short(0x21)
            central.int(crc.toInt()); central.int(entry.bytes.size); central.int(entry.bytes.size)
            central.short(name.size); central.short(0); central.short(0); central.short(0); central.short(0)
            central.int(0); central.int(offset)
            central.bytes(name)
        }
        val centralOffset = out.size
        val centralBytes = central.toByteArray()
        out.bytes(centralBytes)
        // End of central directory
        out.int(0x06054b50); out.short(0); out.short(0)
        out.short(entries.size); out.short(entries.size)
        out.int(centralBytes.size); out.int(centralOffset); out.short(0)
        return out.toByteArray()
    }

    fun read(data: ByteArray): List<Entry> {
        val eocd = findEndOfCentralDirectory(data) ?: throw ZipFormatException("Not a ZIP archive")
        val count = data.u16(eocd + 10)
        var p = data.u32(eocd + 16).toInt()
        val result = ArrayList<Entry>(count)
        repeat(count) {
            if (p < 0 || p + 46 > data.size || data.u32(p) != 0x02014b50L) throw ZipFormatException("Bad central directory")
            val method = data.u16(p + 10)
            val crc = data.u32(p + 16)
            val compressedSize = data.u32(p + 20)
            val size = data.u32(p + 24)
            val nameLen = data.u16(p + 28)
            val extraLen = data.u16(p + 30)
            val commentLen = data.u16(p + 32)
            val localOffset = data.u32(p + 42).toInt()
            val name = data.decodeToString(p + 46, p + 46 + nameLen)
            p += 46 + nameLen + extraLen + commentLen

            if (method != 0 || compressedSize != size) throw ZipFormatException("Unsupported compression for $name")
            if (localOffset < 0 || localOffset + 30 > data.size || data.u32(localOffset) != 0x04034b50L) {
                throw ZipFormatException("Bad local header for $name")
            }
            val start = localOffset + 30 + data.u16(localOffset + 26) + data.u16(localOffset + 28)
            val end = start + size.toInt()
            if (start < 0 || end > data.size || end < start) throw ZipFormatException("Truncated entry $name")
            val bytes = data.copyOfRange(start, end)
            if (Crc32.compute(bytes) != crc) throw ZipFormatException("CRC mismatch for $name")
            result += Entry(name, bytes)
        }
        return result
    }

    private fun findEndOfCentralDirectory(data: ByteArray): Int? {
        val min = maxOf(0, data.size - 22 - 0xFFFF)
        for (i in data.size - 22 downTo min) {
            if (data.u32(i) == 0x06054b50L) return i
        }
        return null
    }

    private fun ByteArray.u16(i: Int): Int = (this[i].toInt() and 0xFF) or ((this[i + 1].toInt() and 0xFF) shl 8)
    private fun ByteArray.u32(i: Int): Long = (u16(i).toLong()) or (u16(i + 2).toLong() shl 16)

    private class ByteSink {
        private var buf = ByteArray(1024)
        var size = 0
            private set

        private fun ensure(extra: Int) {
            if (size + extra > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + extra))
        }

        fun short(v: Int) { ensure(2); buf[size++] = v.toByte(); buf[size++] = (v shr 8).toByte() }
        fun int(v: Int) { short(v and 0xFFFF); short((v ushr 16) and 0xFFFF) }
        fun bytes(b: ByteArray) { ensure(b.size); b.copyInto(buf, size); size += b.size }
        fun toByteArray(): ByteArray = buf.copyOf(size)
    }
}

object Crc32 {
    private val table = LongArray(256) { n ->
        var c = n.toLong()
        repeat(8) { c = if (c and 1L != 0L) 0xEDB88320L xor (c ushr 1) else c ushr 1 }
        c
    }

    fun compute(bytes: ByteArray): Long {
        var crc = 0xFFFFFFFFL
        for (b in bytes) crc = table[((crc xor b.toLong()) and 0xFF).toInt()] xor (crc ushr 8)
        return crc xor 0xFFFFFFFFL
    }
}
