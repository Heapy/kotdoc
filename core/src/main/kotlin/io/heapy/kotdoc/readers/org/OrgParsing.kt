// Text.Pandoc.Readers.Org.Parsing, Copyright (C) 2014-2025 Albert Krewinkel.
// GPL-2.0-or-later; original source/notices retained in upstream/pandoc.
package io.heapy.kotdoc.readers.org
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.state.*
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.layout.CharacterStringChars
internal class OrgGrammar(val runtime:PandocState,val dataRuntime:io.heapy.kotdoc.resources.PandocDataRuntime=OrgPureData(runtime),val zonedDay:()->String={"1970-01-01"}){
 val latex by lazy { io.heapy.kotdoc.latex.LaTeXReader(dataRuntime,zonedDay) }
 fun <A> pure(x:A):OP<A> = ReaderParsec.pure(x)
 fun <A> lift(p:Parsec<OrgState,A>):OP<A> = ReaderParsec.lift(p)
 fun <A> delay(p:()->OP<A>):OP<A> = ReaderParsec.defer(p)
 fun <A> zero():OP<A> = ReaderParsec.zero()
 fun <A> fail(s:String):OP<A> = ReaderParsec.fail(s)
 fun <A> future(x:A):OF<A> = Future.pure(x)
 fun state()=lift(Parsec.state<OrgState>())
 fun update(f:(OrgState)->OrgState)=lift(Parsec.modify<OrgState>{it.copy(user=f(it.user))})
 fun guard(b:Boolean)=lift(Parsec.guard<OrgState>(b))
 fun char(c:Char)=lift(SourceParsers.char<OrgState>(c.code))
 fun text(s:String)=lift(SourceParsers.string<OrgState>(s))
 fun satisfy(f:(Int)->Boolean)=lift(SourceParsers.satisfy<OrgState>(f))
 fun any()=lift(SourceParsers.anyChar<OrgState>())
 fun take(f:(Int)->Boolean)=lift(SourceParsers.takeWhileP<OrgState>(f))
 fun take1(f:(Int)->Boolean)=lift(SourceParsers.takeWhile1P<OrgState>(f))
 fun eof()=lift(Parsec.eof<OrgState>())
 fun sp()=satisfy{it==32||it==9}
 fun skipSpaces()=sp().skipMany()
 fun skipSpaces1()=sp().many1().map{Unit}
 fun space()=lift(SourceParsers.space<OrgState>())
 fun nonspace()=satisfy{it !in intArrayOf(32,9,10,13)}
 fun chars(xs:List<Int>)=buildString{xs.forEach(::appendCodePoint)}
 fun rawNewline()=lift(SourceParsers.newline<OrgState>())
 fun updatePre()=state().flatMap{s->update{it.copy(lastPre=s.position)}}
 fun updateForbidden()=state().flatMap{s->update{it.copy(lastForbidden=s.position)}}
 fun updateStr()=state().flatMap{s->update{it.copy(lastStr=s.position)}}
 fun newline()=rawNewline().before(updatePre()).before(updateForbidden())
 fun anyLine():OP<String> = state().flatMap{s->
  val stream=s.input.stream as Sources
  val chain=stream.unSources.value()
  if(chain !is DeferredChain.Cons)pure<OP<String>>(zero()).flatMap{it}else{
   val first=chain.head.value();val value=first.deferredText.value();val at=value.backing.indexOf('\n',value.start).takeIf{it>=0&&it<value.end}
   if(at==null&&chain.tail.value()!==DeferredChain.Nil)till(any(),rawNewline()).map(::chars)else{
    val rest=SourceText(value.backing,at?:value.end,value.end)
    val input=Sources(Deferred.now(DeferredChain.Cons(Deferred.now(first.withText(rest)),chain.tail)))
    lift(Parsec.modify<OrgState>{it.copy(input=ParseInput(input))}).then(rawNewline()).map{value.backing.substring(value.start,at?:value.end)}
   }
  }
 }.before(updatePre()).before(updateForbidden())
 fun anyLineNewline()=anyLine().map{it+"\n"}
 fun blankline()=skipSpaces().then(rawNewline()).attempt()
 fun blanklines()=blankline().many1().map(::chars).before(updatePre()).before(updateForbidden())
 fun <A> maybe(p:OP<A>):OP<A?> = p.map<A?>{it}.option(null)
 fun <A> choice(ps:List<OP<A>>):OP<A> = ps.reduceOrNull{a,b->a or b}?:zero()
 fun oneOf(s:String)=satisfy{it in s.codePoints().toArray()}
 fun noneOf(s:String)=satisfy{it !in s.codePoints().toArray()}
 fun <A> not(p:OP<A>):OP<Unit> = (p.attempt().map{lift(Parsec.unexpected<OrgState,Unit>(when(it){is Int->Parsec.showChar(it);is Unit->"()";else->it.toString()}))} or pure(pure(Unit))).flatMap{it}.attempt()
 class Chain<A>(val value:A,val next:Chain<A>?)
 fun <A> list(xs:Chain<A>?):List<A> = buildList{var at=xs;while(at!=null){add(at.value);at=at.next}}
 fun <A,B> till(p:OP<A>,end:OP<B>):OP<List<A>>{fun loop():OP<Chain<A>?> = end.map<Chain<A>?>{null} or p.flatMap{x->delay{loop()}.map{Chain(x,it)}}
 return delay{loop()}.map(::list)}
 fun <A,B> until(p:OP<A>,end:OP<B>):OP<Pair<List<A>,B>>{
  fun loop():OP<Pair<Chain<A>?,B>> = end.map<Pair<Chain<A>?,B>>{null to it} or p.flatMap{x->delay{loop()}.map{(xs,e)->Chain(x,xs)to e}}
  return delay{loop()}.map{(xs,e)->list(xs)to e}
 }
 fun anyCase(s:String):OP<String>{
  val points=s.codePoints().toArray()
  fun loop(i:Int):OP<Chain<Int>?> = if(i==points.size)pure(null)else{
   val upper=lift(SourceParsers.char<OrgState>(RegexCharacters.toUpper(points[i])))
   val lower=lift(SourceParsers.char<OrgState>(RegexCharacters.toLower(points[i])))
   (upper or lower).flatMap{c->delay{loop(i+1)}.map{Chain(c,it)}}
  }
  return delay{loop(0)}.map{chars(list(it))}
 }

 fun <A,B> till1(p:OP<A>,end:OP<B>)=not(end).then(p).flatMap{x->till(p,end).map{listOf(x)+it}}
 fun <A> sequence(xs:List<OF<A>>):OF<List<A>>{var out:OF<Chain<A>?> = future(null);for(x in xs.asReversed())out=x.liftA2(out){a,b->a.flatMapValue{av->b.mapValue{Chain(av,it)}}};return out.mapValue(::list)}
 fun inlines(xs:List<OF<Inlines>>)=sequence(xs).mapValue{it.fold(Inlines(),Inlines::plus)}
 fun blocks(xs:List<OF<Blocks>>)=sequence(xs).mapValue{Blocks(it.flatMap(Blocks::values))}
 fun <A,B,C> f2(a:OF<A>,b:OF<B>,f:(A,B)->C):OF<C> = a.liftA2(b){x,y->x.flatMapValue{xx->y.mapValue{yy->f(xx,yy)}}}
 fun <A> context(inList:Boolean,p:OP<A>):OP<A> = state().flatMap{s->update{it.copy(listContext=inList)}.then(p).before(update{it.copy(listContext=s.user.listContext)})}
 fun <A> parseFromString(p:OP<A>,raw:String):OP<A> = update{it.copy(lastPre=null)}.then(state()).flatMap{s->
  lift(Parsec.modify<OrgState>{it.copy(input=ParseInput(Sources.fromText(raw)),position=s.position.copyRaw(name=CharacterStringChars.concat(listOf(s.position.nameRaw,CharacterStringChars.fromString("_chunk"))),line=1,column=1))}).then(p).flatMap{a->
   lift(Parsec.modify<OrgState>{it.copy(input=s.input,position=s.position,user=it.user.copy(lastPre=null))}).map{a}}
 }
 fun argWordChar()=satisfy{ParserCharacters.isAlphaNum(it)||it in intArrayOf(45,95)}
 fun argWord()=argWordChar().many1().map(::chars)
 fun argKey()=skipSpaces().then(char(':')).then(argWord()).attempt()
 fun tagWord()=satisfy{ParserCharacters.isAlphaNum(it)||it in intArrayOf(64,37,35,95)}.many1().map(::chars)
 fun anchor()=text("<<").then(take1{it !in "\t\n\r<>\"' ".codePoints().toArray()}).before(text(">>")).before(skipSpaces()).flatMap{id->update{it.copy(anchorIds=it.anchorIds+id)}.map{id}}.attempt()
}
