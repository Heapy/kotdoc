// Port of zip-archive 0.5's toEntry, putLocalFile, putFileHeader and putArchive
// for Pandoc's extractMedia (zero timestamp, no comments/extra fields).
// Copyright (c) 2008-2012 John MacFarlane. BSD-3-Clause; see licenses/zip-archive-BSD-3-Clause.txt.
package io.heapy.kotdoc.resources

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

internal object MediaZip {
    private data class Entry(val name: ByteArray, val method: Int, val crc: Long, val size: Int, val data: ByteArray, val offset: Int)

    fun encode(items: List<MediaItem>): ByteArray {
        // foldr addEntryToArchive: the first occurrence of a normalized name wins.
        val unique = items.distinctBy { normalize(it.path) }
        require(unique.size < 65535) { "Zip64NotSupported: 65535 or more entries requires ZIP64" }
        val out = ByteArrayOutputStream()
        val entries = unique.map { item ->
            val raw = item.contents
            val compressed = deflate(raw)
            val data = if (compressed.size < raw.size) compressed else raw
            val entry = Entry(normalize(item.path).toByteArray(Charsets.UTF_8), if (data === raw) 0 else 8,
                CRC32().also { it.update(raw) }.value, raw.size, data, out.size())
            out.u32(0x04034b50)
            out.common(entry)
            out.u16(entry.name.size); out.u16(0)
            out.write(entry.name); out.write(entry.data)
            entry
        }
        val centralOffset = out.size()
        for (entry in entries) {
            out.u32(0x02014b50); out.u16(0) // version made by: FAT
            out.common(entry)
            out.u16(entry.name.size); out.u16(0); out.u16(0)
            out.u16(0); out.u16(0); out.u32(0); out.u32(entry.offset.toLong())
            out.write(entry.name)
        }
        val centralSize = out.size() - centralOffset
        out.u32(0x06054b50); out.u16(0); out.u16(0)
        out.u16(entries.size); out.u16(entries.size)
        out.u32(centralSize.toLong()); out.u32(centralOffset.toLong()); out.u16(0)
        return out.toByteArray()
    }

    private fun normalize(path: String): String = path.split('/').filter { it != "." && it.isNotEmpty() }.joinToString("/")
    private fun ByteArrayOutputStream.common(e: Entry) {
        u16(20); u16(0x800); u16(e.method)
        u16(0); u16(33) // epoch 0 clamps to 1980-01-01 00:00:00 in zip-archive
        u32(e.crc); u32(e.data.size.toLong()); u32(e.size.toLong())
    }
    private fun ByteArrayOutputStream.u16(value: Int) { write(value); write(value ushr 8) }
    private fun ByteArrayOutputStream.u32(value: Long) { repeat(4) { write((value ushr (8 * it)).toInt()) } }
    private fun deflate(bytes: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        try {
            deflater.setInput(bytes); deflater.finish()
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(32768)
            while (!deflater.finished()) output.write(buffer, 0, deflater.deflate(buffer))
            return output.toByteArray()
        } finally { deflater.end() }
    }
}
