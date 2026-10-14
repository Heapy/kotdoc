package io.heapy.kotdoc.highlighting

import io.heapy.kotdoc.templates.PartialPaths
import io.heapy.kotdoc.xml.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import kotlin.test.*

class SyntaxParserTest {
    private fun JsonElement.s() = jsonPrimitive.content
    private fun j(vararg values: JsonElement) = JsonArray(values.toList())
    private fun str(value: String?) = value?.let(::JsonPrimitive) ?: JsonNull
    private fun name(n: XmlDocName) = j(str(n.local), str(n.namespace), str(n.prefix))
    private fun instruction(i: XmlInstruction) = j(str(i.target), str(i.data))
    private fun misc(m: XmlMisc): JsonElement = when (m) {
        is XmlMisc.Comment -> j(str("comment"), str(m.text))
        is XmlMisc.Instruction -> j(str("instruction"), instruction(m.instruction))
    }
    private fun external(id: XmlExternalId) = when (id) {
        is XmlExternalId.SystemId -> j(str("system"), str(id.system))
        is XmlExternalId.PublicId -> j(str("public"), str(id.public), str(id.system))
    }
    private fun node(n: XmlDocNode): JsonElement = when (n) {
        is XmlDocNode.Element -> j(str("element"), element(n.element))
        is XmlDocNode.Text -> j(str("text"), str(n.text))
        is XmlDocNode.Comment -> j(str("comment"), str(n.text))
        is XmlDocNode.Instruction -> j(str("instruction"), instruction(n.instruction))
    }
    private fun element(el: XmlDocElement) = j(name(el.name), JsonArray(el.attributes.map { (key, value) -> j(name(key), str(value)) }), JsonArray(el.nodes.map(::node)))
    private fun document(d: XmlDocument): JsonElement = j(JsonArray(d.before.map(::misc)), d.doctype?.let { j(str(it.name), it.externalId?.let(::external) ?: JsonNull) } ?: JsonNull,
        JsonArray(d.after.map(::misc)), element(d.root), JsonArray(d.epilogue.map(::misc)))
    private fun checkResult(expected: JsonObject, action: () -> SyntaxParseResult) {
        try {
            when (val result = action()) {
                is SyntaxParseResult.Failure -> assertEquals(expected, buildJsonObject { put("error", result.error) })
                is SyntaxParseResult.Success -> {
                    val shown = result.syntax.toString() // Force the same full observation as the native adapter.
                    assertTrue("syntax" in expected)
                    assertEquals(expected.getValue("show").s(), shown)
                }
            }
        } catch (e: InvalidIncludeRulesException) { assertEquals(expected, buildJsonObject { put("exception", e.message) }) }
          catch (e: SyntaxUtf8Exception) { assertEquals(expected, buildJsonObject { put("exception", e.message) }) }
    }
    private fun syntaxMap(map: SyntaxMap): JsonElement = JsonArray(map.entries.map { j(str(it.key), str(it.value.toString())) })
    private fun checkDirectory(input: JsonElement, expected: JsonObject) {
        val directory = Files.createTempDirectory("kotdoc-syntax-dir-")
        fun normalize(text: String) = text.replace(directory.toString(), "<dir>")
        try {
            for (row in input.jsonArray) {
                val (name, content) = row.jsonArray
                if (content == JsonNull) Files.createDirectories(directory.resolve(name.s()))
                else Files.write(directory.resolve(name.s()), content.s().hexToByteArray())
            }
            val actualPaths = SyntaxLoader.syntaxFiles(directory)
            assertEquals(expected.getValue("files").jsonArray.map { it.s() }.toSet(), actualPaths.map { normalize(it.toString()) }.toSet())
            val paths = expected.getValue("files").jsonArray.map { directory.resolve(it.s().removePrefix("<dir>/")) }
            val strict = SyntaxLoader.loadOrderedFiles(paths)
            val valid = SyntaxLoader.loadValidOrderedFiles(paths)
            val actual = buildJsonObject {
                put("files", JsonArray(paths.map { str(normalize(it.toString())) })) // File order is supplied explicitly, never inferred from a different filesystem.
                put("strict", when (strict) {
                    is SyntaxMapResult.Failure -> buildJsonObject { put("error", normalize(strict.error)) }
                    is SyntaxMapResult.Success -> buildJsonObject { put("map", syntaxMap(strict.syntaxes)) }
                })
                put("errors", JsonArray(valid.errors.map { (p, e) -> j(str(normalize(p)), str(normalize(e))) }))
                put("valid", syntaxMap(valid.syntaxes))
            }
            assertEquals(expected, actual)
        } finally { directory.toFile().deleteRecursively() }
    }
    @TestFactory fun originalSyntaxXml() = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/highlighting/syntax-parser.jsonl.gz")))
        .bufferedReader().use { it.readLines() }.drop(1).mapIndexed { index, line ->
            val c = Json.parseToJsonElement(line).jsonObject; val op = c.getValue("op").s()
            DynamicTest.dynamicTest("$index $op ${c["xml"]?.s().orEmpty()}") {
                val v = c.getValue("input"); val expected = c.getValue("expected").jsonObject
                when (op) {
                    "document", "document-chunks" -> {
                        val actual = when (val result = if (op == "document") XmlDocumentParser.parseText(v.s()) else XmlDocumentParser.parseTextChunks(v.jsonArray.map { it.s() })) {
                            is XmlDocumentResult.Failure -> buildJsonObject { put("error", result.error) }
                            is XmlDocumentResult.Success -> buildJsonObject { put("document", document(result.document)) }
                        }
                        assertEquals(expected, actual)
                    }
                    "syntax", "syntax-windows" -> checkResult(expected) {
                        val a = v.jsonArray
                        SyntaxParser.parseSyntaxDefinitionFromText(a[0].s(), a[1].s(), if (op == "syntax-windows") PartialPaths.Windows else PartialPaths.Posix)
                    }
                    "syntax-chunks" -> checkResult(expected) {
                        val a = v.jsonArray
                        SyntaxParser.parseSyntaxDefinitionFromTextChunks(a[0].s(), a[1].jsonArray.map { it.s() }, PartialPaths.Posix)
                    }
                    "bytes" -> checkResult(expected) { SyntaxParser.parseSyntaxDefinitionBytes("input.xml", v.s().hexToByteArray(), PartialPaths.Posix) }
                    "load" -> {
                        val directory = Files.createTempDirectory("kotdoc-syntax-file-"); val path = directory.resolve("input.xml")
                        try {
                            Files.write(path, v.s().hexToByteArray())
                            checkResult(expected) { when (val result = SyntaxLoader.loadSyntaxFromFile(path)) {
                                is SyntaxParseResult.Success -> result
                                is SyntaxParseResult.Failure -> SyntaxParseResult.Failure(result.error.replace(path.toString(), "<file>"))
                            } }
                        } finally { Files.deleteIfExists(path); Files.delete(directory) }
                    }
                    "directory" -> checkDirectory(v, expected)
                    else -> error(op)
                }
            }
        }
    private fun root(xml: String) = assertIs<XmlDocumentResult.Success>(XmlDocumentParser.parseText(xml)).document.root
    @Test fun deepEntitiesAdjacentContentAndOwnership() {
        val depth = 10_000
        val declarations = buildString {
            append("<!DOCTYPE a [<!ENTITY e0 'x'>")
            for (i in 1..depth) append("<!ENTITY e$i '&e${i - 1};'>")
            append("]><a>&e$depth;</a>")
        }
        assertEquals("x", root(declarations).strContent)
        val adjacent = root("<a>" + "x&amp;".repeat(50_000) + "<![CDATA[]]></a>")
        assertEquals(listOf(XmlDocNode.Text("x&".repeat(50_000))), adjacent.nodes)
        val attrs = mutableMapOf(XmlDocName("x", "u", "p") to "first")
        val nodes = mutableListOf<XmlDocNode>(XmlDocNode.Text("text"))
        val owned = XmlDocElement(XmlDocName("a"), attrs, nodes)
        attrs.clear(); nodes.clear()
        assertEquals("first", owned.attributes[XmlDocName("x", "u", "different")])
        assertEquals(listOf(XmlDocNode.Text("text")), owned.nodes)
        assertFailsWith<UnsupportedOperationException> { (owned.nodes as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (owned.attributes as MutableMap).clear() }
        assertEquals(0, XmlDocName("x", "u", "a").compareTo(XmlDocName("x", "u", "b")))
        assertEquals(XmlDocName("x", prefix = "a"), XmlDocName("x", prefix = "b"))
    }
    @Test fun originalDefaultResolvedExamples() {
        assertEquals("foo", root("\ufeef<foo/>").name.local)
        assertEquals("bar&baz", root("<foo>bar&amp;baz</foo>").strContent)
        val inline = "<!DOCTYPE foo [<!ENTITY bar \"baz\">]>"
        assertEquals("baz", root(inline + "<foo>&bar;</foo>").strContent)
        assertEquals("baz", root(inline + "<foo bar='&bar;'/>").attributes[XmlDocName("bar")])
        val complex = "<!DOCTYPE foo [<!ENTITY bar \"<p>baz &bim;</p>\"><!ENTITY bim \"Hello\">]>"
        assertEquals("baz Hello", root(complex + "<foo>&bar;</foo>").children.single().strContent)
        assertEquals("baz", root("<!DOCTYPE foo [<!ENTITY bar \"<p>baz</p>\">]><foo class=\"&bar;\"/>").attributes[XmlDocName("class")])
        assertEquals("bazI&", root("<!DOCTYPE foo [<!ENTITY bim \"baz\"><!ENTITY bar \"&bim;&#73;&amp;\">]><foo>&bar;</foo>").strContent)
        assertEquals("www.google.com", root("<a><![CDATA[www.google.com]]></a>").strContent)
        assertEquals("UnresolvedEntityException (fromList [\"bar\",\"baz\",\"foo\"])", assertIs<XmlDocumentResult.Failure>(
            XmlDocumentParser.parseText("<root attr='&bar;'>&foo; --- &baz; &foo;</root>")).error)
        for (xml in listOf("<!DOCTYPE foo [<!ENTITY bim \"&bim;\">]><foo>&bim;</foo>",
            "<!DOCTYPE foo [<!ENTITY bim \"&bim;\">]><foo class=\"&bim;\"/>",
            "<!DOCTYPE foo [<!ENTITY % bim \"Hello\">]><foo>&bim;</foo>")) {
            assertEquals("UnresolvedEntityException (fromList [\"bim\"])", assertIs<XmlDocumentResult.Failure>(XmlDocumentParser.parseText(xml)).error)
        }
    }
    @Test fun deepFiniteXmlRulesAndFilesystemAdapter() {
        val depth = 10_000
        val xml = "<language name=\"Deep\"><highlighting><contexts><context name=\"start\">" + "<Int>".repeat(depth) + "<Float/>" + "</Int>".repeat(depth) + "</context></contexts></highlighting></language>"
        val syntax = assertIs<SyntaxParseResult.Success>(SyntaxParser.parseSyntaxDefinitionFromText("deep.xml", xml)).syntax
        var rule = syntax.contexts.getValue("start").rules.single()
        repeat(depth) { assertEquals(Matcher.Int, rule.matcher); rule = rule.children.single() }; assertEquals(Matcher.Float, rule.matcher)
        val directory = Files.createTempDirectory("kotdoc-syntax-api-")
        try {
            val valid = "<language name=\"One\"><highlighting><contexts><context name=\"start\"/></contexts></highlighting></language>"
            Files.writeString(directory.resolve("one.xml"), valid); Files.writeString(directory.resolve("skip.XML"), "bad")
            Files.createDirectory(directory.resolve("dir.xml"))
            assertEquals(setOf("One"), assertIs<SyntaxMapResult.Success>(SyntaxLoader.loadSyntaxesFromDir(directory)).syntaxes.keys)
            Files.writeString(directory.resolve("bad.xml"), "<language/>")
            assertIs<SyntaxMapResult.Failure>(SyntaxLoader.loadSyntaxesFromDir(directory))
            val result = SyntaxLoader.loadValidSyntaxesFromDir(directory)
            assertEquals(setOf("One"), result.syntaxes.keys); assertEquals(setOf(directory.resolve("bad.xml").toString()), result.errors.keys)
            assertFailsWith<UnsupportedOperationException> { (result.syntaxes as MutableMap).clear() }
            assertFailsWith<UnsupportedOperationException> { (result.errors as MutableMap).clear() }
            assertFailsWith<java.io.IOException> { SyntaxLoader.loadSyntaxFromFile(directory.resolve("missing.xml")) }
        } finally { directory.toFile().deleteRecursively() }
    }
}
