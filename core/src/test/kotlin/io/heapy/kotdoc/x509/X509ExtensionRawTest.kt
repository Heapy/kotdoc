package io.heapy.kotdoc.x509

import io.heapy.kotdoc.asn1.*
import io.heapy.kotdoc.time.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.util.zip.GZIPInputStream

@Suppress("DEPRECATION")
class X509ExtensionRawTest {
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
            "octetsError" -> Asn1Value.OctetString.deferred {error("content demanded")}
            "oidError" -> Asn1Value.Oid.deferred { error("oid demanded") }
            "integerError" -> Asn1Value.IntVal.deferred { error("integer demanded") }
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
    private fun valueJSON(v: Asn1Value): JsonElement = when (v) {
        is Asn1Value.BooleanValue -> array(JsonPrimitive("boolean"), JsonPrimitive(v.value))
        is Asn1Value.IntVal -> array(JsonPrimitive("integer"), JsonPrimitive(v.value.toString()))
        is Asn1Value.BitString -> array(JsonPrimitive("bits"), JsonPrimitive(v.value.length.toString()), byteJSON(v.value.data))
        is Asn1Value.OctetString -> array(JsonPrimitive("octets"), byteJSON(v.bytes))
        Asn1Value.Null -> array(JsonPrimitive("null"))
        is Asn1Value.Oid -> array(JsonPrimitive("oid"), JsonArray(v.arcs.map { JsonPrimitive(it.toString()) }))
        is Asn1Value.Real -> array(JsonPrimitive("real"), JsonPrimitive(v.value.toRawBits().toULong().toString()))
        is Asn1Value.Enumerated -> array(JsonPrimitive("enumerated"), JsonPrimitive(v.value.toString()))
        is Asn1Value.StringValue -> array(JsonPrimitive("string"), JsonPrimitive(v.value.encoding.ordinal), byteJSON(v.value.rawData))
        is Asn1Value.Time -> array(JsonPrimitive("time"), JsonPrimitive(v.kind.ordinal), dateTimeJSON(v.value), v.timezone?.let { JsonPrimitive(it.minutes) } ?: JsonNull)
        is Asn1Value.Other -> array(JsonPrimitive("other"), JsonPrimitive(v.kind.ordinal), JsonPrimitive(v.tag), byteJSON(v.bytes))
        is Asn1Value.Start -> array(JsonPrimitive("start"), constructionJSON(v.kind))
        is Asn1Value.End -> array(JsonPrimitive("end"), constructionJSON(v.kind))
    }


    private fun raw(v: JsonElement): X509ExtensionRaw = v.jsonArray.let {a -> X509ExtensionRaw.deferred(
        {if(a[0]==JsonNull) error("oid demanded") else a[0].jsonArray.map {it.jsonPrimitive.content.toBigInteger()}},
        {if(a[1]==JsonNull) error("critical demanded") else a[1].jsonPrimitive.boolean},
        {if(a[2]==JsonNull) error("content demanded") else bytes(a[2])}
    )}
    private fun rawJSON(v: X509ExtensionRaw)=array(JsonArray(v.oid.map {JsonPrimitive(it.toString())}),JsonPrimitive(v.critical),byteJSON(v.content))
    private fun exts(v: JsonElement): X509Extensions = when {
        v==JsonNull -> X509Extensions(null)
        v is JsonPrimitive -> X509Extensions.presentDeferred {error("extension list demanded")}
        else -> X509Extensions.presentDeferred {v.jsonArray.map(::raw)}
    }
    private fun extsJSON(v: X509Extensions): JsonElement = v.values?.let {JsonArray(it.map(::rawJSON))} ?: JsonNull
    private fun valuesJSON(v: List<Asn1Value>)=JsonArray(v.map(::valueJSON))
    private fun JsonObject.int(k: String)=getValue(k).jsonPrimitive.int
    private fun JsonObject.bool(k: String)=getValue(k).jsonPrimitive.boolean
    private fun values(v: JsonObject,key: String)=v.getValue(key).jsonArray.map(::readValue)
    private fun observe(v: JsonObject): JsonElement = try {
        fun decoded(values: Sequence<Asn1Value>): JsonElement {
            val prefix=values.take(v.int("count").coerceAtLeast(0))
            return if(v.text("part")=="spine") JsonPrimitive(prefix.count()) else JsonArray(prefix.map {JsonPrimitive(it.toString())}.toList())
        }
        fun message(f: Asn1ParseResult.Failure)=JsonPrimitive(buildString {f.message.points().forEach {appendCodePoint(it)}})
        fun <A> from(result: Asn1ParseResult<Asn1Parsed<A>>,json: (A)->JsonElement): JsonElement=when(result) {
            is Asn1ParseResult.Failure -> if(v.text("part")=="tag") buildJsonObject {put("left",true)} else buildJsonObject {put("left",message(result))}
            is Asn1ParseResult.Success -> when(v.text("part")) {
                "tag" -> buildJsonObject {put("right",true)}
                "remaining" -> buildJsonObject {put("remaining",valuesJSON(result.value.remaining))}
                else -> buildJsonObject {put("value",json(result.value.value));put("shown",result.value.value.toString());put("remaining",valuesJSON(result.value.remaining))}
            }
        }
        when(v.text("api")) {
            "rawValue" -> raw(v.getValue("value")).let {a -> buildJsonObject {put("shown",a.toString());put("equal",a==raw(v.getValue("other")))} }
            "setValue" -> exts(v.getValue("value")).let {a -> buildJsonObject {put("shown",a.toString());put("equal",a==exts(v.getValue("other")));put("present",a.isPresent)} }
            "rawEqual" -> JsonPrimitive(raw(v.getValue("value"))==raw(v.getValue("other")))
            "setEqual" -> JsonPrimitive(exts(v.getValue("value"))==exts(v.getValue("other")))
            "field" -> raw(v.getValue("value")).let {a -> when(v.text("part")) {"oid" -> JsonArray(a.oid.map {JsonPrimitive(it.toString())});"critical" -> JsonPrimitive(a.critical);else -> byteJSON(a.content)} }
            "presence" -> JsonPrimitive(exts(v.getValue("value")).isPresent)
            "to" -> {
                val tail=sequence {for(x in v.getValue("tail").jsonArray) yield(readValue(x));if(v.bool("tailError")) error("tail demanded")}
                val out=if(v.bool("set")) X509ExtensionsCodec.toAsn1Stream(exts(v.getValue("value")),tail) else X509ExtensionRawCodec.toAsn1Stream(raw(v.getValue("value")),tail)
                val prefix=out.take(v.int("count").coerceAtLeast(0))
                when(v.text("part")) {
                    "spine" -> JsonPrimitive(prefix.count())
                    "kinds" -> JsonArray(prefix.map {JsonPrimitive(when(it) {is Asn1Value.IntVal -> "integer";is Asn1Value.Oid -> "oid";is Asn1Value.OctetString -> "octets";is Asn1Value.BooleanValue -> "boolean";is Asn1Value.StringValue -> "string";Asn1Value.Null -> "null";is Asn1Value.Start -> "start";is Asn1Value.End -> "end";else -> "other"})}.toList())
                    "shown" -> JsonPrimitive(prefix.joinToString(",","[","]"))
                    "field" -> when(val x=out.elementAt(v.int("index"))) {is Asn1Value.Oid -> JsonArray(x.arcs.map {JsonPrimitive(it.toString())});is Asn1Value.OctetString -> byteJSON(x.bytes);is Asn1Value.BooleanValue -> JsonPrimitive(x.value);else -> JsonPrimitive("other")}
                    "bytes" -> byteJSON(sequence {yieldAll(Asn1BinaryEncoding().encodeDerChunks(out.toList()))}.flatMap {it.asSequence()}.take(v.int("count").coerceAtLeast(0)).toList().toByteArray())
                    else -> valuesJSON(prefix.toList())
                }
            }
            "from" -> if(v.bool("set")) from(X509ExtensionsCodec.fromAsn1(values(v,"values")),::extsJSON) else from(X509ExtensionRawCodec.fromAsn1(values(v,"values")),::rawJSON)
            "try" -> when(val result=raw(v.getValue("value")).tryAsn1()) {
                is Asn1ParseResult.Failure -> if(v.text("part")=="tag") buildJsonObject {put("left",true)} else buildJsonObject {put("left",message(result))}
                is Asn1ParseResult.Success -> if(v.text("part")=="tag") buildJsonObject {put("right",true)} else buildJsonObject {put("right",decoded(result.value))}
            }
            "unsafe" -> decoded(raw(v.getValue("value")).asn1())
            "property" -> {
                val value=exts(v.getValue("value"));val out=X509ExtensionsCodec.toAsn1(value)(emptyList());val parsed=X509ExtensionsCodec.fromAsn1(out)
                val passed=parsed is Asn1ParseResult.Success && parsed.value.remaining.isEmpty() && parsed.value.value==value
                buildJsonObject {put("property",passed);put("values",valuesJSON(out));put("encoded",byteJSON(Asn1BinaryEncoding().encodeDer(out)))}
            }
            else -> error(v)
        }
    } catch(e: RuntimeException) {buildJsonObject {put("fatal",e.message?.lineSequence()?.first())}}
    @Test
    fun ownershipAndDeferredPayloads() {
        val oid=mutableListOf(BigInteger.TWO,BigInteger.valueOf(5),BigInteger.valueOf(29),BigInteger.valueOf(15));val content=byteArrayOf(5,0)
        val raw=X509ExtensionRaw(oid,false,content);oid.clear();content[0]=2;raw.content[0]=2
        assertArrayEquals(byteArrayOf(5,0),raw.content)
        val list=mutableListOf(raw);val set=X509Extensions(list);list.clear();assertEquals(listOf(raw),set.values)
        assertNotEquals(X509Extensions(null),X509Extensions(emptyList()))
        var count=0;val octets=Asn1Value.OctetString.deferred {count++;byteArrayOf(7)}
        assertEquals(0,count);octets.bytes[0]=8;assertArrayEquals(byteArrayOf(7),octets.bytes);assertEquals(1,count)
        val broken=X509ExtensionRaw.deferred({error("oid")},{error("critical")},{byteArrayOf(5,0)})
        val decoded=broken.tryAsn1() as Asn1ParseResult.Success;assertEquals(listOf(Asn1Value.Null),decoded.value.toList())
        val bytes=byteArrayOf(5,0);val owned=X509ExtensionRaw(emptyList(),false,bytes);val result=owned.tryAsn1() as Asn1ParseResult.Success;bytes[0]=2;assertEquals(listOf(Asn1Value.Null),result.value.toList())
        val missing=Asn1Parser.onNextContainerMaybe(Asn1ConstructionType.Sequence,Asn1Parser.pureLazy<Int> {error("value demanded")}).runState(listOf(Asn1Value.Start(Asn1ConstructionType.Sequence),Asn1Value.End(Asn1ConstructionType.Sequence))) as Asn1ParseResult.Success
        val some=missing.value.value as Asn1Maybe.Some;assertEquals("value demanded",assertThrows(IllegalStateException::class.java) {some.value}.message)
    }
    @TestFactory
    fun originalObservations(): List<DynamicTest> {
        val lines=GZIPInputStream(javaClass.getResourceAsStream("/x509-extension-raw/data.jsonl.gz")!!).bufferedReader().use {it.readLines()}
        assertEquals(Json.parseToJsonElement(lines.first()).jsonObject.getValue("cases").jsonPrimitive.int,lines.size-1)
        return lines.drop(1).mapIndexed {i,line -> val c=Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest("$i ${c.text("group")}") {assertEquals(c.getValue("expected"),observe(c.getValue("request").jsonObject),c.getValue("request").toString())}
        }
    }
}
