// Text.Pandoc.Parsing.Smart, © 2006-2024 John MacFarlane.
// GPL-2.0-or-later; original module and full notice in upstream/pandoc and licenses/.
package io.heapy.kotdoc.parsing

import io.heapy.kotdoc.ast.Builder as B
import io.heapy.kotdoc.ast.Inlines
import io.heapy.kotdoc.options.Extension

/** Quote context may live in state or ReaderT.local, independently of lastStrPos. */
class SmartParsing<E,U>(
    private val options: ReaderOptionsAccess<U>,
    private val last: LastStrPositionAccess<U>,
    private val quote: ReaderParsec<E,U,QuoteContext>,
    private val withQuote: (QuoteContext,ReaderParsec<E,U,Inlines>)->ReaderParsec<E,U,Inlines>,
) {
    private fun <A> lift(p: Parsec<U,A>) = ReaderParsec.lift<E,U,A>(p)
    private fun <A> pure(value: A) = ReaderParsec.pure<E,U,A>(value)
    private fun char(c: Char) = lift(SourceParsers.char<U>(c.code))
    private fun <A,B> till(p: ReaderParsec<E,U,A>,end: ReaderParsec<E,U,B>): ReaderParsec<E,U,List<A>> = ReaderParsec { env ->
        class Link<A>(val value: A,val next: Link<A>?)
        fun go(): Parsec<U,Link<A>?> = end.runReader(env).map<Link<A>?> { null } or p.runReader(env).flatMap { first -> Parsec.defer { go() }.map<Link<A>?> { Link(first,it) } }
        Parsec.defer { go() }.map { first -> buildList { var node=first;while(node!=null) { add(node.value);node=node.next } } }
    }
    fun smartPunctuation(inline: ReaderParsec<E,U,Inlines>): ReaderParsec<E,U,Inlines> =
        lift(ReaderOptionGuards.guardEnabled(options,Extension.read("smart"))).then(
            doubleQuoted(inline) or singleQuoted(inline) or apostrophe() or doubleCloseQuote() or dash() or ellipses())
    private fun start(context: QuoteContext,c: Char): ReaderParsec<E,U,Unit> = quote.flatMap {
        if(it==context)ReaderParsec.fail("already inside quotes") else pure(Unit)
    }.then(lift(ParserCapabilities.notAfterString(last))).flatMap { good ->
        lift(Parsec.guard<U>(good)).then(char(c).then(lift(SourceParsers.satisfy<U> { it !in intArrayOf(32,9,10,13) }.lookAhead())).map { Unit }.attempt())
    }
    fun singleQuoteStart()=start(QuoteContext.InSingleQuote,'\'')
    fun doubleQuoteStart()=start(QuoteContext.InDoubleQuote,'"')
    fun singleQuoteEnd()=char('\'').then(lift(SourceParsers.alphaNum<U>().notFollowedBy(Parsec.Companion::showChar))).attempt()
    fun doubleQuoteEnd()=char('"').map { Unit }
    fun doubleQuoted(inline: ReaderParsec<E,U,Inlines>) = doubleQuoteStart().then(
        withQuote(QuoteContext.InDoubleQuote,till(inline,doubleQuoteEnd()).map { B.doubleQuoted(it.fold(Inlines(),Inlines::plus)) }).attempt() or pure(B.str("“")))
    fun singleQuoted(inline: ReaderParsec<E,U,Inlines>): ReaderParsec<E,U,Inlines> {
        val end=singleQuoteEnd()
        // many1Till uses General.notFollowedBy', including empty success.
        val nonempty=ReaderParsec<E,U,Unit> { env ->
            (end.runReader(env).attempt().map { Parsec.unexpected<U,Unit>("()") } or Parsec.pure(Parsec.pure<U,Unit>(Unit))).flatMap { it }.attempt()
        }.then(inline).flatMap { first -> till(inline,end).map { B.singleQuoted(it.fold(first,Inlines::plus)) } }
        return singleQuoteStart().then(withQuote(QuoteContext.InSingleQuote,nonempty).attempt() or pure(B.str("’")))
    }
    fun apostrophe()=(char('\'') or char('’')).map { B.str("’") }
    fun doubleCloseQuote()=char('"').map { B.str("”") }
    fun ellipses()=lift(SourceParsers.string<U>("...")).map { B.str("…") }.attempt()
    fun dash(): ReaderParsec<E,U,Inlines> = lift(Parsec.state<U>()).flatMap { s ->
        if(Extension.read("old_dashes") in options.extractReaderOptions(s.user).extensions)
            char('-').then(char('-').map { B.str("—") } or lift(SourceParsers.digit<U>().lookAhead()).map { B.str("–") })
        else lift(SourceParsers.string<U>("--")).then(char('-').map { B.str("—") } or pure(B.str("–")))
    }.attempt()
}
