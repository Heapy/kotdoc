// Text.HTML.TagSoup.Entity: Copyright (c) 2006–2019 Neil Mitchell, BSD-3-Clause.
// See licenses/tagsoup-BSD-3-Clause.txt and vendor/tagsoup-0.14.8/.
package io.heapy.kotdoc.html

import kotlinx.serialization.json.*
import java.util.Collections

object HtmlEntities {
    val xmlEntities: List<Pair<String, String>> = Collections.unmodifiableList(
        listOf("quot" to "\"", "amp" to "&", "lt" to "<", "gt" to ">"))
    val htmlEntities: List<Pair<String, String>> = Collections.unmodifiableList(
        Json.parseToJsonElement(checkNotNull(javaClass.getResource(
            "/io/heapy/kotdoc/html-entities.json")).readText()).jsonObject.getValue("entities").jsonArray.map {
            it.jsonArray[0].jsonPrimitive.content to it.jsonArray[1].jsonPrimitive.content
        })
    private val named = htmlEntities.toMap()

    fun lookupNamedEntity(name: String): String? = named[name]

    /** No leading # or trailing semicolon; matches the Haskell String API, including surrogate code points. */
    fun lookupNumericEntity(name: String): String? {
        val hex = name.startsWith('x') || name.startsWith('X')
        val digits = if (hex) name.substring(1) else name
        if (digits.isEmpty()) return null
        val radix = if (hex) 16 else 10
        var value = 0
        for (char in digits) {
            val digit = when (char) {
                in '0'..'9' -> char - '0'
                in 'a'..'f' if hex -> char - 'a' + 10
                in 'A'..'F' if hex -> char - 'A' + 10
                else -> return null
            }
            // Arbitrarily many leading zeros are valid; avoid host integer overflow.
            if (value > (0x10ffff - digit) / radix) return null
            value = value * radix + digit
        }
        return String(Character.toChars(value))
    }

    fun lookupEntity(name: String): String? = if (name.startsWith('#'))
        lookupNumericEntity(name.substring(1)) else lookupNamedEntity(name)

    fun escapeXml(text: String): String = buildString {
        for (char in text) append(when (char) {
            '"' -> "&quot;"
            '&' -> "&amp;"
            '<' -> "&lt;"
            '>' -> "&gt;"
            '\'' -> "&#39;"
            else -> char.toString()
        })
    }
}
