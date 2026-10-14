package io.heapy.kotdoc.json

import io.heapy.kotdoc.layout.TextChars
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.util.Base64
import java.util.zip.GZIPInputStream
import kotlin.test.*

/** Transport only: expected text and UTF-8 bytes come from the original Haskell libraries. */
class PrettyJsonTest {
    private fun JsonElement.s() = jsonPrimitive.content
    private fun JsonElement.l() = jsonPrimitive.long
    private fun points(v: JsonElement) = TextChars.fromPoints(v.jsonArray.map { it.jsonPrimitive.int }.toIntArray())
    private fun number(v: JsonElement) = v.jsonArray.let { ScientificNumber(BigInteger(it[1].s()),it[2].l()) }
    private fun value(v: JsonElement): PrettyValue {
        val a = v.jsonArray
        return when(a[0].s()) {
            "z" -> PrettyValue.Null
            "b" -> PrettyValue.Bool(a[1].jsonPrimitive.boolean)
            "s" -> PrettyValue.Text(points(a[1]))
            "n" -> PrettyValue.Number(number(v))
            "a" -> PrettyValue.Array(a[1].jsonArray.map(::value))
            "o" -> PrettyValue.Object(a[1].jsonArray.associate { p -> p.jsonArray.let { points(it[0]) to value(it[1]) } })
            else -> error(a[0])
        }
    }
    private fun compare(name: String, keys: List<String>): Comparator<String> {
        val keyOrder = PrettyJson.keyOrder(keys)
        fun length(s: String) = s.codePointCount(0,s.length)
        return when(name) {
            "equal" -> PrettyJson.keepOrder
            "natural" -> PrettyJson.compare
            "reverse" -> PrettyJson.compare.reversed()
            "length" -> Comparator { a,b -> length(a).compareTo(length(b)) }
            "keyOrder" -> keyOrder
            "keyThenNatural" -> keyOrder.thenComparing(PrettyJson.compare)
            "alwaysGT" -> Comparator { _,_ -> 1 }
            "alwaysLT" -> Comparator { _,_ -> -1 }
            "cycle" -> Comparator { a,b ->
                val x=length(a)%3; val y=length(b)%3
                if (x==y) 0 else if ((x+1)%3==y) -1 else 1
            }
            else -> error(name)
        }
    }
    private fun config(v: JsonElement): PrettyJsonConfig {
        val a = v.jsonArray
        val nf = a[3].jsonArray
        return PrettyJsonConfig(
            if (a[0].s()=="Tab") JsonIndent.Tab else JsonIndent.Spaces(a[0].l()),
            compare(a[1].s(),a[2].jsonArray.map(::points)),
            when(nf[0].s()) {
                "Generic" -> JsonNumberFormat.Generic
                "Scientific" -> JsonNumberFormat.Scientific
                "Decimal" -> JsonNumberFormat.Decimal
                "fields" -> JsonNumberFormat.Custom { "${it.coefficient}@${it.base10Exponent}" }
                "format" -> JsonNumberFormat.Custom { ScientificFormatter.format(it,ScientificFormat.valueOf(nf[1].s()),if(nf[2]==JsonNull) null else nf[2].l()) }
                else -> error(nf[0])
            },a[4].jsonPrimitive.boolean,
        )
    }
    private fun text(t: String) = buildJsonObject { put("text",t) }
    private fun prefix(chunks: Sequence<String>, count: Int): String = buildString {
        var left=count
        if (left > 0) for (chunk in chunks) {
            append(chunk.take(left));left-=minOf(left,chunk.length)
            if(left==0) break
        }
    }
    private fun run(a: JsonArray): JsonElement = when(a[0].s()) {
        "format" -> text(ScientificFormatter.format(number(a[3]),ScientificFormat.valueOf(a[1].s()),if(a[2]==JsonNull) null else a[2].l()))
        "prefix", "stringPrefix" -> text(prefix(ScientificFormatter.chunks(number(a[3]),ScientificFormat.valueOf(a[1].s()),if(a[2]==JsonNull) null else a[2].l()),a[4].jsonPrimitive.int))
        "digits" -> number(a[1]).decimalDigits().let { (ds,e) -> buildJsonObject { put("digits",ds);put("exponent",e) } }
        "double" -> {
            val d = Double.fromBits(a[1].s().toULong().toLong())
            buildJsonObject {
                put("pretty",PrettyJson.encode(PrettyValue.fromDouble(d)))
                if (d.isFinite()) {
                    val s=ScientificNumber.fromDouble(d)
                    put("coefficient",s.coefficient.toString());put("exponent",s.base10Exponent)
                    put("formatted",ScientificFormatter.format(s))
                }
            }
        }
        "pretty" -> {
            val conf = config(a[1]); val v = value(a[2])
            buildJsonObject {
                put("text",PrettyJson.encode(v,conf))
                put("bytesBase64",Base64.getEncoder().encodeToString(PrettyJson.encodeUtf8(v,conf)))
            }
        }
        "sort" -> JsonArray(PrettySort.sort(a[3].jsonArray.map(::points)) { x,y -> compare(a[1].s(),a[2].jsonArray.map(::points)).compare(x,y)>0 }.map(::JsonPrimitive))
        "deep" -> {
            var v: PrettyValue=PrettyValue.Null
            repeat(a[1].jsonPrimitive.int) { v=PrettyValue.Array(listOf(v)) }
            text(PrettyJson.encode(v,PrettyJsonConfig(indent=JsonIndent.Spaces(0))))
        }
        else -> error(a[0])
    }

    @TestFactory fun originalOutputs(): List<DynamicTest> = GZIPInputStream(javaClass.getResourceAsStream("/pretty-json/data.jsonl.gz")!!).bufferedReader().use { reader ->
        val lines=reader.lineSequence().iterator()
        val meta=Json.parseToJsonElement(lines.next()).jsonObject
        val tests=lines.asSequence().mapIndexed { i,line ->
            val op=line.substringAfter("\"request\":[\"").substringBefore('"')
            DynamicTest.dynamicTest("$i $op") {
                val c=Json.parseToJsonElement(line).jsonObject
                assertEquals(c.getValue("expected"),run(c.getValue("request").jsonArray))
            }
        }.toList()
        assertEquals(meta.getValue("cases").jsonPrimitive.int,tests.size)
        tests
    }

    @Test fun iterativeJsonAdapterOwnershipAndCallbackDemand() {
        val tree=Json.parseToJsonElement("{\"z\":[1.00,true,null,\"\\b\\f🙂\"],\"a\":1e20}")
        assertEquals("{\"a\":1.0e20,\"z\":[1.0,true,null,\"\\u0008\\u000c🙂\"]}",PrettyJson.encode(PrettyJson.fromJson(tree),PrettyJsonConfig(indent=JsonIndent.Spaces(0))))
        var deep: JsonElement=JsonNull
        repeat(100000) { deep=JsonArray(listOf(deep)) }
        assertEquals("[".repeat(100000)+"null"+"]".repeat(100000),PrettyJson.encode(PrettyJson.fromJson(deep),PrettyJsonConfig(indent=JsonIndent.Spaces(0))))
        val input=mutableListOf<PrettyValue>(PrettyValue.Bool(true));val array=PrettyValue.Array(input);input.clear()
        assertEquals("[true]",PrettyJson.encode(array,PrettyJsonConfig(indent=JsonIndent.Spaces(0))))
        val v=PrettyValue.Array(listOf(PrettyValue.Number(ScientificNumber(123,-2))))
        var calls=0
        val config=PrettyJsonConfig(indent=JsonIndent.Spaces(0),numberFormat=JsonNumberFormat.Custom { calls++;"unquoted\u0008" })
        val chunks=PrettyJson.chunks(v,config).iterator()
        assertEquals("[",chunks.next());assertEquals(0,calls)
        assertEquals("[unquoted\u0008]",PrettyJson.encode(v,config))
        assertEquals(1,calls)
    }
}
