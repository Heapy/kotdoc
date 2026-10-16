// Network.HTTP.Client.Connection 0.7.19, (c) 2013 Michael Snoyman.
// MIT; complete source/notice retained in vendor/ and licenses/http-client-MIT.txt.
// Required async-2.2.6 concurrently semantics: (c) 2012 Simon Marlow, BSD-3-Clause;
// licenses/Async-BSD-3-Clause.txt and the full pinned source under vendor/.
package io.heapy.kotdoc.http

import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/** Native Network.Socket effects. Address lookup must use defaultHints with
 * addrSocketType=Stream; ipv4 constructs AF_INET/Stream/protocol 6, no flags
 * or canonical name. The native ABI implementation is a separate layer. */
interface HttpSocketBackend<S, A> {
    fun resolve(host: String, service: String): List<A>
    fun ipv4(port: UShort, address: UInt): A
    fun create(address: A): S
    fun noDelay(socket: S, value: Int)
    fun connect(socket: S, address: A)
    fun close(socket: S)
    fun receive(socket: S, chunkSize: Long): HttpBytes
    fun sendAll(socket: S, bytes: HttpBytes)
}
fun interface HttpSocketRaceRuntime { fun delay(microseconds: Long) }
fun interface HttpSizedConnector {
    fun connect(chunkSize: Long, address: UInt?, host: String, port: Long): HttpConnection
}
object HttpSocketRaceSystemRuntime : HttpSocketRaceRuntime {
    override fun delay(microseconds: Long) { TimeUnit.MICROSECONDS.sleep(microseconds) }
}

object HttpSocketConnections {
    private sealed interface Attempt<out R> {
        data class Success<R>(val value: R) : Attempt<R>
        data class Failure(val error: IOException) : Attempt<Nothing>
    }
    private data class Completion(val left: Boolean, val value: Any?, val failure: Throwable?)
    private class Attempts<R>(val head: Attempt<R>, val tail: Attempts<R>?)
    private fun checkCancellation() { if (Thread.interrupted()) throw InterruptedException() }
    private fun <T> uninterruptibly(action: () -> T): T {
        var interrupted = Thread.interrupted()
        try {
            while (true) {
                try { return action() } catch (_: InterruptedException) { interrupted = true }
            }
        } finally { if (interrupted) Thread.currentThread().interrupt() }
    }
    /** The two-child MVar implementation of async-2.2.6 concurrently'. Keep
     * its tree and drain pending completion writes during cancellation; a
     * flat executor changes startup and cancellation ownership. */
    private fun <L, R> concurrently(left: () -> L, right: () -> R): Pair<L, R> {
        val done = ArrayBlockingQueue<Completion>(1)
        fun child(isLeft: Boolean, action: () -> Any?): Thread =
            Thread.ofVirtual().name("kotdoc-http-connect-child").inheritInheritableThreadLocals(false).start {
                val completed = try { checkCancellation(); Completion(isLeft, action(), null) }
                    catch (e: Throwable) { Completion(isLeft, null, e) }
                uninterruptibly { done.put(completed) }
            }
        val lhs = child(true, left)
        val rhs = child(false, right)
        var remaining = 2
        var a: Any? = null; var b: Any? = null
        try {
            repeat(2) {
                val completed = done.take(); remaining--
                completed.failure?.let { throw it }
                if (completed.left) a = completed.value else b = completed.value
            }
            @Suppress("UNCHECKED_CAST")
            return (a as L) to (b as R)
        } finally {
            // Original stop sends cancellation right then left from a
            // separate thread, then drains exactly the outstanding writes.
            val canceller = if (remaining > 0) Thread.ofVirtual().inheritInheritableThreadLocals(false).start {
                if (rhs.isAlive) rhs.interrupt()
                if (lhs.isAlive) lhs.interrupt()
            } else null
            repeat(remaining) { uninterruptibly { done.take() } }
            canceller?.let(::joinUninterruptibly)
            joinUninterruptibly(rhs); joinUninterruptibly(lhs)
        }
    }

    /** Mirrors the original unlinked withAsync around forConcurrently. A
     * non-IO failure in that child cancels sibling attempts but does not fill
     * the result MVar: the caller remains blocked until externally cancelled.
     * Do not silently turn that source behavior into an IO retry or success. */
    fun <A, R> firstSuccessful(addresses: List<A>, runtime: HttpSocketRaceRuntime = HttpSocketRaceSystemRuntime,
        action: (A) -> R,
    ): R {
        if (addresses.isEmpty()) error("getAddrInfo returned empty list")
        val result = ArrayBlockingQueue<Attempt<R>>(1)
        fun attempt(index: Int): Attempt<R> {
            if (index > 0) runtime.delay(index.toLong() * 250_000L)
            val attempted: Attempt<R> = try { Attempt.Success(action(addresses[index])) }
                catch (e: IOException) { Attempt.Failure(e) }
            if (attempted is Attempt.Success) result.offer(attempted)
            return attempted
        }
        fun parallel(index: Int): Attempts<R>? = if (index == addresses.size) null else {
            val (head, tail) = concurrently({ attempt(index) }, { parallel(index + 1) })
            Attempts(head, tail)
        }
        val controller = Thread.ofVirtual().name("kotdoc-http-connect-race").inheritInheritableThreadLocals(false).start {
            try {
                checkCancellation()
                var attempts = parallel(0)!!
                while (true) { attempts = attempts.tail ?: break }
                val last = attempts.head
                if (last is Attempt.Failure) result.offer(last)
                else error("tryAddresses invariant violated")
            } catch (_: Throwable) {
                // withAsync stores this exception without linking it to the
                // parent, which is waiting on the separate result MVar.
            }
        }
        val selected = try { result.take() } finally {
            if (controller.isAlive) controller.interrupt()
            joinUninterruptibly(controller)
        }
        return when (selected) {
            is Attempt.Success -> selected.value
            is Attempt.Failure -> throw selected.error
        }
    }
    private fun joinUninterruptibly(thread: Thread) {
        var interrupted = Thread.interrupted()
        while (true) {
            try { thread.join(); break } catch (_: InterruptedException) { interrupted = true }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }
    fun <S, A> openSocket(backend: HttpSocketBackend<S, A>, tweak: (S) -> Unit, address: A): S {
        val socket = backend.create(address)
        try {
            backend.noDelay(socket, 1)
            tweak(socket)
            backend.connect(socket, address)
            return socket
        } catch (e: Throwable) {
            backend.close(socket)
            throw e
        }
    }
    fun <S, A, R> withSocket(backend: HttpSocketBackend<S, A>, tweak: (S) -> Unit,
        address: UInt?, host: String, port: Long, runtime: HttpSocketRaceRuntime = HttpSocketRaceSystemRuntime,
        action: (S) -> R,
    ): R {
        // The explicit AddrInfo is a lazy list element. network's StrictData
        // forces its checked Word16 port when the child first uses AddrInfo,
        // before socket acquisition; it is not forced in the parent resolver.
        val addresses: List<() -> A> = if (address == null)
            backend.resolve(HttpConnections.strippedHostName(host), port.toString()).map { info -> { info } }
        else listOf({
            if (port !in 0..65535) error("Enum.toEnum{Word16}: tag ($port) is outside of enumeration's range (0,65535)")
            backend.ipv4(port.toUShort(), address)
        })
        val socket = firstSuccessful(addresses, runtime) { openSocket(backend, tweak, it()) }
        try { return action(socket) } catch (e: Throwable) {
            backend.close(socket)
            throw e
        }
    }
    fun <S, A> socketConnection(backend: HttpSocketBackend<S, A>, socket: S, chunkSize: Long): HttpConnection =
        HttpConnections.makeConnection({ backend.receive(socket, chunkSize) }, { backend.sendAll(socket, it) }, { backend.close(socket) })
    fun <S, A> openSocketConnection(backend: HttpSocketBackend<S, A>, tweak: (S) -> Unit,
        address: UInt?, host: String, port: Long, runtime: HttpSocketRaceRuntime = HttpSocketRaceSystemRuntime,
    ): HttpConnection = openSocketConnectionSize(backend, tweak, 8192, address, host, port, runtime)
    fun <S, A> openSocketConnectionSize(backend: HttpSocketBackend<S, A>, tweak: (S) -> Unit,
        chunkSize: Long, address: UInt?, host: String, port: Long, runtime: HttpSocketRaceRuntime = HttpSocketRaceSystemRuntime,
    ): HttpConnection = withSocket(backend, tweak, address, host, port, runtime) { socketConnection(backend, it, chunkSize) }
}
