// Text.Pandoc.Options: Copyright (C) 2012-2024 John MacFarlane.
// GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.options

import io.heapy.kotdoc.highlighting.HighlightJsonException
import io.heapy.kotdoc.highlighting.HighlightStyle
import kotlinx.serialization.json.*

sealed interface HighlightMethod {
    data class Skylighting(val style: HighlightStyle) : HighlightMethod {
        override fun toString() = "Skylighting ($style)"
    }
    data object IdiomaticHighlighting : HighlightMethod
    data object DefaultHighlighting : HighlightMethod
    data object NoHighlighting : HighlightMethod
    fun toJson(): JsonElement = when (this) {
        is Skylighting -> style.toJson()
        IdiomaticHighlighting -> JsonPrimitive("idiomatic")
        DefaultHighlighting -> JsonPrimitive("default")
        NoHighlighting -> JsonPrimitive("none")
    }
    companion object {
        fun fromJson(value: JsonElement): HighlightMethod = when {
            value is JsonPrimitive && value.isString -> when (value.content) {
                "none" -> NoHighlighting; "idiomatic" -> IdiomaticHighlighting; "default" -> DefaultHighlighting
                else -> throw HighlightJsonException("Unknown highlighting method ${value.content}")
            }
            value == JsonPrimitive(true) -> DefaultHighlighting
            value == JsonPrimitive(false) || value == JsonNull -> NoHighlighting
            else -> Skylighting(HighlightStyle.fromJson(value))
        }
    }
}
