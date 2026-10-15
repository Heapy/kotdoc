package io.heapy.kotdoc.http

import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.IOException

class HttpWindowsRegistryBindingTest {
    private class Api : HttpRegistryFunctions {
        val events=mutableListOf<String>()
        var errorAt=""
        var value="Ж😀é"
        var type=4
        override fun RegOpenKeyW(key: Pointer,subkey: WString,result: PointerByReference): Int {
            events.add("open:${Pointer.nativeValue(key)}:$subkey")
            result.value=Pointer.createConstant(123)
            return if(errorAt=="open") 5 else 0
        }
        override fun RegQueryValueExW(key: Pointer,name: WString,reserved: Pointer?,type: IntByReference,data: Pointer,size: IntByReference): Int {
            events.add("dword:${Pointer.nativeValue(key)}:$name:${size.value}")
            assertNull(reserved);assertEquals(4,size.value)
            type.value=this.type;data.setInt(0,-1)
            return if(errorAt=="dword") 234 else 0
        }
        override fun RegQueryValueW(key: Pointer,subkey: WString?,data: Pointer?,size: IntByReference): Int {
            val mode=if(data==null) "size" else "value"
            events.add("$mode:${Pointer.nativeValue(key)}:$subkey")
            if(data==null) size.value=(value.length+1)*2 else {
                assertEquals((value.length+1)*2,size.value)
                value.forEachIndexed {i,c -> data.setShort(i*2L,c.code.toShort())}
                data.setShort(value.length*2L,0)
            }
            return if(errorAt==mode) 2 else 0
        }
        override fun RegCloseKey(key: Pointer): Int {
            events.add("close:${Pointer.nativeValue(key)}")
            return if(errorAt=="close") 6 else 0
        }
    }
    @Test
    fun `native adapter preserves HKCU four byte unsigned DWORD and two call subkey string lookup`() {
        val api=Api();val key=HttpNativeWindowsRegistry.open(api,"test-path")
        assertEquals(UInt.MAX_VALUE,key.queryDword("ProxyEnable"))
        api.type=1;assertNull(key.queryDword("WrongType"))
        assertEquals("Ж😀é",key.queryValue("ProxyServer"))
        api.value="";assertEquals("",key.queryValue(null));key.close()
        assertEquals(listOf("open:-2147483647:test-path","dword:123:ProxyEnable:4","dword:123:WrongType:4",
            "size:123:ProxyServer","value:123:ProxyServer","size:123:null","value:123:null","close:123"),api.events)
    }
    @Test
    fun `native error codes become IO errors at the original call boundary`() {
        for(stage in listOf("open","dword","size","value","close")) {
            val api=Api();api.errorAt=stage
            val error=assertThrows(IOException::class.java) {
                val key=HttpNativeWindowsRegistry.open(api,"path")
                when(stage) {"dword" -> key.queryDword("x");"size","value" -> key.queryValue("x");else -> key.close()}
            }
            val operation=when(stage) {"open" -> "RegOpenKey";"dword" -> "RegQueryValueEx";"close" -> "RegCloseKey";else -> "RegQueryValue"}
            val code=when(stage) {"open" -> 5;"dword" -> 234;"close" -> 6;else -> 2}
            assertEquals("$operation: Windows error $code",error.message)
        }
    }
}
