package io.heapy.kotdoc.http

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.io.IOException
import java.util.UUID

/** Runs only on Windows: real Advapi32 calls and temporary HKCU keys.
 * Internet Settings and the user's real proxy configuration are never edited. */
class HttpWindowsRegistryNativeTest {
    private fun reg(vararg args: String) {
        val p=ProcessBuilder(listOf("reg.exe")+args).redirectErrorStream(true).start()
        val output=p.inputStream.readAllBytes()
        assertEquals(0,p.waitFor(),"reg.exe failed: ${output.toString(Charsets.UTF_8)}")
    }
    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun actualWindowsRegistry() {
            val path="Software\\kotdoc-test-${UUID.randomUUID()}"
            val commandPath="HKCU\\$path"
            try {
                reg("ADD",commandPath,"/v","ProxyEnable","/t","REG_DWORD","/d","1","/f")
                reg("ADD",commandPath,"/v","ProxyServer","/t","REG_SZ","/d","http=wrong-named-value","/f")
                val source=HttpProxyRegistry {requested ->
                    assertEquals(HttpWindowsProxy.registryPath,requested)
                    HttpNativeWindowsRegistry.openCurrentUser(path)
                }
                val key=source.openCurrentUser(HttpWindowsProxy.registryPath)
                try {
                    assertEquals(1u,key.queryDword("ProxyEnable"))
                    assertThrows(IOException::class.java) {key.queryValue("ProxyServer")}
                } finally {key.close()}
                assertNull(HttpWindowsProxy.registryProxyString(source))
                reg("ADD","$commandPath\\ProxyServer","/ve","/t","REG_SZ","/d","http=user:pass@native:3128;https=secure","/f")
                reg("ADD","$commandPath\\ProxyOverride","/ve","/t","REG_SZ","/d","<local>;example.com;Ж😀","/f")
                assertEquals("http=user:pass@native:3128;https=secure" to "<local>;example.com;Ж😀",HttpWindowsProxy.registryProxyString(source))
                val system=HttpWindowsProxy.systemProxy(HttpProxyProtocol.HTTPProxy,source)
                assertNull(system(HttpBytes.pack("localhost")));assertNull(system(HttpBytes.pack("example.com")))
                assertEquals(HttpProxy(HttpBytes.pack("native"),3128),system(HttpBytes.pack("elsewhere"))?.proxy)
                val helper=HttpProxyEnvironment.systemProxyHelper(null,HttpProxyProtocol.HTTPProxy,HttpEnvHelper.NoProxy,
                    {listOf("http_proxy" to "environment","no_proxy" to "example.com")},true,source)
                assertEquals(HttpProxy(HttpBytes.pack("environment"),80),helper(HttpRequest(host=HttpBytes.pack("other"))).proxy)
                assertNull(helper(HttpRequest(host=HttpBytes.pack("example.com"))).proxy)
            } finally {reg("DELETE",commandPath,"/f")}
    }
}
