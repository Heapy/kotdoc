// Port of Text.Pandoc.Options, Copyright (C) 2012-2024 John MacFarlane.
// GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.options

import kotlinx.serialization.json.*
import io.heapy.kotdoc.formats.HaskellString

const val DEFAULT_MATH_JAX_URL = "https://cdn.jsdelivr.net/npm/mathjax@4/tex-chtml.js"
const val DEFAULT_WEB_TEX_URL = "https://latex.codecogs.com/png.latex?"
const val DEFAULT_KA_TEX_URL = "https://cdn.jsdelivr.net/npm/katex@latest/dist/"

sealed interface MathMethod {
    data object PlainMath : MathMethod
    data class WebTeX(val url: String) : MathMethod
    data object GladTeX : MathMethod
    data object MathML : MathMethod
    data class MathJax(val url: String) : MathMethod
    data class KaTeX(val url: String) : MathMethod

    fun toJson(): JsonElement {
        fun withUrl(name: String, url: String): JsonElement = if (url.isEmpty()) JsonPrimitive(name)
            else buildJsonObject { put("method", name); put("url", url) }
        return when (this) {
            PlainMath -> JsonPrimitive("plain")
            is WebTeX -> withUrl("webtex", url)
            GladTeX -> JsonPrimitive("gladtex")
            MathML -> JsonPrimitive("mathml")
            is MathJax -> withUrl("mathjax", url)
            is KaTeX -> withUrl("katex", url)
        }
    }

    companion object {
        /** CommandLineOptions uses Haskell Show strings to construct JSON, not JSON escaping. */
        fun fromArgument(argument: String): MathMethod {
            val delimiter = argument.indexOfFirst { it == ':' || it == '=' }
            val key = if (delimiter < 0) argument else argument.substring(0, delimiter)
            val value = if (delimiter < 0) "true" else argument.substring(delimiter + 1)
            val json = if (value == "true") HaskellString.quote(key)
                else "{\"method\":${HaskellString.quote(key)},\"url\":${HaskellString.quote(value)}}"
            return try { fromJson(Json.parseToJsonElement(json)) }
            catch (_: IllegalArgumentException) { throw MathMethodArgumentException(argument) }
        }

        fun fromJson(value: JsonElement): MathMethod {
            fun invalid(): Nothing = throw IllegalArgumentException("Unknown HTML math method $value")
            val method: String
            val url: String?
            when (value) {
                is JsonObject -> {
                    val name = value["method"]
                    if (name !is JsonPrimitive || !name.isString) invalid()
                    method = name.content
                    url = when (val v = value["url"]) {
                        null, JsonNull -> null
                        is JsonPrimitive -> if (v.isString) v.content else invalid()
                        else -> invalid()
                    }
                }
                is JsonPrimitive -> {
                    if (!value.isString) invalid()
                    method = value.content
                    url = null
                }
                else -> invalid()
            }
            return when (method) {
                "plain" -> PlainMath
                "webtex" -> WebTeX(url ?: DEFAULT_WEB_TEX_URL)
                "gladtex" -> GladTeX
                "mathml" -> MathML
                "mathjax" -> MathJax(url ?: DEFAULT_MATH_JAX_URL)
                "katex" -> KaTeX(url ?: DEFAULT_KA_TEX_URL)
                else -> invalid()
            }
        }
    }
}

class MathMethodArgumentException(val argument: String) : IllegalArgumentException(
    "Unknown math-method '$argument'.  Expected one of: plain, mathml, webtex, mathjax, katex, gladtex."
)
