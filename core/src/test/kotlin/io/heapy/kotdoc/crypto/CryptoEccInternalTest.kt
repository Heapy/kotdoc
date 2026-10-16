package io.heapy.kotdoc.crypto

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.stream.Stream
import java.util.zip.GZIPInputStream

class CryptoEccInternalTest {
    private fun JsonObject.text(k: String)=get(k)?.jsonPrimitive?.content ?: ""
    private fun arg(r: JsonObject,k: String): Lazy<BigInteger> = lazy {
        val s=r.text(k)
        if(s.startsWith("bottom:")) error(s.removePrefix("bottom:"))
        if(s.isEmpty()) BigInteger.ZERO else s.toBigInteger()
    }
    private fun bool(r: JsonObject,k: String): Lazy<Boolean> = lazy {
        val s=r.text(k)
        if(s.startsWith("bottom:")) error(s.removePrefix("bottom:"))
        s=="true"
    }
    private fun bytes(a: ByteArray)=JsonArray(a.map { JsonPrimitive(it.toInt() and 255) })
    private fun tableByte(t: CryptoEccInternal.Table,i: Int)=(t.words[t.offset+i/8] ushr (8*(i%8))).toByte()
    private fun hash(t: CryptoEccInternal.Table): String {
        val h=MessageDigest.getInstance("SHA-256")
        for(i in 0..<t.size) h.update(tableByte(t,i))
        return h.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private fun tableValue(t: CryptoMaybe<CryptoEccInternal.Table>,part: String): JsonObject = when(t) {
        CryptoMaybe.None->buildJsonObject { put("maybe","none") }
        is CryptoMaybe.Some->buildJsonObject { put("maybe","some");if(part!="constructor") put("hash",hash(t.value)) }
    }
    private fun point(p: EccMulResult,part: String): JsonElement = when(p) {
        EccMulResult.Infinity->JsonArray(listOf(JsonPrimitive("infinity")))
        EccMulResult.Unsupported->JsonArray(listOf(JsonPrimitive("unsupported")))
        is EccMulResult.Point->if(part=="constructor") JsonPrimitive("point") else JsonArray(listOf(p.x,p.y).map { JsonPrimitive(it.toString()) })
    }
    private fun memory(a: CryptoEccInternal.Audit)=buildJsonObject {
        put("events",JsonArray(a.events.map { e->buildJsonObject {
            when(e) {
                is CryptoEccInternal.Event.Allocate->put("allocate",e.size)
                is CryptoEccInternal.Event.Released->put("released",bytes(e.bytes))
                is CryptoEccInternal.Event.TableAllocate->put("tableAllocate",e.size)
            }
        } }))
        put("tables",JsonArray(a.tables.map { t->buildJsonObject {
            put("hash",hash(t))
            val guards=ByteArray(32) { i->
                val offset=if(i<16) i else t.size+i
                (t.words[offset/8] ushr (8*(offset%8))).toByte()
            }
            put("guards",bytes(guards))
        } }))
    }
    private fun capture(r: JsonObject,action: (CryptoEccInternal.Audit)->JsonElement): JsonObject {
        val poison=if(r.containsKey("poison")) r.text("poison").toInt() else 165
        val status=if(r.containsKey("status")) r.text("status").toInt() else null
        val audit=CryptoEccInternal.Audit(poison,status)
        val value=try { action(audit) } catch(e: RuntimeException) { buildJsonObject { put("fatal",e.message?.lineSequence()?.first() ?: e.javaClass.simpleName) } }
        return buildJsonObject { put("value",value);put("memory",memory(audit));put("calls",JsonArray(audit.calls.map(::JsonPrimitive))) }
    }
    private val warmCache by lazy { CryptoEccInternal.BaseTableCache().apply { warm() } }
    private fun lookup(r: JsonObject,audit: CryptoEccInternal.Audit,cache: CryptoEccInternal.BaseTableCache)=cache.lookup(arg(r,"p"),arg(r,"a"),arg(r,"b"),lazy { arg(r,"width").value.toInt() },arg(r,"x"),arg(r,"y"),audit)
    private fun ordinary(r: JsonObject,audit: CryptoEccInternal.Audit,cache: CryptoEccInternal.BaseTableCache): JsonElement {
        val part=r.text("part")
        if(part=="ignore") return JsonPrimitive("ignored")
        val p=arg(r,"p");val a=arg(r,"a");val b=arg(r,"b");val k=arg(r,"k");val x=arg(r,"x");val y=arg(r,"y");val fx=arg(r,"fx")
        val width=lazy { arg(r,"width").value.toInt() }
        return when(r.text("op")) {
            "lookup"->tableValue(lookup(r,audit,cache),part)
            "table"->when(val t=CryptoEccInternal.primeCurveTableDeferred(p,a,b,width,x,y,audit)) {
                CryptoMaybe.None->tableValue(t,part)
                is CryptoMaybe.Some->if(part=="tableOnly") tableValue(t,"value") else point(CryptoEccInternal.primeCurveTableMulDeferred(lazy { t.value },p,a,b,width,k,audit),part)
            }
            "prime"->point(CryptoEccInternal.primeCurveMulDeferred(p,a,b,width,k,x,y,audit),part)
            "tableMulBottom"->point(CryptoEccInternal.primeCurveTableMulDeferred(lazy { error("table") },p,a,b,width,k,audit),part)
            "binaryC"->point(CryptoEccInternal.binaryCurveCDeferred(fx,b,width,k,x,y,audit),part)
            "binaryH"->point(CryptoEccInternal.binaryCurveMulDeferred(fx,b,width,k,x,y),part)
            "curve"->{
                val field=lazy {
                    when(r.text("field")) {
                        "bottom"->error("field")
                        "binary"->EccCurveField.Binary(fx.value,b.value)
                        else->EccCurveField.Prime(p.value,a.value,b.value)
                    }
                }
                point(CryptoEccInternal.curveMulDeferred(field,arg(r,"order"),k,x,y,bool(r,"base"),audit,cache),part)
            }
            else->error("unknown operation")
        }
    }
    private fun scenario(r: JsonObject): JsonElement {
        val cache=CryptoEccInternal.BaseTableCache()
        var first: CryptoMaybe<CryptoEccInternal.Table>?=null
        return JsonArray(r.getValue("steps").jsonArray.map { step->
            val s=step.jsonObject
            capture(s) { audit->
                when(s.text("op")) {
                    "mutate"->when(val t=lookup(s,audit,cache)) {
                        CryptoMaybe.None->JsonNull
                        is CryptoMaybe.Some->{
                            val index=s.text("index").toInt();val at=t.value.offset+index/8
                            t.value.words[at]=t.value.words[at] xor (s.text("xor").toLong() shl (8*(index%8)))
                            JsonPrimitive("mutated")
                        }
                    }
                    "lookup"->{
                        val t=lookup(s,audit,cache);val old=first
                        if(old==null) first=t
                        val same=old==null || old===CryptoMaybe.None && t===CryptoMaybe.None || old is CryptoMaybe.Some && t is CryptoMaybe.Some && old.value===t.value
                        buildJsonObject { put("lookup",tableValue(t,s.text("part")));put("sameAsFirst",same) }
                    }
                    else->ordinary(s,audit,cache)
                }
            }
        })
    }
    private fun concurrent(r: JsonObject): JsonElement {
        val cache=CryptoEccInternal.BaseTableCache()
        return capture(r) { audit->
            val pool=Executors.newFixedThreadPool(r.text("threads").toInt())
            val values=try { pool.invokeAll(List(r.text("threads").toInt()) { Callable { lookup(r,audit,cache) } }).map { it.get() } } finally { pool.shutdown() }
            val first=values.first()
            val same=values.all { v->first is CryptoMaybe.Some && v is CryptoMaybe.Some && first.value===v.value || first===CryptoMaybe.None && v===CryptoMaybe.None }
            buildJsonObject { put("same",same);put("some",first is CryptoMaybe.Some) }
        }
    }
    private fun observe(r: JsonObject): JsonElement = when(r.text("op")) {
        "cacheScenario"->scenario(r)
        "cacheConcurrent"->concurrent(r)
        else->capture(r) { ordinary(r,it,warmCache) }
    }
    @TestFactory
    fun upstream(): Stream<DynamicTest> {
        val reader=GZIPInputStream(javaClass.getResourceAsStream("/crypto-ecc-wrapper/data.jsonl.gz")!!).bufferedReader()
        reader.readLine();var index=0
        return reader.lines().map { line->
            val id=index++
            DynamicTest.dynamicTest("$id crypton ECC wrapper") {
                val row=Json.parseToJsonElement(line).jsonObject
                val request=row.getValue("request").jsonObject;val expected=row.getValue("expected")
                val actual=observe(request)
                if(request["mathCheck"]?.jsonPrimitive?.boolean==true) {
                    assertEquals(expected.jsonObject.getValue("observed"),actual,"$id ${row.text("group")} $request")
                    assertEquals(expected.jsonObject.getValue("math"),actual.jsonObject.getValue("value"),"$id independent affine result")
                } else assertEquals(expected,actual,"$id ${row.text("group")} $request")
            }
        }.onClose { reader.close() }
    }
}
