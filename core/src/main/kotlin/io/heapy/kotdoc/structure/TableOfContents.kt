// Port of Text.Pandoc.Chunks and Writers.Shared.toTableOfContents.
// Copyright (C) 2006-2024 John MacFarlane. GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.structure

import io.heapy.kotdoc.ast.*

data class Tree<T>(val value: T, val children: List<Tree<T>> = emptyList())

data class SecInfo(
    val title: List<Inline>,
    val number: String?,
    val id: String,
    val path: String,
    val level: Long,
) {
    /** Pandoc's Walkable Inline instance visits only the title. */
    fun walk(visitor: PandocVisitor): SecInfo = copy(title = visitor.walkInlines(title))
    fun <T> queryInlines(query: (Inline) -> Iterable<T>): List<T> = collectInlines(query) { it.walkInlines(title) }
}

object TableOfContents {
    /** Input is a section hierarchy, normally produced by makeSections. */
    fun toTOCTree(sections: List<Block>): Tree<SecInfo> {
        fun children(blocks: List<Block>): List<Tree<SecInfo>> = blocks.flatMap { block ->
            if (block !is Block.Div) return@flatMap emptyList()
            val header = block.blocks.firstOrNull() as? Block.Header
            val number = header?.attr?.keyValues?.firstOrNull { it.first == "number" }?.second
            when {
                header != null && (number != null || "unlisted" !in header.attr.classes) ->
                    listOf(Tree(SecInfo(header.content, number, block.attr.identifier, "", header.level), children(block.blocks.drop(1))))
                block.blocks.singleOrNull() is Block.Div -> children(block.blocks)
                else -> emptyList()
            }
        }
        return Tree(SecInfo(emptyList(), null, "", "", 0), children(sections))
    }

    fun tocToList(tree: Tree<SecInfo>, includeNumbers: Boolean = false, depth: Long = 3): Block.BulletList {
        fun items(trees: List<Tree<SecInfo>>): List<List<Block>> = trees.filter { it.value.level <= depth }.map { tree ->
            val nested = items(tree.children)
            listOf(Block.Plain(entryToLink(tree.value, includeNumbers))) +
                if (nested.isEmpty()) emptyList() else listOf(Block.BulletList(nested))
        }
        return Block.BulletList(items(tree.children))
    }

    fun toTableOfContents(blocks: List<Block>, numberSections: Boolean = false, depth: Long = 3): Block.BulletList =
        tocToList(toTOCTree(DocumentShared.makeSections(blocks, numberSections)), numberSections, depth)

    private fun entryToLink(section: SecInfo, includeNumbers: Boolean): List<Inline> {
        val clean = object : PandocVisitor() {
            override fun transformInlines(value: List<Inline>): List<Inline> = value.flatMap {
                when (it) {
                    is Inline.Link -> it.content
                    is Inline.Note -> emptyList()
                    else -> listOf(it)
                }
            }
        }.walkInlines(section.title)
        val title = if (includeNumbers && section.number != null)
            listOf(Inline.Span(Attr(classes = listOf("toc-section-number")), listOf(Inline.Str(section.number))), Inline.Space) + clean
        else clean
        val anchor = section.path.ifEmpty { if (section.id.isEmpty()) "" else "#${section.id}" }
        return if (anchor.isEmpty()) title else listOf(Inline.Link(
            Attr(identifier = if (section.id.isEmpty()) "" else "toc-${section.id}"), title, Target(anchor)))
    }
}
