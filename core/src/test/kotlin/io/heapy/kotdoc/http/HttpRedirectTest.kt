package io.heapy.kotdoc.http

import io.heapy.kotdoc.resources.PandocUtcTime
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.util.zip.GZIPInputStream

class HttpRedirectTest {
    private class StripForced : RuntimeException("strip-forced")
    private fun bytes(v: JsonElement) = HttpBytes(v.jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray())
    private fun json(v: HttpBytes) = JsonArray(v.bytes().map { JsonPrimitive(it.toInt() and 255) })
    private fun headers(v: JsonElement) = v.jsonArray.map { it.jsonArray.let { HttpHeader(bytes(it[0]),bytes(it[1])) } }
    private fun jsonHeaders(hs: List<HttpHeader>) = JsonArray(hs.map { JsonArray(listOf(json(it.name),json(it.value))) })
    private fun bool(v: JsonObject,key: String,default: Boolean=false) = v[key]?.jsonPrimitive?.boolean ?: default
    private fun jar(v: JsonElement): HttpCookieJar {
        val t=PandocUtcTime(BigInteger.valueOf(60000),BigInteger.ZERO)
        return HttpCookies.createCookieJar(v.jsonArray.map { HttpCookie(bytes(it),HttpBytes.pack("value"),t,HttpBytes.pack("example.com"),HttpBytes.pack("/"),t,t,false,true,false,false) })
    }
    private fun body(v: JsonElement): HttpRequestBody = v.jsonObject.let {
        when(it.getValue("kind").jsonPrimitive.content) {
            "strict"->HttpRequestBody.Strict(bytes(it.getValue("bytes")))
            "lazy"->HttpRequestBody.Lazy(it.getValue("chunks").jsonArray.map(::bytes))
            "builder"->HttpRequestBody.Builder(it.getValue("length").jsonPrimitive.long) { error("body-forced") }
            "stream"->HttpRequestBody.Stream(it.getValue("length").jsonPrimitive.long) { error("body-forced") }
            "chunked"->HttpRequestBody.Stream(null) { error("body-forced") }
            "io"->HttpRequestBody.IO { error("body-forced") }
            else->error(it)
        }
    }
    private fun json(b: HttpRequestBody): JsonObject = buildJsonObject {
        when(b) {
            is HttpRequestBody.Strict->{put("kind","strict");put("bytes",json(b.data))}
            is HttpRequestBody.Lazy->{put("kind","lazy");put("chunks",JsonArray(b.chunks.filter {it.size>0}.map(::json)))}
            is HttpRequestBody.Builder->{put("kind","builder");put("length",b.length)}
            is HttpRequestBody.Stream->{put("kind",if(b.length==null) "chunked" else "stream");b.length?.let {put("length",it)}}
            is HttpRequestBody.IO->put("kind","io")
        }
    }
    private fun predicate(v: JsonElement): (HttpHeaderName)->Boolean {
        val p=v.jsonObject
        return when(p.getValue("kind").jsonPrimitive.content) {
            "none"->{_ ->false};"all"->{_ ->true};"fail"->{_ ->throw StripForced()}
            "names"->{val names=p.getValue("names").jsonArray.map {HttpHeaderName(bytes(it))};{name->name in names}}
            "original"->{val names=p.getValue("names").jsonArray.map(::bytes);{name->name.original in names}}
            else->error(p)
        }
    }
    private fun request(v: JsonElement): HttpRequest = v.jsonObject.let { p->
        val version=p["version"]?.jsonArray
        HttpRequest(host=p["host"]?.let(::bytes) ?: HttpBytes.pack("localhost"),port=p["port"]?.jsonPrimitive?.long ?: 80,
            path=p["path"]?.let(::bytes) ?: HttpBytes.pack("/"),queryString=p["query"]?.let(::bytes) ?: HttpBytes.empty,secure=bool(p,"secure"),
            method=p["method"]?.let(::bytes) ?: HttpBytes.pack("GET"),requestBody=p["body"]?.let(::body) ?: HttpRequestBody.Lazy(emptyList()),
            requestHeaders=p["headers"]?.let(::headers) ?: emptyList(),cookieJar=if("cookieJar" !in p) HttpCookieJar.empty else p["cookieJar"]?.takeUnless {it is JsonNull}?.let(::jar),
            shouldStripHeaderOnRedirect=p["strip"]?.let(::predicate) ?: {false},shouldStripHeaderOnRedirectIfOnDifferentHostOnly=bool(p,"onlyDifferent"),
            redirectCount=p["redirectCount"]?.jsonPrimitive?.long ?: 10,rawBody=bool(p,"rawBody"),versionMajor=version?.get(0)?.jsonPrimitive?.long ?: 1,versionMinor=version?.get(1)?.jsonPrimitive?.long ?: 1,
            proxy=p["proxy"]?.jsonObject?.let {HttpProxy(bytes(it.getValue("host")),it.getValue("port").jsonPrimitive.long)},
            proxySecureMode=if(bool(p,"proxyConnect",true)) ProxySecureMode.ProxySecureWithConnect else ProxySecureMode.ProxySecureWithoutConnect)
    }
    private fun json(r: HttpRequest): JsonObject = buildJsonObject {
        put("host",json(r.host));put("port",r.port);put("path",json(r.path));put("query",json(r.queryString));put("secure",r.secure);put("method",json(r.method))
        put("headers",jsonHeaders(r.requestHeaders));put("body",json(r.requestBody));put("cookieJar",r.cookieJar?.show()?.let(::JsonPrimitive) ?: JsonNull)
        put("onlyDifferent",r.shouldStripHeaderOnRedirectIfOnDifferentHostOnly);put("redirectCount",r.redirectCount);put("rawBody",r.rawBody)
        put("version",JsonArray(listOf(JsonPrimitive(r.versionMajor),JsonPrimitive(r.versionMinor))))
        put("proxy",r.proxy?.let {buildJsonObject {put("host",json(it.host));put("port",it.port)}} ?: JsonNull)
        put("proxyConnect",r.proxySecureMode==ProxySecureMode.ProxySecureWithConnect);put("show",r.show())
    }
    private fun observe(v: JsonObject): JsonElement = try {
        val original=request(v.getValue("original"));var current=request(v.getValue("request"))
        val results=mutableListOf<JsonElement>()
        for(s in v.getValue("steps").jsonArray) {
            val step=s.jsonObject
            val next=HttpResponses.getRedirectedRequest(original,current,headers(step.getValue("headers")),jar(step.getValue("jar")),step.getValue("status").jsonPrimitive.long)
            results.add(next?.let(::json) ?: JsonNull)
            current=next ?: break
        }
        JsonArray(results)
    } catch(e: StripForced) {buildJsonObject {put("error",e.message!!)}}

    @TestFactory
    fun originalObservations(): List<DynamicTest> = GZIPInputStream(javaClass.getResourceAsStream("/http-redirect/data.jsonl.gz")!!).bufferedReader().use {r->
        r.readLine();r.lineSequence().mapIndexed {index,line->val c=Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest("$index ${c.getValue("group").jsonPrimitive.content}") {assertEquals(c.getValue("expected"),observe(c.getValue("request").jsonObject))}
        }.toList()
    }
    @Test
    fun retainedPoliciesAndBodiesAreNotExecutedDuringRedirection() {
        var effects=0
        val bodies=listOf(HttpRequestBody.IO {effects++;error("IO")},HttpRequestBody.Builder(0) {effects++;error("builder")},HttpRequestBody.Stream(0) {effects++;error("stream")},HttpRequestBody.Stream(null) {effects++;error("chunked")})
        for(body in bodies) for(status in listOf(301L,302L,303L,307L,308L)) {
            val strip: (HttpHeaderName)->Boolean={false}
            val request=HttpRequest(requestBody=body,decompress={effects++;false},onRequestBodyException={effects++},earlyHintHeadersReceived={effects++},shouldStripHeaderOnRedirect=strip)
            val jar=HttpCookieJar(emptyList())
            val redirected=HttpResponses.getRedirectedRequest(request,request,listOf(HttpHeader("Location","/next")),jar,status)!!
            assertSame(strip,redirected.shouldStripHeaderOnRedirect);assertSame(request.decompress,redirected.decompress)
            assertSame(request.onRequestBodyException,redirected.onRequestBodyException);assertSame(request.earlyHintHeadersReceived,redirected.earlyHintHeadersReceived)
            assertSame(jar,redirected.cookieJar)
            if(status==302L || status==303L) assertEquals(HttpRequestBody.Strict(HttpBytes.empty),redirected.requestBody) else assertSame(body,redirected.requestBody)
        }
        assertEquals(0,effects)
    }
}
