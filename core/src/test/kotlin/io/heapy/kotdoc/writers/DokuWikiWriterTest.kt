package io.heapy.kotdoc.writers

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.logging.Logging
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.templates.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals

/** Exact unnormalized outputs and complete diagnostics from the original pinned module. */
class DokuWikiWriterTest {
    private fun resource(path: String) = checkNotNull(javaClass.getResourceAsStream("/dokuwiki-writer/$path"))
    @TestFactory
    fun originalModuleObservations(): List<DynamicTest> = GZIPInputStream(resource("observations.jsonl.gz")).bufferedReader().useLines { lines ->
        lines.map { line ->
            val record = Json.parseToJsonElement(line).jsonObject
            val name = record.getValue("name").jsonPrimitive.content
            DynamicTest.dynamicTest(name) {
                val request = record.getValue("request").jsonObject
                val template = request["template"]?.jsonPrimitive?.content?.let {
                    (TemplateCompiler(TemplateValues.Text).compile("oracle.dokuwiki", it) as CompiledTemplate.Success).template
                }
                val options = WriterOptions(
                    template = template,
                    wrapText = when (request["wrap"]?.jsonPrimitive?.content) {
                        "none" -> WrapOption.WrapNone; "preserve" -> WrapOption.WrapPreserve; else -> WrapOption.WrapAuto
                    },
                    extensions = if (request["rawHtml"]?.jsonPrimitive?.boolean == true) Extensions.of(ExtensionName.RawHtml) else Extensions.Empty,
                    dpi = request["dpi"]?.jsonPrimitive?.long ?: 96,
                    tableOfContents = request["toc"]?.jsonPrimitive?.boolean ?: false,
                )
                val logs = mutableListOf<LogMessage>()
                val actual = DokuWikiWriter.write(options, PandocJson.decode(record.getValue("document")), logs::add)
                assertEquals(record.getValue("output").jsonPrimitive.content, actual)
                assertEquals(record.getValue("logs"), Json.parseToJsonElement(Logging.encodeLogMessagesText(logs)))
                if (name.startsWith("original-old/")) {
                    assertEquals(resource("original/${name.removePrefix("original-old/")}").bufferedReader().use { it.readText() }, actual)
                }
            }
        }.toList()
    }
    @TestFactory
    fun deepOriginalModuleObservations(): List<DynamicTest> = GZIPInputStream(resource("deep.jsonl.gz")).bufferedReader().useLines { lines ->
        lines.map { line ->
            val record = Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest(record.getValue("name").jsonPrimitive.content) {
                val recipe = record.getValue("request").jsonObject.getValue("deep").jsonObject
                val document = deepDocument(recipe.getValue("kind").jsonPrimitive.content, recipe.getValue("depth").jsonPrimitive.int)
                fun checkOutput() {
                    val logs = mutableListOf<LogMessage>()
                    assertEquals(record.getValue("output").jsonPrimitive.content, DokuWikiWriter.write(WriterOptions(), document, logs::add))
                    assertEquals(record.getValue("logs"), Json.parseToJsonElement(Logging.encodeLogMessagesText(logs)))
                }
                checkOutput() // Ordinary JUnit worker stack.
                val failure = AtomicReference<Throwable>()
                // Also prove traversal does not depend on a generously sized JVM stack.
                Thread(null, { try { checkOutput() } catch (error: Throwable) { failure.set(error) } }, "DokuWiki-256k-stack", 256 * 1024L).apply { start(); join() }
                failure.get()?.let { throw it }
            }
        }.toList()
    }

    private fun deepDocument(kind: String, depth: Int): Pandoc {
        fun plain() = Block.Plain(listOf(Inline.Str("x")))
        if (kind == "emph" || kind == "note") {
            var inline: Inline = Inline.Str("x")
            repeat(depth) { inline = if (kind == "emph") Inline.Emph(listOf(inline)) else Inline.Note(listOf(Block.Para(listOf(inline)))) }
            return Pandoc(blocks = listOf(Block.Para(listOf(inline))))
        }
        var block: Block = when (kind) {
            "diagnostic" -> Block.RawBlock("unsupported", "leaf")
            "bullet" -> Block.BulletList(listOf(listOf(plain())))
            "ordered" -> Block.OrderedList(ListAttributes(), listOf(listOf(plain())))
            else -> Block.Para(listOf(Inline.Str("x")))
        }
        repeat(depth) {
            block = when (kind) {
                "div", "diagnostic" -> Block.Div(Attr(), listOf(block))
                "quote" -> Block.BlockQuote(listOf(block))
                "bullet" -> Block.BulletList(listOf(listOf(plain(), block)))
                "ordered" -> Block.OrderedList(ListAttributes(), listOf(listOf(plain(), block)))
                "definition" -> Block.DefinitionList(listOf(listOf(Inline.Str("term")) to listOf(listOf(block))))
                "table" -> Block.Table(Attr(), Caption(null, emptyList()), listOf(ColSpec(io.heapy.kotdoc.ast.Alignment.AlignDefault, ColWidth.Default)), TableHead(Attr(), emptyList()),
                    listOf(TableBody(Attr(), 0, emptyList(), listOf(Row(Attr(), listOf(Cell(Attr(), io.heapy.kotdoc.ast.Alignment.AlignDefault, 1, 1, listOf(block))))))), TableFoot(Attr(), emptyList()))
                "mixed" -> Block.BlockQuote(listOf(Block.Div(Attr(), listOf(Block.BulletList(listOf(listOf(Block.Para(listOf(Inline.Str("x"))), block)))))))
                else -> error("Unknown original deep recipe: $kind")
            }
        }
        return Pandoc(blocks = listOf(block))
    }

    companion object {
        /** Run with -Xmx512m -Xss256k and the core test runtime classpath. */
        @JvmStatic
        fun main(args: Array<String>) {
            val suite = DokuWikiWriterTest()
            val tests = suite.deepOriginalModuleObservations() + suite.originalModuleObservations()
            for (test in tests) test.executable.execute()
            println("DokuWiki: ${tests.size} independent observations passed")
        }
    }

}
