// Selected GHC 9.12.2 Foreign.C.String/Encoding and IO.Encoding IgnoreCodingFailure.
// Copyright University of Glasgow; complete licenses/GHC-base-BSD-3-Clause.txt.
package io.heapy.kotdoc.resources

import com.sun.jna.Memory
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import com.sun.jna.Pointer
import io.heapy.kotdoc.layout.CharacterString

internal object GhcLocaleEncodingName {
    val initial: String by ghcEncodingLazy {
        val libc = NativeLibrary.getInstance(Platform.C_LIBRARY_NAME)
        libc.getFunction("nl_langinfo").invokePointer(arrayOf(if (Platform.isMac()) 0 else 14)).getString(0, "US-ASCII")
    }
}
/** Foreign encoding uses IgnoreCodingFailure, independently of filesystem
 * RoundtripFailure. Character slots remain distinct from UTF-16 code units. */
class GhcForeignCString private constructor(private val name: () -> String, private val encoding: () -> GhcEnvironmentEncoding) {
    private constructor(name: String, codec: GhcEnvironmentEncoding) : this({ name }, { codec })
    constructor(encodingName: String) : this(encodingName, GhcEnvironmentEncoding(encodingName, ignoreFailures = true))
    constructor(encoding: GhcTextEncoding) : this({ encoding.textEncodingName }, { encoding.codec })
    val encodingName: String get() = name()
    fun encode(points: IntArray, nullTerminate: Boolean = true): ByteArray = encoding().encodeCString(points, nullTerminate)
    fun decode(bytes: ByteArray): IntArray = encoding().decode(bytes)
    fun decode(pointer: Pointer): IntArray = decode(pointer.getByteArray(0, pointer.indexOf(0, 0).toInt()))
    fun <A> withCString(text: CharacterString, action: (Pointer) -> A): A {
        val data = encode(text.points())
        return Memory(data.size.toLong() + 1).use { p ->
            if (data.isNotEmpty()) p.write(0, data, 0, data.size)
            p.setByte(data.size.toLong(), 0)
            action(p)
        }
    }
    companion object {
        private fun requirePosix() {
            check(Platform.isMac() || Platform.isLinux()) { "POSIX foreign encoding requires Darwin or Linux/glibc" }
        }
        fun initialPosix(): GhcForeignCString {
            requirePosix()
            return GhcForeignCString(GhcEncodings.initForeignEncoding)
        }
        fun currentPosix(): GhcForeignCString {
            requirePosix()
            return GhcForeignCString(GhcEncodings.getForeignEncoding())
        }
    }
}
