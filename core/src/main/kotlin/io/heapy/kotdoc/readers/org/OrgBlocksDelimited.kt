// Text.Pandoc.Readers.Org.Blocks, Copyright (C) 2014-2024 Albert Krewinkel.
// GPL-2.0-or-later; original source and notices retained in upstream/pandoc.
package io.heapy.kotdoc.readers.org
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.ast.Builder as B
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.latex.LaTeXReaderText as Text
internal fun OrgGrammar.ignHeaders():OP<Unit> = newline().map{Unit} or anyLine().map{Unit}
internal fun OrgGrammar.rawBlockContent(type:String)=rawBlockContent(anyCase("#+end_$type"))
internal fun OrgGrammar.rawBlockContent(end:OP<String>):OP<String> = till(blankline().map{""}.attempt() or anyLine(),skipSpaces().before(end).attempt()).flatMap{lines->state().flatMap{s->
 val split=lines.map{line->val n=line.indexOfFirst{it!=' '&&it!='\t'}.let{if(it<0)line.length else it};line.take(n)to line.drop(n)}
 val tab=s.user.options.tabStop
 fun spaces(value:String)=value.fold(0L){n,c->n+if(c=='\t')tab else 1L}
 val shortest=split.filter{it.second.isNotEmpty()}.minOfOrNull{spaces(it.first)}?:Long.MAX_VALUE
 fun reindent(value:String)=if(!s.user.trimLeadBlockIndent)value else buildString{for(c in value)if(c=='\t'){var n=tab;while(n>0){append(' ');n--}}else append(c)}.let{if(shortest>=it.length)""else it.drop(shortest.coerceAtLeast(0).toInt())}
 fun comma(value:String)=if(value.startsWith(',')&&value.drop(1).dropWhile{it==','}.let{it.startsWith('*')||it.startsWith("#+")})value.drop(1)else value
 val result=split.joinToString("\n",postfix=if(split.isEmpty())""else"\n"){(indent,body)->reindent(indent)+comma(body)}
 update{it.copy(trimLeadBlockIndent=true)}.map{result}
}}.attempt()
internal fun OrgGrammar.orgParamValue():OP<String> {
 val end=(skipSpaces().before(oneOf("\n\r")).attempt() or skipSpaces1().before(argKey()).attempt()).lookAhead()
 return skipSpaces().then(not(argKey())).then(char('"').then(take{it !in intArrayOf(10,13,34)}).before(char('"')) or till1(noneOf("\n\r"),end).map(::chars)).before(skipSpaces()).attempt()
}
internal fun OrgGrammar.blockOption():OP<Pair<String,String>> = argKey().flatMap{k->orgParamValue().option("yes").map{k to it}}.attempt()
internal fun OrgGrammar.blockParameters():OP<List<Pair<String,String>>> = till(argKey().flatMap{k->orgParamValue().map{k to it}},newline()).attempt() or till(space().skipMany().then(argWord()).before(char('=')).flatMap{k->argWord().map{k to it}},newline()).attempt() or skipSpaces().then(anyLine()).map{if(it.isEmpty())emptyList()else listOf("parameters" to it)}
internal data class OrgSwitch(val char:Int,val argument:String?,val plus:Boolean)
internal fun OrgGrammar.orgSwitch():OP<OrgSwitch> {
 val polarity=char('-').map{false} or char('+').map{true}
 fun generic(c:Char,p:OP<String>)=polarity.before(char(c)).before(skipSpaces()).flatMap{plus->maybe(p).map{OrgSwitch(c.code,it,plus)}}.attempt()
 val number=generic('n',take(ParserCharacters::isDigit))
 val label=generic('l',char('"').then(till1(nonspace(),char('"'))).map(::chars))
 val whitespace=text("-i").then(update{it.copy(trimLeadBlockIndent=false)}).map{OrgSwitch(105,null,false)}
 val simple=polarity.flatMap{plus->satisfy(ParserCharacters::isAlpha).map{OrgSwitch(it,null,plus)}}
 return (number or label or whitespace or simple).attempt()
}
internal fun OrgGrammar.switchesAsAttributes():OP<Pair<List<String>,List<Pair<String,String>>>> = skipSpaces().then(orgSwitch().flatMap{first->sp().many1().then(orgSwitch()).many().map{listOf(first)+it}}.option(emptyList()).attempt()).map{switches->
 switches.asReversed().fold(emptyList<String>() to emptyList<Pair<String,String>>()){(classes,kv),sw->if(sw.char!=110)classes to kv else (listOf("numberLines")+(if(sw.plus)listOf("continuedSourceBlock")else emptyList())+classes)to((sw.argument?.let{listOf("startFrom" to it)}?:emptyList())+kv)}
}.attempt()
internal fun OrgGrammar.codeHeaderArgs():OP<Pair<List<String>,List<Pair<String,String>>>> = skipSpaces().then(argWord()).flatMap{language->switchesAsAttributes().flatMap{(classes,kv)->till(blockOption(),newline()).map{parameters->(listOf(OrgShared.translateLang(language))+classes)to(OrgShared.originalLang(language)+kv+parameters)}}}.attempt()
internal fun OrgGrammar.babelResultsBlock():OP<OF<Blocks>> {
 val marker=anyCase("#+RESULTS:").before(blankline()).attempt().map{Unit}
 return blanklines().then(marker or till(metaLineStart().then(anyLineNewline()),marker).lookAhead().map{Unit}).then(delay{block()}).attempt()
}
internal fun OrgGrammar.codeBlock(attrs:OrgBlockAttributes,type:String):OP<OF<Blocks>> = skipSpaces().then(codeHeaderArgs() or ignHeaders().map{emptyList<String>()to emptyList<Pair<String,String>>()}).flatMap{(classes,kv)->rawBlockContent(type).flatMap{content->babelResultsBlock().option(future(Blocks())).map{results->
 val actualClasses=if(classes.firstOrNull()?.startsWith("jupyter-")==true)listOf(classes.first().drop(8),"code")+classes.drop(1)else classes
 val code=B.codeBlockWith(Attr(attrs.name?:"",actualClasses,kv),content)
 val wrapped=attrs.caption?.mapValue{caption->B.divWith(Attr(classes=listOf("captioned-content")),B.divWith(Attr(classes=listOf("caption")),B.plain(caption))+code)}?:future(code)
 f2(if(OrgShared.exportsCode(kv))wrapped else future(Blocks()),if(kv.firstOrNull{it.first=="exports"}?.second in listOf("results","both"))results else future(Blocks()),Blocks::plus)
}}}
internal fun OrgGrammar.orgBlock():OP<OF<Blocks>> = blockAttributes().flatMap{attrs->skipSpaces().then(metaLineStart()).then(anyCase("begin_")).then(take1{!ParserCharacters.isSpace(it)}).flatMap{type->
 fun parsed()=rawBlockContent(type).flatMap{parseFromString(delay{blocks()},it+"\n")}
 fun raw(f:(String)->OF<Blocks>)=ignHeaders().then(rawBlockContent(type)).map(f)
 when(Text.toLower(type)){
 "export"->skipSpaces().then(argWord()).before(ignHeaders()).flatMap{format->rawBlockContent(type).map{future(B.rawBlock(Text.toLower(format),it))}}.attempt()
 "comment"->raw{future(Blocks())}
 "html","latex","ascii"->raw{future(B.rawBlock(Text.toLower(type),it))}
 "example"->skipSpaces().then(switchesAsAttributes()).before(newline()).flatMap{(classes,kv)->rawBlockContent("example").map{future(B.codeBlockWith(Attr(attrs.name?:"",classes,kv),it))}}
 "quote"->ignHeaders().then(parsed()).map{it.mapValue(B::blockQuote)}
 "verse"->ignHeaders().then(rawBlockContent(type)).flatMap{raw->val lines=raw.split('\n').let{if(it.lastOrNull()=="")it.dropLast(1)else it};sequenceParsers(lines.map{line->val cps=line.codePoints().toArray();val n=cps.takeWhile(ParserCharacters::isSpace).size;val rest=chars(cps.drop(n));parseFromString(orgInlines(),rest+"\n").map{it.mapValue{((if(n==0)Inlines()else B.str("\u00a0".repeat(n)))+it).trim()}}}).map{sequence(it).mapValue(B::lineBlock)}}.attempt()
 "src"->codeBlock(attrs,type)
 "note","warning","tip","caution","important"->ignHeaders().then(parsed()).map{it.mapValue{body->val title=when(Text.toLower(type)){"note"->"Note";"warning"->"Warning";"tip"->"Tip";"caution"->"Caution";else->"Important"};B.divWith(Attr(attrs.name?:"",listOf(Text.toLower(type))),B.divWith(Attr(classes=listOf("title")),B.para(B.str(title)))+body)}}
 else->if(type=="abstract")ignHeaders().then(parsed()).flatMap{content->update{s->s.copy(meta=f2(content,s.meta){b,m->m.inserted(type){MetaValue.MetaBlocks(b.values)}})}.map{future(Blocks())}}.attempt()else blockParameters().flatMap{parameters->parsed().map{it.mapValue{body->val attr=attrs.attr();B.divWith(attr.copy(classes=attr.classes+type,keyValues=attr.keyValues+parameters),body)}}}
 }
}}.attempt()
internal fun OrgGrammar.dynamicBlock():OP<OF<Blocks>> = metaLineStart().then(anyCase("begin:")).then(space().skipMany()).then(maybe(argWord())).flatMap{name->blockParameters().flatMap{parameters->rawBlockContent(metaLineStart().then(anyCase("end:"))).flatMap{raw->parseFromString(delay{blocks()},raw+"\n").map{it.mapValue{B.divWith(Attr(classes=listOfNotNull(name),keyValues=parameters),it)}}}}}.attempt()
