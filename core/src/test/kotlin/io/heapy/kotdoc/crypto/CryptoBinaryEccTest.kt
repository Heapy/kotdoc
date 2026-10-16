package io.heapy.kotdoc.crypto

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.util.stream.Stream
import java.util.zip.GZIPInputStream

class CryptoBinaryEccTest {
    private fun JsonObject.text(k: String) = getValue(k).jsonPrimitive.content
    private fun JsonObject.number(k: String) = get(k)?.jsonPrimitive?.content?.toInt() ?: 0
    private fun bytes(r: JsonObject, k: String) = r.getValue(k).jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray()
    private fun json(a: ByteArray) = JsonArray(a.map { JsonPrimitive(it.toInt() and 255) })
    private fun words(a: LongArray) = JsonArray(a.map { JsonPrimitive(it.toULong().toString()) })
    private fun stats(a: CryptoBinaryEcc.Audit) = buildJsonObject {
        put("ops",words(a.operations)); put("ladderOps",words(a.ladderOperations))
        put("hash",a.swapAddressHash.toULong().toString()); put("reads",a.swapReads.toString())
        put("allocations",JsonArray(a.allocations.map { JsonPrimitive(it.toString()) }))
        put("frees",JsonArray(a.frees.map(::JsonPrimitive))); put("cleared",JsonArray(a.cleared.map { JsonPrimitive(if(it) 1 else 0) }))
    }
    private fun helper(r: JsonObject): JsonObject {
        val n=r.number("n"); val width=2*n+4
        val space=LongArray(4*width) { 0xa5a5a5a5a5a5a5a5uL.toLong() }
        for ((i,key) in listOf("a","b","t","out").withIndex())
            r.getValue(key).jsonArray.forEachIndexed { j,v -> space[i*width+2+j]=v.jsonPrimitive.content.toULong().toLong() }
        val a=CryptoBinaryEcc.Region(space,2); val b=a.at(width); val t=b.at(width)
        val out=when(r.number("alias")) { 1->a; 2->b; else->t.at(width) }
        val terms=r.getValue("terms").jsonArray.map { it.jsonPrimitive.int }.toIntArray()
        val audit=CryptoBinaryEcc.Audit(); val f=CryptoBinaryEcc.Field(n,r.number("m"),terms,t,audit)
        val arg=r.text("arg").toULong().toLong()
        var ret=0
        when(r.number("kind")) {
            0 -> out[0]=CryptoBinaryEcc.clmul32(a[0],b[0])
            1 -> CryptoBinaryEcc.clmul(a[0],b[0],out)
            2 -> out[0]=CryptoBinaryEcc.spread(arg)
            3 -> CryptoBinaryEcc.polyMul(out,a,b,n)
            4 -> { audit.count(7); CryptoBinaryEcc.polyMul(out,a,b,n) }
            5 -> { audit.count(8); CryptoBinaryEcc.polySquare(out,a,n) }
            6 -> { audit.count(6); CryptoBinaryEcc.reduce(out,t,n,r.number("m"),terms) }
            7 -> f.mul(out,a,b)
            8 -> f.square(out,a)
            9 -> f.add(out,a,b)
            10 -> ret=if(f.isZero(a)) 1 else 0
            11 -> f.inverse(out,a,b)
            12 -> CryptoBinaryEcc.swap(a,b,arg,n,audit)
            else -> error("unknown helper")
        }
        return buildJsonObject { put("ret",ret); put("space",words(space)); put("stats",stats(audit)) }
    }
    private class Buffer(content: ByteArray) {
        val data=byteArrayOf(17,34,51)+content+byteArrayOf(68,85,102,119)
        val span=CryptoByteSpan(data,3,content.size)
    }
    private fun observe(r: JsonObject): JsonElement {
        if(r.text("op")=="helper") return helper(r)
        val inputs=listOf("fx","b","x","y","k").associateWith { Buffer(bytes(r,it)) }
        fun span(key: String)=inputs.getValue(key).span
        val size=span("b").size
        val ox=if(r["alias"]?.jsonPrimitive?.content=="point") inputs.getValue("x") else Buffer(ByteArray(size) { 165.toByte() })
        val oy=when(r["alias"]?.jsonPrimitive?.content) { "point"->inputs.getValue("y"); "same"->ox; else->Buffer(ByteArray(size) { 165.toByte() }) }
        val audit=CryptoBinaryEcc.Audit(r.number("fail"))
        val ret=CryptoBinaryEcc.multiply(ox.span,oy.span,span("x"),span("y"),span("k"),span("b"),span("fx"),audit)
        val native=buildJsonObject {
            put("ret",ret); put("x",json(ox.data)); put("y",json(oy.data)); put("stats",stats(audit))
            put("inputs",buildJsonObject { inputs.forEach { (k,v)->put(k,json(v.data)) } })
        }
        if(r["mathCheck"]?.jsonPrimitive?.boolean!=true) return native
        fun coordinate(b: Buffer): String {
            val bs=b.data.copyOfRange(3,3+size)
            return (if(bs.isEmpty()) BigInteger.ZERO else BigInteger(1,bs)).toString()
        }
        val point=when(ret) { 1->listOf("infinity"); 0->listOf(coordinate(ox),coordinate(oy)); else->listOf("unsupported") }
        return buildJsonObject { put("native",native); put("math",JsonArray(point.map(::JsonPrimitive))) }
    }

    @TestFactory
    fun upstream(): Stream<DynamicTest> {
        val input=GZIPInputStream(javaClass.getResourceAsStream("/crypto-ecc-binary/data.jsonl.gz")!!).bufferedReader()
        input.readLine(); var index=0
        return input.lines().map { line ->
            val id=index++
            DynamicTest.dynamicTest("$id crypton binary ECC") {
                val row=Json.parseToJsonElement(line).jsonObject
                assertEquals(row.getValue("expected"),observe(row.getValue("request").jsonObject),"$id ${row.text("group")} ${row.getValue("request")}")
            }
        }.onClose { input.close() }
    }
}
