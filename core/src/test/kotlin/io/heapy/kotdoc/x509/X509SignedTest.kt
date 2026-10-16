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
class X509SignedTest {
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
    private class Probe(val n: Lazy<BigInteger>) {
        override fun equals(other: Any?)=other is Probe && n.value==other.n.value
        override fun hashCode()=n.value.hashCode()
        override fun toString()="Probe ${n.value.let {if(it.signum()<0) "($it)" else "$it"}}"
    }
    private val probeCodec=object: Asn1Object<Probe> {
        override fun toAsn1(value: Probe): Asn1StreamBuilder={tail->listOf(Asn1Value.IntVal.deferred {value.n.value})+tail}
        override fun fromAsn1(values: List<Asn1Value>): Asn1ParseResult<Asn1Parsed<Probe>> {
            val n=values.firstOrNull() as? Asn1Value.IntVal ?: return Asn1ParseResult.Failure("probe: expected integer")
            return Asn1ParseResult.Success(Asn1Parsed.deferred({Probe(lazy {n.value})},values.drop(1)))
        }
    }
    private fun probe(v: JsonElement)=Probe(lazy {if(v is JsonPrimitive && v.content=="objectError") error("object demanded");number(v)})
    private fun signatureBytes(v: JsonElement): ByteArray {if(v is JsonPrimitive && v!=JsonNull) error("signature demanded");return bytes(v)}
    private fun <A> signed(v: JsonElement,read: (JsonElement)->A): X509Signed<A> {
        if(v==JsonNull) error("signed demanded")
        val a=v.jsonArray
        return X509Signed.deferred({read(a[0])},{signature(a[1])},{signatureBytes(a[2])})
    }
    private fun <A> signedJSON(s: X509Signed<A>,json: (A)->JsonElement)=array(json(s.signedObject),sigJSON(s.signedAlgorithm),byteJSON(s.signedSignature))
    private fun <A> observedSigned(s: X509Signed<A>,v: JsonObject,json: (A)->JsonElement): JsonElement=when(v.text("field")) {
        "tag"->JsonPrimitive(true);"object"->json(s.signedObject);"algorithm"->sigJSON(s.signedAlgorithm);"signature"->byteJSON(s.signedSignature);"shown"->JsonPrimitive(s.toString());else->signedJSON(s,json)
    }
    private fun <A> observedExact(e: X509SignedExact<A>,v: JsonObject,json: (A)->JsonElement): JsonElement=when(v.text("part")) {
        "tag"->JsonPrimitive(true);"signed"->observedSigned(e.signed,v,json);"raw"->byteJSON(e.signedData);"bytes"->byteJSON(e.encodeSignedObject());"shown"->JsonPrimitive(e.toString())
        else->buildJsonObject {put("signed",signedJSON(e.signed,json));put("raw",byteJSON(e.signedData));put("bytes",byteJSON(e.encodeSignedObject()));put("shown",e.toString())}
    }
    private fun signatureResult(v: JsonObject,raw: Lazy<ByteArray>): X509SignatureResult<Int> {
        if(v.text("signer")=="throw") error("signer demanded")
        return X509SignatureResult.deferred({X509Signature.deferred({if(v.text("signer")=="echo") raw.value else signatureBytes(v.getValue("signature"))},{signature(v.getValue("algorithm"))})},{if(v.text("signer")=="length") raw.value.size else v.int("result")})
    }
    private class ListTag
    private class ListEffect<A>(val values: List<Lazy<A>>): X509Kind<ListTag,A>
    private object ListFunctor: X509Functor<ListTag> {
        override fun <A,B> map(value: X509Kind<ListTag,A>,transform: (A)->B): X509Kind<ListTag,B> = ListEffect((value as ListEffect<A>).values.map {lazy {transform(it.value)}})
    }
    private class MaybeTag
    private class MaybeEffect<A>(val value: Lazy<A>?): X509Kind<MaybeTag,A>
    private object MaybeFunctor: X509Functor<MaybeTag> {
        override fun <A,B> map(value: X509Kind<MaybeTag,A>,transform: (A)->B): X509Kind<MaybeTag,B> = MaybeEffect((value as MaybeEffect<A>).value?.let {lazy {transform(it.value)}})
    }
    private class WriterTag
    private class WriterEffect<A>(val log: String,val value: Lazy<A>): X509Kind<WriterTag,A>
    private object WriterFunctor: X509Functor<WriterTag> {
        override fun <A,B> map(value: X509Kind<WriterTag,A>,transform: (A)->B): X509Kind<WriterTag,B> {value as WriterEffect<A>;return WriterEffect(value.log,lazy {transform(value.value.value)})}
    }
    private fun <A> observeFor(v: JsonObject,read: (JsonElement)->A,json: (A)->JsonElement,obj: Asn1Object<A>): JsonElement {
        val codec=X509SignedCodec(obj,X509PosixTimeReference.binary())
        return when(v.text("api")) {
            "signed"->observedSigned(signed(v.getValue("value"),read),v,json)
            "equal"->JsonPrimitive(signed(v.getValue("value"),read)==signed(v.getValue("other"),read))
            "toExact"->observedExact(codec.signedToExactDeferred(lazy {signed(v.getValue("value"),read)}),v,json)
            "create","objectToSigned"->{val objectValue=lazy {read(v.getValue("value"))}
                val r=if(v.text("api")=="objectToSigned") codec.objectToSignedDeferred(objectValue) {raw->signatureResult(v,raw)} else codec.objectToSignedExactDeferred(objectValue) {raw->signatureResult(v,raw)}
                if(v.text("part")=="result") JsonPrimitive(r.value) else if(v.text("api")=="objectToSigned") observedSigned(r.signed,v,json) else observedExact(r.signedExact,v,json)}
            "functor"->{
                fun sign(raw: Lazy<ByteArray>): X509Signature {val result=lazy {signatureResult(v,raw)};return X509Signature.deferred({result.value.signature.signature},{result.value.signature.signatureAlgorithm})}
                val value=lazy {read(v.getValue("value"))}
                when(v.text("effect")) {
                    "list"->{val effect=codec.objectToSignedExactFDeferred(value,{raw->ListEffect(List(v.int("count")) {lazy {sign(raw)}})},ListFunctor).value as ListEffect
                        JsonArray(effect.values.map {observedExact(it.value,v,json)})}
                    "maybe"->{val effect=codec.objectToSignedExactFDeferred(value,{raw->MaybeEffect(if(v.bool("present")) lazy {sign(raw)} else null)},MaybeFunctor).value as MaybeEffect
                        effect.value?.let {observedExact(it.value,v,json)} ?: JsonNull}
                    else->{val effect=codec.objectToSignedExactFDeferred(value,{raw->WriterEffect(v.text("log"),lazy {sign(raw)})},WriterFunctor).value as WriterEffect
                        array(JsonPrimitive(effect.log),observedExact(effect.value.value,v,json))}
                }
            }
            "decode"->when(val r=codec.decodeSignedObject(bytes(v.getValue("bytes")))) {
                is Asn1ParseResult.Failure->buildJsonObject {put("left",if(v.text("part")=="tag") JsonPrimitive(true) else JsonPrimitive(buildString {r.message.points().forEach(::appendCodePoint)}))}
                is Asn1ParseResult.Success->buildJsonObject {put("right",if(v.text("part")=="tag") JsonPrimitive(true) else observedExact(r.value,v,json))}
            }
            "decodeEqual"->{val a=codec.decodeSignedObject(bytes(v.getValue("bytes")));val b=codec.decodeSignedObject(bytes(v.getValue("other")))
                JsonPrimitive(a is Asn1ParseResult.Success && b is Asn1ParseResult.Success && a.value==b.value)}
            else->error(v)
        }
    }
    private fun observe(v: JsonObject): JsonElement=try {
        when(v.text("kind")) {"certificate"->observeFor(v,::certificate,::certJSON,X509CertificateCodec);"crl"->observeFor(v,::crl,::crlJSON,X509CrlCodec);else->observeFor(v,::probe,{nJSON(it.n.value)},probeCodec)}
    } catch(e: RuntimeException) {buildJsonObject {put("fatal",e.message?.lineSequence()?.first())}}
    @Test
    fun ownershipCacheAndSignerDemand() {
        val codec=X509SignedCodec(probeCodec)
        var calls=0
        val result=codec.objectToSignedExactDeferred(lazy {Probe(lazyOf(BigInteger.valueOf(7)))}) {raw->
            calls++;val bytes=raw.value;bytes[0]=0
            X509SignatureResult(X509Signature(byteArrayOf(1,2),X509SignatureAlgorithm.WithHash(X509HashAlgorithm.HashSHA256,X509PublicKeyAlgorithm.RSA)),19)
        }
        assertEquals(0,calls);assertEquals(19,result.value);assertEquals(1,calls)
        val exact=result.signedExact;assertEquals(48,exact.signedData[0].toInt())
        exact.signedData[0]=0;exact.encodeSignedObject()[0]=0;exact.signed.signedSignature[0]=0
        assertEquals(48,exact.encodeSignedObject()[0].toInt());assertArrayEquals(byteArrayOf(1,2),exact.signed.signedSignature);assertEquals(1,calls)
        val bytes=exact.encodeSignedObject();val decoded=codec.decodeSignedObject(bytes) as Asn1ParseResult.Success;bytes.fill(0)
        assertEquals(exact,decoded.value)
        val unneeded=codec.objectToSignedExactDeferred(lazy<Probe> {error("object demanded")}) {X509SignatureResult.deferred({error("signature demanded")},{23})}
        assertEquals(23,unneeded.value)
        val empty=codec.objectToSignedExactFDeferred(lazy<Probe> {error("object demanded")},{ListEffect<X509Signature>(emptyList())},ListFunctor).value as ListEffect
        assertTrue(empty.values.isEmpty())
    }
    @TestFactory
    fun originalObservations(): List<DynamicTest> {
        val lines=GZIPInputStream(javaClass.getResourceAsStream("/x509-signed/data.jsonl.gz")!!).bufferedReader().use {it.readLines()}
        assertEquals(Json.parseToJsonElement(lines.first()).jsonObject.getValue("cases").jsonPrimitive.int,lines.size-1)
        return lines.drop(1).mapIndexed {i,line->val c=Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest("$i ${c.text("group")}") {assertEquals(c.getValue("expected"),observe(c.getValue("request").jsonObject),c.getValue("request").toString())}
        }
    }
}
