package io.heapy.kotdoc.http

import io.heapy.kotdoc.resources.PandocUtcTime
import io.heapy.kotdoc.errors.*
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.util.zip.GZIPInputStream

class HttpExecutionTest {
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
    private class ScriptFailure(message: String): RuntimeException(message)
    private class Async: java.util.concurrent.CancellationException("thread killed")
    private fun raise(tag: String): Nothing = when(tag) {
        "closed"->throw HttpContentException(HttpFailureContent.ConnectionClosed)
        "nodata"->throw HttpContentException(HttpFailureContent.NoResponseDataReceived)
        "incomplete"->throw HttpContentException(HttpFailureContent.IncompleteHeaders)
        "response-timeout"->throw HttpContentException(HttpFailureContent.ResponseTimeout)
        "request-closed"->throw HttpRequestException(HttpRequest(),HttpFailureContent.ConnectionClosed)
        "vanished"->throw HttpGhcIOException(GhcIoError(IoErrorType.ResourceVanished,Chars.fromString("script-location")))
        "io"->throw HttpGhcIOException(GhcIoError(IoErrorType.UserError,Chars.fromString("script-location"),Chars.fromString("user-io")))
        "async"->throw Async()
        else->throw ScriptFailure(tag)
    }
    private fun timeout(v: JsonElement?): HttpResponseTimeout = when(v?.jsonPrimitive?.content) {
        null,"none"->HttpResponseTimeout.None
        "default"->HttpResponseTimeout.Default
        else->HttpResponseTimeout.Micro(v.jsonPrimitive.long)
    }
    private fun <A> meta(r: HttpResponse<A>) = buildJsonObject {
        put("status",r.status.code);put("message",json(r.status.message));put("headers",jsonHeaders(r.headers))
        put("jar",r.cookieJar.show());put("original",json(r.originalRequest))
    }
    private fun failure(e: Throwable): JsonObject = buildJsonObject {
        when(e) {
            is HttpRequestException -> {
                val content=e.failure
                put("kind",if(content is HttpFailureContent.StatusCodeException) "request-status" else "request")
                put("request",json(e.request));put("show",e.message)
                if(content is HttpFailureContent.StatusCodeException) {put("response",meta(content.response));put("body",json(content.body))}
                else put("content",content.show())
            }
            is HttpContentException->{put("kind","content");put("content",e.failure.show());put("show",e.message)}
            is GhcIOException->{put("kind","io");put("vanished",e.error.type==IoErrorType.ResourceVanished)
                put("message",Chars.build(e.error.description).ifEmpty {e.error.type.shown});put("location",Chars.build(e.error.location))}
            is Async->put("kind","async")
            is ScriptFailure->{put("kind","script");put("message",e.message)}
            else->throw e
        }
    }
    private fun run(v: JsonObject): JsonObject {
        val events=mutableListOf<JsonElement>();val scripts=v.getValue("connections").jsonArray;var connectionIndex=0
        fun event(name: String,content: JsonObjectBuilder.()->Unit={}) {events+=buildJsonObject {put("event",name);content()}}
        fun hookFailure(s: JsonObject,key: String) {s[key]?.jsonPrimitive?.content?.let(::raise)}
        val times=ArrayDeque(v["times"]?.jsonArray?.map {it.jsonPrimitive.content.toBigInteger()} ?: emptyList())
        val runtime=object: HttpExecutionRuntime {
            override fun now(): PandocUtcTime {
                val p=times.removeFirstOrNull() ?: BigInteger("1700000000000000000000")
                event("clock") {put("picoseconds",JsonPrimitive(p))}
                return PandocUtcTime.fromPosixPicoseconds(p)
            }
            override fun acquire(microseconds: Long?,action: ()->KeyedManaged<HttpConnection>): KeyedManaged<HttpConnection>? {
                if(microseconds!=null) event("acquire-timeout") {put("microseconds",microseconds)}
                return if(microseconds!=null && bool(v,"acquireExpires")) null else action()
            }
            override val responseTimeout=object: HttpTimeoutBoundary {
                override fun <A> run(microseconds: Long,action: ()->A): A {
                    event("status-timeout") {put("microseconds",microseconds)}
                    return action()
                }
            }
        }
        val connector=HttpConnector {_,host,port->
            val i=connectionIndex++
            event("connect") {put("index",i);put("host",host);put("port",port)}
            if(i>=scripts.size) raise("connections-exhausted")
            val s=scripts[i].jsonObject;hookFailure(s,"connectFailure");var chunk=0;var write=0
            HttpConnections.makeConnection({
                val n=chunk++;event("read") {put("index",i);put("chunk",n)}
                when(val x=s["chunks"]?.jsonArray?.getOrNull(n)) {
                    null->HttpBytes.empty
                    is JsonPrimitive->raise(x.content)
                    else->bytes(x)
                }
            }, {data->event("write") {put("index",i);put("bytes",json(data))}
                val n=write++
                if(s["writeFailAt"]==null || s["writeFailAt"]?.jsonPrimitive?.int==n) hookFailure(s,"writeFailure") },
                {event("close") {put("index",i)};hookFailure(s,"closeFailure")})
        }
        val poolRuntime=object: KeyedPoolRuntime {
            override fun now()=PandocUtcTime.fromPosixPicoseconds(BigInteger.ZERO)
            override fun registerCleanup(owner: Any,action: ()->Unit) {}
            override fun startReaper(action: ()->Unit) {}
            override fun delayReaper()=error("not started")
        }
        fun manager(i: Int,alternate: HttpManager?): HttpManager {
            val s=v["managers"]?.jsonArray?.getOrNull(i)?.jsonObject ?: buildJsonObject {}
            return HttpManagers.newManager(HttpManagerSettings(rawConnection={connector},tlsConnection={connector},
                proxyInsecure=HttpProxyOverrides.fromRequest,proxySecure=HttpProxyOverrides.fromRequest,
                responseTimeout=if("timeout" in s) timeout(s["timeout"]) else HttpResponseTimeout.Default,
                modifyRequest={r->
                    event("modify-request") {put("manager",i);put("request",json(r))};hookFailure(s,"modifyFailure")
                    r.copy(path=r.path+(s["suffix"]?.let(::bytes) ?: HttpBytes.empty),
                        requestHeaders=r.requestHeaders+(s["appendHeaders"]?.let(::headers) ?: emptyList()),
                        redirectCount=s["redirectCount"]?.jsonPrimitive?.long ?: r.redirectCount,
                        requestManagerOverride=if(bool(s,"override")) alternate else r.requestManagerOverride)
                },modifyResponse={r->event("modify-response") {put("manager",i)};hookFailure(s,"responseFailure");r},
                wrapException=object: HttpManagerExceptionWrapper {
                    override fun <A> wrap(request: HttpRequest,action: ()->A): A {
                        event("wrap") {put("manager",i);put("request",json(request))}
                        return HttpDefaultManagerExceptionWrapper.wrap(request,action)
                    }
                }),{},poolRuntime)
        }
        val m1=manager(1,null);val m0=manager(0,m1);val p=v.getValue("initial").jsonObject
        val initial=request(p).copy(responseTimeout=timeout(p["timeout"]),requestManagerOverride=if(bool(p,"override")) m1 else null,
            checkResponse={req,res->
                event("check") {put("request",json(req));put("status",res.status.code)}
                hookFailure(p,"checkFailure")
                if(bool(p,"checkStatus")) HttpRequests.throwErrorStatusCodes(req,res)
            })
        val execution=HttpExecution(runtime)
        fun consume(res: HttpResponse<HttpBodyReader>): JsonObject {
            val body=if(bool(v,"consume")) HttpBodies.consume(res.body) else null
            if(bool(v,"close")) res.close()
            hookFailure(v,"actionFailure")
            return buildJsonObject {put("kind","success");put("response",meta(res));put("body",body?.let {JsonArray(it.map(::json))} ?: JsonNull)}
        }
        fun consumeHistory(hr: HttpHistoriedResponse<HttpBodyReader>): JsonObject {
            val result=consume(hr.finalResponse)
            return buildJsonObject {
                val res=hr.finalResponse
                val shown=HttpHistoriedResponse(hr.redirects,hr.finalRequest,HttpResponse(res.status,res.version,
                    res.headers,Unit,res.close,res.originalRequest,res.early,res.cookieJar))
                put("showUnit",shown.show({"()"}));put("showUnitPrec11",shown.show({"()"},11)+"TAIL")
                put("result",result);put("finalRequest",json(hr.finalRequest))
                put("redirects",JsonArray(hr.redirects.map {(req,res)->buildJsonObject {
                    put("request",json(req));put("response",meta(res));put("body",JsonArray(res.body.map(::json)))
                }}))
            }
        }
        val result=try {
            if(bool(v,"prime")) HttpBodies.consume(execution.httpRaw(initial,m0).body)
            when(v.getValue("api").jsonPrimitive.content) {
                "raw"->consume(execution.httpRaw(initial,m0))
                "raw-request"->{val (req,res)=execution.httpRawWithRequest(initial,m0);val result=consume(res)
                    buildJsonObject {put("request",json(req));put("result",result)}}
                "open"->consume(execution.responseOpen(initial,m0))
                "with"->execution.withResponse(initial,m0,::consume)
                "history"->consumeHistory(execution.responseOpenHistory(initial,m0))
                "with-history"->execution.withResponseHistory(initial,m0,::consumeHistory)
                "lbs"->{val res=execution.httpLbs(initial,m0);buildJsonObject {put("kind","success");put("response",meta(res));put("body",JsonArray(res.body.map(::json)))}}
                "no-body"->{val res=execution.httpNoBody(initial,m0);buildJsonObject {put("kind","success");put("response",meta(res))}}
                "connection"->execution.withConnection(initial,m0) {conn->conn.write(HttpBytes.pack("interactive"));hookFailure(v,"actionFailure");buildJsonObject {put("kind","success")}}
                else->error(v)
            }
        } catch(e: Throwable) {failure(e)}
        return buildJsonObject {put("result",result);put("events",JsonArray(events.toList()))}
    }
    @TestFactory fun originalExecutionObservations(): List<DynamicTest> {
        val lines=GZIPInputStream(javaClass.getResourceAsStream("/http-execution/data.jsonl.gz")!!).bufferedReader().use {it.readLines()}
        val metadata=Json.parseToJsonElement(lines.first()).jsonObject
        assertEquals(metadata.getValue("cases").jsonPrimitive.int,lines.size-1)
        return lines.drop(1).mapIndexed {i,line->
            val c=Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest("${i+1}: ${c.getValue("group").jsonPrimitive.content}") {
                assertEquals(c.getValue("expected"),run(c.getValue("request").jsonObject),c.getValue("request").toString())
            }
        }
    }
}
