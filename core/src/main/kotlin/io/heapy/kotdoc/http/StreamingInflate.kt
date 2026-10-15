// Data.Streaming.Zlib 0.2.3.1, Copyright (c) 2014 FP Complete, MIT.
// See licenses/streaming-commons-MIT.txt. The C zlib API is an effect boundary.
package io.heapy.kotdoc.http

import com.sun.jna.*
import java.lang.ref.Cleaner
import java.lang.ref.Reference

@Structure.FieldOrder("next_in", "avail_in", "total_in", "next_out", "avail_out", "total_out", "msg", "state", "zalloc", "zfree", "opaque", "data_type", "adler", "reserved")
internal class ZStream : Structure() {
    @JvmField var next_in: Pointer? = null
    @JvmField var avail_in: Int = 0
    @JvmField var total_in = NativeLong(0)
    @JvmField var next_out: Pointer? = null
    @JvmField var avail_out: Int = 0
    @JvmField var total_out = NativeLong(0)
    @JvmField var msg: Pointer? = null
    @JvmField var state: Pointer? = null
    @JvmField var zalloc: Pointer? = null
    @JvmField var zfree: Pointer? = null
    @JvmField var opaque: Pointer? = null
    @JvmField var data_type: Int = 0
    @JvmField var adler = NativeLong(0)
    @JvmField var reserved = NativeLong(0)
}
internal interface ZlibFunctions : Library {
    fun zlibVersion(): String
    fun inflateInit2_(stream: ZStream, bits: Int, version: String, size: Int): Int
    fun inflate(stream: ZStream, flush: Int): Int
    fun inflateSetDictionary(stream: ZStream, dictionary: ByteArray, length: Int): Int
    fun inflateEnd(stream: ZStream): Int
}
sealed interface InflatePop {
    data object Done : InflatePop
    data class Next(val bytes: HttpBytes) : InflatePop
    data class Error(val code: Int) : InflatePop
}

/** The pinned streaming-commons inflater, including its output buffering and
 * error ordering. Feeding another chunk does not reset a completed gzip member.
 * EOF does not implicitly finish or validate a truncated compressed stream. */
class StreamingInflate(bits: Int = 15, private val dictionary: HttpBytes? = null) : AutoCloseable {
    private class State(bits: Int) : Runnable {
        val stream = ZStream()
        val output = Memory(32752)
        var input: Memory? = null
        var last = HttpBytes.empty
        var complete = false
        var closed = false
        init {
            // Lowlevel.inflateInit2 discards the initialization return code.
            functions.inflateInit2_(stream, bits, functions.zlibVersion(), stream.size())
            stream.next_out = output; stream.avail_out = 32752
        }
        override fun run() {
            if (!closed) {
                closed = true
                functions.inflateEnd(stream)
                input?.close(); output.close()
            }
        }
    }
    private val state = State(bits)
    private val cleanable = cleaner.register(this, state)
    fun feed(bytes: HttpBytes): () -> InflatePop {
        check(!state.closed)
        state.last = bytes
        state.input?.close()
        state.input = Memory(maxOf(1, bytes.size).toLong()).also { if (bytes.size > 0) it.write(0, bytes.bytes(), 0, bytes.size) }
        state.stream.next_in = state.input; state.stream.avail_in = bytes.size
        // Updating the two pointer fields must be visible to subsequent reads
        // even when no popper has yet been invoked.
        state.stream.write()
        return { drain().also { Reference.reachabilityFence(this) } }
    }
    private fun drain(): InflatePop {
        check(!state.closed)
        var code = functions.inflate(state.stream, 0)
        if (code == 2 && dictionary != null) {
            functions.inflateSetDictionary(state.stream, dictionary.bytes(), dictionary.size)
            code = functions.inflate(state.stream, 0)
        }
        if (code == 1) state.complete = true
        if (code < 0 && code != -5) return InflatePop.Error(code)
        if (state.stream.avail_out != 0) return InflatePop.Done
        val bytes = HttpBytes(state.output.getByteArray(0, 32752))
        resetOutput()
        return InflatePop.Next(bytes)
    }
    private fun resetOutput() {
        state.stream.next_out = state.output; state.stream.avail_out = 32752
        state.stream.write()
    }
    fun finish(): HttpBytes {
        check(!state.closed)
        val size = 32752 - state.stream.avail_out
        val bytes = HttpBytes(state.output.getByteArray(0, size))
        resetOutput()
        return bytes
    }
    fun flush() = finish()
    fun unused(): HttpBytes {
        check(!state.closed)
        val bytes = state.last.bytes()
        return HttpBytes(bytes.copyOfRange(maxOf(0, bytes.size - state.stream.avail_in), bytes.size))
    }
    fun isComplete() = state.complete
    override fun close() = cleanable.clean()
    companion object {
        private val cleaner = Cleaner.create()
        private val functions by lazy { Native.load(if (Platform.isWindows()) "zlib1" else "z", ZlibFunctions::class.java) }
    }
}
