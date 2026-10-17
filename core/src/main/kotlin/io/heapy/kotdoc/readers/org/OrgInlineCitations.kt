// Text.Pandoc.Readers.Org.Inlines, Copyright (C) 2014-2025 Albert Krewinkel.
// GPL-2.0-or-later; original source and notices retained in upstream/pandoc.
package io.heapy.kotdoc.readers.org
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.ast.Builder as B
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.options.Extension
internal fun <A> OrgGrammar.withRaw(p:OP<A>):OP<Pair<A,String>> = state().flatMap{old->p.flatMap{value->state().map{next->value to sourceDifference(old.input.stream as Sources,next.input.stream as Sources)}}}
internal fun sourceDifference(first:Sources,second:Sources):String=buildString{
 var a=first.unSources.value();var b=second.unSources.value()
 while(a is DeferredChain.Cons){if(b===DeferredChain.Nil){while(a is DeferredChain.Cons){append(a.head.value().text);a=a.tail.value()};break}
  b as DeferredChain.Cons;val x=a.head.value();val y=b.head.value()
  if(x.position==y.position){if(x.text==y.text){a=a.tail.value();b=b.tail.value()}else{if(x.text.endsWith(y.text))append(x.text.dropLast(y.text.length));break}}
  else{append(x.text);a=a.tail.value()}
 }
}
internal fun <A,B> OrgGrammar.sep1(p:OP<A>,sep:OP<B>):OP<List<A>> = p.flatMap{first->sep.then(p).attempt().many().map{listOf(first)+it}}
internal fun OrgGrammar.spnl():OP<Unit> = skipSpaces().then(newline().then(not(blankline())).then(skipSpaces()).optional())
internal fun OrgGrammar.rawAffix(prefix:Boolean):OP<String>{
 fun item():OP<Unit> = satisfy{it !in intArrayOf(94,59,91,93)&&(!prefix||it!=64)}.map{Unit} or char('[').then(delay{rawAffix(prefix)}).before(char(']')).map{Unit}.attempt()
 return withRaw(delay{item()}.many()).map{it.second}
}
internal fun OrgGrammar.citePrefix():OP<OF<Inlines>> = rawAffix(true).flatMap{parseFromString(delay{inline()}.many().map{inlines(it).mapValue(Inlines::trim)},it)}
internal fun OrgGrammar.citeSuffix():OP<OF<Inlines>> = rawAffix(false).flatMap{parseFromString(delay{inline()}.many().map(::inlines),it)}
internal fun OrgGrammar.citeStyle():OP<String> {
 val style=char('/').then(take1(ParserCharacters::isAlpha)).flatMap{s->if(s in listOf("author","a","noauthor","na","nocite","n","text","t","note","ft","numeric","nb","nil"))pure(s)else fail("Unknown org cite style "+io.heapy.kotdoc.formats.HaskellString.quote(s))}.attempt().option("nil")
 val full=choice(listOf("bare","caps","full").map{text(it).attempt()})
 val variants=char('/').then(sep1(full,char('-')) or oneOf("bcf").many1().map{it.map{c->chars(listOf(c))}}).attempt().optional()
 return style.before(variants)
}
internal fun OrgGrammar.citeItem():OP<OF<Citation>> = citePrefix().flatMap{prefix->char('@').then(take1{ParserCharacters.isAlphaNum(it)||it in ".:?!`'/*@+|(){}<>&_^$#%~-".codePoints().toArray()}).flatMap{key->citeSuffix().map{suffix->f2(prefix,suffix){p,s->Citation(key,p.values,s.values)}}}}.attempt()
internal fun OrgGrammar.orgCite():OP<OF<List<Citation>>> = text("[cite").then(citeStyle()).before(char(':')).before(spnl()).flatMap{style->
 maybe(citePrefix().before(char(';')).attempt()).flatMap{prefix->sep1(citeItem(),char(';').before(sp().many())).flatMap{items->
  maybe(char(';').then(citeSuffix()).attempt()).before(spnl()).before(char(']')).map{suffix->
   val citations=sequence(items)
   f2(f2(prefix?:future(Inlines()),citations){p,cs->if(cs.isEmpty())cs else listOf(cs.first().copy(citationPrefix=p.values+cs.first().citationPrefix))+cs.drop(1)},suffix?:future(Inlines())){cs,s->
    val modified=if(cs.isEmpty())cs else cs.dropLast(1)+cs.last().copy(citationSuffix=cs.last().citationSuffix+s.values)
    if(modified.isEmpty())modified else when(style){"text","t"->listOf(modified.first().copy(citationMode=CitationMode.AuthorInText,citationSuffix=modified.first().citationSuffix.dropWhile{it==Inline.Space}))+modified.drop(1);"noauthor","na"->listOf(modified.first().copy(citationMode=CitationMode.SuppressAuthor))+modified.drop(1);else->modified}
   }
  }
 }}
}.attempt()
internal fun OrgGrammar.orgRefCiteMode():OP<CitationMode> = choice(listOf("cite" to CitationMode.AuthorInText,"citep" to CitationMode.NormalCitation,"citep*" to CitationMode.NormalCitation,"citet" to CitationMode.AuthorInText,"citet*" to CitationMode.AuthorInText,"citeyear" to CitationMode.SuppressAuthor).map{(key,mode)->text(key).before(char(':')).map{mode}.attempt()})
internal fun OrgGrammar.orgRefCiteKey():OP<String> {
 val special="-_:\\./"
 fun key(c:Int)=ParserCharacters.isAlphaNum(c)||c in special.codePoints().toArray()
 val end=oneOf(special).many().then(satisfy{!key(it)}).attempt().lookAhead()
 return char('&').optional().then(till1(satisfy(::key),end)).map(::chars).attempt()
}
internal fun OrgGrammar.orgRefCite():OP<OF<List<Citation>>> {
 val normal=orgRefCiteMode().flatMap{mode->orgRefCiteKey().flatMap{first->char(',').then(orgRefCiteKey()).attempt().many().map{keys->future((listOf(first)+keys).map{Citation(it,citationMode=mode)})}}}.attempt()
 val linked=text("[[").then(orgRefCiteMode()).flatMap{mode->orgRefCiteKey().before(text("][")).flatMap{key->
  till(delay{inline()},text("::").attempt()).map{inlines(it).mapValue(Inlines::trim)}.flatMap{prefix->sp().map{true}.option(false).flatMap{space->
   till(delay{inline()},text("]]").attempt()).map{inlines(it).mapValue(Inlines::trim)}.map{suffix->f2(prefix,suffix){p,s->listOf(Citation(key,p.values,(if(space)B.space+s else s).values,mode))}}
  }}
 }}.attempt()
 return (normal or linked).attempt()
}
internal fun OrgGrammar.orgCitation():OP<OF<Inlines>> = state().flatMap{s->guard(Extension.read("citations") in s.user.options.extensions)}.then(withRaw((orgCite() or orgRefCite()).attempt())).map{(citations,raw)->citations.mapValue{B.cite(it,B.text(raw))}}
