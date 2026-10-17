// Text.Pandoc.CSV, Copyright (C) 2017-2024 John MacFarlane.
// GPL-2.0-or-later; original full source retained with development oracle.
package io.heapy.kotdoc.readers
import io.heapy.kotdoc.parsing.*

internal data class RstCsvOptions(val delimiter:Int=44,val quote:Int?=34,val keepSpace:Boolean=false,val escape:Int?=null)
internal object RstCsv {
 fun parser(options:RstCsvOptions):Parsec<Unit,List<List<String>>>{
  fun char(cp:Int)=SourceParsers.char<Unit>(cp)
  val end=char(13).optional().then(char(10)).map{Unit}
  val escaped=if(options.escape==null)options.quote?.let{char(it).then(char(it)).attempt()}?:Parsec.zero()else char(options.escape).then(SourceParsers.satisfy{it!=13&&it!=10}).attempt()
  val quoted=options.quote?.let{q->char(q).then((SourceParsers.satisfy<Unit>{it!=q&&it!=options.escape} or escaped).many()).before(char(q)).map{xs->buildString{xs.forEach(::appendCodePoint)}}}?:Parsec.zero()
  val unquoted=SourceParsers.satisfy<Unit>{it!=options.delimiter&&it!=13&&it!=10}.many().map{xs->buildString{xs.forEach(::appendCodePoint)}}
  val cell=quoted or unquoted
  val delim=char(options.delimiter).then(if(options.keepSpace)Parsec.pure(Unit)else SourceParsers.satisfy<Unit>{it==32||it==9&&options.delimiter!=9}.skipMany())
  val row=cell.flatMap{first->val rest=delim.then(cell);(if(first.isEmpty())rest.many1()else rest.many()).map{listOf(first)+it}}
  class Chain(val value:List<String>,val next:Chain?)
  fun rest():Parsec<Unit,Chain?> = row.flatMap{value->end.then(Parsec.defer{rest()}).option(null).map<Chain?>{Chain(value,it)}} or Parsec.pure(null)
  return Parsec.defer{rest()}.before(SourceParsers.spaces<Unit>()).before(Parsec.eof()).map{chain->buildList{var at=chain;while(at!=null){add(at.value);at=at.next}}}
 }
 fun parse(options:RstCsvOptions,text:String)=parser(options).run(ParseState(ParseInput(TextStream(text)),SourcePos("csv"),Unit))
}
