// Text.Pandoc.Readers.Org.Inlines, Copyright (C) 2014-2025 Albert Krewinkel.
// GPL-2.0-or-later; original source and notices retained in upstream/pandoc.
package io.heapy.kotdoc.readers.org
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.ast.Builder as B
import io.heapy.kotdoc.parsing.*
internal fun OrgGrammar.smart()=SmartParsing<OrgLocal,OrgState>(OrgAccess,OrgAccess,ReaderParsec.asksValue{it.quote}){q,p->p.localValue{it.copy(quote=q)}}
internal fun OrgGrammar.orgSmartQuotes():OP<OF<Inlines>> {
 val smart=smart()
 val single=smart.singleQuoteStart().then(updatePositions(39)).then(till1(delay{inline()},smart.singleQuoteEnd().before(updatePositions(39))).map{inlines(it).mapValue{B.singleQuoted(it.trim())}}.localValue{it.copy(quote=QuoteContext.InSingleQuote)}).attempt()
 // Only the closing branch is under InDoubleQuote in the original reader;
 // contents are parsed in the surrounding context.
 val double=smart.doubleQuoteStart().then(updatePositions(34)).then(not(smart.doubleQuoteEnd()).then(delay{inline()}).attempt().many()).flatMap{parts->
  val content=inlines(parts)
  smart.doubleQuoteEnd().then(updateForbidden()).map{content.mapValue{B.doubleQuoted(it.trim())}}.localValue{it.copy(quote=QuoteContext.InDoubleQuote)} or pure(content.mapValue{B.str("“")+it})
 }.attempt()
 val apostrophe=(char('\'') or char('’')).before(updatePre()).before(updateForbidden()).map{future(B.str("’"))}
 return state().flatMap{s->guard(s.user.export.smartQuotes)}.then(double or single or apostrophe)
}
internal fun OrgGrammar.orgSpecialStrings():OP<OF<Inlines>> {
 val smart=smart()
 val shy=text("\\-").map{B.str("\u00AD")}.attempt().before(updatePositions(45))
 return state().flatMap{s->guard(s.user.export.specialStrings)}.then(smart.dash().before(updatePositions(45)) or smart.ellipses().before(updatePositions(46)) or shy).map(::future)
}
