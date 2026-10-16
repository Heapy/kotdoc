// Network.Socket 3.2.9.0 Types/Syscall/Buffer/ByteString.IO/Options/Name,
// University of Glasgow and Johan Tibell; licenses/Network-BSD-3-Clause.txt.
// GHC 9.12.2 errno guards/Posix.Internals; licenses/GHC-base-BSD-3-Clause.txt.
package io.heapy.kotdoc.http

import com.sun.jna.Memory
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.NativeLong
import com.sun.jna.Platform
import com.sun.jna.Pointer
import io.heapy.kotdoc.errors.GhcIOException
import io.heapy.kotdoc.errors.GhcIoError
import io.heapy.kotdoc.errors.GhcPosixErrno
import io.heapy.kotdoc.errors.IoErrorType
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import kotlinx.serialization.json.*
import java.lang.ref.Cleaner
import java.lang.ref.Reference
import java.util.concurrent.atomic.AtomicInteger

class NetworkSocketIoAbi(val data: JsonObject) {
    fun int(name: String) = data.getValue(name).jsonPrimitive.int
    fun option(name: String): Pair<Int, Int> = data.getValue("options").jsonObject.getValue(name).jsonArray.let {
        it[0].jsonPrimitive.int to it[1].jsonPrimitive.int
    }
    companion object {
        fun load(platform: String): NetworkSocketIoAbi = checkNotNull(NetworkSocketIoAbi::class.java.getResourceAsStream("/io/heapy/kotdoc/http/network-io-abi-$platform.json"))
            .bufferedReader().use { NetworkSocketIoAbi(Json.parseToJsonElement(it.readText()).jsonObject) }
        val currentPosix by lazy {
            NetworkSocketAbi.currentPosix // Verify the supported pointer width and byte order first.
            load(if (Platform.isMac()) "macos-64" else "linux-64")
        }
    }
}

/** Capture errno immediately at the native call boundary. */
data class NetworkNativeResult(val value: Long, val errno: Int)
fun interface NetworkSocketCalls {
    fun call(name: String, arguments: Array<out Any?>): NetworkNativeResult
}
object NetworkPosixSocketCalls : NetworkSocketCalls {
    private interface Fcntl : Library { fun fcntl(fd: Int, command: Int, vararg arguments: Any): Int }
    private val fcntl by lazy { Native.load(Platform.C_LIBRARY_NAME, Fcntl::class.java) }
    private val libc by lazy { NativeLibrary.getInstance(Platform.C_LIBRARY_NAME) }
    override fun call(name: String, arguments: Array<out Any?>): NetworkNativeResult {
        // Darwin/arm64 passes variadic arguments on the stack. A fixed-arity
        // libffi call silently loses F_SETFL's flags even when it returns 0.
        val result = if (name == "fcntl") fcntl.fcntl(arguments[0] as Int, arguments[1] as Int, arguments[2] as Int).toLong() else {
            val function = libc.getFunction(name)
            if (name == "send" || name == "recv") function.invokeLong(arguments) else function.invokeInt(arguments).toLong()
        }
        return NetworkNativeResult(result, Native.getLastError())
    }
}

/** Socket equality is owner identity; Show retains the original descriptor
 * even after close. Explicit close invalidates atomically and ignores errors. */
class NetworkSocketHandle internal constructor(fd: Int, private val calls: NetworkSocketCalls) {
    private class Owner(fd: Int, val calls: NetworkSocketCalls) : Runnable {
        val fd = AtomicInteger(fd)
        override fun run() { val old = fd.getAndSet(-1); if (old != -1) calls.call("close", arrayOf(old)) }
    }
    private val original = fd
    private val owner = Owner(fd, calls)
    private val cleanable = cleaner.register(this, owner)
    val unsafeFd get() = owner.fd.get()
    fun <A> withFd(action: (Int) -> A): A = try { action(owner.fd.get()) } finally { Reference.reachabilityFence(this) }
    fun close() = cleanable.clean()
    override fun toString() = "<socket: $original>"
    companion object { private val cleaner = Cleaner.create() }
}

fun interface NetworkSocketWaiter {
    fun await(socket: NetworkSocketHandle, fd: Int, write: Boolean)
}

/** poll is the JVM readiness boundary. Readiness wakes immediately; bounded
 * waits also observe Java interruption and owner invalidation without taking
 * ownership of the descriptor or changing it back to blocking mode. */
class NetworkPosixSocketWaiter(private val abi: NetworkSocketIoAbi, private val calls: NetworkSocketCalls) : NetworkSocketWaiter {
    override fun await(socket: NetworkSocketHandle, fd: Int, write: Boolean) {
        // poll silently ignores negative entries. Reject at the JVM boundary;
        // exact GHC backend registration errors remain an explicitly tracked gap.
        if (fd < 0) throw GhcPosixErrno.exception("threadWait", abi.int("EBADF"))
        Memory(abi.int("pollSize").toLong()).use { p ->
            p.clear(); p.setInt(abi.int("pollFd").toLong(), fd)
            p.setShort(abi.int("pollEvents").toLong(), abi.int(if (write) "POLLOUT" else "POLLIN").toShort())
            while (true) {
                if (Thread.interrupted()) throw InterruptedException()
                if (socket.unsafeFd != fd) throw GhcPosixErrno.exception("threadWait", abi.int("EBADF"))
                val r = calls.call("poll", arrayOf(p, NativeLong(1), 20))
                if (Thread.interrupted()) throw InterruptedException()
                if (socket.unsafeFd != fd) throw GhcPosixErrno.exception("threadWait", abi.int("EBADF"))
                if (r.value == -1L) {
                    if (r.errno != abi.int("EINTR")) throw GhcPosixErrno.exception("threadWait", r.errno)
                } else if (r.value > 0) {
                    if (p.getShort(abi.int("pollRevents").toLong()).toInt() and abi.int("POLLNVAL") != 0)
                        throw GhcPosixErrno.exception("threadWait", abi.int("EBADF"))
                    return // HUP/ERR are readiness too: retry the actual syscall for its result.
                }
            }
        }
    }
}

class NetworkSocketIo(
    val addresses: NetworkSocketAbi,
    val abi: NetworkSocketIoAbi,
    private val calls: NetworkSocketCalls,
    waiter: NetworkSocketWaiter? = null,
) {
    private val readiness = waiter ?: NetworkPosixSocketWaiter(abi, calls)
    private val memory = NetworkSocketMemory(addresses)
    /** Original throwErrnoIfMinus1Retry[MayBlock], preserving retry order. */
    fun retry(location: String, onBlock: (() -> Unit)? = null, action: () -> NetworkNativeResult): Long {
        while (true) {
            if (Thread.interrupted()) throw InterruptedException()
            val result = action()
            if (result.value != -1L) return result.value
            if (result.errno == abi.int("EINTR")) continue
            if (onBlock != null && (result.errno == abi.int("EAGAIN") || result.errno == abi.int("EWOULDBLOCK"))) { onBlock(); continue }
            throw GhcPosixErrno.exception(location, result.errno)
        }
    }
    internal fun checked(location: String, action: () -> NetworkNativeResult): Long {
        val result = action()
        if (result.value == -1L) throw GhcPosixErrno.exception(location, result.errno)
        return result.value
    }
    private fun once(location: String, name: String, vararg args: Any?) = checked(location) { calls.call(name, args) }
    fun create(family: NetworkSocketFamily, type: NetworkSocketType, protocol: Int): NetworkSocketHandle {
        val fd = retry("Network.Socket.socket") { calls.call("socket", arrayOf(family.value, type.value or abi.int("SOCK_NONBLOCK"), protocol)) }.toInt()
        var socket: NetworkSocketHandle? = null
        try {
            if (abi.int("SOCK_NONBLOCK") == 0) {
                val flags = retry("setNonBlockingFD") { calls.call("fcntl", arrayOf(fd, abi.int("F_GETFL"), 0)) }.toInt()
                val updated = flags or abi.int("O_NONBLOCK")
                // Original Posix.Internals deliberately ignores a failed flag write.
                if (flags != updated) calls.call("fcntl", arrayOf(fd, abi.int("F_SETFL"), updated))
            }
            socket = NetworkSocketHandle(fd, calls)
            if (family == addresses.family("AF_INET6") && type in listOf(addresses.socketType("Stream"), addresses.socketType("Datagram")) && abi.option("IPv6Only") != (-1 to -1))
                setIntOption(socket, "IPv6Only", 0)
            if (family == addresses.family("AF_INET") && abi.option("DontFragment") != (-1 to -1)) setIntOption(socket, "DontFragment", 1)
            return socket
        } catch (e: Throwable) {
            if (socket == null) calls.call("close", arrayOf(fd)) else socket.close()
            throw e
        }
    }
    fun setIntOption(socket: NetworkSocketHandle, option: String, value: Int) = Memory(4).use { p ->
        p.setInt(0, value); val (level, name) = abi.option(option)
        socket.withFd { once("Network.Socket.setSockOpt", "setsockopt", it, level, name, p, 4) }; Unit
    }
    fun getIntOption(socket: NetworkSocketHandle, option: String): Int = Memory(4).use { p -> Memory(4).use { size ->
        size.setInt(0, 4); val (level, name) = abi.option(option)
        socket.withFd { fd -> retry("Network.Socket.getSockOpt") { calls.call("getsockopt", arrayOf(fd, level, name, p, size)) } }
        p.getInt(0)
    } }
    private fun <A> withAddress(address: NetworkSocketAddress, action: (Pointer, Int) -> A): A = Memory(memory.size(address).toLong()).use { p ->
        memory.poke(p, address); action(p, memory.size(address))
    }
    fun connect(socket: NetworkSocketHandle, address: NetworkSocketAddress) = withAddress(address) { p, size -> socket.withFd { fd ->
        val location = "Network.Socket.connect: $socket"
        while (true) {
            if (Thread.interrupted()) throw InterruptedException()
            val r = calls.call("connect", arrayOf(fd, p, size))
            if (r.value != -1L) break
            if (r.errno == abi.int("EINTR")) continue
            if (r.errno != abi.int("EINPROGRESS")) throw GhcPosixErrno.exception(location, r.errno)
            socket.withFd { readiness.await(socket, it, true) }
            val error = getIntOption(socket, "SoError")
            if (error != 0) throw GhcPosixErrno.exception(location, error)
            break
        }
    } }
    fun bind(socket: NetworkSocketHandle, address: NetworkSocketAddress) = withAddress(address) { p, size -> socket.withFd { fd ->
        retry("Network.Socket.bind") { calls.call("bind", arrayOf(fd, p, size)) }; Unit
    } }
    fun listen(socket: NetworkSocketHandle, backlog: Int) { socket.withFd { fd -> retry("Network.Socket.listen") { calls.call("listen", arrayOf(fd, backlog)) } } }
    private fun name(socket: NetworkSocketHandle, peer: Boolean): NetworkSocketAddress = Memory(abi.int("sockaddrStorageSize").toLong()).use { p -> Memory(4).use { size ->
        size.setInt(0, p.size().toInt()); val call = if (peer) "getpeername" else "getsockname"
        socket.withFd { fd -> retry(if (peer) "Network.Socket.getPeerName" else "Network.Socket.getSocketName") { calls.call(call, arrayOf(fd, p, size)) } }
        memory.peek(p)
    } }
    fun socketName(socket: NetworkSocketHandle) = name(socket, false)
    fun peerName(socket: NetworkSocketHandle) = name(socket, true)
    fun shutdown(socket: NetworkSocketHandle, command: Int) { socket.withFd { fd -> retry("Network.Socket.shutdown") { calls.call("shutdown", arrayOf(fd, command)) } } }
    private fun sendBuffer(socket: NetworkSocketHandle, p: Pointer, size: Int): Int =
        socket.withFd { fd -> retry("Network.Socket.sendBuf", { socket.withFd { readiness.await(socket, it, true) } }) {
            calls.call("send", arrayOf(fd, p, NativeLong(size.toLong()), 0))
        } }.toInt()
    fun send(socket: NetworkSocketHandle, data: ByteArray): Int = Memory(maxOf(1, data.size).toLong()).use { p ->
        if (data.isNotEmpty()) p.write(0, data, 0, data.size)
        sendBuffer(socket, p, data.size)
    }
    fun sendAll(socket: NetworkSocketHandle, data: ByteArray) {
        if (data.isEmpty()) return
        Memory(data.size.toLong()).use { p ->
            p.write(0, data, 0, data.size)
            var offset = 0
            do {
                val count = sendBuffer(socket, p.share(offset.toLong()), data.size - offset)
                if (count == 0) socket.withFd { readiness.await(socket, it, true) }
                if (count == data.size - offset) return
                offset = (offset.toLong() + maxOf(0, count)).coerceAtMost(data.size.toLong()).toInt() // ByteString.drop clamps both bounds.
            } while (true)
        }
    }
    fun receive(socket: NetworkSocketHandle, size: Long): HttpBytes {
        if (size < 0) throw invalidReceive("Network.Socket.ByteString.recv")
        if (size == 0L) throw invalidReceive("Network.Socket.recvBuf")
        // ByteString/JVM arrays have distinct allocation limits; oversized native
        // allocations are not silently truncated to a different receive length.
        require(size <= Int.MAX_VALUE) { "ByteString allocation exceeds JVM array capacity: $size" }
        return Memory(size).use { p ->
            val count = socket.withFd { fd -> retry("Network.Socket.recvBuf", { socket.withFd { readiness.await(socket, it, false) } }) {
                calls.call("recv", arrayOf(fd, p, NativeLong(size), 0))
            } }.toInt()
            HttpBytes(p.getByteArray(0, count))
        }
    }
    private fun invalidReceive(location: String) = GhcIOException(GhcIoError(IoErrorType.InvalidArgument, Chars.fromString(location), Chars.fromString("non-positive length")))
    companion object {
        val posix by lazy { NetworkSocketIo(NetworkSocketAbi.currentPosix, NetworkSocketIoAbi.currentPosix, NetworkPosixSocketCalls) }
    }
}

class HttpPosixSocketBackend(
    private val io: NetworkSocketIo = NetworkSocketIo.posix,
    private val resolver: NetworkNameResolver = NetworkNameResolver.posix(),
) : HttpSocketBackend<NetworkSocketHandle, NetworkAddressInfo> {
    override fun resolve(host: String, service: String) = resolver.getAddrInfo(
        NetworkAddressInfo.defaultHints(io.addresses).copy(socketType = io.addresses.socketType("Stream")), Chars.fromString(host), Chars.fromString(service))
    override fun ipv4(port: UShort, address: UInt) = NetworkAddressInfo(emptyList(), io.addresses.family("AF_INET"), io.addresses.socketType("Stream"), 6, NetworkSocketAddress.Inet(port, address), null)
    override fun create(address: NetworkAddressInfo) = io.create(address.family, address.socketType, address.protocol)
    override fun noDelay(socket: NetworkSocketHandle, value: Int) = io.setIntOption(socket, "NoDelay", value)
    override fun connect(socket: NetworkSocketHandle, address: NetworkAddressInfo) = io.connect(socket, address.address)
    override fun close(socket: NetworkSocketHandle) = socket.close()
    override fun receive(socket: NetworkSocketHandle, chunkSize: Long) = io.receive(socket, chunkSize)
    override fun sendAll(socket: NetworkSocketHandle, bytes: HttpBytes) = io.sendAll(socket, bytes.bytes())
}
