package io.heapy.kotdoc.http

import io.heapy.kotdoc.resources.PandocUtcTime
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.util.zip.GZIPInputStream

class HttpLoopTest {
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
    private class ScriptFailure(message: String) : RuntimeException(message)
    private class ScriptIo : java.io.IOException("user-io")
    private fun raise(tag: String): Nothing = when(tag) {
        "content-closed"->throw HttpContentException(HttpFailureContent.ConnectionClosed)
        "request-closed"->throw HttpRequestException(HttpRequest(),HttpFailureContent.ConnectionClosed)
        "content-timeout"->throw HttpContentException(HttpFailureContent.ResponseTimeout)
        "request-timeout"->throw HttpRequestException(HttpRequest(),HttpFailureContent.ResponseTimeout)
        "content-too-many"->throw HttpContentException(HttpFailureContent.TooManyRedirects(emptyList()))
        "request-too-many"->throw HttpRequestException(HttpRequest(),HttpFailureContent.TooManyRedirects(emptyList()))
        "request-invalid-status"->throw HttpRequestException(HttpRequest(),HttpFailureContent.InvalidStatusLine(HttpBytes.pack("bad")))
        "vanished"->throw HttpResourceVanishedException("resource vanished")
        "io"->throw ScriptIo()
        "async"->throw java.util.concurrent.CancellationException("thread killed")
        else->throw ScriptFailure(tag)
    }
    private fun chunk(v: JsonElement) = if(v is JsonArray) bytes(v) else v.jsonObject.let {HttpBytes(ByteArray(maxOf(0,it.getValue("size").jsonPrimitive.int)) {_->it.getValue("byte").jsonPrimitive.int.toByte()})}
    private fun responseJar(s: JsonObject): HttpCookieJar {
        if(!bool(s,"latentCookie")) return s["jar"]?.let(::jar) ?: HttpCookieJar.empty
        val t=PandocUtcTime(BigInteger.valueOf(60000),BigInteger.ZERO)
        return HttpCookieJar(listOf(HttpCookie(HttpBytes.pack("latent"),HttpBytes.pack("value"),lazy {throw ScriptFailure("latent-expiry")},HttpBytes.pack("example.com"),HttpBytes.pack("/"),t,t,false,true,false,false)))
    }
    private fun <A> meta(r: HttpResponse<A>): JsonObject = buildJsonObject {
        put("status",JsonArray(listOf(JsonPrimitive(r.status.code),json(r.status.message))))
        put("version",JsonArray(listOf(JsonPrimitive(r.version.major),JsonPrimitive(r.version.minor))))
        put("headers",jsonHeaders(r.headers));put("jarNames",JsonArray(r.cookieJar.cookies.map {json(it.name)}))
        put("original",json(r.originalRequest));put("early",jsonHeaders(r.early))
    }
    private fun json(r: HttpResponse<List<HttpBytes>>,withShow: Boolean): JsonObject = buildJsonObject {
        meta(r).forEach { (k,v)->put(k,v) };put("body",JsonArray(r.body.map(::json)))
        if(withShow) {put("show",HttpResponses.showResponse(r,HttpBodies::showLazy));put("showPrec11",HttpResponses.showResponse(r,HttpBodies::showLazy,11)+"TAIL")}
    }
    private fun failure(e: Throwable,withShow: Boolean): JsonObject = buildJsonObject {
        when(e) {
            is HttpContentException->when(val f=e.failure) {
                is HttpFailureContent.TooManyRedirects->{put("kind","too-many");put("history",JsonArray(f.responses.map {json(it,withShow)}));if(withShow)put("show",e.message)}
                else->{put("kind","content");put("content",f.show());put("show",e.message)}
            }
            is HttpRequestException->{put("kind","request");put("request",json(e.request));put("content",e.failure.show());put("show",e.message)}
            is HttpResourceVanishedException->{put("kind","io");put("vanished",true);put("message",e.message);put("location","script-location")}
            is ScriptIo->{put("kind","io");put("vanished",false);put("message",e.message);put("location","script-location")}
            is ScriptFailure->{put("kind","script");put("message",e.message)}
            is java.util.concurrent.CancellationException->{put("kind","async");put("message",e.message)}
            else->throw e
        }
    }
    private fun observe(v: JsonObject): JsonElement {
        val logs=mutableListOf<JsonElement>();var index=0
        val scripts=v.getValue("responses").jsonArray;val initial=request(v.getValue("initial"));val withShow=bool(v,"withShow",true)
        fun callback(current: HttpRequest): HttpRedirectStep {
            val i=index++
            logs.add(buildJsonObject {put("event","callback");put("index",i);put("request",json(current))})
            if(v["callbackFailAt"]?.jsonPrimitive?.int==i) raise(v.getValue("callbackFailure").jsonPrimitive.content)
            if(i>=scripts.size) raise("callback-exhausted")
            val s=scripts[i].jsonObject;var reads=0;var closed=false
            val actions=s["chunks"]?.jsonArray ?: JsonArray(emptyList())
            val reader=HttpBodyReader {
                val n=reads++
                logs.add(buildJsonObject {put("event","read");put("index",i);put("chunk",n)})
                if(closed) raise(s["onClosed"]?.jsonPrimitive?.content ?: "content-closed")
                when(val action=actions.getOrNull(n)) {
                    null->HttpBytes.empty
                    is JsonPrimitive->raise(action.content)
                    else->chunk(action)
                }
            }
            val close: ()->Unit={
                logs.add(buildJsonObject {put("event","close");put("index",i)})
                closed=true
                val closeFailure=s["closeFailure"]?.jsonPrimitive?.content
                if(closeFailure!=null) raise(closeFailure)
            }
            val original=s["original"]?.let(::request) ?: current
            val version=s["version"]?.jsonArray
            val response=HttpResponse(HttpStatus(s["status"]?.jsonPrimitive?.long ?: (300L+i),s["message"]?.let(::bytes) ?: HttpBytes.pack("message")),
                HttpVersion(version?.get(0)?.jsonPrimitive?.long ?: 1,version?.get(1)?.jsonPrimitive?.long ?: 1),s["headers"]?.let(::headers) ?: emptyList(),reader,close,
                original.copy(requestBody=HttpRequestBody.Strict(HttpBytes.empty)),s["early"]?.let(::headers) ?: emptyList(),responseJar(s))
            if(bool(s,"preclose")) close()
            return HttpRedirectStep(response,s["next"]?.let(::request) ?: current,bool(s,"redirect"))
        }
        return try {
            val result=try {
                val (r,res)=if(v.getValue("api").jsonPrimitive.content=="simple") {
                    null to HttpCore.httpRedirect(v.getValue("count").jsonPrimitive.long,{current->val step=callback(current);step.response to step.request.takeIf {step.isRedirect}},initial)
                } else HttpCore.httpRedirectWithRequest(v.getValue("count").jsonPrimitive.long,::callback,initial)
                val consumed=if(bool(v,"consumeFinal")) JsonArray(HttpBodies.consume(res.body).map(::json)) else JsonNull
                if(bool(v,"closeFinal")) res.close()
                buildJsonObject {put("kind","success");put("request",r?.let(::json) ?: JsonNull);put("response",meta(res));put("consumed",consumed)}
            } catch(e: Throwable) {failure(e,withShow)}
            buildJsonObject {put("result",result);put("events",JsonArray(logs))}
        } catch(e: ScriptFailure) {buildJsonObject {put("projectionError",e.message)}}
    }
    @TestFactory
    fun originalObservations(): List<DynamicTest> = GZIPInputStream(javaClass.getResourceAsStream("/http-loop/data.jsonl.gz")!!).bufferedReader().use {r->
        r.readLine();r.lineSequence().mapIndexed {index,line->val c=Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest("$index ${c.getValue("group").jsonPrimitive.content}") {assertEquals(c.getValue("expected"),observe(c.getValue("request").jsonObject))}
        }.toList()
    }
    @Test
    fun finalResponseRequestAndReaderRemainOwnedByCaller() {
        val request=HttpRequest();val next=request.copy(path=HttpBytes.pack("/modified"));var reads=0;var closes=0
        val reader=HttpBodyReader {reads++;HttpBytes.empty}
        val response=HttpResponse(HttpStatus(200,HttpBytes.pack("OK")),HttpVersion(1,1),emptyList(),reader,{closes++},request,emptyList())
        val (actualRequest,actualResponse)=HttpCore.httpRedirectWithRequest(0,{HttpRedirectStep(response,next,false)},request)
        assertSame(next,actualRequest);assertSame(response,actualResponse);assertSame(reader,actualResponse.body)
        assertSame(response,HttpCore.httpRedirect(0,{response to null},request));assertEquals(0,reads);assertEquals(0,closes)
    }
    @Test
    fun unrelatedErrorsEscapeUnchangedAndDoNotCloseResponse() {
        val request=HttpRequest();var closes=0
        val errors=listOf(java.util.concurrent.CancellationException("cancel"),java.io.IOException("closed"),ScriptFailure("failure"),HttpContentException(HttpFailureContent.ResponseTimeout),HttpRequestException(request,HttpFailureContent.ResponseTimeout))
        for(error in errors) {
            val response=HttpResponse(HttpStatus(301,HttpBytes.empty),HttpVersion(1,1),emptyList(),HttpBodyReader {throw error},{closes++},request,emptyList())
            val actual=assertThrows(Throwable::class.java) {HttpCore.httpRedirectWithRequest(0,{HttpRedirectStep(response,request,true)},request)}
            assertSame(error,actual)
        }
        assertEquals(0,closes)
    }
    @Test
    fun historySnapshotPreservesResponseObjectsWithoutForcingCookieExpiry() {
        val request=HttpRequest();val cookie=responseJar(buildJsonObject {put("latentCookie",true)})
        val response=HttpResponse(HttpStatus(301,HttpBytes.empty),HttpVersion(1,1),listOf(HttpHeader("X","v")),HttpBodies.const(listOf(HttpBytes.pack("body"))),{},request,listOf(HttpHeader("Link","hint")),cookie)
        val error=assertThrows(HttpContentException::class.java) {HttpCore.httpRedirectWithRequest(0,{HttpRedirectStep(response,request,true)},request)}
        val history=(error.failure as HttpFailureContent.TooManyRedirects).responses
        val saved=history.single();assertSame(response.close,saved.close);assertSame(response.originalRequest,saved.originalRequest)
        assertSame(response.headers,saved.headers);assertSame(response.early,saved.early);assertSame(cookie,saved.cookieJar)
        assertEquals(listOf(HttpBytes.pack("body")),saved.body)
        assertThrows(UnsupportedOperationException::class.java) {(history as MutableList).clear()}
        assertThrows(ScriptFailure::class.java) {error.message}
    }
}
