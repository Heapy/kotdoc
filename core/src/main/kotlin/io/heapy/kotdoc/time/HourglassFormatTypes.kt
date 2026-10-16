// Time.Format, time-hourglass 0.3.0; Vincent Hanquez 2014.
// Complete notice: licenses/time-hourglass-BSD-3-Clause.txt.
package io.heapy.kotdoc.time

import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import io.heapy.kotdoc.parsing.Parsec

enum class HourglassFormatKind(val haskellName: String) {
    Year2("Year2"), Year4("Year4"), Year("Year"), Month("Month"), Month2("Month2"), MonthNameShort("MonthName_Short"),
    DayYear("DayYear"), DayYear3("DayYear3"), Day("Day"), Day2("Day2"), Hour("Hour"), Minute("Minute"), Second("Second"),
    UnixSecond("UnixSecond"), MilliSecond("MilliSecond"), MicroSecond("MicroSecond"), NanoSecond("NanoSecond"),
    TimezoneName("TimezoneName"), TzHMColonZ("TzHM_Colon_Z"), TzHMColon("TzHM_Colon"), TzHM("TzHM"), TzOffset("Tz_Offset"), Spaces("Spaces")
}
sealed interface HourglassFormatElement {
    fun showCharacters(): CharacterString = if (this is Function) Chars.append(Chars.fromString("Format_Fct "),function.name) else Chars.fromString(toString())
    data class Standard(val kind: HourglassFormatKind): HourglassFormatElement {
        override fun toString() = "Format_${kind.haskellName}"
    }
    data class Precision(val digits: Long): HourglassFormatElement {
        override fun toString() = "Format_Precision ${if (digits < 0) "($digits)" else digits.toString()}"
    }
    data class Text(val point: Int): HourglassFormatElement {
        init { require(point in 0..0x10ffff) }
        override fun toString() = "Format_Text ${Parsec.showChar(point)}"
    }
    data class Function(val function: HourglassFormatFunction): HourglassFormatElement {
        override fun toString() = "Format_Fct $function"
    }
}
fun interface HourglassTimeFormat { fun toFormat(): HourglassTimeFormatString }
class HourglassTimeFormatString(elements: List<HourglassFormatElement>): HourglassTimeFormat {
    val elements: List<HourglassFormatElement> = java.util.Collections.unmodifiableList(elements.toList())
    override fun toFormat() = this
    override fun equals(other: Any?) = other is HourglassTimeFormatString && elements == other.elements
    override fun hashCode() = elements.hashCode()
    fun showCharacters() = Chars.concat(listOf(Chars.fromString("TimeFormatString [")) + elements.flatMapIndexed { i,e -> if (i == 0) listOf(e.showCharacters()) else listOf(Chars.fromString(","),e.showCharacters()) } + Chars.fromString("]"))
    override fun toString() = Chars.build(showCharacters())
    companion object {
        fun fromString(text: String) = fromCharacters(Chars.fromString(text))
        fun fromCharacters(text: CharacterString): HourglassTimeFormatString {
            val points = text.points(); val out = mutableListOf<HourglassFormatElement>(); var at = 0
            val tokens = listOf("YYYY" to HourglassFormatKind.Year4, "YY" to HourglassFormatKind.Year2,
                "MM" to HourglassFormatKind.Month2, "Mon" to HourglassFormatKind.MonthNameShort, "MI" to HourglassFormatKind.Minute, "M" to HourglassFormatKind.Month,
                "JJJ" to HourglassFormatKind.DayYear3, "DD" to HourglassFormatKind.Day2, "H" to HourglassFormatKind.Hour, "S" to HourglassFormatKind.Second,
                "ms" to HourglassFormatKind.MilliSecond, "us" to HourglassFormatKind.MicroSecond, "μ" to HourglassFormatKind.MicroSecond, "ns" to HourglassFormatKind.NanoSecond,
                "EPOCH" to HourglassFormatKind.UnixSecond, "TZHM" to HourglassFormatKind.TzHM, "TZH:M" to HourglassFormatKind.TzHMColon, "TZOFS" to HourglassFormatKind.TzOffset)
            while (at < points.size) {
                val token = tokens.firstOrNull { (s,_) -> at+s.length <= points.size && s.indices.all { points[at+it] == s[it].code } }
                when {
                    token != null -> { out.add(HourglassFormatElement.Standard(token.second)); at += token.first.length }
                    points[at] == 112 && at+1 < points.size && points[at+1] in 49..57 -> { out.add(HourglassFormatElement.Precision((points[at+1]-48).toLong())); at += 2 }
                    points[at] == 92 && at+1 < points.size -> { out.add(HourglassFormatElement.Text(points[at+1])); at += 2 }
                    points[at] == 32 -> { out.add(HourglassFormatElement.Standard(HourglassFormatKind.Spaces)); at++ }
                    else -> { out.add(HourglassFormatElement.Text(points[at])); at++ }
                }
            }
            return HourglassTimeFormatString(out)
        }
    }
}
data object HourglassISO8601Date: HourglassTimeFormat {
    override fun toFormat() = HourglassTimeFormatString(listOf(HourglassFormatElement.Standard(HourglassFormatKind.Year),HourglassFormatElement.Text(45),HourglassFormatElement.Standard(HourglassFormatKind.Month2),HourglassFormatElement.Text(45),HourglassFormatElement.Standard(HourglassFormatKind.Day2)))
    override fun toString() = "ISO8601_Date"
}
data object HourglassISO8601DateAndTime: HourglassTimeFormat {
    override fun toFormat() = HourglassTimeFormatString(HourglassISO8601Date.toFormat().elements + listOf(HourglassFormatElement.Text(84),HourglassFormatElement.Standard(HourglassFormatKind.Hour),HourglassFormatElement.Text(58),HourglassFormatElement.Standard(HourglassFormatKind.Minute),HourglassFormatElement.Text(58),HourglassFormatElement.Standard(HourglassFormatKind.Second),HourglassFormatElement.Standard(HourglassFormatKind.TzHMColonZ)))
    override fun toString() = "ISO8601_DateAndTime"
}

/** Date/time fields and the enclosing DateTime constructor can remain suspended. */
class HourglassFormatState private constructor(private val pair: Lazy<Payload>) {
    private class Node(val date: Lazy<HourglassDate>, val time: Lazy<HourglassTimeOfDay>)
    private class Payload(val node: Lazy<Node>, val timezone: HourglassTimezoneOffset)
    val timezone get() = pair.value.timezone
    val date get() = pair.value.node.value.date.value
    val time get() = pair.value.node.value.time.value
    val dateTime get() = HourglassDateTime.deferred({ date },{ time })
    fun withTimezone(value: HourglassTimezoneOffset) = HourglassFormatState(lazy { val p = pair.value; Payload(p.node,value) })
    fun withDateTime(value: () -> HourglassDateTime) = HourglassFormatState(lazy {
        val p = pair.value; Payload(lazy { val dt = value(); Node(lazy { dt.date },lazy { dt.time }) },p.timezone)
    })
    fun mapDate(f: (HourglassDate) -> HourglassDate): HourglassFormatState {
        val previous = pair
        return HourglassFormatState(lazy { val p = previous.value; val n = p.node.value; Payload(lazyOf(Node(lazy { f(n.date.value) },n.time)),p.timezone) })
    }
    fun mapTime(f: (HourglassTimeOfDay) -> HourglassTimeOfDay): HourglassFormatState {
        val previous = pair
        return HourglassFormatState(lazy { val p = previous.value; val n = p.node.value; Payload(lazyOf(Node(n.date,lazy { f(n.time.value) })),p.timezone) })
    }
    companion object {
        fun deferred(value: () -> HourglassDateTime, timezone: HourglassTimezoneOffset): HourglassFormatState = HourglassFormatState(lazyOf(Payload(lazy { val dt = value(); Node(lazy { dt.date },lazy { dt.time }) },timezone)))
        fun initial() = deferred({ HourglassDateTime(HourglassDate(0,HourglassMonth.January,0),HourglassTimeCodecs.ZERO_TIME) },HourglassTimezoneOffset.UTC)
    }
}
sealed interface HourglassCustomParseResult {
    data class Failure(val message: CharacterString): HourglassCustomParseResult
    data class Success(val state: HourglassFormatState, val remaining: CharacterString): HourglassCustomParseResult
}
class HourglassFormatFunction(val name: CharacterString,
    val parse: (HourglassFormatState,CharacterString) -> HourglassCustomParseResult,
    val print: (Lazy<HourglassDateTime>,HourglassTimezoneOffset) -> CharacterString) {
    override fun equals(other: Any?) = other is HourglassFormatFunction && name == other.name
    override fun hashCode() = name.hashCode()
    override fun toString() = Chars.build(name)
}
sealed interface HourglassFormatParseResult<out A> {
    data class Failure(val element: HourglassFormatElement, val message: CharacterString): HourglassFormatParseResult<Nothing>
    class Success<A>(value: () -> A, val remaining: CharacterString): HourglassFormatParseResult<A> {
        val value: A by lazy(value)
    }
}
class HourglassOptionalTime<A>(value: () -> A) { val value: A by lazy(value) }
