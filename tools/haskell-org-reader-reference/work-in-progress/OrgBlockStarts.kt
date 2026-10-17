// Text.Pandoc.Readers.Org.BlockStarts, ©2014-2024 Albert Krewinkel.
// GPL-2.0-or-later; original source/notices retained in upstream/pandoc.
package io.heapy.kotdoc.readers.org
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.options.Extension
import io.heapy.kotdoc.resources.safeReadInteger
import io.heapy.kotdoc.latex.LaTeXMathReader
internal fun OrgGrammar.hline()=skipSpaces().then(text("-----")).before(char('-').many()).before(skipSpaces()).before(newline()).map{Unit}.attempt()
internal fun OrgGrammar.headerStart()=char('*').many1().map{it.size.toLong()}.before(char(' ').many1()).before(updatePre()).attempt()
internal fun OrgGrammar.tableStart()=skipSpaces().then(char('|')).attempt()
internal fun OrgGrammar.gridTableStart()=skipSpaces().before(char('+')).before(char('-')).attempt()
internal fun OrgGrammar.latexEnvStart():OP<String> = skipSpaces().then(text("\\begin{")).then(take1(ParserCharacters::isAlphaNum)).flatMap{name->text("*").option("").map{name+it}}.before(char('}')).flatMap{name->guard(name !in LaTeXMathReader().inlineEnvironmentNames).map{name}}.attempt()
internal fun OrgGrammar.listCounterCookie():OP<Long>{
 val number=take1(ParserCharacters::isDigit).flatMap{safeReadInteger(it)?.toLong()?.let(::pure)?:zero()}
 return text("[@").then(number or lift(ListParsing.lowerAlpha<OrgState>()).map{it.second} or lift(ListParsing.upperAlpha<OrgState>()).map{it.second}).before(char(']')).before(skipSpaces() or char('\n').lookAhead().map{Unit}).attempt()
}
internal fun OrgGrammar.bulletListStart():OP<Long> = sp().many().flatMap{ind->oneOf(if(ind.isEmpty())"+-"else"*+-").then(skipSpaces1() or char('\n').lookAhead().map{Unit}).then(maybe(listCounterCookie())).map{ind.size+1L}}.attempt()
internal fun OrgGrammar.orderedListStart():OP<Pair<Long,ListAttributes>> = sp().many().flatMap{ind->state().flatMap{st->
 val fancy=Extension.read("fancy_lists")in st.user.options.extensions
 val style=take1(ParserCharacters::isDigit).map{if(fancy)ListNumberStyle.Decimal else ListNumberStyle.DefaultStyle} or if(fancy)(lift(ListParsing.lowerAlpha<OrgState>()).map{it.first} or lift(ListParsing.upperAlpha<OrgState>()).map{it.first})else zero()
 val delim=char('.').map{if(fancy)ListNumberDelim.Period else ListNumberDelim.DefaultDelim} or char(')').map{if(fancy)ListNumberDelim.OneParen else ListNumberDelim.DefaultDelim}
 style.flatMap{s->delim.flatMap{d->(skipSpaces1() or char('\n').lookAhead().map{Unit}).then(listCounterCookie().option(1)).map{(ind.size+1L)to ListAttributes(it,s,d)}}}
}}.attempt()
internal fun OrgGrammar.drawerStart()=skipSpaces().then(char(':')).then(till(nonspace(),char(':'))).map(::chars).before(skipSpaces()).before(newline()).attempt()
internal fun OrgGrammar.metaLineStart()=skipSpaces().before(text("#+")).attempt()
internal fun OrgGrammar.commentLineStart()=skipSpaces().before(text("#")).before(oneOf(" \n").lookAhead()).attempt()
internal fun OrgGrammar.exampleLineStart()=skipSpaces().then(char(':')).then(char(' ').map{Unit} or char('\n').lookAhead().map{Unit}).attempt()
internal fun OrgGrammar.noteMarker():OP<String> = char('[').then(till1(satisfy(ParserCharacters::isDigit),char(']')).map(::chars) or text("fn:").flatMap{prefix->till1(noneOf("\n\r\t "),char(']')).map{prefix+chars(it)}}).attempt()
internal fun OrgGrammar.endOfBlock():OP<Unit> = (blankline().map{Unit} or choice(listOf(exampleLineStart(),hline(),metaLineStart(),commentLineStart(),gridTableStart(),noteMarker().map{Unit},tableStart().map{Unit},drawerStart().map{Unit},headerStart().map{Unit},latexEnvStart().map{Unit},bulletListStart().map{Unit},orderedListStart().map{Unit})).attempt()).attempt().lookAhead()
