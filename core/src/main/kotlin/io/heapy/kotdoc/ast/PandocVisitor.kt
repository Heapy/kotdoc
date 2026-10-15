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

    fun walkMeta(meta: Map<String, MetaValue>): Map<String, MetaValue> = walkValue(Kind.Meta, meta)
    fun walkMetaValue(value: MetaValue): MetaValue = walkValue(Kind.MetaValue, value)
    fun walkInlines(values: List<Inline>): List<Inline> = walkValue(Kind.Inlines, values)
    fun walkBlocks(values: List<Block>): List<Block> = walkValue(Kind.Blocks, values)
    fun walkInline(value: Inline): Inline = walkValue(Kind.Inline, value)
    fun walkBlock(value: Block): Block = walkValue(Kind.Block, value)
    fun walkCitation(value: Citation): Citation = walkValue(Kind.Citation, value)
    fun walkCaption(value: Caption): Caption = walkValue(Kind.Caption, value)
    fun walkCell(value: Cell): Cell = walkValue(Kind.Cell, value)
    fun walkRow(value: Row): Row = walkValue(Kind.Row, value)
    fun walkTableHead(value: TableHead): TableHead = walkValue(Kind.TableHead, value)
    fun walkTableBody(value: TableBody): TableBody = walkValue(Kind.TableBody, value)
    fun walkTableFoot(value: TableFoot): TableFoot = walkValue(Kind.TableFoot, value)

    private enum class Kind { Meta, MetaEntries, MetaValue, Inlines, Blocks, Inline, Block, Citation, Caption, Cell, Row, TableHead, TableBody, TableFoot }
    private data class Step(val kind: Kind, val value: Any)

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> walkValue(kind: Kind, value: T): T = walker(Step(kind, value)) as T

    // Each call owns its continuation frames. Hooks retain their original
    // preorder/postorder semantics without consuming the JVM call stack.
    @Suppress("UNCHECKED_CAST")
    private val walker = DeepRecursiveFunction<Step, Any> { (kind, input) ->
        when (kind) {
            Kind.Meta -> {
                val value = input as Map<String, MetaValue>
                visitMeta(value)
                transformMeta(callRecursive(Step(Kind.MetaEntries, value)) as Map<String, MetaValue>)
            }
            Kind.MetaEntries -> {
                val value = input as Map<String, MetaValue>
                value.toSortedMap(UnicodeTextOrder).mapValues { callRecursive(Step(Kind.MetaValue, it.value)) as MetaValue }
            }
            Kind.MetaValue -> {
                val value = input as MetaValue
                visitMetaValue(value)
                transformMetaValue(when (value) {
                    is MetaValue.MetaMap -> value.copy(values = (callRecursive(Step(Kind.MetaEntries, value.values)) as Map<String, MetaValue>))
                    is MetaValue.MetaList -> value.copy(values = value.values.map { callRecursive(Step(Kind.MetaValue, it)) as MetaValue })
                    is MetaValue.MetaInlines -> value.copy(content = (callRecursive(Step(Kind.Inlines, value.content)) as List<Inline>))
                    is MetaValue.MetaBlocks -> value.copy(content = (callRecursive(Step(Kind.Blocks, value.content)) as List<Block>))
                    is MetaValue.MetaBool, is MetaValue.MetaString -> value
                })
            }
            Kind.Inlines -> {
                val value = input as List<Inline>
                visitInlines(value)
                transformInlines(value.map { callRecursive(Step(Kind.Inline, it)) as Inline })
            }
            Kind.Blocks -> {
                val value = input as List<Block>
                visitBlocks(value)
                transformBlocks(value.map { callRecursive(Step(Kind.Block, it)) as Block })
            }
            Kind.Inline -> {
                val value = input as Inline
                visitInline(value)
                transformInline(when (value) {
                    is Inline.Emph -> value.copy(content = (callRecursive(Step(Kind.Inlines, value.content)) as List<Inline>))
                    is Inline.Underline -> value.copy(content = (callRecursive(Step(Kind.Inlines, value.content)) as List<Inline>))
                    is Inline.Strong -> value.copy(content = (callRecursive(Step(Kind.Inlines, value.content)) as List<Inline>))
                    is Inline.Strikeout -> value.copy(content = (callRecursive(Step(Kind.Inlines, value.content)) as List<Inline>))
                    is Inline.Superscript -> value.copy(content = (callRecursive(Step(Kind.Inlines, value.content)) as List<Inline>))
                    is Inline.Subscript -> value.copy(content = (callRecursive(Step(Kind.Inlines, value.content)) as List<Inline>))
                    is Inline.SmallCaps -> value.copy(content = (callRecursive(Step(Kind.Inlines, value.content)) as List<Inline>))
                    is Inline.Quoted -> value.copy(content = (callRecursive(Step(Kind.Inlines, value.content)) as List<Inline>))
                    is Inline.Cite -> value.copy(citations = value.citations.map { callRecursive(Step(Kind.Citation, it)) as Citation }, content = (callRecursive(Step(Kind.Inlines, value.content)) as List<Inline>))
                    is Inline.Link -> value.copy(content = (callRecursive(Step(Kind.Inlines, value.content)) as List<Inline>))
                    is Inline.Image -> value.copy(content = (callRecursive(Step(Kind.Inlines, value.content)) as List<Inline>))
                    is Inline.Note -> value.copy(blocks = (callRecursive(Step(Kind.Blocks, value.blocks)) as List<Block>))
                    is Inline.Span -> value.copy(content = (callRecursive(Step(Kind.Inlines, value.content)) as List<Inline>))
                    is Inline.Str, is Inline.Code, is Inline.Math, is Inline.RawInline,
                    Inline.Space, Inline.SoftBreak, Inline.LineBreak -> value
                })
            }
            Kind.Block -> {
                val value = input as Block
                visitBlock(value)
                transformBlock(when (value) {
                    is Block.Plain -> value.copy(content = (callRecursive(Step(Kind.Inlines, value.content)) as List<Inline>))
                    is Block.Para -> value.copy(content = (callRecursive(Step(Kind.Inlines, value.content)) as List<Inline>))
                    is Block.LineBlock -> value.copy(lines = value.lines.map { callRecursive(Step(Kind.Inlines, it)) as List<Inline> })
                    is Block.BlockQuote -> value.copy(blocks = (callRecursive(Step(Kind.Blocks, value.blocks)) as List<Block>))
                    is Block.OrderedList -> value.copy(items = value.items.map { callRecursive(Step(Kind.Blocks, it)) as List<Block> })
                    is Block.BulletList -> value.copy(items = value.items.map { callRecursive(Step(Kind.Blocks, it)) as List<Block> })
                    is Block.DefinitionList -> value.copy(items = value.items.map { (term, definitions) ->
                        (callRecursive(Step(Kind.Inlines, term)) as List<Inline>) to definitions.map { callRecursive(Step(Kind.Blocks, it)) as List<Block> }
                    })
                    is Block.Header -> value.copy(content = (callRecursive(Step(Kind.Inlines, value.content)) as List<Inline>))
                    is Block.Table -> value.copy(caption = (callRecursive(Step(Kind.Caption, value.caption)) as Caption), head = (callRecursive(Step(Kind.TableHead, value.head)) as TableHead),
                        bodies = value.bodies.map { callRecursive(Step(Kind.TableBody, it)) as TableBody }, foot = (callRecursive(Step(Kind.TableFoot, value.foot)) as TableFoot))
                    is Block.Figure -> value.copy(caption = (callRecursive(Step(Kind.Caption, value.caption)) as Caption), blocks = (callRecursive(Step(Kind.Blocks, value.blocks)) as List<Block>))
                    is Block.Div -> value.copy(blocks = (callRecursive(Step(Kind.Blocks, value.blocks)) as List<Block>))
                    is Block.CodeBlock, is Block.RawBlock, Block.HorizontalRule -> value
                })
            }
            Kind.Citation -> {
                val value = input as Citation
                value.copy(
                    citationPrefix = (callRecursive(Step(Kind.Inlines, value.citationPrefix)) as List<Inline>), citationSuffix = (callRecursive(Step(Kind.Inlines, value.citationSuffix)) as List<Inline>))
            }
            Kind.Caption -> {
                val value = input as Caption
                value.copy(short = value.short?.let { callRecursive(Step(Kind.Inlines, it)) as List<Inline> }, long = (callRecursive(Step(Kind.Blocks, value.long)) as List<Block>))
            }
            Kind.Cell -> {
                val value = input as Cell
                value.copy(blocks = (callRecursive(Step(Kind.Blocks, value.blocks)) as List<Block>))
            }
            Kind.Row -> {
                val value = input as Row
                value.copy(cells = value.cells.map { callRecursive(Step(Kind.Cell, it)) as Cell })
            }
            Kind.TableHead -> {
                val value = input as TableHead
                value.copy(rows = value.rows.map { callRecursive(Step(Kind.Row, it)) as Row })
            }
            Kind.TableBody -> {
                val value = input as TableBody
                value.copy(head = value.head.map { callRecursive(Step(Kind.Row, it)) as Row }, body = value.body.map { callRecursive(Step(Kind.Row, it)) as Row })
            }
            Kind.TableFoot -> {
                val value = input as TableFoot
                value.copy(rows = value.rows.map { callRecursive(Step(Kind.Row, it)) as Row })
            }
        }
    }
}
