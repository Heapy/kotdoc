// Text.Pandoc.Readers.HTML.Table, © 2006-2024 John MacFarlane,
// 2020-2024 Albert Krewinkel. GPL-2.0-or-later; original in upstream/pandoc.
package io.heapy.kotdoc.html

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.formats.HaskellNumber
import io.heapy.kotdoc.parsing.TokenParsec

object HtmlTableParsing {
    private sealed interface Width {
        data class Relative(val value: Long) : Width
        data class Fixed(val value: ColWidth) : Width
    }
    private data class TypedCell(val header: Boolean, val cell: Cell)
    private data class TypedRow(val headers: Int, val row: Row)
    private fun <U> blanks() = HtmlTagParsing.pBlank<U>().skipMany()
    private fun <U> open(name: String) = HtmlTagParsing.pSatisfy<U> { HtmlTagParsing.matchTagOpen(name, emptyList(), it) }.map { (it as HtmlTag.Open).attributes }
    private fun <U> close(name: String) = HtmlTagParsing.pSatisfy<U> { HtmlTagParsing.matchTagClose(name, it) }.map { }
    private fun attr(attrs: List<Pair<String, String>>, name: String) = attrs.firstOrNull { it.first == name }?.second
    private fun <U, A> maybe(parser: HtmlTagParser<U, A>): HtmlTagParser<U, A?> = parser.map<A?> { it }.option(null)
    private fun <U> columns(): HtmlTagParser<U, Width> = open<U>("col").before(blanks()).before(close<U>("col").optional()).before(blanks()).map { raw ->
        val attrs = HtmlTagParsing.toStringAttr(raw)
        val width = attr(attrs, "width")
        if (width == null) {
            val style = attr(attrs, "style")
            if (style != null && style.startsWith("width:") && '%' in style.substring(6)) {
                val numeric = style.substring(6).filterNot { it in " \t\r\n%'\";" }
                Width.Fixed(HaskellNumber.readDouble(numeric)?.let { ColWidth.Width(it / 100.0) } ?: ColWidth.Default)
            } else Width.Fixed(ColWidth.Default)
        } else when {
            width.endsWith('*') -> Width.Relative(HaskellNumber.readInteger(width.dropLast(1))?.toLong() ?: 1)
            width.endsWith('%') -> Width.Fixed(HaskellNumber.readDouble(width.dropLast(1))?.let { ColWidth.Width(it / 100.0) } ?: ColWidth.Default)
            else -> Width.Fixed(ColWidth.Default)
        }
    }.attempt()
    private fun <U> columnGroup(): HtmlTagParser<U, List<Width>> = open<U>("colgroup").before(blanks()).then(
        TokenParsec.manyTill(columns<U>(), HtmlTagParsing.pCloses<U>("colgroup") or TokenParsec.eof(HtmlTagMatching::show))
    ).before(blanks()).attempt()
    private fun resolveRelativeLengths(widths: List<Width>): List<ColWidth> {
        var absolute = 0.0; var relative = 0L
        for (w in widths) when (w) {
            is Width.Relative -> relative += w.value
            is Width.Fixed -> absolute += (w.value as? ColWidth.Width)?.value ?: 0.0
        }
        val unit = (1.0 - absolute) / relative.toDouble()
        return widths.map { when (it) { is Width.Fixed -> it.value; is Width.Relative -> ColWidth.Width(it.value.toDouble() * unit) } }
    }
    private fun <U> cell(block: HtmlTagParser<U, Blocks>, header: Boolean): HtmlTagParser<U, TypedCell> {
        val name = if (header) "th" else "td"
        return blanks<U>().then(open<U>(name).lookAhead()).flatMap { attrs ->
            val css = attr(attrs, "style")?.let(PandocCss::cssAttributes) ?: emptyList()
            val align = when (attr(attrs, "align") ?: attr(css, "text-align")) {
                "left" -> Alignment.AlignLeft; "right" -> Alignment.AlignRight; "center" -> Alignment.AlignCenter; else -> Alignment.AlignDefault
            }
            val rowspan = attr(attrs, "rowspan")?.let(HaskellNumber::readInteger)?.toLong() ?: 1
            val colspan = attr(attrs, "colspan")?.let(HaskellNumber::readInteger)?.toLong() ?: 1
            HtmlTagParsing.pInTags(name, block, Blocks(), Blocks::plus).before(blanks()).map { contents ->
                val remaining = attrs.mapNotNull { pair -> when (pair.first) {
                    "style" -> css.filter { it.first != "text-align" }.takeIf { it.isNotEmpty() }?.let { cs -> "style" to cs.joinToString("; ") { (k, v) -> "$k: $v" } }
                    "align", "colspan", "rowspan", "text-align" -> null
                    else -> pair
                } }
                TypedCell(header, Builder.cellWith(HtmlTagParsing.toAttr(remaining), align, rowspan, colspan, contents))
            }
        }.attempt()
    }
    private fun <U> row(block: HtmlTagParser<U, Blocks>): HtmlTagParser<U, TypedRow> = blanks<U>().then(open<U>("tr")).before(blanks()).flatMap { attrs ->
        (cell(block, false) or cell(block, true)).many().before(close<U>("tr").optional()).map { cells ->
            TypedRow(cells.takeWhile { it.header }.size, Row(HtmlTagParsing.toAttr(attrs), cells.map { it.cell }))
        }
    }.attempt()
    private fun <U> headerRow(block: HtmlTagParser<U, Blocks>): HtmlTagParser<U, Row> = blanks<U>().then(open<U>("tr")).flatMap { attrs ->
        cell(block, true).many().before(blanks()).before(HtmlTagParsing.pSatisfy<U> { HtmlTagParsing.matchTagOpen("td", emptyList(), it) }.notFollowedBy(HtmlTagMatching::show))
            .before(close<U>("tr").optional()).map { cells -> Row(HtmlTagParsing.toAttr(attrs), cells.map { it.cell }) }
    }.attempt()
    private fun <U> head(block: HtmlTagParser<U, Blocks>): HtmlTagParser<U, TableHead> = blanks<U>().then(
        maybe(HtmlTagParsing.pInTagWithAttribs(TagOmission.ClosingTagOptional, "thead", row(block).many()))
    ).flatMap { section ->
        if (section != null) TokenParsec.pure(TableHead(HtmlTagParsing.toAttr(section.first), section.second.map { it.row }))
        else maybe(headerRow(block)).map { r -> TableHead(Attr(), if (r != null && r.cells.isNotEmpty()) listOf(r) else emptyList()) }
    }.attempt()
    private fun <U> foot(block: HtmlTagParser<U, Blocks>): HtmlTagParser<U, TableFoot> = blanks<U>().then(open<U>("tfoot")).before(blanks()).flatMap { attrs ->
        row(block).before(blanks()).many().before(close<U>("tfoot").optional()).map { rows -> TableFoot(HtmlTagParsing.toAttr(attrs), rows.map { it.row }) }
    }.attempt()
    private fun <U> body(block: HtmlTagParser<U, Blocks>): HtmlTagParser<U, TableBody> = blanks<U>().then(maybe(open<U>("tbody").before(blanks()))).flatMap { attrs ->
        headerRow(block).many().flatMap { headers -> row(block).before(blanks()).many().before(close<U>("tbody").optional()).flatMap { rows ->
            if (attrs == null && headers.isEmpty() && rows.isEmpty()) TokenParsec.zero()
            else {
                val counts = LongArray(rows.size)
                for ((index, typed) in rows.withIndex()) for (cell in typed.row.cells.take(typed.headers)) {
                    val end = index.toLong() + cell.rowSpan
                    for (i in counts.indices) if (i.toLong() >= index.toLong() && i.toLong() < end) counts[i] += cell.colSpan
                }
                val rowHeadColumns = counts.firstOrNull()?.takeIf { n -> counts.all { it == n } } ?: 0
                TokenParsec.pure(TableBody(HtmlTagParsing.toAttr(attrs ?: emptyList()), rowHeadColumns, headers, rows.map { it.row }))
            }
        } }
    }.attempt()

    /** Complete original pTable; cell/caption interpretation is supplied by the reader. */
    fun <U> pTable(block: HtmlTagParser<U, Blocks>): HtmlTagParser<U, Blocks> = open<U>("table").before(blanks()).flatMap { attrs ->
        HtmlTagParsing.pInTags("caption", block, Blocks(), Blocks::plus).before(blanks()).option(Blocks()).flatMap { caption ->
            (columnGroup<U>().many1().map { it.flatten() } or columns<U>().many()).map(::resolveRelativeLengths).before(blanks()).flatMap { widths ->
                head(block).before(blanks()).flatMap { h -> maybe(foot(block)).before(blanks()).flatMap { top ->
                    body(block).many().before(blanks()).flatMap { bodies -> maybe(foot(block)).before(blanks()).before(close<U>("table")).flatMap { bottom ->
                        normalize(attrs, caption, widths, h, bodies, top ?: bottom ?: TableFoot(Attr(), emptyList()))
                    } }
                } }
            }
        }
    }.attempt()
    private fun <U> normalize(attrs: List<Pair<String, String>>, caption: Blocks, widths: List<ColWidth>, head: TableHead, bodies: List<TableBody>, foot: TableFoot): HtmlTagParser<U, Blocks> {
        val rows = head.rows + bodies.flatMap { it.head + it.body } + foot.rows
        if (rows.isEmpty()) return TokenParsec.fail("empty table")
        val n = rows.maxOf { row -> row.cells.fold(0L) { sum, cell -> sum + cell.colSpan } }.coerceAtLeast(0)
        val count = Math.toIntExact(n)
        val simple = TableSimplicity.onlySimpleTableCells(rows.map { row -> row.cells.map { it.blocks } })
        val normalizedWidths = if (widths.isEmpty()) List(count) { if (simple) ColWidth.Default else ColWidth.Width(1.0 / n.toDouble()) }
            else widths + List(maxOf(0, count - widths.size)) { ColWidth.Default }
        val firstBodyRow = bodies.asSequence().flatMap { (it.head + it.body).asSequence() }.firstOrNull()
        val alignments = mutableListOf<Alignment>()
        if (firstBodyRow != null) for (cell in firstBodyRow.cells) {
            val take = minOf((count - alignments.size).toLong(), cell.colSpan.coerceAtLeast(0)).toInt()
            repeat(take) { alignments.add(cell.alignment) }
            if (alignments.size == count) break
        }
        while (alignments.size < count) alignments.add(Alignment.AlignDefault)
        val specs = alignments.zip(normalizedWidths) { a, w -> ColSpec(a, w) }
        return TokenParsec.pure(Builder.tableWith(HtmlTagParsing.toAttr(attrs), Builder.simpleCaption(caption), specs, head, bodies, foot))
    }
}
