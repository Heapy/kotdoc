package io.heapy.kotdoc.http

import com.sun.jna.NativeLong
import com.sun.jna.Platform
import com.sun.jna.Pointer
import io.heapy.kotdoc.errors.GhcIOException
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

class NetworkSocketIoTest {
    private var invalidWaitManager: GhcEventManager? = null
    private val platform = if (Platform.isMac()) "macos-64" else "linux-64"
    private fun n(v: JsonObject, key: String) = v.getValue(key).jsonPrimitive.long
    private fun s(v: JsonObject, key: String) = v.getValue(key).jsonPrimitive.content
    private fun raw(v: JsonElement) = v.jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray()
    private fun bytes(b: ByteArray) = JsonArray(b.map { JsonPrimitive(it.toInt() and 255) })
    private fun failure(e: GhcIOException) = buildJsonObject {
        val error = e.error
        put("type", error.type.shown); put("location", Chars.build(error.location)); put("description", Chars.build(error.description))
        put("errno", error.errno?.let(::JsonPrimitive) ?: JsonNull); put("filename", error.filename?.let(Chars::build)?.let(::JsonPrimitive) ?: JsonNull)
        put("show", error.toString())
    }
    private fun observe(action: () -> JsonElement): JsonElement = try { action() } catch (e: GhcIOException) {
        buildJsonObject { put("error", failure(e)) }
    }
    private fun request(v: JsonObject): JsonElement {
        val addressAbi = NetworkSocketAbi.load(platform); val abi = NetworkSocketIoAbi.load(platform)
        val io = NetworkSocketIo(addressAbi, abi, NetworkPosixSocketCalls)
        return when (s(v, "api")) {
            "guard" -> {
                val script = v.getValue("script").jsonArray.iterator(); val events = mutableListOf<String>()
                val action = { events.add("call"); val step = script.next().jsonArray; NetworkNativeResult(step[0].jsonPrimitive.long, step[1].jsonPrimitive.int) }
                val result = observe { JsonPrimitive(when (s(v, "mode")) {
                    "once" -> io.checked("guard", action)
                    "retry" -> io.retry("guard", action = action)
                    else -> io.retry("guard", { events.add("wait") }, action)
                }) }
                buildJsonObject { put("result", result); put("events", JsonArray(events.map(::JsonPrimitive))) }
            }
            "send-script" -> {
                val script = v.getValue("script").jsonArray.iterator(); val events = mutableListOf<JsonElement>()
                val calls = NetworkSocketCalls { name, args ->
                    check(name == "send")
                    events.add(bytes((args[1] as Pointer).getByteArray(0, (args[2] as NativeLong).toInt())))
                    val step = script.next().jsonArray; val kind = step[0].jsonPrimitive.int; val value = step[1].jsonPrimitive.int
                    if (kind == 0) NetworkNativeResult(value.toLong(), 0) else NetworkNativeResult(-1, value)
                }
                val controlled = NetworkSocketIo(addressAbi, abi, calls, NetworkSocketWaiter { _, _, _ -> events.add(JsonArray(listOf(JsonPrimitive(-1)))) })
                val socket = NetworkSocketHandle(-1, calls)
                val result = observe { controlled.sendAll(socket, raw(v.getValue("bytes"))); JsonNull }
                buildJsonObject { put("result", result); put("events", JsonArray(events)) }
            }
            "wait-invalid" -> {
                val manager = invalidWaitManager ?: GhcEventRuntime.newManager().also { invalidWaitManager = it }
                try { observe { manager.await(-1, v.getValue("write").jsonPrimitive.boolean, abi.int("EBADF")); JsonPrimitive(true) } }
                finally { if (v.getValue("write").jsonPrimitive.boolean) manager.cleanup() }
            }
            "invalid" -> observe {
                val socket = NetworkSocketHandle(-1, NetworkPosixSocketCalls)
                val address = NetworkSocketAddress.Inet(80u, NetworkHostAddresses.fromTuple(listOf(127u, 0u, 0u, 1u)))
                when (s(v, "operation")) {
                    "recv" -> bytes(io.receive(socket, n(v, "size")).bytes())
                    "send" -> JsonPrimitive(io.send(socket, raw(v.getValue("bytes"))))
                    "sendAll" -> { io.sendAll(socket, raw(v.getValue("bytes"))); JsonNull }
                    "set" -> { io.setIntOption(socket, "NoDelay", 1); JsonNull }
                    "get" -> JsonPrimitive(io.getIntOption(socket, "NoDelay"))
                    "connect" -> { io.connect(socket, address); JsonNull }
                    "bind" -> { io.bind(socket, address); JsonNull }
                    "listen" -> { io.listen(socket, 1); JsonNull }
                    "name" -> JsonPrimitive(io.socketName(socket).show(addressAbi))
                    "peer" -> JsonPrimitive(io.peerName(socket).show(addressAbi))
                    "shutdown" -> { io.shutdown(socket, 1); JsonNull }
                    else -> error(v)
                }
            }
            "socket" -> observe {
                val family = NetworkSocketFamily(n(v, "family").toInt()); val type = NetworkSocketType(n(v, "type").toInt())
                val socket = io.create(family, type, n(v, "protocol").toInt())
                try {
                    val fd = socket.unsafeFd
                    val flags = NetworkPosixSocketCalls.call("fcntl", arrayOf(fd, abi.int("F_GETFL"), 0)).value.toInt()
                    val descriptorFlags = NetworkPosixSocketCalls.call("fcntl", arrayOf(fd, abi.int("F_GETFD"), 0)).value.toInt()
                    val option = if (family == addressAbi.family("AF_INET")) io.getIntOption(socket, "DontFragment")
                        else if (family == addressAbi.family("AF_INET6") && type.value in listOf(1, 2)) io.getIntOption(socket, "IPv6Only") else -99
                    buildJsonObject {
                        put("nonblock", flags and abi.int("O_NONBLOCK") != 0); put("cloexec", descriptorFlags and abi.int("FD_CLOEXEC") != 0)
                        put("type", io.getIntOption(socket, "Type")); put("option", option)
                    }
                } finally { socket.close() }
            }
            "tcp" -> observe { tcp(raw(v.getValue("bytes")), n(v, "size")) }
            else -> error(v)
        }
    }
    private fun tcp(payload: ByteArray, chunk: Long): JsonElement = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { listener ->
        listener.soTimeout = 5000
        val task = FutureTask<ByteArray> {
            listener.accept().use { peer ->
                peer.soTimeout = 5000
                val sent = peer.getInputStream().readNBytes(payload.size)
                peer.getOutputStream().write(payload.reversedArray() + byteArrayOf(0, -1, 1)); peer.shutdownOutput()
                sent
            }
        }
        val server = Thread.ofVirtual().start(task)
        try {
            val connection = HttpSocketConnections.openSocketConnectionSize(HttpPosixSocketBackend(), {}, chunk, null, "127.0.0.1", listener.localPort.toLong())
            val received = ByteArrayOutputStream()
            try {
                connection.write(HttpBytes(payload))
                while (true) {
                    val next = connection.read().bytes(); if (next.isEmpty()) break
                    assertTrue(next.size <= chunk); received.write(next)
                }
                assertEquals(HttpBytes.empty, connection.read())
            } finally { connection.close() }
            buildJsonObject { put("sent", bytes(task.get(5, TimeUnit.SECONDS))); put("received", bytes(received.toByteArray())) }
        } finally {
            listener.close(); server.join(5000); assertFalse(server.isAlive)
        }
    }
    @TestFactory fun originalNativeIo(): List<DynamicTest> {
        if (!Platform.isMac() && !Platform.isLinux()) return emptyList()
        val stream = checkNotNull(javaClass.getResourceAsStream("/network-io/$platform.jsonl.gz"))
        return GZIPInputStream(stream).bufferedReader().use { reader ->
            reader.readLine(); reader.lineSequence().mapIndexed { i, line ->
                val row = Json.parseToJsonElement(line).jsonObject
                DynamicTest.dynamicTest("$platform $i ${row.getValue("group").jsonPrimitive.content}") {
                    assertEquals(row.getValue("expected"), request(row.getValue("request").jsonObject))
                }
            }.toList()
        }
    }
    @Test fun blockedNativeReceiveObservesInterruptionAndClose() {
        assumeTrue(Platform.isMac() || Platform.isLinux())
        for (close in listOf(false, true)) ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { listener ->
            listener.soTimeout = 5000
            val holdPeer = CountDownLatch(1); val peerTask = FutureTask<Boolean> { listener.accept().use { holdPeer.await(5, TimeUnit.SECONDS) } }
            val server = Thread.ofVirtual().start(peerTask)
            val io = NetworkSocketIo.posix
            val socket = io.create(io.addresses.family("AF_INET"), io.addresses.socketType("Stream"), 0)
            try {
                io.connect(socket, NetworkSocketAddress.Inet(listener.localPort.toUShort(), NetworkHostAddresses.fromTuple(listOf(127u, 0u, 0u, 1u))))
                val reading = FutureTask { io.receive(socket, 1) }; val thread = Thread.ofVirtual().start(reading)
                awaitRegistered(socket, reading)
                if (close) socket.close() else thread.interrupt()
                val failed = assertThrows(ExecutionException::class.java) { reading.get(5, TimeUnit.SECONDS) }
                if (close) assertInstanceOf(GhcIOException::class.java, failed.cause) else assertInstanceOf(InterruptedException::class.java, failed.cause)
                thread.join(5000); assertFalse(thread.isAlive)
            } finally { socket.close(); holdPeer.countDown(); peerTask.get(5, TimeUnit.SECONDS); server.join(5000) }
        }
    }
    @Test fun boundSocketKeepsOriginalIdentityAndDescriptorShowAfterRepeatedClose() {
        assumeTrue(Platform.isMac() || Platform.isLinux())
        val io = NetworkSocketIo.posix; val socket = io.create(io.addresses.family("AF_INET"), io.addresses.socketType("Stream"), 0)
        val original = socket.toString()
        try {
            io.bind(socket, NetworkSocketAddress.Inet(0u, NetworkHostAddresses.fromTuple(listOf(127u, 0u, 0u, 1u))))
            io.listen(socket, 1)
            val address = io.socketName(socket) as NetworkSocketAddress.Inet
            assertTrue(address.port > 0u)
            java.net.Socket("127.0.0.1", address.port.toInt()).use { assertTrue(it.isConnected) }
        } finally { socket.close(); socket.close() }
        assertEquals(-1, socket.unsafeFd); assertEquals(original, socket.toString()); assertEquals(socket, socket)
    }

    /** Literal units from network/tests/Network/Socket/ByteStringSpec.hs.
     * A Java peer makes the wire exchange independent of the Kotlin native IO. */
    @TestFactory fun originalTcpUnits(): List<DynamicTest> {
        if (!Platform.isMac() && !Platform.isLinux()) return emptyList()
        val message = "This is a test message.".toByteArray(Charsets.US_ASCII)
        return listOf("send", "sendAll", "recv", "recv-overflow", "recv-EOF", "send-closed", "sendAll-closed", "recv-closed", "send-unconnected", "recv-unconnected").map { mode ->
            DynamicTest.dynamicTest("original ByteStringSpec $mode") {
                val io = NetworkSocketIo.posix
                val socket = io.create(io.addresses.family("AF_INET"), io.addresses.socketType("Stream"), 0)
                try {
                    when {
                        mode.endsWith("closed") || mode.endsWith("unconnected") -> {
                            if (mode.endsWith("closed")) socket.close()
                            assertThrows(GhcIOException::class.java) { when {
                                mode.startsWith("sendAll") -> io.sendAll(socket, message)
                                mode.startsWith("send") -> io.send(socket, message)
                                else -> io.receive(socket, 1024)
                            } }
                        }
                        else -> ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { listener ->
                            listener.soTimeout = 5000
                            val peer = FutureTask<ByteArray> {
                                listener.accept().use { accepted ->
                                    accepted.soTimeout = 5000
                                    if (mode.startsWith("send")) accepted.getInputStream().readNBytes(message.size)
                                    else {
                                        if (mode != "recv-EOF") accepted.getOutputStream().write(message)
                                        accepted.shutdownOutput(); byteArrayOf()
                                    }
                                }
                            }
                            val server = Thread.ofVirtual().start(peer)
                            try {
                                io.connect(socket, NetworkSocketAddress.Inet(listener.localPort.toUShort(), NetworkHostAddresses.fromTuple(listOf(127u, 0u, 0u, 1u))))
                                when (mode) {
                                    "send" -> { io.send(socket, message); assertArrayEquals(message, peer.get(5, TimeUnit.SECONDS)) }
                                    "sendAll" -> { io.sendAll(socket, message); assertArrayEquals(message, peer.get(5, TimeUnit.SECONDS)) }
                                    "recv" -> assertArrayEquals(message, io.receive(socket, 1024).bytes())
                                    "recv-overflow" -> assertArrayEquals(message, io.receive(socket, (message.size - 3).toLong()).bytes() + io.receive(socket, 1024).bytes())
                                    "recv-EOF" -> assertEquals(HttpBytes.empty, io.receive(socket, 4096))
                                }
                                peer.get(5, TimeUnit.SECONDS)
                            } finally { listener.close(); server.join(5000); assertFalse(server.isAlive) }
                        }
                    }
                } finally { socket.close() }
            }
        }
    }

    @Test fun nativeHttpManagerReusesTheActualConnectionAfterChunkedAndFixedResponses() {
        assumeTrue(Platform.isMac() || Platform.isLinux())
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { listener ->
            listener.soTimeout = 5000
            val served = FutureTask<List<String>> {
                listener.accept().use { socket ->
                    socket.soTimeout = 5000; socket.tcpNoDelay = true
                    List(2) { index ->
                        val header = ByteArrayOutputStream()
                        while (!header.toString(Charsets.ISO_8859_1).endsWith("\r\n\r\n")) {
                            val b = socket.getInputStream().read(); check(b >= 0); header.write(b)
                        }
                        val reply = if (index == 0) "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n4\r\nbody\r\n0\r\n\r\n"
                            else "HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\nbody"
                        for (b in reply.toByteArray(Charsets.US_ASCII)) socket.getOutputStream().write(b.toInt())
                        header.toString(Charsets.ISO_8859_1)
                    }
                }
            }
            val server = Thread.ofVirtual().start(served)
            try {
                val settings = HttpManagers.defaultPosixManagerSettings().copy(
                    proxyInsecure = HttpProxyOverrides.fromRequest, proxySecure = HttpProxyOverrides.fromRequest)
                val manager = HttpManagers.newPosixManager(settings)
                val request = HttpRequest(host = HttpBytes.pack("127.0.0.1"), port = listener.localPort.toLong())
                var previous: HttpConnection? = null
                repeat(2) { index ->
                    val managed = HttpManagers.getConnection(request, manager)
                    assertEquals(index == 1, managed.reused)
                    if (index == 1) assertSame(previous, managed.resource)
                    previous = managed.resource
                    HttpRequests.requestBuilder(request, managed.resource.write)?.invoke()
                    val response = HttpResponses.getResponse(4096, 100, null, request, HttpManagers.managedConnection(managed))
                    assertEquals("body", HttpBodies.consume(response.body).joinToString("") { it.latin1() }); response.close()
                }
                val final = HttpManagers.getConnection(request, manager)
                assertTrue(final.reused); assertSame(previous, final.resource); final.release(HttpReuse.DontReuse)
                val headers = served.get(5, TimeUnit.SECONDS)
                assertEquals(2, headers.size)
                headers.forEach { assertTrue(it.startsWith("GET / HTTP/1.1\r\n")); assertTrue(it.contains("Host: 127.0.0.1:${listener.localPort}\r\n")) }
            } finally { listener.close(); server.join(5000); assertFalse(server.isAlive) }
        }
    }

    @Test fun blockedNativeSendAllPreservesPartialProgressAndCanBeInterrupted() {
        assumeTrue(Platform.isMac() || Platform.isLinux())
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { listener ->
            listener.soTimeout = 5000
            val release = CountDownLatch(1)
            val peer = FutureTask<Int> {
                listener.accept().use { socket ->
                    socket.soTimeout = 5000; release.await(5, TimeUnit.SECONDS)
                    var total = 0; val buffer = ByteArray(8192)
                    while (true) { val n = socket.getInputStream().read(buffer); if (n < 0) break; total += n }
                    total
                }
            }
            val server = Thread.ofVirtual().start(peer)
            val io = NetworkSocketIo.posix
            val socket = io.create(io.addresses.family("AF_INET"), io.addresses.socketType("Stream"), 0)
            try {
                io.setIntOption(socket, "SendBuffer", 4096)
                io.connect(socket, NetworkSocketAddress.Inet(listener.localPort.toUShort(), NetworkHostAddresses.fromTuple(listOf(127u, 0u, 0u, 1u))))
                val writing = FutureTask { io.sendAll(socket, ByteArray(8 * 1024 * 1024) { 65 }) }
                val thread = Thread.ofVirtual().start(writing)
                awaitRegistered(socket, writing); thread.interrupt()
                val error = assertThrows(ExecutionException::class.java) { writing.get(5, TimeUnit.SECONDS) }
                assertInstanceOf(InterruptedException::class.java, error.cause)
                thread.join(5000); assertFalse(thread.isAlive)
            } finally { socket.close(); release.countDown() }
            try { assertTrue(peer.get(5, TimeUnit.SECONDS) in 1..<(8 * 1024 * 1024)) }
            finally { listener.close(); server.join(5000); assertFalse(server.isAlive) }
        }
    }
    private fun awaitRegistered(socket: NetworkSocketHandle, task: FutureTask<*>) {
        val limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (GhcEventRuntime.system.snapshot(socket.unsafeFd).isNullOrEmpty()) {
            assertFalse(task.isDone, "IO finished before an event registration became visible")
            assertTrue(System.nanoTime() < limit, "event registration did not become visible")
            Thread.sleep(1)
        }
    }
    @Test fun invalidNativeRegistrationFailsBeforeEnteringTheEventPoll() {
        assumeTrue(Platform.isMac() || Platform.isLinux())
        val abi = GhcEventAbi.currentPosix; var polls = 0
        val calls = NetworkSocketCalls { name, arguments ->
            if (name == "epoll_wait" || name == "kevent" && (arguments[4] as Int) > 0) polls++
            NetworkPosixSocketCalls.call(name, arguments)
        }
        val manager = GhcEventManager(GhcNativeEventBackend(abi, calls), GhcNativeEventControl(abi, calls), abi.haveOneShot, abi.kqueue)
        try {
            val socket = NetworkSocketHandle(-1, NetworkPosixSocketCalls)
            assertThrows(GhcIOException::class.java) { NetworkPosixSocketWaiter(manager).await(socket, -1, false) }
            assertEquals(0, polls)
        } finally { manager.cleanup() }
    }
}
