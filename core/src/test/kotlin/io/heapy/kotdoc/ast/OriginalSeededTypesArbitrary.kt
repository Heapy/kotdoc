// Text.Pandoc.Arbitrary: Copyright (c) 2006-2023 John MacFarlane.
// BSD-3-Clause; see licenses/pandoc-types-BSD-3-Clause.txt.
package io.heapy.kotdoc.ast

import io.heapy.kotdoc.ast.OriginalQuickCheck.Gen

/** Literal generator expressions retain QCGen's applicative/monadic split tree. */
internal object OriginalSeededTypesArbitrary {
    private fun <A> pure(x:A) = OriginalQuickCheck.pure(x)
    private fun <A> defer(f:()->Gen<A>) = Gen<A> { r,n -> f().run(r,n) }
    private fun choose(a:Int,b:Int) = OriginalQuickCheck.choose(a.toLong(),b.toLong())
    private fun <A> elements(xs:List<A>) = OriginalQuickCheck.elements(xs)
    private fun <A> list(g:Gen<A>, nonempty:Boolean=false) = OriginalQuickCheck.list(g,nonempty)
    private fun <A> vector(n:Int,g:Gen<A>) = OriginalQuickCheck.vector(n,g)
    private fun <A> frequency(xs:List<Pair<Int,Gen<A>>>) = OriginalQuickCheck.frequency(xs)
    @Suppress("UNCHECKED_CAST") private fun <A> List<Any?>.at(i:Int):A = this[i] as A
    @Suppress("UNCHECKED_CAST")
    private fun <A> record(vararg fields:Gen<*>, f:(List<Any?>)->A):Gen<A> {
        var g = (fields.first() as Gen<Any?>).map { listOf(it) }
        for (next in fields.drop(1)) g = OriginalQuickCheck.zip(g,next as Gen<Any?>) { xs,x -> xs+x }
        return g.map(f)
    }
    private fun <A> enumeration(xs:List<A>):Gen<A> = choose(0,xs.lastIndex).bind { pure(xs[it.toInt()]) }
    private fun <A> maybe(g:Gen<A>):Gen<A?> = frequency(listOf(1 to pure<A?>(null), 3 to g.bind { pure<A?>(it) }))
    private fun chars(a:Int,b:Int) = OriginalQuickCheck.chooseInt(0,b-a).map { (a+it).toChar() }
    fun text():Gen<String> = list(frequency(listOf(9 to chars(32,127),1 to chars(128,9999)))).resize(8).map { it.joinToString("") }
    fun attr():Gen<Attr> = elements(listOf("","loc")).bind { id ->
        elements(listOf(emptyList(),listOf("haskell"),listOf("c","numberLines"))).bind { classes ->
            elements(listOf(emptyList(),listOf("start" to "22"),listOf("a" to "11","b_2" to "a b c"))).bind { pairs -> pure(Attr(id,classes,pairs)) }
        }
    }
    fun alignment() = enumeration(Alignment.entries)
    fun quote() = enumeration(QuoteType.entries)
    fun math() = enumeration(MathType.entries)
    fun mode() = enumeration(CitationMode.entries)
    fun style() = enumeration(ListNumberStyle.entries)
    fun delim() = enumeration(ListNumberDelim.entries)
    fun citation():Gen<Citation> = record(
        list(elements(('a'..'z').toList()+('0'..'9').toList()+'_')).map { it.joinToString("") },
        inlines(1),inlines(1),mode(),OriginalQuickCheck.integral(),OriginalQuickCheck.integral()
    ) { Citation(it.at(0),it.at(1),it.at(2),it.at(3),it.at(4),it.at(5)) }
    fun inline():Gen<Inline> = inline(2).resize(3)
    fun block():Gen<Block> = block(2).resize(3)
    fun inlines(depth:Int):Gen<List<Inline>> = OriginalQuickCheck.suchThat(list(inline(depth),true)) {
        it.first()!=Inline.Space && it.first()!=Inline.SoftBreak
    }
    private fun nest(depth:Int,f:(List<Inline>)->Inline):Gen<Inline> = inlines(depth).map(f)
    fun inline(depth:Int):Gen<Inline> = defer {
        val base = listOf(
            60 to text().map<Inline> { Inline.Str(it) },40 to pure<Inline>(Inline.Space),
            10 to pure<Inline>(Inline.SoftBreak),10 to pure<Inline>(Inline.LineBreak),
            10 to record<Inline>(attr(),text()) { Inline.Code(it.at(0),it.at(1)) },
            5 to elements<Inline>(listOf(Inline.RawInline("html","<a id=\"eek\">"),Inline.RawInline("latex","\\my{command}"))))
        val nested = if(depth<=1) emptyList() else listOf(
            10 to nest(depth-1,Inline::Emph),10 to nest(depth-1,Inline::Underline),10 to nest(depth-1,Inline::Strong),
            10 to nest(depth-1,Inline::Strikeout),10 to nest(depth-1,Inline::Superscript),10 to nest(depth-1,Inline::Subscript),
            10 to nest(depth-1,Inline::SmallCaps),10 to record<Inline>(attr(),inlines(depth-1)) { Inline.Span(it.at(0),it.at(1)) },
            10 to record<Inline>(quote(),inlines(depth-1)) { Inline.Quoted(it.at(0),it.at(1)) },
            10 to record<Inline>(math(),text()) { Inline.Math(it.at(0),it.at(1)) },
            10 to record<Inline>(attr(),inlines(depth-1),record(text(),text()) { Target(it.at(0),it.at(1)) }) { Inline.Link(it.at(0),it.at(1),it.at(2)) },
            10 to record<Inline>(attr(),inlines(depth-1),record(text(),text()) { Target(it.at(0),it.at(1)) }) { Inline.Image(it.at(0),it.at(1),it.at(2)) },
            2 to record<Inline>(list(citation()),inlines(1)) { Inline.Cite(it.at(0),it.at(1)) },
            2 to list(block(depth-1),true).resize(3).map<Inline> { Inline.Note(it) })
        frequency(base+nested)
    }
    fun caption():Gen<Caption> = record(maybe(list(inline())),list(block())) { Caption(it.at(0),it.at(1)) }
    fun cell(depth:Int):Gen<Cell> = record(attr(),alignment(),choose(1,2),choose(1,2),list(block(depth))) {
        Cell(it.at(0),it.at(1),it.at(2),it.at(3),it.at(4))
    }
    fun row(depth:Int):Gen<Row> = choose(0,5).bind { cs -> record(attr(),vector(cs.toInt(),cell(depth))) { Row(it.at(0),it.at(1)) } }
    fun head(depth:Int):Gen<TableHead> = choose(0,5).bind { rs -> record(attr(),vector(rs.toInt(),row(depth))) { TableHead(it.at(0),it.at(1)) } }
    fun body(depth:Int):Gen<TableBody> = choose(0,2).bind { hrs -> choose(0,5).bind { rs -> choose(0,5).bind { rhc ->
        record(attr(),pure(rhc),vector(hrs.toInt(),row(depth)),vector(rs.toInt(),row(depth))) { TableBody(it.at(0),it.at(1),it.at(2),it.at(3)) }
    } } }
    fun foot(depth:Int):Gen<TableFoot> = choose(0,5).bind { rs -> record(attr(),vector(rs.toInt(),row(depth))) { TableFoot(it.at(0),it.at(1)) } }
    fun block(depth:Int):Gen<Block> = defer {
        val base = listOf(
            10 to inlines(depth-1).map<Block> { Block.Plain(it) },15 to inlines(depth-1).map<Block> { Block.Para(it) },
            5 to record<Block>(attr(),text()) { Block.CodeBlock(it.at(0),it.at(1)) },
            3 to record<Block>(inlines(Math.floorMod(depth-1,3)),vector(maxOf(0,Math.floorDiv(depth-1,3)),inlines(3))) {
                Block.LineBlock(listOf(it.at<List<Inline>>(0))+it.at<List<List<Inline>>>(1)) },
            2 to elements<Block>(listOf(Block.RawBlock("html","<div>\n*&amp;*\n</div>"),Block.RawBlock("latex","\\begin[opt]{env}\nhi\n{\\end{env}"))),
            5 to record<Block>(choose(1,6),pure(Attr()),inlines(depth-1)) { Block.Header(it.at(0),it.at(1),it.at(2)) },
            2 to pure<Block>(Block.HorizontalRule))
        val nested = if(depth<=0) emptyList() else listOf(
            5 to list(block(depth-1),true).map<Block> { Block.BlockQuote(it) },
            5 to record<Block>(record(OriginalQuickCheck.suchThat(OriginalQuickCheck.integral()) { it>0 },style(),delim()) { ListAttributes(it.at(0),it.at(1),it.at(2)) },
                list(list(block(depth-1),true),true)) { Block.OrderedList(it.at(0),it.at(1)) },
            5 to list(list(block(depth-1),true),true).map<Block> { Block.BulletList(it) },
            5 to list(record(inlines(depth-1),list(list(block(depth-1),true),true)) { it.at<List<Inline>>(0) to it.at<List<List<Block>>>(1) },true).map<Block> { Block.DefinitionList(it) },
            5 to record<Block>(attr(),list(block(depth-1),true)) { Block.Div(it.at(0),it.at(1)) },
            2 to choose(1,6).bind { cs -> choose(0,2).bind { bs ->
                record<Block>(attr(),caption(),vector(cs.toInt(),record(alignment(),elements<ColWidth>(listOf(ColWidth.Default,ColWidth.Width(1.0/3),ColWidth.Width(0.25)))) { ColSpec(it.at(0),it.at(1)) }),
                    head(depth-1),vector(bs.toInt(),body(depth-1)),foot(depth-1)) { Block.Table(it.at(0),it.at(1),it.at(2),it.at(3),it.at(4),it.at(5)) }
            } },
            2 to record<Block>(attr(),caption(),list(block(depth-1),true)) { Block.Figure(it.at(0),it.at(1),it.at(2)) })
        frequency(base+nested)
    }
    fun meta():Gen<Map<String,MetaValue>> = list(inline()).bind { title ->
        list(list(inline())).map { it.filter(List<Inline>::isNotEmpty) }.bind { authors ->
            list(inline()).bind { date -> pure(mapOf("title" to MetaValue.MetaInlines(title),"author" to MetaValue.MetaList(authors.map { MetaValue.MetaInlines(it) }),"date" to MetaValue.MetaInlines(date))) }
        }
    }
    fun pandoc():Gen<Pandoc> = record(meta(),list(block())) { Pandoc(it.at(0),it.at(1)) }.resize(8)
}
