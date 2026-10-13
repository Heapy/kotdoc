// Port of Text.Pandoc.Chunks.PathTemplate and resolvePathTemplate.
// Copyright (C) 2022-2024 John MacFarlane. GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.structure

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

@JvmInline
value class PathTemplate(val value: String) {
    fun resolve(chunkNumber: Long, heading: String, identifier: String, sectionNumber: String): String = value
        .replace("%i", identifier)
        .replace("%h", heading)
        .replace("%s", sectionNumber)
        .replace("%n", String.format(Locale.ROOT, "%03d", chunkNumber))
        .filter { it != '/' && it != '\\' }

    fun toJson(): JsonPrimitive = JsonPrimitive(value)

    companion object {
        fun fromJson(value: JsonElement): PathTemplate {
            require(value is JsonPrimitive && value.isString) { "Expected a path template string" }
            return PathTemplate(value.content)
        }
    }
}
