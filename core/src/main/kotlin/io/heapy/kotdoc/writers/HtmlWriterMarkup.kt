// Text.Pandoc.Writers.HTML, © 2006–2024 John MacFarlane, GPL-2.0-or-later.
// Skylighting.Format.HTML, © 2016–2022 John MacFarlane, BSD-3-Clause; licenses/.
package io.heapy.kotdoc.writers

import io.heapy.kotdoc.markup.*
import io.heapy.kotdoc.highlighting.*
import io.heapy.kotdoc.highlighting.format.numberedLines
import io.heapy.kotdoc.parsing.ParserCharacters
import java.util.ArrayDeque

/** Built-in Blaze nodes retain tag identity for Writers.Blaze layout. */
internal object HtmlWriterMarkup {
    fun tag(name: String, content: Markup): Markup = MarkupM.Parent(StaticString.fromText(name),
        StaticString.fromText("<$name"), StaticString.fromText("</$name>"), if (name == "script" || name == "style") content.external() else content)
    fun leaf(name: String, xhtml: Boolean = true): Markup = MarkupM.Leaf(StaticString.fromText(name),
        StaticString.fromText("<$name"), StaticString.fromText(if (xhtml) " />" else ">"), lazyOf(Unit))
    fun attr(name: String, value: String, escaped: Boolean = false): Attribute = Blaze.attribute(
        Blaze.textTag(name), Blaze.textTag(" $name=\""), AttributeValue(if (escaped)
            ChoiceString.PreEscaped(ChoiceString.Text(value)) else ChoiceString.Text(value)))
    fun customAttr(name: String, value: String): Attribute = Blaze.customAttribute(Blaze.textTag(name), AttributeValue(ChoiceString.Text(value)))
    val nl: Markup get() = Blaze.preEscapedString("\n")
    fun concat(values: Iterable<Markup>): Markup = values.toList().asReversed().fold(Blaze.empty) { a, x -> x + a }
    fun join(values: List<Markup>, separator: Markup = nl): Markup = concat(values.flatMapIndexed { i, x -> if (i == 0) listOf(x) else listOf(separator, x) })
    fun empty(markup: Markup): Boolean {
        val todo = ArrayDeque<MarkupM<*>>(); todo.push(markup)
        while (todo.isNotEmpty()) when (val x = todo.pop()) {
            is MarkupM.Empty -> Unit
            is MarkupM.Append -> { todo.push(x.right); todo.push(x.left) }
            else -> return false
        }
        return true
    }
    fun text(value: String): Markup {
        var start = 0; val pieces = mutableListOf<Markup>(); var at = 0
        while (at < value.length) {
            val c = value[at]
            if (c == '\'' || c == '"' || c == '↩' || c == '↔') {
                if (start < at) pieces.add(Blaze.text(value.substring(start, at)))
                pieces.add(if (c == '\'' || c == '"') Blaze.preEscapedText(c.toString())
                    else if (value.getOrNull(at + 1) == '\uFE0E') Blaze.text(c.toString())
                    else Blaze.preEscapedText("$c\uFE0E"))
                start = at + 1
            }
            at++
        }
        if (pieces.isEmpty()) return Blaze.text(value)
        if (start < value.length) pieces.add(Blaze.text(value.substring(start)))
        return concat(pieces)
    }
    fun uri(html5: Boolean, value: String): String = if (html5) value else buildString {
        value.codePoints().forEach { cp ->
            if (cp > 127 || ParserCharacters.isSpace(cp) || cp.toChar() in "<>|\"{}[]^`") {
                String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).forEach { b -> append('%'); append((b.toInt() and 255).toString(16).uppercase().padStart(2, '0')) }
            } else appendCodePoint(cp)
        }
    }
    private val shorts = listOf("kw","dt","dv","bn","fl","cn","ch","sc","st","vs","ss","im","co","do","an","cv","ot","fu","va","cf","op","bu","ex","pp","at","re","in","wa","al","er","")
    private fun tokens(options: FormatOptions, line: SourceLine): Markup = concat(line.map { token ->
        if (token.type == TokenType.NormalTok) Blaze.text(token.text) else
            tag("span", Blaze.text(token.text)).with(attr("class", shorts[token.type.ordinal], true))
                .withIf(options.titleAttributes) { attr("title", token.type.name, true) }
    })
    private fun code(options: FormatOptions, classes: List<String>, body: Markup): Markup = tag("code", body)
        .with(attr("class", (listOf("sourceCode") + classes.filter { it != "sourceCode" }).joinToString(" ")))
        .withIf(options.startNumber - 1 != 0L) { attr("style", "counter-reset: source-line ${options.startNumber - 1};") }
    fun inline(options: FormatOptions, lines: List<SourceLine>): Markup = code(options,
        (options.codeClasses + options.containerClasses).distinct(), join(lines.map { tokens(options, it) }, Blaze.text("\n")))
    fun block(options: FormatOptions, lines: List<SourceLine>, html5: Boolean): Markup {
        val content = numberedLines(options.startNumber, lines) { n, line ->
            val id = options.lineIdPrefix + n
            var anchor = tag("a", Blaze.empty).with(attr("href", "#$id"))
            if (html5) anchor = anchor.with(customAttr(if (options.numberLines) "aria-label" else "aria-hidden", if (options.numberLines) n.toString() else "true"))
            if (!options.numberLines) anchor = anchor.with(customAttr("tabindex", "-1"))
            tag("span", anchor + tokens(options, line)).with(attr("id", id))
        }
        val classes = listOf("sourceCode") + (if (options.numberLines) listOf("numberSource") else emptyList()) + options.containerClasses.filter { it != "sourceCode" }
        return tag("div", tag("pre", code(options, options.codeClasses, join(content, Blaze.text("\n")))).with(attr("class", classes.joinToString(" ")))).with(attr("class", "sourceCode"))
    }
}
