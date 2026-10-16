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

class X509AlgorithmTest {
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

    private val keys=listOf(X509PublicKeyAlgorithm.RSA,X509PublicKeyAlgorithm.RSAPSS,X509PublicKeyAlgorithm.DSA,X509PublicKeyAlgorithm.EC,X509PublicKeyAlgorithm.X25519,X509PublicKeyAlgorithm.X448,X509PublicKeyAlgorithm.Ed25519,X509PublicKeyAlgorithm.Ed448,X509PublicKeyAlgorithm.DH)
    private fun readOid(v: JsonElement)=v.jsonArray.map { it.jsonPrimitive.content.toBigInteger() }
    private fun oidJSON(o: Asn1Oid)=JsonArray(o.map { JsonPrimitive(it.toString()) })
    private fun key(v: JsonElement): X509PublicKeyAlgorithm = if(v is JsonArray) X509PublicKeyAlgorithm.Unknown(readOid(v[1])) else keys[v.jsonPrimitive.int]
    private fun keyJSON(k: X509PublicKeyAlgorithm): JsonElement = if(k is X509PublicKeyAlgorithm.Unknown) array(JsonPrimitive("unknown"),oidJSON(k.oid)) else JsonPrimitive(keys.indexOf(k))
    private fun signature(v: JsonElement): X509SignatureAlgorithm = v.jsonArray.let { a -> when(a[0].jsonPrimitive.content) {
        "with" -> X509SignatureAlgorithm.WithHash(X509HashAlgorithm.entries[a[1].jsonPrimitive.int],key(a[2]))
        "intrinsic" -> X509SignatureAlgorithm.IntrinsicHash(key(a[1]))
        else -> X509SignatureAlgorithm.Unknown(readOid(a[1]))
    } }
    private fun sigJSON(s: X509SignatureAlgorithm): JsonElement = when(s) {
        is X509SignatureAlgorithm.WithHash -> array(JsonPrimitive("with"),JsonPrimitive(s.hash.ordinal),keyJSON(s.key))
        is X509SignatureAlgorithm.IntrinsicHash -> array(JsonPrimitive("intrinsic"),keyJSON(s.key))
        is X509SignatureAlgorithm.Unknown -> array(JsonPrimitive("unknown"),oidJSON(s.oid))
    }
    private fun valuesJSON(v: List<Asn1Value>)=JsonArray(v.map(::valueJSON))
    private fun JsonObject.int(k: String)=getValue(k).jsonPrimitive.int
    private fun JsonObject.bool(k: String)=getValue(k).jsonPrimitive.boolean
    private fun observe(v: JsonObject): JsonElement = try {
        val codec=X509SignatureAlgorithmCodec
        when(v.text("api")) {
            "hash" -> X509HashAlgorithm.entries[v.int("hash")].let { h -> buildJsonObject { put("shown",h.toString()); put("equal",h==X509HashAlgorithm.entries[v.int("other")]) } }
            "key" -> key(v.getValue("key")).let { k -> buildJsonObject { put("shown",k.toString()); put("oid",oidJSON(k.getObjectId())); put("equal",k==key(v.getValue("other"))) } }
            "signature" -> signature(v.getValue("signature")).let { s -> buildJsonObject { put("shown",s.toString()); put("equal",s==signature(v.getValue("other"))) } }
            "to" -> {
                val tail=sequence { for(x in v.getValue("tail").jsonArray) yield(readValue(x)); if(v.bool("tailError")) error("tail demanded") }
                val out=codec.toAsn1Stream(signature(v.getValue("signature")),tail)
                val prefix=out.take(v.int("count").coerceAtLeast(0))
                when(v.text("part")) {
                    "spine" -> JsonPrimitive(prefix.count())
                    "kinds" -> JsonArray(prefix.map { JsonPrimitive(when(it) { is Asn1Value.IntVal -> "integer"; is Asn1Value.Oid -> "oid"; Asn1Value.Null -> "null"; is Asn1Value.Start -> "start"; is Asn1Value.End -> "end"; else -> "other" }) }.toList())
                    "shown" -> JsonPrimitive(prefix.joinToString(",","[","]"))
                    "field" -> when(val x=out.elementAt(v.int("index"))) { is Asn1Value.Oid -> oidJSON(x.arcs); is Asn1Value.IntVal -> JsonPrimitive(x.value.toString()); else -> JsonPrimitive("other") }
                    "bytes" -> byteJSON(Asn1BinaryEncoding().encodeDerChunks(out.toList()).flatMap { it.asSequence() }.take(v.int("count").coerceAtLeast(0)).toList().toByteArray())
                    else -> valuesJSON(prefix.toList())
                }
            }
            "from" -> when(val result=codec.fromAsn1(v.getValue("values").jsonArray.map(::readValue))) {
                is Asn1ParseResult.Failure -> buildJsonObject { put("left",buildString { result.message.points().forEach { appendCodePoint(it) } }) }
                is Asn1ParseResult.Success -> when(v.text("part")) {
                    "tag" -> buildJsonObject { put("right",true) }
                    "remaining" -> buildJsonObject { put("remaining",valuesJSON(result.value.remaining)) }
                    else -> buildJsonObject { put("signature",sigJSON(result.value.value)); put("shown",result.value.value.toString()); put("remaining",valuesJSON(result.value.remaining)) }
                }
            }
            "property" -> {
                val s=signature(v.getValue("signature")); val encoded=codec.toAsn1(s)(emptyList())
                val parsed=codec.fromAsn1(encoded)
                val passed=parsed is Asn1ParseResult.Success && parsed.value.remaining.isEmpty() && parsed.value.value==s
                buildJsonObject { put("property",passed); put("values",valuesJSON(encoded)); put("encoded",byteJSON(Asn1BinaryEncoding().encodeDer(encoded))) }
            }
            else -> error(v)
        }
    } catch(e: RuntimeException) { buildJsonObject { put("fatal",e.message?.lineSequence()?.first()) } }
    @Test
    fun ownedListsAndLazyPayloadCaches() {
        val oid=mutableListOf(BigInteger.ONE,BigInteger.TWO)
        val key=X509PublicKeyAlgorithm.Unknown(oid); val sig=X509SignatureAlgorithm.Unknown(oid)
        oid.clear(); assertEquals(listOf(BigInteger.ONE,BigInteger.TWO),key.getObjectId()); assertEquals(key.getObjectId(),sig.oid)
        var demanded=0
        val integer=Asn1Value.IntVal.deferred { demanded++; BigInteger.TEN }
        assertEquals(0,demanded); assertEquals(BigInteger.TEN,integer.component1()); assertEquals(integer,integer.copy()); assertEquals(1,demanded)
        var oidDemanded=0
        val encoded=Asn1Value.Oid.deferred { oidDemanded++; mutableListOf(BigInteger.ONE,BigInteger.TWO) }
        assertEquals(0,oidDemanded); assertEquals(listOf(BigInteger.ONE,BigInteger.TWO),encoded.arcs); assertEquals(encoded,encoded); assertEquals(1,oidDemanded)
        val original=mutableListOf<Asn1Value>(Asn1Value.Start(Asn1ConstructionType.Sequence),Asn1Value.Oid(listOf(BigInteger.ONE,BigInteger.TWO)),Asn1Value.End(Asn1ConstructionType.Sequence),Asn1Value.Null)
        val parsed=codecParse(original); original.clear(); assertEquals(listOf(Asn1Value.Null),parsed.remaining)
    }
    private fun codecParse(v: List<Asn1Value>)=(X509SignatureAlgorithmCodec.fromAsn1(v) as Asn1ParseResult.Success).value
    @TestFactory
    fun originalObservations(): List<DynamicTest> {
        val lines=GZIPInputStream(javaClass.getResourceAsStream("/x509-algorithm/data.jsonl.gz")!!).bufferedReader().use { it.readLines() }
        assertEquals(Json.parseToJsonElement(lines.first()).jsonObject.getValue("cases").jsonPrimitive.int,lines.size-1)
        return lines.drop(1).mapIndexed { i,line ->
            val c=Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest("$i ${c.text("group")}") { assertEquals(c.getValue("expected"),observe(c.getValue("request").jsonObject),c.getValue("request").toString()) }
        }
    }
}
