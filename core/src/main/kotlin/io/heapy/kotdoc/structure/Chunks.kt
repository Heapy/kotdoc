// Port of Text.Pandoc.Chunks.
// Copyright (C) 2022–2024 John MacFarlane. GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.structure

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.html.HtmlTag
import io.heapy.kotdoc.html.TagSoup

/** Navigation stores immutable snapshots from the original three passes, not cyclic references. */
data class Chunk(
    val heading: List<Inline>,
    val id: String,
    val level: Long,
    val number: Long,
    val sectionNumber: String?,
    val path: String,
    val up: Chunk? = null,
    val prev: Chunk? = null,
    val next: Chunk? = null,
    val unlisted: Boolean = false,
    val contents: List<Block>,
) {
    /** Only contents are walked: heading and navigation snapshots remain untouched. */
    fun walk(visitor: PandocVisitor): Chunk = copy(contents = visitor.walkBlocks(contents))
    fun <T> queryInlines(query: (Inline) -> Iterable<T>): List<T> = collectInlines(query) { it.walkBlocks(contents) }
    fun <T> queryBlocks(query: (Block) -> Iterable<T>): List<T> = collectBlocks(query) { it.walkBlocks(contents) }
}

data class ChunkedDoc(val meta: Map<String, MetaValue>, val toc: Tree<SecInfo>, val chunks: List<Chunk>) {
    /** Walk effects occur in metadata first, then chunks. The TOC is not walked. */
    fun walk(visitor: PandocVisitor): ChunkedDoc = copy(
        meta = visitor.walkMeta(meta), chunks = chunks.map { it.walk(visitor) })

    /** Query order intentionally differs from walk: chunk contents precede metadata. */
    fun <T> queryInlines(query: (Inline) -> Iterable<T>): List<T> = collectInlines(query) { visitor ->
        chunks.forEach { visitor.walkBlocks(it.contents) }
        visitor.walkMeta(meta)
    }
    fun <T> queryBlocks(query: (Block) -> Iterable<T>): List<T> = collectBlocks(query) { visitor ->
        chunks.forEach { visitor.walkBlocks(it.contents) }
        visitor.walkMeta(meta)
    }
}

internal fun <T> collectInlines(query: (Inline) -> Iterable<T>, walk: (PandocVisitor) -> Unit): List<T> = buildList {
    walk(object : PandocVisitor() { override fun visitInline(value: Inline) { addAll(query(value)) } })
}
private fun <T> collectBlocks(query: (Block) -> Iterable<T>, walk: (PandocVisitor) -> Unit): List<T> = buildList {
    walk(object : PandocVisitor() { override fun visitBlock(value: Block) { addAll(query(value)) } })
}

object Chunks {
    /** Defaults match the original pandoc.structure.split_into_chunks API. */
    fun splitIntoChunks(
        document: Pandoc,
        pathTemplate: PathTemplate = PathTemplate("chunk-%n"),
        numberSections: Boolean = false,
        baseLevel: Long? = null,
        chunkLevel: Long = 1,
    ): ChunkedDoc {
        val sections = ensureIds(DocumentShared.makeSections(document.blocks, numberSections, baseLevel))
        val chunks = makeChunks(sections, document.meta, pathTemplate, chunkLevel)
        val toc = fixTocPaths(chunks, TableOfContents.toTOCTree(sections))
        return addNavigation(fixInternalReferences(ChunkedDoc(document.meta, toc, chunks)))
    }

    private fun ensureIds(blocks: List<Block>): List<Block> {
        val used = mutableSetOf<String>()
        return object : PandocVisitor() {
            override fun transformBlock(value: Block): Block {
                if (value !is Block.Div || value.attr.classes.firstOrNull() != "section") return value
                val header = value.blocks.firstOrNull() as? Block.Header ?: return value
                val id = value.attr.identifier.ifEmpty { Identifiers.uniqueIdent(header.content, used) }
                used.add(id)
                return value.copy(attr = value.attr.copy(identifier = id))
            }
        }.walkBlocks(blocks)
    }

    private fun makeChunks(blocks: List<Block>, meta: Map<String, MetaValue>, template: PathTemplate, level: Long): List<Chunk> {
        fun isChunkHeader(block: Block): Boolean = block is Block.Div &&
            block.attr.classes.firstOrNull() == "section" &&
            (block.blocks.firstOrNull() as? Block.Header)?.let { it.level <= level } == true

        fun section(number: Long, div: Block.Div): Chunk {
            val header = div.blocks.first() as Block.Header
            val sectionNumber = div.attr.keyValues.firstOrNull { it.first == "number" }?.second
            return Chunk(header.content, div.attr.identifier, header.level, number, sectionNumber,
                template.resolve(number, DocumentShared.stringifyInlines(header.content), div.attr.identifier, sectionNumber ?: ""),
                unlisted = "unlisted" in div.attr.classes.drop(1), contents = listOf(div))
        }
        fun preamble(number: Long, contents: List<Block>): Chunk {
            val title = docTitle(meta)
            val id = Identifiers.inlineListToIdentifier(title) + "-$number"
            return Chunk(title, id, 0, number, null,
                template.resolve(number, DocumentShared.stringifyInlines(title), id, "0"), contents = contents)
        }

        // A deque preserves the original ys ++ rest ordering without repeatedly copying the suffix.
        val pending = ArrayDeque(blocks)
        return buildList {
            var number = 1L
            while (pending.isNotEmpty()) {
                if (!isChunkHeader(pending.first())) {
                    val prefix = buildList { while (pending.isNotEmpty() && !isChunkHeader(pending.first())) add(pending.removeFirst()) }
                    add(preamble(number++, prefix))
                    continue
                }
                val div = pending.removeFirst() as Block.Div
                val header = div.blocks.first() as Block.Header
                if (header.level == level) add(section(number++, div))
                else {
                    val split = (1..<div.blocks.size).firstOrNull { isChunkHeader(div.blocks[it]) } ?: div.blocks.size
                    add(section(number++, div.copy(blocks = div.blocks.subList(0, split))))
                    for (i in div.blocks.lastIndex downTo split) pending.addFirst(div.blocks[i])
                }
            }
        }
    }

    private fun fixInternalReferences(document: ChunkedDoc): ChunkedDoc {
        val references = mutableMapOf<String, String>()
        fun rawIds(format: Format, text: String): List<String> =
            if (format.value in listOf("html", "html4", "html5")) TagSoup.parseTextTags(text)
                .filterIsInstance<HtmlTag.Open>().map { it.attribute("id") }.filter { it.isNotEmpty() }
            else emptyList()
        fun id(value: String): List<String> = if (value.isEmpty()) emptyList() else listOf(value)
        for (chunk in document.chunks) {
            // Chunk IDs include empty strings upstream; node attribute IDs exclude them.
            val ids = listOf(chunk.id) + chunk.queryBlocks { block -> when (block) {
                is Block.Div -> id(block.attr.identifier)
                is Block.Header -> id(block.attr.identifier)
                is Block.Table -> id(block.attr.identifier)
                is Block.RawBlock -> rawIds(block.format, block.text)
                else -> emptyList()
            } } + chunk.queryInlines { inline -> when (inline) {
                is Inline.Span -> id(inline.attr.identifier)
                is Inline.Link -> id(inline.attr.identifier)
                is Inline.Image -> id(inline.attr.identifier)
                is Inline.RawInline -> rawIds(inline.format, inline.text)
                else -> emptyList()
            } }
            ids.forEach { references.putIfAbsent(it, chunk.path) }
        }
        return document.walk(object : PandocVisitor() {
            override fun transformInline(value: Inline): Inline {
                if (value !is Inline.Link || !value.target.url.startsWith('#')) return value
                val path = references[value.target.url.substring(1)] ?: return value
                return value.copy(target = value.target.copy(url = path + value.target.url))
            }
        })
    }

    private fun fixTocPaths(chunks: List<Chunk>, tree: Tree<SecInfo>): Tree<SecInfo> {
        val paths = mutableMapOf<String, String>()
        for (chunk in chunks) {
            val ids = listOf(chunk.id) + chunk.queryBlocks {
                if (it is Block.Div && it.attr.classes.firstOrNull() == "section") listOf(it.attr.identifier) else emptyList()
            }
            ids.filter { it.isNotEmpty() }.forEach { paths.putIfAbsent(it, chunk.path) }
        }
        fun fix(tree: Tree<SecInfo>, inheritedPath: String): Tree<SecInfo> {
            val path = paths[tree.value.id] ?: inheritedPath
            return Tree(tree.value.copy(path = "$path#${tree.value.id}"), tree.children.map { fix(it, path) })
        }
        return fix(tree, "")
    }

    private fun addNavigation(document: ChunkedDoc): ChunkedDoc {
        val withUp = buildList<Chunk> {
            for (chunk in document.chunks) {
                val previous = lastOrNull()
                add(when {
                    previous == null -> chunk
                    previous.level < chunk.level -> chunk.copy(up = previous)
                    previous.level == chunk.level -> chunk.copy(up = previous.up)
                    else -> chunk
                })
            }
        }
        val withPrev = withUp.mapIndexed { index, chunk -> chunk.copy(prev = withUp.getOrNull(index - 1)) }
        val withNext = withPrev.mapIndexed { index, chunk -> chunk.copy(next = withPrev.getOrNull(index + 1)) }
        return document.copy(chunks = withNext)
    }
}
