// GHC 9.12.2 Foreign.C.Error.errnoToIOError and cbits/strerror.c.
// University of Glasgow; complete licenses/GHC-base-BSD-3-Clause.txt.
package io.heapy.kotdoc.errors

import com.sun.jna.Memory
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import io.heapy.kotdoc.resources.GhcForeignCString
import kotlinx.serialization.json.*

object GhcPosixErrno {
    private val libc by lazy {NativeLibrary.getInstance(Platform.C_LIBRARY_NAME)}
    private val patterns: List<Pair<Int,IoErrorType>> by lazy {
        val name=if(Platform.isMac()) "macos-64" else "linux-64"
        val stream=checkNotNull(javaClass.getResourceAsStream("/io/heapy/kotdoc/http/network-abi-$name.json"))
        stream.bufferedReader().use {Json.parseToJsonElement(it.readText()).jsonObject.getValue("errnoTypes").jsonArray.map {row->row.jsonArray.let {it[0].jsonPrimitive.int to IoErrorType.valueOf(it[1].jsonPrimitive.content)}}}
    }
    fun toError(location: CharacterString,code: Int,filename: CharacterString?=null,handle: ErrorHandle?=null,
        encoding: ()->GhcForeignCString=GhcForeignCString::currentPosix,
    ): GhcIoError {
        val description=Memory(512).use {p->
            val result=libc.getFunction(if(Platform.isLinux()) "__xpg_strerror_r" else "strerror_r").invokeInt(arrayOf(code,p,com.sun.jna.NativeLong(512)))
            if(result==34) {p.setByte(508,46);p.setByte(509,46);p.setByte(510,46)}
            if(result!=0 && result!=34) Chars.fromString("errnoToString failed") else CharacterString(encoding().decode(p))
        }
        val type=patterns.firstOrNull {it.first==code}?.second ?: IoErrorType.OtherError
        return GhcIoError(type,location,description,filename,code,handle)
    }
    fun exception(location: String,code: Int)=GhcIOException(toError(Chars.fromString(location),code))
}
