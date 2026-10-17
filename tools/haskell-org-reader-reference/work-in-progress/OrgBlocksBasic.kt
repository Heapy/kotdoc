// Text.Pandoc.Readers.Org.Blocks, Copyright (C) 2014-2024 Albert Krewinkel.
// GPL-2.0-or-later; original source and notices retained in upstream/pandoc.
package io.heapy.kotdoc.readers.org
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.ast.Builder as B
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.latex.LaTeXReaderText as Text
import io.heapy.kotdoc.latex.LaTeXMathReader
internal data class OrgBlockAttributes(val name:String?,val caption:OF<Inlines>?,val keyValues:List<Pair<String,String>>){
 fun attr():Attr {fun value(k:String)=keyValues.firstOrNull{it.first==k}?.second
 val classes=(value("class")?:"").codePoints().toArray().fold(mutableListOf(StringBuilder())){words,c->if(ParserCharacters.isSpace(c)){if(words.last().isNotEmpty())words.add(StringBuilder())}else words.last().appendCodePoint(c);words}.map{it.toString()}.filter(String::isNotEmpty)
 return Attr(value("id")?:"",classes,keyValues.filter{it.first!in listOf("id","class")})}
}
internal fun OrgGrammar.stringyMetaAttribute():OP<Pair<String,String>> = metaLineStart().then(not(anyCase("begin").then(oneOf(":_")))).then(till1(nonspace(),char(':'))).map{Text.toLower(chars(it))}.before(skipSpaces()).flatMap{name->(anyLine() or newline().map{""}).map{name to it}}.attempt()
internal fun OrgGrammar.keyValues():OP<List<Pair<String,String>>> {
 val key=skipSpaces().then(char(':')).then(take1{it !in intArrayOf(32,9,10,13)}).attempt()
 val end=sp().many1().before(key).attempt().lookAhead().map{Unit} or skipSpaces().before(newline().lookAhead()).attempt()
 return till(key.flatMap{k->skipSpaces().then(till(any(),end)).map{k to chars(it)}},newline()).attempt()
}
internal fun OrgGrammar.blockAttributes():OP<OrgBlockAttributes> = stringyMetaAttribute().many().flatMap{pairs->guard(pairs.all{it.first in listOf("name","label","caption","attr_html","attr_latex","results")}).then(pure(Unit)).flatMap{
 fun combined(key:String)=pairs.filter{it.first==key}.map{it.second}.takeIf{it.isNotEmpty()}?.joinToString(" ")
 val caption=combined("caption")
 val parsed=if(caption==null)pure<OF<Inlines>?>(null)else parseFromString(orgInlines(),caption+"\n").map<OF<Inlines>?>{it}
 parsed.flatMap{cap->parseFromString(keyValues(),(combined("attr_html")?:"")+"\n").map{values->OrgBlockAttributes(pairs.lastOrNull{it.first=="name"||it.first=="label"}?.second,cap,values)}}
 }}.attempt()
internal fun OrgGrammar.endOfParagraph():OP<Unit> = skipSpaces().then(newline()).then(endOfBlock()).attempt()
internal fun OrgGrammar.figure():OP<OF<Blocks>> = blockAttributes().flatMap{attrs->skipSpaces().then(char('[')).then(linkTarget()).before(char(']')).before(skipSpaces()).before(endOfParagraph()).flatMap{source->val target=OrgShared.cleanLinkText(source)
 if(target==null||!OrgShared.isImageFilename(target))zero()else {val caption=attrs.caption?:future(Inlines());val attr=Attr(attrs.name?:"",emptyList(),attrs.keyValues)
 pure(caption.mapValue{if(attrs.caption!=null)B.figureWith(attr,Caption(null,B.plain(it).values),B.plain(B.image(target,"",Inlines())))else B.para(B.imageWith(attr,target,attrs.name?:"",it))})}
 }}.attempt()
internal fun OrgGrammar.example():OP<OF<Blocks>> = exampleLineStart().then(anyLine()).attempt().many1().map{future(B.codeBlock(it.joinToString("\n",postfix="\n")))}.attempt()
internal fun OrgGrammar.genericDrawer():OP<OF<Blocks>> = drawerStart().map(Text::toUpper).flatMap{name->till(anyLine(),skipSpaces().then(anyCase(":END:")).before(skipSpaces()).before(newline()).attempt()).flatMap{lines->state().flatMap{s->val(exclude,names)=s.user.export.drawers
 if(name=="PROPERTIES"||(exclude&&name in names)||(!exclude&&name !in names))pure(future(Blocks()))else parseFromString(delay{blocks()},lines.joinToString("\n",postfix="\n\n")).map{it.mapValue{B.divWith(Attr(classes=listOf(name,"drawer")),it)}}
 }}}.attempt()
internal fun OrgGrammar.rawExportLine():OP<Blocks> = metaLineStart().then(metaKey()).flatMap{key->guard(key in listOf("latex","html","texinfo","beamer")).then(anyLine()).map{B.rawBlock(key,it)}}.attempt()
internal fun OrgGrammar.rawOrgLine():OP<OF<Blocks>> = metaLineStart().then(anyLine()).map{future(B.rawBlock("org","#+$it"))}
internal fun OrgGrammar.commentLine():OP<Blocks> = commentLineStart().then(anyLine()).map{Blocks()}
internal fun OrgGrammar.specialLine():OP<OF<Blocks>> = (rawExportLine() or skipSpaces().before(text("#+print_bibliography:")).before(anyLine()).map{B.divWith(Attr(identifier="refs"),Blocks())}.attempt() or metaLine() or commentLine()).attempt().map(::future)
internal fun OrgGrammar.horizontalRule():OP<OF<Blocks>> = hline().attempt().map{future(B.horizontalRule)}
internal fun OrgGrammar.latexFragment():OP<OF<Blocks>> = latexEnvStart().flatMap{name->guard(name !in LaTeXMathReader().inlineEnvironmentNames).then(state()).flatMap{s->till(any(),text("\\end{$name}").before(blankline()).attempt()).map{body->val raw="\\begin{$name}"+chars(body)+"\\end{$name}";future(when(s.user.export.withLatex){TeXExport.Export->B.rawBlock("latex",raw+"\n");TeXExport.Ignore->Blocks();TeXExport.Verbatim->B.para(B.text(raw))})}}}.attempt()
internal fun OrgGrammar.noteBlock():OP<OF<Blocks>> = noteMarker().before(skipSpaces()).before(updatePre()).flatMap{ref->
 val end=eof() or noteMarker().lookAhead().map{Unit} or headerStart().lookAhead().map{Unit} or blankline().then(blankline()).attempt().lookAhead().map{Unit}
 till1(delay{block()},end).map(::blocks).flatMap{content->addToNotesTable(ref,content).map{future(Blocks())}}
}.attempt()
internal fun OrgGrammar.paraOrPlain():OP<OF<Blocks>> = not(headerStart()).then(orgInlines()).flatMap{ils->newline().map{true}.option(false).flatMap{nl->
 val following=state().flatMap{s->guard(s.user.listContext)}.then(orderedListStart().map{Unit} or bulletListStart().map{Unit})
 guard(nl).then(not(following)).map{ils.mapValue(B::para)}.attempt() or pure(ils.mapValue(B::plain))
}}.attempt()
