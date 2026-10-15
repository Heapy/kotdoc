// Text.Pandoc.Writers.HTML 3.12.1, © 2006–2024 John MacFarlane.
// GPL-2.0-or-later; original source and complete notice retained in upstream/licenses.
package io.heapy.kotdoc.writers

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.highlighting.Highlighting
import io.heapy.kotdoc.highlighting.format.HtmlFormat
import io.heapy.kotdoc.layout.*
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.markup.*
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.options.HTMLSlideVariant.*
import io.heapy.kotdoc.resources.*
import io.heapy.kotdoc.state.PandocState
import io.heapy.kotdoc.state.PandocStateContext
import io.heapy.kotdoc.structure.Slides
import io.heapy.kotdoc.structure.TableOfContents
import io.heapy.kotdoc.templates.*
import io.heapy.kotdoc.translations.*
import io.heapy.kotdoc.xml.PandocXml
import io.heapy.kotdoc.writers.HtmlWriterMarkup.attr
import io.heapy.kotdoc.writers.HtmlWriterMarkup.tag
import io.heapy.kotdoc.writers.HtmlWriterMarkup.nl
import io.heapy.kotdoc.writers.HtmlWriterMarkup.uri
import java.util.ArrayDeque

/** The complete writer entry-point surface. Each call owns its writer state;
 * callers can share a TranslationRuntime to retain Pandoc's common state/effects. */
object HtmlWriter {
    fun writeHtml4String(options: WriterOptions, document: Pandoc, runtime: TranslationRuntime = EmbeddedHtmlRuntime()): String =
        writeString(HtmlWriterSession(runtime, false), options, WriterShared.ensureValidXmlIdentifiers(document))
    fun writeHtml5String(options: WriterOptions, document: Pandoc, runtime: TranslationRuntime = EmbeddedHtmlRuntime()): String =
        writeString(HtmlWriterSession(runtime, true), options, document)
    fun writeHtml4(options: WriterOptions, document: Pandoc, runtime: TranslationRuntime = EmbeddedHtmlRuntime()): Markup =
        writeMarkup(HtmlWriterSession(runtime, false), options, WriterShared.ensureValidXmlIdentifiers(document))
    fun writeHtml5(options: WriterOptions, document: Pandoc, runtime: TranslationRuntime = EmbeddedHtmlRuntime()): Markup =
        writeMarkup(HtmlWriterSession(runtime, true), options, document)
    fun writeHtmlStringForEPUB(version: EPUBVersion, options: WriterOptions, document: Pandoc, runtime: TranslationRuntime = EmbeddedHtmlRuntime()): String =
        writeString(HtmlWriterSession(runtime, version == EPUBVersion.EPUB3, version), options.copy(wrapText = WrapOption.WrapNone), document)
    fun writeS5(options: WriterOptions, document: Pandoc, runtime: TranslationRuntime = EmbeddedHtmlRuntime()) = slides(S5Slides, options, document, runtime)
    fun writeSlidy(options: WriterOptions, document: Pandoc, runtime: TranslationRuntime = EmbeddedHtmlRuntime()) = slides(SlidySlides, options, document, runtime)
    fun writeSlideous(options: WriterOptions, document: Pandoc, runtime: TranslationRuntime = EmbeddedHtmlRuntime()) = slides(SlideousSlides, options, document, runtime)
    fun writeDZSlides(options: WriterOptions, document: Pandoc, runtime: TranslationRuntime = EmbeddedHtmlRuntime()) = slides(DZSlides, options, document, runtime)
    fun writeRevealJs(options: WriterOptions, document: Pandoc, runtime: TranslationRuntime = EmbeddedHtmlRuntime()) = slides(RevealJsSlides, options, document, runtime)
    private fun slides(variant: HTMLSlideVariant, options: WriterOptions, document: Pandoc, runtime: TranslationRuntime): String {
        val modern = variant == RevealJsSlides || variant == DZSlides
        return writeString(HtmlWriterSession(runtime, modern, slides = variant), options, if (modern) document else WriterShared.ensureValidXmlIdentifiers(document))
    }
    fun tagWithAttributes(options: WriterOptions, html5: Boolean, selfClosing: Boolean, name: String, attributes: Attr): String = try {
        val session = HtmlWriterSession(EmbeddedHtmlRuntime(), html5)
        BlazeRenderer.renderText(session.addAttrs(options, attributes, Blaze.customLeaf(Blaze.textTag(name), selfClosing)))
    } catch (_: PandocError) { "" }

    private fun writeMarkup(session: HtmlWriterSession, opts: WriterOptions, document: Pandoc): Markup =
        if (opts.template != null || opts.preferAscii) Blaze.preEscapedText(writeString(session, opts, document)) else session.document(opts, document).first
    private fun writeString(session: HtmlWriterSession, opts: WriterOptions, document: Pandoc): String {
        val (body, context) = session.document(opts, document)
        val columns = if (opts.wrapText == WrapOption.WrapAuto) opts.columns else null
        val template = opts.template
        val result = if (template == null) {
            if (columns == null) BlazeRenderer.renderText(body) else DocLayout.Text.render(columns, BlazeLayout.layoutMarkup(body))
        } else {
            if (context.lookup("lang", TemplateValues.Text.targetDecoder) == null && hasVariable("lang", template)) session.runtime.report(LogMessage.NoLangSpecified)
            var ctx = context
            if (ctx.lookup("pagetitle", TemplateValues.Text.targetDecoder).isNullOrEmpty() && hasVariable("pagetitle", template)) {
                val source = opts.variables.lookup("sourcefile", TemplateValues.Text.listDecoder(TemplateValues.Text.targetDecoder))?.firstOrNull()
                val fallback = if (source == null) "Untitled" else {
                    val file = PartialPaths.System.fileName(source)
                    val dot = file.lastIndexOf('.')
                    if (dot < 0) file else file.take(dot)
                }
                session.runtime.report(LogMessage.NoTitleElement(fallback))
                ctx = WriterMetadata.resetField("pagetitle", DocLayout.Text.literal(fallback), ctx, TemplateValues.Text.document)
            }
            ctx = WriterMetadata.defField("body", BlazeLayout.layoutMarkup(body), ctx, TemplateValues.Text.document)
            DocLayout.Text.render(columns, TemplateRenderer.Text.render(template, ctx))
        }
        return if (opts.preferAscii) PandocXml.toEntities(result) else result
    }
    private fun hasVariable(name: String, template: Template<*>): Boolean {
        val todo = ArrayDeque<Template<*>>(); todo.push(template)
        fun matches(v: Variable) = v.parts.joinToString(".") == name
        while (todo.isNotEmpty()) when (val t = todo.pop()) {
            is Template.Interpolate -> if (matches(t.variable)) return true
            is Template.Conditional -> { if (matches(t.variable)) return true; todo.push(t.no); todo.push(t.yes) }
            is Template.Iterate -> { if (matches(t.variable)) return true; todo.push(t.separator); todo.push(t.body) }
            is Template.Nested -> todo.push(t.template)
            is Template.Partial -> todo.push(t.template)
            is Template.Concat -> { todo.push(t.right); todo.push(t.left) }
            else -> Unit
        }
        return false
    }
}

/** Embedded-data pure default; application IO/custom data selection is injectable. */
private class EmbeddedHtmlRuntime(private val common: PandocStateContext = PandocStateContext()) : TranslationRuntime, PandocState by common {
    override fun readDataFile(file: CharacterString): ByteArray = PackagedPandocData.read(file)
        ?: throw PandocError.PandocCouldNotFindDataFileError(TextChars.fromPoints(file.points()))
}

internal fun HtmlWriterSession.document(opts: WriterOptions, doc: Pandoc): Pair<Markup, Context<String>> {
    WriterShared.setupTranslations(runtime, doc.meta)
    slideLevel = opts.slideLevel ?: Slides.getSlideLevel(doc.blocks)
    val values = TemplateValues.Text
    val metadata = WriterMetadata.metaToContext(opts, values, { BlazeLayout.layoutMarkup(blocks(opts, it)) }, { BlazeLayout.layoutMarkup(inlines(opts, it)) }, doc.meta)
    fun stringifyHTML(xs: List<Inline>) = PandocXml.escapeStringForXML(DocumentShared.stringifyInlines(xs))
    val authors = docAuthors(doc.meta).map { DocLayout.Text.literal(stringifyHTML(it)) }
    val date = stringifyHTML(docDate(doc.meta))
    val description = DocLayout.Text.literal(PandocXml.escapeStringForXML(DocumentShared.lookupMetaString("description", doc.meta)))
    val abstractTitle = runtime.translateTerm(Term.Abstract)
    val sections = DocumentShared.makeSections(if (slides == NoSlides) doc.blocks else Slides.prepSlides(slideLevel, doc.blocks), opts.numberSections, offsets = opts.numberOffset)
    val toc = if (opts.tableOfContents && slides != S5Slides && sections.isNotEmpty()) {
        val list = TableOfContents.toTableOfContents(sections, opts.numberSections, opts.tocDepth)
        if (list.items.isEmpty()) null else BlazeLayout.layoutMarkup(block(if (slides == RevealJsSlides) opts.copy(identifierPrefix = "/" + opts.identifierPrefix) else opts, list))
    } else null
    val content = blocks(opts, sections)
    val footnotes = if (notes.isEmpty()) Blaze.empty else {
        val result = footnoteSection(opts, ReferenceLocation.EndOfDocument, emittedNotes + 1, notes.asReversed())
        emittedNotes += notes.size; notes = emptyList(); result
    }
    val body = content + footnotes
    val mathMarkup = when (val method = opts.mathMethod) {
        is MathMethod.MathJax -> if (slides == RevealJsSlides) Blaze.empty else tag("script", if (slides == SlideousSlides) Blaze.preEscapedString("MathJax.Hub.Queue([\"Typeset\",MathJax.Hub]);") else Blaze.empty)
            .with(attr("defer", "")).with(attr("src", uri(html5, method.url))).with(attr("type", "text/javascript"))
        is MathMethod.KaTeX -> {
            val flush = metadata.lookup("classoption", values.listDecoder(values.documentDecoder))?.contains(DocLayout.Text.literal("fleqn")) == true
            val script = listOf("document.addEventListener(\"DOMContentLoaded\", function () {", " var mathElements = document.getElementsByClassName(\"math\");", " var macros = [];", " for (var i = 0; i < mathElements.length; i++) {", "  var texText = mathElements[i].firstChild;", "  if (mathElements[i].tagName == \"SPAN\") {", "   katex.render(texText.data, mathElements[i], {", "    displayMode: mathElements[i].classList.contains('display'),", "    throwOnError: false,", "    macros: macros,", "    fleqn: " + if (flush) "true" else "false", "   });", "}}});").joinToString("\n", postfix = "\n")
            tag("script", Blaze.empty).with(attr("defer", "")).with(attr("src", uri(html5, method.url + "katex.min.js"))) + nl +
                tag("script", Blaze.text(script)) + nl + HtmlWriterMarkup.leaf("link").with(attr("rel", "stylesheet")).with(attr("href", uri(html5, method.url + "katex.min.css")))
        }
        else -> Blaze.empty
    }
    var ctx = metadata
    fun field(name: String, value: Val<String>) { if (name !in ctx.entries) ctx = Context(ctx.entries + (name to value)) }
    fun bool(name: String, value: Boolean) = field(name, Val.BoolVal(value))
    fun text(name: String, value: String) = field(name, Val.SimpleVal(DocLayout.Text.literal(value)))
    fun document(name: String, value: Doc<String>) = field(name, Val.SimpleVal(value))
    bool("html5", html5)
    bool("table-caption-below", opts.tableCaptionPosition == CaptionPosition.CaptionBelow)
    text("s5-url", "s5/default"); text("revealjs-url", "https://unpkg.com/reveal.js@^6"); text("slideous-url", "slideous"); text("slidy-url", "https://www.w3.org/Talks/Tools/Slidy2")
    text("idprefix", opts.identifierPrefix); text("pagetitle", stringifyHTML(docTitle(doc.meta)))
    document("description-meta", description)
    DocumentShared.normalizeDate(date)?.let { text("date-meta", it) }
    field("author-meta", Val.ListVal(authors.map { Val.SimpleVal(it) }))
    toc?.let { document("table-of-contents", it); document("toc", it) }
    bool("quotes", quotes)
    bool("document-css", metadata.lookup("css", values.listDecoder(values.targetDecoder)) == null && slides == NoSlides)
    if (slides == RevealJsSlides) {
        for ((name, value) in htmlRevealBooleanDefaults) bool(name, value)
        for ((name, value) in htmlRevealTextDefaults) text(name, value)
        when (val progress = metadata["scrollProgress"]) {
            is Val.BoolVal -> if (progress.value) bool("scrollProgress", true)
            else -> bool("scrollProgressAuto", true)
        }
        if (opts.highlightMethod == HighlightMethod.IdiomaticHighlighting) { bool("highlight-js", true); text("highlightjs-theme", "monokai") }
    }
    if (opts.mathMethod == MathMethod.PlainMath || opts.mathMethod is MathMethod.WebTeX) bool("displaymath-css", true)
    if (opts.mathMethod is MathMethod.MathJax) { bool("mathjax", true); text("mathjaxurl", opts.mathMethod.url.substringBefore('?')) } else bool("mathjax", false)
    text("abstract-title", abstractTitle)
    if (math) document("math", BlazeLayout.layoutMarkup(mathMarkup))
    if (csl) { bool("csl-css", true); cslEntrySpacing?.let { text("csl-entry-spacing", "${it}em") } }
    if (highlighting) when (val method = opts.highlightMethod) {
        is HighlightMethod.Skylighting -> text("highlighting-css", HtmlFormat.styleToCss(method.style))
        HighlightMethod.DefaultHighlighting -> text("highlighting-css", HtmlFormat.styleToCss(Highlighting.defaultStyle))
        else -> Unit
    }
    return body to ctx
}
