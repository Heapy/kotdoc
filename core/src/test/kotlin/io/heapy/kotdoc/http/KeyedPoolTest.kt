package io.heapy.kotdoc.http

import io.heapy.kotdoc.resources.PandocUtcTime
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import java.math.BigInteger
import java.util.Collections
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import java.util.zip.GZIPInputStream

class KeyedPoolTest {
    private class ScriptFailure(message: String) : RuntimeException(message)
    private fun raise(tag: String): Nothing = when(tag) {
        "async"->throw CancellationException()
        "io"->throw java.io.IOException("pool-io")
        else->throw ScriptFailure(tag)
    }
    private fun failure(e: Throwable) = buildJsonObject {
        when(e) {
            is CancellationException,is InterruptedException->put("error","async")
            is java.io.IOException->{put("error","io");put("message",e.message)}
            is ScriptFailure->{put("error","script");put("message",e.message)}
            else->throw e
        }
    }
    private fun attempt(action: ()->JsonElement): JsonElement = try {action()} catch(e: Throwable) {failure(e)}
    private fun utc(v: JsonElement) = v.jsonArray.let {PandocUtcTime(BigInteger(it[0].jsonPrimitive.content),BigInteger(it[1].jsonPrimitive.content))}
    private fun json(t: PandocUtcTime) = JsonArray(listOf(JsonPrimitive(t.day.toString()),JsonPrimitive(t.dayPicoseconds.toString())))
    private fun bool(v: JsonObject,key: String) = v[key]?.jsonPrimitive?.boolean ?: false
    private fun text(v: JsonObject,key: String,default: String) = v[key]?.jsonPrimitive?.content ?: default
    private inner class Runtime(val controlled: Boolean,val log: (JsonElement)->Unit) : KeyedPoolRuntime {
        val time=AtomicReference(PandocUtcTime(BigInteger.valueOf(60000),BigInteger.ZERO))
        val nextFailure=AtomicReference<String?>()
        private val finals=mutableListOf<Pair<AtomicBoolean,()->Unit>>()
        private val permit=LinkedBlockingQueue<Unit>()
        private val feedback=LinkedBlockingQueue<String>()
        private val finished=AtomicBoolean()
        override fun now(): PandocUtcTime {
            if(!controlled) return KeyedPoolSystemRuntime.now()
            val current=time.get();log(buildJsonObject {put("event","time");put("value",json(current))})
            nextFailure.getAndSet(null)?.let(::raise)
            return current
        }
        override fun registerCleanup(owner: Any,action: ()->Unit) {
            if(!controlled) {KeyedPoolSystemRuntime.registerCleanup(owner,action);return}
            val index=synchronized(finals) {val index=finals.size;finals.add(AtomicBoolean() to action);index}
            log(buildJsonObject {put("event","register");put("index",index)})
        }
        override fun startReaper(action: ()->Unit) {
            if(!controlled) {KeyedPoolSystemRuntime.startReaper(action);return}
            log(buildJsonObject {put("event","fork")})
            Thread.ofVirtual().name("kotdoc-pool-test-reaper").uncaughtExceptionHandler {_,_->}.start {
                try {action()} finally {finished.set(true);feedback.put("finished")}
            }
        }
        override fun delayReaper() {
            if(!controlled) {KeyedPoolSystemRuntime.delayReaper();return}
            feedback.put("delay");permit.take()
        }
        fun await(): String = feedback.poll(10,TimeUnit.SECONDS) ?: throw AssertionError("reaper did not reach its next boundary")
        fun signal() {permit.put(Unit)}
        fun fire(index: Int) {val (fired,action)=synchronized(finals) {finals[index]};if(fired.compareAndSet(false,true))action()}
        fun stop() {
            if(!controlled) return
            fire(0)
            if(!finished.get()) {signal();assertEquals("finished",await())}
        }
    }
    private data class Worker(val thread: Thread,val result: CompletableFuture<JsonElement>)
    private fun json(h: KeyedManaged<Long?>) = buildJsonObject {put("resource",h.resource?.let(::JsonPrimitive) ?: JsonNull);put("reused",h.reused)}
    private fun observe(v: JsonObject): JsonElement {
        val events=Collections.synchronizedList(mutableListOf<JsonElement>())
        val runtime=Runtime(v.getValue("engine").jsonPrimitive.content=="controlled") {events.add(it)}
        val counter=AtomicLong();val handles=ConcurrentHashMap<String,KeyedManaged<Long?>>()
        val workers=ConcurrentHashMap<String,Worker>();val blocked=LinkedBlockingQueue<JsonElement>();val gate=LinkedBlockingQueue<Unit>()
        fun numbers(key: String) = v[key]?.jsonArray?.map {it.jsonPrimitive.long} ?: emptyList()
        fun block(phase: String,n: Long) {
            if(n in numbers(if(phase=="create") "blockCreate" else "blockDestroy")) {
                blocked.put(JsonArray(listOf(JsonPrimitive(phase),JsonPrimitive(n))));gate.take()
            }
        }
        val poolRef=AtomicReference<KeyedPool<Long,Long?>?>(KeyedPool.createKeyedPool<Long,Long?>(
            create={key->
                val n=counter.incrementAndGet();events.add(buildJsonObject {put("event","create");put("key",key);put("resource",n)})
                block("create",n)
                if(n in numbers("createFailures")) raise(text(v,"createFailureKind","create-failure"))
                if(bool(v,"nullValues")) null else n
            },destroy={r->
                events.add(buildJsonObject {put("event","destroy");put("resource",r?.let(::JsonPrimitive) ?: JsonNull)})
                r?.let {block("destroy",it)}
                if(bool(v,"destroyAllFailure") || r in numbers("destroyFailures")) raise(text(v,"destroyFailureKind","destroy-failure"))
            },maxPerKey=v.getValue("maxPerKey").jsonPrimitive.long,maxTotal=v.getValue("maxTotal").jsonPrimitive.long,
            onReaperException={e->
                events.add(buildJsonObject {put("event","reaper-error");put("exception",failure(e))})
                v["reaperHandlerFailure"]?.jsonPrimitive?.content?.let(::raise)
            },runtime=runtime))
        if(runtime.controlled) assertEquals("delay",runtime.await())
        fun take(c: JsonObject): JsonElement {
            val h=poolRef.get()!!.take(c.getValue("key").jsonPrimitive.long)
            handles[c.getValue("as").jsonPrimitive.content]=h
            return json(h)
        }
        fun release(c: JsonObject): JsonElement {
            handles.getValue(c.getValue("handle").jsonPrimitive.content).release(if(bool(c,"reuse"))HttpReuse.Reuse else HttpReuse.DontReuse)
            return JsonNull
        }
        fun start(c: JsonObject,action: ()->JsonElement): JsonElement {
            val future=CompletableFuture<JsonElement>()
            val thread=Thread.ofVirtual().name("kotdoc-pool-test-worker").unstarted {future.complete(attempt(action))}
            workers[c.getValue("worker").jsonPrimitive.content]=Worker(thread,future);thread.start()
            return JsonNull
        }
        try {
            val results=v.getValue("commands").jsonArray.map {item->val c=item.jsonObject;attempt {
                when(c.getValue("op").jsonPrimitive.content) {
                    "take"->take(c)
                    "release"->release(c)
                    "keep"->{handles.getValue(c.getValue("handle").jsonPrimitive.content).keepAlive();JsonNull}
                    "peek"->json(handles.getValue(c.getValue("handle").jsonPrimitive.content))
                    "dummy"->{val h=KeyedPool.dummyManaged(c.getValue("resource").takeUnless {it is JsonNull}?.jsonPrimitive?.long);handles[c.getValue("as").jsonPrimitive.content]=h;json(h)}
                    "forget"->{handles.remove(c.getValue("handle").jsonPrimitive.content);JsonNull}
                    "forget-pool"->{poolRef.set(null);JsonNull}
                    "collect"->{
                        val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
                        fun destroyed()=synchronized(events) {events.count {it.jsonObject["event"]?.jsonPrimitive?.content=="destroy"}}
                        val wanted=c.getValue("destroyCount").jsonPrimitive.int
                        while(destroyed()<wanted && System.nanoTime()<deadline) {System.gc();Thread.sleep(10)}
                        JsonPrimitive(destroyed()>=wanted)
                    }
                    "time"->{runtime.time.set(utc(c.getValue("time")));JsonNull}
                    "fail-time"->{runtime.nextFailure.set(c.getValue("error").jsonPrimitive.content);JsonNull}
                    "finalize"->{runtime.fire(c.getValue("index").jsonPrimitive.int);JsonNull}
                    "tick"->{runtime.signal();JsonPrimitive(runtime.await())}
                    "start-tick"->{runtime.signal();JsonNull}
                    "await-tick"->JsonPrimitive(runtime.await())
                    "fork-take"->start(c) {take(c)}
                    "fork-release"->start(c) {release(c)}
                    "await-blocked"->blocked.poll(10,TimeUnit.SECONDS) ?: throw AssertionError("callback did not block")
                    "unblock"->{gate.put(Unit);JsonNull}
                    "join"->workers.getValue(c.getValue("worker").jsonPrimitive.content).result.get(10,TimeUnit.SECONDS)
                    "interrupt"->{workers.getValue(c.getValue("worker").jsonPrimitive.content).thread.interrupt();JsonNull}
                    else->error(c)
                }
            } }
            handles.values.forEach {it.keepAlive()}
            val snapshot=synchronized(events) {events.toList()}
            Reference.reachabilityFence(poolRef);Reference.reachabilityFence(handles)
            return buildJsonObject {put("results",JsonArray(results));put("events",JsonArray(snapshot))}
        } finally {runtime.stop()}
    }
    @TestFactory
    fun originalObservations(): List<DynamicTest> = GZIPInputStream(javaClass.getResourceAsStream("/keyed-pool/data.jsonl.gz")!!).bufferedReader().use {r->
        r.readLine();r.lineSequence().mapIndexed {index,line->val c=Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest("$index ${c.getValue("group").jsonPrimitive.content}") {assertEquals(c.getValue("expected"),observe(c.getValue("request").jsonObject))}
        }.toList()
    }
    @Test
    fun realCleanerDoesNotRetainPoolThroughCreateCallbackOrInheritedThreadContext() {
        val destroyed=CountDownLatch(1)
        val context=InheritableThreadLocal<AtomicReference<KeyedPool<Int,Int>>>()
        fun abandonedPool(): WeakReference<KeyedPool<Int,Int>> {
            val owner=AtomicReference<KeyedPool<Int,Int>>()
            context.set(owner)
            val pool=try {
                KeyedPool.createKeyedPool<Int,Int>({check(owner.get()!=null);it},{destroyed.countDown()},1,1,{throw it})
            } finally {context.remove()}
            owner.set(pool)
            val managed=pool.take(1);managed.release(HttpReuse.Reuse);managed.keepAlive()
            return WeakReference(pool)
        }
        val weak=abandonedPool();val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
        while(destroyed.count>0 && System.nanoTime()<deadline) {System.gc();Thread.sleep(10)}
        Reference.reachabilityFence(context)
        assertEquals(0,destroyed.count);assertNull(weak.get())
    }
    @Test
    fun releaseOverflowPropagatesTheOriginalExceptionObjectOnlyOnce() {
        val error=ScriptFailure("exact");val runtime=Runtime(true) {};val calls=AtomicInteger()
        val pool=KeyedPool.createKeyedPool<Int,Int>({it},{calls.incrementAndGet();throw error},0,0,{},runtime)
        assertEquals("delay",runtime.await())
        try {
            val managed=pool.take(1)
            assertSame(error,assertThrows(ScriptFailure::class.java) {managed.release(HttpReuse.Reuse)})
            managed.release(HttpReuse.DontReuse);managed.release(HttpReuse.Reuse);managed.keepAlive()
            assertEquals(1,calls.get())
        } finally {runtime.stop()}
    }
}
