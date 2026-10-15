// Text.Pandoc.Readers.LaTeX.Macro, Pandoc 3.12.1.
// Copyright (C) John MacFarlane; GPL-2.0-or-later, licenses/pandoc-GPL-2.0.md.
package io.heapy.kotdoc.latex

import io.heapy.kotdoc.formats.HaskellNumber
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.options.Extension
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.tex.*
import io.heapy.kotdoc.tex.SourcePos

/** Definitions use the same token parser and PandocMonad effects as expansion. */
class LaTeXMacros(val kernel: LaTeXKernel = LaTeXKernel()) {
    private fun <A> pure(x: A): LP<A> = ReaderTokenParsec.pure(x)
    private fun <A> zero(): LP<A> = ReaderTokenParsec.zero()
    private fun <A> fail(s: String): LP<A> = ReaderTokenParsec.fail(s)
    private fun <A> defer(p: ()->LP<A>): LP<A> = ReaderTokenParsec.defer(p)
    private fun <A> effect(f: ()->A): LP<A> = ReaderTokenParsec.lift(TokenParsec.lift(f))
    private fun state() = LaTeXParsing.state()
    private fun update(f: (LaTeXState)->LaTeXState) = LaTeXParsing.update(f)
    private fun <A> verbatim(p: LP<A>) = LaTeXParsing.withVerbatimMode(p)
    private fun disabled(): LP<Unit> = ReaderTokenParsec.lift(TokenParserCapabilities.guardDisabled(
        TokenReaderOptionsAccess<Tok,LaTeXState> { it.options },Extension.read("latex_macros")))
    private fun <A> choice(xs: List<LP<A>>) = xs.reduce { a,b -> a or b }
    private fun commands(vararg names: String) = choice(names.map(kernel::controlSeq))
    private fun append(a: TokenList,b: TokenList) = (TokStream(false,a)+TokStream(false,b)).tokens
    private fun cons(t: Tok,rest: TokenList) = Deferred.now(DeferredChain.Cons(Deferred.now(t),rest))
    private fun <A> values(xs: Deferred<DeferredChain<A>>): List<A> {
        val out=ArrayList<A>();var node=xs.value()
        while(node is DeferredChain.Cons) {out.add(node.head.value());node=node.tail.value()};return out
    }
    private fun macro(scope: MacroScope,point: ExpansionPoint,args: ArgumentList,default: TokenList?,body: TokenList) =
        Macro(Deferred.now(scope),Deferred.now(point),args,
            Deferred.now(if(default==null)TeXMaybe.Nothing else TeXMaybe.Just(default)),body)
    private fun macro(scope: MacroScope,point: ExpansionPoint,args: List<ArgSpec>,default: TokenList?,body: TokenList) =
        macro(scope,point,DeferredChain.of(args),default,body)
    private fun withScope(m: Macro,scope: MacroScope) = Macro(Deferred.now(scope),m.deferredExpansion,m.arguments,m.default,m.replacement)
    private fun report(name: String,pos: SourcePos) = effect { kernel.context.report(LogMessage.MacroAlreadyDefined(name,pos)) }
    private fun <A> sequence(xs: List<LP<A>>): LP<Unit> {
        var p=pure(Unit)
        for(x in xs)p=p.then(x.map { Unit })
        return p
    }
    fun <A> macroDef(constructor: (String)->A,empty: A): LP<A> = defer {
        kernel.peekTok().flatMap { first ->
            val name=(first.type as? TokType.CtrlSeq)?.text
            if(name==null)fail("Pattern match failure in 'do' block at src/Text/Pandoc/Readers/LaTeX/Macro.hs:26:5-26")
            else if(name !in macroDefCommands)zero()
            else LaTeXParsing.withRaw(commandDef() or environmentDef()).flatMap { (_,raw) ->
                disabled().map { constructor(LaTeXTokens.untokenize(raw)) } or pure(empty)
            }
        }
    }
    private fun commandDef(): LP<Unit> = defer {
        (newcommand() or newDocumentCommand() or newDocumentEnvironment() or commandCopy() or environmentCopy() or
            checkGlobal(letmacro() or edefmacro() or defmacro() or newif())).flatMap { pairs ->
            disabled() or sequence(pairs.map { (name,m)->insertMacro(name,m) })
        }
    }
    private fun environmentDef(): LP<Unit> = newenvironment().flatMap { result ->
        if(result==null)pure(Unit) else disabled() or insertMacro(result.first,result.second).then(insertMacro("end"+result.first,result.third))
    }
    fun insertMacro(name: String,macro: Macro): LP<Unit> = update { state ->
        state.copy(macros=if(macro.scope==MacroScope.GlobalScope)state.macros.mapAll { it+(name to macro) }
            else state.macros.replaceHead(state.macros.head+(name to macro)))
    }
    private fun lookupMacro(name: String): LP<Macro> = state().flatMap { it.user.macros.head[name]?.let(::pure) ?: fail("Macro not found") }
    private fun letmacro(): LP<List<Pair<String,Macro>>> = kernel.controlSeq("let").then(verbatim(kernel.anyControlSeq().flatMap { name ->
        kernel.symbol('=').optional().then(kernel.spaces()).then(kernel.anyControlSeq() or kernel.singleChar()).flatMap { target ->
            val fallback=listOf((name.type as TokType.CtrlSeq).text to macro(MacroScope.GroupScope,ExpansionPoint.ExpandWhenDefined,emptyList(),null,tokenList(listOf(target))))
            val targetName=(target.type as? TokType.CtrlSeq)?.text
            if(targetName==null)pure(fallback) else lookupMacro(targetName).map { listOf((name.type as TokType.CtrlSeq).text to it) } or pure(fallback)
        }
    }))
    private fun checkGlobal(p: LP<List<Pair<String,Macro>>>): LP<List<Pair<String,Macro>>> =
        kernel.controlSeq("global").then(p).map { it.map { (n,m)->n to withScope(m,MacroScope.GlobalScope) } } or p
    private fun edefmacro(): LP<List<Pair<String,Macro>>> =
        (kernel.controlSeq("edef").map { MacroScope.GroupScope } or kernel.controlSeq("xdef").map { MacroScope.GlobalScope }).flatMap { scope ->
            verbatim(kernel.anyControlSeq().flatMap { name -> kernel.bracedOrToken().map { name to it } }).flatMap { (name,body) ->
                LaTeXParsing.parseFromToks(kernel.anyTok().many(),body).map { expanded ->
                    listOf((name.type as TokType.CtrlSeq).text to macro(scope,ExpansionPoint.ExpandWhenDefined,emptyList(),null,tokenList(expanded)))
                }
            }
        }
    private fun defmacro(): LP<List<Pair<String,Macro>>> =
        (kernel.controlSeq("def").map { MacroScope.GroupScope } or kernel.controlSeq("gdef").map { MacroScope.GlobalScope }).flatMap { scope ->
            verbatim(kernel.anyControlSeq().flatMap { name ->
                (kernel.satisfyTok(LaTeXKernel::isArgTok).map<ArgSpec> { ArgSpec.ArgNum((it.type as TokType.Arg).deferredNumber) } or
                    kernel.satisfyTok { (it.type==TokType.Symbol || it.type==TokType.Word) && it.text !in listOf("{","\\","}") }
                        .many1().map { ArgSpec.Pattern(it) }).many().flatMap { args ->
                    kernel.bracedOrToken().map { body -> listOf((name.type as TokType.CtrlSeq).text to macro(scope,ExpansionPoint.ExpandWhenUsed,args,null,body)) }
                }
            })
        }
    private fun newif(): LP<List<Pair<String,Macro>>> = kernel.controlSeq("newif").then(verbatim(kernel.anyControlSeq().flatMap { nameToken ->
        val name=(nameToken.type as TokType.CtrlSeq).text;val pos=nameToken.deferredPosition
        if(!name.startsWith("if"))zero() else {
            fun t(type: TokType,s: String) = Tok(pos,Deferred.now(type),Deferred.now(s))
            fun cs(s: String) = t(TokType.CtrlSeq(s),"\\$s")
            val base=name.drop(2)
            fun m(body: List<Tok>) = macro(MacroScope.GroupScope,ExpansionPoint.ExpandWhenUsed,emptyList(),null,tokenList(body))
            pure(listOf(name to m(listOf(cs("iffalse"))),base+"true" to m(listOf(cs("def"),cs(name),t(TokType.Symbol,"{"),cs("iftrue"),t(TokType.Symbol,"}"))),
                base+"false" to m(listOf(cs("def"),cs(name),t(TokType.Symbol,"{"),cs("iffalse"),t(TokType.Symbol,"}")))))
        }
    })).attempt()
    private fun commandName(spaces: Boolean,star: Boolean=false): LP<Tok> =
        (if(star)kernel.symbol('*').optional() else pure(Unit)).then(if(spaces)kernel.spaces() else pure(Unit)).then(
            kernel.anyControlSeq() or kernel.symbol('{').then(kernel.spaces()).then(kernel.anyControlSeq()).before(kernel.spaces()).before(kernel.symbol('}')))
    private fun args(count: Long): ArgumentList {
        fun go(n: Long): ArgumentList = Deferred.later {
            if(n>count)DeferredChain.Nil else DeferredChain.Cons(Deferred.now(ArgSpec.ArgNum(n)),
                if(n==count)Deferred.now(DeferredChain.Nil) else Deferred.defer { go(n+1) })
        }
        return go(1)
    }
    private fun bracketedNum(): LP<Long> = kernel.bracketedToks().map { HaskellNumber.readInteger(LaTeXTokens.untokenize(it))?.toLong() ?: 0 }
    private fun optionalTokens(): LP<TokenList?> = kernel.bracketedToks().attempt().map<TokenList?> { it }.option(null)
    private fun newcommand(): LP<List<Pair<String,Macro>>> = commands("newcommand","renewcommand","providecommand","DeclareMathOperator","DeclareRobustCommand").flatMap { command ->
        verbatim(commandName(false,true).flatMap { token ->
            val name=(token.type as TokType.CtrlSeq).text;val type=(command.type as TokType.CtrlSeq).text
            kernel.spaces().then(bracketedNum().attempt().option(0)).flatMap { count ->
                kernel.spaces().then(optionalTokens()).flatMap { default -> kernel.spaces().then(kernel.bracedOrToken()).flatMap { original ->
                    val body=if(type=="DeclareMathOperator") {
                        val p=command.deferredPosition
                        fun t(kind: TokType,s: String)=Tok(p,Deferred.now(kind),Deferred.now(s))
                        append(tokenList(listOf(t(TokType.CtrlSeq("mathop"),"\\mathop"),t(TokType.Symbol,"{"),t(TokType.CtrlSeq("mathrm"),"\\mathrm"),t(TokType.Symbol,"{"))),
                            append(original,tokenList(listOf(t(TokType.Symbol,"}"),t(TokType.Symbol,"}")))))
                    } else original
                    val result=listOf(name to macro(MacroScope.GroupScope,ExpansionPoint.ExpandWhenUsed,args(count),default,body))
                    lookupMacro(name).flatMap { when(type) {
                        "providecommand"->pure(emptyList());"renewcommand"->pure(result)
                        else->report(token.text,command.position).map { emptyList() }
                    } } or pure(result)
                } }
            }
        })
    }
    private fun checkExisting(type: String,name: String,raw: String,pos: SourcePos,result: List<Pair<String,Macro>>,provide: Boolean): LP<List<Pair<String,Macro>>> =
        lookupMacro(name).flatMap {
            when { provide && type.startsWith("Provide")->pure(emptyList())
                type.startsWith("New")->report(raw,pos).map { emptyList() }
                else->pure(result) }
        } or pure(result)
    private fun newDocumentCommand(): LP<List<Pair<String,Macro>>> = commands(*documentCommands.toTypedArray()).flatMap { command ->
        verbatim(commandName(true).flatMap { name -> kernel.spaces().then(xparseArgSpecs(false)).flatMap { specs ->
            kernel.spaces().then(kernel.bracedOrToken()).flatMap { body ->
                val text=(name.type as TokType.CtrlSeq).text
                checkExisting((command.type as TokType.CtrlSeq).text,text,name.text,command.position,
                    listOf(text to macro(MacroScope.GroupScope,ExpansionPoint.ExpandWhenUsed,specs.arguments,null,body)),true)
            }
        } })
    }.attempt()
    private fun newDocumentEnvironment(): LP<List<Pair<String,Macro>>> = commands(*documentEnvironments.toTypedArray()).flatMap { command ->
        verbatim(kernel.spaces().then(kernel.braced()).flatMap { nameTokens ->
            val name=LaTeXTokens.untokenize(nameTokens).trim { ParserCharacters.isSpace(it.code) }
            kernel.spaces().then(xparseArgSpecs(true)).flatMap { specs ->
                kernel.spaces().then(kernel.bracedOrToken()).flatMap { start -> kernel.spaces().then(kernel.bracedOrToken()).flatMap { end ->
                    val pos=command.deferredPosition
                    fun t(kind: TokType,s: String)=Tok(pos,Deferred.now(kind),Deferred.now(s))
                    val bg=t(TokType.CtrlSeq("bgroup"),"\\bgroup ");val eg=t(TokType.CtrlSeq("egroup"),"\\egroup ")
                    val helperName="pandocxparseenvend"+letterize(name)
                    val helper=t(TokType.CtrlSeq(helperName),"\\$helperName ")
                    val def=append(tokenList(listOf(t(TokType.CtrlSeq("def"),"\\def"),helper,t(TokType.Symbol,"{"))),append(end,tokenList(listOf(t(TokType.Symbol,"}")))))
                    val result=if(specs.usesBody) {
                        val pattern=ArgSpec.Pattern(LaTeXTokens.tokenize(pos,Deferred.now("\\end{$name}")))
                        val args=appendArguments(specs.arguments,DeferredChain.of(listOf(pattern)))
                        listOf(name to macro(MacroScope.GroupScope,ExpansionPoint.ExpandWhenUsed,args,null,
                            cons(bg,append(def,append(start,tokenList(listOf(helper,eg)))))))
                    } else listOf(name to macro(MacroScope.GroupScope,ExpansionPoint.ExpandWhenUsed,specs.arguments,null,cons(bg,append(def,start))),
                        "end"+name to macro(MacroScope.GroupScope,ExpansionPoint.ExpandWhenUsed,emptyList(),null,tokenList(listOf(helper,eg))))
                    checkExisting((command.type as TokType.CtrlSeq).text,name,name,command.position,result,true)
                } }
            }
        })
    }.attempt()
    private fun appendArguments(a: ArgumentList,b: ArgumentList): ArgumentList = a.flatMapValue { chain -> when(chain) {
        DeferredChain.Nil->b;is DeferredChain.Cons->Deferred.now(DeferredChain.Cons(chain.head,Deferred.defer { appendArguments(chain.tail,b) }))
    } }
    private fun commandCopy(): LP<List<Pair<String,Macro>>> = commands("NewCommandCopy","RenewCommandCopy","DeclareCommandCopy").flatMap { command ->
        verbatim(commandName(true).flatMap { name -> kernel.spaces().then(kernel.anyControlSeq()).flatMap { target ->
            val text=(name.type as TokType.CtrlSeq).text;val targetName=(target.type as TokType.CtrlSeq).text
            (lookupMacro(targetName).map { listOf(text to it) } or pure(listOf(text to macro(MacroScope.GroupScope,ExpansionPoint.ExpandWhenDefined,emptyList(),null,tokenList(listOf(target))))))
                .flatMap { result -> checkExisting((command.type as TokType.CtrlSeq).text,text,name.text,command.position,result,false) }
        } })
    }.attempt()
    private fun environmentCopy(): LP<List<Pair<String,Macro>>> = commands("NewEnvironmentCopy","RenewEnvironmentCopy","DeclareEnvironmentCopy").flatMap { command ->
        verbatim(kernel.spaces().then(kernel.braced()).flatMap { ns -> kernel.spaces().then(kernel.braced()).flatMap { ts ->
            val name=LaTeXTokens.untokenize(ns);val target=LaTeXTokens.untokenize(ts)
            fun copy(to: String,from: String,fallback: String): LP<Pair<String,Macro>> = lookupMacro(from).map { to to it } or
                pure(to to macro(MacroScope.GroupScope,ExpansionPoint.ExpandWhenUsed,emptyList(),null,LaTeXTokens.tokenize(command.deferredPosition,Deferred.now(fallback))))
            copy(name,target,"\\begin{$target}").flatMap { start -> copy("end"+name,"end"+target,"\\end{$target}").flatMap { end ->
                checkExisting((command.type as TokType.CtrlSeq).text,name,name,command.position,listOf(start,end),false)
            } }
        } })
    }.attempt()
    class Specs(val arguments: ArgumentList,val usesBody: Boolean)
    fun xparseArgSpecs(allowBody: Boolean): LP<Specs> {
        fun go(n: Long): LP<Specs> = kernel.spaces().then(kernel.symbol('}').map { Specs(DeferredChain.of(emptyList()),false) } or
            xparseArgSpec(allowBody,n).flatMap { (spec,body) ->
                if(body)kernel.spaces().before(kernel.symbol('}')).map { Specs(DeferredChain.of(listOf(spec)),true) }
                else defer { go(n+numslots(spec)) }.map { Specs(Deferred.now(DeferredChain.Cons(Deferred.now(spec),it.arguments)),it.usesBody) }
            })
        return defer { kernel.symbol('{').then(go(1)) }
    }
    private fun numslots(spec: ArgSpec): Long {
        var inner=spec
        while(inner is ArgSpec.ProcessedArg)inner=inner.deferredArgument.value()
        return if(inner is ArgSpec.EmbellishArg)values(inner.embellishments).size.toLong() else 1
    }
    private fun xparseArgSpec(allowBody: Boolean,n: Long): LP<Pair<ArgSpec,Boolean>> {
        fun go(noBang: Boolean): LP<Pair<ArgSpec,Boolean>> = defer { kernel.singleChar().flatMap { token ->
            fun plain(spec: ArgSpec)=pure(spec to false)
            fun t(text: String)=Tok(token.deferredPosition,Deferred.now(TokType.Symbol),Deferred.now(text))
            fun specTok()=kernel.spaces().then(kernel.singleChar())
            fun delimiters(default: Boolean): LP<Pair<ArgSpec,Boolean>> = specTok().flatMap { open -> specTok().flatMap { close ->
                (if(default)kernel.spaces().then(kernel.braced()).map<TokenList?> { it } else pure(null)).flatMap { value ->
                    plain(ArgSpec.DelimArg(Deferred.now(noBang),Deferred.now(open),Deferred.now(close),Deferred.now(if(value==null)TeXMaybe.Nothing else TeXMaybe.Just(value))))
                }
            } }
            when(token.text) {
                "+"->kernel.spaces().then(defer { go(noBang) })
                "!"->kernel.spaces().then(defer { go(false) })
                "="->kernel.spaces().then(kernel.braced()).then(kernel.spaces()).then(defer { go(noBang) })
                ">"->kernel.spaces().then(kernel.braced()).flatMap { proc -> kernel.spaces().then(defer { go(noBang) }).map { (spec,body) ->
                    if(body)spec to body else {
                        val processed=if(spec is ArgSpec.ProcessedArg)ArgSpec.ProcessedArg(Deferred.now(DeferredChain.Cons(Deferred.now(proc),spec.processors)),spec.deferredArgument)
                            else ArgSpec.ProcessedArg(DeferredChain.of(listOf(proc)),Deferred.now(spec))
                        processed to false
                    }
                } }
                "m"->plain(ArgSpec.ArgNum(n))
                "o"->plain(ArgSpec.DelimArg(noBang,t("["),t("]"),null))
                "O"->kernel.spaces().then(kernel.braced()).flatMap { value -> plain(ArgSpec.DelimArg(Deferred.now(noBang),Deferred.now(t("[")),Deferred.now(t("]")),Deferred.now(TeXMaybe.Just(value)))) }
                "s"->plain(ArgSpec.BoolArg(noBang,t("*")))
                "t"->specTok().flatMap { plain(ArgSpec.BoolArg(noBang,it)) }
                "d","r"->delimiters(false)
                "D","R"->delimiters(true)
                "v"->plain(ArgSpec.VerbArg)
                "e","E"->kernel.spaces().then(kernel.braced()).flatMap { embs ->
                    val ds: LP<List<TokenList>> = if(token.text=="E")kernel.spaces().then(kernel.symbol('{')).then(kernel.spaces().then(kernel.braced()).attempt().many())
                        .before(kernel.spaces()).before(kernel.symbol('}')) else pure(emptyList())
                    ds.flatMap { defaults ->
                        val embellishments=values(embs).filterNot { LaTeXKernel.tokTypeIn(listOf(TokType.Spaces,TokType.Newline,TokType.Comment),it) }
                            .mapIndexed { i,t -> Embellishment(Deferred.now(t),Deferred.now(defaults.getOrNull(i)?.let { TeXMaybe.Just(it) } ?: TeXMaybe.Nothing)) }
                        plain(ArgSpec.EmbellishArg(DeferredChain.of(embellishments)))
                    }
                }
                "b"->if(allowBody)pure(ArgSpec.BodyArg(noBang,n) to true) else fail("unsupported xparse argument specifier")
                "c"->if(allowBody)pure(ArgSpec.VerbBodyArg(noBang,n) to true) else fail("unsupported xparse argument specifier")
                else->fail("unsupported xparse argument specifier")
            }
        } }
        return go(true)
    }
    private fun newenvironment(): LP<Triple<String,Macro,Macro>?> = state().flatMap { initial ->
        commands("newenvironment","renewenvironment","provideenvironment").flatMap { command ->
            verbatim(kernel.symbol('*').optional().then(kernel.spaces()).then(kernel.braced()).flatMap { ts ->
                val name=LaTeXTokens.untokenize(ts)
                kernel.spaces().then(bracketedNum().attempt().option(0)).flatMap { count -> kernel.spaces().then(optionalTokens()).flatMap { default ->
                    kernel.spaces().then(kernel.bracedOrToken()).flatMap { start -> kernel.spaces().then(kernel.bracedOrToken()).flatMap { end ->
                        val bg=Tok(initial.position,TokType.CtrlSeq("bgroup"),"\\bgroup ");val eg=Tok(initial.position,TokType.CtrlSeq("egroup"),"\\egroup ")
                        val result=Triple(name,macro(MacroScope.GroupScope,ExpansionPoint.ExpandWhenUsed,args(count),default,cons(bg,start)),
                            macro(MacroScope.GroupScope,ExpansionPoint.ExpandWhenUsed,emptyList(),null,append(end,tokenList(listOf(eg)))))
                        lookupMacro(name).flatMap<Triple<String,Macro,Macro>?> { when((command.type as TokType.CtrlSeq).text) {
                            "provideenvironment"->pure(null);"renewenvironment"->pure(result)
                            else->report(name,initial.position).map { null }
                        } } or pure(result)
                    } }
                } }
            })
        }
    }
    companion object {
        private val documentCommands=listOf("NewDocumentCommand","RenewDocumentCommand","ProvideDocumentCommand","DeclareDocumentCommand",
            "NewExpandableDocumentCommand","RenewExpandableDocumentCommand","ProvideExpandableDocumentCommand","DeclareExpandableDocumentCommand")
        private val documentEnvironments=listOf("NewDocumentEnvironment","RenewDocumentEnvironment","ProvideDocumentEnvironment","DeclareDocumentEnvironment")
        val macroDefCommands=setOf("global","let","edef","xdef","def","gdef","newif","newcommand","renewcommand","providecommand","DeclareMathOperator","DeclareRobustCommand",
            *documentCommands.toTypedArray(),*documentEnvironments.toTypedArray(),"NewCommandCopy","RenewCommandCopy","DeclareCommandCopy","NewEnvironmentCopy","RenewEnvironmentCopy","DeclareEnvironmentCopy",
            "newenvironment","renewenvironment","provideenvironment")
        fun letterize(text: String): String = buildString {
            TextChars.fromString(text).codePoints().forEach { point ->
                if(ParserCharacters.isAlpha(point))appendCodePoint(point)
                else {append('x');point.toString().forEach { append((it.code+49).toChar()) }}
            }
        }
    }
}
