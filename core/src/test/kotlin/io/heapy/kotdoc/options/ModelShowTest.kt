package io.heapy.kotdoc.options

import io.heapy.kotdoc.templates.*
import io.heapy.kotdoc.layout.*
import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.highlighting.*
import io.heapy.kotdoc.highlighting.Context as SyntaxContext
import io.heapy.kotdoc.templates.Context
import io.heapy.kotdoc.highlighting.regex.RE
import io.heapy.kotdoc.structure.PathTemplate
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.*

class ModelShowTest {
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
    private fun variable(v: JsonElement) = Variable(v.jsonArray[0].jsonArray.map { it.s() }, v.jsonArray[1].jsonArray.map(::pipe))
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
    private fun JsonElement.b() = jsonPrimitive.boolean
    private fun JsonElement.nInt() = jsonPrimitive.int
    private fun chars(v: JsonElement) = v.jsonArray.map { it.nInt() }
    private fun re(v: JsonElement): RE { val a = v.jsonArray; return RE(a[0].s().hexToByteArray(), a[1].b(), a[2].b()) }
    private fun kw(v: JsonElement): KeywordAttr { val a = v.jsonArray; return KeywordAttr(a[0].b(), chars(a[1])) }
    private fun ws(v: JsonElement): WordSet<String> {
        val a = v.jsonArray; val words = a[1].jsonArray.map { it.s() }
        return if (a[0].b()) WordSet.CaseSensitiveWords(words, TextWordOperations) else WordSet.CaseInsensitiveWords(words, TextWordOperations)
    }
    private fun ctx(v: JsonElement): ContextName { val a = v.jsonArray; return ContextName(a[0].s(), a[1].s()) }
    private fun matcher(v: JsonElement): Matcher {
        val a = v.jsonArray
        return when (a[0].s()) {
            "DetectChar" -> Matcher.DetectChar(a[1].nInt())
            "Detect2Chars" -> Matcher.Detect2Chars(a[1].nInt(), a[2].nInt())
            "AnyChar" -> Matcher.AnyChar(chars(a[1]))
            "RangeDetect" -> Matcher.RangeDetect(a[1].nInt(), a[2].nInt())
            "StringDetect" -> Matcher.StringDetect(a[1].s())
            "WordDetect" -> Matcher.WordDetect(a[1].s())
            "RegExpr" -> Matcher.RegExpr(re(a[1]))
            "Keyword" -> { val w = a[2].jsonArray; Matcher.Keyword(kw(a[1]), if (w[0].s() == "Left") KeywordWords.Unresolved(w[1].s()) else KeywordWords.Resolved(ws(w[1]))) }
            "Int" -> Matcher.Int; "Float" -> Matcher.Float; "HlCOct" -> Matcher.HlCOct; "HlCHex" -> Matcher.HlCHex
            "HlCStringChar" -> Matcher.HlCStringChar; "HlCChar" -> Matcher.HlCChar
            "LineContinue" -> Matcher.LineContinue(a[1].nInt())
            "IncludeRules" -> Matcher.IncludeRules(ctx(a[1]))
            "DetectSpaces" -> Matcher.DetectSpaces; "DetectIdentifier" -> Matcher.DetectIdentifier
            else -> error(a[0].s())
        }
    }
    private fun switch(v: JsonElement): ContextSwitch = if (v.jsonArray.isEmpty()) ContextSwitch.Pop else ContextSwitch.Push(ctx(v.jsonArray[0]))
    private fun item(v: JsonElement): ListItem { val a = v.jsonArray; return if (a[0].s() == "Item") ListItem.Item(a[1].s()) else ListItem.IncludeList(ctx(a[1])) }
    private fun rule(v: JsonElement): Rule { val a = v.jsonArray; return Rule(matcher(a[0]), TokenType.valueOf(a[1].s()), a[2].b(), chars(a[3]), a[4].b(), a[5].b(), a[6].jsonArray.map(::rule), a[7].b(), a[8].b(), a[9].takeUnless { it == JsonNull }?.jsonPrimitive?.long, a[10].jsonArray.map(::switch)) }
    private fun syntaxContext(v: JsonElement): SyntaxContext { val a = v.jsonArray; return SyntaxContext(a[0].s(), a[1].s(), a[2].jsonArray.map(::rule), TokenType.valueOf(a[3].s()), a[4].jsonArray.map(::switch), a[5].jsonArray.map(::switch), a[6].b(), a[7].jsonArray.map(::switch), a[8].b()) }
    private fun <A> map(v: JsonElement, f: (JsonElement) -> A): Map<String, A> = v.jsonArray.associate { val a = it.jsonArray; a[0].s() to f(a[1]) }
    private fun syntax(v: JsonElement): Syntax { val a = v.jsonArray; return Syntax(a[0].s(), a[1].s(), a[2].s(), map(a[3]) { it.jsonArray.map(::item) }, map(a[4], ::syntaxContext), a[5].s(), a[6].s(), a[7].s(), a[8].jsonArray.map { it.s() }, a[9].s()) }

    private fun extension(v: JsonElement): Extension {
        val a = v.jsonArray
        return if (a[0].s() == "custom") Extension.Custom(a[1].s()) else
            Extension.Builtin(ExtensionName.entries.single { it.wireName == a[1].s() })
    }
    private fun math(v: JsonElement): MathMethod {
        val a = v.jsonArray
        return when (a[0].s()) {
            "PlainMath" -> MathMethod.PlainMath; "GladTeX" -> MathMethod.GladTeX; "MathML" -> MathMethod.MathML
            "WebTeX" -> MathMethod.WebTeX(a[1].s()); "MathJax" -> MathMethod.MathJax(a[1].s()); "KaTeX" -> MathMethod.KaTeX(a[1].s())
            else -> error(v)
        }
    }
    private fun highlight(v: JsonElement): HighlightMethod = if (v is JsonObject) HighlightMethod.Skylighting(HighlightStyle.fromJson(v))
        else when (v.s()) {
            "NoHighlighting" -> HighlightMethod.NoHighlighting; "DefaultHighlighting" -> HighlightMethod.DefaultHighlighting
            "IdiomaticHighlighting" -> HighlightMethod.IdiomaticHighlighting; else -> error(v)
        }
    private fun options(v: JsonElement): WriterOptions {
        val o = WriterOptions(syntaxMap = emptyMap())
        val p = v.jsonObject
        fun bool(k: String, default: Boolean) = p[k]?.jsonPrimitive?.boolean ?: default
        fun num(k: String, default: Long) = p[k]?.n() ?: default
        fun str(k: String, default: String) = p[k]?.s() ?: default
        fun nullable(k: String) = p[k]?.takeUnless { it == JsonNull }
        return o.copy(
            template = nullable("template")?.let(::template),
            variables = p["variables"]?.let { (value(it) as Val.MapVal).context } ?: o.variables,
            tabStop = num("tabStop",o.tabStop),
            tableOfContents = bool("tableOfContents",o.tableOfContents),
            listOfFigures = bool("listOfFigures",o.listOfFigures),
            listOfTables = bool("listOfTables",o.listOfTables),
            incremental = bool("incremental",o.incremental),
            mathMethod = p["mathMethod"]?.let(::math) ?: o.mathMethod,
            numberSections = bool("numberSections",o.numberSections),
            numberOffset = p["numberOffset"]?.jsonArray?.map { it.n() } ?: o.numberOffset,
            sectionDivs = bool("sectionDivs",o.sectionDivs),
            extensions = p["extensions"]?.jsonArray?.map(::extension)?.let(Extensions::from) ?: o.extensions,
            referenceLinks = bool("referenceLinks",o.referenceLinks),
            dpi = num("dpi",o.dpi),
            wrapText = p["wrapText"]?.s()?.let(WrapOption::valueOf) ?: o.wrapText,
            columns = num("columns",o.columns),
            emailObfuscation = p["emailObfuscation"]?.s()?.let(ObfuscationMethod::valueOf) ?: o.emailObfuscation,
            identifierPrefix = str("identifierPrefix",o.identifierPrefix),
            citeMethod = p["citeMethod"]?.s()?.let(CiteMethod::valueOf) ?: o.citeMethod,
            htmlQTags = bool("htmlQTags",o.htmlQTags),
            slideLevel = nullable("slideLevel")?.n(),
            topLevelDivision = p["topLevelDivision"]?.s()?.let(TopLevelDivision::valueOf) ?: o.topLevelDivision,
            highlightMethod = p["highlightMethod"]?.let(::highlight) ?: o.highlightMethod,
            setextHeaders = bool("setextHeaders",o.setextHeaders),
            listTables = bool("listTables",o.listTables),
            epubSubdirectory = str("epubSubdirectory",o.epubSubdirectory),
            epubMetadata = nullable("epubMetadata")?.s(),
            epubFonts = p["epubFonts"]?.jsonArray?.map { CharacterStringChars.fromString(it.s()) } ?: o.epubFonts,
            epubTitlePage = bool("epubTitlePage",o.epubTitlePage),
            splitLevel = num("splitLevel",o.splitLevel),
            chunkTemplate = p["chunkTemplate"]?.s()?.let(::PathTemplate) ?: o.chunkTemplate,
            tocDepth = num("tocDepth",o.tocDepth),
            referenceDoc = nullable("referenceDoc")?.s()?.let(CharacterStringChars::fromString),
            referenceLocation = p["referenceLocation"]?.s()?.let(ReferenceLocation::valueOf) ?: o.referenceLocation,
            figureCaptionPosition = p["figureCaptionPosition"]?.s()?.let(CaptionPosition::valueOf) ?: o.figureCaptionPosition,
            tableCaptionPosition = p["tableCaptionPosition"]?.s()?.let(CaptionPosition::valueOf) ?: o.tableCaptionPosition,
            syntaxMap = p["syntaxMap"]?.let { if (it is JsonPrimitive && it.s() == "builtin") Syntaxes.defaultSyntaxMap else map(it,::syntax) } ?: o.syntaxMap,
            preferAscii = bool("preferAscii",o.preferAscii),
            linkImages = bool("linkImages",o.linkImages),
        )
    }
    private fun observe(c: JsonObject): String {
        val v = c.getValue("input")
        val p = c.getValue("precedence").jsonPrimitive.int
        val show = TemplateShow.Text
        return when (c.getValue("op").s()) {
            "writer" -> options(v).haskellShow(p)
            "writer-path-points" -> {
                val path = CharacterString(v.jsonArray.map { it.jsonPrimitive.int }.toIntArray())
                WriterOptions(syntaxMap = emptyMap(),referenceDoc = path,epubFonts = listOf(path)).haskellShow(p)
            }
            "writer-text-points" -> {
                val t = TextChars.fromPoints(v.jsonArray.map { it.jsonPrimitive.int }.toIntArray())
                WriterOptions(syntaxMap = emptyMap(),identifierPrefix = t,epubSubdirectory = t,epubMetadata = t,chunkTemplate = PathTemplate(t),mathMethod = MathMethod.WebTeX(t)).haskellShow(p)
            }
            "template" -> show.template(template(v),p)
            "value" -> show.value(value(v),p)
            "context" -> show.context((value(v) as Val.MapVal).context,p)
            "doc" -> show.doc(doc(v),p)
            "variable" -> show.variable(variable(v),p)
            "pipe" -> show.pipe(pipe(v),p)
            "border" -> show.border(Border(v.jsonArray[0].s(),v.jsonArray[1].s()),p)
            "font" -> { val a = part(JsonArray(listOf(JsonNull,v,JsonPrimitive("")))); show.font(a.font,p) }
            "style" -> show.style(style(v),p)
            "extension" -> extension(v).haskellShow(p)
            "extensions" -> Extensions.from(v.jsonArray.map(::extension)).haskellShow(p)
            "math" -> math(v).haskellShow(p)
            "highlight" -> highlight(v).haskellShow(p)
            "doc-int" -> TemplateShow<Long> { n, q -> if (n < 0 && q > 6) "($n)" else n.toString() }.doc(doc(v).mapValues(String::toLong),p)
            "doc-string-points" -> TemplateShow.String.doc(Doc.Text(v.jsonArray[0].n(),CharacterString(v.jsonArray[1].jsonArray.map { it.jsonPrimitive.int }.toIntArray())),p)
            else -> error(c)
        }
    }
    @TestFactory fun nativeShowCases(): List<DynamicTest> {
        val rows = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/options/model-show.jsonl.gz")))
            .bufferedReader().use { it.readLines().drop(1) }
        return rows.mapIndexed { i, raw -> DynamicTest.dynamicTest("native Show $i") {
            val c = Json.parseToJsonElement(raw).jsonObject
            assertEquals(c.getValue("expected").jsonPrimitive.content,observe(c),"case $i ${c.getValue("op")}")
        } }
    }
    @Test fun filePathCharsPreserveOwnershipAndCopy() {
        val points = intArrayOf(0xd800,0xdc00)
        val path = CharacterString(points)
        val o = WriterOptions(syntaxMap = emptyMap(),referenceDoc = path,epubFonts = listOf(path))
        points[0] = 65
        path.points()[0] = 66
        val copy = o.copy(columns = 0)
        assertContentEquals(intArrayOf(0xd800,0xdc00),copy.referenceDoc!!.points())
        assertEquals(listOf(path),copy.epubFonts)
        assertTrue(copy.haskellShow().contains("writerReferenceDoc = Just \"\\55296\\56320\""))
        assertNotEquals(path,CharacterString(intArrayOf(0x10000)))
    }
    @Test fun deepTreesAndTargetPrecedence() {
        var t: Template<String> = Template.Empty
        repeat(10_000) { t = Template.Nested(t) }
        val shown = TemplateShow.Text.template(t)
        assertEquals("Nested (".repeat(9999) + "Nested Empty" + ")".repeat(9999), shown)
        var v: Val<String> = Val.NullVal
        repeat(10_000) { v = Val.ListVal(listOf(v)) }
        assertEquals("ListVal [".repeat(10_000) + "NullVal" + "]".repeat(10_000),TemplateShow.Text.value(v))
        val calls = mutableListOf<Pair<String,Int>>()
        val custom = TemplateShow<String> { x,p -> calls.add(x to p); if (p > 10) "(Box ${HaskellString.quote(x)})" else "Box ${HaskellString.quote(x)}" }
        assertEquals("Text 0 (Box \"x\")",custom.doc(Doc.Text(0,"x")))
        assertEquals(listOf("x" to 11), calls)
        val o = WriterOptions(syntaxMap = emptyMap())
        assertEquals(o.haskellShow(),o.toString())
    }
}
