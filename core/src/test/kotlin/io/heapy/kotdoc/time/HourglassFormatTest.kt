package io.heapy.kotdoc.time

import com.sun.jna.Pointer
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.parsing.ParserCharacters
import org.junit.jupiter.api.Test
import java.util.zip.GZIPInputStream

class HourglassFormatTest {
    @Test
    fun formatOwnershipAndIndependentDateTimeFields() {
        val elements = mutableListOf<HourglassFormatElement>(HourglassFormatElement.Text(65))
        val format = HourglassTimeFormatString(elements); elements.clear()
        assertEquals(listOf(HourglassFormatElement.Text(65)),format.elements)
        assertThrows(UnsupportedOperationException::class.java) { (format.elements as MutableList).clear() }
        var dates = 0; var times = 0
        val dt = HourglassDateTime.deferred({ dates++; HourglassDate(2024,HourglassMonth.February,29) },{ times++; error("time demanded") })
        assertEquals(HourglassDate(2024,HourglassMonth.February,29),dt.date)
        assertEquals(1,dates); assertEquals(0,times)
        assertNotEquals(dt,HourglassDateTime(HourglassDate(2025,HourglassMonth.February,28),HourglassTimeCodecs.ZERO_TIME))
        assertEquals(0,times)
        assertEquals("time demanded",assertThrows(IllegalStateException::class.java) { dt.time }.message)
    }
    private fun JsonObject.text(k: String) = getValue(k).jsonPrimitive.content
    private fun JsonObject.long(k: String) = getValue(k).jsonPrimitive.long
    private fun JsonObject.bool(k: String) = getValue(k).jsonPrimitive.boolean
    private fun JsonObject.longs(k: String) = getValue(k).jsonArray.map { it.jsonPrimitive.long }
    private fun j(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is List<*> -> JsonArray(value.map(::j))
        is Map<*, *> -> JsonObject(value.mapKeys { it.key.toString() }.mapValues { j(it.value) })
        else -> error("unhandled JSON value: $value")
    }
    private fun obj(vararg pairs: Pair<String, Any?>) = j(mapOf(*pairs))
    private fun ep(a: List<Long>) = HourglassElapsedP(HourglassElapsed(HourglassSeconds(a[0])), HourglassNanoSeconds(a[1]))
    private fun dateTime(a: List<Long>) = HourglassDateTime(HourglassDate(a[0], HourglassMonth.entries[a[1].toInt()], a[2]), HourglassTimeOfDay(HourglassHours(a[3]), HourglassMinutes(a[4]), HourglassSeconds(a[5]), HourglassNanoSeconds(a[6])))
    private fun dateJSON(a: HourglassDate) = listOf(a.year, a.month.ordinal.toLong(), a.day)
    private fun todJSON(a: HourglassTimeOfDay) = listOf(a.hour.value, a.minute.value, a.second.value, a.nanoSecond.value)
    private fun dtJSON(a: HourglassDateTime) = dateJSON(a.date) + todJSON(a.time)
    private fun epoch(n: Int): HourglassEpoch = when (n) { 0 -> HourglassUnixEpoch; 1 -> HourglassWindowsEpoch; else -> HourglassMJDEpoch }
    private fun capture(f: () -> JsonElement) = try { f() } catch (e: RuntimeException) { obj("fatal" to e.message?.lineSequence()?.first()) }

    private fun points(v: JsonElement) = CharacterString(v.jsonArray.map { it.jsonPrimitive.int }.toIntArray())
    private fun pointsJSON(v: CharacterString) = j(v.points().toList())
    private fun shown(v: Any) = pointsJSON(when (v) {
        is HourglassTimeFormatString -> v.showCharacters()
        is HourglassFormatElement -> v.showCharacters()
        else -> Chars.fromString(v.toString())
    })
    private fun function(v: JsonObject): HourglassFormatFunction {
        val kind = v.text("kind")
        val name = if (kind == "original") Chars.fromString("customTimeFormatFct") else points(v.getValue("name"))
        return HourglassFormatFunction(name,{ state,input ->
            when (kind) {
                "original" -> {
                    val ps = input.points(); val chars = ps.take(3).toIntArray()
                    val message = when {
                        chars.size < 3 -> "not enough chars: expecting 3 got ${HaskellString.quoteCodePoints(chars)}"
                        !chars.all(ParserCharacters::isDigit) -> "non-digit char(s) in ${HaskellString.quoteCodePoints(chars)}"
                        else -> null
                    }
                    if (message != null) HourglassCustomParseResult.Failure(Chars.fromString(message))
                    else {
                        val d = chars.fold(0L) { a,c -> a*10+c-48 }
                        if (d !in 1..366) HourglassCustomParseResult.Failure(Chars.fromString("day of year invalid, got: $d"))
                        else HourglassCustomParseResult.Success(state.mapDate { HourglassCalendar.dateAddPeriod(HourglassDate(it.year,HourglassMonth.January,1),HourglassPeriod(0,0,d-1)) },CharacterString(ps.drop(3).toIntArray()))
                    }
                }
                "reject" -> HourglassCustomParseResult.Failure(points(v.getValue("message")))
                "error" -> error(v.text("error"))
                "replace" -> HourglassCustomParseResult.Success(HourglassFormatState.deferred({ dateTime(v.longs("dt")) },HourglassTimezoneOffset(v.long("timezone"))),points(v.getValue("suffix")))
                "partialDate", "partialTime" -> HourglassCustomParseResult.Success(HourglassFormatState.deferred({
                    val dt = dateTime(v.longs("dt"))
                    HourglassDateTime.deferred({ if (kind == "partialDate") error("date demanded") else dt.date },{ if (kind == "partialTime") error("time demanded") else dt.time })
                },HourglassTimezoneOffset(v.long("timezone"))),points(v.getValue("suffix")))
                else -> HourglassCustomParseResult.Success(state,if (v.bool("replaceSuffix")) points(v.getValue("suffix")) else CharacterString(input.points().drop(v.long("drop").coerceAtLeast(0).toInt()).toIntArray()))
            }
        },{ dt,_ ->
            when (kind) {
                "original" -> {
                    val d = HourglassCalendar.getDayOfTheYear(dt.value.date)
                    Chars.fromString(if (d >= 100) "$d" else if (d >= 10) "0$d" else "00$d")
                }
                "error" -> error(v.text("error"))
                else -> points(v.getValue("printed"))
            }
        })
    }
    private fun element(v: JsonElement): HourglassFormatElement = if (v is JsonPrimitive) HourglassFormatElement.Standard(HourglassFormatKind.entries.single { it.haskellName == v.content })
        else v.jsonObject.let { o -> when {
            "precision" in o -> HourglassFormatElement.Precision(o.long("precision"))
            "text" in o -> HourglassFormatElement.Text(o.long("text").toInt())
            else -> HourglassFormatElement.Function(function(o.getValue("function").jsonObject))
        } }
    private fun format(v: JsonElement): HourglassTimeFormat = v.jsonObject.let { o -> when {
        "string" in o -> HourglassTimeFormatString.fromCharacters(points(o.getValue("string")))
        "builtin" in o -> if (o.long("builtin") == 0L) HourglassISO8601Date else HourglassISO8601DateAndTime
        else -> HourglassTimeFormatString(o.getValue("elements").jsonArray.map(::element))
    } }
    private fun failure(r: HourglassFormatParseResult.Failure) = obj("left" to listOf(shown(r.element),pointsJSON(r.message)))
    private fun local(part: String, r: HourglassFormatParseResult<HourglassLocalTime<HourglassDateTime>>): JsonElement = when (r) {
        is HourglassFormatParseResult.Failure -> failure(r)
        is HourglassFormatParseResult.Success -> when (part) {
            "tag" -> j(true)
            "remaining" -> pointsJSON(r.remaining)
            "timezone" -> j(r.value.timezone.minutes)
            "date" -> j(dateJSON(r.value.value.date))
            "time" -> j(todJSON(r.value.value.time))
            else -> obj("right" to listOf(j(dtJSON(r.value.value)),j(r.value.timezone.minutes),pointsJSON(r.remaining)))
        }
    }
    private fun global(part: String, r: HourglassFormatParseResult<HourglassDateTime>): JsonElement = when (r) {
        is HourglassFormatParseResult.Failure -> failure(r)
        is HourglassFormatParseResult.Success -> when (part) {
            "tag" -> j(true)
            "remaining" -> pointsJSON(r.remaining)
            "date" -> j(dateJSON(r.value.date))
            "time" -> j(todJSON(r.value.time))
            else -> obj("right" to listOf(j(dtJSON(r.value)),pointsJSON(r.remaining)))
        }
    }
    private fun observe(v: JsonObject,backend: HourglassNativeBackend): JsonElement {
        val f = format(v.getValue("format")); val engine = HourglassFormat(backend); val c = HourglassTimeCodecs { backend }
        return when (v.text("api")) {
            "meta" -> obj("shown" to shown(f.toFormat()),"elements" to f.toFormat().elements.map(::shown),"equal" to (f.toFormat() == format(v.getValue("other")).toFormat()),"isoShown" to listOf(HourglassISO8601Date.toString(),HourglassISO8601DateAndTime.toString()))
            "parse" -> {
                val input = points(v.getValue("input")); val part = v.text("part")
                when (v.text("action")) {
                    "localE" -> local(part,engine.localTimeParseE(f,input))
                    "globalE" -> global(part,engine.timeParseE(f,input))
                    "local" -> engine.localTimeParse(f,input)?.let { local(part,HourglassFormatParseResult.Success({ it.value },Chars.empty)) } ?: JsonNull
                    "global" -> engine.timeParse(f,input)?.let { global(part,HourglassFormatParseResult.Success({ it.value },Chars.empty)) } ?: JsonNull
                    else -> error(v)
                }
            }
            "roundtrip" -> {
                val dt = dateTime(v.longs("dt")); val printed = engine.timePrint(f,dt,c.dateTime); val parsed = engine.timeParseE(f,printed)
                obj("printed" to pointsJSON(printed),"parsed" to global("full",parsed),"equal" to (parsed is HourglassFormatParseResult.Success && parsed.value == dt))
            }
            "propertyPrint" -> {
                val value = HourglassElapsed(HourglassSeconds(v.long("scalar")))
                j(listOf(pointsJSON(engine.timePrint(HourglassISO8601Date,value,c.elapsed)),pointsJSON(engine.timePrint(HourglassTimeFormatString.fromString("EPOCH"),value,c.elapsed))))
            }
            "print" -> when (val kind = v.long("kind").toInt()) {
                0 -> print(v,f,HourglassCTime(v.long("scalar")),c.cTime,engine)
                1 -> print(v,f,HourglassElapsed(HourglassSeconds(v.long("scalar"))),c.elapsed,engine)
                2 -> print(v,f,ep(v.longs("ep")),c.elapsedP,engine)
                3 -> print(v,f,dateTime(v.longs("dt")).date,c.date,engine)
                4 -> print(v,f,dateTime(v.longs("dt")),c.dateTime,engine)
                in 5..7 -> { val e = epoch(kind-5); print(v,f,HourglassElapsedSince(e,HourglassSeconds(v.long("scalar"))),c.since(e),engine) }
                else -> { val e = epoch(kind-8); val x = ep(v.longs("ep")); print(v,f,HourglassElapsedSinceP(HourglassElapsedSince(e,x.elapsed.seconds),x.nanoSeconds),c.sinceP(e),engine) }
            }
            else -> error(v)
        }
    }
    private fun <A> print(v: JsonObject, format: HourglassTimeFormat, value: A, time: HourglassTimeable<A>, engine: HourglassFormat) = pointsJSON(
        if (v.bool("local")) engine.localTimePrint(format,HourglassLocalTime(value,HourglassTimezoneOffset(v.long("timezone"))),time) else engine.timePrint(format,value,time))
    private fun observation(v: JsonObject, model: String): JsonElement {
        val trace = mutableListOf<Any>()
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
                    trace.add(listOf("gm",seconds.getLong(0)))
                    if (!config!!.bool("gmOk")) return false
                    config.longs("tm").forEachIndexed { i,x -> output.setInt(i*4L,x.toInt()) }; return true
                }
                override fun localTimeR(seconds: Pointer, output: Pointer): Boolean {
                    trace.add(listOf("local",seconds.getLong(0)))
                    if (!config!!.bool("localOk")) return false
                    output.setLong(40,config.long("zoneSeconds")); return true
                }
                override fun clockCalendar(output: Pointer) {
                    trace.add(listOf("clock")); output.setLong(0,config!!.long("clockSeconds")); output.setLong(8,config.long("clockNanos"))
                }
            })
            else -> HourglassWindowsTime(object: HourglassWindowsTimeApi {
                override fun fileTimeToSystemTime(fileTime: Pointer, systemTime: Pointer): Boolean {
                    trace.add(listOf("file",fileTime.getLong(0).toULong().toString()))
                    if (!config!!.bool("fileOk")) return false
                    config.longs("systemTime").forEachIndexed { i,x -> systemTime.setShort(i*2L,x.toShort()) }; return true
                }
                override fun getSystemTimeAsFileTime(output: Pointer) { trace.add(listOf("fileNow")); output.setLong(0,config!!.text("fileTime").toULong().toLong()) }
                override fun getTimeZoneInformation(output: Pointer): UInt {
                    trace.add(listOf("timezone")); if (!config!!.bool("zoneOk")) return UInt.MAX_VALUE
                    output.setInt(0,config.long("bias").toInt()); output.setInt(84,config.long("standardBias").toInt()); output.setInt(168,config.long("daylightBias").toInt()); return config.long("mode").toUInt()
                }
                override fun getLastError() = 87u
                override fun failWith(location: String, code: UInt): Nothing = error("native failure")
            })
        }
        val result = capture { observe(v,backend) }
        return if (model in listOf("portable","native")) result else obj("output" to result, "trace" to trace)
    }
    @TestFactory
    fun originalObservations(): List<DynamicTest> {
        val lines = GZIPInputStream(javaClass.getResourceAsStream("/hourglass-format/data.jsonl.gz")!!).bufferedReader().use { it.readLines() }
        assertEquals(Json.parseToJsonElement(lines.first()).jsonObject.getValue("cases").jsonPrimitive.int,lines.size-1)
        return lines.drop(1).mapIndexed { i,line ->
            val c = Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest("$i ${c.text("group")}") {
                val result = observation(c.getValue("request").jsonObject,c.text("model"))
                assertEquals(c.getValue("expected"),result)
            }
        }
    }
}
