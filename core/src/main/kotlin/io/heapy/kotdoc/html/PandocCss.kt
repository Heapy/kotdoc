// Text.Pandoc.CSS, Copyright (C) 2006-2024 John MacFarlane,
// 2015-2016 Mauro Bieg, 2015 Ophir Lifshitz. GPL-2.0-or-later; licenses/.
package io.heapy.kotdoc.html

import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.parsing.ParserCharacters

/** The upstream style-attribute parser, including Parsec's consumed-failure rules. */
object PandocCss {
    fun cssAttributes(style: String): List<Pair<String, String>> = parse(style) ?: emptyList()

    fun pickStylesToKVs(properties: List<String>, style: String): List<Pair<String, String>> =
        cssAttributes(style).filter { it.first in properties }

    fun pickStyleAttrProps(properties: List<String>, style: String): String? {
        val styles = parse(style) ?: return null
        for (key in properties) styles.firstOrNull { it.first == key }?.let { return it.second }
        return null
    }

    private fun parse(input: String): List<Pair<String, String>>? {
        val style = TextChars.fromString(input)
        val result = mutableListOf<Pair<String, String>>()
        var position = 0
        while (position < style.length) {
            // many1 ruleParser stops only when its next rule fails without
            // consuming input. There is deliberately no eof check.
            if (style[position] == ':') break
            val start = position
            while (position < style.length && style[position] != ':') position++
            if (position == style.length) return null
            val property = trim(style.substring(start, position))
            position++
            val valueStart = position
            while (position < style.length && style[position] != ':' && style[position] != ';') position++
            if (position == valueStart) return null
            val value = trim(style.substring(valueStart, position))
            if (position < style.length && style[position] == ';') position++
            while (position < style.length) {
                val point = style.codePointAt(position)
                if (!ParserCharacters.isSpace(point)) break
                position += Character.charCount(point)
            }
            result.add(property to value)
        }
        return result.takeIf { it.isNotEmpty() }
    }

    // Text.Pandoc.Shared.trim uses four ASCII whitespace characters. This
    // differs from Parsec.spaces, which uses GHC's Unicode isSpace predicate.
    private fun trim(value: String) = value.trim { it == ' ' || it == '\r' || it == '\n' || it == '\t' }
}
