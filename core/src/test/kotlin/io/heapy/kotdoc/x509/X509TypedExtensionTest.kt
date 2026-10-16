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
class X509TypedExtensionTest {
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
    private fun oid(v: JsonElement): Asn1Oid = if(v==JsonNull) error("oid demanded") else v.jsonArray.map {it.jsonPrimitive.content.toBigInteger()}
    private fun oidJSON(v: Asn1Oid)=JsonArray(v.map {JsonPrimitive(it.toString())})
    private fun data(v: JsonElement)=if(v==JsonNull) error("content demanded") else bytes(v)
    private fun raw(v: JsonElement): X509ExtensionRaw = v.jsonArray.let {a -> X509ExtensionRaw.deferred(
        {oid(a[0])},{if(a[1]==JsonNull) error("critical demanded") else a[1].jsonPrimitive.boolean},{data(a[2])})}
    private fun rawJSON(v: X509ExtensionRaw)=array(oidJSON(v.oid),JsonPrimitive(v.critical),byteJSON(v.content))
    private fun exts(v: JsonElement): X509Extensions = when {
        v==JsonNull -> X509Extensions(null)
        v is JsonPrimitive -> X509Extensions.presentDeferred {error("extension list demanded")}
        else -> X509Extensions.presentDeferred {v.jsonArray.map(::raw)}
    }
    private fun dn(v: JsonElement)=X509DistinguishedName(v.jsonArray.map {it.jsonArray.let {a -> X509DnAttribute(oid(a[0]),Asn1CharacterString(Asn1StringEncoding.entries[a[1].jsonPrimitive.int],data(a[2])))}})
    private fun dnJSON(d: X509DistinguishedName)=JsonArray(d.elements.map {array(oidJSON(it.oid),JsonPrimitive(it.value.encoding.ordinal),byteJSON(it.value.rawData))})
    private fun name(v: JsonElement): X509AltName = v.jsonArray.let {a -> when(a[0].jsonPrimitive.content) {
        "dn" -> X509AltName.DN(dn(a[1]))
        "ip" -> X509AltName.IP(data(a[1]))
        else -> X509AltName.Text(when(a[0].jsonPrimitive.content) {"rfc822"->X509TextNameKind.RFC822;"dns"->X509TextNameKind.DNS;"uri"->X509TextNameKind.URI;"xmpp"->X509TextNameKind.XMPP;else->X509TextNameKind.DNSSRV},CharacterString(a[1].jsonArray.map {it.jsonPrimitive.int}.toIntArray()))
    }}
    private fun nameJSON(n: X509AltName)=when(n) {
        is X509AltName.DN -> array(JsonPrimitive("dn"),dnJSON(n.value))
        is X509AltName.IP -> array(JsonPrimitive("ip"),byteJSON(n.bytes))
        is X509AltName.Text -> array(JsonPrimitive(when(n.type) {X509TextNameKind.RFC822->"rfc822";X509TextNameKind.DNS->"dns";X509TextNameKind.URI->"uri";X509TextNameKind.XMPP->"xmpp";X509TextNameKind.DNSSRV->"srv"}),JsonArray(n.text.points().map(::JsonPrimitive)))
    }
    private fun purpose(v: JsonElement)=if(v is JsonArray) X509KeyUsagePurpose.Unknown(oid(v[1])) else x509Purposes[v.jsonPrimitive.int]
    private fun purposeJSON(p: X509KeyUsagePurpose)=if(p is X509KeyUsagePurpose.Unknown) array(JsonPrimitive("unknown"),oidJSON(p.oid)) else JsonPrimitive(x509Purposes.indexOf(p))
    private fun sub(v: JsonElement)=v.jsonArray.let {a -> X509GeneralSubtree(name(a[0]),a[1].jsonPrimitive.content.toBigInteger(),if(a[2]==JsonNull) null else a[2].jsonPrimitive.content.toBigInteger())}
    private fun subJSON(s: X509GeneralSubtree)=array(nameJSON(s.base),JsonPrimitive(s.minimum.toString()),s.maximum?.let {JsonPrimitive(it.toString())} ?: JsonNull)
    private fun point(v: JsonElement): X509DistributionPoint=v.jsonArray.let {a -> if(a[0].jsonPrimitive.content=="full") X509DistributionPoint.FullName(a[1].jsonArray.map(::name)) else X509DistributionPoint.Relative(dn(a[1]))}
    private fun pointJSON(p: X509DistributionPoint)=when(p) {
        is X509DistributionPoint.FullName -> array(JsonPrimitive("full"),JsonArray(p.names.map(::nameJSON)))
        is X509DistributionPoint.Relative -> array(JsonPrimitive("relative"),dnJSON(p.name))
    }
    private fun extension(k: Int,v: JsonElement): X509TypedExtension=when(k) {
        0 -> v.jsonArray.let {X509BasicConstraints(it[0].jsonPrimitive.boolean,if(it[1]==JsonNull) null else it[1].jsonPrimitive.content.toBigInteger())}
        1 -> if(v is JsonPrimitive) X509KeyUsage.deferred {error("flags demanded")} else X509KeyUsage(v.jsonArray.map {X509KeyUsageFlag.fromOrdinal(it.jsonPrimitive.long)})
        2 -> if(v is JsonPrimitive) X509ExtendedKeyUsage.deferred {error("purposes demanded")} else X509ExtendedKeyUsage(v.jsonArray.map(::purpose))
        3 -> X509SubjectKeyId.deferred {data(v)}
        4 -> X509SubjectAltName(v.jsonArray.map(::name))
        5 -> X509AuthorityKeyId.deferred {data(v)}
        6 -> X509CrlDistributionPoints(v.jsonArray.map(::point))
        7 -> v.jsonArray.let {X509NameConstraints(it[0].jsonArray.map(::sub),it[1].jsonArray.map(::sub))}
        else -> X509NetscapeComment.deferred {data(v)}
    }
    private fun extensionJSON(v: X509TypedExtension): JsonElement=when(v) {
        is X509BasicConstraints -> array(JsonPrimitive(v.authority),v.pathLength?.let {JsonPrimitive(it.toString())} ?: JsonNull)
        is X509KeyUsage -> JsonArray(v.flagCells().toList().map {JsonPrimitive(it.value.ordinal)})
        is X509ExtendedKeyUsage -> JsonArray(v.purposeCells().toList().map {purposeJSON(it.value)})
        is X509ByteExtension -> byteJSON(v.bytes)
        is X509SubjectAltName -> JsonArray(v.cells().toList().map {nameJSON(it.value)})
        is X509CrlDistributionPoints -> JsonArray(v.points.map(::pointJSON))
        is X509NameConstraints -> array(JsonArray(v.permitted.map(::subJSON)),JsonArray(v.excluded.map(::subJSON)))
    }
    private fun ordering(r: Int)=JsonPrimitive(if(r<0) "LT" else if(r>0) "GT" else "EQ")
    private fun payload(v: X509TypedExtension,part: String,count: Int): JsonElement {
        val cells: Sequence<Lazy<*>> = when(v) {is X509KeyUsage->v.flagCells();is X509ExtendedKeyUsage->v.purposeCells();is X509SubjectAltName->v.cells();else->return JsonNull}
        val prefix=cells.take(count.coerceAtLeast(0))
        if(part=="payloadSpine") return JsonPrimitive(prefix.count())
        return JsonArray(prefix.toList().map {when(val n=it.value) {is X509KeyUsageFlag->JsonPrimitive(n.ordinal);is X509KeyUsagePurpose->purposeJSON(n);is X509AltName->nameJSON(n);else->error(n.toString())}}.toList())
    }
    private fun kind(v: Asn1Value)=when(v) {is Asn1Value.BooleanValue->"boolean";is Asn1Value.IntVal->"integer";is Asn1Value.Oid->"oid";is Asn1Value.OctetString->"octets";is Asn1Value.BitString->"bits";is Asn1Value.StringValue->"string";Asn1Value.Null->"null";is Asn1Value.Start->"start";is Asn1Value.End->"end";else->"other"}
    private fun valuesJSON(v: List<Asn1Value>)=JsonArray(v.map(::valueJSON))
    private fun observe(v: JsonObject): JsonElement = try {
        val api=v.text("api")
        val k=v["kind"]?.jsonPrimitive?.int ?: 0
        val codec=X509ExtensionCodecs.forKind(X509ExtensionKind.entries[k])
        fun value()=extension(k,v.getValue("value"))
        fun shown(x: X509TypedExtension)=buildJsonObject {put("data",extensionJSON(x));put("shown",x.toString())}
        fun either(r: Asn1ParseResult<X509TypedExtension>): JsonElement=when(r) {
            is Asn1ParseResult.Failure -> if(v.text("part")=="tag") buildJsonObject {put("left",true)} else buildJsonObject {put("left",buildString {r.message.points().forEach(::appendCodePoint)})}
            is Asn1ParseResult.Success -> when(v.text("part")) {
                "tag" -> buildJsonObject {put("right",true)}
                "payloadSpine","payloadPrefix" -> buildJsonObject {put("right",payload(r.value,v.text("part"),v.int("count")))}
                "reencode" -> buildJsonObject {put("right",valuesJSON(codec.encodeStream(r.value).take(v.int("count").coerceAtLeast(0)).toList()))}
                "rekinds" -> buildJsonObject {put("right",JsonArray(codec.encodeStream(r.value).take(v.int("count").coerceAtLeast(0)).map {JsonPrimitive(kind(it))}.toList()))}
                else -> buildJsonObject {put("right",shown(r.value))}
            }
        }
        fun maybeEither(r: Asn1Maybe<Asn1ParseResult<X509TypedExtension>>): JsonElement=when(r) {Asn1Maybe.None->JsonNull;is Asn1Maybe.Some->if(v.text("part")=="maybe") JsonPrimitive(true) else either(r.value)}
        when(api) {
            "recognized" -> JsonArray(X509ExtensionCodecs.recognizedOids.map(::oidJSON))
            "metadata" -> buildJsonObject {put("oid",oidJSON(codec.oid));put("nested",codec.hasNestedAsn1)}
            "value" -> {val x=value();val y=extension(k,v.getValue("other"));buildJsonObject {put("shown",x.toString());put("data",extensionJSON(x));put("equal",x==y);put("ordering",if(x is X509SubjectAltName && y is X509SubjectAltName) ordering(x.compareTo(y)) else JsonNull)}}
            "model" -> {
                val x: Any;val y: Any
                when(v.text("type")) {"alt"->{x=name(v.getValue("value"));y=name(v.getValue("other"))};"purpose"->{x=purpose(v.getValue("value"));y=purpose(v.getValue("other"))};"subtree"->{x=sub(v.getValue("value"));y=sub(v.getValue("other"))};else->{x=point(v.getValue("value"));y=point(v.getValue("other"))}}
                buildJsonObject {put("shown",x.toString());put("equal",x==y);when {x is X509AltName && y is X509AltName->put("ordering",ordering(x.compareTo(y)));x is X509KeyUsagePurpose && y is X509KeyUsagePurpose->put("ordering",ordering(x.compareTo(y)))}}
            }
            "enum" -> {
                val reason=v.bool("reason")
                fun at(n: Long): Enum<*> = if(reason) X509ReasonFlag.fromOrdinal(n) else X509KeyUsageFlag.fromOrdinal(n)
                val x=at(v.getValue("from").jsonPrimitive.long);val y=at(v.getValue("next").jsonPrimitive.long)
                if(v.text("method")=="value") buildJsonObject {put("shown",x.toString());put("ordinal",x.ordinal);put("equal",x==y);put("ordering",ordering(x.ordinal.compareTo(y.ordinal)))} else {
                    val mode=v.text("mode");val end=at(v.getValue("end").jsonPrimitive.long)
                    val next=if(mode=="then" || mode=="thenTo") y else null
                    val last=if(mode=="to" || mode=="thenTo") end else null
                    val seq=if(reason) x509ReasonRange(x as X509ReasonFlag,next as X509ReasonFlag?,last as X509ReasonFlag?) else x509KeyUsageRange(x as X509KeyUsageFlag,next as X509KeyUsageFlag?,last as X509KeyUsageFlag?)
                    JsonArray(seq.take(v.int("count").coerceAtLeast(0)).map {JsonPrimitive(it.ordinal)}.toList())
                }
            }
            "encode" -> {
                val x=value();val prefix=codec.encodeStream(x).take(v.int("count").coerceAtLeast(0))
                when(v.text("part")) {"spine"->JsonPrimitive(prefix.count());"kinds"->JsonArray(prefix.map {JsonPrimitive(kind(it))}.toList());"shown"->JsonPrimitive(prefix.joinToString(",","[","]"));"bytes"->byteJSON(codec.encodeBytes(x));else->valuesJSON(prefix.toList())}
            }
            "decode" -> either(codec.decodeSequence(sequence {for(t in v.getValue("values").jsonArray) yield(readValue(t));if(v.bool("tailError")) error("tail demanded")}))
            "decodeBytes" -> either(codec.decodeBytesLazy {data(v.getValue("bytes"))})
            "roundtrip" -> either(codec.decodeSequence(codec.encodeStream(value())))
            "roundtripBytes" -> either(codec.decodeBytes(codec.encodeBytes(value())))
            "encodeRaw" -> {val r=codec.encodeRaw(v.bool("critical"),value());when(v.text("part")) {"oid"->oidJSON(r.oid);"critical"->JsonPrimitive(r.critical);else->rawJSON(r)}}
            "decodeRaw" -> maybeEither(codec.decodeRaw(raw(v.getValue("raw"))))
            "getE" -> maybeEither(codec.getE(exts(v.getValue("extensions"))))
            "get" -> when(val r=codec.get(exts(v.getValue("extensions")))) {Asn1Maybe.None->JsonNull;is Asn1Maybe.Some->if(v.text("part")=="tag") JsonPrimitive(true) else shown(r.value)}
            "property" -> {val x=value();val r=codec.decodeSequence(codec.encodeStream(x));buildJsonObject {put("property",r is Asn1ParseResult.Success && r.value==x);put("encoded",byteJSON(codec.encodeBytes(x)));put("values",valuesJSON(codec.encode(x)))}}
            else -> error(api)
        }
    } catch(e: RuntimeException) {buildJsonObject {put("fatal",e.message?.lineSequence()?.first())}}
    @Test
    fun ownedInputsAndCachedDeferredPayloads() {
        val input=byteArrayOf(1,2);val subject=X509SubjectKeyId(input);input[0]=9;subject.bytes[0]=8
        assertArrayEquals(byteArrayOf(1,2),subject.bytes)
        val flags=mutableListOf(X509KeyUsageFlag.KeyUsage_digitalSignature);val usage=X509KeyUsage(flags);flags.clear()
        assertEquals(listOf(X509KeyUsageFlag.KeyUsage_digitalSignature),usage.flags)
        var calls=0;val raw=Asn1Value.Other.deferred(Asn1Class.Context,7) {calls++;byteArrayOf(127,0,0,1)}
        val result=X509SubjectAltNameCodec.decode(listOf(Asn1Value.Start(x509Seq),raw,Asn1Value.End(x509Seq))) as Asn1ParseResult.Success
        assertEquals(0,calls)
        assertEquals(listOf("start","other"),X509SubjectAltNameCodec.encodeStream(result.value).take(2).map(::kind).toList())
        assertEquals(0,calls)
        assertEquals("ExtSubjectAltName [AltNameIP \"\\DEL\\NUL\\NUL\\SOH\"]",result.value.toString())
        assertEquals(1,calls)
        val original=byteArrayOf(65);var stringCalls=0
        val text=Asn1CharacterString.deferred(Asn1StringEncoding.UTF8) {stringCalls++;original}
        assertEquals(0,stringCalls);assertArrayEquals(byteArrayOf(65),text.rawData);original[0]=66;text.rawData[0]=67
        assertArrayEquals(byteArrayOf(65),text.rawData);assertEquals(1,stringCalls)
        var bitsCalls=0;val bit=Asn1Value.BitString.deferred {bitsCalls++;Asn1BitArray(1u,byteArrayOf(128.toByte()))}
        assertEquals(0,bitsCalls);assertEquals(bit.value,bit.component1());assertEquals(bit,bit.copy());assertEquals(1,bitsCalls)
    }
    @TestFactory
    fun originalObservations(): List<DynamicTest> {
        val lines=GZIPInputStream(javaClass.getResourceAsStream("/x509-typed/data.jsonl.gz")!!).bufferedReader().use {it.readLines()}
        assertEquals(Json.parseToJsonElement(lines.first()).jsonObject.getValue("cases").jsonPrimitive.int,lines.size-1)
        return lines.drop(1).mapIndexed {i,line -> val c=Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest("$i ${c.text("group")}") {assertEquals(c.getValue("expected"),observe(c.getValue("request").jsonObject),c.getValue("request").toString())}
        }
    }
}
