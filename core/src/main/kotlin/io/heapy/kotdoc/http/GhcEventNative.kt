// GHC 9.12.2 Event.EPoll/KQueue/Control, retained with full GHC base BSD notice.
package io.heapy.kotdoc.http

import com.sun.jna.Memory
import com.sun.jna.NativeLong
import io.heapy.kotdoc.errors.GhcPosixErrno
import kotlinx.serialization.json.*
import java.util.concurrent.atomic.AtomicBoolean

class GhcEventAbi(val data: JsonObject) {
    fun int(name: String) = data.getValue(name).jsonPrimitive.long.toInt()
    val kqueue get() = data.getValue("backend").jsonPrimitive.content == "kqueue"
    val haveOneShot get() = data.getValue("haveOneShot").jsonPrimitive.boolean
    companion object {
        fun load(platform: String): GhcEventAbi = checkNotNull(GhcEventAbi::class.java.getResourceAsStream("/io/heapy/kotdoc/http/ghc-event-abi-$platform.json"))
            .bufferedReader().use { GhcEventAbi(Json.parseToJsonElement(it.readText()).jsonObject) }
        val currentPosix by lazy {
            NetworkSocketAbi.currentPosix
            load(if (com.sun.jna.Platform.isMac()) "macos-64" else "linux-64")
        }
    }
}
private fun NetworkNativeResult.checked(location: String): Long {
    if (value == -1L) throw GhcPosixErrno.exception(location, errno)
    return value
}
private fun setEventCloseOnExec(fd: Int, abi: GhcEventAbi, calls: NetworkSocketCalls) {
    calls.call("fcntl", arrayOf(fd, abi.int("F_SETFD"), abi.int("FD_CLOEXEC"))).checked("setCloseOnExec")
}
private fun setEventNonblocking(fd: Int, abi: GhcEventAbi, calls: NetworkSocketCalls) {
    val flags: Int
    while (true) {
        val r = calls.call("fcntl", arrayOf(fd, abi.int("F_GETFL"), 0))
        if (r.value == -1L && r.errno == abi.int("EINTR")) continue
        flags = r.checked("setNonBlockingFD").toInt(); break
    }
    val updated = flags or abi.int("O_NONBLOCK")
    if (updated != flags) calls.call("fcntl", arrayOf(fd, abi.int("F_SETFL"), updated))
}

/** Native EPoll/KQueue algorithms, including their different unsupported-fd
 * and failed-registration behavior. */
class GhcNativeEventBackend(val abi: GhcEventAbi, private val calls: NetworkSocketCalls = NetworkPosixSocketCalls) : GhcEventBackend {
    private val fd: Int
    private val pid: Long
    private var capacity = 64
    private var buffer: Memory
    init {
        fd = calls.call(if (abi.kqueue) "kqueue" else "epoll_create", if (abi.kqueue) emptyArray() else arrayOf(256))
            .checked(if (abi.kqueue) "kqueue" else "epollCreate").toInt()
        if (!abi.kqueue) setEventCloseOnExec(fd, abi, calls)
        buffer = Memory(capacity.toLong() * abi.int("eventSize"))
        pid = if (abi.kqueue) calls.call("getpid", emptyArray()).value else 0
    }
    private fun fromEvent(event: Int) = (if (event and 1 != 0) abi.int("EPOLLIN") else 0) or (if (event and 2 != 0) abi.int("EPOLLOUT") else 0)
    private fun epollEvent(fd: Int, event: Int, action: (Memory) -> NetworkNativeResult): NetworkNativeResult = Memory(abi.int("eventSize").toLong()).use { p ->
        p.clear(); p.setInt(abi.int("eventTypes").toLong(), event); p.setInt(abi.int("eventFd").toLong(), fd); action(p)
    }
    override fun modify(fd: Int, old: Int, new: Int): Boolean {
        if (abi.kqueue) return kqueueControl(fd, old, new, false)
        val operation = abi.int(if (old == 0) "EPOLL_CTL_ADD" else if (new == 0) "EPOLL_CTL_DEL" else "EPOLL_CTL_MOD")
        epollEvent(fd, fromEvent(new)) { calls.call("epoll_ctl", arrayOf(this.fd, operation, fd, it)) }.checked("epollControl")
        return true
    }
    override fun modifyOnce(fd: Int, events: Int): Boolean {
        if (abi.kqueue) return kqueueControl(fd, 0, events, true)
        val event = fromEvent(events) or abi.int("EPOLLONESHOT")
        val result = epollEvent(fd, event) { calls.call("epoll_ctl", arrayOf(this.fd, abi.int("EPOLL_CTL_MOD"), fd, it)) }
        if (result.value == 0L) return true
        if (result.errno != abi.int("ENOENT")) throw GhcPosixErrno.exception("modifyFdOnce", result.errno)
        epollEvent(fd, event) { calls.call("epoll_ctl", arrayOf(this.fd, abi.int("EPOLL_CTL_ADD"), fd, it)) }.checked("epollControl")
        return true
    }
    private fun filters(event: Int) = buildList {
        if (event and 1 != 0) add(abi.int("EVFILT_READ"))
        if (event and 2 != 0) add(abi.int("EVFILT_WRITE"))
    }
    private fun kqueueControl(fd: Int, old: Int, new: Int, once: Boolean): Boolean {
        val changes = filters(old).map { it to abi.int("EV_DELETE") } + filters(new).map { it to (abi.int("EV_ADD") or if (once) abi.int("EV_ONESHOT") else 0) }
        val result = Memory(maxOf(1, changes.size * abi.int("eventSize")).toLong()).use { p -> Memory(abi.int("timespecSize").toLong()).use { time ->
            p.clear(); time.clear()
            changes.forEachIndexed { index, (filter, flags) ->
                val e = p.share(index.toLong() * abi.int("eventSize"))
                e.setLong(abi.int("eventFd").toLong(), fd.toLong())
                e.setShort(abi.int("eventFilter").toLong(), filter.toShort())
                e.setShort(abi.int("eventFlags").toLong(), flags.toShort())
                e.setInt(abi.int("eventFflags").toLong(), abi.int("NOTE_EOF"))
            }
            calls.call("kevent", arrayOf(this.fd, p, changes.size, null, 0, time))
        } }
        if (result.value != -1L || result.errno == abi.int("EINTR")) return true
        if (result.errno == abi.int("EINVAL") || result.errno == abi.int("ENOTSUP")) return false
        throw GhcPosixErrno.exception("kevent", result.errno)
    }
    override fun poll(timeout: GhcEventTimeout?, callback: (Int, Int) -> Unit): Int {
        val events = buffer
        val result = if (abi.kqueue) {
            fun wait(time: Memory?) = calls.call("kevent", arrayOf(fd, null, 0, events, capacity, time))
            if (timeout == GhcEventTimeout.Forever) wait(null) else Memory(abi.int("timespecSize").toLong()).use { time ->
                time.clear()
                if (timeout is GhcEventTimeout.After) {
                    time.setLong(abi.int("timespecSec").toLong(), (timeout.nanos / 1_000_000_000u).toLong())
                    time.setLong(abi.int("timespecNsec").toLong(), (timeout.nanos % 1_000_000_000u).toLong())
                }
                wait(time)
            }
        } else {
            val milliseconds = when (timeout) {
                null -> 0
                GhcEventTimeout.Forever -> -1
                is GhcEventTimeout.After -> ((timeout.nanos + 999_999u) / 1_000_000u).toInt()
            }
            calls.call("epoll_wait", arrayOf(fd, events, capacity, milliseconds))
        }
        val location = if (abi.kqueue) "kevent" else if (timeout == null) "epollWaitNonBlock" else "epollWait"
        val count = if (result.value == -1L && result.errno == abi.int("EINTR")) 0 else result.checked(location).toInt()
        if (count > 0) {
            repeat(count) { index ->
                val offset = index.toLong() * abi.int("eventSize")
                val descriptor: Int; val event: Int
                if (abi.kqueue) {
                    descriptor = events.getLong(offset + abi.int("eventFd")).toInt()
                    event = when (val filter = events.getShort(offset + abi.int("eventFilter")).toInt()) {
                        abi.int("EVFILT_READ") -> 1
                        abi.int("EVFILT_WRITE") -> 2
                        else -> error("toEvent: unknown filter $filter")
                    }
                } else {
                    descriptor = events.getInt(offset + abi.int("eventFd"))
                    val flags = events.getInt(offset + abi.int("eventTypes"))
                    val error = abi.int("EPOLLERR") or abi.int("EPOLLHUP")
                    event = (if (flags and (abi.int("EPOLLIN") or error) != 0) 1 else 0) or
                        (if (flags and (abi.int("EPOLLOUT") or error) != 0) 2 else 0)
                }
                callback(descriptor, event)
            }
            if (count == capacity) { capacity *= 2; buffer = Memory(capacity.toLong() * abi.int("eventSize")) }
        }
        return count
    }
    override fun delete() {
        if (!abi.kqueue || calls.call("getpid", emptyArray()).value == pid) calls.call("close", arrayOf(fd))
    }
}

class GhcNativeEventControl(private val abi: GhcEventAbi, private val calls: NetworkSocketCalls = NetworkPosixSocketCalls) : GhcEventControl {
    private val dead = AtomicBoolean()
    private val controlWrite: Int
    private val wakeWrite: Int
    override val readFd: Int
    override val wakeFd: Int
    init {
        val control = pipe(); readFd = control.first; controlWrite = control.second
        if (abi.kqueue) {
            val wake = pipe(); wakeFd = wake.first; wakeWrite = wake.second
        } else {
            wakeFd = calls.call("eventfd", arrayOf(0, 0)).checked("eventfd").toInt(); wakeWrite = wakeFd
            setEventNonblocking(wakeFd, abi, calls); setEventCloseOnExec(wakeFd, abi, calls)
        }
    }
    private fun pipe(): Pair<Int, Int> = Memory(8).use { p ->
        calls.call("pipe", arrayOf(p)).checked("pipe")
        val read = p.getInt(0); val write = p.getInt(4)
        setEventNonblocking(write, abi, calls)
        setEventCloseOnExec(read, abi, calls); setEventCloseOnExec(write, abi, calls)
        read to write
    }
    override fun read(fd: Int): Boolean {
        if (fd == wakeFd) return Memory(if (abi.kqueue) 4096 else 8).use { p ->
            calls.call("read", arrayOf(fd, p, NativeLong(p.size()))).checked("readWakeupMessage"); false
        }
        return Memory(1).use { p ->
            calls.call("read", arrayOf(fd, p, NativeLong(1))).checked("readControlMessage")
            when (p.getByte(0).toInt() and 255) {
                255 -> false
                254 -> true
                else -> error("JVM event control does not install the GHC RTS signal trampoline")
            }
        }
    }
    override fun wake() {
        val r = if (abi.kqueue) message(wakeWrite, 255) else calls.call("eventfd_write", arrayOf<Any?>(wakeFd, 1L))
        val failed = if (abi.kqueue) r.value == -1L else r.value != 0L
        if (!failed || r.errno == abi.int("EBADF") && dead.get()) return
        if (abi.kqueue && (r.errno == abi.int("EAGAIN") || r.errno == abi.int("EWOULDBLOCK"))) return
        throw GhcPosixErrno.exception("sendWakeup", r.errno)
    }
    private fun message(fd: Int, byte: Int): NetworkNativeResult = Memory(1).use { p ->
        p.setByte(0, byte.toByte()); calls.call("write", arrayOf(fd, p, NativeLong(1)))
    }
    override fun die() { message(controlWrite, 254).checked("sendDie") }
    override fun close() {
        dead.set(true)
        for (fd in if (abi.kqueue) listOf(readFd, controlWrite, wakeFd, wakeWrite) else listOf(readFd, controlWrite, wakeFd)) calls.call("close", arrayOf(fd))
    }
}

object GhcEventRuntime {
    fun newManager(abi: GhcEventAbi = GhcEventAbi.currentPosix) =
        GhcEventManager(GhcNativeEventBackend(abi), GhcNativeEventControl(abi), abi.haveOneShot, abi.kqueue)
    val system: GhcEventManager by lazy {
        val manager = newManager()
        Thread.ofPlatform().daemon(true).name("kotdoc-ghc-event-manager").start { manager.loop() }
        manager
    }
}
