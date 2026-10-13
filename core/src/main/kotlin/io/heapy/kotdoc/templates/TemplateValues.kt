// Text.DocTemplates.Internal 0.11.1, Copyright (C) 2009-2019 John MacFarlane.
// BSD-3-Clause; see licenses/Doctemplates-BSD-3-Clause.txt.
package io.heapy.kotdoc.templates

import io.heapy.kotdoc.formats.HaskellDouble
import io.heapy.kotdoc.layout.*
import kotlinx.serialization.json.*

/** Context/Val conversions for a TemplateTarget representation. */
class TemplateValues<A : Any>(
    val layout: DocLayout<A>,
    val fromText: (String) -> A,
    val toText: (A) -> String,
) {
    val target = ToContext<A, A> { Val.SimpleVal(layout.literal(it)) }
    val document = ToContext<A, Doc<A>> { Val.SimpleVal(it) }
    val boolean = ToContext<A, Boolean> { Val.BoolVal(it) }
    val value = ToContext<A, Val<A>> { it }
    val context = ToContext<A, Context<A>> { Val.MapVal(it) }
    val json = ToContext<A, JsonElement> { fromJson(it) }

    val targetDecoder = FromContext<A, A> { v ->
        if (v is Val.SimpleVal) layout.render(null, v.doc) else null
    }
    val documentDecoder = FromContext<A, Doc<A>> { (it as? Val.SimpleVal)?.doc }
    val valueDecoder = FromContext<A, Val<A>> { it }

    fun <B> list(converter: ToContext<A, B>) = ToContext<A, List<B>> {
        Val.ListVal(it.map(converter::toVal))
    }
    fun <B> map(converter: ToContext<A, B>) = ToContext<A, Map<String, B>> {
        Val.MapVal(Context(it.mapValues { entry -> converter.toVal(entry.value) }))
    }
    fun <B : Any> listDecoder(decoder: FromContext<A, B>) = FromContext<A, List<B>> { v ->
        val values = if (v is Val.ListVal) v.values else listOf(v)
        val result = ArrayList<B>(values.size)
        for (item in values) result.add(decoder.fromVal(item) ?: return@FromContext null)
        result
    }

    /** JSON numbers intentionally become strings; this is not an identity codec. */
    fun fromJson(value: JsonElement): Val<A> = when (value) {
        JsonNull -> Val.NullVal
        is JsonObject -> Val.MapVal(Context(value.mapValues { fromJson(it.value) }))
        is JsonArray -> Val.ListVal(value.map(::fromJson))
        is JsonPrimitive -> when {
            value.isString -> target.toVal(fromText(value.content))
            value.booleanOrNull != null -> Val.BoolVal(value.boolean)
            else -> target.toVal(fromText(scientificText(value.content)))
        }
    }

    /** FromJSON Context rejects every non-object, unlike ToContext's empty fallback. */
    fun contextFromJson(value: JsonElement): Context<A> {
        val result = fromJson(value)
        require(result is Val.MapVal) { "Expecting MapVal" }
        return result.context
    }
    fun toJson(value: Val<A>): JsonElement = when (value) {
        Val.NullVal -> JsonNull
        is Val.BoolVal -> JsonPrimitive(value.value)
        is Val.SimpleVal -> JsonPrimitive(toText(layout.render(null, value.doc)))
        is Val.ListVal -> JsonArray(value.values.map(::toJson))
        is Val.MapVal -> toJson(value.context)
    }
    fun toJson(context: Context<A>) = JsonObject(context.entries.mapValues { toJson(it.value) })

    companion object {
        val Text = TemplateValues(DocLayout.Text, { it }, { it })
        val String = TemplateValues(DocLayout.String, CharacterStringChars::fromString, CharacterStringChars::build)

        private fun scientificText(text: kotlin.String): kotlin.String {
            // Scientific.floatingOrInteger checks integrality before converting to Double.
            // BigInteger is required: an integral JSON number is not limited to Int64.
            val number = text.toBigDecimal()
            val normalized = number.stripTrailingZeros()
            return if (normalized.scale() <= 0) number.toBigIntegerExact().toString()
            else HaskellDouble.show(number.toDouble())
        }
    }
}
