// Text.Pandoc.Writers.HTML 3.12.1, © 2006–2024 John MacFarlane.
// GPL-2.0-or-later; complete original source and notice retained in upstream/licenses.
package io.heapy.kotdoc.writers

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.markup.*
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.options.HTMLSlideVariant.*
import io.heapy.kotdoc.highlighting.*
import io.heapy.kotdoc.images.*
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.math.*
import io.heapy.kotdoc.resources.*
import io.heapy.kotdoc.translations.TranslationRuntime
import io.heapy.kotdoc.xml.*
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.writers.HtmlWriterMarkup.tag
import io.heapy.kotdoc.writers.HtmlWriterMarkup.leaf
import io.heapy.kotdoc.writers.HtmlWriterMarkup.attr
import io.heapy.kotdoc.writers.HtmlWriterMarkup.customAttr
import io.heapy.kotdoc.writers.HtmlWriterMarkup.nl
import io.heapy.kotdoc.writers.HtmlWriterMarkup.concat
import io.heapy.kotdoc.writers.HtmlWriterMarkup.join
import io.heapy.kotdoc.writers.HtmlWriterMarkup.empty
import io.heapy.kotdoc.writers.HtmlWriterMarkup.text
import io.heapy.kotdoc.writers.HtmlWriterMarkup.uri

internal class HtmlWriterSession(val runtime: TranslationRuntime, val html5: Boolean,
    val epub: EPUBVersion? = null, val slides: HTMLSlideVariant = NoSlides) {
    var notes = emptyList<Markup>()
    var emittedNotes = 0L
    var emittedNoteBlocks = 0L
    var math = false
    var quotes = false
    var highlighting = false
    var slideLevel = 1L
    var inSection = false
    var codeBlockNum = 0L
    var csl = false
    var cslEntrySpacing: Long? = null
    var blockLevel = 0L

    fun attributes(opts: WriterOptions, a: Attr): List<Attribute> {
        val seen = hashSetOf<String>()
        val others = mutableListOf<Attribute>()
        for ((k, v) in a.keyValues) {
            if (!seen.add(k)) { runtime.report(LogMessage.DuplicateAttribute(k, v)); continue }
            if (k.isEmpty()) continue
            if (html5) {
                val key = if (((k in PandocXml.html5Attributes || k in PandocXml.rdfaAttributes) && k != "label") || ':' in k || k.startsWith("data-") || k.startsWith("aria-")) k else "data-$k"
                others.add(customAttr(key, v))
            } else if (epub != EPUBVersion.EPUB2 || k in PandocXml.html4Attributes || k in PandocXml.rdfaAttributes || k.startsWith("xml:")) others.add(customAttr(k, v))
        }
        return buildList {
            if (a.identifier.isNotEmpty()) add(attr("id", opts.identifierPrefix + a.identifier))
            val classes = a.classes.filter { it.isNotEmpty() }.distinct()
            if (classes.isNotEmpty()) add(attr("class", classes.joinToString(" ")))
            addAll(others)
        }
    }
    fun addAttrs(opts: WriterOptions, a: Attr, h: Markup): Markup = attributes(opts, a).fold(h) { h, a -> h.with(a) }
    private fun id(opts: WriterOptions, s: String) = if (s.isEmpty()) Attribute.Empty else attr("id", opts.identifierPrefix + s)
    fun imageAttributes(opts: WriterOptions, a: Attr): List<Attribute> {
        val fields = a.keyValues.filter { it.first != "width" && it.first != "height" }.toMutableList()
        for (dir in listOf(Direction.Width, Direction.Height)) when (val dim = Dimensions.dimension(dir, a)) {
            null -> Unit
            is Dimension.Pixel -> fields.add(dir.toString() to dim.value.toString())
            else -> fields.add("style" to "$dir:$dim")
        }
        val styles = fields.filter { it.first == "style" }
        val consolidated = if (styles.isEmpty()) fields else listOf("style" to styles.joinToString(";") { it.second }) + fields.filter { it.first != "style" }
        return attributes(opts, a.copy(keyValues = consolidated))
    }
    fun blocks(opts: WriterOptions, values: List<Block>): Markup = join(values.map { block(opts, it) }.filterNot(::empty))
    fun inlines(opts: WriterOptions, values: List<Inline>): Markup = concat(values.map { inline(opts, it) })
    private fun rawHtml(format: Format): Boolean = format.value in setOf("html", "html4", "s5", "slidy", "slideous", "dzslides", "revealjs") ||
        html5 && format.value == "html5" || epub != null && (format.value == "epub" || format.value == if (epub == EPUBVersion.EPUB2) "epub2" else "epub3")
    private fun allowsEnvironment(m: MathMethod) = m is MathMethod.MathJax || m is MathMethod.KaTeX || m is MathMethod.WebTeX || m == MathMethod.MathML
    private fun mathEnvironment(s: String) = s.startsWith("\\begin{") && s.drop(7).substringBefore('}') in setOf("align","align*","alignat","alignat*","aligned","alignedat","array","Bmatrix","bmatrix","cases","CD","eqnarray","eqnarray*","equation","equation*","gather","gather*","gathered","matrix","multline","multline*","pmatrix","prooftree","smallmatrix","split","subarray","Vmatrix","vmatrix")
    fun inline(opts: WriterOptions, value: Inline): Markup = when (value) {
        is Inline.Str -> text(value.text)
        Inline.Space -> Blaze.text(" ")
        Inline.SoftBreak -> Blaze.text(if (opts.wrapText == WrapOption.WrapPreserve) "\n" else " ")
        Inline.LineBreak -> leaf("br") + Blaze.text("\n")
        is Inline.Emph -> tag("em", inlines(opts, value.content))
        is Inline.Underline -> tag("u", inlines(opts, value.content))
        is Inline.Strong -> tag("strong", inlines(opts, value.content))
        is Inline.Strikeout -> tag("del", inlines(opts, value.content))
        is Inline.SmallCaps -> tag("span", inlines(opts, value.content)).with(attr("class", "smallcaps"))
        is Inline.Superscript -> tag("sup", inlines(opts, value.content))
        is Inline.Subscript -> tag("sub", inlines(opts, value.content))
        is Inline.Span -> span(opts, value)
        is Inline.Code -> {
            val a = value.attr
            val kind = if ("sample" in a.classes) "samp" else if ("variable" in a.classes) "var" else null
            val classes = a.classes.toMutableList().also { if (kind != null) it.remove(if (kind == "samp") "sample" else "variable") }
            val hl = if (opts.highlightMethod is HighlightMethod.Skylighting || opts.highlightMethod == HighlightMethod.DefaultHighlighting)
                Highlighting.highlight(opts.syntaxMap, HtmlWriterMarkup::inline, a, value.text) else Highlighted.Failure("")
            when (hl) {
                is Highlighted.Failure -> {
                    if (hl.error.isNotEmpty()) runtime.report(LogMessage.CouldNotHighlight(hl.error))
                    addAttrs(opts, a.copy(classes = classes), tag(kind ?: "code", text(value.text)))
                }
                is Highlighted.Success -> {
                    highlighting = true
                    addAttrs(opts, a.copy(classes = emptyList()), if (kind == null) hl.value else tag(kind, hl.value))
                }
            }
        }
        is Inline.Quoted -> if (opts.htmlQTags) {
            quotes = true
            val span = value.content.singleOrNull() as? Inline.Span
            if (span != null && span.attr.keyValues.any { it.first == "cite" }) addAttrs(opts, span.attr, tag("q", inlines(opts, span.content)))
            else tag("q", inlines(opts, value.content))
        } else Blaze.text(if (value.type == QuoteType.SingleQuote) "‘" else "“") + inlines(opts, value.content) + Blaze.text(if (value.type == QuoteType.SingleQuote) "’" else "”")
        is Inline.Math -> math(opts, value)
        is Inline.RawInline -> if (rawHtml(value.format)) Blaze.preEscapedText(value.text) else {
            if (value.format.value in listOf("tex", "latex") && allowsEnvironment(opts.mathMethod) && mathEnvironment(value.text)) {
                math = true; inline(opts, Inline.Math(MathType.DisplayMath, value.text))
            } else if (value.format.value in listOf("tex", "latex") && opts.mathMethod is MathMethod.MathJax && (value.text.startsWith("\\ref{") || value.text.startsWith("\\eqref{"))) {
                math = true; inline(opts, Inline.Math(MathType.InlineMath, value.text))
            } else { runtime.report(LogMessage.InlineNotRendered(value)); Blaze.empty }
        }
        is Inline.Link -> {
            val body = inlines(opts, WriterShared.removeLinks(value.content))
            if (value.target.url.startsWith("mailto:")) obfuscate(opts, value.attr, body, value.target.url)
            else {
                val source = value.target.url.let { if (it.startsWith('#')) "#" + (if (slides == RevealJsSlides) "/" else opts.identifierPrefix) + it.drop(1) else it }
                val link = addAttrs(opts, value.attr, tag("a", body).with(attr("href", uri(html5, source))))
                link.withIf(value.target.title.isNotEmpty()) { attr("title", value.target.title) }
            }
        }
        is Inline.Image -> image(opts, value)
        is Inline.Note -> {
            val previous = notes
            val number = (emittedNotes + previous.size + 1).toString()
            val contents = note(opts, number, value.blocks)
            notes = listOf(contents) + previous
            var link = tag("a", if (epub != null) Blaze.text(number) else tag("sup", Blaze.text(number)))
                .with(attr("href", uri(html5, "#" + (if (slides == RevealJsSlides) "/" else "") + opts.identifierPrefix + "fn" + number)))
                .with(attr("class", "footnote-ref")).with(id(opts, "fnref$number"))
            if (epub == EPUBVersion.EPUB3) link = link.with(customAttr("epub:type", "noteref")).with(customAttr("role", "doc-noteref"))
            else if (html5) link = link.with(attr("role", "doc-noteref"))
            link
        }
        is Inline.Cite -> {
            val values = if (!html5) value.content else object : PandocVisitor() {
                override fun transformInline(value: Inline): Inline = if (value is Inline.Link && value.target.url.startsWith("#ref-"))
                    value.copy(attr = value.attr.copy(keyValues = listOf("role" to "doc-biblioref") + value.attr.keyValues)) else value
            }.walkInlines(value.content)
            tag("span", inlines(opts, values)).with(attr("class", "citation"))
                .withIf(html5) { customAttr("data-cites", value.citations.joinToString(" ") { it.citationId }) }
        }
    }
    private fun span(opts: WriterOptions, s: Inline.Span): Markup {
        val a = s.attr
        if (a.identifier.isEmpty() && a.keyValues.isEmpty() && a.classes.size == 1 && a.classes[0] in setOf("csl-block", "csl-left-margin", "csl-right-inline", "csl-indent"))
            return tag("div", inlines(opts, s.content)).with(attr("class", a.classes[0]))
        val tags = mutableListOf<String>(); val other = mutableListOf<String>(); var found = false
        for (c in a.classes) if (c in setOf("kbd", "mark", "dfn", "abbr", "smallcaps", "underline")) { found = true; tags.add(c) }
            else if (found) other.add(0, c)
        val css = listOf("csl-no-emph" to "font-style:normal;", "csl-no-strong" to "font-weight:normal;", "csl-no-smallcaps" to "font-variant:normal;")
        val styles = css.filter { it.first in a.classes }.joinToString("") { it.second }
        val kvs = (if (styles.isEmpty()) emptyList() else listOf("style" to styles)) + a.keyValues
        var body = inlines(opts, s.content)
        if (found) {
            for (t in tags.asReversed()) body = when (t) {
                "smallcaps" -> tag("span", body).with(attr("class", "smallcaps"))
                "underline" -> tag("u", body)
                else -> Blaze.customParent(Blaze.textTag(t), body)
            }
            return addAttrs(opts, a.copy(classes = other, keyValues = kvs), body)
        }
        return addAttrs(opts, a.copy(classes = a.classes.filter { c -> css.none { it.first == c } }, keyValues = kvs), tag("span", body))
    }
    private fun math(opts: WriterOptions, value: Inline.Math): Markup {
        math = true
        val cls = "math " + if (value.type == MathType.InlineMath) "inline" else "display"
        val input = value.text
        return when (val method = opts.mathMethod) {
            is MathMethod.WebTeX -> {
                val stripped = input.trim { io.heapy.kotdoc.parsing.ParserCharacters.isSpace(it.code) }
                leaf("img").with(attr("style", "vertical-align:middle"))
                    .with(attr("src", method.url + ResourceUri.urlEncode((if (value.type == MathType.InlineMath) "\\textstyle " else "\\displaystyle ") + stripped)))
                    .with(attr("alt", stripped)).with(attr("title", stripped)).with(attr("class", cls))
            }
            MathMethod.GladTeX -> Blaze.customParent(Blaze.textTag("eq"), text(input)).with(customAttr("env", if (value.type == MathType.InlineMath) "math" else "displaymath"))
            MathMethod.MathML -> when (val r = PandocMath.convertMath(runtime, MathMLWriter::writeMathML, value.type, input)) {
                is MathConversion.Fallback -> tag("span", inline(opts, r.inline)).with(attr("class", cls))
                is MathConversion.Converted -> Blaze.content(ChoiceString.Deferred(lazy {
                    val root = r.value
                    val children = root.content.filterIsInstance<LegacyXmlElement>()
                    val child = when (children.size) { 1 -> children[0]; else -> LegacyXmlElement.node("mrow", children) }
                    val annotation = LegacyXmlElement(LegacyXmlName("annotation"), listOf(LegacyXmlAttribute("encoding", "application/x-tex")), listOf(LegacyXmlText(input)))
                    val result = LegacyXmlElement(root.name, root.attributes, listOf(LegacyXmlElement.node("semantics", listOf(child, annotation))), root.line)
                    ChoiceString.PreEscaped(ChoiceString.String(LegacyXmlOutput.printContent(result, LegacyXmlPrintConfig(shortEmptyTag = { false }))))
                }))
            }
            is MathMethod.MathJax -> tag("span", Blaze.text(if (value.type == MathType.InlineMath) "\\($input\\)" else "\\[$input\\]")).with(attr("class", cls))
            is MathMethod.KaTeX -> tag("span", Blaze.text(input)).with(attr("class", cls))
            MathMethod.PlainMath -> tag("span", inlines(opts, PandocMath.texMathToInlines(runtime, value.type, input))).with(attr("class", cls))
        }
    }
    private fun image(opts: WriterOptions, value: Inline.Image): Markup {
        val source = value.target.url
        val alternate = DocumentShared.stringifyInlines(value.content)
        val attrs = listOf(if (slides == RevealJsSlides) customAttr("data-src", source) else attr("src", uri(html5, source))) +
            (if (value.target.title.isEmpty()) emptyList() else listOf(attr("title", value.target.title))) + imageAttributes(opts, value.attr)
        val path = source.removeSuffix(".gz")
        val parsedPath = MediaBagUri.parseReference(path)?.path
        val category = if (source.startsWith("data:")) source.drop(5).substringBefore('/') else MimeTypes.mediaCategory(parsedPath ?: source)
        val (node, special) = when (category) {
            "video", "audio" -> tag(category, tag("a", Blaze.text(if (value.content.isEmpty()) if (category == "video") "Video" else "Audio" else alternate))
                .with(attr("href", uri(html5, source)))) to listOf(attr("controls", ""))
            null, "image" -> leaf("img") to if (value.attr.keyValues.none { it.first == "alt" } && (epub != null || value.content.isNotEmpty())) listOf(attr("alt", alternate)) else emptyList()
            else -> leaf("embed") to emptyList()
        }
        return (attrs + special).fold(node) { h, a -> h.with(a) }
    }
    private fun note(opts: WriterOptions, ref: String, values: List<Block>): Markup {
        val backlink = Inline.Link(Attr(classes = listOf("footnote-back"), keyValues = if (html5) listOf("role" to "doc-backlink") else emptyList()),
            listOf(Inline.Str(if (epub == null) "↩" else ref)), Target("#fnref$ref"))
        val blocks = if (epub == null) {
            val last = values.lastOrNull()
            val image = (last as? Block.Para)?.content?.singleOrNull() as? Inline.Image
            when {
                last == null -> emptyList()
                image != null && (image.target.title.startsWith("fig:") || "r-stretch" in image.attr.classes) -> values + Block.Plain(listOf(backlink))
                last is Block.Para -> values.dropLast(1) + last.copy(content = last.content + backlink)
                last is Block.Plain -> values.dropLast(1) + last.copy(content = last.content + backlink)
                else -> values + Block.Plain(listOf(backlink))
            }
        } else if (epub == EPUBVersion.EPUB3) values else when (val first = values.firstOrNull()) {
            is Block.Para -> listOf(first.copy(content = listOf(backlink, Inline.Str("."), Inline.Space) + first.content)) + values.drop(1)
            is Block.Plain -> listOf(first.copy(content = listOf(backlink, Inline.Str("."), Inline.Space) + first.content)) + values.drop(1)
            else -> listOf(Block.Para(listOf(backlink, Inline.Str(".")))) + values
        }
        val contents = blocks(opts, blocks)
        return if (epub == null) tag("li", contents).with(id(opts, "fn$ref")) + nl else {
            val tag = if (epub == EPUBVersion.EPUB3) tag("aside", nl + contents + nl).with(customAttr("epub:type", "footnote")).with(customAttr("role", "doc-footnote")) else tag("div", nl + contents + nl)
            tag.with(id(opts, "fn$ref")) + nl
        }
    }
    fun footnoteSection(opts: WriterOptions, location: ReferenceLocation, start: Long, values: List<Markup>): Markup {
        val ident = if (emittedNoteBlocks <= 0) "footnotes" else "footnotes-${emittedNoteBlocks + 1}"
        emittedNoteBlocks++
        if (values.isEmpty()) return Blaze.empty
        val className = "footnotes " + when (location) { ReferenceLocation.EndOfBlock -> "footnotes-end-of-block"; ReferenceLocation.EndOfSection -> "footnotes-end-of-section"; ReferenceLocation.EndOfDocument -> "footnotes-end-of-document" }
        val contents = nl + (if (location == ReferenceLocation.EndOfBlock) Blaze.empty else leaf("hr") + nl) +
            (if (epub != null) concat(values) else tag("ol", nl + concat(values)).withIf(start != 1L) { attr("start", start.toString()) } + nl)
        val container = when {
            html5 && epub == EPUBVersion.EPUB3 -> tag("section", contents).with(id(opts, ident)).with(attr("class", className)).with(customAttr("epub:type", "footnotes"))
            html5 -> tag(if (location == ReferenceLocation.EndOfDocument) "section" else "aside", contents).with(id(opts, ident)).with(attr("class", className)).with(attr("role", if (location == ReferenceLocation.EndOfDocument) "doc-endnotes" else "doc-footnote"))
            slides != NoSlides -> tag("div", contents).with(attr("class", "footnotes slide"))
            else -> tag("div", contents).with(attr("class", className))
        }
        return nl + container
    }
    private fun obfuscate(opts: WriterOptions, a: Attr, body: Markup, source: String): Markup {
        if (opts.emailObfuscation == ObfuscationMethod.NoObfuscation) return addAttrs(opts, a, tag("a", body).with(attr("href", uri(html5, source))))
        val text = BlazeRenderer.renderText(body)
        val s = source.take(7).lowercase() + source.drop(7)
        val address = s.drop(7); val name = address.substringBefore('@'); val domain = if ('@' in address) address.substringAfter('@') else ""
        if (opts.emailObfuscation == ObfuscationMethod.ReferenceObfuscation) return addAttrs(opts, a.copy(classes = listOf("email") + a.classes),
            tag("a", Blaze.preEscapedText(obfuscateMarkup(text))).with(attr("href", obfuscateString(s), true)))
        val auto = text == address
        val linkText = if (auto) "e" else "'" + obfuscateMarkup(text).replace("</", "<\\/") + "'"
        val alternate = if (auto) name + " at " + domain.replace(".", " dot ") else "$text ($name at ${domain.replace(".", " dot ")})"
        val classes = a.classes.joinToString("") { " $it" }
        val other = (if (a.identifier.isEmpty()) "" else " id=\"${escapeJsAttr(opts.identifierPrefix + a.identifier)}\"") + a.keyValues.joinToString("") { (k, v) -> " $k=\"${escapeJsAttr(v)}\"" }
        val script = "\n<!--\nh='${obfuscateString(domain)}';a='${obfuscateString("@")}';n='${obfuscateString(name)}';e=n+a+h;\n" +
            "document.write('<a h'+'ref'+'=\"ma'+'ilto'+':'+e+'\" clas'+'s=\"em' + 'ail$classes\"$other>'+" + linkText + "+'<\\/'+'a'+'>');\n// -->\n"
        return tag("script", Blaze.preEscapedText(script)).with(attr("type", "text/javascript")) + tag("noscript", Blaze.preEscapedText(obfuscateMarkup(alternate)))
    }
    private fun obfuscateString(s: String): String = buildString { PandocXml.fromEntities(s).codePoints().forEach { p -> append("&#"); append(if (p % 2 == 0) p.toString() else "x" + p.toString(16)); append(';') } }
    private fun obfuscateMarkup(s: String): String = buildString {
        var at = 0
        while (at < s.length) {
            val tag = s.indexOf('<', at).let { if (it < 0) s.length else it }
            append(obfuscateString(s.substring(at, tag)))
            val end = s.indexOf('>', tag)
            if (end < 0) { append(s.substring(tag)); break }
            append(s.substring(tag, end + 1)); at = end + 1
        }
    }
    private fun escapeJsAttr(s: String): String = buildString { for (c in s) append(when (c) {
        '&' -> "&amp;"; '<' -> "&lt;"; '>' -> "&gt;"; '"' -> "&quot;"; '\'' -> "&#39;"; '\\' -> "&#92;"; '\n' -> "&#10;"; '\r' -> "&#13;"; else -> c.toString()
    }) }

    fun block(opts: WriterOptions, value: Block): Markup {
        val increase = value !is Block.Div || "section" !in value.attr.classes
        if (increase) blockLevel++
        val contents = blockInner(opts, value)
        val result = if (opts.referenceLocation == ReferenceLocation.EndOfBlock && blockLevel == 1L) {
            val rendered = if (notes.isEmpty()) Blaze.empty else footnoteSection(opts, opts.referenceLocation, emittedNotes + 1, notes.asReversed())
            emittedNotes += notes.size; notes = emptyList(); contents + rendered
        } else contents
        if (increase) blockLevel--
        return result
    }
    private fun blockInner(opts: WriterOptions, value: Block): Markup = when (value) {
        is Block.Plain -> inlines(opts, value.content)
        is Block.Para -> {
            val image = value.content.singleOrNull() as? Inline.Image
            if (slides == RevealJsSlides && image != null && "r-stretch" in image.attr.classes) inline(opts, image)
            else inlines(opts, value.content).let { if (empty(it) && ExtensionName.EmptyParagraphs !in opts.extensions) Blaze.empty else tag("p", it) }
        }
        is Block.LineBlock -> tag("div", inlines(opts, value.lines.flatMapIndexed { i, line -> if (i == 0) line else listOf(Inline.LineBreak) + line })).with(attr("class", "line-block"))
        is Block.Div -> div(opts, value)
        is Block.RawBlock -> if (rawHtml(value.format)) Blaze.preEscapedText(value.text) else {
            if (value.format.value in listOf("latex", "tex") && allowsEnvironment(opts.mathMethod) && mathEnvironment(value.text)) {
                math = true; block(opts, Block.Plain(listOf(Inline.Math(MathType.DisplayMath, value.text))))
            } else { runtime.report(LogMessage.BlockNotRendered(value)); Blaze.empty }
        }
        Block.HorizontalRule -> leaf("hr")
        is Block.CodeBlock -> codeBlock(opts, value)
        is Block.BlockQuote -> {
            val single = value.blocks.singleOrNull()
            if (slides != NoSlides && (single is Block.BulletList || single is Block.OrderedList || single is Block.DefinitionList))
                block(opts.copy(incremental = !opts.incremental), checkNotNull(single))
            else tag("blockquote", nl + blocks(opts, value.blocks) + nl)
        }
        is Block.Header -> {
            val content = inlines(opts, value.content)
            val num = value.attr.keyValues.firstOrNull { it.first == "number" }?.second.orEmpty()
            val numbered = if (opts.numberSections && num.isNotEmpty() && "unnumbered" !in value.attr.classes)
                tag("span", Blaze.text(num)).with(attr("class", "header-section-number")) + Blaze.text(" ") + content else content
            val keys = setOf("lang", "dir", "title", "style", "align", "onclick", "ondblclick", "onmousedown", "onmouseup", "onmouseover", "onmousemove", "onmouseout", "onkeypress", "onkeydown", "onkeyup")
            val a = value.attr.copy(classes = (if (value.level > 6) listOf("heading") else emptyList()) + value.attr.classes,
                keyValues = if (html5) value.attr.keyValues else value.attr.keyValues.filter { it.first in keys })
            addAttrs(opts, a, tag(if (value.level in 1..6) "h${value.level}" else "p", numbered))
        }
        is Block.BulletList -> {
            val contents = value.items.map { listItem(opts, it) }
            list(opts, "ul", contents.map { nl + tag("li", it) } + nl)
                .withIf(value.items.all { WriterShared.toTaskListItem(it) != null }) { attr("class", "task-list") }
        }
        is Block.OrderedList -> {
            val a = value.attributes
            var result = list(opts, "ol", value.items.map { nl + tag("li", listItem(opts, it)) } + nl)
            if (a.start != 1L) result = result.with(attr("start", a.start.toString()))
            if (a.style == ListNumberStyle.Example) result = result.with(attr("class", "example"))
            if (a.style != ListNumberStyle.DefaultStyle) {
                val style = when (a.style) { ListNumberStyle.LowerAlpha -> "lower-alpha"; ListNumberStyle.UpperAlpha -> "upper-alpha"; ListNumberStyle.LowerRoman -> "lower-roman"; ListNumberStyle.UpperRoman -> "upper-roman"; else -> "decimal" }
                result = result.with(if (html5) attr("type", when (a.style) { ListNumberStyle.LowerAlpha -> "a"; ListNumberStyle.UpperAlpha -> "A"; ListNumberStyle.LowerRoman -> "i"; ListNumberStyle.UpperRoman -> "I"; else -> "1" }) else attr("style", "list-style-type: $style"))
            }
            result
        }
        is Block.DefinitionList -> {
            val items = value.items.map { (term, definitions) ->
                val dt = tag("dt", inlines(opts, term))
                val ds = definitions.map { tag("dd", nl + blocks(opts, it) + nl) }
                nl + dt + nl + join(ds)
            }
            list(opts, "dl", items + nl)
        }
        is Block.Table -> table(opts, AnnotatedTable.toTable(value))
        is Block.Figure -> {
            val attrs = attributes(opts, value.attr)
            val body = blocks(opts, value.blocks)
            val caption = blocks(opts, value.caption.long)
            val image = (value.blocks.singleOrNull() as? Block.Plain)?.content?.singleOrNull() as? Inline.Image
            val sameAlt = image != null && DocumentShared.stringifyBlocks(value.caption.long) ==
                (image.attr.keyValues.firstOrNull { it.first == "alt" }?.second ?: DocumentShared.stringifyInlines(image.content))
            val captionTag = if (html5) tag("figcaption", caption).withIf(sameAlt) { customAttr("aria-hidden", "true") }
                else tag("div", caption).with(attr("class", "figcaption"))
            val innards = if (value.caption.long.isEmpty()) nl + body + nl else if (opts.figureCaptionPosition == CaptionPosition.CaptionAbove)
                nl + captionTag + nl + body + nl else nl + body + nl + captionTag + nl
            ((if (html5) emptyList() else listOf(attr("class", "float"))) + attrs).fold(tag(if (html5) "figure" else "div", innards)) { h, a -> h.with(a) }
        }
    }
    private fun list(opts: WriterOptions, name: String, items: List<Markup>): Markup = if (!opts.incremental) tag(name, concat(items))
        else if (slides == RevealJsSlides) tag(name, concat(items.map { it.with(attr("class", "fragment")) }))
        else tag(name, concat(items)).with(attr("class", "incremental"))
    private fun listItem(opts: WriterOptions, items: List<Block>): Markup {
        val task = WriterShared.toTaskListItem(items) ?: return blocks(opts, items)
        val first = task.second.first()
        val content = if (first is Block.Para) first.content else (first as Block.Plain).content
        val checkbox = leaf("input").with(attr("type", "checkbox")).withIf(task.first) { attr("checked", "") }
        val label = tag("label", checkbox + inlines(opts, content))
        val remaining = blocks(opts, task.second.drop(1))
        return (if (first is Block.Para) tag("p", label) else label) + (if (task.second.size == 1) Blaze.empty else nl) + remaining
    }
    private fun codeBlock(opts: WriterOptions, b: Block.CodeBlock): Markup {
        val a = b.attr
        val identifier = if (a.identifier.isEmpty()) { codeBlockNum++; opts.identifierPrefix + "cb$codeBlockNum" } else opts.identifierPrefix + a.identifier
        val lhs = ExtensionName.LiterateHaskell in opts.extensions && a.classes.any { it.lowercase() == "haskell" } && a.classes.any { it.lowercase() == "literate" }
        val classes = if (lhs) a.classes.map { if (it.lowercase() == "haskell") "literatehaskell" else it } else a.classes
        val code = if (lhs) {
            val lines = b.text.split('\n').let { if (b.text.isEmpty()) emptyList() else if (b.text.endsWith('\n')) it.dropLast(1) else it }
            lines.joinToString("", transform = { "> $it\n" })
        } else b.text
        if (slides == RevealJsSlides && opts.highlightMethod == HighlightMethod.IdiomaticHighlighting) {
            highlighting = true
            return tag("pre", addAttrs(opts, a.copy(classes = classes.mapIndexed { i, c -> if (i == 0) "language-$c" else c }), tag("code", Blaze.text(code))))
        }
        val hl = if (opts.highlightMethod is HighlightMethod.Skylighting || opts.highlightMethod == HighlightMethod.DefaultHighlighting)
            Highlighting.highlight(opts.syntaxMap, { o, ls -> HtmlWriterMarkup.block(o, ls, html5) }, Attr(identifier, classes, a.keyValues), code) else Highlighted.Failure("")
        return when (hl) {
            is Highlighted.Failure -> {
                if (hl.error.isNotEmpty()) runtime.report(LogMessage.CouldNotHighlight(hl.error))
                addAttrs(opts, a, tag("pre", tag("code", Blaze.text(code))))
            }
            is Highlighted.Success -> { highlighting = true; addAttrs(opts.copy(identifierPrefix = ""), Attr(identifier, emptyList(), a.keyValues), hl.value) }
        }
    }
    private fun div(opts: WriterOptions, d: Block.Div): Markup {
        val first = d.blocks.firstOrNull()
        if (d.attr.classes.firstOrNull() == "section" && first is Block.Header) return section(opts, d, first)
        if (d.attr.keyValues.firstOrNull { it.first == "wrapper" }?.second == "1" && d.blocks.size == 1)
            return addAttrs(opts, d.attr.copy(keyValues = d.attr.keyValues.filter { it.first != "wrapper" }), blockInner(opts, d.blocks[0]))
        val a = d.attr
        val bib = a.identifier == "refs" || "csl-bib-body" in a.classes
        if (bib) { csl = true; cslEntrySpacing = a.keyValues.firstOrNull { it.first == "entry-spacing" }?.second?.let(::safeReadInteger)?.toLong() }
        val fields = a.keyValues.filter { it.first != "width" || "column" !in a.classes } +
            a.keyValues.filter { "column" in a.classes && it.first == "width" }.map { "style" to "width:${it.second};" } +
            (if (bib && html5) listOf("role" to "list") else emptyList()) +
            (if ("csl-entry" in a.classes && html5) listOf("role" to "listitem") else emptyList())
        val speaker = "notes" in a.classes
        val incrementalDiv = !speaker && ("incremental" in a.classes || "nonincremental" in a.classes)
        val options = when { speaker -> opts.copy(incremental = false); "incremental" in a.classes -> opts.copy(incremental = true); "nonincremental" in a.classes -> opts.copy(incremental = false); else -> opts }
        val classes = if (slides == NoSlides) a.classes else a.classes.filter { it != "incremental" && it != "nonincremental" }
        val values = if ("csl-entry" !in classes) d.blocks else object : PandocVisitor() {
            override fun transformBlock(value: Block): Block = if (value is Block.Para) Block.Plain(value.content) else value
        }.walkBlocks(d.blocks)
        val contents = if ("columns" in classes) concat(values.map { block(options, it) }) else blocks(options, values)
        val tag = if (html5 && "section" in classes) "section" else "div"
        val classes2 = if (tag == "section") classes.filter { it != "section" } else classes
        val adjusted = Attr(a.identifier, classes2, fields)
        return when {
            incrementalDiv && adjusted == Attr() -> contents
            speaker -> when (slides) {
                RevealJsSlides -> addAttrs(options, a, tag("aside", nl + contents + nl))
                DZSlides -> addAttrs(options, a, tag("div", nl + contents + nl)).with(attr("role", "note"))
                NoSlides -> addAttrs(options, a, tag("div", nl + contents + nl))
                else -> Blaze.empty
            }
            else -> addAttrs(opts, adjusted, tag(tag, nl + contents + nl))
        }
    }
    private fun section(opts: WriterOptions, d: Block.Div, h: Block.Header): Markup {
        val level = h.level
        val slide = slides != NoSlides && level <= slideLevel
        val titleSlide = slide && level < slideLevel
        val oldInSection = inSection
        val header = if (h.content == listOf(Inline.Str("\u0000"))) Blaze.empty else block(opts, h.copy(level = if (level <= slideLevel && slides == SlidySlides) 1 else level))
        fun isSection(b: Block): Boolean = b is Block.Div && (b.attr.classes.firstOrNull() == "section" || b.blocks.any(::isSection))
        val rest = d.blocks.drop(1)
        val split = if (titleSlide) rest.indexOfFirst(::isSection).let { if (it < 0) rest.size else it } else 0
        fun pauses(bs: List<Block>): List<Block> {
            if (!slide) return bs
            val pause = Block.Para(listOf(Inline.Str("."), Inline.Space, Inline.Str("."), Inline.Space, Inline.Str(".")))
            return object : PandocVisitor() {
                override fun transformBlocks(value: List<Block>): List<Block> {
                    if (value.isEmpty()) return value
                    val parts = mutableListOf<List<Block>>(); var at = 0
                    while (at < value.size) {
                        val start = at
                        while (at < value.size && value[at] != pause) at++
                        parts.add(value.subList(start, at))
                        while (at < value.size && value[at] == pause) at++
                    }
                    return parts.firstOrNull().orEmpty() + parts.drop(1).flatMap { listOf(Block.RawBlock("html", "<div class=\"${if (slides == RevealJsSlides) "fragment" else "incremental"}\">")) + it + Block.RawBlock("html", "</div>") }
                }
            }.walkBlocks(bs)
        }
        val titleBlocks = if (titleSlide) pauses(rest.take(split)) else emptyList()
        val inner = if (titleSlide) rest.drop(split) else pauses(rest)
        val titleContents = blocks(opts, titleBlocks)
        inSection = true
        var innerContents = blocks(opts, inner)
        inSection = oldInSection
        if (opts.referenceLocation == ReferenceLocation.EndOfSection && notes.isNotEmpty()) {
            val pending = notes
            innerContents += footnoteSection(opts, opts.referenceLocation, emittedNotes + 1, pending.asReversed())
            notes = emptyList(); emittedNotes += pending.size
        }
        val dclasses = d.attr.classes.drop(1)
        val classes = (if (titleSlide) listOf("title-slide") else emptyList()) + (if (slide) listOf("slide") else emptyList()) +
            (if ((slide || opts.sectionDivs) && !html5) listOf("section") else emptyList()) +
            (if (slide || opts.sectionDivs) listOf("level$level") else emptyList()) + dclasses.filter { slides != RevealJsSlides || it != "r-fit-text" }
        val a = d.attr.copy(classes = classes)
        val name = if (html5) "section" else "div"
        if (titleSlide) {
            val title = addAttrs(opts, a, tag(name, nl + header + nl + titleContents + nl))
            return if (slides == RevealJsSlides && !oldInSection && inner.isNotEmpty()) tag("section", nl + title + nl + innerContents)
                else title + nl + if (inner.isEmpty()) Blaze.empty else innerContents + nl
        }
        if (opts.sectionDivs || slide || (h.attr.identifier != d.attr.identifier && h.attr.identifier.isNotEmpty() && d.attr.identifier.isNotEmpty()) || h.attr.classes != dclasses || h.attr.keyValues != d.attr.keyValues)
            return addAttrs(opts, a, tag(name, nl + header + nl + if (inner.isEmpty()) Blaze.empty else innerContents + nl))
        fun <A> difference(xs: List<A>, ys: List<A>): List<A> = xs.toMutableList().also { result -> ys.forEach { result.remove(it) } }
        val merged = addAttrs(opts, a.copy(classes = difference(classes, h.attr.classes), keyValues = difference(a.keyValues, h.attr.keyValues)), header)
        return merged + if (inner.isEmpty()) Blaze.empty else nl + innerContents
    }
    private fun table(opts: WriterOptions, t: AnnotatedTable.Table): Markup {
        val caption = if (t.caption.long.isEmpty()) Blaze.empty else tag("caption", blocks(opts, t.caption.long)) + nl
        val cols = if (t.columns.all { it.width == ColWidth.Default }) Blaze.empty else tag("colgroup", nl + concat(t.columns.map { c ->
            leaf("col").let { if (c.width is ColWidth.Width) it.with(attr(if (html5) "style" else "width", (if (html5) "width: " else "") + percent(c.width.value))) else it } + nl
        })) + nl
        fun rows(headers: List<AnnotatedTable.HeaderRow>, header: Boolean): Markup = nl + concat(headers.map { row ->
            addAttrs(opts, row.attr, tag("tr", nl + concat(row.cells.map { cell(opts, it, header) }))) + nl
        })
        fun part(name: String, a: Attr, rs: List<AnnotatedTable.HeaderRow>, header: Boolean): Markup {
            val emptyCell = Cell(Attr(), Alignment.AlignDefault, 1, 1, emptyList())
            if (rs.isEmpty() || rs.all { r -> r.cells.all { it.cell == emptyCell } }) return Blaze.empty
            return addAttrs(opts, a, tag(name, rows(rs, header))) + nl
        }
        val head = part("thead", t.head.attr, t.head.rows, true)
        val bodies = t.bodies.map { body ->
            val intermediate = if (body.head.isEmpty()) Blaze.empty else rows(body.head, true)
            val bodyRows = nl + concat(body.body.map { row -> addAttrs(opts, row.attr, tag("tr", nl + concat(row.head.map { cell(opts, it, true) }) + concat(row.body.map { cell(opts, it, false) }))) + nl })
            addAttrs(opts, body.attr, tag("tbody", intermediate + bodyRows))
        }
        val foot = part("tfoot", t.foot.attr, t.foot.rows, false)
        val width = t.columns.sumOf { (it.width as? ColWidth.Width)?.value ?: 0.0 }
        val a = if (t.attr.keyValues.none { it.first == "style" } && width > 0 && width < 1)
            t.attr.copy(keyValues = listOf("style" to "width:${java.math.BigDecimal(width * 100).toBigInteger().toLong()}%;") + t.attr.keyValues) else t.attr
        return addAttrs(opts, a, tag("table", nl + caption + cols + head + join(bodies) + foot + nl))
    }
    private fun percent(value: Double) = java.math.BigDecimal(100 * value).toBigInteger().toString() + "%"
    private fun cell(opts: WriterOptions, annotated: AnnotatedTable.Cell, header: Boolean): Markup {
        val c = annotated.cell
        val contents = blocks(opts, c.blocks)
        val alignment = if (c.alignment == Alignment.AlignDefault) annotated.columns.head.alignment else c.alignment
        val align = WriterHtml.htmlAlignmentToString(alignment)
        val fields = if (align == null) c.attr.keyValues else if (html5) WriterHtml.htmlAddStyle("text-align" to align, c.attr.keyValues) else {
            val old = c.attr.keyValues.indexOfFirst { it.first == "align" }
            if (old < 0) listOf("align" to align) + c.attr.keyValues else c.attr.keyValues.toMutableList().also { it[old] = "align" to align }
        }
        val others = attributes(opts, c.attr.copy(keyValues = fields))
        val attrs = (if (c.colSpan == 1L) emptyList() else listOf(attr("colspan", c.colSpan.toString()))) +
            (if (c.rowSpan == 1L) emptyList() else listOf(attr("rowspan", c.rowSpan.toString()))) + others
        return attrs.fold(tag(if (header) "th" else "td", contents)) { h, a -> h.with(a) } + nl
    }
}
