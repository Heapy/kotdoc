// GHC 9.12.2 IO.Encoding.CodePage.API and Windows error effects.
// University of Glasgow; complete licenses/GHC-base-BSD-3-Clause.txt.
package io.heapy.kotdoc.resources

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import io.heapy.kotdoc.errors.GhcIOException
import io.heapy.kotdoc.errors.GhcWindowsErrors
import io.heapy.kotdoc.layout.CharacterStringChars as Chars

internal data class GhcCpInfo(val maxCharSize: UInt, val defaultChar: IntArray, val leadByte: IntArray) {
    fun poke(pointer: Pointer) {
        pointer.setInt(0, maxCharSize.toInt())
        fun put(name: String, size: Int, values: IntArray, offset: Long) {
            if (values.size != size) throw IllegalStateException("CPINFO.$name: expected $size elements in list but got ${values.size}")
            values.forEachIndexed { i, b -> pointer.setByte(offset + i, b.toByte()) }
        }
        put("defaultChar", 2, defaultChar, 4); put("leadByte", 12, leadByte, 6)
    }
    companion object {
        const val size = 18 // Original Storable size; native CPINFO has two tail padding bytes.
        fun peek(pointer: Pointer) = GhcCpInfo(pointer.getInt(0).toUInt(), IntArray(2) { pointer.getByte(it + 4L).toInt() and 255 }, IntArray(12) { pointer.getByte(it + 6L).toInt() and 255 })
    }
}
internal interface GhcCodePageApi {
    fun getConsoleCP(): UInt
    fun getACP(): UInt
    fun getCPInfo(cp: UInt, output: Pointer): Boolean
    fun multiByteToWideChar(cp: UInt, flags: UInt, input: Pointer, count: Int, output: Pointer, capacity: Int): Int
    fun wideCharToMultiByte(cp: UInt, flags: UInt, input: Pointer, count: Int, output: Pointer, capacity: Int, defaultChar: Pointer?, usedDefault: Pointer): Int
    fun isDBCSLeadByte(cp: UInt, byte: Int): Boolean
    fun getLastError(): UInt
    fun failWith(location: String, error: UInt): Nothing
}
internal interface GhcCodePageKernel : StdCallLibrary {
    fun GetConsoleCP(): Int
    fun GetACP(): Int
    fun GetCPInfo(cp: Int, output: Pointer): Int
    fun MultiByteToWideChar(cp: Int, flags: Int, input: Pointer, count: Int, output: Pointer, capacity: Int): Int
    fun WideCharToMultiByte(cp: Int, flags: Int, input: Pointer, count: Int, output: Pointer, capacity: Int, defaultChar: Pointer?, usedDefault: Pointer): Int
    fun IsDBCSLeadByteEx(cp: Int, byte: Byte): Int
}
internal object GhcNativeCodePageApi : GhcCodePageApi {
    private val kernel by lazy { Native.load("Kernel32", GhcCodePageKernel::class.java) }
    private val error = ThreadLocal.withInitial { 0u }
    private fun capture(result: Int): Int { error.set(Native.getLastError().toUInt()); return result }
    override fun getConsoleCP() = capture(kernel.GetConsoleCP()).toUInt()
    override fun getACP() = capture(kernel.GetACP()).toUInt()
    override fun getCPInfo(cp: UInt, output: Pointer) = capture(kernel.GetCPInfo(cp.toInt(), output)) != 0
    override fun multiByteToWideChar(cp: UInt, flags: UInt, input: Pointer, count: Int, output: Pointer, capacity: Int) = capture(kernel.MultiByteToWideChar(cp.toInt(), flags.toInt(), input, count, output, capacity))
    override fun wideCharToMultiByte(cp: UInt, flags: UInt, input: Pointer, count: Int, output: Pointer, capacity: Int, defaultChar: Pointer?, usedDefault: Pointer) = capture(kernel.WideCharToMultiByte(cp.toInt(), flags.toInt(), input, count, output, capacity, defaultChar, usedDefault))
    override fun isDBCSLeadByte(cp: UInt, byte: Int) = capture(kernel.IsDBCSLeadByteEx(cp.toInt(), byte.toByte())) != 0
    override fun getLastError() = error.get()
    override fun failWith(location: String, error: UInt): Nothing = throw GhcIOException(GhcWindowsErrors.toError(Chars.fromString(location), error, GhcNativeWindowsLookup))
}

internal object GhcCodePageApiBuffers {
    private fun maxCharSize(cp: UInt, api: GhcCodePageApi): Int = Memory(20).use { info ->
        if (!api.getCPInfo(cp, info)) api.failWith("GetCPInfo $cp", api.getLastError())
        GhcCpInfo.peek(info).maxCharSize.toInt()
    }
    fun decoder(cp: UInt, api: GhcCodePageApi): GhcBufferCode {
        val max = maxCharSize(cp, api)
        return { input, output -> decode(cp, max, api, input, output) }
    }
    fun encoder(cp: UInt, api: GhcCodePageApi): GhcBufferCode {
        maxCharSize(cp, api)
        return { input, output -> encode(cp, api, input, output) }
    }
    private sealed interface Attempt {
        data class Wrote(val count: Int, val written: Int) : Attempt
        data class Stopped(val why: GhcCodingProgress) : Attempt
    }
    /** null means success; true/false indicate insufficient output/invalid input. */
    internal data class Call(val written: Int = 0, val failure: Boolean? = null)
    fun recode(attempt: (Pointer, Int, Pointer, Int) -> Call, prefix: (Int) -> Boolean, maxInput: Int, minOutput: Int,
        input: GhcCodeBuffer, output: GhcCodeBuffer): GhcCodingStep {
        var incoming = input; var outgoing = output
        while (true) {
            if (incoming.empty) return GhcCodingStep(GhcCodingProgress.InputUnderflow, incoming, outgoing)
            if (outgoing.available < minOutput) return GhcCodingStep(GhcCodingProgress.OutputUnderflow, incoming, outgoing)
            val frame = RecodeFrame(attempt, prefix, maxInput, incoming, outgoing)
            val result = frame.trySize(minOf(incoming.elements.toLong(), maxInput.toLong() * outgoing.available / minOutput).toInt(), frame::seekSmaller)
            when (result) {
                is Attempt.Stopped -> return GhcCodingStep(result.why, incoming, outgoing)
                is Attempt.Wrote -> { incoming = incoming.remove(result.count); outgoing = outgoing.copy(right = outgoing.right + result.written) }
            }
        }
    }
    private class RecodeFrame(val call: (Pointer, Int, Pointer, Int) -> Call, val prefix: (Int) -> Boolean,
        val max: Int, val input: GhcCodeBuffer, val output: GhcCodeBuffer) {
        fun trySize(n: Int, failed: (Int, Boolean) -> Attempt): Attempt {
            val result = call(input.raw.share(input.left.toLong() * input.unit), n, output.raw.share(output.right.toLong() * output.unit), output.available)
            return result.failure?.let { failed(n, it) } ?: Attempt.Wrote(n, result.written)
        }
        fun seekSmaller(n: Int, longerValid: Boolean): Attempt = when {
            n <= 1 -> Attempt.Stopped(if (longerValid) GhcCodingProgress.OutputUnderflow else {
                if (prefix(input.read(input.left)) && input.elements < max) GhcCodingProgress.InputUnderflow else GhcCodingProgress.InvalidSequence
            })
            n < 2 * max -> trySize(n - 1) { smaller, valid -> seekSmaller(smaller, longerValid || valid) }
            else -> trySize(n / 2) { size, valid -> postDivide(n / 2, longerValid, size, valid) }
        }
        private fun postDivide(half: Int, longerValid: Boolean, n: Int, valid: Boolean): Attempt = when {
            valid -> seekSmaller(n, true)
            n < half + max - 1 -> trySize(n + 1) { size, good -> postDivide(half, longerValid, size, good) }
            else -> seekSmaller(half, longerValid)
        }
    }
    fun bSearch(message: String, code: GhcBufferCode, input: GhcCodeBuffer, temporary: GhcCodeBuffer,
        target: Int, minimum: Int, middle: Int, maximum: Int): Int {
        var min = minimum; var mid = middle; var max = maximum
        while (true) {
            val step = code(input.copy(right = input.left + mid), temporary)
            when {
                step.output.elements == target -> return mid - step.input.elements
                step.output.elements < target -> min = mid + 1
                else -> max = mid - 1
            }
            if (min > max) throw IllegalStateException("bSearch($message): search crossed! (\"${input.summary()}\",\"${temporary.summary()}\",$target,$min,$max)")
            mid = min + (max - min) / 2
        }
    }
    private fun nativeResult(location: String, result: Int, api: GhcCodePageApi): Call = if (result != 0) Call(result) else {
        val error = api.getLastError()
        when (error) { 122u -> Call(failure = true); 1113u -> Call(failure = false); else -> api.failWith(location, error) }
    }
    fun decode(cp: UInt, max: Int, api: GhcCodePageApi, input: GhcCodeBuffer, output: GhcCodeBuffer): GhcCodingStep =
        withCodeBuffer(2, minOf(input.elements * 2, output.available * 2), write = true) { temporary ->
            val attempt: (Pointer, Int, Pointer, Int) -> Call = { p, count, out, size ->
                if (size == 0) Call(failure = true) else nativeResult("MultiByteToWideChar", api.multiByteToWideChar(cp, 8u, p, count, out, size), api)
            }
            val code: GhcBufferCode = { i, o -> recode(attempt, { api.isDBCSLeadByte(cp, it) }, max, 1, i, o) }
            val first = code(input, temporary)
            val (second, consumed) = GhcCodePageUtf16.saner(GhcCodePageUtf16::decode, first.output.copy(write = false), output)
            when (second.why) {
                GhcCodingProgress.InputUnderflow -> if (second.input.empty) GhcCodingStep(first.why, first.input, second.output) else throw IllegalStateException("cpDecode: impossible underflown UTF-16 buffer")
                GhcCodingProgress.InvalidSequence -> throw IllegalStateException("InvalidSequence on output of Windows API")
                GhcCodingProgress.OutputUnderflow -> GhcCodingStep(GhcCodingProgress.OutputUnderflow,
                    input.remove(bSearch("cpDecode", code, input, temporary, consumed, consumed / 2, consumed, consumed * max)), second.output)
            }
        }
    fun encode(cp: UInt, api: GhcCodePageApi, input: GhcCodeBuffer, output: GhcCodeBuffer): GhcCodingStep =
        withCodeBuffer(2, minOf(input.elements * 2, output.available * 2), write = true) { temporary ->
            val first = GhcCodePageUtf16.encode(input, temporary)
            val code: GhcBufferCode = { i, o -> recode({ p, count, out, size ->
                if (size == 0) Call(failure = true) else Memory(4).use { used ->
                    used.setInt(0, 0)
                    val result = api.wideCharToMultiByte(cp, 0u, p, count, out, size, null, used)
                    val defaulted = used.getInt(0) != 0
                    val call = nativeResult("WideCharToMultiByte", result, api)
                    if (call.failure == null && defaulted) Call(failure = false) else call
                }
            }, { false }, 2, 1, i, o) }
            val (second, consumed) = GhcCodePageUtf16.saner(code, first.output.copy(write = false), output)
            if (second.why == GhcCodingProgress.InputUnderflow) {
                if (!second.input.empty) throw IllegalStateException("cpEncode: impossible underflown UTF-16 buffer")
                GhcCodingStep(first.why, first.input, second.output)
            } else GhcCodingStep(second.why, input.remove(bSearch("cpEncode", GhcCodePageUtf16::encode, input, temporary, consumed, consumed / 2, consumed, consumed)), second.output)
        }
}
