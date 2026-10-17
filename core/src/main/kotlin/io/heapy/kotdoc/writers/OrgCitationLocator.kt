// Text.Pandoc.Citeproc.Locator / Util.splitStrWhen, Copyright (C) John MacFarlane.
// GPL-2.0-or-later; complete pinned sources retained in upstream/pandoc.
// text-2.1.2 case folding: Bryan O'Sullivan, Duncan Coutts, Tom Harper,
// Andrew Lelechenko; BSD-2-Clause, licenses/text-BSD-2-Clause.txt.
package io.heapy.kotdoc.writers

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.latex.LaTeXReaderText
import kotlinx.serialization.json.*

private typealias LP<A> = TokenParsec<Inline,Unit,A>
internal data class OrgLocatorInfo(val raw:String,val label:String,val locator:String)
internal object OrgCaseFold {
 private val mappings by lazy {Json.parseToJsonElement(checkNotNull(javaClass.getResource("/io/heapy/kotdoc/org-casefold-2.1.2.json")).readText()).jsonObject.mapKeys{it.key.toInt(16)}.mapValues{it.value.jsonPrimitive.content}}
 fun fold(value:String):String=buildString{TextChars.fromString(value).codePoints().forEach{cp->mappings[cp]?.let(::append)?:appendCodePoint(cp)}}
}
/** Only the writer-used parseLocator closure; CSL Locale conversion is separate. */
internal object OrgCitationLocator {
 val labels:Map<String,String> = mapOf(
  "bk." to "book",
  "bks." to "book",
  "book" to "book",
  "chap." to "chapter",
  "chaps." to "chapter",
  "chapter" to "chapter",
  "col." to "column",
  "cols." to "column",
  "column" to "column",
  "figure" to "figure",
  "fig." to "figure",
  "figs." to "figure",
  "folio" to "folio",
  "fol." to "folio",
  "fols." to "folio",
  "number" to "number",
  "no." to "number",
  "nos." to "number",
  "line" to "line",
  "l." to "line",
  "ll." to "line",
  "note" to "note",
  "n." to "note",
  "nn." to "note",
  "opus" to "opus",
  "op." to "opus",
  "opp." to "opus",
  "page" to "page",
  "p" to "page",
  "p." to "page",
  "pp." to "page",
  "paragraph" to "paragraph",
  "para." to "paragraph",
  "paras." to "paragraph",
  "¶" to "paragraph",
  "¶¶" to "paragraph",
  "part" to "part",
  "pt." to "part",
  "pts." to "part",
  "§" to "section",
  "§§" to "section",
  "section" to "section",
  "sec." to "section",
  "secs." to "section",
  "sub verbo" to "sub verbo",
  "s.v." to "sub verbo",
  "s.vv." to "sub verbo",
  "verse" to "verse",
  "v." to "verse",
  "vv." to "verse",
  "volume" to "volume",
  "vol." to "volume",
  "vols." to "volume"
 )
 private fun <A> pure(a:A):LP<A> = TokenParsec.pure(a)
 private fun <A> delay(f:()->LP<A>):LP<A> = TokenParsec.defer(f)
 private fun <A> zero():LP<A> = TokenParsec.zero()
 private fun guard(ok:Boolean):LP<Unit> = if(ok)pure(Unit)else zero()
 // parseLocator discards parse errors; token Show is deliberately not forced.
 private fun any():LP<Inline> = TokenParsec.anyToken{ "" }
 private fun satisfy(name:String,f:(Inline)->Boolean):LP<Inline> = TokenParsec.tokenPrim<Inline,Unit,Inline>({""},{p,_,_->p}){if(f(it))TokenMatch.Accept(it)else TokenMatch.Reject}.label(name)
 private fun char(name:String,p:(Int)->Boolean)=satisfy(name){it is Inline.Str&&it.text.codePoints().toArray().singleOrNull()?.let(p)==true}
 private fun symbol(c:Char)=char(c.toString()){it==c.code}
 private fun <A> not(p:LP<A>)=p.notFollowedBy{""}
 private fun <A,B> till(p:LP<A>,end:LP<B>)=TokenParsec.manyTill(p,end)
 private fun remaining():LP<List<Inline>> = TokenParsec.state<Inline,Unit>().map{(it.input as ListTokenStream<Inline>).remaining()}
 private fun stringify(xs:List<Inline>)=DocumentShared.stringifyInlines(xs)
 private fun punct(cp:Int)=RegexCharacters.categoryName(cp)in setOf("Pc","Pd","Ps","Pe","Pi","Pf","Po")
 private fun locatorPunct(cp:Int)=cp!=45&&cp!=0x2013&&cp!=58&&punct(cp)
 private fun pSpace()=satisfy("space"){it==Inline.Space||it==Inline.SoftBreak||it==Inline.Str("\u00a0")}
 private fun pMath()=satisfy(""){it is Inline.Math}
 private fun pPunct()=char("punctuation",::locatorPunct)
 private fun pSep()=char("locator separator"){it==44||it==59}
 fun split(input:List<Inline>):List<Inline> = buildList {
  fun separate(cp:Int)=ParserCharacters.isSpace(cp)||(punct(cp)&&cp!=58)
  for(i in input)if(i is Inline.Str){val cps=i.text.codePoints().toArray();var at=0;while(at<cps.size){val start=at++;while(at<cps.size&&!separate(cps[at-1])&&!separate(cps[at]))at++;add(Inline.Str(String(cps,start,at-start)))}}else add(i)
 }
 fun maybeAddComma(input:List<Inline>):List<Inline> = when(val first=input.firstOrNull()){
  null->input;Inline.Space->input
  is Inline.Str->if(first.text.codePoints().findFirst().orElse(-1).let{it>=0&&punct(it)})input else listOf(Inline.Str(","),Inline.Space)+input
  else->listOf(Inline.Str(","),Inline.Space)+input
 }
 fun parse(input:List<Inline>,map:Map<String,String> = labels):Pair<OrgLocatorInfo?,List<Inline>> {
  val reply=words(map).run(TokenParseState(ListTokenStream(split(input)),SourcePos("suffix"),Unit))
  return if(reply.success)reply.value!! else null to maybeAddComma(input)
 }
 private fun words(map:Map<String,String>):LP<Pair<OrgLocatorInfo?,List<Inline>>> = symbol(',').optional().then(pSpace().optional()).then(delimited(map)or integrated(map)).flatMap{info->remaining().map{rest->if(info.label.isEmpty()&&info.locator.isEmpty())null to maybeAddComma(rest)else info to rest}}
 private data class Label(val raw:String,val label:String,val implicit:Boolean)
 private fun label(map:Map<String,String>,limit:LP<String>):LP<Label>{
  fun go(acc:String):LP<Label> = any().flatMap{first->till(any(),limit.lookAhead()).flatMap{rest->val value=acc+stringify(listOf(first)+rest);val key=OrgCaseFold.fold(value.trim{ParserCharacters.isSpace(it.code)});val found=map[key];if(found!=null)delay{go(value)}or pure(Label(value,found,false))else delay{go(value)}}}.attempt()
  return delay{go("")}
 }
 private fun labelDelimited(map:Map<String,String>):LP<Label> = label(map,any().map{stringify(listOf(it))})or char("digit",ParserCharacters::isDigit).lookAhead().map{Label("","page",true)}or pure(Label("","",true))
 private fun digits(v:Pair<Boolean,String>):LP<String> = if(v.second.codePoints().anyMatch(ParserCharacters::isDigit))pure(v.second)else TokenParsec.fail("requireDigits")
 private fun romansOrDigits(v:Pair<Boolean,String>):LP<String> = if(v.first)pure(v.second)else TokenParsec.fail("requireRomansOrDigits")
 private fun labelIntegrated(map:Map<String,String>):LP<Label> = label(map,word(true).flatMap(::romansOrDigits).attempt())or word(true).flatMap(::digits).attempt().lookAhead().map{Label("","page",true)}
 private fun integrated(map:Map<String,String>):LP<OrgLocatorInfo> = labelIntegrated(map).flatMap{lab->
  val modify:(Pair<Boolean,String>)->LP<String> = if(lab.implicit)::digits else ::romansOrDigits
  word(!lab.implicit).flatMap(modify).attempt().flatMap{first->word(false).flatMap(modify).attempt().many().map{rest->val loc=first+rest.joinToString("");OrgLocatorInfo(lab.raw+loc,lab.label,loc)}}
 }.attempt()
 private fun delimited(map:Map<String,String>):LP<OrgLocatorInfo> = symbol('{').then(pSpace().skipMany()).then(labelDelimited(map)).flatMap{lab->balanced(listOf('{' to '}','[' to ']'),any().map{true to cslJsonify(listOf(it))}).many().before(symbol('}')).map{groups->val loc=groups.joinToString(""){it.second};OrgLocatorInfo(lab.raw+"{"+loc+"}",lab.label,loc)}}.attempt()
 private fun combine(xs:List<Pair<Boolean,String>>)=xs.any{it.first}to xs.joinToString(""){it.second}
 private fun balanced(braces:List<Pair<Char,Char>>,body:LP<Pair<Boolean,String>>):LP<Pair<Boolean,String>>{
  val chars=braces.flatMap{listOf(it.first.code,it.second.code)};val except=not(char("braces"){it in chars}).then(body)
  var surround=except
  for((open,close)in braces){val other=surround;surround=symbol(open).then(except.option(false to "")).before(symbol(close)).map{(digit,mid)->digit to(open+mid+close)}.attempt()or other}
  return surround.many1().map(::combine).attempt()
 }
 private fun word(first:Boolean):LP<Pair<Boolean,String>> = (if(first)pure("")else pSep().map{stringify(listOf(it))}.option("")).flatMap{punct->pSpace().map{" "}.option("").flatMap{space->balanced(listOf('(' to ')','[' to ']','{' to '}'),delay{pageSeq()}).map{it.first to(punct+space+it.second)}}}.attempt()
 private fun pageSeq():LP<Pair<Boolean,String>> {
  val period=symbol('.').then(delay{pageUnit()}.attempt()).map{it.first to("."+it.second)}.attempt()
  return delay{pageUnit()}.flatMap{first->period.many().map{combine(listOf(first)+it)}}or period
 }
 private fun roman():LP<Pair<Boolean,String>> = any().flatMap{token->if(token !is Inline.Str)zero()else{
  val text=LaTeXReaderText.toUpper(token.text);val result=ListParsing.romanNumeral<Unit>(true).before(Parsec.eof()).run(ParseState(ParseInput(text),SourcePos("roman numeral"),Unit))
  if(result.success)pure(true to token.text)else zero()
 }}.attempt()
 private fun pageUnit():LP<Pair<Boolean,String>> = roman()or not(pSpace()).then(not(pPunct())).then(not(pMath())).then(any()).many1().map{tokens->val text=stringify(tokens);text.codePoints().anyMatch(ParserCharacters::isDigit)to text}
 fun cslJsonify(input:List<Inline>):String=buildString {
  val work=java.util.ArrayDeque<Any>();for(i in input.asReversed())work.addLast(i)
  fun children(xs:List<Inline>,open:String="",close:String=""){append(open);if(close.isNotEmpty())work.addLast(close);for(i in xs.asReversed())work.addLast(i)}
  while(work.isNotEmpty())when(val value=work.removeLast()){
   is String->append(value)
   is Inline.Str->append(value.text)
   Inline.Space,Inline.SoftBreak,Inline.LineBreak->append(' ')
   is Inline.Emph->children(value.content,"<i>","</i>")
   is Inline.Underline->children(value.content,"<u>","</u>")
   is Inline.Strong->children(value.content,"<b>","</b>")
   is Inline.Strikeout->children(value.content)
   is Inline.Superscript->children(value.content,"<sup>","</sup>")
   is Inline.Subscript->children(value.content,"<sub>","</sub>")
   is Inline.SmallCaps->children(value.content,"<span style=\"font-variant:small-caps;\">","</span>")
   is Inline.Quoted->{val q=if(value.type==QuoteType.SingleQuote)"'"else"\"";children(value.content,q,q)}
   is Inline.Cite->children(value.content)
   is Inline.Code->append(value.text)
   is Inline.Math->append(value.text)
   is Inline.RawInline,is Inline.Note->Unit
   is Inline.Link->children(value.content)
   is Inline.Image->children(value.content)
   is Inline.Span->when{
    value.attr.classes.isNotEmpty()->children(value.content,"<span class=\""+value.attr.classes.joinToString(" ")+"\">","</span>")
    value.attr.keyValues==listOf("style" to "baseline")->children(value.content,"<span style=\"baseline\">","</span>")
    else->children(value.content)
   }
  }
 }
}
