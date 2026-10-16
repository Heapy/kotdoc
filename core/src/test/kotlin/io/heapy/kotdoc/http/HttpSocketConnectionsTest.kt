package io.heapy.kotdoc.http

import io.heapy.kotdoc.errors.GhcIoError
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.Collections
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream

class HttpSocketConnectionsTest {
    private fun text(v: JsonObject,k: String,default: String="")=v[k]?.takeUnless {it is JsonNull}?.jsonPrimitive?.content ?: default
    private fun bool(v: JsonObject,k: String)=v[k]?.jsonPrimitive?.booleanOrNull ?: false
    private fun number(v: JsonObject,k: String,default: Long)=v[k]?.jsonPrimitive?.longOrNull ?: default
    private fun bytes(v: JsonElement)=HttpBytes(v.jsonArray.map {it.jsonPrimitive.int.toByte()}.toByteArray())
    private fun json(b: HttpBytes)=JsonArray(b.bytes().map {JsonPrimitive(it.toInt() and 255)})
    private class Fatal(message: String): RuntimeException(message)
    private class Async: CancellationException()
    private fun raise(id: Int,tag: String): Nothing=when(tag) {
        "io"->throw HttpGhcIOException(GhcIoError.userError("socket-io-$id"))
        "async"->throw Async()
        else->throw Fatal(tag)
    }
    private fun failure(e: Throwable)=buildJsonObject {
        when(e) {
            is HttpGhcIOException->{put("kind","io");put("show",e.error.toString())}
            is HttpContentException->{put("kind","content");put("show",e.failure.show())}
            is Async,is InterruptedException->put("kind","async")
            is Fatal,is IllegalStateException->{put("kind","fatal");put("show",e.message)}
            else->throw e
        }
    }
    private fun attempt(action: ()->JsonElement): JsonElement=try {action()} catch(e: Throwable) {buildJsonObject {put("error",failure(e))}}
    private class Gate {val entered=CountDownLatch(1);val permit=LinkedBlockingQueue<Unit>()}
    private data class Address(val id: Int,val ipv6: Boolean=false,val port: UShort=id.toUShort(),val host: UInt=id.toUInt()) {
        fun show()=if(ipv6) "[::${if(id==0) "" else id.toString(16)}]:$port" else (0..3).joinToString(".") {((host shr (it*8)) and 255u).toString()}+":$port"
    }
    private fun observe(v: JsonObject): JsonElement {
        val events=Collections.synchronizedList(mutableListOf<JsonObject>())
        val gates=ConcurrentHashMap<Pair<String,Int>,Gate>()
        fun gate(phase: String,id: Int)=gates.computeIfAbsent(phase to id) {Gate()}
        fun event(id: Int,phase: String,fields: JsonObject=buildJsonObject {}) {
            events.add(buildJsonObject {put("id",id);put("phase",phase);fields.forEach { (k,value)->put(k,value) }})
            gate(phase,id).entered.countDown()
        }
        fun waitPermit(phase: String,id: Int) {val gate=gate(phase,id);gate.entered.countDown();gate.permit.take()}
        val attempts=v["attempts"]?.jsonArray?.map {it.jsonObject} ?: listOf(buildJsonObject {})
        fun config(id: Int)=if(id<0) buildJsonObject {} else attempts[id]
        fun phaseFailure(id: Int,phase: String) {val cfg=config(id);if(text(cfg,"failPhase")==phase) raise(id,text(cfg,"failure","io"))}
        fun <A> perform(id: Int,phase: String,fields: JsonObject=buildJsonObject {},action: ()->A): A {event(id,phase,fields);phaseFailure(id,phase);return action()}
        val runtime=HttpSocketRaceRuntime {microseconds->
            val id=(microseconds/250000).toInt();event(id,"delay",buildJsonObject {put("microseconds",microseconds)})
            if(bool(v,"controlled")) waitPermit("delay",id) else TimeUnit.MICROSECONDS.sleep(microseconds)
        }
        val backend=object: HttpSocketBackend<Int,Address> {
            override fun resolve(host: String,service: String): List<Address> = perform(-1,"resolve",buildJsonObject {put("host",host);put("service",service)}) {
                v["resolveFailure"]?.jsonPrimitive?.content?.let {raise(-1,it)}
                attempts.indices.map {Address(it,bool(attempts[it],"ipv6"))}
            }
            override fun ipv4(port: UShort,address: UInt)=Address(0,false,port,address)
            override fun create(address: Address)=perform(address.id,"create",buildJsonObject {put("family",if(address.ipv6) "AF_INET6" else "AF_INET");put("type","Stream");put("protocol",6+address.id)}) {address.id}
            override fun noDelay(socket: Int,value: Int)=perform(socket,"no-delay",buildJsonObject {put("option","NoDelay");put("value",value)}) {}
            override fun connect(socket: Int,address: Address)=perform(socket,"connect",buildJsonObject {put("address",address.show())}) {
                if(bool(v,"controlled")) waitPermit("attempt",socket)
                val result=text(config(socket),"result","success");if(result!="success") raise(socket,result)
            }
            override fun close(socket: Int)=perform(socket,"close") {}
            override fun receive(socket: Int,chunkSize: Long)=perform(socket,"receive",buildJsonObject {put("size",chunkSize)}) {HttpBytes(ByteArray(256) {it.toByte()})}
            override fun sendAll(socket: Int,bytes: HttpBytes)=perform(socket,"send",buildJsonObject {put("bytes",json(bytes))}) {}
        }
        val tweak: (Int)->Unit = {perform(it,"tweak") {}}
        val address=v["address"]?.takeUnless {it is JsonNull}?.jsonPrimitive?.long?.toUInt()
        val host=text(v,"host","localhost");val port=number(v,"port",80)
        fun action(): JsonElement=when(text(v,"api")) {
            "racer"->JsonPrimitive(HttpSocketConnections.firstSuccessful(attempts.indices.toList(),runtime) {id->
                event(id,"attempt")
                if(bool(v,"controlled")) waitPermit("attempt",id)
                try {val result=text(config(id),"result","success");if(result!="success") raise(id,result);id}
                finally {event(id,"finished")}
            })
            "with"->HttpSocketConnections.withSocket(backend,tweak,address,host,port,runtime) {id->perform(id,"final") {JsonPrimitive(id)}}
            "open"->{
                val conn=if(bool(v,"managerFactory")) {
                    if(bool(v,"defaultSize")) HttpManagers.rawConnectionModifySocket(backend,tweak,runtime)().connect(address,host,port)
                    else HttpManagers.rawConnectionModifySocketSize(backend,tweak,runtime)().connect(number(v,"chunkSize",8192),address,host,port)
                } else if(bool(v,"defaultSize")) HttpSocketConnections.openSocketConnection(backend,tweak,address,host,port,runtime)
                    else HttpSocketConnections.openSocketConnectionSize(backend,tweak,number(v,"chunkSize",8192),address,host,port,runtime)
                JsonArray(v.getValue("commands").jsonArray.map {item->val c=item.jsonObject;attempt {
                    when(text(c,"op")) {
                        "read"->json(conn.read())
                        "write"->{conn.write(bytes(c.getValue("bytes")));JsonNull}
                        "unread"->{conn.unread(bytes(c.getValue("bytes")));JsonNull}
                        "close"->{conn.close();JsonNull}
                        else->error(c)
                    }
                }})
            }
            else->error(v)
        }
        val future=CompletableFuture<JsonElement>()
        val worker=Thread.ofVirtual().name("kotdoc-socket-test-driver").unstarted {
            try {future.complete(attempt(::action))} catch(e: Throwable) {future.completeExceptionally(e)}
        }
        worker.start()
        fun join() {worker.join(10000);assertFalse(worker.isAlive,"socket action/children did not terminate")}
        val result: JsonElement
        try {
            for(item in v["steps"]?.jsonArray ?: emptyList()) {
                val s=item.jsonObject;val g=gate(text(s,"phase"),number(s,"id",0).toInt())
                assertTrue(g.entered.await(10,TimeUnit.SECONDS),"missing step $s in $v")
                if(!bool(s,"awaitOnly")) g.permit.put(Unit)
            }
            result=if(bool(v,"interrupt")) {worker.interrupt();join();buildJsonObject {put("error",buildJsonObject {put("kind","async")})}}
            else if(bool(v,"blocked")) {
                try {future.get(30,TimeUnit.MILLISECONDS)} catch(_: TimeoutException) {worker.interrupt();join();buildJsonObject {put("blocked",true)}}
            } else future.get(10,TimeUnit.SECONDS)
        } finally {if(worker.isAlive) worker.interrupt();join()}
        val snapshot=synchronized(events) {events.toList()}
        val grouped=JsonArray(snapshot.groupBy {it.getValue("id").jsonPrimitive.int}.toSortedMap().map { (id,events)->buildJsonObject {put("id",id);put("events",JsonArray(events))} })
        return buildJsonObject {put("result",result);put("events",grouped)}
    }
    @TestFactory
    fun originalSocketObservations(): List<DynamicTest> = GZIPInputStream(javaClass.getResourceAsStream("/http-socket/data.jsonl.gz")!!).bufferedReader().use {r->
        r.readLine();r.lineSequence().mapIndexed {i,line->val row=Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest("$i ${row.getValue("group").jsonPrimitive.content}") {assertEquals(row.getValue("expected"),observe(row.getValue("request").jsonObject))}
        }.toList()
    }
    @Test
    fun allFailuresRethrowTheExactLastInputIOException() {
        val failures=List(4) {java.io.IOException("$it")}
        assertSame(failures.last(),assertThrows(java.io.IOException::class.java) {
            HttpSocketConnections.firstSuccessful(failures,HttpSocketRaceRuntime {}) {throw it}
        })
    }
    @Test
    fun nullIsARealSuccessfulResultAndNoInheritableThreadStateIsCopied() {
        val state=InheritableThreadLocal<String>();state.set("caller")
        try {assertNull(HttpSocketConnections.firstSuccessful(listOf(0)) {assertNull(state.get());null})}
        finally {state.remove()}
    }
    @Test
    fun successWaitsForCancelledSiblingCleanupBeforeReturning() {
        val entered=CountDownLatch(1);val cancelled=CountDownLatch(1);val cleanupDone=AtomicInteger()
        val result=HttpSocketConnections.firstSuccessful(listOf(0,1),HttpSocketRaceRuntime {}) {i->
            if(i==0) {assertTrue(entered.await(5,TimeUnit.SECONDS));9}
            else try {entered.countDown();CountDownLatch(1).await();0} finally {
                cancelled.countDown();Thread.sleep(20);cleanupDone.incrementAndGet()
            }
        }
        assertEquals(9,result);assertEquals(0,cancelled.count);assertEquals(1,cleanupDone.get())
    }
    @Test
    fun actualSystemRuntimeStartsTheSecondAttemptAfterAQuarterSecond() {
        val started=System.nanoTime()
        val result=HttpSocketConnections.firstSuccessful(listOf(0,1)) {i->
            if(i==0) {CountDownLatch(1).await();0} else i
        }
        val elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)
        assertEquals(1,result);assertTrue(elapsed>=200,"second attempt started too early: $elapsed ms")
    }
}
