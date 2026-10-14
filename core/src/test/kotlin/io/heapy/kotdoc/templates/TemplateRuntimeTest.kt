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

class TemplateRuntimeTest {
    private val codec = TemplateValues.Text
    private val layout = DocLayout.Text
    private val renderer = TemplateRenderer.Text
    private fun JsonElement.s() = jsonPrimitive.content
    private fun JsonElement.n() = jsonPrimitive.long
    private fun j(vararg values: JsonElement) = JsonArray(values.toList())
    private fun tag(name: String, vararg fields: JsonElement) = j(JsonPrimitive(name), *fields)
    private fun strings(xs: List<String>) = JsonArray(xs.map(::JsonPrimitive))
    private fun part(v: JsonElement): AttributedPart<String> {
        val a = v.jsonArray
        val link = a[0].takeUnless { it == JsonNull }?.s()
        if (a.size == 2) return AttributedPart(link, Font.Base, a[1].s())
        val f = a[1].jsonArray
        fun color(i: Int) = f[i].takeUnless { it == JsonNull }?.jsonPrimitive?.int?.let { Color8.entries[it] }
        return AttributedPart(link, Font(f[0].jsonPrimitive.boolean, f[1].jsonPrimitive.boolean,
            Underline.entries[f[2].jsonPrimitive.int], f[3].jsonPrimitive.boolean, color(4), color(5),
            f[6].takeUnless { it == JsonNull }?.s()), a[2].s())
    }
    private fun attributed(v: JsonElement) = Attributed(v.jsonArray.map(::part))
    private fun style(v: JsonElement): StyleRequest {
        val a = v.jsonArray
        return when (a[0].s()) {
            "weight" -> StyleRequest.Weight(a[1].jsonPrimitive.boolean)
            "shape" -> StyleRequest.Shape(a[1].jsonPrimitive.boolean)
            "strikeout" -> StyleRequest.Strikeout(a[1].jsonPrimitive.boolean)
            "underline" -> StyleRequest.Underlined(Underline.entries[a[1].jsonPrimitive.int])
            "fg" -> StyleRequest.Foreground(a[1].takeUnless { it == JsonNull }?.jsonPrimitive?.int?.let { Color8.entries[it] })
            "bg" -> StyleRequest.Background(a[1].takeUnless { it == JsonNull }?.jsonPrimitive?.int?.let { Color8.entries[it] })
            else -> error(v)
        }
    }
    private fun styleJson(s: StyleRequest) = when (s) {
        is StyleRequest.Weight -> tag("weight", JsonPrimitive(s.bold))
        is StyleRequest.Shape -> tag("shape", JsonPrimitive(s.italic))
        is StyleRequest.Strikeout -> tag("strikeout", JsonPrimitive(s.struck))
        is StyleRequest.Underlined -> tag("underline", JsonPrimitive(s.underline.ordinal))
        is StyleRequest.Foreground -> tag("fg", s.color?.let { JsonPrimitive(it.ordinal) } ?: JsonNull)
        is StyleRequest.Background -> tag("bg", s.color?.let { JsonPrimitive(it.ordinal) } ?: JsonNull)
    }
    private fun attributedJson(a: Attributed<String>) = JsonArray(a.parts.map { p ->
        val f = p.font
        j(p.link?.let(::JsonPrimitive) ?: JsonNull,
            j(JsonPrimitive(f.bold), JsonPrimitive(f.italic), JsonPrimitive(f.underline.ordinal), JsonPrimitive(f.strikeout),
                f.foreground?.let { JsonPrimitive(it.ordinal) } ?: JsonNull, f.background?.let { JsonPrimitive(it.ordinal) } ?: JsonNull,
                f.link?.let(::JsonPrimitive) ?: JsonNull), JsonPrimitive(p.value))
    })
    private fun doc(v: JsonElement): Doc<String> {
        if (v is JsonPrimitive && v.isString) return layout.text(v.content)
        val a = v.jsonArray
        fun d(i: Int) = doc(a[i])
        return with(layout) { when (a[0].s()) {
            "empty" -> empty; "space" -> space; "cr" -> cr; "newline" -> Doc.NewLine
            "blanklines" -> Doc.BlankLines(a[1].n()); "vfill" -> Doc.VFill(a[1].n(), a[2].s())
            "raw", "text" -> Doc.Text(a[1].n(), a[2].s()); "literal" -> text(a[1].s())
            "concat" -> Doc.Concat(d(1), d(2)); "hsep" -> hsep(a[1].jsonArray.map(::doc))
            "vcat" -> vcat(a[1].jsonArray.map(::doc)); "prefix" -> Doc.Prefixed(a[1].s(), d(2))
            "bold" -> bold(d(1)); "link" -> Doc.Linked(a[1].s(), d(2)); "before" -> Doc.BeforeNonBlank(d(1))
            "after" -> Doc.AfterBreak(a[1].s()); "flush" -> Doc.Flush(d(1)); "styled" -> Doc.Styled(style(a[1]), d(2))
            "block" -> Doc.Block(a[1].n(), a[2].jsonArray.map(::attributed))
            "cooked" -> Doc.CookedText(a[1].n(), attributed(a[2]))
            else -> error(v)
        } }
    }
    private fun docJson(d: Doc<String>): JsonElement = when (d) {
        is Doc.Text -> tag("text", JsonPrimitive(d.width), JsonPrimitive(d.value))
        is Doc.Block -> tag("block", JsonPrimitive(d.width), JsonArray(d.lines.map(::attributedJson)))
        is Doc.VFill -> tag("vfill", JsonPrimitive(d.width), JsonPrimitive(d.value))
        is Doc.CookedText -> tag("cooked", JsonPrimitive(d.width), attributedJson(d.value))
        is Doc.Prefixed -> tag("prefix", JsonPrimitive(d.prefix), docJson(d.doc))
        is Doc.BeforeNonBlank -> tag("before", docJson(d.doc)); is Doc.Flush -> tag("flush", docJson(d.doc))
        Doc.BreakingSpace -> tag("space"); is Doc.AfterBreak -> tag("after", JsonPrimitive(d.value))
        Doc.CarriageReturn -> tag("cr"); Doc.NewLine -> tag("newline"); is Doc.BlankLines -> tag("blanklines", JsonPrimitive(d.count))
        is Doc.Concat -> tag("concat", docJson(d.left), docJson(d.right))
        is Doc.Styled -> tag("styled", styleJson(d.style), docJson(d.doc))
        is Doc.Linked -> tag("link", JsonPrimitive(d.link), docJson(d.doc)); Doc.Empty -> tag("empty")
    }
    private fun value(v: JsonElement): Val<String> {
        val a = v.jsonArray
        return when (a[0].s()) {
            "json" -> codec.fromJson(a[1]); "simple" -> Val.SimpleVal(doc(a[1]))
            "list" -> Val.ListVal(a[1].jsonArray.map(::value))
            "map" -> Val.MapVal(Context(a[1].jsonArray.associate { it.jsonArray[0].s() to value(it.jsonArray[1]) }))
            "bool" -> Val.BoolVal(a[1].jsonPrimitive.boolean); "null" -> Val.NullVal
            else -> error(v)
        }
    }
    private fun pipe(v: JsonElement): Pipe {
        if (v is JsonArray) return Pipe.Block(Alignment.entries[v[1].jsonPrimitive.int], v[2].n(), Border(v[3].s(), v[4].s()))
        return when (v.s()) {
            "pairs" -> Pipe.ToPairs; "uppercase" -> Pipe.ToUppercase; "lowercase" -> Pipe.ToLowercase; "length" -> Pipe.ToLength
            "reverse" -> Pipe.Reverse; "first" -> Pipe.FirstItem; "last" -> Pipe.LastItem; "rest" -> Pipe.Rest
            "allbutlast" -> Pipe.AllButLast; "chomp" -> Pipe.Chomp; "alpha" -> Pipe.ToAlpha; "roman" -> Pipe.ToRoman
            "nowrap" -> Pipe.NoWrap; else -> error(v)
        }
    }
    private fun pipeJson(p: Pipe): JsonElement = when (p) {
        Pipe.ToPairs -> JsonPrimitive("pairs"); Pipe.ToUppercase -> JsonPrimitive("uppercase"); Pipe.ToLowercase -> JsonPrimitive("lowercase")
        Pipe.ToLength -> JsonPrimitive("length"); Pipe.Reverse -> JsonPrimitive("reverse"); Pipe.FirstItem -> JsonPrimitive("first")
        Pipe.LastItem -> JsonPrimitive("last"); Pipe.Rest -> JsonPrimitive("rest"); Pipe.AllButLast -> JsonPrimitive("allbutlast")
        Pipe.Chomp -> JsonPrimitive("chomp"); Pipe.ToAlpha -> JsonPrimitive("alpha"); Pipe.ToRoman -> JsonPrimitive("roman")
        Pipe.NoWrap -> JsonPrimitive("nowrap")
        is Pipe.Block -> tag("block", JsonPrimitive(p.alignment.ordinal), JsonPrimitive(p.width), JsonPrimitive(p.border.left), JsonPrimitive(p.border.right))
    }
    private fun variable(v: JsonElement) = Variable(v.jsonArray[0].jsonArray.map { it.s() }, v.jsonArray[1].jsonArray.map(::pipe))
    private fun variableJson(v: Variable) = j(strings(v.parts), JsonArray(v.pipes.map(::pipeJson)))
    private fun template(v: JsonElement): Template<String> {
        val a = v.jsonArray
        fun t(i: Int) = template(a[i])
        return when (a[0].s()) {
            "empty" -> Template.Empty; "literal" -> Template.Literal(doc(a[1]))
            "interpolate" -> Template.Interpolate(variable(a[1]))
            "conditional" -> Template.Conditional(variable(a[1]), t(2), t(3))
            "iterate" -> Template.Iterate(variable(a[1]), t(2), t(3))
            "nested" -> Template.Nested(t(1)); "partial" -> Template.Partial(a[1].jsonArray.map(::pipe), t(2))
            "concat" -> Template.Concat(t(1), t(2)); "append" -> t(1) + t(2)
            else -> error(v)
        }
    }
    private fun templateJson(t: Template<String>): JsonElement = when (t) {
        Template.Empty -> tag("empty"); is Template.Literal -> tag("literal", docJson(t.doc))
        is Template.Interpolate -> tag("interpolate", variableJson(t.variable))
        is Template.Conditional -> tag("conditional", variableJson(t.variable), templateJson(t.yes), templateJson(t.no))
        is Template.Iterate -> tag("iterate", variableJson(t.variable), templateJson(t.body), templateJson(t.separator))
        is Template.Nested -> tag("nested", templateJson(t.template))
        is Template.Partial -> tag("partial", JsonArray(t.pipes.map(::pipeJson)), templateJson(t.template))
        is Template.Concat -> tag("concat", templateJson(t.left), templateJson(t.right))
    }
    private fun <A : Any> observe(d: Doc<A>, codec: TemplateValues<A>) = buildJsonObject {
        val l = codec.layout
        put("doc", docJson(d.mapValues(codec.toText))); put("offset", l.offset(d)); put("minOffset", l.minOffset(d)); put("height", l.height(d))
        put("columns", JsonArray(listOf(-9L, 0L, 17L).map { JsonPrimitive(l.updateColumn(d, it)) }))
        put("renders", JsonArray(listOf(null, 0L, 1L, 3L, 10L, 80L).map { w ->
            strings(listOf(codec.toText(l.render(w, d)), l.renderANSI(w, d)))
        }))
    }
    private fun <A : Any> runtime(t: Template<A>, ctx: Context<A>, codec: TemplateValues<A>): JsonElement {
        val engine = TemplateRenderer(codec)
        val mapped = t.mapValues { s -> codec.fromText(buildString {
            codec.toText(s).codePoints().toArray().reversed().forEach(::appendCodePoint)
        }) }
        val effects = mutableListOf<String>()
        val traversed = t.mapValues { s -> val i = effects.size; effects.add(codec.toText(s)); codec.fromText("$i:${codec.toText(s)}") }
        fun values(t: Template<A>) = strings(t.foldValues(emptyList<String>()) { xs, s -> xs + codec.toText(s) })
        return buildJsonObject {
            put("template", templateJson(t.mapValues(codec.toText))); put("variables", strings(t.getVariables())); put("values", values(t))
            put("rendered", observe(engine.render(t, ctx), codec)); put("mappedValues", values(mapped)); put("mapped", observe(engine.render(mapped, ctx), codec))
            put("effects", strings(effects)); put("traversedValues", values(traversed)); put("traversed", observe(engine.render(traversed, ctx), codec))
        }
    }
    internal fun <A : Any> context(context: JsonElement, codec: TemplateValues<A>): Context<A> =
        codec.value.toContext(value(context).mapValues(codec.fromText))
    internal fun <A : Any> compiledTree(t: Template<A>, codec: TemplateValues<A>): JsonElement = buildJsonObject {
        put("template", templateJson(t.mapValues(codec.toText))); put("variables", strings(t.getVariables()))
    }
    private fun actual(op: String, input: JsonElement): JsonElement = when (op) {
        "runtime", "runtime-string" -> {
            val t = template(input.jsonArray[0]); val ctx = codec.value.toContext(value(input.jsonArray[1]))
            if (op == "runtime") runtime(t, ctx, codec) else runtime(t.mapValues(CharacterStringChars::fromString),
                ctx.mapValues(CharacterStringChars::fromString), TemplateValues.String)
        }
        "variable" -> {
            val a = variable(input.jsonArray[0]); val b = variable(input.jsonArray[1])
            buildJsonObject { put("append", variableJson(a + b)); put("order", order(a.compareTo(b))) }
        }
        "pipe-order" -> {
            val ps = input.jsonArray.map(::pipe)
            buildJsonObject {
                put("sorted", JsonArray(ps.sorted().map(::pipeJson)))
                put("orders", JsonArray(ps.map { a -> strings(ps.map { b -> order(a.compareTo(b)) }) }))
            }
        }
        else -> error(op)
    }
    private fun order(n: Int) = if (n < 0) "LT" else if (n > 0) "GT" else "EQ"

    @TestFactory
    fun originalTemplateRuntime(): Stream<DynamicTest> {
        val input = javaClass.getResourceAsStream("/templates/runtime.jsonl.gz") ?: error("Missing runtime corpus")
        val reader = GZIPInputStream(input).bufferedReader(); reader.readLine()
        return reader.lineSequence().mapIndexed { index, line ->
            DynamicTest.dynamicTest("original template runtime $index") {
                val c = Json.parseToJsonElement(line).jsonObject
                assertEquals(c.getValue("expected"), actual(c.getValue("op").s(), c.getValue("input")),
                    "Original input: ${c.getValue("input")}")
            }
        }.asStream().onClose(reader::close)
    }
    @Test
    fun deepFiniteTemplateTraversalAndRendering() {
        var t: Template<String> = Template.Interpolate(Variable(listOf("x")))
        repeat(20_000) { t = Template.Concat(t, Template.Literal(layout.text("z"))) }
        assertEquals(listOf("x"), t.getVariables())
        var visits = 0
        val mapped = t.mapValues { visits++; it.uppercase() }
        assertEquals(20_000, visits)
        assertEquals(20_000, mapped.foldValues(0) { n, _ -> n + 1 })
        val output = renderer.render(mapped, mapOf("x" to "start"), codec.map(codec.target))
        assertEquals("start" + "Z".repeat(20_000), layout.render(null, output))
        var nested: Template<String> = Template.Literal(layout.text("leaf"))
        repeat(20_000) { nested = Template.Nested(nested) }
        assertEquals("leaf", layout.render(null, renderer.render(nested, Context())))
    }
}
