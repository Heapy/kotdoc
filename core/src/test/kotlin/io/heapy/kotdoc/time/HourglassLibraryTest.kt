package io.heapy.kotdoc.time

import com.sun.jna.Pointer
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.util.zip.GZIPInputStream

class HourglassLibraryTest {
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
    private fun epJSON(a: HourglassElapsedP) = listOf(a.elapsed.seconds.value, a.nanoSeconds.value)
    private fun duration(a: List<Long>) = HourglassDuration(HourglassHours(a[0]), HourglassMinutes(a[1]), HourglassSeconds(a[2]), HourglassNanoSeconds(a[3]))
    private fun durationJSON(a: HourglassDuration) = listOf(a.hours.value, a.minutes.value, a.seconds.value, a.nanoSeconds.value)
    private fun period(a: List<Long>) = HourglassPeriod(a[0], a[1], a[2])
    private fun periodJSON(a: HourglassPeriod) = listOf(a.years, a.months, a.days)
    private fun dateTime(a: List<Long>) = HourglassDateTime(HourglassDate(a[0], HourglassMonth.entries[a[1].toInt()], a[2]), HourglassTimeOfDay(HourglassHours(a[3]), HourglassMinutes(a[4]), HourglassSeconds(a[5]), HourglassNanoSeconds(a[6])))
    private fun dateJSON(a: HourglassDate) = listOf(a.year, a.month.ordinal.toLong(), a.day)
    private fun todJSON(a: HourglassTimeOfDay) = listOf(a.hour.value, a.minute.value, a.second.value, a.nanoSecond.value)
    private fun dtJSON(a: HourglassDateTime) = dateJSON(a.date) + todJSON(a.time)
    private fun ratioJSON(a: HourglassRational) = listOf(a.numerator.toString(), a.denominator.toString())
    private fun order(a: Int) = if (a < 0) "LT" else if (a > 0) "GT" else "EQ"
    private fun epoch(n: Int): HourglassEpoch = when (n) { 0 -> HourglassUnixEpoch; 1 -> HourglassWindowsEpoch; else -> HourglassMJDEpoch }
    private fun capture(f: () -> JsonElement) = try { f() } catch (e: RuntimeException) { obj("fatal" to e.message?.lineSequence()?.first()) }
    private fun observe(v: JsonObject, backend: HourglassNativeBackend): JsonElement = when (v.text("api")) {
        "elapsed" -> {
            val a = ep(v.longs("a")); val b = ep(v.longs("b")); val r = HourglassRational(v.text("numerator").toBigInteger(), v.text("denominator").toBigInteger())
            val (s, ns) = HourglassElapsedP.fromRationalSeconds(r)
            obj("a" to epJSON(a), "b" to epJSON(b), "shown" to listOf(a.toString(), b.toString()), "equal" to (a == b), "ordering" to order(a.compareTo(b)),
                "normalized" to epJSON(HourglassElapsedP.normalized(a.elapsed.seconds, a.nanoSeconds)), "plus" to epJSON(a+b), "minus" to epJSON(a-b), "times" to epJSON(a*b),
                "negate" to epJSON(-a), "abs" to epJSON(a.absolute()), "signum" to epJSON(a.signum()), "rational" to ratioJSON(a.toRational()),
                "rationalSeconds" to listOf(s.value,ns.value), "rationalElapsed" to epJSON(HourglassElapsedP.fromRational(r)), "integer" to epJSON(HourglassElapsedP.fromInteger(v.text("integer").toBigInteger())))
        }
        "duration" -> {
            val a = duration(v.longs("a")); val b = duration(v.longs("b")); val p = period(v.longs("p")); val q = period(v.longs("q")); val (s,ns) = a.flatten()
            val (from,rest) = HourglassIntervals.duration.fromSeconds(HourglassSeconds(v.long("seconds")))
            obj("shown" to listOf(a.toString(),b.toString()), "ordering" to order(a.compareTo(b)), "equal" to (a == b), "flatten" to listOf(s.value,ns.value),
                "normalized" to durationJSON(a.normalized()), "sum" to durationJSON(a+b), "empty" to durationJSON(HourglassDuration.ZERO),
                "toSeconds" to HourglassIntervals.duration.toSeconds(a).value, "fromSeconds" to listOf(durationJSON(from), rest.value),
                "periodShown" to listOf(p.toString(),q.toString()), "periodOrdering" to order(p.compareTo(q)), "periodEqual" to (p == q), "periodSum" to periodJSON(p+q), "periodEmpty" to periodJSON(HourglassPeriod.ZERO))
        }
        "interval" -> {
            val n = v.long("value"); val s = HourglassSeconds(v.long("seconds"))
            fun <A> from(units: HourglassTimeInterval<A>): List<Any> = units.fromSeconds(s).let { listOf(it.first.toString(),it.second.value) }
            obj("toSeconds" to listOf(HourglassIntervals.nanoSeconds.toSeconds(HourglassNanoSeconds(n)).value,n,HourglassIntervals.minutes.toSeconds(HourglassMinutes(n)).value,HourglassIntervals.hours.toSeconds(HourglassHours(n)).value),
                "fromSeconds" to listOf(from(HourglassIntervals.nanoSeconds),from(HourglassIntervals.seconds),from(HourglassIntervals.minutes),from(HourglassIntervals.hours)))
        }
        "calendar" -> {
            val dt = dateTime(v.longs("dt")); val d = dt.date; val added = HourglassCalendar.dateAddPeriod(d,period(v.longs("period")))
            obj("leap" to HourglassCalendar.isLeapYear(d.year), "days" to HourglassCalendar.daysInMonth(d.year,d.month), "yearDay" to HourglassCalendar.getDayOfTheYear(d),
                "weekDay" to HourglassCalendar.getWeekDay(d).toString(), "dateEpoch" to HourglassCalendar.dateToUnixEpoch(d).toString(), "timeSeconds" to HourglassCalendar.timeOfDayToSeconds(dt.time).toString(),
                "dateTimeEpoch" to HourglassCalendar.dateTimeToUnixEpoch(dt).toString(), "added" to dateJSON(added), "addedShown" to added.toString())
        }
        "pad" -> {
            val n = v.text("value").toBigInteger()
            j(when (v.long("function").toInt()) { 0 -> HourglassPadding.pad2(n); 1 -> HourglassPadding.pad3(n); 2 -> HourglassPadding.pad4(n); else -> HourglassPadding.padN(v.long("width"),n) })
        }
        "timezone" -> {
            val a = HourglassTimezoneMinutes(v.long("value"))
            obj("shown" to a.toString(), "offset" to a.offset, "name" to a.name, "utc" to listOf(HourglassUTC.toString(),HourglassUTC.name), "utcOffset" to HourglassUTC.offset)
        }
        "epoch" -> {
            val e = epoch(v.long("epoch").toInt()); val x = ep(v.longs("a")); val y = ep(v.longs("b"))
            val a = HourglassElapsedSinceP(HourglassElapsedSince(e,x.elapsed.seconds),x.nanoSeconds); val b = HourglassElapsedSinceP(HourglassElapsedSince(e,y.elapsed.seconds),y.nanoSeconds)
            fun proj(x: HourglassElapsedSinceP<HourglassEpoch>) = listOf(x.elapsed.seconds.value,x.nanoSeconds.value)
            obj("name" to e.name, "diff" to e.diffToUnix.toString(), "shownEpoch" to e.toString(), "shown" to listOf(a.toString(),b.toString()),
                "plus" to proj(a+b), "minus" to proj(a-b), "times" to proj(a*b), "negate" to proj(-a), "abs" to proj(a.absolute()), "signum" to proj(a.signum()),
                "ordering" to order(a.compareTo(b)), "equal" to (a == b), "rational" to ratioJSON(a.toRational()), "integer" to proj(HourglassElapsedSinceP.fromInteger(e,v.text("integer").toBigInteger())),
                "unix" to a.convert(HourglassUnixEpoch).toString(), "windows" to a.convert(HourglassWindowsEpoch).toString(), "mjd" to a.convert(HourglassMJDEpoch).toString(), "elapsed" to epJSON(HourglassTimeCodecs { backend }.sinceP(e).getElapsedP(a)))
        }
        "native" -> when (v.text("action")) {
            "epoch" -> j(dtJSON(backend.dateTimeFromUnixEpoch(HourglassElapsed(HourglassSeconds(v.long("seconds"))))))
            "epochP" -> j(dtJSON(backend.dateTimeFromUnixEpochP(ep(v.longs("elapsed")))))
            "timezone" -> j(backend.systemGetTimezone().minutes)
            "now" -> j(backend.systemGetElapsed().toString())
            "nowP" -> j(epJSON(backend.systemGetElapsedP()))
            else -> error(v)
        }
        "system" -> {
            val system = HourglassSystem(backend)
            when (v.text("action")) {
                "current" -> j(system.timeCurrent().toString())
                "currentP" -> j(epJSON(system.timeCurrentP()))
                "date" -> j(dtJSON(system.dateCurrent()))
                "timezone" -> j(system.timezoneCurrent().minutes)
                "local" -> j(system.localDateCurrent().toString())
                "localAt" -> j(system.localDateCurrentAt(HourglassTimezoneOffset(v.long("timezone"))).toString())
                else -> error(v)
            }
        }
        "direct" -> {
            val dt = dateTime(v.longs("dt")); val codecs = HourglassTimeCodecs { backend }
            when (v.text("action")) {
                "dt" -> j(dtJSON(codecs.dateTime.getDateTime(dt)))
                "date" -> j(dateJSON(codecs.dateTime.getDate(dt)))
                "tod" -> j(todJSON(codecs.dateTime.getTimeOfDay(dt)))
                "identity" -> j(dtJSON(HourglassTimeOperations.convert(dt,codecs.dateTime,codecs.dateTime)))
                "dateDt" -> j(dtJSON(codecs.date.getDateTime(dt.date)))
                "dateIdentity" -> j(dateJSON(HourglassTimeOperations.convert(dt.date,codecs.date,codecs.date)))
                else -> error(v)
            }
        }
        "time" -> {
            val c = HourglassTimeCodecs { backend }
            when (val kind = v.long("kind").toInt()) {
                0 -> time(v,HourglassCTime(v.long("scalar")),c.cTime,c,backend)
                1 -> time(v,HourglassElapsed(HourglassSeconds(v.long("scalar"))),c.elapsed,c,backend)
                2 -> time(v,ep(v.longs("ep")),c.elapsedP,c,backend)
                3 -> time(v,dateTime(v.longs("dt")).date,c.date,c,backend)
                4 -> time(v,dateTime(v.longs("dt")),c.dateTime,c,backend)
                in 5..7 -> { val e = epoch(kind-5); time(v,HourglassElapsedSince(e,HourglassSeconds(v.long("scalar"))),c.since(e),c,backend) }
                else -> { val e = epoch(kind-8); val x = ep(v.longs("ep")); time(v,HourglassElapsedSinceP(HourglassElapsedSince(e,x.elapsed.seconds),x.nanoSeconds),c.sinceP(e),c,backend) }
            }
        }
        else -> error(v)
    }
    private fun <A: Comparable<A>> time(v: JsonObject, a: A, t: HourglassTime<A>, c: HourglassTimeCodecs, backend: HourglassNativeBackend): JsonElement {
        val tz = HourglassTimezoneOffset(v.long("timezone")); val target = HourglassTimezoneOffset(v.long("targetTimezone")); val local = HourglassLocalTime(a,tz)
        fun <B> convert(to: HourglassTime<B>) = j(HourglassTimeOperations.convertGeneric(a,t,to).toString())
        return when (v.text("action")) {
            "ep" -> j(epJSON(t.getElapsedP(a)))
            "e" -> j(t.getElapsed(a).toString())
            "ns" -> j(t.getNanoSeconds(a).toString())
            "diff" -> j(HourglassTimeOperations.diff(a,ep(v.longs("second")),t,c.elapsedP).toString())
            "diffP" -> j(HourglassTimeOperations.diffP(a,ep(v.longs("second")),t,c.elapsedP).let { listOf(it.first.value,it.second.value) })
            "dt" -> j(dtJSON(HourglassTimeOperations.genericDateTime(a,t,backend)))
            "date" -> j(dateJSON(HourglassTimeOperations.genericDateTime(a,t,backend).date))
            "tod" -> j(todJSON(HourglassTimeOperations.genericTimeOfDay(a,t)))
            "convert" -> when (val k = v.long("target").toInt()) {
                0 -> convert(c.cTime); 1 -> convert(c.elapsed); 2 -> convert(c.elapsedP); 3 -> convert(c.date); 4 -> convert(c.dateTime)
                in 5..7 -> convert(c.since(epoch(k-5)))
                else -> convert(c.sinceP(epoch(k-8)))
            }
            "add" -> j(HourglassTimeOperations.add(a,duration(v.longs("duration")),t,HourglassIntervals.duration).toString())
            "localGlobal" -> j(local.toGlobal(t).toString())
            "localSet" -> j(local.setTimezone(target,t).toString())
            "localConvert" -> j(local.convert(t,c.elapsedP).toString())
            "localShow" -> j(local.toString())
            "localCompare" -> j(order(HourglassLocalTime.compare(local,HourglassLocalTime(a,target),t)))
            "localEqual" -> j(local == HourglassLocalTime(a,target))
            else -> error(v)
        }
    }
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
        val lines = GZIPInputStream(javaClass.getResourceAsStream("/hourglass/data.jsonl.gz")!!).bufferedReader().use { it.readLines() }
        assertEquals(Json.parseToJsonElement(lines.first()).jsonObject.getValue("cases").jsonPrimitive.int,lines.size-1)
        return lines.drop(1).mapIndexed { i,line ->
            val c = Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest("$i ${c.text("group")}") {
                val result = observation(c.getValue("request").jsonObject,c.text("model"))
                assertEquals(c.getValue("expected"),result)
                c["assertion"]?.jsonObject?.let { assertEquals(it.getValue("expected"),result.jsonObject.getValue(it.text("operation"))) }
                c["rationalAssertion"]?.jsonArray?.let { assertEquals(HourglassRational(it[0].jsonPrimitive.content.toBigInteger(),it[1].jsonPrimitive.content.toBigInteger()),result.jsonObject.getValue("rational").jsonArray.let { r -> HourglassRational(r[0].jsonPrimitive.content.toBigInteger(),r[1].jsonPrimitive.content.toBigInteger()) }) }
            }
        }
    }
}
