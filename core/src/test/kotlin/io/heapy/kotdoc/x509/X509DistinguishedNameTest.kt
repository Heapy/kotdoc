package io.heapy.kotdoc.x509

import io.heapy.kotdoc.asn1.*
import io.heapy.kotdoc.time.*
import io.heapy.kotdoc.layout.CharacterStringChars
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.util.zip.GZIPInputStream

class X509DistinguishedNameTest {
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


    private fun dn(v: JsonElement)=X509DistinguishedName(v.jsonArray.map { token ->
        val a=token.jsonArray
        X509DnAttribute.deferred({if(a[0]==JsonNull) error("oid demanded") else a[0].jsonArray.map {it.jsonPrimitive.content.toBigInteger()}},Asn1CharacterString(Asn1StringEncoding.entries[a[1].jsonPrimitive.int],bytes(a[2])))
    })
    private fun dnJSON(value: X509DistinguishedName)=JsonArray(value.elements.map {array(JsonArray(it.oid.map {x -> JsonPrimitive(x.toString())}),JsonPrimitive(it.value.encoding.ordinal),byteJSON(it.value.rawData))})
    private fun csJSON(value: Asn1CharacterString?)=value?.let {array(JsonPrimitive(it.encoding.ordinal),byteJSON(it.rawData))} ?: JsonNull
    private fun valuesJSON(v: List<Asn1Value>)=JsonArray(v.map(::valueJSON))
    private fun JsonObject.int(k: String)=getValue(k).jsonPrimitive.int
    private fun JsonObject.bool(k: String)=getValue(k).jsonPrimitive.boolean
    private fun fromDn(inner: Boolean,vs: List<Asn1Value>): Asn1ParseResult<Asn1Parsed<X509DistinguishedName>> {
        if(!inner) return X509DistinguishedNameCodec.fromAsn1(vs)
        return when(val result=X509DistinguishedNameInnerCodec.fromAsn1(vs)) {
            is Asn1ParseResult.Failure -> result
            is Asn1ParseResult.Success -> Asn1ParseResult.Success(Asn1Parsed.deferred({result.value.value.value},result.value.remaining))
        }
    }
    private fun observe(v: JsonObject): JsonElement = try {
        when(v.text("api")) {
            "element" -> X509DnElement.entries[v.int("element")].let {e -> buildJsonObject {put("shown",e.toString());put("oid",JsonArray(e.getObjectId().map {JsonPrimitive(it.toString())}));put("equal",e==X509DnElement.entries[v.int("other")])} }
            "value" -> {
                val a=dn(v.getValue("value"));val b=dn(v.getValue("other"))
                buildJsonObject {put("shown",a.toString());put("innerShown",X509DistinguishedNameInner(a).toString());put("equal",a==b);put("innerEqual",X509DistinguishedNameInner(a)==X509DistinguishedNameInner(b));put("ordering",when {a<b -> "LT";a>b -> "GT";else -> "EQ"});put("append",dnJSON(a+b));put("emptyLeft",X509DistinguishedName.Empty+a==a);put("emptyRight",a+X509DistinguishedName.Empty==a)}
            }
            "lookup" -> csJSON(dn(v.getValue("value")).getElement(X509DnElement.entries[v.int("element")]))
            "to" -> {
                val tail=sequence {for(x in v.getValue("tail").jsonArray) yield(readValue(x));if(v.bool("tailError")) error("tail demanded")}
                val value=dn(v.getValue("value"))
                val out=if(v.bool("inner")) X509DistinguishedNameInnerCodec.toAsn1Stream(X509DistinguishedNameInner(value),tail) else X509DistinguishedNameCodec.toAsn1Stream(value,tail)
                val prefix=out.take(v.int("count").coerceAtLeast(0))
                when(v.text("part")) {
                    "spine" -> JsonPrimitive(prefix.count())
                    "kinds" -> JsonArray(prefix.map {JsonPrimitive(when(it) {is Asn1Value.IntVal -> "integer";is Asn1Value.Oid -> "oid";is Asn1Value.StringValue -> "string";Asn1Value.Null -> "null";is Asn1Value.Start -> "start";is Asn1Value.End -> "end";else -> "other"})}.toList())
                    "shown" -> JsonPrimitive(prefix.joinToString(",","[","]"))
                    "field" -> when(val x=out.elementAt(v.int("index"))) {is Asn1Value.Oid -> JsonArray(x.arcs.map {JsonPrimitive(it.toString())});is Asn1Value.StringValue -> csJSON(x.value);else -> JsonPrimitive("other")}
                    "bytes" -> byteJSON(Asn1BinaryEncoding().encodeDerChunks(out.toList()).flatMap {it.asSequence()}.take(v.int("count").coerceAtLeast(0)).toList().toByteArray())
                    else -> valuesJSON(prefix.toList())
                }
            }
            "from" -> when(val result=fromDn(v.bool("inner"),v.getValue("values").jsonArray.map(::readValue))) {
                is Asn1ParseResult.Failure -> if(v.text("part")=="tag") buildJsonObject {put("left",true)} else buildJsonObject {put("left",buildString {result.message.points().forEach {appendCodePoint(it)}})}
                is Asn1ParseResult.Success -> when(v.text("part")) {
                    "tag" -> buildJsonObject {put("right",true)}
                    "remaining" -> buildJsonObject {put("remaining",valuesJSON(result.value.remaining))}
                    "lookup" -> buildJsonObject {put("lookup",csJSON(result.value.value.getElement(X509DnElement.entries[v.int("element")])))}
                    else -> buildJsonObject {put("value",dnJSON(result.value.value));put("shown",result.value.value.toString());put("remaining",valuesJSON(result.value.remaining))}
                }
            }
            "container" -> {
                val input=sequence {for(x in v.getValue("values").jsonArray) yield(readValue(x));if(v.bool("tailError")) error("tail demanded")}
                valuesJSON(X509Asn1.containerStream(readConstruction(v.getValue("kind")),input).take(v.int("count").coerceAtLeast(0)).toList())
            }
            "property" -> {
                val value=dn(v.getValue("value"));val out=X509DistinguishedNameCodec.toAsn1(value)(emptyList());val parsed=X509DistinguishedNameCodec.fromAsn1(out)
                val passed=parsed is Asn1ParseResult.Success && parsed.value.remaining.isEmpty() && parsed.value.value==value
                buildJsonObject {put("property",passed);put("values",valuesJSON(out))}
            }
            else -> error(v)
        }
    } catch(e: RuntimeException) {buildJsonObject {put("fatal",e.message?.lineSequence()?.first())}}
    @Test
    fun ownedInputAndDeferredFailures() {
        val oid=mutableListOf(BigInteger.TWO,BigInteger.valueOf(5),BigInteger.valueOf(4),BigInteger.valueOf(3))
        val bytes=byteArrayOf(65);val entry=X509DnAttribute(oid,Asn1CharacterString(Asn1StringEncoding.IA5,bytes))
        val entries=mutableListOf(entry);val dn=X509DistinguishedName(entries)
        oid.clear();entries.clear();bytes[0]=66
        assertArrayEquals(byteArrayOf(65),dn.getElement(X509DnElement.DnCommonName)!!.rawData)
        assertEquals(dn,X509DistinguishedName.Empty+dn);assertEquals(dn,dn+X509DistinguishedName.Empty)
        var demanded=0
        val failure=Asn1ParseResult.Failure.deferred {demanded++;CharacterStringChars.fromString("error")}
        assertEquals(0,demanded);assertEquals(failure.message,failure.component1());assertEquals(failure,failure.copy());assertEquals(1,demanded)
        val bad=X509DistinguishedNameCodec.fromAsn1(listOf(Asn1Value.Start(Asn1ConstructionType.Sequence),Asn1Value.Start(Asn1ConstructionType.Set),Asn1Value.Start(Asn1ConstructionType.Sequence),Asn1Value.Oid.deferred {error("oid demanded")},Asn1Value.End(Asn1ConstructionType.Sequence),Asn1Value.End(Asn1ConstructionType.Set),Asn1Value.End(Asn1ConstructionType.Sequence)))
        assertTrue(bad is Asn1ParseResult.Failure)
        assertEquals("oid demanded",assertThrows(IllegalStateException::class.java) {(bad as Asn1ParseResult.Failure).message}.message)
    }
    @TestFactory
    fun originalObservations(): List<DynamicTest> {
        val lines=GZIPInputStream(javaClass.getResourceAsStream("/x509-dn/data.jsonl.gz")!!).bufferedReader().use {it.readLines()}
        assertEquals(Json.parseToJsonElement(lines.first()).jsonObject.getValue("cases").jsonPrimitive.int,lines.size-1)
        return lines.drop(1).mapIndexed {i,line -> val c=Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest("$i ${c.text("group")}") {assertEquals(c.getValue("expected"),observe(c.getValue("request").jsonObject),c.getValue("request").toString())}
        }
    }
}
