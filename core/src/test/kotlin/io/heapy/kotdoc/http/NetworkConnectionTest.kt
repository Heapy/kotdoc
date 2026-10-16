package io.heapy.kotdoc.http

import com.sun.jna.Platform
import io.heapy.kotdoc.errors.GhcIOException
import io.heapy.kotdoc.errors.GhcIoError
import io.heapy.kotdoc.errors.IoErrorType
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

class NetworkConnectionTest {
    private fun bytes(v: JsonElement) = HttpBytes(v.jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray())
    private fun json(b: HttpBytes) = JsonArray(b.bytes().map { JsonPrimitive(it.toInt() and 255) })
    private fun text(s: CharacterString) = buildString { s.points().forEach { appendCodePoint(if (it in 0xd800..0xdfff) 0xfffd else it) } }
    private fun io(message: String): Nothing = throw GhcIOException(GhcIoError.userError(message))
    private fun failure(e: Throwable): JsonElement = when (e) {
        is GhcIOException -> buildJsonObject {
            put("error", "io"); put("type", e.error.type.shown)
            put("description", if (e.error.type == IoErrorType.UserError) text(e.error.description) else e.error.type.shown)
            put("location", text(e.error.location))
            put("filename", e.error.filename?.let { JsonPrimitive(text(it)) } ?: JsonNull)
        }
        is ConnectionLineTooLong -> buildJsonObject { put("error", "LineTooLong") }
        is AssertionError -> buildJsonObject { put("error", "exception"); put("message", e.message!!) }
        else -> throw e
    }
    private fun outcome(action: () -> JsonElement): JsonElement = try { action() } catch (e: Throwable) { failure(e) }
    private class Stream(data: HttpBytes): ConnectionStream {
        val input = ByteArrayInputStream(data.bytes())
        override fun getSome(maximum: Int) = HttpBytes(input.readNBytes(maximum))
        override fun put(bytes: HttpBytes) = error("observer does not write to raw files")
        override fun flush() = Unit
        override fun close() = Unit
    }
    private fun run(v: JsonObject): JsonElement {
        if (v["session"]?.jsonPrimitive?.boolean == true) return runSessions(v)
        if (v["factory"]?.jsonPrimitive?.boolean == true) return runFactory(v)
        val events = mutableListOf<JsonElement>()
        val chunks = ArrayDeque(v["tlsChunks"]?.jsonArray?.map(::bytes) ?: emptyList())
        var command = buildJsonObject { }
        fun effect(name: String) {
            events.add(JsonPrimitive(name))
            val failures = (command["failures"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()) +
                listOfNotNull(command["failure"]?.jsonPrimitive?.content)
            if ("$name-io" in failures) io("$name-io")
            if ("$name-fatal" in failures) throw AssertionError("$name-fatal")
        }
        val tls = object: ConnectionTls {
            override fun handshake() = effect("handshake")
            override fun receive(): HttpBytes { effect("receive"); return chunks.removeFirstOrNull() ?: HttpBytes.empty }
            override fun send(bytes: HttpBytes) { events.add(buildJsonObject { put("send", json(bytes)) }); effect("send") }
            override fun bye() = effect("bye")
            override fun close() = effect("close")
        }
        val id = ConnectionId(v["host"]?.jsonArray?.let { CharacterString(it.map { it.jsonPrimitive.int }.toIntArray()) } ?: Chars.fromString("host"),
            (v["port"]?.jsonPrimitive?.long ?: 443L).toUShort())
        val conn = NetworkConnection(id, if (v["tls"]?.jsonPrimitive?.boolean == true) ConnectionBackend.Tls(tls)
            else ConnectionBackend.Stream(Stream(bytes(v.getValue("input")))))
        // Observe the same private MVar cell as the upstream observer. This is test
        // reflection, not an additional production accessor or a consuming read.
        val buffer = NetworkConnection::class.java.getDeclaredField("buffer").apply { isAccessible = true }
        if ("buffer" in v) buffer.set(conn, v.getValue("buffer").takeUnless { it is JsonNull }?.let(::bytes))
        val steps = v.getValue("commands").jsonArray.map { c ->
            command = c.jsonObject
            val result = outcome {
                when (command.getValue("op").jsonPrimitive.content) {
                    "get" -> json(conn.get(command.getValue("size").jsonPrimitive.long))
                    "exact" -> json(conn.getExact(command.getValue("size").jsonPrimitive.long))
                    "chunk" -> json(conn.getChunk())
                    "line" -> json(conn.getLine(command.getValue("limit").jsonPrimitive.long))
                    "wait" -> JsonPrimitive(conn.waitForInput(command.getValue("milliseconds").jsonPrimitive.long,
                        ConnectionTimeout { micros, action -> if (micros == 0L) false else { action(); true } }))
                    "split" -> json(conn.getChunkWith { b ->
                        val n = command.getValue("size").jsonPrimitive.long.coerceIn(0, b.size.toLong()).toInt()
                        val mode = command["mode"]?.jsonPrimitive?.content
                        if (mode == "pair-fail") io("pair-fail")
                        ConnectionChunk({ if (mode == "value-fail") io("value-fail"); HttpBytes(b.bytes().copyOfRange(0, n)) },
                            { if (mode == "rest-fail") io("rest-fail"); HttpBytes(b.bytes().copyOfRange(n, b.size)) })
                    })
                    "upgrade" -> {
                        conn.setSecure {
                            if (command["paramsFail"]?.jsonPrimitive?.boolean == true) io("params-fail")
                            events.add(buildJsonObject {
                                put("contextNew", JsonArray(id.host.points().map(::JsonPrimitive)))
                                put("port", json(HttpBytes.pack(id.port.toString())))
                            })
                            effect("contextNew"); tls.handshake(); tls
                        }
                        JsonNull
                    }
                    "secure" -> JsonPrimitive(conn.isSecure())
                    "close" -> { conn.close(); JsonNull }
                    "put" -> { conn.put(bytes(command.getValue("bytes"))); JsonNull }
                    else -> error(command)
                }
            }
            buildJsonObject {
                put("result", result); put("buffer", (buffer.get(conn) as HttpBytes?)?.let(::json) ?: JsonNull)
            }
        }
        return buildJsonObject { put("steps", JsonArray(steps)); put("events", JsonArray(events)) }
    }
    private fun runFactory(v: JsonObject): JsonElement {
        val events = mutableListOf<JsonElement>()
        var closed = false
        val stream = object: ConnectionStream {
            override fun getSome(maximum: Int) = HttpBytes.empty
            override fun put(bytes: HttpBytes) = Unit
            override fun flush() = Unit
            override fun close() { closed = true }
        }
        fun effect(name: String) {
            events.add(JsonPrimitive(name))
            when (v["failure"]?.jsonPrimitive?.content) {
                "$name-io" -> io("$name-io")
                "$name-fatal" -> throw AssertionError("$name-fatal")
            }
        }
        val tls = object: ConnectionTls {
            override fun handshake() = effect("handshake")
            override fun receive() = HttpBytes.empty
            override fun send(bytes: HttpBytes) = Unit
            override fun bye() = effect("bye")
            override fun close() = effect("close")
        }
        val factory = ConnectionTlsFactory<Unit, Lazy<Unit>> { _, id, settings, raw ->
            assertEquals(ConnectionBackend.Stream(stream), raw)
            settings.value
            events.add(buildJsonObject {
                put("contextNew", JsonArray(id.host.points().map(::JsonPrimitive)))
                put("port", json(HttpBytes.pack(id.port.toString())))
            })
            effect("contextNew"); tls
        }
        val result = outcome {
            val params = HttpTlsConnectionParams(Chars.fromString("factory"), 123u,
                if (v["secure"]?.jsonPrimitive?.boolean == true) lazy {
                    if (v["paramsFail"]?.jsonPrimitive?.boolean == true) io("params-fail")
                } else null, lazy<HttpSocketProxy?> { io("socks-forced") })
            val conn = NetworkConnections.connectFromHandle(lazy<Unit> { error("context-forced") }, stream, params, factory)
            val before = closed; val secure = conn.isSecure(); conn.close()
            buildJsonObject { put("secure", secure); put("closedBefore", before); put("closedAfter", closed) }
        }
        return buildJsonObject { put("result", result); put("streamClosed", closed); put("events", JsonArray(events)) }
    }
    private fun runSessions(v: JsonObject): JsonElement {
        val sessions = ConnectionSessionManager<() -> Nothing>()
        val steps = v.getValue("commands").jsonArray.map { item ->
            val c = item.jsonObject
            val id = bytes(c.getValue("id"))
            outcome {
                when (c.getValue("op").jsonPrimitive.content) {
                    "establish" -> { val result: Any? = sessions.establish(id) { io(c.getValue("value").jsonPrimitive.content) }; JsonPrimitive(result != null) }
                    "resume" -> { sessions.resume(id)?.invoke(); JsonNull }
                    "once" -> { sessions.resumeOnlyOnce(id)?.invoke(); JsonNull }
                    "invalidate" -> { sessions.invalidate(id); JsonNull }
                    else -> error(c)
                }
            }
        }
        return buildJsonObject { put("steps", JsonArray(steps)); put("useTicket", sessions.useTicket) }
    }
    @TestFactory fun originalConnectionPrograms(): List<DynamicTest> =
        GZIPInputStream(javaClass.getResourceAsStream("/connection/data.jsonl.gz")!!).bufferedReader().use { reader ->
            val metadata = Json.parseToJsonElement(reader.readLine()).jsonObject
            val cases = reader.lineSequence().map { Json.parseToJsonElement(it).jsonObject }.toList()
            assertEquals(metadata.getValue("cases").jsonPrimitive.int, cases.size)
            cases.mapIndexed { i, c -> DynamicTest.dynamicTest("$i ${c.getValue("group").jsonPrimitive.content}") {
                assertEquals(c.getValue("expected"), run(c.getValue("request").jsonObject))
            } }
        }

    @Test fun sessionResumeOnlyOnceHasOneWinnerAcrossConcurrentReaders() {
        val sessions = ConnectionSessionManager<Any>()
        val key = HttpBytes.pack("id"); val value = Any(); sessions.establish(key, value)
        val start = CountDownLatch(1)
        val tasks = List(64) { FutureTask { start.await(); sessions.resumeOnlyOnce(key) } }
        val threads = tasks.map { Thread.ofVirtual().start(it) }
        start.countDown()
        val results = tasks.map { it.get(5, TimeUnit.SECONDS) }
        assertEquals(1, results.count { it === value }); assertNull(sessions.resume(key))
        threads.forEach { it.join(5000); assertFalse(it.isAlive) }
    }

    @Test fun backendSnapshotIsReleasedDuringIOAndRawWriteFlushesInOrder() {
        val events = mutableListOf<String>()
        lateinit var connection: NetworkConnection
        val stream = object: ConnectionStream {
            override fun getSome(maximum: Int): HttpBytes { assertEquals(16384, maximum); assertFalse(connection.isSecure()); return HttpBytes.pack("x") }
            override fun put(bytes: HttpBytes) { assertFalse(connection.isSecure()); events.add("put:${bytes.latin1()}") }
            override fun flush() { events.add("flush") }
            override fun close() { assertFalse(connection.isSecure()); events.add("close") }
        }
        connection = NetworkConnection(ConnectionId(Chars.fromString("h"), 1u), ConnectionBackend.Stream(stream))
        val task = FutureTask { assertEquals(HttpBytes.pack("x"), connection.getChunk()); connection.put(HttpBytes.pack("a")); connection.close() }
        val thread = Thread.ofVirtual().start(task)
        try { task.get(5, TimeUnit.SECONDS); assertEquals(listOf("put:a", "flush", "close"), events) }
        finally { thread.interrupt(); thread.join(5000); assertFalse(thread.isAlive) }
    }

    @Test fun lazyResultMayReadAgainAfterTheBufferCellWasCommitted() {
        val connection = NetworkConnection(ConnectionId(Chars.fromString("h"), 1u), ConnectionBackend.Stream(Stream(HttpBytes.pack("abc"))))
        val task = FutureTask {
            connection.getChunkWith { b -> ConnectionChunk({ connection.getChunk() }, { b }) }
        }
        val thread = Thread.ofVirtual().start(task)
        try { assertEquals(HttpBytes.pack("abc"), task.get(5, TimeUnit.SECONDS)); assertEquals(HttpBytes.empty, connection.getChunk()) }
        finally { thread.interrupt(); thread.join(5000); assertFalse(thread.isAlive) }
    }

    @Test fun upgradeWaitsForTheReaderThenHoldsBothCellsUntilHandshakeFinishes() {
        val reading = CountDownLatch(1); val releaseRead = CountDownLatch(1)
        val handshaking = CountDownLatch(1); val releaseHandshake = CountDownLatch(1)
        val stream = object: ConnectionStream {
            override fun getSome(maximum: Int): HttpBytes { reading.countDown(); releaseRead.await(); return HttpBytes.pack("old") }
            override fun put(bytes: HttpBytes) = Unit
            override fun flush() = Unit
            override fun close() = Unit
        }
        val tls = object: ConnectionTls {
            override fun handshake() { handshaking.countDown(); releaseHandshake.await() }
            override fun receive() = HttpBytes.pack("new")
            override fun send(bytes: HttpBytes) = Unit
            override fun bye() = Unit
            override fun close() = Unit
        }
        val connection = NetworkConnection(ConnectionId(Chars.fromString("h"), 1u), ConnectionBackend.Stream(stream))
        val read = FutureTask { connection.get(1) }; val reader = Thread.ofVirtual().start(read)
        assertTrue(reading.await(5, TimeUnit.SECONDS))
        val upgrade = FutureTask {
            connection.setSecure(lazyOf(Unit), Unit, ConnectionTlsFactory<Unit, Unit> { _, _, _, _ -> tls })
        }
        val upgrader = Thread.ofVirtual().start(upgrade)
        var inspector: Thread? = null
        try {
            awaitWaiting(upgrader); assertFalse(upgrade.isDone); assertEquals(1L, handshaking.count)
            releaseRead.countDown(); assertEquals(HttpBytes.pack("o"), read.get(5, TimeUnit.SECONDS))
            assertTrue(handshaking.await(5, TimeUnit.SECONDS))
            val secure = FutureTask { connection.isSecure() }; inspector = Thread.ofVirtual().start(secure)
            awaitWaiting(inspector); assertFalse(secure.isDone)
            releaseHandshake.countDown(); upgrade.get(5, TimeUnit.SECONDS); assertTrue(secure.get(5, TimeUnit.SECONDS))
            assertEquals(HttpBytes.pack("new"), connection.getChunk()) // discarded "ld"
        } finally {
            releaseRead.countDown(); releaseHandshake.countDown()
            listOfNotNull(reader, upgrader, inspector).forEach { it.interrupt(); it.join(5000); assertFalse(it.isAlive) }
        }
    }
    private fun awaitWaiting(thread: Thread) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (thread.state !in listOf(Thread.State.WAITING, Thread.State.TIMED_WAITING)) {
            assertTrue(thread.isAlive); assertTrue(System.nanoTime() < deadline); Thread.sleep(1)
        }
    }

    @Test fun nativeSocketTransfersBinaryDataLinesAndEOFThroughTheOriginalBuffer() {
        if (!Platform.isMac() && !Platform.isLinux()) return
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { listener ->
            listener.soTimeout = 5000
            val data = ByteArray(4097) { it.toByte() }
            val peer = FutureTask {
                listener.accept().use { socket ->
                    socket.soTimeout = 5000
                    socket.getOutputStream().write("first\r\nsecond\n".toByteArray())
                    socket.getOutputStream().flush()
                    assertEquals(42, socket.getInputStream().read())
                    socket.getOutputStream().write(data); socket.getOutputStream().flush()
                }
            }
            val server = Thread.ofVirtual().start(peer)
            val io = NetworkSocketIo.posix
            val handle = io.create(io.addresses.family("AF_INET"), io.addresses.socketType("Stream"), 0)
            val client = FutureTask {
                io.connect(handle, NetworkSocketAddress.Inet(listener.localPort.toUShort(), NetworkHostAddresses.fromTuple(listOf(127u, 0u, 0u, 1u))))
                val params = HttpTlsConnectionParams<Unit>(Chars.fromString("127.0.0.1"), listener.localPort.toUShort(), null,
                    lazy<HttpSocketProxy?> { error("SOCKS must remain unforced") })
                val connection = NetworkConnections.connectFromSocket(lazy<Unit> { error("context forced") },
                    PosixConnectionSocket(handle), params, ConnectionTlsFactory<Unit, Unit> { _, _, _, _ -> error("TLS forced") })
                assertEquals(HttpBytes.pack("first\r"), connection.getLine(100))
                assertEquals(HttpBytes.pack("second"), connection.getLine(100))
                connection.put(HttpBytes(byteArrayOf(42)))
                assertArrayEquals(data, connection.getExact(data.size.toLong()).bytes())
                assertEquals(HttpBytes.empty, connection.getChunk())
                val error = assertThrows(GhcIOException::class.java) { connection.getChunk() }
                assertEquals(IoErrorType.EOF, error.error.type)
                assertEquals("Network.Connection.connectionGetChunk", text(error.error.location))
                connection.close(); assertEquals(-1, handle.unsafeFd)
            }
            val thread = Thread.ofVirtual().start(client)
            try { client.get(10, TimeUnit.SECONDS); peer.get(10, TimeUnit.SECONDS) }
            finally {
                handle.close(); listener.close()
                listOf(thread, server).forEach { it.interrupt(); it.join(5000); assertFalse(it.isAlive) }
            }
        }
    }
}
