package io.heapy.kotdoc.http

import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import io.heapy.kotdoc.uri.URI
import io.heapy.kotdoc.uri.URIAuth
import io.heapy.kotdoc.uri.NetworkURI
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.nio.file.Files
import java.io.IOException
import java.util.zip.GZIPInputStream

class HttpRequestTest {
    private class FixtureIoError(val detail: String) : IOException(detail)
    private class FixtureRuntimeError(val detail: String) : RuntimeException(detail)
    private fun exceptionShow(e: Exception) = when(e) {
        is FixtureIoError -> "user error (${e.detail})"
        is FixtureRuntimeError -> e.detail
        is WrongRequestBodyStreamSize -> "HttpExceptionContentWrapper {unHttpExceptionContentWrapper = ${e.message}}"
        else -> throw e
    }
    private fun raw(v: JsonElement) = CharacterString(v.jsonArray.map { it.jsonPrimitive.int }.toIntArray())
    private fun bytes(v: JsonElement) = HttpBytes(v.jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray())
    private fun points(s: String) = JsonArray(s.codePoints().toArray().map(::JsonPrimitive))
    private fun json(b: HttpBytes) = JsonArray(b.bytes().map { JsonPrimitive(it.toInt() and 255) })
    private fun headers(v: JsonElement) = v.jsonArray.map { val p=it.jsonArray;HttpHeader(bytes(p[0]),bytes(p[1])) }
    private fun uri(v: JsonElement): URI {
        val a=v.jsonArray
        return URI(raw(a[0]),if(a[1] is JsonNull)null else a[1].jsonArray.let { URIAuth(raw(it[0]),raw(it[1]),raw(it[2])) },raw(a[2]),raw(a[3]),raw(a[4]))
    }
    private fun patch(o: JsonObject,r: HttpRequest): HttpRequest = r.copy(
        method=o["method"]?.let(::bytes) ?: r.method,host=o["host"]?.let(::bytes) ?: r.host,
        port=o["port"]?.jsonPrimitive?.long ?: r.port,secure=o["secure"]?.jsonPrimitive?.boolean ?: r.secure,
        path=o["path"]?.let(::bytes) ?: r.path,queryString=o["query"]?.let(::bytes) ?: r.queryString,
        requestHeaders=o["headers"]?.let(::headers) ?: r.requestHeaders,
        rawBody=o["rawBody"]?.jsonPrimitive?.boolean ?: r.rawBody,redirectCount=o["redirectCount"]?.jsonPrimitive?.long ?: r.redirectCount,
        decompress=when(o["decompress"]?.jsonPrimitive?.int ?: 0) {1->HttpRequests::alwaysDecompress;2->{_->false};else->HttpRequests::browserDecompress},
        versionMajor=o["version"]?.jsonArray?.get(0)?.jsonPrimitive?.long ?: r.versionMajor,
        versionMinor=o["version"]?.jsonArray?.get(1)?.jsonPrimitive?.long ?: r.versionMinor,
        redactHeaders=o["redact"]?.jsonArray?.map(::bytes) ?: r.redactHeaders,
        responseTimeout=o["timeout"]?.jsonArray?.let { when(it[0].jsonPrimitive.content) {"none"->HttpResponseTimeout.None;"micro"->HttpResponseTimeout.Micro(it[1].jsonPrimitive.long);else->HttpResponseTimeout.Default} } ?: r.responseTimeout,
    )
    private fun op(r: HttpRequest,v: JsonElement): HttpRequest {
        val a=v.jsonArray
        return when(a[0].jsonPrimitive.content) {
            "basic"->HttpRequests.applyBasicAuth(bytes(a[1]),bytes(a[2]),r)
            "proxy-basic"->HttpRequests.applyBasicProxyAuth(bytes(a[1]),bytes(a[2]),r)
            "bearer"->HttpRequests.applyBearerAuth(bytes(a[1]),r)
            "proxy"->HttpRequests.addProxy(bytes(a[1]),a[2].jsonPrimitive.long,r)
            "proxy-direct"->HttpRequests.addProxySecureWithoutConnect(r)
            "check"->HttpRequests.setRequestCheckStatus(r)
            "ignore"->HttpRequests.setRequestIgnoreStatus(r)
            "uri"->HttpRequests.setUri(r,uri(a[1]))
            "relative"->HttpRequests.setUriRelative(r,uri(a[1]))
            "query"->HttpRequests.setQueryString(a[1].jsonArray.map { val p=it.jsonArray;bytes(p[0]) to if(p[1] is JsonNull)null else bytes(p[1]) },r)
            "form"->HttpRequests.urlEncodedBody(a[1].jsonArray.map { val p=it.jsonArray;bytes(p[0]) to bytes(p[1]) },r)
            "partial"->HttpRequests.setQueryStringPartialEscape(a[1].jsonArray.map { val p=it.jsonArray
                bytes(p[0]) to p[1].jsonArray.map { val e=it.jsonArray;if(e[0].jsonPrimitive.boolean)HttpEscapeItem.QE(bytes(e[1])) else HttpEscapeItem.QN(bytes(e[1])) }
            },r)
            else->error(a)
        }
    }
    private fun body(v: JsonElement): HttpRequestBody {
        val a=v.jsonArray
        fun provider(cs: JsonArray) = HttpBodyProvider { consume ->
            val iter=cs.iterator()
            consume {
                if(!iter.hasNext())HttpBytes.empty else {
                    val next=iter.next()
                    if(next is JsonObject) when(next.getValue("error").jsonPrimitive.content) {
                        "io"->throw FixtureIoError("popper")
                        else->throw FixtureRuntimeError("popper")
                    } else bytes(next)
                }
            }
        }
        return when(a[0].jsonPrimitive.content) {
            "bs"->HttpRequestBody.Strict(bytes(a[1]))
            "lbs"->HttpRequestBody.Lazy(a[1].jsonArray.map(::bytes))
            "builder"->HttpRequestBody.Builder(a[1].jsonPrimitive.long) { bytes(a[2]) }
            "stream"->HttpRequestBody.Stream(a[1].jsonPrimitive.long,provider(a[2].jsonArray))
            "chunked"->HttpRequestBody.Stream(null,provider(a[1].jsonArray))
            "io"->HttpRequestBody.IO { body(a[1]) }
            "io-error"->HttpRequestBody.IO { throw FixtureIoError("body") }
            else->error(a)
        }
    }
    private fun wire(options: JsonObject,r0: HttpRequest): JsonObject {
        val sent=mutableListOf<HttpBytes>()
        val trace=mutableListOf<String>();var writes=0
        val active=options["faults"]?.jsonPrimitive?.boolean ?: false
        val r=r0.copy(onRequestBodyException={ e->
            trace.add("hook: ${exceptionShow(e)}")
            when(options["hook"]?.jsonPrimitive?.content) {"swallow"->Unit;"throw"->throw e;else->r0.onRequestBodyException(e)}
        })
        val result=try {
            val continuation=HttpRequests.requestBuilder(r) { chunk->
                val n=writes++
                if(active)trace.add("write: ${chunk.size}")
                if(active && n==(options["failWrite"]?.jsonPrimitive?.int ?: -1))throw FixtureIoError("write")
                sent.add(chunk)
            }
            val before=sent.toList();sent.clear();continuation?.invoke()
            buildJsonObject {put("before",JsonArray(before.map(::json)));put("after",JsonArray(sent.map(::json)));put("continued",continuation!=null)}
        } catch(e: Exception) {
            buildJsonObject {put("error",points(exceptionShow(e)));put("sent",JsonArray(sent.map(::json)))}
        }
        return if(active)buildJsonObject {put("result",result);put("trace",JsonArray(trace.map(::JsonPrimitive)))} else result
    }
    private fun observe(v: JsonObject): JsonObject = try {
        if(v["mode"]?.jsonPrimitive?.content=="file") {
            val path=Files.createTempFile("kotdoc-http-request", ".bin")
            try {
                Files.write(path,bytes(v.getValue("data")).bytes())
                val events=mutableListOf<JsonElement>()
                val body=HttpRequests.observedStreamFile({ events.add(JsonArray(listOf(JsonPrimitive(it.fileSize),JsonPrimitive(it.readSoFar),JsonPrimitive(it.thisChunkSize)))) },path)
                v["afterData"]?.let {Files.write(path,bytes(it).bytes())}
                val count=v["times"]?.jsonPrimitive?.int ?: 1
                val ws=List(count){wire(v,HttpRequest(requestBody=body))}
                buildJsonObject {if(count==1)put("wire",ws.single()) else put("wires",JsonArray(ws));put("events",JsonArray(events))}
            } finally {Files.delete(path)}
        } else {
            var r=when(v["mode"]?.jsonPrimitive?.content) {
                "default"->HttpRequest()
                "throw"->HttpRequests.parseUrlThrow(raw(v.getValue("url")))
                "uri"->HttpRequests.requestFromURI(uri(v.getValue("uri")))
                else->HttpRequests.parseRequest(raw(v.getValue("url")))
            }
            v["patch"]?.let {r=patch(it.jsonObject,r)}
            for(o in v["ops"]?.jsonArray ?: emptyList())r=op(r,o)
            v["body"]?.let {r=r.copy(requestBody=body(it))}
            val w=wire(v,r)
            buildJsonObject {
                put("host",json(r.host));put("port",r.port);put("secure",r.secure);put("path",json(r.path));put("query",json(r.queryString));put("method",json(r.method))
                put("headers",JsonArray(r.requestHeaders.map {JsonArray(listOf(json(it.name),json(it.value)))}))
                put("proxy",r.proxy?.let {JsonArray(listOf(json(it.host),JsonPrimitive(it.port)))} ?: JsonNull)
                put("show",points(r.show()));put("uri",points(HttpRequests.getUri(r).toString()));put("check",r.checkStatus)
                put("gunzip",HttpRequests.needsGunzip(r,v["responseHeaders"]?.let(::headers) ?: emptyList()));put("wire",w)
            }
        }
    } catch(e: InvalidHttpUrl) {buildJsonObject {put("error",points(e.message!!))}}

    @TestFactory fun independentOriginalObservations(): List<DynamicTest> =
        GZIPInputStream(javaClass.getResourceAsStream("/http-request/data.jsonl.gz")!!).bufferedReader().use { reader ->
            reader.readLine()
            reader.lineSequence().mapIndexed { i,line ->
                val c=Json.parseToJsonElement(line).jsonObject
                DynamicTest.dynamicTest("$i ${c.getValue("group").jsonPrimitive.content}") {
                    assertEquals(c.getValue("expected"),observe(c.getValue("request").jsonObject))
                }
            }.toList()
        }

    // Network.HTTP.Client.RequestSpec and RequestBodySpec, all original assertions.
    @TestFactory fun originalSpecs(): List<DynamicTest> {
        fun b(s: String)=HttpBytes.pack(s)
        fun parse(s: String)=HttpRequests.parseUrlThrow(s)
        val tests=mutableListOf<Pair<String,()->Unit>>()
        fun test(name: String,f: ()->Unit) {tests.add(name to f)}
        for(url in listOf("http://example.com","httP://example.com","HttP://example.com","HttPs://example.com","ftp://example.com")) {
            test("scheme $url") {if(url.startsWith("ftp:"))assertThrows(InvalidHttpUrl::class.java){parse(url)} else parse(url)}
            test("scheme URI $url") {val uri=NetworkURI.parseURI(url)!!;if(url.startsWith("ftp:"))assertThrows(InvalidHttpUrl::class.java){HttpRequests.requestFromURI(uri)} else HttpRequests.requestFromURI(uri)}
        }
        test("auth passes validation") {parse("http://agent:topsecret@example.com")}
        test("auth adds header") {
            val r=parse("http://user:pass@example.com")
            assertEquals("example.com",Chars.build(HttpRequests.getUri(r).authority!!.regName))
            val value=r.requestHeaders.firstOrNull {it.named("Authorization")}?.value
            assertNotNull(value);assertEquals(b("Basic dXNlcjpwYXNz"),value)
        }
        for(url in listOf("http://example.com/","https://example.com/","https://example.com:80/"))test("getUri $url") {assertEquals(url,HttpRequests.getUri(HttpRequests.parseRequest(url)).toString())}
        test("Show redacts authorization") {assertFalse("secret" in HttpRequest(requestHeaders=listOf(HttpHeader("Authorization","secret"))).show())}
        val proxyAuth={HttpRequests.applyBasicProxyAuth(b("user"),b("pass"),parse("http://example.org")).requestHeaders.firstOrNull {it.named("Proxy-Authorization")}?.value}
        test("proxy auth header exists") {assertNotNull(proxyAuth())}
        test("proxy auth header value") {assertEquals(b("Basic dXNlcjpwYXNz"),proxyAuth())}
        fun credentials(url: String)=HttpRequests.extractBasicAuthInfo(NetworkURI.parseURI(url)!!)
        test("extract username") {assertEquals(b("agent"),credentials("http://agent:secret@example.com")!!.first)}
        test("extract password") {assertEquals(b("shakenNotStirred"),credentials("http://agent007:shakenNotStirred@example.com")!!.second)}
        test("decode username RFC3986") {assertEquals(b("/?#[]!$&'()*+,;="),credentials("http://%2F%3F%23%5B%5D%21%24%26%27%28%29%2A%2B%2C%3B%3D:therealpassword@example.com")!!.first)}
        test("decode password RFC3986") {assertEquals(b("?#[]!$&'()*+,;=/"),credentials("http://therealusername:%3F%23%5B%5D%21%24%26%27%28%29%2A%2B%2C%3B%3D%2F@example.com")!!.second)}
        test("no auth is empty") {assertNull(credentials("http://example.com")?.first);assertNull(credentials("http://example.com")?.second)}
        fun bodies(): List<HttpRequestBody> = listOf(HttpRequestBody.Strict(b("data")),HttpRequestBody.Lazy(listOf(b("data"))),HttpRequestBody.Builder(4){b("data")},
            HttpRequestBody.Stream(4,HttpBodyProvider { f->var used=false;f {if(used)HttpBytes.empty else {used=true;b("data")}} }),
            HttpRequestBody.Stream(null,HttpBodyProvider { f->var used=false;f {if(used)HttpBytes.empty else {used=true;b("data")}} }))
        val nonChunked="PUT /foo HTTP/1.1\r\nHost: localhost\r\nAccept-Encoding: gzip\r\nContent-Length: 4\r\n"
        val chunked="PUT /foo HTTP/1.1\r\nHost: localhost\r\nAccept-Encoding: gzip\r\nTransfer-Encoding: chunked\r\n"
        test("requestBuilder full request") {
            for((i,body) in bodies().withIndex()) {
                val sent=mutableListOf<String>()
                assertNull(HttpRequests.requestBuilder(parse("http://localhost").copy(method=b("PUT"),path=b("foo"),requestBody=body)){sent.add(it.latin1())})
                val expected=when(i) {3->listOf(nonChunked+"\r\n","","data");4->listOf(chunked+"\r\n","","4\r\ndata\r\n","0\r\n\r\n");else->listOf(nonChunked+"\r\ndata")}
                assertEquals(expected,sent)
            }
        }
        test("requestBuilder Expect continuation") {
            for((i,body) in bodies().withIndex()) {
                val sent=mutableListOf<String>()
                val later=HttpRequests.requestBuilder(parse("http://localhost").copy(method=b("PUT"),path=b("foo"),requestHeaders=listOf(HttpHeader("Expect","100-continue")),requestBody=body)){sent.add(it.latin1())}
                assertEquals(listOf((if(i==4)chunked else nonChunked)+"Expect: 100-continue\r\n\r\n",""),sent)
                sent.clear();later?.invoke()
                assertEquals(if(i==4)listOf("4\r\ndata\r\n","0\r\n\r\n") else listOf("data"),sent)
            }
        }
        test("streamFile works") {
            val file=Files.createTempFile("request-body-stream-file", ".bin")
            try {
                val data=b("Hello, world!\r\n".repeat(5000));Files.write(file,data.bytes())
                var first=true;var position=0
                HttpRequests.requestBuilder(parse("http://example.com").copy(requestBody=HttpRequests.streamFile(file))) { chunk->
                    if(first) {assertEquals("GET / HTTP/1.1\r\nHost: example.com\r\nAccept-Encoding: gzip\r\nContent-Length: 75000\r\n\r\n",chunk.latin1());first=false}
                    else {assertArrayEquals(data.bytes().copyOfRange(position,position+chunk.size),chunk.bytes());position+=chunk.size}
                }
                assertEquals(75000,position)
            } finally {Files.delete(file)}
        }
        assertEquals(26,tests.size)
        return tests.map {(name,action)->DynamicTest.dynamicTest(name,action)}
    }
}
