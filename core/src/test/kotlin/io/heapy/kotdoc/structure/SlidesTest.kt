// Text.Pandoc.Slides: John MacFarlane, GPL-2.0-or-later.
package io.heapy.kotdoc.structure

import io.heapy.kotdoc.ast.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.streams.asStream
import kotlin.test.*

class SlidesTest {
    private fun h(level: Long, title: String = "heading", attr: Attr = Attr()) = Block.Header(level, attr, listOf(Inline.Str(title)))
    private fun body() = Block.Para(listOf(Inline.Str("body")))
    private fun div(vararg blocks: Block) = Block.Div(Attr(), blocks.toList())
    private fun separator(level: Long) = h(level, "\u0000")

    @TestFactory fun `slide inference and section preparation match original Pandoc`(): Stream<DynamicTest> {
        val reader = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/structure/slides.jsonl.gz"))).bufferedReader()
        return reader.lineSequence().drop(1).flatMap { line ->
            val c = Json.parseToJsonElement(line).jsonObject
            val name = c.getValue("name").jsonPrimitive.content
            sequenceOf(DynamicTest.dynamicTest("level $name") {
                assertEquals(c.getValue("level").jsonPrimitive.long, Slides.getSlideLevel(PandocJson.decode(c.getValue("document")).blocks))
            }) + c.getValue("variants").jsonArray.asSequence().mapIndexed { i, value ->
                DynamicTest.dynamicTest("preparation $i $name") {
                    val v = value.jsonObject
                    val options = v.getValue("options").jsonObject
                    val blocks = PandocJson.decode(c.getValue("document")).blocks
                    val requested = options.getValue("slide_level").jsonPrimitive.long
                    val level = if (requested <= 0) Slides.getSlideLevel(blocks) else requested
                    val actual = DocumentShared.makeSections(Slides.prepSlides(level, blocks),
                        options["number_sections"]?.jsonPrimitive?.boolean ?: false,
                        options["base_level"]?.jsonPrimitive?.long)
                    assertEquals(v.getValue("expected").jsonArray.map(PandocJson::decodeBlock), actual)
                }
            }
        }.asStream().onClose { reader.close() }
    }
    @Test fun `slide inference respects adjacency branch thresholds and the six-level cap`() {
        assertEquals(6, Slides.getSlideLevel(emptyList()))
        assertEquals(6, Slides.getSlideLevel(listOf(h(1))))
        assertEquals(6, Slides.getSlideLevel(listOf(h(1),Block.HorizontalRule)))
        assertEquals(2, Slides.getSlideLevel(listOf(h(1),h(2),body())))
        assertEquals(6, Slides.getSlideLevel(listOf(h(7),body())))
        assertEquals(-2, Slides.getSlideLevel(listOf(h(-2),body())))
        assertEquals(3, Slides.getSlideLevel(listOf(h(3),div(h(1),body()))))
        assertEquals(1, Slides.getSlideLevel(listOf(h(7),div(h(1),body()))))
        assertEquals(6, Slides.getSlideLevel(listOf(Block.BlockQuote(listOf(h(1),body())))))
        // Feeding the left branch's result into the right branch would incorrectly return one.
        assertEquals(2, Slides.getSlideLevel(listOf(div(h(2),body()),h(4),div(h(1),body()))))
    }
    @Test fun `horizontal rules and leading slide headers follow exact top-level rules`() {
        val titled = h(2, "title", Attr("id", listOf("class"), listOf("key" to "value")))
        assertEquals(listOf(separator(2)), Slides.prepSlides(2, emptyList()))
        assertEquals(listOf(titled), Slides.prepSlides(2,listOf(Block.HorizontalRule,titled)))
        assertEquals(listOf(separator(1),titled), Slides.prepSlides(1,listOf(Block.HorizontalRule,titled)))
        assertEquals(listOf(separator(2),separator(2)), Slides.prepSlides(2,listOf(Block.HorizontalRule,Block.HorizontalRule)))
        assertEquals(listOf(h(1)), Slides.prepSlides(2,listOf(h(1))))
        assertEquals(listOf(separator(2),h(3)), Slides.prepSlides(2,listOf(h(3))))
        assertEquals(listOf(div(h(1),Block.HorizontalRule)), Slides.prepSlides(2,listOf(div(h(1),Block.HorizontalRule))))
        assertEquals(listOf(separator(2),div(div(h(1)))), Slides.prepSlides(2,listOf(div(div(h(1))))))
        assertEquals(listOf(separator(2),body()), Slides.prepSlides(2,listOf(body())))
    }
    @Test fun `only the final exact refs Div has its initial header extracted`() {
        val attrs = Attr("refs",listOf("references"),listOf("key" to "value"))
        val title = h(2,"References",Attr("bibliography",listOf("unnumbered")))
        val refs = Block.Div(attrs,listOf(title,body(),Block.HorizontalRule))
        assertEquals(listOf(title,refs.copy(blocks=listOf(body(),Block.HorizontalRule))), Slides.prepSlides(2,listOf(refs)))
        assertEquals(listOf(separator(2),body(),title,refs.copy(blocks=listOf(body(),Block.HorizontalRule))),
            Slides.prepSlides(2,listOf(body(),refs)))
        assertEquals(listOf(refs,body()), Slides.prepSlides(2,listOf(refs,body())))
        val upper = refs.copy(attr=attrs.copy(identifier="Refs"))
        assertEquals(listOf(upper), Slides.prepSlides(2,listOf(upper)))
        val empty = refs.copy(blocks=emptyList())
        assertEquals(listOf(separator(2),empty), Slides.prepSlides(2,listOf(empty)))
        val noHeader = refs.copy(blocks=listOf(body(),title))
        assertEquals(listOf(separator(2),noHeader), Slides.prepSlides(2,listOf(noHeader)))
        val onlyHeader = refs.copy(blocks=listOf(title))
        assertEquals(listOf(title,empty), Slides.prepSlides(2,listOf(onlyHeader)))
    }
    @Test fun `preparation accepts signed levels without applying Lua automatic-level policy`() {
        for (level in listOf(Long.MIN_VALUE,-1L,0L,1L,Long.MAX_VALUE)) {
            assertEquals(listOf(separator(level)), Slides.prepSlides(level,emptyList()))
            assertEquals(listOf(h(level)), Slides.prepSlides(level,listOf(Block.HorizontalRule,h(level))))
        }
        assertEquals(Long.MIN_VALUE, Slides.getSlideLevel(listOf(h(Long.MIN_VALUE),body())))
        assertEquals(6, Slides.getSlideLevel(listOf(h(Long.MAX_VALUE),body())))
    }
    @Test fun `inference handles long and deeply nested Div lists without stack recursion`() {
        assertEquals(2, Slides.getSlideLevel(List(20000) { div(h(2),body()) }))
        var nested: Block = div(h(1),body())
        repeat(10000) { nested = div(nested) }
        assertEquals(1, Slides.getSlideLevel(listOf(nested)))
    }
}
