// Original cases from Tests.Writers.FB2, GPL-2.0-or-later; see upstream/ and licenses/.
package io.heapy.kotdoc.formats

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.resources.*
import io.heapy.kotdoc.xml.XmlParseException
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.*

class FB2WriterTest {
    private fun fixtures(): List<JsonObject> {
        val data = Json.parseToJsonElement(checkNotNull(javaClass.getResource("/fb2/writer.json")).readText()).jsonObject
        return (data.getValue("corpus").jsonArray + data.getValue("edges").jsonArray).map { it.jsonObject }
    }

    @TestFactory
    fun `original FB2 writer examples`(): List<DynamicTest> {
        val prefix = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<FictionBook xmlns=\"http://www.gribuser.ru/xml/fictionbook/2.0\" xmlns:l=\"http://www.w3.org/1999/xlink\"><description><title-info><genre>unrecognised</genre></title-info><document-info><program-used>pandoc</program-used></document-info></description><body><title><p /></title><section>"
        val cases = listOf(
            "paragraph" to (Builder.para(Builder.text("Lorem ipsum cetera.")) to "<p>Lorem ipsum cetera.</p>"),
            "emphasis" to (Builder.para(Builder.emph(Builder.text("emphasized"))) to "<p><emphasis>emphasized</emphasis></p>"),
            "bullet list" to (Builder.bulletList(listOf("first", "second", "third").map { Builder.plain(Builder.text(it)) }) to
                "<p>•\u00a0first</p><p>•\u00a0second</p><p>•\u00a0third</p>"),
        )
        return cases.map { (name, pair) -> DynamicTest.dynamicTest(name) {
            assertEquals(prefix + pair.second + "</section></body></FictionBook>\n", FB2Writer.write(Builder.doc(pair.first)))
        } }
    }

    @TestFactory
    fun `writer matches pinned Pandoc across full AST corpus and edge cases`(): List<DynamicTest> = fixtures().map { fixture ->
        val name = (fixture["path"] ?: fixture.getValue("name")).jsonPrimitive.content
        DynamicTest.dynamicTest(name) {
            val bag = MediaBag()
            fixture.getValue("media").jsonArray.forEach { bag.insert(it.jsonPrimitive.content, "image/png", "oracle".toByteArray()) }
            val resources = ResourceContext(bag, resourcePaths = emptyList())
            val options = FB2WriterOptions(fixture["webtex"]?.jsonPrimitive?.content?.let(MathMethod::WebTeX) ?: MathMethod.PlainMath)
            val doc = PandocJson.decode(fixture.getValue("document"))
            if (!fixture.getValue("accepted").jsonPrimitive.boolean) {
                assertFailsWith<XmlParseException> { FB2Writer.write(doc, options, resources) }
            } else {
                val result = FB2Writer.writeWithEffects(doc, options, resources)
                assertEquals(fixture.getValue("expected").jsonPrimitive.content, result.text)
                val expected = fixture.getValue("messages").jsonArray.mapNotNull { message ->
                    val m = message.jsonObject
                    when (m.getValue("type").jsonPrimitive.content) {
                        "RunningFilter", "FilterCompleted" -> null
                        "InlineNotRendered" -> WriterMessage.InlineNotRendered(decodeInline(m.getValue("contents")))
                        "BlockNotRendered" -> WriterMessage.BlockNotRendered(decodeBlock(m.getValue("contents")))
                        "CouldNotFetchResource" -> WriterMessage.CouldNotFetchResource(m.getValue("path").jsonPrimitive.content, m.getValue("message").jsonPrimitive.content)
                        "CouldNotDetermineMimeType" -> WriterMessage.CouldNotDetermineMimeType(m.getValue("path").jsonPrimitive.content)
                        else -> error("Unmapped writer diagnostic: $m")
                    }
                }
                assertEquals(expected, result.messages, "diagnostic order and contents")
            }
        }
    }

    @TestFactory
    fun `shared section and flattening operations match original`(): List<DynamicTest> = fixtures().flatMap { fixture ->
        val name = (fixture["path"] ?: fixture.getValue("name")).jsonPrimitive.content
        val doc = PandocJson.decode(fixture.getValue("document"))
        val shared = fixture.getValue("shared").jsonObject
        listOf(DynamicTest.dynamicTest("sections: $name") {
            assertEquals(PandocJson.decode(shared.getValue("sections")).blocks, DocumentShared.makeSections(doc.blocks))
        }, DynamicTest.dynamicTest("blocksToInlines: $name") {
            assertEquals(PandocJson.decode(shared.getValue("flattened")).blocks,
                listOf(Block.Plain(DocumentShared.blocksToInlines(doc.blocks).values)))
        })
    }

    private fun decodeBlock(value: JsonElement): Block = PandocJson.decode(buildJsonObject {
        put("pandoc-api-version", JsonArray(listOf(1,23,1,2).map(::JsonPrimitive))); put("meta", buildJsonObject {}); put("blocks", JsonArray(listOf(value)))
    }).blocks.single()
    private fun decodeInline(value: JsonElement): Inline = (decodeBlock(buildJsonObject {
        put("t", "Plain"); put("c", JsonArray(listOf(value)))
    }) as Block.Plain).content.single()
}
