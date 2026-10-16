// GHC 9.12.2 Event.Manager/Thread/Internal.Types, University of Glasgow et al.
// Full source retained in vendor/ghc-event-9.12.2; licenses/GHC-base-BSD-3-Clause.txt.
package io.heapy.kotdoc.http

import io.heapy.kotdoc.errors.GhcPosixErrno
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

sealed interface GhcEventTimeout {
    data object Forever : GhcEventTimeout
    data class After(val nanos: ULong) : GhcEventTimeout
}
interface GhcEventBackend {
    fun modify(fd: Int, old: Int, new: Int): Boolean
    fun modifyOnce(fd: Int, events: Int): Boolean
    fun poll(timeout: GhcEventTimeout?, callback: (Int, Int) -> Unit): Int
    fun delete()
}
interface GhcEventControl {
    val readFd: Int
    val wakeFd: Int
    fun read(fd: Int): Boolean // True is CMsgDie; False is CMsgWakeup.
    fun wake()
    fun die()
    fun close()
}
data class GhcEventKey(val fd: Int, val unique: Long)
data class GhcEventEntry(val key: GhcEventKey, val eventLifetime: Int, val callback: (GhcEventKey, Int) -> Unit)
enum class GhcEventState { Created, Running, Dying, Releasing, Finished }

/** Original callback table/state transitions. The 32 locks retain the source
 * fd hash partition; callback invocation happens after releasing the table. */
class GhcEventManager(
    private val backend: GhcEventBackend,
    private val control: GhcEventControl,
    private val haveOneShot: Boolean,
    private val wakeOnModify: Boolean,
) {
    private class Table {
        val lock = ReentrantLock()
        val entries = HashMap<Int, List<GhcEventEntry>>()
    }
    private val tables = Array(32) { Table() }
    private val source = AtomicLong()
    private val state = AtomicReference(GhcEventState.Created)
    private val loopLock = ArrayBlockingQueue<Unit>(1).also { it.add(Unit) }
    val currentState get() = state.get()
    init {
        requireBackend("registerControlFd", control.readFd, backend.modify(control.readFd, 0, 1))
        requireBackend("registerControlFd", control.wakeFd, backend.modify(control.wakeFd, 0, 1))
    }
    private fun table(fd: Int) = tables[fd and 31]
    private inline fun <T> locked(fd: Int, action: (Table) -> T): T {
        val t = table(fd)
        // mask_ defers interruption except while an acquisition blocks.
        if (!t.lock.tryLock()) t.lock.lockInterruptibly()
        try { return action(t) } finally { t.lock.unlock() }
    }
    private fun events(entries: List<GhcEventEntry>) = entries.fold(0) { bits, entry -> bits or entry.eventLifetime }
    private fun requireBackend(location: String, fd: Int, supported: Boolean) {
        if (!supported) error("Failed while attempting to modify registration of file $fd at location $location")
    }
    private fun wakeManager() { if (wakeOnModify) control.wake() }
    fun register(fd: Int, event: Int, multi: Boolean = false, callback: (GhcEventKey, Int) -> Unit): GhcEventKey {
        val key = GhcEventKey(fd, source.getAndIncrement())
        val lifetime = event or (if (multi) 8 else 0)
        val (modify, supported) = locked(fd) { t ->
            val previous = t.entries[fd]
            t.entries[fd] = listOf(GhcEventEntry(key, lifetime, callback)) + previous.orEmpty()
            val old = events(previous.orEmpty()); val new = old or lifetime
            val result = if (new and 8 == 0 && haveOneShot) false to backend.modifyOnce(fd, new and 7)
                else (old != new).let { changed -> changed to (!changed || backend.modify(fd, old and 7, new and 7)) }
            // Only an explicit False rolls back. A throwing backend leaves the
            // already inserted entry, as in the original mutable IntTable.
            if (!result.second) { if (previous == null) t.entries.remove(fd) else t.entries[fd] = previous }
            result
        }
        if (!supported) callback(key, event)
        if (modify) wakeManager()
        return key
    }
    fun unregisterWithoutWake(key: GhcEventKey): Boolean = locked(key.fd) { t ->
        val old = t.entries[key.fd]
        if (old != null) {
            val saved = old.filterNot { it.key.unique == key.unique }
            if (saved.isEmpty()) t.entries.remove(key.fd) else t.entries[key.fd] = saved
        }
        val before = events(old.orEmpty()); val after = events(t.entries[key.fd].orEmpty())
        val modify = before != after
        if (modify) requireBackend("unregisterFd_", key.fd,
            if (after and 8 == 0 && after and 7 != 0 && haveOneShot) backend.modifyOnce(key.fd, after and 7)
            else backend.modify(key.fd, before and 7, after and 7))
        modify
    }
    fun unregister(key: GhcEventKey) { if (unregisterWithoutWake(key)) wakeManager() }
    fun dispatch(fd: Int, event: Int) {
        if (fd == control.readFd || fd == control.wakeFd) {
            if (control.read(fd)) state.set(GhcEventState.Finished)
            return
        }
        val ready = locked(fd) { t ->
            val original = t.entries.remove(fd) ?: return@locked emptyList()
            val (triggered, notTriggered) = original.partition { event and (it.eventLifetime and 7) != 0 }
            val saved = notTriggered + triggered.filter { it.eventLifetime and 8 != 0 }
            val before = events(original); val after = events(saved)
            t.entries[fd] = saved // An empty list is deliberately still an entry.
            if (!(before and 8 != 0 && before == after)) {
                if (after and 8 == 0 && haveOneShot) {
                    if (!(before and 8 == 0 && after and 7 == 0)) backend.modifyOnce(fd, after and 7)
                } else backend.modify(fd, before and 7, after and 7)
            }
            triggered
        }
        for (entry in ready) entry.callback(entry.key, event)
    }
    fun closeFd(fd: Int, close: () -> Unit) {
        val callbacks = locked(fd) { t ->
            val entries = t.entries.remove(fd)
            if (entries != null && events(entries) and 7 != 0) {
                backend.modify(fd, events(entries) and 7, 0); wakeManager()
            }
            close()
            entries.orEmpty()
        }
        for (entry in callbacks) entry.callback(entry.key, (entry.eventLifetime and 7) or 4)
    }
    /** Thread.closeFdWith's stable manager-array path. */
    fun closeFdWith(fd: Int, close: () -> Unit) = closeFdWithAll(listOf(this), fd, close)
    companion object {
        fun closeFdWithAll(managers: List<GhcEventManager>, fd: Int, close: () -> Unit) {
            val tables = managers.map { it.table(fd) }
            for (table in tables) table.lock.lock() // uninterruptible acquisition and raw close.
            val callbacks = ArrayList<List<GhcEventEntry>>()
            try {
                managers.zip(tables).forEach { (manager, table) ->
                    val entries = table.entries.remove(fd).orEmpty(); callbacks.add(entries)
                    val before = manager.events(entries)
                    if (before != 0) { manager.backend.modify(fd, before and 7, 0); manager.wakeManager() }
                }
            } catch (e: Throwable) {
                for (table in tables) table.lock.unlock()
                throw e
            }
            try { close() } finally {
                managers.indices.forEach { index ->
                    tables[index].lock.unlock()
                    for (entry in callbacks[index]) entry.callback(entry.key, (entry.eventLifetime and 7) or 4)
                }
            }
        }
    }
    fun snapshot(fd: Int): List<Int>? = locked(fd) { it.entries[fd]?.map(GhcEventEntry::eventLifetime) }
    fun step(): GhcEventState {
        if (backend.poll(null, ::dispatch) <= 0) {
            Thread.yield()
            if (backend.poll(null, ::dispatch) <= 0) backend.poll(GhcEventTimeout.Forever, ::dispatch)
        }
        return state.get()
    }
    fun loop() {
        loopLock.take()
        val old = state.getAndUpdate { if (it == GhcEventState.Created || it == GhcEventState.Releasing) GhcEventState.Running else it }
        when (old) {
            GhcEventState.Created, GhcEventState.Releasing -> try {
                while (true) {
                    when (step()) {
                        GhcEventState.Running -> Thread.yield()
                        GhcEventState.Releasing -> { loopLock.put(Unit); return }
                        else -> { cleanup(); return }
                    }
                }
            } catch (e: Throwable) { cleanup(); throw e }
            GhcEventState.Dying -> cleanup()
            GhcEventState.Finished -> Unit
            else -> { cleanup(); error("GHC.Internal.Event.Manager.loop: state is already $old") }
        }
    }
    fun shutdown() { if (state.getAndSet(GhcEventState.Dying) == GhcEventState.Running) control.die() }
    fun release() { if (state.getAndSet(GhcEventState.Releasing) == GhcEventState.Running) control.wake() }
    fun cleanup() {
        state.set(GhcEventState.Finished); loopLock.offer(Unit)
        backend.delete(); control.close()
    }
    fun await(fd: Int, write: Boolean, badFd: Int) {
        val completed = ArrayBlockingQueue<Int>(1)
        val key = register(fd, if (write) 2 else 1) { _, event ->
            var interrupted = Thread.interrupted()
            try { while (true) try { completed.put(event); break } catch (_: InterruptedException) { interrupted = true } }
            finally { if (interrupted) Thread.currentThread().interrupt() }
        }
        val event = try { completed.poll() ?: completed.take() }
            catch (e: Throwable) { unregisterWithoutWake(key); throw e }
        if (event and 4 != 0) throw GhcPosixErrno.exception("threadWait", badFd)
    }
}
