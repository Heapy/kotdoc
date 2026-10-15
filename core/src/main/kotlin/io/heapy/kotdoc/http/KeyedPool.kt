// Data.KeyedPool, http-client 0.7.19, Copyright (c) 2013 Michael Snoyman.
// MIT; licenses/http-client-MIT.txt. Original source is retained under vendor/.
package io.heapy.kotdoc.http

import io.heapy.kotdoc.resources.PandocUtcTime
import java.lang.ref.Cleaner
import java.lang.ref.Reference
import java.math.BigInteger
import java.time.Instant
import java.util.TreeMap
import java.util.concurrent.atomic.AtomicBoolean

/** Effects at the original clock, weak-finalizer and reaper-thread boundaries. */
interface KeyedPoolRuntime {
    fun now(): PandocUtcTime
    fun registerCleanup(owner: Any, action: () -> Unit)
    fun startReaper(action: () -> Unit)
    fun delayReaper()
}
object KeyedPoolSystemRuntime : KeyedPoolRuntime {
    private val cleaner = Cleaner.create()
    override fun now(): PandocUtcTime = Instant.now().let {
        PandocUtcTime.fromPosixPicoseconds(BigInteger.valueOf(it.epochSecond) * BigInteger.TEN.pow(12) + BigInteger.valueOf(it.nano.toLong()) * BigInteger.valueOf(1000))
    }
    override fun registerCleanup(owner: Any, action: () -> Unit) { cleaner.register(owner, Runnable(action)) }
    override fun startReaper(action: () -> Unit) { Thread.ofVirtual().name("kotdoc-http-pool-reaper").inheritInheritableThreadLocals(false).start(action) }
    override fun delayReaper() { Thread.sleep(5000) }
}

class KeyedManaged<R> internal constructor(
    val resource: R,
    val reused: Boolean,
    private val releaseAction: (HttpReuse) -> Unit,
    runtime: KeyedPoolRuntime?,
) {
    private val alive = Any()
    init {
        // Capture the action, never this wrapper or its weak-reference key.
        val action = releaseAction
        runtime?.registerCleanup(alive) { action(HttpReuse.DontReuse) }
    }
    fun release(reuse: HttpReuse) = releaseAction(reuse)
    fun keepAlive() { Reference.reachabilityFence(alive) }
}

class KeyedPool<K, R> private constructor(
    private val create: (K) -> R,
    private val state: State<K, R>,
    private val runtime: KeyedPoolRuntime,
) {
    private val alive = Any()

    fun take(key: K): KeyedManaged<R> {
        val idle = state.take(key)
        val resource = if (idle == null) create(key) else idle.resource
        val released = AtomicBoolean()
        // The release closure keeps the pool's alive token reachable while a
        // checked-out Managed exists, just as the original kp closure does.
        val pool = this
        val release: (HttpReuse) -> Unit = { reuse ->
            if (released.compareAndSet(false, true)) {
                when (reuse) {
                    HttpReuse.Reuse -> pool.state.put(key, resource)
                    HttpReuse.DontReuse -> pool.state.destroyIgnoring(resource)
                }
            }
        }
        return KeyedManaged(resource, idle != null, release, runtime)
    }

    private data class Idle<R>(val resource: R, val time: PandocUtcTime)
    private class State<K, R>(
        private val comparator: Comparator<in K>,
        private val destroy: (R) -> Unit,
        private val maxPerKey: Long,
        private val maxTotal: Long,
        private val runtime: KeyedPoolRuntime,
    ) {
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") // JVM monitor wait/notify mirrors STM retry.
        private val lock = Object()
        private var closed = false
        private var idleCount = 0L
        private var idle = TreeMap<K, ArrayDeque<Idle<R>>>(comparator)
        fun take(key: K): Idle<R>? = synchronized(lock) {
            val list = if (closed) null else idle[key]
            val entry = list?.removeFirstOrNull()
            if (entry != null) {
                idleCount--
                if (list.isEmpty()) idle.remove(key)
            }
            lock.notifyAll()
            entry
        }
        fun put(key: K, resource: R) {
            // The original reads the clock before entering its STM transaction,
            // even if this pool is closed or the resource will be rejected.
            val now = runtime.now()
            val accepted = synchronized(lock) {
                val list = idle[key]
                val accept = !closed && idleCount < maxTotal && (list == null || maxPerKey > list.size.toLong())
                if (accept) {
                    val destination = list ?: ArrayDeque<Idle<R>>().also { idle[key] = it }
                    destination.addFirst(Idle(resource, now))
                    idleCount++
                }
                lock.notifyAll()
                accept
            }
            // Reuse overflow destruction is deliberately not caught here.
            if (!accepted) destroy(resource)
        }
        fun destroyIgnoring(resource: R) { try { destroy(resource) } catch (_: Throwable) { /* original ignoreExceptions */ } }
        fun close() {
            val resources = synchronized(lock) {
                val saved = idle.values.flatMap { list -> list.map { it.resource } }
                closed = true; idleCount = 0; idle = TreeMap(comparator)
                lock.notifyAll()
                saved
            }
            resources.forEach(::destroyIgnoring)
        }
        private fun fresh(time: PandocUtcTime, now: PandocUtcTime): Boolean {
            val deadline = PandocUtcTime.fromPosixPicoseconds(time.posixPicoseconds() + BigInteger.valueOf(30) * BigInteger.TEN.pow(12))
            return deadline.day > now.day || deadline.day == now.day && deadline.dayPicoseconds >= now.dayPicoseconds
        }
        private fun reapLoop() {
            while (true) {
                runtime.delayReaper()
                val expired = synchronized(lock) {
                    while (!closed && idle.isEmpty()) lock.wait()
                    if (closed) return
                    // Time is read only after an empty-map wait has finished.
                    val now = runtime.now()
                    val kept = TreeMap<K, ArrayDeque<Idle<R>>>(comparator)
                    val removed = mutableListOf<R>()
                    for ((key, list) in idle) {
                        val prefix = ArrayDeque<Idle<R>>()
                        var stale = false
                        for (entry in list) {
                            // Preserve span: a stale entry makes the whole tail
                            // stale, including timestamps after a clock rollback.
                            if (!stale && fresh(entry.time, now)) prefix.addLast(entry)
                            else { stale = true; removed.add(entry.resource) }
                        }
                        if (prefix.isNotEmpty()) kept[key] = prefix
                    }
                    idle = kept; idleCount -= removed.size.toLong()
                    lock.notifyAll()
                    removed
                }
                expired.forEach(::destroyIgnoring)
            }
        }
        fun runReaper(onException: (Throwable) -> Unit) {
            while (true) {
                try { reapLoop(); return } catch (e: Throwable) { onException(e) }
            }
        }
    }
    companion object {
        fun <K, R> createKeyedPool(comparator: Comparator<in K>, create: (K) -> R, destroy: (R) -> Unit,
            maxPerKey: Long, maxTotal: Long, onReaperException: (Throwable) -> Unit,
            runtime: KeyedPoolRuntime = KeyedPoolSystemRuntime,
        ): KeyedPool<K, R> {
            val state = State(comparator, destroy, maxPerKey, maxTotal, runtime)
            val pool = KeyedPool(create, state, runtime)
            // Neither callback captures pool/alive: the reaper must not keep
            // the weak-reference owner alive forever.
            runtime.registerCleanup(pool.alive) { state.close() }
            runtime.startReaper { state.runReaper(onReaperException) }
            return pool
        }
        fun <K : Comparable<K>, R> createKeyedPool(create: (K) -> R, destroy: (R) -> Unit,
            maxPerKey: Long, maxTotal: Long, onReaperException: (Throwable) -> Unit,
            runtime: KeyedPoolRuntime = KeyedPoolSystemRuntime,
        ): KeyedPool<K, R> = createKeyedPool(naturalOrder<K>(), create, destroy, maxPerKey, maxTotal, onReaperException, runtime)
        fun <R> dummyManaged(resource: R) = KeyedManaged(resource, false, {}, null)
    }
}
