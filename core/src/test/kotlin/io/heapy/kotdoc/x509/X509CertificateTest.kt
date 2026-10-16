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
class X509CertificateTest {
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
            "timeError" -> Asn1Value.Time.deferred({error("kind demanded")},{error("datetime demanded")},{error("zone demanded")})
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
    private val algorithms=listOf(X509PublicKeyAlgorithm.RSA,X509PublicKeyAlgorithm.RSAPSS,X509PublicKeyAlgorithm.DSA,X509PublicKeyAlgorithm.EC,X509PublicKeyAlgorithm.X25519,X509PublicKeyAlgorithm.X448,X509PublicKeyAlgorithm.Ed25519,X509PublicKeyAlgorithm.Ed448,X509PublicKeyAlgorithm.DH)
    private fun algorithm(v: JsonElement)=if(v is JsonArray) X509PublicKeyAlgorithm.Unknown(oid(v[1])) else algorithms[v.jsonPrimitive.int]
    private fun algorithmJSON(v: X509PublicKeyAlgorithm)=if(v is X509PublicKeyAlgorithm.Unknown) array(JsonPrimitive("unknown"),oidJSON(v.oid)) else JsonPrimitive(algorithms.indexOf(v))
    private fun signature(v: JsonElement): X509SignatureAlgorithm {
        if(v==JsonNull) error("signature demanded")
        return v.jsonArray.let {a->when(a[0].jsonPrimitive.content) {"with"->X509SignatureAlgorithm.WithHash(X509HashAlgorithm.entries[a[1].jsonPrimitive.int],algorithm(a[2]));"intrinsic"->X509SignatureAlgorithm.IntrinsicHash(algorithm(a[1]));else->X509SignatureAlgorithm.Unknown(oid(a[1]))}}
    }
    private fun sigJSON(v: X509SignatureAlgorithm): JsonElement=when(v) {
        is X509SignatureAlgorithm.WithHash->array(JsonPrimitive("with"),JsonPrimitive(v.hash.ordinal),algorithmJSON(v.key))
        is X509SignatureAlgorithm.IntrinsicHash->array(JsonPrimitive("intrinsic"),algorithmJSON(v.key))
        is X509SignatureAlgorithm.Unknown->array(JsonPrimitive("unknown"),oidJSON(v.oid))
    }
    private fun dn(v: JsonElement)=if(v==JsonNull) X509DistinguishedName.deferred {error("dn demanded")} else X509DistinguishedName(v.jsonArray.map {it.jsonArray.let {a->X509DnAttribute.deferred({oid(a[0])},Asn1CharacterString.deferred(Asn1StringEncoding.entries[a[1].jsonPrimitive.int]) {data(a[2])})}})
    private fun dnJSON(v: X509DistinguishedName)=JsonArray(v.elements.map {array(oidJSON(it.oid),JsonPrimitive(it.value.encoding.ordinal),byteJSON(it.value.rawData))})
    private fun raw(v: JsonElement): X509ExtensionRaw=v.jsonArray.let {a->X509ExtensionRaw.deferred({oid(a[0])},{if(a[1]==JsonNull) error("critical demanded") else a[1].jsonPrimitive.boolean},{data(a[2])})}
    private fun rawJSON(v: X509ExtensionRaw)=array(oidJSON(v.oid),JsonPrimitive(v.critical),byteJSON(v.content))
    private fun exts(v: JsonElement): X509Extensions=when {
        v==JsonNull->X509Extensions(null)
        v is JsonPrimitive->if(v.content=="listError") X509Extensions.presentDeferred {error("extension list demanded")} else error("extensions demanded")
        else->X509Extensions(v.jsonArray.map(::raw))
    }
    private fun extsJSON(v: X509Extensions): JsonElement=v.values?.let {JsonArray(it.map(::rawJSON))} ?: JsonNull
    private fun date(v: JsonElement): HourglassDateTime {
        if(v==JsonNull) error("datetime demanded")
        return readDateTime(v)
    }
    private fun validity(v: JsonElement): X509CertificateValidity {
        if(v==JsonNull) error("validity demanded")
        return X509CertificateValidity.deferred({date(v.jsonArray[0])},{date(v.jsonArray[1])})
    }
    private fun validityJSON(v: X509CertificateValidity)=array(dateTimeJSON(v.notBefore),dateTimeJSON(v.notAfter))
    private fun certificate(v: JsonElement): X509Certificate {
        fun f(i: Int): JsonElement=if(v==JsonNull) error("certificate demanded") else v.jsonArray[i]
        return X509Certificate.deferred({if(f(0)==JsonNull) error("version demanded") else number(f(0)).toLong()},{number(f(1))},{signature(f(2))},{dn(f(3))},{validity(f(4))},{dn(f(5))},{if(f(6)==JsonNull) error("key demanded") else readKey(f(6))},{exts(f(7))})
    }
    private fun certJSON(v: X509Certificate)=array(JsonPrimitive(v.version.toString()),nJSON(v.serial),sigJSON(v.signatureAlgorithm),dnJSON(v.issuerDN),validityJSON(v.validity),dnJSON(v.subjectDN),keyJSON(v.publicKey),extsJSON(v.extensions))
    private fun field(v: JsonObject,c: X509Certificate): JsonElement=when(v.text("field")) {"version"->JsonPrimitive(c.version.toString());"serial"->nJSON(c.serial);"signature"->sigJSON(c.signatureAlgorithm);"issuer"->dnJSON(c.issuerDN);"validity"->validityJSON(c.validity);"subject"->dnJSON(c.subjectDN);"key"->keyJSON(c.publicKey);else->extsJSON(c.extensions)}
    private fun values(v: JsonObject)=sequence {for(t in v.getValue("values").jsonArray) yield(readValue(t));if(v.bool("tailError")) error("tail demanded")}
    private fun kind(v: Asn1Value)=when(v) {is Asn1Value.Start->"start";is Asn1Value.End->"end";is Asn1Value.IntVal->"integer";is Asn1Value.Oid->"oid";Asn1Value.Null->"null";is Asn1Value.BitString->"bits";is Asn1Value.OctetString->"octets";is Asn1Value.Time->"time";else->"other"}
    private fun valuesJSON(v: List<Asn1Value>)=JsonArray(v.map(::valueJSON))
    private fun observe(v: JsonObject): JsonElement=try {
        fun cert()=certificate(v.getValue("value"))
        fun parsed(r: Asn1ParseResult<X509CertificateParsed>): JsonElement=when(r) {
            is Asn1ParseResult.Failure->if(v.text("part")=="tag") buildJsonObject {put("left",true)} else buildJsonObject {put("left",buildString {r.message.points().forEach(::appendCodePoint)})}
            is Asn1ParseResult.Success->when(v.text("part")) {
                "tag"->buildJsonObject {put("right",true)}
                "remaining"->buildJsonObject {put("remaining",valuesJSON(r.value.remainingSequence().take(v.int("count").coerceAtLeast(0)).toList()))}
                "field"->buildJsonObject {put("field",field(v,r.value.certificate))}
                "rekinds"->buildJsonObject {put("right",JsonArray(X509CertificateCodec.toAsn1Stream(r.value.certificate).take(v.int("count").coerceAtLeast(0)).map {JsonPrimitive(kind(it))}.toList()))}
                "reencode"->buildJsonObject {put("right",valuesJSON(X509CertificateCodec.toAsn1Stream(r.value.certificate).take(v.int("count").coerceAtLeast(0)).toList()))}
                else->buildJsonObject {put("data",certJSON(r.value.certificate));put("remaining",valuesJSON(r.value.remaining));put("shown",r.value.certificate.toString())}
            }
        }
        when(v.text("api")) {
            "value"->{val c=cert();buildJsonObject {put("data",certJSON(c));put("equal",c==certificate(v.getValue("other")));put("shown",c.toString())}}
            "equal"->JsonPrimitive(cert()==certificate(v.getValue("other")))
            "field"->field(v,cert())
            "encode"->{val prefix=X509CertificateCodec.toAsn1Stream(cert(),values(v)).take(v.int("count").coerceAtLeast(0))
                when(v.text("part")) {"spine"->JsonPrimitive(prefix.count());"kinds"->JsonArray(prefix.map {JsonPrimitive(kind(it))}.toList());"shown"->JsonPrimitive(prefix.joinToString(",","[","]"));"bytes"->byteJSON(Asn1BinaryEncoding().encodeDerSequence(prefix));else->valuesJSON(prefix.toList())}
            }
            "from"->parsed(X509CertificateCodec.fromSequence(values(v)))
            "roundtrip"->parsed(X509CertificateCodec.fromSequence(X509CertificateCodec.toAsn1Stream(cert())))
            "property"->{val c=cert();val encoded=X509CertificateCodec.toAsn1(c)(emptyList());val r=X509CertificateCodec.fromAsn1(encoded)
                val good=r is Asn1ParseResult.Success && r.value.remaining.isEmpty() && r.value.value==c
                buildJsonObject {put("property",good);put("values",valuesJSON(encoded))}
            }
            else->error(v)
        }
    } catch(e: RuntimeException) {buildJsonObject {put("fatal",e.message?.lineSequence()?.first())}}
    @Test
    fun cachedFieldsDeferredTimeAndIndependentRemainder() {
        val date=HourglassDateTime(HourglassDate(2050,HourglassMonth.January,1),HourglassTimeOfDay(HourglassHours(0),HourglassMinutes(0),HourglassSeconds(0),HourglassNanoSeconds(0)))
        val calls=mutableListOf<String>()
        val time=Asn1Value.Time.deferred({calls.add("kind");Asn1TimeType.TimeGeneralized},{calls.add("value");date},{calls.add("zone");HourglassTimezoneOffset.UTC})
        assertTrue(calls.isEmpty());assertEquals(Asn1TimeType.TimeGeneralized,time.component1());assertEquals(listOf("kind"),calls)
        assertEquals(date,time.component2());assertEquals(HourglassTimezoneOffset.UTC,time.component3());assertEquals(listOf("kind","value","zone"),calls)
        assertEquals(time,time.copy());assertEquals(time.hashCode(),time.copy().hashCode());assertEquals(listOf("kind","value","zone"),calls)
        var versions=0
        val cert=X509Certificate.deferred({versions++;2},{BigInteger.ONE},{X509SignatureAlgorithm.WithHash(X509HashAlgorithm.HashSHA256,X509PublicKeyAlgorithm.RSA)},{X509DistinguishedName.Empty},{X509CertificateValidity(date,date)},{X509DistinguishedName.Empty},{X509PublicKey.Rsa(X509RsaPublicKey(1,BigInteger.valueOf(127),BigInteger.valueOf(3)))},{X509Extensions(null)})
        assertEquals(0,versions)
        assertEquals(listOf("start","integer","end"),X509CertificateCodec.toAsn1Stream(cert).take(3).map(::kind).toList());assertEquals(0,versions)
        assertEquals(2L,cert.version);assertEquals(2L,cert.version);assertEquals(1,versions)
        val badIssuer=X509Certificate.deferred({cert.version},{cert.serial},{cert.signatureAlgorithm},{error("issuer demanded")},{cert.validity},{cert.subjectDN},{cert.publicKey},{cert.extensions})
        assertEquals("start",X509CertificateCodec.toAsn1Stream(badIssuer).take(9).map(::kind).last())
        assertEquals("issuer demanded",assertThrows(IllegalStateException::class.java) {X509CertificateCodec.toAsn1Stream(badIssuer).take(10).toList()}.message)
        val encoded=X509CertificateCodec.toAsn1(cert)(emptyList())
        val parsed=X509CertificateCodec.fromSequence(sequence {yieldAll(encoded);yield(Asn1Value.Null);error("tail demanded")}) as Asn1ParseResult.Success
        assertEquals(cert,parsed.value.certificate)
        assertEquals(emptyList<Asn1Value>(),parsed.value.remaining.take(0));assertEquals(Asn1Value.Null,parsed.value.remaining[0])
        assertEquals("tail demanded",assertThrows(IllegalStateException::class.java) {parsed.value.remaining.size}.message)
        assertArrayEquals(X509CertificateCodec.encodeBytes(cert),Asn1BinaryEncoding().encodeDer(encoded))
    }
    @TestFactory
    fun originalObservations(): List<DynamicTest> {
        val lines=GZIPInputStream(javaClass.getResourceAsStream("/x509-certificate/data.jsonl.gz")!!).bufferedReader().use {it.readLines()}
        assertEquals(Json.parseToJsonElement(lines.first()).jsonObject.getValue("cases").jsonPrimitive.int,lines.size-1)
        return lines.drop(1).mapIndexed {i,line->val c=Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest("$i ${c.text("group")}") {assertEquals(c.getValue("expected"),observe(c.getValue("request").jsonObject),c.getValue("request").toString())}
        }
    }
}
