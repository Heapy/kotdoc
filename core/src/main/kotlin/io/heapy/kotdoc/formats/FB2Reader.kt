// Port of Text.Pandoc.Readers.FB2, Pandoc 3.12.1.
// Copyright (C) 2018-2020 Alexander Krotov. GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.formats

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.xml.*
import java.io.ByteArrayOutputStream

object FB2Reader {
    fun read(input: String): Pandoc = readWithEffects(input).document

    fun readWithEffects(input: String): ReaderResult = State().read(XmlLight.parseElement(input.replace("\r", "")))

    private class State {
        var level = 1L
        val meta = linkedMapOf<String, MetaValue>()
        val authors = mutableListOf<String>()
        val notes = mutableMapOf<String, Blocks>()
        val messages = mutableListOf<ReaderMessage>()
        val media = mutableListOf<MediaInsertion>()

        fun read(root: XmlElement): ReaderResult {
            val blocks = if (root.localName == "FictionBook") {
                root.children.firstOrNull(::isNotes)?.children?.filter { it.localName == "section" }?.forEach(::parseNote)
                children(root, ::fictionBookChild)
            } else unexpected(root, "root")
            if (authors.isNotEmpty()) meta["author"] = MetaValue.MetaList(authors.map { Builder.text(it).toMetaValue() })
            return ReaderResult(Pandoc(meta.toMap(), blocks.values), messages.toList(), media.toList())
        }

        fun isNotes(e: XmlElement) = e.localName == "body" && e.attr("name") == "notes"
        fun ignored(name: String) { messages += ReaderMessage.IgnoredElement(name) }
        fun unexpected(e: XmlElement, parent: String): Blocks {
            messages += ReaderMessage.UnexpectedXmlElement(e.localName, parent)
            return Blocks()
        }
        fun children(e: XmlElement, parse: (XmlElement) -> Blocks): Blocks = Blocks(e.children.flatMap { parse(it).values })

        fun parseNote(e: XmlElement) {
            val id = e.attr("id") ?: return
            val children = if (e.children.firstOrNull()?.localName == "title") e.children.drop(1) else e.children
            notes["#$id"] = Blocks(children.flatMap { sectionChild(it).values })
        }

        fun fictionBookChild(e: XmlElement): Blocks = when (e.localName) {
            "stylesheet" -> Blocks()
            "description" -> { e.children.forEach(::descriptionChild); Blocks() }
            "body" -> if (isNotes(e)) Blocks() else children(e, ::bodyChild)
            "binary" -> { binary(e); Blocks() }
            else -> unexpected(e, "FictionBook")
        }

        fun descriptionChild(e: XmlElement) {
            when (e.localName) {
                "title-info" -> e.children.forEach(::titleInfoChild)
                "src-title-info", "document-info", "publish-info", "custom-info", "output" -> Unit
                else -> ignored("${e.localName} in description")
            }
        }

        fun titleInfoChild(e: XmlElement) {
            when (e.localName) {
                "genre", "lang", "src-lang", "translator", "sequence" -> Unit
                "author" -> authors += e.children.mapNotNull { child ->
                    when (child.localName) {
                        "first-name", "middle-name", "last-name", "nickname", "home-page", "email" -> child.strContent
                        else -> { ignored("${child.localName} in author"); null }
                    }
                }.joinToString(" ")
                "book-title" -> meta["title"] = Builder.text(e.strContent).toMetaValue()
                "annotation" -> meta["abstract"] = children(e, ::annotationChild).toMetaValue()
                "keywords" -> meta["keywords"] = MetaValue.MetaList(e.strContent.split(',').map {
                    MetaValue.MetaString(it.trim(::haskellSpace))
                })
                "date" -> meta["date"] = Builder.text(e.strContent).toMetaValue()
                "coverpage" -> e.children.firstOrNull { it.name == XmlName("image", FB2_NS) }?.href()?.let {
                    meta["cover-image"] = MetaValue.MetaString(it.removePrefix("#"))
                }
                else -> ignored("${e.localName} in title-info")
            }
        }

        fun binary(e: XmlElement) {
            val id = e.attr("id")
            val mime = e.attr("content-type")
            when {
                id == null -> ignored("binary without id attribute")
                mime == null -> ignored("binary without content-type attribute")
                else -> media += MediaInsertion(id, mime, decodeBase64Lenient(e.strContent))
            }
        }

        fun bodyChild(e: XmlElement): Blocks = when (e.localName) {
            "image" -> blockImage(e)
            "title" -> title(e)
            "epigraph" -> epigraph(e)
            "section" -> section(e)
            else -> unexpected(e, "body")
        }

        fun section(e: XmlElement): Blocks {
            level++
            val result = Builder.divWith(Attr(e.attr("id") ?: "", listOf("section")), children(e, ::sectionChild))
            level--
            return result
        }

        fun sectionChild(e: XmlElement): Blocks = when (e.localName) {
            "title" -> title(e)
            "epigraph" -> epigraph(e)
            "image" -> blockImage(e)
            "annotation" -> children(e, ::annotationChild)
            "poem" -> children(e, ::poemChild)
            "cite" -> cite(e)
            "empty-line" -> Builder.horizontalRule
            "table" -> Blocks() // Deliberately unsupported by the original reader.
            "subtitle" -> subtitle(e)
            "p" -> Builder.para(style(e))
            "section" -> section(e)
            else -> unexpected(e, "section")
        }

        fun annotationChild(e: XmlElement): Blocks = when (e.localName) {
            "p" -> Builder.para(style(e))
            "poem" -> children(e, ::poemChild)
            "cite" -> cite(e)
            "subtitle" -> subtitle(e)
            "table" -> Blocks()
            "empty-line" -> Builder.horizontalRule
            else -> unexpected(e, "annotation")
        }

        fun epigraph(e: XmlElement): Blocks = Builder.divWith(
            Attr(e.attr("id") ?: "", listOf("epigraph")), children(e, ::epigraphChild),
        )

        fun epigraphChild(e: XmlElement): Blocks = when (e.localName) {
            "p", "text-author" -> Builder.para(style(e))
            "poem" -> children(e, ::poemChild)
            "cite" -> cite(e)
            "empty-line" -> Builder.horizontalRule
            else -> unexpected(e, "epigraph")
        }

        fun cite(e: XmlElement): Blocks = Builder.blockQuote(children(e, ::citeChild))
        fun citeChild(e: XmlElement): Blocks = when (e.localName) {
            "p", "text-author" -> Builder.para(style(e))
            "poem" -> children(e, ::poemChild)
            "empty-line" -> Builder.horizontalRule
            "subtitle" -> subtitle(e)
            "table" -> Blocks()
            else -> unexpected(e, "cite")
        }

        fun poemChild(e: XmlElement): Blocks = when (e.localName) {
            "title" -> title(e)
            "subtitle" -> subtitle(e)
            "epigraph" -> epigraph(e)
            "stanza" -> stanza(e)
            "text-author" -> Builder.para(style(e))
            "date" -> Builder.para(Builder.text(e.strContent))
            else -> unexpected(e, "poem")
        }

        fun stanza(e: XmlElement): Blocks {
            val result = mutableListOf<Block>()
            for (block in children(e, ::stanzaChild)) {
                val previous = result.lastOrNull()
                if (previous is Block.LineBlock && block is Block.LineBlock)
                    result[result.lastIndex] = Block.LineBlock(previous.lines + block.lines)
                else result += block
            }
            return Blocks(result)
        }

        fun stanzaChild(e: XmlElement): Blocks = when (e.localName) {
            "title" -> title(e)
            "subtitle" -> subtitle(e)
            "v" -> Builder.lineBlock(listOf(style(e)))
            else -> unexpected(e, "stanza")
        }

        fun title(e: XmlElement): Blocks {
            val lines = e.children.mapNotNull {
                when (it.localName) { "p" -> style(it); "empty-line" -> Inlines(); else -> null }
            }
            val content = lines.foldIndexed(Inlines()) { index, acc, line ->
                acc + (if (index == 0) Inlines() else Builder.linebreak) + line
            }
            return Builder.header(level, content)
        }
        fun subtitle(e: XmlElement) = Builder.headerWith(Attr(classes = listOf("unnumbered")), level, style(e))

        fun blockImage(e: XmlElement): Blocks {
            val href = e.href() ?: run { ignored(" image without href"); return Blocks() }
            return Builder.para(Builder.imageWith(Attr(e.attr("id") ?: ""), href.removePrefix("#"),
                e.attr("title") ?: "", e.attr("alt")?.let(Builder::str) ?: Inlines()))
        }

        fun inlineImage(e: XmlElement): Inlines {
            val href = e.href() ?: run { ignored("inline image without href"); return Inlines() }
            return Builder.image(href.removePrefix("#"), "", e.attr("alt")?.let(Builder::str) ?: Inlines())
        }

        fun style(e: XmlElement, context: String = ""): Inlines = e.content.fold(Inlines()) { acc, child -> acc + inline(child, context) }

        fun inline(content: XmlContent, context: String = ""): Inlines {
            if (content is XmlText) return Builder.text(content.text)
            val e = content as XmlElement
            if (context == "link" && e.localName == "a") { ignored("nested link"); return Inlines() }
            return when (e.localName) {
                "strong" -> Builder.strong(style(e))
                "emphasis" -> Builder.emph(style(e))
                "style" -> namedStyle(e)
                "a" -> link(e)
                "strikethrough" -> Builder.strikeout(style(e))
                "sub" -> Builder.subscript(style(e))
                "sup" -> Builder.superscript(style(e))
                "code" -> Builder.code(e.strContent)
                "image" -> inlineImage(e)
                else -> { ignored(e.localName + if (context == "style") " in style" else ""); Inlines() }
            }
        }

        fun namedStyle(e: XmlElement): Inlines {
            val content = style(e, "style")
            val lang = e.attr(XmlName("lang", prefix = "xml"))?.let { listOf("lang" to it) } ?: emptyList()
            val name = e.attr("name") ?: run { ignored("link without required name"); return Inlines() }
            return Builder.spanWith(Attr(classes = listOf(name), keyValues = lang), content)
        }

        fun link(e: XmlElement): Inlines {
            val content = style(e, "link")
            val href = e.href() ?: run { ignored("link without required href"); return Inlines() }
            if (e.attr("type") == "note") notes[href]?.let { return Builder.note(it) }
            return Builder.link(href, "", content)
        }
    }

    private const val FB2_NS = "http://www.gribuser.ru/xml/fictionbook/2.0"
    private fun XmlElement.href() = attr(XmlName("href", "http://www.w3.org/1999/xlink"))

    private fun haskellSpace(c: Char): Boolean = c in '\t'..'\r' || c == ' ' || Character.getType(c) == Character.SPACE_SEPARATOR.toInt()

    /** base64-bytestring decodeLenient ignores every non-alphabet byte, including '='. */
    internal fun decodeBase64Lenient(value: String): ByteArray {
        val output = ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt() and 255
            val digit = when (c) {
                in 65..90 -> c - 65
                in 97..122 -> c - 97 + 26
                in 48..57 -> c - 48 + 52
                43 -> 62
                47 -> 63
                else -> continue
            }
            buffer = (buffer shl 6) or digit
            bits += 6
            if (bits >= 8) { bits -= 8; output.write((buffer ushr bits) and 255) }
        }
        return output.toByteArray()
    }
}
