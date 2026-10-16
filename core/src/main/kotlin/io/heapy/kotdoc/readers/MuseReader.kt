// Text.Pandoc.Readers.Muse, Pandoc 3.12.1.
// Copyright (C) 2017-2020 Alexander Krotov; GPL version 2 or later.
// Narrow Parsing.General helpers: Copyright (C) 2006-2024 John MacFarlane.
package io.heapy.kotdoc.readers

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.ast.Builder as B
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.state.PandocState
import io.heapy.kotdoc.state.PandocStateContext

private typealias MF<A> = Future<MuseState,A>
private typealias MP<A> = ReaderParsec<MuseEnvironment,MuseState,A>
private data class MuseEnvironment(val inLink:Boolean=false,val inPara:Boolean=false)
private data class MuseNote(val position:SourcePos,val contents:MF<Blocks>)
private data class MuseState(
    val options:ReaderOptions=ReaderOptions(),val meta:MF<Map<String,MetaValue>> = Future.pure(emptyMap()),
    val identifiers:Set<String> = emptySet(),val lastSpace:SourcePos?=null,val lastStr:SourcePos?=null,
    val messages:List<LogMessage> = emptyList(),val notes:Map<String,MuseNote> = emptyMap(),
)
object MuseReader {
    fun read(options:ReaderOptions,text:String,runtime:PandocState=PandocStateContext()):Pandoc=read(options,Sources.fromText(text),runtime)
    fun read(options:ReaderOptions,sources:Sources,runtime:PandocState=PandocStateContext()):Pandoc {
        val initial=ParseState(ParseInput(sources),SourcePos(sources.initialSourceName()),MuseState(options=options))
        val result=Grammar(runtime).document().run(initial,MuseEnvironment())
        if(!result.success)throw io.heapy.kotdoc.latex.LaTeXReader.fromParsecError(sources,result.error)
        return checkNotNull(result.value)
    }
    private class Grammar(val runtime:PandocState) {
        fun <A> pure(x:A):MP<A> = ReaderParsec.pure(x)
        fun <A> lift(x:Parsec<MuseState,A>):MP<A> = ReaderParsec.lift(x)
        fun <A> delay(f:()->MP<A>):MP<A> = ReaderParsec.defer(f)
        fun <A> future(x:A):MF<A> = Future.pure(x)
        fun <A,B,C> f2(a:MF<A>,b:MF<B>,f:(A,B)->C):MF<C> = a.liftA2(b){x,y->x.flatMapValue { xx->y.mapValue { yy->f(xx,yy) } }}
        class Chain<A>(val value:A,val next:Chain<A>?)
        fun <A> chainList(chain:Chain<A>?):List<A> = buildList { var c=chain;while(c!=null){add(c.value);c=c.next} }
        fun <A> prepend(x:A,xs:List<A>):List<A> = MuseConcatList(listOf(x),xs)
        fun <A> sequence(xs:List<MF<A>>):MF<List<A>> {
            var result:MF<Chain<A>?> = future(null)
            for(x in xs.toList().asReversed())result=f2(x,result){a,b->Chain(a,b)}
            return result.mapValue(::chainList)
        }
        fun inlines(xs:List<MF<Inlines>>):MF<Inlines> = sequence(xs).mapValue { it.fold(Inlines(),Inlines::plus) }
        fun blocks(xs:List<MF<Blocks>>):MF<Blocks> = sequence(xs).mapValue { Blocks(it.flatMap(Blocks::values)) }
        fun append(a:MF<Blocks>,b:MF<Blocks>):MF<Blocks> = f2(a,b) { x,y->Blocks(MuseConcatList(x.values,y.values)) }
        fun trim(x:MF<Inlines>):MF<Inlines> = x.mapValue(Inlines::trim)
        fun guard(value:Boolean):MP<Unit> = lift(Parsec.guard(value))
        fun char(c:Char)=lift(SourceParsers.char<MuseState>(c.code))
        fun text(s:String)=lift(SourceParsers.string<MuseState>(s))
        fun satisfy(f:(Int)->Boolean)=lift(SourceParsers.satisfy<MuseState>(f))
        fun any()=lift(SourceParsers.anyChar<MuseState>())
        fun state()=lift(Parsec.state<MuseState>())
        fun update(f:(MuseState)->MuseState)=lift(Parsec.modify<MuseState>{it.copy(user=f(it.user))})
        fun eof()=lift(Parsec.eof<MuseState>())
        fun newline()=lift(SourceParsers.newline<MuseState>())
        fun sp()=satisfy{it==32||it==9}
        fun space()=lift(SourceParsers.space<MuseState>())
        fun nonspace()=satisfy{it !in intArrayOf(32,9,10,13)}
        fun eol()=newline().map{Unit} or eof()
        fun blankline()=sp().skipMany().then(newline()).attempt()
        fun firstColumn()=state().flatMap{guard(it.position.column==1L)}
        fun getIndent()=sp().many().then(state()).map{it.position.column-1}
        fun enabled(name:String)=state().map{Extension.read(name) in it.user.options.extensions}
        fun guardEnabled(name:String)=enabled(name).flatMap(::guard)
        fun guardDisabled(name:String)=enabled(name).flatMap{guard(!it)}
        fun chars(xs:List<Int>)=buildString{xs.forEach(::appendCodePoint)}
        fun take(f:(Int)->Boolean)=lift(SourceParsers.takeWhileP<MuseState>(f))
        fun take1(f:(Int)->Boolean)=lift(SourceParsers.takeWhile1P<MuseState>(f))
        fun <A> show(x:A):String=when(x){Unit->"()";is Int->Parsec.showChar(x);is String->AstShow.show(x);else->error("Unhandled parser show: $x")}
        fun <A> not(p:MP<A>):MP<Unit> = (p.attempt().map{lift(Parsec.unexpected<MuseState,Unit>(show(it)))} or pure(pure(Unit))).flatMap{it}.attempt()
        fun <A,C> until(p:MP<A>,end:MP<C>):MP<Pair<List<A>,C>> {
            fun go():MP<Pair<Chain<A>?,C>> = end.map<Pair<Chain<A>?,C>>{null to it} or p.flatMap{x->delay{go()}.map<Pair<Chain<A>?,C>>{(xs,e)->Chain(x,xs) to e}}
            return delay{go()}.map{(xs,e)->chainList(xs) to e}
        }
        fun <A,C> till(p:MP<A>,end:MP<C>)=until(p,end).map{it.first}
        fun <A,C> till1(p:MP<A>,end:MP<C>)=not(end).then(p).flatMap{x->till(p,end).map{listOf(x)+it}}
        fun <A> maybe(p:MP<A>):MP<A?> = p.map<A?>{it}.option(null)
        fun updateSpace()=state().flatMap{s->update{it.copy(lastSpace=s.position)}}
        fun updateStr()=state().flatMap{s->update{it.copy(lastStr=s.position)}}
        fun atStart()=state().flatMap{guard(it.user.lastStr!=it.position)}
        fun noSpaceBefore()=state().flatMap{guard(it.user.lastSpace!=it.position)}
        fun <A> allow(p:MP<A>)=p.localValue{it.copy(inPara=false)}
        fun indent(n:Long):MP<Unit> = state().flatMap{s->
            val count=count(n,char(' ')).map{Unit}
            if(n<s.user.options.tabStop)count else count.attempt() or char('\t').then(delay{indent(n-s.user.options.tabStop)}).attempt()
        }
        fun openTag(tag:String):MP<List<Pair<String,String>>> {
            val attr=sp().many1().then(take1{it!=61&&it!=10}).before(text("=\"")).flatMap{k->till(satisfy{it!=34},char('"')).map{k to chars(it)}}.attempt()
            return char('<').then(text(tag)).then(till(attr,char('>'))).attempt()
        }
        fun closeTag(tag:String)=text("</").then(text(tag)).then(char('>')).map{Unit}.attempt()
        fun attributes(xs:List<Pair<String,String>>)=Attr(xs.firstOrNull{it.first=="id"}?.second?:"",sourceWords(xs.firstOrNull{it.first=="class"}?.second?:""),xs.filter{it.first!in listOf("id","class")})
        fun sourceWords(s:String):List<String> {val words=mutableListOf<String>();val word=StringBuilder()
            for(point in s.codePoints().toArray())if(ParserCharacters.isSpace(point)){if(word.isNotEmpty()){words+=word.toString();word.setLength(0)}}else word.appendCodePoint(point)
            if(word.isNotEmpty())words+=word.toString();return words
        }
        fun parseAnchor():MP<String> = firstColumn().then(char('#')).then(satisfy(ParserCharacters::isAlpha)).flatMap{c->take{ParserCharacters.isAlpha(it)||ParserCharacters.isDigit(it)||it==45}.map{chars(listOf(c))+it}}.attempt()
        fun registerHeader(id:String,contents:Inlines):MP<Attr> = state().flatMap{s->
            val exts=s.user.options.extensions
            if(id.isEmpty()&&Extension.read("auto_identifiers") in exts){
                val generated=DokuWikiIdentifiers.unique(contents.values,s.user.identifiers,exts)
                val ascii=if(Extension.read("ascii_identifiers") in exts)Asciify.toAsciiText(generated) else generated
                update{it.copy(identifiers=it.identifiers+generated+ascii)}.map{Attr(ascii)}
            }else update{old->old.copy(identifiers=if(id.isEmpty())old.identifiers else old.identifiers+id,messages=if(id.isNotEmpty()&&id in old.identifiers)old.messages+LogMessage.DuplicateIdentifier(id,s.position) else old.messages)}.map{Attr(id)}
        }
        fun whitespace()=sp().many1().then(updateSpace()).map{future(B.space)}.attempt()
        fun endline()=newline().before(not(blankline())).then(updateSpace()).map{future(B.softbreak)}.attempt()
        val inline:MP<MF<Inlines>> get()=delay{endline() or inlinePrime}
        val inlinePrime:MP<MF<Inlines>> get()=delay{
            whitespace() or take1(ParserCharacters::isAlphaNum).before(updateStr()).map{future(B.str(it))} or
                text("<br>").map{future(B.linebreak)}.attempt() or anchor() or footnote() or
                emphasis(text("***").before(not(char('*')))).map{it.mapValue{v->B.strong(B.emph(v))}} or
                emphasis(text("**").before(not(char('*')))).map{it.mapValue(B::strong)} or inlineTag("strong").map{it.mapValue(B::strong)} or
                emphasis(char('*').before(not(char('*')))).map{it.mapValue(B::emph)} or inlineTag("em").map{it.mapValue(B::emph)} or
                guardDisabled("amuse").then(emphasis(char('_'))).map{it.mapValue(B::underline)} or
                inlineTag("sup").map{it.mapValue(B::superscript)} or inlineTag("sub").map{it.mapValue(B::subscript)} or inlineTag("del").map{it.mapValue(B::strikeout)} or
                openTag("verbatim").then(till(any(),closeTag("verbatim"))).map{future(B.text(chars(it)))} or
                classTag() or direction("<<<",">>>","rtl") or direction(">>>","<<<","ltr") or text("~~").map{future(B.str("\u00a0"))}.attempt() or
                linkOrImage() or code() or openTag("code").map(::attributes).flatMap{attr->till(any(),closeTag("code")).map{future(B.codeWith(attr,chars(it)))}} or
                openTag("math").then(till(any(),closeTag("math"))).map{future(B.math(chars(it)))} or inlineLiteral() or
                take1{it==42}.map{future(B.str(it))} or nonspace().map{future(B.str(chars(listOf(it))))}
        }.label("inline")
        fun anchor()=parseAnchor().flatMap{id->(sp().skipMany() or newline().map{Unit}).map{future(B.spanWith(Attr(id),Inlines()))}}.attempt()
        fun noteMarker():MP<String> {
            fun one(l:Char,r:Char)=char(l).then(satisfy{it in 49..57}).flatMap{x->till(satisfy(ParserCharacters::isDigit),char(r)).map{l+chars(listOf(x)+it)+r}}.attempt()
            return one('[',']') or one('{','}')
        }
        fun footnote():MP<MF<Inlines>> = ReaderParsec.asksValue<MuseEnvironment,MuseState,Boolean>{it.inLink}.flatMap{guard(!it)}.then(noteMarker()).map{ref->
            Future<MuseState,Inlines>{state->state.flatMapValue{s->val note=s.notes[ref]
                if(note==null)Deferred.now(B.str(ref)) else note.contents.runDelayed(Deferred.now(s.copy(notes=s.notes-ref))).mapValue(B::note)
            }}
        }.attempt()
        fun <A> emphasis(p:MP<A>)=atStart().then(p).before(not(space())).then(till1(inline,noSpaceBefore().then(p).before(not(satisfy(ParserCharacters::isAlphaNum))).attempt())).map{trim(inlines(it))}.attempt()
        fun inlineTag(tag:String)=openTag(tag).then(till(inline,closeTag(tag))).map(::inlines).attempt()
        fun classTag()=openTag("class").flatMap{attrs->till(inline,closeTag("class")).map{inlines(it).mapValue{v->B.spanWith(Attr(classes=sourceWords(attrs.firstOrNull{it.first=="name"}?.second?:"")),v)}}}
        fun direction(open:String,close:String,dir:String)=text(open).then(till(inline,text(close))).map{inlines(it).mapValue{v->B.spanWith(Attr(keyValues=listOf("dir" to dir)),v)}}.attempt()
        fun code():MP<MF<Inlines>> = atStart().then(char('=')).before(not(sp() or newline())).then(
            until(satisfy{it!=10&&it!=13} or newline().before(not(newline())),satisfy{it !in intArrayOf(32,9,10,13,61)}.before(char('=')).attempt())
        ).before(not(satisfy(ParserCharacters::isAlphaNum))).map{(xs,end)->future(B.code(chars(xs+end)))}.attempt()
        fun inlineLiteral()=openTag("literal").flatMap{attrs->till(any(),closeTag("literal")).map{future(B.rawInline(attrs.firstOrNull{it.first=="style"}?.second?:"html",chars(it)))}}.attempt()
        fun linkContent()=char('[').then(till(inline,char(']'))).map{trim(inlines(it))}
        fun link(prefix:String):MP<MF<Inlines>> = ReaderParsec.asksValue<MuseEnvironment,MuseState,Boolean>{it.inLink}.flatMap{guard(!it)}.then(text("[["+prefix)).then(till(any(),char(']'))).map(::chars).flatMap{url->
            linkContent().localValue{it.copy(inLink=true)}.option(future(B.str(url))).before(char(']')).map{it.mapValue{v->B.link(url,"",v)}}
        }.attempt()
        fun linkOrImage()=link("URL:") or image() or link("")
        data class ImageOptions(val extension:String,val width:String?,val align:Int?)
        fun image():MP<MF<Inlines>> {
            val extension=listOf(".eps",".gif",".jpg",".jpeg",".pbm",".png",".tiff",".xbm",".xpm").map{text(it).attempt()}.reduce{a,b->a or b}
            val attrs=sp().many1().then(maybe(take1(ParserCharacters::isDigit))).before(sp().skipMany()).flatMap{width->maybe(satisfy{it in intArrayOf(114,108,102)}).map{width to it}}
            val end=extension.flatMap{ext->attrs.option(null to null).map{(width,align)->ImageOptions(ext,width,align)}}.before(char(']'))
            return text("[[").then(until(satisfy{it!=93},end)).flatMap{(xs,opt)->linkContent().option(future(Inlines())).before(char(']')).map{content->content.mapValue{v->
                val kv=if(opt.align==102)listOf("width" to ((opt.width?:"100")+"%"),"height" to "75%") else opt.width?.let{listOf("width" to (it+"%"))}?:emptyList()
                val classes=when(opt.align){114->listOf("align-right");108->listOf("align-left");else->emptyList()}
                B.imageWith(Attr(classes=classes,keyValues=kv),chars(xs)+opt.extension,"",v)
            }}}.attempt()
        }
        fun directiveKey()=char('#').then(take{ParserCharacters.isAlpha(it)||it==45})
        fun directive():MP<Unit> = enabled("amuse").flatMap{amuse->
            directiveKey().flatMap{key->
                val contents=if(amuse)sp().many1().then(till1(inline,(eof() or newline().then(blankline().map{Unit} or directiveKey().map{Unit}).attempt()).lookAhead())).before(blankline().many())
                    else sp().then(till(inlinePrime,eol()))
                contents.map{trim(inlines(it))}.flatMap{value->update{old->old.copy(meta=f2(value,old.meta){v,m->m+((if(key=="cover")"cover-image" else key) to v.toMetaValue())})}}
            }
        }
        fun document():MP<Pandoc> = directive().many().then(parseBlocks()).flatMap{first->parseSection().many().before(eof()).flatMap{rest->state().map{s->
            s.user.messages.forEach(runtime::report)
            f2(s.user.meta,blocks(listOf(first)+rest)){m,b->Pandoc(m,b.values)}.runF(s.user)
        }}}
        fun parseBlocks():MP<MF<Blocks>> = delay{
            val end=eof().map{future(Blocks())}
            val section=headingStart().lookAhead().map{future(Blocks())}
            val list=allow(anyListUntil(delay{parseBlocks()}) or noteBlockUntil(delay{parseBlocks()})).map{(a,b)->append(a,b)}
            val block=(blockElements or emacsNoteBlock()).flatMap{a->allow(delay{parseBlocks()}).map{append(a,it)}}
            val para=sp().many().flatMap{spaces->paraUntil(delay{parseBlocks()}).map{(a,b)->append(if(spaces.size in 2..5)a.mapValue(B::blockQuote) else a,b)}}
            (end or section or list or block or para).attempt()
        }
        fun parseSection():MP<MF<Blocks>> = delay{
            emacsHeading().flatMap{a->parseBlocks().map{append(a,it)}} or headingUntil(parseBlocks()).map{(a,b)->append(a,b)}
        }
        fun <A> parseBlocksTill(end:MP<A>):MP<MF<Blocks>> {
            fun go():MP<MF<Blocks>> = delay{
                val done=end.map{future(Blocks())}
                val list=allow(anyListUntil(done or delay{go()})).map{(a,b)->append(a,b)}
                val block=blockElements.flatMap{a->allow(delay{go()}).map{append(a,it)}}
                val para=paraUntil(done or delay{go()}).map{(a,b)->append(a,b)}
                (done or list or block or para).attempt()
            }
            return go()
        }
        fun <A> paraContentsUntil(end:MP<A>):MP<Pair<MF<Inlines>,A>> = until(inline,
            till(sp(),eol()).then(end.localValue{it.copy(inPara=true)}).attempt()).map{(xs,e)->trim(inlines(xs)) to e}
        fun <A> paraUntil(end:MP<A>):MP<Pair<MF<Blocks>,A>> = ReaderParsec.asksValue<MuseEnvironment,MuseState,Boolean>{it.inPara}.flatMap{guard(!it)}.then(paraContentsUntil(end)).map{(x,e)->x.mapValue(B::para) to e}
        fun <A> itemUntil(col:Long,pre:MP<A>,end:MP<A>):MP<Pair<MF<Blocks>,A>> {
            fun item():MP<Pair<MF<Blocks>,A>> = delay{
                val before=pre.map{future(Blocks()) to it}
                val done=end.map{future(Blocks()) to it}
                val continuation=maybe(blankline()).flatMap{blank->blankline().skipMany().then(indent(col)).then(delay{item()}.localValue{it.copy(inPara=it.inPara&&blank==null)})}.attempt()
                val next=before or continuation or done
                val list=allow(anyListUntil(next)).map{(x,r)->append(x,r.first) to r.second}.attempt()
                val block=blockElements.flatMap{x->allow(next).map{r->append(x,r.first) to r.second}}.attempt()
                val para=paraUntil(next).map{(x,r)->append(x,r.first) to r.second}.attempt()
                list or block or para
            }
            return item()
        }
        fun headingStart():MP<Pair<String,Int>> = parseAnchor().before(till(sp(),eol())).attempt().option("").flatMap{id->firstColumn().then(char('*').many1()).before(sp()).map{id to it.size}}.attempt()
        fun emacsHeading():MP<MF<Blocks>> = guardDisabled("amuse").then(headingStart()).flatMap{(id,n)->till(inline,eol()).map{trim(inlines(it))}.flatMap{content->registerHeader(id,content.runF(MuseState())).map{attr->content.mapValue{B.headerWith(attr,n.toLong(),it)}}}}.attempt()
        fun <A> headingUntil(end:MP<A>):MP<Pair<MF<Blocks>,A>> = guardEnabled("amuse").then(headingStart()).flatMap{(id,n)->paraContentsUntil(end).flatMap{(content,e)->registerHeader(id,content.runF(MuseState())).map{attr->content.mapValue{B.headerWith(attr,n.toLong(),it)} to e}}}.attempt()
        fun unindent(s:String):String {
            val chopped=s.removePrefix("\n");val rows=chopped.split('\n');var count=0
            while(true){val chars=rows.mapNotNull{it.getOrNull(count)};if(chars.isEmpty()||chars[0] !in " \t"||chars.any{it!=chars[0]})break;count++}
            return rows.joinToString("\n"){it.drop(count)}.removeSuffix("\n")
        }
        fun htmlContents(tag:String):MP<Pair<Attr,MF<Blocks>>> = getIndent().flatMap{n->openTag(tag).map(::attributes).before(till(sp(),eol())).flatMap{attr->allow(parseBlocksTill(indent(n).then(closeTag(tag)).attempt())).before(till(sp(),eol())).map{attr to it}}}.attempt()
        fun example()=text("{{{").before(sp().many()).then(till(any(),text("}}}"))).map{future(B.codeBlock(unindent(chars(it))))}.attempt()
        fun exampleTag()=sp().many().then(openTag("example")).map(::attributes).flatMap{attr->till(any(),closeTag("example")).before(till(sp(),eol())).map{future(B.codeBlockWith(attr,unindent(chars(it))))}}.attempt()
        fun literalTag()=sp().many().then(openTag("literal")).before(till(sp(),eol())).flatMap{attr->till(any(),closeTag("literal")).before(till(sp(),eol())).map{future(B.rawBlock(attr.firstOrNull{it.first=="style"}?.second?:"html",unindent(chars(it))))}}.attempt()
        fun verseLine():MP<MF<Inlines>> = char(' ').many1().map{future(B.str("\u00a0".repeat(it.size)))}.option(future(Inlines())).flatMap{prefix->till(inlinePrime,eol()).map{f2(prefix,trim(inlines(it)),Inlines::plus)}}
        fun verseTag()=getIndent().flatMap{n->openTag("verse").before(till(sp(),eol())).then(till(indent(n).then(verseLine()),indent(n).then(closeTag("verse")).attempt())).before(till(sp(),eol())).map{sequence(it).mapValue(B::lineBlock)}}.attempt()
        fun lineBlock():MP<MF<Blocks>> = getIndent().flatMap{n->
            val line=char('>').before(blankline()).map{future(Inlines())}.attempt() or text("> ").attempt().then(verseLine())
            line.flatMap{first->indent(n).then(line).attempt().many().map{sequence(listOf(first)+it).mapValue(B::lineBlock)}}
        }.attempt()
        val blockElements:MP<MF<Blocks>> get()=delay{
            blankline().map{future(Blocks())} or
                firstColumn().then(char(';')).before(sp().then(take{it!=10}).optional()).before(eol()).map{future(Blocks())}.attempt() or
                text("----").before(char('-').many()).before(sp().many()).before(eol()).map{future(B.horizontalRule)}.attempt() or
                count(6L,sp()).then(sp().many()).then(text("* * * * *")).before(till(sp(),eol())).map{future(B.divWith(Attr(keyValues=listOf("style" to "page-break-before: always;")),Blocks()))}.attempt() or
                example() or exampleTag() or literalTag() or htmlContents("center").map{it.second} or htmlContents("right").map{it.second} or htmlContents("quote").map{it.second.mapValue(B::blockQuote)} or
                htmlContents("div").map{(attr,x)->x.mapValue{B.divWith(attr,it)}} or
                guardEnabled("amuse").then(htmlContents("biblio")).map{it.second.mapValue{v->B.divWith(Attr(classes=listOf("biblio")),v)}} or
                guardEnabled("amuse").then(htmlContents("play")).map{it.second.mapValue{v->B.divWith(Attr(classes=listOf("play")),v)}} or
                verseTag() or lineBlock() or gridTable() or table() or
                sp().many().then(openTag("comment")).then(till(any(),closeTag("comment"))).before(till(sp(),eol())).map{future(Blocks())}.attempt()
        }
        fun <A> count(n:Long,p:MP<A>):MP<List<A>> {
            fun go(rest:Long):MP<Chain<A>?> = if(rest<=0)pure(null) else p.flatMap{x->delay{go(rest-1)}.map<Chain<A>?>{Chain(x,it)}}
            return delay{go(n)}.map(::chainList)
        }
        fun addNote(ref:String,pos:SourcePos,contents:MF<Blocks>):MP<Unit> = update{st->st.copy(notes=st.notes+(ref to MuseNote(pos,contents)),messages=if(ref in st.notes)st.messages+LogMessage.DuplicateNoteReference(ref,pos) else st.messages)}
        fun <A> noteBlockUntil(end:MP<A>):MP<Pair<MF<Blocks>,A>> = guardEnabled("amuse").then(noteMarker()).flatMap{ref->state().flatMap{s->
            (sp().map{Unit} or eol().lookAhead()).then(allow(itemUntil(s.position.column,ReaderParsec.fail("x"),end))).flatMap{(contents,e)->addNote(ref,s.position,contents).map{future(Blocks()) to e}}
        }}.attempt()
        fun emacsNoteBlock():MP<MF<Blocks>> = guardDisabled("amuse").then(noteMarker()).flatMap{ref->state().flatMap{s->
            till1(parseBlock(),eof() or noteMarker().lookAhead().map{Unit}).map(::blocks).flatMap{contents->addNote(ref,s.position,contents).map{future(Blocks())}}
        }}.attempt()
        fun parseBlock():MP<MF<Blocks>> = (blockElements or paraUntil((eof() or blockElements.lookAhead().map{Unit}).attempt()).map{it.first}).flatMap{result->state().map{s->
            runtime.trace("Parsed "+AstShow.show(result.runF(MuseState()).values).codePoints().limit(60).toArray().let{chars(it.toList())}+" at line ${s.position.line}"+if(s.position.name=="chunk")" of chunk" else "")
            result
        }}
        fun <A> bulletItems(n:Long,end:MP<A>):MP<Pair<List<MF<Blocks>>,A>> = char('-').then(sp().map{Unit} or eol().lookAhead()).then(
            allow(itemUntil(n+2,blankline().optional().then(indent(n)).then(delay{bulletItems(n,end)}).attempt(),end.map{emptyList<MF<Blocks>>() to it}))
        ).map{(x,r)->prepend(x,r.first) to r.second}.attempt()
        fun <A> bulletList(end:MP<A>)=getIndent().flatMap{n->guard(n!=0L).then(bulletItems(n,end)).map{(xs,e)->sequence(xs).mapValue(B::bulletList) to e}}.attempt()
        fun number(style:ListNumberStyle):MP<Long> {
            val p=when(style){ListNumberStyle.Decimal->ListParsing.decimal<MuseState>();ListNumberStyle.UpperRoman->ListParsing.upperRoman();ListNumberStyle.LowerRoman->ListParsing.lowerRoman();ListNumberStyle.UpperAlpha->ListParsing.upperAlpha();ListNumberStyle.LowerAlpha->ListParsing.lowerAlpha();else->Parsec.fail("Unhandled case")}
            return lift(p).map{it.second}.before(char('.'))
        }
        fun <A> orderedItems(n:Long,style:ListNumberStyle,end:MP<A>):MP<Pair<List<MF<Blocks>>,A>> = delay{
            state().flatMap{pos->(sp().map{Unit} or eol().lookAhead()).then(allow(itemUntil(pos.position.column,
                blankline().optional().then(indent(n)).then(number(style)).then(delay{orderedItems(n,style,end)}).attempt(),end.map{emptyList<MF<Blocks>>() to it})))
                .map{(x,r)->prepend(x,r.first) to r.second}}
        }.attempt()
        fun <A> orderedList(end:MP<A>):MP<Pair<MF<Blocks>,A>> = getIndent().flatMap{n->guard(n!=0L).then(
            lift(ListParsing.decimal<MuseState>() or ListParsing.lowerRoman() or ListParsing.upperRoman() or ListParsing.lowerAlpha() or ListParsing.upperAlpha())
        ).before(char('.')).flatMap{(style,start)->orderedItems(n,style,end).map{(xs,e)->sequence(xs).mapValue{B.orderedListWith(ListAttributes(start,style,ListNumberDelim.Period),it)} to e}}}.attempt()
        fun <A> descriptions(n:Long,end:MP<A>):MP<Pair<List<MF<Blocks>>,A>> = (sp().map{Unit} or eol().lookAhead()).then(allow(itemUntil(n,
            blankline().optional().then(indent(n)).then(till(sp(),text("::"))).then(delay{descriptions(n,end)}).attempt(),end.map{emptyList<MF<Blocks>>() to it}
        ))).map{(x,r)->prepend(x,r.first) to r.second}
        fun <A> definitions(n:Long,end:MP<A>):MP<Pair<List<MF<Pair<Inlines,List<Blocks>>>>,A>> = delay{
            state().flatMap{pos->till(inlinePrime,text("::").attempt()).map{trim(inlines(it))}.flatMap{term->
                val more=blankline().optional().then(indent(n)).then(delay{definitions(n,end)}).attempt() or end.map{emptyList<MF<Pair<Inlines,List<Blocks>>>>() to it}
                descriptions(pos.position.column,more).map{(xs,r)->prepend(f2(term,sequence(xs)){t,ds->t to ds},r.first) to r.second}
            }}
        }.attempt()
        fun <A> definitionList(end:MP<A>):MP<Pair<MF<Blocks>,A>> = getIndent().flatMap{n->(guardDisabled("amuse") or guard(n!=0L)).then(definitions(n,end)).map{(xs,e)->sequence(xs).mapValue(B::definitionList) to e}}.attempt()
        fun <A> anyListUntil(end:MP<A>):MP<Pair<MF<Blocks>,A>> = delay{bulletList(end) or orderedList(end) or definitionList(end)}
        data class MuseTable(val caption:Inlines=Inlines(),val heads:List<List<Blocks>> = emptyList(),val rows:List<List<Blocks>> = emptyList(),val feet:List<List<Blocks>> = emptyList())
        sealed interface TableElement {
            data class Row(val kind:Int,val cells:List<Blocks>):TableElement
            data class Caption(val content:Inlines):TableElement
        }
        fun row(cells:List<Blocks>)=Row(Attr(),cells.map{Cell(Attr(),Alignment.AlignDefault,1,1,it.values)})
        fun makeTable(caption:Caption,head:List<Blocks>,rows:List<List<Blocks>>,columns:Int)=B.table(caption,List(columns){ColSpec(Alignment.AlignDefault,ColWidth.Default)},TableHead(Attr(),if(head.isEmpty())emptyList() else listOf(row(head))),listOf(TableBody(Attr(),0,emptyList(),rows.map(::row))),TableFoot(Attr(),emptyList()))
        fun tableToBlocks(table:MuseTable):Blocks {
            val all=table.heads+table.rows+table.feet;val width=all.maxOfOrNull{it.size}?:0
            return TableCompaction.compactify(makeTable(B.simpleCaption(B.plain(table.caption)),table.heads.firstOrNull()?:emptyList(),table.heads.drop(1)+table.rows+table.feet,width))
        }
        fun table():MP<MF<Blocks>> = tableElement().many1().map{sequence(it).mapValue{xs->
            var table=MuseTable()
            for(x in xs.toList().asReversed())table=when(x){is TableElement.Caption->table.copy(caption=x.content);is TableElement.Row->when(x.kind){2->table.copy(heads=MuseConcatList(listOf(x.cells),table.heads));1->table.copy(rows=MuseConcatList(listOf(x.cells),table.rows));else->table.copy(feet=MuseConcatList(listOf(x.cells),table.feet))}}
            tableToBlocks(table)
        }}.attempt()
        fun tableElement():MP<MF<TableElement>> = tableRow(2).map{it.mapValue<TableElement>{v->TableElement.Row(2,v)}} or
            tableRow(1).map{it.mapValue<TableElement>{v->TableElement.Row(1,v)}} or tableRow(3).map{it.mapValue<TableElement>{v->TableElement.Row(3,v)}} or
            sp().many().then(text("|+")).then(till1(inline,text("+|").then(eol()).attempt())).map{trim(inlines(it)).mapValue<TableElement>{v->TableElement.Caption(v)}}.attempt()
        fun tableRow(n:Int):MP<MF<List<Blocks>>> = state().flatMap{s->
            val sources=s.input.stream as Sources;val chain=sources.unSources.value()
            val possible=if(chain is DeferredChain.Cons){val chunk=chain.head.value().text;val end=chunk.indexOf('\n');
                if(end<0&&chain.tail.value() is DeferredChain.Cons)true else {val line=if(end<0)chunk else chunk.substring(0,end);line.contains(" "+"|".repeat(n))||line.contains("\t"+"|".repeat(n))}
            }else false
            val sep=sp().many1().then(count(n.toLong(),char('|'))).before((sp().many1().map{Unit} or eol()).lookAhead()).map{Unit}.attempt()
            fun cell(end:MP<Unit>)=till(inlinePrime,end).map{trim(inlines(it)).mapValue(B::plain)}.attempt()
            fun cells():MP<List<MF<Blocks>>> = cell(sep).flatMap{first->(delay{cells()} or cell(eol()).map{listOf(it)}).map{MuseConcatList(listOf(first),it)}}
            guard(possible).then(delay{cells()}).map(::sequence)
        }.attempt()
        fun gridHeader():MP<List<Int>> = char('+').then((char('-').many1().before(char('+')).map{it.size}.attempt()).many1()).before(till(sp(),eol())).attempt()
        fun gridRaw(indices:List<Int>):MP<List<String>> = char('|').then(indices.fold(pure(emptyList<String>())){p,n->p.flatMap{xs->count(n.toLong(),any()).before(char('|')).map{xs+chars(it)}}}).before(till(sp(),eol()))
        fun <A> parseFromString(p:MP<A>,text:String):MP<A> = state().flatMap{old->
            lift(Parsec.modify<MuseState>{it.copy(input=ParseInput(Sources.fromText(text)),position=SourcePos(old.position.name+"_chunk"),user=it.user.copy(lastStr=null))}).then(p).before(
                lift(Parsec.modify<MuseState>{it.copy(input=old.input,position=old.position,user=it.user.copy(lastStr=old.user.lastStr))}))
        }
        fun gridRow(n:Long,indices:List<Int>):MP<MF<List<Blocks>>> = indent(n).then(gridRaw(indices)).attempt().many1().before(indent(n).then(gridHeader())).flatMap{lines->
            val columns=indices.indices.map{col->lines.joinToString("\n",postfix="\n"){it[col].trimEnd{c->ParserCharacters.isSpace(c.code)}}}
            columns.fold(pure(emptyList<MF<Blocks>>())){p,column->p.flatMap{xs->parseFromString(parseBlocks(),column).map{xs+it}}}.map(::sequence)
        }.attempt()
        fun gridTable():MP<MF<Blocks>> = getIndent().flatMap{n->gridHeader().flatMap{indices->gridRow(n,indices).many1().map{sequence(it).mapValue{rows->makeTable(Caption(null,emptyList()),emptyList(),rows,rows.maxOfOrNull{it.size}?:0)}}}}.attempt()
    }
}

/** Persistent block/list concatenation: original Sequence append without quadratic copies. */
private class MuseConcatList<A>(val left:List<A>,val right:List<A>):AbstractList<A>() {
    override val size:Int=left.size+right.size
    override fun get(index:Int):A {if(index !in 0 until size)throw IndexOutOfBoundsException(index);var list:List<A> = this;var at=index
        while(list is MuseConcatList){if(at<list.left.size)list=list.left else {at-=list.left.size;list=list.right}};return list[at]}
    override fun iterator():Iterator<A> = object:Iterator<A>{
        val pending=java.util.ArrayDeque<List<A>>().apply{add(this@MuseConcatList)};var current:Iterator<A> = emptyList<A>().iterator()
        override fun hasNext():Boolean {while(!current.hasNext()&&pending.isNotEmpty()){val part=pending.removeLast();if(part is MuseConcatList){pending.add(part.right);pending.add(part.left)}else current=part.iterator()};return current.hasNext()}
        override fun next():A {if(!hasNext())throw NoSuchElementException();return current.next()}
    }
}
