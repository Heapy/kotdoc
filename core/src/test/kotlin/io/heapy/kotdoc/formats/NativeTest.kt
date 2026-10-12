// Native fixtures derive from Pandoc's GPL-2.0-or-later test suite. See upstream/ and licenses/.
package io.heapy.kotdoc.formats

import io.heapy.kotdoc.ast.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.*

class NativeTest {
    private fun fixtures(): JsonArray = Json.parseToJsonElement(checkNotNull(javaClass.getResource("/native/upstream.json")).readText()).jsonArray
    private val root = Path.of(checkNotNull(System.getProperty("kotdoc.root")))

    @TestFactory
    fun `all upstream native fixtures decode like Pandoc`(): List<DynamicTest> = fixtures().map { entry ->
        val fixture = entry.jsonObject
        val path = fixture.getValue("path").jsonPrimitive.content
        DynamicTest.dynamicTest(path) {
            val input = root.resolve("upstream/pandoc").resolve(path).readText()
            if (fixture.getValue("accepted").jsonPrimitive.boolean) {
                assertEquals(PandocJson.decode(fixture.getValue("json")), NativeReader.read(input))
            } else assertFails { NativeReader.read(input) }
        }
    }

    @TestFactory
    fun `writer reproduces upstream native layout`(): List<DynamicTest> = fixtures().filter { it.jsonObject.getValue("accepted").jsonPrimitive.boolean }.flatMap { entry ->
        val fixture = entry.jsonObject
        val path = fixture.getValue("path").jsonPrimitive.content
        val doc = PandocJson.decode(fixture.getValue("json"))
        listOf(DynamicTest.dynamicTest("fragment: $path") {
            assertEquals(fixture.getValue("fragment").jsonPrimitive.content, NativeWriter.write(doc))
        }, DynamicTest.dynamicTest("standalone: $path") {
            assertEquals(fixture.getValue("standalone").jsonPrimitive.content, NativeWriter.write(doc, standalone = true))
        })
    }

    @TestFactory
    fun `ported upstream p_write_rt and p_write_blocks_rt over complete fixture corpus`(): List<DynamicTest> = fixtures().filter { it.jsonObject.getValue("accepted").jsonPrimitive.boolean }.flatMap { entry ->
        val fixture = entry.jsonObject
        val path = fixture.getValue("path").jsonPrimitive.content
        val doc = PandocJson.decode(fixture.getValue("json"))
        listOf(DynamicTest.dynamicTest("p_write_rt: $path") {
            assertEquals(doc, NativeReader.read(NativeWriter.write(doc, standalone = true)))
        }, DynamicTest.dynamicTest("p_write_blocks_rt: $path") {
            assertEquals(Pandoc(blocks = doc.blocks), NativeReader.read(NativeWriter.write(doc)))
        })
    }

    @Test
    fun `Native reader accepts the five upstream entry shapes`() {
        val plain = Pandoc(blocks = listOf(Block.Plain(listOf(Inline.Str("hi")))))
        assertEquals(plain, NativeReader.read("Str \"hi\""))
        assertEquals(plain, NativeReader.read("[Str \"hi\"]"))
        assertEquals(plain, NativeReader.read("Plain [Str \"hi\"]"))
        assertEquals(plain, NativeReader.read("[Plain [Str \"hi\"]]"))
        assertEquals(plain, NativeReader.read("Pandoc Meta { unMeta = fromList [] } [Plain [Str \"hi\"]]"))
        assertEquals(plain, NativeReader.read("((Str \"hi\"))"))
        assertEquals(Pandoc(), NativeReader.read("[]"))
    }

    @Test
    fun `Haskell string escaping preserves Unicode controls and digit boundaries`() {
        val text = "é1😀\u0000\u000eH\u007f\n\t\\\""
        val native = """[ Para [ Str "\233\&1\128512\NUL\SO\&H\DEL\n\t\\\"" ] ]"""
        val doc = Pandoc(blocks = listOf(Block.Para(listOf(Inline.Str(text)))))
        assertEquals(native, NativeWriter.write(doc, columns = 200))
        assertEquals(doc, NativeReader.read(native))
        assertEquals(Pandoc(blocks = listOf(Block.Plain(listOf(Inline.Str("abc"))))), NativeReader.read("Str \"a\\x62\\o143\""))
        assertEquals(Pandoc(blocks = listOf(Block.Plain(listOf(Inline.Str("ab"))))), NativeReader.read("Str \"a\\\n  \\b\""))
    }

    @Test
    fun `malformed native input fails rather than returning partial content`() {
        for (input in listOf("", "Str", "Str 1", "Str \"x\" trailing", "[Str \"x\",]", "(Str) \"x\"", "Str \"\\1114112\"", "Str \"\\^?\"",
            "Para [HorizontalRule]", "Pandoc (Meta (fromList [])) []", "Header 1.0 (\"\",[],[]) []")) {
            assertFails(input) { NativeReader.read(input) }
        }
    }
}
