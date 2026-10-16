package io.heapy.kotdoc.asn1

import io.heapy.kotdoc.time.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.util.zip.GZIPInputStream

class Asn1PrettyTest {
    private fun JsonObject.text(k: String) = getValue(k).jsonPrimitive.content
    private fun bytes(v: JsonElement) = v.jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray()
    private fun byteJSON(v: ByteArray) = JsonArray(v.map { JsonPrimitive(it.toInt() and 255) })
    private fun array(vararg v: JsonElement) = JsonArray(v.toList())
    private fun readConstruction(v: JsonElement): Asn1ConstructionType {
        val a = v.jsonArray
        return when (a[0].jsonPrimitive.content) { "sequence" -> Asn1ConstructionType.Sequence; "set" -> Asn1ConstructionType.Set; else -> Asn1ConstructionType.Container(Asn1Class.entries[a[1].jsonPrimitive.int], a[2].jsonPrimitive.long) }
    }
    private fun constructionJSON(v: Asn1ConstructionType): JsonElement = when (v) {
        Asn1ConstructionType.Sequence -> array(JsonPrimitive("sequence"))
        Asn1ConstructionType.Set -> array(JsonPrimitive("set"))
        is Asn1ConstructionType.Container -> array(JsonPrimitive("container"), JsonPrimitive(v.kind.ordinal), JsonPrimitive(v.tag))
    }
    private fun readDateTime(v: JsonElement): HourglassDateTime {
        val a = v.jsonArray.map { it.jsonPrimitive.long }
        return HourglassDateTime(HourglassDate(a[0], HourglassMonth.entries[a[1].toInt()], a[2]), HourglassTimeOfDay(HourglassHours(a[3]), HourglassMinutes(a[4]), HourglassSeconds(a[5]), HourglassNanoSeconds(a[6])))
    }
    private fun dateTimeJSON(v: HourglassDateTime) = JsonArray(listOf(v.date.year, v.date.month.ordinal.toLong(), v.date.day, v.time.hour.value, v.time.minute.value, v.time.second.value, v.time.nanoSecond.value).map(::JsonPrimitive))
    private fun readValue(v: JsonElement): Asn1Value {
        val a = v.jsonArray
        return when (a[0].jsonPrimitive.content) {
            "timeError" -> {
                val part = a[1].jsonPrimitive.content
                Asn1Value.Time(Asn1TimeType.TimeUTC, HourglassDateTime.deferred(
                    { if (part == "date") error("date demanded") else HourglassDate(2026,HourglassMonth.October,16) },
                    { if (part == "time") error("time demanded") else HourglassTimeOfDay(HourglassHours(5),HourglassMinutes(34),HourglassSeconds(12),HourglassNanoSeconds(0)) }
                ),null)
            }
            "boolean" -> Asn1Value.BooleanValue(a[1].jsonPrimitive.boolean)
            "integer" -> Asn1Value.IntVal(a[1].jsonPrimitive.content.toBigInteger())
            "bits" -> Asn1Value.BitString(Asn1BitArray(a[1].jsonPrimitive.content.toULong(), bytes(a[2])))
            "octets" -> Asn1Value.OctetString(bytes(a[1]))
            "null" -> Asn1Value.Null
            "oid" -> Asn1Value.Oid(a[1].jsonArray.map { it.jsonPrimitive.content.toBigInteger() })
            "real" -> Asn1Value.Real(Double.fromBits(a[1].jsonPrimitive.content.toULong().toLong()))
            "enumerated" -> Asn1Value.Enumerated(a[1].jsonPrimitive.content.toBigInteger())
            "string" -> Asn1Value.StringValue(Asn1CharacterString(Asn1StringEncoding.entries[a[1].jsonPrimitive.int], bytes(a[2])))
            "time" -> Asn1Value.Time(Asn1TimeType.entries[a[1].jsonPrimitive.int], readDateTime(a[2]), if (a[3] == JsonNull) null else HourglassTimezoneOffset(a[3].jsonPrimitive.long))
            "other" -> Asn1Value.Other(Asn1Class.entries[a[1].jsonPrimitive.int], a[2].jsonPrimitive.long, bytes(a[3]))
            "start" -> Asn1Value.Start(readConstruction(a[1]))
            "end" -> Asn1Value.End(readConstruction(a[1]))
            else -> error(v)
        }
    }

    private fun JsonObject.int(k: String) = getValue(k).jsonPrimitive.int
    private fun JsonObject.long(k: String) = getValue(k).jsonPrimitive.long
    private fun JsonObject.bool(k: String) = getValue(k).jsonPrimitive.boolean
    private fun mode(v: JsonObject, other: Boolean = false): Asn1PrettyType {
        val single = if (other) "otherSingle" else "single"
        val indent = if (other) "otherIndent" else "indent"
        return if (v.bool(single)) Asn1PrettyType.SingleLine else Asn1PrettyType.Multiline(v.long(indent))
    }
    private fun observe(v: JsonObject): JsonElement = try {
        when (v.text("api")) {
            "mode" -> buildJsonObject { put("shown",mode(v).toString()); put("equal",mode(v)==mode(v,true)) }
            "pretty" -> {
                val values = sequence {
                    for (x in v.getValue("values").jsonArray) yield(readValue(x))
                    if (v.bool("tailError")) error("tail demanded")
                }
                val output = Asn1Pretty.stream(mode(v),values)
                JsonArray((if (v.bool("prefix")) output.take(v.int("count").coerceAtLeast(0)) else output).map(::JsonPrimitive).toList())
            }
            "encode" -> {
                val p=Asn1Primitives()
                val values=v.getValue("values").jsonArray.map(::readValue)
                val encoded by lazy { when(v.text("method")) {
                    "primitive" -> p.encodePrimitive(readValue(v.getValue("value")))
                    "one" -> p.encodeOne(readValue(v.getValue("value")))
                    "constructed" -> p.encodeConstructed(readValue(v.getValue("value")),values)
                    else -> p.encodeList(values)
                } }
                val events=sequence { yieldAll(encoded.events) }
                val cells=sequence { yieldAll(encoded.lazyEvents) }
                val n=v.int("count").coerceAtLeast(0)
                when(v.text("part")) {
                    "length" -> JsonPrimitive(encoded.length)
                    "spine" -> JsonPrimitive(cells.take(n).count())
                    "kinds" -> JsonArray(events.take(n).map { JsonPrimitive(when(it) {
                        is Asn1Event.Header -> "header"; is Asn1Event.Primitive -> "primitive"
                        Asn1Event.ConstructionBegin -> "begin"; Asn1Event.ConstructionEnd -> "end"
                    }) }.toList())
                    "headerTags" -> JsonArray(events.take(n).map { JsonPrimitive(if (it is Asn1Event.Header) it.header.tag else 0) }.toList())
                    else -> JsonPrimitive(events.take(n).joinToString(",","[","]"))
                }
            }
            else -> error(v)
        }
    } catch (e: RuntimeException) { buildJsonObject { put("fatal",e.message?.lineSequence()?.first()) } }
    @Test
    fun ownedInputAndDeferredHeader() {
        val input=mutableListOf<Asn1Value>(Asn1Value.Null)
        val output=Asn1Pretty.stream(Asn1PrettyType.SingleLine,input)
        input.clear()
        assertEquals("null,",buildString { output.forEach(::appendCodePoint) })
        var demanded=0
        val header=Asn1Event.Header.deferred { demanded++; Asn1Header(Asn1Class.Universal,2,false,Asn1Length.Short(1)) }
        assertEquals(0,demanded)
        assertEquals(header.header,header.component1())
        assertEquals(header,header.copy())
        assertEquals(1,demanded)
    }
    @TestFactory
    fun originalObservations(): List<DynamicTest> {
        val lines=GZIPInputStream(javaClass.getResourceAsStream("/asn1-pretty/data.jsonl.gz")!!).bufferedReader().use { it.readLines() }
        assertEquals(Json.parseToJsonElement(lines.first()).jsonObject.getValue("cases").jsonPrimitive.int,lines.size-1)
        return lines.drop(1).mapIndexed { i,line ->
            val c=Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest("$i ${c.text("group")}") { assertEquals(c.getValue("expected"),observe(c.getValue("request").jsonObject),c.getValue("request").toString()) }
        }
    }
}
