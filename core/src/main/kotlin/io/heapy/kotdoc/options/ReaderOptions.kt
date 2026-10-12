// Port of Text.Pandoc.Options, Copyright (C) 2012-2024 John MacFarlane.
// GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.options

import io.heapy.kotdoc.ast.UnicodeTextOrder
import kotlinx.serialization.json.*

/** Defaults of the library's ReaderOptions, which differ from application defaults. */
data class ReaderOptions(
    override val extensions: Extensions = Extensions.Empty,
    val standalone: Boolean = false,
    val columns: Long = 80,
    val tabStop: Long = 4,
    val indentedCodeClasses: List<String> = emptyList(),
    val abbreviations: Set<String> = DefaultAbbreviations,
    val defaultImageExtension: String = "",
    val trackChanges: TrackChanges = TrackChanges.AcceptChanges,
    val stripComments: Boolean = false,
    val typstInputs: List<Pair<String, String>> = emptyList(),
) : HasSyntaxExtensions {
    fun toJson(): JsonObject = buildJsonObject {
        put("extensions", extensions.toJson())
        put("standalone", standalone)
        put("columns", columns)
        put("tab-stop", tabStop)
        put("indented-code-classes", JsonArray(indentedCodeClasses.map(::JsonPrimitive)))
        put("abbreviations", JsonArray(abbreviations.sortedWith(UnicodeTextOrder).map(::JsonPrimitive)))
        put("default-image-extension", defaultImageExtension)
        put("track-changes", trackChanges.toJson())
        put("strip-comments", stripComments)
        put("typst-inputs", JsonArray(typstInputs.map { (key, value) -> JsonArray(listOf(JsonPrimitive(key), JsonPrimitive(value))) }))
    }

    companion object {
        val DefaultAbbreviations: Set<String> = java.util.Collections.unmodifiableSet(setOf(
            "Mr.", "Mrs.", "Ms.", "Capt.", "Dr.", "Prof.", "Gen.", "Gov.", "e.g.", "i.e.", "Sgt.", "St.",
            "vol.", "vs.", "Sen.", "Rep.", "Pres.", "Hon.", "Rev.", "Ph.D.", "M.D.", "M.A.", "p.", "pp.",
            "ch.", "sec.", "cf.", "cp.",
        ))

        /** Aeson-derived record JSON requires every field; unknown fields are ignored. */
        fun fromJson(value: JsonElement): ReaderOptions {
            require(value is JsonObject) { "ReaderOptions must be a JSON object" }
            fun field(name: String): JsonElement = value[name] ?: throw IllegalArgumentException("Missing ReaderOptions field: $name")
            fun string(v: JsonElement): String {
                require(v is JsonPrimitive && v.isString) { "Expected JSON string, found $v" }
                return v.content
            }
            fun array(v: JsonElement): JsonArray {
                require(v is JsonArray) { "Expected JSON array, found $v" }
                return v
            }
            fun strings(name: String): List<String> = array(field(name)).map(::string)
            fun bool(name: String): Boolean {
                val v = field(name)
                require(v is JsonPrimitive && !v.isString && v.booleanOrNull != null) { "Expected JSON boolean, found $v" }
                return v.boolean
            }
            fun integer(name: String): Long {
                val v = field(name)
                require(v is JsonPrimitive && !v.isString) { "Expected JSON integer, found $v" }
                return v.content.toBigDecimal().longValueExact()
            }
            return ReaderOptions(
                extensions = Extensions.fromJson(field("extensions")),
                standalone = bool("standalone"),
                columns = integer("columns"),
                tabStop = integer("tab-stop"),
                indentedCodeClasses = strings("indented-code-classes"),
                abbreviations = strings("abbreviations").toSet(),
                defaultImageExtension = string(field("default-image-extension")),
                trackChanges = TrackChanges.fromJson(field("track-changes")),
                stripComments = bool("strip-comments"),
                typstInputs = array(field("typst-inputs")).map {
                    val pair = array(it)
                    require(pair.size == 2) { "Expected a two-element pair, found $it" }
                    string(pair[0]) to string(pair[1])
                },
            )
        }
    }
}
