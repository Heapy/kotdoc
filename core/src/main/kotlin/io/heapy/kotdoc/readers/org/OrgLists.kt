// Text.Pandoc.Readers.Org.Blocks and Shared compactify closure.
// Copyright (C) 2014-2024 Albert Krewinkel; (C) 2006-2024 John MacFarlane.
// GPL-2.0-or-later; original source and notices retained in upstream/pandoc.
package io.heapy.kotdoc.readers.org
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.ast.Builder as B
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.options.Extension
internal fun OrgGrammar.countChars(n:Long,p:OP<Int>):OP<String>{fun loop(left:Long):OP<OrgGrammar.Chain<Int>?> = if(left<=0)pure(null)else p.flatMap{c->delay{loop(left-1)}.map{OrgGrammar.Chain(c,it)}}
 return delay{loop(n)}.map{chars(list(it))}}
internal fun OrgGrammar.indentWith(n:Long):OP<String> = state().flatMap{s->val spaces=countChars(n,char(' '));if(n<s.user.options.tabStop)spaces else spaces.attempt() or char('\t').then(delay{indentWith(n-s.user.options.tabStop)}).attempt()}
internal fun compactOrgLists(items:List<Blocks>):List<Blocks> {
 if(items.isEmpty())return items
 val others=items.dropLast(1);val final=items.last().values;val last=final.lastOrNull()
 return if(last is Block.Para&&final.dropLast(1).none{it is Block.Para}&&others.none{b->b.values.any{it is Block.Para}})others+listOf(Blocks(final.dropLast(1)+Block.Plain(last.content)))
 else if(items.none{b->b.values.any{it is Block.Para}})items else items.map{b->Blocks(b.values.map{if(it is Block.Plain)Block.Para(it.content)else it})}
}
internal fun compactOrgDefinitions(items:List<Pair<Inlines,List<Blocks>>>):List<Pair<Inlines,List<Blocks>>> {
 val (term,defs)=items.lastOrNull()?:return items;val last=defs.lastOrNull()?.values?:return items;val para=last.lastOrNull()as?Block.Para?:return items
 return if(last.dropLast(1).any{it is Block.Para})items else items.dropLast(1)+(term to (defs.dropLast(1)+listOf(Blocks(last.dropLast(1)+Block.Plain(para.content)))))
}
internal fun OrgGrammar.listContinuation(n:Long):OP<String> {
 val line=indentWith(n).then(anyLineNewline()).attempt()
 val block=indentWith(n).then(blockAttributes()).flatMap{attrs->if(attrs.attr()==Attr())countChars(1,any())else indentWith(n)}.lookAhead().then(withRaw(delay{orgBlock()}).map{it.second}).attempt()
 return not(blankline()).then((block or line).many1()).flatMap{parts->blankline().many().map{parts.joinToString("")+chars(it)}}.attempt()
}
internal fun prependOrgInlines(box:Inline,content:Blocks):Blocks {
 val first=content.values.firstOrNull();val prefix=listOf(box,Inline.Space)
 return Blocks(when(first){is Block.Plain->listOf(Block.Plain(prefix+first.content))+content.values.drop(1);is Block.Para->listOf(Block.Para(prefix+first.content))+content.values.drop(1);else->listOf(Block.Plain(prefix))+content.values})
}
internal fun OrgGrammar.checkbox():OP<Inline> = state().flatMap{s->guard(Extension.read("task_lists") in s.user.options.extensions)}.then(char('[').then(oneOf(" X-")).before(char(']')).map<Inline>{Inline.Str(if(it==88)"☒"else"☐")}.attempt().label("checkbox"))
internal fun OrgGrammar.listItem(marker:OP<Long>):OP<OF<Blocks>> = context(true,marker.attempt().flatMap{n->maybe(checkbox()).flatMap{box->anyLineNewline().flatMap{first->blankline().map{"\n"}.option("").flatMap{blank->listContinuation(n).many().flatMap{rest->
 parseFromString((paraOrPlain() or pure(future(Blocks()))).flatMap{initial->blocks().map{f2(initial,it,Blocks::plus)}},first+blank+rest.joinToString("")).map{content->if(box==null)content else content.mapValue{prependOrgInlines(box,it)}}
 }}}}}).attempt()
internal fun OrgGrammar.definitionListItem(marker:OP<Long>):OP<OF<Pair<Inlines,List<Blocks>>>> = marker.flatMap{n->
 val separator=sp().then(text("::")).before(sp() or newline().lookAhead()).attempt()
 till(noneOf("\n\r"),separator).map(::chars).flatMap{term->anyLineNewline().flatMap{first->blankline().map{"\n"}.option("").flatMap{blank->listContinuation(n).many().flatMap{rest->parseFromString(orgInlines(),term).flatMap{label->parseFromString(delay{blocks()},first+blank+rest.joinToString("")).map{contents->f2(label,contents){t,b->t to listOf(b)}}}}}}
}}.attempt()
internal fun OrgGrammar.indented(marker:OP<Long>,n:Long):OP<Long> = marker.flatMap{i->guard(n<=i).map{i}}.attempt()
internal fun OrgGrammar.orgDefinitionList():OP<OF<Blocks>> = bulletListStart().lookAhead().flatMap{indent->definitionListItem(indented(bulletListStart(),indent)).many1().map{sequence(it).mapValue{B.definitionList(compactOrgDefinitions(it))}}}.attempt()
internal fun OrgGrammar.orgBulletList():OP<OF<Blocks>> = bulletListStart().lookAhead().flatMap{indent->listItem(indented(bulletListStart(),indent)).many1().map{sequence(it).mapValue{B.bulletList(compactOrgLists(it))}}}.attempt()
internal fun OrgGrammar.orgOrderedList():OP<OF<Blocks>> = orderedListStart().lookAhead().flatMap{(indent,attributes)->listItem(indented(orderedListStart().map{it.first},indent)).many1().map{sequence(it).mapValue{B.orderedListWith(attributes,compactOrgLists(it))}}}.attempt()
internal fun OrgGrammar.orgList():OP<OF<Blocks>> = (orgDefinitionList() or orgBulletList() or orgOrderedList()).label("list")
