// Port of pandoc-types Text.Pandoc.Builder.
// Copyright (c) 2006-2023 John MacFarlane. BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.ast

data class Inlines(val values: List<Inline> = emptyList()) : Iterable<Inline> {
    override fun iterator(): Iterator<Inline> = values.iterator()

    /** Only meld the touching endpoints; nested inline lists use ordinary concatenation. */
    operator fun plus(other: Inlines): Inlines {
        val a = values.lastOrNull() ?: return other
        val b = other.values.firstOrNull() ?: return this
        val merged: Inline? = when {
            a == Inline.Space && b == Inline.Space -> Inline.Space
            a in listOf(Inline.Space, Inline.SoftBreak) && b in listOf(Inline.Space, Inline.SoftBreak) -> Inline.SoftBreak
            a == Inline.LineBreak && b in listOf(Inline.Space, Inline.SoftBreak) -> Inline.LineBreak
            b == Inline.LineBreak && a in listOf(Inline.Space, Inline.SoftBreak) -> Inline.LineBreak
            a is Inline.Str && b is Inline.Str -> Inline.Str(a.text + b.text)
            a is Inline.Emph && b is Inline.Emph -> Inline.Emph(a.content + b.content)
            a is Inline.Underline && b is Inline.Underline -> Inline.Underline(a.content + b.content)
            a is Inline.Strong && b is Inline.Strong -> Inline.Strong(a.content + b.content)
            a is Inline.Subscript && b is Inline.Subscript -> Inline.Subscript(a.content + b.content)
            a is Inline.Superscript && b is Inline.Superscript -> Inline.Superscript(a.content + b.content)
            a is Inline.Strikeout && b is Inline.Strikeout -> Inline.Strikeout(a.content + b.content)
            else -> null
        }
        return if (merged == null) Inlines(values + other.values)
        else Inlines(values.dropLast(1) + merged + other.values.drop(1))
    }

    fun trim(): Inlines = Inlines(values.dropWhile(::isTrimSpace).dropLastWhile(::isTrimSpace))
    fun toMetaValue(): MetaValue = MetaValue.MetaInlines(values)
    private fun isTrimSpace(value: Inline): Boolean = value == Inline.Space || value == Inline.SoftBreak
}

data class Blocks(val values: List<Block> = emptyList()) : Iterable<Block> {
    override fun iterator(): Iterator<Block> = values.iterator()
    operator fun plus(other: Blocks): Blocks = Blocks(values + other.values)
    fun toMetaValue(): MetaValue = MetaValue.MetaBlocks(values)
}

/** Type-directed metadata conversion; converters compose for arbitrary lists and maps. */
fun String.toMetaValue(): MetaValue = MetaValue.MetaString(this)
fun Boolean.toMetaValue(): MetaValue = MetaValue.MetaBool(this)
fun MetaValue.toMetaValue(): MetaValue = this
fun <T> List<T>.toMetaValue(convert: (T) -> MetaValue): MetaValue = MetaValue.MetaList(map(convert))
fun <T> Map<String, T>.toMetaValue(convert: (T) -> MetaValue): MetaValue = MetaValue.MetaMap(mapValues { convert(it.value) })

object Builder {
    fun doc(blocks: Blocks): Pandoc = Pandoc(blocks = blocks.values)
    fun str(value: String): Inlines = Inlines(listOf(Inline.Str(value)))

    fun text(value: String): Inlines {
        val result = mutableListOf<Inline>()
        var start = 0
        while (start < value.length) {
            val whitespace = isSpace(value[start])
            var end = start + 1
            while (end < value.length && isSpace(value[end]) == whitespace) end++
            val chunk = value.substring(start, end)
            result += if (!whitespace) Inline.Str(chunk)
            else if (chunk.any { it == '\r' || it == '\n' }) Inline.SoftBreak else Inline.Space
            start = end
        }
        return Inlines(result)
    }

    private fun isSpace(char: Char): Boolean = char == ' ' || char == '\r' || char == '\n' || char == '\t'
    fun emph(value: Inlines): Inlines = Inlines(listOf(Inline.Emph(value.values)))
    fun underline(value: Inlines): Inlines = Inlines(listOf(Inline.Underline(value.values)))
    fun strong(value: Inlines): Inlines = Inlines(listOf(Inline.Strong(value.values)))
    fun strikeout(value: Inlines): Inlines = Inlines(listOf(Inline.Strikeout(value.values)))
    fun superscript(value: Inlines): Inlines = Inlines(listOf(Inline.Superscript(value.values)))
    fun subscript(value: Inlines): Inlines = Inlines(listOf(Inline.Subscript(value.values)))
    fun smallcaps(value: Inlines): Inlines = Inlines(listOf(Inline.SmallCaps(value.values)))
    fun singleQuoted(value: Inlines): Inlines = Inlines(listOf(Inline.Quoted(QuoteType.SingleQuote, value.values)))
    fun doubleQuoted(value: Inlines): Inlines = Inlines(listOf(Inline.Quoted(QuoteType.DoubleQuote, value.values)))
    fun cite(citations: List<Citation>, value: Inlines): Inlines = Inlines(listOf(Inline.Cite(citations, value.values)))
    fun codeWith(attr: Attr, value: String): Inlines = Inlines(listOf(Inline.Code(attr, value)))
    fun code(value: String): Inlines = codeWith(Attr(), value)
    val space = Inlines(listOf(Inline.Space))
    val softbreak = Inlines(listOf(Inline.SoftBreak))
    val linebreak = Inlines(listOf(Inline.LineBreak))
    fun math(value: String): Inlines = Inlines(listOf(Inline.Math(MathType.InlineMath, value)))
    fun displayMath(value: String): Inlines = Inlines(listOf(Inline.Math(MathType.DisplayMath, value)))
    fun rawInline(format: String, value: String): Inlines = Inlines(listOf(Inline.RawInline(format, value)))
    fun linkWith(attr: Attr, url: String, title: String, label: Inlines): Inlines = Inlines(listOf(Inline.Link(attr, label.values, Target(url, title))))
    fun link(url: String, title: String, label: Inlines): Inlines = linkWith(Attr(), url, title, label)
    fun imageWith(attr: Attr, url: String, title: String, alt: Inlines): Inlines = Inlines(listOf(Inline.Image(attr, alt.values, Target(url, title))))
    fun image(url: String, title: String, alt: Inlines): Inlines = imageWith(Attr(), url, title, alt)
    fun note(blocks: Blocks): Inlines = Inlines(listOf(Inline.Note(blocks.values)))
    fun spanWith(attr: Attr, value: Inlines): Inlines = Inlines(listOf(Inline.Span(attr, value.values)))
    fun trimInlines(value: Inlines): Inlines = value.trim()

    fun para(value: Inlines): Blocks = Blocks(listOf(Block.Para(value.values)))
    fun plain(value: Inlines): Blocks = if (value.values.isEmpty()) Blocks() else Blocks(listOf(Block.Plain(value.values)))
    fun lineBlock(lines: List<Inlines>): Blocks = Blocks(listOf(Block.LineBlock(lines.map { it.values })))
    fun codeBlockWith(attr: Attr, value: String): Blocks = Blocks(listOf(Block.CodeBlock(attr, value)))
    fun codeBlock(value: String): Blocks = codeBlockWith(Attr(), value)
    fun rawBlock(format: String, value: String): Blocks = Blocks(listOf(Block.RawBlock(format, value)))
    fun blockQuote(value: Blocks): Blocks = Blocks(listOf(Block.BlockQuote(value.values)))
    fun orderedListWith(attr: ListAttributes, items: List<Blocks>): Blocks = Blocks(listOf(Block.OrderedList(attr, items.map { it.values })))
    fun orderedList(items: List<Blocks>): Blocks = orderedListWith(ListAttributes(), items)
    fun bulletList(items: List<Blocks>): Blocks = Blocks(listOf(Block.BulletList(items.map { it.values })))
    fun definitionList(items: List<Pair<Inlines, List<Blocks>>>): Blocks = Blocks(listOf(Block.DefinitionList(items.map { (term, definitions) -> term.values to definitions.map { it.values } })))
    fun headerWith(attr: Attr, level: Long, value: Inlines): Blocks = Blocks(listOf(Block.Header(level, attr, value.values)))
    fun header(level: Long, value: Inlines): Blocks = headerWith(Attr(), level, value)
    val horizontalRule = Blocks(listOf(Block.HorizontalRule))
    fun cellWith(attr: Attr, alignment: Alignment, rows: Long, cols: Long, content: Blocks): Cell = Cell(attr, alignment, rows, cols, content.values)
    fun cell(alignment: Alignment, rows: Long, cols: Long, content: Blocks): Cell = cellWith(Attr(), alignment, rows, cols, content)
    fun simpleCell(content: Blocks): Cell = cell(Alignment.AlignDefault, 1, 1, content)
    val emptyCell: Cell get() = Tables.emptyCell
    val emptyCaption: Caption get() = Tables.emptyCaption
    fun tableWith(attr: Attr, caption: Caption, columns: List<ColSpec>, head: TableHead, bodies: List<TableBody>, foot: TableFoot): Blocks =
        Blocks(TableBuilders.tableWith({attr}, {caption}, {columns}, {head}, {bodies}, {foot}).toSourceList())
    fun table(caption: Caption, columns: List<ColSpec>, head: TableHead, bodies: List<TableBody>, foot: TableFoot): Blocks =
        Blocks(TableBuilders.table({caption}, {columns}, {head}, {bodies}, {foot}).toSourceList())
    fun simpleTable(headers: List<Blocks>, rows: List<List<Blocks>>): Blocks =
        Blocks(TableBuilders.simpleTable(
            {TableStreams.mapped({headers}) { Many.fromList(it().values) }},
            {TableStreams.mapped({rows}) { row -> TableStreams.mapped(row) { Many.fromList(it().values) } }}
        ).toSourceList())
    fun caption(short: List<Inline>?, content: Blocks): Caption = Caption(short, content.values)
    fun simpleCaption(content: Blocks): Caption = caption(null, content)
    fun figureWith(attr: Attr, caption: Caption, content: Blocks): Blocks = Blocks(listOf(Block.Figure(attr, caption, content.values)))
    fun figure(caption: Caption, content: Blocks): Blocks = figureWith(Attr(), caption, content)
    fun simpleFigureWith(attr: Attr, caption: Inlines, url: String, title: String): Blocks =
        figure(simpleCaption(plain(caption)), plain(imageWith(attr, url, title, Inlines())))
    fun simpleFigure(caption: Inlines, url: String, title: String): Blocks = simpleFigureWith(Attr(), caption, url, title)
    fun divWith(attr: Attr, content: Blocks): Blocks = Blocks(listOf(Block.Div(attr, content.values)))
}
