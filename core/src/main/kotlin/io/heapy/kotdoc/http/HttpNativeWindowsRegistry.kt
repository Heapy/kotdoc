// Selected System.Win32.Registry 2.14.2.1 wrappers, (c) Alastair Reid 1997-2003,
// Esa Ilari Vuokko 2006, shelarcy 2012-2013. Full BSD notice: licenses/Win32-BSD-3-Clause.txt.
package io.heapy.kotdoc.http

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import java.io.IOException

internal interface HttpRegistryFunctions : StdCallLibrary {
    fun RegOpenKeyW(key: Pointer, subkey: WString, result: PointerByReference): Int
    fun RegQueryValueW(key: Pointer, subkey: WString?, data: Pointer?, size: IntByReference): Int
    fun RegQueryValueExW(key: Pointer, name: WString, reserved: Pointer?, type: IntByReference, data: Pointer, size: IntByReference): Int
    fun RegCloseKey(key: Pointer): Int
}

/** Loads Advapi32 only on first actual use. No registry modification is performed. */
object HttpNativeWindowsRegistry : HttpProxyRegistry {
    private val functions by lazy { Native.load("Advapi32", HttpRegistryFunctions::class.java) }
    override fun openCurrentUser(path: String): HttpProxyRegistryKey = open(functions, path)
    internal fun open(api: HttpRegistryFunctions, path: String): HttpProxyRegistryKey {
        val result = PointerByReference()
        checkError("RegOpenKey", api.RegOpenKeyW(Pointer.createConstant(-2147483647L), WString(path), result))
        val handle = checkNotNull(result.value)
        return object : HttpProxyRegistryKey {
            override fun queryDword(name: String): UInt? = Memory(4).use { data ->
                val type = IntByReference()
                val size = IntByReference(4)
                checkError("RegQueryValueEx", api.RegQueryValueExW(handle, WString(name), null, type, data, size))
                if (type.value == 4) data.getInt(0).toUInt() else null
            }
            override fun queryValue(subkey: String?): String {
                val name = subkey?.let(::WString)
                val size = IntByReference()
                checkError("RegQueryValue", api.RegQueryValueW(handle, name, null, size))
                // Original allocaArray0 allocates value_len+1 WCHARs (the API
                // reports bytes); preserve that capacity and the original size.
                return Memory((size.value.toLong() + 1) * 2).use { data ->
                    checkError("RegQueryValue", api.RegQueryValueW(handle, name, data, size))
                    // WCHAR is two bytes on Windows, independently of the host
                    // JNA wchar_t size used by cross-platform boundary tests.
                    buildString {
                        var offset = 0L
                        while (offset + 1 < data.size()) {
                            val c = data.getShort(offset).toInt() and 65535
                            if (c == 0) break
                            append(c.toChar()); offset += 2
                        }
                    }
                }
            }
            override fun close() { checkError("RegCloseKey", api.RegCloseKey(handle)) }
        }
    }
    private fun checkError(function: String, error: Int) {
        if (error != 0) throw IOException("$function: Windows error ${error.toUInt()}")
    }
}
