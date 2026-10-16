// Network.Connection, crypton-connection 0.4.6, Copyright (c) Vincent Hanquez.
// BSD-3-Clause; complete original implementation and notice retained.
package io.heapy.kotdoc.http

import io.heapy.kotdoc.errors.GhcIOException
import io.heapy.kotdoc.formats.HaskellNumber
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import java.math.BigInteger

/** IO failures use the translated GHC IOException record (GhcIOException). */
interface ConnectionDialBackend<S: ConnectionSocket, A> {
    fun resolve(host: CharacterString, port: UShort): List<A>
    fun create(address: A): S
    fun connect(socket: S, address: A)
}
class PosixConnectionDialBackend(private val io: NetworkSocketIo = NetworkSocketIo.posix,
    private val resolver: NetworkNameResolver = NetworkNameResolver.posix()): ConnectionDialBackend<PosixConnectionSocket, NetworkAddressInfo> {
    override fun resolve(host: CharacterString, port: UShort) = resolver.getAddrInfo(
        NetworkAddressInfo.defaultHints(io.addresses).copy(flags = listOf(NetworkAddressFlag.AI_ADDRCONFIG), socketType = io.addresses.socketType("Stream")),
        host, Chars.fromString(port.toString()))
    override fun create(address: NetworkAddressInfo) = PosixConnectionSocket(io.create(address.family, address.socketType, address.protocol), io)
    override fun connect(socket: PosixConnectionSocket, address: NetworkAddressInfo) = io.connect(socket.handle, address.address)
}

object ConnectionSocksEnvironment {
    fun parse(value: CharacterString): HttpSocketProxy.Simple? {
        val points = value.points(); val colon = points.indexOf(58)
        if (colon < 0) return HttpSocketProxy.Simple(value, 1080u)
        val portText = Chars.build(CharacterString(points.copyOfRange(colon + 1, points.size)))
        // reads must return exactly one parse with an *empty* remainder. safeRead's
        // usual trailing-space tolerance would incorrectly accept "host:80 ".
        if (portText.isEmpty() || HaskellNumber.isSpace(portText.last())) return null
        val port = HaskellNumber.readInteger(portText) ?: return null
        // Network.Socket.PortNumber uses safeInt, not Word16's wrapping Read.
        if (port < BigInteger.ZERO || port > BigInteger.valueOf(65535)) return null
        return HttpSocketProxy.Simple(CharacterString(points.copyOfRange(0, colon)), port.toInt().toUShort())
    }
}

/** Original sequential acquisition and bracketOnError ownership. The environment
 * getter is the GHC getEnv effect, whose IO failures trigger direct connection. */
class NetworkConnectionDialer<C, T, S: ConnectionSocket, A>(private val backend: ConnectionDialBackend<S, A>,
    private val factory: ConnectionTlsFactory<C, T>, private val getEnvironment: (CharacterString) -> CharacterString) {
    fun connect(context: Lazy<C>, params: HttpTlsConnectionParams<T>): NetworkConnection {
        val socket = acquire(params.socks, params.host, params.port)
        try { return NetworkConnections.connectFromSocket(context, socket, params, factory) }
        catch (e: Throwable) { socket.close(); throw e }
    }
    private fun direct(host: CharacterString, port: UShort): S {
        val addresses = backend.resolve(host, port)
        val errors = mutableListOf<GhcIOException>()
        for (address in addresses) {
            try {
                val socket = backend.create(address)
                try { backend.connect(socket, address); return socket }
                catch (e: Throwable) { socket.close(); throw e }
            } catch (e: GhcIOException) { errors.add(e) }
        }
        if (errors.isEmpty()) throw ConnectionHostNotResolved(host)
        throw ConnectionHostCannotConnect(host, errors.asReversed())
    }
    private fun acquire(proxy: HttpSocketProxy?, host: CharacterString, port: UShort): S = when (proxy) {
        null -> direct(host, port)
        is HttpSocketProxy.Other -> direct(proxy.host, proxy.port)
        is HttpSocketProxy.Simple -> socks(proxy.host, proxy.port, host, port)
        is HttpSocketProxy.Environment -> {
            val value = try { getEnvironment(proxy.name ?: Chars.fromString("SOCKS_SERVER")) } catch (_: GhcIOException) { null }
            val parsed = value?.let(ConnectionSocksEnvironment::parse)
            if (parsed == null) direct(host, port) else socks(parsed.host, parsed.port, host, port)
        }
    }
    private fun socks(proxyHost: CharacterString, proxyPort: UShort, host: CharacterString, port: UShort): S {
        val socket = direct(proxyHost, proxyPort)
        val destination = Socks5.connectWithSocket(socket, SocksAddress(SocksHostAddress.Domain(HttpBytes.pack(host)), port))
        if (destination.host is SocksHostAddress.Domain)
            throw SocksProtocolError("internal error: socks connect return a resolved address as domain name")
        // Acquisition has not returned to bracketOnError until this point. The
        // source performs no explicit close if SOCKS negotiation itself fails.
        return socket
    }
}

/** Connects the original http-client-tls adapter to the translated connection
 * implementation. A real TLS engine/context initializer must still be supplied. */
class NetworkConnectionClient<C, T, S: ConnectionSocket, A>(override val defaultTlsSettings: T,
    private val initialize: () -> C, backend: ConnectionDialBackend<S, A>,
    private val factory: ConnectionTlsFactory<C, T>, environment: (CharacterString) -> CharacterString): HttpTlsConnectionBackend<C, NetworkConnection, T> {
    private val dialer = NetworkConnectionDialer(backend, factory, environment)
    override fun initializeContext() = initialize()
    override fun connect(context: Lazy<C>, params: HttpTlsConnectionParams<T>) = dialer.connect(context, params)
    override fun close(connection: NetworkConnection) = connection.close()
    override fun getChunk(connection: NetworkConnection) = connection.getChunk()
    override fun put(connection: NetworkConnection, bytes: HttpBytes) = connection.put(bytes)
    override fun setSecure(context: Lazy<C>, connection: NetworkConnection, settings: T) = connection.setSecure(context, settings, factory)
}
