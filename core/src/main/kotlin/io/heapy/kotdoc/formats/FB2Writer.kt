// Port of Text.Pandoc.Writers.FB2, Pandoc 3.12.1.
// Copyright (C) 2011-2012 Sergey Astanin; 2012-2024 John MacFarlane.
// GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.formats

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.resources.*
import io.heapy.kotdoc.xml.*
import java.util.Base64
import java.util.Locale

sealed interface MathMethod {
    data object PlainMath : MathMethod
    data class WebTeX(val url: String) : MathMethod
    data object GladTeX : MathMethod
    data object MathML : MathMethod
    data class MathJax(val url: String) : MathMethod
    data class KaTeX(val url: String) : MathMethod
}

/** The FB2 writer consults only writerMathMethod from Pandoc's WriterOptions. */
data class FB2WriterOptions(val mathMethod: MathMethod = MathMethod.PlainMath)
sealed interface WriterMessage {
    data class InlineNotRendered(val inline: Inline) : WriterMessage
    data class BlockNotRendered(val block: Block) : WriterMessage
    data class CouldNotFetchResource(val path: String, val message: String) : WriterMessage
    data class CouldNotDetermineMimeType(val path: String) : WriterMessage
    data class Resource(val event: ResourceEvent) : WriterMessage
}
data class WriterResult(val text: String, val messages: List<WriterMessage>)

object FB2Writer {
    fun write(document: Pandoc, options: FB2WriterOptions = FB2WriterOptions(), resources: ResourceAccess = ResourceContext()): String =
        writeWithEffects(document, options, resources).text
    fun writeWithEffects(document: Pandoc, options: FB2WriterOptions = FB2WriterOptions(), resources: ResourceAccess = ResourceContext()): WriterResult =
        State(options, resources).write(DocumentShared.ensureValidXmlIdentifiers(document))

    private data class Footnote(val number: Int, val id: String, val content: List<XmlContent>)
    private class State(val options: FB2WriterOptions, val resources: ResourceAccess) {
        var footnotes = emptyList<Footnote>()
        val images = mutableListOf<Pair<String, String>>()
        var parentMarker = ""
        val messages = mutableListOf<WriterMessage>()

        fun write(document: Pandoc): WriterResult {
            val description = description(document.meta)
            val title = inlines(docTitle(document.meta))
            val body = element("body", listOf(element("title", listOf(element("p", title)))) + renderSections(document.blocks))
            val notes = if (footnotes.isEmpty()) emptyList() else listOf(element("body", footnotes.asReversed().map {
                element("section", listOf(element("title", listOf(element("p", listOf(XmlText(it.number.toString())))))) + it.content, listOf(attr("id", it.id)))
            }, listOf(attr("name", "notes"))))
            val binaries = mutableListOf<XmlContent>()
            val missing = mutableSetOf<String>()
            for ((id, url) in images) {
                val binary = fetchImage(id, url)
                if (binary == null) missing += "#$id" else binaries += binary
            }
            val root = element("FictionBook", listOf(description, replaceMissing(body, missing)) + notes + binaries,
                listOf(attr("xmlns", "http://www.gribuser.ru/xml/fictionbook/2.0"), prefixed("xmlns", "l", "http://www.w3.org/1999/xlink")))
            return WriterResult(XmlOutput.header + "\n" + XmlOutput.showContent(root) + "\n", messages.toList())
        }

        fun description(meta: Map<String, MetaValue>): XmlElement {
            val genre = DocumentShared.lookupMetaString("genre", meta).ifEmpty { "unrecognised" }
            val bookTitle = inlines(docTitle(meta))
            val authors = docAuthors(meta).map(::author)
            val date = inlines(docDate(meta))
            val annotation = (meta["abstract"] as? MetaValue.MetaBlocks)?.let { listOf(element("annotation", blocks(it.content.map { b ->
                if (b is Block.Plain) Block.Para(b.content) else b
            }))) } ?: emptyList()
            val lang = when (val v = meta["lang"]) {
                is MetaValue.MetaString -> v.value
                is MetaValue.MetaInlines -> (v.content.singleOrNull() as? Inline.Str)?.text
                else -> null
            }
            val coverUrl = when (val v = meta["cover-image"]) {
                is MetaValue.MetaString -> v.value
                is MetaValue.MetaInlines -> DocumentShared.stringifyInlines(v.content)
                else -> null
            }
            val cover = coverUrl?.let { listOf(element("coverpage", listOf(image(Inline.Image(Attr(), emptyList(), Target(it)), false)))) } ?: emptyList()
            return element("description", listOf(element("title-info",
                listOf(element("genre", listOf(XmlText(genre)))) + authors +
                    (if (bookTitle.isEmpty()) emptyList() else listOf(element("book-title", bookTitle))) + annotation +
                    (if (date.isEmpty()) emptyList() else listOf(element("date", date))) + cover +
                    (lang?.let { listOf(element("lang", listOf(XmlText(it.substringBefore('-'))))) } ?: emptyList())),
                element("document-info", listOf(element("program-used", listOf(XmlText("pandoc")))))))
        }

        fun author(inlines: List<Inline>): XmlElement {
            val words = split(plain(inlines).toList(), ::haskellSpace).map { it.joinToString("") }.filter { it.isNotEmpty() }
            val email = words.firstOrNull { '@' in it }
            val names = words.filter { '@' !in it }
            val parts = when (names.size) {
                0 -> emptyList()
                1 -> listOf("nickname" to names[0])
                2 -> listOf("first-name" to names[0], "last-name" to names[1])
                else -> listOf("first-name" to names[0], "middle-name" to names.subList(1, names.lastIndex).joinToString(""), "last-name" to names.last())
            } + (email?.let { listOf("email" to it) } ?: emptyList())
            return element("author", parts.map { (tag, value) -> element(tag, listOf(XmlText(value))) })
        }

        fun renderSections(blocks: List<Block>): List<XmlContent> {
            val sections = DocumentShared.makeSections(blocks)
            val first = sections.indexOfFirst { it is Block.Div && it.attr.classes.firstOrNull() == "section" && it.blocks.firstOrNull() is Block.Header }
                .let { if (it < 0) sections.size else it }
            val prepared = if (first == 0) sections else listOf(Block.Div(Attr(classes = listOf("section")),
                listOf(Block.Header(1, Attr(), emptyList())) + sections.take(first))) + sections.drop(first)
            return prepared.flatMap(::section)
        }

        fun section(block: Block): List<XmlContent> {
            if (block !is Block.Div) return block(block)
            val header = block.blocks.firstOrNull() as? Block.Header
            if (block.attr.classes.firstOrNull() != "section" || header == null) return block.blocks.flatMap(::section)
            val title = if (header.content.isEmpty()) emptyList() else listOf(element("title",
                split(header.content) { it == Inline.LineBreak }.flatMap { block(Block.Para(it)) }))
            return listOf(element("section", title + block.blocks.drop(1).flatMap(::section),
                if (block.attr.identifier.isEmpty()) emptyList() else listOf(attr("id", block.attr.identifier))))
        }

        fun blocks(values: List<Block>): List<XmlContent> = values.flatMap(::block)
        fun block(value: Block): List<XmlContent> = when (value) {
            is Block.Plain -> (value.content.singleOrNull() as? Inline.Image)?.let { listOf(image(it, true)) } ?: inlines(value.content)
            is Block.Para -> when (val only = value.content.singleOrNull()) {
                is Inline.Image -> listOf(image(only, true))
                is Inline.Math if only.type == MathType.DisplayMath -> listOf(math(only.text, true))
                else -> listOf(element("p", inlines(value.content)))
            }
            is Block.CodeBlock -> listOf(element("empty-line")) + textLines(value.text).map { element("p", listOf(element("code", listOf(XmlText(it))))) } + element("empty-line")
            is Block.RawBlock -> if (value.format == Format("fb2")) XmlLight.parseContents(value.text) else emptyList()
            is Block.Div -> blocks(value.blocks)
            is Block.BlockQuote -> listOf(element("cite", blocks(value.blocks)))
            is Block.LineBlock -> listOf(element("poem", split(value.lines) { it.isEmpty() }.map { stanza ->
                element("stanza", stanza.map { element("v", inlines(it)) })
            }))
            is Block.OrderedList -> DocumentShared.orderedListMarkers(value.attributes).zip(value.items.asSequence()).flatMap { (marker, item) -> item(marker, item) }.toList()
            is Block.BulletList -> value.items.flatMap { item("•", it) }
            is Block.DefinitionList -> value.items.flatMap { (term, definitions) ->
                // Upstream renders definitions before the term, so effects have this order.
                val rendered = definitions.flatMap { blocks(plainToPara(indentBlocks("\u00a0".repeat(4), it))) }
                listOf(element("p", listOf(element("strong", inlines(term))))) + rendered
            }
            is Block.Header -> { messages += WriterMessage.BlockNotRendered(value); emptyList() }
            Block.HorizontalRule -> listOf(element("empty-line"))
            is Block.Table -> {
                val legacy = DocumentShared.toLegacyTable(value)
                fun row(tag: String, cells: List<List<Block>>) = element("tr", cells.zip(legacy.alignments).map { (cell, alignment) ->
                    element(tag, blocks(cell), listOf(attr("align", when (alignment) {
                        Alignment.AlignCenter -> "center"; Alignment.AlignRight -> "right"; else -> "left"
                    })))
                })
                val head = if (legacy.headers.isEmpty()) emptyList() else listOf(row("th", legacy.headers))
                val body = legacy.rows.map { row("td", it) }
                val caption = element("emphasis", inlines(legacy.caption))
                listOf(element("table", head + body), element("p", listOf(caption)))
            }
            is Block.Figure -> {
                val alt = DocumentShared.blocksToInlines(value.caption.long).values
                val updated = object : PandocVisitor() {
                    override fun transformInline(value: Inline): Inline = if (value is Inline.Image && value.content.isEmpty()) value.copy(content = alt) else value
                }.walkBlocks(value.blocks)
                blocks(updated)
            }
        }

        fun item(marker: String, value: List<Block>): List<XmlContent> {
            val old = parentMarker
            parentMarker += marker + '\u00a0'
            val result = blocks(plainToPara(indentBlocks(parentMarker, value)))
            parentMarker = old
            return result
        }

        fun inlines(values: List<Inline>): List<XmlContent> = values.flatMap(::inline)
        fun inline(value: Inline): List<XmlContent> = when (value) {
            is Inline.Str -> listOf(XmlText(value.text))
            is Inline.Span -> inlines(value.content)
            is Inline.Emph -> listOf(element("emphasis", inlines(value.content)))
            is Inline.Underline -> listOf(element("underline", inlines(value.content)))
            is Inline.Strong -> listOf(element("strong", inlines(value.content)))
            is Inline.Strikeout -> listOf(element("strikethrough", inlines(value.content)))
            is Inline.Superscript -> listOf(element("sup", inlines(value.content)))
            is Inline.Subscript -> listOf(element("sub", inlines(value.content)))
            is Inline.SmallCaps -> inlines(DocumentShared.capitalize(value.content))
            is Inline.Quoted -> listOf(XmlText(if (value.type == QuoteType.SingleQuote) "‘" else "“")) + inlines(value.content) + XmlText(if (value.type == QuoteType.SingleQuote) "’" else "”")
            is Inline.Cite -> inlines(value.content)
            is Inline.Code -> listOf(element("code", listOf(XmlText(value.text))))
            Inline.Space -> listOf(XmlText(" "))
            Inline.LineBreak, Inline.SoftBreak -> listOf(XmlText("\n"))
            is Inline.Math -> listOf(math(value.text, false))
            is Inline.RawInline -> { messages += WriterMessage.InlineNotRendered(value); emptyList() }
            is Inline.Link -> listOf(element("a", inlines(value.content), listOf(prefixed("l", "href", value.target.url))))
            is Inline.Image -> listOf(image(value, false))
            is Inline.Note -> {
                val old = footnotes
                val number = old.size + 1
                val id = "n$number"
                val content = blocks(value.blocks)
                footnotes = listOf(Footnote(number, id, content)) + old
                listOf(element("a", listOf(XmlText("[$number]")), listOf(prefixed("l", "href", "#$id"), attr("type", "note"))))
            }
        }

        fun math(formula: String, normal: Boolean): XmlContent = when (val method = options.mathMethod) {
            is MathMethod.WebTeX -> image(Inline.Image(Attr(), listOf(Inline.Code(Attr(), formula)), Target(method.url + ResourceUri.urlEncode(formula))), normal)
            else -> element("code", listOf(XmlText(formula)))
        }
        fun image(value: Inline.Image, normal: Boolean): XmlElement {
            val id = "image${images.size + 1}"
            images.add(0, id to value.target.url)
            return element("image", attributes = listOf(prefixed("l", "href", "#$id"), prefixed("l", "type", if (normal) "imageType" else "inlineImageType"),
                attr("alt", plain(value.content))) + if (normal && value.target.title.isNotEmpty()) listOf(attr("title", value.target.title)) else emptyList())
        }
        fun fetchImage(id: String, url: String): XmlElement? {
            val dataUri = readDataUri(url)
            val image = if (ResourceUri.isUri(url) && dataUri != null) {
                val mime = dataUri.mime.lowercase(Locale.ROOT)
                if (dataUri.base64 && mime in listOf("image/png", "image/jpeg")) mime to dataUri.data else null
            } else try {
                val fetched = resources.fetch(url) { messages += WriterMessage.Resource(it) }
                if (fetched.mimeType == null) { messages += WriterMessage.CouldNotDetermineMimeType(url); null }
                else fetched.mimeType to Base64.getEncoder().encodeToString(fetched.contents)
            } catch (e: ResourceException) { messages += WriterMessage.CouldNotFetchResource(url, e.description); null }
            return image?.let { (mime, data) -> element("binary", listOf(XmlText(data)), listOf(attr("id", id), attr("content-type", mime))) }
        }
    }

    private fun replaceMissing(content: XmlContent, missing: Set<String>): XmlContent {
        if (content !is XmlElement) return content
        val updated = content.copy(content = content.content.map { replaceMissing(it, missing) })
        if (updated.attributes.none { it.name == XmlName("href", prefix = "l") && it.value in missing }) return updated
        val alt = updated.attr("alt") ?: return updated
        return if (updated.attr(XmlName("type", prefix = "l")) == "imageType") element("p", listOf(XmlText(alt))) else XmlText(alt)
    }

    private fun plainToPara(blocks: List<Block>): List<Block> = blocks.flatMap { when (it) {
        is Block.Plain -> listOf(Block.Para(it.content)); is Block.Para -> listOf(it, Block.HorizontalRule); else -> listOf(it)
    } }
    private fun indentBlocks(prefix: String, blocks: List<Block>): List<Block> = blocks.mapIndexed { index, block ->
        indentPrefix(if (index == 0) prefix else "\u00a0".repeat(prefix.codePointCount(0, prefix.length)), block)
    }
    private fun indentPrefix(prefix: String, block: Block): Block = when (block) {
        is Block.Plain -> block.copy(content = listOf(Inline.Str(prefix)) + block.content)
        is Block.Para -> block.copy(content = listOf(Inline.Str(prefix)) + block.content)
        is Block.CodeBlock -> block.copy(text = textLines(block.text).joinToString("") { "$prefix$it\n" })
        is Block.BlockQuote -> block.copy(blocks = block.blocks.map { indentPrefix("\u00a0".repeat(4), it) })
        is Block.Header -> block.copy(content = split(block.content) { it == Inline.LineBreak }.flatMapIndexed { index, line ->
            (if (index == 0) emptyList() else listOf(Inline.LineBreak)) + Inline.Str(prefix) + line
        })
        else -> block
    }

    private fun plain(inlines: List<Inline>): String = inlines.joinToString("") { when (it) {
        is Inline.Str -> it.text; is Inline.Code -> it.text; is Inline.Math -> it.text
        Inline.Space, Inline.SoftBreak -> " "; Inline.LineBreak -> "\n"
        is Inline.RawInline, is Inline.Note -> ""
        is Inline.Link -> plain(it.content) + " <${it.target.url}>"
        else -> plain(DocumentShared.inlineChildren(it))
    } }
    private fun textLines(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split('\n').let { if (value.endsWith('\n')) it.dropLast(1) else it }
    private fun <T> split(values: List<T>, delimiter: (T) -> Boolean): List<List<T>> = buildList {
        var start = 0
        while (start < values.size) {
            var end = start
            while (end < values.size && !delimiter(values[end])) end++
            add(values.subList(start, end))
            start = end + 1
        }
    }
    private fun haskellSpace(c: Char) = c in '\t'..'\r' || c == ' ' || Character.getType(c) == Character.SPACE_SEPARATOR.toInt()
    private fun attr(name: String, value: String) = XmlAttribute(XmlName(name), value)
    private fun prefixed(prefix: String, name: String, value: String) = XmlAttribute(XmlName(name, prefix = prefix), value)
    private fun element(name: String, content: List<XmlContent> = emptyList(), attributes: List<XmlAttribute> = emptyList()) = XmlElement(XmlName(name), attributes, content)

    data class DataUri(val mime: String, val charset: String, val base64: Boolean, val data: String)
    internal fun readDataUri(value: String): DataUri? {
        if (!value.startsWith("data:")) return null
        val rest = value.drop(5)
        var mime = "text/plain"
        var charset = "US-ASCII"
        var base64 = false
        for (part in rest.substringBefore(',').split(';').asReversed()) when {
            isMimeType(part) -> mime = part
            part.startsWith("charset=") -> charset = part.drop(8)
            part == "base64" -> base64 = true
        }
        return DataUri(mime, charset, base64, rest.substringAfter(',', ""))
    }
    internal fun isMimeType(value: String): Boolean {
        val parts = value.split('/')
        if (parts.size != 2) return false
        val type = parts[0].lowercase(Locale.ROOT)
        return (type in listOf("text", "image", "audio", "video", "application", "message", "multipart") || type.startsWith("x-")) &&
            parts.all { part -> part.all { it.code in 33..126 && it !in "()<>@,;:\\\"/[]?=" } }
    }
}
