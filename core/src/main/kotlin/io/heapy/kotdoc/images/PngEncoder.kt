// Codec.Picture.Png.Internal.Export / Metadata / Type:
// Copyright (c) 2011 Vincent Berthoux, BSD-3-Clause; see licenses/.
// Gamma Int64 ceiling: Copyright (c) Daniel Fischer 2010, GHC BSD-3-Clause.
package io.heapy.kotdoc.images

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.CRC32
import java.util.zip.Deflater

/** Original noninterlaced, filter-zero PNG export for the eight PngSavable types. */
internal object PngEncoder {
    private val supported = setOf("y8", "y16", "ya8", "ya16", "rgb8", "rgb16", "rgba8", "rgba16")
    fun encodeDynamic(image: DynamicRaster): PngDecoder.Result<Lazy<ByteArray>> =
        if (image.tag !in supported) PngDecoder.Result.Failure("Unsupported image format for PNG export")
        else PngDecoder.Result.Success(lazy {
            image.dynamicMap(object : DynamicRasterVisitor<ByteArray> {
                override fun <P : Any, C : Any, A : Any> visit(codec: RasterCodec<P, C, A>, image: () -> RasterImage<P, C, A>) = encode(codec, image).value
            })
        })

    fun <P : Any, C : Any, A : Any> encode(codec: RasterCodec<P, C, A>, image: () -> RasterImage<P, C, A>, metadata: () -> ImageMetadatas = { ImageMetadatas.empty }): Lazy<ByteArray> = lazy {
        require(codec.name in supported) { "Unsupported image format for PNG export" }
        val i = image()
        val color = when (codec.name) { "y8", "y16" -> 0; "ya8", "ya16" -> 4; "rgb8", "rgb16" -> 2; else -> 6 }
        file(i.width, i.height, if (codec.name.endsWith("16")) 16 else 8, color, metadata(), emptyList()) { scanlines(i) }
    }

    fun <P : Any> encodePaletted(codec: RasterCodec<P, UByte, ByteArray>, palette: () -> RasterImage<P, UByte, ByteArray>, image: () -> RasterImage<UByte, UByte, ByteArray>, metadata: () -> ImageMetadatas = { ImageMetadatas.empty }): PngDecoder.Result<Lazy<ByteArray>> {
        require(codec.name in listOf("rgb8", "rgba8"))
        val p = palette()
        if (p.width <= 0 || p.width > 256 || p.height != 1L) return PngDecoder.Result.Failure("Invalid palette")
        val i = image()
        if ((0L..<i.vector.length).any { i.vector[it].toLong() >= p.width }) return PngDecoder.Result.Failure("Image contains indexes absent from the palette")
        return PngDecoder.Result.Success(lazy {
            // Metadata chunks precede palette construction and image data demand.
            val metas = metadataChunks(metadata())
            val chunks = if (codec.name == "rgb8") {
                listOf(chunk("PLTE", byteVector(p.vector), p.width * 3))
            } else {
                val rgb = ByteArray(Math.toIntExact(p.width * 3)) { n -> p.vector[(n / 3) * 4L + n % 3].toByte() }
                val alpha = ByteArray(Math.toIntExact(p.width)) { n -> p.vector[n * 4L + 3].toByte() }
                listOf(chunk("PLTE", rgb, p.width * 3), chunk("tRNS", alpha))
            }
            assemble(i.width, i.height, 8, 3, metas + chunks) { scanlines(i) }
        })
    }

    fun <P : Any, C : Any, A : Any> write(path: Path, codec: RasterCodec<P, C, A>, image: () -> RasterImage<P, C, A>) {
        Files.newOutputStream(path).use { it.write(encode(codec, image).value) }
    }
    fun writeDynamic(path: Path, image: DynamicRaster): PngDecoder.Result<Boolean> = when (val out = encodeDynamic(image)) {
        is PngDecoder.Result.Failure -> out
        is PngDecoder.Result.Success -> { Files.newOutputStream(path).use { it.write(out.value.value) }; PngDecoder.Result.Success(true) }
    }
    private fun byteVector(vector: RasterVector<UByte, ByteArray>) = ByteArray(Math.toIntExact(vector.length)) { vector[it.toLong()].toByte() }
    private fun <P : Any, C : Any, A : Any> scanlines(image: RasterImage<P, C, A>): ByteArray {
        val out = ByteArrayOutputStream()
        val lineSize = image.width * image.codec.components
        val short = image.codec.name.endsWith("16")
        if (image.height <= 0) return out.toByteArray()
        if (short) RasterVector.allocationLength(lineSize * 2)
        for (row in 0L..<image.height) {
            out.write(0)
            // An empty 8-bit blit still demands its source vector. The 16-bit
            // loop allocates an empty line without reading the source at all.
            if (!short) image.vector
            for (n in 0L..<lineSize) {
                val value = image.codec.storage.bits(image.vector[row * lineSize + n])
                if (short) out.write((value shr 8).toInt())
                out.write(value.toInt())
            }
        }
        return out.toByteArray()
    }
    private fun compress(bytes: ByteArray): ByteArray {
        val deflater = Deflater()
        try {
            deflater.setInput(bytes); deflater.finish()
            val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
            return out.toByteArray()
        } finally { deflater.end() }
    }
    private fun word(value: Long) = ByteArray(4) { (value ushr (24 - 8 * it)).toByte() }
    private fun chunk(type: String, data: ByteArray, declaredLength: Long = data.size.toLong()): ByteArray {
        val signature = type.toByteArray(Charsets.ISO_8859_1)
        val crc = CRC32().apply { update(signature); update(data) }.value
        return word(declaredLength) + signature + (if (declaredLength == 0L) byteArrayOf() else data) + word(crc)
    }
    private fun file(w: Long, h: Long, depth: Int, color: Int, metadata: ImageMetadatas, chunks: List<ByteArray>, data: () -> ByteArray) =
        assemble(w, h, depth, color, metadataChunks(metadata) + chunks, data)
    private fun assemble(w: Long, h: Long, depth: Int, color: Int, chunks: List<ByteArray>, data: () -> ByteArray): ByteArray {
        val header = word(w) + word(h) + byteArrayOf(depth.toByte(), color.toByte(), 0, 0, 0)
        return ByteArrayOutputStream().apply {
            write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)); write(chunk("IHDR", header))
            chunks.forEach(::write)
            write(chunk("IDAT", compress(data()))); write(chunk("IEND", byteArrayOf()))
        }.toByteArray()
    }
    private fun latin1(value: String): ByteArray = value.codePoints().toArray().map { it.toByte() }.toByteArray()
    private fun metadataChunks(m: ImageMetadatas): List<ByteArray> {
        val chunks = mutableListOf<ByteArray>()
        val x = m.lookup(ImageMetadataKey.DpiX)
        if (x != null) m.lookup(ImageMetadataKey.DpiY)?.let { y -> chunks.add(chunk("pHYs", word(ImageMetadatas.dotsPerInchToMeter(x).toLong()) + word(ImageMetadatas.dotsPerInchToMeter(y).toLong()) + byteArrayOf(1))) }
        for (entry in m.entries) {
            when (val key = entry.key) {
                ImageMetadataKey.Gamma -> {
                    val scaled = (entry.value as Double) * 100000.0
                    // GHC's Word32 specialization uses ceilingDoubleInt, then
                    // narrows the Int. Preserve its Int64 increment/wrap, also
                    // at nonfinite and out-of-range values in the ARM64 oracle.
                    val truncated = scaled.toLong()
                    val gamma = if (truncated.toDouble() < scaled) truncated + 1 else truncated
                    chunks.add(chunk("gAMA", word(gamma)))
                }
                ImageMetadataKey.Title, ImageMetadataKey.Description, ImageMetadataKey.Author, ImageMetadataKey.Copyright,
                ImageMetadataKey.Software, ImageMetadataKey.Comment, ImageMetadataKey.Disclaimer, ImageMetadataKey.Source, ImageMetadataKey.Warning ->
                    chunks.add(chunk("tEXt", latin1(key.label) + byteArrayOf(0) + latin1(entry.value as String)))
                is ImageMetadataKey.Unknown -> {
                    val value = entry.value
                    if (value is ImageMetadataValue.StringValue) chunks.add(chunk("tEXt", latin1(key.name) + byteArrayOf(0) + latin1(value.value)))
                }
                else -> Unit
            }
        }
        return chunks
    }
}
