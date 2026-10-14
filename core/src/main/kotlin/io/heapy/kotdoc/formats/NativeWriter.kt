// Port of Pandoc 3.12.1 Text.Pandoc.Writers.Native.
// Copyright (C) 2006-2024 John MacFarlane. GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.formats

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.ast.Target
import io.heapy.kotdoc.options.WriterOptions
import kotlin.math.round

/** Pandoc's Haskell-readable native format, including HughesPJ-compatible layout. */
object NativeWriter {
    fun write(document: Pandoc, options: WriterOptions): String {
        val ribbon = HaskellNumber.round((options.columns.toFloat() / 1.2f).toDouble()).toLong()
        val standalone = options.template != null
        val value = if (standalone) con("Pandoc", meta(document.meta), blocks(document.blocks)) else blocks(document.blocks)
        return render(options.columns, ribbon, if (standalone) 1 else 0, value.doc) + if (standalone) "\n" else ""
    }
    fun write(document: Pandoc, columns: Int = 72, standalone: Boolean = false): String {
        val ribbon = round(columns.toFloat() / 1.2f).toInt()
        val value = if (standalone) con("Pandoc", meta(document.meta), blocks(document.blocks)) else blocks(document.blocks)
        return render(columns.toLong(), ribbon.toLong(), if (standalone) 1 else 0, value.doc) + if (standalone) "\n" else ""
    }

    private sealed interface Doc { val width: Int }
    private data class Text(val text: String) : Doc { override val width: Int = text.codePointCount(0, text.length) }
    private data class Group(val elements: List<Element>) : Doc {
        override val width: Int = elements.sumOf { it.prefix.length + it.doc.width + it.suffix.length + 1 } - 1
    }
    private data class Element(val indent: Int, val prefix: String, val doc: Doc, val suffix: String)
    private data class Value(val atomic: Boolean, val doc: Doc)

    private fun con(name: String, vararg args: Value): Value = if (args.isEmpty()) Value(true, Text(name)) else
        Value(false, Group(listOf(Element(0, "", Text(name), ""), Element(2, "", Group(args.map {
            Element(0, if (it.atomic) "" else "(", it.doc, if (it.atomic) "" else ")")
        }), ""))))

    private fun bracket(open: String, separator: String, close: String, docs: List<Doc>): Doc =
        Group(docs.mapIndexed { index, doc -> Element(0, if (index == 0) open else separator, doc, "") } + Element(0, "", Text(close), ""))
    private fun <T> list(values: List<T>, convert: (T) -> Value): Value = if (values.isEmpty()) Value(true, Text("[]"))
        else Value(true, bracket("[ ", ", ", "]", values.map { convert(it).doc }))
    private fun tuple(vararg values: Value): Value = Value(true, bracket("( ", ", ", ")", values.map { it.doc }))
    private fun record(name: String, fields: List<Pair<String, Value>>): Value = Value(true, Group(listOf(
        Element(0, "", Text(name), ""), Element(2, "", bracket("{ ", ", ", "}", fields.map { (key, value) ->
            Group(listOf(Element(0, "", Text("$key ="), ""), Element(2, "", value.doc, "")))
        }), ""))))
    private fun number(value: Long): Value = Value(value >= 0, Text(value.toString()))
    private fun string(value: String): Value = Value(true, Text(HaskellString.quote(value)))
    private fun enum(value: Enum<*>): Value = Value(true, Text(value.name))
    private fun bool(value: Boolean): Value = Value(true, Text(if (value) "True" else "False"))
    private fun <T> map(values: Map<String, T>, convert: (T) -> Value): Value = con("fromList",
        list(values.toSortedMap(UnicodeTextOrder).entries.toList()) { tuple(string(it.key), convert(it.value)) })
    private fun meta(value: Map<String, MetaValue>): Value = record("Meta", listOf("unMeta" to map(value, ::metaValue)))
    private fun metaValue(value: MetaValue): Value = when (value) {
        is MetaValue.MetaMap -> con("MetaMap", map(value.values, ::metaValue))
        is MetaValue.MetaList -> con("MetaList", list(value.values, ::metaValue))
        is MetaValue.MetaBool -> con("MetaBool", bool(value.value))
        is MetaValue.MetaString -> con("MetaString", string(value.value))
        is MetaValue.MetaInlines -> con("MetaInlines", inlines(value.content))
        is MetaValue.MetaBlocks -> con("MetaBlocks", blocks(value.content))
    }
    private fun blocks(values: List<Block>) = list(values, ::block)
    private fun inlines(values: List<Inline>) = list(values, ::inline)
    private fun attr(value: Attr): Value = tuple(string(value.identifier), list(value.classes, ::string),
        list(value.keyValues) { tuple(string(it.first), string(it.second)) })
    private fun format(value: Format): Value = con("Format", string(value.value))
    private fun target(value: Target): Value = tuple(string(value.url), string(value.title))

    private fun block(value: Block): Value = when (value) {
        is Block.Plain -> con("Plain", inlines(value.content))
        is Block.Para -> con("Para", inlines(value.content))
        is Block.LineBlock -> con("LineBlock", list(value.lines, ::inlines))
        is Block.CodeBlock -> con("CodeBlock", attr(value.attr), string(value.text))
        is Block.RawBlock -> con("RawBlock", format(value.format), string(value.text))
        is Block.BlockQuote -> con("BlockQuote", blocks(value.blocks))
        is Block.OrderedList -> con("OrderedList", tuple(number(value.attributes.start), enum(value.attributes.style), enum(value.attributes.delimiter)), list(value.items, ::blocks))
        is Block.BulletList -> con("BulletList", list(value.items, ::blocks))
        is Block.DefinitionList -> con("DefinitionList", list(value.items) { tuple(inlines(it.first), list(it.second, ::blocks)) })
        is Block.Header -> con("Header", number(value.level), attr(value.attr), inlines(value.content))
        Block.HorizontalRule -> con("HorizontalRule")
        is Block.Table -> con("Table", attr(value.attr), caption(value.caption), list(value.columns, ::colSpec),
            tableHead(value.head), list(value.bodies, ::tableBody), tableFoot(value.foot))
        is Block.Figure -> con("Figure", attr(value.attr), caption(value.caption), blocks(value.blocks))
        is Block.Div -> con("Div", attr(value.attr), blocks(value.blocks))
    }
    private fun caption(value: Caption): Value = con("Caption", value.short?.let { con("Just", inlines(it)) } ?: con("Nothing"), blocks(value.long))
    private fun colSpec(value: ColSpec): Value = tuple(enum(value.alignment), when (val width = value.width) {
        ColWidth.Default -> con("ColWidthDefault")
        is ColWidth.Width -> con("ColWidth", floating(width.value))
    })
    private fun tableHead(value: TableHead): Value = con("TableHead", attr(value.attr), list(value.rows, ::row))
    private fun tableFoot(value: TableFoot): Value = con("TableFoot", attr(value.attr), list(value.rows, ::row))
    private fun tableBody(value: TableBody): Value = con("TableBody", attr(value.attr), con("RowHeadColumns", number(value.rowHeadColumns)), list(value.head, ::row), list(value.body, ::row))
    private fun row(value: Row): Value = con("Row", attr(value.attr), list(value.cells, ::cell))
    private fun cell(value: Cell): Value = con("Cell", attr(value.attr), enum(value.alignment), con("RowSpan", number(value.rowSpan)), con("ColSpan", number(value.colSpan)), blocks(value.blocks))
    private fun inline(value: Inline): Value = when (value) {
        is Inline.Str -> con("Str", string(value.text))
        is Inline.Emph -> con("Emph", inlines(value.content))
        is Inline.Underline -> con("Underline", inlines(value.content))
        is Inline.Strong -> con("Strong", inlines(value.content))
        is Inline.Strikeout -> con("Strikeout", inlines(value.content))
        is Inline.Superscript -> con("Superscript", inlines(value.content))
        is Inline.Subscript -> con("Subscript", inlines(value.content))
        is Inline.SmallCaps -> con("SmallCaps", inlines(value.content))
        is Inline.Quoted -> con("Quoted", enum(value.type), inlines(value.content))
        is Inline.Cite -> con("Cite", list(value.citations, ::citation), inlines(value.content))
        is Inline.Code -> con("Code", attr(value.attr), string(value.text))
        Inline.Space -> con("Space")
        Inline.SoftBreak -> con("SoftBreak")
        Inline.LineBreak -> con("LineBreak")
        is Inline.Math -> con("Math", enum(value.type), string(value.text))
        is Inline.RawInline -> con("RawInline", format(value.format), string(value.text))
        is Inline.Link -> con("Link", attr(value.attr), inlines(value.content), target(value.target))
        is Inline.Image -> con("Image", attr(value.attr), inlines(value.content), target(value.target))
        is Inline.Note -> con("Note", blocks(value.blocks))
        is Inline.Span -> con("Span", attr(value.attr), inlines(value.content))
    }
    private fun citation(value: Citation): Value = record("Citation", listOf(
        "citationId" to string(value.citationId), "citationPrefix" to inlines(value.citationPrefix),
        "citationSuffix" to inlines(value.citationSuffix), "citationMode" to enum(value.citationMode),
        "citationNoteNum" to number(value.citationNoteNum), "citationHash" to number(value.citationHash)))

    private fun floating(number: Double): Value {
        val text = HaskellDouble.show(number)
        return Value(!text.startsWith('-'), Text(text))
    }

    private fun render(lineLength: Long, ribbonLength: Long, glue: Int, doc: Doc): String = buildString {
        fun flat(value: Doc) {
            when (value) {
                is Text -> append(value.text)
                is Group -> value.elements.forEachIndexed { index, element ->
                    if (index > 0) append(' ')
                    append(element.prefix); flat(element.doc); append(element.suffix)
                }
            }
        }
        fun go(indent: Int, column: Int, following: Int, value: Doc) {
            if (column.toLong() + value.width + following <= lineLength && column.toLong() - indent + value.width + following <= ribbonLength) {
                flat(value)
            } else when (value) {
                is Text -> append(value.text)
                is Group -> value.elements.forEachIndexed { index, element ->
                    val nextIndent = column + element.indent
                    val left = if (index == 0) indent else nextIndent
                    val start = if (index == 0) column else nextIndent
                    val after = element.suffix.length + if (index == value.elements.lastIndex) following else 0
                    if (index > 0) { append('\n'); repeat(maxOf(0, nextIndent)) { append(' ') } }
                    append(element.prefix)
                    go(left, start + element.prefix.length, after, element.doc)
                    append(element.suffix)
                }
            }
        }
        go(0, 0, glue, doc)
    }
}

/** GHC Show String escaping, including decimal-escape termination and SO/H ambiguity. */
internal object HaskellString {
    val controlNames = listOf("NUL", "SOH", "STX", "ETX", "EOT", "ENQ", "ACK", "BEL", "BS", "HT", "LF", "VT", "FF", "CR", "SO", "SI", "DLE", "DC1", "DC2", "DC3", "DC4", "NAK", "SYN", "ETB", "CAN", "EM", "SUB", "ESC", "FS", "GS", "RS", "US")
    fun quote(value: String): String = quoteCodePoints(value.codePoints().toArray())
    fun quoteCodePoints(codePoints: IntArray): String = buildString {
        append('"')
        codePoints.forEachIndexed { index, cp ->
            val next = codePoints.getOrNull(index + 1)
            when (cp) {
                7 -> append("\\a")
                8 -> append("\\b")
                9 -> append("\\t")
                10 -> append("\\n")
                11 -> append("\\v")
                12 -> append("\\f")
                13 -> append("\\r")
                34 -> append("\\\"")
                92 -> append("\\\\")
                127 -> append("\\DEL")
                in 0..31 -> { append('\\'); append(controlNames[cp]); if (cp == 14 && next == 72) append("\\&") }
                in 32..126 -> append(cp.toChar())
                else -> { append('\\'); append(cp); if (next != null && next in 48..57) append("\\&") }
            }
        }
        append('"')
    }
}
