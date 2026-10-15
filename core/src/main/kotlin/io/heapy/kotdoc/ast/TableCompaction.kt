// Text.Pandoc.Shared.compactifyTable, Copyright (C) 2006-2024 John MacFarlane.
// GPL-2.0-or-later; licenses/pandoc-GPL-2.0.md.
package io.heapy.kotdoc.ast

object TableCompaction {
    /** Explicit Walkable dictionary preserves the source's polymorphic scope. */
    fun <A> compactify(value: A, walk: (PandocVisitor, A) -> A): A {
        var simple = true
        walk(object : PandocVisitor() {
            override fun visitBlocks(value: List<Block>) {
                if (value.size > 1 || value.size == 1 && value[0] !is Block.Plain && value[0] !is Block.Para) simple = false
            }
        }, value)
        if (!simple) return value
        val plain = walk(object : PandocVisitor() {
            override fun transformBlock(value: Block): Block = if (value is Block.Para) Block.Plain(value.content) else value
        }, value)
        return walk(object : PandocVisitor() {
            override fun transformInline(value: Inline): Inline =
                if (value is Inline.Note && value.blocks.size == 1 && value.blocks[0] is Block.Plain)
                    Inline.Note(listOf(Block.Para((value.blocks[0] as Block.Plain).content))) else value
        }, plain)
    }

    // Builder.Many uses the generic Traversable instance: it visits each Block
    // without treating the outer container as an occurrence of [Block].
    fun compactify(value: Blocks): Blocks = compactify(value) { v, x -> Blocks(x.values.map(v::walkBlock)) }
}
