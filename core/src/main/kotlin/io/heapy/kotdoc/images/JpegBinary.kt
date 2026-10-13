// Codec.Picture.Jpg.Internal.Types: Copyright (c) 2011 Vincent Berthoux.
// Translated from JuicyPixels 3.3.9, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.images

import java.io.ByteArrayOutputStream

internal class JpegBinaryException(val offset: Int, message: String) : IllegalArgumentException(message)

internal class JpegInput(val bytes: ByteArray) {
    var position: Int = 0
        private set
    fun fail(message: String): Nothing = throw JpegBinaryException(position, message)
    private fun ensure(count: Int) { if (count > bytes.size - position) fail("not enough bytes") }
    fun u8(): Int { ensure(1); return bytes[position++].toInt() and 255 }
    fun u16(): Int {
        ensure(2)
        val value = ((bytes[position].toInt() and 255) shl 8) or (bytes[position + 1].toInt() and 255)
        position += 2
        return value
    }
    fun take(count: Int): ByteArray {
        if (count <= 0) return byteArrayOf()
        ensure(count)
        return bytes.copyOfRange(position, position + count).also { position += count }
    }
    fun rewind(count: Int) { require(count in 0..position); position -= count }
    fun skip(count: Int) { if (count > 0) { ensure(count); position += count } }
}

internal class JpegOutput {
    private val output = ByteArrayOutputStream()
    fun u8(value: Int) { output.write(value and 255) }
    fun u16(value: Int) { u8(value ushr 8); u8(value) }
    fun nibbles(high: Int, low: Int) { u8((high shl 4) or low) }
    fun bytes(value: ByteArray) { output.write(value) }
    fun result(): ByteArray = output.toByteArray()
}

internal sealed interface JpgFrameKind {
    enum class Fixed(val marker: Int) : JpgFrameKind {
        JpgBaselineDCTHuffman(0xc0), JpgExtendedSequentialDCTHuffman(0xc1), JpgProgressiveDCTHuffman(0xc2),
        JpgLosslessHuffman(0xc3), JpgDifferentialSequentialDCTHuffman(0xc5),
        JpgDifferentialProgressiveDCTHuffman(0xc6), JpgDifferentialLosslessHuffman(0xc7),
        JpgExtendedSequentialArithmetic(0xc9), JpgProgressiveDCTArithmetic(0xca), JpgLosslessArithmetic(0xcb),
        JpgDifferentialSequentialDCTArithmetic(0xcd), JpgDifferentialProgressiveDCTArithmetic(0xce),
        JpgDifferentialLosslessArithmetic(0xcf), JpgQuantizationTable(0xdb), JpgHuffmanTableMarker(0xc4),
        JpgStartOfScan(0xda), JpgEndOfImage(0xd9), JpgRestartInterval(0xdd),
    }
    data class App(val value: Int) : JpgFrameKind {
        init { require(value in 0..255) }
        override fun toString() = "JpgAppSegment $value"
    }
    data class Extension(val value: Int) : JpgFrameKind {
        init { require(value in 0..255) }
        override fun toString() = "JpgExtensionSegment $value"
    }
    data class RestartEnd(val value: Int) : JpgFrameKind {
        init { require(value in 0..255) }
        override fun toString() = "JpgRestartIntervalEnd $value"
    }
}

internal enum class JpgColorSpace {
    JpgColorSpaceYCbCr, JpgColorSpaceYCC, JpgColorSpaceY, JpgColorSpaceYA, JpgColorSpaceYCCA,
    JpgColorSpaceYCCK, JpgColorSpaceCMYK, JpgColorSpaceRGB, JpgColorSpaceRGBA,
}
internal enum class JpgImageKind { BaseLineDCT, ProgressiveDCT }
internal enum class AdobeTransform { AdobeUnknown, AdobeYCbCr, AdobeYCck }
internal enum class JFifUnit { JFifUnitUnknown, JFifPixelsPerInch, JFifPixelsPerCentimeter }

private fun <T> List<T>.shown() = joinToString(",", "[", "]")

internal data class JpgAdobeApp14(val version: Int, val flag0: Int, val flag1: Int, val transform: AdobeTransform) {
    override fun toString() = "JpgAdobeApp14 {_adobeDctVersion = $version, _adobeFlag0 = $flag0, _adobeFlag1 = $flag1, _adobeTransform = $transform}"
}
internal data class JpgJFIFApp0(val unit: JFifUnit, val dpiX: Int, val dpiY: Int, val thumbnail: Long?) {
    override fun toString(): String {
        val thumb = thumbnail?.let { "Just " + if (it < 0) "($it)" else "$it" } ?: "Nothing"
        return "JpgJFIFApp0 {_jfifUnit = $unit, _jfifDpiX = $dpiX, _jfifDpiY = $dpiY, _jfifThumbnail = $thumb}"
    }
}
internal data class JpgComponent(val identifier: Int, val horizontal: Int, val vertical: Int, val quantization: Int) {
    val size: Int get() = 3
    override fun toString() = "JpgComponent {componentIdentifier = $identifier, horizontalSamplingFactor = $horizontal, verticalSamplingFactor = $vertical, quantizationTableDest = $quantization}"
}
internal data class JpgFrameHeader(val length: Int, val precision: Int, val height: Int, val width: Int, val componentCount: Int, val components: List<JpgComponent>) {
    val size: Int get() = 8 + components.sumOf { it.size }
    override fun toString() = "JpgFrameHeader {jpgFrameHeaderLength = $length, jpgSamplePrecision = $precision, jpgHeight = $height, jpgWidth = $width, jpgImageComponentCount = $componentCount, jpgComponents = ${components.shown()}}"
}
internal data class JpgScanSpecification(val selector: Int, val dcTable: Int, val acTable: Int) {
    val size: Int get() = 2
    override fun toString() = "JpgScanSpecification {componentSelector = $selector, dcEntropyCodingTable = $dcTable, acEntropyCodingTable = $acTable}"
}
internal data class JpgScanHeader(val length: Int, val componentCount: Int, val scans: List<JpgScanSpecification>, val spectralBegin: Int, val spectralEnd: Int, val approxHigh: Int, val approxLow: Int) {
    val size: Int get() = 6 + scans.sumOf { it.size }
    override fun toString() = "JpgScanHeader {scanLength = $length, scanComponentCount = $componentCount, scans = ${scans.shown()}, spectralSelection = ($spectralBegin,$spectralEnd), successiveApproxHigh = $approxHigh, successiveApproxLow = $approxLow}"
}
internal data class JpgQuantTableSpec(val precision: Int, val destination: Int, val table: List<Short>) {
    val size: Int get() = 1 + (precision + 1) * 64
    override fun toString() = "JpgQuantTableSpec {quantPrecision = $precision, quantDestination = $destination, quantTable = ${table.shown()}}"
}
internal data class JpgHuffmanTableSpec(val tableClass: DctComponent, val destination: Int, val sizes: List<Int>, val codes: List<List<Int>>) {
    val size: Int get() = 17 + sizes.sum()
    override fun toString() = "JpgHuffmanTableSpec {huffmanTableClass = ${if (tableClass == DctComponent.DC) "DcComponent" else "AcComponent"}, huffmanTableDest = $destination, huffSizes = ${sizes.shown()}, huffCodes = ${codes.map { it.shown() }.shown()}}"
}

internal object JpegBinary {
    fun kind(input: JpegInput): JpgFrameKind {
        val code = input.u8()
        return JpgFrameKind.Fixed.entries.firstOrNull { it.marker == code } ?: when {
            code >= 0xf0 -> JpgFrameKind.Extension(code)
            code >= 0xe0 -> JpgFrameKind.App(code - 0xe0)
            code in 0xd0..0xd7 -> JpgFrameKind.RestartEnd(code)
            else -> input.fail("Invalid frame marker ($code)")
        }
    }
    fun put(out: JpegOutput, kind: JpgFrameKind) {
        out.u8(255)
        out.u8(when (kind) {
            is JpgFrameKind.Fixed -> kind.marker
            is JpgFrameKind.App -> kind.value + 0xe0
            is JpgFrameKind.Extension -> kind.value
            is JpgFrameKind.RestartEnd -> kind.value
        })
    }
    fun unit(input: JpegInput): JFifUnit = JFifUnit.entries.getOrElse(input.u8()) { JFifUnit.JFifUnitUnknown }
    fun adobeTransform(input: JpegInput): AdobeTransform = AdobeTransform.entries.getOrElse(input.u8()) { AdobeTransform.AdobeUnknown }
    fun jfif(input: JpegInput): JpgJFIFApp0 {
        if (!input.take(5).contentEquals(byteArrayOf(74, 70, 73, 70, 0))) input.fail("Invalid JFIF signature")
        val major = input.u8(); val minor = input.u8()
        if (major != 1 && minor > 2) input.fail("Unrecognize JFIF version")
        val unit = unit(input); val x = input.u16(); val y = input.u16()
        input.u8(); input.u8() // Thumbnail dimensions are read, but its bytes are not consumed.
        return JpgJFIFApp0(unit, x, y, null)
    }
    fun put(out: JpegOutput, value: JpgJFIFApp0) {
        out.bytes(byteArrayOf(74, 70, 73, 70, 0, 1, 2))
        out.u8(value.unit.ordinal); out.u16(value.dpiX); out.u16(value.dpiY); out.u8(0); out.u8(0)
    }
    fun adobe(input: JpegInput): JpgAdobeApp14 {
        if (!input.take(5).contentEquals("Adobe".toByteArray(Charsets.US_ASCII))) input.fail("Invalid Adobe APP14 marker")
        val version = input.u16()
        if (version != 100) input.fail("Invalid Adobe APP14 version $version")
        return JpgAdobeApp14(version, input.u16(), input.u16(), adobeTransform(input))
    }
    fun put(out: JpegOutput, value: JpgAdobeApp14) {
        out.bytes("Adobe".toByteArray(Charsets.US_ASCII)); out.u16(value.version)
        out.u16(value.flag0); out.u16(value.flag1); out.u8(value.transform.ordinal)
    }
    fun component(input: JpegInput): JpgComponent {
        val id = input.u8(); val sampling = input.u8()
        return JpgComponent(id, sampling ushr 4, sampling and 15, input.u8())
    }
    fun put(out: JpegOutput, value: JpgComponent) {
        out.u8(value.identifier); out.nibbles(value.horizontal, value.vertical); out.u8(value.quantization)
    }
    fun frameHeader(input: JpegInput): JpgFrameHeader {
        val start = input.position
        val length = input.u16(); val precision = input.u8(); val height = input.u16(); val width = input.u16(); val count = input.u8()
        val components = List(count) { component(input) }
        // Upstream's signed begin-end test is preserved; negative skip is a no-op.
        if (start - input.position < length) input.skip(length - (input.position - start))
        return JpgFrameHeader(length, precision, height, width, count, components)
    }
    fun put(out: JpegOutput, value: JpgFrameHeader) {
        out.u16(value.length); out.u8(value.precision); out.u16(value.height); out.u16(value.width); out.u8(value.componentCount)
        value.components.forEach { put(out, it) }
    }
    fun scanSpecification(input: JpegInput): JpgScanSpecification {
        val selector = input.u8(); val tables = input.u8()
        return JpgScanSpecification(selector, tables ushr 4, tables and 15)
    }
    fun put(out: JpegOutput, value: JpgScanSpecification) {
        out.u8(value.selector); out.nibbles(value.dcTable, value.acTable)
    }
    fun scanHeader(input: JpegInput): JpgScanHeader {
        val length = input.u16(); val count = input.u8(); val scans = List(count) { scanSpecification(input) }
        val begin = input.u8(); val end = input.u8(); val approx = input.u8()
        return JpgScanHeader(length, count, scans, begin, end, approx ushr 4, approx and 15)
    }
    fun put(out: JpegOutput, value: JpgScanHeader) {
        out.u16(value.length); out.u8(value.componentCount); value.scans.forEach { put(out, it) }
        out.u8(value.spectralBegin); out.u8(value.spectralEnd); out.nibbles(value.approxHigh, value.approxLow)
    }
    fun quantTable(input: JpegInput): JpgQuantTableSpec {
        val header = input.u8(); val precision = header ushr 4
        return JpgQuantTableSpec(precision, header and 15, List(64) { (if (precision == 0) input.u8() else input.u16()).toShort() })
    }
    fun put(out: JpegOutput, value: JpgQuantTableSpec) {
        out.nibbles(value.precision, value.destination)
        value.table.forEach { if (value.precision == 0) out.u8(it.toInt()) else out.u16(it.toInt()) }
    }
    fun huffmanTable(input: JpegInput): JpgHuffmanTableSpec {
        val header = input.u8(); val sizes = List(16) { input.u8() }
        val codes = sizes.map { size -> List(size) { input.u8() } }
        return JpgHuffmanTableSpec(if (header ushr 4 == 0) DctComponent.DC else DctComponent.AC, header and 15, sizes, codes)
    }
    fun put(out: JpegOutput, value: JpgHuffmanTableSpec) {
        out.nibbles(if (value.tableClass == DctComponent.DC) 0 else 1, value.destination)
        value.sizes.forEach(out::u8)
        for (i in 0..<16) if (value.sizes[i] != 0) value.codes[i].forEach(out::u8)
    }
    fun restart(input: JpegInput): Int {
        if (input.u16() != 4) input.fail("Invalid jpeg restart interval size")
        return input.u16()
    }
    fun putRestart(out: JpegOutput, interval: Int) { out.u16(4); out.u16(interval) }
    fun <T> tableList(input: JpegInput, read: (JpegInput) -> T): List<T> {
        var left = input.u16() - 2
        return buildList {
            while (left != 0) {
                val start = input.position
                add(read(input))
                left -= input.position - start
            }
        }
    }
    fun <T> putTableList(out: JpegOutput, values: List<T>, size: (T) -> Int, put: (JpegOutput, T) -> Unit) {
        out.u16(2 + values.sumOf(size)); values.forEach { put(out, it) }
    }
    fun skipUntilFrames(input: JpegInput) {
        val first = input.u8(); val second = input.u8()
        if (first != 255 || second != 216) input.fail("Invalid marker used")
        while (input.u8() != 255) { /* Source accepts arbitrary bytes before the next marker. */ }
    }
    fun skipFrameMarker(input: JpegInput) {
        val code = input.u8()
        if (code != 255) input.fail("Invalid Frame marker ($code, bytes read : ${input.position})")
    }
}

internal data class JpegEcsResult(val bytes: ByteArray, val remaining: ByteArray, val consumed: Int)

/** JuicyPixels' two ECS entry points intentionally differ at EOF and chunk boundaries. */
internal object JpegEcs {
    private fun marker(previous: Int, next: Int) = previous == 255 && next != 0 && next !in 0xd0..0xd7

    fun simple(input: JpegInput): ByteArray {
        val start = input.position
        var previous = input.u8()
        while (true) {
            val next = input.u8()
            if (marker(previous, next)) {
                val length = input.position - start - 2
                // Successful lookAhead restores the original cursor; a failing one does not.
                input.rewind(input.position - start)
                return input.take(length)
            }
            previous = next
        }
    }

    fun parse(input: JpegInput): ByteArray {
        if (input.position == input.bytes.size) input.fail("not enough bytes")
        val result = parseChunks(listOf(input.bytes.copyOfRange(input.position, input.bytes.size)))
        input.skip(result.consumed)
        return result.bytes
    }

    fun parseChunks(chunks: List<ByteArray>): JpegEcsResult {
        val nonempty = chunks.filter { it.isNotEmpty() }
        if (nonempty.isEmpty()) throw JpegBinaryException(0, "not enough bytes")
        val output = ByteArrayOutputStream()
        var previous = nonempty[0][0].toInt() and 255
        output.write(previous)
        var previousChunk = byteArrayOf()
        var fed = 1
        for (index in nonempty.indices) {
            val chunk = if (index == 0) nonempty[index].copyOfRange(1, nonempty[index].size) else nonempty[index]
            for (offset in chunk.indices) {
                val next = chunk[offset].toInt() and 255
                if (marker(previous, next)) {
                    val remaining = ByteArrayOutputStream()
                    val consumed: Int
                    if (offset == 0) {
                        // withInputChunks already accumulated the previous chunk. The source
                        // also appends its prefix here, duplicating it in the returned ECS.
                        output.write(previousChunk, 0, (previousChunk.size - 1).coerceAtLeast(0))
                        remaining.write(previous)
                        remaining.write(chunk)
                        consumed = fed - 1
                    } else {
                        output.write(chunk, 0, offset - 1)
                        remaining.write(chunk, offset - 1, chunk.size - offset + 1)
                        consumed = fed + offset - 1
                    }
                    for (following in index + 1..<nonempty.size) remaining.write(nonempty[following])
                    return JpegEcsResult(output.toByteArray(), remaining.toByteArray(), consumed)
                }
                previous = next
            }
            output.write(chunk)
            fed += chunk.size
            if (chunk.isNotEmpty()) previousChunk = chunk
        }
        return JpegEcsResult(output.toByteArray(), byteArrayOf(), fed)
    }
}
