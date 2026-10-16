package io.heapy.kotdoc.http

import com.sun.jna.Platform
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import io.heapy.kotdoc.errors.GhcIOException
import io.heapy.kotdoc.errors.GhcIoError
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.UUID
import java.util.zip.GZIPInputStream

class NetworkConnectionDialerTest {
    private fun bytes(v: JsonElement) = HttpBytes(v.jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray())
    private fun json(b: HttpBytes) = JsonArray(b.bytes().map { JsonPrimitive(it.toInt() and 255) })
    private fun chars(v: JsonElement): CharacterString = if (v is JsonArray) CharacterString(v.map { it.jsonPrimitive.int }.toIntArray()) else Chars.fromString(v.jsonPrimitive.content)
    private fun points(v: CharacterString) = JsonArray(v.points().map(::JsonPrimitive))
    private fun io(message: String): Nothing = throw GhcIOException(GhcIoError.userError(message))
    private fun raise(message: String): Nothing = if (message.startsWith("io:")) io(message) else throw AssertionError(message)
    private fun check(v: JsonObject, key: String) { v[key]?.jsonPrimitive?.content?.let(::raise) }
    private fun failure(e: Throwable): JsonElement = when (e) {
        is GhcIOException -> buildJsonObject {
            put("error", "io"); put("type", e.error.type.shown); put("location", Chars.build(e.error.location))
            put("description", Chars.build(e.error.description)); put("filename", e.error.filename?.let { JsonPrimitive(Chars.build(it)) } ?: JsonNull)
        }
        is ConnectionHostNotResolved -> buildJsonObject { put("error", "HostNotResolved"); put("host", points(e.host)) }
        is ConnectionHostCannotConnect -> buildJsonObject {
            put("error", "HostCannotConnect"); put("host", points(e.host)); put("errors", JsonArray(e.errors.map(::failure)))
        }
        is SocksError -> buildJsonObject { put("error", "socks"); put("show", e.showsPrec(0)) }
        is SocksProtocolError -> buildJsonObject { put("error", "protocol"); put("message", e.message) }
        is AssertionError -> buildJsonObject { put("error", "fatal"); put("message", e.message!!) }
        else -> throw e
    }
    private fun host(v: JsonObject): SocksHostAddress = when (v.getValue("kind").jsonPrimitive.content) {
        "ipv4" -> SocksHostAddress.IPv4(v.getValue("word").jsonPrimitive.long.toUInt())
        "ipv6" -> v.getValue("words").jsonArray.map { it.jsonPrimitive.long.toUInt() }.let { SocksHostAddress.IPv6(NetworkHostAddress6(it[0], it[1], it[2], it[3])) }
        "domain" -> SocksHostAddress.Domain(bytes(v.getValue("bytes")))
        else -> error(v)
    }
    private fun json(h: SocksHostAddress): JsonObject = buildJsonObject {
        when (h) {
            is SocksHostAddress.IPv4 -> { put("kind", "ipv4"); put("word", h.host.toLong()) }
            is SocksHostAddress.IPv6 -> { put("kind", "ipv6"); put("words", JsonArray(h.host.words().map { JsonPrimitive(it.toLong()) })) }
            is SocksHostAddress.Domain -> { put("kind", "domain"); put("bytes", json(h.name)) }
        }
        put("show", h.show())
    }
    private fun address(v: JsonObject) = SocksAddress(host(v.getValue("host").jsonObject), v.getValue("port").jsonPrimitive.long.toUShort())
    private fun json(a: SocksAddress) = buildJsonObject { put("host", json(a.host)); put("port", a.port.toInt()) }
    private fun method(v: JsonObject) = v["other"]?.let { SocksMethod.Other(it.jsonPrimitive.long.toUByte()) } ?: SocksMethod.fromEnum(v.getValue("code").jsonPrimitive.long)
    private fun command(v: JsonObject) = v["other"]?.let { SocksCommand.Other(it.jsonPrimitive.long.toUByte()) } ?: SocksCommand.fromEnum(v.getValue("code").jsonPrimitive.long)
    private fun reply(v: JsonObject) = v["other"]?.let { SocksReply.Error(SocksError.Other(it.jsonPrimitive.long.toUByte())) } ?: SocksReply.fromEnum(v.getValue("code").jsonPrimitive.long)
    private fun request(v: JsonObject) = SocksRequest(command(v.getValue("command").jsonObject), host(v.getValue("host").jsonObject), v.getValue("port").jsonPrimitive.long.toUShort())
    private fun response(v: JsonObject) = SocksResponse(reply(v.getValue("reply").jsonObject), host(v.getValue("host").jsonObject), v.getValue("port").jsonPrimitive.long.toUShort())
    private fun json(r: SocksRequest) = buildJsonObject { put("command", r.command.show()); put("host", json(r.host)); put("port", r.port.toInt()) }
    private fun json(r: SocksResponse) = buildJsonObject { put("reply", r.reply.show()); put("host", json(r.host)); put("port", r.port.toInt()) }
    private fun proxy(v: JsonElement?): HttpSocketProxy? {
        if (v == null || v is JsonNull) return null
        val p = v.jsonObject
        return when (p.getValue("kind").jsonPrimitive.content) {
            "simple" -> HttpSocketProxy.Simple(chars(p.getValue("host")), p.getValue("port").jsonPrimitive.long.toUShort())
            "other" -> HttpSocketProxy.Other(chars(p.getValue("host")), p.getValue("port").jsonPrimitive.long.toUShort())
            "environment" -> HttpSocketProxy.Environment(p["name"]?.takeUnless { it is JsonNull }?.let(::chars))
            else -> error(p)
        }
    }
    private fun run(v: JsonObject): JsonElement {
        val events = mutableListOf<JsonElement>()
        val packets = ArrayDeque(v["chunks"]?.jsonArray?.toList() ?: emptyList())
        fun event(name: String, fields: JsonObjectBuilder.() -> Unit = {}) { events.add(buildJsonObject { put("event", name); fields() }) }
        fun effect(name: String) {
            events.add(JsonPrimitive(name))
            when (v["failure"]?.jsonPrimitive?.content) {
                "$name-io" -> io("$name-io")
                "$name-fatal" -> throw AssertionError("$name-fatal")
            }
        }
        fun socket(entry: JsonObject? = null) = object: ConnectionSocket {
            override fun receive(maximum: Int): HttpBytes {
                event("recv") { put("maximum", maximum) }
                val next = packets.removeFirstOrNull() ?: JsonArray(emptyList())
                if (next is JsonPrimitive) raise(next.content)
                return bytes(next)
            }
            override fun sendAll(bytes: HttpBytes) { event("send") { put("bytes", json(bytes)) } }
            override fun close() {
                checkNotNull(entry); event("close-socket") { put("id", entry.getValue("id")) }; check(entry, "closeFailure")
            }
        }
        val result = try {
            when (val api = v.getValue("api").jsonPrimitive.content) {
                "dial" -> {
                    val backend = object: ConnectionDialBackend<ConnectionSocket, JsonObject> {
                        override fun resolve(host: CharacterString, port: UShort): List<JsonObject> {
                            event("resolve") { put("host", points(host)); put("service", port.toString()); put("flags", JsonArray(listOf(JsonPrimitive("AI_ADDRCONFIG")))); put("type", "Stream") }
                            check(v, "resolveFailure"); return v["addresses"]?.jsonArray?.map { it.jsonObject } ?: emptyList()
                        }
                        override fun create(address: JsonObject): ConnectionSocket {
                            event("socket") { put("id", address.getValue("id")); put("family", "AF_INET"); put("type", "Stream") }
                            check(address, "createFailure"); return socket(address)
                        }
                        override fun connect(socket: ConnectionSocket, address: JsonObject) {
                            event("connect") { put("id", address.getValue("id")); put("addressPort", address.getValue("id")) }; check(address, "connectFailure")
                        }
                    }
                    val factory = ConnectionTlsFactory<Unit, Unit> { _, id, _, _ ->
                        events.add(buildJsonObject { put("contextNew", points(id.host)); put("port", json(HttpBytes.pack(id.port.toString()))) })
                        effect("contextNew")
                        object: ConnectionTls {
                            override fun handshake() = effect("handshake")
                            override fun receive() = error("unexpected TLS receive")
                            override fun send(bytes: HttpBytes) = error("unexpected TLS send")
                            override fun bye() = effect("bye")
                            override fun close() = effect("close")
                        }
                    }
                    val dialer = NetworkConnectionDialer(backend, factory) { name ->
                        event("getEnv") { put("name", points(name)) }; check(v, "environmentFailure")
                        v["environment"]?.let(::chars) ?: io("io:environment-missing")
                    }
                    val params = HttpTlsConnectionParams(chars(v.getValue("host")), v.getValue("port").jsonPrimitive.long.toUShort(),
                        if (v["secure"]?.jsonPrimitive?.boolean == true) Unit else null, proxy(v["proxy"]))
                    val connection = dialer.connect(lazy<Unit> { error("context-forced") }, params)
                    val secure = connection.isSecure()
                    if (v["close"]?.jsonPrimitive?.boolean == true) connection.close()
                    buildJsonObject { put("secure", secure); put("host", points(connection.id.host)); put("port", connection.id.port.toInt()) }
                }
                "encode" -> json(when (v.getValue("kind").jsonPrimitive.content) {
                    "hello" -> Socks5.encodeHello(v.getValue("methods").jsonArray.map { method(it.jsonObject) })
                    "helloResponse" -> Socks5.encodeHelloResponse(method(v.getValue("method").jsonObject))
                    "request" -> Socks5.encodeRequest(request(v))
                    "response" -> Socks5.encodeResponse(response(v))
                    else -> error(v)
                })
                "enum" -> {
                    val n = v.getValue("number").jsonPrimitive.long
                    val m = SocksMethod.fromEnum(n); val c = SocksCommand.fromEnum(n); val e = SocksError.fromEnum(n); val r = SocksReply.fromEnum(n)
                    buildJsonObject { put("method", m.show()); put("command", c.show()); put("error", e.showsPrec(0)); put("reply", r.show())
                        put("methodCode", m.code.toInt()); put("commandCode", c.code.toInt()); put("errorCode", e.code.toInt()); put("replyCode", r.code.toInt()) }
                }
                "hello" -> JsonArray(Socks5.receiveHello(socket()).map { JsonPrimitive(it.show()) })
                "helloResponse" -> JsonPrimitive(Socks5.receiveHelloResponse(socket()).show())
                "request" -> json(Socks5.receiveRequest(socket()))
                "response" -> json(Socks5.receiveResponse(socket()))
                "connect" -> json(Socks5.connectWithSocket(socket(), address(v)))
                "listen" -> json(Socks5.listen(socket()))
                "rpc" -> when (val r = Socks5.rpc(socket(), request(v))) {
                    is SocksRpcResult.Success -> buildJsonObject { put("right", json(r.address)) }
                    is SocksRpcResult.Error -> buildJsonObject { put("left", r.error.showsPrec(0)) }
                }
                else -> error(api)
            }
        } catch (e: Throwable) { failure(e) }
        return buildJsonObject { put("result", result); put("events", JsonArray(events)) }
    }
    @TestFactory fun originalAcquisitionAndSocksPrograms(): List<DynamicTest> =
        GZIPInputStream(javaClass.getResourceAsStream("/connection-dial/data.jsonl.gz")!!).bufferedReader().use { reader ->
            val metadata = Json.parseToJsonElement(reader.readLine()).jsonObject
            val cases = reader.lineSequence().map { Json.parseToJsonElement(it).jsonObject }.toList()
            assertEquals(metadata.getValue("cases").jsonPrimitive.int, cases.size)
            cases.mapIndexed { i, c -> DynamicTest.dynamicTest("$i ${c.getValue("group").jsonPrimitive.content}") {
                assertEquals(c.getValue("expected"), run(c.getValue("request").jsonObject))
            } }
        }

    @Test fun actualHttpExecutionUsesNativeConnectionClientThroughSocksProxy() {
        if (!Platform.isMac() && !Platform.isLinux()) return
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { listener ->
            listener.soTimeout = 5000
            val variable = "KOTDOC_SOCKS_${UUID.randomUUID()}"
            val libc = NativeLibrary.getInstance(Platform.C_LIBRARY_NAME)
            assertNull(System.getenv(variable))
            assertEquals(0, libc.getFunction("setenv").invokeInt(arrayOf<Any>(variable, "127.0.0.1:${listener.localPort}", 1)))
            val peer = FutureTask {
                listener.accept().use { socket ->
                    socket.soTimeout = 5000
                    val input = socket.getInputStream(); val output = socket.getOutputStream()
                    assertArrayEquals(byteArrayOf(5, 1, 0), input.readNBytes(3))
                    output.write(byteArrayOf(5, 0)); output.flush()
                    assertArrayEquals(byteArrayOf(5, 1, 0, 3), input.readNBytes(4))
                    val length = input.read(); assertTrue(length > 0)
                    assertEquals("book.invalid", input.readNBytes(length).toString(Charsets.ISO_8859_1))
                    assertArrayEquals(byteArrayOf(0, 80), input.readNBytes(2))
                    output.write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 0, 80)); output.flush()
                    val header = StringBuilder()
                    while (!header.endsWith("\r\n\r\n")) { val b = input.read(); check(b >= 0); header.append(b.toChar()); check(header.length < 4096) }
                    assertTrue(header.startsWith("GET /book HTTP/1.1\r\n")); assertTrue("Host: book.invalid\r\n" in header)
                    output.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); output.flush()
                }
            }
            val server = Thread.ofVirtual().start(peer)
            val client = FutureTask {
                val backend = NetworkConnectionClient(Unit, {}, PosixConnectionDialBackend(),
                    ConnectionTlsFactory<Unit, Unit> { _, _, _, _ -> error("unexpected TLS") })
                val adapters = HttpTlsManagers(backend, HttpManagers.defaultPosixManagerSettings(), {})
                val settings = adapters.mkManagerSettings(Unit, HttpSocketProxy.Environment(Chars.fromString(variable)))
                    .copy(proxyInsecure = HttpProxyOverrides.fromRequest, proxySecure = HttpProxyOverrides.fromRequest)
                val manager = HttpManagers.newManager(settings, {})
                val request = HttpRequests.parseUrlThrow("http://book.invalid/book").copy(responseTimeout = HttpResponseTimeout.Micro(5_000_000))
                assertEquals(200L, HttpExecution.system.httpNoBody(request, manager).status.code)
            }
            val thread = Thread.ofVirtual().start(client)
            try { client.get(10, TimeUnit.SECONDS); peer.get(10, TimeUnit.SECONDS) }
            finally {
                assertEquals(0, libc.getFunction("unsetenv").invokeInt(arrayOf(variable)))
                listener.close(); listOf(thread, server).forEach { it.interrupt(); it.join(5000); assertFalse(it.isAlive) }
            }
        }
    }

    @Test fun nativeResolverReceivesAddressConfiguredStreamHintsAndFreesItsResult() {
        if (!Platform.isMac() && !Platform.isLinux()) return
        val abi = NetworkSocketAbi.currentPosix
        var calls = 0; var frees = 0
        val functions = object: NetworkNameFunctions {
            override fun getAddrInfo(host: Pointer?, service: Pointer?, hints: Pointer?, result: PointerByReference): Int {
                calls++
                assertNotNull(hints)
                assertEquals(abi.packFlags(listOf(NetworkAddressFlag.AI_ADDRCONFIG)), hints!!.getInt(abi.info("flags").toLong()))
                assertEquals(abi.socketType("Stream").value, hints.getInt(abi.info("type").toLong()))
                assertEquals("127.0.0.1", host!!.getString(0, "US-ASCII")); assertEquals("443", service!!.getString(0, "US-ASCII"))
                return NetworkPosixNameFunctions.getAddrInfo(host, service, hints, result)
            }
            override fun freeAddrInfo(pointer: Pointer?) { frees++; NetworkPosixNameFunctions.freeAddrInfo(pointer) }
            override fun gaiStrError(code: Int) = NetworkPosixNameFunctions.gaiStrError(code)
        }
        val resolver = NetworkNameResolver(abi, functions, initializeSockets = {}, darwin = Platform.isMac())
        val backend = PosixConnectionDialBackend(resolver = resolver)
        val addresses = backend.resolve(Chars.fromString("127.0.0.1"), 443u)
        assertTrue(addresses.isNotEmpty()); assertEquals(1, calls); assertEquals(1, frees)
        assertTrue(addresses.all { it.socketType == abi.socketType("Stream") })
    }
}
