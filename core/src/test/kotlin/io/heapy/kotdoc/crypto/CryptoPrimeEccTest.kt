package io.heapy.kotdoc.crypto

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.security.MessageDigest
import java.util.stream.Stream
import java.util.zip.GZIPInputStream

class CryptoPrimeEccTest {
    private fun JsonObject.text(k: String) = getValue(k).jsonPrimitive.content
    private fun JsonObject.number(k: String) = get(k)?.jsonPrimitive?.content?.toInt() ?: 0
    private fun bytes(r: JsonObject, k: String) = r[k]?.jsonArray?.map { it.jsonPrimitive.int.toByte() }?.toByteArray() ?: byteArrayOf()
    private fun json(a: ByteArray) = JsonArray(a.map { JsonPrimitive(it.toInt() and 255) })
    private fun words(a: LongArray) = JsonArray(a.map { JsonPrimitive(it.toULong().toString()) })
    private class Buffer(content: ByteArray) {
        val data = byteArrayOf(17,34,51) + content + byteArrayOf(68,85,102,119)
        val span = CryptoByteSpan(data,3,content.size)
    }
    private fun stats(a: CryptoPrimeEcc.Audit) = buildJsonObject {
        put("ops",words(a.operations)); put("hash",a.tableAddressHash.toULong().toString())
        put("allocations",JsonArray(a.allocations.map { JsonPrimitive(it.toString()) }))
        put("frees",JsonArray(a.frees.map(::JsonPrimitive))); put("cleared",JsonArray(a.cleared.map { JsonPrimitive(if (it) 1 else 0) }))
    }
    private fun tableHash(a: LongArray, size: Int): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (i in 0..<size) digest.update((a[2+i/8] ushr (8*(i%8))).toByte())
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private fun point(result: JsonObject, size: Int): JsonArray {
        val code = result.getValue("ret").jsonPrimitive.int
        if (code == 1) return JsonArray(listOf(JsonPrimitive("infinity")))
        if (code != 0) return JsonArray(listOf(JsonPrimitive("unsupported")))
        fun value(key: String): String {
            val a = result.getValue(key).jsonArray.drop(3).take(size).map { it.jsonPrimitive.int.toByte() }.toByteArray()
            return (if (a.isEmpty()) BigInteger.ZERO else BigInteger(1,a)).toString()
        }
        return JsonArray(listOf(JsonPrimitive(value("x")),JsonPrimitive(value("y"))))
    }
    private fun observe(r: JsonObject): JsonElement {
        if (r.text("op") == "size") return buildJsonObject { put("size",CryptoPrimeEcc.tableSize(r.text("plen").toUInt(),r.text("klen").toUInt()).toString()) }
        val inputs = listOf("p","a","b","x","y","k").associateWith { Buffer(bytes(r,it)) }
        val p = inputs.getValue("p").span; val a = inputs.getValue("a").span; val b = inputs.getValue("b").span
        val x = inputs.getValue("x").span; val y = inputs.getValue("y").span; val k = inputs.getValue("k").span
        val outx = if (r["alias"]?.jsonPrimitive?.content == "point") inputs.getValue("x") else Buffer(ByteArray(p.size) { 165.toByte() })
        val outy = when (r["alias"]?.jsonPrimitive?.content) { "point" -> inputs.getValue("y"); "same" -> outx; else -> Buffer(ByteArray(p.size) { 165.toByte() }) }
        fun result(code: Int, audit: CryptoPrimeEcc.Audit) = buildJsonObject {
            put("ret",code); put("x",json(outx.data)); put("y",json(outy.data)); put("stats",stats(audit))
            put("inputs",buildJsonObject { inputs.forEach { (name,buffer) -> put(name,json(buffer.data)) } })
        }
        val actual: JsonObject
        val computedPoint: JsonElement
        if (r.text("op") == "mul") {
            val audit = CryptoPrimeEcc.Audit(r.number("fail"))
            val code = CryptoPrimeEcc.multiply(outx.span,outy.span,x,y,k,a,b,p,audit)
            actual = result(code,audit); computedPoint = point(actual,p.size)
        } else {
            val size = CryptoPrimeEcc.tableSize(p.size.toUInt(),k.size.toUInt()).toInt()
            val nwords = size/8
            val table = LongArray(nwords+4) { 0xa5a5a5a5a5a5a5a5uL.toLong() }
            val buildAudit = CryptoPrimeEcc.Audit(r.number("buildFail"))
            val built = CryptoPrimeEcc.buildTable(table,x,y,k.size,a,b,p,buildAudit,tableOffset=2)
            val originalHash = tableHash(table,size)
            if (built == 0) {
                r["mutations"]?.jsonArray?.forEach { v ->
                    val pair = v.jsonArray; val at = pair[0].jsonPrimitive.int; val value = pair[1].jsonPrimitive.long
                    table[2+at/8] = table[2+at/8] xor (value shl (8*(at%8)))
                }
                if (r["tableOverride"]?.jsonPrimitive?.content == "zero") table.fill(0,2,2+nwords)
            }
            val before = tableHash(table,size)
            val multiplication: JsonElement
            if (built == 0) {
                val audit = CryptoPrimeEcc.Audit(r.number("fail"))
                multiplication = result(CryptoPrimeEcc.multiplyTable(outx.span,outy.span,table,k,a,b,p,audit,tableOffset=2),audit)
                computedPoint = point(multiplication.jsonObject,p.size)
            } else { multiplication = JsonNull; computedPoint = JsonArray(listOf(JsonPrimitive("unsupported"))) }
            actual = buildJsonObject {
                put("build",built); put("buildStats",stats(buildAudit)); put("builtHash",originalHash); put("tableHash",before)
                put("tableHashAfter",tableHash(table,size)); put("guards",words(longArrayOf(table[0],table[1],table[nwords+2],table[nwords+3])))
                put("result",multiplication)
            }
        }
        return if (r["mathCheck"]?.jsonPrimitive?.boolean == true) buildJsonObject { put("native",actual); put("math",computedPoint) } else actual
    }

    @TestFactory
    fun upstream(): Stream<DynamicTest> {
        val input = GZIPInputStream(javaClass.getResourceAsStream("/crypto-ecc-prime/data.jsonl.gz")!!).bufferedReader()
        input.readLine()
        var index = 0
        return input.lines().map { line ->
            val id = index++
            DynamicTest.dynamicTest("$id crypton prime ECC") {
                val row = Json.parseToJsonElement(line).jsonObject
                assertEquals(row.getValue("expected"),observe(row.getValue("request").jsonObject),"$id ${row.text("group")} ${row.getValue("request")}")
            }
        }.onClose { input.close() }
    }
}
