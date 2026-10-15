// Text.Pandoc.Translations.Types, © 2017–2024 John MacFarlane, GPL-2.0-or-later.
// containers 0.7 Map Show/Semigroup/Monoid; original notices in licenses/.
package io.heapy.kotdoc.translations

import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.json.PrettyJson
import io.heapy.kotdoc.json.PrettyValue
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.parsing.ParserCharacters
import io.heapy.kotdoc.yaml.YamlFromJsonResult
import kotlinx.serialization.json.*
import java.math.BigInteger

/** Immutable term map. As upstream, construction through JSON strips each value. */
class Translations private constructor(private val values: Map<Term, String>) {
    fun lookupTerm(term: Term): String? = values[term]

    /** Map.union retains the left value, including an empty string. */
    operator fun plus(other: Translations): Translations = Translations(other.values + values)

    fun repeat(multiplier: BigInteger): Translations {
        require(multiplier.signum() >= 0) { "stimesIdempotentMonoid: negative multiplier" }
        return if (multiplier.signum() == 0) empty else this
    }
    fun repeat(multiplier: Long): Translations = repeat(BigInteger.valueOf(multiplier))

    fun show(precedence: Long = 0): String {
        val body = "Translations (fromList " + Term.entries.mapNotNull { term ->
            values[term]?.let { "(${term.name},${HaskellString.quote(it)})" }
        }.joinToString(",", "[", "]") + ")"
        return if (precedence > 10) "($body)" else body
    }
    override fun toString(): String = show()

    companion object {
        val empty = Translations(emptyMap())

        fun concatenate(values: Iterable<Translations>): Translations =
            values.fold(empty) { accumulated, next -> accumulated + next }

        /** Original FromJSON instance over the YAML decoder's exact Aeson Value. */
        fun fromValue(value: PrettyValue): YamlFromJsonResult<Translations> {
            fun failure(detail: String) = YamlFromJsonResult.Failure(CharacterStringChars.fromString("Error in $: $detail"))
            fun mismatch(expected: String, item: PrettyValue): String {
                val actual = when (item) {
                    PrettyValue.Null -> "Null"
                    is PrettyValue.Bool -> "Boolean"
                    is PrettyValue.Number -> "Number"
                    is PrettyValue.Text -> "String"
                    is PrettyValue.Array -> "Array"
                    is PrettyValue.Object -> "Object"
                }
                return "expected $expected, but encountered $actual"
            }
            if (value !is PrettyValue.Object) return failure(mismatch("Translations",value))
            val result = linkedMapOf<Term,String>()
            for (key in value.values.keys.sortedWith(PrettyJson.compare)) {
                val term = Term.named(key) ?: return failure("Invalid Term name ${HaskellString.quote(key)}")
                val item = value.values.getValue(key)
                if (item !is PrettyValue.Text) return failure(mismatch("String",item))
                result[term] = strip(item.value)
            }
            return YamlFromJsonResult.Success(Translations(result.toMap()))
        }

        fun fromJson(value: JsonElement): Translations {
            if (value !is JsonObject) throw typeMismatch("Translations", value)
            // M.toList fixes validation order even when the JSON object was inserted differently.
            val normalized = value.entries.associate { TextChars.fromString(it.key) to it.value }
            val result = linkedMapOf<Term, String>()
            for (key in normalized.keys.sortedWith(PrettyJson.compare)) {
                val term = Term.named(key) ?: throw invalidName(key)
                val item = normalized.getValue(key)
                if (item !is JsonPrimitive || !item.isString) throw typeMismatch("String", item)
                result[term] = strip(item.content)
            }
            return Translations(result.toMap())
        }
        private fun strip(value: String): String {
            val points = TextChars.fromString(value).codePoints().toArray()
            var start = 0; var end = points.size
            while (start < end && ParserCharacters.isSpace(points[start])) start++
            while (end > start && ParserCharacters.isSpace(points[end - 1])) end--
            return String(points,start,end-start)
        }
    }
}
