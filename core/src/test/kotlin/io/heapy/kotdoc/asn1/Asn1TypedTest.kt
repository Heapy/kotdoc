package io.heapy.kotdoc.asn1

import io.heapy.kotdoc.time.*
import io.heapy.kotdoc.layout.CharacterString
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import com.sun.jna.Pointer
import java.util.zip.GZIPInputStream

class Asn1TypedTest {
    @Test
    fun ownershipLazyFailuresAndIndependentEncodedComponents() {
        val p = Asn1Primitives(); val binary = Asn1BinaryEncoding(p)
        val values = mutableListOf<Asn1Value>(Asn1Value.IntVal(BigInteger.ONE),Asn1Value.Oid(emptyList()))
        val encoded = p.encodeList(values); values.clear()
        val prefix = encoded.events.take(2).toList()
        assertEquals(Asn1Event.Primitive(byteArrayOf(1)),prefix[1])
        assertEquals("invalid OID format []",assertThrows(IllegalStateException::class.java) { encoded.length }.message)
        val damaged = binary.decode(byteArrayOf(2,0),Asn1EncodingRules.DER)
        assertTrue(damaged is Asn1Result.Success)
        val lazyValues = (damaged as Asn1Result.Success).value
        assertEquals(emptyList<Asn1Value>(),lazyValues.take(0).toList())
        assertEquals(Asn1Error.TypeDecodingFailed("integer: null encoding"),assertThrows(Asn1Exception::class.java) { lazyValues.first() }.error)
        assertTrue(binary.decode(byteArrayOf(2,0,-1),Asn1EncodingRules.DER) is Asn1Result.Failure)
        val bytes = byteArrayOf(2,1,7); val decoded = binary.decode(bytes) as Asn1Result.Success; bytes[2] = 99
        assertEquals(listOf(Asn1Value.IntVal(BigInteger.valueOf(7))),decoded.value.toList())
        val owned = p.encodePrimitive(Asn1Value.OctetString(byteArrayOf(7)))
        (owned.events.last() as Asn1Event.Primitive).bytes[0] = 99
        assertEquals(Asn1Event.Primitive(byteArrayOf(7)),owned.events.last())
    }
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
    private fun valuesJSON(values: List<Asn1Value>) = JsonArray(values.map(::valueJSON))
    private fun JsonObject.long(k: String) = getValue(k).jsonPrimitive.long
    private fun JsonObject.bool(k: String) = getValue(k).jsonPrimitive.boolean
    private fun JsonObject.int(k: String) = getValue(k).jsonPrimitive.int

    private fun readLength(v: JsonElement): Asn1Length = v.jsonArray.let { a -> when (a[0].jsonPrimitive.content) {
        "short" -> Asn1Length.Short(a[1].jsonPrimitive.long)
        "long" -> Asn1Length.LongForm(a[1].jsonPrimitive.long,a[2].jsonPrimitive.long)
        else -> Asn1Length.Indefinite
    } }
    private fun lengthJSON(v: Asn1Length): JsonElement = when (v) {
        is Asn1Length.Short -> array(JsonPrimitive("short"),JsonPrimitive(v.value))
        is Asn1Length.LongForm -> array(JsonPrimitive("long"),JsonPrimitive(v.octets),JsonPrimitive(v.value))
        Asn1Length.Indefinite -> array(JsonPrimitive("indefinite"))
    }
    private fun readHeader(v: JsonElement) = v.jsonArray.let { Asn1Header(Asn1Class.entries[it[0].jsonPrimitive.int],it[1].jsonPrimitive.long,it[2].jsonPrimitive.boolean,readLength(it[3])) }
    private fun headerJSON(v: Asn1Header) = array(JsonPrimitive(v.kind.ordinal),JsonPrimitive(v.tag),JsonPrimitive(v.constructed),lengthJSON(v.length))
    private fun eventJSON(v: Asn1Event): JsonElement = when (v) {
        is Asn1Event.Header -> array(JsonPrimitive("header"),headerJSON(v.header))
        is Asn1Event.Primitive -> array(JsonPrimitive("primitive"),byteJSON(v.bytes))
        Asn1Event.ConstructionBegin -> JsonPrimitive("begin")
        Asn1Event.ConstructionEnd -> JsonPrimitive("end")
    }
    private fun readEvent(v: JsonElement): Asn1Event = when (v) {
        is JsonPrimitive -> if (v.content == "begin") Asn1Event.ConstructionBegin else Asn1Event.ConstructionEnd
        else -> v.jsonArray.let { if (it[0].jsonPrimitive.content == "header") Asn1Event.Header(readHeader(it[1])) else Asn1Event.Primitive(bytes(it[1])) }
    }
    private fun capture(f: () -> JsonElement): JsonElement = try { f() } catch (e: RuntimeException) { buildJsonObject { put("fatal",e.message?.lineSequence()?.first()) } }
    private fun <A> result(r: Asn1Result<A>, f: (A) -> JsonElement): JsonElement = when (r) {
        is Asn1Result.Failure -> buildJsonObject { put("left",r.error.show()) }
        is Asn1Result.Success -> buildJsonObject { put("right",f(r.value)) }
    }
    private fun valueResult(r: Asn1Result<Asn1Value>) = result(r) { buildJsonObject { put("value",valueJSON(it)); put("shown",it.toString()) } }
    private fun values(v: JsonObject) = v.getValue("values").jsonArray.map(::readValue)
    private fun <A> streamResult(v: JsonObject, r: Asn1Result<Sequence<A>>, f: (A) -> JsonElement): JsonElement = when (r) {
        is Asn1Result.Failure -> buildJsonObject { put("left",r.error.show()) }
        is Asn1Result.Success -> if (v.text("part") == "tag") JsonPrimitive(true) else result(r) { xs -> JsonArray((if (v.text("part") == "prefix") xs.take(v.int("count")) else xs).map(f).toList()) }
    }
    private fun observe(v: JsonObject, p: Asn1Primitives): JsonElement {
        fun b() = bytes(v.getValue("bytes"))
        val binary = Asn1BinaryEncoding(p)
        return when (v.text("api")) {
            "get" -> valueResult(when (v.text("method")) {
                "boolean" -> p.getBoolean(v.bool("der"),b()); "integer" -> p.getInteger(b()); "double" -> p.getDouble(b()); "bits" -> p.getBitString(b())
                "octets" -> p.getOctetString(b()); "null" -> p.getNull(b()); "oid" -> p.getOID(b()); "time" -> p.getTime(Asn1TimeType.entries[v.int("type")],b())
                else -> error(v)
            })
            "decodePrimitive" -> valueResult(p.decodePrimitive(readHeader(v.getValue("header")),b()))
            "put" -> byteJSON(when (v.text("method")) {
                "integer" -> p.putInteger(v.text("integer").toBigInteger())
                "double" -> p.putDouble(Double.fromBits(v.text("bits").toULong().toLong()))
                "bits" -> p.putBitString(Asn1BitArray(v.text("length").toULong(),b()))
                "string" -> p.putString(b())
                "oid" -> p.putOID(v.getValue("oid").jsonArray.map { it.jsonPrimitive.content.toBigInteger() })
                "time" -> p.putTime(Asn1TimeType.entries[v.int("type")],readDateTime(v.getValue("dt")),v.getValue("timezone").let { if (it == JsonNull) null else HourglassTimezoneOffset(it.jsonPrimitive.long) })
                else -> error(v)
            })
            "float" -> {
                val d = Asn1Real.encodeFloat(v.text("mantissa").toBigInteger(),v.long("exponent"),if (v.bool("native")) Asn1IntegerFloatBackend.Native else Asn1IntegerFloatBackend.GMP)
                buildJsonObject { put("bits",d.toRawBits().toULong().toString()); put("shown",io.heapy.kotdoc.formats.HaskellDouble.show(d)) }
            }
            "header" -> {
                val h = p.encodeHeader(v.bool("constructed"),readLength(v.getValue("length")),readValue(v.getValue("value")))
                buildJsonObject { put("header",headerJSON(h)); put("shown",h.toString()) }
            }
            "smallest" -> lengthJSON(Asn1Primitives.smallestLength(v.long("length")))
            "encode" -> {
                val encoded by lazy { when (v.text("method")) {
                    "primitive" -> p.encodePrimitive(readValue(v.getValue("value")))
                    "one" -> p.encodeOne(readValue(v.getValue("value")))
                    "constructed" -> p.encodeConstructed(readValue(v.getValue("value")),values(v))
                    else -> p.encodeList(values(v))
                } }
                fun events() = sequence { yieldAll(encoded.events) }
                when (v.text("part")) {
                    "length" -> JsonPrimitive(encoded.length)
                    "events" -> JsonArray(events().map(::eventJSON).toList())
                    "prefix" -> JsonArray(events().take(v.int("count")).map(::eventJSON).toList())
                    else -> { val size = encoded.length; val es = JsonArray(events().map(::eventJSON).toList()); buildJsonObject { put("length",size); put("events",es) } }
                }
            }
            "binary" -> {
                val chunks = v.getValue("chunks").jsonArray.map(::bytes).asSequence(); val rules = if (v.bool("der")) Asn1EncodingRules.DER else Asn1EncodingRules.BER
                if (v.bool("repr")) streamResult(v,binary.decodeRepresentationChunks(chunks,rules)) { array(valueJSON(it.value),JsonArray(it.events.map(::eventJSON))) }
                else streamResult(v,binary.decodeChunks(chunks,rules),::valueJSON)
            }
            "write" -> when (v.text("part")) {
                "chunks" -> JsonArray(binary.encodeDerChunks(values(v)).map(::byteJSON).toList())
                "prefix" -> byteJSON(binary.encodeDerChunks(values(v)).flatMap { it.asSequence() }.take(v.int("count")).toList().toByteArray())
                else -> byteJSON(binary.encodeDer(values(v)))
            }
            "property" -> when (v.text("kind")) {
                "header" -> {
                    val h = readHeader(v.getValue("header")); val out = Asn1Raw.putHeader(h); val parsed = Asn1Serialize.header.start(out)
                    buildJsonObject { put("property",parsed is Asn1GetResult.Done && parsed.value == h); put("encoded",byteJSON(out)) }
                }
                "event" -> {
                    val events = v.getValue("events").jsonArray.map(::readEvent); val out = Asn1Raw.write(events); val decoded = Asn1Raw.parse(out)
                    buildJsonObject { put("property",decoded is Asn1Result.Success && decoded.value == events); put("encoded",byteJSON(out)) }
                }
                else -> {
                    val vs = when (v.text("kind")) {
                        "der" -> values(v)
                        "real" -> listOf(Asn1Value.Real(Double.fromBits(v.text("bits").toULong().toLong())))
                        else -> listOf(Asn1Value.Real(v.text("integer").toBigInteger().toDouble()))
                    }
                    val out = binary.encodeDer(vs); val decoded = when (val r = binary.decode(out,Asn1EncodingRules.DER)) { is Asn1Result.Failure -> r; is Asn1Result.Success -> Asn1Result.Success(r.value.toList()) }
                    buildJsonObject { put("property",decoded is Asn1Result.Success && decoded.value == vs); put("encoded",byteJSON(out)); put("decoded",result(decoded,::valuesJSON)) }
                }
            }
            else -> error(v)
        }
    }
    private fun observation(v: JsonObject, model: String): JsonElement {
        val trace = mutableListOf<JsonElement>()
        val config = v["config"]?.jsonObject
        val backend = when (model) {
            "native" -> HourglassNativeTime.system
            "portable" -> object: HourglassNativeBackend {
                override fun dateTimeFromUnixEpoch(value: HourglassElapsed): HourglassDateTime = error("unexpected native call")
                override fun dateTimeFromUnixEpochP(value: HourglassElapsedP): HourglassDateTime = error("unexpected native call")
                override fun systemGetTimezone(): HourglassTimezoneOffset = error("unexpected native call")
                override fun systemGetElapsed(): HourglassElapsed = error("unexpected native call")
                override fun systemGetElapsedP(): HourglassElapsedP = error("unexpected native call")
            }
            "posix" -> HourglassPosixTime(object: HourglassPosixTimeApi {
                override fun gmTimeR(seconds: Pointer, output: Pointer): Boolean {
                    trace.add(array(JsonPrimitive("gm"),JsonPrimitive(seconds.getLong(0))))
                    if (!config!!.bool("gmOk")) return false
                    config.getValue("tm").jsonArray.map { it.jsonPrimitive.long }.forEachIndexed { i,x -> output.setInt(i*4L,x.toInt()) }; return true
                }
                override fun localTimeR(seconds: Pointer, output: Pointer): Boolean {
                    trace.add(array(JsonPrimitive("local"),JsonPrimitive(seconds.getLong(0))))
                    if (!config!!.bool("localOk")) return false
                    output.setLong(40,config.long("zoneSeconds")); return true
                }
                override fun clockCalendar(output: Pointer) {
                    trace.add(array(JsonPrimitive("clock"))); output.setLong(0,config!!.long("clockSeconds")); output.setLong(8,config.long("clockNanos"))
                }
            })
            else -> HourglassWindowsTime(object: HourglassWindowsTimeApi {
                override fun fileTimeToSystemTime(fileTime: Pointer, systemTime: Pointer): Boolean {
                    trace.add(array(JsonPrimitive("file"),JsonPrimitive(fileTime.getLong(0).toULong().toString())))
                    if (!config!!.bool("fileOk")) return false
                    config.getValue("systemTime").jsonArray.map { it.jsonPrimitive.long }.forEachIndexed { i,x -> systemTime.setShort(i*2L,x.toShort()) }; return true
                }
                override fun getSystemTimeAsFileTime(output: Pointer) { trace.add(array(JsonPrimitive("fileNow"))); output.setLong(0,config!!.text("fileTime").toULong().toLong()) }
                override fun getTimeZoneInformation(output: Pointer): UInt {
                    trace.add(array(JsonPrimitive("timezone"))); if (!config!!.bool("zoneOk")) return UInt.MAX_VALUE
                    output.setInt(0,config.long("bias").toInt()); output.setInt(84,config.long("standardBias").toInt()); output.setInt(168,config.long("daylightBias").toInt()); return config.long("mode").toUInt()
                }
                override fun getLastError() = 87u
                override fun failWith(location: String, code: UInt): Nothing = error("native failure")
            })
        }
        val profile = if (v["nanProfile"]?.jsonPrimitive?.content == "negative") Asn1NaNProfile.Negative else Asn1NaNProfile.Positive
        val output = capture { observe(v, Asn1Primitives(HourglassFormat(backend),nanProfile=profile)) }
        return if (model in listOf("portable","native")) output else buildJsonObject { put("output",output); put("trace",JsonArray(trace)) }
    }
    @TestFactory
    fun originalObservations(): List<DynamicTest> {
        val lines = GZIPInputStream(javaClass.getResourceAsStream("/asn1-typed/data.jsonl.gz")!!).bufferedReader().use { it.readLines() }
        assertEquals(Json.parseToJsonElement(lines.first()).jsonObject.getValue("cases").jsonPrimitive.int,lines.size-1)
        return lines.drop(1).mapIndexed { i,line ->
            val c = Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest("$i ${c.text("group")}") { assertEquals(c.getValue("expected"),observation(c.getValue("request").jsonObject,c.text("model"))) }
        }
    }
}
