package io.heapy.kotdoc.http

import com.sun.jna.Memory
import com.sun.jna.NativeLong
import com.sun.jna.Platform
import io.heapy.kotdoc.errors.GhcIOException
import io.heapy.kotdoc.errors.GhcIoError
import io.heapy.kotdoc.errors.GhcPosixErrno
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import java.util.concurrent.FutureTask
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

class GhcEventManagerTest {
    private val current = if (Platform.isMac()) "macos-64" else "linux-64"
    private fun number(o: JsonObject, k: String, default: Int = 0) = o[k]?.jsonPrimitive?.intOrNull ?: default
    private fun text(o: JsonObject, k: String, default: String = "") = o[k]?.jsonPrimitive?.content ?: default
    private fun flag(o: JsonObject, k: String) = o[k]?.jsonPrimitive?.booleanOrNull ?: false
    private fun error(e: Throwable) = buildJsonObject {
        when (e) {
            is GhcIOException -> {
                val x = e.error
                put("kind", "io"); put("type", x.type.shown); put("location", Chars.build(x.location)); put("description", Chars.build(x.description))
                put("errno", x.errno?.let(::JsonPrimitive) ?: JsonNull); put("show", x.toString())
            }
            is InterruptedException -> { put("kind", "async"); put("show", "thread killed") }
            is IllegalStateException -> { put("kind", "fatal"); put("show", e.message!!) }
            else -> throw e
        }
    }
    private fun observe(action: () -> JsonElement): JsonElement = try { action() } catch (e: Throwable) { buildJsonObject { put("error", error(e)) } }
    private fun run(request: JsonObject, abi: GhcEventAbi): JsonElement {
        if (text(request, "api") == "waiters") return waiters(request, abi)
        if (text(request, "api") == "lifecycle") return lifecycle(abi)
        if (text(request, "api") == "burst") return burst(request, abi)
        val actions = request.getValue("actions").jsonArray.map { it.jsonObject }
        val native = text(request, "api") == "native-program"
        val trace = mutableListOf<JsonElement>(); val responses = ArrayDeque<JsonElement>(); val polls = ArrayDeque<JsonObject>()
        val keys = HashMap<String, GhcEventKey>()
        lateinit var manager: GhcEventManager
        val original = if (native) GhcNativeEventBackend(abi) else null
        val backend = object : GhcEventBackend {
            private fun effect(value: JsonObject): Boolean {
                trace.add(value)
                val response = responses.removeFirstOrNull() ?: return true
                response.jsonPrimitive.booleanOrNull?.let { return it }
                throw GhcPosixErrno.exception("backend", response.jsonPrimitive.int)
            }
            override fun modify(fd: Int, old: Int, new: Int): Boolean {
                val event = buildJsonObject { put("op", "modify"); put("fd", fd); put("old", old); put("new", new) }
                if (original == null) return effect(event)
                trace.add(event); return original.modify(fd, old, new)
            }
            override fun modifyOnce(fd: Int, events: Int): Boolean {
                val event = buildJsonObject { put("op", "once"); put("fd", fd); put("events", events) }
                if (original == null) return effect(event)
                trace.add(event); return original.modifyOnce(fd, events)
            }
            override fun poll(timeout: GhcEventTimeout?, callback: (Int, Int) -> Unit): Int {
                trace.add(buildJsonObject { put("op", "poll"); put("timeout", when (timeout) {
                    null -> "nonblock"
                    GhcEventTimeout.Forever -> "Forever"
                    is GhcEventTimeout.After -> "Timeout ${timeout.nanos}"
                }) })
                if (original != null) return original.poll(timeout, callback)
                val poll = polls.removeFirstOrNull() ?: return 0
                when (text(poll, "control")) { "release" -> manager.release(); "shutdown" -> manager.shutdown() }
                val notifications = poll["events"]?.jsonArray ?: JsonArray(emptyList())
                for (item in notifications) item.jsonArray.let { callback(it[0].jsonPrimitive.int, it[1].jsonPrimitive.int) }
                return number(poll, "result", notifications.size)
            }
            override fun delete() { trace.add(buildJsonObject { put("op", "delete") }); original?.delete() }
        }
        val control = if (native) GhcNativeEventControl(abi) else object : GhcEventControl {
            override val readFd = 10; override val wakeFd = 11
            override fun read(fd: Int) = false
            override fun wake() = Unit
            override fun die() = Unit
            override fun close() = Unit
        }
        manager = GhcEventManager(backend, control, abi.haveOneShot, abi.kqueue)
        val fds = actions.map { number(it, "fd", 123) }.distinct().sorted()
        try {
            return JsonArray(actions.map { action ->
                trace.clear(); responses.clear(); responses.addAll(action["responses"]?.jsonArray.orEmpty())
                polls.clear(); polls.addAll(action["polls"]?.jsonArray?.map { it.jsonObject }.orEmpty())
                val fd = number(action, "fd", 123)
                val result = observe {
                    when (text(action, "op")) {
                        "register" -> {
                            val label = text(action, "id")
                            val key = manager.register(fd, number(action, "events"), flag(action, "multi")) { _, event ->
                                trace.add(buildJsonObject { put("op", "callback"); put("id", label); put("events", event) })
                                if (flag(action, "callbackError")) throw GhcIOException(GhcIoError.userError("callback:$label"))
                            }
                            keys[label] = key; JsonNull
                        }
                        "unregister" -> { manager.unregister(keys[text(action, "id", "unknown")] ?: GhcEventKey(fd, -1)); JsonNull }
                        "dispatch" -> { manager.dispatch(fd, number(action, "events")); JsonNull }
                        "close" -> { manager.closeFd(fd) { trace.add(buildJsonObject { put("op", "close"); put("fd", fd) }) }; JsonNull }
                        "step" -> JsonPrimitive(manager.step().toString())
                        "loop" -> { manager.loop(); JsonNull }
                        "shutdown" -> { manager.shutdown(); JsonNull }
                        "release" -> { manager.release(); JsonNull }
                        else -> error(action)
                    }
                }
                buildJsonObject {
                    put("result", result); put("effects", JsonArray(trace.toList())); put("state", manager.currentState.toString())
                    put("tables", JsonArray(fds.map { descriptor -> buildJsonObject {
                        put("fd", descriptor)
                        put("entries", manager.snapshot(descriptor)?.let { list -> JsonArray(list.map { entry -> buildJsonObject {
                            put("events", entry and 7); put("multi", entry and 8 != 0)
                        } }) } ?: JsonNull)
                    } }))
                }
            })
        } finally { if (manager.currentState != GhcEventState.Finished) manager.cleanup() }
    }
    private fun waiters(request: JsonObject, abi: GhcEventAbi): JsonElement {
        val count = number(request, "count"); val managerCount = number(request, "managers")
        val mode = text(request, "mode"); val cancelled = if (mode == "cancel") number(request, "cancel") else 0
        val managers = List(managerCount) { GhcEventRuntime.newManager(abi) }
        val loops = managers.map { manager -> FutureTask { manager.loop() }.also { Thread.ofPlatform().daemon(true).start(it) } }
        val (rd, wr) = Memory(8).use { p ->
            val created = NetworkPosixSocketCalls.call("pipe", arrayOf(p))
            if (created.value == -1L) throw GhcPosixErrno.exception("pipe", created.errno)
            p.getInt(0) to p.getInt(4)
        }
        var closed = false; var closeCount = 0
        fun closeRead() = GhcEventManager.closeFdWithAll(managers, rd) {
            closed = true; closeCount++; NetworkPosixSocketCalls.call("close", arrayOf(rd))
            if (flag(request, "closeError")) throw GhcIOException(GhcIoError.userError("raw-close"))
        }
        fun snapshot() = JsonArray(managers.map { manager -> manager.snapshot(rd)?.let { entries -> JsonArray(entries.map { entry ->
            buildJsonObject { put("events", entry and 7); put("multi", entry and 8 != 0) }
        }) } ?: JsonNull })
        fun awaitCount(expected: Int) {
            val limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (managers.sumOf { it.snapshot(rd)?.size ?: 0 } != expected) {
                assertTrue(System.nanoTime() < limit, "native registrations did not reach $expected")
                Thread.sleep(1)
            }
        }
        data class Worker(val id: Int, val thread: Thread, val task: FutureTask<Unit>)
        fun spawn(id: Int): Worker {
            val task = FutureTask { managers[id % managerCount].await(rd, false, abi.int("EBADF")) }
            return Worker(id, Thread.ofVirtual().start(task), task)
        }
        fun collect(worker: Worker) = buildJsonObject {
            put("id", worker.id)
            put("result", observe {
                try { worker.task.get(5, TimeUnit.SECONDS) } catch (e: ExecutionException) { throw e.cause!! }
                JsonNull
            })
        }
        try {
            val workers = List(count, ::spawn)
            awaitCount(count); val before = snapshot()
            workers.take(cancelled).forEach { it.thread.interrupt() }
            val cancelledResults = workers.take(cancelled).map(::collect)
            val afterCancel = snapshot()
            val extra = if (flag(request, "replace")) listOf(spawn(count)) else emptyList()
            awaitCount(count - cancelled + extra.size)
            val closeResult = if (mode == "close") observe { closeRead(); JsonNull } else {
                Memory(1).use { p ->
                    p.setByte(0, 1)
                    val written = NetworkPosixSocketCalls.call("write", arrayOf(wr, p, NativeLong(1)))
                    if (written.value == -1L) throw GhcPosixErrno.exception("write", written.errno)
                }
                JsonNull
            }
            val results = cancelledResults + (workers.drop(cancelled) + extra).map(::collect)
            val after = snapshot()
            if (!closed) closeRead()
            return buildJsonObject {
                put("before", before); put("afterCancel", afterCancel); put("after", after)
                put("results", JsonArray(results)); put("closeResult", closeResult); put("closeCount", closeCount)
            }
        } finally {
            if (!closed) closeRead()
            NetworkPosixSocketCalls.call("close", arrayOf(wr))
            managers.zip(loops).forEach { (manager, loop) -> manager.shutdown(); loop.get(5, TimeUnit.SECONDS) }
        }
    }
    private fun lifecycle(abi: GhcEventAbi): JsonElement {
        val manager = GhcEventRuntime.newManager(abi)
        fun launch() = FutureTask { manager.loop() }.also { Thread.ofPlatform().daemon(true).start(it) }
        fun awaitRunning() {
            val limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (manager.currentState != GhcEventState.Running) { assertTrue(System.nanoTime() < limit); Thread.sleep(1) }
        }
        val first = launch(); awaitRunning(); manager.release(); first.get(5, TimeUnit.SECONDS)
        val released = manager.currentState.toString()
        val second = launch(); awaitRunning(); manager.shutdown(); second.get(5, TimeUnit.SECONDS)
        return JsonArray(listOf(JsonPrimitive(released), JsonPrimitive(manager.currentState.toString())))
    }
    private fun burst(request: JsonObject, abi: GhcEventAbi): JsonElement {
        val manager = GhcEventRuntime.newManager(abi)
        val pipes = List(number(request, "count")) {
            Memory(8).use { p ->
                val created = NetworkPosixSocketCalls.call("pipe", arrayOf(p))
                if (created.value == -1L) throw GhcPosixErrno.exception("pipe", created.errno)
                p.getInt(0) to p.getInt(4)
            }
        }
        val seen = mutableListOf<Int>()
        try {
            pipes.forEachIndexed { index, (rd, _) -> manager.register(rd, 1) { _, _ -> seen.add(index) } }
            for ((_, wr) in pipes) Memory(1).use { p ->
                p.setByte(0, 1)
                val written = NetworkPosixSocketCalls.call("write", arrayOf(wr, p, NativeLong(1)))
                if (written.value == -1L) throw GhcPosixErrno.exception("write", written.errno)
            }
            val steps = mutableListOf<Int>()
            while (seen.size < pipes.size) {
                val before = seen.size; manager.step(); steps.add(seen.size - before)
            }
            return buildJsonObject { put("steps", JsonArray(steps.map(::JsonPrimitive))); put("ids", JsonArray(seen.sorted().map(::JsonPrimitive))) }
        } finally {
            for ((rd, wr) in pipes) {
                manager.closeFdWith(rd) { NetworkPosixSocketCalls.call("close", arrayOf(rd)) }
                NetworkPosixSocketCalls.call("close", arrayOf(wr))
            }
            manager.cleanup()
        }
    }
    private fun fixtures(platform: String): List<DynamicTest> {
        if (!Platform.isMac() && !Platform.isLinux()) return emptyList()
        val abi = GhcEventAbi.load(platform)
        val stream = checkNotNull(javaClass.getResourceAsStream("/ghc-event/$platform.jsonl.gz"))
        return GZIPInputStream(stream).bufferedReader().use { reader ->
            reader.readLine(); reader.lineSequence().mapIndexedNotNull { index, line ->
                val row = Json.parseToJsonElement(line).jsonObject; val request = row.getValue("request").jsonObject
                if (text(request, "api") != "program" && platform != current) null
                else DynamicTest.dynamicTest("$platform $index ${text(row, "group")}") {
                    assertEquals(row.getValue("expected"), run(request, abi))
                }
            }.toList()
        }
    }
    @TestFactory fun originalMacOs() = fixtures("macos-64")
    @TestFactory fun originalLinux() = fixtures("linux-64")
}
