// Text.Pandoc.Shared, © 2006–2024 John MacFarlane, GPL-2.0-or-later.
// Date parsing specializes time-1.14's original ParseTime Day grammar.
// TimeLib © Ashley Yakeley/contributors and University of Glasgow; licenses/Time-BSD-2-Clause.txt.
package io.heapy.kotdoc.ast

import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.parsing.ParserCharacters
import io.heapy.kotdoc.parsing.RegexCharacters
import java.math.BigInteger
import java.time.LocalDate

/** The eleven original formats, including their padding and 1969–2068 pivot.
 * Syntax must have exactly one parse before Day validation, as in parseTimeM.
 * This is deliberately a specialization, not a general strptime replacement. */
internal object DateNormalization {
    private val months = listOf("January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December")
    private val formats = listOf("%m/%d/%y", "%m/%d/%Y", "%m/%d/%y", "%Y-%m-%d", "%d %b %Y",
        "%e %B %Y", "%b. %e, %Y", "%B %e, %Y", "%Y%m%d", "%Y%m", "%Y")

    fun normalize(input: String): String? {
        val points = TextChars.points(TextChars.fromString(input))
        for (format in formats) {
            val fields = parse(points, format) ?: continue
            var year = BigInteger.valueOf(1970)
            var month = 1
            var day = 1L
            for ((specifier, value) in fields) when (specifier) {
                'Y' -> year = value.toBigInteger()
                'y' -> value.toInt().let { year = BigInteger.valueOf((if (it >= 69) 1900L else 2000L) + it) }
                'm' -> month = value.toInt()
                'b', 'B' -> month = value.toInt()
                // DayOfMonth is a GHC Int, whose Read instance wraps at 64 bits.
                'd', 'e' -> day = value.toBigInteger().toLong()
            }
            if (year < BigInteger.valueOf(1601) || year > BigInteger.valueOf(9999) || month !in 1..12 || day !in 1..31) continue
            val y = year.toInt()
            val maxDay = LocalDate.of(y, month, 1).lengthOfMonth()
            if (day > maxDay) continue
            return year.toString().padStart(4, '0') + "-" + month.toString().padStart(2, '0') + "-" + day.toString().padStart(2, '0')
        }
        return null
    }

    private fun parse(points: IntArray, format: String): List<Pair<Char, String>>? {
        val results = mutableListOf<List<Pair<Char, String>>>()
        fun skipSpaces(start: Int): Int {
            var at = start
            while (at < points.size && ParserCharacters.isSpace(points[at])) at++
            return at
        }
        fun go(at: Int, token: Int, fields: List<Pair<Char, String>>) {
            if (results.size > 1) return
            if (token == format.length) {
                if (skipSpaces(at) == points.size) results.add(fields)
                return
            }
            val c = format[token]
            if (c == '%') {
                val specifier = format[token + 1]
                if (specifier == 'b' || specifier == 'B') {
                    for ((index, month) in months.withIndex()) {
                        val name = if (specifier == 'b') month.take(3) else month
                        if (at + name.length <= points.size && name.indices.all { RegexCharacters.toUpper(points[at + it]) == name[it].uppercaseChar().code }) {
                            go(at + name.length, token + 2, fields + (specifier to (index + 1).toString()))
                        }
                    }
                } else if (specifier == 'Y' || specifier == 'e') {
                    val negative = specifier == 'Y' && points.getOrNull(at) == '-'.code
                    val start = skipSpaces(at + if (negative) 1 else 0)
                    var end = start
                    while (end < points.size && points[end] in 48..57) end++
                    for (last in start + 1..end) {
                        val value = (if (negative) "-" else "") + String(points, start, last - start)
                        go(last, token + 2, fields + (specifier to value))
                    }
                } else if (at + 2 <= points.size && points[at] in 48..57 && points[at + 1] in 48..57) {
                    go(at + 2, token + 2, fields + (specifier to String(points, at, 2)))
                }
            } else if (c == ' ') {
                if (at < points.size && ParserCharacters.isSpace(points[at])) go(skipSpaces(at + 1), token + 1, fields)
            } else if (points.getOrNull(at) == c.code) go(at + 1, token + 1, fields)
        }
        go(skipSpaces(0), 0, emptyList())
        return results.singleOrNull()
    }
}
