// Text.Pandoc.Readers.Org.DocumentTree, ©2014-2024 Albert Krewinkel.
// GPL-2.0-or-later; original source/notices retained in upstream/pandoc.
package io.heapy.kotdoc.readers.org
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.ast.Builder as B
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.latex.LaTeXReaderText as Text
import io.heapy.kotdoc.options.Extension
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.readers.DokuWikiIdentifiers
internal data class OrgPlanning(val closed:String?=null,val deadline:String?=null,val scheduled:String?=null)
internal data class OrgHeadline(val level:Long,val todo:TodoMarker?,val text:Deferred<Inlines>,val tags:List<String>,val planning:OrgPlanning,val properties:List<Pair<String,String>>,val contents:Deferred<Blocks>,val children:Deferred<List<OrgHeadline>>)
internal fun OrgGrammar.documentTree():OP<OF<OrgHeadline>> = commentLineStart().before(anyLine()).many().then(propertiesDrawer().option(emptyList())).flatMap{properties->blocks().flatMap{initial->till(headline(1),eof()).flatMap{heads->state().map{st->
 val title=st.user.meta.mapValue{m->Inlines(docTitle(m))}
 Future{final->Deferred.now(OrgHeadline(0,null,title.runDelayed(final),emptyList(),OrgPlanning(),properties,initial.runDelayed(final),sequence(heads).runDelayed(final)))}
}}}}
internal fun OrgGrammar.headline(minimum:Long):OP<OF<OrgHeadline>> = headerStart().flatMap{level->guard(level>=minimum).then(maybe(todoKeyword())).flatMap{todo->
 val tags=skipSpaces().then((char(':').then(tagWord().before(char(':')).many1()).before(skipSpaces())).option(emptyList())).before(newline()).attempt()
 until(delay{inline()},tags).flatMap{(title,ts)->planningInfo().option(OrgPlanning()).flatMap{planning->propertiesDrawer().option(emptyList()).flatMap{properties->blocks().flatMap{contents->delay{headline(level+1)}.many().map{children->
  val text=inlines(title).mapValue(Inlines::trim)
  Future{final->Deferred.now(OrgHeadline(level,todo,text.runDelayed(final),ts,planning,properties,contents.runDelayed(final),sequence(children).runDelayed(final)))}
 }}}}}
}}.attempt()
internal fun OrgGrammar.todoKeyword():OP<TodoMarker> = state().flatMap{s->choice(s.user.activeTodos().map{t->text(t.name).before(sp()).before(updatePre()).map{t}.attempt()})}.attempt()
internal fun OrgGrammar.propertiesDrawer():OP<List<Pair<String,String>>> {
 val end=skipSpaces().then(anyCase(":END:")).before(skipSpaces()).before(newline()).attempt()
 val key=skipSpaces().then(char(':')).then(till1(nonspace(),char(':').before(sp()).attempt())).map{Text.toLower(chars(it))}.attempt()
 val value=skipSpaces().then(till(any(),skipSpaces().then(newline()).attempt())).map(::chars).attempt()
 return drawerStart().flatMap{name->guard(Text.toUpper(name)=="PROPERTIES")}.then(till(key.flatMap{k->value.map{k to it}}.attempt(),end)).attempt()
}
internal fun OrgGrammar.planningInfo():OP<OrgPlanning>{
 val timestamp=oneOf("<[").flatMap{c->till1(any(),char(if(c==60)'>'else']')).map{chars(listOf(c)+it+if(c==60)62 else 93)}}.attempt()
 fun datum(key:String,f:(OrgPlanning,String)->OrgPlanning)=text(key).before(char(':')).before(skipSpaces()).then(timestamp).map{v->{p:OrgPlanning->f(p,v)}}
 val one=skipSpaces().then(choice(listOf(datum("SCHEDULED"){p,v->p.copy(scheduled=v)},datum("DEADLINE"){p,v->p.copy(deadline=v)},datum("CLOSED"){p,v->p.copy(closed=v)})))
 return one.many1().before(skipSpaces()).before(newline()).map{fs->fs.asReversed().fold(OrgPlanning()){p,f->f(p)}}.attempt()
}
private fun OrgHeadline.hasSelect(s:OrgState)=tags.any{it in s.selectTags}
private fun containsSelect(root:OrgHeadline,s:OrgState):Boolean{
 val stack=java.util.ArrayDeque<Iterator<OrgHeadline>>();var node=root
 while(true){if(node.hasSelect(s))return true;stack.addLast(node.children.value().iterator());var next:OrgHeadline?=null;while(stack.isNotEmpty()){val it=stack.peekLast();if(it.hasNext()){next=it.next();break}else stack.removeLast()};node=next?:return false}
}
private fun includeSelected(root:OrgHeadline,s:OrgState):OrgHeadline?{
 val go=DeepRecursiveFunction<OrgHeadline,OrgHeadline?>{h->if(h.hasSelect(s))h else{val children=buildList{for(c in h.children.value()){val v=callRecursive(c);if(v!=null)add(v)}};if(children.isEmpty())null else h.copy(children=Deferred.now(children))}}
 return go(root)
}
private fun removeExcluded(root:OrgHeadline,s:OrgState):OrgHeadline{
 val go=DeepRecursiveFunction<OrgHeadline,OrgHeadline>{h->val cs=buildList{for(c in h.children.value())if(c.tags.none{it in s.excludeTags})add(callRecursive(c))};h.copy(children=Deferred.now(cs))};return go(root)
}
internal fun OrgGrammar.unprunedHeadlineToBlocks(root:OrgHeadline,snapshot:OrgState):OP<List<Block>>{
 val selected=containsSelect(root,snapshot)
 val included=if(!selected)root else root.copy(children=Deferred.now(root.children.value().mapNotNull{includeSelected(it,snapshot)}))
 val pruned=removeExcluded(included,snapshot)
 return if(!selected||pruned.hasSelect(snapshot))headlineToBlocks(pruned).before(update{s->s.copy(meta=s.meta.mapValue{m->pruned.properties.asReversed().fold(m){out,(k,v)->out.inserted(k){MetaValue.MetaString(v)}}})}).map{it.values.drop(1)}
 else sequenceParsers(pruned.children.value().map{delay{headlineToBlocks(it)}}).map{it.flatMap(Blocks::values)}
}
internal fun <A> OrgGrammar.sequenceParsers(xs:List<OP<A>>):OP<List<A>>{
 fun loop(i:Int):OP<OrgGrammar.Chain<A>?> = if(i==xs.size)pure(null)else xs[i].flatMap{v->delay{loop(i+1)}.map{OrgGrammar.Chain(v,it)}}
 return delay{loop(0)}.map(::list)
}
internal fun OrgGrammar.headlineToBlocks(h:OrgHeadline):OP<Blocks> = state().flatMap{s->
 when{
  "ARCHIVE" in h.tags->when(s.user.export.archivedTrees){ArchivedTrees.NoExport->pure(Blocks());ArchivedTrees.Export->headlineWithContents(h);ArchivedTrees.HeadlineOnly->headlineToHeader(h)}
  (h.text.value().values.firstOrNull()as?Inline.Str)?.text=="COMMENT"->pure(Blocks())
  s.user.export.headlineLevels<=h.level->headlineToHeader(h).flatMap{header->sequenceParsers(h.children.value().map{delay{headlineToBlocks(it)}}).flatMap{elements->planningToBlock(h.planning).map{planning->
   val title=if(s.user.export.headlineLevels==h.level)header else (header.values.firstOrNull()as?Block.Header)?.let{B.para(Inlines(it.content))}?:Blocks()
   title+planning+h.contents.value()+if(elements.isEmpty())Blocks()else orgHeadlineOrderedList(elements)
  }}}
  else->headlineWithContents(h)
 }
}
private fun OrgGrammar.headlineWithContents(h:OrgHeadline)=headlineToHeader(h).flatMap{header->planningToBlock(h.planning).flatMap{planning->sequenceParsers(h.children.value().map{delay{headlineToBlocks(it)}}).map{children->children.fold(header+planning+h.contents.value(),Blocks::plus)}}}
private fun sourceWords(value:String):List<String>{val result=mutableListOf<String>();val word=StringBuilder();for(c in value.codePoints().toArray())if(ParserCharacters.isSpace(c)){if(word.isNotEmpty()){result+=word.toString();word.setLength(0)}}else word.appendCodePoint(c);if(word.isNotEmpty())result+=word.toString();return result}
private fun propertiesAttr(ps:List<Pair<String,String>>):Attr{
 fun value(k:String)=ps.firstOrNull{it.first==k}?.second
 val id=value("custom_id")?:value("id")?:"";val classes=sourceWords(value("class")?:"")+if(value("unnumbered")?.let{Text.toLower(it)!in listOf("()","{}","nil")}==true)listOf("unnumbered")else emptyList()
 return Attr(id,classes,ps.filter{it.first !in listOf("custom_id","id","class","unnumbered")})
}
internal fun OrgGrammar.headlineToHeader(h:OrgHeadline):OP<Blocks> = state().flatMap{s->
 val todo=if(s.user.export.withTodoKeywords)h.todo?.let{B.spanWith(Attr(classes=listOf(if(it.done)"done"else"todo",it.name)),B.str(it.name))+B.space}?:Inlines()else Inlines()
 val tags=if(s.user.export.withTags&&h.tags.isNotEmpty())B.space+h.tags.map{B.spanWith(Attr(classes=listOf("tag"),keyValues=listOf("tag-name" to it)),B.smallcaps(B.str(it)))}.reduce{a,b->a+B.str("\u00a0")+b}else Inlines()
 val title=h.text.value();registerHeader(propertiesAttr(h.properties),title).map{B.headerWith(it,h.level,todo+title+tags)}
}
internal fun OrgGrammar.registerHeader(attr:Attr,title:Inlines):OP<Attr> = state().flatMap{s->
 if(attr.identifier.isEmpty()&&Extension.read("auto_identifiers")in s.user.options.extensions){val id=DokuWikiIdentifiers.unique(title.values,s.user.identifiers,s.user.options.extensions);val ascii=if(Extension.read("ascii_identifiers")in s.user.options.extensions)Asciify.toAsciiText(id)else id;update{it.copy(identifiers=it.identifiers+id+ascii)}.map{attr.copy(identifier=ascii)}}
 else update{it.copy(identifiers=if(attr.identifier.isEmpty())it.identifiers else it.identifiers+attr.identifier,messages=if(attr.identifier.isNotEmpty()&&attr.identifier in it.identifiers)it.messages+LogMessage.DuplicateIdentifier(attr.identifier,s.position)else it.messages)}.map{attr}
}
internal fun OrgGrammar.planningToBlock(p:OrgPlanning):OP<Blocks> = state().map{s->if(!s.user.export.withPlanning)Blocks()else{
 val values=listOf("CLOSED" to p.closed,"DEADLINE" to p.deadline,"SCHEDULED" to p.scheduled).mapNotNull{(k,v)->v?.let{B.strong(B.str(k+":"))+B.space+B.emph(B.str(it))}};B.plain(values.reduceOrNull{a,b->a+B.space+b}?:Inlines())
}}

// Builder.hs473–478: singleton OrderedList/default attrs/map toList. These are
// finite parser-produced Blocks; retaining each values list preserves its heads
// and fields without repeated Generic AST encode/decode wrappers per depth.
private fun orgHeadlineOrderedList(elements:List<Blocks>):Blocks =
 Blocks(listOf(Block.OrderedList(ListAttributes(),elements.map(Blocks::values))))
