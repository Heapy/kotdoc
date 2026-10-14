package io.heapy.kotdoc.options

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.formats.*
import io.heapy.kotdoc.highlighting.*
import io.heapy.kotdoc.templates.*
import io.heapy.kotdoc.structure.PathTemplate
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.*

class WriterOptionsTest {
    private fun JsonElement.s()=jsonPrimitive.content
    private val values=TemplateValues.Text
    private fun options(p: JsonObject): WriterOptions {
        val base=WriterOptions()
        fun integer(key: String,default: Long)=p[key]?.s()?.toLong() ?: default
        fun bool(key: String,default: Boolean)=p[key]?.jsonPrimitive?.boolean ?: default
        fun string(key: String,default: String)=p[key]?.s() ?: default
        val template=p["template"]?.let { assertIs<CompiledTemplate.Success<String>>(TemplateCompiler(values).compile("templates/default.custom",it.s())).template }
        return base.copy(
            tableOfContents=bool("table_of_contents",base.tableOfContents),
            listOfFigures=bool("list_of_figures",base.listOfFigures),
            listOfTables=bool("list_of_tables",base.listOfTables),
            incremental=bool("incremental",base.incremental),
            numberSections=bool("number_sections",base.numberSections),
            sectionDivs=bool("section_divs",base.sectionDivs),
            referenceLinks=bool("reference_links",base.referenceLinks),
            htmlQTags=bool("html_q_tags",base.htmlQTags),
            setextHeaders=bool("setext_headers",base.setextHeaders),
            listTables=bool("list_tables",base.listTables),
            epubTitlePage=bool("epub_title_page",base.epubTitlePage),
            preferAscii=bool("prefer_ascii",base.preferAscii),
            linkImages=bool("link_images",base.linkImages),
            tabStop=integer("tab_stop",base.tabStop),
            dpi=integer("dpi",base.dpi),
            columns=integer("columns",base.columns),
            splitLevel=integer("split_level",base.splitLevel),
            tocDepth=integer("toc_depth",base.tocDepth),
            identifierPrefix=string("identifier_prefix",base.identifierPrefix),
            epubSubdirectory=string("epub_subdirectory",base.epubSubdirectory),
            wrapText=p["wrap_text"]?.let(WrapOption::fromJson) ?: base.wrapText,
            emailObfuscation=p["email_obfuscation"]?.let(ObfuscationMethod::fromJson) ?: base.emailObfuscation,
            citeMethod=p["cite_method"]?.let(CiteMethod::fromJson) ?: base.citeMethod,
            topLevelDivision=p["top_level_division"]?.let(TopLevelDivision::fromJson) ?: base.topLevelDivision,
            referenceLocation=p["reference_location"]?.let(ReferenceLocation::fromJson) ?: base.referenceLocation,
            figureCaptionPosition=p["figure_caption_position"]?.let(CaptionPosition::fromJson) ?: base.figureCaptionPosition,
            tableCaptionPosition=p["table_caption_position"]?.let(CaptionPosition::fromJson) ?: base.tableCaptionPosition,
            mathMethod=p["html_math_method"]?.let(MathMethod::fromJson) ?: base.mathMethod,
            highlightMethod=p["highlight_method"]?.let(HighlightMethod::fromJson) ?: base.highlightMethod,
            numberOffset=p["number_offset"]?.jsonArray?.map { it.s().toLong() } ?: base.numberOffset,
            epubFonts=p["epub_fonts"]?.jsonArray?.map { it.s() } ?: base.epubFonts,
            epubMetadata=p["epub_metadata"]?.s(),referenceDoc=p["reference_doc"]?.s(),slideLevel=p["slide_level"]?.s()?.toLong(),
            chunkTemplate=p["chunk_template"]?.let(PathTemplate::fromJson) ?: base.chunkTemplate,
            extensions=p["extensions"]?.let { if(it is JsonArray) Extensions.from(it.map { Extension.read(it.s()) }) else Extensions.Empty } ?: base.extensions,
            variables=p["variables"]?.let(values::contextFromJson) ?: base.variables,
            template=template,
        )
    }
    // Observe the exact native Lua protocol; this is not a public JSON instance.
    private fun record(o: WriterOptions)=buildJsonObject {
        fun list(key: String,xs: List<String>) { put(key,if(xs.isEmpty()) JsonObject(emptyMap()) else JsonArray(xs.map(::JsonPrimitive))) }
        put("table_of_contents",o.tableOfContents)
        put("list_of_figures",o.listOfFigures)
        put("list_of_tables",o.listOfTables)
        put("incremental",o.incremental)
        put("number_sections",o.numberSections)
        put("section_divs",o.sectionDivs)
        put("reference_links",o.referenceLinks)
        put("html_q_tags",o.htmlQTags)
        put("setext_headers",o.setextHeaders)
        put("list_tables",o.listTables)
        put("epub_title_page",o.epubTitlePage)
        put("prefer_ascii",o.preferAscii)
        put("link_images",o.linkImages)
        put("tab_stop",o.tabStop.toString())
        put("dpi",o.dpi.toString())
        put("columns",o.columns.toString())
        put("split_level",o.splitLevel.toString())
        put("toc_depth",o.tocDepth.toString())
        put("identifier_prefix",o.identifierPrefix)
        put("epub_subdirectory",o.epubSubdirectory)
        put("wrap_text",o.wrapText.toJson())
        put("email_obfuscation",o.emailObfuscation.toJson())
        put("cite_method",o.citeMethod.toJson())
        put("top_level_division",o.topLevelDivision.toJson())
        put("reference_location",o.referenceLocation.toJson())
        put("figure_caption_position",o.figureCaptionPosition.toJson())
        put("table_caption_position",o.tableCaptionPosition.toJson())
        put("html_math_method",o.mathMethod.toJson());put("highlight_method",o.highlightMethod.toJson())
        put("epub_metadata",o.epubMetadata?.let(::JsonPrimitive) ?: JsonNull)
        put("reference_doc",o.referenceDoc?.let(::JsonPrimitive) ?: JsonNull)
        put("slide_level",o.slideLevel?.let { JsonPrimitive(it.toString()) } ?: JsonNull)
        put("chunk_template",o.chunkTemplate.toJson());put("variables",values.toJson(o.variables))
        put("template",if(o.template==null) JsonNull else JsonPrimitive(true))
        list("epub_fonts",o.epubFonts);list("number_offset",o.numberOffset.map(Long::toString));list("extensions",o.extensions.sortedNames())
    }
    private fun observe(c: JsonObject): JsonElement {
        val o=options(c.getValue("options").jsonObject)
        return buildJsonObject {
            put("record",record(o))
            when(c.getValue("op").s()) {
                "native"->put("text",NativeWriter.write(NativeReader.read(c.getValue("input").s()),o))
                "fb2"->put("text",FB2Writer.write(NativeReader.read(c.getValue("input").s()),o))
                "template"->put("rendered",values.layout.render(null,TemplateRenderer(values).render(checkNotNull(o.template),o.variables)))
            }
        }
    }
    @TestFactory fun originalWriterOptions(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/options/writer-options.jsonl.gz"))).bufferedReader().use { reader ->
        val m=Json.parseToJsonElement(checkNotNull(reader.readLine())).jsonObject;val rows=reader.readLines();assertEquals(m.getValue("cases").jsonPrimitive.int,rows.size)
        rows.mapIndexed { index,row ->DynamicTest.dynamicTest("$index writer options") { val c=Json.parseToJsonElement(row).jsonObject;assertEquals(c.getValue("expected"),observe(c),"case $index: $row") } }
    }
    @Test fun copyPreservesTypedPayloadsAndOwnership() {
        val offsets=mutableListOf<Long>(1,2,3);val fonts=mutableListOf("x.ttf")
        val map=mutableMapOf("C++" to Syntaxes.defaultSyntaxMap.getValue("C++"))
        val compiled=assertIs<CompiledTemplate.Success<String>>(TemplateCompiler(values).compile("x.tpl","\$body\$")).template
        val context=io.heapy.kotdoc.templates.Context(mapOf("body" to Val.BoolVal(true)))
        val original=WriterOptions(template=compiled,variables=context,numberOffset=offsets,epubFonts=fonts,syntaxMap=map,
            extensions=Extensions.from(listOf(Extension.Builtin(ExtensionName.Strikeout))),columns=Long.MAX_VALUE)
        val copied=original.copy(columns=Long.MIN_VALUE,numberSections=true)
        offsets.clear();fonts.clear();map.clear()
        assertSame(compiled,copied.template);assertSame(context,copied.variables)
        assertEquals(listOf<Long>(1,2,3),copied.numberOffset);assertEquals(listOf("x.ttf"),copied.epubFonts);assertEquals(1,copied.syntaxMap.size)
        assertEquals(Long.MAX_VALUE,original.columns);assertEquals(Long.MIN_VALUE,copied.columns);assertTrue(copied.numberSections);assertFalse(original.numberSections)
        assertTrue(copied.isEnabled(ExtensionName.Strikeout))
        assertFailsWith<UnsupportedOperationException> { (copied.numberOffset as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (copied.epubFonts as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (copied.syntaxMap as MutableMap).clear() }
        assertSame(Syntaxes.defaultSyntaxMap,WriterOptions().syntaxMap)
        assertEquals(195,WriterOptions().syntaxMap.size)
        assertEquals(WriterOptions().numberOffset,WriterOptions().copy().numberOffset)
    }
}
