// Original FB2 goldens: Copyright (C) 2018-2020 Alexander Krotov, GPL-2.0-or-later.
package io.heapy.kotdoc.formats

import io.heapy.kotdoc.ast.PandocJson
import io.heapy.kotdoc.logging.Logging
import io.heapy.kotdoc.logging.logMessages
import io.heapy.kotdoc.logging.assertLogJsonEquals
import io.heapy.kotdoc.xml.XmlParseException
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.*

class FB2ReaderTest {
    private val root = Path.of(checkNotNull(System.getProperty("kotdoc.root")))
    private fun fixtures() = Json.parseToJsonElement(checkNotNull(javaClass.getResource("/fb2/oracle.json")).readText()).jsonObject

    @TestFactory
    fun `all original FB2 reader golden tests`(): List<DynamicTest> =
        listOf("emphasis", "titles", "epigraph", "poem", "meta", "notes").map { name ->
            DynamicTest.dynamicTest(name) {
                val path = root.resolve("upstream/pandoc/test/fb2/reader/$name")
                val input = Path.of("$path.fb2").readText().removePrefix("\uFEFF")
                val expected = Path.of("$path.native").readText().replace("\r", "")
                val document = FB2Reader.read(input)
                assertEquals(NativeReader.read(expected), document)
                assertEquals(expected, NativeWriter.write(document, standalone = true).replace("\r", ""))
            }
        }

    @TestFactory
    fun `complete upstream FB2 file corpus against original reader`(): List<DynamicTest> = fixtures().getValue("upstream").jsonArray.map {
        val fixture = it.jsonObject
        val path = fixture.getValue("path").jsonPrimitive.content
        DynamicTest.dynamicTest(path) {
            checkFixture(fixture, root.resolve("upstream/pandoc/$path").readText())
        }
    }

    @TestFactory
    fun `FB2 syntax semantics and effects match pinned Pandoc`(): List<DynamicTest> = fixtures().getValue("edges").jsonArray.map {
        val fixture = it.jsonObject
        DynamicTest.dynamicTest(fixture.getValue("name").jsonPrimitive.content) {
            checkFixture(fixture, fixture.getValue("input").jsonPrimitive.content)
        }
    }

    private fun checkFixture(fixture: JsonObject, input: String) {
        if (!fixture.getValue("accepted").jsonPrimitive.boolean) {
            assertFailsWith<XmlParseException> { FB2Reader.read(input.removePrefix("\uFEFF")) }
            return
        }
        val result = FB2Reader.readWithEffects(input.removePrefix("\uFEFF"))
        assertEquals(PandocJson.decode(fixture.getValue("document")), result.document, "document")
        val expectedMessages = fixture.getValue("messages").jsonArray.map { entry ->
            val m = entry.jsonObject
            when (m.getValue("type").jsonPrimitive.content) {
                "IgnoredElement" -> ReaderMessage.IgnoredElement(m.getValue("contents").jsonPrimitive.content)
                "UnexpectedXmlElement" -> ReaderMessage.UnexpectedXmlElement(
                    m.getValue("element").jsonPrimitive.content, m.getValue("parent").jsonPrimitive.content)
                else -> error("Unmapped upstream diagnostic: $m")
            }
        }
        assertEquals(expectedMessages, result.messages, "diagnostics")
        assertLogJsonEquals(fixture.getValue("messages"),Json.parseToJsonElement(Logging.encodeLogMessagesText(result.logMessages())))
        // These fixtures use simple relative IDs. General MediaBag path canonicalization
        // is application infrastructure, separately tracked in the porting ledger.
        val actualMedia = result.mediaInsertions.associateBy { it.path }
        val expectedMedia = fixture.getValue("media").jsonArray
        assertEquals(expectedMedia.size, actualMedia.size, "media count")
        for (entry in expectedMedia) {
            val m = entry.jsonObject
            val path = m.getValue("path").jsonPrimitive.content
            val actual = assertNotNull(actualMedia[path], path)
            assertEquals(m.getValue("mime").jsonPrimitive.content, actual.mimeType, path)
            assertEquals(m.getValue("hex").jsonPrimitive.content, actual.contents.toHexString(), path)
        }
    }
}
