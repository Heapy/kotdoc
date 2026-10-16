package io.heapy.kotdoc.http

import io.heapy.kotdoc.resources.PandocUtcTime
import io.heapy.kotdoc.errors.*
import io.heapy.kotdoc.tls.*
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.util.zip.GZIPInputStream

class HttpTlsManagerTest {
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
    private fun string(v: JsonElement): String = if(v is JsonArray) buildString {
        v.forEach {appendCodePoint(it.jsonPrimitive.int)}
    } else v.jsonPrimitive.content
    private fun chars(v: JsonElement): CharacterString = if(v is JsonArray) CharacterString(v.map {it.jsonPrimitive.int}.toIntArray()) else Chars.fromString(v.jsonPrimitive.content)
    private fun points(s: CharacterString)=JsonArray(s.points().map(::JsonPrimitive))
    private fun num(v: JsonObject,k: String,default: Long)=v[k]?.takeUnless {it is JsonNull}?.jsonPrimitive?.long ?: default
    private fun socket(v: JsonElement): HttpSocketProxy = v.jsonObject.let {s->when(s.getValue("kind").jsonPrimitive.content) {
        "simple"->HttpSocketProxy.Simple(chars(s.getValue("host")),num(s,"port",0).toUShort())
        "environment"->HttpSocketProxy.Environment(s["name"]?.takeUnless {it is JsonNull}?.let(::chars))
        "other"->HttpSocketProxy.Other(chars(s.getValue("host")),num(s,"port",0).toUShort())
        else->error(s)
    }}
    private fun json(s: HttpSocketProxy?): JsonElement=when(s) {
        null->JsonNull
        is HttpSocketProxy.Simple->buildJsonObject {put("kind","simple");put("host",points(s.host));put("port",s.port.toInt())}
        is HttpSocketProxy.Other->buildJsonObject {put("kind","other");put("host",points(s.host));put("port",s.port.toInt())}
        is HttpSocketProxy.Environment->buildJsonObject {put("kind","environment");put("name",s.name?.let(::points) ?: JsonNull)}
    }
    private fun tlsError(v: JsonElement): TlsError = v.jsonObject.let {o->
        val d=o["description"]?.let(::chars) ?: Chars.fromString("description")
        val a=TlsAlertDescription(num(o,"alert",255).toUByte())
        when(num(o,"kind",0).toInt()) {
            0->TlsError.Misc(d);1->TlsError.Protocol(d,a);2->TlsError.ProtocolWarning(d,a);3->TlsError.Certificate(d)
            4->TlsError.HandshakePolicy(d);5->TlsError.EOF;6->TlsError.Packet(d)
            7->TlsError.UnexpectedPacket(d,chars(o.getValue("other")));8->TlsError.PacketParsing(d);else->TlsError.TcpTerminate
        }
    }
    private fun tlsException(v: JsonElement): TlsException = v.jsonObject.let {o->
        fun e()=tlsError(o.getValue("error"))
        when(num(o,"kind",0).toInt()) {
            0->TlsException.Terminated(bool(o,"fatal"),o["description"]?.let(::chars) ?: Chars.fromString("reason"),e())
            1->TlsException.HandshakeFailed(e());2->TlsException.PostHandshake(e());3->TlsException.Uncontextualized(e())
            4->TlsException.ConnectionNotEstablished;else->TlsException.MissingHandshake
        }
    }
    private class Async: java.util.concurrent.CancellationException()
    private class Fatal(val tag: String): RuntimeException(tag)
    private fun io(type: IoErrorType,location: String,description: String="")=HttpGhcIOException(GhcIoError(type,Chars.fromString(location),Chars.fromString(description)))
    private fun raise(tag: String): Nothing=when(tag) {
        "io"->throw io(IoErrorType.UserError,"script-location","script-io")
        "vanished"->throw io(IoErrorType.ResourceVanished,"script-location")
        "post-eof"->throw TlsException.PostHandshake(TlsError.EOF)
        "handshake-eof"->throw TlsException.HandshakeFailed(TlsError.EOF)
        "uncontextualized-eof"->throw TlsException.Uncontextualized(TlsError.EOF)
        "terminated-eof"->throw TlsException.Terminated(true,Chars.fromString("reason"),TlsError.EOF)
        "post-misc"->throw TlsException.PostHandshake(TlsError.Misc("misc"))
        "tls-not-established"->throw TlsException.ConnectionNotEstablished
        "tls-missing-handshake"->throw TlsException.MissingHandshake
        "line"->throw ConnectionLineTooLong()
        "resolve"->throw ConnectionHostNotResolved(Chars.fromString("host"))
        "connect"->throw ConnectionHostCannotConnect(Chars.fromString("host"),listOf(io(IoErrorType.UserError,"one"),io(IoErrorType.ResourceVanished,"two")))
        "content-closed"->throw HttpContentException(HttpFailureContent.ConnectionClosed)
        "request-closed"->throw HttpRequestException(HttpRequest(),HttpFailureContent.ConnectionClosed)
        "nodata"->throw HttpContentException(HttpFailureContent.NoResponseDataReceived)
        "incomplete"->throw HttpContentException(HttpFailureContent.IncompleteHeaders)
        "async"->throw Async()
        else->throw Fatal(tag)
    }
    private fun failure(e: Throwable): JsonObject=buildJsonObject {
        val kind=when(e) {
            is HttpRequestException->"request";is HttpContentException->"content";is GhcIOException->"io"
            is TlsException->"tls";is ConnectionLineTooLong->"line";is ConnectionHostNotResolved->"resolve"
            is ConnectionHostCannotConnect->"connect";is Async->"async";is Fatal,is HttpTlsProxySocksConflict->"fatal"
            else->throw e
        }
        put("kind",kind)
        put("show",when(e) {
            is HttpContentException->e.failure.show()
            is GhcIOException->e.error.toString()
            is HttpExceptionShow->e.showsPrec(0)
            is Async->"thread killed"
            else->e.message!!
        })
    }
    private fun attempt(action: ()->JsonElement): JsonElement=try {action()} catch(e: Throwable) {buildJsonObject {put("error",failure(e))}}
    private fun meta(r: HttpResponse<Unit>)=buildJsonObject {
        put("status",r.status.code);put("headers",jsonHeaders(r.headers));put("jar",r.cookieJar.show());put("show",HttpResponses.showResponse(r,{"()"}))
    }
    private class NativeConnection(val index: Int,val chunks: ArrayDeque<JsonElement>)
    private fun run(v: JsonObject): JsonObject {
        val events=mutableListOf<JsonElement>()
        fun event(name: String,contents: JsonObjectBuilder.()->Unit={}) {events+=buildJsonObject {put("event",name);contents()}}
        fun fail(key: String) {v[key]?.takeUnless {it is JsonNull}?.jsonPrimitive?.content?.let(::raise)}
        var contexts=0;var connections=0
        val backend=object: HttpTlsConnectionBackend<Int,NativeConnection,Int> {
            override val defaultTlsSettings=0
            override fun initializeContext(): Int {
                val i=contexts++;event("context") {put("index",i)};fail("contextFailure");return i
            }
            override fun connect(context: Lazy<Int>,params: HttpTlsConnectionParams<Int>): NativeConnection {
                val i=connections++;event("connect-start") {put("index",i)};fail("beforeConnectFailure")
                val snapshot=buildJsonObject {
                    put("event","connect");put("index",i)
                    put("context",if(bool(v,"forceContext",true)) JsonPrimitive(context.value) else JsonNull)
                    put("host",points(params.host));put("port",params.port.toInt());put("tls",params.secure?.let(::JsonPrimitive) ?: JsonNull)
                    put("socks",json(params.socks))
                }
                events+=snapshot;fail("connectFailure")
                return NativeConnection(i,ArrayDeque(v["chunks"]?.jsonArray ?: emptyList()))
            }
            override fun close(connection: NativeConnection) {event("nc-close") {put("index",connection.index)};fail("closeFailure")}
            override fun getChunk(connection: NativeConnection): HttpBytes {
                event("nc-read") {put("index",connection.index)}
                return when(val value=connection.chunks.removeFirstOrNull()) {
                    null->HttpBytes.empty
                    is JsonPrimitive->raise(value.content)
                    else->bytes(value)
                }
            }
            override fun put(connection: NativeConnection,bytes: HttpBytes) {event("nc-write") {put("index",connection.index);put("bytes",json(bytes))};fail("writeFailure")}
            override fun setSecure(context: Lazy<Int>,connection: NativeConnection,settings: Int) {
                event("secure") {put("index",connection.index);put("context",context.value);put("tls",settings)};fail("secureFailure")
            }
        }
        fun rawFactory(tag: String): ()->HttpConnector = {
            event("raw-init") {put("tag",tag)}
            HttpConnector {_,host,port->event("raw-connect") {put("tag",tag);put("host",host);put("port",port)}
                HttpConnections.makeConnection({HttpBytes.empty},{},{})}
        }
        fun proxyPolicy(tag: String)=HttpProxyOverride {secure->
            event("proxy-init") {put("tag",tag);put("secure",secure)}
            val modifier: (HttpRequest)->HttpRequest={r->r.copy(proxy=HttpProxy(HttpBytes.pack(tag),8000))}
            modifier
        }
        val defaults=HttpManagerSettings(rawConnection=rawFactory("default"),proxyInsecure=proxyPolicy("default-http"),proxySecure=proxyPolicy("default-https"))
        fun base(input: JsonObject): HttpManagerSettings=if(!bool(input,"custom")) defaults else defaults.copy(
            rawConnection=rawFactory("custom"),tlsConnection=rawFactory("custom-tls"),tlsProxyConnection={
                event("proxy-tls-custom-init")
                HttpProxyConnector {_,_,_,_,host,port->event("proxy-tls-custom-connect") {put("host",host);put("port",port)}
                    HttpConnections.makeConnection({HttpBytes.empty},{},{})}
            },proxyInsecure=proxyPolicy("custom-http"),proxySecure=proxyPolicy("custom-https"),
            retryableException={false},wrapException=object: HttpManagerExceptionWrapper {override fun <A> wrap(request: HttpRequest,action: ()->A)=action()},
            connectionCount=3,idleConnectionCount=7,responseTimeout=HttpResponseTimeout.Micro(42))
        val poolRuntime=object: KeyedPoolRuntime {
            override fun now()=PandocUtcTime(BigInteger.ZERO,BigInteger.ZERO)
            override fun registerCleanup(owner: Any,action: ()->Unit) {}
            override fun startReaper(action: ()->Unit) {}
            override fun delayReaper()=error("unused")
        }
        val adapter=HttpTlsManagers(backend,defaults,{event("new-manager")},environment={
            event("environment");fail("environmentFailure")
            v["environment"]?.jsonArray?.map {it.jsonArray.let {string(it[0]) to string(it[1])}} ?: emptyList()
        },poolRuntime=poolRuntime)
        fun optional(input: JsonObject,key: String)=input[key]?.takeUnless {it is JsonNull}
        fun settings(input: JsonObject)=adapter.mkManagerSettingsContext(base(input),optional(input,"context")?.jsonPrimitive?.int,
            num(input,"tls",1).toInt(),optional(input,"httpSocks")?.let(::socket),optional(input,"httpsSocks")?.let(::socket))
        fun viewSettings(settings: HttpManagerSettings)=buildJsonObject {
            put("count",settings.connectionCount);put("idle",settings.idleConnectionCount);put("timeout",settings.responseTimeout.show())
        }
        val result=when(v.getValue("api").jsonPrimitive.content) {
            "socks"->json(HttpTlsManagers.parseSocksSettings(v.getValue("environment").jsonArray.map {it.jsonArray.let {string(it[0]) to string(it[1])}},string(v.getValue("name"))))
            "errors"->{val e=tlsError(v.getValue("error"));val ex=tlsException(v.getValue("exception"))
                val level=TlsAlertLevel(num(v,"level",0).toUByte());val alert=TlsAlertDescription(num(v,"alert",0).toUByte())
                buildJsonObject {put("error",e.show());put("errorPrec11",e.show(11)+"TAIL");put("exception",ex.showsPrec(0));put("exceptionPrec11",ex.showsPrec(11)+"TAIL")
                    put("equalError",e==tlsError(v.getValue("otherError")));put("equalException",ex==tlsException(v.getValue("otherException")))
                    put("level",level.show());put("levelPrec11",level.show()+"TAIL");put("alert",alert.show());put("alertPrec11",alert.show()+"TAIL")}
            }
            "wrap"->{val s=settings(v);val req=request(v.getValue("request"));val tag=v.getValue("failure").jsonPrimitive.content
                val error=try {raise(tag)} catch(e: Throwable) {e}
                val retry=s.retryableException(error)
                val outcome=attempt {s.wrapException.wrap(req) {raise(tag)}}
                buildJsonObject {put("retry",retry);put("outcome",outcome)}
            }
            "settings"->viewSettings(settings(v))
            "initialize"->attempt {
                if(bool(v,"proxy")) adapter.getTlsProxyConnection(optional(v,"context")?.jsonPrimitive?.int,1,optional(v,"socks")?.let(::socket)).invoke()
                else adapter.getTlsConnection(optional(v,"context")?.jsonPrimitive?.int,1,optional(v,"socks")?.let(::socket)).invoke()
                JsonNull
            }
            "factory"->attempt {
                val ctx=optional(v,"context")?.jsonPrimitive?.int
                val tls=if("tls" !in v) 1 else optional(v,"tls")?.jsonPrimitive?.int
                val socks=optional(v,"socks")?.let(::socket);val host=v["host"]?.let(::string) ?: "[::1]";val port=num(v,"port",443)
                val connection=if(bool(v,"proxy")) adapter.getTlsProxyConnection(ctx,tls ?: 1,socks).invoke().connect(
                    v["connectBytes"]?.let(::bytes) ?: HttpBytes.pack("CONNECT"),{c->
                        event("check")
                        if(bool(v,"checkRead")) {val b=c.read();event("check-read") {put("bytes",json(b))}}
                        if(bool(v,"checkClose")) c.close()
                        fail("checkFailure")
                    },v["server"]?.let(::string) ?: "[server]",optional(v,"address")?.jsonPrimitive?.long?.toUInt(),host,port)
                    else adapter.getTlsConnection(ctx,tls,socks).invoke().connect(optional(v,"address")?.jsonPrimitive?.long?.toUInt(),host,port)
                JsonArray(v["commands"]?.jsonArray?.map {cmd->attempt {
                    val c=cmd.jsonObject
                    when(c.getValue("op").jsonPrimitive.content) {
                        "read"->json(connection.read())
                        "unread"->{connection.unread(bytes(c.getValue("bytes")));JsonNull}
                        "write"->{connection.write(bytes(c.getValue("bytes")));JsonNull}
                        "close"->{connection.close();JsonNull}
                        else->raise("unknown-command")
                    }
                }} ?: emptyList())
            }
            "digest"->attempt {
                val initial=request(v.getValue("request"))
                val inner=HttpDigestAuth.applyDigestAuth(bytes(v.getValue("user")),bytes(v.getValue("password")),initial) {req->
                    event("probe") {put("request",json(req))};fail("probeFailure")
                    val res=v["response"]?.jsonObject ?: buildJsonObject {}
                    HttpResponse(HttpStatus(num(res,"status",401),res["message"]?.let(::bytes) ?: HttpBytes.pack("custom")),HttpVersion(1,1),res["headers"]?.let(::headers) ?: emptyList(),
                        Unit,{},req.copy(requestBody=HttpRequestBody.Strict(HttpBytes.empty)),emptyList(),res["jar"]?.let(::jar) ?: HttpCookieJar.empty)
                }
                if(bool(v,"defer")) buildJsonObject {put("deferred",true)} else try {
                    val request=inner();buildJsonObject {put("request",json(request))}
                } catch(e: DigestAuthException) {buildJsonObject {
                    put("digestError",e.details.name);put("request",json(e.request));put("response",meta(e.response))
                    put("show",e.showsPrec(0));put("showPrec11",e.showsPrec(11)+"TAIL");put("display",e.display())
                }}
            }
            "manager"->{val managers=mutableListOf<HttpManager>();val identities=mutableListOf<HttpManager>()
                fun identify(m: HttpManager): Int {val i=identities.indexOfFirst {it===m};if(i>=0) return i;identities+=m;return identities.lastIndex}
                JsonArray(v.getValue("commands").jsonArray.map {cmd->attempt {
                    val c=cmd.jsonObject
                    when(c.getValue("op").jsonPrimitive.content) {
                        "mk"->{event("new-manager");val m=HttpManagers.newManager(settings(c),{},poolRuntime);managers+=m;JsonPrimitive(identify(m))}
                        "new"->{val m=if(bool(c,"with")) adapter.newTlsManagerWith(base(c)) else adapter.newTlsManager()
                            managers+=m;JsonPrimitive(identify(m))}
                        "get"->{val m=adapter.getGlobalManager();managers+=m;JsonPrimitive(identify(m))}
                        "set"->{adapter.setGlobalManager(managers[num(c,"manager",0).toInt()]);JsonNull}
                        "proxy"->json(managers[num(c,"manager",0).toInt()].setProxy(request(c.getValue("request"))))
                        "connect"->{HttpManagers.getConnection(request(c.getValue("request")),managers[num(c,"manager",0).toInt()]).release(HttpReuse.DontReuse);JsonNull}
                        else->raise("manager-command")
                    }
                }})
            }
            else->error(v)
        }
        return buildJsonObject {put("result",result);put("events",JsonArray(events.toList()))}
    }
    @TestFactory fun originalTlsAdapterObservations(): List<DynamicTest> {
        val lines=GZIPInputStream(javaClass.getResourceAsStream("/http-tls/data.jsonl.gz")!!).bufferedReader().use {it.readLines()}
        val metadata=Json.parseToJsonElement(lines.first()).jsonObject
        assertEquals(metadata.getValue("cases").jsonPrimitive.int,lines.size-1)
        return lines.drop(1).mapIndexed {i,line->val row=Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest("${i+1}: ${row.getValue("group").jsonPrimitive.content}") {
                assertEquals(row.getValue("expected"),run(row.getValue("request").jsonObject),row.getValue("request").toString())
            }
        }
    }
}
