// Port of Text.Pandoc.Arbitrary: Copyright (c) 2006-2023 John MacFarlane.
// BSD-3-Clause; see licenses/pandoc-types-BSD-3-Clause.txt.
package io.heapy.kotdoc.ast

import kotlin.random.Random

/** Source size/depth/distribution policy. RNG is supplied; it is not GHC's QCGen. */
internal class OriginalTypesArbitrary(private val random: Random) {
    private fun int(a: Int, b: Int) = random.nextInt(a, b + 1)
    private fun <A> pick(xs: List<A>) = xs[int(0, xs.lastIndex)]
    private inline fun <reified A : Enum<A>> enum(): A = pick(enumValues<A>().toList())
    private fun <A> list(size: Int, nonempty: Boolean = false, gen: () -> A): List<A> =
        List(int(if (nonempty) 1 else 0, maxOf(if (nonempty) 1 else 0, size)), { gen() })
    private fun weighted(weights: List<Int>): Int {
        var x = int(1, weights.sum())
        for ((i, weight) in weights.withIndex()) { x -= weight; if (x <= 0) return i }
        error("frequency exhausted")
    }
    fun text(): String = buildString {
        repeat(int(0, 8)) { append(if (int(1, 10) <= 9) int(32, 127).toChar() else int(128, 9999).toChar()) }
    }
    fun attr() = Attr(pick(listOf("", "loc")), pick(listOf(emptyList(), listOf("haskell"), listOf("c", "numberLines"))),
        pick(listOf(emptyList(), listOf("start" to "22"), listOf("a" to "11", "b_2" to "a b c"))))
    private fun <A> suchThat(size: Int, gen: (Int) -> A, predicate: (A) -> Boolean): A {
        var base = size
        while (true) {
            for (attempt in base..2 * base) {
                val x = gen(attempt)
                if (predicate(x)) return x
            }
            base++
        }
    }
    private fun inlines(depth: Int, size: Int): List<Inline> = suchThat(size,
        { n -> list(n, true) { inline(depth, n) } },
        { xs -> xs.first() != Inline.Space && xs.first() != Inline.SoftBreak })
    fun inline(depth: Int = 2, size: Int = 3): Inline {
        val i = weighted(listOf(60,40,10,10,10,5) + if (depth > 1) listOf(10,10,10,10,10,10,10,10,10,10,10,10,2,2) else emptyList())
        fun ils() = inlines(depth - 1, size)
        return when (i) {
            0 -> Inline.Str(text()); 1 -> Inline.Space; 2 -> Inline.SoftBreak; 3 -> Inline.LineBreak
            4 -> Inline.Code(attr(), text())
            5 -> pick(listOf(Inline.RawInline("html", "<a id=\"eek\">"), Inline.RawInline("latex", "\\my{command}")))
            6 -> Inline.Emph(ils()); 7 -> Inline.Underline(ils()); 8 -> Inline.Strong(ils())
            9 -> Inline.Strikeout(ils()); 10 -> Inline.Superscript(ils()); 11 -> Inline.Subscript(ils())
            12 -> Inline.SmallCaps(ils()); 13 -> Inline.Span(attr(), ils())
            14 -> Inline.Quoted(enum(), ils()); 15 -> Inline.Math(enum(), text())
            16 -> Inline.Link(attr(), ils(), Target(text(), text()))
            17 -> Inline.Image(attr(), ils(), Target(text(), text()))
            18 -> Inline.Cite(list(size) { citation(size) }, inlines(1, size))
            19 -> Inline.Note(list(3, true) { block(depth - 1, 3) })
            else -> error("arbInline")
        }
    }
    fun citation(size: Int = 3) = Citation(
        list(size) { pick(('a'..'z').toList() + ('0'..'9').toList() + '_') }.joinToString(""),
        inlines(1, size), inlines(1, size), enum(), int(-size, size).toLong(), int(-size, size).toLong())
    fun caption(size: Int = 3) = Caption(if (int(1,4) == 1) null else list(size) { inline() }, list(size) { block() })
    fun cell(depth: Int = 2, size: Int = 3) = Cell(attr(), enum(), int(1,2).toLong(), int(1,2).toLong(), list(size) { block(depth, size) })
    fun row(depth: Int = 2, size: Int = 3) = Row(attr(), List(int(0,5)) { cell(depth, size) })
    fun head(depth: Int = 2, size: Int = 3) = TableHead(attr(), List(int(0,5)) { row(depth,size) })
    fun body(depth: Int = 2, size: Int = 3): TableBody {
        val hrs = int(0,2); val rs = int(0,5); val rhc = int(0,5)
        return TableBody(attr(), rhc.toLong(), List(hrs) { row(depth,size) }, List(rs) { row(depth,size) })
    }
    fun foot(depth: Int = 2, size: Int = 3) = TableFoot(attr(), List(int(0,5)) { row(depth,size) })
    fun block(depth: Int = 2, size: Int = 3): Block {
        val i = weighted(listOf(10,15,5,3,2,5,2) + if (depth > 0) listOf(5,5,5,5,5,2,2) else emptyList())
        fun bs() = list(size, true) { block(depth - 1, size) }
        return when (i) {
            0 -> Block.Plain(inlines(depth - 1,size)); 1 -> Block.Para(inlines(depth - 1,size))
            2 -> Block.CodeBlock(attr(),text())
            3 -> Block.LineBlock(listOf(inlines(Math.floorMod(depth - 1,3),size)) + List(maxOf(0, Math.floorDiv(depth - 1,3))) { inlines(3,size) })
            4 -> pick(listOf(Block.RawBlock("html", "<div>\n*&amp;*\n</div>"), Block.RawBlock("latex", "\\begin[opt]{env}\nhi\n{\\end{env}")))
            5 -> Block.Header(int(1,6).toLong(), Attr(), inlines(depth - 1,size))
            6 -> Block.HorizontalRule; 7 -> Block.BlockQuote(bs())
            8 -> Block.OrderedList(ListAttributes(suchThat(size, { n -> int(-n,n).toLong() }, { it > 0 }), enum(), enum()), list(size,true) { bs() })
            9 -> Block.BulletList(list(size,true) { bs() })
            10 -> Block.DefinitionList(list(size,true) { inlines(depth - 1,size) to list(size,true) { bs() } })
            11 -> Block.Div(attr(), bs())
            12 -> {
                val cs = int(1,6); val bodies = int(0,2)
                Block.Table(attr(), caption(size), List(cs) { ColSpec(enum(), pick(listOf(ColWidth.Default, ColWidth.Width(1.0/3), ColWidth.Width(0.25)))) },
                    head(depth - 1,size), List(bodies) { body(depth - 1,size) }, foot(depth - 1,size))
            }
            13 -> Block.Figure(attr(), caption(size),bs())
            else -> error("arbBlock")
        }
    }
    fun pandoc(): Pandoc {
        val title = list(8) { inline() }
        val authors = list(8) { list(8) { inline() } }.filter { it.isNotEmpty() }
        val date = list(8) { inline() }
        return Pandoc(mapOf("title" to MetaValue.MetaInlines(title), "author" to MetaValue.MetaList(authors.map { MetaValue.MetaInlines(it) }),
            "date" to MetaValue.MetaInlines(date)), list(8) { block() })
    }
}
