package io.heapy.kotdoc.x509

import io.heapy.kotdoc.asn1.*
import io.heapy.kotdoc.time.*
import io.heapy.kotdoc.layout.CharacterString
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.util.zip.GZIPInputStream

@Suppress("DEPRECATION")
class X509PublicKeyTest {
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
            "bitsError" -> Asn1Value.BitString.deferred {error("bits demanded")}
            "stringError" -> Asn1Value.StringValue(Asn1CharacterString.deferred(Asn1StringEncoding.UTF8) {error("string demanded")})
            "otherError" -> Asn1Value.Other.deferred(Asn1Class.entries[a[1].jsonPrimitive.int],a[2].jsonPrimitive.long) {error("content demanded")}
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


    private fun JsonObject.int(k: String)=getValue(k).jsonPrimitive.int
    private fun JsonObject.bool(k: String)=getValue(k).jsonPrimitive.boolean
    private fun number(v: JsonElement)=if(v==JsonNull) error("integer demanded") else v.jsonPrimitive.content.toBigInteger()
    private fun data(v: JsonElement)=if(v==JsonNull) error("content demanded") else bytes(v)
    private fun oid(v: JsonElement): Asn1Oid=if(v==JsonNull) error("oid demanded") else v.jsonArray.map {number(it)}
    private fun oidJSON(v: Asn1Oid)=JsonArray(v.map {JsonPrimitive(it.toString())})
    private fun nJSON(v: BigInteger)=JsonPrimitive(v.toString())
    private fun point(v: JsonElement)=X509SerializedPoint.deferred {data(v)}
    private fun readKey(v: JsonElement): X509PublicKey=v.jsonArray.let {a -> when(a[0].jsonPrimitive.content) {
        "rsa" -> X509PublicKey.Rsa(X509RsaPublicKey.deferred({a[1].jsonPrimitive.long},{number(a[2])},{number(a[3])}))
        "dsa" -> X509PublicKey.Dsa(X509DsaPublicKey.deferred(X509DsaParameters.deferred({number(a[1])},{number(a[2])},{number(a[3])}),{number(a[4])}))
        "ecNamed" -> X509PublicKey.Ec(X509EcPublicKey.Named(X509CurveName.entries[a[1].jsonPrimitive.int],point(a[2])))
        "ecPrime" -> X509PublicKey.Ec(X509EcPublicKey.Prime(point(a[1]),lazy {number(a[2])},lazy {number(a[3])},lazy {number(a[4])},point(a[5]),lazy {number(a[6])},lazy {number(a[7])},lazy {number(a[8])}))
        "dh" -> X509PublicKey.Dh(number(a[1]),number(a[2]),number(a[3]),if(a[4]==JsonNull) null else number(a[4]),a[5].jsonArray.map {it.jsonPrimitive.int},number(a[6]))
        "unknown" -> X509PublicKey.Unknown.deferred({oid(a[1])},{data(a[2])})
        else -> X509PublicKey.Bytes.deferred(when(a[0].jsonPrimitive.content) {"x25519"->X509BytePublicKeyKind.X25519;"x448"->X509BytePublicKeyKind.X448;"ed25519"->X509BytePublicKeyKind.Ed25519;else->X509BytePublicKeyKind.Ed448}) {data(a[1])}
    }}
    private fun keyJSON(k: X509PublicKey): JsonElement=when(k) {
        is X509PublicKey.Rsa -> array(JsonPrimitive("rsa"),JsonPrimitive(k.key.size),nJSON(k.key.modulus),nJSON(k.key.exponent))
        is X509PublicKey.Dsa -> array(JsonPrimitive("dsa"),nJSON(k.key.parameters.p),nJSON(k.key.parameters.g),nJSON(k.key.parameters.q),nJSON(k.key.y))
        is X509PublicKey.Ec -> when(val e=k.key) {
            is X509EcPublicKey.Named -> array(JsonPrimitive("ecNamed"),JsonPrimitive(e.curve.ordinal),byteJSON(e.point.bytes))
            is X509EcPublicKey.Prime -> array(JsonPrimitive("ecPrime"),byteJSON(e.point.bytes),nJSON(e.a),nJSON(e.b),nJSON(e.prime),byteJSON(e.generator.bytes),nJSON(e.order),nJSON(e.cofactor),nJSON(e.seed))
        }
        is X509PublicKey.Dh -> array(JsonPrimitive("dh"),nJSON(k.p),nJSON(k.g),nJSON(k.q),k.j?.let(::nJSON) ?: JsonNull,JsonArray(k.seed.map(::JsonPrimitive)),nJSON(k.counter))
        is X509PublicKey.Unknown -> array(JsonPrimitive("unknown"),oidJSON(k.oid),byteJSON(k.bytes))
        is X509PublicKey.Bytes -> array(JsonPrimitive(k.type.toString().lowercase()),byteJSON(k.bytes))
    }
    private fun values(v: JsonObject)=sequence {for(t in v.getValue("values").jsonArray) yield(readValue(t));if(v.bool("tailError")) error("tail demanded")}
    private fun kind(v: Asn1Value)=when(v) {is Asn1Value.Start->"start";is Asn1Value.End->"end";is Asn1Value.IntVal->"integer";is Asn1Value.Oid->"oid";Asn1Value.Null->"null";is Asn1Value.BitString->"bits";is Asn1Value.OctetString->"octets";else->"other"}
    private fun valuesJSON(v: List<Asn1Value>)=JsonArray(v.map(::valueJSON))
    private fun observe(v: JsonObject): JsonElement=try {
        fun key()=readKey(v.getValue("value"))
        fun parsed(r: Asn1ParseResult<X509PublicKeyParsed>): JsonElement=when(r) {
            is Asn1ParseResult.Failure -> if(v.text("part")=="tag") buildJsonObject {put("left",true)} else buildJsonObject {put("left",buildString {r.message.points().forEach(::appendCodePoint)})}
            is Asn1ParseResult.Success -> when(v.text("part")) {
                "tag" -> buildJsonObject {put("right",true)}
                "remaining" -> buildJsonObject {put("remaining",valuesJSON(r.value.remainingSequence().take(v.int("count").coerceAtLeast(0)).toList()))}
                "algorithm" -> buildJsonObject {put("algorithm",r.value.key.algorithm.toString())}
                else -> buildJsonObject {put("value",keyJSON(r.value.key));put("shown",r.value.key.toString());put("remaining",valuesJSON(r.value.remaining))}
            }
        }
        when(v.text("api")) {
            "lookup" -> {
                val table=v.getValue("table").jsonArray.map {it.jsonArray.let {a->X509OidEntry.deferred({number(a[0])},{oid(a[1])})}}
                fun <A> result(m: Asn1Maybe<A>,encode: (A)->JsonElement): JsonElement=when(m) {Asn1Maybe.None->JsonNull;is Asn1Maybe.Some->if(v.text("part")=="tag") JsonPrimitive(true) else encode(m.value)}
                if(v.bool("byOid")) result(X509OidLookup.byOidDeferred(table) {oid(v.getValue("target"))},::nJSON) else result(X509OidLookup.forValueDeferred(table) {number(v.getValue("target"))},::oidJSON)
            }
            "curves" -> JsonArray(X509CurveOids.entries.map {(curve,oid)->array(JsonPrimitive(curve.toString()),JsonPrimitive(curve.ordinal),oidJSON(oid))})
            "value" -> {val x=key();buildJsonObject {put("shown",x.toString());put("value",keyJSON(x));put("equal",x==readKey(v.getValue("other")))}}
            "algorithm" -> key().algorithm.let {buildJsonObject {put("shown",it.toString());put("oid",oidJSON(it.getObjectId()))}}
            "encode" -> {
                val prefix=X509PublicKeyCodec.toAsn1Stream(key(),values(v)).take(v.int("count").coerceAtLeast(0))
                when(v.text("part")) {"spine"->JsonPrimitive(prefix.count());"kinds"->JsonArray(prefix.map {JsonPrimitive(kind(it))}.toList());"shown"->JsonPrimitive(prefix.joinToString(",","[","]"));"bytes"->byteJSON(Asn1BinaryEncoding().encodeDerSequence(prefix));else->valuesJSON(prefix.toList())}
            }
            "from" -> parsed(X509PublicKeyCodec.fromSequence(values(v)))
            "roundtrip" -> parsed(X509PublicKeyCodec.fromSequence(X509PublicKeyCodec.toAsn1Stream(key())))
            "property" -> {
                val x=key();val encoded=X509PublicKeyCodec.toAsn1(x)(emptyList());val r=X509PublicKeyCodec.fromAsn1(encoded)
                val good=r is Asn1ParseResult.Success && r.value.remaining.isEmpty() && r.value.value==x
                buildJsonObject {put("property",good);put("values",valuesJSON(encoded));put("bytes",byteJSON(Asn1BinaryEncoding().encodeDer(encoded)))}
            }
            else -> error(v)
        }
    } catch(e: RuntimeException) {buildJsonObject {put("fatal",e.message?.lineSequence()?.first())}}
    @Test
    fun ownedKeyBytesAndIndependentDeferredRemainder() {
        val input=ByteArray(32) {it.toByte()};val key=X509PublicKey.Bytes(X509BytePublicKeyKind.X25519,input)
        input.fill(9);key.bytes.fill(8);assertEquals(0,key.bytes[0].toInt())
        val point=X509SerializedPoint(input);input.fill(7);point.bytes.fill(6);assertEquals(9,point.bytes[0].toInt())
        val oid=mutableListOf(BigInteger.ONE,BigInteger.TWO);val unknown=X509PublicKey.Unknown(oid,input);oid.clear();input.fill(5)
        assertEquals(listOf(BigInteger.ONE,BigInteger.TWO),unknown.oid);assertEquals(7,unknown.bytes[0].toInt())
        val values=X509PublicKeyCodec.toAsn1(key)(emptyList())
        val parsed=X509PublicKeyCodec.fromSequence(sequence {yieldAll(values);yield(Asn1Value.Null);error("tail demanded")}) as Asn1ParseResult.Success
        assertEquals(key,parsed.value.key);assertFalse(parsed.value.remaining.isEmpty())
        assertEquals(emptyList<Asn1Value>(),parsed.value.remaining.take(0))
        assertEquals(Asn1Value.Null,parsed.value.remaining[0])
        assertEquals(listOf(Asn1Value.Null),parsed.value.remainingSequence().take(1).toList())
        assertEquals("tail demanded",assertThrows(IllegalStateException::class.java) {parsed.value.remaining.size}.message)
        // The ASN1Object List API must also retain a lazy nested BER remainder.
        val rsaOid=X509PublicKeyAlgorithm.RSA.getObjectId()
        val bytes=byteArrayOf(48,6,2,1,1,2,1,3,2,0)
        val outer=listOf(Asn1Value.Start(x509Seq),Asn1Value.Start(x509Seq),Asn1Value.Oid(rsaOid),Asn1Value.End(x509Seq),Asn1Value.BitString(Asn1BitArray(0u,bytes)),Asn1Value.End(x509Seq))
        val decoded=X509PublicKeyCodec.fromAsn1(outer) as Asn1ParseResult.Success
        assertEquals(X509PublicKeyAlgorithm.RSA,decoded.value.value.algorithm)
        assertEquals(emptyList<Asn1Value>(),decoded.value.remaining.take(0))
        assertThrows(Asn1Exception::class.java) {decoded.value.remaining.isEmpty()}
        val roots=sequence {yield(Asn1Value.Null);error("tail demanded")}
        assertArrayEquals(byteArrayOf(5,0),Asn1BinaryEncoding().encodeDerSequenceChunks(roots).first())
        assertArrayEquals(X509PublicKeyCodec.encodeBytes(key),Asn1BinaryEncoding().encodeDer(values))
    }
    @TestFactory
    fun originalObservations(): List<DynamicTest> {
        val lines=GZIPInputStream(javaClass.getResourceAsStream("/x509-public-key/data.jsonl.gz")!!).bufferedReader().use {it.readLines()}
        assertEquals(Json.parseToJsonElement(lines.first()).jsonObject.getValue("cases").jsonPrimitive.int,lines.size-1)
        return lines.drop(1).mapIndexed {i,line -> val c=Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest("$i ${c.text("group")}") {assertEquals(c.getValue("expected"),observe(c.getValue("request").jsonObject),c.getValue("request").toString())}
        }
    }
}
