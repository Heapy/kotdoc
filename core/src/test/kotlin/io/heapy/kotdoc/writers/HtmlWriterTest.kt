package io.heapy.kotdoc.writers

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.layout.*
import io.heapy.kotdoc.logging.*
import io.heapy.kotdoc.markup.*
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.resources.*
import io.heapy.kotdoc.state.*
import io.heapy.kotdoc.templates.*
import io.heapy.kotdoc.translations.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.*

/** Transport only; original Haskell library/test modules supply all expectations. */
class HtmlWriterTest {
    private fun logs(xs: List<LogMessage>) = Json.parseToJsonElement(Logging.encodeLogMessagesText(xs))
    private class Runtime : TranslationRuntime, PandocState by PandocStateContext() {
        override fun readDataFile(file: CharacterString) = PackagedPandocData.read(file) ?: throw PandocError.PandocCouldNotFindDataFileError(TextChars.fromPoints(file.points()))
    }
    private fun options(o: JsonObject): WriterOptions {
        fun bool(k: String, default: Boolean = false) = o[k]?.jsonPrimitive?.boolean ?: default
        fun number(k: String, default: Long) = o[k]?.jsonPrimitive?.long ?: default
        fun text(k: String, default: String = "") = o[k]?.jsonPrimitive?.content ?: default
        val template = o["template"]?.takeIf { it != JsonNull }?.let {
            when (val r = TemplateCompiler(TemplateValues.Text, PandocTemplates().defaultPartials).compile("fixture.html", it.jsonPrimitive.content)) {
                is CompiledTemplate.Success -> r.template
                is CompiledTemplate.Failure -> throw PandocError.PandocTemplateError(r.error)
            }
        }
        val actualTemplate = template ?: o["defaultTemplate"]?.let { PandocTemplates().compileDefaultTemplate(it.jsonPrimitive.content) }
        return WriterOptions(template = actualTemplate, variables = o["variables"]?.let(TemplateValues.Text::contextFromJson) ?: Context(),
            tabStop = number("tabStop",4),tableOfContents = bool("tableOfContents"),listOfFigures = bool("listOfFigures"),listOfTables = bool("listOfTables"),
            incremental = bool("incremental"),mathMethod = o["mathMethod"]?.let(MathMethod::fromJson) ?: MathMethod.MathML,
            numberSections = bool("numberSections"),numberOffset = o["numberOffset"]?.jsonArray?.map { it.jsonPrimitive.long } ?: listOf(0,0,0,0,0,0),
            sectionDivs = bool("sectionDivs"),extensions = o["extensions"]?.let(Extensions::fromJson) ?: Extensions.Empty,
            referenceLinks = bool("referenceLinks"),dpi = number("dpi",96),wrapText = o["wrapText"]?.let(WrapOption::fromJson) ?: WrapOption.WrapNone,
            columns = number("columns",72),emailObfuscation = o["emailObfuscation"]?.let(ObfuscationMethod::fromJson) ?: ObfuscationMethod.NoObfuscation,
            identifierPrefix = text("identifierPrefix"),citeMethod = o["citeMethod"]?.let(CiteMethod::fromJson) ?: CiteMethod.Citeproc,
            htmlQTags = bool("htmlQTags"),slideLevel = o["slideLevel"]?.takeIf { it != JsonNull }?.jsonPrimitive?.long,
            topLevelDivision = o["topLevelDivision"]?.let(TopLevelDivision::fromJson) ?: TopLevelDivision.TopLevelDefault,
            highlightMethod = o["highlightMethod"]?.let(HighlightMethod::fromJson) ?: HighlightMethod.DefaultHighlighting,
            setextHeaders = bool("setextHeaders"),listTables = bool("listTables"),epubSubdirectory = text("epubSubdirectory","EPUB"),
            epubMetadata = o["epubMetadata"]?.takeIf { it != JsonNull }?.jsonPrimitive?.content,
            epubFonts = o["epubFonts"]?.jsonArray?.map { CharacterStringChars.fromString(it.jsonPrimitive.content) } ?: emptyList(),
            epubTitlePage = bool("epubTitlePage",true),splitLevel = number("splitLevel",1),tocDepth = number("tocDepth",3),
            referenceDoc = o["referenceDoc"]?.takeIf { it != JsonNull }?.jsonPrimitive?.content?.let(CharacterStringChars::fromString),
            referenceLocation = o["referenceLocation"]?.let(ReferenceLocation::fromJson) ?: ReferenceLocation.EndOfDocument,
            figureCaptionPosition = o["figureCaptionPosition"]?.let(CaptionPosition::fromJson) ?: CaptionPosition.CaptionBelow,
            tableCaptionPosition = o["tableCaptionPosition"]?.let(CaptionPosition::fromJson) ?: CaptionPosition.CaptionAbove,
            preferAscii = bool("preferAscii"),linkImages = bool("linkImages"))
    }
    private fun runOne(a: JsonArray, runtime: Runtime): JsonElement {
        val opts = options(a[2].jsonObject)
        val doc = PandocJson.decode(a[1])
        val output = when (a[0].jsonPrimitive.content) {
            "html4" -> HtmlWriter.writeHtml4String(opts,doc,runtime)
            "html5" -> HtmlWriter.writeHtml5String(opts,doc,runtime)
            "html4-markup" -> BlazeRenderer.renderText(HtmlWriter.writeHtml4(opts,doc,runtime))
            "html5-markup" -> BlazeRenderer.renderText(HtmlWriter.writeHtml5(opts,doc,runtime))
            "epub2" -> HtmlWriter.writeHtmlStringForEPUB(EPUBVersion.EPUB2,opts,doc,runtime)
            "epub3" -> HtmlWriter.writeHtmlStringForEPUB(EPUBVersion.EPUB3,opts,doc,runtime)
            "s5" -> HtmlWriter.writeS5(opts,doc,runtime)
            "slidy" -> HtmlWriter.writeSlidy(opts,doc,runtime)
            "slideous" -> HtmlWriter.writeSlideous(opts,doc,runtime)
            "dzslides" -> HtmlWriter.writeDZSlides(opts,doc,runtime)
            "revealjs" -> HtmlWriter.writeRevealJs(opts,doc,runtime)
            else -> error("writer")
        }
        return buildJsonObject { put("output",output);put("log",logs(runtime.getLog())) }
    }
    private fun run(a: JsonArray): JsonElement = try {
        when (a[0].jsonPrimitive.content) {
            "sequence" -> { val runtime = Runtime();JsonArray(a[1].jsonArray.map { runOne(it.jsonArray,runtime) }) }
            "tag" -> {
                val v = a[5].jsonArray
                val attr = Attr(v[0].jsonPrimitive.content,v[1].jsonArray.map { it.jsonPrimitive.content },v[2].jsonArray.map { it.jsonArray.let { it[0].jsonPrimitive.content to it[1].jsonPrimitive.content } })
                JsonPrimitive(HtmlWriter.tagWithAttributes(options(a[1].jsonObject),a[2].jsonPrimitive.boolean,a[3].jsonPrimitive.boolean,a[4].jsonPrimitive.content,attr))
            }
            else -> runOne(a, Runtime())
        }
    } catch (error: PandocError) { buildJsonObject { put("error",error.toString()) } }

    @org.junit.jupiter.api.Test
    fun originalSourceExamples() {
        val cases = listOf(
            "Code (\"\",[],[]) \"@&\"" to "<code>@&amp;</code>",
            "Code (\"\",[\"haskell\"],[]) \"\u003e\u003e=\"" to "<code class=\"sourceCode haskell\"><span class=\"op\">&gt;&gt;=</span></code>",
            "Code (\"\",[\"nolanguage\"],[]) \"\u003e\u003e=\"" to "<code class=\"nolanguage\">&gt;&gt;=</code>",
            "Image (\"\",[],[]) [Str \"my\",Space,Emph [Str \"image\"]] (\"/url\",\"title\")" to "<img src=\"/url\" title=\"title\" alt=\"my image\" />",
            "DefinitionList [([] , [[Para [Str \"foo bar\"]]])]" to "<dl>\n<dt></dt>\n<dd>\n<p>foo bar</p>\n</dd>\n</dl>",
            "Header 1 (\"\",[],[(\"invalid\",\"1\"),(\"lang\",\"en\")]) [Str \"test\"]" to "<h1 lang=\"en\">test</h1>",
            "Quoted DoubleQuote [Span (\"\",[],[(\"cite\",\"http://example.org\")]) [Str \"examples\"]]" to "“<span cite=\"http://example.org\">examples</span>”",
            "Code (\"\",[],[]) \"Answer is 42\"" to "<code>Answer is 42</code>",
            "Code (\"\",[\"sample\"],[]) \"Answer is 42\"" to "<samp>Answer is 42</samp>",
            "Code (\"\",[\"variable\"],[]) \"result\"" to "<var>result</var>",
            "Code (\"\",[\"haskell\",\"sample\"],[]) \"\u003e\u003e=\"" to "<samp><code class=\"sourceCode haskell sample\"><span class=\"op\">&gt;&gt;=</span></code></samp>",
            "Code (\"\",[\"haskell\",\"variable\"],[]) \"\u003e\u003e=\"" to "<var><code class=\"sourceCode haskell variable\"><span class=\"op\">&gt;&gt;=</span></code></var>",
        )
        val opts = WriterOptions(wrapText = WrapOption.WrapNone)
        for ((input,expected) in cases) assertEquals(expected, HtmlWriter.writeHtml4String(opts,io.heapy.kotdoc.formats.NativeReader.read(input)),input)
        val quoted = io.heapy.kotdoc.formats.NativeReader.read("Quoted DoubleQuote [Span (\"\",[],[(\"cite\",\"http://example.org\")]) [Str \"examples\"]]")
        assertEquals("<q cite=\"http://example.org\">examples</q>",HtmlWriter.writeHtml4String(opts.copy(htmlQTags = true),quoted))
    }

    @TestFactory
    fun completeOriginalHtmlWriterObservations(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/html-writer/data.jsonl.gz"))).bufferedReader().use { reader ->
        reader.readLine()
        reader.lineSequence().mapIndexed { i,line ->
            var transport: String? = line
            DynamicTest.dynamicTest("HTML writer ${i+1}") {
                val c = Json.parseToJsonElement(checkNotNull(transport)).jsonObject;transport = null
                val result = run(c.getValue("request").jsonArray)
                assertLogJsonEquals(c.getValue("expected"), result)
                c["originalExpected"]?.let { expected ->
                    val actual = result.jsonObject.getValue("output").jsonPrimitive.content
                    fun lines(s: String) = if (s.endsWith('\n')) s.dropLast(1) else s
                    assertEquals(lines(expected.jsonPrimitive.content), lines(actual))
                }
            }
        }.toList()
    }
}
