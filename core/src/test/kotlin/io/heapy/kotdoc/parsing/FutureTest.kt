package io.heapy.kotdoc.parsing

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Test
import java.util.zip.GZIPInputStream
import java.util.concurrent.Executors
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** Lazy datatype/function dictionaries and JSON transport, not expected results. */
class FutureTest {
    private sealed interface D {
        data class N(val value: Deferred<Long>): D
        data class S(val value: String): D
        data class A(val value: JsonElement): D
        data class L(val values: Deferred<Spine>): D
        data class Pair(val first: Deferred<D>,val second: Deferred<D>): D
        data class O(val fields: List<kotlin.Pair<String,Deferred<D>>>): D
    }
    private sealed interface Spine {
        data class Cons(val head: Deferred<D>,val tail: Deferred<Spine>): Spine
        data object Nil: Spine
    }
    private fun j(vararg v: JsonElement)=JsonArray(v.toList())
    private fun tag(s: String,vararg v: JsonElement)=j(JsonPrimitive(s),*v)
    private fun JsonElement.s()=jsonPrimitive.content
    private fun JsonElement.n()=jsonPrimitive.long
    private fun list(xs: List<Deferred<D>>,at: Int=0): Deferred<Spine> = Deferred.defer {
        Deferred.now(if(at==xs.size) Spine.Nil else Spine.Cons(xs[at],list(xs,at+1)))
    }
    private fun datum(v: JsonElement): Deferred<D> = Deferred.defer {
        val a=v.jsonArray
        when(a[0].s()) {
            "n"->Deferred.now(D.N(Deferred.now(a[1].n())));"s"->Deferred.now(D.S(a[1].s()));"a"->Deferred.now(D.A(a[1]))
            "l"->Deferred.now(D.L(list(a[1].jsonArray.map(::datum))))
            "p"->Deferred.now(D.Pair(datum(a[1]),datum(a[2])))
            "o"->Deferred.now(D.O(a[1].jsonArray.map { it.jsonArray.let { x -> x[0].s() to datum(x[1]) } }))
            "cons"->Deferred.now(D.L(Deferred.now(Spine.Cons(datum(a[1]),spine(datum(a[2]))))))
            "self"->{ lateinit var loop: Deferred<D>;loop=Deferred.defer { loop };loop }
            "repeat"->{
                val head=datum(a[1]);lateinit var loop: Deferred<Spine>
                loop=Deferred.later { Spine.Cons(head,loop) };Deferred.now(D.L(loop))
            }
            "err"->Deferred.later { error(a[1].s()) }
            else->error(v)
        }
    }
    private fun enc(v: D): JsonElement=when(v) {
        is D.N->tag("n",JsonPrimitive(v.value.value()));is D.S->tag("s",JsonPrimitive(v.value));is D.A->tag("a",v.value)
        is D.Pair->tag("p",enc(v.first.value()),enc(v.second.value()))
        is D.O->tag("o",JsonArray(v.fields.map { j(JsonPrimitive(it.first),enc(it.second.value())) }))
        is D.L->{
            val xs=mutableListOf<JsonElement>();var current=v.values
            while(true)when(val cell=current.value()) { Spine.Nil->break;is Spine.Cons->{ xs.add(enc(cell.head.value()));current=cell.tail } }
            tag("l",JsonArray(xs))
        }
    }
    private fun number(v: Deferred<D>): Deferred<Long> = v.flatMapValue { if(it is D.N) it.value else error("not number") }
    private fun spine(v: Deferred<D>): Deferred<Spine> = v.flatMapValue { if(it is D.L) it.values else error("not list") }
    private fun nth(xs: Deferred<Spine>,index: Long): Deferred<D> = Deferred.defer {
        if(index<0)error("Prelude.!!: negative index")
        xs.flatMapValue { when(it) { Spine.Nil->error("Prelude.!!: index too large");is Spine.Cons->if(index==0L) it.head else nth(it.tail,index-1) } }
    }
    private fun length(xs: Deferred<Spine>,n: Long=0): Deferred<D> = xs.flatMapValue {
        when(it) { Spine.Nil->Deferred.now(D.N(Deferred.now(n)));is Spine.Cons->length(it.tail,n+1) }
    }
    private fun concat(left: Deferred<Spine>,right: Deferred<Spine>): Deferred<Spine> = left.flatMapValue {
        when(it) { Spine.Nil->right;is Spine.Cons->Deferred.now(Spine.Cons(it.head,Deferred.defer { concat(it.tail,right) })) }
    }
    private fun fn(f: JsonElement,x: Deferred<D>): Deferred<D> = Deferred.defer {
        val a=f.jsonArray
        when(a[0].s()) {
            "id"->x;"const"->datum(a[1]);"err"->Deferred.later { error(a[1].s()) }
            "add"->Deferred.now(D.N(number(x).mapValue { it+a[1].n() }));"times"->Deferred.now(D.N(number(x).mapValue { it*a[1].n() }))
            "negate"->Deferred.now(D.N(number(x).mapValue { -it }))
            "first","second"->x.flatMapValue { if(it is D.Pair) { if(a[0].s()=="first") it.first else it.second } else error("not pair") }
            "field"->x.flatMapValue { if(it is D.O) it.fields.firstOrNull { e->e.first==a[1].s() }?.second ?: error("missing field") else error("not object") }
            "head","tail"->spine(x).flatMapValue { when(it) { Spine.Nil->error("empty list");is Spine.Cons->if(a[0].s()=="head") it.head else Deferred.now(D.L(it.tail)) } }
            "length"->Deferred.now(D.N(length(spine(x)).flatMapValue { (it as D.N).value }));"nth"->x.flatMapValue { if(it is D.L) nth(it.values,a[1].n()) else error("not list") }
            "pair"->Deferred.now(D.Pair(fn(a[1],x),fn(a[2],x)))
            "list"->Deferred.now(D.L(list(a[1].jsonArray.map { fn(it,x) })))
            "compose"->fn(a[1],fn(a[2],x))
            else->error(f)
        }
    }
    private fun function(v: JsonElement): Future<D,(Deferred<D>)->Deferred<D>> {
        val a=v.jsonArray
        return when(a[0].s()) {
            "pure"->Future.pure { x->fn(a[1],x) }
            "env"->Future.asksF { state->Deferred.now { x->fn(a[1],Deferred.now(D.Pair(state,x))) } }
            "mapped"->core(a[2]).map { v0->Deferred.now { x->fn(a[1],Deferred.now(D.Pair(v0,x))) } }
            else->error(v)
        }
    }
    private fun core(v: JsonElement): Future<D,D> {
        val a=v.jsonArray
        return when(a[0].s()) {
            "pure"->Future.pureDeferred(datum(a[1]));"ask"->Future.askF()
            "asks"->Future.asksF { fn(a[1],it) };"constructor"->Future { fn(a[1],it) }
            "fail"->Future { Deferred.later { error(a[1].s()) } }
            "map"->core(a[2]).map { fn(a[1],it) }
            "replace"->core(a[2]).replace(datum(a[1]))
            "bind"->core(a[1]).flatMap { Future.pureDeferred(fn(a[2],it)) }
            "bind-env"->core(a[1]).flatMap { x->Future.asksF { s->fn(a[2],Deferred.now(D.Pair(x,s))) } }
            "ap"->Future.apply(function(a[1]),core(a[2]))
            "lift"->core(a[2]).liftA2(core(a[3])) { x,y->fn(a[1],Deferred.now(D.Pair(x,y))) }
            "then"->core(a[1]).then(core(a[2]));"before"->core(a[1]).before(core(a[2]))
            "append"->core(a[1]).append(core(a[2])) { x,y->Deferred.now(D.L(concat(spine(x),spine(y)))) }
            "sum","product"->core(a[1]).append(core(a[2])) { x,y->Deferred.now(D.N(number(x).flatMapValue { n->number(y).mapValue { if(a[0].s()=="sum") n+it else n*it } })) }
            "append-left"->core(a[1]).append(core(a[2])) { x,_->x }
            "append-right"->core(a[1]).append(core(a[2])) { _,y->y }
            "empty"->Future.mempty(Deferred.now(D.L(Deferred.now(Spine.Nil))))
            "empty-sum"->Future.mempty(Deferred.now(D.N(Deferred.now(0L))))
            "empty-product"->Future.mempty(Deferred.now(D.N(Deferred.now(1L))))
            else->error(v)
        }
    }
    private fun operation(v: JsonElement): JsonElement {
        val a=v.jsonArray
        return when(a[0].s()) {
            "nullable"->{
                val base=Future.asksValue<Long,Deferred<Long>?> { if(it<0) null else Deferred.now(it) }
                val action: Future<Long,Deferred<Long>?> = when(a[1].s()) {
                    "nothing","empty"->Future.mempty(Deferred.now(null))
                    "ask"->base
                    "map"->base.mapValue { it?.mapValue { n->n+1 } }
                    "drop"->base.map { Deferred.now(null) }
                    "bind"->base.flatMapValue { value -> if(value==null) Future.pure(null) else Future.asksF { state->Deferred.now(value.flatMapValue { n->state.mapValue { n+it } }) } }
                    "sum"->base.append(Future.pure(Deferred.now(100L))) { x,y->x.flatMapValue { left->if(left==null)y else y.mapValue { right->if(right==null)left else left.flatMapValue { n->right.mapValue { n+it } } } } }
                    else->error(v)
                }
                action.runF(a[2].n())?.value()?.let(::JsonPrimitive) ?: JsonNull
            }
            "memo"->{
                var calls=0
                val d=Deferred.later<D> { calls++;if(a[1].s()=="failure") error("shared failure") else D.N(Deferred.now(42L)) }
                val p=Future.pureDeferred<Unit,D>(d)
                fun probe(): JsonElement=try { JsonPrimitive((p.runFLazy(Deferred.later { error("state") }) as D.N).value.value()) } catch(e: IllegalStateException) { buildJsonObject { put("error",checkNotNull(e.message)) } }
                val first=probe();val second=probe()
                buildJsonObject { put("first",first);put("second",second);put("calls",calls) }
            }
            "return-shape"->Future.returnF<D,D,JsonElement>(datum(a[2])) { result -> when(a[1].s()) {
                "maybe"->JsonPrimitive("just");"list"->JsonPrimitive(1);"proxy"->JsonPrimitive("proxy")
                "identity","io"->{ result.value();JsonPrimitive(a[1].s()) };else->error(v)
            } }
            "run"->enc(core(a[1]).runFLazy(datum(a[2])))
            "runDelayed"->enc(core(a[1]).runDelayed(datum(a[2])).value())
            "multi"->{ val p=core(a[1]);JsonArray(a[2].jsonArray.map { enc(p.runFLazy(datum(it))) }) }
            "return"->{
                val value=datum(a[2]);val state=datum(a[3])
                when(a[1].s()) {
                    "proxy"->Future.returnF<D,D,JsonElement>(value) { JsonPrimitive("proxy") }
                    "maybe"->Future.returnF<D,D,JsonElement>(value) { tag("just",enc(it.value().runFLazy(state))) }
                    "list"->Future.returnF<D,D,JsonElement>(value) { j(enc(it.value().runFLazy(state))) }
                    "identity","io"->Future.returnF<D,D,JsonElement>(value) { enc(it.value().runFLazy(state)) }
                    else->error(v)
                }
            }
            "deep"->{
                var p=Future.askF<D>()
                repeat(a[2].jsonPrimitive.int) { p=when(a[1].s()) {
                    "map"->p.mapValue { D.N((it as D.N).value.mapValue { n->n+1 }) }
                    "bind"->p.flatMapValue { Future.pure(D.N((it as D.N).value.mapValue { n->n+1 })) }
                    "map-lazy"->p.map { fn(tag("add",JsonPrimitive(1)),it) }
                    "bind-lazy"->p.flatMap { Future.pureDeferred(fn(tag("add",JsonPrimitive(1)),it)) }
                    "then"->Future.pureDeferred<D,D>(Deferred.later { error("ignored") }).then(p)
                    "before"->p.before(Future.pureDeferred<D,D>(Deferred.later { error("ignored") }))
                    else->error(v)
                } }
                enc(p.runFLazy(datum(a[3])))
            }
            else->error(v)
        }
    }
    private fun canonical(v: JsonElement): JsonElement=when(v) {
        is JsonArray->JsonArray(v.map(::canonical));is JsonObject->JsonObject(v.mapValues { canonical(it.value) })
        is JsonPrimitive->if(v.isString || v==JsonNull || v.booleanOrNull!=null) v else Json.parseToJsonElement(v.content.toBigDecimal().stripTrailingZeros().toPlainString())
    }
    @TestFactory fun originalObservations(): List<DynamicTest> =
        GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/parsing/future.jsonl.gz"))).bufferedReader().use { reader ->
            reader.lineSequence().drop(1).mapIndexed { index,line->DynamicTest.dynamicTest("original Future $index") {
                val c=Json.parseToJsonElement(line).jsonObject
                val result=try { operation(c.getValue("input")) } catch(e: IllegalStateException) { buildJsonObject { put("error",checkNotNull(e.message)) } }
                assertEquals(canonical(c.getValue("expected")),canonical(result),"case $index: ${c.getValue("input")}")
            } }.toList()
        }
    @Test fun sharedDemandAcrossConsumers() {
        val calls=AtomicInteger();val value=Deferred.later { calls.incrementAndGet();42 }
        val pool=Executors.newFixedThreadPool(8)
        try { val results=pool.invokeAll(List(32) { Callable { Future.pureDeferred<Unit,Int>(value).runF(Unit) } });assertEquals(List(32) { 42 },results.map { it.get() }) }
        finally { pool.shutdownNow() }
        assertEquals(1,calls.get())
        val error=IllegalStateException("shared failure");val attempts=AtomicInteger()
        val bad=Deferred.later<Int> { attempts.incrementAndGet();throw error }
        repeat(2) { val caught=try { bad.value();null } catch(e: IllegalStateException) { e };assertSame(error,caught) }
        assertEquals(1,attempts.get())
    }
}
