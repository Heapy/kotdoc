// Port of Text.Pandoc.Arbitrary, Copyright (c) 2006-2023 John MacFarlane,
// and QuickCheck 2.16.0.0 Arbitrary, Copyright (c) 2000-2019 Koen Claessen,
// 2006-2008 Björn Bringert, 2009-2019 Nick Smallbone. BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.ast

import io.heapy.kotdoc.parsing.ParserCharacters
import io.heapy.kotdoc.parsing.RegexCharacters

/** Ordered candidates, duplicates and source no-op shrinkers are preserved. */
@Suppress("UNUSED_PARAMETER")
internal object OriginalTypesShrink {
    // These source instances deliberately inherit Arbitrary's empty shrink.
    fun pandoc(x: Pandoc): Sequence<Pandoc> = emptySequence()
    fun meta(x: Map<String, MetaValue>): Sequence<Map<String, MetaValue>> = emptySequence()
    fun citation(x: Citation): Sequence<Citation> = emptySequence()
    fun mode(x: CitationMode): Sequence<CitationMode> = emptySequence()
    fun math(x: MathType): Sequence<MathType> = emptySequence()
    fun quote(x: QuoteType): Sequence<QuoteType> = emptySequence()
    fun alignment(x: Alignment): Sequence<Alignment> = emptySequence()
    fun style(x: ListNumberStyle): Sequence<ListNumberStyle> = emptySequence()
    fun delim(x: ListNumberDelim): Sequence<ListNumberDelim> = emptySequence()

    fun <A> list(xs: List<A>, shrink: (A) -> Sequence<A>): Sequence<List<A>> = sequence {
        suspend fun SequenceScope<List<A>>.removes(k: Int, offset: Int) {
            val n = xs.size - offset
            if (k > n) return
            if (k == n) { yield(xs.take(offset)); return }
            yield(xs.take(offset) + xs.drop(offset + k))
            removes(k, offset + k)
        }
        var k = xs.size
        while (k > 0) { removes(k, 0); k /= 2 }
        for ((i,x) in xs.withIndex()) for (y in shrink(x)) yield(xs.take(i) + y + xs.drop(i+1))
    }
    private fun char(c: Int): Sequence<Int> {
        fun stamp(x: Int) = listOf(!RegexCharacters.isLower(x), !RegexCharacters.isUpper(x), !ParserCharacters.isDigit(x),
            x != 32, !ParserCharacters.isSpace(x)).map { if (it) 1 else 0 } + x
        fun less(a: Int, b: Int): Boolean {
            val x = stamp(a); val y = stamp(b)
            for (i in x.indices) if (x[i] != y[i]) return x[i] < y[i]
            return false
        }
        return (listOf(97,98,99) + (if (RegexCharacters.isUpper(c)) listOf(RegexCharacters.toLower(c)) else emptyList()) +
            listOf(65,66,67,49,50,51,32,10)).asSequence().distinct().filter { less(it,c) }
    }
    fun text(s: String): Sequence<String> = list(s.codePoints().toArray().toList(), ::char).map { points ->
        buildString { for (p in points) appendCodePoint(p) }
    }
    private fun pair(x: Pair<String,String>) = text(x.first).map { it to x.second } + text(x.second).map { x.first to it }
    fun attr(x: Attr): Sequence<Attr> = text(x.identifier).flatMap { a -> list(x.classes, ::text).flatMap { b ->
        list(x.keyValues, ::pair).map { c -> Attr(a,b,c) }
    } }
    fun inlines(xs: List<Inline>): Sequence<List<Inline>> = list(xs, ::inline) + flatten(xs, ::flattenInline)
    fun blocks(xs: List<Block>): Sequence<List<Block>> = list(xs, ::block) + flatten(xs, ::flattenBlock)
    private fun <A> flatten(xs: List<A>, f: (A) -> List<A>): Sequence<List<A>> = sequence {
        for ((i,x) in xs.withIndex()) { val ys = f(x); if (ys.isNotEmpty()) yield(xs.take(i) + ys + xs.drop(i+1)) }
    }
    private fun flattenInline(x: Inline): List<Inline> = when (x) {
        is Inline.Emph -> x.content; is Inline.Underline -> x.content; is Inline.Strong -> x.content
        is Inline.Strikeout -> x.content; is Inline.Superscript -> x.content; is Inline.Subscript -> x.content
        is Inline.SmallCaps -> x.content; is Inline.Quoted -> x.content; is Inline.Cite -> x.content
        is Inline.Link -> x.content; is Inline.Image -> x.content; is Inline.Span -> x.content
        else -> emptyList()
    }
    private fun flattenCaption(x: Caption) = (x.short?.let { listOf(Block.Para(it)) } ?: emptyList()) + x.long
    private fun flattenRows(xs: List<Row>) = xs.flatMap { r -> r.cells.flatMap { it.blocks } }
    private fun flattenBlock(x: Block): List<Block> = when (x) {
        is Block.LineBlock -> x.lines.map { Block.Para(it) }; is Block.BlockQuote -> x.blocks
        is Block.OrderedList -> x.items.flatten(); is Block.BulletList -> x.items.flatten()
        is Block.DefinitionList -> x.items.flatMap { (ils,bs) -> listOf(Block.Para(ils)) + bs.flatten() }
        is Block.Header -> listOf(Block.Para(x.content)); is Block.Div -> x.blocks
        is Block.Figure -> flattenCaption(x.caption) + x.blocks
        is Block.Table -> flattenCaption(x.caption) + flattenRows(x.head.rows) + x.bodies.flatMap { flattenRows(it.head) + flattenRows(it.body) } + flattenRows(x.foot.rows)
        else -> emptyList()
    }
    fun inline(x: Inline): Sequence<Inline> = when (x) {
        is Inline.Str -> text(x.text).map { x.copy(text=it) }
        is Inline.Emph -> inlines(x.content).map { x.copy(content=it) }
        is Inline.Underline -> inlines(x.content).map { x.copy(content=it) }
        is Inline.Strong -> inlines(x.content).map { x.copy(content=it) }
        is Inline.Strikeout -> inlines(x.content).map { x.copy(content=it) }
        is Inline.Superscript -> inlines(x.content).map { x.copy(content=it) }
        is Inline.Subscript -> inlines(x.content).map { x.copy(content=it) }
        is Inline.SmallCaps -> inlines(x.content).map { x.copy(content=it) }
        is Inline.Quoted -> inlines(x.content).map { x.copy(content=it) }
        is Inline.Cite -> inlines(x.content).map { x.copy(content=it) } + list(x.citations, ::citation).map { x.copy(citations=it) }
        is Inline.Code -> text(x.text).map { x.copy(text=it) } + attr(x.attr).map { x.copy(attr=it) }
        is Inline.Math -> text(x.text).map { x.copy(text=it) }
        is Inline.RawInline -> text(x.text).map { x.copy(text=it) }
        is Inline.Link -> inlines(x.content).map { x.copy(content=it) } + pair(x.target.url to x.target.title).map { x.copy(target=Target(it.first,it.second)) } + attr(x.attr).map { x.copy(attr=it) }
        is Inline.Image -> inlines(x.content).map { x.copy(content=it) } + pair(x.target.url to x.target.title).map { x.copy(target=Target(it.first,it.second)) } + attr(x.attr).map { x.copy(attr=it) }
        is Inline.Note -> blocks(x.blocks).map { x.copy(blocks=it) }
        // Span's source uses shrink [Inline], not the builder Inlines shrink.
        is Inline.Span -> list(x.content, ::inline).map { x.copy(content=it) } + attr(x.attr).map { x.copy(attr=it) }
        Inline.Space, Inline.SoftBreak, Inline.LineBreak -> emptySequence()
    }
    fun block(x: Block): Sequence<Block> = when (x) {
        is Block.Plain -> inlines(x.content).map { x.copy(content=it) }
        is Block.Para -> inlines(x.content).map { x.copy(content=it) }
        is Block.LineBlock -> list(x.lines, ::inlines).map { x.copy(lines=it) }
        is Block.CodeBlock -> text(x.text).map { x.copy(text=it) } + attr(x.attr).map { x.copy(attr=it) }
        is Block.RawBlock -> text(x.text).map { x.copy(text=it) }
        is Block.BlockQuote -> blocks(x.blocks).map { x.copy(blocks=it) }
        is Block.OrderedList -> list(x.items, ::blocks).map { x.copy(items=it) }
        is Block.BulletList -> list(x.items, ::blocks).map { x.copy(items=it) }
        is Block.DefinitionList -> {
            fun defs(xs: List<Pair<List<Inline>,List<List<Block>>>>): Sequence<List<Pair<List<Inline>,List<List<Block>>>>> {
                if (xs.isEmpty()) return emptySequence()
                val (ils,bs) = xs.first(); val rest = xs.drop(1)
                return sequenceOf(rest) + (inlines(ils).map { it to bs } + list(bs, ::blocks).map { ils to it }).map { listOf(it) + rest } + defs(rest).map { listOf(xs.first()) + it }
            }
            defs(x.items).map { x.copy(items=it) }
        }
        is Block.Header -> inlines(x.content).map { x.copy(content=it) } + attr(x.attr).map { x.copy(attr=it) }
        is Block.Table -> attr(x.attr).map { x.copy(attr=it) } + head(x.head).map { x.copy(head=it) } + list(x.bodies, ::body).map { x.copy(bodies=it) } + foot(x.foot).map { x.copy(foot=it) } + caption(x.caption).map { x.copy(caption=it) }
        is Block.Figure -> blocks(x.blocks).map { x.copy(blocks=it) } + caption(x.caption).map { x.copy(caption=it) } + attr(x.attr).map { x.copy(attr=it) }
        is Block.Div -> blocks(x.blocks).map { x.copy(blocks=it) } + attr(x.attr).map { x.copy(attr=it) }
        Block.HorizontalRule -> emptySequence()
    }
    fun row(x: Row) = attr(x.attr).map { x.copy(attr=it) } + list(x.cells, ::cell).map { x.copy(cells=it) }
    fun head(x: TableHead) = attr(x.attr).map { x.copy(attr=it) } + list(x.rows, ::row).map { x.copy(rows=it) }
    fun body(x: TableBody) = attr(x.attr).map { x.copy(attr=it) } + list(x.head, ::row).map { x.copy(head=it) } + list(x.body, ::row).map { x.copy(body=it) }
    fun foot(x: TableFoot) = attr(x.attr).map { x.copy(attr=it) } + list(x.rows, ::row).map { x.copy(rows=it) }
    fun cell(x: Cell) = blocks(x.blocks).map { x.copy(blocks=it) } + attr(x.attr).map { x.copy(attr=it) } + alignment(x.alignment).map { x.copy(alignment=it) }
    fun caption(x: Caption) = (x.short?.let { sequenceOf<Caption>(x.copy(short=null)) + list(it, ::inline).map { ys -> x.copy(short=ys) } } ?: emptySequence()) + blocks(x.long).map { x.copy(long=it) }
}
