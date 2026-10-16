// Text.Pandoc.Readers.DokuWiki, Pandoc 3.12.1.
// Copyright (C) 2018-2020 Alexander Krotov <ilabdsf@gmail.com>.
// GNU GPL version 2 or later; original source and notices in upstream/pandoc.
// Narrow Parsing.General helpers: Copyright (C) 2006-2024 John MacFarlane.
package io.heapy.kotdoc.readers

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.ast.Builder as B
import io.heapy.kotdoc.ast.Alignment
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.uri.PandocURI
import io.heapy.kotdoc.xml.PandocXml
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.state.PandocState
import io.heapy.kotdoc.state.PandocStateContext

private typealias DW<A> = Parsec<ParserState,A>
object DokuWikiReader {
    fun read(options: ReaderOptions, text: String, runtime: PandocState = PandocStateContext()): Pandoc = read(options, Sources.fromText(text), runtime)
    fun read(options: ReaderOptions, sources: Sources, runtime: PandocState = PandocStateContext()): Pandoc {
        val result = Grammar(runtime).document().run(ParseState(ParseInput(sources), SourcePos(sources.initialSourceName()), ParserState(options=options)))
        if (!result.success) throw io.heapy.kotdoc.latex.LaTeXReader.fromParsecError(sources,result.error)
        return checkNotNull(result.value)
    }
    private class Grammar(val runtime: PandocState) {
        fun <A> pure(value: A): DW<A> = Parsec.pure(value)
        fun guard(value: Boolean): DW<Unit> = Parsec.guard(value)
        fun char(c: Char) = SourceParsers.char<ParserState>(c.code)
        fun text(s: String) = SourceParsers.string<ParserState>(s)
        fun any() = SourceParsers.anyChar<ParserState>()
        fun satisfy(f: (Int)->Boolean) = SourceParsers.satisfy<ParserState>(f)
        fun state() = Parsec.state<ParserState>()
        fun update(f: (ParserState)->ParserState) = Parsec.modify<ParserState> { it.copy(user=f(it.user)) }
        fun eof() = Parsec.eof<ParserState>()
        fun newline() = SourceParsers.newline<ParserState>()
        fun sp() = satisfy { it==32 || it==9 }
        fun space() = SourceParsers.space<ParserState>()
        fun nonspace() = satisfy { it !in intArrayOf(32,9,10,13) }
        fun skipSpaces() = sp().skipMany()
        fun blankline() = skipSpaces().then(newline()).attempt()
        fun eol() = newline().map { Unit } or eof()
        fun chars(xs: List<Int>) = buildString { xs.forEach(::appendCodePoint) }
        fun concat(xs: List<Inlines>) = xs.fold(Inlines(),Inlines::plus)
        fun blocks(xs: List<Blocks>) = xs.fold(Blocks(),Blocks::plus)
        class Chain<A>(val head: A,val tail: Chain<A>?)
        fun <A,C> till(p: DW<A>, end: DW<C>): DW<List<A>> {
            fun go(): DW<Chain<A>?> = end.map<Chain<A>?> { null } or p.flatMap { x -> Parsec.defer { go() }.map<Chain<A>?> { Chain(x,it) } }
            return Parsec.defer { go() }.map { chain -> buildList { var c=chain;while(c!=null){add(c.head);c=c.tail} } }
        }
        fun <A,C> till1(p: DW<A>,end: DW<C>) = not(end).then(p).flatMap { a -> till(p,end).map { listOf(a)+it } }
        fun <A> not(p: DW<A>): DW<Unit> = (p.attempt().map { Parsec.unexpected<ParserState,Unit>(show(it)) } or pure(pure(Unit))).flatMap { it }.attempt()
        fun show(value: Any?): String = when(value) {
            is Blocks -> "Many {unMany = fromList "+AstShow.show(value.values)+"}"
            is Inlines -> "Many {unMany = fromList "+AstShow.show(value.values)+"}"
            is Int -> Parsec.showChar(value)
            is String -> AstShow.show(value)
            Unit -> "()"
            else -> error("Unsupported parser observation: $value")
        }
        fun document(): DW<Pandoc> = block.many().before(SourceParsers.spaces()).before(eof()).map { B.doc(blocks(it)) }
        val inline: DW<Inlines> get() = Parsec.defer { endline() or inlinePrime }
        val inlinePrime: DW<Inlines> get() = Parsec.defer { whitespace() or inlineDouble }
        val inlineDouble: DW<Inlines> get() = Parsec.defer {
            br() or formatted("**",B::strong) or formatted("//",B::emph) or formatted("__",B::underline) or
                nowiki() or percent() or link() or image() or formatted("''") { B.code(DocumentShared.stringifyInlines(it.values)) } or
                betweenTag("sub",B::subscript) or betweenTag("sup",B::superscript) or betweenTag("del",B::strikeout) or
                enclosed("((","))") { B.note(B.para(it)) } or inlineRaw() or
                MathParsing.mathDisplay(ParserStateAccess).map(B::displayMath) or MathParsing.mathInline(ParserStateAccess).map(B::math) or
                autoLink() or autoEmail() or text("~~NOTOC~~").map { Inlines() }.attempt() or text("~~NOCACHE~~").map { Inlines() }.attempt() or
                smart().smartPunctuation(ReaderParsec.lift<Unit,ParserState,Inlines>(inline)).runReader(Deferred.now(Unit)) or
                str() or not(blockCode).then(nonspace()).map { B.str(chars(listOf(it))) }
        }.label("inline")
        fun smart() = SmartParsing<Unit,ParserState>(ParserStateAccess,ParserStateAccess,
            ReaderParsec.lift(ParserStateAccess.getQuoteContext()), { context,p -> ReaderParsec { env -> ParserStateAccess.withQuoteContext(context,p.runReader(env)) } })
        fun whitespace() = sp().many1().map { B.space }.attempt()
        fun endline() = skipSpaces().then(newline()).before(not(newline())).before(eof() or pure(Unit)).map { B.softbreak }.attempt()
        fun br() = text("\\\\").before(space()).map { B.linebreak }.attempt()
        fun nested(end: DW<*>): DW<Inlines> = whitespace().before(not(end)).attempt() or not(whitespace()).then(inline)
        fun enclosed(open: String,close: String, f: (Inlines)->Inlines): DW<Inlines> {
            val end=text(close).attempt()
            return text(open).then(not(whitespace())).then(till1(nested(end),end)).map { f(concat(it)) }.attempt()
        }
        fun formatted(delimiter: String,f: (Inlines)->Inlines) = enclosed(delimiter,delimiter,f)
        fun betweenTag(tag: String,f: (Inlines)->Inlines) = enclosed("<$tag>","</$tag>",f)
        fun nowiki() = text("<nowiki>").then(till(any(),text("</nowiki>").attempt())).map { B.text(chars(it)) }.attempt()
        fun percent(): DW<Inlines> {
            val end=text("%%").attempt()
            val inner=SourceParsers.takeWhile1P<ParserState> { it==32 || it==9 }.before(not(end)).attempt() or nonspace().map { chars(listOf(it)) }
            return text("%%").then(not(whitespace())).then(till1(inner,end)).map { B.text(it.joinToString("")) }.attempt()
        }
        fun inlineRaw(): DW<Inlines> = char('<').then(choice(listOf("html","php","latex"))).before(char('>')).flatMap { format ->
            till(any(),text("</$format>").attempt()).map { val content=chars(it);if(format=="php")B.rawInline("html","<?php $content ?>") else B.rawInline(format,content) }
        }.attempt()
        fun choice(values: List<String>): DW<String> = values.map(::text).reduce { a,b -> a or b }
        fun str(): DW<Inlines> = (SourceParsers.takeWhile1P<ParserState>(ParserCharacters::isAlphaNum) or entity()).map(B::str)
        fun entity(): DW<String> = char('&').then(till1(nonspace(),char(';'))).flatMap { points ->
            PandocXml.lookupEntity(chars(points)+";")?.let(::pure) ?: Parsec.fail("entity not found")
        }.attempt()
        fun parseLink(left: String,right: String): DW<Pair<String,Inlines?>> = text(left).then(till1(any(),(char('|').map{Unit} or text(right).attempt().map{Unit}).lookAhead())).flatMap { path ->
            (char('|').then(till1(any(),text(right).attempt().lookAhead()).map { B.trimInlines(B.text(chars(it))) as Inlines? }.option(null)) or pure(null)).before(text(right)).map { chars(path) to it }
        }
        fun link(): DW<Inlines> = state().flatMap { s -> guard(s.user.allowLinks).then(update { it.copy(allowLinks=false) }).then(parseLink("[[","]]")).before(update { it.copy(allowLinks=true) }).map { (raw,description) ->
            val path=SharedText.trim(raw);val split=splitInterwiki(path)
            B.link(if(split==null)normalizePath(path) else interwiki(split.first,split.second),"",description ?: B.str(split?.second ?: urlText(path)))
        } }.attempt()
        fun autoEmail(): DW<Inlines> = state().flatMap { guard(it.user.allowLinks) }.then(char('<')).then(email()).before(char('>')).map { B.link(it.second,"",B.str(it.first)) }.attempt()
        fun autoLink(): DW<Inlines> = state().flatMap { guard(it.user.allowLinks) }.then(uri()).flatMap { (label,url) ->
            guard(!label.contains("%%//%%") && (url.last()=='/' || ParserCharacters.isAlphaNum(url.codePointBefore(url.length)))).map { B.link(url,"",B.str(label)) }
        }.attempt()
        fun image(): DW<Inlines> = parseLink("{{","}}").map { (raw,description) ->
            val path=SharedText.trim(raw);val p=path.indexOf('?').let { if(it<0)path.length else it };val base=path.take(p);val query=path.drop(p)
            val params=query.drop(1).split('&');val dimensions=params.firstOrNull(::widthHeight)
            val width=dimensions?.takeWhile { it in '0'..'9' };val height=dimensions?.substringAfter('x',"")?.takeIf { dimensions.contains('x') }
            val attrs=buildList { width?.let { add("width" to it) };height?.let { add("height" to it) };if(query.isNotEmpty())add("query" to query) }
            val classes=when(raw.startsWith(' ') to raw.endsWith(' ')){false to true->listOf("align-left");true to false->listOf("align-right");true to true->listOf("align-center");else->emptyList()}
            val label=description ?: B.str(urlText(base))
            if("linkonly" in params)B.link(normalizePath(base),"",label) else B.imageWith(Attr(classes=classes,keyValues=attrs),normalizePath(base),"",label)
        }.attempt()
        val block: DW<Blocks> get() = Parsec.defer { (blankline().many1().map { Blocks() } or blockElements or para()).before(blankline().skipMany()).flatMap { value -> state().flatMap { st -> Parsec.lift {
            if(runtime.getTrace()) {
                val shown=AstShow.show(value.values).codePoints().limit(60).toArray()
                runtime.trace("Parsed "+chars(shown.toList())+" at line ${st.position.line}"+if(st.position.name=="chunk")" of chunk" else "")
            };value
        } } } }
        val blockElements: DW<Blocks> get() = Parsec.defer { horizontal() or header() or list("  ") or indentedCode() or quote() or blockCode or blockRaw() or table() }
        fun horizontal() = text("---").then(char('-').many1()).before(eol()).map { B.horizontalRule }.attempt()
        fun header(): DW<Blocks> = state().flatMap { guard(it.position.column==1L) }.then(char('=').many1()).flatMap { eqs ->
            guard(eqs.size<7).then(till(inline,char('=').then(char('=').many1()).attempt())).map { B.trimInlines(concat(it)) }.flatMap { content ->
                state().flatMap { st ->
                    val exts=st.user.options.extensions
                    if(ExtensionName.AutoIdentifiers in exts) {
                        val id=DokuWikiIdentifiers.unique(content.values,st.user.identifiers,exts)
                        val ascii=if(ExtensionName.AsciiIdentifiers in exts)Asciify.toAsciiText(id) else id
                        update { it.copy(identifiers=it.identifiers+id+ascii) }.map { B.headerWith(Attr(identifier=ascii),(7-eqs.size).toLong(),content) }
                    } else pure(B.header((7-eqs.size).toLong(),content))
                }
            }
        }.attempt()
        fun list(prefix: String): DW<Blocks> = parseList(prefix,'*').map(B::bulletList).attempt() or parseList(prefix,'-').map(B::orderedList).attempt()
        fun parseList(prefix: String,marker: Char): DW<List<Blocks>> {
            val contents=inlinePrime.many1().map { B.plain(concat(it)) } or blockCode
            val item=text(prefix).then(char(' ').optional()).then(char(marker)).then(char(' ')).then(contents.many1()).before(eol()).map(::blocks).attempt()
            return item.flatMap { first -> Parsec.defer { list("  $prefix") }.attempt().many().map { first+blocks(it) } }.many1()
        }
        fun indentedCode() = text("  ").then(till(any(),eol())).map(::chars).attempt().many1().map { B.codeBlock(it.joinToString("",postfix="") { line -> "$line\n" }) }.attempt()
        fun quote(): DW<Blocks> {
            val line=char('>').many1().before(skipSpaces()).flatMap { levels ->
                (blockCode.before(skipSpaces()).before(eol().optional()) or till1(inlinePrime,eol()).map { B.plain(B.trimInlines(concat(it))) }).map { levels.size to it }
            }.attempt()
            return line.many1().map(::quoteBlocks)
        }
        fun quoteBlocks(lines: List<Pair<Int,Blocks>>): Blocks {
            // Heap continuation preserves the source's recursive grouping.
            val go=DeepRecursiveFunction<List<Pair<Int,Blocks>>,Blocks> { xs ->
                var at=0;var out=Blocks()
                while(at<xs.size) {
                    val start=at++;val zero=xs[start].first==0
                    while(at<xs.size && (xs[at].first==0)==zero)at++
                    val group=xs.subList(start,at)
                    if(!zero)out+=B.blockQuote(callRecursive(group.map { it.first-1 to it.second }))
                    else {
                        var plain=Inlines();var others=Blocks()
                        for((_,b) in group) {
                            val one=b.values.singleOrNull()
                            if(one is Block.Plain)plain=if(plain.values.isEmpty())Inlines(one.content) else plain+B.linebreak+Inlines(one.content)
                            else { plain=Inlines();others+=b } // Original lns==lns branch deliberately discards accumulated text.
                        }
                        out+=others+if(plain.values.isEmpty())Blocks() else B.plain(plain)
                    }
                }
                out
            }
            return go(lines)
        }
        fun codeTag(tag: String): DW<Blocks> = char('<').then(text(tag)).then(
            sp().then(till(any(),(sp() or char('>')).lookAhead())).map(::chars).option("-").map { Attr(classes=if(it=="-")emptyList() else listOf(it)) }.attempt()
        ).flatMap { attr -> till(any(),char('>')).then(till(sp(),eol()).optional()).then(till(any(),text("</$tag>").attempt())).map { B.codeBlockWith(attr,chars(it)) } }.attempt()
        val blockCode: DW<Blocks> get() = Parsec.defer { codeTag("code") or codeTag("file") }
        fun blockRaw(): DW<Blocks> = char('<').then(choice(listOf("HTML","PHP","LATEX"))).before(char('>')).flatMap { format ->
            till(sp(),eol()).optional().then(till(any(),text("</$format>").attempt())).map { val content=chars(it);if(format=="PHP")B.rawBlock("html","<?php $content ?>") else B.rawBlock(format.lowercase(),content) }
        }.attempt()
        fun separator() = char('|') or char('^')
        fun tableCell(): DW<Pair<Alignment,Blocks>> = till(sp().map { B.space } or inlinePrime,separator()).map { values ->
            val left=values.take(2)==listOf(B.space,B.space);val right=values.takeLast(2)==listOf(B.space,B.space)
            val align=when(left to right){true to true->Alignment.AlignCenter;true to false->Alignment.AlignRight;false to true->Alignment.AlignLeft;else->Alignment.AlignDefault}
            align to B.plain(B.trimInlines(concat(values)))
        }.attempt()
        fun table(): DW<Blocks> = separator().lookAhead().flatMap { first ->
            separator().then(tableCell().many1()).before(till(any(),eol())).many1().map { rows ->
                val head=if(first=='^'.code)rows.first() else emptyList();val body=if(head.isEmpty())rows else rows.drop(1)
                fun row(cells: List<Pair<Alignment,Blocks>>) = Row(Attr(),cells.map { B.simpleCell(it.second) })
                TableCompaction.compactify(B.table(B.emptyCaption,rows.first().map { ColSpec(it.first,ColWidth.Default) },TableHead(Attr(),if(head.isEmpty())emptyList() else listOf(row(head))),listOf(TableBody(Attr(),0,emptyList(),body.map(::row))),TableFoot(Attr(),emptyList())))
            }
        }
        fun para(): DW<Blocks> {
            val endInput=blankline().skipMany().then(skipSpaces()).then(eof()).attempt()
            val endPara=blankline().then(blankline().many1()).map { Unit }.attempt()
            val newBlock=blankline().then(blockElements).map { Unit }.attempt() or blockCode.lookAhead().map { Unit }
            return till1(inline,(endInput or endPara or newBlock).lookAhead()).map { val value=concat(it);if(value.values.all { i -> i==Inline.Space })Blocks() else B.para(B.trimInlines(value)) }
        }
        fun email(): DW<Pair<String,String>> {
            fun parts(p: DW<String>) = p.flatMap { first -> char('.').then(p).attempt().many().map { (listOf(first)+it).joinToString(".") } }
            val mailbox=satisfy(ParserCharacters::isAlphaNum).flatMap { first -> satisfy { ParserCharacters.isAlphaNum(it) || it in "!\"#$%&'*+-/=?^_{|}~;".map(Char::code) }.many().map { chars(listOf(first)+it) } }
            val hyphen=char('-').before(not(satisfy { !ParserCharacters.isAlphaNum(it) })).attempt()
            val domain=(satisfy(ParserCharacters::isAlphaNum) or hyphen).many1().map(::chars)
            return parts(mailbox).flatMap { box -> char('@').then(parts(domain)).map { val value=PandocXml.fromEntities("$box@$it");value to PandocURI.escapeURI("mailto:$value") } }.attempt()
        }
        class Trie(var terminal: Boolean=false,val children: MutableMap<Int,Trie> = mutableMapOf())
        val schemes by lazy {
            val root=Trie()
            for(scheme in PandocURI.schemes) {var node=root;for(c in scheme.codePoints().toArray())node=node.children.getOrPut(c){Trie()};node.terminal=true}
            root
        }
        fun uriScheme(): DW<String> {
            fun go(node: Trie,acc: String): DW<String> = any().flatMap { cp ->
                val lower=UnicodeText.toLower(chars(listOf(cp))).codePointAt(0)
                val sub=node.children[lower]
                if(sub==null)Parsec.fail("not a URI scheme") else {
                    val next=acc+chars(listOf(cp));val rest=Parsec.defer { go(sub,next) }
                    if(sub.terminal)rest.attempt().option(next) else rest
                }
            }
            return Parsec.defer { go(schemes,"") }.attempt()
        }
        fun uri(): DW<Pair<String,String>> {
            val word=satisfy { ParserCharacters.isAlphaNum(it) || it in "#$%+/@\\_-&=".map(Char::code) }
            val percent=char('%').flatMap { first -> SourceParsers.hexDigit<ParserState>().many1().map { chars(listOf(first)+it) } }.attempt()
            val punctuation=(char(',').many1().map(::chars) or satisfy { !ParserCharacters.isSpace(it) && it!=60 && it!=62 }.map { chars(listOf(it)) }).attempt()
            val chunk=word.many1().map(::chars) or percent or entity().attempt() or punctuation.before((word.map { Unit } or percent.map { Unit }).lookAhead()).attempt()
            fun balanced(left: Char,right: Char) = char(left).then(chunk).before(char(right)).map { "$left$it$right" }.attempt()
            return uriScheme().before(char(':')).before(not(satisfy { it==42 || it==95 || it==93 })).flatMap { scheme ->
                (balanced('(',')') or balanced('{','}') or balanced('[',']') or chunk).many1().map { it.joinToString("") }.flatMap { body -> char('/').map { "$body/" }.option(body) }.map { val original=scheme+":"+PandocXml.fromEntities(it);original to PandocURI.escapeURI(original) }
            }.attempt()
        }
        fun prefixWhile(path: String,test: (Int)->Boolean): String { var at=0;while(at<path.length && test(path.codePointAt(at)))at+=Character.charCount(path.codePointAt(at));return path.take(at) }
        fun external(path: String): Boolean = path.drop(prefixWhile(path) { ParserCharacters.isAlphaNum(it) || it in intArrayOf(45,46,43) }.length).startsWith("://")
        fun splitInterwiki(path: String): Pair<String,String>? {
            val pref=prefixWhile(path) { ParserCharacters.isAlphaNum(it) || it==46 };val rest=path.drop(pref.length)
            return if(rest.startsWith('>'))pref to rest.drop(1) else null
        }
        fun normalizePath(path: String): String {
            if(external(path))return path
            val absolute=!path.startsWith('.') && path.contains(':')
            val prefix=path.takeWhile { it=='.' };val rest=path.drop(prefix.length)
            val normalizedDots=if(prefix.isNotEmpty() && !rest.startsWith(':'))"$prefix:$rest" else path
            val normalized=normalizedDots.split(':').dropWhile { it=="." }.joinToString("/")
            return if(absolute && !normalized.startsWith('/'))"/$normalized" else normalized
        }
        fun urlText(path: String) = if(external(path))path else path.substringAfterLast(':')
        fun interwiki(prefix: String,page: String) = when(prefix) {
            "callto"->"callto://$page";"doku"->"https://www.dokuwiki.org/$page";"phpfn"->"https://secure.php.net/$page";"tel"->"tel:$page"
            "wp"->"https://en.wikipedia.org/wiki/$page";"wpde"->"https://de.wikipedia.org/wiki/$page";"wpes"->"https://es.wikipedia.org/wiki/$page";"wpfr"->"https://fr.wikipedia.org/wiki/$page";"wpjp"->"https://jp.wikipedia.org/wiki/$page";"wppl"->"https://pl.wikipedia.org/wiki/$page";else->"$prefix>$page"
        }
        fun widthHeight(value: String): Boolean {
            if(value.isEmpty() || value[0] !in '0'..'9')return false
            val rest=value.dropWhile { it in '0'..'9' }
            return rest.isEmpty() || rest.startsWith('x') && rest.length>1 && rest.drop(1).all { it in '0'..'9' }
        }
    }
}
