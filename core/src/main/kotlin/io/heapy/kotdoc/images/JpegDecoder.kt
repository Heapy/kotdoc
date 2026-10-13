// Codec.Picture.Jpg and YCbCrK conversion: Copyright (c) 2011 Vincent Berthoux.
// Translated from JuicyPixels 3.3.9, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.images

internal sealed interface JpgDecodeResult {
    data class Failure(val message: String) : JpgDecodeResult
    data class Image(val color: String, val width: Int, val height: Int, val pixels: ByteArray,
        private val readMetadata: Lazy<ImageMetadatas> = lazy { ImageMetadatas.empty }) : JpgDecodeResult {
        val metadata: ImageMetadatas get() = readMetadata.value
    }
}

internal object JpegDecoder {
    private class State {
        // prepareHuffmanTable supplies a singleton zero placeholder, not a packed
        // standard tree. Missing DHT therefore retains the original traversal.
        val dc: MutableList<() -> IntArray> = MutableList(4) { { intArrayOf(0) } }
        val ac: MutableList<() -> IntArray> = MutableList(4) { { intArrayOf(0) } }
        val quants = MutableList(4) { ShortArray(64) { 1 } }
        var restart = -1L
        var frame: JpgFrameHeader? = null
        var adobe: JpgAdobeApp14? = null
        var jfif: JpgJFIFApp0? = null
        var exif: List<ImageFileDirectory>? = null
        var progressive = false
        var maxWidth = 0L
        var maxHeight = 0L
        val scans = mutableListOf<JpgScanData>()
    }
    private fun prepare(frames: List<JpgFrame>): State {
        val state = State()
        for (frame in frames) when (frame) {
            is JpgFrame.Adobe -> state.adobe = frame.data
            is JpgFrame.Jfif -> state.jfif = frame.data
            is JpgFrame.Exif -> state.exif = frame.fields
            is JpgFrame.App, is JpgFrame.Extension -> Unit
            is JpgFrame.Restart -> state.restart = frame.interval.toLong()
            is JpgFrame.Quant -> frame.tables.forEach { state.quants[it.destination] = it.table.toShortArray() }
            is JpgFrame.Huffman -> frame.tables.forEach { entry ->
                val tables = if (entry.specification.tableClass == DctComponent.DC) state.dc else state.ac
                if (entry.specification.destination < tables.size) tables[entry.specification.destination] = { entry.packed.toIntArray() }
            }
            is JpgFrame.Scans -> {
                state.frame = frame.header
                state.progressive = frame.kind == JpgFrameKind.Fixed.JpgProgressiveDCTHuffman
                state.maxWidth = frame.header.components.maxOf { it.horizontal }.toLong()
                state.maxHeight = frame.header.components.maxOf { it.vertical }.toLong()
            }
            is JpgFrame.ScanBlob -> {
                val header = state.frame
                val parameters = frame.header.scans.flatMap { spec ->
                    val component = header?.components?.indexOfFirst { it.identifier == spec.selector } ?: -1
                    if (component < 0) error("Jpg decoding error - bad component selector in blob.")
                    val h = checkNotNull(header) { "Jpg decoding error - no previous frame" }
                    val description = h.components[component]
                    val w = description.horizontal.toLong(); val ht = description.vertical.toLong()
                    val xCount = if (frame.header.scans.size > 1 || state.progressive) w else 1L
                    val yCount = if (frame.header.scans.size > 1 || state.progressive) ht else 1L
                    val dc = state.dc[minOf(3, spec.dcTable)]; val ac = state.ac[minOf(3, spec.acTable)]
                    val restart = state.restart; val blob = state.scans.size.toLong()
                    val subSampling = (state.maxWidth - w + 1) to (state.maxHeight - ht + 1)
                    buildList {
                        for (y in 0..<yCount) for (x in 0..<xCount) add(lazy { JpgUnpackerParameter(dc(), ac(), component.toLong(), restart, w, ht,
                            subSampling,
                            frame.header.spectralBegin.toLong() to frame.header.spectralEnd.toLong(),
                            frame.header.approxLow.toLong() to frame.header.approxHigh.toLong(), blob,
                            if (frame.header.scans.size == 1) 0L else 1L, y * w + x, x, y) })
                    }
                }
                // The list is lazy, but each original parameter has strict tree fields.
                val deferred = object : AbstractList<JpgUnpackerParameter>() {
                    override val size get() = parameters.size
                    override fun get(index: Int) = parameters[index].value
                }
                state.scans.add(JpgScanData(deferred, frame.blob.bytes))
            }
        }
        return state
    }
    private fun color(state: State): JpgColorSpace? {
        val components = state.frame?.components ?: return null
        val adobe = when (components.size to state.adobe?.transform) {
            3 to AdobeTransform.AdobeYCbCr -> JpgColorSpace.JpgColorSpaceYCbCr
            1 to AdobeTransform.AdobeUnknown -> JpgColorSpace.JpgColorSpaceY
            3 to AdobeTransform.AdobeUnknown -> JpgColorSpace.JpgColorSpaceRGB
            4 to AdobeTransform.AdobeYCck -> JpgColorSpace.JpgColorSpaceYCCK
            else -> null
        }
        if (adobe != null) return adobe
        val names = components.map { it.identifier.toChar() }.joinToString("")
        return when (names) {
            "RGB" -> JpgColorSpace.JpgColorSpaceRGB
            "YCc" -> JpgColorSpace.JpgColorSpaceYCC
            "RGBA" -> JpgColorSpace.JpgColorSpaceRGBA
            "YCcA" -> JpgColorSpace.JpgColorSpaceYCCA
            "YCcK" -> JpgColorSpace.JpgColorSpaceYCCK
            else -> when (components.size) {
                1 -> JpgColorSpace.JpgColorSpaceY
                2 -> JpgColorSpace.JpgColorSpaceYA
                3 -> JpgColorSpace.JpgColorSpaceYCbCr
                4 -> JpgColorSpace.JpgColorSpaceCMYK
                else -> null
            }
        }
    }
    fun decode(bytes: ByteArray): JpgDecodeResult {
        val image = try { JpegFrames.image(JpegInput(bytes)) }
            catch (e: JpegBinaryException) { return JpgDecodeResult.Failure(e.message.orEmpty()) }
        val frames = image.frames.toList()
        val kinds = frames.filterIsInstance<JpgFrame.Scans>().filter { it.kind in listOf(JpgFrameKind.Fixed.JpgBaselineDCTHuffman,
            JpgFrameKind.Fixed.JpgExtendedSequentialDCTHuffman, JpgFrameKind.Fixed.JpgProgressiveDCTHuffman) }
        if (kinds.size != 1) return JpgDecodeResult.Failure("Unknown JPG kind")
        val first = frames.filterIsInstance<JpgFrame.Scans>().first().header
        val state = prepare(frames)
        val frame = checkNotNull(state.frame)
        val pixels = if (kinds.single().kind == JpgFrameKind.Fixed.JpgProgressiveDCTHuffman)
            JpegProgressive.unpack(state.maxWidth to state.maxHeight, frame, state.quants, state.scans).pixels
        else baseline(frame, state.quants, state.scans, ByteArray(Math.toIntExact(first.width.toLong() * first.height * first.components.size)))
        val color = color(state) ?: return JpgDecodeResult.Failure("Unknown color space")
        val name = when (color) {
            JpgColorSpace.JpgColorSpaceY -> "Y8"
            JpgColorSpace.JpgColorSpaceYA -> "YA8"
            JpgColorSpace.JpgColorSpaceRGB -> "RGB8"
            JpgColorSpace.JpgColorSpaceYCbCr -> "YCbCr8"
            JpgColorSpace.JpgColorSpaceCMYK, JpgColorSpace.JpgColorSpaceYCCK -> "CMYK8"
            else -> return JpgDecodeResult.Failure("Wrong color space : $color")
        }
        if (color == JpgColorSpace.JpgColorSpaceYCCK) ycckToCmyk(pixels)
        // Retain only metadata records, not the entropy streams and decoder workspace.
        val jfifMarker = state.jfif
        val exifFields = state.exif
        return JpgDecodeResult.Image(name, first.width, first.height, pixels, lazy {
            val jfif = jfifMarker?.let(ImageExifMetadata::extractJfif) ?: ImageMetadatas.empty
            val exif = exifFields?.let(ImageExifMetadata::extractTiff) ?: ImageMetadatas.empty
            jfif + (exif + ImageMetadatas.basic(ImageSourceFormat.SourceJpeg, first.width.toLong(), first.height.toLong()))
        })
    }
    private fun macro(dcTree: IntArray, acTree: IntArray, quant: ShortArray, work: ShortArray, previous: Short, reader: ImageBitReader): Pair<Short, ShortArray> {
        val next = (previous + JpegBlocks.dcCoefficientDecode(dcTree, reader)).toShort()
        val block = JpegBlocks.empty(); block[0] = next
        var n = 1
        while (n < 64) {
            val (r, s) = JpegBlocks.decodeRrrrSsss(acTree, reader)
            if (r == 0 && s == 0) break
            if (r == 15 && s == 0) { n += 16; continue }
            block[n + r] = JpegBlocks.decodeInt(reader, s).toShort()
            n += r + 1
        }
        return next to JpegBlocks.decodeMacroBlock(quant, work, block)
    }
    private fun baseline(frame: JpgFrameHeader, quants: List<ShortArray>, scans: List<JpgScanData>, pixels: ByteArray): ByteArray {
        val count = frame.components.size.toLong(); val width = frame.width.toLong(); val height = frame.height.toLong()
        val work = JpegBlocks.empty(); val dc = ShortArray(frame.components.size)
        val restart = scans.firstOrNull()?.parameters?.firstOrNull()?.restartInterval ?: -1L
        var reset = restart
        for (scan in scans) {
            val p = scan.parameters; val reader = ImageBitReader.jpeg(scan.bytes)
            val maxW by lazy { if (p.size > 1) p.maxOf { it.componentWidth } else p.maxOf { it.subSampling.first } }
            val maxH by lazy { if (p.size > 1) p.maxOf { it.componentHeight } else p.maxOf { it.subSampling.second } }
            val mcuW by lazy { Math.floorDiv(JpegBlocks.toBlockSize(width) + maxW - 1, maxW) }
            val mcuH by lazy { Math.floorDiv(JpegBlocks.toBlockSize(height) + maxH - 1, maxH) }
            // rasterMap's outer loop can finish without forcing the horizontal size.
            JpegBlocks.rasterMap(1, mcuH) { _, y -> JpegBlocks.rasterMap(mcuW, 1) { x, _ ->
                if (reset == 0L) { dc.fill(0); reader.alignJpeg(); JpegBlocks.decodeRestartInterval(); reset = restart - 1 } else reset--
                for (parameter in p) {
                    val i = Math.toIntExact(parameter.componentIndex)
                    val quant = quants[minOf(3, frame.components[i].quantization)]
                    val (prediction, block) = macro(parameter.dcHuffmanTree, parameter.acHuffmanTree, quant, work, dc[i], reader)
                    dc[i] = prediction
                    val (sx, sy) = parameter.subSampling
                    val bx = x * maxW + parameter.blockMcuX; val by = y * maxH + parameter.blockMcuY
                    if (x == mcuW - 1 || y == mcuH - 1) JpegBlocks.unpackMacroBlock(count, sx, sy, i.toLong(), bx, by, width, height, pixels, block)
                    else when {
                        count == 1L && sx == 1L && sy == 1L -> direct(pixels, block, width, bx, by, 1, 0, 1)
                        count == 3L && sx == 1L && sy == 1L -> direct(pixels, block, width, bx, by, 3, i, 1)
                        sx == 2L && sy == 1L -> direct(pixels, block, width, bx, by, 3, i, 2)
                        else -> JpegBlocks.unpackMacroBlock(count, sx, sy, i.toLong(), bx, by, width, height, pixels, block)
                    }
                }
            } }
        }
        return pixels
    }
    private fun direct(pixels: ByteArray, block: ShortArray, width: Long, x: Long, y: Long, stride: Int, component: Int, repeatX: Int) {
        for (row in 0..<8) for (column in 0..<8) {
            val value = block[row * 8 + column].toInt().coerceIn(0, 255).toByte()
            for (dx in 0..<repeatX) pixels[Math.toIntExact(((y * 8 + row) * width + x * 8 + column * repeatX + dx) * stride + component)] = value
        }
    }
    private fun ycckToCmyk(pixels: ByteArray) {
        for (i in pixels.indices step 4) {
            val converted = ImageCmykConversion.toCmyk(PixelYCbCrK8(
                (255 - (pixels[i].toInt() and 255)).toUByte(),
                (255 - (pixels[i + 1].toInt() and 255)).toUByte(),
                (255 - (pixels[i + 2].toInt() and 255)).toUByte(),
                (255 - (pixels[i + 3].toInt() and 255)).toUByte(),
            ))
            pixels[i] = converted.cyan.toByte(); pixels[i + 1] = converted.magenta.toByte()
            pixels[i + 2] = converted.yellow.toByte(); pixels[i + 3] = converted.black.toByte()
        }
    }
}
