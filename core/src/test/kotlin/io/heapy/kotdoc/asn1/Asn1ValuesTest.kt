package io.heapy.kotdoc.asn1

import io.heapy.kotdoc.time.*
import io.heapy.kotdoc.layout.CharacterString
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.util.zip.GZIPInputStream

class Asn1ValuesTest {
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
    private fun readEvent(v: JsonElement): Asn1Event = if (v is JsonPrimitive) { if (v.content == "begin") Asn1Event.ConstructionBegin else Asn1Event.ConstructionEnd } else Asn1Event.Primitive(bytes(v))
    private fun reprJSON(v: Asn1Representation) = array(valueJSON(v.value), JsonArray(v.events.map { JsonPrimitive(it.toString()) }))
    private fun <A> resultJSON(v: Asn1ParseResult<A>, f: (A) -> JsonElement): JsonElement = when (v) {
        is Asn1ParseResult.Failure -> buildJsonObject { put("left", buildString { v.message.points().forEach { appendCodePoint(if (it in 0xd800..0xdfff) 0xfffd else it) } }) }
        is Asn1ParseResult.Success -> buildJsonObject { put("right", f(v.value)) }
    }
    private fun <A> resultPoints(v: Asn1ParseResult<A>, f: (A) -> JsonElement): JsonElement = when (v) {
        is Asn1ParseResult.Failure -> buildJsonObject { put("left", JsonArray(v.message.points().map(::JsonPrimitive))) }
        is Asn1ParseResult.Success -> buildJsonObject { put("right", f(v.value)) }
    }
    private fun capture(f: () -> JsonElement): JsonElement = try { f() } catch (e: RuntimeException) { buildJsonObject { put("fatal", e.message?.lineSequence()?.first()) } }
    private fun order(value: Int) = if (value < 0) "LT" else if (value > 0) "GT" else "EQ"
    private fun accepts(select: String, value: Asn1Value) = when (select) {
        "any" -> true
        "integer" -> value is Asn1Value.IntVal
        "boolean" -> value is Asn1Value.BooleanValue
        "start" -> value is Asn1Value.Start
        "null" -> value == Asn1Value.Null
        "error" -> error("selector demanded")
        else -> false
    }
    private fun <A> maybeJSON(v: Asn1Maybe<A>, f: (A) -> JsonElement): JsonElement = when (v) { Asn1Maybe.None -> JsonNull; is Asn1Maybe.Some -> f(v.value) }
    private fun mapper(v: JsonObject, argument: Lazy<JsonElement>): JsonElement = when (v.text("mapper")) {
        "wrap" -> buildJsonObject { put("mapped", argument.value) }
        "constant" -> v.getValue("value")
        "error" -> error(v.text("message"))
        else -> error(v)
    }
    private val witness = object : Asn1Object<BigInteger> {
        override fun toAsn1(value: BigInteger): Asn1StreamBuilder = { tail -> listOf(Asn1Value.IntVal(value)) + tail }
        override fun fromAsn1(values: List<Asn1Value>): Asn1ParseResult<Asn1Parsed<BigInteger>> =
            (values.firstOrNull() as? Asn1Value.IntVal)?.let { Asn1ParseResult.Success(Asn1Parsed(it.value, values.subList(1, values.size))) } ?: Asn1ParseResult.Failure("witness: expected integer")
    }
    private fun functions(v: JsonObject): Asn1Parser<(Lazy<JsonElement>) -> JsonElement> = when (v.text("op")) {
        "pure" -> Asn1Parser.pure { argument -> mapper(v, argument) }
        "bottom" -> Asn1Parser.pureLazy { error(v.text("message")) }
        "fail" -> Asn1Parser.fail(v.text("message"))
        "consume" -> Asn1Parser.getNext.then(Asn1Parser.pure { argument -> mapper(v, argument) })
        else -> error(v)
    }
    private fun program(v: JsonObject): Asn1Parser<JsonElement> = when (v.text("op")) {
        "next" -> Asn1Parser.getNext.map(::valueJSON)
        "has" -> Asn1Parser.hasNext.map(::JsonPrimitive)
        "optional" -> Asn1Parser.getNextMaybe { if (v.text("select") == "bottomSome") Asn1Maybe.Some.deferred<JsonElement> { error("selected demanded") } else if (accepts(v.text("select"), it)) Asn1Maybe.Some(valueJSON(it)) else Asn1Maybe.None }.map { maybeJSON(it) { x -> x } }
        "container" -> Asn1Parser.getNextContainer(readConstruction(v.getValue("kind"))).map(::valuesJSON)
        "containerMaybe" -> Asn1Parser.getNextContainerMaybe(readConstruction(v.getValue("kind"))).map { maybeJSON(it, ::valuesJSON) }
        "on" -> Asn1Parser.onNextContainer(readConstruction(v.getValue("kind")), program(v.getValue("program").jsonObject))
        "onMaybe" -> Asn1Parser.onNextContainerMaybe(readConstruction(v.getValue("kind")), program(v.getValue("program").jsonObject)).map { maybeJSON(it) { x -> x } }
        "pure" -> Asn1Parser.pure(v.getValue("value"))
        "bottom" -> Asn1Parser.pureLazy { error(v.text("message")) }
        "fail", "monadFail" -> Asn1Parser.fail(v.text("message"))
        "failPoints" -> Asn1Parser.fail(CharacterString(v.getValue("points").jsonArray.map { it.jsonPrimitive.int }.toIntArray()))
        "empty" -> Asn1Parser.empty()
        "choice" -> program(v.getValue("first").jsonObject).orElse(program(v.getValue("second").jsonObject))
        "then" -> program(v.getValue("first").jsonObject).then(program(v.getValue("second").jsonObject))
        "map" -> program(v.getValue("program").jsonObject).mapLazy { mapper(v, it) }
        "apply" -> Asn1Parser.apply(functions(v.getValue("function").jsonObject), program(v.getValue("program").jsonObject))
        "bind" -> program(v.getValue("program").jsonObject).flatMap { x -> program(v.getValue(if (x == v.getValue("equals")) "yes" else "no").jsonObject) }
        "sequence" -> v.getValue("programs").jsonArray.fold(Asn1Parser.pure(emptyList<Lazy<JsonElement>>())) { parser, p -> parser.flatMapLazy { previous -> program(p.jsonObject).mapLazy { next -> previous.value + next } } }.map { JsonArray(it.map { x -> x.value }) }
        "many" -> Asn1Parser.getMany(program(v.getValue("program").jsonObject)).map(::JsonArray)
        "object" -> Asn1Parser.getObject(witness).map { JsonPrimitive(it.toString()) }
        else -> error(v)
    }
    private fun observe(v: JsonObject): JsonElement = capture {
        fun values() = v.getValue("values").jsonArray.map(::readValue)
        when (v.text("api")) {
            "values" -> {
                val values = values()
                buildJsonObject { put("values", valuesJSON(values)); put("shown", JsonArray(values.map { JsonPrimitive(it.toString()) })); put("equal", JsonArray(values.map { a -> JsonArray(values.map { b -> JsonPrimitive(a == b) }) })) }
            }
            "timeModels" -> {
                val a = readDateTime(v.getValue("first")); val b = readDateTime(v.getValue("second")); val ta = HourglassTimezoneOffset(v.getValue("tz1").jsonPrimitive.long); val tb = HourglassTimezoneOffset(v.getValue("tz2").jsonPrimitive.long)
                fun shown(time: HourglassDateTime) = buildJsonObject { put("date", time.date.toString()); put("time", time.time.toString()); put("dateTime", time.toString()) }
                buildJsonObject {
                    put("first", shown(a)); put("second", shown(b)); put("date", order(a.date.compareTo(b.date))); put("time", order(a.time.compareTo(b.time))); put("dateTime", order(a.compareTo(b))); put("equal", a == b); put("timezone", order(ta.compareTo(tb)))
                    put("timezoneShown", array(capture { JsonPrimitive(ta.toString()) }, capture { JsonPrimitive(tb.toString()) }))
                    put("timezoneSeconds", buildJsonObject { put("first", ta.toSeconds().toString()); put("second", tb.toSeconds().toString()) }); put("utc", HourglassTimezoneOffset.UTC.toString())
                }
            }
            "stream" -> {
                val values = values(); val events = v.getValue("events").jsonArray.map { it.jsonArray.map(::readEvent) }; val repr = values.zip(events) { value, e -> Asn1Representation(value, e) }
                val (inside, rest) = Asn1Stream.getConstructedEnd(v.getValue("depth").jsonPrimitive.long, values); val (ri, rr) = Asn1Stream.getConstructedEndRepr(repr)
                buildJsonObject { put("inside", valuesJSON(inside)); put("rest", valuesJSON(rest)); put("reprInside", JsonArray(ri.map(::reprJSON))); put("reprRest", JsonArray(rr.map(::reprJSON))); put("shownRepr", JsonArray(repr.map { JsonPrimitive(it.toString()) })) }
            }
            "parse" -> {
                val values = values(); val parser = program(v.getValue("program").jsonObject)
                buildJsonObject {
                    put("state", capture { resultJSON(parser.runState(values)) { array(it.value, valuesJSON(it.remaining)) } })
                    put("whole", capture { resultJSON(parser.run(values)) { it } })
                }
            }
            "parsePoints" -> {
                val values = values(); val parser = program(v.getValue("program").jsonObject)
                buildJsonObject {
                    put("state", capture { resultPoints(parser.runState(values)) { array(it.value, valuesJSON(it.remaining)) } })
                    put("whole", capture { resultPoints(parser.run(values)) { it } })
                }
            }
            "object" -> {
                val values = values(); val n = v.text("integer").toBigInteger()
                buildJsonObject { put("written", valuesJSON(witness.toAsn1(n)(values))); put("parsed", resultJSON(witness.fromAsn1(values)) { array(JsonPrimitive(it.value.toString()), valuesJSON(it.remaining)) }) }
            }
            else -> error(v)
        }
    }
    @TestFactory
    fun originalObservations(): List<DynamicTest> {
        val lines = GZIPInputStream(javaClass.getResourceAsStream("/asn1-values/data.jsonl.gz")!!).bufferedReader().use { it.readLines() }
        assertEquals(Json.parseToJsonElement(lines.first()).jsonObject.getValue("cases").jsonPrimitive.int, lines.size - 1)
        return lines.drop(1).mapIndexed { index, line ->
            val c = Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest("$index ${c.text("group")}") { assertEquals(c.getValue("expected"), observe(c.getValue("request").jsonObject)) }
        }
    }
    @Test
    fun ownershipFloatingEqualityAndOptionalNull() {
        val bytes = byteArrayOf(0, -1); val octets = Asn1Value.OctetString(bytes); val other = Asn1Value.Other(Asn1Class.Private, -1, bytes)
        bytes[0] = 42; octets.bytes[0] = 42; other.bytes[0] = 42
        assertArrayEquals(byteArrayOf(0, -1), octets.bytes); assertArrayEquals(byteArrayOf(0, -1), other.bytes)
        val arcs = mutableListOf(BigInteger.ONE); val oid = Asn1Value.Oid(arcs); arcs.clear(); assertEquals(listOf(BigInteger.ONE), oid.arcs)
        assertEquals(Asn1Value.Real(0.0), Asn1Value.Real(-0.0)); assertEquals(Asn1Value.Real(0.0).hashCode(), Asn1Value.Real(-0.0).hashCode())
        val nan = Asn1Value.Real(Double.NaN); assertFalse(nan == nan)
        val parsed = Asn1Parser.getNextMaybe { Asn1Maybe.Some<String?>(null) }.run(listOf(Asn1Value.Null))
        assertTrue(parsed is Asn1ParseResult.Success)
        val optional = (parsed as Asn1ParseResult.Success).value
        assertTrue(optional is Asn1Maybe.Some && optional.value == null)
        val remainder = mutableListOf<Asn1Value>(Asn1Value.Null, Asn1Value.Null)
        val state = Asn1Parser.getNext.runState(remainder) as Asn1ParseResult.Success
        remainder.clear(); assertEquals(listOf(Asn1Value.Null), state.value.remaining)
        val events = mutableListOf<Asn1Event>(Asn1Event.ConstructionBegin)
        val repr = Asn1Representation(Asn1Value.Null, events); events.clear(); assertEquals(listOf(Asn1Event.ConstructionBegin), repr.events)
        val reprNan = Asn1Representation(nan, emptyList()); assertFalse(reprNan == reprNan)
    }
}
