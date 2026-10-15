// Text.Pandoc.Shared.onlySimpleTableCells, © 2006-2024 John MacFarlane.
// GPL-2.0-or-later; original source in upstream/pandoc.
package io.heapy.kotdoc.ast

import java.util.ArrayDeque

object TableSimplicity {
    fun onlySimpleTableCells(rows: List<List<List<Block>>>): Boolean = rows.all { row -> row.all { blocks ->
        when {
            blocks.isEmpty() -> true
            blocks.size != 1 -> false
            else -> when (val block = blocks[0]) {
                is Block.Plain -> !hasLineBreak(block.content)
                is Block.Para -> !hasLineBreak(block.content)
                else -> false
            }
        }
    } }
    // query isLineBreak traverses citations, notes, captions and nested tables too.
    private fun hasLineBreak(inlines: List<Inline>): Boolean {
        val work = ArrayDeque<Any>(); work.add(inlines)
        fun add(value: Any?) { if (value != null) work.add(value) }
        while (work.isNotEmpty()) when (val node = work.removeLast()) {
            Inline.LineBreak -> return true
            is Inline.Cite -> { add(node.citations); add(node.content) }
            is Inline.Note -> add(node.blocks)
            is Inline -> add(DocumentShared.inlineChildren(node))
            is Citation -> { add(node.citationPrefix); add(node.citationSuffix) }
            is Block.Plain -> add(node.content)
            is Block.Para -> add(node.content)
            is Block.LineBlock -> add(node.lines)
            is Block.BlockQuote -> add(node.blocks)
            is Block.OrderedList -> add(node.items)
            is Block.BulletList -> add(node.items)
            is Block.DefinitionList -> add(node.items)
            is Block.Header -> add(node.content)
            is Block.Table -> { add(node.caption); add(node.head); add(node.bodies); add(node.foot) }
            is Block.Figure -> { add(node.caption); add(node.blocks) }
            is Block.Div -> add(node.blocks)
            is Caption -> { add(node.short); add(node.long) }
            is TableHead -> add(node.rows)
            is TableBody -> { add(node.head); add(node.body) }
            is TableFoot -> add(node.rows)
            is Row -> add(node.cells)
            is Cell -> add(node.blocks)
            is Pair<*, *> -> { add(node.first); add(node.second) }
            is List<*> -> node.forEach(::add)
        }
        return false
    }
}
