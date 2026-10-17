// Text.Pandoc.Readers.Org.Inlines, Copyright (C) 2014-2025 Albert Krewinkel.
// GPL-2.0-or-later; original source and notices retained in upstream/pandoc.
package io.heapy.kotdoc.readers.org
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.ast.Builder as B
import io.heapy.kotdoc.parsing.*

internal const val orgSpecialChars="\"$'()*+-,./:;<=>@[\\]^_{|}~"
internal const val orgEmphasisForbidden="\t\n\r \u200B"
internal const val orgMathForbidden="\t\n\r ,;.$"
internal const val orgMathPost="\t\n \"'),-.:;?"
internal fun isOrgStrEnd(c:Int)=c==32||c==10||c==13||c in orgSpecialChars.codePoints().toArray()
internal fun OrgGrammar.updatePositions(c:Int):OP<Int> = state().flatMap{s->
 (if(c in s.user.emphasisPre.codePoints().toArray())updatePre()else pure(Unit)).then(
 if(c in orgEmphasisForbidden.codePoints().toArray())updateForbidden()else pure(Unit)).map{c}
}
internal fun OrgGrammar.orgStr():OP<OF<Inlines>> = take1{!isOrgStrEnd(it)}.flatMap{s->updatePositions(s.codePointBefore(s.length)).map{future(B.str(s))}}.before(updateStr())
internal fun OrgGrammar.orgWhitespace():OP<OF<Inlines>> = skipSpaces1().then(updatePre()).then(updateForbidden()).map{future(B.space)}.label("whitespace")
internal fun OrgGrammar.orgLinebreak():OP<OF<Inlines>> = text("\\\\").then(skipSpaces()).then(newline()).map{future(B.linebreak)}.attempt()
internal fun OrgGrammar.orgEndline():OP<OF<Inlines>> = newline().then(not(endOfBlock())).then(update{it.copy(emphasisNewlines=it.emphasisNewlines?.minus(1))}).then(state()).flatMap{s->guard(s.user.emphasisNewlines?.let{it>=0}!=false).then(updatePre()).map{future(if(s.user.export.preserveBreaks)B.linebreak else B.softbreak)}}.attempt()
internal fun OrgGrammar.orgSymbol():OP<OF<Inlines>> = oneOf(orgSpecialChars).flatMap{updatePositions(it)}.map{future(B.str(chars(listOf(it))))}
internal fun OrgGrammar.emphasisStart(c:Char):OP<Int> = state().flatMap{s->guard(s.user.lastPre==null||s.user.lastPre==s.position)}.then(char(c)).before(noneOf(orgEmphasisForbidden).lookAhead()).before(update{it.copy(emphasisStack=listOf(c.code)+it.emphasisStack)}).before(updatePre()).attempt()
internal fun OrgGrammar.emphasisEnd(c:Char):OP<Int> = state().flatMap{s->guard(s.user.lastForbidden!=s.position)}.then(char(c)).flatMap{close->
 (eof() or state().flatMap{s->oneOf(chars(s.user.emphasisStack.drop(1).take(1))+s.user.emphasisPost).lookAhead().map{Unit}}).then(updateStr()).then(update{it.copy(emphasisStack=it.emphasisStack.drop(1))}).map{close}
}.attempt()
internal fun OrgGrammar.emphasisBetween(c:Char):OP<OF<Inlines>> = update{it.copy(emphasisNewlines=1)}.then(emphasisStart(c)).then(till1(delay{inline()},emphasisEnd(c))).map{inlines(it).mapValue(Inlines::trim)}.flatMap{x->state().flatMap{s->(if(s.user.emphasisStack.isEmpty())update{it.copy(emphasisNewlines=null)}else pure(Unit)).map{x}}}.attempt()
internal fun OrgGrammar.emphasizedText():OP<OF<Inlines>> = state().flatMap{s->guard(s.user.export.emphasizedText)}.then(choice(listOf(
 emphasisBetween('/').map{it.mapValue(B::emph)},emphasisBetween('*').map{it.mapValue(B::strong)},emphasisBetween('+').map{it.mapValue(B::strikeout)},emphasisBetween('_').map{it.mapValue(B::underline)}))).attempt()
internal fun <A> OrgGrammar.linesLimited(n:Int,p:OP<Int>,end:OP<A>):OP<String> {
 fun more(left:Int):OP<List<Int>> = till(p,end).attempt() or if(left>0)till(p,newline()).flatMap{first->delay{more(left-1)}.map{first+10+it}}else zero()
 return delay{more(n)}.flatMap{points->guard(points.isNotEmpty()).map{chars(points)}}.attempt()
}
internal fun OrgGrammar.verbatimBetween(c:Char):OP<String> = emphasisStart(c).then(linesLimited(1,noneOf("\n\r").flatMap{updatePositions(it)},emphasisEnd(c))).map{it.replace('\n',' ')}.attempt()
internal fun OrgGrammar.orgCode():OP<OF<Inlines>> = verbatimBetween('~').map{future(B.code(it))}
internal fun OrgGrammar.orgVerbatim():OP<OF<Inlines>> = verbatimBetween('=').map{future(B.codeWith(Attr(classes=listOf("verbatim")),it))}
internal fun OrgGrammar.mathEnd(c:Char):OP<Int> = noneOf("$c$orgMathForbidden").before(char(c)).before(eof() or oneOf(orgMathPost).lookAhead().map{Unit}).attempt()
internal fun OrgGrammar.mathOne(c:Char):OP<String> = char(c).then(noneOf("$c$orgMathForbidden")).before(char(c)).before(eof() or oneOf(orgMathPost).lookAhead().map{Unit}).map{chars(listOf(it))}.attempt()
internal fun OrgGrammar.mathText(c:Char):OP<String> = char(c).before(not(oneOf("$c$orgMathForbidden"))).then(linesLimited(2,noneOf("$c\n\r"),mathEnd(c).lookAhead())).flatMap{first->mathEnd(c).map{first+chars(listOf(it))}}.attempt()
internal fun OrgGrammar.rawMath(start:String,end:String):OP<String> = text(start).then(till(any(),text(end).attempt())).map(::chars).attempt()
internal fun OrgGrammar.orgMath():OP<OF<Inlines>> = (mathOne('$') or mathText('$') or rawMath("\\(","\\)")).map{future(B.math(it))}
internal fun OrgGrammar.orgDisplayMath():OP<OF<Inlines>> = (rawMath("\\[","\\]") or rawMath("$$","$$")).map{future(B.displayMath(it))}
internal fun OrgGrammar.balanced(open:Char,close:Char):OP<String> {
 fun item():OP<String> = delay{balanced(open,close)}.map{"$open$it$close"} or noneOf("$open$close\n\r").map{chars(listOf(it))}
 return char(open).then(till(delay{item()},char(close))).map{it.joinToString("")}
}
internal fun OrgGrammar.subOrSuperExpr():OP<OF<Inlines>> = state().flatMap{s->
 fun braced()=balanced('{','}').flatMap{parseFromString(delay{inline()}.many().map(::inlines),it)}
 when(s.user.export.subSuperscripts){
 SubSup.None->zero()
 SubSup.Braced->braced()
 SubSup.All->(text("*") or maybe(oneOf("+-")).flatMap{prefix->take1(ParserCharacters::isAlphaNum).map{(prefix?.let{chars(listOf(it))}?:"")+it}}).map{future(B.str(it))}.attempt() or braced() or balanced('(',')').flatMap{parseFromString(delay{inline()}.many().map(::inlines),"($it)")}
 }
}.attempt()
internal fun OrgGrammar.orgSubscript():OP<OF<Inlines>> = state().flatMap{s->guard(s.user.lastStr==s.position)}.then(char('_').then(subOrSuperExpr()).attempt()).map{it.mapValue(B::subscript)}
internal fun OrgGrammar.orgSuperscript():OP<OF<Inlines>> = state().flatMap{s->guard(s.user.lastStr==s.position)}.then(char('^').then(subOrSuperExpr()).attempt()).map{it.mapValue(B::superscript)}
internal fun OrgGrammar.addToNotesTable(ref:String,note:OF<Blocks>)=update{it.copy(notes=listOf(ref to note)+it.notes)}
internal fun OrgGrammar.inlineNote():OP<OF<Inlines>> = text("[fn:").then(take{ParserCharacters.isAlphaNum(it)||it==45||it==95}).before(char(':')).flatMap{ref->till1(delay{inline()},char(']')).map{inlines(it).mapValue{B.para(it.trim())}}.flatMap{note->(if(ref.isNotEmpty())addToNotesTable("fn:$ref",note)else pure(Unit)).map{note.mapValue(B::note)}}}.attempt()
internal fun OrgGrammar.referencedNote():OP<OF<Inlines>> = noteMarker().map{ref->Future<OrgState,Inlines>{final->final.flatMapValue{s->val note=s.notes.firstOrNull{it.first==ref}?.second;if(note==null)Deferred.now(B.str("[$ref]"))else note.runDelayed(Deferred.now(s.copy(notes=emptyList()))).mapValue(B::note)}}}.attempt()
internal fun OrgGrammar.orgFootnote():OP<OF<Inlines>> = (inlineNote() or referencedNote()).flatMap{note->state().map{if(it.user.export.withFootnotes)note else future(Inlines())}}.attempt()
internal fun OrgGrammar.exportSnippet():OP<OF<Inlines>> = text("@@").then(till1(satisfy{ParserCharacters.isAlphaNum(it)||it==45},char(':'))).map(::chars).flatMap{format->till(any(),text("@@").attempt()).map{future(B.rawInline(format,chars(it)))}}.attempt()
internal fun OrgGrammar.orgMacro():OP<OF<Inlines>> = state().flatMap{s->guard(s.user.macroDepth<15).then(text("{{{")).then(take(ParserCharacters::isAlphaNum)).flatMap{name->
 val eoa=text(")}}}")
 val arg=till(not(eoa).then(char('\\').then(oneOf(",\\")).attempt() or noneOf(",")),(char(',').map{false} or eoa.map{true}).lookAhead()).map(::chars)
 val args=text("}}}").map{emptyList<String>()} or char('(').then(arg.flatMap{first->char(',').then(arg).many().map{listOf(first)+it}}).before(eoa)
 args.flatMap{as_->val expand=s.user.macros[name];if(expand==null)zero()else update{it.copy(macroDepth=s.user.macroDepth+1)}.then(parseFromString(delay{inline()}.many().map(::inlines),expand(as_))).before(update{it.copy(macroDepth=s.user.macroDepth)})}
}}.attempt()
