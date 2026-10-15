// Text.Pandoc.Readers.HTML.Parsing; © 2006-2024 John MacFarlane.
// GPL-2.0-or-later; unchanged original source in upstream/pandoc.
package io.heapy.kotdoc.html

import io.heapy.kotdoc.ast.Attr
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.xml.PandocXml

/** Opening/closing-tag omission policy from the original HTML reader. */
enum class TagOmission { TagsRequired, ClosingTagOptional, TagsOmittable }
typealias HtmlTagParser<U, A> = TokenParsec<HtmlTag, U, A>

object HtmlTagParsing {
    fun <U> pSatisfy(test: (HtmlTag) -> Boolean): HtmlTagParser<U, HtmlTag> = TokenParsec.primitive { state ->
        var input = state.input; var position = state.position
        var step = input.uncons()
        while (step?.token is HtmlTag.Position) {
            val token = step.token
            position = SourcePos("input", token.row, token.column); input = step.tail; step = input.uncons()
        }
        if (step != null && test(step.token)) {
            val next = state.copy(input = step.tail, position = position)
            ParserReply(true, step.token, next, true, ParseError(position))
        } else ParserReply(false, null, state, false, ParseError(position,
            listOf(ParseMessage(MessageKind.SysUnExpect, step?.token?.let(HtmlTagMatching::show) ?: ""))))
    }
    fun <U> pAny(): HtmlTagParser<U, HtmlTag> = pSatisfy { true }
    fun <U> pBlank(): HtmlTagParser<U, Unit> = pSatisfy<U> { tag -> when (tag) {
        is HtmlTag.Text -> tag.text.codePoints().allMatch(::isSpace)
        is HtmlTag.Comment -> true
        else -> false
    } }.map { }
    fun isSpace(point: Int) = point == 32 || point == 9 || point == 10 || point == 13
    fun matchTagOpen(name: String, attributes: List<Pair<String, String>>, tag: HtmlTag) = HtmlTagMatching.matches(tag, HtmlTag.Open(name, attributes))
    fun matchTagClose(name: String, tag: HtmlTag) = HtmlTagMatching.matches(tag, HtmlTag.Close(name))
    fun maybeFromAttrib(name: String, tag: HtmlTag): String? = (tag as? HtmlTag.Open)?.attributes?.firstOrNull { it.first == name }?.second

    fun <U> pCloses(name: String): HtmlTagParser<U, Unit> = pSatisfy<U> { it is HtmlTag.Open || it is HtmlTag.Close }.lookAhead().flatMap { tag ->
        when {
            tag is HtmlTag.Close && (tag.name == name || tag.name == "th" && name == "td" || tag.name == "td" && name == "th") -> pAny<U>().map { Unit }
            tag is HtmlTag.Open && closes(tag.name, name) -> TokenParsec.pure(Unit)
            tag is HtmlTag.Close && when (tag.name) {
                "ul", "ol" -> name == "li"
                "dl" -> name == "dd"
                "table" -> name in setOf("td", "th", "tr")
                "td", "th" -> name in HtmlTagCategories.blockHtmlTags
                else -> false
            } -> TokenParsec.pure(Unit)
            tag is HtmlTag.Close && name == "p" && tag.name in HtmlTagCategories.blockHtmlTags -> TokenParsec.pure(Unit)
            else -> TokenParsec.zero()
        }
    }.attempt()

    fun <U, A> pInTags(name: String, parser: HtmlTagParser<U, A>, empty: A, append: (A, A) -> A): HtmlTagParser<U, A> =
        pInTagsWhere(name, { true }, parser, empty, append)
    fun <U, A> pInTagsWhere(name: String, test: (HtmlTag) -> Boolean, parser: HtmlTagParser<U, A>, empty: A, append: (A, A) -> A): HtmlTagParser<U, A> =
        pSatisfy<U> { matchTagOpen(name, emptyList(), it) && test(it) }.then(
            TokenParsec.manyTill(parser, pCloses<U>(name) or TokenParsec.eof(HtmlTagMatching::show))
        ).map { it.foldRight(empty, append) }.attempt()
    fun <U, A> pInTag(omission: TagOmission, name: String, parser: HtmlTagParser<U, A>): HtmlTagParser<U, A> =
        pInTagWithAttribs(omission, name, parser).map { it.second }
    fun <U, A> pInTagWithAttribs(omission: TagOmission, name: String, parser: HtmlTagParser<U, A>): HtmlTagParser<U, Pair<List<Pair<String, String>>, A>> {
        val blanks = pBlank<U>().skipMany()
        val opening = pSatisfy<U> { matchTagOpen(name, emptyList(), it) }.map { (it as? HtmlTag.Open)?.attributes ?: emptyList() }
        val attrs = if (omission == TagOmission.TagsOmittable) opening.option(emptyList()) else opening
        val closing = pSatisfy<U> { matchTagClose(name, it) }.map { Unit }
        return blanks.then(attrs).before(blanks).flatMap { attributes ->
            parser.before(blanks).before(if (omission == TagOmission.TagsRequired) closing else closing.optional()).before(blanks).map { attributes to it }
        }.attempt()
    }

    private fun closes(open: String, current: String): Boolean = when {
        current == "body" || current == "html" -> false
        open == "body" && current == "head" -> true
        open == "a" && current == "a" || open == "li" && current == "li" -> true
        open in setOf("th", "td") && current in setOf("th", "td") -> true
        open == "tr" && current in setOf("th", "td", "tr", "colgroup") -> true
        open in setOf("dd", "dt") && current in setOf("dt", "dd") -> true
        open == "rt" && current in setOf("rb", "rt", "rtc") -> true
        open in setOf("col", "colgroup") && current == "col" -> true
        open == "optgroup" && current in setOf("optgroup", "option") || open == "option" && current == "option" -> true
        current == "p" && open in paragraphClosers -> true
        current == "meta" -> true
        open == current && open in setOf("form", "label", "map", "object") -> true
        current in setOf("option", "style", "script", "textarea", "title") -> true
        current == "select" && open != "option" -> true
        open == "thead" && current == "colgroup" -> true
        open == "tfoot" && current in setOf("thead", "colgroup") -> true
        open == "tbody" && current in setOf("tbody", "tfoot", "thead", "colgroup") -> true
        open in headingClosers && current in headingsAndParagraph -> true
        open in HtmlTagCategories.blockTags && current !in HtmlTagCategories.blockTags && current !in HtmlTagCategories.eitherBlockOrInline -> true
        else -> false
    }
    private val paragraphClosers = setOf("address", "article", "aside", "blockquote", "dir", "div", "dl", "fieldset", "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "header", "hr", "main", "menu", "nav", "ol", "p", "pre", "section", "table", "ul")
    private val headingClosers = setOf("h1", "h2", "h3", "h4", "h5", "h6", "dl", "ol", "ul", "table", "div", "main", "p")
    private val headingsAndParagraph = setOf("h1", "h2", "h3", "h4", "h5", "h6", "p")

    private val standardAttributes = PandocXml.html5Attributes + PandocXml.html4Attributes + PandocXml.rdfaAttributes
    fun toStringAttr(attributes: List<Pair<String, String>>): List<Pair<String, String>> {
        val seen = mutableSetOf<String>()
        return attributes.mapNotNull { (key, value) ->
            val normalized = when {
                key == "xml:lang" -> "lang"
                key.startsWith("data-") && key.removePrefix("data-") !in standardAttributes -> key.removePrefix("data-")
                else -> key
            }
            if (seen.add(normalized)) normalized to value else null
        }
    }
    fun toAttr(attributes: List<Pair<String, String>>): Attr {
        val normalized = toStringAttr(attributes)
        fun get(key: String) = normalized.firstOrNull { it.first == key }?.second
        return Attr(get("id") ?: get("name") ?: "", words(get("class") ?: "") + words(get("epub:type") ?: ""),
            normalized.filter { it.first != "class" && it.first != "id" && it.first != "name" })
    }
    private fun words(text: String): List<String> {
        val out = mutableListOf<String>(); val word = StringBuilder()
        text.codePoints().forEach { point ->
            if (ParserCharacters.isSpace(point)) { if (word.isNotEmpty()) { out.add(word.toString()); word.setLength(0) } }
            else word.appendCodePoint(point)
        }
        if (word.isNotEmpty()) out.add(word.toString())
        return out
    }
}
