// Text.Pandoc.Readers.LaTeX.Table, Pandoc 3.12.1.
// Copyright (C) John MacFarlane; GPL-2.0-or-later, licenses/pandoc-GPL-2.0.md.
package io.heapy.kotdoc.latex

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.formats.HaskellNumber
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.tex.*

class LaTeXTables(val kernel: LaTeXKernel = LaTeXKernel()) {
    data class Column(val alignment: Alignment, val width: ColWidth, val prefix: TokenList, val suffix: TokenList)
    private fun <A> pure(x: A): LP<A> = ReaderTokenParsec.pure(x)
    private fun <A> defer(p: () -> LP<A>): LP<A> = ReaderTokenParsec.defer(p)
    private fun <A, B> till(p: LP<A>, end: LP<B>): LP<List<A>> = ReaderTokenParsec { env -> TokenParsec.manyTill(p.runReader(env), end.runReader(env)) }
    private fun <A> sequence(ps: List<LP<A>>): LP<List<A>> {
        fun go(i: Int): LP<DeferredChain<A>> = if (i == ps.size) pure(DeferredChain.Nil) else ps[i].flatMap { x ->
            defer { go(i + 1) }.map { DeferredChain.Cons(Deferred.now(x), Deferred.now(it)) }
        }
        return defer { go(0) }.map { list ->
            val result = ArrayList<A>(); var node = list
            while (node is DeferredChain.Cons) { result.add(node.head.value()); node = node.tail.value() }
            result
        }
    }
    private fun <A, B> sepEndBy(p: LP<A>, sep: LP<B>): LP<List<A>> {
        fun go(): LP<DeferredChain<A>> = (p.flatMap { x ->
            (sep.then(defer { go() }) or pure(DeferredChain.Nil)).map<DeferredChain<A>> { DeferredChain.Cons(Deferred.now(x), Deferred.now(it)) }
        } or pure(DeferredChain.Nil))
        return defer { go() }.map { list ->
            val result = ArrayList<A>(); var node = list
            while (node is DeferredChain.Cons) { result.add(node.head.value()); node = node.tail.value() }
            result
        }
    }
    private fun concat(parts: List<TokenList>): TokenList {
        var result = tokenList(emptyList())
        for (i in parts.indices.reversed()) result = (TokStream(false, parts[i]) + TokStream(false, result)).tokens
        return result
    }
    private fun mapTokens(ts: TokenList, f: (Tok) -> Tok): TokenList = Deferred.later {
        when (val node = ts.value()) {
            DeferredChain.Nil -> DeferredChain.Nil
            is DeferredChain.Cons -> DeferredChain.Cons(node.head.mapValue(f), mapTokens(node.tail, f))
        }
    }
    fun environments(block: LP<Blocks>, inline: LP<Inlines>): Map<String, LP<Blocks>> {
        val result = sortedMapOf<String, LP<Blocks>>(UnicodeTextOrder)
        result["longtable"] = kernel.env("longtable", LaTeXParsing.resetCaption().then(simpTable(block, inline, "longtable", false)).flatMap(::addTableCaption))
        result["table"] = kernel.env("table", kernel.skipopts().then(LaTeXParsing.resetCaption()).then(block.many().map { it.fold(Blocks(), Blocks::plus) }).flatMap(::addTableCaption))
        for ((name, width) in listOf("tabular*" to true, "tabularx" to true, "tabular" to false, "supertabular" to false, "supertabular*" to false))
            result[name] = kernel.env(name, simpTable(block, inline, name, width))
        return java.util.Collections.unmodifiableMap(result)
    }
    fun hline(): LP<Unit> = kernel.spaces().then(
        (kernel.controlSeq("hline") or kernel.controlSeq("cline")).map { false } or
            listOf("cmidrule", "toprule", "bottomrule", "midrule", "endhead", "endfirsthead")
                .map { kernel.controlSeq(it).map { true } }.reduce { a, b -> a or b }
    ).flatMap { paren -> kernel.rawopt().optional().then(
        if (paren) kernel.parenWrapped(kernel.singleChar().map { Unit }, Unit) { _, _ -> Unit }.optional() else pure(Unit)
    ) }.attempt()
    fun linebreak(): LP<Tok> = (kernel.controlSeq("\\") or kernel.controlSeq("tabularnewline"))
        .before(kernel.rawopt().optional()).before(kernel.spaces())
    fun splitWordTok(): LP<Unit> = LaTeXParsing.state().flatMap { state ->
        val stream = state.input as TokStream
        val node = stream.tokens.value() as? DeferredChain.Cons
        val head = node?.head?.value()
        if (head?.type == TokType.Word) {
            val chars = head.text.codePoints().toArray().map { Tok(head.position, TokType.Symbol, String(Character.toChars(it))) }
            LaTeXParsing.modify { it.copy(input = TokStream(stream.expanded, (TokStream(false, tokenList(chars)) + TokStream(false, node.tail)).tokens)) }
        } else pure(Unit)
    }
    fun parseAligns(): LP<List<Column>> = defer {
        val maybeBar = kernel.sp().then(kernel.symbol('|').map { Unit } or kernel.symbol('@').then(kernel.braced()).map { Unit }).attempt().skipMany()
        val align = splitWordTok().then(listOf('c', 'l', 'r', 'p', 'X', 'm', 'b').map { c ->
            kernel.symbol(c).map { when (c) { 'c' -> Alignment.AlignCenter; 'r' -> Alignment.AlignRight; else -> Alignment.AlignLeft } }
        }.reduce { a, b -> a or b })
        val width = kernel.braced().map { ts ->
            val ds = ArrayList<Tok>(); var node = ts.value(); var found = false
            while (node is DeferredChain.Cons) {
                val t = node.head.value()
                if ((t.type as? TokType.CtrlSeq)?.text == "linewidth") { found = true; break }
                ds.add(t); node = node.tail.value()
            }
            if (found) HaskellNumber.readDouble(trim(LaTeXTokens.untokenize(ds))) else null
        }.attempt()
        val skipped = kernel.braced().flatMap { ts -> LaTeXParsing.state().flatMap { state ->
            ReaderTokenParsec.lift(TokenParsec.lift { kernel.context.report(LogMessage.SkippedContent(LaTeXTokens.untokenize(ts), state.position)); null as Double? })
        } }.option(null)
        val spec = kernel.symbol('>').then(kernel.braced()).option(tokenList(emptyList())).flatMap { pref ->
            kernel.spaces().then(align).flatMap { al -> (width or skipped).flatMap { w ->
                kernel.spaces().then(kernel.symbol('<').then(kernel.braced()).option(tokenList(emptyList()))).map { suff ->
                    Column(al, if (w != null && w > 0) ColWidth.Width(w) else ColWidth.Default, pref, suff)
                }
            } }
        }
        val star = kernel.symbol('*').then(kernel.spaces()).then(kernel.bracedOrToken()).flatMap { ds ->
            kernel.spaces().then(kernel.bracedOrToken()).flatMap { specTokens ->
                val text = trim(LaTeXTokens.untokenize(ds)); val count = HaskellNumber.readInteger(text)?.toLong()
                if (count == null) ReaderTokenParsec.fail("Could not parse $text as number") else LaTeXParsing.modify { state ->
                    fun copies(n: Long): TokenList = Deferred.defer {
                        if (n <= 0) tokenList(emptyList()) else (TokStream(false, specTokens) + TokStream(false, copies(n - 1))).tokens
                    }
                    state.copy(input = TokStream(false, (TokStream(false, copies(count)) + TokStream(false, (state.input as TokStream).tokens)).tokens))
                }
            }
        }
        kernel.bgroup().then(kernel.spaces()).then(maybeBar).then(
            kernel.spaces().then(star.optional()).then(spec.before(maybeBar)).attempt().many()
        ).before(kernel.spaces()).before(kernel.egroup()).before(kernel.spaces()).attempt()
    }
    fun parseTableRow(block: LP<Blocks>, inline: LP<Inlines>, name: String, prefsufs: List<Pair<TokenList, TokenList>>): LP<Row> = defer {
        fun cellTokens(pref: TokenList, suff: TokenList): LP<TokenList> = LaTeXParsing.state().flatMap { start ->
            val protected = (kernel.controlSeq("parbox").lookAhead().then(block.map { Unit }) or
                kernel.satisfyTok { (it.type as? TokType.CtrlSeq)?.text in listOf("begin", "verb", "Verb") }.lookAhead().then(inline.map { Unit }) or
                kernel.controlSeq("begin").lookAhead().then(block.map { Unit }) or
                kernel.symbol('$').lookAhead().then(inline.map { Unit }))
            val boundary = kernel.symbol('&').map { Unit } or linebreak().map { Unit } or kernel.end(name)
            val contents = (LaTeXParsing.withRaw(protected).map { it.second } or
                boundary.notFollowedBy { "()" }.then(kernel.anyTok()).map { tokenList(listOf(it)) }).many()
            contents.flatMap { parts -> LaTeXParsing.state().flatMap { end ->
                kernel.symbol('&').optional().map {
                    concat(listOf(mapTokens(pref) { Tok(Deferred.now(start.position), it.deferredType, it.deferredText) }, concat(parts), mapTokens(suff) { Tok(Deferred.now(end.position), it.deferredType, it.deferredText) }))
                }
            } }
        }
        kernel.spaces().then(kernel.end(name)).notFollowedBy { "()" }.then(
            sequence(prefsufs.map { (pref, suff) -> cellTokens(pref, suff) })
        ).flatMap { cells -> sequence(cells.map { LaTeXParsing.parseFromToks(parseTableCell(block), it) }) }
            .before(kernel.spaces()).map { Row(Attr(), it) }
    }
    fun parseTableCell(block: LP<Blocks>): LP<Cell> = defer {
        kernel.spaces().then(LaTeXParsing.update { it.copy(inTableCell = true) }).then(
            multicolumnCell(block) or multirowCell(block) or block.many().map { Builder.simpleCell(it.fold(Blocks(), Blocks::plus)) } or
                kernel.spaces().map { Builder.emptyCell }
        ).before(LaTeXParsing.update { it.copy(inTableCell = false) }).before(kernel.spaces())
    }
    fun cellAlignment(): LP<Alignment> = kernel.symbol('|').skipMany().then(kernel.singleChar().before(kernel.braced().optional()).map {
        when (LaTeXTokens.untoken(it)) { "l" -> Alignment.AlignLeft; "r" -> Alignment.AlignRight; "c" -> Alignment.AlignCenter; else -> Alignment.AlignDefault }
    }).before(kernel.symbol('|').skipMany())
    private fun span(): LP<Long> = kernel.braced().map { HaskellNumber.readInteger(LaTeXTokens.untokenize(it))?.toLong() ?: 1 }
    fun multirowCell(block: LP<Blocks>): LP<Cell> = kernel.controlSeq("multirow").then(
        kernel.symbol('[').then(cellAlignment()).before(kernel.symbol(']')).optional()
    ).then(span()).flatMap { rows ->
        val optional = kernel.symbol('[').then(till(kernel.anyTok(), kernel.symbol(']'))).optional()
        optional.then(kernel.symbol('{')).then(till(kernel.anyTok(), kernel.symbol('}'))).then(optional)
            .then(kernel.symbol('{')).then(block.many()).before(kernel.symbol('}')).map { Builder.cell(Alignment.AlignDefault, rows, 1, it.fold(Blocks(), Blocks::plus)) }
    }
    fun multicolumnCell(block: LP<Blocks>): LP<Cell> = kernel.controlSeq("multicolumn").then(span()).flatMap { cols ->
        kernel.symbol('{').then(cellAlignment()).before(kernel.symbol('}')).flatMap { alignment ->
            val nested = multirowCell(block).map { Builder.cell(alignment, it.rowSpan, cols, Blocks(it.blocks)) }
            val simple = block.many().map { Builder.cell(alignment, 1, cols, it.fold(Blocks(), Blocks::plus)) }
            kernel.symbol('{').then(nested or simple).before(kernel.symbol('}'))
        }
    }
    fun simpTable(block: LP<Blocks>, inline: LP<Inlines>, name: String, hasWidth: Boolean): LP<Blocks> = defer {
        (if (hasWidth) kernel.tokWith(inline).map { Unit } else pure(Unit)).then(kernel.skipopts()).then(parseAligns()).flatMap { specs ->
            val caption = kernel.controlSeq("caption").then(kernel.setCaption(inline)).optional()
            val label = kernel.label().optional()
            val row = parseTableRow(block, inline, name, specs.map { it.prefix to it.suffix })
            caption.then(kernel.spaces()).then(label).then(kernel.spaces()).then(linebreak().optional()).then(kernel.spaces())
                .then(hline().skipMany()).then(kernel.spaces()).then(row.before(linebreak()).before(hline().many1()).map { listOf(it) }.attempt().option(emptyList()))
                .flatMap { head -> kernel.spaces().then(sepEndBy(row, linebreak().before(hline().skipMany().optional()))).flatMap { rows ->
                    kernel.spaces().then(caption).then(kernel.spaces()).then(label).then(kernel.spaces()).then(linebreak().optional()).then(kernel.spaces())
                        .then(kernel.controlSeq("end").lookAhead()).map {
                            TableCompaction.compactify(Builder.table(Tables.emptyCaption, specs.map { ColSpec(it.alignment, it.width) },
                                TableHead(Attr(), fixTableRows(head)), listOf(TableBody(Attr(), 0, emptyList(), fixTableRows(rows))), TableFoot(Attr(), emptyList())))
                        }
                } }
        }.attempt()
    }
    fun addTableCaption(blocks: Blocks): LP<Blocks> = LaTeXParsing.state().flatMap { initial ->
        var state = initial.user
        val visitor = object : PandocVisitor() {
            override fun transformBlock(value: Block): Block {
                if (value !is Block.Table) return value
                val old = state; val label = old.lastLabel
                if (label != null) {
                    val chapter = old.lastHeaderNum.numbers.firstOrNull().takeIf { old.hasChapters }
                    val numbers = old.lastTableNum.numbers
                    val number = DottedNum(when (numbers.size) {
                        2 -> if (chapter == null) listOf(1) else if (chapter == numbers[0]) listOf(chapter, numbers[1] + 1) else listOf(chapter, 1)
                        1 -> if (chapter == null) listOf(numbers[0] + 1) else listOf(chapter, 1)
                        else -> if (chapter == null) listOf(1) else listOf(chapter, 1)
                    })
                    state = old.copy(lastTableNum = number, labels = old.labels + (label to listOf(Inline.Str(number.render()))))
                }
                val table = value.copy(attr = if (label == null) value.attr else value.attr.copy(identifier = label), caption = old.caption ?: value.caption)
                return if (label == null) table else kernel.removeLabel(label, table) { v, x -> v.walkBlock(x) as Block.Table }
            }
        }
        val result = Blocks(visitor.walkBlocks(blocks.values))
        LaTeXParsing.update { state }.map { result }
    }
    companion object {
        private fun trim(s: String) = SharedText.trim(s)
        fun fixTableRows(rows: List<Row>): List<Row> = LaTeXTableRows.fixRows(rows)
    }
}
