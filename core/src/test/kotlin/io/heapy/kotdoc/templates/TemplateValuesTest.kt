package io.heapy.kotdoc.templates

import io.heapy.kotdoc.layout.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.streams.asStream
import kotlin.test.*

class TemplateValuesTest {
    private val codec = TemplateValues.Text
    private val layout = codec.layout
    private fun JsonElement.s() = jsonPrimitive.content
    private fun JsonElement.n() = jsonPrimitive.long
    private fun doc(v: JsonElement): Doc<String> {
        if (v is JsonPrimitive && v.isString) return layout.text(v.content)
        val a = v.jsonArray
        fun d(i: Int) = doc(a[i])
        fun parts(v: JsonElement) = Attributed(v.jsonArray.map {
            val p = it.jsonArray
            AttributedPart(p[0].takeUnless { it == JsonNull }?.s(), Font.Base, p[1].s())
        })
        return with(layout) { when (a[0].s()) {
            "empty" -> empty
            "raw" -> Doc.Text(a[1].n(), a[2].s())
            "literal" -> text(a[1].s())
            "concat" -> Doc.Concat(d(1), d(2))
            "hsep" -> hsep(a[1].jsonArray.map(::doc))
            "vcat" -> vcat(a[1].jsonArray.map(::doc))
            "prefix" -> prefixed(a[1].s(), d(2))
            "bold" -> bold(d(1))
            "link" -> link(a[1].s(), d(2))
            "before" -> beforeNonBlank(d(1))
            "after" -> afterBreak(a[1].s())
            "block" -> Doc.Block(a[1].n(), a[2].jsonArray.map(::parts))
            "cooked" -> Doc.CookedText(a[1].n(), parts(a[2]))
            else -> error(v)
        } }
    }
    private fun value(v: JsonElement): Val<String> {
        val a = v.jsonArray
        return when (a[0].s()) {
            "json" -> codec.fromJson(a[1])
            "simple" -> Val.SimpleVal(doc(a[1]))
            "list" -> Val.ListVal(a[1].jsonArray.map(::value))
            "map" -> Val.MapVal(Context(a[1].jsonArray.associate { it.jsonArray[0].s() to value(it.jsonArray[1]) }))
            "bool" -> Val.BoolVal(a[1].jsonPrimitive.boolean)
            "null" -> Val.NullVal
            else -> error(v)
        }
    }
    private fun nullableText(v: String?) = v?.let(::JsonPrimitive) ?: JsonNull
    private fun strings(v: List<String>?) = v?.let { JsonArray(it.map(::JsonPrimitive)) } ?: JsonNull
    private fun nestedStrings(v: List<List<String>>?) = v?.let { JsonArray(it.map(::strings)) } ?: JsonNull
    private fun values(v: Val<String>): JsonElement = JsonArray(v.foldValues(emptyList<String>()) { xs, x -> xs + x }.map(::JsonPrimitive))
    private fun reverse(s: String) = buildString { s.codePoints().toArray().reversed().forEach(::appendCodePoint) }
    private fun renders(v: Val<String>): JsonElement = when (v) {
        is Val.SimpleVal -> JsonArray(listOf(null, 3L, 10L).map { w ->
            JsonArray(listOf(layout.render(w, v.doc), layout.renderANSI(w, v.doc)).map(::JsonPrimitive))
        })
        is Val.ListVal -> JsonArray(v.values.map(::renders))
        is Val.MapVal -> JsonObject(v.context.entries.mapValues { renders(it.value) })
        else -> JsonNull
    }
    private fun describe(v: Val<String>): JsonElement {
        val mapped = v.mapValues(::reverse)
        val effects = mutableListOf<String>()
        val traversed = v.mapValues { text -> val i = effects.size; effects.add(text); "$i:$text" }
        return buildJsonObject {
            put("json", codec.toJson(v)); put("values", values(v))
            put("text", nullableText(codec.targetDecoder.fromVal(v)))
            put("doc", nullableText(codec.documentDecoder.fromVal(v)?.let { layout.render(null, it) }))
            put("list", strings(codec.listDecoder(codec.targetDecoder).fromVal(v)))
            put("nestedList", nestedStrings(codec.listDecoder(codec.listDecoder(codec.targetDecoder)).fromVal(v)))
            put("context", codec.toJson(codec.value.toContext(v)))
            put("mappedJson", codec.toJson(mapped)); put("mappedValues", values(mapped))
            put("effects", strings(effects)); put("traversedJson", codec.toJson(traversed))
            put("traversedValues", values(traversed)); put("renders", renders(v))
            put("mappedRenders", renders(mapped)); put("traversedRenders", renders(traversed))
        }
    }
    private fun actual(op: String, input: JsonElement): JsonElement = when (op) {
        "value" -> describe(value(input))
        "json-text" -> actual("json", Json.parseToJsonElement(input.s()))
        "json" -> buildJsonObject {
            put("value", describe(codec.json.toVal(input)))
            put("toContext", codec.toJson(codec.json.toContext(input)))
            put("decodeContext", try {
                buildJsonObject { put("success", codec.toJson(codec.contextFromJson(input))) }
            } catch (e: IllegalArgumentException) {
                buildJsonObject { put("error", e.message) }
            })
        }
        "union" -> {
            val a = input.jsonArray
            val c = codec.value.toContext(value(a[0])) + codec.value.toContext(value(a[1]))
            val collected = mutableListOf<String>(); c.forEachValue(collected::add)
            buildJsonObject {
                put("json", codec.toJson(c)); put("keys", strings(c.entries.keys.toList()))
                put("values", strings(collected)); put("lookup", JsonArray((c.entries.keys.toList() + listOf("missing")).map { k ->
                    buildJsonObject {
                        put("key", k); put("present", c.entries.containsKey(k))
                        put("val", c.lookup(k, codec.valueDecoder)?.let(codec::toJson) ?: JsonNull)
                        put("text", nullableText(codec.targetDecoder.lookupContext(k, c)))
                        put("list", strings(codec.listDecoder(codec.targetDecoder).lookupContext(k, c)))
                    }
                }))
            }
        }
        "conversion" -> {
            val a = input.jsonArray
            when (a[0].s()) {
                "text" -> describe(codec.target.toVal(a[1].s()))
                "string" -> TemplateValues.String.run { toJson(target.toVal(fromText(a[1].s()))) }
                "texts" -> describe(codec.list(codec.target).toVal(a[1].jsonArray.map { it.s() }))
                "strings" -> TemplateValues.String.run { toJson(list(target).toVal(a[1].jsonArray.map { fromText(it.s()) })) }
                "bool" -> describe(codec.boolean.toVal(a[1].jsonPrimitive.boolean))
                "map" -> describe(codec.map(codec.target).toVal(a[1].jsonObject.mapValues { it.value.s() }))
                else -> error(input)
            }
        }
        else -> error(op)
    }

    @TestFactory
    fun originalValueInstances(): Stream<DynamicTest> {
        val input = javaClass.getResourceAsStream("/templates/values.jsonl.gz") ?: error("Missing original corpus")
        val reader = GZIPInputStream(input).bufferedReader()
        reader.readLine() // Source manifest is independently checked by the importer in CI.
        return reader.lineSequence().mapIndexed { index, line ->
            DynamicTest.dynamicTest("original Context/Val $index") {
                val c = Json.parseToJsonElement(line).jsonObject
                assertEquals(c.getValue("expected"), actual(c.getValue("op").s(), c.getValue("input")),
                    "Original input: ${c.getValue("input")}")
            }
        }.asStream().onClose(reader::close)
    }

    @Test
    fun deepFiniteValueTraversal() {
        var value: Val<String> = codec.target.toVal("leaf")
        repeat(20_000) { value = if (it % 2 == 0) Val.ListVal(listOf(value)) else Val.MapVal(Context(mapOf("x" to value))) }
        val mapped = value.mapValues { "[$it]" }
        assertEquals(listOf("[leaf]"), mapped.foldValues(emptyList<String>()) { xs, x -> xs + x })
        assertSame(Val.NullVal, codec.valueDecoder.fromVal(Val.NullVal))
        assertNull(Context<String>().lookup("missing", codec.valueDecoder))
    }
}
