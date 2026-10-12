// Port of pandoc-types Text.Pandoc.Walk traversal order and child selection.
// Copyright (c) 2006-2023 John MacFarlane. BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.ast

/**
 * Hooks visit original nodes in preorder and transform nodes in postorder.
 * Returned replacements are not traversed again, matching Pandoc's bottom-up walk.
 * Override one transform hook to implement a specialized Pandoc walk, or use
 * visit hooks to accumulate queries. Attribute strings and targets are not inlines.
 */
open class PandocVisitor {
    open fun visitInline(value: Inline) {}
    open fun visitBlock(value: Block) {}
    open fun visitMetaValue(value: MetaValue) {}
    open fun visitMeta(value: Map<String, MetaValue>) {}
    open fun visitInlines(value: List<Inline>) {}
    open fun visitBlocks(value: List<Block>) {}
    open fun transformInline(value: Inline): Inline = value
    open fun transformBlock(value: Block): Block = value
    open fun transformMetaValue(value: MetaValue): MetaValue = value
    open fun transformMeta(value: Map<String, MetaValue>): Map<String, MetaValue> = value
    open fun transformInlines(value: List<Inline>): List<Inline> = value
    open fun transformBlocks(value: List<Block>): List<Block> = value

    fun walk(document: Pandoc): Pandoc = document.copy(meta = walkMeta(document.meta), blocks = walkBlocks(document.blocks))

    fun walkMeta(meta: Map<String, MetaValue>): Map<String, MetaValue> {
        visitMeta(meta)
        return transformMeta(walkMetaEntries(meta))
    }

    private fun walkMetaEntries(meta: Map<String, MetaValue>): Map<String, MetaValue> =
        meta.toSortedMap().mapValues { walkMetaValue(it.value) }

    fun walkMetaValue(value: MetaValue): MetaValue {
        visitMetaValue(value)
        return transformMetaValue(when (value) {
            is MetaValue.MetaMap -> value.copy(values = walkMetaEntries(value.values))
            is MetaValue.MetaList -> value.copy(values = value.values.map(::walkMetaValue))
            is MetaValue.MetaInlines -> value.copy(content = walkInlines(value.content))
            is MetaValue.MetaBlocks -> value.copy(content = walkBlocks(value.content))
            is MetaValue.MetaBool, is MetaValue.MetaString -> value
        })
    }

    fun walkInlines(values: List<Inline>): List<Inline> {
        visitInlines(values)
        return transformInlines(values.map(::walkInline))
    }

    fun walkBlocks(values: List<Block>): List<Block> {
        visitBlocks(values)
        return transformBlocks(values.map(::walkBlock))
    }

    fun walkInline(value: Inline): Inline {
        visitInline(value)
        return transformInline(when (value) {
            is Inline.Emph -> value.copy(content = walkInlines(value.content))
            is Inline.Underline -> value.copy(content = walkInlines(value.content))
            is Inline.Strong -> value.copy(content = walkInlines(value.content))
            is Inline.Strikeout -> value.copy(content = walkInlines(value.content))
            is Inline.Superscript -> value.copy(content = walkInlines(value.content))
            is Inline.Subscript -> value.copy(content = walkInlines(value.content))
            is Inline.SmallCaps -> value.copy(content = walkInlines(value.content))
            is Inline.Quoted -> value.copy(content = walkInlines(value.content))
            is Inline.Cite -> value.copy(citations = value.citations.map(::walkCitation), content = walkInlines(value.content))
            is Inline.Link -> value.copy(content = walkInlines(value.content))
            is Inline.Image -> value.copy(content = walkInlines(value.content))
            is Inline.Note -> value.copy(blocks = walkBlocks(value.blocks))
            is Inline.Span -> value.copy(content = walkInlines(value.content))
            is Inline.Str, is Inline.Code, is Inline.Math, is Inline.RawInline,
            Inline.Space, Inline.SoftBreak, Inline.LineBreak -> value
        })
    }

    fun walkBlock(value: Block): Block {
        visitBlock(value)
        return transformBlock(when (value) {
            is Block.Plain -> value.copy(content = walkInlines(value.content))
            is Block.Para -> value.copy(content = walkInlines(value.content))
            is Block.LineBlock -> value.copy(lines = value.lines.map(::walkInlines))
            is Block.BlockQuote -> value.copy(blocks = walkBlocks(value.blocks))
            is Block.OrderedList -> value.copy(items = value.items.map(::walkBlocks))
            is Block.BulletList -> value.copy(items = value.items.map(::walkBlocks))
            is Block.DefinitionList -> value.copy(items = value.items.map { (term, definitions) ->
                walkInlines(term) to definitions.map(::walkBlocks)
            })
            is Block.Header -> value.copy(content = walkInlines(value.content))
            is Block.Table -> value.copy(caption = walkCaption(value.caption), head = walkTableHead(value.head),
                bodies = value.bodies.map(::walkTableBody), foot = walkTableFoot(value.foot))
            is Block.Figure -> value.copy(caption = walkCaption(value.caption), blocks = walkBlocks(value.blocks))
            is Block.Div -> value.copy(blocks = walkBlocks(value.blocks))
            is Block.CodeBlock, is Block.RawBlock, Block.HorizontalRule -> value
        })
    }

    fun walkCitation(value: Citation): Citation = value.copy(
        citationPrefix = walkInlines(value.citationPrefix), citationSuffix = walkInlines(value.citationSuffix))
    fun walkCaption(value: Caption): Caption = value.copy(short = value.short?.let(::walkInlines), long = walkBlocks(value.long))
    fun walkCell(value: Cell): Cell = value.copy(blocks = walkBlocks(value.blocks))
    fun walkRow(value: Row): Row = value.copy(cells = value.cells.map(::walkCell))
    fun walkTableHead(value: TableHead): TableHead = value.copy(rows = value.rows.map(::walkRow))
    fun walkTableBody(value: TableBody): TableBody = value.copy(head = value.head.map(::walkRow), body = value.body.map(::walkRow))
    fun walkTableFoot(value: TableFoot): TableFoot = value.copy(rows = value.rows.map(::walkRow))
}
