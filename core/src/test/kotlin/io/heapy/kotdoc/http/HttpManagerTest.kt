package io.heapy.kotdoc.http

import io.heapy.kotdoc.errors.GhcIoError
import io.heapy.kotdoc.errors.IoErrorType
import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import io.heapy.kotdoc.resources.PandocUtcTime
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.util.concurrent.CancellationException
import java.util.zip.GZIPInputStream

class HttpManagerTest {
    private fun bytes(v: JsonElement) = HttpBytes(v.jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray())
    private fun json(b: HttpBytes) = JsonArray(b.bytes().map { JsonPrimitive(it.toInt() and 255) })
    private fun text(v: JsonObject,k: String,default: String="") = v[k]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content ?: default
    private fun bool(v: JsonObject,k: String,default: Boolean=false) = v[k]?.jsonPrimitive?.booleanOrNull ?: default
    private fun number(v: JsonObject,k: String,default: Long) = v[k]?.jsonPrimitive?.longOrNull ?: default
    private fun present(v: JsonObject,k: String) = v[k]?.takeUnless { it is JsonNull }
    private fun proxy(v: JsonElement) = v.jsonObject.let { HttpProxy(bytes(it.getValue("host")),number(it,"port",80)) }
    private fun request(v: JsonElement): HttpRequest = v.jsonObject.let { o -> HttpRequest(
        host=present(o,"host")?.let(::bytes) ?: HttpBytes.pack("localhost"),port=number(o,"port",80),secure=bool(o,"secure"),
        hostAddress=present(o,"address")?.jsonPrimitive?.long?.toUInt(),proxy=present(o,"proxy")?.let(::proxy),
        method=present(o,"method")?.let(::bytes) ?: HttpBytes.pack("GET"),
        proxySecureMode=if(bool(o,"connect",true)) ProxySecureMode.ProxySecureWithConnect else ProxySecureMode.ProxySecureWithoutConnect,
        requestHeaders=o["headers"]?.jsonArray?.map { it.jsonArray.let { HttpHeader(bytes(it[0]),bytes(it[1])) } } ?: emptyList(),
        requestBody=if(bool(o,"latentBody")) HttpRequestBody.IO { error("body-forced") } else HttpRequestBody.Lazy(emptyList()),
    ) }
    private fun view(r: HttpRequest) = buildJsonObject {
        put("host",json(r.host));put("port",r.port);put("secure",r.secure);put("address",r.hostAddress?.toLong()?.let(::JsonPrimitive) ?: JsonNull)
        put("proxy",r.proxy?.show()?.let(::JsonPrimitive) ?: JsonNull);put("connect",r.proxySecureMode==ProxySecureMode.ProxySecureWithConnect)
        put("headers",JsonArray(r.requestHeaders.map { JsonArray(listOf(json(it.name),json(it.value))) }));put("method",json(r.method));put("show",r.show())
    }
    private fun key(v: JsonElement): HttpConnectionKey = v.jsonObject.let { o ->
        val host=bytes(o.getValue("host"));val port=number(o,"port",80);val address=present(o,"address")?.jsonPrimitive?.long?.toUInt()
        when(text(o,"kind")) {
            "raw"->HttpConnectionKey.Raw(address,host,port)
            "secure"->HttpConnectionKey.Secure(address,host,port)
            "proxy"->HttpConnectionKey.Proxy(host,port,present(o,"auth")?.let(::bytes),bytes(o.getValue("ultimateHost")),number(o,"ultimatePort",443))
            else->error(o)
        }
    }
    private class Fatal(val tag: String): RuntimeException(), HttpExceptionShow {
        override fun showsPrec(precedence: Int): String = ("ObserverFailure "+HaskellString.quoteCodePoints(tag.codePoints().toArray())).let { if(precedence>10) "($it)" else it }
    }
    private class Async: CancellationException(), HttpExceptionShow { override fun showsPrec(precedence: Int)="thread killed" }
    private fun exception(tag: String): Throwable = when(tag) {
        "io"->HttpGhcIOException(GhcIoError(IoErrorType.UserError,Chars.fromString("script-location"),Chars.fromString("script-io")))
        "async"->Async()
        "incomplete"->HttpContentException(HttpFailureContent.IncompleteHeaders)
        "nodata"->HttpContentException(HttpFailureContent.NoResponseDataReceived)
        "timeout"->HttpContentException(HttpFailureContent.ResponseTimeout)
        "tls"->HttpContentException(HttpFailureContent.TlsNotSupported)
        "closed"->HttpContentException(HttpFailureContent.ConnectionClosed)
        "request-nodata"->HttpRequestException(HttpRequest(),HttpFailureContent.NoResponseDataReceived)
        "request-incomplete"->HttpRequestException(HttpRequest(),HttpFailureContent.IncompleteHeaders)
        "nested-connection"->HttpContentException(HttpFailureContent.ConnectionFailure(HttpExceptionValue(exception("io"))))
        "nested-internal"->HttpContentException(HttpFailureContent.InternalException(HttpExceptionValue(exception("io"))))
        else->Fatal(tag)
    }
    private fun failure(e: Throwable) = buildJsonObject {
        when(e) {
            is HttpContentException->{put("kind","content");put("content",e.failure.show());put("prec11",e.failure.showsPrec(11)+"TAIL")}
            is HttpRequestException->{put("kind","request");put("content",e.failure.show())}
            is java.io.IOException->put("kind","io")
            is Async->put("kind","async")
            is Fatal->{put("kind","fatal");put("tag",e.tag)}
            else->throw e
        }
        put("show",(e as HttpExceptionShow).showsPrec(0))
    }
    private fun attempt(action: ()->JsonElement): JsonElement = try { action() } catch(e: Throwable) {buildJsonObject {put("error",failure(e))}}
    private val runtime=object: KeyedPoolRuntime {
        override fun now()=PandocUtcTime(BigInteger.valueOf(60000),BigInteger.ZERO)
        override fun registerCleanup(owner: Any,action: ()->Unit) {}
        override fun startReaper(action: ()->Unit) {}
        override fun delayReaper()=error("no reaper was started")
    }
    private fun observe(v: JsonObject): JsonElement {
        val events=mutableListOf<JsonElement>();var next=0;val roots=mutableListOf<HttpConnection>();val handles=mutableMapOf<String,KeyedManaged<HttpConnection>>()
        fun event(name: String,fields: JsonObject) {events.add(buildJsonObject {put("event",name);fields.forEach { (k,v)->put(k,v) }})}
        fun config(which: String) = v[which]?.jsonObject ?: buildJsonObject {}
        fun selectedFailure(cfg: JsonObject,name: String) {present(cfg,name)?.jsonPrimitive?.content?.let {throw exception(it)}}
        fun connect(which: String,address: UInt?,host: String,port: Long,proxy: Pair<HttpBytes,String>?=null): HttpConnection {
            val id=++next;val cfg=config(which)
            event("connect",buildJsonObject {
                put("kind",which);put("id",id);put("address",address?.toLong()?.let(::JsonPrimitive) ?: JsonNull)
                put("host",JsonArray(host.codePoints().toArray().map(::JsonPrimitive)));put("port",port)
                proxy?.let {put("wire",json(it.first));put("ultimate",JsonArray(it.second.codePoints().toArray().map(::JsonPrimitive)))}
            })
            selectedFailure(cfg,"failure")
            val chunks=ArrayDeque(cfg["chunks"]?.jsonArray?.toList() ?: listOf(json(HttpBytes.pack("HTTP/1.1 200 OK\r\n\r\n")),json(HttpBytes.pack("body"))))
            return HttpConnections.makeConnection(
                {event("read",buildJsonObject {put("id",id)});val chunk=chunks.removeFirstOrNull();when {chunk==null->HttpBytes.empty;chunk is JsonPrimitive && chunk.isString->throw exception(chunk.content);else->bytes(chunk)}},
                {b->event("write",buildJsonObject {put("id",id);put("bytes",json(b))});selectedFailure(cfg,"writeFailure")},
                {event("close",buildJsonObject {put("id",id)});selectedFailure(cfg,"closeFailure")},
            ).also {roots.add(it)}
        }
        fun <A> factory(which: String,action: A): A {event("factory",buildJsonObject {put("kind",which)});selectedFailure(config(which),"initFailure");return action}
        fun policy(which: String)=HttpProxyOverride {secure->
            event("proxy-prepare",buildJsonObject {put("kind",which);put("secure",secure)});selectedFailure(config(which),"initFailure")
            val modify: (HttpRequest)->HttpRequest = {r->present(config(which),"proxy")?.let {r.copy(proxy=proxy(it))} ?: r};modify
        }
        fun settings(): HttpManagerSettings {
            val defaults=HttpManagerSettings(rawConnection={HttpConnector {_,_,_->error("unused")}})
            return defaults.copy(
                rawConnection={factory("raw",HttpConnector {a,h,p->connect("raw",a,h,p)})},
                tlsConnection=if(bool(v,"defaultTls")) defaults.tlsConnection else { {factory("tls",HttpConnector {a,h,p->connect("tls",a,h,p)})} },
                tlsProxyConnection=if(bool(v,"defaultTls")) defaults.tlsProxyConnection else { {factory("proxy",HttpProxyConnector {wire,parse,ultimate,a,h,p->
                    val connection=connect("proxy",a,h,p,wire to ultimate);repeat(number(config("proxy"),"parseCount",1).toInt()) {parse(connection)};connection
                })} },
                proxyInsecure=policy("http"),proxySecure=policy("https"),connectionCount=number(v,"count",10),idleConnectionCount=number(v,"idle",512),
                maxHeaderLength=if(bool(v,"unlimitedLength")) null else number(v,"maxLength",4096),maxNumberHeaders=if(bool(v,"unlimitedHeaders")) null else number(v,"maxHeaders",100),
            )
        }
        val result: JsonElement=when(text(v,"api")) {
            "key"->{val req=request(v.getValue("request"));val k=HttpManagers.connectionKey(req);buildJsonObject {put("key",k.show());put("prec11",k.show(11)+"TAIL");put("drop",view(HttpManagers.dropProxyAuthSecure(req)))}}
            "compare"->{val a=key(v.getValue("left"));val b=key(v.getValue("right"));buildJsonObject {put("eq",a==b);put("order",a.compareTo(b).let {if(it<0) "LT" else if(it>0) "GT" else "EQ"})}}
            "defaults"->settings().let {s->buildJsonObject {put("count",s.connectionCount);put("idle",s.idleConnectionCount);put("timeout",s.responseTimeout.show());put("maxLength",s.maxHeaderLength);put("maxHeaders",s.maxNumberHeaders)}}
            "retry"->JsonPrimitive(HttpManagers.defaultRetryableException(exception(text(v,"exception"))))
            "wrap"->attempt {HttpDefaultManagerExceptionWrapper.wrap(request(v.getValue("request"))) {present(v,"exception")?.let {throw exception(it.jsonPrimitive.content)};v.getValue("result")}}
            "override"->{val p=proxy(v.getValue("proxy"));val policy=when(text(v,"policy")) {"request"->HttpProxyOverrides.fromRequest;"none"->HttpProxyOverrides.noProxy;"use"->HttpProxyOverrides.useProxy(p);"without-connect"->HttpProxyOverrides.useProxySecureWithoutConnect(p);else->error(v)}
                val modify=policy.prepare(bool(v,"secure"));JsonArray(v.getValue("requests").jsonArray.map {view(modify(request(it)))})}
            "factory"->{val create=try {HttpManagers.createConnection(settings())} catch(e: Throwable) {return buildJsonObject {put("result",buildJsonObject {put("constructionError",failure(e))});put("events",JsonArray(events))}}
                buildJsonObject {put("results",JsonArray(v.getValue("keys").jsonArray.map {attempt {create(key(it));JsonPrimitive(true)}}))}}
            "manager"->{val manager=try {HttpManagers.newManager(settings(),{},runtime)} catch(e: Throwable) {return buildJsonObject {put("result",buildJsonObject {put("constructionError",failure(e))});put("events",JsonArray(events))}}
                val results=v.getValue("commands").jsonArray.map {item->val c=item.jsonObject;attempt {
                    when(text(c,"op")) {
                        "take"->{val h=HttpManagers.getConnection(request(c.getValue("request")),manager);handles[text(c,"as")]=h;buildJsonObject {put("reused",h.reused)}}
                        "release"->{handles.getValue(text(c,"handle")).release(if(bool(c,"reuse")) HttpReuse.Reuse else HttpReuse.DontReuse);JsonNull}
                        "read"->json(handles.getValue(text(c,"handle")).resource.read())
                        "write"->{handles.getValue(text(c,"handle")).resource.write(bytes(c.getValue("bytes")));JsonNull}
                        "close"->{HttpManagers.closeManager(manager);JsonNull}
                        "proxy"->view(manager.setProxy(request(c.getValue("request"))))
                        else->error(c)
                    }
                }}
                buildJsonObject {put("results",JsonArray(results));put("timeout",manager.responseTimeout.show());put("maxLength",manager.maxHeaderLength);put("maxHeaders",manager.maxNumberHeaders)}}
            else->error(v)
        }
        handles.values.forEach {it.keepAlive()};java.lang.ref.Reference.reachabilityFence(roots)
        return buildJsonObject {put("result",result);put("events",JsonArray(events))}
    }
    @TestFactory
    fun originalManagerObservations(): List<DynamicTest> = GZIPInputStream(javaClass.getResourceAsStream("/http-manager/data.jsonl.gz")!!).bufferedReader().use {r->
        r.readLine();r.lineSequence().mapIndexed {index,line->val row=Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest("$index ${row.getValue("group").jsonPrimitive.content}") {assertEquals(row.getValue("expected"),observe(row.getValue("request").jsonObject))}
        }.toList()
    }
    @Test
    fun initializationRunsFirstAndCallbacksRemainDeferredAndRetainIdentity() {
        val events=mutableListOf<String>();val connection=HttpDummyConnection(emptyList()).connection
        val modifyRequest: (HttpRequest)->HttpRequest = {error("deferred request")}
        val modifyResponse: (HttpResponse<HttpBodyReader>)->HttpResponse<HttpBodyReader> = {error("deferred response")}
        val retry: (Throwable)->Boolean = {error("deferred retry")}
        val settings=HttpManagerSettings(rawConnection={events.add("raw");HttpConnector {_,_,_->connection}},
            tlsConnection={events.add("tls");HttpConnector {_,_,_->connection}},tlsProxyConnection={events.add("CONNECT");HttpProxyConnector {_,_,_,_,_,_->connection}},
            proxyInsecure=HttpProxyOverride {secure->events.add("http:$secure");{it}},proxySecure=HttpProxyOverride {secure->events.add("https:$secure");{it}},
            modifyRequest=modifyRequest,modifyResponse=modifyResponse,retryableException=retry,responseTimeout=HttpResponseTimeout.Micro(-1),maxHeaderLength=null,maxNumberHeaders=0)
        val manager=HttpManagers.newManager(settings,{events.add("sockets")},runtime)
        assertEquals(listOf("sockets","http:false","https:true","raw","tls","CONNECT"),events)
        assertSame(modifyRequest,manager.modifyRequest);assertSame(modifyResponse,manager.modifyResponse);assertSame(retry,manager.retryableException);assertSame(settings.wrapException,manager.wrapException)
        assertEquals(settings.responseTimeout,manager.responseTimeout);assertNull(manager.maxHeaderLength);assertEquals(0L,manager.maxNumberHeaders)
        val failure=java.io.IOException("socket-init")
        assertSame(failure,assertThrows(java.io.IOException::class.java) {HttpManagers.newManager(settings,{throw failure},runtime)})
        assertEquals(6,events.size)
    }
    @Test
    fun wrappingPreservesCauseAndUnrelatedExceptionsAndDefersTheirShowDictionary() {
        val cause=object: java.io.IOException(),HttpExceptionShow {override fun showsPrec(precedence: Int): String=error("show deferred")}
        val wrapped=assertThrows(HttpContentException::class.java) {HttpDefaultManagerExceptionWrapper.wrap(HttpRequest()) {throw cause}}
        assertSame(cause,(wrapped.failure as HttpFailureContent.InternalException).cause.exception)
        val factory=HttpManagers.createConnection(HttpManagerSettings(rawConnection={HttpConnector {_,_,_->throw cause}}))
        val connect=assertThrows(HttpContentException::class.java) {factory(HttpConnectionKey.Raw(null,HttpBytes.pack("a"),80))}
        assertSame(cause,(connect.failure as HttpFailureContent.ConnectionFailure).cause.exception)
        for(error in listOf(Fatal("fatal"),Async(),HttpContentException(HttpFailureContent.NoResponseDataReceived))) {
            assertSame(error,assertThrows(error.javaClass) {HttpDefaultManagerExceptionWrapper.wrap(HttpRequest()) {throw error}})
            val create=HttpManagers.createConnection(HttpManagerSettings(rawConnection={HttpConnector {_,_,_->throw error}}))
            assertSame(error,assertThrows(error.javaClass) {create(HttpConnectionKey.Raw(null,HttpBytes.pack("a"),80))})
        }
    }
    @Test
    fun responseAdapterReturnsActualConnectionToPoolOnlyAfterConsumption() {
        var closes=0;var creates=0
        val chunks=ArrayDeque(listOf(HttpBytes.pack("HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\nbody")))
        val conn=HttpConnections.makeConnection({chunks.removeFirstOrNull() ?: HttpBytes.empty},{},{closes++})
        val settings=HttpManagerSettings(rawConnection={HttpConnector {_,_,_->creates++;conn}},proxyInsecure=HttpProxyOverrides.fromRequest,proxySecure=HttpProxyOverrides.fromRequest)
        val req=HttpRequest();val manager=HttpManagers.newManager(settings,{},runtime)
        val managed=HttpManagers.getConnection(req,manager)
        val response=HttpResponses.getResponse(4096,100,null,req,HttpManagers.managedConnection(managed))
        assertEquals(listOf(HttpBytes.pack("body")),HttpBodies.consume(response.body));response.close();HttpManagers.closeManager(manager)
        val next=HttpManagers.getConnection(req,manager)
        assertTrue(next.reused);assertSame(conn,next.resource);assertEquals(1,creates);assertEquals(0,closes)
        next.release(HttpReuse.DontReuse);next.release(HttpReuse.Reuse);assertEquals(1,closes)
    }
    @Test
    fun partiallyConsumedResponseIsClosedAndNeverReturnedToThePool() {
        var creates=0;var closes=0
        val settings=HttpManagerSettings(rawConnection={HttpConnector {_,_,_->
            creates++
            val chunks=ArrayDeque(listOf(HttpBytes.pack("HTTP/1.1 200 OK\r\nContent-Length: 8\r\n\r\npart"),HttpBytes.pack("rest")))
            HttpConnections.makeConnection({chunks.removeFirstOrNull() ?: HttpBytes.empty},{},{closes++})
        }},proxyInsecure=HttpProxyOverrides.fromRequest,proxySecure=HttpProxyOverrides.fromRequest)
        val manager=HttpManagers.newManager(settings,{},runtime);val req=HttpRequest()
        val response=HttpResponses.getResponse(4096,100,null,req,HttpManagers.managedConnection(HttpManagers.getConnection(req,manager)))
        assertEquals(HttpBytes.pack("part"),response.body.read())
        response.close();response.close();assertEquals(1,closes)
        val next=HttpManagers.getConnection(req,manager)
        assertFalse(next.reused);assertEquals(2,creates);next.release(HttpReuse.DontReuse);assertEquals(2,closes)
    }
    @Test
    fun withManagerDoesNotCleanUpOnSuccessOrOnCallbackFailure() {
        var closes=0
        val settings=HttpManagerSettings(rawConnection={HttpConnector {_,_,_->HttpConnections.makeConnection({HttpBytes.empty},{},{closes++})}},proxyInsecure=HttpProxyOverrides.fromRequest,proxySecure=HttpProxyOverrides.fromRequest)
        val req=HttpRequest();var escaped: KeyedManaged<HttpConnection>?=null
        val returned=HttpManagers.withManager(settings,{},runtime) {manager->
            val h=HttpManagers.getConnection(req,manager);h.release(HttpReuse.Reuse);escaped=h;manager
        }
        val next=HttpManagers.getConnection(req,returned);assertTrue(next.reused);assertEquals(0,closes)
        next.release(HttpReuse.DontReuse);escaped!!.keepAlive();assertEquals(1,closes)
        val cause=Fatal("callback");var second: KeyedManaged<HttpConnection>?=null
        assertSame(cause,assertThrows(Fatal::class.java) {
            HttpManagers.withManager(settings,{},runtime) {manager->second=HttpManagers.getConnection(req,manager);throw cause}
        })
        assertEquals(1,closes);second!!.release(HttpReuse.DontReuse);second!!.keepAlive();assertEquals(2,closes)
    }
}
