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
class X509UtilitiesTest {
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
    private fun date(v: JsonElement): HourglassDateTime {if(v==JsonNull || v is JsonPrimitive) error("datetime demanded");return readDateTime(v)}
    private fun revoked(v: JsonElement): X509RevokedCertificate {
        if(v==JsonNull) error("revoked demanded")
        val a=v.jsonArray
        return X509RevokedCertificate.deferred({number(a[0])},{date(a[1])},{exts(a[2])})
    }
    private fun revokedJSON(v: X509RevokedCertificate)=array(nJSON(v.serialNumber),dateTimeJSON(v.revocationDate),extsJSON(v.extensions))
    private fun next(v: JsonElement): Asn1Maybe<HourglassDateTime> = when {
        v==JsonNull->Asn1Maybe.None
        v is JsonPrimitive && v.content=="maybeError"->error("next demanded")
        else->Asn1Maybe.Some.deferred {date(v)}
    }
    private fun crl(v: JsonElement): X509Crl {
        fun f(i: Int): JsonElement=if(v==JsonNull) error("crl demanded") else v.jsonArray[i]
        return X509Crl.deferred({number(f(0))},{signature(f(1))},{dn(f(2))},{date(f(3))},{next(f(4))},{
            val rs=f(5);if(rs==JsonNull || rs is JsonPrimitive) error("revoked list demanded")
            rs.jsonArray.asSequence().map {r->lazy {revoked(r)}}
        },{exts(f(6))})
    }
    private fun nextJSON(v: Asn1Maybe<HourglassDateTime>): JsonElement=when(v) {Asn1Maybe.None->JsonNull;is Asn1Maybe.Some->dateTimeJSON(v.value)}
    private fun revokedListJSON(cells: Sequence<Lazy<X509RevokedCertificate>>)=JsonArray(cells.toList().map {revokedJSON(it.value)})
    private fun crlJSON(c: X509Crl)=array(nJSON(c.version),sigJSON(c.signatureAlgorithm),dnJSON(c.issuer),dateTimeJSON(c.thisUpdate),nextJSON(c.nextUpdateMaybe),revokedListJSON(c.revokedCells()),extsJSON(c.extensions))
    private fun crlField(v: JsonObject,c: X509Crl): JsonElement=when(v.text("field")) {
        "version"->nJSON(c.version)
        "signature"->sigJSON(c.signatureAlgorithm)
        "issuer"->dnJSON(c.issuer)
        "this"->dateTimeJSON(c.thisUpdate)
        "next"->nextJSON(c.nextUpdateMaybe)
        "nextTag"->JsonPrimitive(c.nextUpdateMaybe is Asn1Maybe.Some)
        "revokedSpine"->JsonPrimitive(c.revokedCells().take(v.int("count").coerceAtLeast(0)).count())
        "revokedPrefix"->revokedListJSON(c.revokedCells().take(v.int("count").coerceAtLeast(0)))
        "revoked"->revokedListJSON(c.revokedCells())
        else->extsJSON(c.extensions)
    }
    private fun revokedField(v: JsonObject,r: X509RevokedCertificate): JsonElement=when(v.text("field")) {"serial"->nJSON(r.serialNumber);"date"->dateTimeJSON(r.revocationDate);else->extsJSON(r.extensions)}
    private fun values(v: JsonObject)=sequence {for(t in v.getValue("values").jsonArray) yield(readValue(t));if(v.bool("tailError")) error("tail demanded")}
    private fun kind(v: Asn1Value)=when(v) {is Asn1Value.Start->"start";is Asn1Value.End->"end";is Asn1Value.IntVal->"integer";is Asn1Value.Oid->"oid";Asn1Value.Null->"null";is Asn1Value.BitString->"bits";is Asn1Value.OctetString->"octets";is Asn1Value.Time->"time";else->"other"}
    private fun valuesJSON(v: List<Asn1Value>)=JsonArray(v.map(::valueJSON))
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
    private fun ecPoint(v: JsonElement): X509EcPoint = when(v) {
        JsonNull->X509EcPoint.Infinity
        is JsonArray->X509EcPoint.Affine.deferred({number(v[0])},{number(v[1])})
        else->error("point demanded")
    }
    private fun ecPointJSON(p: X509EcPoint): JsonElement=when(p) {X509EcPoint.Infinity->JsonNull;is X509EcPoint.Affine->array(nJSON(p.x),nJSON(p.y))}
    private fun common(v: JsonElement): X509EcCurveCommon {
        if(v==JsonNull) error("common demanded")
        val a=v.jsonArray
        return X509EcCurveCommon.deferred({number(a[0])},{number(a[1])},{ecPoint(a[2])},{number(a[3])},{number(a[4])})
    }
    private fun commonJSON(c: X509EcCurveCommon)=array(nJSON(c.a),nJSON(c.b),ecPointJSON(c.generator),nJSON(c.order),nJSON(c.cofactor))
    private fun curve(v: JsonElement): X509EcCurve {
        if(v==JsonNull) error("curve demanded")
        val a=v.jsonArray
        return when(a[0].jsonPrimitive.content) {
            "named"->X509Ec.getCurveByName(X509CurveName.entries[a[1].jsonPrimitive.int])
            "prime"->X509EcCurve.Prime.deferred({number(a[1])},{common(a[2])})
            else->X509EcCurve.Binary.deferred({number(a[1])},{common(a[2])})
        }
    }
    private fun curveJSON(c: X509EcCurve)=array(JsonPrimitive(if(c is X509EcCurve.Prime) "prime" else "binary"),nJSON(c.modulus),commonJSON(c.common))
    private fun curveField(v: JsonObject,c: X509EcCurve): JsonElement=when(v.text("part")) {
        "tag"->JsonPrimitive(true);"bits"->JsonPrimitive(X509Ec.curveSizeBits(c));"modulus"->nJSON(c.modulus);"a"->nJSON(c.common.a);"b"->nJSON(c.common.b);"generator"->ecPointJSON(c.common.generator);"order"->nJSON(c.common.order);"cofactor"->nJSON(c.common.cofactor);"shown"->JsonPrimitive(c.toString());else->curveJSON(c)
    }
    private fun maybeCurve(v: JsonObject,c: Asn1Maybe<X509EcCurve>): JsonElement=when(c) {Asn1Maybe.None->JsonNull;is Asn1Maybe.Some->if(v.text("part")=="tag") JsonPrimitive(true) else curveField(v,c.value)}
    private fun pointBytes(v: JsonElement): ByteArray {if(v !is JsonArray) error("point bytes demanded");return bytes(v)}
    private fun publicEc(v: JsonElement): X509EcPublicKey {
        if(v==JsonNull) error("key demanded")
        val a=v.jsonArray
        return if(a[0].jsonPrimitive.content=="named") X509EcPublicKey.Named(X509CurveName.entries[a[1].jsonPrimitive.int],X509SerializedPoint.deferred {pointBytes(a[2])})
        else X509EcPublicKey.Prime(X509SerializedPoint.deferred {pointBytes(a[1])},lazy {number(a[2])},lazy {number(a[3])},lazy {number(a[4])},X509SerializedPoint.deferred {pointBytes(a[5])},lazy {number(a[6])},lazy {number(a[7])},lazy {number(a[8])})
    }
    private fun privateEc(v: JsonElement): X509EcPrivateKey {
        if(v==JsonNull) error("key demanded")
        val a=v.jsonArray
        return if(a[0].jsonPrimitive.content=="named") X509EcPrivateKey.Named(X509CurveName.entries[a[1].jsonPrimitive.int],lazy {number(a[2])})
        else X509EcPrivateKey.Prime(X509SecretIntegers(listOf(1,2,3,4,6,7,8).map {i->lazy {number(a[i])}}),X509SerializedPoint.deferred {pointBytes(a[5])})
    }
    private fun rawCertificate(v: JsonElement): ByteArray {if(v !is JsonArray) error("raw certificate demanded");return bytes(v)}
    private fun rawChain(v: JsonElement,tailError: Boolean)=X509CertificateChainRaw.deferred {sequence {
        if(v==JsonNull) error("chain demanded")
        for(x in v.jsonArray) yield(lazy {rawCertificate(x)})
        if(tailError) error("tail demanded")
    }}
    private fun chain(v: JsonElement,tailError: Boolean)=X509CertificateChain.deferred {sequence {
        if(v==JsonNull) error("chain demanded")
        for(x in v.jsonArray) yield(lazy {
            if(x==JsonNull) error("certificate demanded")
            when(val r=X509.decodeSignedCertificate(rawCertificate(x))) {is Asn1ParseResult.Success->r.value;is Asn1ParseResult.Failure->error(buildString {r.message.points().forEach(::appendCodePoint)})}
        })
        if(tailError) error("tail demanded")
    }}
    private fun rawChainJSON(v: JsonObject,raw: X509CertificateChainRaw): JsonElement=when(v.text("part")) {
        "spine"->JsonPrimitive(raw.rawCells().take(v.int("count")).count())
        "shown"->JsonPrimitive(raw.toString())
        else->JsonArray(raw.rawCells().take(v.int("count")).toList().map {byteJSON(it.value)})
    }
    private fun chainJSON(v: JsonObject,chain: X509CertificateChain): JsonElement=when(v.text("part")) {
        "spine"->JsonPrimitive(chain.certificateCells().take(v.int("count")).count())
        "shown"->JsonPrimitive(chain.toString())
        "certificates"->JsonArray(chain.certificateCells().take(v.int("count")).toList().map {certJSON(X509.getCertificate(it.value))})
        else->rawChainJSON(v,X509CertificateChainCodec().encodeCertificateChain(chain))
    }
    private fun observe(v: JsonObject): JsonElement=try {
        when(v.text("api")) {
            "curve"->curveField(v,curve(v.getValue("curve")))
            "curveEqual"->JsonPrimitive(curve(v.getValue("curve"))==curve(v.getValue("other")))
            "valid"->JsonPrimitive(X509Ec.isPointValidDeferred(lazy {curve(v.getValue("curve"))},ecPoint(v.getValue("point"))))
            "unserialize"->when(val p=X509Ec.unserializePointDeferred(lazy {curve(v.getValue("curve"))},X509SerializedPoint.deferred {pointBytes(v.getValue("bytes"))})) {Asn1Maybe.None->JsonNull;is Asn1Maybe.Some->ecPointJSON(p.value)}
            "publicCurve"->maybeCurve(v,X509Ec.ecPubKeyCurve(publicEc(v.getValue("key"))))
            "privateCurve"->maybeCurve(v,X509Ec.ecPrivKeyCurve(privateEc(v.getValue("key"))))
            "publicName"->X509Ec.ecPubKeyCurveName(publicEc(v.getValue("key")))?.let {JsonPrimitive(it.ordinal)} ?: JsonNull
            "privateName"->X509Ec.ecPrivKeyCurveName(privateEc(v.getValue("key")))?.let {JsonPrimitive(it.ordinal)} ?: JsonNull
            "oid"->X509Ec.lookupCurveNameByOid(oid(v.getValue("oid")))?.let {JsonPrimitive(it.ordinal)} ?: JsonNull
            "hash"->byteJSON(X509.hashDn(dn(v.getValue("dn"))))
            "hashOld"->byteJSON(X509.hashDnOld(dn(v.getValue("dn"))))
            "rawChain"->rawChainJSON(v,rawChain(v.getValue("value"),v.bool("tailError")))
            "encodeChain"->rawChainJSON(v,X509CertificateChainCodec().encodeCertificateChain(chain(v.getValue("value"),v.bool("tailError"))))
            "rawEqual"->JsonPrimitive(rawChain(v.getValue("value"),v.bool("tailError"))==rawChain(v.getValue("other"),v.bool("otherTailError")))
            "chainEqual"->JsonPrimitive(chain(v.getValue("value"),v.bool("tailError"))==chain(v.getValue("other"),v.bool("otherTailError")))
            "decodeChain"->when(val r=X509CertificateChainCodec().decodeCertificateChain(rawChain(v.getValue("value"),v.bool("tailError")))) {
                is X509CertificateChainResult.Failure->when(v.text("part")) {"tag"->buildJsonObject {put("left",true)};"index"->JsonPrimitive(r.index);else->buildJsonObject {put("index",r.index);put("error",buildString {r.message.points().forEach(::appendCodePoint)})}}
                is X509CertificateChainResult.Success->buildJsonObject {put("right",if(v.text("part")=="tag") JsonPrimitive(true) else chainJSON(v,r.chain))}
            }
            "certificate"->when(val r=X509.decodeSignedCertificate(bytes(v.getValue("bytes")))) {is Asn1ParseResult.Failure->buildJsonObject {put("left",buildString {r.message.points().forEach(::appendCodePoint)})};is Asn1ParseResult.Success->buildJsonObject {put("right",certJSON(X509.getCertificate(r.value)))}}
            "crl"->when(val r=X509.decodeSignedCrl(bytes(v.getValue("bytes")))) {is Asn1ParseResult.Failure->buildJsonObject {put("left",buildString {r.message.points().forEach(::appendCodePoint)})};is Asn1ParseResult.Success->buildJsonObject {put("right",crlJSON(X509.getCrl(r.value)))}}
            else->error(v)
        }
    } catch(e: RuntimeException) {buildJsonObject {put("fatal",e.message?.lineSequence()?.first())}}
    @Test
    fun listByteOwnershipAndIndependentChainDemand() {
        val input=byteArrayOf(5,0);val inputs=mutableListOf(input)
        val raw=X509CertificateChainRaw(inputs);input[0]=0;inputs.clear();raw.certificates[0][0]=0;raw.rawCells().first().value[0]=0
        assertArrayEquals(byteArrayOf(5,0),raw.certificates[0])
        val codec=X509CertificateChainCodec()
        val fail=codec.decodeCertificateChain(X509CertificateChainRaw.deferred {sequence {yield(lazyOf(byteArrayOf()));yield(lazy<ByteArray> {error("later certificate demanded")});error("tail demanded")}}) as X509CertificateChainResult.Failure
        assertEquals(0L,fail.index);assertTrue(buildString {fail.message.points().forEach(::appendCodePoint)}.contains("empty"))
        val chain=X509CertificateChain.deferred {sequence {yield(lazy<X509SignedCertificate> {error("certificate demanded")});error("tail demanded")}}
        val encoded=codec.encodeCertificateChain(chain)
        assertTrue(encoded.certificates.take(0).isEmpty());assertEquals(1,encoded.rawCells().take(1).count())
        assertEquals("certificate demanded",assertThrows(IllegalStateException::class.java) {encoded.certificates[0]}.message)
        assertEquals("tail demanded",assertThrows(IllegalStateException::class.java) {encoded.certificates.size}.message)
        val ec=X509EcPrivateKey.Named(X509CurveName.SEC_p256r1,lazy {error("secret demanded")})
        assertEquals(X509CurveName.SEC_p256r1,X509Ec.ecPrivKeyCurveName(ec));assertTrue(X509Ec.ecPrivKeyCurve(ec) is Asn1Maybe.Some)
        val curve=X509Ec.getCurveByName(X509CurveName.SEC_p256r1)
        assertTrue(X509Ec.isPointValid(curve,curve.common.generator))
        assertTrue(X509Ec.isPointValidDeferred(lazy {error("curve demanded")},X509EcPoint.Infinity))
    }
    @TestFactory
    fun originalObservations(): List<DynamicTest> {
        val lines=GZIPInputStream(javaClass.getResourceAsStream("/x509-utilities/data.jsonl.gz")!!).bufferedReader().use {it.readLines()}
        assertEquals(Json.parseToJsonElement(lines.first()).jsonObject.getValue("cases").jsonPrimitive.int,lines.size-1)
        return lines.drop(1).mapIndexed {i,line->val c=Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest("$i ${c.text("group")}") {assertEquals(c.getValue("expected"),observe(c.getValue("request").jsonObject),c.getValue("request").toString())}
        }
    }
}
