// Port of Text.Pandoc.Slides.
// Copyright (C) 2012–2024 John MacFarlane. GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.structure

import io.heapy.kotdoc.ast.*

object Slides {
    /** Minimum eligible heading level, capped at six, with Pandoc's adjacency/Div traversal rules. */
    fun getSlideLevel(blocks: List<Block>): Long {
        data class Branch(val blocks: List<Block>, val start: Int, val least: Long)
        val pending = ArrayDeque<Branch>()
        pending.add(Branch(blocks, 0, 6))
        var result = 6L
        while (pending.isNotEmpty()) {
            val branch = pending.removeLast()
            var least = branch.least
            var index = branch.start
            while (index < branch.blocks.size) {
                val block = branch.blocks[index]
                val next = branch.blocks.getOrNull(index + 1)
                when {
                    block is Block.Header && next != null && block.level < least &&
                        next !is Block.Header && next != Block.HorizontalRule -> {
                        least = block.level
                        index += 2 // the immediately following block is consumed, including a Div
                    }
                    block is Block.Div -> {
                        // Both original recursive branches receive the SAME incoming least value.
                        pending.add(Branch(branch.blocks, index + 1, least))
                        pending.add(Branch(block.blocks, 0, least))
                        break
                    }
                    else -> index++
                }
            }
            if (index == branch.blocks.size) result = minOf(result, least)
        }
        return result
    }

    /** Prepare a finite block list for makeSections; this is not a presentation writer. */
    fun prepSlides(slideLevel: Long, blocks: List<Block>): List<Block> {
        val last = blocks.lastOrNull() as? Block.Div
        val referenceHeader = last?.blocks?.firstOrNull() as? Block.Header
        val extracted = if (last?.attr?.identifier == "refs" && referenceHeader != null)
            blocks.dropLast(1) + referenceHeader + last.copy(blocks = last.blocks.drop(1))
        else blocks
        fun separator() = Block.Header(slideLevel, Attr(), listOf(Inline.Str("\u0000")))
        val split = buildList {
            var index = 0
            while (index < extracted.size) {
                val block = extracted[index++]
                if (block != Block.HorizontalRule) add(block)
                else {
                    val next = extracted.getOrNull(index) as? Block.Header
                    if (next?.level == slideLevel) { add(next); index++ }
                    else add(separator())
                }
            }
        }
        val first = split.firstOrNull()
        val firstHeader = (first as? Block.Header) ?: (first as? Block.Div)?.blocks?.firstOrNull() as? Block.Header
        return if (firstHeader != null && firstHeader.level <= slideLevel) split else listOf(separator()) + split
    }
}
