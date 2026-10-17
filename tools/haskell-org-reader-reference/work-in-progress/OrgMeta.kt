// Text.Pandoc.Readers.Org.Meta, ©2014-2024 Albert Krewinkel; GPL-2.0-or-later.
package io.heapy.kotdoc.readers.org
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.ast.Builder as B
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.latex.LaTeXReaderText as Text
import io.heapy.kotdoc.uri.PandocURI
import io.heapy.kotdoc.resources.safeReadInteger
internal fun OrgGrammar.metaExport():OP<OF<MetadataMap<MetaValue>>> = state().map{s->s.user.meta.mapValue{m->var out=m;if(!s.user.export.withAuthor)out=out.deleted("author");if(!s.user.export.withCreator)out=out.deleted("creator");if(!s.user.export.withEmail)out=out.deleted("email");out}}
internal fun OrgGrammar.metaKey()=take1{it !in intArrayOf(58,32,10,13)}.map(Text::toLower).before(char(':')).before(skipSpaces())
internal fun OrgGrammar.lineOfInlines()=updatePre().then(till(delay{inline()},newline())).map{inlines(it).mapValue(Inlines::trim)}
internal fun <A> OrgGrammar.parseMeta(key:String,p:OP<OF<A>>,convert:(A,MetadataMap<MetaValue>)->MetaValue):OP<Unit> = p.flatMap{value->state().flatMap{s->
 val updated:OF<MetadataMap<MetaValue>> = Future{final->s.user.meta.runDelayed(final).mapValue{old->old.inserted(key){convert(value.runFLazy(final),old)}}}
 update{it.copy(meta=updated)}
}}
internal fun OrgGrammar.metaLine():OP<Blocks> = metaLineStart().then(metaKey()).flatMap{key->
 fun lines()=parseMeta(key,lineOfInlines()){v,m->orgLinesValue(key,v,m)}
 fun string(target:String=key,p:OP<String> = anyLine())=parseMeta(target,p.map(::future)){v,_->MetaValue.MetaString(v)}
 fun snippets(format:String)=parseMeta("header-includes",anyLine().map{future(B.rawInline(format,it))}){v,m->orgListValue("header-includes",MetaValue.MetaInlines(v.values),m)}
 when(key){
  "author","date","description","header-includes","institute","keywords","nocite","subtitle","title"->if(key=="date")parseMeta(key,lineOfInlines()){v,_->MetaValue.MetaInlines(v.values)}else lines()
  "bibliography"->parseMeta(key,anyLine().map(::future)){v,m->orgListValue(key,MetaValue.MetaString(v),m)}
  "creator","email"->string()
  "language"->string("lang")
  "latex_class"->string("documentclass")
  "latex_class_options"->string("classoption",anyLine().map{it.filter{c->c!='['&&c!=']'}})
  "html_head","html_head_extra"->snippets("html")
  "latex_header","latex_header_extra"->snippets("latex")
  "exclude_tags","select_tags"->tagList().flatMap{tags->update{s->if(key=="exclude_tags")s.copy(excludeTags=if(s.excludeTagsChanged)s.excludeTags+tags else tags.toSet(),excludeTagsChanged=true)else s.copy(selectTags=if(s.selectTagsChanged)s.selectTags+tags else tags.toSet(),selectTagsChanged=true)}}
  "link"->addLinkFormatter()
  "macro"->macroDefinition().flatMap{(name,f)->update{it.copy(macros=it.macros+(name to f))}}
  "options"->exportSettings()
  "pandoc-emphasis-post","pandoc-emphasis-pre"->skipSpaces().then(anyLine()).map{raw->try{ReadDictionary<List<Int>>(ReadShape.Items(ReadShape.Prim.CHAR)).read(raw).let(::chars)}catch(_:IllegalArgumentException){null}}.flatMap{value->update{if(key.endsWith("post"))it.copy(emphasisPost=value?:OrgState().emphasisPost)else it.copy(emphasisPre=value?:OrgState().emphasisPre)}}
  "result"->anyLine().map{Unit}
  "seq_todo","todo","typ_todo"->todoSequence().flatMap{seq->update{it.copy(todoSequences=listOf(seq)+it.todoSequences)}}
  else->fail("Unknown keyword: $key")
 }
}.map{Blocks()}.attempt()
internal fun OrgGrammar.tagList()=skipSpaces().then(tagWord().before(skipSpaces()).many()).before(newline())
internal fun OrgGrammar.addLinkFormatter():OP<Unit> = satisfy(ParserCharacters::isAlpha).flatMap{c->take{ParserCharacters.isAlphaNum(it)||it==45||it==95}.map{chars(listOf(c))+it}}.before(skipSpaces()).flatMap{key->
 fun rest()=till(any(),eof() or oneOf("\n\r").map{Unit}).map(::chars)
 fun replacement(c:Char,escape:Boolean)=till(noneOf("\n\r"),text("%$c").attempt()).map(::chars).flatMap{first->rest().map{last->{value:String->first+(if(escape)PandocURI.urlEncode(value)else value)+last}}}.attempt()
 val formatter=replacement('s',false) or replacement('h',true) or rest().map{first->{value:String->first+value}}
 formatter.flatMap{f->update{it.copy(linkFormatters=it.linkFormatters+(key to f))}}
}.attempt()
internal fun OrgGrammar.todoSequence():OP<List<TodoMarker>>{
 val sep=skipSpaces().then(char('|')).before(skipSpaces1()).map{Unit}.attempt()
 val keyword=take1{it !in intArrayOf(32,9,10,13)}.map{it.takeWhile{c->c!='('}}.before(skipSpaces())
 val todo=till(keyword,(sep or newline().map{Unit}).lookAhead()).attempt()
 val done=till(keyword.before(sep.optional()),newline().lookAhead()).attempt()
 return todo.flatMap{ts->maybe(sep.then(done)).before(newline()).flatMap{ds->
  if(ds==null&&ts.isEmpty())zero()else pure((if(ds==null)ts.dropLast(1)else ts).map{TodoMarker(false,it)}+(ds?:listOf(ts.last())).map{TodoMarker(true,it)})
 }}.attempt()
}
internal fun OrgGrammar.macroDefinition():OP<Pair<String,(List<String>)->String>> {
 val placeholder=char('$').then(take1(ParserCharacters::isDigit)).map{safeReadInteger(it)?.toLong()?:1L}.attempt()
 val part=not(placeholder).then(noneOf("\n\r")).many().map(::chars).attempt()
 return take1{it !in intArrayOf(32,9,10,13)}.before(skipSpaces()).flatMap{name->part.flatMap{first->placeholder.flatMap{i->part.map{i to it}}.many().map{rest->
  val order=rest.map{it.first};val pieces=listOf(first)+rest.map{it.second}
  name to {args:List<String>->val values=order.mapNotNull{n->val index=(n-1).coerceAtLeast(0);if(index>=args.size)null else args[index.toInt()]};buildString{for(i in 0 until maxOf(pieces.size,values.size)){append(pieces.getOrElse(i){""});append(values.getOrElse(i){""})}}}
 }}}.attempt()
}
