package io.heapy.kotdoc.http

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.zip.GZIPInputStream

class HttpWindowsProxyTest {
    private class ScriptError(message: String) : RuntimeException(message)
    private fun bytes(v: JsonElement) = HttpBytes(v.jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray())
    private fun json(b: HttpBytes) = JsonArray(b.bytes().map { JsonPrimitive(it.toInt() and 255) })
    private fun text(v: JsonElement) = buildString {v.jsonArray.forEach {appendCodePoint(it.jsonPrimitive.int)}}
    private fun points(s: String) = JsonArray(s.codePoints().toArray().map(::JsonPrimitive))
    private fun proxy(v: JsonElement) = v.jsonObject.let {HttpProxy(bytes(it.getValue("host")),it.getValue("port").jsonPrimitive.long)}
    private fun request(v: JsonElement): HttpRequest = v.jsonObject.let {o ->
        HttpRequest(host=bytes(o.getValue("host")),proxy=o["proxy"]?.let(::proxy),
            secure=o["secure"]?.jsonPrimitive?.boolean ?: false,method=o["method"]?.let(::bytes) ?: HttpBytes.pack("GET"),
            requestHeaders=o["headers"]?.jsonArray?.map {it.jsonArray.let {HttpHeader(bytes(it[0]),bytes(it[1]))}} ?: emptyList())
    }
    private fun view(r: HttpRequest): JsonElement = buildJsonObject {
        put("proxy",r.proxy?.show()?.let(::JsonPrimitive) ?: JsonNull)
        put("headers",JsonArray(r.requestHeaders.map {JsonArray(listOf(json(it.name),json(it.value)))}))
        put("host",json(r.host));put("secure",r.secure);put("method",json(r.method));put("show",r.show())
    }
    private fun settings(s: HttpProxySettings?): JsonElement = s?.let {
        buildJsonObject {put("show",s.show());put("prec11",s.show(11)+"TAIL")}
    } ?: JsonNull
    private fun failure(e: Throwable): JsonElement = buildJsonObject {
        when(e) {
            is HttpContentException -> {
                put("kind","content");put("show",e.message);put("content",e.failure.show());put("prec11",e.failure.showsPrec(11)+"TAIL")
            }
            is IOException -> {put("kind","io");put("message",e.message)}
            is CancellationException -> put("kind","async")
            is ScriptError, is CookieBytesException -> {put("kind","error");put("message",e.message)}
            else -> throw e
        }
    }
    private fun observe(v: JsonObject): JsonElement {
        var config=v
        val events=mutableListOf<JsonElement>()
        fun event(vararg values: JsonElement) {events.add(JsonArray(values.toList()))}
        fun raise(key: String) {
            when(config["failures"]?.jsonObject?.get(key)?.jsonPrimitive?.content) {
                "io" -> throw IOException(key)
                "fatal" -> throw ScriptError(key)
                "async" -> throw CancellationException()
            }
        }
        val registry=HttpProxyRegistry {path ->
            event(JsonPrimitive("open"),JsonPrimitive(1),JsonPrimitive(path));raise("open")
            object : HttpProxyRegistryKey {
                override fun queryDword(name: String): UInt? {
                    event(JsonPrimitive("dword"),JsonPrimitive(7),JsonPrimitive(name),JsonPrimitive(4));raise("dword")
                    return if((config["valueType"]?.jsonPrimitive?.int ?: 4)==4) (config["enabled"]?.jsonPrimitive?.long ?: 1L).toUInt() else null
                }
                override fun queryValue(subkey: String?): String {
                    event(JsonPrimitive("string"),JsonPrimitive(7),subkey?.let(::JsonPrimitive) ?: JsonNull)
                    val key=if(subkey=="ProxyServer") "server" else "override"
                    raise(key)
                    return config[if(key=="server") "server" else "overrideValue"]?.let(::text) ?: throw IOException("missing value")
                }
                override fun close() {event(JsonPrimitive("close"),JsonPrimitive(7));raise("close")}
            }
        }
        fun environment(): List<Pair<String,String>> {
            event(JsonPrimitive("environment"));raise("environment")
            return config.getValue("env").jsonArray.map {it.jsonArray.let {text(it[0]) to text(it[1])}}
        }
        fun observed(action: ()->JsonElement): JsonElement = try {action()} catch(e: Throwable) {buildJsonObject {put("error",failure(e))}}
        fun construct(action: ()->((JsonElement)->JsonElement)): JsonElement {
            val f=try {action()} catch(e: Throwable) {return buildJsonObject {put("constructionError",failure(e))}}
            v["after"]?.let {config=it.jsonObject}
            return buildJsonObject {put("results",JsonArray(v.getValue("hosts").jsonArray.map {h -> observed {f(h)}}))}
        }
        val protocol=HttpProxyEnvironment.httpProtocol(v["secure"]?.jsonPrimitive?.boolean ?: false)
        val result=when(v.getValue("api").jsonPrimitive.content) {
            "parse" -> observed {settings(HttpWindowsProxy.parseProxy(protocol,text(v.getValue("text"))))}
            "select" -> observed {HttpWindowsProxy.parseWindowsProxy(protocol,text(v.getValue("text")))?.let(::points) ?: JsonNull}
            "registry" -> observed {HttpWindowsProxy.registryProxyString(registry)?.let {JsonArray(listOf(points(it.first),points(it.second)))} ?: JsonNull}
            "system" -> construct {val f=HttpWindowsProxy.systemProxy(protocol,registry);{h -> settings(f(bytes(h)))}}
            "helper" -> construct {
                val fallback=when(v["fallback"]?.jsonPrimitive?.content ?: "request") {
                    "request" -> HttpEnvHelper.FromRequest
                    "none" -> HttpEnvHelper.NoProxy
                    "use" -> HttpEnvHelper.UseProxy(proxy(v.getValue("fallbackProxy")))
                    else -> error(v)
                }
                val f=HttpProxyEnvironment.systemProxyHelper(v["override"]?.takeUnless {it is JsonNull}?.let(::text),protocol,fallback,::environment,true,registry)
                val project: (JsonElement)->JsonElement = {h -> view(f(request(h)))}
                project
            }
            else -> error(v)
        }
        return buildJsonObject {put("result",result);put("events",JsonArray(events))}
    }
    @TestFactory
    fun originalWindowsBranch(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/http-windows-proxy/data.jsonl.gz"))).bufferedReader().useLines {lines ->
        lines.drop(1).mapIndexed {i,line ->
            val row=Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest("$i ${row.getValue("group").jsonPrimitive.content}") {
                assertEquals(row.getValue("expected"),observe(row.getValue("request").jsonObject))
            }
        }.toList()
    }
    @Test
    fun `non-Windows platform skips registry and keeps original request and deferred body`() {
        var reads=0
        val body=HttpRequestBody.IO {error("body must stay deferred")}
        val req=HttpRequest(requestBody=body,proxy=HttpProxy(HttpBytes.pack("existing"),80))
        val helper=HttpProxyEnvironment.systemProxyHelper(null,HttpProxyProtocol.HTTPProxy,HttpEnvHelper.FromRequest,
            {reads++;emptyList()},false,HttpProxyRegistry {error("registry must not be opened")})
        assertEquals(1,reads);assertSame(req,helper(req));assertSame(req,helper(req));assertEquals(1,reads)
        assertSame(body,helper(req).requestBody)
    }
}
