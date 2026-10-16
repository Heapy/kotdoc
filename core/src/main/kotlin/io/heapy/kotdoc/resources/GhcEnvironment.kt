// GHC 9.12.2 GHC.Internal.System.Environment.getEnvironment/divvy and selected
// C-string decoding. (c) University of Glasgow 2001; full licenses/GHC-base-BSD-3-Clause.txt.
package io.heapy.kotdoc.resources

import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary

internal interface GhcWindowsEnvironmentApi : StdCallLibrary {
    fun GetEnvironmentStringsW(): Pointer?
    fun FreeEnvironmentStringsW(block: Pointer?): Int
}

/** Reads the live OS block rather than the JVM's cached environment map. */
object GhcEnvironment {
    private val libc by lazy { NativeLibrary.getInstance(Platform.C_LIBRARY_NAME) }
    private val windows by lazy { Native.load("Kernel32", GhcWindowsEnvironmentApi::class.java) }

    /** UTF-16 facade. Use getEnvironmentCodePoints to retain unsafe out-of-range
     * Char slots produced by some native iconv aliases without replacement. */
    fun getEnvironment(): List<Pair<String, String>> = getEnvironmentCodePoints().map { toJvmString(it.first) to toJvmString(it.second) }

    fun getEnvironmentCodePoints(): List<Pair<IntArray, IntArray>> = if (Platform.isWindows()) {
        windowsEnvironment(windows).map { it.first.codePoints().toArray() to it.second.codePoints().toArray() }
    } else {
        posixEnvironmentCodePoints({
            if (Platform.isMac()) libc.getFunction("_NSGetEnviron").invokePointer(emptyArray()).getPointer(0)
            else libc.getGlobalVariableAddress("environ").getPointer(0)
        }, GhcEncodings::getFileSystemEncoding)
    }

    internal fun posixEnvironment(block: () -> Pointer?, encoding: () -> GhcTextEncoding): List<Pair<String, String>> {
        return posixEnvironmentCodePoints(block, encoding).map { toJvmString(it.first) to toJvmString(it.second) }
    }

    internal fun posixEnvironmentCodePoints(block: () -> Pointer?, encoding: () -> GhcTextEncoding): List<Pair<IntArray, IntArray>> {
        val array = block() ?: return emptyList()
        val decoder = encoding()
        val pointers = buildList {
            var at = 0L
            while (true) {
                val p = array.getPointer(at) ?: break
                add(p); at += Native.POINTER_SIZE
            }
        }
        return pointers.map { p ->
            val bytes = p.getByteArray(0, p.indexOf(0, 0.toByte()).toInt())
            val points = decoder.decode(bytes)
            val equal = points.indexOf(61)
            if (equal < 0) points to intArrayOf() else points.copyOf(equal) to points.copyOfRange(equal + 1, points.size)
        }
    }

    internal fun windowsEnvironment(api: GhcWindowsEnvironmentApi): List<Pair<String, String>> {
        val block = api.GetEnvironmentStringsW()
        try {
            if (block == null) return emptyList()
            return buildList {
                var at = 0L
                while (block.getShort(at).toInt() != 0) {
                    var next = at + 2
                    // Original seekNull deliberately peeks Word8 while moving
                    // by sizeof(CWchar). Preserve low-byte-zero splitting.
                    while (block.getByte(next).toInt() != 0) next += 2
                    next += 2
                    val text = buildString {
                        var p = at
                        while (true) {
                            val c = block.getShort(p).toInt() and 65535
                            if (c == 0) break
                            append(c.toChar()); p += 2
                        }
                    }
                    add(divvy(text)); at = next
                }
            }
        } finally { api.FreeEnvironmentStringsW(block) } // Bool result is ignored, including null blocks.
    }

    internal fun divvy(text: String): Pair<String, String> {
        val equal = text.indexOf('=')
        return if (equal < 0) text to "" else text.substring(0, equal) to text.substring(equal + 1)
    }
    private fun toJvmString(points: IntArray): String = buildString { points.forEach { appendCodePoint(it) } }
}
