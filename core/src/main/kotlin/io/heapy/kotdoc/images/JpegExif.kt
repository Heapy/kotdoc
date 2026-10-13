// Codec.Picture.Jpg.Internal.Types: Copyright (c) 2011 Vincent Berthoux.
// Translated from JuicyPixels 3.3.9, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.images

internal object JpegExif {
    private val signature = byteArrayOf(69, 120, 105, 102, 0, 0)
    fun parse(payload: ByteArray): List<ImageFileDirectory>? {
        if (payload.size < signature.size || !signature.indices.all { payload[it] == signature[it] }) return null
        return try {
            val input = JpegInput(payload.copyOfRange(signature.size, payload.size))
            TiffBinary.document(input).directories.firstOrNull()
        } catch (_: JpegBinaryException) { null }
    }
    fun put(out: JpegOutput, fields: List<ImageFileDirectory>) {
        val (base, exif) = fields.partition { it.identifier.isInIFD0 }
        val offset = ImageFileDirectory(ExifTag.Known.TagExifOffset, IfdType.TypeLong, 1, 0)
        val directories = if (exif.isEmpty()) listOf(base) else listOf(base + offset, exif)
        val payload = JpegOutput()
        payload.bytes(signature)
        TiffBinary.put(payload, byteArrayOf(), TiffDocument(TiffHeader(TiffEndian.EndianBig, 8), directories))
        val bytes = payload.result()
        JpegBinary.put(out, JpgFrameKind.App(1)); out.u16(bytes.size + 2); out.bytes(bytes)
    }
}
