package io.heapy.kotdoc.layout

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.streams.asStream
import kotlin.test.*

class DocLayoutTest {
    private val layout = DocLayout.Text
    private fun JsonElement.s() = jsonPrimitive.content
    private fun JsonElement.n() = jsonPrimitive.long
    private fun JsonElement.nullableInt() = if (this == JsonNull) null else jsonPrimitive.int
    private fun style(v: JsonElement): StyleRequest {
        val a = v.jsonArray; val x = a[1]
        return when (a[0].s()) {
            "weight" -> StyleRequest.Weight(x.jsonPrimitive.boolean)
            "shape" -> StyleRequest.Shape(x.jsonPrimitive.boolean)
            "strikeout" -> StyleRequest.Strikeout(x.jsonPrimitive.boolean)
            "underline" -> StyleRequest.Underlined(Underline.entries[x.jsonPrimitive.int])
            "fg" -> StyleRequest.Foreground(x.nullableInt()?.let { Color8.entries[it] })
            "bg" -> StyleRequest.Background(x.nullableInt()?.let { Color8.entries[it] })
            else -> error(v)
        }
    }
    private fun font(v: JsonElement): Font {
        val a = v.jsonArray
        return Font(a[0].jsonPrimitive.boolean, a[1].jsonPrimitive.boolean, Underline.entries[a[2].jsonPrimitive.int],
            a[3].jsonPrimitive.boolean, a[4].nullableInt()?.let { Color8.entries[it] }, a[5].nullableInt()?.let { Color8.entries[it] },
            a[6].takeUnless { it == JsonNull }?.s())
    }
    private fun attributed(v: JsonElement) = Attributed(v.jsonArray.map {
        val a = it.jsonArray
        AttributedPart(a[0].takeUnless { it == JsonNull }?.s(), font(a[1]), a[2].s())
    })
    private fun fontJson(f: Font) = JsonArray(listOf(JsonPrimitive(f.bold), JsonPrimitive(f.italic), JsonPrimitive(f.underline.ordinal),
        JsonPrimitive(f.strikeout), f.foreground?.let { JsonPrimitive(it.ordinal) } ?: JsonNull,
        f.background?.let { JsonPrimitive(it.ordinal) } ?: JsonNull, f.link?.let(::JsonPrimitive) ?: JsonNull))
    private fun partJson(p: AttributedPart<String>) = JsonArray(listOf(p.link?.let(::JsonPrimitive) ?: JsonNull, fontJson(p.font), JsonPrimitive(p.value)))
    private fun attributedJson(a: Attributed<String>) = JsonArray(a.parts.map(::partJson))
    private fun <A> transformed(l: DocLayout<A>, d: Doc<A>, op: String): Doc<A> = with(l) { when (op) {
        "identity" -> d; "lblock" -> lblock(3, d); "rblock" -> rblock(4, d); "cblock" -> cblock(5, d)
        "prefix" -> prefixed("> ", d); "bold" -> bold(d); "link" -> link("https://x", d)
        "nowrap" -> nowrap(d); "chomp" -> chomp(d); "nestle" -> nestle(d); else -> error(op)
    } }
    private fun doc(v: JsonElement): Doc<String> {
        if (v is JsonPrimitive && v.isString) return layout.text(v.content)
        val a = v.jsonArray
        fun d(i: Int) = doc(a[i])
        return with(layout) { when (a[0].s()) {
            "empty" -> empty; "space" -> space; "cr" -> cr; "newline" -> Doc.NewLine
            "blanklines" -> blanklines(a[1].n()); "raw-text" -> Doc.Text(a[1].n(), a[2].s())
            "block" -> Doc.Block(a[1].n(), a[2].jsonArray.map(::attributed))
            "vfill" -> Doc.VFill(a[1].n(), a[2].s()); "cooked" -> Doc.CookedText(a[1].n(), attributed(a[2]))
            "prefixed" -> Doc.Prefixed(a[1].s(), d(2)); "before" -> Doc.BeforeNonBlank(d(1)); "flush" -> Doc.Flush(d(1))
            "after" -> afterBreak(a[1].s()); "concat" -> Doc.Concat(d(1), d(2)); "append" -> d(1) + d(2)
            "spaced" -> spaced(d(1), d(2)); "above" -> above(d(1), d(2)); "separated" -> separated(d(1), d(2))
            "styled" -> Doc.Styled(style(a[1]), d(2)); "linked" -> link(a[1].s(), d(2))
            "nowrap" -> nowrap(d(1)); "chomp" -> chomp(d(1)); "nestle" -> nestle(d(1))
            "nest" -> nest(a[1].n(), d(2)); "hang" -> hang(a[1].n(), d(2), d(3))
            "lblock" -> lblock(a[1].n(), d(2)); "rblock" -> rblock(a[1].n(), d(2)); "cblock" -> cblock(a[1].n(), d(2))
            "fill" -> vfill(a[1].s()); "hcat" -> hcat(a[1].jsonArray.map(::doc)); "hsep" -> hsep(a[1].jsonArray.map(::doc))
            "vcat" -> vcat(a[1].jsonArray.map(::doc)); "vsep" -> vsep(a[1].jsonArray.map(::doc))
            "braces" -> braces(d(1)); "brackets" -> brackets(d(1)); "parens" -> parens(d(1))
            "quotes" -> quotes(d(1)); "doubleQuotes" -> doubleQuotes(d(1))
            else -> error(v)
        } }
    }
    private fun widths(points: IntArray): List<Long> = listOf(DisplayWidth.length(points), DisplayWidth.length(points, wide = true),
        DisplayWidth.length(points, shortcut = false), DisplayWidth.length(points, wide = true, shortcut = false))
    private fun actual(op: String, input: JsonElement): JsonElement = when (op) {
        "walk" -> {
            val d = doc(input)
            fun reversed(s: String) = buildString { s.codePoints().toArray().reversed().forEach(::appendCodePoint) }
            fun values(d: Doc<String>) = JsonArray(d.foldValues(emptyList<String>()) { xs, x -> xs + x }.map(::JsonPrimitive))
            fun renders(d: Doc<String>) = JsonArray(listOf(null, 4L, 10L).map { w -> JsonArray(listOf(layout.render(w, d), layout.renderANSI(w, d)).map(::JsonPrimitive)) })
            val mapped = d.mapValues(::reversed); val effects = mutableListOf<String>()
            val traversed = d.mapValues { value -> val index = effects.size; effects.add(value); "$index:$value" }
            buildJsonObject {
                put("values", values(d)); put("mappedValues", values(mapped)); put("traversedValues", values(traversed))
                put("effects", JsonArray(effects.map(::JsonPrimitive))); put("mappedRenders", renders(mapped)); put("traversedRenders", renders(traversed))
            }
        }
        "font" -> {
            val a = input.jsonArray; val f = a[1].jsonArray.fold(font(a[0])) { f, s -> f.apply(style(s)) }
            buildJsonObject { put("font", fontJson(f)); put("render", f.render()) }
        }
        "attr-append" -> {
            val a = attributed(input).parts
            partJson(a[0].append(a[1], TextChars))
        }
        "attributed" -> {
            val c = AttributedChars(TextChars); val a = attributed(input)
            buildJsonObject {
                put("right", JsonArray(c.foldRight(a, emptyList<Int>()) { p, xs -> listOf(p) + xs }.map(::JsonPrimitive)))
                put("left", JsonArray(c.foldLeft(a, emptyList<Int>()) { xs, p -> listOf(p) + xs }.map(::JsonPrimitive)))
                put("null", c.isNull(a)); put("split", JsonArray(c.splitLines(a).map(::attributedJson))); put("build", c.build(a))
            }
        }
        "rich-doc" -> {
            val a = input.jsonArray; val l = DocLayout(AttributedChars(TextChars)); val d = transformed(l, l.literal(attributed(a[0])), a[1].s())
            buildJsonObject {
                put("offset", l.offset(d)); put("minOffset", l.minOffset(d)); put("height", l.height(d))
                put("renders", JsonArray(a[2].jsonArray.map { w ->
                    val width = w.takeUnless { it == JsonNull }?.n()
                    JsonArray(listOf(attributedJson(l.render(width, d)), JsonPrimitive(l.renderANSI(width, d))))
                }))
            }
        }
        "part-doc" -> {
            val a = input.jsonArray; val l = DocLayout(PartChars(TextChars))
            val part = attributed(JsonArray(listOf(a[0]))).parts.single()
            val d = transformed(l, l.literal(part), a[1].s())
            buildJsonObject {
                put("offset", l.offset(d)); put("minOffset", l.minOffset(d)); put("height", l.height(d))
                put("renders", JsonArray(a[2].jsonArray.map { w ->
                    val width = w.takeUnless { it == JsonNull }?.n()
                    JsonArray(listOf(partJson(l.render(width, d)), JsonPrimitive(l.renderANSI(width, d))))
                }))
            }
        }
        "string-doc" -> {
            val a = input.jsonArray; val l = DocLayout.Characters
            val d = transformed(l, l.literal(CharacterString(a[0].jsonArray.map { it.jsonPrimitive.int }.toIntArray())), a[1].s())
            buildJsonObject {
                put("offset", l.offset(d)); put("minOffset", l.minOffset(d)); put("height", l.height(d))
                put("renders", JsonArray(a[2].jsonArray.map { w ->
                    val width = w.takeUnless { it == JsonNull }?.n()
                    JsonArray(listOf(JsonArray(l.render(width, d).points().map(::JsonPrimitive)), JsonPrimitive(l.renderANSI(width, d))))
                }))
            }
        }
        "width" -> JsonArray(widths(input.jsonArray.map { it.jsonPrimitive.int }.toIntArray()).map(::JsonPrimitive))
        "chars" -> JsonArray(input.jsonArray.map { p ->
            val c = p.jsonPrimitive.int
            JsonArray((listOf(DisplayWidth.charWidth(c)) + widths(intArrayOf(c)) + listOf(DisplayWidth.isSkinToneModifier(c), DisplayWidth.isEmojiVariation(c), DisplayWidth.isZWJ(c)).map { if (it) 1L else 0L }).map(::JsonPrimitive))
        })
        "measure" -> {
            val a = input.jsonArray; val d = doc(a[0])
            buildJsonObject { put("offset", layout.offset(d)); put("minOffset", layout.minOffset(d)); put("column", layout.updateColumn(d, a[1].n())) }
        }
        "doc" -> {
            val a = input.jsonArray; val d = doc(a[0])
            buildJsonObject {
                put("offset", layout.offset(d)); put("minOffset", layout.minOffset(d)); put("height", layout.height(d))
                put("column", layout.updateColumn(d, a[2].n())); put("empty", layout.isEmpty(d))
                put("renders", JsonArray(a[1].jsonArray.map { w ->
                    val width = w.takeUnless { it == JsonNull }?.n()
                    JsonArray(listOf(layout.render(width, d), layout.renderANSI(width, d)).map(::JsonPrimitive))
                }))
            }
        }
        else -> error(op)
    }
    @TestFactory fun originalObservations(): Stream<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/layout/doclayout.jsonl.gz")))
        .bufferedReader().use { it.readLines() }.drop(1).asSequence().map { line ->
            val name = Json.parseToJsonElement(line).jsonObject.getValue("name").s()
            DynamicTest.dynamicTest(name) {
                val c = Json.parseToJsonElement(line).jsonObject
                val expected = c.getValue("expected"); val value = actual(c.getValue("op").s(), c.getValue("input"))
                assertEquals(expected, value)
                if (c["quickcheck"]?.jsonPrimitive?.boolean == true) {
                    assertEquals(value.jsonArray[0], value.jsonArray[2]); assertEquals(value.jsonArray[1], value.jsonArray[3])
                }
                c["original"]?.let { assertEquals(it.jsonObject.getValue("golden"), value.jsonObject.getValue("renders").jsonArray[0].jsonArray[0]) }
            }
        }.asStream()
    @Test fun originalOffsetExamples() = with(layout) {
        assertEquals(35L, offset(nest(3, text("**") + text("thisIsGoingToBeTooLongAnyway") + text("**")) + blankline))
        assertEquals(10L, offset(text("hello") + nest(2, text("world"))))
        assertEquals(4L, offset(text("aa") + nest(2, text("bb") + cr + text("cc"))))
    }
    @Test fun originalChopStylingExample() = assertContains(layout.renderANSI(null, layout.lblock(4, layout.bold(layout.text("hi there")))), "\u001b[1m")
    @TestFactory fun originalWidthExamples(): List<DynamicTest> = listOf(
        Triple("This is going to be too long anyway", false, 35L), Triple("Circle◯Circle", false, 13L), Triple("圆◯圆", true, 6L),
        Triple("*a", false, 2L), Triple("*\ufe0f\u20e3a", false, 3L), Triple("⌚", false, 2L), Triple("©️", false, 2L),
        Triple("©", false, 1L), Triple("🅰️🇦🇨", false, 4L), Triple("🖐🏿", false, 2L), Triple("1🏿", false, 3L),
    ).map { (text, wide, width) -> DynamicTest.dynamicTest("original width $text") { assertEquals(width, DisplayWidth.length(text.codePoints().toArray(), wide = wide)) } }
    @Test fun deepConcatenationAndStylesUseFiniteWorkStacks() = with(layout) {
        var left: Doc<String> = empty
        repeat(50000) { left = left + text("x") }
        assertEquals(50000L, offset(left)); assertEquals("x".repeat(50000), render(null, nowrap(left)))
        assertEquals("x".repeat(50000), render(null, chomp(left + blankline)))
        var nested: Doc<String> = text("x")
        repeat(10000) { nested = Doc.Styled(StyleRequest.Weight(true), Doc.Prefixed("", nested)) }
        assertEquals("x", render(null, nested)); assertEquals(1L, offset(nested))
    }
}
