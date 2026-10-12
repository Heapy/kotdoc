// Port of Text.Pandoc.Extensions, Copyright (C) 2012-2024 John MacFarlane.
// GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.options

import com.ibm.icu.lang.UCharacter
import io.heapy.kotdoc.ast.UnicodeTextOrder
import kotlinx.serialization.json.*

sealed interface Extension : Comparable<Extension> {
    val wireName: String

    data class Builtin(val name: ExtensionName) : Extension {
        override val wireName: String get() = name.wireName
    }
    data class Custom(override val wireName: String) : Extension

    override fun compareTo(other: Extension): Int = when {
        this is Builtin && other is Builtin -> name.ordinal.compareTo(other.name.ordinal)
        this is Builtin -> -1
        other is Builtin -> 1
        else -> UnicodeTextOrder.compare(wireName, other.wireName)
    }

    fun toJson(): JsonPrimitive = JsonPrimitive(wireName)

    companion object {
        private val builtins = ExtensionName.entries.associateBy { it.wireName }

        /** The exact lhs alias precedes Haskell Read's trailing whitespace handling. */
        fun read(name: String): Extension {
            if (name == "lhs") return Builtin(ExtensionName.LiterateHaskell)
            val token = name.trimEnd { it in '\t'..'\r' || UCharacter.getType(it.code) == Character.SPACE_SEPARATOR.toInt() }
            return builtins[token]?.let(::Builtin) ?: Custom(name)
        }

        fun fromJson(value: JsonElement): Extension {
            require(value is JsonPrimitive && value.isString) { "Extension must be a JSON string" }
            return read(value.content)
        }
    }
}

/** Immutable set ordered like Haskell's Extension constructors, then custom Text. */
class Extensions private constructor(private val values: Set<Extension>) : Iterable<Extension>, Comparable<Extensions> {
    val size: Int get() = values.size
    fun isEmpty(): Boolean = values.isEmpty()
    operator fun contains(extension: Extension): Boolean = extension in values
    operator fun contains(extension: ExtensionName): Boolean = Extension.Builtin(extension) in values
    override fun iterator(): Iterator<Extension> = values.toList().iterator()
    fun toList(): List<Extension> = values.toList()
    fun names(): List<String> = values.map { it.wireName }
    fun sortedNames(): List<String> = names().sortedWith(UnicodeTextOrder)
    operator fun plus(other: Extensions): Extensions = from(values + other.values)
    operator fun minus(other: Extensions): Extensions = from(values - other.values)
    fun enable(extension: Extension): Extensions = from(values + extension)
    fun disable(extension: Extension): Extensions = from(values - extension)
    fun toJson(): JsonArray = JsonArray(values.map { it.toJson() })
    override fun equals(other: Any?): Boolean = other is Extensions && values == other.values
    override fun hashCode(): Int = values.hashCode()
    override fun toString(): String = names().toString()
    override fun compareTo(other: Extensions): Int {
        val a = iterator()
        val b = other.iterator()
        while (a.hasNext() && b.hasNext()) {
            val comparison = a.next().compareTo(b.next())
            if (comparison != 0) return comparison
        }
        return a.hasNext().compareTo(b.hasNext())
    }

    companion object {
        val Empty: Extensions = Extensions(emptySet())
        fun from(values: Iterable<Extension>): Extensions = Extensions(values.toSortedSet().toSet())
        fun of(vararg values: ExtensionName): Extensions = from(values.map(Extension::Builtin))
        fun read(names: Iterable<String>): Extensions = from(names.map(Extension::read))
        fun fromJson(value: JsonElement): Extensions {
            require(value is JsonArray) { "Extensions must be a JSON array" }
            return from(value.map(Extension::fromJson))
        }
    }
}

/** Complete declarative registry of the pinned Pandoc; availability of readers is separate. */
object ExtensionRegistry {
    private val tables: JsonObject = Json.parseToJsonElement(checkNotNull(
        javaClass.getResource("/io/heapy/kotdoc/extensions.json")
    ).readText()).jsonObject
    private fun table(name: String): Map<String, Extensions> = tables.getValue(name).jsonObject
        .mapValues { Extensions.fromJson(it.value) }
    private val defaults = table("defaults")
    private val supported = table("supported")
    val presets: Map<String, Extensions> = java.util.Collections.unmodifiableMap(table("presets"))
    val pandocExtensions: Extensions get() = presets.getValue("pandocExtensions")
    val plainExtensions: Extensions get() = presets.getValue("plainExtensions")
    val strictExtensions: Extensions get() = presets.getValue("strictExtensions")
    val phpMarkdownExtraExtensions: Extensions get() = presets.getValue("phpMarkdownExtraExtensions")
    val githubMarkdownExtensions: Extensions get() = presets.getValue("githubMarkdownExtensions")
    val multimarkdownExtensions: Extensions get() = presets.getValue("multimarkdownExtensions")
    fun defaults(format: String): Extensions = defaults[format] ?: defaults.getValue("_")
    fun supported(format: String): Extensions = supported[format] ?: supported.getValue("_")
    fun config(format: String): ExtensionsConfig = ExtensionsConfig(defaults(format), supported(format))
    internal val fileTypes: Map<String, String> = tables.getValue("fileTypes").jsonObject.mapValues { it.value.jsonPrimitive.content }
}
