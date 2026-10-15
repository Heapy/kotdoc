// Text.Pandoc.Readers.HTML, © 2006-2024 John MacFarlane.
// GPL-2.0-or-later; complete original source and notices in upstream/pandoc and licenses/.
package io.heapy.kotdoc.html

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.ast.Builder as B
import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.formats.HaskellNumber
import io.heapy.kotdoc.latex.LaTeXReader
import io.heapy.kotdoc.latex.LaTeXReaderText
import io.heapy.kotdoc.math.*
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.resources.*
import io.heapy.kotdoc.uri.NetworkURI
import io.heapy.kotdoc.uri.PandocURI
import java.util.Base64

/** Original readHtml grammar; URL effects are supplied by the Pandoc runtime. */
class HtmlReader(private val runtime: PandocDataRuntime,private val openURL: (String)->FetchedResource,private val zonedDay: ()->String) {
    private val latex by lazy { LaTeXReader(runtime,zonedDay) }
    private fun <A> pure(a: A): HtmlReaderTagParser<A> = ReaderTokenParsec.pure(a)
    private fun <A> zero(): HtmlReaderTagParser<A> = ReaderTokenParsec.zero()
    private fun <A> defer(f: ()->HtmlReaderTagParser<A>): HtmlReaderTagParser<A> = ReaderTokenParsec.defer(f)
    private fun <A> lift(p: HtmlTagParser<HtmlState,A>): HtmlReaderTagParser<A> = ReaderTokenParsec.lift(p)
    private fun state()=lift(TokenParsec.state<HtmlTag,HtmlState>())
    private fun update(f: (HtmlState)->HtmlState)=lift(TokenParsec.modify<HtmlTag,HtmlState> { it.copy(user=f(it.user)) })
    private fun <A> effect(f: ()->A)=lift(TokenParsec.lift<HtmlTag,HtmlState,A>(f))
    private fun guard(b: Boolean)=if(b)pure(Unit) else zero()
    private fun enabled(name: String,on: Boolean=true)=state().flatMap { guard((Extension.read(name) in it.user.parserState.options.extensions)==on) }
    private fun satisfy(p: (HtmlTag)->Boolean)=lift(HtmlTagParsing.pSatisfy<HtmlState>(p))
    private fun any()=lift(HtmlTagParsing.pAny<HtmlState>())
    private fun blanks()=lift(HtmlTagParsing.pBlank<HtmlState>().skipMany())
    private fun close(name: String)=lift(HtmlTagParsing.pCloses<HtmlState>(name))
    private fun eof()=lift(TokenParsec.eof<HtmlTag,HtmlState>(HtmlTagMatching::show))
    private fun open(name: String)=satisfy { HtmlTagParsing.matchTagOpen(name,emptyList(),it) }.map { it as HtmlTag.Open }
    private fun exactClose(name: String)=satisfy { HtmlTagParsing.matchTagClose(name,it) }.map { Unit }
    private fun <A,C> till(p: HtmlReaderTagParser<A>,end: HtmlReaderTagParser<C>): HtmlReaderTagParser<List<A>> = ReaderTokenParsec { env -> TokenParsec.manyTill(p.runReader(env),end.runReader(env)) }
    private fun <A> inTags(name: String,p: HtmlReaderTagParser<A>,empty: A,append: (A,A)->A): HtmlReaderTagParser<A> = ReaderTokenParsec { env -> HtmlTagParsing.pInTags(name,p.runReader(env),empty,append) }
    private fun inBlocks(name: String)=inTags(name,block,Blocks(),Blocks::plus)
    private fun inInlines(name: String)=inTags(name,inline,Inlines(),Inlines::plus)
    private fun concat(xs: List<Inlines>)=xs.fold(Inlines(),Inlines::plus)
    private fun concatBlocks(xs: List<Blocks>)=xs.fold(Blocks(),Blocks::plus)
    private fun attr(xs: List<Pair<String,String>>,key: String)=xs.firstOrNull { it.first==key }?.second
    private fun type(xs: List<Pair<String,String>>)=attr(xs,"type") ?: attr(xs,"epub:type") ?: ""
    private fun sourceWords(s: String): List<String> {
        val out=ArrayList<String>();val word=StringBuilder()
        s.codePoints().forEach { if(ParserCharacters.isSpace(it)) { if(word.isNotEmpty()) {out.add(word.toString());word.setLength(0)} } else word.appendCodePoint(it) }
        if(word.isNotEmpty())out.add(word.toString());return out
    }
    private fun log(message: LogMessage)=lift(TokenParserCapabilities.logMessage<HtmlTag,HtmlState>(HtmlStateAccess,message))
    fun read(options: ReaderOptions,input: String): Pandoc = read(options,Sources.fromText(input))
    fun read(options: ReaderOptions,input: Sources): Pandoc = readWithDepth(0,options,input)
    private fun readWithDepth(depth: Long,options: ReaderOptions,input: Sources): Pandoc {
        val tags=stripPrefixes(HtmlRendering.canonicalize(TagSoup.parseTextTags(input.sourcesToText(),HtmlParseOptions.Default.copy(tagPosition=true))))
        val parser=till(block,eof()).flatMap { bs -> replaceNotes(fixPlains(false,concatBlocks(bs)).values).flatMap { replaced ->
            state().flatMap { s -> lift(TokenParserCapabilities.reportLogMessages<HtmlTag,HtmlState>(HtmlStateAccess,runtime)).map {
                Pandoc(s.user.parserState.meta,extractMain(replaced))
            } }
        } }
        val result=parser.run(TokenParseState(ListTokenStream(tags),SourcePos("source"),HtmlState.initial(options,depth)),HtmlLocal())
        if(!result.success)throw PandocError.PandocParseError(result.error.messages.sortedBy { it.kind }.firstOrNull()?.text ?: "")
        return checkNotNull(result.value)
    }
    private fun replaceNotes(bs: List<Block>): HtmlReaderTagParser<List<Block>> = state().flatMap { initial ->
        var s=initial.user
        val result=object : PandocVisitor() {
            override fun transformInline(value: Inline): Inline {
                if(value !is Inline.RawInline || value.format!=Format.literal("noteref"))return value
                val note=s.noteTable[value.text]
                if(note!=null)return Inline.Note(note.values)
                s=HtmlStateAccess.addLogMessage(LogMessage.ReferenceNotFound(value.text,s.noteRefPos[value.text] ?: initial.position),s)
                return Inline.Note(emptyList())
            }
        }.walkBlocks(bs)
        update { s }.map { result }
    }
    val block: HtmlReaderTagParser<Blocks> get()=defer(::parseBlock)
    val inline: HtmlReaderTagParser<Inlines> get()=defer(::parseInline)
    private fun parseBlock(): HtmlReaderTagParser<Blocks> {
        val dispatch=satisfy(::isBlockTag).lookAhead().flatMap { tag -> state().flatMap { s ->
            if(tag !is HtmlTag.Open)zero() else {
                val name=tag.name;val attrs=tag.attributes;val t=type(attrs);val epub=Extension.read("epub_html_exts") in s.user.parserState.options.extensions
                when {
                    name in HtmlTagCategories.sectioningContent && epub && "chapter" in t->inBlocks(name).localValue { it.copy(inChapter=true) }
                    epub && t in listOf("footnotes","rearnotes")->footnotes()
                    epub && t in listOf("footnote","rearnote")->footnote().map { Blocks() }
                    epub && t=="toc"->toc().map { Blocks() }
                    "titlepage" in t && name in (HtmlTagCategories.groupingContent+"section")->inBlocks(name).map { Blocks() }.attempt()
                    attr(attrs,"role")=="doc-endnotes"->footnotes()
                    name=="p"->para()
                    name in listOf("h1","h2","h3","h4","h5","h6")->header()
                    name=="blockquote"->inBlocks(name).map { B.blockQuote(fixPlains(false,it)) }
                    name=="pre"->codeBlock() or preBlock()
                    name=="ul"->bulletList()
                    name=="ol"->orderedList()
                    name=="dl"->definitionList()
                    name=="table"->ReaderTokenParsec { env -> HtmlTableParsing.pTable(block.runReader(env)) }
                    name=="hr"->selfClosing({it=="hr"},{true}).flatMap { state().map { if(it.user.inFootnotes)Blocks() else B.horizontalRule } }
                    name=="html" || name=="body"->languageContainer(name)
                    name=="head"->head()
                    name=="div" && Extension.read("line_blocks") in s.user.parserState.options.extensions && attr(attrs,"class")=="line-block"->lineBlock()
                    name in listOf("div","aside","section","header","main")->div()
                    name=="figure"->figure()
                    name=="iframe"->iframe()
                    name in listOf("style","textarea")->rawBlock()
                    name=="switch" && epub->switch(B::para,block,Blocks(),Blocks::plus)
                    else->zero()
                }
            }
        } }
        return (dispatch or plain() or rawBlock()).flatMap { result -> effect {
            if(runtime.getTrace())runtime.trace(takeCodePoints(AstShow.chunks(result.values),60));result
        } }
    }
    private fun languageContainer(name: String)=open(name).lookAhead().flatMap { t ->
        val lang=attr(t.attributes,"lang") ?: attr(t.attributes,"xml:lang")
        (if(lang==null)pure(Unit) else update { it.setMeta("lang",B.text(lang).toMetaValue()) }).then(inBlocks(name))
    }
    private fun head(): HtmlReaderTagParser<Blocks> {
        val title=inInlines("title").flatMap { text -> update { it.setMeta("title",B.trimInlines(text).toMetaValue()) }.map { Blocks() } }
        val meta=open("meta").flatMap { tag -> val name=tag.attribute("name")
            (if(name.isEmpty())pure(Unit) else update { it.copy(parserState=it.parserState.copy(meta=InlineShared.addMetaField(name,B.text(tag.attribute("content")),it.parserState.meta))) }).map { Blocks() }
        }
        val base=open("base").flatMap { tag -> update { it.copy(baseHref=NetworkURI.parseURIReference(tag.attribute("href"))) }.map { Blocks() } }
        return inTags("head",title or meta or base or any().map { Blocks() },Blocks(),Blocks::plus)
    }
    private fun <A> switch(constructor: (Inlines)->A,parser: HtmlReaderTagParser<A>,empty: A,append: (A,A)->A): HtmlReaderTagParser<A> = enabled("epub_html_exts").then(open("switch")).then(
        till(case().before(blanks()),open("default").attempt().lookAhead())
    ).flatMap { cases -> blanks().then(inTags("default",blanks().then(parser).before(blanks()),empty,append)).flatMap { fallback ->
        blanks().then(exactClose("switch")).map { cases.firstOrNull { it!=null }?.let(constructor) ?: fallback }
    } }.attempt()
    private fun case(): HtmlReaderTagParser<Inlines?> = blanks().then(open("case").lookAhead()).flatMap { tag ->
        if(attr(HtmlTagParsing.toStringAttr(tag.attributes),"required-namespace")==mathMLNamespace)
            inTags("case",blanks().then(math(true)).before(blanks()),Inlines(),Inlines::plus).map<Inlines?> { it }
        else till(any(),exactClose("case")).map { null }
    }
    private fun footnote(): HtmlReaderTagParser<Unit> = state().flatMap { s -> satisfy { t -> t is HtmlTag.Open && (type(t.attributes) in listOf("footnote","rearnote") || s.user.inFootnotes) }.lookAhead().flatMap { tag ->
        tag as HtmlTag.Open;val id=attr(HtmlTagParsing.toStringAttr(tag.attributes),"id") ?: ""
        inBlocks(tag.name).flatMap { content -> update { it.copy(noteTable=it.noteTable+(id to content)) } }
    } }
    private fun footnotes(): HtmlReaderTagParser<Blocks> = any().lookAhead().flatMap { t -> if(t !is HtmlTag.Open)zero() else {
        val attrs=HtmlTagParsing.toStringAttr(t.attributes)
        (guard(attr(attrs,"role")=="doc-endnotes") or enabled("epub_html_exts").then(guard(type(attrs) in listOf("footnotes","rearnotes")))).then(update { it.copy(inFootnotes=true) }).then(inBlocks(t.name)).flatMap { content ->
            update { it.copy(inFootnotes=false) }.map { if(content.values.isEmpty())content else B.divWith(HtmlTagParsing.toAttr(t.attributes),content) }
        }
    } }.attempt()
    private fun noteref(): HtmlReaderTagParser<Inlines> = satisfy { it is HtmlTag.Open && (type(it.attributes)=="noteref" || it.attribute("role")=="doc-noteref") }.flatMap { t ->
        t as HtmlTag.Open;val href=attr(t.attributes,"href")
        guard(href!=null && href.startsWith('#')).then(state()).flatMap { s -> val ref=checkNotNull(href).drop(1)
            update { it.copy(noteRefPos=if(ref in it.noteRefPos)it.noteRefPos else it.noteRefPos+(ref to s.position)) }.then(till(any(),exactClose(t.name))).map { B.rawInline("noteref",ref) }
        }
    }.attempt()
    private fun toc()=enabled("epub_html_exts").then(any().lookAhead()).flatMap { t ->
        if(t !is HtmlTag.Open)zero() else guard(type(t.attributes)=="toc").then(inBlocks(t.name)).map { Unit }
    }.attempt()
    private fun orphans(): HtmlReaderTagParser<List<Blocks>> = open("li").notFollowedBy(HtmlTagMatching::show).then(satisfy { it is HtmlTag.Close }.notFollowedBy(HtmlTagMatching::show)).then(block).many()
    private fun listItem(): HtmlReaderTagParser<Blocks> = open("li").lookAhead().flatMap { tag ->
        inBlocks("li").flatMap { item -> blanks().then(orphans()).before(blanks()).map { rest ->
            val bs=item+concatBlocks(rest);val id=attr(HtmlTagParsing.toStringAttr(tag.attributes),"id")
            if(id==null)bs else if(bs.values.firstOrNull() is Block.Plain) {
                val first=bs.values.first() as Block.Plain;Blocks(listOf(Block.Plain(listOf(Inline.Span(Attr(id),first.content))))+bs.values.drop(1))
            } else B.divWith(Attr(id),bs)
        } }
    }.localValue { it.copy(inListItem=true) }
    private fun bulletList()=open("ul").then(blanks()).then(orphans()).flatMap { orphan -> till(listItem(),close("ul")).map { items -> B.bulletList((if(orphan.isEmpty())items else listOf(concatBlocks(orphan))+items).map { fixPlains(true,it) }) } }.attempt()
    private fun orderedList(): HtmlReaderTagParser<Blocks> = open("ol").flatMap { tag -> state().flatMap { s ->
        val attrs=HtmlTagParsing.toStringAttr(tag.attributes)
        val start=attr(attrs,"start")?.let { HaskellNumber.readInteger(it)?.toLong() } ?: 1
        val style=attr(attrs,"type")?.let { when(it) { "i"->ListNumberStyle.LowerRoman;"I"->ListNumberStyle.UpperRoman;"a"->ListNumberStyle.LowerAlpha;"A"->ListNumberStyle.UpperAlpha;"1"->ListNumberStyle.Decimal;else->ListNumberStyle.DefaultStyle } } ?:
            attr(attrs,"class")?.let { sourceWords(it).map(::listStyle).firstOrNull { x->x!=ListNumberStyle.DefaultStyle } } ?:
            attr(attrs,"style")?.let { PandocCss.pickStyleAttrProps(listOf("list-style-type","list-style"),it)?.let(::listStyle) } ?: ListNumberStyle.DefaultStyle
        blanks().then(orphans()).flatMap { orphan ->
            if(s.user.inFootnotes)till(footnote() or lift(HtmlTagParsing.pBlank<HtmlState>()),close("ol")).map { Blocks() }
            else till(listItem(),close("ol")).map { items -> B.orderedListWith(ListAttributes(start,style,ListNumberDelim.DefaultDelim),(if(orphan.isEmpty())items else listOf(concatBlocks(orphan))+items).map { fixPlains(true,it) }) }
        }
    } }.attempt()
    private fun definitionList(): HtmlReaderTagParser<Blocks> {
        val nonItem=satisfy { !HtmlTagParsing.matchTagOpen("dt",emptyList(),it) && !HtmlTagParsing.matchTagOpen("dd",emptyList(),it) && !HtmlTagParsing.matchTagClose("dl",it) }
        val item=nonItem.skipMany().then(inInlines("dt")).attempt().many1().flatMap { terms ->
            nonItem.skipMany().then(inBlocks("dd")).attempt().many1().before(nonItem.skipMany()).map { defs ->
                terms.fold(Inlines()) { a,b->if(a.values.isEmpty())B.trimInlines(b) else a+B.linebreak+B.trimInlines(b) } to defs.map { fixPlains(true,it) }
            }
        }.attempt()
        return open("dl").then(till(item,close("dl"))).map(B::definitionList).attempt()
    }
    private fun rawTag(): HtmlReaderTagParser<String> = any().map { t ->
        val name=when(t) { is HtmlTag.Open->t.name;is HtmlTag.Close->t.name;else->null }
        if(name in listOf("html","head","body","!DOCTYPE","?xml"))"" else HtmlRendering.renderPandoc(listOf(t))
    }
    private fun <A> ignore(raw: String,empty: A): HtmlReaderTagParser<A> = state().flatMap { s ->
        (if(raw.isEmpty())pure(Unit) else log(LogMessage.SkippedContent(raw,s.position))).map { empty }
    }
    private fun htmlBlock(name: String): HtmlReaderTagParser<String> = open(name).flatMap { first -> till(any(),exactClose(name)).map { HtmlRendering.renderPandoc(listOf(first)+it+HtmlTag.Close(name)) } }.attempt()
    private fun rawBlock()=(htmlBlock("script") or htmlBlock("style") or htmlBlock("textarea") or rawTag()).flatMap { raw ->
        state().flatMap { if(Extension.read("raw_html") in it.user.parserState.options.extensions && raw.isNotEmpty())pure(B.rawBlock("html",raw)) else ignore(raw,Blocks()) }
    }
    private fun lineBlock()=enabled("line_blocks").then(satisfy { it is HtmlTag.Open && it.name=="div" && it.attributes==listOf("class" to "line-block") }).then(
        till(inline,exactClose("div"))
    ).map { xs ->
        val lines=ArrayList<Inlines>();var current=ArrayList<Inline>()
        for(i in B.trimInlines(concat(xs)).values)when(i) { Inline.SoftBreak->Unit;Inline.LineBreak->{lines.add(Inlines(current));current=ArrayList()};else->current.add(i) }
        lines.add(Inlines(current));B.lineBlock(lines)
    }.attempt()
    private fun div(): HtmlReaderTagParser<Blocks> = enabled("native_divs").then(satisfy { it is HtmlTag.Open && it.name in listOf("div","section","header","main","aside") }.lookAhead()).flatMap { t ->
        t as HtmlTag.Open;val a=HtmlTagParsing.toAttr(t.attributes)
        inBlocks(t.name).map { content ->
            val first=content.values.firstOrNull() as? Block.Header
            val bs=if(first?.attr?.identifier==a.identifier)Blocks(listOf(first.copy(attr=first.attr.copy(identifier="")))+content.values.drop(1)) else content
            val attrs=when(t.name) { "section","aside","header"->a.copy(classes=listOf(t.name)+a.classes);"main"->if(a.keyValues.none { it.first=="role" })a.copy(keyValues=listOf("role" to "main")+a.keyValues) else a;else->a }
            B.divWith(attrs,bs)
        }
    }.attempt()
    private fun iframe(): HtmlReaderTagParser<Blocks> = enabled("raw_html",false).then(satisfy { it is HtmlTag.Open && it.name=="iframe" && attr(it.attributes,"src")!=null }).before(blanks()).before(close("iframe") or eof()).flatMap { t ->
        t as HtmlTag.Open;canonicalizeUrl(t.attribute("src")).flatMap { url -> state().flatMap { s -> effect {
            if(url.isEmpty() || s.user.iframeDepth>=5) Triple<Blocks?,PandocError?,Boolean>(null,null,true) else try {
                val resource=openURL(url);val mime=resource.mimeType
                val contents=when {
                    mime?.startsWith("text/html")==true->B.divWith(Attr(classes=listOf("iframe")),Blocks(readWithDepth(s.user.iframeDepth+1,s.user.readerOpts,Sources.fromText(PandocUtf8.toText(resource.contents))).blocks))
                    mime?.startsWith("image/")==true->B.divWith(Attr(classes=listOf("iframe")),B.plain(B.image(url,"",Inlines())))
                    else->B.divWith(Attr(classes=listOf("iframe"),keyValues=listOf("src" to url)),Blocks())
                }
                Triple(contents,null,false)
            } catch(e: PandocError) { Triple(null,e,true) }
        }.flatMap { (result,error,skip) ->
            (if(error==null)pure(Unit) else log(LogMessage.CouldNotFetchResource(url,error.renderError()))).then(
                if(skip)ignore(HtmlRendering.renderPandoc(listOf(t,HtmlTag.Close("iframe"))),Blocks()) else pure(checkNotNull(result)))
        } } }
    }.attempt()
    private fun header()=satisfy { it is HtmlTag.Open && it.name in listOf("h1","h2","h3","h4","h5","h6") }.flatMap { t ->
        t as HtmlTag.Open;val a=HtmlTagParsing.toStringAttr(t.attributes)
        till(inline,close(t.name) or eof()).map { B.trimInlines(concat(it)) }.flatMap { content ->
            registerHeader(Attr(attr(a,"id") ?: "",sourceWords(attr(a,"class") ?: ""),a.filter { it.first !in listOf("id","class") }),content).map { B.headerWith(it,t.name.drop(1).toLong(),content) }
        }
    }.attempt()
    private fun registerHeader(a: Attr,content: Inlines)=state().flatMap { p -> val s=p.user;val exts=s.parserState.options.extensions
        if(a.identifier.isEmpty() && Extension.read("auto_identifiers") in exts) {
            val id=LaTeXReaderText.uniqueIdent(content.values,s.identifiers,exts);val ascii=if(Extension.read("ascii_identifiers") in exts)Asciify.toAsciiText(id) else id
            update { it.copy(identifiers=it.identifiers+id+ascii) }.map { a.copy(identifier=ascii) }
        } else (if(a.identifier.isNotEmpty() && a.identifier in s.identifiers)log(LogMessage.DuplicateIdentifier(a.identifier,p.position)) else pure(Unit)).then(
            if(a.identifier.isEmpty())pure(Unit) else update { it.copy(identifiers=it.identifiers+a.identifier) }).map { a }
    }
    private fun plain()=inline.many1().map { B.trimInlines(concat(it)) }.localValue { it.copy(inPlain=true) }.map { if(it.values.isEmpty())Blocks() else B.plain(it) }
    private fun para()=inInlines("p").map(B::trimInlines).flatMap { content -> state().map { if(content.values.isEmpty() && Extension.read("empty_paragraphs") !in it.user.parserState.options.extensions)Blocks() else B.para(B.trimInlines(content)) } }
    private fun figure()=open("figure").flatMap { t ->
        val parser=inBlocks("figcaption").map { true to it } or block.map { false to it }
        till(parser,close("figure") or eof()).map { parts -> B.figureWith(HtmlTagParsing.toAttr(t.attributes),B.simpleCaption(concatBlocks(parts.filter { it.first }.map { it.second })),concatBlocks(parts.filter { !it.first }.map { it.second })) }
    }
    private fun preBlock()=open("pre").then(state()).flatMap { old -> update { it.copy(inPre=true) }.then(till(block,close("pre") or eof())).flatMap { bs -> update { it.copy(inPre=old.user.inPre) }.map { concatBlocks(bs) } } }.attempt()
    private fun codeBlock()=open("pre").flatMap { pre -> open("code").flatMap { code ->
        val a=HtmlTagParsing.toAttr((pre.attributes+code.attributes).map { (k,v)->k to if(k=="class")sourceWords(v).joinToString(" ") { it.removePrefix("language-") } else v })
        till(any(),close("pre") or eof()).map { ts -> B.codeBlockWith(a,ts.joinToString("") { when(it) { is HtmlTag.Text->it.text;is HtmlTag.Open->if(it.name=="br")"\n" else "";else->"" } }.removeSuffix("\n")) }
    } }.attempt()
    private fun selfClosing(name: (String)->Boolean,attrs: (List<Pair<String,String>>)->Boolean)=satisfy { it is HtmlTag.Open && name(it.name) && attrs(it.attributes) }.before(satisfy { it is HtmlTag.Close && name(it.name) }.optional()).map { it as HtmlTag.Open }
    private fun parseInline(): HtmlReaderTagParser<Inlines> = tagText() or satisfy(::isInlineTag).lookAhead().flatMap { t ->
        if(t !is HtmlTag.Open)rawInline() else state().flatMap { s ->
            val ext=s.user.parserState.options.extensions;val attrs=t.attributes
            when(t.name) {
                "a"->if(((Extension.read("epub_html_exts") in ext && type(attrs)=="noteref") || attr(attrs,"role")=="doc-noteref") && (attr(attrs,"href") ?: "").startsWith('#'))noteref() else link()
                "switch"->switch({it},inline,Inlines(),Inlines::plus)
                "q"->quote()
                "em","i"->formatted(t.name,B::emph)
                "strong","b"->formatted(t.name,B::strong)
                "sup"->formatted(t.name,B::superscript)
                "sub"->formatted(t.name,B::subscript)
                "small"->formatted(t.name) { B.spanWith(Attr(classes=listOf("small")),it) }
                "s","strike","del"->formatted(t.name,B::strikeout)
                "u","ins"->formatted(t.name,B::underline)
                "br"->selfClosing({it=="br"},{true}).map { B.linebreak }
                "img"->image()
                "svg"->svg()
                "bdo"->open("bdo").lookAhead().flatMap { a -> inInlines("bdo").map { bs -> attr(HtmlTagParsing.toStringAttr(a.attributes),"dir")?.let { B.spanWith(Attr(keyValues=listOf("dir" to LaTeXReaderText.toLower(it))),bs) } ?: bs } }.attempt()
                "tt","code"->code(null)
                "samp"->code("sample")
                "var"->code("variable")
                "span"->span()
                "math"->math(false)
                "input"->if(attr(attrs,"type")=="checkbox")ReaderTokenParsec.asksValue<HtmlLocal,HtmlTag,HtmlState,Boolean> { it.inListItem }.flatMap { guard(it) }.then(selfClosing({it=="input"},{attr(it,"type")=="checkbox"})).map { B.str(if(attr(it.attributes,"checked")==null)"☐" else "☒")+B.space } else zero()
                "style"->htmlBlock("style").flatMap { raw -> if(Extension.read("raw_html") in ext)pure(B.rawInline("html",raw)) else ignore(raw,Inlines()) }
                "script"->if((attr(attrs,"type") ?: "").startsWith("math/tex"))scriptMath() else rawInline()
                else->if(t.name in InlineShared.htmlSpanLikeElements)open(t.name).then(till(inline,close(t.name) or eof())).map { B.spanWith(HtmlTagParsing.toAttr(attrs).let { a->a.copy(classes=listOf(t.name)+a.classes) },concat(it)) } else rawInline()
            }
        }
    }
    private fun formatted(name: String,f: (Inlines)->Inlines)=inInlines(name).map { InlineShared.extractSpaces(f,it) }
    private fun quote()=open("q").flatMap { t -> ReaderTokenParsec.ask<HtmlLocal,HtmlTag,HtmlState>().flatMap { local ->
        val double=local.quoteContext!=QuoteContext.InDoubleQuote
        till(inline,close("q")).localValue { it.copy(quoteContext=if(double)QuoteContext.InDoubleQuote else QuoteContext.InSingleQuote) }.flatMap { xs ->
            val cite=attr(t.attributes,"cite")
            (if(cite==null)pure<(Inlines)->Inlines>({it}) else canonicalizeUrl(cite).map { url -> { content: Inlines-> B.spanWith(Attr(attr(t.attributes,"name") ?: attr(t.attributes,"id") ?: "",sourceWords(attr(t.attributes,"class") ?: ""),listOf("cite" to PandocURI.escapeURI(url))),content) } }).map { wrap ->
                InlineShared.extractSpaces({ value -> if(double)B.doubleQuoted(wrap(value)) else B.singleQuoted(wrap(value)) },concat(xs))
            }
        }
    } }
    private fun link()=open("a").flatMap { t -> val a=HtmlTagParsing.toAttr(t.attributes.filter { it.first !in listOf("title","href") })
        till(inline,close("a")).map(::concat).flatMap { label -> state().flatMap { s ->
            if(s.user.inFootnotes && attr(t.attributes,"role")=="doc-backlink")pure(Inlines()) else {
                val url=attr(t.attributes,"href")
                if(url==null)pure(InlineShared.extractSpaces({ B.spanWith(a,it) },label)) else canonicalizeUrl(url).map { u -> InlineShared.extractSpaces({ B.linkWith(a,PandocURI.escapeURI(u),t.attribute("title"),it) },label) }
            }
        } }
    }.attempt()
    private fun image()=selfClosing({it=="img"},{attr(it,"src")!=null}).flatMap { t -> canonicalizeUrl(t.attribute("src")).map { url ->
        B.imageWith(HtmlTagParsing.toAttr(t.attributes.filter { it.first !in listOf("alt","title","src") }),PandocURI.escapeURI(url),t.attribute("title"),B.text(t.attribute("alt")))
    } }
    private fun svg()=enabled("raw_html",false).then(open("svg")).flatMap { t ->
        close("svg").notFollowedBy { "()" }.then(any()).many().before(close("svg") or eof()).map { ts ->
            val a=HtmlTagParsing.toAttr(t.attributes);val raw=HtmlRendering.renderPandoc(listOf(t)+ts+HtmlTag.Close("svg")).trim { ParserCharacters.isSpace(it.code) }
            val data="data:image/svg+xml;base64,"+Base64.getEncoder().encodeToString(raw.toByteArray(Charsets.UTF_8))
            B.imageWith(a.copy(keyValues=if(a.classes.any { it in listOf("fa-w-14","fa-w-16","fa-fw") })listOf("width" to "1em") else emptyList()),data,"",Inlines())
        }
    }
    private fun code(extra: String?): HtmlReaderTagParser<Inlines> = satisfy { it is HtmlTag.Open && it.name in (if(extra==null)listOf("code","tt") else if(extra=="sample")listOf("samp") else listOf("var")) }.flatMap { t ->
        t as HtmlTag.Open;val a=HtmlTagParsing.toAttr(t.attributes).let { if(extra==null)it else it.copy(classes=listOf(extra)+it.classes) }
        till(inline,close(t.name)).map { InlineShared.formatCode(a,concat(it)) }
    }.attempt()
    private fun span()=open("span").lookAhead().flatMap { t -> val a=HtmlTagParsing.toAttr(t.attributes)
        when {
            a.classes.any { it in listOf("mjx-chtml","MathJax_CHTML","MathJax_Preview") } || a.classes==listOf("katex-html")->inInlines("span").map { Inlines() }
            "MJX_Assistive_MathML" in a.classes->inInlines("span")
            else->state().flatMap { s -> when {
                Extension.read("native_spans") in s.user.parserState.options.extensions->inInlines("span").map { content ->
                    val small=PandocCss.pickStyleAttrProps(listOf("font-variant"),attr(t.attributes,"style") ?: "")=="small-caps" || "smallcaps" in sourceWords(attr(t.attributes,"class") ?: "")
                    if(small)B.smallcaps(content) else B.spanWith(a,content)
                }
                Extension.read("raw_html") in s.user.parserState.options.extensions->open("span").map { B.rawInline("html",HtmlRendering.renderPandoc(listOf(it))) }
                else->inInlines("span")
            } }
        }
    }
    private fun rawInline()=ReaderTokenParsec.ask<HtmlLocal,HtmlTag,HtmlState>().flatMap { local ->
        (satisfy { it is HtmlTag.Comment } or satisfy { if(local.inPlain)!isBlockTag(it) else isInlineTag(it) }).flatMap { t -> state().flatMap { s ->
            val raw=HtmlRendering.renderPandoc(listOf(t));if(Extension.read("raw_html") in s.user.parserState.options.extensions)pure(B.rawInline("html",raw)) else ignore(raw,Inlines())
        } }
    }
    private fun scriptMath()=open("script").flatMap { t -> val ty=attr(t.attributes,"type") ?: ""
        guard(ty.startsWith("math/tex")).then(till(any(),exactClose("script"))).map { val text=TagSoup.innerText(it);if(ty.endsWith("display"))B.displayMath(text) else B.math(text) }
    }.attempt()
    private fun math(inCase: Boolean)=open("math").flatMap { t -> val a=HtmlTagParsing.toStringAttr(t.attributes)
        guard(inCase || attr(a,"xmlns")==null || attr(a,"xmlns")==mathMLNamespace).then(till(any(),exactClose("math"))).map { ts ->
            fun make(text: String)=if(attr(a,"display")=="block")B.displayMath(text) else B.math(text)
            val annotation=extractTeXAnnotation(ts)
            if(annotation!=null)make(annotation) else when(val result=MathMLReader.readMathML(HtmlRendering.render(listOf(t)+ts+HtmlTag.Close("math")))) {
                is MathMLResult.Failure->B.spanWith(Attr(classes=listOf("math"),keyValues=a),B.text(TagSoup.innerText(ts)))
                is MathMLResult.Success->MathTeXWriter.writeTeX(result.expressions).let { if(it.isEmpty())Inlines() else make(it) }
            }
        }
    }.attempt()
    private fun canonicalizeUrl(url: String): HtmlReaderTagParser<String> = if(url.startsWith("data:"))pure(url) else state().map { s ->
        val relative=NetworkURI.parseURIReference(url);val base=s.user.baseHref
        if(relative==null || base==null)url else NetworkURI.nonStrictRelativeTo(relative,base).toString()
    }
    private fun tagText(): HtmlReaderTagParser<Inlines> = state().flatMap { before -> satisfy { it is HtmlTag.Text }.flatMap { t -> state().flatMap { p ->
        val text=(t as HtmlTag.Text).text;val s=p.user;val ext=s.parserState.options.extensions
        val smart=Extension.read("smart") in ext;val dollars=Extension.read("tex_math_dollars") in ext
        val backslash=listOf("raw_tex","tex_math_single_backslash","tex_math_double_backslash").any { Extension.read(it) in ext }
        if(!s.inPre && text.codePoints().noneMatch { isBad(it) || it==36 && dollars || it==92 && backslash || smart && it in intArrayOf(39,34,46,45) })pure(B.text(text)) else
            ReaderTokenParsec { env -> TokenParsec.lift {
                val result=contents.many().run(ParseState(ParseInput(Sources(listOf(SourceChunk(before.position,text)))),before.position.copy(name="text",line=1,column=1),s),env.value())
                if(!result.success)throw PandocError.PandocParseError("Could not parse `$text'")
                concat(checkNotNull(result.value))
            } }
    } } }.attempt()
    private fun <A> charLift(p: Parsec<HtmlState,A>): HtmlReaderCharacterParser<A> = ReaderParsec.lift(p)
    private val contents: HtmlReaderCharacterParser<Inlines> get()=ReaderParsec.defer(::parseContents)
    private fun parseContents(): HtmlReaderCharacterParser<Inlines> {
        val str=charLift(SourceParsers.satisfy<HtmlState> { !HtmlTagParsing.isSpace(it) && !isSpecial(it) && !isBad(it) }.many1()).before(charLift(ParserCapabilities.updateLastStrPos(HtmlStateAccess))).map { B.str(points(it)) }
        val spaces=charLift(SourceParsers.satisfy<HtmlState>(HtmlTagParsing::isSpace).many1()).flatMap { cs -> charLift(Parsec.state<HtmlState>()).map { s ->
            if(!s.user.inPre)if(10 in cs)B.softbreak else B.space else {
                var result=Inlines();val chunk=ArrayList<Int>()
                fun flush() { val text=points(chunk.map { if(it==32)160 else it });if(text.isNotEmpty())result+=B.str(text);chunk.clear() }
                for(c in cs)if(c==10) {flush();result+=B.linebreak} else chunk.add(c)
                flush();result
            }
        } }
        val punctuation=SmartParsing(HtmlStateAccess,HtmlStateAccess,HtmlReaderContext.getQuoteContext()) { q,p -> p.localValue { it.copy(quoteContext=q) } }.smartPunctuation(contents)
        val symbol=charLift(SourceParsers.satisfy<HtmlState>(::isSpecial)).map { B.str(points(listOf(it))) }
        val bad=charLift(SourceParsers.satisfy<HtmlState>(::isBad)).map { B.str(points(listOf(badChar(it)))) }
        return str or spaces or charLift(MathParsing.mathDisplay(HtmlStateAccess)).map(B::displayMath) or charLift(MathParsing.mathInline(HtmlStateAccess)).map(B::math) or punctuation or rawTeX() or symbol or bad
    }
    private fun rawTeX(): HtmlReaderCharacterParser<Inlines> {
        val start=SourceParsers.char<HtmlState>(92).then(listOf("begin","eqref","ref").map { SourceParsers.string<HtmlState>(it).attempt() }.reduce { a,b->a or b }).attempt().lookAhead()
        return charLift(start.then(ReaderOptionGuards.guardEnabled(HtmlStateAccess,Extension.read("raw_tex"))).then(Parsec.state<HtmlState>())).flatMap { initial ->
            val result=latex.rawLaTeXInline(HtmlStateAccess,HtmlStateAccess).run(initial.copy(position=SourcePos("chunk")))
            if(!result.success)ReaderParsec.zero() else {
                val input=(initial.input.stream as Sources).sourcesToText()
                val remaining=(result.state.input.stream as Sources).sourcesToText()
                val raw=input.codePointCount(0,input.length)-remaining.codePointCount(0,remaining.length)
                charLift(countChars(raw.toLong())).map { B.rawInline("tex",checkNotNull(result.value)) }
            }
        }
    }
    private fun countChars(n: Long): Parsec<HtmlState,Unit> { var p=Parsec.pure<HtmlState,Unit>(Unit);repeat(n.coerceAtLeast(0).toInt()) { p=p.then(SourceParsers.anyChar<HtmlState>().map { Unit }) };return p }
    companion object {
        fun <U> htmlTag(test: (HtmlTag)->Boolean,options: ReaderOptionsAccess<U>)=HtmlSourceParsing.htmlTag(test,options)
        fun <U> htmlInBalanced(test: (HtmlTag)->Boolean)=HtmlSourceParsing.htmlInBalanced<U>(test)
        fun toAttr(attrs: List<Pair<String,String>>)=HtmlTagParsing.toAttr(attrs)
        const val mathMLNamespace="http://www.w3.org/1998/Math/MathML"
        fun stripPrefixes(tags: List<HtmlTag>)=tags.map { when(it) { is HtmlTag.Open->it.copy(name=it.name.substringAfterLast(':'));is HtmlTag.Close->it.copy(name=it.name.substringAfterLast(':'));else->it } }
        fun isCommentTag(t: HtmlTag)=t is HtmlTag.Comment
        fun isTextTag(t: HtmlTag)=t is HtmlTag.Text
        fun isInlineTag(t: HtmlTag): Boolean = when(t) {
            is HtmlTag.Comment->true
            is HtmlTag.Open->when(t.name) { "script"->t.attribute("type").startsWith("math/tex");"style"->true;else->t.name !in HtmlTagCategories.blockTags || t.name.startsWith('?') }
            is HtmlTag.Close->t.name in listOf("script","style") || t.name !in HtmlTagCategories.blockTags || t.name.startsWith('?')
            else->false
        }
        fun isBlockTag(t: HtmlTag): Boolean { val name=when(t) { is HtmlTag.Open->t.name;is HtmlTag.Close->t.name;else->null };return t is HtmlTag.Comment || name!=null && (name.startsWith('?') || name.startsWith('!') || name in HtmlTagCategories.blockTags || name in HtmlTagCategories.eitherBlockOrInline) }
        fun fixPlains(inList: Boolean,bs: Blocks): Blocks {
            val paraish=bs.values.any { when(it) { is Block.Para,is Block.CodeBlock,is Block.Header,is Block.BlockQuote->true;is Block.BulletList,is Block.OrderedList,is Block.DefinitionList->!inList;else->false } }
            return if(!paraish)bs else Blocks(bs.values.map { if(it is Block.Plain)Block.Para(it.content) else it })
        }
        fun extractMain(bs: List<Block>): List<Block> {
            val mains=ArrayList<Block.Div>();object : PandocVisitor() { override fun visitBlock(value: Block) { if(value is Block.Div && value.attr.keyValues.firstOrNull { it.first=="role" }?.second=="main")mains.add(value) } }.walkBlocks(bs)
            return when { mains.isEmpty()->bs;mains.size==1 && mains[0].attr.identifier.isEmpty() && mains[0].attr.classes.isEmpty()->mains[0].blocks;else->mains }
        }
        fun extractTeXAnnotation(tags: List<HtmlTag>): String? {
            for(i in tags.indices)if(tags[i]==HtmlTag.Open("annotation",listOf("encoding" to "application/x-tex")))return TagSoup.innerText(tags.drop(i+1).takeWhile { it!=HtmlTag.Close("annotation") })
            return null
        }
        private fun listStyle(s: String)=when(s) { "lower-roman"->ListNumberStyle.LowerRoman;"upper-roman"->ListNumberStyle.UpperRoman;"lower-alpha"->ListNumberStyle.LowerAlpha;"upper-alpha"->ListNumberStyle.UpperAlpha;"decimal"->ListNumberStyle.Decimal;else->ListNumberStyle.DefaultStyle }
        private fun isBad(c: Int)=c in 128..159
        private fun isSpecial(c: Int)=c in intArrayOf(34,39,46,45,36,92,8216,8217,8220,8221)
        private fun badChar(c: Int)=mapOf(128 to 8364,130 to 8218,131 to 402,132 to 8222,133 to 8230,134 to 8224,135 to 8225,136 to 710,137 to 8240,138 to 352,139 to 8249,140 to 338,142 to 381,145 to 8216,146 to 8217,147 to 8220,148 to 8221,149 to 8226,150 to 8211,151 to 8212,152 to 732,153 to 8482,154 to 353,155 to 8250,156 to 339,158 to 382,159 to 376)[c] ?: 63
        private fun points(cs: List<Int>)=buildString { cs.forEach(::appendCodePoint) }
        private fun takeCodePoints(chunks: Sequence<String>,n: Int)=buildString { var remaining=n;for(c in chunks) { val count=minOf(remaining,c.codePointCount(0,c.length));append(c,0,c.offsetByCodePoints(0,count));remaining-=count;if(remaining==0)break } }
    }
}
