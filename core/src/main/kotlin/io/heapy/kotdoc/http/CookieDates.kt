// cookie-0.5.1 Web.Cookie and the used time-1.14 parse/format/calendar paths.
// MIT and BSD-2-Clause; licenses/cookie-MIT.txt and licenses/Time-BSD-2-Clause.txt.
package io.heapy.kotdoc.http

import io.heapy.kotdoc.parsing.ParserCharacters
import io.heapy.kotdoc.resources.PandocGregorian
import io.heapy.kotdoc.resources.PandocUtcTime
import java.math.BigInteger

/** Specialization of the original TimeLocale/ParseTime/FormatTime for expiresFormat. */
internal object CookieDates {
    val picosPerSecond: BigInteger = BigInteger.TEN.pow(12)
    private val picosPerMinute = picosPerSecond * bi(60)
    private val picosPerDay = picosPerSecond * bi(86400)
    private val weekdays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private fun bi(value: Long) = BigInteger.valueOf(value)
    fun floorDiv(value: BigInteger, divisor: BigInteger): BigInteger {
        val (quotient, remainder) = value.divideAndRemainder(divisor)
        return if (remainder.signum() < 0) quotient - BigInteger.ONE else quotient
    }
    private fun leap(year: BigInteger) = year.mod(bi(4)) == BigInteger.ZERO &&
        (year.mod(bi(100)) != BigInteger.ZERO || year.mod(bi(400)) == BigInteger.ZERO)
    private fun fromGregorian(year: BigInteger, month: Int, day: Int): BigInteger? {
        val lengths = intArrayOf(31, if (leap(year)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        if (month !in 1..12 || day !in 1..lengths[month - 1]) return null
        val ordinal = lengths.take(month - 1).sum() + day
        val y = year - BigInteger.ONE
        return bi(ordinal.toLong()) + bi(365) * y + floorDiv(y, bi(4)) - floorDiv(y, bi(100)) + floorDiv(y, bi(400)) - bi(678576)
    }

    fun parse(text: String): PandocUtcTime? {
        var at = 0
        fun spaces(): Int {
            val start = at
            while (at < text.length && ParserCharacters.isSpace(text[at].code)) at++
            return at - start
        }
        fun literal(value: String): Boolean {
            if (!text.regionMatches(at, value, 0, value.length, ignoreCase = true)) return false
            at += value.length
            return true
        }
        fun digits2(): Int? {
            if (at + 2 > text.length || text[at] !in '0'..'9' || text[at + 1] !in '0'..'9') return null
            val value = (text[at] - '0') * 10 + (text[at + 1] - '0')
            at += 2
            return value
        }
        spaces()
        if (weekdays.none { text.regionMatches(at, it, 0, 3, ignoreCase = true) }) return null
        at += 3
        if (!literal(",") || spaces() == 0) return null
        val day = digits2() ?: return null
        if (!literal("-")) return null
        val month = months.indexOfFirst { text.regionMatches(at, it, 0, 3, ignoreCase = true) } + 1
        if (month == 0) return null
        at += 3
        if (!literal("-")) return null
        val negative = at < text.length && text[at] == '-'
        if (negative) at++
        spaces()
        val start = at
        while (at < text.length && text[at] in '0'..'9') at++
        if (at == start) return null
        var year = BigInteger(text.substring(start, at)).let { if (negative) -it else it }
        if (spaces() == 0) return null
        val hour = digits2() ?: return null
        if (!literal(":")) return null
        val minute = digits2() ?: return null
        if (!literal(":")) return null
        val second = digits2() ?: return null
        if (hour !in 0..23 || minute !in 0..59 || second !in 0..60 || spaces() == 0 || !literal("GMT")) return null
        spaces()
        if (at != text.length || fromGregorian(year, month, day) == null) return null
        if (year >= BigInteger.ZERO && year <= bi(99)) year += if (year <= bi(69)) bi(2000) else bi(1900)
        return PandocUtcTime(fromGregorian(year, month, day)!!, bi(((hour * 60L + minute) * 60 + second)) * picosPerSecond)
    }

    private data class Display(val day: BigInteger, val hour: Long, val minute: Long, val secondPicos: BigInteger)
    private fun display(time: PandocUtcTime): Display {
        val picos = time.dayPicoseconds
        if (picos >= picosPerDay) return Display(time.day, 23, 59, picos - picosPerMinute * bi(1439))
        val minutes = floorDiv(picos, picosPerMinute)
        // timeToTimeOfDay converts hours to the original machine Int before the
        // zero-offset utcToLocalTimeOfDay performs its day adjustment.
        val hour = floorDiv(minutes, bi(60)).toLong()
        return Display(time.day + bi(Math.floorDiv(hour, 24L)), Math.floorMod(hour, 24L),
            minutes.mod(bi(60)).toLong(), picos.mod(picosPerMinute))
    }
    fun format(time: PandocUtcTime): String {
        val display = display(time)
        val date = PandocGregorian.showGregorian(display.day)
        val year = BigInteger(date.dropLast(6))
        val month = date.takeLast(5).take(2).toInt()
        val weekday = weekdays[(display.day + bi(3)).mod(bi(7)).toInt()]
        return "$weekday, ${date.takeLast(2)}-${months[month - 1]}-$year " +
            "${display.hour.toString().padStart(2, '0')}:${display.minute.toString().padStart(2, '0')}:" +
            // FormatTime's %S explicitly floors to machine Int, unlike Show Pico.
            "${showInt2(floorDiv(display.secondPicos, picosPerSecond).toLong())} GMT"
    }
    private fun showInt2(value: Long) = (if (value < 0) "-" else "") + bi(value).abs().toString().padStart(2, '0')
    fun showFixed(picos: BigInteger): String {
        val (whole, fraction) = picos.abs().divideAndRemainder(picosPerSecond)
        return (if (picos.signum() < 0) "-" else "") + whole +
            (if (fraction == BigInteger.ZERO) "" else "." + fraction.toString().padStart(12, '0').trimEnd('0'))
    }
    fun showUtc(time: PandocUtcTime): String {
        val display = display(time)
        val seconds = showFixed(display.secondPicos).let { if (display.secondPicos < picosPerSecond * bi(10)) "0$it" else it }
        return PandocGregorian.showGregorian(display.day) + " " + display.hour.toString().padStart(2, '0') + ":" +
            display.minute.toString().padStart(2, '0') + ":" + seconds + " UTC"
    }
}
