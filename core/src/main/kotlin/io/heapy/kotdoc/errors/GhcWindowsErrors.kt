// GHC 9.12.2 Internal.Windows and cbits/Win32Utils.c; University of Glasgow.
// Full BSD notice/source retained. Numeric headers: mingw-w64 v12.0.0, Public Domain.
package io.heapy.kotdoc.errors

import com.sun.jna.Pointer
import io.heapy.kotdoc.formats.HaskellNumber
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import kotlinx.serialization.json.*

interface GhcWindowsErrorApi {
    fun getLastError(): UInt
    fun getErrorMessage(code: UInt): Pointer?
    fun localFree(pointer: Pointer)
}

object GhcWindowsErrors {
    private val data by lazy {
        checkNotNull(javaClass.getResourceAsStream("/io/heapy/kotdoc/errors/windows-errors.json")).bufferedReader().use {
            Json.parseToJsonElement(it.readText()).jsonObject
        }
    }
    private fun result(v: JsonArray) = v[0].jsonPrimitive.int to IoErrorType.valueOf(v[1].jsonPrimitive.content)
    fun mapErrno(code: UInt): Pair<Int, IoErrorType> {
        val table = data.getValue("table").jsonArray
        table.firstOrNull { it.jsonArray[0].jsonPrimitive.long == code.toLong() }?.jsonArray?.let { return result(JsonArray(it.drop(1))) }
        fun inside(name: String) = data.getValue(name).jsonArray.let { code.toLong() in it[0].jsonPrimitive.long..it[1].jsonPrimitive.long }
        return result(data.getValue(if (inside("accessRange")) "access" else if (inside("execRange")) "exec" else "default").jsonArray)
    }
    fun errorMessage(code: UInt, api: GhcWindowsErrorApi): CharacterString {
        val pointer = api.getErrorMessage(code) ?: return Chars.fromString("Error 0x${code.toString(16)}")
        val message = readWide(pointer)
        api.localFree(pointer) // Source ignores a failing LocalFree result.
        return message
    }
    fun toError(location: CharacterString, code: UInt, api: GhcWindowsErrorApi): GhcIoError {
        val message = errorMessage(code, api).points()
        var length = message.size
        while (length > 0 && message[length - 1] <= 65535 && HaskellNumber.isSpace(message[length - 1].toChar())) length--
        val (errno, type) = mapErrno(code)
        return GhcIoError(type, location, CharacterString(message.copyOf(length)), errno = errno)
    }
    fun throwLastError(location: String, api: GhcWindowsErrorApi): Nothing =
        throw GhcIOException(toError(Chars.fromString(location), api.getLastError(), api))

    /** Windows cWcharsToChars: merge valid pairs and retain unmatched surrogates. */
    fun readWide(pointer: Pointer): CharacterString {
        val points = ArrayList<Int>()
        var offset = 0L
        while (true) {
            val first = pointer.getShort(offset).toInt() and 65535
            if (first == 0) break
            offset += 2
            if (first in 0xd800..0xdbff) {
                val second = pointer.getShort(offset).toInt() and 65535
                if (second in 0xdc00..0xdfff) { points.add(0x10000 + (first - 0xd800) * 1024 + second - 0xdc00); offset += 2; continue }
            }
            points.add(first)
        }
        return CharacterString(points.toIntArray())
    }
    fun wideUnits(text: CharacterString): IntArray = buildList {
        for (point in text.points()) {
            if (point < 0x10000) add(point and 65535)
            else { val p = point - 0x10000; add((p / 1024 + 0xd800) and 65535); add((p % 1024 + 0xdc00) and 65535) }
        }
    }.toIntArray()
}
