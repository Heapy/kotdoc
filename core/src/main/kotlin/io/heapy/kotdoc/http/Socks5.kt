// crypton-socks 0.6.2, Copyright (c) 2010-2019 Vincent Hanquez.
// BSD-3-Clause; complete original sources and notice retained in vendor/ and licenses/.
// Incremental byte-demand behavior: cereal 0.5.8.3, (c) Lennart Kolmodin,
// Galois, Inc.; licenses/cereal-BSD-3-Clause.txt.
package io.heapy.kotdoc.http

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SocksProtocolError(override val message: String): RuntimeException(message)

sealed class SocksMethod(val code: UByte, private val constructor: String, private val rank: Int): Comparable<SocksMethod> {
    data object None: SocksMethod(0u, "SocksMethodNone", 0)
    data object GSSAPI: SocksMethod(1u, "SocksMethodGSSAPI", 1)
    data object UsernamePassword: SocksMethod(2u, "SocksMethodUsernamePassword", 2)
    data class Other(val value: UByte): SocksMethod(value, "SocksMethodOther", 3)
    data object NotAcceptable: SocksMethod(255u, "SocksMethodNotAcceptable", 4)
    fun show() = constructor + if (this is Other) " $value" else ""
    override fun compareTo(other: SocksMethod) = rank.compareTo(other.rank).takeIf { it != 0 } ?: code.compareTo(other.code)
    companion object {
        fun fromEnum(n: Long): SocksMethod = when (n) {
            0L -> None; 1L -> GSSAPI; 2L -> UsernamePassword; 255L -> NotAcceptable
            else -> if (n < 256) Other(n.toUByte()) else throw SocksProtocolError("socks method is only 8 bits")
        }
    }
}
sealed class SocksCommand(val code: UByte, private val constructor: String, private val rank: Int): Comparable<SocksCommand> {
    data object Connect: SocksCommand(1u, "SocksCommandConnect", 0)
    data object Bind: SocksCommand(2u, "SocksCommandBind", 1)
    data object UdpAssociate: SocksCommand(3u, "SocksCommandUdpAssociate", 2)
    data class Other(val value: UByte): SocksCommand(value, "SocksCommandOther", 3)
    fun show() = constructor + if (this is Other) " $value" else ""
    override fun compareTo(other: SocksCommand) = rank.compareTo(other.rank).takeIf { it != 0 } ?: code.compareTo(other.code)
    companion object {
        fun fromEnum(n: Long): SocksCommand = when (n) {
            1L -> Connect; 2L -> Bind; 3L -> UdpAssociate
            else -> if (n < 256) Other(n.toUByte()) else throw SocksProtocolError("socks command is only 8 bits")
        }
    }
}
sealed class SocksError(val code: UByte, private val constructor: String, private val rank: Int): RuntimeException(), HttpExceptionShow, Comparable<SocksError> {
    data object GeneralServerFailure: SocksError(1u, "SocksErrorGeneralServerFailure", 0)
    data object ConnectionNotAllowedByRule: SocksError(2u, "SocksErrorConnectionNotAllowedByRule", 1)
    data object NetworkUnreachable: SocksError(3u, "SocksErrorNetworkUnreachable", 2)
    data object HostUnreachable: SocksError(4u, "SocksErrorHostUnreachable", 3)
    data object ConnectionRefused: SocksError(5u, "SocksErrorConnectionRefused", 4)
    data object TTLExpired: SocksError(6u, "SocksErrorTTLExpired", 5)
    data object CommandNotSupported: SocksError(7u, "SocksErrorCommandNotSupported", 6)
    data object AddrTypeNotSupported: SocksError(8u, "SocksErrorAddrTypeNotSupported", 7)
    data class Other(val value: UByte): SocksError(value, "SocksErrorOther", 8)
    override fun showsPrec(precedence: Int): String {
        val s = constructor + if (this is Other) " $value" else ""
        return if (this is Other && precedence > 10) "($s)" else s
    }
    override val message get() = showsPrec(0)
    override fun compareTo(other: SocksError) = rank.compareTo(other.rank).takeIf { it != 0 } ?: code.compareTo(other.code)
    companion object {
        fun fromEnum(n: Long): SocksError = when (n) {
            1L -> GeneralServerFailure; 2L -> ConnectionNotAllowedByRule; 3L -> NetworkUnreachable
            4L -> HostUnreachable; 5L -> ConnectionRefused; 6L -> TTLExpired
            7L -> CommandNotSupported; 8L -> AddrTypeNotSupported; else -> Other(n.toUByte())
        }
    }
}
sealed interface SocksReply {
    data object Success: SocksReply
    data class Error(val error: SocksError): SocksReply
    val code: UByte get() = when (this) { Success -> 0u; is Error -> error.code }
    fun show() = when (this) { Success -> "SocksReplySuccess"; is Error -> "SocksReplyError ${error.showsPrec(11)}" }
    companion object { fun fromEnum(n: Long): SocksReply = if (n == 0L) Success else Error(SocksError.fromEnum(n)) }
}
sealed interface SocksHostAddress {
    data class IPv4(val host: UInt): SocksHostAddress
    data class Domain(val name: HttpBytes): SocksHostAddress
    data class IPv6(val host: NetworkHostAddress6): SocksHostAddress
    fun show() = when (this) {
        // This Show implementation divides the numeric HostAddress into octets,
        // independently of the wire's native-endian Word32 representation.
        is IPv4 -> "SocksAddrIPV4(${NetworkHostAddresses.show4(host, true)})"
        is Domain -> "SocksAddrDomainName(${name.show()})"
        is IPv6 -> "SocksAddrIPV6(${host.tuple().joinToString(":") { it.toString(16) }})"
    }
}
data class SocksAddress(val host: SocksHostAddress, val port: UShort)
data class SocksRequest(val command: SocksCommand, val host: SocksHostAddress, val port: UShort)
data class SocksResponse(val reply: SocksReply, val host: SocksHostAddress, val port: UShort)
sealed interface SocksRpcResult {
    data class Success(val address: SocksAddress): SocksRpcResult
    data class Error(val error: SocksError): SocksRpcResult
}

/** Original Wire serialization and Command.runGetDone consumption policy. Native
 * Word32 order is intentional, including for each IPv6 word; port order is BE. */
object Socks5 {
    fun encodeHello(methods: List<SocksMethod>) = HttpBytes(byteArrayOf(5, methods.size.toByte()) + methods.map { it.code.toByte() }.toByteArray())
    fun encodeHelloResponse(method: SocksMethod) = HttpBytes(byteArrayOf(5, method.code.toByte()))
    fun encodeRequest(request: SocksRequest) = encodeAddress(request.command.code, request.host, request.port)
    fun encodeResponse(response: SocksResponse) = encodeAddress(response.reply.code, response.host, response.port)
    private fun encodeAddress(code: UByte, host: SocksHostAddress, port: UShort): HttpBytes {
        val bytes = ByteArrayOutputStream()
        bytes.write(byteArrayOf(5, code.toByte(), 0))
        fun word(n: UInt) { bytes.write(ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(n.toInt()).array()) }
        when (host) {
            is SocksHostAddress.IPv4 -> { bytes.write(1); word(host.host) }
            is SocksHostAddress.Domain -> { bytes.write(3); bytes.write(host.name.size); bytes.write(host.name.bytes()) }
            is SocksHostAddress.IPv6 -> { bytes.write(4); host.host.words().forEach(::word) }
        }
        bytes.write(port.toInt() shr 8); bytes.write(port.toInt() and 255)
        return HttpBytes(bytes.toByteArray())
    }
    fun receiveHello(socket: ConnectionSocket): List<SocksMethod> = Reader(socket).read {
        if (byte() != 5) throw SocksProtocolError("unsupported sock hello version")
        List(byte()) { SocksMethod.fromEnum(byte().toLong()) }
    }
    fun receiveHelloResponse(socket: ConnectionSocket): SocksMethod = Reader(socket).read {
        if (byte() != 5) throw SocksProtocolError("unsupported sock hello response version")
        SocksMethod.fromEnum(byte().toLong())
    }
    fun receiveRequest(socket: ConnectionSocket): SocksRequest = Reader(socket).read {
        val version = byte()
        if (version != 5) throw SocksProtocolError("unsupported version of the protocol $version")
        val command = SocksCommand.fromEnum(byte().toLong()); byte()
        val host = address(); val port = port()
        SocksRequest(command, host, port)
    }
    fun receiveResponse(socket: ConnectionSocket): SocksResponse = Reader(socket).read {
        val version = byte()
        if (version != 5) throw SocksProtocolError("unsupported version of the protocol $version")
        val reply = SocksReply.fromEnum(byte().toLong()); byte()
        val host = address(); val port = port()
        SocksResponse(reply, host, port)
    }
    fun establish(socket: ConnectionSocket, methods: List<SocksMethod>): SocksMethod {
        socket.sendAll(encodeHello(methods))
        return receiveHelloResponse(socket)
    }
    fun rpc(socket: ConnectionSocket, request: SocksRequest): SocksRpcResult {
        socket.sendAll(encodeRequest(request))
        val response = receiveResponse(socket)
        return when (val reply = response.reply) {
            SocksReply.Success -> SocksRpcResult.Success(SocksAddress(response.host, response.port))
            is SocksReply.Error -> SocksRpcResult.Error(reply.error)
        }
    }
    fun rpcOrThrow(socket: ConnectionSocket, request: SocksRequest): SocksAddress {
        when (val reply = rpc(socket, request)) {
            is SocksRpcResult.Success -> return reply.address
            is SocksRpcResult.Error -> throw reply.error
        }
    }
    fun connectWithSocket(socket: ConnectionSocket, destination: SocksAddress): SocksAddress {
        val method = establish(socket, listOf(SocksMethod.None))
        if (method == SocksMethod.NotAcceptable) throw SocksProtocolError("cannot connect with no socks method of authentication")
        return rpcOrThrow(socket, SocksRequest(SocksCommand.Connect, destination.host, destination.port))
    }
    fun listen(socket: ConnectionSocket): SocksRequest {
        receiveHello(socket)
        socket.sendAll(encodeHelloResponse(SocksMethod.None))
        return receiveRequest(socket)
    }
    private class Reader(private val socket: ConnectionSocket) {
        private var data = socket.receive(4096).bytes()
        private var offset = 0
        fun take(n: Int): ByteArray {
            while (data.size - offset < n) {
                val next = socket.receive(4096).bytes()
                if (next.isEmpty()) throw SocksProtocolError("too few bytes\nFrom:\tdemandInput\n\n")
                data = data.copyOfRange(offset, data.size) + next; offset = 0
            }
            val result = data.copyOfRange(offset, offset + n); offset += n; return result
        }
        fun byte() = take(1)[0].toInt() and 255
        fun port() = ((byte() shl 8) or byte()).toUShort()
        fun word() = ByteBuffer.wrap(take(4)).order(ByteOrder.nativeOrder()).int.toUInt()
        fun address(): SocksHostAddress = when (val type = byte()) {
            1 -> SocksHostAddress.IPv4(word())
            3 -> SocksHostAddress.Domain(HttpBytes(take(byte())))
            4 -> SocksHostAddress.IPv6(NetworkHostAddress6(word(), word(), word(), word()))
            else -> throw SocksProtocolError("cannot get unknown socket address type: $type")
        }
        fun <A> read(action: Reader.() -> A): A {
            val result = action()
            if (offset != data.size) throw SocksProtocolError("got too many bytes while receiving data")
            return result
        }
    }
}
