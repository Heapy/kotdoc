// Codec.Picture.Jpg.Internal.Types: Copyright (c) 2011 Vincent Berthoux.
// Translated from JuicyPixels 3.3.9, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.images

import io.heapy.kotdoc.formats.HaskellString

private fun <T> Iterable<T>.shown() = joinToString(",", "[", "]")
private fun JpgFrameKind.argument() = if (this is JpgFrameKind.Fixed) toString() else "($this)"

internal class JpgBytes(val bytes: ByteArray) {
    override fun equals(other: Any?) = other is JpgBytes && bytes.contentEquals(other.bytes)
    override fun hashCode() = bytes.contentHashCode()
    override fun toString() = HaskellString.quote(bytes.toString(Charsets.ISO_8859_1))
}

/** Packing is deferred: frame-header/debug readers and the writer do not force it. */
internal class JpgHuffmanEntry(val specification: JpgHuffmanTableSpec, packed: () -> List<Int>) {
    constructor(specification: JpgHuffmanTableSpec) : this(specification, { JpegHuffman.pack(JpegHuffman.build(specification.codes)).toList() })
    val packed: List<Int> by lazy(packed)
    override fun equals(other: Any?) = other is JpgHuffmanEntry && specification == other.specification && packed == other.packed
    override fun hashCode() = 31 * specification.hashCode() + packed.hashCode()
    override fun toString() = "($specification,${packed.shown()})"
}

internal sealed interface JpgFrame {
    data class App(val code: Int, val payload: JpgBytes) : JpgFrame { override fun toString() = "JpgAppFrame $code $payload" }
    data class Adobe(val data: JpgAdobeApp14) : JpgFrame { override fun toString() = "JpgAdobeAPP14 ($data)" }
    data class Jfif(val data: JpgJFIFApp0) : JpgFrame { override fun toString() = "JpgJFIF ($data)" }
    data class Exif(val fields: List<ImageFileDirectory>) : JpgFrame { override fun toString() = "JpgExif ${fields.shown()}" }
    data class Extension(val code: Int, val payload: JpgBytes) : JpgFrame { override fun toString() = "JpgExtension $code $payload" }
    data class Quant(val tables: List<JpgQuantTableSpec>) : JpgFrame { override fun toString() = "JpgQuantTable ${tables.shown()}" }
    data class Huffman(val tables: List<JpgHuffmanEntry>) : JpgFrame { override fun toString() = "JpgHuffmanTable ${tables.shown()}" }
    data class ScanBlob(val header: JpgScanHeader, val blob: JpgBytes) : JpgFrame { override fun toString() = "JpgScanBlob ($header) $blob" }
    data class Scans(val kind: JpgFrameKind, val header: JpgFrameHeader) : JpgFrame { override fun toString() = "JpgScans ${kind.argument()} ($header)" }
    data class Restart(val interval: Int) : JpgFrame { override fun toString() = "JpgIntervalRestart $interval" }
}

/** Memoized list spine. Iterating the prefix never requests or copies a deferred scan. */
internal class JpgFrameSequence(private val prefix: List<JpgFrame>, tail: (() -> JpgFrameSequence)? = null) : Iterable<JpgFrame> {
    private val tail by lazy { tail?.invoke() }
    override fun iterator(): Iterator<JpgFrame> = object : Iterator<JpgFrame> {
        private var current: JpgFrameSequence? = this@JpgFrameSequence
        private var index = 0
        override fun hasNext(): Boolean {
            while (true) {
                val part = current ?: return false
                if (index < part.prefix.size) return true
                current = part.tail
                index = 0
            }
        }
        override fun next(): JpgFrame {
            if (!hasNext()) throw NoSuchElementException()
            return current!!.prefix[index++]
        }
    }
    override fun equals(other: Any?): Boolean {
        if (other !is JpgFrameSequence) return false
        val left = iterator(); val right = other.iterator()
        while (left.hasNext()) if (!right.hasNext() || left.next() != right.next()) return false
        return !right.hasNext()
    }
    override fun hashCode(): Int = fold(1) { hash, value -> hash * 31 + value.hashCode() }
    override fun toString() = shown()
}
internal data class JpgImage(val frames: JpgFrameSequence) {
    constructor(frames: List<JpgFrame>) : this(JpgFrameSequence(frames))
    override fun toString() = "JpgImage {jpgFrame = $frames}"
}
internal data class JpgFrameLocation(val kind: JpgFrameKind, val offset: Int, val length: Int) {
    override fun toString() = "($kind,$offset,$length)"
}

internal object JpegFrames {
    private fun payload(input: JpegInput): ByteArray = input.take(input.u16() - 2)
    private fun <T> optional(bytes: ByteArray, read: (JpegInput) -> T): T? =
        try { read(JpegInput(bytes)) } catch (_: JpegBinaryException) { null }

    fun frame(input: JpegInput, kind: JpgFrameKind): JpgFrame? = when (kind) {
        JpgFrameKind.Fixed.JpgEndOfImage -> null
        is JpgFrameKind.App -> {
            val bytes = payload(input)
            when (kind.value) {
                0 -> optional(bytes, JpegBinary::jfif)?.let(JpgFrame::Jfif)
                1 -> JpegExif.parse(bytes)?.let(JpgFrame::Exif)
                14 -> optional(bytes, JpegBinary::adobe)?.let(JpgFrame::Adobe)
                else -> JpgFrame.App(kind.value, JpgBytes(bytes))
            }
        }
        is JpgFrameKind.Extension -> JpgFrame.Extension(kind.value, JpgBytes(payload(input)))
        JpgFrameKind.Fixed.JpgQuantizationTable -> JpgFrame.Quant(JpegBinary.tableList(input, JpegBinary::quantTable))
        JpgFrameKind.Fixed.JpgHuffmanTableMarker -> JpgFrame.Huffman(JpegBinary.tableList(input, JpegBinary::huffmanTable).map(::JpgHuffmanEntry))
        JpgFrameKind.Fixed.JpgRestartInterval -> JpgFrame.Restart(JpegBinary.restart(input))
        JpgFrameKind.Fixed.JpgStartOfScan -> JpgFrame.ScanBlob(JpegBinary.scanHeader(input), JpgBytes(JpegEcs.parse(input)))
        else -> JpgFrame.Scans(kind, JpegBinary.frameHeader(input))
    }
    fun frames(input: JpegInput): List<JpgFrame> = buildList {
        while (true) {
            val kind = JpegBinary.kind(input)
            if (kind == JpgFrameKind.Fixed.JpgEndOfImage) break
            frame(input, kind)?.let(::add)
            JpegBinary.skipFrameMarker(input)
        }
    }
    fun strictImage(input: JpegInput): JpgImage { JpegBinary.skipUntilFrames(input); return JpgImage(frames(input)) }

    fun image(input: JpegInput): JpgImage { JpegBinary.skipUntilFrames(input); return JpgImage(semiLazyFrames(input)) }
    private fun semiLazyFrames(input: JpegInput): JpgFrameSequence {
        val prefix = mutableListOf<JpgFrame>()
        while (true) {
            val kind = JpegBinary.kind(input)
            when (kind) {
                JpgFrameKind.Fixed.JpgEndOfImage -> return JpgFrameSequence(prefix)
                JpgFrameKind.Fixed.JpgStartOfScan -> {
                    val header = JpegBinary.scanHeader(input)
                    val start = input.position
                    input.skip(input.bytes.size - start)
                    return JpgFrameSequence(prefix) {
                        // The original getRemainingLazyBytes succeeds before parsing any ECS.
                        // Keep only the original array and offset until this list cell is forced.
                        val remaining = input.bytes.copyOfRange(start, input.bytes.size)
                        val ecs = try { JpegEcs.parse(JpegInput(remaining)) } catch (_: JpegBinaryException) { remaining }
                        JpgFrameSequence(listOf(JpgFrame.ScanBlob(header, JpgBytes(ecs)))) {
                            val next = minOf(ecs.size.toLong() + 1, remaining.size.toLong()).toInt()
                            try { semiLazyFrames(JpegInput(remaining.copyOfRange(next, remaining.size))) }
                            catch (_: JpegBinaryException) { JpgFrameSequence(emptyList()) }
                        }
                    }
                }
                else -> { frame(input, kind)?.let(prefix::add); JpegBinary.skipFrameMarker(input) }
            }
        }
    }
    fun kinds(input: JpegInput): List<JpgFrameLocation> = buildList {
        while (true) {
            val start = input.position
            val kind = JpegBinary.kind(input)
            if (kind == JpgFrameKind.Fixed.JpgEndOfImage) { add(JpgFrameLocation(kind, start, 0)); break }
            val before = input.position
            frame(input, kind)
            add(JpgFrameLocation(kind, start, input.position - before))
            JpegBinary.skipFrameMarker(input)
        }
    }
    fun firstHeader(input: JpegInput): JpgFrameHeader? {
        while (true) {
            val kind = JpegBinary.kind(input)
            if (kind == JpgFrameKind.Fixed.JpgEndOfImage) return null
            if (kind == JpgFrameKind.Fixed.JpgStartOfScan) input.fail("parseToFirstFrameHeader: Encountered SOS frame marker before frame header that tells its dimensions")
            val frame = frame(input, kind)
            if (frame is JpgFrame.Scans) return frame.header
            JpegBinary.skipFrameMarker(input)
        }
    }
    fun put(out: JpegOutput, frame: JpgFrame) {
        fun marker(kind: JpgFrameKind.Fixed) = JpegBinary.put(out, kind)
        fun genericPayload(kind: JpgFrameKind, payload: JpgBytes) {
            JpegBinary.put(out, kind); out.u16(payload.bytes.size)
            // Binary strict ByteString writes its own signed Int64 length prefix.
            repeat(8) { index -> out.u8((payload.bytes.size.toLong() ushr (8 * (7 - index))).toInt()) }
            out.bytes(payload.bytes)
        }
        when (frame) {
            is JpgFrame.App -> genericPayload(JpgFrameKind.App(frame.code), frame.payload)
            is JpgFrame.Extension -> genericPayload(JpgFrameKind.Extension(frame.code), frame.payload)
            is JpgFrame.Adobe -> { JpegBinary.put(out, JpgFrameKind.App(14)); out.u16(14); JpegBinary.put(out, frame.data) }
            is JpgFrame.Jfif -> { JpegBinary.put(out, JpgFrameKind.App(0)); out.u16(16); JpegBinary.put(out, frame.data) }
            is JpgFrame.Exif -> JpegExif.put(out, frame.fields)
            is JpgFrame.Quant -> {
                marker(JpgFrameKind.Fixed.JpgQuantizationTable)
                JpegBinary.putTableList(out, frame.tables, { it.size }, JpegBinary::put)
            }
            is JpgFrame.Huffman -> {
                marker(JpgFrameKind.Fixed.JpgHuffmanTableMarker)
                JpegBinary.putTableList(out, frame.tables.map { it.specification }, { it.size }, JpegBinary::put)
            }
            is JpgFrame.ScanBlob -> { marker(JpgFrameKind.Fixed.JpgStartOfScan); JpegBinary.put(out, frame.header); out.bytes(frame.blob.bytes) }
            is JpgFrame.Scans -> { JpegBinary.put(out, frame.kind); JpegBinary.put(out, frame.header) }
            is JpgFrame.Restart -> { marker(JpgFrameKind.Fixed.JpgRestartInterval); JpegBinary.putRestart(out, frame.interval) }
        }
    }
    fun put(out: JpegOutput, image: JpgImage) {
        out.u8(255); out.u8(216); image.frames.forEach { put(out, it) }; out.u8(255); out.u8(217)
    }
    fun printMacroBlock(block: List<Long>): String = printBlock(block, true)
    fun printPureMacroBlock(block: List<Long>): String = printBlock(block, false)
    private fun printBlock(block: List<Long>, mutable: Boolean): String = buildString {
        repeat(64) { index ->
            if (index % 8 == 0) append('\n')
            append(block[index].toString().padStart(5))
            if (mutable || index % 8 == 0) append(' ')
        }
        append("===============================\n")
    }
}
