// Text.Pandoc.Readers.RST, Pandoc 3.12.1.
// Copyright (C) 2006-2024 John MacFarlane; GPL-2.0-or-later.
// Original source and full license retained in upstream/pandoc and licenses/.
package io.heapy.kotdoc.readers

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.ast.Builder as B
import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.latex.LaTeXReaderText
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.state.*
import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.uri.PandocURI
import io.heapy.kotdoc.xml.PandocXml

private typealias RP<A> = Parsec<RstState,A>
private data class RstTarget(val url:String,val title:String="",val attr:Attr=Attr())
private data class RstRole(val base:String,val format:String?=null,val attr:Attr=Attr())
private data class RstState(
 val options:ReaderOptions,val meta:Map<String,MetaValue> = emptyMap(),val identifiers:Set<String> = emptySet(),
 val headerTable:List<Pair<Boolean,Int>> = emptyList(),val keys:Map<Key,RstTarget> = emptyMap(),
 val substitutions:Map<Key,Blocks> = emptyMap(),val notes:List<Pair<String,String>> = emptyList(),val citations:Map<String,String> = emptyMap(),
 val defaultRole:String="title-reference",val highlight:String?=null,val customRoles:Map<String,RstRole> = emptyMap(),
 val includeFiles:List<String> = emptyList(),val messages:List<LogMessage> = emptyList(),val lastStr:SourcePos?=null,
 val quote:QuoteContext=QuoteContext.NoQuote,val listContext:Boolean=false,val nextExample:Long=1,val examples:Map<String,Long> = emptyMap(),
)
private object RstAccess:ReaderOptionsAccess<RstState>,LastStrPositionAccess<RstState>,ExampleNumberState<RstState>{
 override fun extractReaderOptions(state:RstState)=state.options
 override fun setLastStrPos(position:SourcePos?,state:RstState)=state.copy(lastStr=position)
 override fun getLastStrPos(state:RstState)=state.lastStr
 override fun nextExample(state:RstState)=state.nextExample
 override fun examples(state:RstState)=state.examples
 override fun update(state:RstState,nextExample:Long,examples:Map<String,Long>)=state.copy(nextExample=nextExample,examples=examples)
}
private class RstWalkTag
private class RstWalkValue<A>(val parser:RP<A>):WalkEffect<RstWalkTag,A>
private object RstWalkMonad:WalkMonad<RstWalkTag>{
 override fun <A> pure(value:A):WalkEffect<RstWalkTag,A> = RstWalkValue(Parsec.pure(value))
 override fun <A,B> flatMap(value:WalkEffect<RstWalkTag,A>,next:(A)->WalkEffect<RstWalkTag,B>):WalkEffect<RstWalkTag,B> = RstWalkValue((value as RstWalkValue<A>).parser.flatMap{(next(it)as RstWalkValue<B>).parser})
 override fun <S,A> tailRecM(initial:S,step:(S)->WalkEffect<RstWalkTag,WalkLoop<S,A>>):WalkEffect<RstWalkTag,A>{
  fun loop(s:S):RP<A> = Parsec.defer{(step(s)as RstWalkValue<WalkLoop<S,A>>).parser.flatMap{when(it){is WalkLoop.Continue->loop(it.state);is WalkLoop.Done->Parsec.pure(it.value)}}}
  return RstWalkValue(loop(initial))
 }
}
/** Effects used by include/raw/CSV/date directives are explicit and testable. */
interface RstReaderEffects {
 fun readFile(path:String):ByteArray
 fun fetchItem(url:String,runtime:PandocState):ByteArray=RstFetch.fetch(url,runtime,::readFile,::openURL)
 /** Pure-compatible default. IO callers supply the source HTTP backend explicitly. */
 fun openURL(url:String):ByteArray=throw PandocError.PandocResourceNotFound(url)
 fun getTimestamp():io.heapy.kotdoc.resources.PandocUtcTime
}

object RstReader {
 val pureEffects:RstReaderEffects=object:RstReaderEffects {
  override fun readFile(path:String):ByteArray=throw PandocError.PandocResourceNotFound(path)
  override fun fetchItem(url:String,runtime:PandocState):ByteArray=RstFetch.fetch(url,runtime,::readFile){throw PandocError.PandocResourceNotFound(it)}
  override fun getTimestamp()=RstDateFormat.epoch.utc
 }

 fun read(options:ReaderOptions,text:String,effects:RstReaderEffects=pureEffects,runtime:PandocState=PandocStateContext()):Pandoc=read(options,Sources.fromText(text),effects,runtime)
 fun read(options:ReaderOptions,sources:Sources,effects:RstReaderEffects=pureEffects,runtime:PandocState=PandocStateContext()):Pandoc {
  val input=sources.ensureFinalNewlines(2)
  val result=Grammar(runtime,effects).document().run(ParseState(ParseInput(input),SourcePos(input.initialSourceName()),RstState(options)))
  if(!result.success)throw io.heapy.kotdoc.latex.LaTeXReader.fromParsecError(input,result.error)
  return checkNotNull(result.value)
 }
 private class Grammar(val runtime:PandocState,val effects:RstReaderEffects){
  fun <A> pure(x:A):RP<A> = Parsec.pure(x)
  fun <A> zero():RP<A> = Parsec.zero()
  fun <A> delay(f:()->RP<A>):RP<A> = Parsec.defer(f)
  fun state()=Parsec.state<RstState>()
  fun update(f:(RstState)->RstState)=Parsec.modify<RstState>{it.copy(user=f(it.user))}
  fun guard(x:Boolean)=Parsec.guard<RstState>(x)
  fun char(c:Char)=SourceParsers.char<RstState>(c.code)
  fun text(s:String)=SourceParsers.string<RstState>(s)
  fun satisfy(f:(Int)->Boolean)=SourceParsers.satisfy<RstState>(f)
  fun any()=SourceParsers.anyChar<RstState>()
  fun take(f:(Int)->Boolean)=SourceParsers.takeWhileP<RstState>(f)
  fun take1(f:(Int)->Boolean)=SourceParsers.takeWhile1P<RstState>(f)
  fun newline()=SourceParsers.newline<RstState>()
  fun sp()=satisfy{it==32||it==9}
  fun eof()=Parsec.eof<RstState>()
  fun eol()=newline().map{Unit} or eof()
  fun blankline()=sp().skipMany().then(newline()).attempt()
  fun chars(xs:List<Int>)=buildString{xs.forEach(::appendCodePoint)}
  fun blanklines()=blankline().many1().map(::chars)
  fun anyLine()=take{it!=10}.before(newline())
  fun anyLineNewline()=anyLine().map{it+"\n"}
  fun trim(s:String)=s.trim{ParserCharacters.isSpace(it.code)}
  fun trimr(s:String)=s.trimEnd{ParserCharacters.isSpace(it.code)}
  fun words(s:String):List<String>{val out=mutableListOf<String>();val word=StringBuilder();for(cp in s.codePoints().toArray())if(ParserCharacters.isSpace(cp)){if(word.isNotEmpty()){out+=word.toString();word.setLength(0)}}else word.appendCodePoint(cp);if(word.isNotEmpty())out+=word.toString();return out}
  fun <A> count(n:Long,p:RP<A>):RP<List<A>>{class Link<A>(val x:A,val next:Link<A>?);fun loop(i:Long):RP<Link<A>?> = if(i<=0)pure(null)else p.flatMap{x->delay{loop(i-1)}.map{Link(x,it)}}
return delay{loop(n)}.map{link->buildList{var node=link;while(node!=null){add(node.x);node=node.next}}}}
  fun <A,C> till(p:RP<A>,end:RP<C>):RP<List<A>>{class Link<A>(val x:A,val next:Link<A>?);fun loop():RP<Link<A>?> = end.map<Link<A>?>{null} or p.flatMap{x->delay{loop()}.map{Link(x,it)}}
return delay{loop()}.map{link->buildList{var node=link;while(node!=null){add(node.x);node=node.next}}}}
  fun sourceShow(x:Any?):String=when(x){is Unit->"()";is Int->Parsec.showChar(x);is String->HaskellString.quote(x);is Inlines->"Many {unMany = fromList "+AstShow.show(x.values)+"}";is Blocks->"Many {unMany = fromList "+AstShow.show(x.values)+"}";is Pair<*,*>->"("+sourceShow(x.first)+","+sourceShow(x.second)+")";else->x.toString()}
  fun <A> not(p:RP<A>)=p.notFollowedBy(::sourceShow)
  fun <A> notPrime(p:RP<A>):RP<Unit> = (p.attempt().map{Parsec.unexpected<RstState,Unit>(sourceShow(it))} or pure(pure(Unit))).flatMap{it}.attempt()
  fun <A> parseFromString(p:RP<A>,raw:String):RP<A> = state().flatMap{old->Parsec.modify<RstState>{it.copy(input=ParseInput(Sources.fromText(raw)),position=SourcePos(old.position.name+"_chunk"),user=it.user.copy(lastStr=null))}.then(p).before(Parsec.modify{it.copy(input=old.input,position=old.position,user=it.user.copy(lastStr=old.user.lastStr))})}
  fun concat(xs:List<Inlines>)=xs.fold(Inlines(),Inlines::plus)
  fun blocks(xs:List<Blocks>)=xs.fold(Blocks(),Blocks::plus)
  fun log(message:LogMessage)=update{it.copy(messages=listOf(message)+it.messages)}
  fun <A,B> walk(dictionary:Walkable<A,B>,f:(A)->RP<A>,value:B):RP<B> = (dictionary.walkM(RstWalkMonad,{RstWalkValue(f(it))},value)as RstWalkValue<B>).parser
  fun <A,C> till1(p:RP<A>,end:RP<C>):RP<List<A>> = notPrime(end).then(p).flatMap{first->till(p,end).map{listOf(first)+it}}
  fun parseBlocks():RP<Blocks> = till(delay{block()},eof()).map(::blocks)
  fun block():RP<Blocks> = (codeBlock() or blockQuote() or fieldList() or optionList() or referenceKey() or noteBlock(false) or noteBlock(true) or directive() or anchor() or comment() or header() or hrule() or lineBlock() or table() or list() or lhsCodeBlock() or para() or blanklines().map{Blocks()}).label("block")
  fun rawFieldListItem(minIndent:Long):RP<Pair<String,String>> = char(' ').many().flatMap{spaces->guard(spaces.size>=minIndent).then(char(':')).then(take1{it!=10&&it!=58}).before(char(':')).flatMap{name->(newline().lookAhead().map{Unit} or sp().many1().map{Unit}).then(anyLine()).flatMap{first->count(spaces.size.toLong(),char(' ')).then(sp()).lookAhead().then(indentedBlock()).attempt().option("").map{rest->name to((if(first.isEmpty())""else first+"\n")+rest+if(first.isEmpty()&&rest.isEmpty())""else"\n")}}}}.attempt()
  fun fieldListItem(minIndent:Long):RP<Pair<Inlines,List<Blocks>>> = rawFieldListItem(minIndent).flatMap{(name,raw)->parseInlineFromText(name).flatMap{term->parseFromString(delay{parseBlocks()},raw).before(blanklines().optional()).map{body->term to listOf(if(body.values.size==1&&body.values[0]is Block.Para)B.plain(Inlines((body.values[0]as Block.Para).content))else body)}}}.attempt()
  fun fieldList():RP<Blocks> = sp().many().lookAhead().flatMap{spaces->fieldListItem(spaces.size.toLong()).many1().map(B::definitionList)}.attempt()
  fun optArg():RP<Unit> = (satisfy(ParserCharacters::isAlpha) or char('<')).flatMap{c->if(c==60)till(satisfy{it!=60&&it!=62},char('>')).map{Unit}else satisfy{ParserCharacters.isAlphaNum(it)||it==95||it==45}.skipMany()}
  fun shortOpt():RP<Unit> = char('-').then(satisfy(ParserCharacters::isAlphaNum)).then(char(' ').optional().then(optArg()).attempt().optional()).attempt()
  fun longOpt():RP<Unit> = text("--").then(satisfy(ParserCharacters::isAlphaNum)).then(satisfy{ParserCharacters.isAlphaNum(it)||it==45||it==95}.many1()).then(oneOf(" =").then(optArg()).attempt().optional()).attempt()
  fun dosOpt():RP<Unit> = char('/').then(satisfy(ParserCharacters::isAlphaNum)or char('?')).then(char(' ').then(optArg()).attempt().optional()).attempt()
  fun optionList():RP<Blocks> = optionListItem().many1().map(B::definitionList)
  fun optionListItem():RP<Pair<Inlines,List<Blocks>>> {
   val opt=shortOpt() or longOpt() or dosOpt()
   val opts=withRaw(opt.then(char(',').then(sp().many()).then(opt).attempt().many())){_,raw->raw}
   val first=char(' ').then(char(' ').many1()).then(anyLineNewline()).attempt() or sp().skipMany().then(newline()).map{""}.attempt()
   return opts.flatMap{raw->first.flatMap{one->indentedBlock().option("").flatMap{rest->parseFromString(delay{parseBlocks()},one+rest+"\n\n").before(blanklines().optional()).map{B.code(raw)to listOf(it)}}}}.attempt()
  }
  fun lineBlock():RP<Blocks>{
   val normal=char('|').then(char(' ')).then(sp().many()).flatMap{white->not(newline()).then(anyLine()).flatMap{line->char(' ').then(anyLine()).attempt().many().map{"\u00a0".repeat(white.size)+(listOf(line)+it).joinToString(" ")}}}.attempt()
   val blank=char('|').then(blankline()).map{String(Character.toChars(it))}.attempt()
   return(normal or blank).many1().before(blankline().skipMany()).flatMap{lines->sequence(lines.map(::parseInlineFromText)).map(B::lineBlock)}.attempt()
  }
  fun lineBlockDirective(body:String):RP<Blocks> = sequence(textLines(body.trimEnd('\n')).map(::parseInlineFromText)).map(B::lineBlock)
  fun textLines(s:String):List<String> = if(s.isEmpty())emptyList()else s.removeSuffix("\n").split('\n')
  fun lhsCodeBlock():RP<Blocks>{
   val latexLineStart=sp().skipMany().then(text("\\begin{code}")).then(blankline()).attempt()
   val latex=latexLineStart.then(till1(anyLine(),sp().skipMany().then(text("\\end{code}")).then(blankline()).attempt())).attempt()
   val bird=char('>').then(anyLine()).many1().map{lines->if(lines.all{it.isEmpty()||it.startsWith(' ')})lines.map{it.drop(1)}else lines}
   return state().flatMap{st->guard(st.position.column==1L&&ExtensionName.LiterateHaskell in st.user.options.extensions)}.then(text("::").then(blankline()).then(blankline()).optional()).then(latex or bird).before(blanklines()).map{B.codeBlockWith(Attr(classes=listOf("haskell","literate")),it.joinToString("\n"))}.attempt()
  }
  fun definitionListItem():RP<Pair<Inlines,List<Blocks>>> = not(text("..").attempt()).then(till1(delay{inline()},delay{endline()})).map{concat(it).trim()}.flatMap{term->indentedBlock().flatMap{raw->parseFromString(delay{parseBlocks()},raw+"\n").map{term to listOf(it)}}}.attempt()
  fun definitionList()=definitionListItem().many1().map(B::definitionList)
  fun bulletListStart():RP<Long> = notPrime(hrule()).then(oneOf(bullets)).then(sp().many1().map{it.size.toLong()}or newline().lookAhead().map{0L}).map{it+1}.attempt()
  fun orderedListStart(style:ListNumberStyle,delim:ListNumberDelim):RP<Long> = state().flatMap{old->ListParsing.orderedListMarker(style,delim,RstAccess).then(state()).flatMap{next->(sp().many1().map{it.size.toLong()}or newline().lookAhead().map{0L}).map{white->next.position.column-old.position.column+white}}}.attempt()
  fun indentWith(n:Long):RP<Unit> = state().flatMap{st->val spaces=count(n,char(' ')).map{Unit};if(n<st.user.options.tabStop)spaces else spaces.attempt()or char('\t').then(delay{indentWith(n-st.user.options.tabStop)}).attempt()}
  fun gobbleSpaces(n:Long):RP<Unit> = if(n<0)Parsec.fail("gobbleSpaces called with negative number")else if(n==0L)pure(Unit)else gobbleAtMostSpaces(n).flatMap{guard(it==n)}.attempt()
  fun listLine(n:Long):RP<String> = not(blankline()).then(indentWith(n)).then(anyLineNewline()).attempt()
  fun rawListItem(start:RP<Long>):RP<Pair<Long,String>> = start.flatMap{n->anyLineNewline().flatMap{first->listLine(n).many().map{n to(first+it.joinToString(""))}}}.attempt()
  fun listContinuation(n:Long):RP<String> = blankline().many1().map(::chars).flatMap{blank->listLine(n).many1().map{blank+it.joinToString("")}}.attempt()
  fun listItem(start:RP<Long>):RP<Blocks> = rawListItem(start).flatMap{(n,first)->listContinuation(n).many().flatMap{rest->(blankline().many1().map{Unit} or start.lookAhead().map{Unit}).then(state()).flatMap{old->update{it.copy(listContext=true)}.then(parseFromString(delay{parseBlocks()},first+rest.joinToString("")+"\n")).flatMap{parsed->update{it.copy(listContext=old.user.listContext)}.map{val xs=parsed.values;if(xs.firstOrNull()is Block.Para&&(xs.size==1||xs.size==2&&(xs[1]is Block.BulletList||xs[1]is Block.OrderedList||xs[1]is Block.DefinitionList)))Blocks(listOf(Block.Plain((xs[0]as Block.Para).content))+xs.drop(1))else parsed}}}}}.attempt()
  fun compactify(items:List<Blocks>):List<Blocks>{if(items.isEmpty())return items;val last=items.last().values;val earlier=items.dropLast(1);if(last.lastOrNull()is Block.Para&&last.dropLast(1).none{it is Block.Para}&&earlier.none{b->b.values.any{it is Block.Para}})return earlier+listOf(Blocks(last.dropLast(1)+Block.Plain((last.last()as Block.Para).content)));if(items.none{b->b.values.any{it is Block.Para}})return items;return items.map{b->Blocks(b.values.map{if(it is Block.Plain)Block.Para(it.content)else it})}}
  fun orderedList():RP<Blocks> = ListParsing.anyOrderedListMarker(RstAccess).before(sp()).lookAhead().flatMap{attr->listItem(orderedListStart(attr.style,attr.delimiter)).many1().map{B.orderedListWith(attr,compactify(it))}}.attempt()
  fun bulletList():RP<Blocks> = listItem(bulletListStart()).many1().map{B.bulletList(compactify(it))}
  fun list():RP<Blocks> = (bulletList()or orderedList()or definitionList()).label("list")
  fun comment():RP<Blocks> = text("..").then(sp().many1().map{Unit}or newline().lookAhead().map{Unit}).then(anyLine()).then(indentedBlock().optional()).then(blanklines().optional()).map{Blocks()}.attempt()
  val underlines="!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
  val bullets="*+-•‣⁃"
  fun oneOf(cs:String)=satisfy{cs.codePoints().anyMatch{c->c==it}}
  fun sourceDifference(first:Sources,second:Sources):String=buildString{
   var a=first.unSources.value();var b=second.unSources.value()
   while(a is DeferredChain.Cons){if(b===DeferredChain.Nil){while(a is DeferredChain.Cons){append(a.head.value().text);a=a.tail.value()};break}
    b as DeferredChain.Cons;val x=a.head.value();val y=b.head.value()
    if(x.position==y.position){if(x.text==y.text){a=a.tail.value();b=b.tail.value()}else{if(x.text.endsWith(y.text))append(x.text.dropLast(y.text.length));break}}
    else{append(x.text);a=a.tail.value()}
   }
  }
  fun <A,B> withRaw(p:RP<A>,f:(A,String)->B):RP<B> = state().flatMap{old->p.flatMap{value->state().map{next->f(value,sourceDifference(old.input.stream as Sources,next.input.stream as Sources))}}}
  fun registerHeader(id:String,content:Inlines):RP<Attr> = state().flatMap{st->val exts=st.user.options.extensions
   if(id.isEmpty()&&ExtensionName.AutoIdentifiers in exts){val auto=LaTeXReaderText.uniqueIdent(content.values,st.user.identifiers,exts);val ascii=if(ExtensionName.AsciiIdentifiers in exts)Asciify.toAsciiText(auto)else auto;update{it.copy(identifiers=it.identifiers+auto+ascii)}.map{Attr(identifier=ascii)}}
   else{val warn=if(id.isNotEmpty()&&id in st.user.identifiers)log(LogMessage.DuplicateIdentifier(id,st.position))else pure(Unit);warn.then(update{if(id.isEmpty())it else it.copy(identifiers=it.identifiers+id)}).map{Attr(identifier=id)}}
  }
  fun header():RP<Blocks> = (headerParts(true) or headerParts(false)).flatMap{(content,c,double)->state().flatMap{st->val marker=double to c;val existing=st.user.headerTable.indexOf(marker);val level=if(existing<0)st.user.headerTable.size+1 else existing+1;update{if(existing<0)it.copy(headerTable=it.headerTable+marker)else it}.then(registerHeader("",content)).flatMap{attr->update{it.copy(keys=it.keys+(toKey(DocumentShared.stringifyInlines(content.values))to RstTarget("#"+attr.identifier)))}.map{B.headerWith(attr,level.toLong(),content)}}}}.label("header")
  data class Heading(val content:Inlines,val char:Int,val double:Boolean)
  fun headerParts(double:Boolean):RP<Heading>{
   val contents=not(blankline()).then(delay{inline()}).flatMap{first->till(not(blankline()).then(delay{inline()}),blankline().lookAhead()).map{listOf(first)+it}}.flatMap{xs->state().map{concat(xs).trim()to(it.position.column-1)}}
   if(double)return oneOf(underlines).flatMap{c->satisfy{it==c}.many().flatMap{rest->sp().skipMany().then(newline()).then(contents).flatMap{(txt,len)->
    (if(len>rest.size+1)Parsec.fail("title longer than border")else pure(Unit)).then(blankline()).then(count(rest.size.toLong()+1,satisfy{it==c})).then(blanklines()).map{Heading(txt,c,true)}
   }}}.attempt()
   val possible=anyLine().then(oneOf(underlines).flatMap{c->satisfy{it==c}.skipMany().then(blankline())}).lookAhead()
   return notPrime(sp().many1().map{B.space}).then(possible).then(contents).flatMap{(txt,len)->blankline().then(oneOf(underlines)).flatMap{c->count(len-1,satisfy{it==c}).then(satisfy{it==c}.many()).then(blanklines()).map{Heading(txt,c,false)}}}.attempt()
  }
  fun hrule():RP<Blocks> = oneOf(underlines).flatMap{c->count(3,satisfy{it==c}).then(satisfy{it==c}.skipMany()).then(blankline()).then(blanklines()).map{B.horizontalRule}}.attempt()
  fun quotedBlock():RP<String> = oneOf(underlines).lookAhead().flatMap{c->satisfy{it==c}.lookAhead().then(anyLine()).many1().before(blanklines().optional()).map{it.joinToString("\n",postfix="\n")}}.attempt()
  fun gobbleAtMostSpaces(n:Long):RP<Long>{
   if(n<0)return Parsec.fail("gobbleAtMostSpaces called with negative number")
   if(n==0L)return pure(0)
   val one=char(' ') or state().flatMap{st->char('\t').lookAhead().then(Parsec.modify<RstState>{current->val count=current.user.options.tabStop-Math.floorMod(current.position.column-1,current.user.options.tabStop);val stream=current.input.stream as Sources;val chain=stream.unSources.value();if(chain !is DeferredChain.Cons)error("eatOneSpaceOfTab - empty Sources list");val old=chain.head.value();val replacement=SourceChunk(old.position," ".repeat(count.coerceAtLeast(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())+old.text.drop(1));current.copy(input=ParseInput(Sources(Deferred.now(DeferredChain.Cons(Deferred.now(replacement),chain.tail)))))}).then(char(' '))}
   return one.then(delay{gobbleAtMostSpaces(n-1)}).map{it+1}.option(0)
  }
  fun indentedBlock():RP<String> = sp().many1().lookAhead().flatMap{spaces->
   blanklines().option("").flatMap{blank->sp().lookAhead().then(gobbleAtMostSpaces(spaces.size.toLong())).then(anyLine()).map{blank+it}}.attempt().many1().before(blanklines().optional()).map{it.joinToString("\n",postfix="\n")}
  }.attempt()
  fun codeBlockBody():RP<Blocks> = state().flatMap{st->(indentedBlock() or quotedBlock()).map{B.codeBlockWith(Attr(classes=listOfNotNull(st.user.highlight)),it.trimEnd('\n'))}.attempt()}
  fun codeBlock():RP<Blocks> = text("::").then(blankline()).then(blankline()).then(codeBlockBody()).attempt()
  fun blockQuote():RP<Blocks> = indentedBlock().flatMap{parseFromString(delay{parseBlocks()},it+"\n\n").map(B::blockQuote)}
  fun para():RP<Blocks> = delay{inline()}.many1().map{concat(it).trim()}.flatMap{result->newline().then(blanklines()).then(
   if(result.values.lastOrNull()is Inline.Str&&(result.values.last()as Inline.Str).text.endsWith("::"))codeBlockBody().option(Blocks()).map{raw->B.para(Inlines(result.values.dropLast(1))+B.str((result.values.last()as Inline.Str).text.dropLast(1)))+raw}
   else pure(B.para(result))).attempt().option(B.plain(result))
  }.attempt()
  fun plain():RP<Blocks> = delay{inline()}.many1().map{B.plain(concat(it).trim())}
  val special="\\`|*_<>$:/[]{}()-.\"'‘’“”".codePoints().toArray().toSet()
  fun canPrecedeOpener(cp:Int)=RegexCharacters.categoryName(cp)in setOf("Ps","Pi","Pf","Pd","So")
  fun whitespace():RP<Inlines> = sp().many1().map{B.space}.label("whitespace")
  fun str():RP<Inlines> = take1{it!=9&&it!=10&&it!=32&&it !in special}.flatMap{value->last().map{B.str(value)}}
  fun endline():RP<Inlines> = newline().then(not(blankline())).then(state()).flatMap{st->(if(st.user.listContext)not(ListParsing.anyOrderedListMarker(RstAccess).then(sp())).then(notPrime(bulletListStart()))else pure(Unit)).map{B.softbreak}}.attempt()
  fun symbol():RP<Inlines> = satisfy{it in special}.flatMap{cp->(if(canPrecedeOpener(cp))pure(Unit)else last()).map{B.str(chars(listOf(cp)))}}
  fun escapedChar():RP<Inlines> = char('\\').then(any()).attempt().flatMap{cp->if(cp==32||cp==10||cp==13)pure(Inlines())else(if(canPrecedeOpener(cp))pure(Unit)else last()).map{B.str(chars(listOf(cp)))}}
  fun <A> atStart(p:RP<A>):RP<A> = state().flatMap{guard(it.user.lastStr!=it.position)}.then(p)
  fun <A,C,D> enclosed(start:RP<C>,end:RP<D>,body:RP<A>):RP<List<A>> = start.then(not(SourceParsers.space<RstState>())).then(till1(body,end)).attempt()
  fun code():RP<Inlines> = text("``").then(till(any(),text("``").attempt())).map{B.code(trim(textLines(chars(it)).joinToString(" ")))}.attempt()
  fun emph():RP<Inlines> = enclosed(atStart(char('*')),char('*'),delay{inlineContent()}).map{B.emph(concat(it).trim())}
  fun strong():RP<Inlines> = enclosed(atStart(text("**")),text("**").attempt(),delay{inlineContent()}).map{B.strong(concat(it).trim())}
  val smartParser by lazy{SmartParsing<Unit,RstState>(RstAccess,RstAccess,ReaderParsec.lift(state().map{it.user.quote}),{context,p->ReaderParsec.lift(state().flatMap{old->update{it.copy(quote=context)}.then(p.runReader(Deferred.now(Unit))).before(update{it.copy(quote=old.user.quote)})})})}
  fun smart():RP<Inlines> = smartParser.smartPunctuation(ReaderParsec.lift(delay{inline()})).runReader(Deferred.now(Unit))
  fun inlineContent():RP<Inlines> = (whitespace()or str()or endline()or smart()or escapedChar()or symbol()).label("inline content")
  fun inline():RP<Inlines> = (note()or state().flatMap{guard(it.user.lastStr!=it.position)}.then(delay{link()or inlineAnchor()or strong()or emph()})or code()or subst()or interpretedRole()or inlineContent()).label("inline")
  fun parseInlineFromText(raw:String):RP<Inlines> = parseFromString(delay{inline()}.many().map{concat(it).trim()},raw)
  fun quotedReferenceName():RP<String> = char('`').then(not(char('`'))).then(till(any(),char('`'))).map(::chars).attempt()
  fun simpleReferenceName():RP<String> = satisfy(ParserCharacters::isAlphaNum).flatMap{first->(satisfy(ParserCharacters::isAlphaNum)or oneOf("-_:+.").before(satisfy(ParserCharacters::isAlphaNum).lookAhead()).attempt()).many().map{chars(listOf(first)+it)}}
  fun referenceName():RP<String> = quotedReferenceName()or simpleReferenceName()
  fun citationMarker():RP<String> = char('[').then(simpleReferenceName()).before(char(']'))
  fun noteMarker():RP<String> = char('[').then(take1(ParserCharacters::isDigit)or char('#').then(simpleReferenceName()).map{"#"+it}.attempt()or oneOf("#*").map{chars(listOf(it))}).before(char(']'))
  fun note():RP<Inlines> = whitespace().optional().then(noteMarker()).before(char('_')).map{B.linkWith(Attr(),"##NOTE##"+it,"",B.text(it))}.attempt()
  fun referenceLink():RP<Inlines> = (referenceName()or citationMarker().map{"[$it]"}).before(char('_')).flatMap{ref->char('_').map{true}.option(false).before(eof()or not(satisfy(ParserCharacters::isAlphaNum))).map{anon->B.linkWith(Attr(),"##REF##"+(if(anon)"_"else ref),"",B.text(ref))}}.attempt()
  fun explicitLink():RP<Inlines> = char('`').then(not(char('`'))).then(till(not(char('`')).then(delay{inlineContent()}),char('<'))).map{concat(it).trim()}.flatMap{label->till(satisfy{it!=62&&it!=10}or newline().before(not(blankline())),char('>')).map{trim(chars(it.filter{c->c!=10}))}.before(sp().skipMany()).before(text("`_")).before(char('_').optional()).flatMap{src->val url=if(PandocURI.isURI(src))PandocURI.escapeURI(src)else if(src.endsWith('_'))"##REF##"+src.dropLast(1)else src;val key=toKey(DocumentShared.stringifyInlines(label.values));(if(key==Key(""))pure(Unit)else update{it.copy(keys=it.keys+(key to RstTarget(url)))}).map{B.linkWith(Attr(),url,"",if(label.values.isEmpty())B.str(src)else label)}}}.attempt()
  fun linkPossible():RP<Unit> = state().flatMap{st->val chain=(st.input.stream as Sources).unSources.value();if(chain !is DeferredChain.Cons)zero()else{val txt=chain.head.value().text;val stop=txt.indexOfFirst{it==' '||it=='\t'||it=='\n'};val word=if(stop<0)txt else txt.take(stop);guard((stop<0&&chain.tail.value()!==DeferredChain.Nil)||word.any{it in "`[_:@"})}}
  fun link():RP<Inlines> = linkPossible().then(explicitLink()or referenceLink()or autoLink()).label("link")
  fun subst():RP<Inlines> = withRaw(enclosed(char('|'),char('|'),delay{inline()})){_,raw->raw}.flatMap{ref->val substitution=B.linkWith(Attr(),"##SUBST##"+ref,"",B.text(ref));char('_').map{B.linkWith(Attr(),"##REF##"+ref.drop(1).dropLast(1),"",substitution)}.option(substitution)}.attempt()
  fun inlineAnchor():RP<Inlines> = char('_').then(quotedReferenceName()or simpleReferenceName()).flatMap{name->val filtered=buildString{for(cp in LaTeXReaderText.toLower(name).codePoints().toArray())if(ParserCharacters.isSpace(cp)||ParserCharacters.isAlphaNum(cp)||cp==45||cp==95||cp==46)appendCodePoint(cp)};val joined=words(filtered).joinToString("-");var start=0;while(start<joined.length&&!ParserCharacters.isAlpha(joined.codePointAt(start)))start+=Character.charCount(joined.codePointAt(start));val id=joined.drop(start);update{it.copy(keys=it.keys+(toKey(name)to RstTarget("#"+id)))}.map{B.spanWith(Attr(identifier=id),B.text(name))}}.attempt()
  fun roleName():RP<String> = (satisfy(ParserCharacters::isAlphaNum)or oneOf("-_.:+").before(satisfy(ParserCharacters::isAlphaNum).lookAhead()).attempt()).many1().map(::chars)
  fun roleMarker():RP<String> = char(':').then(roleName()).before(char(':'))
  fun unmarkedInterpretedText():RP<String>{
   val ordinary=take1{it!=96&&it!=92&&it!=10}
   val slash=char('\\').then(satisfy{it!=10}).map{"\\"+chars(listOf(it))}
   val line=newline().before(not(blankline())).map{"\n"}
   val tick=char('`').before(not(roleMarker().map{Unit})).before(satisfy(ParserCharacters::isAlphaNum).lookAhead()).map{"`"}.attempt()
   return atStart(char('`')).then((ordinary or slash or line or tick).many1()).before(char('`')).map{it.joinToString("")}.attempt()
  }
  fun interpretedRole():RP<Inlines>{val before=roleMarker().flatMap{role->unmarkedInterpretedText().map{role to it}}.attempt();val after=unmarkedInterpretedText().flatMap{body->(roleMarker()or state().map{it.user.defaultRole}).map{it to body}}.attempt();return(before or after).flatMap{(role,body)->renderRole(body,null,role,Attr())}.attempt()}
  fun renderRole(body:String,format:String?,role:String,attr:Attr):RP<Inlines>{
   fun treat():Inlines{val parts=body.split('\\');return B.text(parts.first()+parts.drop(1).joinToString(""){it.removePrefix(" ")})}
   return when(role){
    "sup","superscript"->pure(B.superscript(treat()));"sub","subscript"->pure(B.subscript(treat()));"mark"->pure(B.spanWith(Attr(classes=listOf("mark")),treat()));"emphasis"->pure(B.emph(treat()));"strong"->pure(B.strong(treat()))
    "rfc-reference","RFC"->pure(B.link("http://www.faqs.org/rfcs/rfc$body.html","RFC $body",B.str("RFC $body")))
    "pep-reference","PEP"->pure(B.link("http://www.python.org/dev/peps/pep-"+"0".repeat((4-body.codePointCount(0,body.length)).coerceAtLeast(0))+body+"/","PEP $body",B.str("PEP $body")))
    "literal","code"->pure(B.codeWith(attr,body));"math"->pure(B.math(body));"title-reference","title","t"->pure(B.spanWith(Attr(classes=listOf("title-ref")),treat()));"span"->pure(B.spanWith(attr,treat()));"raw"->pure(B.rawInline(format?:"",body))
    else->if(role.startsWith("cite"))pure(cite(role.removePrefix("cite"),body))else state().flatMap{st->val parent=st.user.customRoles[role];if(parent==null)pure(B.codeWith(Attr(classes=listOf("interpreted-text"),keyValues=listOf("role" to role)),body))else delay{renderRole(body,parent.format,parent.base,parent.attr)}}
   }
  }
  fun parseCite(text:String):Citation{var mode=0;val prefix=StringBuilder();val suffix=StringBuilder();val id=StringBuilder();for(cp in text.codePoints().toArray())when(mode){0->if(cp==123)mode=1 else{mode=3;id.appendCodePoint(cp)};1->if(cp==125)mode=3 else prefix.appendCodePoint(cp);2->if(cp==125)mode=3 else suffix.appendCodePoint(cp);else->if(cp==123)mode=2 else id.appendCodePoint(cp)}
return Citation(id.toString(),B.text(prefix.toString()).values,B.text(suffix.toString()).values)}
  fun cite(kind:String,raw:String):Inlines{val citations=raw.split(',').map(::parseCite).toMutableList();if(citations.isNotEmpty())when(kind){":t",":ct"->citations[0]=citations[0].copy(citationMode=CitationMode.AuthorInText);":year",":yearpar"->citations[0]=citations[0].copy(citationMode=CitationMode.SuppressAuthor)}
return B.cite(citations,B.str(raw))}
  fun entity():RP<String> = char('&').then(till1(satisfy{it!=32&&it!=9&&it!=10&&it!=13},char(';'))).flatMap{xs->PandocXml.lookupEntity(chars(xs)+";")?.let{pure(it)}?:Parsec.fail("entity not found")}.attempt()
  class Trie(var terminal:Boolean=false,val children:MutableMap<Int,Trie> = mutableMapOf())
  val schemes by lazy{val root=Trie();for(scheme in PandocURI.schemes){var node=root;for(cp in scheme.codePoints().toArray())node=node.children.getOrPut(RegexCharacters.toLower(cp)){Trie()};node.terminal=true};root}
  fun uriScheme():RP<String>{fun go(node:Trie,acc:String):RP<String> = any().flatMap{cp->val child=node.children[RegexCharacters.toLower(cp)]?:return@flatMap Parsec.fail("not a URI scheme");val next=acc+chars(listOf(cp));if(child.terminal)delay{go(child,next)}.attempt().option(next)else delay{go(child,next)}}
return delay{go(schemes,"")}.attempt()}
  fun uri():RP<Pair<String,String>>{
   val word=satisfy{ParserCharacters.isAlphaNum(it)||it in "#$%+/@\\_-&=".map(Char::code)}
   val percent=char('%').flatMap{first->satisfy{it in 48..57||it in 65..70||it in 97..102}.many1().map{chars(listOf(first)+it)}}.attempt()
   val punct=(char(',').many1().map(::chars)or satisfy{!ParserCharacters.isSpace(it)&&it!=60&&it!=62}.map{chars(listOf(it))}).attempt()
   val chunk=word.many1().map(::chars)or percent or entity()or punct.before((word.map{Unit}or percent.map{Unit}).lookAhead()).attempt()
   fun balanced(left:Char,right:Char)=char(left).then(chunk).before(char(right)).map{"$left$it$right"}.attempt()
   return uriScheme().before(char(':')).before(not(satisfy{it==42||it==95||it==93})).flatMap{scheme->(balanced('(',')')or balanced('{','}')or balanced('[',']')or chunk).many1().map{it.joinToString("")}.flatMap{body->char('/').map{"$body/"}.option(body)}.map{body->val original=scheme+":"+PandocXml.fromEntities(body);original to PandocURI.escapeURI(original)}}.attempt()
  }
  fun email():RP<Pair<String,String>>{
   fun parts(p:RP<String>)=p.flatMap{first->char('.').then(p).attempt().many().map{(listOf(first)+it).joinToString(".")}}
   val mailbox=satisfy(ParserCharacters::isAlphaNum).flatMap{first->satisfy{ParserCharacters.isAlphaNum(it)||it in "!\"#$%&'*+-/=?^_{|}~;".map(Char::code)}.many().map{chars(listOf(first)+it)}}
   val hyphen=char('-').before(not(satisfy{!ParserCharacters.isAlphaNum(it)})).attempt();val domain=(satisfy(ParserCharacters::isAlphaNum)or hyphen).many1().map(::chars)
   return parts(mailbox).flatMap{box->char('@').then(parts(domain)).map{val text=PandocXml.fromEntities("$box@$it");text to PandocURI.escapeURI("mailto:$text")}}.attempt()
  }
  fun autoLink():RP<Inlines> = (uri()or email()).map{(label,url)->B.link(url,"",B.str(label))}
  fun document():RP<Pandoc> = blanklines().optional().then(delay{parseBlocks()}).flatMap{bs->state().flatMap{st->
   val citations=st.user.citations.entries.sortedWith{a,b->UnicodeTextOrder.compare(a.key,b.key)}
   sequence(citations.map{(ref,raw)->parseFromString(delay{parseBlocks()},raw).map{body->listOf(Inline.Span(Attr(classes=listOf("citation-label"),identifier=ref),listOf(Inline.Str(ref))))to listOf(body.values)}}).flatMap{items->state().flatMap{after->
    val transformed=if(after.user.options.standalone)titleTransform(bs.values,after.user.meta)else bs.values to after.user.meta
    val refBlock=if(items.isEmpty())emptyList()else listOf(Block.Div(Attr(identifier="citations"),listOf(Block.DefinitionList(items))))
    val notes=after.user.notes.asReversed();val named=notes.filter{it.first!="*"&&it.first!="#"}.asReversed().toMap()
    update{it.copy(notes=notes.filter{p->p.first=="*"||p.first=="#"})}.then(walk(Walk.blockInPandoc,::resolveBlockSubstitutions,Pandoc(transformed.second,transformed.first+refBlock))).flatMap{doc->walk(Walk.inlineInPandoc,{resolveReferences(named,emptySet(),it)},doc)}.flatMap{doc->state().flatMap{end->Parsec.lift{end.user.messages.asReversed().forEach(runtime::report);doc}}}
   }}
  }}
  fun <A> sequence(xs:List<RP<A>>):RP<List<A>>{class Link<A>(val x:A,val next:Link<A>?);var p:RP<Link<A>?> = pure(null);for(x in xs.asReversed()){val tail=p;p=x.flatMap{value->tail.map{Link(value,it)}}}
return p.map{link->buildList{var n=link;while(n!=null){add(n.x);n=n.next}}}}
  fun titleTransform(input:List<Block>,originalMeta:Map<String,MetaValue>):Pair<List<Block>,Map<String,MetaValue>>{
   var bs=input;var meta=originalMeta
   fun isHeader(b:Block,n:Long)=b is Block.Header&&b.level==n
   fun promote(n:Long,xs:List<Block>)=xs.map{if(it is Block.Header)it.copy(level=it.level-n)else it}
   if(bs.size>=2&&isHeader(bs[0],1)&&isHeader(bs[1],2)&&bs.drop(2).none{isHeader(it,1)||isHeader(it,2)}){meta=meta+mapOf("title" to MetaValue.MetaInlines((bs[0]as Block.Header).content),"subtitle" to MetaValue.MetaInlines((bs[1]as Block.Header).content));bs=promote(2,bs.drop(2))}
   else if(bs.firstOrNull()?.let{isHeader(it,1)}==true&&bs.drop(1).none{isHeader(it,1)}){meta=meta+("title" to MetaValue.MetaInlines((bs[0]as Block.Header).content));bs=promote(1,bs.drop(1))}
   if(bs.firstOrNull()is Block.DefinitionList){meta=metaFromDefinitions((bs[0]as Block.DefinitionList).items,meta);bs=bs.drop(1)}
   return bs to meta
  }
  fun metaFromDefinitions(ds:List<Pair<List<Inline>,List<List<Block>>>>,original:Map<String,MetaValue>):Map<String,MetaValue>{
   var meta=original
   for((k,v)in ds.asReversed()){val value=if(v.size==1&&v[0].size==1&&v[0][0]is Block.Plain)MetaValue.MetaInlines((v[0][0]as Block.Plain).content)else MetaValue.MetaBlocks(v.flatten());meta=meta+(LaTeXReaderText.toLower(DocumentShared.stringifyInlines(k))to value)}
   val renamed=linkedMapOf<String,MetaValue>();for((k,v)in meta.entries.sortedWith{a,b->UnicodeTextOrder.compare(a.key,b.key)})renamed[if(k=="authors")"author"else k]=v
   val author=renamed["author"]
   if(author is MetaValue.MetaInlines){val factored=author.content.flatMap{x->if(x !is Inline.Str)listOf(x)else{val out=mutableListOf<Inline>();var rest=x.text;while(rest.isNotEmpty()){val semi=rest.indexOf(';');if(semi<0){out+=Inline.Str(rest);break};out+=Inline.Str(rest.substring(0,semi));out+=Inline.Str(";");rest=rest.substring(semi+1)};out}}
    val groups=mutableListOf<List<Inline>>();var at=0
    while(at<factored.size){val start=at;while(at<factored.size&&factored[at]!=Inline.Str(";"))at++;groups+=factored.subList(start,at).dropWhile{it===Inline.Space||it===Inline.SoftBreak||it===Inline.LineBreak}.dropLastWhile{it===Inline.Space||it===Inline.SoftBreak||it===Inline.LineBreak};while(at<factored.size&&factored[at]==Inline.Str(";"))at++}
    renamed["author"]=MetaValue.MetaList(groups.map{MetaValue.MetaInlines(it)})
   }
return renamed
  }
  fun resolveBlockSubstitutions(b:Block):RP<Block>{
   val link=(b as? Block.Para)?.content?.singleOrNull()as? Inline.Link?:return pure(b)
   val ref=link.target.url.removePrefix("##SUBST##");if(ref==link.target.url)return pure(b)
   return state().flatMap{st->val key=toKey(ref.drop(1).dropLast(1));val target=st.user.substitutions[key]
    if(target==null)log(LogMessage.ReferenceNotFound(HaskellString.quote(key.text),st.position)).map{Block.Para(link.content)}else pure(target.values.singleOrNull()?:Block.Div(Attr(),target.values))}
  }
  fun lookupKey(old:List<Key>,key:Key):RP<RstTarget> = state().flatMap{st->val value=st.user.keys[key]
   if(value==null)log(LogMessage.ReferenceNotFound(key.text,st.position)).map{RstTarget("")}
   else if(value.title.isEmpty()&&value.url.length>1&&value.url.endsWith('_')&&!value.url.startsWith('#')){val raw=value.url.dropLast(1);val next=toKey(raw);if(next in old)log(LogMessage.CircularReference(raw,st.position)).map{RstTarget("")}else delay{lookupKey(listOf(key)+old,next)}}
   else pure(value)
  }
  fun resolveReferences(named:Map<String,String>,seen:Set<Key>,x:Inline):RP<Inline>{
   if(x !is Inline.Link)return pure(x)
   val url=x.target.url
   if(url.startsWith("##REF##")){val ref=url.removePrefix("##REF##");return state().flatMap{st->
    val key=if(ref=="_")st.user.keys.keys.sorted().firstOrNull{it>=Key("_")}?.takeIf{it.text.startsWith('_')}?:return@flatMap zero<Inline>()else toKey(ref)
    if(key in seen)log(LogMessage.CircularReference(key.text,st.position)).map{x}else lookupKey(emptyList(),key).flatMap{target->(if(key.text.startsWith('_'))update{it.copy(keys=it.keys-key)}else pure(Unit)).then(delay{resolveReferences(named,seen+key,Inline.Link(target.attr,x.content,Target(target.url,target.title)))})}
   }}
   if(url.startsWith("##NOTE##")){val ref=url.removePrefix("##NOTE##");return state().flatMap{st->val auto=st.user.notes;val index=auto.indexOfFirst{it.first==ref};val note=if(ref=="*"||ref=="#")auto.getOrNull(index)?.second else named[ref]
    if(note==null)log(LogMessage.ReferenceNotFound(ref,st.position)).map{x}else{val newNotes=if(ref=="*"||ref=="#")auto.filterIndexed{i,_->i!=index}else auto;update{it.copy(notes=emptyList())}.then(parseFromString(delay{parseBlocks()},note)).flatMap{body->update{it.copy(notes=newNotes)}.map{Inline.Note(body.values)}}}}
   }
   if(url.startsWith("##SUBST##")){val ref=url.removePrefix("##SUBST##");val key=toKey(ref.drop(1).dropLast(1));return state().flatMap{st->
    if(key in seen)log(LogMessage.CircularReference(key.text,st.position)).map{Inline.Span(Attr(),x.content)}else{val target=st.user.substitutions[key];if(target==null)log(LogMessage.ReferenceNotFound(HaskellString.quote(key.text),st.position)).map{Inline.Span(Attr(),x.content)}else{val para=target.values.singleOrNull()as? Block.Para;val resolved=if(para!=null)para.content.singleOrNull()?:Inline.Span(Attr(),para.content)else Inline.Span(Attr(),blocksToInlines(target.values));delay{resolveReferences(named,seen+key,resolved)}}}}
   }
return pure(x)
  }
  fun noteBlock(citation:Boolean):RP<Blocks> = text("..").then(sp()).then(sp().skipMany()).then(if(citation)citationMarker()else noteMarker()).flatMap{ref->
   (sp().then(sp().skipMany()).then(anyLine()) or newline().map{""}).flatMap{first->blanklines().option("").flatMap{blanks->indentedBlock().option("").flatMap{rest->val raw=first+"\n"+blanks+rest+"\n";update{if(citation)it.copy(citations=it.citations+(ref to raw),keys=it.keys+(toKey(ref)to RstTarget("#"+ref,attr=Attr(classes=listOf("citation")))))else it.copy(notes=listOf(ref to raw)+it.notes)}.map{Blocks()}}}}
  }.attempt()
  fun targetURI():RP<String> = sp().skipMany().then(newline().before(not(blankline())).attempt().optional()).then((take1{it!=10} or newline().then(sp().many1()).then(satisfy{it!=32&&it!=9&&it!=10}).map{chars(listOf(it))}.attempt()).many1()).before(blanklines()).map{parts->val value=trim(parts.joinToString(""));if(value.endsWith("`_"))value.dropLast(2).dropWhile{it=='`'}+"_"else if(value.endsWith('_'))value else PandocURI.escapeURI(value)}
  fun referenceNames():RP<List<String>>{
   val part=text(".. _").then(quotedReferenceName()or(satisfy{it!=92&&it!=58&&it!=10}or newline().before(text("   ")).before(not(blankline())).attempt()or char('\\').then(char(':')).attempt()or char(':').before(satisfy(ParserCharacters::isAlphaNum).lookAhead()).attempt()).many().map(::chars)).before(char(':')).attempt()
   return part.flatMap{first->blanklines().then(part).attempt().many().map{listOf(first)+it}}
  }
  fun referenceKey():RP<Blocks> = (substKey() or anonymousKey() or regularKey()).before(blanklines().optional()).map{Blocks()}
  fun anonymousKey():RP<Unit> = (text(".. __:")or text("__")).then(sp().many1()).then(targetURI()).flatMap{src->update{val name="_"+it.keys.size.toString().padStart(4,'0');it.copy(keys=it.keys+(toKey(name)to RstTarget(src)))}}.attempt()
  fun regularKey():RP<Unit> = referenceNames().flatMap{refs->targetURI().flatMap{src->guard(src.isNotEmpty()).then(update{st->st.copy(keys=st.keys+refs.associate{toKey(it)to RstTarget(src)})})}}.attempt()
  fun substKey():RP<Unit> = text("..").then(sp().many1()).then(withRaw(enclosed(char('|'),char('|'),delay{inline()})){xs,raw->concat(xs).trim() to raw}).flatMap{(alt,raw)->directiveBody().flatMap{result->
   val para=result.values.singleOrNull()as? Block.Para;val single=para?.content?.singleOrNull()
   fun replace(x:Inline):Inline=if(x is Inline.Image&&x.content==listOf(Inline.Str("image")))x.copy(content=alt.values)else x
   val body=when(single){is Inline.Image->B.para(Inlines(listOf(replace(single))));is Inline.Link->if(single.content.singleOrNull()is Inline.Image)B.para(Inlines(listOf(single.copy(content=single.content.map(::replace)))))else result;else->result}
   update{it.copy(substitutions=it.substitutions+(toKey(raw.drop(1).dropLast(1))to body))}
  }}.attempt()
  fun anchor():RP<Blocks> = referenceNames().before(blanklines()).flatMap{refs->update{st->st.copy(keys=st.keys+refs.associate{toKey(it)to RstTarget("#"+it)})}.then(delay{block()}).map{body->val header=body.values.singleOrNull()as? Block.Header;if(header!=null&&refs.isNotEmpty())Blocks(listOf(header.copy(attr=header.attr.copy(identifier=refs.last()),content=header.content+refs.dropLast(1).reversed().map{Inline.Span(Attr(identifier=it),emptyList())})))else refs.asReversed().fold(body){inside,ref->B.divWith(Attr(identifier=ref),inside)}}}.attempt()
  fun stripNewlines(s:String)=s.trimEnd('\n')
  fun field(fields:List<Pair<String,String>>,name:String)=fields.firstOrNull{it.first==name}?.second
  fun mkAttr(name:String,classes:List<String>,fields:List<Pair<String,String>>)=Attr(name,classes,fields.filter{it.first !in listOf("name","id","class")}.map{it.first to trimr(it.second)})
  fun codeblock(name:String,classes:List<String>,fields:List<Pair<String,String>>,language:String,strip:Boolean,body:String):Blocks{
   val number=field(fields,"number-lines");val cls=listOf(language).filter{it.isNotEmpty()}+(if(number==null)emptyList()else listOf("numberLines"))+classes
   val kv=fields.filter{it.first !in listOf("number-lines","class","id","name")}+(if(number.isNullOrEmpty())emptyList()else listOf("startFrom" to number))
   return B.codeBlockWith(Attr(name,cls,kv),if(strip)stripNewlines(body)else body)
  }
  fun unicodeTransform(raw:String)=RstReaderPure.unicodeTransform(raw)
  fun chunks(raw:String)=RstReaderPure.chunks(raw)
  fun addNewRole(raw:String,fields:List<Pair<String,String>>):RP<Blocks> = state().flatMap{old->parseFromString(roleName().flatMap{role->char('(').then(roleName()).before(char(')')).option("span").map{role to it}},raw).flatMap{(role,parent)->
   var base=RstRole(parent);while(old.user.customRoles.containsKey(base.base))base=old.user.customRoles.getValue(base.base)
   val format=if(parent=="raw")field(fields,"format")else base.format
   val classes=((field(fields,"class")?.let(::words)?:listOf(role))+(if(base.base=="code")listOfNotNull(field(fields,"language"))else emptyList())+base.attr.classes).distinct()
   val logs=fields.mapNotNull{(key,_)->when(key){"language"->if(base.base!="code")":language: [because parent of role is not :code:]"else null;"format"->if(base.base!="raw")":format: [because parent of role is not :raw:]"else null;else->":$key:"}}.toMutableList()
   if(parent=="raw"&&fields.count{it.first=="format"}>1)logs+=":format: [after first in definition of role]"
   if(parent=="code"&&fields.count{it.first=="language"}>1)logs+=":language: [after first in definition of role]"
   sequence(logs.map{log(LogMessage.SkippedContent(it,old.position))}).then(update{it.copy(customRoles=it.customRoles+(role to RstRole(base.base,format,base.attr.copy(classes=classes))))}).map{Blocks()}
  }}
  fun directive():RP<Blocks> = text("..").then(delay{directiveBody()}).attempt()
  fun directiveBody():RP<Blocks> = sp().many1().then(till1(satisfy{ParserCharacters.isAlpha(it)||it==45},text("::").attempt())).map{LaTeXReaderText.toLower(chars(it))}.before(sp().skipMany()).flatMap{label->
   (satisfy{it!=10}or newline().before(notPrime(rawFieldListItem(1))).before(char(' ').many1()).before(not(blankline())).attempt()).many().map(::chars).before(newline()).flatMap{top->
    char(' ').many().lookAhead().flatMap{indent->if(indent.isEmpty())pure(emptyList())else rawFieldListItem(indent.size.toLong()).many()}.flatMap{fields->
     val rawFile=field(fields,"file")?.let(::trim)
     val body=if(label=="raw"&&rawFile!=null)state().flatMap{st->Parsec.lift{readFileFromDirectory(st.position.name,rawFile)?:""}}else blanklines().then(indentedBlock()).attempt().option("")
     body.before(blanklines().optional()).flatMap{raw->directiveResult(label,top,fields,raw)}
    }
   }
  }
  fun directiveResult(label:String,top:String,fields:List<Pair<String,String>>,body:String):RP<Blocks>{
   val appended=body+"\n\n";val name=trim(field(fields,"name")?:"");val classes=words(trim(field(fields,"class")?:""));val kv=fields.filter{it.first!="name"&&it.first!="class"}.map{it.first to trim(it.second)}
   fun parsed()=parseFromString(delay{parseBlocks()},appended)
   fun imageAttrs():Pair<Attr,List<String>>{
    val align=words(trim(field(fields,"class")?:"")+(field(fields,"align")?.let{"align-"+trim(it)}?:""))
    val scale=field(fields,"scale")?.let(::trim)?.let{if(it.endsWith('%'))io.heapy.kotdoc.formats.HaskellNumber.readDouble(it.dropLast(1))?.div(100)else io.heapy.kotdoc.formats.HaskellNumber.readDouble(it)}?:1.0
    val dimensions=listOf("width","height").mapNotNull{k->field(fields,k)?.filterNot{ParserCharacters.isSpace(it.code)}?.let(io.heapy.kotdoc.images.Dimensions::lengthToDim)?.let{k to io.heapy.kotdoc.images.Dimensions.scaleDimension(scale,it).toString()}}
    return Attr(name,classes,dimensions)to align
   }
   return when(label){
    "include"->includeDirective(top,fields,appended)
    "table"->tableDirective(top,fields,appended)
    "list-table"->listTableDirective(top,fields,appended)
    "csv-table"->csvTableDirective(top,fields,appended)
    "line-block"->lineBlockDirective(appended)
    "raw"->pure(B.rawBlock(trim(top),stripNewlines(body)))
    "role"->addNewRole(top,fields.map{it.first to trim(it.second)})
    "container"->parsed().map{B.divWith(Attr(name,listOf("container")+words(top)+classes),it)}
    "replace"->parseInlineFromText(trim(top)).map(B::para)
    "date"->Parsec.lift<RstState,io.heapy.kotdoc.resources.PandocUtcTime>{effects.getTimestamp()}.map{B.para(B.text(RstDateFormat.format(trim(top).ifEmpty{"%Y-%m-%d"},RstDateFormat.Timestamp(it))))}
    "unicode"->parseInlineFromText(trim(unicodeTransform(top))).map(B::para)
    "compound"->parsed()
    "pull-quote","epigraph","highlights"->parsed().map(B::blockQuote)
    "rubric"->parseInlineFromText(top).map{B.para(B.strong(it))}
    "attention","caution","danger","error","hint","important","note","tip","warning","admonition"->parseFromString(delay{parseBlocks()},top+"\n\n"+appended).map{bod->val title=if(label=="admonition")Blocks()else B.divWith(Attr(classes=listOf("title")),B.para(B.str(chars(listOf(RegexCharacters.toUpper(label.codePointAt(0))))+label.drop(1))));B.divWith(Attr(name,listOf(label)+classes,kv),title+bod)}
    "sidebar"->parseInlineFromText(trim(top)+(field(fields,"subtitle")?.let(::trim)?.takeIf{it.isNotEmpty()}?.let{":  $it"}?:"")).flatMap{title->parsed().map{B.divWith(Attr(name,listOf("sidebar")+classes,kv),B.para(B.strong(title))+it)}}
    "topic"->parseInlineFromText(top).flatMap{title->parsed().map{B.divWith(Attr(name,listOf("topic")+classes,kv),B.para(B.strong(title))+it)}}
    "default-role"->update{it.copy(defaultRole=trim(top).ifEmpty{"title-reference"})}.map{Blocks()}
    "highlight"->update{it.copy(highlight=trim(top).takeIf{it.isNotEmpty()})}.map{Blocks()}
    "code","code-block","sourcecode"->pure(codeblock(name,classes,fields.map{it.first to trimr(it.second)},trim(top),true,body))
    "aafig"->pure(B.codeBlockWith(Attr(name,listOf("aafig"),fields.map{it.first to trimr(it.second)}),stripNewlines(body)))
    "math"->{val attr=mkAttr(name,classes,fields);val math=concat(chunks(top+"\n\n"+body).map(B::displayMath));pure(B.para(if(attr==Attr())math else B.spanWith(attr,math)))}
    "figure"->parseFromString(delay{inline()}.many().map{concat(it).trim()}.flatMap{caption->blanklines().optional().then(delay{block()}.many()).map{caption to blocks(it)}},appended).map{(caption,legend)->val(img,align)=imageAttrs();val figClasses=kv.filter{it.first=="figclass"}.flatMap{words(it.second)};val alt=field(fields,"alt")?.let{B.text(trim(it))}?:caption;B.figureWith(Attr(classes=figClasses+align),B.caption(null,B.plain(caption)+legend),B.plain(B.imageWith(img,PandocURI.escapeURI(trim(top)),"",alt)))}
    "image"->{val(img,align)=imageAttrs();val image=B.imageWith(img.copy(classes=img.classes+align),PandocURI.escapeURI(trim(top)),"",B.str(field(fields,"alt")?.let(::trim)?:"image"));pure(B.para(field(fields,"target")?.let{B.link(PandocURI.escapeURI(trim(it)),"",image)}?:image))}
    "bibliography"->pure(B.divWith(Attr(identifier="refs"),Blocks()))
    "class"->{val attr=Attr(name,words(trim(top)),fields.map{it.first to trimr(it.second)});(if(body.isEmpty())delay{block()}else parsed()).map{children->val header=children.values.singleOrNull()as? Block.Header;if(header!=null&&body.isEmpty())Blocks(listOf(header.copy(attr=Attr(header.attr.identifier+attr.identifier,header.attr.classes+attr.classes,header.attr.keyValues+attr.keyValues))))else B.divWith(attr,children)}}
    else->state().flatMap{st->log(LogMessage.SkippedContent(".. "+label,st.position)).then(parseFromString(delay{parseBlocks()},top+"\n\n"+appended)).map{B.divWith(Attr(name,listOf(label)+classes,kv),it)}}
   }
  }
  fun strictWidth(w:Double):ColWidth=if(w>0)ColWidth.Width(w)else ColWidth.Default
  fun numbers(s:String)=s.split(' ', ',').filter{it.isNotEmpty()}.map{io.heapy.kotdoc.formats.HaskellNumber.readDouble(it)?:0.0}
  fun tableDirective(top:String,fields:List<Pair<String,String>>,body:String):RP<Blocks> = parseFromString(delay{parseBlocks()},body).flatMap{bs->val table=bs.values.singleOrNull()as? Block.Table
   if(table==null)state().flatMap{st->log(LogMessage.SkippedContent(body,st.position)).map{Blocks()}}else parseInlineFromText(top).flatMap{title->state().map{st->
    val columns=table.head.rows.firstOrNull()?.cells?.sumOf{maxOf(1,it.colSpan)}?:0
    val widths=when(val spec=field(fields,"widths")?.let(::trim)){"auto"->List(columns.coerceAtLeast(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()){ColWidth.Default};"grid",null->table.columns.map{it.width};else->numbers(spec).map{strictWidth(it/maxOf(1.0,(st.user.options.columns-columns).toDouble()))}}
    Blocks(listOf(table.copy(caption=B.caption(null,B.plain(title)),columns=table.columns.zip(widths){col,width->col.copy(width=width)})))
   }}
  }
  fun tableFromRows(top:String,fields:List<Pair<String,String>>,rows:List<List<Blocks>>,explicitHeader:Boolean=false):RP<Blocks> = parseInlineFromText(top).map{title->
   val count=rows.firstOrNull()?.size?:0;val header=(field(fields,"header-rows")?.let{io.heapy.kotdoc.formats.HaskellNumber.readInteger(it)?.toLong()}?.let{it>0}?:explicitHeader)
   val heads=if(header)rows.take(1)else emptyList();val body=if(header)rows.drop(1)else rows
   val widths=when(val spec=field(fields,"widths")?.let(::trim)){"auto",null->List(count){0.0};else->numbers(spec).let{xs->xs.map{it/maxOf(1.0,xs.sum())}}}
   val base=RstTableAdapter.components(RstTableAdapter.Normalization.None,List(count){Alignment.AlignDefault},widths,heads.map{row->row.map{RstTableAdapter.Spanned(it)}},body.map{row->row.map{RstTableAdapter.Spanned(it)}})
   Blocks(listOf((base.values.single()as Block.Table).copy(caption=B.caption(null,B.plain(title)))))
  }
  fun listTableDirective(top:String,fields:List<Pair<String,String>>,body:String):RP<Blocks> = parseFromString(delay{parseBlocks()},body).flatMap{bs->
   val list=bs.values.singleOrNull()as? Block.BulletList;val rows=list?.items?.map{row->(row.singleOrNull()as? Block.BulletList)?.items?.map{Blocks(it)}?:emptyList()}?:emptyList()
   tableFromRows(top,fields,rows)
  }
  fun csvTableDirective(top:String,fields:List<Pair<String,String>>,body:String):RP<Blocks>{
   val explicit=field(fields,"header")?.let(::trim)
   fun single(s:String?)=s?.let(::trim)?.codePoints()?.toArray()?.singleOrNull()
   val delim=when(field(fields,"delim")?.let(::trim)){"tab"->9;"space"->32;else->single(field(fields,"delim"))?:44}
   val options=RstCsvOptions(delim,single(field(fields,"quote"))?:34,field(fields,"keepspace")?.let(::trim)=="true",single(field(fields,"escape")))
   return Parsec.lift<RstState,String>{(field(fields,"file")?:field(fields,"url"))?.let{io.heapy.kotdoc.resources.PandocUtf8.toText(effects.fetchItem(trim(it),runtime))}?:body}.flatMap{csv->
    fun parse(raw:String,opts:RstCsvOptions):List<List<String>>{val res=RstCsv.parse(opts,raw);if(!res.success)throw io.heapy.kotdoc.latex.LaTeXReader.fromParsecError(Sources.fromText(csv),res.error);return res.value!!}
    val rows=(explicit?.let{parse(it,RstCsvOptions())}?:emptyList())+parse(csv,options)
    sequence(rows.map{row->sequence(row.map(::parseCell))}).flatMap{tableFromRows(top,fields,it,explicit!=null)}.map(TableCompaction::compactify)
   }
  }
  fun readFileFromDirectory(source:String,file:String):String?{
   val paths=io.heapy.kotdoc.templates.PartialPaths.System;val path=paths.combine(paths.directory(source),file)
   return try{io.heapy.kotdoc.resources.PandocText.toTextM(io.heapy.kotdoc.layout.CharacterStringChars.fromString(path),effects.readFile(path))}catch(_:PandocError){null}
  }
  fun includeDirective(top:String,fields:List<Pair<String,String>>,body:String):RP<Blocks>{
   val file=trim(top);if(file.isEmpty()||trim(body).isNotEmpty())return zero()
   fun integer(key:String)=field(fields,key)?.let{io.heapy.kotdoc.formats.HaskellNumber.readInteger(it)?.toLong()}
   val start=integer("start-line");val end=integer("end-line");val lang=field(fields,"code")?:field(fields,"literal")
   return state().flatMap{old->
    if(file in old.user.includeFiles)throw PandocError.PandocParseError("Include file loop at "+old.position.toString())
    update{it.copy(includeFiles=listOf(file)+it.includeFiles)}.then(Parsec.lift<RstState,String>{
     val loaded=readFileFromDirectory(old.position.name,file)
     if(loaded==null){runtime.report(LogMessage.CouldNotLoadIncludeFile(file,old.position));""}else if(start==null&&end==null)loaded else{
      val lines=textLines(loaded);val first=start?.let{if(it>=0)it else lines.size+it}?:1;val last=end?.let{if(it>=0)it else lines.size+it}?:lines.size.toLong()
      lines.drop((first-1).coerceAtLeast(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).take((last-first+1).coerceAtLeast(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).let{selected->selected.joinToString("\n",postfix=if(selected.isEmpty())""else"\n")}
     }
    }).flatMap{contents->
     var lines=textLines(contents);field(fields,"start-after")?.let(::trim)?.let{pattern->lines=lines.dropWhile{pattern !in it}.drop(1)};field(fields,"end-before")?.let(::trim)?.let{pattern->lines=lines.takeWhile{pattern !in it}}
     val raw=lines.joinToString("\n",postfix=if(lines.isEmpty())""else"\n")+(if(lang==null)"\n"else"")
     val initial=SourcePos(file,start?:1,1)
     Parsec.modify<RstState>{it.copy(input=ParseInput(Sources(listOf(SourceChunk(SourcePos(file),raw)))),position=initial)}.then(if(lang!=null)pure(codeblock(field(fields,"name")?.let(::trimr)?:"",field(fields,"class")?.let(::words)?:emptyList(),fields,trimr(lang),false,raw))else delay{parseBlocks()}).before(Parsec.modify{it.copy(input=old.input,position=old.position,user=it.user.copy(includeFiles=it.user.includeFiles.drop(1)))})
    }
   }
  }
  fun dashedLine(ch:Char):RP<Pair<Int,Int>> = char(ch).many1().flatMap{ds->char(' ').many().map{ds.size to it.size}}
  fun simpleDashedLines(ch:Char):RP<List<Pair<Int,Int>>> = dashedLine(ch).many1().before(sp().skipMany()).before(newline()).map{xs->xs.mapIndexed{i,(n,s)->n to(n+if(i==xs.lastIndex)0 else s)}}.attempt()
  fun dashedIndices(ds:List<Pair<Int,Int>>):List<Int>{var n=0;return listOf(0)+ds.map{n+=it.second;n}}
  fun simpleTableFooter():RP<Unit> = simpleDashedLines('=').then(blanklines()).map{Unit}.attempt()
  fun colSpans(indices:List<Int>,spans:List<Int>)=RstReaderPure.colSpans(indices,spans)
  fun splitWidth(raw:String,width:Int):Pair<String,String>{var at=0;var n=0L;while(at<raw.length&&n<width){val cp=raw.codePointAt(at);at+=Character.charCount(cp);n+=io.heapy.kotdoc.layout.DisplayWidth.charWidth(cp)}
return raw.substring(0,at) to raw.substring(at)}
  fun splitTableLine(indices:List<Int>,line:Pair<String,List<Int>?>):List<Pair<String,Long>>?{
   val explicit=line.second;val cuts=explicit?:indices;val spans=if(explicit==null)List(indices.size){1L}else colSpans(indices,explicit)
   if(spans==null)return null
   val pieces=mutableListOf<String>();var raw=line.first;var previous=0
   for(index in cuts.dropLast(1)){val(a,b)=splitWidth(raw,index-previous);pieces+=trimr(a);raw=b;previous=index};pieces+=trimr(raw)
   return pieces.drop(1).zip(spans)
  }
  fun rowWithOptionalColSpan():RP<Pair<String,List<Int>?>> = anyLine().flatMap{line->simpleDashedLines('-').map<List<Pair<Int,Int>>?>{it}.option(null).map{line to it?.let(::dashedIndices)}}
  fun simpleTableRawLine(indices:List<Int>):RP<List<Pair<String,Long>>> = rowWithOptionalColSpan().flatMap{row->splitTableLine(indices,row)?.let(::pure)?:Parsec.fail("col spans don't match")}
  fun parseCell(raw:String):RP<Blocks> = parseFromString(delay{parseBlocks()},trim(raw)+"\n\n")
  fun simpleTableRow(indices:List<Int>):RP<List<RstTableAdapter.Spanned>> = notPrime(blanklines().map{Unit} or simpleTableFooter()).then(simpleTableRawLine(indices)).flatMap{first->
   simpleTableRawLine(indices).flatMap{cells->guard(cells.firstOrNull()?.first?.all{it==' '||it=='\t'}==true).map{cells}}.attempt().many().flatMap{continuations->
    val rows=listOf(first.map{it.first})+continuations.map{row->row.map{it.first}}+(if(continuations.isEmpty())emptyList()else listOf(List(indices.size){""}))
    val columns=(0 until(rows.maxOfOrNull{it.size}?:0)).map{i->rows.mapNotNull{it.getOrNull(i)}.joinToString("\n",postfix="\n")}
    fun loop(i:Int):RP<List<Blocks>> = if(i==columns.size)pure(emptyList())else parseCell(columns[i]).flatMap{b->delay{loop(i+1)}.map{listOf(b)+it}}
    delay{loop(0)}.map{cells->cells.zip(first){b,f->RstTableAdapter.Spanned(b,1,f.second)}}
   }
  }
  data class SimpleHeader(val rows:List<List<RstTableAdapter.Spanned>>,val aligns:List<Alignment>,val indices:List<Int>)
  fun simpleTableHeader(headless:Boolean):RP<SimpleHeader> = blanklines().optional().then(simpleDashedLines('=')).flatMap{ds->
   val content=if(headless)pure(listOf("" to null))else not(simpleDashedLines('=')).then(rowWithOptionalColSpan()).many1()
   content.before(if(headless)pure(Unit)else simpleDashedLines('=').map{Unit}).flatMap{raw->
    val indices=dashedIndices(ds);val heads=if(headless)emptyList()else raw.map{splitTableLine(indices,it)}
    if(heads.any{it==null})Parsec.fail("col spans don't match")else{
     fun row(cells:List<Pair<String,Long>>):RP<List<RstTableAdapter.Spanned>>{
      fun loop(i:Int):RP<List<RstTableAdapter.Spanned>> = if(i==cells.size)pure(emptyList())else parseFromString(delay{plain()}.many().map(::blocks),trim(cells[i].first)).flatMap{b->delay{loop(i+1)}.map{listOf(RstTableAdapter.Spanned(b,1,cells[i].second))+it}}
      return delay{loop(0)}
     }
     fun loop(i:Int):RP<List<List<RstTableAdapter.Spanned>>> = if(i==heads.size)pure(emptyList())else row(heads[i]!!).flatMap{h->delay{loop(i+1)}.map{listOf(h)+it}}
     delay{loop(0)}.map{SimpleHeader(it,List(ds.size){Alignment.AlignDefault},indices)}
    }
   }
  }.attempt()
  fun simpleTable(headless:Boolean):RP<Blocks> = simpleTableHeader(headless).flatMap{h->simpleTableRow(h.indices).many1().before(simpleTableFooter()).map{rows->RstTableAdapter.components(RstTableAdapter.Normalization.None,h.aligns,List(h.aligns.size){0.0},h.rows,rows)}}.attempt()
  fun table():RP<Blocks> = (RstTableAdapter.grid(RstTableAdapter.Normalization.None,{s:RstState->s.options.columns},blanklines()){raw->parseFromString(delay{parseBlocks()},raw)} or simpleTable(false) or simpleTable(true)).label("table").map(TableCompaction::compactify)
  fun blocksToInlines(bs:List<Block>)=RstBlockInlines.convert(bs)
  fun enabled(name:String)=state().map{Extension.read(name)in it.user.options.extensions}
  fun last()=ParserCapabilities.updateLastStrPos(RstAccess)
 }
}
