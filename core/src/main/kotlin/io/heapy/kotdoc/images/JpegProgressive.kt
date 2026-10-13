// Codec.Picture.Jpg.Internal.Progressive: Copyright (c) 2011 Vincent Berthoux.
// Translated from JuicyPixels 3.3.9, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.images

internal data class JpgScanData(val parameters: List<JpgUnpackerParameter>, val bytes: ByteArray)
internal data class JpgDecodedRaster(val width: Int, val height: Int, val components: Int, val pixels: ByteArray)

internal object JpegProgressive {
    private enum class Unpacker { FIRST_DC, REFINE_DC, FIRST_AC, REFINE_AC }
    private data class ComponentData(val indices: List<LongArray>, val blocks: Array<ShortArray>, val id: Int, val blockCount: Long)
    private fun index(value: Long): Int = Math.toIntExact(value)
    private fun indices(component: JpgComponent, width: Long, mcuWidth: Long): List<LongArray> {
        val w = component.horizontal.toLong(); val h = component.vertical.toLong()
        val solo = mutableListOf<Long>(); val multi = mutableListOf<Long>()
        JpegBlocks.rasterMap(JpegBlocks.toBlockSize(width), h) { x, y -> solo.add(y * mcuWidth * w + x) }
        for (mcu in 0..<mcuWidth) JpegBlocks.rasterMap(w, h) { x, y -> multi.add((mcu + y * mcuWidth) * w + x) }
        return listOf(solo.toLongArray(), multi.toLongArray())
    }
    private fun selection(parameter: JpgUnpackerParameter): Unpacker = when {
        parameter.successiveApprox.second == 0L && parameter.coefficientRange.first == 0L -> Unpacker.FIRST_DC
        parameter.successiveApprox.second == 0L -> Unpacker.FIRST_AC
        parameter.coefficientRange.first == 0L -> Unpacker.REFINE_DC
        else -> Unpacker.REFINE_AC
    }
    private fun unpack(
        strategy: Unpacker, p: JpgUnpackerParameter, dc: ShortArray, block: ShortArray, eob: Int, reader: ImageBitReader,
    ): Int {
        val low = p.successiveApprox.first.toInt()
        val start = p.coefficientRange.first; val end = p.coefficientRange.second
        val plus = (1L shl low).toShort().toInt()
        val minus = (-1L shl low).toShort().toInt()
        when (strategy) {
            Unpacker.FIRST_DC -> {
                val delta = JpegBlocks.dcCoefficientDecode(p.dcHuffmanTree, reader)
                val component = index(p.componentIndex)
                val next = (dc[component] + delta).toShort()
                block[0] = (next.toLong() shl low).toShort(); dc[component] = next
                return eob
            }
            Unpacker.REFINE_DC -> {
                if (reader.readJpegBit()) block[0] = (block[0].toInt() or plus).toShort()
                return eob
            }
            Unpacker.FIRST_AC -> {
                if (eob > 0) return eob - 1
                var n = start
                while (n <= end) {
                    val (r, s) = JpegBlocks.decodeRrrrSsss(p.acHuffmanTree, reader)
                    when {
                        r == 15 && s == 0 -> n += 16
                        r == 0 && s == 0 -> return 0
                        s == 0 -> return (1 shl r) - 1 + JpegBlocks.unpackInt(reader, r)
                        else -> {
                            n += r
                            block[index(n)] = (JpegBlocks.decodeInt(reader, s).toLong() shl low).toShort()
                            n++
                        }
                    }
                }
                return 0
            }
            Unpacker.REFINE_AC -> {
                fun refine(at: Long) {
                    val i = index(at); val coefficient = block[i].toInt()
                    if (coefficient != 0 && reader.readJpegBit() && coefficient and plus == 0) {
                        block[i] = (coefficient + if (coefficient >= 0) plus else minus).toShort()
                    }
                }
                fun eobRun(from: Long) { for (at in from..end) refine(at) }
                fun updateCoefficients(run: Int, from: Long): Long {
                    var r = run; var at = from
                    while (r >= 0 && at <= end) {
                        if (block[index(at)] != 0.toShort()) refine(at) else r--
                        at++
                    }
                    return if (r < 0) at - 1 else at
                }
                if (eob != 0) { eobRun(start); return eob - 1 }
                var at = start
                while (at <= end) {
                    val (r, s) = JpegBlocks.decodeRrrrSsss(p.acHuffmanTree, reader)
                    when {
                        r == 15 && s == 0 -> at = updateCoefficients(15, at) + 1
                        s == 0 -> {
                            val next = (1 shl r) + JpegBlocks.unpackInt(reader, r) - 1
                            eobRun(at); return next
                        }
                        else -> {
                            val value = if (reader.readJpegBit()) plus else minus
                            at = updateCoefficients(r, at)
                            if (at <= end) block[index(at)] = value.toShort()
                            at++
                        }
                    }
                }
                return 0
            }
        }
    }

    fun unpack(maximum: Pair<Long, Long>, frame: JpgFrameHeader, quants: List<ShortArray>, scans: List<JpgScanData>): JpgDecodedRaster {
        // A scan chooses one strategy from its first parameter, even if later
        // parameters carry different ranges or approximation fields.
        val strategies = scans.map { scan ->
            selection(scan.parameters.firstOrNull() ?: error("progressiveUnpack, no component"))
        }
        val readers = scans.map { ImageBitReader.jpeg(it.bytes) }
        val (maxWidth, maxHeight) = maximum
        val width = frame.width.toLong(); val height = frame.height.toLong()
        val mcuWidth by lazy { Math.floorDiv(JpegBlocks.toBlockSize(width) + maxWidth - 1, maxWidth) }
        val mcuHeight by lazy { Math.floorDiv(JpegBlocks.toBlockSize(height) + maxHeight - 1, maxHeight) }
        val components = frame.components.size
        val allBlocks = frame.components.mapIndexed { id, component ->
            val count = component.horizontal.toLong() * component.vertical
            ComponentData(indices(component, width, mcuWidth), Array(index(count * mcuWidth * 2)) { JpegBlocks.empty() }, id, count)
        }
        val restart = scans.firstOrNull()?.parameters?.firstOrNull()?.restartInterval ?: -1L
        val dc = ShortArray(components)
        val eobRuns = IntArray(scans.size)
        val work = JpegBlocks.empty()
        val writeIndices = LongArray(components)
        val restarts = LongArray(scans.size) { restart }
        val pixels = ByteArray(index(width * height * components)) { 128.toByte() }
        JpegBlocks.rasterMap(1, mcuHeight) { _, mcuY ->
            allBlocks.forEach { component -> component.blocks.forEach { it.fill(0) } }
            writeIndices.fill(0)
            JpegBlocks.rasterMap(mcuWidth, 1) { _, _ ->
                for (i in scans.indices) {
                    val value = restarts[i]
                    if (value == 0L) {
                        if (i == 0) dc.fill(0)
                        readers[i].alignJpeg(); JpegBlocks.decodeRestartInterval()
                        eobRuns[i] = 0; restarts[i] = restart - 1
                    } else restarts[i] = value - 1
                }
                scans.forEachIndexed { scanIndex, scan ->
                    for (p in scan.parameters) {
                        val readerIndex = index(p.readerIndex)
                        val reader = readers[readerIndex]
                        val eob = eobRuns[readerIndex]
                        val component = index(p.componentIndex)
                        val write = writeIndices[component]
                        val data = allBlocks[component]
                        val order = data.indices[index(p.indiceVector)]
                        if (write + p.blockIndex < order.size) {
                            val at = order[index(write + p.blockIndex)]
                            val block = data.blocks[index(at)]
                            eobRuns[readerIndex] = unpack(strategies[scanIndex], p, dc, block, eob, reader)
                        }
                    }
                }
                allBlocks.forEach { writeIndices[it.id] += it.blockCount }
            }
            for (data in allBlocks) {
                val component = frame.components[data.id]
                val quant = quants[minOf(3, component.quantization)]
                val w = component.horizontal.toLong(); val h = component.vertical.toLong()
                val subX = maxWidth - w + 1; val subY = maxHeight - h + 1
                JpegBlocks.rasterMap(mcuWidth * w, h) { x, y ->
                    val block = data.blocks[index(y * mcuWidth * w + x)]
                    val transformed = JpegBlocks.decodeMacroBlock(quant, work, block)
                    JpegBlocks.unpackMacroBlock(components.toLong(), subX, subY, data.id.toLong(), x * subX, mcuY * maxHeight + y,
                        width, height, pixels, transformed)
                }
            }
        }
        return JpgDecodedRaster(frame.width, frame.height, components, pixels)
    }
}
