// GHC 9.12.2 Internal.System.Environment getEnv/lookupEnv and Windows wide strings.
// University of Glasgow; complete BSD notice and pinned sources retained.
package io.heapy.kotdoc.resources

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import io.heapy.kotdoc.errors.*
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars

interface GhcWindowsLookupApi: GhcWindowsErrorApi {
    fun query(name: Pointer, buffer: Pointer, size: UInt): UInt
}
internal interface GhcEnvironmentLookupKernel: StdCallLibrary {
    fun GetEnvironmentVariableW(name: Pointer, buffer: Pointer, size: Int): Int
    fun FormatMessageW(flags: Int, source: Pointer?, code: Int, language: Int, output: PointerByReference, size: Int, arguments: Pointer?): Int
    fun LocalFree(pointer: Pointer): Pointer?
}
internal object GhcNativeWindowsLookup: GhcWindowsLookupApi {
    private val kernel by lazy { Native.load("Kernel32", GhcEnvironmentLookupKernel::class.java) }
    private val lastError = ThreadLocal.withInitial { 0u }
    override fun query(name: Pointer, buffer: Pointer, size: UInt): UInt {
        val result = kernel.GetEnvironmentVariableW(name, buffer, size.toInt())
        lastError.set(Native.getLastError().toUInt())
        return result.toUInt()
    }
    override fun getLastError() = lastError.get()
    override fun getErrorMessage(code: UInt): Pointer? {
        val output = PointerByReference()
        val count = kernel.FormatMessageW(0x1100, null, code.toInt(), 0x400, output, 0, null)
        return if (count == 0) null else output.value
    }
    override fun localFree(pointer: Pointer) { kernel.LocalFree(pointer) }
}

object GhcEnvironmentLookup {
    private val libc by lazy { NativeLibrary.getInstance(Platform.C_LIBRARY_NAME) }
    fun lookupEnv(name: CharacterString): CharacterString? = if (Platform.isWindows()) windowsLookup(name, GhcNativeWindowsLookup)
        else posixLookup(name, GhcForeignCString::currentPosix, GhcEncodings::getFileSystemEncoding) { libc.getFunction("getenv").invokePointer(arrayOf(it)) }
    fun getEnv(name: CharacterString): CharacterString {
        val value = lookupEnv(name)
        if (value != null) return value
        if (Platform.isWindows() && GhcNativeWindowsLookup.getLastError() != 203u)
            GhcWindowsErrors.throwLastError("getEnv", GhcNativeWindowsLookup)
        missing(name)
    }
    internal fun posixLookup(name: CharacterString, foreign: () -> GhcForeignCString, filesystem: () -> GhcTextEncoding,
        query: (Pointer) -> Pointer?): CharacterString? = foreign().withCString(name) { encoded ->
        val value = query(encoded)
        if (value == null) null else CharacterString(filesystem().decode(value.getByteArray(0, value.indexOf(0, 0).toInt())))
    }
    internal fun posixGet(name: CharacterString, foreign: () -> GhcForeignCString, filesystem: () -> GhcTextEncoding,
        query: (Pointer) -> Pointer?): CharacterString = posixLookup(name, foreign, filesystem, query) ?: missing(name)
    internal fun windowsLookup(name: CharacterString, api: GhcWindowsLookupApi): CharacterString? {
        val units = GhcWindowsErrors.wideUnits(name)
        return Memory((units.size + 1L) * 2).use { pointer ->
            units.forEachIndexed { i, unit -> pointer.setShort(i * 2L, unit.toShort()) }; pointer.setShort(units.size * 2L, 0)
            var size = 256u
            while (true) {
                val value = Memory(size.toLong() * 2).use { buffer ->
                    val result = api.query(pointer, buffer, size)
                    when { result == 0u -> LookupResult.Done(null)
                        result > size -> LookupResult.Grow(result)
                        else -> LookupResult.Done(GhcWindowsErrors.readWide(buffer)) }
                }
                when (value) { is LookupResult.Done -> return@use value.value; is LookupResult.Grow -> size = value.size }
            }
            @Suppress("UNREACHABLE_CODE") null
        }
    }
    internal fun windowsGet(name: CharacterString, api: GhcWindowsLookupApi): CharacterString {
        windowsLookup(name, api)?.let { return it }
        if (api.getLastError() == 203u) missing(name)
        GhcWindowsErrors.throwLastError("getEnv", api)
    }
    private sealed interface LookupResult {
        data class Grow(val size: UInt): LookupResult
        data class Done(val value: CharacterString?): LookupResult
    }
    private fun missing(name: CharacterString): Nothing = throw GhcIOException(GhcIoError(
        IoErrorType.NoSuchThing, Chars.fromString("getEnv"), Chars.fromString("no environment variable"), filename = name))
}
