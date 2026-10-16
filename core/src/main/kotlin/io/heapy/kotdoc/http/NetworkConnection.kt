// Network.Connection, crypton-connection 0.4.6, (c) Vincent Hanquez.
// BSD-3-Clause; complete source and notice retained in vendor/ and licenses/.
package io.heapy.kotdoc.http

import io.heapy.kotdoc.errors.GhcIOException
import io.heapy.kotdoc.errors.GhcIoError
import io.heapy.kotdoc.errors.IoErrorType
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.Semaphore

/** Byte-oriented handle effects; reading must not decode characters. */
interface ConnectionStream {
    fun getSome(maximum: Int): HttpBytes
    fun put(bytes: HttpBytes)
    fun flush()
    fun close()
}
interface ConnectionSocket {
    fun receive(maximum: Int): HttpBytes
    fun sendAll(bytes: HttpBytes)
    fun close()
}
/** Implemented by the TLS engine, not by the buffered connection layer. */
interface ConnectionTls {
    fun handshake()
    fun receive(): HttpBytes
    fun send(bytes: HttpBytes)
    fun bye()
    fun close()
}
sealed interface ConnectionBackend {
    data class Stream(val stream: ConnectionStream): ConnectionBackend
    data class Socket(val socket: ConnectionSocket): ConnectionBackend
    data class Tls(val tls: ConnectionTls): ConnectionBackend
}
data class ConnectionId(val host: CharacterString, val port: UShort)

/** Native socket effects already ported from Network.Socket. */
class PosixConnectionSocket(val handle: NetworkSocketHandle,
    private val io: NetworkSocketIo = NetworkSocketIo.posix): ConnectionSocket {
    override fun receive(maximum: Int) = io.receive(handle, maximum.toLong())
    override fun sendAll(bytes: HttpBytes) = io.sendAll(handle, bytes.bytes())
    override fun close() = handle.close()
}

/** The TLS implementation supplies parameter construction and contextNew. */
fun interface ConnectionTlsFactory<C, T> {
    fun contextNew(context: Lazy<C>, id: ConnectionId, settings: T, backend: ConnectionBackend): ConnectionTls
}

object NetworkConnections {
    fun <C, T> connectFromHandle(context: Lazy<C>, stream: ConnectionStream, params: HttpTlsConnectionParams<T>,
        factory: ConnectionTlsFactory<C, T>): NetworkConnection =
        connectFromBackend(context, ConnectionBackend.Stream(stream), params, factory)

    fun <C, T> connectFromSocket(context: Lazy<C>, socket: ConnectionSocket, params: HttpTlsConnectionParams<T>,
        factory: ConnectionTlsFactory<C, T>): NetworkConnection =
        connectFromBackend(context, ConnectionBackend.Socket(socket), params, factory)

    private fun <C, T> connectFromBackend(context: Lazy<C>, raw: ConnectionBackend, params: HttpTlsConnectionParams<T>,
        factory: ConnectionTlsFactory<C, T>): NetworkConnection {
        val id = ConnectionId(params.host, params.port)
        // Existing handles ignore SOCKS. Plain connections never force the context.
        val backend = params.secure?.let { ConnectionBackend.Tls(establish(context, id, it, raw, factory)) } ?: raw
        return NetworkConnection(id, backend)
    }

    internal fun <C, T> establish(context: Lazy<C>, id: ConnectionId, settings: T, backend: ConnectionBackend,
        factory: ConnectionTlsFactory<C, T>): ConnectionTls {
        val tls = factory.contextNew(context, id, settings, backend)
        tls.handshake()
        // The original does not close the context here if handshake throws.
        return tls
    }
}

/** The callback's pair, result and remainder are separate source evaluation boundaries.
 * On EOF the remainder is never evaluated; a result is evaluated after buffer commit. */
class ConnectionChunk<A>(val result: () -> A, val remainder: () -> HttpBytes)

/** System.Timeout boundary: true on completion, false on timeout. The runtime owns
 * asynchronous cancellation and must finish cancellation before returning false. */
fun interface ConnectionTimeout {
    fun run(microseconds: Long, action: () -> Unit): Boolean
}

/** Port of the source connection buffer and backend lifecycle. Destination resolution,
 * SOCKS and TLS context creation/handshake remain separate effects. */
class NetworkConnection(val id: ConnectionId, backend: ConnectionBackend) {
    private val backendLock = Semaphore(1, true)
    private val bufferLock = Semaphore(1, true)
    private var backend = backend
    private var buffer: HttpBytes? = HttpBytes.empty

    // readMVar releases the backend cell before running the backend IO.
    private fun snapshot(): ConnectionBackend = locked(backendLock) { backend }

    fun put(bytes: HttpBytes) {
        when (val b = snapshot()) {
            is ConnectionBackend.Stream -> { b.stream.put(bytes); b.stream.flush() }
            is ConnectionBackend.Socket -> b.socket.sendAll(bytes)
            is ConnectionBackend.Tls -> b.tls.send(bytes)
        }
    }

    fun get(size: Long): HttpBytes {
        if (size < 0) throw GhcIOException(GhcIoError.userError("Network.Connection.connectionGet: size < 0"))
        if (size == 0L) return HttpBytes.empty
        return chunkBase("connectionGet") { bytes ->
            val data = bytes.bytes()
            val n = minOf(size, data.size.toLong()).toInt()
            ConnectionChunk({ HttpBytes(data.copyOfRange(0, n)) }, { HttpBytes(data.copyOfRange(n, data.size)) })
        }
    }

    fun getExact(size: Long): HttpBytes {
        val output = ByteArrayOutputStream()
        var count = 0L
        while (count != size) {
            val next = get(size - count)
            output.write(next.bytes())
            count += next.size
        }
        return HttpBytes(output.toByteArray())
    }

    fun getChunk(): HttpBytes = chunkBase("connectionGetChunk") { bytes ->
        ConnectionChunk({ bytes }, { HttpBytes.empty })
    }

    fun <A> getChunkWith(split: (HttpBytes) -> ConnectionChunk<A>): A = chunkBase("connectionGetChunk'", split)

    fun waitForInput(milliseconds: Long, timeout: ConnectionTimeout): Boolean =
        timeout.run(milliseconds * 1000L) {
            chunkBase("connectionWaitForInput") { bytes -> ConnectionChunk({ Unit }, { bytes }) }
        }

    private fun <A> chunkBase(location: String, split: (HttpBytes) -> ConnectionChunk<A>): A {
        val result = locked(bufferLock) {
            val old = buffer ?: eof(location)
            val bytes = if (old.size == 0) when (val b = snapshot()) {
                is ConnectionBackend.Stream -> b.stream.getSome(16 * 1024)
                is ConnectionBackend.Socket -> b.socket.receive(1500)
                is ConnectionBackend.Tls -> b.tls.receive()
            } else old
            val pair = split(bytes)
            // Assign only after the pair/remainder succeeds: modifyMVar restores
            // the old cell on failure, but cannot undo an underlying read.
            val next = if (bytes.size == 0) null else pair.remainder()
            buffer = next
            pair.result
        }
        return result()
    }

    fun getLine(limit: Long): HttpBytes {
        val parts = ByteArrayOutputStream()
        var count = 0L
        while (true) {
            // The source returns an IO continuation and joins it after releasing
            // the buffer cell. In particular LineTooLong consumes the last chunk.
            val step = chunkBase("connectionGetLine") { bytes ->
                val data = bytes.bytes()
                val lf = data.indexOf(10.toByte())
                when {
                    data.isEmpty() -> ConnectionChunk({ LineStep.End }, { HttpBytes.empty })
                    lf < 0 -> ConnectionChunk({ LineStep.More(data) }, { HttpBytes.empty })
                    else -> ConnectionChunk({ LineStep.Done(data.copyOfRange(0, lf)) },
                        { HttpBytes(data.copyOfRange(lf + 1, data.size)) })
                }
            }
            when (step) {
                LineStep.End -> {
                    return HttpBytes(parts.toByteArray())
                }
                is LineStep.Done -> { parts.write(step.bytes); return HttpBytes(parts.toByteArray()) }
                is LineStep.More -> {
                    count += step.bytes.size
                    if (count > limit) throw ConnectionLineTooLong()
                    parts.write(step.bytes)
                }
            }
        }
    }
    private sealed interface LineStep {
        data object End: LineStep
        data class More(val bytes: ByteArray): LineStep
        data class Done(val bytes: ByteArray): LineStep
    }

    fun close() {
        when (val b = snapshot()) {
            is ConnectionBackend.Stream -> b.stream.close()
            is ConnectionBackend.Socket -> b.socket.close()
            is ConnectionBackend.Tls -> try {
                try { b.tls.bye() } catch (_: IOException) { }
            } finally { b.tls.close() }
        }
    }

    /** establish includes contextNew and handshake. Failed upgrades restore both
     * cells; successful upgrades discard pre-TLS bytes, including the EOF marker. */
    fun setSecure(establish: (ConnectionBackend) -> ConnectionTls) = locked(bufferLock) {
        locked(backendLock) {
            if (backend !is ConnectionBackend.Tls) {
                val tls = establish(backend)
                backend = ConnectionBackend.Tls(tls)
                buffer = HttpBytes.empty
            }
        }
    }
    fun <C, T> setSecure(context: Lazy<C>, settings: T, factory: ConnectionTlsFactory<C, T>) =
        setSecure { backend -> NetworkConnections.establish(context, id, settings, backend, factory) }
    fun isSecure(): Boolean = snapshot() is ConnectionBackend.Tls

    private fun eof(location: String): Nothing = throw GhcIOException(GhcIoError(
        IoErrorType.EOF, location = Chars.fromString("Network.Connection.$location"),
        filename = Chars.concat(listOf(id.host, Chars.fromString(":" + id.port.toString())))))

    private inline fun <A> locked(lock: Semaphore, action: () -> A): A {
        lock.acquire()
        try { return action() } finally { lock.release() }
    }
}

/** Network.Connection.connectionSessionManager. resumeOnlyOnce is one atomic
 * lookup/delete; establishment returns no ticket and sessionUseTicket is false. */
class ConnectionSessionManager<S: Any> {
    private val sessions = HashMap<HttpBytes, S>()
    val useTicket = false
    fun resume(id: HttpBytes): S? = synchronized(sessions) { sessions[id] }
    fun establish(id: HttpBytes, session: S): Nothing? = synchronized(sessions) { sessions[id] = session; null }
    fun invalidate(id: HttpBytes) { synchronized(sessions) { sessions.remove(id) } }
    fun resumeOnlyOnce(id: HttpBytes): S? = synchronized(sessions) { sessions.remove(id) }
}
