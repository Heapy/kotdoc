package io.heapy.kotdoc.http

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import io.heapy.kotdoc.layout.CharacterString

class HttpResponseTest {
    private fun bytes(v: JsonElement) = HttpBytes(v.jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray())
    private fun json(b: HttpBytes) = JsonArray(b.bytes().map { JsonPrimitive(it.toInt() and 255) })
    private fun points(s: String) = JsonArray(s.codePoints().toArray().map(::JsonPrimitive))
    private fun headers(hs: List<HttpHeader>) = JsonArray(hs.map { JsonArray(listOf(json(it.name),json(it.value))) })
    private fun status(sh: HttpStatusHeaders) = buildJsonObject {
        put("code",sh.status.code);put("message",json(sh.status.message));put("version",JsonArray(listOf(JsonPrimitive(sh.version.major),JsonPrimitive(sh.version.minor))))
        put("early",headers(sh.early));put("headers",headers(sh.headers))
    }
    private fun attempt(action: ()->JsonElement): JsonObject = try { buildJsonObject {put("ok",action())} }
        catch(e: HttpContentException) {buildJsonObject {put("error",points(e.message!!))}}
    private fun ops(v: JsonObject, f: (JsonArray)->JsonElement) = JsonArray((v["ops"]?.jsonArray ?: emptyList()).map { op->attempt {f(op.jsonArray)} })
    private fun number(v: JsonObject,key: String) = v[key]?.takeUnless {it is JsonNull}?.jsonPrimitive?.long
    private fun bool(v: JsonObject,key: String) = v[key]?.jsonPrimitive?.boolean ?: false
    private fun readOp(reader: HttpBodyReader, op: JsonArray): JsonElement = when(op[0].jsonPrimitive.content) {
        "read"->json(reader.read())
        "some"->JsonArray(HttpBodies.readSome(reader,op[1].jsonPrimitive.long).map(::json))
        "consume"->JsonArray(HttpBodies.consume(reader).map(::json))
        else->error(op)
    }
    private fun observe(v: JsonObject): JsonElement {
        val mode=v.getValue("mode").jsonPrimitive.content
        if(mode=="inflate") {
            StreamingInflate(number(v,"bits")?.toInt() ?: 31,v["dictionary"]?.let(::bytes)).use { inf->
                val poppers=mutableListOf<()->InflatePop>()
                return ops(v) {op->when(op[0].jsonPrimitive.content) {
                    "feed"->{val i=poppers.size;poppers.add(inf.feed(bytes(op[1])));JsonPrimitive(i)}
                    "pop"->when(val pop=poppers[op[1].jsonPrimitive.int]()) {
                        InflatePop.Done->JsonArray(listOf(JsonPrimitive("done")))
                        is InflatePop.Next->JsonArray(listOf(JsonPrimitive("next"),json(pop.bytes)))
                        is InflatePop.Error->JsonArray(listOf(JsonPrimitive("error"),JsonPrimitive(pop.code)))
                    }
                    "flush"->json(inf.flush());"finish"->json(inf.finish());"unused"->json(inf.unused());"complete"->JsonPrimitive(inf.isComplete())
                    else->error(op)
                }}
            }
        }
        val dummy=HttpDummyConnection(v["input"]?.jsonArray?.map(::bytes) ?: emptyList())
        val base=dummy.connection;val events=mutableListOf<JsonElement>()
        fun event(name: String, value: JsonElement?=null) {events.add(JsonArray(listOfNotNull(JsonPrimitive(name),value)))}
        val conn=HttpConnection({
            if((number(v,"readDelay") ?: 0)>0) {event("read-start");Thread.sleep(java.time.Duration.ofNanos(number(v,"readDelay")!!*1000))}
            base.read().also {event("read",json(it))}
        }, {event("unread",json(it));base.unread(it)}, {event("write",json(it));base.write(it)}, {event("close")})
        val maxLength=number(v,"maxLength");val maxHeaders=number(v,"maxHeaders");val timeout=number(v,"timeout")
        val early: (List<HttpHeader>)->Unit = {event("early",headers(it))}
        val continuation: (()->Unit)?=if(bool(v,"continue"))({event("continue");Thread.sleep(java.time.Duration.ofNanos((number(v,"continueDelay") ?: 0)*1000));conn.write(HttpBytes.pack("data"))}) else null
        val request=HttpRequest(method=v["method"]?.let(::bytes) ?: HttpBytes.pack("GET"),rawBody=bool(v,"raw"),earlyHintHeadersReceived=early,
            decompress=when(number(v,"decompress")?.toInt() ?: 0) {1->HttpRequests::alwaysDecompress;2->{_->false};else->HttpRequests::browserDecompress})
        val result=attempt {when(mode) {
            "header-pair"->{
                fun header(p: JsonElement)=p.jsonArray.let {HttpHeader(bytes(it[0]),bytes(it[1]))}
                val a=header(v.getValue("left"));val b=header(v.getValue("right"))
                if(a==b)assertEquals(a.hashCode(),b.hashCode())
                JsonArray(listOf(JsonPrimitive(a==b),JsonPrimitive(a.compareTo(b).compareTo(0))))
            }
            "headers"->status(HttpHeaders.parseStatusHeaders(maxLength,maxHeaders,conn,timeout,early,continuation))
            "validate"->HttpHeaders.validate(v["headers"]?.jsonArray?.map { val p=it.jsonArray;HttpHeader(bytes(p[0]),bytes(p[1])) } ?: emptyList())?.let(::json) ?: JsonNull
            "host"->JsonArray(HttpConnections.strippedHostName(CharacterString(v.getValue("host").jsonArray.map {it.jsonPrimitive.int}.toIntArray())).points().map(::JsonPrimitive))
            "line"->ops(v) {op->when(op[0].jsonPrimitive.content) {
                "read"->json(HttpConnections.readLine(maxLength,conn));"with"->json(HttpConnections.readLineWith(maxLength,conn,bytes(op[1])))
                "unread"->{HttpConnections.unreadLine(conn,bytes(op[1]));JsonNull};"drop"->{HttpConnections.dropTillBlankLine(maxLength,conn);JsonNull};else->error(op)
            }}
            "make"->{val c=HttpConnections.makeConnection(conn.read,conn.write,conn.close);ops(v) {op->when(op[0].jsonPrimitive.content) {
                "read"->json(c.read());"unread"->{c.unread(bytes(op[1]));JsonNull};"write"->{c.write(bytes(op[1]));JsonNull};"close"->{c.close();JsonNull};else->error(op)
            }}}
            "body"->{val clean={event("cleanup")};val reader=when(v["kind"]?.jsonPrimitive?.content) {
                "length"->HttpBodies.length(clean,number(v,"length") ?: 0,conn)
                "unlimited"->HttpBodies.unlimited(clean,conn)
                "const"->HttpBodies.const(v["input"]?.jsonArray?.map(::bytes) ?: emptyList())
                "gzip"->HttpBodies.gzip(HttpBodyReader(conn.read))
                else->HttpBodies.chunked(maxLength,clean,bool(v,"raw"),conn)
            };ops(v) {readOp(reader,it)}}
            "response"->{
                val managed=HttpManagedConnection(conn,{event("release",JsonPrimitive(it.toString()))},{event("keepAlive")})
                val res=HttpResponses.getResponse(maxLength,maxHeaders,timeout,request,managed,continuation)
                val results=ops(v) {op->when(op[0].jsonPrimitive.content) {
                    "again"->{val second=HttpResponses.getResponse(maxLength,maxHeaders,timeout,HttpResponses.getOriginalRequest(res),managed)
                        buildJsonObject {put("status",status(HttpStatusHeaders(second.status,second.version,second.early,second.headers)));put("body",JsonArray(HttpBodies.consume(second.body).map(::json)))}
                    }
                    "close"->{res.close();JsonNull};"original"->points(HttpResponses.getOriginalRequest(res).show())
                    "lbs"->JsonArray(HttpResponses.lbsResponse(res).body.map(::json));else->readOp(res.body,op)
                }}
                buildJsonObject {put("status",status(HttpStatusHeaders(res.status,res.version,res.early,res.headers)));put("results",results)}
            }
            else->error(mode)
        }}
        return buildJsonObject {put("result",result);put("events",JsonArray(events));put("rest",JsonArray(dummy.input().map(::json)));put("writes",JsonArray(dummy.output().map(::json)))}
    }
    private val corpus by lazy {
        GZIPInputStream(javaClass.getResourceAsStream("/http-response/data.jsonl.gz")!!).bufferedReader().use {r->r.readLine();r.lineSequence().map {Json.parseToJsonElement(it).jsonObject}.toList()}
    }
    @TestFactory fun originalObservations() = corpus.mapIndexed {i,c->DynamicTest.dynamicTest("$i ${c.getValue("group").jsonPrimitive.content}") {assertEquals(c.getValue("expected"),observe(c.getValue("request").jsonObject))}}

    @TestFactory fun originalStreamingInflateUnits(): List<DynamicTest> {
        fun input(group: String): List<HttpBytes> = corpus.first {it.getValue("group").jsonPrimitive.content==group}
            .getValue("request").jsonObject.getValue("ops").jsonArray.map {it.jsonArray}.filter {it[0].jsonPrimitive.content=="feed"}.map {bytes(it[1])}
        fun popAll(inflate: StreamingInflate, chunks: List<HttpBytes>): List<HttpBytes> = buildList {
            for(chunk in chunks) {
                val pop=inflate.feed(chunk)
                while(true) when(val r=pop()) {InflatePop.Done->break;is InflatePop.Next->add(r.bytes);is InflatePop.Error->error("ZlibException ${r.code}")}
            }
            add(inflate.finish())
        }
        val license=HttpBytes(javaClass.getResourceAsStream("/http-response/LICENSE")!!.readBytes().filter {it!=13.toByte()}.toByteArray())
        val gzip=HttpBytes(javaClass.getResourceAsStream("/http-response/LICENSE.gz")!!.readBytes())
        val tests=mutableListOf<DynamicTest>()
        for(single in listOf(true,false))tests.add(DynamicTest.dynamicTest("streaming-commons license ${if(single)"single" else "multi"} inflate") {
            StreamingInflate(31).use {inf->
                val chunks=if(single)listOf(gzip) else gzip.bytes().map {HttpBytes(byteArrayOf(it))}
                assertEquals(license,HttpBytes(popAll(inf,chunks).flatMap {it.bytes().asIterable()}.toByteArray()))
            }
        })
        tests.add(DynamicTest.dynamicTest("streaming-commons different dict") {
            StreamingInflate(15,HttpBytes.pack("NITIALDICTIONARY")).use {inf->assertEquals(0,popAll(inf,input("original-different-dictionary-license")).sumOf {it.size})}
        })
        tests.add(DynamicTest.dynamicTest("streaming-commons large raw decompressing") {
            StreamingInflate(-15).use {inf->val out=popAll(inf,input("original-large-raw-inflate"));assertTrue(out.all {it.bytes().all {b->b==10.toByte()}});assertEquals(32752L*4+1,out.sumOf {it.size.toLong()})}
        })
        tests.add(DynamicTest.dynamicTest("streaming-commons getUnusedInflate") {
            StreamingInflate(15).use {inf->
                val out=popAll(inf,input("original-get-unused-inflate"))
                assertEquals("This data is stored compressed.",out.joinToString(""){it.latin1()})
                assertEquals(HttpBytes.pack("This data isn't."),inf.unused())
            }
        })
        return tests
    }

    /** All unchanged HeadersSpec/BodySpec/ResponseSpec/ConnectionSpec assertions. */
    @TestFactory fun originalSpecs(): List<DynamicTest> {
        fun b(s: String)=HttpBytes.pack(s)
        fun connection(chunks: List<String>)=HttpDummyConnection(chunks.map(::b))
        val tests=mutableListOf<Pair<String,()->Unit>>()
        fun test(name: String,action: ()->Unit) {tests.add(name to action)}
        fun check(sh: HttpStatusHeaders,code: Long,version: HttpVersion,early: List<HttpHeader>,headers: List<HttpHeader>) {
            assertEquals(HttpStatus(code,b("ignored")),sh.status);assertEquals(version,sh.version);assertEquals(early,sh.early);assertEquals(headers,sh.headers)
        }
        test("HeadersSpec simple response") {
            val c=connection(listOf("HTTP/","1.1 200"," OK\r\nfoo",": bar\r\n","baz:bin\r\n\r","\nignored"))
            check(HttpHeaders.parseStatusHeaders(null,null,c.connection),200,HttpVersion(1,1),emptyList(),listOf(HttpHeader("foo","bar"),HttpHeader("baz","bin")))
        }
        for(success in listOf(true,false))test("HeadersSpec Expect $success") {
            val c=connection(if(success)listOf("HTTP/1.1 100 Continue\r\n\r\n","HTTP/1.1 200 OK\r\n","foo: bar\r\n\r\n") else listOf("HTTP/1.1 417 Expectation Failed\r\n\r\n"))
            val sh=HttpHeaders.parseStatusHeaders(null,null,c.connection,continuation={c.connection.write(b("data"))})
            check(sh,if(success)200 else 417,HttpVersion(1,1),emptyList(),if(success)listOf(HttpHeader("foo","bar")) else emptyList())
            assertEquals(if(success)listOf(b("data")) else emptyList<HttpBytes>(),c.output())
        }
        test("HeadersSpec Continue without expectation") {
            val c=connection(listOf("HTTP/1.1 100 Continue\r\n\r\n","HTTP/1.1 200 OK\r\n","foo: bar\r\n\r\n","result"))
            check(HttpHeaders.parseStatusHeaders(null,null,c.connection),200,HttpVersion(1,1),emptyList(),listOf(HttpHeader("foo","bar")))
            assertTrue(c.output().isEmpty());assertEquals(listOf(b("result")),c.input())
        }
        for(multiple in listOf(false,true))test("HeadersSpec early hints $multiple") {
            val input=mutableListOf("HTTP/1.1 103 Early Hints\r\n","Link: </foo.js>\r\n","Link: </bar.js>\r\n\r\n")
            if(multiple)input.addAll(listOf("HTTP/1.1 103 Early Hints\r\n","Link: </baz.js>\r\n\r\n"))
            input.addAll(listOf("HTTP/1.1 200 OK\r\n","Content-Type: text/html\r\n\r\n","<div></div>"))
            val c=connection(input);val callbacks=mutableListOf<List<HttpHeader>>()
            val first=listOf(HttpHeader("Link","</foo.js>"),HttpHeader("Link","</bar.js>"));val second=listOf(HttpHeader("Link","</baz.js>"))
            check(HttpHeaders.parseStatusHeaders(null,null,c.connection,onEarlyHints=callbacks::add),200,HttpVersion(1,1),first+if(multiple)second else emptyList(),listOf(HttpHeader("Content-Type","text/html")))
            assertEquals(listOf(b("<div></div>")),c.input());assertEquals(if(multiple)listOf(first,second) else listOf(first),callbacks)
        }
        for(raw in listOf(false,true))for(pieces in listOf(false,true))for(trailers in listOf(false,true))test("BodySpec chunked raw=$raw pieces=$pieces trailers=$trailers") {
            val trailer=if(trailers)if(raw)"trailers-are: returned\r\nin-raw: body\r\n" else "trailers-are: ignored\r\nbut: consumed\r\n" else ""
            val input="5\r\nhello\r\n6\r\n world\r\n0\r\n"+trailer+"\r\nnot consumed"
            val c=connection(if(pieces)input.map {it.toString()} else listOf(input))
            val reader=HttpBodies.chunked(null,{},raw,c.connection)
            assertEquals(if(raw)input.removeSuffix("not consumed") else "hello world",HttpBodies.consume(reader).joinToString(""){it.latin1()})
            assertEquals("not consumed",c.input().joinToString(""){it.latin1()});assertEquals(b(""),reader.read())
        }
        for(pieces in listOf(false,true))test("BodySpec length pieces=$pieces") {
            val input="hello world done";val c=connection(if(pieces)input.map {it.toString()} else listOf(input));val reader=HttpBodies.length({},11,c.connection)
            assertEquals("hello world",HttpBodies.consume(reader).joinToString(""){it.latin1()});assertEquals(" done",c.input().joinToString(""){it.latin1()});assertEquals(b(""),reader.read())
        }
        fun originalInput(group: String)=corpus.first {it.getValue("group").jsonPrimitive.content==group}.getValue("request").jsonObject.getValue("input").jsonArray.map(::bytes)
        test("BodySpec gzip") {
            val c=HttpDummyConnection(originalInput("original-body-gzip")+b("ignored"));val n=c.input().sumOf {it.size.toLong()}-7
            val reader=HttpBodies.gzip(HttpBodies.length({},n,c.connection))
            assertEquals("Hello world!".repeat(5000),HttpBodies.consume(reader).joinToString(""){it.latin1()});assertEquals("ignored",c.input().joinToString(""){it.latin1()});assertEquals(b(""),reader.read())
        }
        for(kind in listOf("basic","no length","chunked","gzip"))test("ResponseSpec $kind") {
            val chunks=when(kind) {
                "basic"->listOf("HTTP/1.1 200 OK\r\n","Key1: Value1\r\n","Content-length: 11\r\n\r\n","Hello"," W","orld\r\nHTTP/1.1").map(::b)
                "no length"->listOf("HTTP/1.1 200 OK\r\n","Key1: Value1\r\n\r\n","Hello"," W","orld\r\nHTTP/1.1").map(::b)
                "chunked"->listOf("HTTP/1.1 200 OK\r\n","Key1: Value1\r\n","Transfer-encoding: chunked\r\n\r\n","5\r\nHello\r","\n2\r\n W","\r\n4  ignored\r\norld\r\n0\r\n\r\nHTTP/1.1").map(::b)
                else->originalInput("original-response-gzip")
            }
            val c=HttpDummyConnection(chunks);val res=HttpResponses.getResponse(null,null,null,HttpRequests.parseRequest_("http://localhost"),HttpManagedConnection(c.connection,{}))
            assertEquals(HttpStatus(200,b("OK")),res.status);assertEquals(HttpVersion(1,1),res.version)
            assertEquals(listOf(HttpHeader("Key1","Value1"))+when(kind){"basic"->listOf(HttpHeader("Content-length","11"));"chunked"->listOf(HttpHeader("Transfer-encoding","chunked"));"gzip"->listOf(HttpHeader("Content-Encoding","gzip"));else->emptyList()},res.headers)
            val pieces=HttpBodies.consume(res.body)
            if(kind=="gzip")assertEquals("Compressed Hello World",pieces.joinToString(""){it.latin1()})
            else assertEquals(listOf("Hello"," W",if(kind=="no length")"orld\r\nHTTP/1.1" else "orld"),pieces.map{it.latin1()})
        }
        test("Connection normal host") {assertEquals("example.com",HttpConnections.strippedHostName("example.com"))}
        test("Connection IPv4") {assertEquals("127.0.0.1",HttpConnections.strippedHostName("127.0.0.1"))}
        test("Connection IPv6 brackets") {assertEquals("::1",HttpConnections.strippedHostName("[::1]"));assertEquals("::127.0.0.1",HttpConnections.strippedHostName("[::127.0.0.1]"))}
        test("Connection IPvFuture") {assertEquals("[v2.huh]",HttpConnections.strippedHostName("[v2.huh]"))}
        test("Connection trailing host") {assertEquals("[::1]foo",HttpConnections.strippedHostName("[::1]foo"))}
        assertEquals(26,tests.size)
        return tests.map {(n,f)->DynamicTest.dynamicTest(n,f)}
    }
}
