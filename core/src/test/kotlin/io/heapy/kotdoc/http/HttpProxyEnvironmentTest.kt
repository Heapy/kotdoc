package io.heapy.kotdoc.http

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream

class HttpProxyEnvironmentTest {
    private fun bytes(v: JsonElement) = HttpBytes(v.jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray())
    private fun json(b: HttpBytes) = JsonArray(b.bytes().map { JsonPrimitive(it.toInt() and 255) })
    private fun text(v: JsonElement) = buildString { v.jsonArray.forEach { appendCodePoint(it.jsonPrimitive.int) } }
    private fun env(v: JsonElement) = v.jsonArray.map { it.jsonArray.let { text(it[0]) to text(it[1]) } }
    private fun proxy(v: JsonElement) = v.jsonObject.let { HttpProxy(bytes(it.getValue("host")),it.getValue("port").jsonPrimitive.long) }
    private fun request(v: JsonElement): HttpRequest = v.jsonObject.let { o ->
        HttpRequest(host=bytes(o.getValue("host")), proxy=o["proxy"]?.let(::proxy),
            secure=o["secure"]?.jsonPrimitive?.boolean ?: false,
            method=o["method"]?.let(::bytes) ?: HttpBytes.pack("GET"),
            requestHeaders=o["headers"]?.jsonArray?.map { it.jsonArray.let { HttpHeader(bytes(it[0]),bytes(it[1])) } } ?: emptyList())
    }
    private fun view(r: HttpRequest): JsonElement = buildJsonObject {
        put("proxy",r.proxy?.show()?.let(::JsonPrimitive) ?: JsonNull)
        put("headers",JsonArray(r.requestHeaders.map { JsonArray(listOf(json(it.name),json(it.value))) }))
        put("host",json(r.host));put("secure",r.secure);put("method",json(r.method));put("show",r.show())
    }
    private fun failure(e: Throwable): JsonElement = buildJsonObject {
        when(e) {
            is HttpContentException -> {
                put("kind","content");put("show",e.message);put("content",e.failure.show())
                put("prec11",e.failure.showsPrec(11)+"TAIL")
            }
            is CookieBytesException -> { put("kind","error");put("message",e.message) }
            else -> throw e
        }
    }
    private fun observe(v: JsonObject): JsonElement {
        var environment=env(v.getValue("env"))
        val after=v["afterEnv"]?.let(::env)
        val results: List<JsonElement>
        if(v.getValue("api").jsonPrimitive.content=="env") {
            val modifier=try { HttpProxyEnvironment.envHelper(text(v.getValue("name"))) {environment} }
                catch(e: HttpContentException) { return buildJsonObject {put("constructionError",failure(e))} }
            if(after!=null) environment=after
            results=v.getValue("hosts").jsonArray.map { h ->
                try {
                    modifier(bytes(h))?.let { buildJsonObject { put("show",it.show());put("prec11",it.show(11)+"TAIL") } } ?: JsonNull
                } catch(e: CookieBytesException) { buildJsonObject {put("error",failure(e))} }
            }
        } else {
            val fallback=when(v.getValue("fallback").jsonPrimitive.content) {
                "request" -> HttpEnvHelper.FromRequest
                "none" -> HttpEnvHelper.NoProxy
                "use" -> HttpEnvHelper.UseProxy(proxy(v.getValue("fallbackProxy")))
                else -> error(v)
            }
            val modifier=try {
                HttpProxyEnvironment.environmentProxyHelper(v["override"]?.takeUnless {it is JsonNull}?.let(::text),
                    HttpProxyEnvironment.httpProtocol(v["secure"]?.jsonPrimitive?.boolean ?: false),fallback) {environment}
            } catch(e: HttpContentException) { return buildJsonObject {put("constructionError",failure(e))} }
            if(after!=null) environment=after
            results=v.getValue("hosts").jsonArray.map { h ->
                try {view(modifier(request(h)))} catch(e: CookieBytesException) {buildJsonObject {put("error",failure(e))}}
            }
        }
        return buildJsonObject {put("results",JsonArray(results))}
    }
    @TestFactory
    fun originalProxyObservations(): List<DynamicTest> {
        val input=checkNotNull(javaClass.getResourceAsStream("/http-proxy/data.jsonl.gz"))
        return GZIPInputStream(input).bufferedReader().useLines { lines ->
            lines.drop(1).mapIndexed { i,line ->
                val row=Json.parseToJsonElement(line).jsonObject
                DynamicTest.dynamicTest("$i ${row.getValue("group").jsonPrimitive.content}") {
                    assertEquals(row.getValue("expected"),observe(row.getValue("request").jsonObject))
                }
            }.toList()
        }
    }
    @Test
    fun `fallback preserves callback body and header objects and captures environment once`() {
        var calls=0
        val body=HttpRequestBody.IO { error("must remain deferred") }
        val strip: (HttpHeaderName)->Boolean = { error("must remain deferred") }
        val req=HttpRequest(requestBody=body,shouldStripHeaderOnRedirect=strip,requestHeaders=listOf(HttpHeader("Proxy-Authorization","existing")))
        val modify=HttpProxyEnvironment.environmentProxyHelper(null,HttpProxyProtocol.HTTPProxy,HttpEnvHelper.FromRequest) {calls++; emptyList()}
        assertEquals(1,calls); assertSame(req,modify(req)); assertSame(req,modify(req)); assertEquals(1,calls)
        val clear=HttpProxyEnvironment.environmentProxyHelper(null,HttpProxyProtocol.HTTPProxy,HttpEnvHelper.NoProxy) {emptyList()}
        val changed=clear(req.copy(proxy=HttpProxy(HttpBytes.pack("old"),80)))
        assertNull(changed.proxy);assertSame(body,changed.requestBody);assertSame(strip,changed.shouldStripHeaderOnRedirect);assertSame(req.requestHeaders,changed.requestHeaders)
    }
    @Test
    fun `environment boundary exception propagates as the same object`() {
        val cause=java.io.IOException("environment failed")
        assertSame(cause,assertThrows(java.io.IOException::class.java) {HttpProxyEnvironment.envHelper("http_proxy") {throw cause}})
    }
}
