package io.heapy.kotdoc.templates

import io.heapy.kotdoc.layout.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.*

class TemplateComparisonTest {
    private val codec = TemplateValues.Text
    private val layout = DocLayout.Text
    private fun JsonElement.s() = TextChars.fromString(jsonPrimitive.content)
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
        if (v is JsonPrimitive && v.isString) return layout.literal(v.s())
        val a = v.jsonArray
        fun d(i: Int) = doc(a[i])
        return with(layout) { when (a[0].s()) {
            "empty" -> empty; "space" -> space; "cr" -> cr; "newline" -> Doc.NewLine
            "blanklines" -> Doc.BlankLines(a[1].n()); "vfill" -> Doc.VFill(a[1].n(), a[2].s())
            "raw", "text" -> Doc.Text(a[1].n(), a[2].s()); "literal" -> literal(a[1].s())
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

    private class ProbeException : RuntimeException("forced target")
    private val probe = object : TargetComparison<String> {
        private fun check(a: String,b: String) { if (a == "!" || b == "!" || a == "L!" || b == "R!") throw ProbeException() }
        override fun equal(left: String,right: String): Boolean { check(left,right);return left == right }
        override fun compare(left: String,right: String): Int { check(left,right);return TargetComparison.Text.compare(left,right) }
    }
    private fun result(value: () -> JsonElement): JsonElement = try { value() } catch (e: ProbeException) { buildJsonObject { put("error",e.message) } }
    private fun report(compare: () -> Int,equal: () -> Boolean,notEqual: () -> Boolean,less: () -> Boolean,le: () -> Boolean,
                       greater: () -> Boolean,ge: () -> Boolean,min: () -> JsonElement,max: () -> JsonElement): JsonObject = buildJsonObject {
        put("compare",result { JsonPrimitive(when (compare().compareTo(0)) { -1 -> "LT"; 1 -> "GT"; else -> "EQ" }) })
        put("equal",result { JsonPrimitive(equal()) });put("notEqual",result { JsonPrimitive(notEqual()) });put("less",result { JsonPrimitive(less()) });put("lessEqual",result { JsonPrimitive(le()) })
        put("greater",result { JsonPrimitive(greater()) });put("greaterEqual",result { JsonPrimitive(ge()) })
        put("minimum",result(min));put("maximum",result(max))
    }
    private fun fontJson(f: Font) = j(JsonPrimitive(f.bold),JsonPrimitive(f.italic),JsonPrimitive(f.underline.ordinal),JsonPrimitive(f.strikeout),
        f.foreground?.let { JsonPrimitive(it.ordinal) } ?: JsonNull,f.background?.let { JsonPrimitive(it.ordinal) } ?: JsonNull,f.link?.let(::JsonPrimitive) ?: JsonNull)
    private fun <A> typed(c: JsonObject,ops: TemplateComparison<A>,fromText: (String)->A,toText: (A)->String): JsonElement {
        val x=c.getValue("input").jsonArray
        fun p(v: JsonElement): AttributedPart<A> { val p=part(v);return AttributedPart(p.link,p.font,fromText(p.value)) }
        fun seq(v: JsonElement)=Attributed(v.jsonArray.map(::p))
        fun enc(p: AttributedPart<A>)=attributedJson(Attributed(listOf(AttributedPart(p.link,p.font,toText(p.value)))))
        fun enc(s: Attributed<A>)=attributedJson(Attributed(s.parts.map { AttributedPart(it.link,it.font,toText(it.value)) }))
        return when (c.getValue("op").s()) {
            "doc" -> {
                val a=doc(x[0]).mapValues(fromText);val b=doc(x[1]).mapValues(fromText)
                report({ops.compare(a,b)},{ops.equal(a,b)},{ops.notEqual(a,b)},{ops.less(a,b)},{ops.lessEqual(a,b)},{ops.greater(a,b)},{ops.greaterEqual(a,b)},
                    {docJson(ops.minimum(a,b).mapValues(toText))},{docJson(ops.maximum(a,b).mapValues(toText))})
            }
            "template" -> {
                val a=template(x[0]).mapValues(fromText);val b=template(x[1]).mapValues(fromText)
                report({ops.compare(a,b)},{ops.equal(a,b)},{ops.notEqual(a,b)},{ops.less(a,b)},{ops.lessEqual(a,b)},{ops.greater(a,b)},{ops.greaterEqual(a,b)},
                    {templateJson(ops.minimum(a,b).mapValues(toText))},{templateJson(ops.maximum(a,b).mapValues(toText))})
            }
            "part" -> {
                val a=p(x[0]);val b=p(x[1])
                report({ops.compare(a,b)},{ops.equal(a,b)},{ops.notEqual(a,b)},{ops.less(a,b)},{ops.lessEqual(a,b)},{ops.greater(a,b)},{ops.greaterEqual(a,b)},
                    {enc(ops.minimum(a,b))},{enc(ops.maximum(a,b))})
            }
            "attributed" -> {
                val a=seq(x[0]);val b=seq(x[1])
                report({ops.compare(a,b)},{ops.equal(a,b)},{ops.notEqual(a,b)},{ops.less(a,b)},{ops.lessEqual(a,b)},{ops.greater(a,b)},{ops.greaterEqual(a,b)},
                    {enc(ops.minimum(a,b))},{enc(ops.maximum(a,b))})
            }
            else -> error(c)
        }
    }
    private fun observe(c: JsonObject): JsonElement {
        val op=c.getValue("op").s()
        if (op in setOf("doc","template","part","attributed")) return when (c.getValue("target").s()) {
            "text" -> typed(c,TemplateComparison.Text,{it},{it})
            "double" -> typed(c,TemplateComparison.Double,{Double.fromBits(it.toULong().toLong())},{it.toRawBits().toULong().toString()})
            "int64" -> typed(c,TemplateComparison.Int64,String::toLong,Long::toString)
            "string-points" -> typed(c,TemplateComparison.String,{CharacterString(Json.parseToJsonElement(it).jsonArray.map { it.jsonPrimitive.int }.toIntArray())},
                {it.points().joinToString(",","[","]")})
            "probe" -> typed(c,TemplateComparison(probe),{it},{it})
            else -> error(c)
        }
        val x=c.getValue("input").jsonArray
        val ops=TemplateComparison.Text
        return when (op) {
            "font" -> {
                fun parse(it: JsonElement): Font = part(j(JsonNull,it,JsonPrimitive(""))).font
                fun enc(it: Font): JsonElement = fontJson(it)
                val a=parse(x[0]);val b=parse(x[1])
                report({ops.compare(a,b)},{ops.equal(a,b)},{ops.notEqual(a,b)},{ops.less(a,b)},{ops.lessEqual(a,b)},{ops.greater(a,b)},{ops.greaterEqual(a,b)},
                    {enc(ops.minimum(a,b))},{enc(ops.maximum(a,b))})
            }
            "style" -> {
                fun parse(it: JsonElement): StyleRequest = style(it)
                fun enc(it: StyleRequest): JsonElement = styleJson(it)
                val a=parse(x[0]);val b=parse(x[1])
                report({ops.compare(a,b)},{ops.equal(a,b)},{ops.notEqual(a,b)},{ops.less(a,b)},{ops.lessEqual(a,b)},{ops.greater(a,b)},{ops.greaterEqual(a,b)},
                    {enc(ops.minimum(a,b))},{enc(ops.maximum(a,b))})
            }
            "variable" -> {
                fun parse(it: JsonElement): Variable = variable(it)
                fun enc(it: Variable): JsonElement = variableJson(it)
                val a=parse(x[0]);val b=parse(x[1])
                report({ops.compare(a,b)},{ops.equal(a,b)},{ops.notEqual(a,b)},{ops.less(a,b)},{ops.lessEqual(a,b)},{ops.greater(a,b)},{ops.greaterEqual(a,b)},
                    {enc(ops.minimum(a,b))},{enc(ops.maximum(a,b))})
            }
            "pipe" -> {
                fun parse(it: JsonElement): Pipe = pipe(it)
                fun enc(it: Pipe): JsonElement = pipeJson(it)
                val a=parse(x[0]);val b=parse(x[1])
                report({ops.compare(a,b)},{ops.equal(a,b)},{ops.notEqual(a,b)},{ops.less(a,b)},{ops.lessEqual(a,b)},{ops.greater(a,b)},{ops.greaterEqual(a,b)},
                    {enc(ops.minimum(a,b))},{enc(ops.maximum(a,b))})
            }
            "border" -> {
                fun parse(it: JsonElement): Border = Border(it.jsonArray[0].s(),it.jsonArray[1].s())
                fun enc(it: Border): JsonElement = j(JsonPrimitive(it.left),JsonPrimitive(it.right))
                val a=parse(x[0]);val b=parse(x[1])
                report({ops.compare(a,b)},{ops.equal(a,b)},{ops.notEqual(a,b)},{ops.less(a,b)},{ops.lessEqual(a,b)},{ops.greater(a,b)},{ops.greaterEqual(a,b)},
                    {enc(ops.minimum(a,b))},{enc(ops.maximum(a,b))})
            }
            "alignment" -> {
                fun parse(it: JsonElement): Alignment = Alignment.entries[it.jsonPrimitive.int]
                fun enc(it: Alignment): JsonElement = JsonPrimitive(it.name)
                val a=parse(x[0]);val b=parse(x[1])
                report({ops.compare(a,b)},{ops.equal(a,b)},{ops.notEqual(a,b)},{ops.less(a,b)},{ops.lessEqual(a,b)},{ops.greater(a,b)},{ops.greaterEqual(a,b)},
                    {enc(ops.minimum(a,b))},{enc(ops.maximum(a,b))})
            }
            else -> error(c)
        }
    }
    @TestFactory fun originalOrderCases(): List<DynamicTest> {
        val rows=GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/templates/order.jsonl.gz"))).bufferedReader().use { it.readLines().drop(1) }
        return rows.mapIndexed { i,raw -> DynamicTest.dynamicTest("native Eq/Ord $i") {
            val c=Json.parseToJsonElement(raw).jsonObject
            assertEquals(c.getValue("expected"),observe(c),"case $i ${c.getValue("op")} ${c.getValue("target")}")
        } }
    }
    @Test fun deepTreesAndShortCircuit() {
        var a: Template<String> = Template.Literal(Doc.Text(1,"a"))
        var b: Template<String> = Template.Literal(Doc.Text(1,"b"))
        repeat(10_000) { a=Template.Nested(a);b=Template.Nested(b) }
        assertTrue(TemplateComparison.Text.less(a,b));assertFalse(TemplateComparison.Text.equal(a,b))
        assertTrue(TemplateComparison.Text.equal(a,a))
        var x: Doc<String> = Doc.Text(1,"a")
        var y: Doc<String> = Doc.Text(1,"b")
        repeat(10_000) { x=Doc.Concat(Doc.Empty,x);y=Doc.Concat(Doc.Empty,y) }
        assertTrue(TemplateComparison.Text.compare(x,y)<0)
        val ops=TemplateComparison(probe)
        assertFalse(ops.equal(Attributed(listOf(AttributedPart(null,Font.Base,"!"))),Attributed(emptyList())))
        assertTrue(ops.less(Doc.Text(0,"!"),Doc.Text(1,"!")))
        assertFailsWith<ProbeException> { ops.compare(Doc.Text(0,"!"),Doc.Text(0,"a")) }
        fun lines(value: String) = object : AbstractList<Attributed<String>>() {
            override val size: Int = 1_000_000
            override fun get(index: Int): Attributed<String> {
                check(index == 0) { "comparison read an unreachable list tail" }
                return Attributed(listOf(AttributedPart(null,Font.Base,value)))
            }
        }
        assertTrue(TemplateComparison.Text.compare(Doc.Block(0,lines("a")),Doc.Block(0,lines("b"))) < 0)
        assertFalse(TemplateComparison.Text.equal(Doc.Block(0,lines("a")),Doc.Block(0,lines("b"))))
        assertTrue(TemplateComparison.Text.equal(Doc.Text(0,"\ud800"),Doc.Text(0,"\ufffd")))
        assertTrue(TemplateComparison.Text.less(Doc.Text(0,"\ufffd"),Doc.Text(0,"😀")))
        val n=Double.fromBits(0x7ff8000000000001L)
        val d=Doc.Text(0,n)
        val cmp=TemplateComparison.Double
        assertFalse(cmp.equal(d,d));assertTrue(cmp.less(d,d));assertTrue(cmp.greater(d,d))
        assertFalse(cmp.lessEqual(d,d));assertFalse(cmp.greaterEqual(d,d))
    }
}
