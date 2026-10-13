// Codec.Picture.Jpg and FastDct: Copyright (c) 2011 Vincent Berthoux.
// Translated from JuicyPixels 3.3.9, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.images

internal enum class JpgEncodingColor(val components: Int, val maximumSampling: Int) {
    Y8(1, 1), YCbCr8(3, 2), RGB8(3, 1), CMYK8(4, 1),
}

internal object JpegEncoder {
    /** Int32 fixed-point arithmetic, including overflow and both original biases. */
    fun fastDct(work: IntArray, samples: ShortArray): IntArray {
        fun transform(column: Boolean, offset: Int) {
            fun at(i: Int) = if (column) work[offset + i * 8] else samples[offset * 8 + i].toInt()
            fun write(i: Int, value: Int) { work[if (column) offset + i * 8 else offset * 8 + i] = value }
            val b = IntArray(8, ::at)
            val a0 = b[0] + b[7]; val a1 = b[1] + b[6]; val a2 = b[2] + b[5]; val a3 = b[3] + b[4]
            val t10 = a0 + a3 + if (column) 2 else 0
            val t12 = a0 - a3; val t11 = a1 + a2; val t13 = a1 - a2
            val d0 = b[0] - b[7]; val d1 = b[1] - b[6]; val d2 = b[2] - b[5]; val d3 = b[3] - b[4]
            if (column) { write(0, (t10 + t11) shr 5); write(4, (t10 - t11) shr 5) }
            else { write(0, (t10 + t11 - 1024) shl 2); write(4, (t10 - t11) shl 2) }
            val bias = if (column) 16384 else 1024
            val shift = if (column) 18 else 11
            val z = (t12 + t13) * 4433 + bias
            write(2, (z + t12 * 6270) shr shift); write(6, (z - t13 * 15137) shr shift)
            val e10 = d0 + d3; val e11 = d1 + d2; val e12 = d0 + d2; val e13 = d1 + d3
            val z1 = (e12 + e13) * 9633 + bias
            val e0 = d0 * 12299; val e1 = d1 * 25172; val e2 = d2 * 16819; val e3 = d3 * 2446
            val f10 = e10 * -7373; val f11 = e11 * -20995
            val f12 = e12 * -3196 + z1; val f13 = e13 * -16069 + z1
            write(1, (e0 + f10 + f12) shr shift); write(3, (e1 + f11 + f13) shr shift)
            write(5, (e2 + f11 + f12) shr shift); write(7, (e3 + f10 + f13) shr shift)
        }
        repeat(8) { transform(false, it) }; repeat(8) { transform(true, it) }
        return work
    }
    private data class Component(val index: Int, val width: Int, val height: Int, val quant: ShortArray,
        val dc: List<JpegHuffman.WriterCode>, val ac: List<JpegHuffman.WriterCode>)

    private fun extract(pixels: ByteArray, width: Int, height: Int, count: Int, component: Int, samplingX: Int, samplingY: Int,
        blockX: Long, blockY: Long, block: ShortArray) {
        for (y in 0..<8) for (x in 0..<8) {
            var sum = 0
            for (dy in 0..<samplingY) for (dx in 0..<samplingX) {
                val px = minOf(width.toLong() - 1, (blockX * 8 + x) * samplingX + dx)
                val py = minOf(height.toLong() - 1, (blockY * 8 + y) * samplingY + dy)
                val at = Math.toIntExact((py * width + px) * count + component)
                sum += pixels[at].toInt() and 255
            }
            block[y * 8 + x] = (sum / (samplingX * samplingY)).toShort()
        }
    }
    private fun power(value: Int): Int = if (value == 0) 0 else 32 - Integer.numberOfLeadingZeros(kotlin.math.abs(value))
    private fun serialize(writer: ImageBitWriter, dc: List<JpegHuffman.WriterCode>, ac: List<JpegHuffman.WriterCode>, block: IntArray) {
        fun code(table: List<JpegHuffman.WriterCode>, at: Int) { val c = table[at]; writer.writeJpeg(c.code.toUInt(), c.depth) }
        fun integer(value: Int, size: Int) { writer.writeJpeg((if (value > 0) value else value - 1).toUInt(), size) }
        val size = power(block[0]); code(dc, size)
        if (size != 0) integer(block[0], size)
        var zeroes = 0
        for (i in 1..<64) {
            val value = block[i]
            if (value == 0) {
                if (i == 63) code(ac, 0) else zeroes++
            } else {
                while (zeroes >= 16) { code(ac, 240); zeroes -= 16 }
                val n = power(value); code(ac, zeroes shl 4 or n); integer(value, n); zeroes = 0
            }
        }
    }

    fun encodeWithMetadata(color: JpgEncodingColor, width: Int, height: Int, pixels: ByteArray, quality: Int,
        metadata: ImageMetadatas): ByteArray {
        val exif = ImageExifMetadata.encodeTiff(metadata)
        return encode(color, width, height, pixels, quality,
            ImageExifMetadata.encodeJfif(metadata) + if (exif.isEmpty()) emptyList() else listOf(JpgFrame.Exif(exif)))
    }

    /** Pre-encoded metadata frames precede the image frames; empty by default. */
    fun encode(color: JpgEncodingColor, width: Int, height: Int, pixels: ByteArray, quality: Int = 50,
        metadata: List<JpgFrame> = emptyList()): ByteArray {
        require(quality in 0..255)
        val ycbcr = color == JpgEncodingColor.YCbCr8
        val count = color.components; val maximum = color.maximumSampling
        val identifiers = when (color) { JpgEncodingColor.RGB8 -> "RGB".map(Char::code); JpgEncodingColor.CMYK8 -> "CMYK".map(Char::code); else -> (1..count).toList() }
        val components = List(count) { i -> JpgComponent(identifiers[i], if (ycbcr && i == 0) 2 else 1, if (ycbcr && i == 0) 2 else 1, if (ycbcr && i > 0) 1 else 0) }
        val tables = listOf(JpegDefaultTables.lumaQuantizationTable, JpegDefaultTables.chromaQuantizationTable).take(if (ycbcr) 2 else 1).mapIndexed { i, values ->
            val scaled = JpegHuffman.scaleQuantisation(quality.toLong(), values.toShortArray())
            JpgQuantTableSpec(0, i, JpegBlocks.zigZagForwardValues(scaled.toList()))
        }
        val defaults = listOf(DctComponent.DC to JpegDefaultTables.dcLumaHuffmanTable, DctComponent.AC to JpegDefaultTables.acLumaHuffmanTable,
            DctComponent.DC to JpegDefaultTables.dcChromaHuffmanTable, DctComponent.AC to JpegDefaultTables.acChromaHuffmanTable).take(if (ycbcr) 4 else 2)
        val huffman = defaults.mapIndexed { i, (kind, rows) -> JpgHuffmanEntry(JpgHuffmanTableSpec(kind, i / 2, rows.map(List<Int>::size), rows)) { listOf(0) } }
        val dcLuma = JpegHuffman.inverse(JpegDefaultTables.dcLumaHuffmanTree)
        val acLuma = JpegHuffman.inverse(JpegDefaultTables.acLumaHuffmanTree)
        val dcChroma by lazy { JpegHuffman.inverse(JpegDefaultTables.dcChromaHuffmanTree) }
        val acChroma by lazy { JpegHuffman.inverse(JpegDefaultTables.acChromaHuffmanTree) }
        val states = components.mapIndexed { i, component -> Component(i, component.horizontal, component.vertical, tables[component.quantization].table.toShortArray(),
            if (ycbcr && i > 0) dcChroma else dcLuma, if (ycbcr && i > 0) acChroma else acLuma) }
        val writer = ImageBitWriter(); val dc = ShortArray(count); val block = ShortArray(64); val work = IntArray(64); val ordered = IntArray(64)
        fun upward(value: Long, divisor: Long): Long = Math.floorDiv(value, divisor) + if (Math.floorMod(value, divisor) != 0L) 1 else 0
        JpegBlocks.rasterMap(upward(width.toLong(), 8L * maximum), upward(height.toLong(), 8L * maximum)) { mx, my ->
            for (state in states) JpegBlocks.rasterMap(state.width.toLong(), state.height.toLong()) { sx, sy ->
                extract(pixels, width, height, count, state.index, maximum - state.width + 1, maximum - state.height + 1,
                    mx * state.width + sx, my * state.height + sy, block)
                JpegBlocks.zigZagForward(ordered, fastDct(work, block))
                repeat(64) { i -> val q = state.quant[i].toInt(); ordered[i] = (ordered[i] + q / 2) / q }
                val next = ordered[0]; ordered[0] = next - dc[state.index]; dc[state.index] = next.toShort()
                serialize(writer, state.dc, state.ac, ordered)
            }
        }
        val header = JpgFrameHeader(8 + 3 * count, 8, height and 65535, width and 65535, count, components)
        val scan = JpgScanHeader(6 + 2 * count, count, components.map { JpgScanSpecification(it.identifier, it.quantization, it.quantization) }, 0, 63, 0, 0)
        val frames = buildList {
            addAll(metadata)
            if (color == JpgEncodingColor.RGB8) add(JpgFrame.Adobe(JpgAdobeApp14(100, 0, 0, AdobeTransform.AdobeUnknown)))
            add(JpgFrame.Quant(tables)); add(JpgFrame.Scans(JpgFrameKind.Fixed.JpgBaselineDCTHuffman, header)); add(JpgFrame.Huffman(huffman))
            add(JpgFrame.ScanBlob(scan, JpgBytes(writer.finishJpeg())))
        }
        return JpegOutput().also { JpegFrames.put(it, JpgImage(frames)) }.result()
    }
}
