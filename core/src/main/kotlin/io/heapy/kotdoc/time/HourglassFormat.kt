// Time.Format, time-hourglass 0.3.0; Vincent Hanquez 2014.
// Complete notice: licenses/time-hourglass-BSD-3-Clause.txt.
package io.heapy.kotdoc.time

import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import io.heapy.kotdoc.parsing.Parsec
import io.heapy.kotdoc.parsing.ParserCharacters
import java.math.BigInteger

class HourglassFormat(private val native: HourglassNativeBackend = HourglassNativeTime.system) {
    private val codecs = HourglassTimeCodecs { native }
    fun <A> timePrint(format: HourglassTimeFormat, value: A, time: HourglassTimeable<A>) = printWith(format,value,time,HourglassTimezoneOffset.UTC)
    fun <A> localTimePrint(format: HourglassTimeFormat, value: HourglassLocalTime<A>, time: HourglassTimeable<A>) = printWith(format,value.value,time,value.timezone)
    private fun <A> printWith(format: HourglassTimeFormat, value: A, time: HourglassTimeable<A>, timezone: HourglassTimezoneOffset): CharacterString {
        val dt by lazy { HourglassTimeOperations.genericDateTime(value,time,native) }
        val date by lazy { dt.date }
        val tod by lazy { dt.time }
        val ns by lazy { time.getNanoSeconds(value).value }
        fun pad2(n: Long) = HourglassPadding.pad2(BigInteger.valueOf(n))
        fun pad(n: Long,width: Long) = HourglassPadding.padN(width,BigInteger.valueOf(n))
        fun zone(): String {
            val absolute = kotlin.math.abs(timezone.minutes)
            return (if (timezone.minutes < 0) "-" else "+")+pad2(Math.floorDiv(absolute,60L))+":"+pad2(Math.floorMod(absolute,60L))
        }
        return Chars.concat(format.toFormat().elements.map { e ->
            if (e is HourglassFormatElement.Text) CharacterString(intArrayOf(e.point))
            else if (e is HourglassFormatElement.Function) e.function.print(lazy { dt },timezone)
            else Chars.fromString(when (e) {
                is HourglassFormatElement.Precision -> {
                    if (e.digits !in 1..9) error("invalid precision format")
                    pad(Math.floorDiv(ns,pow10(9-e.digits)),e.digits)
                }
                is HourglassFormatElement.Standard -> when (e.kind) {
                    HourglassFormatKind.Year -> date.year.toString()
                    HourglassFormatKind.Year4 -> HourglassPadding.pad4(BigInteger.valueOf(date.year))
                    HourglassFormatKind.Year2 -> pad2(date.year-1900)
                    HourglassFormatKind.Month -> (date.month.ordinal+1).toString()
                    HourglassFormatKind.Month2 -> pad2(date.month.ordinal+1L)
                    HourglassFormatKind.MonthNameShort -> date.month.name.take(3)
                    HourglassFormatKind.DayYear -> HourglassCalendar.getDayOfTheYear(date).toString()
                    HourglassFormatKind.DayYear3 -> HourglassPadding.pad3(BigInteger.valueOf(HourglassCalendar.getDayOfTheYear(date)))
                    HourglassFormatKind.Day -> date.day.toString()
                    HourglassFormatKind.Day2 -> pad2(date.day)
                    HourglassFormatKind.Hour -> pad2(tod.hour.value)
                    HourglassFormatKind.Minute -> pad2(tod.minute.value)
                    HourglassFormatKind.Second -> pad2(tod.second.value)
                    HourglassFormatKind.UnixSecond -> time.getElapsed(value).seconds.value.toString()
                    HourglassFormatKind.MilliSecond -> pad(Math.floorDiv(ns,1000000L),3)
                    HourglassFormatKind.MicroSecond -> pad(Math.floorMod(Math.floorDiv(ns,1000L),1000L),3)
                    HourglassFormatKind.NanoSecond -> pad(Math.floorMod(ns,1000L),3)
                    HourglassFormatKind.TimezoneName -> HourglassTimezoneMinutes(timezone.minutes).name
                    HourglassFormatKind.TzHM -> timezone.toString()
                    HourglassFormatKind.TzHMColon -> zone()
                    HourglassFormatKind.TzHMColonZ -> if (timezone.minutes == 0L) "Z" else zone()
                    HourglassFormatKind.TzOffset -> timezone.minutes.toString()
                    HourglassFormatKind.Spaces -> " "
                }
            })
        })
    }

    fun localTimeParseE(format: HourglassTimeFormat, input: CharacterString): HourglassFormatParseResult<HourglassLocalTime<HourglassDateTime>> {
        var state = HourglassFormatState.initial(); var points = input.points(); var at = 0
        fun text(s: String) = Chars.fromString(s)
        fun tail() = points.copyOfRange(at,points.size)
        fun raw(ps: IntArray) = CharacterString(ps)
        fun quoted(ps: IntArray) = HaskellString.quoteCodePoints(ps)
        fun number(ps: IntArray): Long = ps.fold(0L) { n,c -> n*10+c-48 }
        class Failed(val messageChars: CharacterString): RuntimeException()
        fun fail(s: String): Nothing = throw Failed(text(s))
        fun digits(n: Long): Long {
            val count = n.coerceAtLeast(0).coerceAtMost((points.size-at).toLong()).toInt()
            val ps = points.copyOfRange(at,at+count)
            if (count.toLong() < n) fail("not enough chars: expecting $n got ${quoted(ps)}")
            if (!ps.all(ParserCharacters::isDigit)) fail("non-digit char(s) in ${quoted(ps)}")
            at += count; return number(ps)
        }
        fun unbounded(): Long {
            val start = at
            while (at < points.size && ParserCharacters.isDigit(points[at])) at++
            if (at == start) throw Failed(Chars.append(text("no digits at start of:"),raw(tail())))
            return number(points.copyOfRange(start,at))
        }
        fun modifyDate(f: (HourglassDate) -> HourglassDate) { state = state.mapDate(f) }
        fun modifyTime(f: (HourglassTimeOfDay) -> HourglassTimeOfDay) { state = state.mapTime(f) }
        fun dayYear(d: Long) {
            if (d <= 0 || d > 366) fail("day of year invalid, got: $d")
            val previous = state
            modifyDate { HourglassCalendar.dateAddPeriod(HourglassDate(previous.date.year,HourglassMonth.January,1),HourglassPeriod(0,0,d-1)) }
        }
        fun fraction(shift: Long,value: Long) {
            modifyTime {
                val s = pow10(shift); val ns = it.nanoSecond.value
                val divided = Math.floorDiv(ns,s); val low = Math.floorMod(ns,s); val high = Math.floorDiv(divided,1000L)
                it.copy(nanoSecond=HourglassNanoSeconds((high*1000+value)*s+low))
            }
        }
        fun zone(colon: Boolean) {
            var negative = false
            if (points[at] == 45 || points[at] == 43) { negative = points[at] == 45; at++ }
            val n = if (colon) 5 else 4
            if (points.size-at < n || (colon && points[at+2] != 58)) fail("invalid timezone format")
            val ps = if (colon) intArrayOf(points[at],points[at+1],points[at+3],points[at+4]) else points.copyOfRange(at,at+4)
            if (!ps.all(ParserCharacters::isDigit)) fail("non-digit char(s) in: ${quoted(ps)}")
            val minutes = number(ps.copyOfRange(0,2))*60+number(ps.copyOfRange(2,4)); at += n
            state = state.withTimezone(HourglassTimezoneOffset(if (negative) -minutes else minutes))
        }
        for (e in format.toFormat().elements) {
            if (at == points.size) return HourglassFormatParseResult.Failure(e,text("empty"))
            try {
                when (e) {
                    is HourglassFormatElement.Text -> {
                        if (points[at] != e.point) fail("unexpected char, got: ${Parsec.showChar(points[at])}")
                        at++
                    }
                    is HourglassFormatElement.Precision -> { val n = digits(e.digits); modifyTime { it.copy(nanoSecond=HourglassNanoSeconds(n)) } }
                    is HourglassFormatElement.Function -> when (val r = e.function.parse(state,raw(tail()))) {
                        is HourglassCustomParseResult.Failure -> throw Failed(r.message)
                        is HourglassCustomParseResult.Success -> {
                            // A custom callback can replace the remaining input, not only consume a prefix.
                            state = r.state; points = r.remaining.points(); at = 0
                        }
                    }
                    is HourglassFormatElement.Standard -> when (e.kind) {
                        HourglassFormatKind.Year -> { val n = unbounded(); modifyDate { it.copy(year=n) } }
                        HourglassFormatKind.Year4 -> { val n = digits(4); modifyDate { it.copy(year=n) } }
                        HourglassFormatKind.Year2 -> { val n = digits(2); modifyDate { it.copy(year=n+if (n<70) 2000 else 1900) } }
                        HourglassFormatKind.Month -> {
                            val n = unbounded(); if (n !in 1..12) fail("month invalid, got: $n")
                            modifyDate { it.copy(month=HourglassMonth.entries[(n-1).toInt()]) }
                        }
                        HourglassFormatKind.Month2 -> { val n = digits(2); modifyDate { it.copy(month=HourglassMonth.entries[Math.floorMod(n-1,12L).toInt()]) } }
                        HourglassFormatKind.MonthNameShort -> {
                            val start = at; val count = minOf(3,points.size-at); val ps = points.copyOfRange(at,at+count)
                            if (count < 3) fail("not enough chars: expecting 3 got ${quoted(ps)}")
                            val month = HourglassMonth.entries.firstOrNull { it.name.take(3).codePoints().toArray().contentEquals(ps) }
                                ?: throw Failed(Chars.append(text("unknown month: "),raw(ps)))
                            at = start+3; modifyDate { it.copy(month=month) }
                        }
                        HourglassFormatKind.DayYear -> dayYear(unbounded())
                        HourglassFormatKind.DayYear3 -> dayYear(digits(3))
                        HourglassFormatKind.Day -> { val n = unbounded(); if (n !in 1..31) fail("day of month invalid, got: $n"); modifyDate { it.copy(day=n) } }
                        HourglassFormatKind.Day2 -> { val n = digits(2); modifyDate { it.copy(day=n) } }
                        HourglassFormatKind.Hour -> { val n = digits(2); modifyTime { it.copy(hour=HourglassHours(n)) } }
                        HourglassFormatKind.Minute -> { val n = digits(2); modifyTime { it.copy(minute=HourglassMinutes(n)) } }
                        HourglassFormatKind.Second -> { val n = digits(2); modifyTime { it.copy(second=HourglassSeconds(n)) } }
                        HourglassFormatKind.MilliSecond -> fraction(6,digits(3))
                        HourglassFormatKind.MicroSecond -> fraction(3,digits(3))
                        HourglassFormatKind.NanoSecond -> fraction(0,digits(3))
                        HourglassFormatKind.UnixSecond -> { val n = unbounded(); state = state.withDateTime { native.dateTimeFromUnixEpochP(HourglassElapsedP(HourglassElapsed(HourglassSeconds(n)),HourglassNanoSeconds(0))) } }
                        HourglassFormatKind.TimezoneName -> {
                            val start = at; while (at < points.size && !ParserCharacters.isSpace(points[at])) at++
                            if (at == start) throw Failed(Chars.append(text("no non-white space at start of: "),raw(tail())))
                        }
                        HourglassFormatKind.TzHMColonZ -> if (points[at] == 90) at++ else zone(true)
                        HourglassFormatKind.TzHMColon -> zone(true)
                        HourglassFormatKind.TzHM -> zone(false)
                        HourglassFormatKind.TzOffset -> {
                            val negative = points[at] == 45
                            if (negative || points[at] == 43) at++
                            val n = unbounded(); val limit = if (negative) 720 else 840
                            if (n < 0 || n > limit) fail("offset invalid, got: ${if (negative) "-" else "+"}$n")
                            state = state.withTimezone(HourglassTimezoneOffset(if (negative) -n else n))
                        }
                        HourglassFormatKind.Spaces -> {
                            val start = at; while (at < points.size && ParserCharacters.isSpace(points[at])) at++
                            if (at == start) throw Failed(Chars.append(text("no white space at start of: "),raw(tail())))
                        }
                    }
                }
            } catch (failure: Failed) { return HourglassFormatParseResult.Failure(e,failure.messageChars) }
        }
        val end = state; val remaining = raw(tail())
        return HourglassFormatParseResult.Success({ HourglassLocalTime(end.dateTime,end.timezone) },remaining)
    }
    fun timeParseE(format: HourglassTimeFormat, input: CharacterString): HourglassFormatParseResult<HourglassDateTime> = when (val r = localTimeParseE(format,input)) {
        is HourglassFormatParseResult.Failure -> r
        is HourglassFormatParseResult.Success -> HourglassFormatParseResult.Success({ r.value.toGlobal(codecs.dateTime) },r.remaining)
    }
    fun localTimeParse(format: HourglassTimeFormat, input: CharacterString): HourglassOptionalTime<HourglassLocalTime<HourglassDateTime>>? = when (val r = localTimeParseE(format,input)) {
        is HourglassFormatParseResult.Failure -> null
        is HourglassFormatParseResult.Success -> HourglassOptionalTime { r.value }
    }
    fun timeParse(format: HourglassTimeFormat, input: CharacterString): HourglassOptionalTime<HourglassDateTime>? = when (val r = timeParseE(format,input)) {
        is HourglassFormatParseResult.Failure -> null
        is HourglassFormatParseResult.Success -> HourglassOptionalTime { r.value }
    }
    private fun pow10(n: Long): Long { var r = 1L; repeat(n.toInt()) { r *= 10 }; return r }
}
