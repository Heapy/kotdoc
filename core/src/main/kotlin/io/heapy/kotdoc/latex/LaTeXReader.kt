// Text.Pandoc.Readers.LaTeX, Pandoc 3.12.1.
// Copyright (C) 2006-2024 John MacFarlane. GPL-2.0-or-later;
// full original source and notice in upstream/pandoc and licenses/.
package io.heapy.kotdoc.latex

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.ast.Builder as B
import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.formats.HaskellNumber
import io.heapy.kotdoc.highlighting.Highlighting
import io.heapy.kotdoc.highlighting.Syntaxes
import io.heapy.kotdoc.images.Dimensions
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.resources.*
import io.heapy.kotdoc.tex.*
import io.heapy.kotdoc.tex.SourcePos
import io.heapy.kotdoc.translations.translateTerm

/** File, translation and common-state effects stay at the existing runtime
 * boundary. zonedDay supplies showGregorian(localDay(getZonedTime)). */
class LaTeXReader(val runtime: PandocDataRuntime, private val zonedDay: () -> String) {
    val kernel = LaTeXKernel(runtime)
    private val macro = LaTeXMacros(kernel)
    private val math = LaTeXMathReader(kernel)
    private val citations = LaTeXCitations(kernel)
    private val languages = LaTeXLanguages(kernel)
    private val commands = LaTeXInlines(kernel,runtime::translateTerm)
    private val tables = LaTeXTables(kernel)
    private fun <A> pure(x: A): LP<A> = ReaderTokenParsec.pure(x)
    private fun <A> zero(): LP<A> = ReaderTokenParsec.zero()
    private fun <A> defer(p: () -> LP<A>): LP<A> = ReaderTokenParsec.defer(p)
    private fun <A> effect(f: () -> A): LP<A> = ReaderTokenParsec.lift(TokenParsec.lift(f))
    private fun state() = LaTeXParsing.state()
    private fun update(f: (LaTeXState)->LaTeXState) = LaTeXParsing.update(f)
    private fun guard(b: Boolean): LP<Unit> = if(b)pure(Unit) else zero()
    private fun enabled(name: String): LP<Unit> = state().flatMap { guard(Extension.read(name) in it.user.options.extensions) }
    private fun <A,B> till(p: LP<A>,end: LP<B>): LP<List<A>> = ReaderTokenParsec { env -> TokenParsec.manyTill(p.runReader(env),end.runReader(env)) }
    private fun eof(): LP<Unit> = ReaderTokenParsec.lift(TokenParsec.eof(Tok::show))
    private fun untok(tokens: TokenList) = LaTeXTokens.untokenize(tokens)
    private fun position() = state().map { it.position }
    private fun setInput(s: TokStream) = LaTeXParsing.modify { it.copy(input=s) }
    private fun input() = state().map { it.input as TokStream }
    private fun report(message: LogMessage) = effect { runtime.report(message) }
    private fun concat(xs: List<Inlines>) = xs.fold(Inlines(),Inlines::plus)
    private fun blocks(xs: List<Blocks>) = xs.fold(Blocks(),Blocks::plus)
    private fun groupedInline() = kernel.grouped(inline,Inlines(),Inlines::plus)
    private fun groupedBlock() = kernel.grouped(block,Blocks(),Blocks::plus)
    private fun tok() = kernel.tokWith(inline)
    private fun bracketedInline() = kernel.bracketed(inline,Inlines(),Inlines::plus)
    private fun opt(): LP<Inlines> = kernel.sp().then(kernel.bracketedToks()).before(kernel.sp()).attempt().flatMap { LaTeXParsing.parseFromToks(inlines,it) }
    val inline: LP<Inlines> get() = defer(::parseInline)
    val inlines: LP<Inlines> get() = inline.many().map(::concat)
    val block: LP<Blocks> get() = defer(::parseBlock)
    val blocks: LP<Blocks> get() = block.many().map(::blocks)
    val environment: LaTeXEnv by lazy { LaTeXEnv(inlineCommands,blockCommands,environments) }

    fun read(options: ReaderOptions,sources: Sources): Pandoc {
        val reply=parseLaTeX().run(TokenParseState(TokStream(false,LaTeXTokens.tokenizeSources(sources)),SourcePos("source"),LaTeXState(options=options)),environment)
        if(!reply.success)throw fromParsecError(sources,reply.error)
        return checkNotNull(reply.value)
    }
    fun read(options: ReaderOptions,text: String): Pandoc = read(options,Sources.fromText(text))
    fun <U> applyMacros(text: String,macros: MacrosAccess<U>,options: ReaderOptionsAccess<U>): Parsec<U,String> = LaTeXParsingBridge(kernel).applyMacros(text,macros,options)
    private fun <U> commandStart(): Parsec<U,Unit> = SourceParsers.char<U>('\\'.code).then(SourceParsers.letter<U>()).attempt().lookAhead().map { Unit }
    fun <U> rawLaTeXInline(macros: MacrosAccess<U>,options: ReaderOptionsAccess<U>): Parsec<U,String> = commandStart<U>().then(LaTeXTokens.getInputTokens<U>()).flatMap { ts ->
        val bridge=LaTeXParsingBridge(kernel)
        val input=kernel.controlSeq("input").then(kernel.rawopt().skipMany()).then(kernel.braced()).map { Unit }
        (bridge.rawLaTeXParser(environment,ts,input,inlines,macros,options) or bridge.rawLaTeXParser(environment,ts,inline.map { Unit },inlines,macros,options)).flatMap { pair ->
            Parsec.string<U>("{}").attempt().many().map { pair.second+it.joinToString("") }
        }
    }
    fun <U> inlineCommand(macros: MacrosAccess<U>,options: ReaderOptionsAccess<U>): Parsec<U,Inlines> = commandStart<U>().then(LaTeXTokens.getInputTokens<U>()).flatMap { ts ->
        LaTeXParsingBridge(kernel).rawLaTeXParser(environment,ts,(math.inlineEnvironment() or inlineCommand()).map { Unit },inlines,macros,options).map { it.first }
    }
    fun <U> rawLaTeXBlock(macros: MacrosAccess<U>,options: ReaderOptionsAccess<U>): Parsec<U,String> = commandStart<U>().then(LaTeXTokens.getInputTokens<U>()).flatMap { ts ->
        val bridge=LaTeXParsingBridge(kernel)
        val atLetter=kernel.controlSeq("makeatletter").then(till(kernel.whitespace() or kernel.newlineTok() or macro.macroDef({ Unit },Unit) or environmentParser().map { Unit } or blockCommand().map { Unit },kernel.controlSeq("makeatother"))).map { Unit }.attempt()
        val include=listOf("include","input","subfile","usepackage").map { kernel.controlSeq(it) }.reduce { a,b->a or b }.then(opt().skipMany()).then(kernel.braced()).map { Unit }
        val prep=atLetter or macro.macroDef({ Unit },Unit) or include
        (bridge.rawLaTeXParser(environment,ts,prep,blocks,macros,options) or
            bridge.rawLaTeXParser(environment,ts,(environmentParser() or blockCommand()).map { Unit },(block or beginOrEndCommand()).many().map(::blocks),macros,options)).map { it.second }
    }
    private fun beginOrEndCommand(): LP<Blocks> = kernel.anyControlSeq().flatMap { token ->
        val name=(token.type as TokType.CtrlSeq).text
        guard(name=="begin" || name=="end").then(LaTeXParsing.withRaw(kernel.braced())).flatMap { (env,args) ->
            guard(untok(env) !in math.inlineEnvironments).map { B.rawBlock("latex",token.text+untok(args)) }
        }
    }.attempt()
    fun parseLaTeX(): LP<Pandoc> = blocks.before(eof()).flatMap { bs -> state().map { p ->
        val st=p.user
        var minimum=1L
        object : PandocVisitor() { override fun visitBlock(value: Block) { if(value is Block.Header)minimum=minOf(minimum,value.level) } }.walkBlocks(bs.values)
        val resolve=object : PandocVisitor() {
            override fun transformInline(value: Inline): Inline = resolveRefs(st.labels,value)
        }
        val footnotes=object : PandocVisitor() {
            override fun transformInline(value: Inline): Inline = resolveFootnoteMarks(st.footnoteTexts,value)
        }
        var result=footnotes.walkBlocks(resolve.walkBlocks(bs.values))
        if(minimum<1)result=object : PandocVisitor() {
            override fun transformBlock(value: Block): Block = if(value is Block.Header)value.copy(level=value.level+1-minimum) else value
        }.walkBlocks(result)
        Pandoc(st.meta,result)
    } }
    private fun inlineGroup(): LP<Inlines> = groupedInline().map { if(it.values.isEmpty())it else B.spanWith(Attr(),it) }
    private fun parseInline(): LP<Inlines> = kernel.peekTok().flatMap { t -> state().flatMap { p ->
        val eat=kernel.satisfyTok { true }
        val literal=eat.map { B.str(t.text) }
        val unescaped=eat.then(report(LogMessage.ParsingUnescaped(t.text,t.position))).map { B.str(t.text) }
        when(t.type) {
            TokType.Comment->eat.map { Inlines() }
            TokType.Spaces->eat.map { B.space }
            TokType.Newline->kernel.endline().map { B.softbreak }
            TokType.Word->literal
            TokType.Symbol->when(t.text) {
                "-"->if(p.user.ligatures)eat.then(kernel.symbol('-').then(kernel.symbol('-').map { B.str("—") }.option(B.str("–"))).option(B.str("-"))) else literal
                "'"->if(p.user.ligatures)eat.then(kernel.symbol('\'').map { B.str("”") }.option(B.str("’"))) else literal
                "~"->eat.map { B.str("\u00a0") }
                "`"->if(p.user.ligatures)doubleQuote() or singleQuote() or kernel.symbol('`').map { B.str("‘") } else literal
                "\""->if(p.user.ligatures)doubleQuote() or singleQuote() or literal else literal
                "“"->doubleQuote() or literal
                "‘"->singleQuote() or literal
                "$"->math.dollarsMath() or unescaped
                "|"->enabled("literate_haskell").then(eat).then(till(kernel.satisfyTok { it.type!=TokType.Newline },kernel.symbol('|'))).map { B.codeWith(Attr(classes=listOf("haskell")),LaTeXTokens.untokenize(it)) } or literal
                "{"->inlineGroup()
                "#","&","_","^"->unescaped
                "\\","}"->zero()
                else->literal
            }
            is TokType.CtrlSeq->macro.macroDef({ B.rawInline("latex",it) },Inlines()) or inlineGroup() or inlineCommand() or math.inlineEnvironment()
            TokType.Esc1,TokType.Esc2->kernel.primEscape().map { B.str(String(Character.toChars(it))) }
            else->zero()
        }
    } }
    private fun quoted(f: (Inlines)->Inlines,start: LP<List<Tok>>,end: LP<Unit>): LP<Inlines> = start.flatMap { ts -> state().flatMap { s ->
        val text=LaTeXTokens.untokenize(ts)
        if(Extension.read("smart") !in s.user.options.extensions)pure(B.str(text)) else
            end.notFollowedBy { "()" }.then(inline).many().flatMap { xs ->
                end.map { f(concat(xs)) } or pure(B.str(when(text) {"``"->"“";"`"->"‘";else->text})+concat(xs))
            }
    } }
    private fun two(a: Char,b: Char): LP<List<Tok>> = kernel.symbol(a).flatMap { x -> kernel.symbol(b).map { listOf(x,it) } }.attempt()
    private fun doubleQuote(): LP<Inlines> = quoted(B::doubleQuoted,two('`','`'),two('\'','\'').map { Unit }) or
        quoted(B::doubleQuoted,kernel.symbol('“').map { listOf(it) },kernel.symbol('”').map { Unit }) or
        quoted(B::doubleQuoted,two('"','`'),two('"','\'').map { Unit })
    private fun singleQuote(): LP<Inlines> {
        val letter=kernel.satisfyTok { it.type==TokType.Word && it.text.isNotEmpty() && ParserCharacters.isAlpha(it.text.codePointAt(0)) }
        fun end(c: Char)=kernel.symbol(c).then(letter.notFollowedBy(Tok::show)).attempt()
        return quoted(B::singleQuoted,kernel.symbol('`').map { listOf(it) },end('\'')) or quoted(B::singleQuoted,kernel.symbol('‘').map { listOf(it) },end('’'))
    }
    fun inlineCommand(): LP<Inlines> = kernel.anyControlSeq().flatMap { t ->
        val name=(t.type as TokType.CtrlSeq).text
        guard(name !in listOf("begin","end","and")).then(
            if(name.codePoints().allMatch(ParserCharacters::isAlphaNum))kernel.symbol('*').before(kernel.sp()).map { "*" }.option("") else pure("")
        ).flatMap { star -> kernel.overlaySpecification().option("").flatMap { overlay ->
            LaTeXParsing.askEnv().flatMap { env -> env.inlineCommands[name+star+overlay] ?: env.inlineCommands[name] ?:
                guard(isInlineCommand(name) || !isBlockCommand(name)).then(kernel.getRawCommand(name,t.text+star)).flatMap { raw ->
                    enabled("raw_tex").map { B.rawInline("latex",raw) } or kernel.ignore(raw,Inlines())
                }
            }
        } }
    }.attempt()
    private fun disableLigatures(p: LP<Inlines>): LP<Inlines> = state().flatMap { old ->
        update { it.copy(ligatures=false) }.then(p).flatMap { v -> update { it.copy(ligatures=old.user.ligatures) }.map { v } }
    }
    val inlineCommands: Map<String,LP<Inlines>> by lazy {
        val rest=sortedMapOf<String,LP<Inlines>>(UnicodeTextOrder)
        fun put(name: String,p: LP<Inlines>) { rest[name]=p }
        fun format(name: String,f: (Inlines)->Inlines,p: LP<Inlines> = tok()) { put(name,p.map { InlineShared.extractSpaces(f,it) }) }
        for(name in listOf("emph","textit","textsl"))format(name,B::emph)
        format("textsc",B::smallcaps)
        for((name,style) in listOf("textsf" to "sans-serif","textmd" to "medium","textrm" to "roman","textup" to "upright","textnormal" to "nodecor"))format(name,{ B.spanWith(Attr(classes=listOf(style)),it) })
        put("texttt",disableLigatures(tok()).map { InlineShared.formatCode(Attr(),it) })
        put("alert",kernel.skipopts().then(tok()).map { B.spanWith(Attr(classes=listOf("alert")),it) })
        format("textsuperscript",B::superscript);format("textsubscript",B::subscript);format("textbf",B::strong)
        put("underline",tok().map(B::underline))
        for(name in listOf("mbox","hbox"))put(name,commands.rawInlineOr(name,tok().map(LaTeXReaderText::processHBox)))
        put("vbox",commands.rawInlineOr("vbox",tok()));put("lettrine",commands.rawInlineOr("lettrine",defer(::lettrine)))
        put("(",math.withMathMode(till(kernel.anyTok(),kernel.controlSeq(")")).map { LaTeXMathReader.mathInline(LaTeXTokens.untokenize(it)) }))
        put("[",math.withMathMode(till(kernel.anyTok(),kernel.controlSeq("]")).map { LaTeXMathReader.mathDisplay(LaTeXTokens.untokenize(it)) }))
        put("ensuremath",math.withMathMode(kernel.braced().map { LaTeXMathReader.mathInline(untok(it)) }))
        put("texorpdfstring",tok().before(tok()))
        for(name in listOf("em","it","sl","itshape","slshape"))format(name,B::emph,inlines)
        for(name in listOf("bf","bfseries"))format(name,B::strong,inlines)
        put("tt",inlines.map { InlineShared.formatCode(Attr(),it) });format("ttfamily",{ InlineShared.formatCode(Attr(),it) },inlines)
        put("rm",inlines);format("scshape",B::smallcaps,inlines)
        for(name in listOf("MakeUppercase","MakeTextUppercase","uppercase"))put(name,defer { caseTransform("upper",LaTeXReaderText::toUpper) })
        for(name in listOf("MakeLowercase","MakeTextLowercase","lowercase"))put(name,defer { caseTransform("lower",LaTeXReaderText::toLower) })
        put("MakeTitlecase",defer(::titlecase));put("NoCaseChange",tok().map { B.spanWith(Attr(classes=listOf("nocasechange")),it) })
        put("CaseSwitch",tok().before(tok()).before(tok()).before(tok()))
        put("thanks",kernel.skipopts().then(groupedBlock()).map(B::note))
        put("footnote",kernel.skipopts().then(defer(::footnote)));put("footnotemark",defer(::footnotemark));put("footnotetext",defer(::footnotetext))
        put("newline",pure(B.linebreak));put("NoValue",pure(B.str("-NoValue-")));put("BooleanTrue",pure(Inlines()));put("BooleanFalse",pure(Inlines()))
        put("passthrough",tok().map(LaTeXReaderText::fixPassthroughEscapes))
        for(name in listOf("includegraphics","includesvg"))put(name,kernel.keyvals().option(emptyList()).flatMap { opts -> bracedFilename().flatMap { mkImage(opts,LaTeXReaderText.unescapeURL(it)) } })
        put("url",kernel.bracedUrl().map { val u=LaTeXReaderText.unescapeURL(untok(it));B.linkWith(Attr(classes=listOf("uri")),u,"",B.str(u)) })
        put("nolinkurl",kernel.bracedUrl().map { B.code(LaTeXReaderText.unescapeURL(untok(it))) })
        put("href",kernel.bracedUrl().flatMap { u -> kernel.sp().then(tok()).map { B.link(LaTeXReaderText.unescapeURL(untok(u)),"",it) } })
        put("hyperlink",kernel.braced().flatMap { u -> tok().map { B.link("#"+untok(u),"",it) } }.attempt())
        put("hyperref",defer(::hyperref));put("hypertarget",kernel.braced().flatMap { id -> tok().map { B.spanWith(Attr(untok(id)),it) } }.attempt())
        put("nohyphens",tok());put("textnhtt",tok().map { InlineShared.formatCode(Attr(),it) });put("nhttfamily",tok().map { InlineShared.formatCode(Attr(),it) })
        for((name,style) in listOf("textcolor" to "color","colorbox" to "background-color"))put(name,kernel.skipopts().then(kernel.braced()).flatMap { c -> tok().map { B.spanWith(Attr(keyValues=listOf("style" to "$style: ${untok(c)}")),it) } })
        put("newtoggle",toggle(null).map { Inlines() });put("toggletrue",toggle(true).map { Inlines() });put("togglefalse",toggle(false).map { Inlines() })
        put("iftoggle",defer(::ifToggle).then(inline).attempt())
        for(name in listOf("input","expandableinput"))put(name,commands.rawInlineOr(name,defer { include("input") }.map { Inlines() }))
        for(name in listOf("st","sout"))format(name,B::strikeout)
        for(name in listOf("ul","uline"))put(name,tok().map(B::underline))
        format("hl",{ B.spanWith(Attr(classes=listOf("mark")),it) })
        put("ifdim",till(kernel.anyTok(),kernel.controlSeq("fi")).map { B.rawInline("latex","\\ifdim"+LaTeXTokens.untokenize(it)+"\\fi") })
        put("today",effect { B.text(zonedDay()) });put("pandocbounded",tok())
        for((name,text) in listOf("ampersand" to "&","atsign" to "@","backslash" to "\\","left_brace" to "{","right_brace" to "}","circumflex" to "^","colon" to ":","dollar" to "$","hash" to "#","percent" to "%","tilde" to "~","underscore" to "_"))put("c_${name}_str",pure(B.str(text)))
        val result=sortedMapOf<String,LP<Inlines>>(UnicodeTextOrder)
        for(map in listOf(commands.accentCommands(tok()),commands.miscCommands(),citations.citationCommands(inline),LaTeXSIunitx(kernel).siunitxCommands(tok()),commands.acronymCommands(),commands.refCommands(),commands.nameCommands(),commands.verbCommands(),commands.charCommands(),languages.enquoteCommands(tok()),languages.inlineLanguageCommands(tok()),commands.biblatexInlineCommands(tok()),rest))
            for((k,v) in map)result.putIfAbsent(k,v)
        java.util.Collections.unmodifiableMap(result)
    }
    private fun bracedFilename(): LP<String> = kernel.braced().map { ts -> LaTeXReaderText.removeDoubleQuotes(untok(filterTokens(ts) { it.type!=TokType.Comment }).trim { ParserCharacters.isSpace(it.code) }) }
    private fun filterTokens(ts: TokenList,p: (Tok)->Boolean): TokenList = tokenList(materialize(ts).filter(p))
    private fun materialize(ts: TokenList): List<Tok> { val out=ArrayList<Tok>();var n=ts.value();while(n is DeferredChain.Cons) {out.add(n.head.value());n=n.tail.value()};return out }
    private fun mkImage(options: List<Pair<String,String>>,src: String): LP<Inlines> = state().flatMap { s -> effect {
        val kvs=options.filter { it.first in listOf("width","height") }.map { (k,v) ->
            val parsed=Dimensions.numUnit(v);k to if(parsed!=null && parsed.second in listOf("\\textwidth","\\linewidth","\\textheight")) Dimensions.showFl(parsed.first*100)+"%" else v
        }
        var path=src
        if(extension(src).isEmpty()) {
            val default=s.user.options.defaultImageExtension
            if(default.isNotEmpty())path=addExtension(src,default) else {
                val suffixes=listOf(".pdf",".png",".jpg",".mps",".jpeg",".jbig2",".jb2")
                path=(suffixes+suffixes.map(LaTeXReaderText::toUpper)).firstNotNullOfOrNull { suffix -> addExtension(src,suffix).takeIf { runtime.fileExists(CharacterStringChars.fromString(it)) } } ?: src
            }
        }
        B.imageWith(Attr(keyValues=kvs),path,"",B.str(options.firstOrNull { it.first=="alt" }?.second ?: "image"))
    } }
    private fun hyperref(): LP<Inlines> {
        val bracket=kernel.sp().then(kernel.bracketedToks()).before(kernel.sp()).attempt().map { "#"+untok(it) }
        val brace=kernel.bracedUrl().before(kernel.bracedUrl()).before(kernel.bracedUrl()).map(::untok)
        return (bracket or brace).flatMap { u -> tok().map { B.link(u,"",it) } }.attempt()
    }
    private fun caseTransform(kind: String,f: (String)->String): LP<Inlines> = kernel.keyvals().option(emptyList()).then(state()).flatMap { s -> tok().map { LaTeXReaderText.caseTransformWith(s.user.caseExclusions[kind] ?: emptySet(),f,it) } }
    private fun titlecase(): LP<Inlines> = kernel.keyvals().option(emptyList()).flatMap { options -> state().flatMap { s -> tok().map {
        if(options.firstOrNull { p -> p.first=="words" }?.second=="all")LaTeXReaderText.makeTitlecaseAll(s.user.caseExclusions["title"] ?: emptySet(),it) else LaTeXReaderText.makeTitlecase(it)
    } } }
    private fun lettrine(): LP<Inlines> = kernel.rawopt().optional().then(tok()).flatMap { x -> tok().map { InlineShared.extractSpaces({ v -> B.spanWith(Attr(classes=listOf("lettrine")),v) },x)+B.smallcaps(it) } }
    private fun optionalNoteNum(): LP<Long?> = kernel.bracketedToks().map { readNoteNum(untok(it)) }.option(null)
    private fun resolveNoteLabels(bs: Blocks): LP<Blocks> = state().flatMap { initial ->
        var s=initial.user
        val visitor=object : PandocVisitor() {
            override fun transformInline(value: Inline): Inline {
                if(value !is Inline.Span)return value
                val lab=value.attr.keyValues.firstOrNull { it.first=="label" }?.second ?: return value
                s=s.copy(labels=s.labels+(lab to B.text(s.lastNoteNum.toString()).values))
                return Inline.Span(value.attr.copy(identifier=lab),emptyList())
            }
        }
        val result=Blocks(visitor.walkBlocks(bs.values));update { s }.map { result }
    }
    private fun footnote(): LP<Inlines> = update { it.copy(lastNoteNum=it.lastNoteNum+1) }.then(groupedBlock()).flatMap(::resolveNoteLabels).map(B::note)
    private fun footnotemark(): LP<Inlines> = optionalNoteNum().flatMap { n ->
        (if(n!=null)pure(n) else update { it.copy(lastNoteNum=it.lastNoteNum+1) }.then(state()).map { it.user.lastNoteNum }).map { B.spanWith(Attr(classes=listOf("footnote-mark"),keyValues=listOf("note-num" to it.toString())),Inlines()) }
    }
    private fun footnotetext(): LP<Inlines> = optionalNoteNum().flatMap { n -> state().flatMap { s ->
        groupedBlock().flatMap(::resolveNoteLabels).flatMap { bs -> update { it.copy(footnoteTexts=it.footnoteTexts+((n ?: s.user.lastNoteNum) to bs)) }.map { Inlines() } }
    } }
    private fun toggle(value: Boolean?): LP<Unit> = kernel.braced().flatMap { ts -> update {
        val key=untok(ts);it.copy(toggles=if(value==null)it.toggles+(key to false) else if(key in it.toggles)it.toggles+(key to value) else it.toggles)
    } }
    private fun ifToggle(): LP<Unit> = kernel.braced().flatMap { name -> kernel.spaces().then(LaTeXParsing.withVerbatimMode(kernel.braced())).flatMap { yes ->
        kernel.spaces().then(LaTeXParsing.withVerbatimMode(kernel.braced())).flatMap { no -> state().flatMap { s ->
            val key=untok(name);val value=s.user.toggles[key]
            if(value==null)report(LogMessage.UndefinedToggle(key,s.position)) else setInput(TokStream(false,(TokStream(false,if(value)yes else no)+(s.input as TokStream)).tokens))
        } }
    } }
    private fun parseBlock(): LP<Blocks> = kernel.peekTok().flatMap { t ->
        val primary=when(t.type) {
            TokType.Newline,TokType.Spaces,TokType.Comment->kernel.spaces1().map { Blocks() }
            TokType.Word->paragraph()
            is TokType.CtrlSeq->if((t.type as TokType.CtrlSeq).text=="begin")environmentParser() else macro.macroDef({ B.rawBlock("latex",it) },Blocks()) or blockCommand()
            else->zero()
        }
        (primary or paragraph() or groupedBlock()).flatMap { result -> effect {
            if(runtime.getTrace())runtime.trace(buildString {
                var remaining=60
                for(chunk in AstShow.chunks(result.values)) {
                    val count=chunk.codePointCount(0,chunk.length)
                    append(chunk,0,chunk.offsetByCodePoints(0,minOf(remaining,count)))
                    remaining-=minOf(remaining,count)
                    if(remaining==0)break
                }
            })
            result
        } }
    }
    private fun paragraph(): LP<Blocks> = inline.many1().map { val x=B.trimInlines(concat(it));if(x.values.isEmpty())Blocks() else B.para(x) }
    private fun preamble(): LP<Blocks> = defer {
        val p=kernel.spaces1().map { Blocks() } or macro.macroDef({ B.rawBlock("latex",it) },Blocks()) or filecontents() or
            blockCommand().map { Blocks() } or kernel.braced().map { Blocks() } or
            kernel.begin("document").notFollowedBy { "()" }.then(kernel.anyTok()).map { Blocks() }
        p.many().map(::blocks)
    }
    fun blockCommand(): LP<Blocks> = kernel.anyControlSeq().flatMap { t ->
        val name=(t.type as TokType.CtrlSeq).text
        guard(name !in listOf("begin","end","and")).then(kernel.symbol('*').before(kernel.sp()).map { "*" }.option("")).flatMap { star ->
            val start=kernel.anyControlSeq().flatMap { guard((it.type as TokType.CtrlSeq).text.startsWith("start")) }.attempt()
            fun rawContents()=kernel.getRawCommand(name,t.text+star).flatMap { raw -> enabled("raw_tex").map { B.rawBlock("latex",raw) } or kernel.ignore(raw,Blocks()) }
            val definite=defer { guard(isBlockCommand(name)).then(rawContents()) }
            val maybe=defer { guard(!isInlineCommand(name)).then(rawContents()).flatMap { curr ->
                start.notFollowedBy { "()" }.then(defer(::blockCommand)).many().before((kernel.blankline() or start).lookAhead()).map { curr+blocks(it) }
            } }.attempt()
            LaTeXParsing.askEnv().flatMap { env -> env.blockCommands[name+star] ?: env.blockCommands[name] ?: (definite or maybe) }
        }
    }.attempt()
    private fun rawBlockOr(name: String,p: LP<Blocks>): LP<Blocks> = state().flatMap { s ->
        if(Extension.read("raw_tex") in s.user.options.extensions)kernel.getRawCommand(name,"\\$name").map { B.rawBlock("latex",it) } else p
    }
    private fun registerHeader(attr: Attr,content: Inlines): LP<Attr> = state().flatMap { p ->
        val old=p.user;val exts=old.options.extensions
        if(attr.identifier.isEmpty() && Extension.read("auto_identifiers") in exts) {
            val id=LaTeXReaderText.uniqueIdent(content.values,old.identifiers,exts)
            val ascii=if(Extension.read("ascii_identifiers") in exts)Asciify.toAsciiText(id) else id
            update { it.copy(identifiers=it.identifiers+id+ascii) }.map { attr.copy(identifier=ascii) }
        } else update { s ->
            if(attr.identifier.isEmpty())s else {
                val st=if(attr.identifier in s.identifiers)LaTeXStateAccess.addLogMessage(LogMessage.DuplicateIdentifier(attr.identifier,p.position),s) else s
                st.copy(identifiers=st.identifiers+attr.identifier)
            }
        }.map { attr }
    }
    private fun section(attr: Attr,level: Long): LP<Blocks> = kernel.skipopts().then(groupedInline()).flatMap { contents ->
        kernel.spaces().then(kernel.controlSeq("label")).then(kernel.spaces()).then(kernel.braced()).map(::untok).attempt().option(attr.identifier).flatMap { label ->
            (if(level==0L)update { it.copy(hasChapters=true) } else pure(Unit)).then(
                if("unnumbered" in attr.classes)pure(Unit) else update { s ->
                    val number=s.lastHeaderNum.increment(level+if(s.hasChapters)1 else 0)
                    s.copy(lastHeaderNum=number,labels=s.labels+(label to listOf(Inline.Str(number.render()))))
                }
            ).then(registerHeader(attr.copy(identifier=label),contents)).map { B.headerWith(it,level,contents) }
        }
    }
    private fun <A,B> sepBy(p: LP<A>,sep: LP<B>): LP<List<A>> = p.flatMap { first -> sep.then(p).many().map { listOf(first)+it } }.option(emptyList())
    private fun authors(): LP<Unit> = kernel.bgroup().then(sepBy(block.many1().map { DocumentShared.blocksToInlines(blocks(it).values) },kernel.controlSeq("and")))
        .before(kernel.egroup()).flatMap { authors -> LaTeXParsing.addMeta("author",MetaValue.MetaList(authors.map { B.trimInlines(it).toMetaValue() })) }.attempt()
    private fun rule(): LP<Blocks> = kernel.skipopts().then(tok()).flatMap { width -> tok().map {
        val text=DocumentShared.stringifyInlines(width.values).takeWhile { c -> c in '0'..'9' || c=='.' }
        if(HaskellNumber.readDouble(text)==0.0)Blocks() else B.horizontalRule
    } }
    private fun parbox(): LP<Blocks> = kernel.skipopts().then(kernel.braced()).then(state()).flatMap { old ->
        update { it.copy(inTableCell=false) }.then(groupedBlock()).flatMap { bs -> update { it.copy(inTableCell=old.user.inTableCell) }.map { bs } }
    }.attempt()
    private fun closing(): LP<Blocks> = tok().flatMap { contents -> state().map { p ->
        val authors=(p.user.meta["author"] as? MetaValue.MetaList)?.values
        val sigs=authors?.flatMapIndexed { i,v ->
            val bs=(v as? MetaValue.MetaBlocks)?.content
            val ils=when(val b=bs?.singleOrNull()) { is Block.Plain->b.content;is Block.Para->b.content;else->emptyList() }
            (if(i==0)emptyList() else listOf(Inline.LineBreak))+ils
        }
        B.para(B.trimInlines(contents))+(if(sigs==null)Blocks() else B.para(B.trimInlines(Inlines(sigs))))
    } }
    private fun blockquote(cvariant: Boolean,language: String?): LP<Blocks> {
        val citation=if(cvariant)citations.cites(inline,CitationMode.NormalCitation,false).map { B.para(B.cite(it,Inlines())) } else bracketedInline().map(B::para).option(Blocks())
        return citation.flatMap { cite -> bracketedInline().option(Inlines()).then(groupedBlock()).before(kernel.symbolIn(".:;?!".map { it.code }.toSet()).optional()).map { bs ->
            val lang=language?.let(LaTeXLanguages::babelLangToBCP47)
            B.blockQuote(if(lang==null)bs+cite else B.divWith(Attr(keyValues=listOf("lang" to lang.renderLang())),bs+cite))
        } }
    }
    private fun coloredBlock(style: String): LP<Blocks> = kernel.skipopts().then(kernel.braced()).flatMap { color ->
        groupedInline().notFollowedBy { AstShow.show(it.values) }.then(groupedBlock()).map { B.divWith(Attr(keyValues=listOf("style" to "$style: ${untok(color)}")),it) }
    }.attempt()
    private fun hypertargetBlock(): LP<Blocks> = kernel.braced().flatMap { ref -> groupedBlock().map { bs ->
        val label=untok(ref);val header=bs.values.singleOrNull() as? Block.Header
        if(header?.level==1L && header.attr.identifier==label)bs else B.divWith(Attr(label),bs)
    } }.attempt()
    private fun declareExclusions(kind: String): LP<Blocks> = kernel.braced().flatMap { ts ->
        val words=untok(ts).split(',').map { it.trim { c -> ParserCharacters.isSpace(c.code) } }.toSet()
        update { s -> s.copy(caseExclusions=s.caseExclusions+(kind to (words+(s.caseExclusions[kind] ?: emptySet())))) }.map { Blocks() }
    }
    val blockCommands: Map<String,LP<Blocks>> by lazy {
        val result=sortedMapOf<String,LP<Blocks>>(UnicodeTextOrder)
        fun put(name: String,p: LP<Blocks>) { result[name]=p }
        fun empty(name: String,p: LP<*>) { put(name,p.map { Blocks() }) }
        fun meta(name: String)=kernel.skipopts().then(tok()).flatMap { LaTeXParsing.addMeta(name,it.toMetaValue()) }
        empty("par",kernel.skipopts());put("parbox",defer(::parbox))
        empty("title",kernel.skipopts().then(groupedInline().flatMap { LaTeXParsing.addMeta("title",it.toMetaValue()) } or groupedBlock().flatMap { LaTeXParsing.addMeta("title",it.toMetaValue()) }))
        for(name in listOf("subtitle","address","date","extratitle","frontispiece","titlehead","subject","publishers","uppertitleback","lowertitleback","dedication"))empty(name,meta(name))
        empty("author",kernel.skipopts().then(defer(::authors)));empty("signature",kernel.skipopts().then(defer(::authors)))
        put("newtheorem",math.newtheorem());put("theoremstyle",math.theoremstyle())
        for((name,level) in listOf("part" to -1L,"chapter" to 0L,"section" to 1L,"subsection" to 2L,"subsubsection" to 3L,"paragraph" to 4L,"subparagraph" to 5L)) {
            put(name,defer { section(Attr(),level) });put("$name*",defer { section(Attr(classes=listOf("unnumbered")),level) })
        }
        put("minisec",defer { section(Attr(classes=listOf("unnumbered","unlisted")),6) });put("frametitle",defer { section(Attr(),3) });put("framesubtitle",defer { section(Attr(),4) })
        put("opening",kernel.skipopts().then(tok()).map { B.para(B.trimInlines(it)) });put("closing",kernel.skipopts().then(defer(::closing)))
        for(name in listOf("plainbreak","fancybreak"))for(star in listOf("","*"))put(name+star,kernel.braced().map { B.horizontalRule })
        for(star in listOf("","*"))put("plainfancybreak$star",kernel.braced().then(kernel.braced()).then(kernel.braced()).map { B.horizontalRule })
        for(name in listOf("pfbreak","pfbreak*","hrule"))put(name,pure(B.horizontalRule))
        put("strut",pure(Blocks()));put("rule",defer(::rule))
        put("item",state().flatMap { guard(!it.user.inListItem) }.then(kernel.skipopts()).map { Blocks() })
        put("documentclass",kernel.skipopts().then(kernel.braced()).then(defer(::preamble)))
        put("centerline",kernel.skipopts().then(tok()).map { B.para(B.trimInlines(it)) })
        empty("caption",kernel.setCaption(inline))
        for(name in listOf("bibliography","addbibresource"))empty(name,kernel.skipopts().then(kernel.braced()).flatMap { ts -> LaTeXParsing.addMeta("bibliography",MetaValue.MetaList(splitBibs(untok(ts)).map(Inlines::toMetaValue))) })
        empty("endinput",position().flatMap { kernel.infile(it.nameRaw).skipMany() })
        put("lstinputlisting",defer(::inputListing));put("inputminted",defer(::inputMinted));put("graphicspath",defer(::graphicsPath))
        for(name in listOf("setdefaultlanguage","setmainlanguage"))put(name,languages.setDefaultLanguage())
        put("hypertarget",defer(::hypertargetBlock))
        for((name,style) in listOf("textcolor" to "color","colorbox" to "background-color"))put(name,defer { coloredBlock(style) })
        put("blockquote",defer { blockquote(false,null) });put("blockcquote",defer { blockquote(true,null) })
        for((name,cite) in listOf("foreignblockquote" to false,"foreignblockcquote" to true,"hyphenblockquote" to false,"hyphenblockcquote" to true))put(name,kernel.braced().flatMap { blockquote(cite,untok(it)) })
        for(name in listOf("include","input","expandableinput"))put(name,rawBlockOr(name,defer { include(if(name=="include")name else "input") }.map { Blocks() }))
        put("subfile",rawBlockOr("subfile",defer(::subfile)));put("usepackage",rawBlockOr("usepackage",defer(::usepackage).map { Blocks() }))
        empty("PackageError",kernel.braced().then(kernel.braced()).then(kernel.braced()))
        for(name in listOf("ExplSyntaxOn","ExplSyntaxOff","raggedright"))put(name,pure(Blocks()))
        empty("ShowCommand",LaTeXParsing.withVerbatimMode(kernel.spaces().then(kernel.anyControlSeq())))
        for(name in listOf("ShowEnvironment","AddToNoCaseChangeList"))empty(name,kernel.braced())
        for(name in listOf("DeclareKeys","DeclareUnknownKeyHandler","SetKeys"))empty(name,kernel.skipopts().then(kernel.braced()))
        empty("ProcessKeyOptions",kernel.skipopts())
        for((name,kind) in listOf("DeclareUppercaseExclusions" to "upper","DeclareLowercaseExclusions" to "lower","DeclareTitlecaseExclusions" to "title"))put(name,defer { declareExclusions(kind) })
        empty("DeclareCaseChangeEquivalent",LaTeXParsing.withVerbatimMode(kernel.spaces().then(kernel.anyControlSeq())).then(kernel.braced()))
        for(name in listOf("DeclareUppercaseMapping","DeclareLowercaseMapping","DeclareTitlecaseMapping"))empty(name,kernel.skipopts().then(kernel.braced()).then(kernel.braced()))
        put("epigraph",groupedBlock().flatMap { one -> groupedBlock().map { B.divWith(Attr(classes=listOf("epigraph")),one+it) } })
        empty("newtoggle",toggle(null));empty("toggletrue",toggle(true));empty("togglefalse",toggle(false));put("iftoggle",defer(::ifToggle).then(block).attempt())
        java.util.Collections.unmodifiableMap(result)
    }
    private fun environmentParser(): LP<Blocks> = kernel.controlSeq("begin").then(kernel.braced()).flatMap { ts ->
        val name=untok(ts)
        LaTeXParsing.askEnv().flatMap { env -> (env.environments[name] ?: zero()) or langEnvironment(name) or math.theoremEnvironment(blocks,inlines,opt(),name) or
            if(name in math.inlineEnvironments)zero() else rawEnv(name).attempt() or rawVerbEnv(name)
        }
    }.attempt()
    val environments: Map<String,LP<Blocks>> by lazy {
        val result=sortedMapOf<String,LP<Blocks>>(UnicodeTextOrder)
        fun put(name: String,p: LP<Blocks>) { result[name]=p }
        put("document",kernel.env("document",blocks).before(kernel.anyTok().skipMany()))
        put("abstract",kernel.env("abstract",blocks).flatMap { LaTeXParsing.addMeta("abstract",it.toMetaValue()) }.map { Blocks() })
        put("sloppypar",kernel.env("sloppypar",blocks));put("letter",kernel.env("letter",defer(::letterContents)))
        put("minipage",kernel.env("minipage",kernel.skipopts().then(kernel.spaces()).then(kernel.braced().optional()).then(kernel.spaces()).then(blocks)).map { B.divWith(Attr(classes=listOf("minipage")),it) })
        for(name in listOf("figure","figure*"))put(name,kernel.env(name,defer(::figure)))
        put("subfigure",kernel.env("subfigure",kernel.skipopts().then(tok()).then(defer(::figure))))
        put("center",kernel.env("center",blocks).map { B.divWith(Attr(classes=listOf("center")),it) })
        for(name in listOf("quote","quotation","verse"))put(name,kernel.env(name,blocks).map(B::blockQuote))
        put("itemize",listenv("itemize",defer(::item).many()).map(B::bulletList))
        put("description",listenv("description",defer(::descItem).many()).map(B::definitionList))
        put("enumerate",defer(::orderedList))
        put("alltt",kernel.env("alltt",blocks).map(::alltt))
        put("code",enabled("literate_haskell").then(kernel.verbEnv("code")).map { B.codeBlockWith(Attr(classes=listOf("haskell","literate")),it) })
        put("comment",kernel.verbEnv("comment").map { Blocks() });put("verbatim",kernel.verbEnv("verbatim").map(B::codeBlock))
        for(name in listOf("Verbatim","BVerbatim"))put(name,defer { fancyverbEnv(name) })
        put("lstlisting",kernel.keyvals().option(emptyList()).flatMap { opts -> kernel.verbEnv("lstlisting").map { B.codeBlockWith(parseListingsOptions(opts),it) } })
        put("minted",defer(::mintedAttr).flatMap { attr -> kernel.verbEnv("minted").map { B.codeBlockWith(attr,it) } })
        put("obeylines",defer(::obeylines))
        for(name in listOf("tikzpicture","tikzcd","lilypond","ly"))put(name,defer { rawVerbEnv(name) })
        put("proof",math.proof(blocks,opt()));put("CSLReferences",kernel.braced().then(kernel.braced()).then(kernel.env("CSLReferences",blocks)))
        put("otherlanguage",kernel.env("otherlanguage",kernel.skipopts().then(kernel.braced()).flatMap { ts ->
            val lang=LaTeXLanguages.babelLangToBCP47(untok(ts));blocks.map { if(lang==null)it else B.divWith(Attr(keyValues=listOf("lang" to lang.renderLang())),it) }
        }))
        for((name,p) in tables.environments(block,inline))result[name]=p
        java.util.Collections.unmodifiableMap(result)
    }
    private fun langEnvironment(name: String): LP<Blocks> {
        val lang=LaTeXLanguages.babelLangToBCP47(name) ?: return zero()
        return kernel.env(name,blocks.map { B.divWith(Attr(keyValues=listOf("lang" to lang.renderLang())),it) })
    }
    private fun filecontents(): LP<Blocks> = kernel.controlSeq("begin").then(kernel.braced()).flatMap { ts ->
        val name=untok(ts);guard(name in listOf("filecontents","filecontents*")).then(kernel.skipopts()).then(kernel.braced()).flatMap { file ->
            kernel.verbEnv(name).flatMap { text -> update { it.copy(fileContents=it.fileContents+(untok(file) to text)) }.map { Blocks() } }
        }
    }.attempt()
    private fun rawEnv(name: String): LP<Blocks> = state().flatMap { s -> kernel.rawopt().many().flatMap { opts -> position().flatMap { first ->
        val begin="\\begin{$name}"+opts.joinToString("")
        if(Extension.read("raw_tex") in s.user.options.extensions)LaTeXParsing.withRaw(kernel.env(name,blocks)).map { B.rawBlock("latex",begin+untok(it.second)) }
        else kernel.env(name,blocks).flatMap { bs -> report(LogMessage.SkippedContent(begin,first)).then(position()).flatMap { last ->
            report(LogMessage.SkippedContent("\\end{$name}",last)).map { B.divWith(Attr(classes=listOf(name)),bs) }
        } }
    } } }
    private fun rawVerbEnv(name: String): LP<Blocks> = position().flatMap { pos -> LaTeXParsing.withRaw(kernel.verbEnv(name)).flatMap { pair ->
        val raw="\\begin{$name}"+untok(pair.second)
        enabled("raw_tex").map { B.rawBlock("latex",raw) } or report(LogMessage.SkippedContent(raw,pos)).map { Blocks() }
    } }
    private fun fancyverbEnv(name: String): LP<Blocks> = kernel.keyvals().option(emptyList()).flatMap { options -> kernel.verbEnv(name).map {
        B.codeBlockWith(Attr(classes=if(options.firstOrNull { p -> p.first=="numbers" }?.second=="left")listOf("numberLines") else emptyList(),keyValues=listingKeys(options)),it)
    } }
    private fun mintedAttr(): LP<Attr> = kernel.keyvals().option(emptyList()).flatMap { options -> kernel.braced().map { ts ->
        val lang=untok(ts);Attr(classes=listOf(lang).filter(String::isNotEmpty)+(if(options.firstOrNull { it.first=="linenos" }?.second=="true")listOf("numberLines") else emptyList()),keyValues=listingKeys(options))
    } }
    private fun inputMinted(): LP<Blocks> = position().flatMap { pos -> mintedAttr().flatMap { attr -> kernel.braced().flatMap { ts ->
        val file=untok(ts).filter { it!='"' };readFileFromTexinputs(file).flatMap { code ->
            (if(code==null)report(LogMessage.CouldNotLoadIncludeFile(file,pos)) else pure(Unit)).map { B.codeBlockWith(attr,code ?: "") }
        }
    } } }
    private fun parseListingsOptions(options: List<Pair<String,String>>): Attr = Attr(options.firstOrNull { it.first=="label" }?.second ?: "",
        (if(options.firstOrNull { it.first=="numbers" }?.second=="left")listOf("numberLines") else emptyList())+listOfNotNull(LaTeXInlines.listingsLanguage(options)),listingKeys(options))
    private fun listingKeys(options: List<Pair<String,String>>) = options.map { (k,v) -> (if(k=="firstnumber")"startFrom" else k) to v }
    private fun inputListing(): LP<Blocks> = position().flatMap { pos -> kernel.keyvals().option(emptyList()).flatMap { options -> kernel.braced().flatMap { ts ->
        val file=untok(ts).filter { it!='"' }
        readFileFromTexinputs(file).flatMap { raw -> (if(raw==null)report(LogMessage.CouldNotLoadIncludeFile(file,pos)) else pure(Unit)).map {
            val lines=raw?.let(::haskellLines) ?: emptyList();val attr=parseListingsOptions(options)
            val classes=(if(LaTeXInlines.listingsLanguage(options)==null)Highlighting.languagesByExtension(Syntaxes.defaultSyntaxMap,extension(file)).take(1) else emptyList())+attr.classes
            val first=options.firstOrNull { it.first=="firstline" }?.second?.let { HaskellNumber.readInteger(it)?.toLong() } ?: 1L
            val last=options.firstOrNull { it.first=="lastline" }?.second?.let { HaskellNumber.readInteger(it)?.toLong() } ?: lines.size.toLong()
            B.codeBlockWith(attr.copy(classes=classes),lines.drop(maxOf(0,first-1).coerceAtMost(lines.size.toLong()).toInt()).take(maxOf(0,1+last-first).coerceAtMost(lines.size.toLong()).toInt()).joinToString("\n"))
        } }
    } } }
    private fun obeylines(): LP<Blocks> = kernel.env("obeylines",inlines).map { value ->
        val visitor=object : PandocVisitor() { override fun transformInline(value: Inline): Inline = if(value===Inline.SoftBreak)Inline.LineBreak else value }
        B.para(Inlines(visitor.walkInlines(value.values).dropWhile { it===Inline.LineBreak }.dropLastWhile { it===Inline.LineBreak }))
    }
    private fun alltt(bs: Blocks): Blocks = Blocks(object : PandocVisitor() {
        override fun transformInline(value: Inline): Inline = when(value) { is Inline.Str->Inline.Code(Attr(),value.text);Inline.Space->Inline.RawInline("latex","\\ ");Inline.SoftBreak->Inline.LineBreak;else->value }
    }.walkBlocks(bs.values))
    private fun letterContents(): LP<Blocks> = blocks.flatMap { bs -> state().map { p ->
        val first=(p.user.meta["address"] as? MetaValue.MetaBlocks)?.content?.singleOrNull() as? Block.Plain
        (if(first==null)Blocks() else B.para(B.trimInlines(Inlines(first.content))))+bs
    } }
    private fun figure(): LP<Blocks> = kernel.sp().then(kernel.bracketedToks().map(::untok).option("")).flatMap { hint -> kernel.sp().then(LaTeXParsing.resetCaption()).then(
        kernel.label().map { Blocks() }.attempt().or(block).many()
    ).flatMap { inner ->
        val content=Blocks(object : PandocVisitor() {
            override fun transformBlock(value: Block): Block {
                val image=(value as? Block.Para)?.content?.singleOrNull() as? Inline.Image
                return if(image!=null && image.content==listOf(Inline.Str("image")))Block.Plain(listOf(image.copy(content=emptyList()))) else value
            }
        }.walkBlocks(blocks(inner).values))
        state().flatMap { p -> val s=p.user;val label=s.lastLabel
            (if(label==null)pure(Unit) else LaTeXParsing.getNextNumber { it.lastFigureNum }.flatMap { number ->
                update { s.copy(lastFigureNum=number,labels=s.labels+(label to listOf(Inline.Str(number.render())))) }
            }).map { B.figureWith(Attr(label ?: "",keyValues=if(hint.isEmpty())emptyList() else listOf("latex-placement" to hint)),s.caption ?: B.emptyCaption,content) }
        }
    } }.attempt()
    private fun graphicsPath(): LP<Blocks> = kernel.bgroup().then(kernel.spaces()).then(till(kernel.braced().before(kernel.spaces()),kernel.egroup())).flatMap { paths ->
        effect { runtime.setResourcePath(runtime.getResourcePath()+paths.map { CharacterStringChars.fromString(untok(it)) });Blocks() }
    }
    private fun splitBibs(text: String): List<Inlines> = splitTextByComma(text).map { B.str(replaceExtension(SharedText.trim(it),"bib")) }
    private fun splitTextByComma(text: String): List<String> {
        val out=ArrayList<String>();var at=0
        while(at<text.length) { val start=at;while(at<text.length && text[at]!=',')at++;out.add(text.substring(start,at));while(at<text.length && text[at]==',')at++ }
        return out
    }
    private fun item(): LP<Blocks> = blocks.then(kernel.controlSeq("item")).then(kernel.skipopts()).then(blocks)
    private fun descItem(): LP<Pair<Inlines,List<Blocks>>> = kernel.spaces1().optional().then(kernel.controlSeq("item")).then(kernel.sp()).then(opt()).flatMap { title -> blocks.map { title to listOf(it) } }
    private fun <A> listenv(name: String,p: LP<A>): LP<A> = state().flatMap { old ->
        update { it.copy(inListItem=true) }.then(kernel.env(name,p)).flatMap { value -> update { it.copy(inListItem=old.user.inListItem) }.map { value } }
    }.attempt()
    private fun orderedList(): LP<Blocks> {
        val marker=kernel.symbol('[').then(till(kernel.anyTok(),kernel.symbol(']'))).map { LaTeXTokens.untokenize(it) }.flatMap { text ->
            val result=ListParsing.anyOrderedListMarker(ExampleNumbers).run(ParseState(ParseInput(text),SourcePos("option"),ExampleNumbers()))
            if(result.success)pure(checkNotNull(result.value)) else position().flatMap { report(LogMessage.SkippedContent("[$text]",it)).map { ListAttributes() } }
        }
        val setLength=kernel.controlSeq("setlength").then(kernel.grouped(kernel.controlSeq("itemindent").map { Unit },Unit) { _,_->Unit }).then(kernel.braced()).attempt()
        val setCounter=position().flatMap { pos -> kernel.controlSeq("setcounter").then(kernel.braced()).flatMap { ctr ->
            val counter=untok(ctr);guard(counter.startsWith("enum") && counter.drop(4).all { it in "iv" }).then(kernel.sp()).then(kernel.braced()).flatMap { number ->
                val raw=untok(number);val n=HaskellNumber.readInteger(raw)?.toLong()
                if(n==null)report(LogMessage.SkippedContent("\\setcounter{$counter}{$raw}",pos)).map { 1L } else pure(n+1)
            }
        } }.attempt()
        return kernel.spaces().then(marker.option(ListAttributes())).flatMap { attrs -> kernel.spaces().then(setLength.optional()).then(kernel.spaces()).then(setCounter.option(1)).flatMap { start ->
            listenv("enumerate",defer(::item).many()).map { B.orderedListWith(attrs.copy(start=start),it) }
        } }.attempt()
    }
    private fun readFileFromTexinputs(file: String): LP<String?> = state().flatMap { s ->
        if(file in s.user.fileContents)pure(s.user.fileContents.getValue(file)) else effect {
            val dirs=(runtime.lookupEnv("TEXINPUTS") ?: "").split(':').map { it.ifEmpty { "." } }
            var result: String?=null
            for(dir in dirs) {
                val path=PandocFilePaths.combine(CharacterStringChars.fromString(dir),CharacterStringChars.fromString(file),runtime.filePaths)
                try {
                    result=PandocText.toTextM(path,runtime.readFileStrict(path))
                    break
                } catch(_: PandocError) { /* original catchError retries the next directory */ }
            }
            result
        }
    }
    private fun ensureExtension(file: String,allowed: (String)->Boolean,default: String): String = if(allowed(extension(file)))file else addExtension(file,default)
    private fun includedTokens(file: String): LP<TokStream> = state().flatMap { p ->
        if(file in p.user.containers)effect { throw PandocError.PandocParseError("Include file loop at "+CharacterStringChars.build(p.position.showCharacters())) } else
            update { LaTeXStateAccess.addIncludeFile(file,it) }.then(readFileFromTexinputs(file)).flatMap { contents ->
                (if(contents==null)report(LogMessage.CouldNotLoadIncludeFile(file,p.position)) else pure(Unit)).then(update { LaTeXStateAccess.dropLatestIncludeFile(it) })
                    .map { TokStream(false,LaTeXTokens.tokenize(SourcePos(file),contents ?: "")) }
            }
    }
    private fun insertIncluded(file: String): LP<Unit> = includedTokens(file).flatMap { added -> input().flatMap { setInput(added+it) } }
    private fun filenames(): LP<List<String>> = opt().many().then(kernel.braced()).map { ts -> untok(filterTokens(ts) { it.type!=TokType.Comment }).split(',').map { LaTeXReaderText.removeDoubleQuotes(it.trim { c -> ParserCharacters.isSpace(c.code) }) } }
    private fun include(name: String): LP<Unit> = filenames().flatMap { files ->
        files.fold(pure(Unit)) { p,file -> p.then(defer { insertIncluded(ensureExtension(file,{ if(name=="include")it==".tex" else name=="input" && it.isNotEmpty() },".tex")) }) }
    }
    private fun subfile(): LP<Blocks> = opt().many().then(bracedFilename()).flatMap { file -> input().flatMap { old ->
        setInput(TokStream(false,tokenList(emptyList()))).then(insertIncluded(ensureExtension(file,String::isNotEmpty,".tex"))).then(blocks).before(eof()).flatMap { result -> setInput(old).map { result } }
    } }
    private fun usepackage(): LP<Unit> = filenames().flatMap { files -> files.fold(pure(Unit)) { p,file -> p.then(defer {
        includedTokens(ensureExtension(file,{ it==".sty" },".sty")).flatMap { ts -> LaTeXParsing.parseFromToks(blocks.then(eof() or position().flatMap { report(LogMessage.CouldNotParseIncludeFile(file,it)) }),ts.tokens) }
    }) } }
    private fun isBlockCommand(name: String) = name in blockCommands || name in treatAsBlock
    private fun isInlineCommand(name: String) = name in inlineCommands || name in treatAsInline
    private val treatAsBlock=setOf("special","pdfannot","pdfstringdef","bibliographystyle","maketitle","makeindex","makeglossary","addcontentsline","addtocontents","addtocounter","ignore","hyperdef","markboth","markright","markleft","hspace","vspace","newpage","clearpage","pagebreak","titleformat","listoffigures","listoftables","write")
    private val treatAsInline=setOf("index","hspace","vspace","noindent","newpage","clearpage","pagebreak")
    companion object {
        fun resolveRefs(labels: Map<String,List<Inline>>,value: Inline): Inline {
            if(value !is Inline.Link)return value
            val kind=value.attr.keyValues.firstOrNull { it.first=="reference-type" }?.second?.substringBefore('+')
            val label=value.attr.keyValues.firstOrNull { it.first=="reference" }?.second
            return if(kind=="ref" && label!=null && label in labels)value.copy(content=labels.getValue(label),target=Target("#$label","")) else value
        }
        fun resolveFootnoteMarks(notes: Map<Long,Blocks>,value: Inline): Inline {
            if(value !is Inline.Span || "footnote-mark" !in value.attr.classes)return value
            val raw=value.attr.keyValues.firstOrNull { it.first=="note-num" }?.second ?: return value
            val num=readNoteNum(raw) ?: return value
            return notes[num]?.let { Inline.Note(it.values) } ?: Inline.Str("")
        }
        private fun extension(path: String): String = io.heapy.kotdoc.templates.PartialPaths.System.takeExtension(path)
        private fun addExtension(path: String,ext: String): String = io.heapy.kotdoc.templates.PartialPaths.System.addExtension(path,ext)
        private fun replaceExtension(path: String,ext: String): String = addExtension(path.dropLast(extension(path).length),ext)
        fun fromParsecError(sources: Sources,error: ParseError): PandocError.PandocParseError {
            val pos=error.position
            val chunk=sources.chunks().filter { it.position.nameRaw==pos.nameRaw && it.position.line<=pos.line }.sortedByDescending { it.position.line }.firstOrNull()
            val line=chunk?.let { c -> val index=pos.line-c.position.line;haskellLines(c.text).plus("").getOrNull(index.toInt()).takeIf { index>=0 && index<=Int.MAX_VALUE } }
            val context=if(line==null)"" else "\n$line\n"+" ".repeat(maxOf(0,pos.column-1).toInt())+"^"
            return PandocError.PandocParseError("Error at "+error.showText()+context)
        }
        private fun haskellLines(text: String): List<String> = if(text.isEmpty())emptyList() else text.split('\n').let { if(text.endsWith('\n'))it.dropLast(1) else it }
        // reads requires an empty remainder here; safeRead would also accept
        // trailing whitespace and therefore changes optional footnote numbers.
        private fun readNoteNum(text: String): Long? = if(text.isNotEmpty() && ParserCharacters.isSpace(text.codePointBefore(text.length)))null else HaskellNumber.readInteger(text)?.toLong()
    }
}
