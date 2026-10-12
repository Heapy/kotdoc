// Port of pandoc-types Text.Pandoc.Definition.
// Copyright (c) 2006-2023 John MacFarlane. BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.ast

data class Pandoc(val meta: Map<String, MetaValue> = emptyMap(), val blocks: List<Block> = emptyList())

sealed interface MetaValue {
    data class MetaMap(val values: Map<String, MetaValue>) : MetaValue
    data class MetaList(val values: List<MetaValue>) : MetaValue
    data class MetaBool(val value: Boolean) : MetaValue
    data class MetaString(val value: String) : MetaValue
    data class MetaInlines(val content: List<Inline>) : MetaValue
    data class MetaBlocks(val content: List<Block>) : MetaValue
}

/** A list preserves order and duplicate attribute keys, unlike a Map. */
data class Attr(
    val identifier: String = "",
    val classes: List<String> = emptyList(),
    val keyValues: List<Pair<String, String>> = emptyList(),
)

enum class ListNumberStyle { DefaultStyle, Example, Decimal, LowerRoman, UpperRoman, LowerAlpha, UpperAlpha }
enum class ListNumberDelim { DefaultDelim, Period, OneParen, TwoParens }
data class ListAttributes(
    val start: Int = 1,
    val style: ListNumberStyle = ListNumberStyle.DefaultStyle,
    val delimiter: ListNumberDelim = ListNumberDelim.DefaultDelim,
)
enum class Alignment { AlignLeft, AlignRight, AlignCenter, AlignDefault }
enum class QuoteType { SingleQuote, DoubleQuote }
enum class MathType { DisplayMath, InlineMath }
enum class CitationMode { AuthorInText, SuppressAuthor, NormalCitation }

data class Target(val url: String, val title: String = "")
data class Citation(
    val citationId: String,
    val citationPrefix: List<Inline> = emptyList(),
    val citationSuffix: List<Inline> = emptyList(),
    val citationMode: CitationMode = CitationMode.NormalCitation,
    val citationNoteNum: Int = 0,
    val citationHash: Int = 0,
)

sealed interface ColWidth {
    data class Width(val value: Double) : ColWidth
    data object Default : ColWidth
}
data class ColSpec(val alignment: Alignment, val width: ColWidth)
data class Caption(val short: List<Inline>?, val long: List<Block>)
data class Row(val attr: Attr, val cells: List<Cell>)
data class Cell(val attr: Attr, val alignment: Alignment, val rowSpan: Int, val colSpan: Int, val blocks: List<Block>)
data class TableHead(val attr: Attr, val rows: List<Row>)
data class TableBody(val attr: Attr, val rowHeadColumns: Int, val head: List<Row>, val body: List<Row>)
data class TableFoot(val attr: Attr, val rows: List<Row>)

sealed interface Block {
    data class Plain(val content: List<Inline>) : Block
    data class Para(val content: List<Inline>) : Block
    data class LineBlock(val lines: List<List<Inline>>) : Block
    data class CodeBlock(val attr: Attr, val text: String) : Block
    data class RawBlock(val format: String, val text: String) : Block
    data class BlockQuote(val blocks: List<Block>) : Block
    data class OrderedList(val attributes: ListAttributes, val items: List<List<Block>>) : Block
    data class BulletList(val items: List<List<Block>>) : Block
    data class DefinitionList(val items: List<Pair<List<Inline>, List<List<Block>>>>) : Block
    data class Header(val level: Int, val attr: Attr, val content: List<Inline>) : Block
    data object HorizontalRule : Block
    data class Table(
        val attr: Attr,
        val caption: Caption,
        val columns: List<ColSpec>,
        val head: TableHead,
        val bodies: List<TableBody>,
        val foot: TableFoot,
    ) : Block
    data class Figure(val attr: Attr, val caption: Caption, val blocks: List<Block>) : Block
    data class Div(val attr: Attr, val blocks: List<Block>) : Block
}

sealed interface Inline {
    data class Str(val text: String) : Inline
    data class Emph(val content: List<Inline>) : Inline
    data class Underline(val content: List<Inline>) : Inline
    data class Strong(val content: List<Inline>) : Inline
    data class Strikeout(val content: List<Inline>) : Inline
    data class Superscript(val content: List<Inline>) : Inline
    data class Subscript(val content: List<Inline>) : Inline
    data class SmallCaps(val content: List<Inline>) : Inline
    data class Quoted(val type: QuoteType, val content: List<Inline>) : Inline
    data class Cite(val citations: List<Citation>, val content: List<Inline>) : Inline
    data class Code(val attr: Attr, val text: String) : Inline
    data object Space : Inline
    data object SoftBreak : Inline
    data object LineBreak : Inline
    data class Math(val type: MathType, val text: String) : Inline
    data class RawInline(val format: String, val text: String) : Inline
    data class Link(val attr: Attr, val content: List<Inline>, val target: Target) : Inline
    data class Image(val attr: Attr, val content: List<Inline>, val target: Target) : Inline
    data class Note(val blocks: List<Block>) : Inline
    data class Span(val attr: Attr, val content: List<Inline>) : Inline
}
