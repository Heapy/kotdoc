// GHC 9.12.2 IO.Buffer, IO.Encoding.CodePage/API, UTF16 and Failure.
// University of Glasgow; complete licenses/GHC-base-BSD-3-Clause.txt.
package io.heapy.kotdoc.resources

import com.sun.jna.Memory
import com.sun.jna.Pointer
import io.heapy.kotdoc.errors.*
import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars

enum class GhcCodingProgress { InputUnderflow, OutputUnderflow, InvalidSequence }

internal data class GhcCodeBuffer(val raw: Pointer, val unit: Int, val size: Int,
    val left: Int, val right: Int, val offset: ULong = 0u, val write: Boolean = false) {
    val elements: Int get() = right - left
    val available: Int get() = size - right
    val empty: Boolean get() = left == right
    fun read(index: Int): Int = when (unit) { 1 -> raw.getByte(index.toLong()).toInt() and 255; 2 -> raw.getShort(index * 2L).toInt() and 65535; else -> raw.getInt(index * 4L) }
    fun put(index: Int, value: Int) { when (unit) { 1 -> raw.setByte(index.toLong(), value.toByte()); 2 -> raw.setShort(index * 2L, value.toShort()); else -> raw.setInt(index * 4L, value) } }
    fun remove(count: Int): GhcCodeBuffer = if (left + count == right) copy(left = 0, right = 0) else copy(left = left + count)
    fun summary(): String = "0x${Pointer.nativeValue(raw).toULong().toString(16)}@buf$size($left-$right) (>=$offset)"
    fun byteView(): GhcCodeBuffer = copy(unit = 1, size = size * 2, left = left * 2, right = right * 2)
    fun cwcharView(): GhcCodeBuffer {
        fun half(n: Int): Int { if (n % 2 != 0) throw IllegalStateException("cwcharView: utf16_(encode|decode) (wrote out|consumed) non multiple-of-2 number of bytes"); return n / 2 }
        return copy(unit = 2, size = half(size), left = half(left), right = half(right))
    }
}
internal data class GhcCodingStep(val why: GhcCodingProgress, val input: GhcCodeBuffer, val output: GhcCodeBuffer)
internal typealias GhcBufferCode = (GhcCodeBuffer, GhcCodeBuffer) -> GhcCodingStep

internal inline fun <A> withCodeBuffer(unit: Int, size: Int, left: Int = 0, right: Int = 0, write: Boolean = false,
    action: (GhcCodeBuffer) -> A): A = Memory(maxOf(1L, unit.toLong() * size)).use { action(GhcCodeBuffer(it, unit, size, left, right, write = write)) }

internal object GhcCodePageRecovery {
    fun decode(mode: GhcCodingFailureMode, byte: Int): Int? = when (mode) {
        GhcCodingFailureMode.Ignore -> null
        GhcCodingFailureMode.Transliterate -> 0xfffd
        GhcCodingFailureMode.Roundtrip -> if (byte < 128) byte else 0xdc00 + byte
        GhcCodingFailureMode.Error -> throw GhcIOException(GhcIoError(IoErrorType.InvalidArgument, Chars.fromString("recoverDecode"), Chars.fromString("cannot decode byte sequence starting from $byte")))
    }
    fun encodingError(point: Int): Nothing {
        val shown = when (point) { 39 -> "'\\''"; 34 -> "'\"'"; else -> "'" + (if (point < 0) "\\${point.toUInt().toLong()}" else HaskellString.quoteCodePoints(intArrayOf(point)).let { it.substring(1, it.length - 1) }) + "'" }
        throw GhcIOException(GhcIoError(IoErrorType.InvalidArgument, Chars.fromString("recoverEncode"), Chars.fromString("cannot encode character $shown")))
    }
    fun encode(mode: GhcCodingFailureMode, input: GhcCodeBuffer, output: GhcCodeBuffer): Pair<GhcCodeBuffer, GhcCodeBuffer> {
        val point = input.read(input.left)
        return when (mode) {
            GhcCodingFailureMode.Ignore -> input.copy(left = input.left + 1) to output
            GhcCodingFailureMode.Transliterate -> if (point == 63) input.copy(left = input.left + 1) to output else {
                input.put(input.left, 63); input to output
            }
            GhcCodingFailureMode.Roundtrip -> if (point in 0xdc80..0xdcff) {
                output.put(output.right, point); input.copy(left = input.left + 1) to output.copy(right = output.right + 1)
            } else encodingError(point)
            GhcCodingFailureMode.Error -> encodingError(point)
        }
    }
}

/** Original C-string buffer loops applied to a fresh stateless CodePage codec. */
internal class GhcBufferCStringCodec(private val mode: GhcCodingFailureMode,
    private val decoder: () -> GhcBufferCode, private val encoder: () -> GhcBufferCode) : GhcCStringCodec {
    override fun decode(bytes: ByteArray): IntArray {
        val code = decoder() // newCP/GetCPInfo happens even for an empty CString.
        return withCodeBuffer(1, bytes.size, right = bytes.size) { original ->
            bytes.forEachIndexed { i, b -> original.put(i, b.toInt()) }
            withCodeBuffer(4, maxOf(1, bytes.size), write = true) { emptyOutput ->
                var input = original
                buildList {
                    while (true) {
                        val step = code(input, emptyOutput); input = step.input; var output = step.output
                        val exhausted = input.empty
                        if (!input.empty && step.why != GhcCodingProgress.OutputUnderflow) {
                            GhcCodePageRecovery.decode(mode, input.read(input.left))?.let { output.put(output.right, it); output = output.copy(right = output.right + 1) }
                            input = input.copy(left = input.left + 1)
                        }
                        for (i in output.left..<output.right) add(output.read(i))
                        if (exhausted) break
                    }
                }.toIntArray()
            }
        }
    }
    override fun encodeCString(points: IntArray, nullTerminate: Boolean): ByteArray {
        val code = encoder()
        return withCodeBuffer(4, points.size, right = points.size) { original ->
            points.forEachIndexed(original::put)
            var capacity = points.size + 1
            while (true) {
                val result = withCodeBuffer(1, capacity, write = true) { emptyOutput ->
                    var input = original; var output = emptyOutput
                    while (true) {
                        val step = code(input, output); input = step.input; output = step.output
                        if (input.empty) return@withCodeBuffer if (nullTerminate && output.available == 0) null else output.raw.getByteArray(0, output.right)
                        if (step.why == GhcCodingProgress.OutputUnderflow) return@withCodeBuffer null
                        val recovered = GhcCodePageRecovery.encode(mode, input, output); input = recovered.first; output = recovered.second
                    }
                    @Suppress("UNREACHABLE_CODE") null
                }
                if (result != null) return@withCodeBuffer result
                capacity = Math.multiplyExact(capacity, 2)
            }
            @Suppress("UNREACHABLE_CODE") byteArrayOf()
        }
    }
}

internal object GhcCodePageUtf16 {
    fun encode(input: GhcCodeBuffer, output: GhcCodeBuffer): GhcCodingStep {
        var i = input.left; var o = output.right
        fun done(why: GhcCodingProgress) = GhcCodingStep(why, input.remove(i - input.left), output.copy(right = o))
        while (true) {
            if (i >= input.right) return done(GhcCodingProgress.InputUnderflow)
            if (output.size - o < 1) return done(GhcCodingProgress.OutputUnderflow)
            val cp = input.read(i).toUInt()
            if (cp < 0x10000u) {
                if (cp in 0xd800u..0xdfffu) return done(GhcCodingProgress.InvalidSequence)
                output.put(o++, cp.toInt()); i++
            } else {
                if (output.size - o < 2) return done(GhcCodingProgress.OutputUnderflow)
                val n = cp - 0x10000u
                output.put(o++, ((n shr 10) + 0xd800u).toInt() and 65535); output.put(o++, ((n and 1023u) + 0xdc00u).toInt()); i++
            }
        }
    }
    fun decode(input: GhcCodeBuffer, output: GhcCodeBuffer): GhcCodingStep {
        var i = input.left; var o = output.right
        fun done(why: GhcCodingProgress) = GhcCodingStep(why, input.remove(i - input.left), output.copy(right = o))
        while (true) {
            if (o >= output.size) return done(GhcCodingProgress.OutputUnderflow)
            if (i >= input.right) return done(GhcCodingProgress.InputUnderflow)
            val first = input.read(i)
            if (first !in 0xd800..0xdfff) { output.put(o++, first); i++ }
            else {
                if (input.right - i < 2) return done(GhcCodingProgress.InputUnderflow)
                val second = input.read(i + 1)
                if (first !in 0xd800..0xdbff || second !in 0xdc00..0xdfff) return done(GhcCodingProgress.InvalidSequence)
                output.put(o++, 0x10000 + (first - 0xd800) * 1024 + second - 0xdc00); i += 2
            }
        }
    }
    fun saner(code: GhcBufferCode, input: GhcCodeBuffer, output: GhcCodeBuffer): Pair<GhcCodingStep, Int> {
        val step = code(input, output)
        return if (step.input.empty) step.copy(why = GhcCodingProgress.InputUnderflow) to input.elements else step to step.input.left - input.left
    }
}
