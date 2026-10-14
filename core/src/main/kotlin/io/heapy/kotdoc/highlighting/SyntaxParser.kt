// Skylighting.Parser 0.15, Copyright (c) 2016-2024 John MacFarlane.
// BSD-3-Clause; licenses/Skylighting-core-BSD-3-Clause.txt.
package io.heapy.kotdoc.highlighting

import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.highlighting.regex.RE
import io.heapy.kotdoc.highlighting.regex.RERead
import io.heapy.kotdoc.parsing.ParserCharacters
import io.heapy.kotdoc.parsing.RegexCharacters
import io.heapy.kotdoc.templates.PartialPaths
import io.heapy.kotdoc.xml.XmlDocElement
import io.heapy.kotdoc.xml.XmlDocumentParser
import io.heapy.kotdoc.xml.XmlDocumentResult
import io.heapy.kotdoc.xml.XmlDocName
import java.nio.file.Files
import java.nio.file.Path

sealed interface SyntaxParseResult {
    class Success(val syntax: Syntax) : SyntaxParseResult
    class Failure(val error: String) : SyntaxParseResult
}
/** Source `error`, distinct from an ordinary ExceptT/Left parser error. */
class InvalidIncludeRulesException : IllegalStateException("IncludeRules doesn't specify a syntax and context")
class SyntaxUtf8Exception(val byte: Int) : IllegalArgumentException(
    "Cannot decode byte '\\x${byte.toString(16)}': Data.Text.Internal.Encoding: Invalid UTF-8 stream"
)

object SyntaxParser {
    private class ParseFailure(val error: String) : RuntimeException(null, null, false, false)
    private fun fail(error: String): Nothing = throw ParseFailure(error)
    private val standardDelimiters = " \n\t.():!+,-<=>%&*/;?[]^{|}~\\".codePoints().toArray().toSet()
    private fun bool(default: Boolean, value: String): Boolean = when (value) {
        "true", "yes", "1" -> true; "false", "no", "0" -> false; else -> default
    }
    // xml-types Name equality ignores prefixes, even when the prefix is undeclared.
    private fun XmlDocName.named(name: String) = namespace == null && local == name
    private fun XmlDocElement.elements(name: String) = children.filter { it.name.named(name) }
    private fun XmlDocElement.attribute(name: String): String? = attributes.entries.lastOrNull { it.key.named(name) }?.value
    private fun XmlDocElement.value(name: String) = attribute(name).orEmpty()
    private fun strip(text: String): String {
        val points = text.codePoints().toArray(); var start = 0; var end = points.size
        while (start < end && ParserCharacters.isSpace(points[start])) start++
        while (end > start && ParserCharacters.isSpace(points[end - 1])) end--
        return buildString { for (i in start..<end) appendCodePoint(points[i]) }
    }
    private fun words(text: String): List<String> {
        val output = mutableListOf<String>(); var word = StringBuilder()
        for (point in text.codePoints().toArray()) if (point == ';'.code || ParserCharacters.isSpace(point)) {
            if (word.isNotEmpty()) { output.add(word.toString()); word = StringBuilder() }
        } else word.appendCodePoint(point)
        if (word.isNotEmpty()) output.add(word.toString())
        return output
    }
    private fun splitContext(text: String): ContextName {
        val value = strip(text); val split = value.indexOf("##")
        return if (split < 0) ContextName("", value) else ContextName(value.substring(split + 2), value.substring(0, split))
    }
    private fun switches(syntax: String, value: String): List<ContextSwitch> {
        val result = mutableListOf<ContextSwitch>(); var text = value
        while (true) {
            if (text.isEmpty() || text == "#stay") break
            if (text.startsWith("#pop")) { result.add(ContextSwitch.Pop); text = text.drop(4); continue }
            for (part in text.split('!').filter { it.isNotEmpty() }) {
                val context = splitContext(part)
                result.add(ContextSwitch.Push(ContextName(context.syntax.ifEmpty { syntax }, context.context)))
            }
            break
        }
        return result
    }
    private fun readChar(text: String): Int {
        val points = text.codePoints().toArray()
        return if (points.size == 1) points[0] else RERead.readCharacterLiteral("'$text'") ?: 0xffff
    }
    private fun decimal(text: String): Long? {
        if (text.isEmpty() || text[0] !in '0'..'9') return null
        var number = 0L
        for (char in text) { if (char !in '0'..'9') break; number = number * 10 + char.code - '0'.code }
        return number
    }
    private fun tokenType(text: String): TokenType = when (text) {
        "dsNormal" -> TokenType.NormalTok; "dsOthers" -> TokenType.OtherTok
        else -> TokenType.entries.firstOrNull { it != TokenType.OtherTok && "ds" + it.name.removeSuffix("Tok") == text } ?: TokenType.NormalTok
    }
    private fun nameShow(name: XmlDocName): String {
        fun maybe(value: String?) = value?.let { "Just " + HaskellString.quote(it) } ?: "Nothing"
        return "Name {nameLocalName = ${HaskellString.quote(name.local)}, nameNamespace = ${maybe(name.namespace)}, namePrefix = ${maybe(name.prefix)}}"
    }
    private fun shortname(filename: String, paths: PartialPaths): String {
        val points = paths.baseName(filename).codePoints().toArray(); val camel = mutableListOf<Int>(); var i = 0
        while (i < points.size) {
            if (i + 1 < points.size && !ParserCharacters.isAlphaNum(points[i])) { camel.add(RegexCharacters.toUpper(points[i + 1])); i += 2 }
            else camel.add(points[i++])
        }
        if (camel.isNotEmpty()) camel[0] = RegexCharacters.toUpper(camel[0])
        return buildString { camel.forEach { appendCodePoint(it) } }
    }
    private fun keywordAttributes(sensitive: Boolean, root: XmlDocElement): KeywordAttr {
        val el = root.elements("general").flatMap { it.elements("keywords") }.firstOrNull()
            ?: return KeywordAttr(sensitive, standardDelimiters)
        return KeywordAttr(bool(sensitive, el.value("casesensitive")), delimiters(standardDelimiters, el))
    }
    private fun delimiters(base: Set<Int>, element: XmlDocElement): Set<Int> =
        (base + element.value("additionalDeliminator").codePoints().toArray().toSet()) -
            element.value("weakDeliminator").codePoints().toArray().toSet()

    fun parseSyntaxDefinitionFromText(path: String, text: String, paths: PartialPaths = PartialPaths.System): SyntaxParseResult =
        when (val parsed = XmlDocumentParser.parseText(text)) {
            is XmlDocumentResult.Failure -> SyntaxParseResult.Failure(parsed.error)
            is XmlDocumentResult.Success -> documentToSyntax(path, parsed.document.root, paths)
        }

    fun parseSyntaxDefinition(path: Path): SyntaxParseResult = parseSyntaxDefinitionBytes(path.toString(), Files.readAllBytes(path))

    fun parseSyntaxDefinitionFromTextChunks(path: String, chunks: List<String>, paths: PartialPaths = PartialPaths.System): SyntaxParseResult =
        when (val parsed = XmlDocumentParser.parseTextChunks(chunks)) {
            is XmlDocumentResult.Failure -> SyntaxParseResult.Failure(parsed.error)
            is XmlDocumentResult.Success -> documentToSyntax(path, parsed.document.root, paths)
        }

    fun parseSyntaxDefinitionBytes(path: String, bytes: ByteArray, paths: PartialPaths = PartialPaths.System): SyntaxParseResult {
        val decoded = SyntaxUtf8.decodeFile(bytes)
        return when (val parsed = XmlDocumentParser.parseTextChunks(decoded.chunks) { decoded.error?.let { throw it } }) {
            is XmlDocumentResult.Failure -> SyntaxParseResult.Failure(parsed.error)
            is XmlDocumentResult.Success -> documentToSyntax(path, parsed.document.root, paths)
        }
    }

    /** Convert an already parsed Text.XML-compatible root, without compiling its REs. */
    fun documentToSyntax(path: String, root: XmlDocElement, paths: PartialPaths = PartialPaths.System): SyntaxParseResult = try {
        if (!root.name.named("language")) fail("Root element is not language")
        val filename = paths.fileName(path)
        val highlighting = root.elements("highlighting").firstOrNull() ?: fail("No highlighting element")
        val lists = linkedMapOf<String, List<ListItem>>()
        for (list in highlighting.elements("list")) {
            val name = list.attribute("name") ?: fail("No name attribute on list")
            lists[name] = list.children.map { child -> when {
                child.name.named("item") -> ListItem.Item(strip(child.strContent))
                child.name.named("include") -> ListItem.IncludeList(splitContext(child.strContent))
                else -> fail("Unknown element ${nameShow(child.name)} in list")
            } }
        }
        val itemData = highlighting.elements("itemDatas").flatMap { it.elements("itemData") }
            .associate { it.value("name") to tokenType(it.value("defStyleNum")) }
        val kw = keywordAttributes(bool(true, root.value("casesensitive")), root)
        val name = root.value("name")
        val contexts = highlighting.elements("contexts").flatMap { it.elements("context") }.map { context ->
            val attribute = context.value("attribute")
            val end = switches(name, context.value("lineEndContext"))
            Context(context.value("name"), name, rules(name, itemData, kw, attribute, context.children),
                itemData[attribute] ?: TokenType.NormalTok,
                switches(name, context.value("lineEmptyContext")).ifEmpty { end }, end,
                bool(false, context.value("fallthrough")), switches(name, context.value("fallthroughContext")),
                bool(false, context.value("dynamic")))
        }
        val starting = contexts.firstOrNull()?.name ?: fail("No contexts")
        SyntaxParseResult.Success(Syntax(name, filename, shortname(filename, paths), lists, contexts.associateBy { it.name },
            root.value("author"), root.value("version"), root.value("license"), words(root.value("extensions")), starting))
    } catch (error: ParseFailure) { SyntaxParseResult.Failure(error.error) }

    private fun rules(syntax: String, itemData: Map<String, TokenType>, kw: KeywordAttr, contextAttribute: String, elements: List<XmlDocElement>): List<Rule> {
        data class Frame(val element: XmlDocElement, val inherited: String, val finish: Boolean)
        val work = ArrayDeque<Frame>(); val done = ArrayDeque<Rule>()
        elements.asReversed().forEach { work.addLast(Frame(it, contextAttribute, false)) }
        while (work.isNotEmpty()) {
            val frame = work.removeLast(); val el = frame.element; val attribute = el.value("attribute")
            if (!frame.finish) {
                work.addLast(frame.copy(finish = true))
                el.children.asReversed().forEach { work.addLast(Frame(it, attribute, false)) }
                continue
            }
            val children = List(el.children.size) { done.removeLast() }.asReversed()
            val name = el.name.local
            val insensitive = bool(false, el.value("insensitive"))
            val delims = delimiters(kw.delimiters, el)
            val rawText = el.value("String"); val anchored = name == "RegExpr" && rawText.startsWith('^')
            val text = if (anchored) rawText.drop(1) else rawText
            val switch = switches(syntax, el.value("context"))
            val matcher = when (name) {
                "DetectChar" -> Matcher.DetectChar(readChar(el.value("char")))
                "Detect2Chars" -> Matcher.Detect2Chars(readChar(el.value("char")), readChar(el.value("char1")))
                "AnyChar" -> Matcher.AnyChar(text.codePoints().toArray().toSet())
                "RangeDetect" -> Matcher.RangeDetect(readChar(el.value("char")), readChar(el.value("char1")))
                "StringDetect" -> Matcher.StringDetect(text)
                "WordDetect" -> Matcher.WordDetect(text)
                "RegExpr" -> Matcher.RegExpr(RE(text.toByteArray(Charsets.UTF_8), !insensitive, bool(false, el.value("minimal"))))
                "keyword" -> Matcher.Keyword(KeywordAttr(if (el.attribute("insensitive") != null) !insensitive else kw.caseSensitive, delims), KeywordWords.Unresolved(text))
                "Int" -> Matcher.Int; "Float" -> Matcher.Float; "HlCOct" -> Matcher.HlCOct; "HlCHex" -> Matcher.HlCHex
                "HlCStringChar" -> Matcher.HlCStringChar; "HlCChar" -> Matcher.HlCChar
                "LineContinue" -> Matcher.LineContinue(el.value("char").codePoints().findFirst().orElse('\\'.code))
                "IncludeRules" -> Matcher.IncludeRules(ContextName.deferred(
                    { (switch.firstOrNull() as? ContextSwitch.Push)?.context?.syntax ?: throw InvalidIncludeRulesException() },
                    { (switch.firstOrNull() as? ContextSwitch.Push)?.context?.context ?: throw InvalidIncludeRulesException() }))
                "DetectSpaces" -> Matcher.DetectSpaces; "DetectIdentifier" -> Matcher.DetectIdentifier
                else -> fail("Unknown element $name")
            }
            done.addLast(Rule(matcher, itemData[attribute.ifEmpty { frame.inherited }] ?: TokenType.NormalTok,
                bool(false, el.value("includeAttrib")), delims, bool(false, el.value("dynamic")), !insensitive, children,
                bool(false, el.value("lookAhead")), bool(false, el.value("firstNonSpace")),
                if (anchored) 0L else decimal(el.value("column")), if (name == "IncludeRules") emptyList() else switch))
        }
        return done.toList()
    }
}
