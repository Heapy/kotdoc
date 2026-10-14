// Text.Pandoc.XML, Copyright (C) 2006-2024 John MacFarlane.
// Commonmark.Entity.lookupEntity, Copyright (C) 2018-2021 John MacFarlane
// and 2006-2018 Neil Mitchell (BSD-3-Clause). See licenses/.
// Pandoc functions are GPL-2.0-or-later.
package io.heapy.kotdoc.xml

import io.heapy.kotdoc.html.HtmlEntities
import io.heapy.kotdoc.layout.*
import io.heapy.kotdoc.parsing.ParserCharacters
import kotlinx.serialization.json.*
import java.util.Collections

/** Pandoc's XML utilities; separate from XML.Light and TagSoup escaping. */
object PandocXml {
    private val data = Json.parseToJsonElement(checkNotNull(javaClass.getResource(
        "/io/heapy/kotdoc/pandoc-xml-data.json")).readText()).jsonObject
    private fun attributes(key: String): Set<String> = Collections.unmodifiableSet(
        data.getValue(key).jsonArray.map { it.jsonPrimitive.content }.toSet())
    val html4Attributes: Set<String> = attributes("html4Attributes")
    val html5Attributes: Set<String> = attributes("html5Attributes")
    val rdfaAttributes: Set<String> = attributes("rdfaAttributes")
    private val entities = data.getValue("entities").jsonArray.associate {
        it.jsonArray[0].jsonPrimitive.content to it.jsonArray[1].jsonPrimitive.content
    }
    private val shortEntities: Map<Int, String> = buildMap {
        // foldr + insertWith prefers the earlier source name on equal lengths.
        for ((entity, value) in HtmlEntities.htmlEntities.asReversed()) {
            val points = TextChars.points(value)
            if (points.size != 1) continue
            val name = entity.substringBefore(';')
            val previous = get(points[0])
            if (previous == null || name.length <= previous.length) put(points[0], name)
        }
    }

    fun escapeCharForXML(point: Int): String {
        require(point in 0..0x10ffff) { "Not a Haskell Char: $point" }
        return when (point) {
            38 -> "&amp;"; 60 -> "&lt;"; 62 -> "&gt;"; 34 -> "&quot;"
            else -> TextChars.fromPoints(intArrayOf(point))
        }
    }

    fun escapeStringForXML(value: String): String = buildString {
        for (point in TextChars.points(TextChars.fromString(value))) {
            if (legal(point)) append(escapeCharForXML(point))
        }
    }
    private fun legal(point: Int) = point == 9 || point == 10 || point == 13 ||
        point in 0x20..0xd7ff || point in 0xe000..0xfffd || point in 0x10000..0x10ffff

    fun escapeNCName(value: String): String = buildString {
        for ((index, point) in TextChars.points(TextChars.fromString(value)).withIndex()) {
            val valid = ParserCharacters.isAlpha(point) || point == 95 ||
                (index > 0 && (point == 45 || point == 46 || point == 183 || ParserCharacters.isDigit(point) ||
                    point in 0x300..0x36f || point in 0x203f..0x2040))
            if (valid) appendCodePoint(point)
            else append('U').append(point.toString(16).uppercase().padStart(4, '0'))
        }
    }

    fun toEntities(value: String): String = buildString {
        for (point in TextChars.points(TextChars.fromString(value))) {
            if (point < 128) appendCodePoint(point)
            else append("&#x").append(point.toString(16).uppercase()).append(';')
        }
    }
    fun toHtml5Entities(value: String): String = buildString {
        for (point in TextChars.points(TextChars.fromString(value))) {
            if (point < 128) appendCodePoint(point)
            else {
                val name = shortEntities[point]
                if (name == null) append("&#").append(point).append(';')
                else append('&').append(name).append(';')
            }
        }
    }

    /** Commonmark lookup, without a leading '&'; numeric names may omit ';'. */
    fun lookupEntity(input: String): String? {
        val name = TextChars.fromString(input)
        if (!name.startsWith('#')) return entities[name]
        var digits = name.substring(1)
        val radix = if (digits.startsWith('x') || digits.startsWith('X')) {
            digits = digits.substring(1); 16
        } else 10
        if (radix == 16 && (digits.startsWith("0x") || digits.startsWith("0X"))) digits = digits.substring(2)
        if (digits.endsWith(';')) digits = digits.dropLast(1)
        if (digits.isEmpty()) return null
        // The source parses Integer: overlarge values still succeed, producing
        // U+FFFD. Saturation avoids allocating arbitrarily large host integers.
        var point = 0
        for (char in digits) {
            val digit = when (char) {
                in '0'..'9' -> char - '0'
                in 'a'..'f' if radix == 16 -> char - 'a' + 10
                in 'A'..'F' if radix == 16 -> char - 'A' + 10
                else -> return null
            }
            point = minOf(0x110000, point * radix + digit)
        }
        return TextChars.fromPoints(intArrayOf(if (point == 0 || point > 0x10ffff) 0xfffd else point))
    }

    fun fromEntities(input: String): String {
        val value = TextChars.fromString(input)
        return buildString {
            var position = 0
            while (position < value.length) {
                val amp = value.indexOf('&', position)
                if (amp < 0) { append(value, position, value.length); break }
                append(value, position, amp)
                var end = amp
                while (end < value.length) {
                    val point = value.codePointAt(end)
                    if (point == 59 || ParserCharacters.isSpace(point)) break
                    end += Character.charCount(point)
                }
                val decoded = lookupEntity(value.substring(amp + 1, end) + ";")
                if (decoded == null) {
                    append(value, amp, end)
                    position = end
                } else {
                    append(decoded)
                    position = if (end < value.length && value[end] == ';') end + 1 else end
                }
            }
        }
    }

    private fun tag(name: String, attrs: List<Pair<String, String>>, close: String): String = buildString {
        append('<').append(TextChars.fromString(name))
        for ((key, value) in attrs) append(' ').append(escapeStringForXML(key)).append("=\"")
            .append(escapeStringForXML(value).replace("\n", "&#10;")).append('"')
        append(close)
    }
    fun <A> inTags(layout: DocLayout<A>, indented: Boolean, name: String,
                   attrs: List<Pair<String, String>>, contents: Doc<A>): Doc<A> {
        val open = layout.text(tag(name, attrs, ">"))
        val close = layout.text("</" + TextChars.fromString(name) + ">")
        return if (indented) layout.above(open, layout.above(layout.nest(2, contents), close))
            else open + (contents + close)
    }
    fun <A> selfClosingTag(layout: DocLayout<A>, name: String, attrs: List<Pair<String, String>>): Doc<A> =
        layout.text(tag(name, attrs, " />"))
    fun <A> inTagsSimple(layout: DocLayout<A>, name: String, contents: Doc<A>): Doc<A> =
        inTags(layout, false, name, emptyList(), contents)
    fun <A> inTagsIndented(layout: DocLayout<A>, name: String, contents: Doc<A>): Doc<A> =
        inTags(layout, true, name, emptyList(), contents)
}
