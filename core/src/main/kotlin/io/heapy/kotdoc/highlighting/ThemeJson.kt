package io.heapy.kotdoc.highlighting

import kotlinx.serialization.json.*
import kotlinx.serialization.SerializationException

/** Keep Aeson's first duplicate member while using the shared JSON syntax parser. */
internal object ThemeJson {
    private val number = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
    fun parse(text: String): JsonElement {
        Json.parseToJsonElement(text) // Validate all syntax, including discarded values.
        return Parser(text).value()
    }
    private class Parser(val text: String) {
        private var at = 0
        private fun spaces() { while (at < text.length && text[at] in " \n\r\t") at++ }
        private fun string(): JsonPrimitive {
            val start = at++
            while (true) {
                when (val c = text[at++]) {
                    '\\' -> at++
                    '"' -> {
                        val value = Json.parseToJsonElement(text.substring(start, at)).jsonPrimitive
                        // Aeson rejects lone UTF-16 escapes even in discarded members.
                        var i = 0
                        while (i < value.content.length) {
                            val c = value.content[i++]
                            if (c.isHighSurrogate()) {
                                if (i == value.content.length || !value.content[i++].isLowSurrogate())
                                    throw SerializationException("Invalid Unicode surrogate in theme")
                            } else if (c.isLowSurrogate()) throw SerializationException("Invalid Unicode surrogate in theme")
                        }
                        return value
                    }
                    else -> if (c < ' ') throw SerializationException("Unescaped control character in theme")
                }
            }
        }
        fun value(): JsonElement {
            spaces()
            return when (text[at]) {
                '"' -> string()
                '{' -> {
                    at++; spaces(); val fields = linkedMapOf<String, JsonElement>()
                    if (text[at] != '}') while (true) {
                        spaces(); val key = string().content; spaces(); at++
                        val value = value(); fields.putIfAbsent(key, value); spaces()
                        if (text[at] == '}') break
                        at++
                    }
                    at++; JsonObject(fields)
                }
                '[' -> {
                    at++; spaces(); val values = mutableListOf<JsonElement>()
                    if (text[at] != ']') while (true) {
                        values.add(value()); spaces()
                        if (text[at] == ']') break
                        at++
                    }
                    at++; JsonArray(values)
                }
                else -> {
                    val start = at
                    while (at < text.length && text[at] !in " \n\r\t,]}") at++
                    val literal = text.substring(start, at)
                    if (literal !in listOf("null", "true", "false") && !number.matches(literal))
                        throw SerializationException("Invalid JSON literal in theme")
                    Json.parseToJsonElement(literal)
                }
            }
        }
    }
}
