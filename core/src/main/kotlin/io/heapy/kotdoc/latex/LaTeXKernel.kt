// Text.Pandoc.Readers.LaTeX.Parsing, Copyright (C) 2006-2024 John MacFarlane.
// GPL-2.0-or-later; retained original source and notice in upstream/pandoc and licenses/.
package io.heapy.kotdoc.latex

import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.formats.HaskellNumber
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.state.PandocState
import io.heapy.kotdoc.state.PandocStateContext
import io.heapy.kotdoc.tex.*
import io.heapy.kotdoc.tex.SourcePos

/** Token grammar and macro engine share the same consumed/empty parser machine.
 * Nested argument parsers discard local state, while PandocMonad effects survive. */
class LaTeXKernel(val context: PandocState = PandocStateContext()) {
    private fun <A> pure(value: A): LP<A> = ReaderTokenParsec.pure(value)
    private fun <A> zero(): LP<A> = ReaderTokenParsec.zero()
    private fun <A> fail(message: String): LP<A> = ReaderTokenParsec.fail(message)
    private fun <A> defer(parser: () -> LP<A>): LP<A> = ReaderTokenParsec.defer(parser)
    private fun <A> effect(action: () -> A): LP<A> = ReaderTokenParsec.lift(TokenParsec.lift(action))
    private fun state() = LaTeXParsing.state()
    private fun update(f: (LaTeXState) -> LaTeXState) = LaTeXParsing.update(f)
    private fun input(): LP<TokStream> = state().map { it.input as TokStream }
    private fun position(): LP<SourcePos> = state().map { it.position }
    private fun setInput(stream: TokStream) = LaTeXParsing.modify { it.copy(input = stream) }
    private fun <A,B> till(p: LP<A>, end: LP<B>): LP<List<A>> = ReaderTokenParsec { env ->
        TokenParsec.manyTill(p.runReader(env), end.runReader(env))
    }
    private fun <A> sequence(ps: List<LP<A>>): LP<List<A>> {
        fun go(i: Int): LP<DeferredChain<A>> = if(i==ps.size) pure(DeferredChain.Nil)
            else ps[i].flatMap { x -> defer { go(i+1) }.map { DeferredChain.Cons(Deferred.now(x),Deferred.now(it)) } }
        return defer { go(0) }.map { materialize(Deferred.now(it)) }
    }
    private fun <A> materialize(xs: Deferred<DeferredChain<A>>): List<A> {
        var node=xs.value();val out=ArrayList<A>()
        while(node is DeferredChain.Cons) {out.add(node.head.value());node=node.tail.value()}
        return out
    }
    private fun head(xs: TokenList) = xs.value() as? DeferredChain.Cons
    private fun cons(t: Tok, xs: TokenList): TokenList = Deferred.now(DeferredChain.Cons(Deferred.now(t),xs))
    private fun append(xs: TokenList, ys: TokenList) = (TokStream(false,xs)+TokStream(false,ys)).tokens
    private fun concat(xs: List<TokenList>): TokenList {
        var out=tokenList(emptyList())
        for(i in xs.indices.reversed())out=append(xs[i],out)
        return out
    }
    private fun one(p: LP<Tok>) = p.map { tokenList(listOf(it)) }
    private fun same(a: Tok,b: Tok) = a.type==b.type && a.text==b.text
    private fun sym(t: Tok,s: String) = t.type==TokType.Symbol && t.text==s
    private fun spaceLike(t: Tok) = t.type in listOf(TokType.Spaces,TokType.Newline,TokType.Comment)
    private fun named(t: Tok,s: String) = (t.type as? TokType.CtrlSeq)?.text==s
    private fun match(t: Tok) = satisfyTok { same(t,it) }
    private fun pattern(ts: TokenList) = sequence(materialize(ts).map(::match)).map { Unit }.attempt()
    private fun report(s: String,p: SourcePos) = effect { context.report(LogMessage.SkippedContent(s,p)) }

    fun satisfyTok(test: (Tok) -> Boolean): LP<Tok> = defer {
        doMacros().then(ReaderTokenParsec.lift(TokenParsec.tokenPrim<Tok,LaTeXState,Tok>(
            LaTeXTokens::untoken,
            { p,t,rest -> (rest as TokStream).unconsLazy()?.token?.value()?.position
                ?: p.copy(column=p.column+t.text.codePointCount(0,t.text.length)) },
            { if(test(it))TokenMatch.Accept(it) else TokenMatch.Reject }
        ))).flatMap { t ->
            update { if(it.rawScopes>0 && it.enableWithRaw)
                it.copy(rawTokens=cons(t,it.rawTokens),rawTokenCount=it.rawTokenCount+1) else it }.map { t }
        }
    }
    fun peekTok(): LP<Tok> = defer { doMacros().then(input()).flatMap { head(it.tokens)?.let { t -> pure(t.head.value()) } ?: zero() } }
    fun anyTok() = satisfyTok { true }
    fun anyControlSeq() = satisfyTok { it.type is TokType.CtrlSeq }
    fun anySymbol() = satisfyTok { it.type==TokType.Symbol }
    fun infile(name: CharacterString) = satisfyTok { it.position.nameRaw==name }
    fun controlSeq(name: String) = satisfyTok { named(it,name) }
    fun symbol(point: Int) = satisfyTok { it.type==TokType.Symbol && it.text.isNotEmpty() && it.text.codePointAt(0)==point }
    fun symbol(c: Char) = symbol(c.code)
    fun symbolIn(chars: Set<Int>) = satisfyTok { it.type==TokType.Symbol && it.text.isNotEmpty() && it.text.codePointAt(0) in chars }
    fun spaces() = satisfyTok(::spaceLike).skipMany()
    fun spaces1() = satisfyTok(::spaceLike).many1().map { Unit }
    fun whitespace() = satisfyTok { it.type==TokType.Spaces }.map { Unit }
    fun comment() = satisfyTok { it.type==TokType.Comment }.map { Unit }
    fun newlineTok() = satisfyTok { it.type==TokType.Newline }.map { Unit }
    fun blankline(): LP<Unit> = defer { whitespace().skipMany().then(newlineTok()).attempt() }
    fun endline(): LP<Unit> = defer { newlineTok().before(anyTok().lookAhead()).before(blankline().notFollowedBy { "()" }).attempt() }
    fun sp(): LP<Unit> = defer { (whitespace() or comment()).skipMany().optional()
        .then(endline().then((whitespace() or comment()).skipMany()).optional()) }
    fun singleChar(): LP<Tok> = defer {
        satisfyTok { t -> t.type==TokType.Word && t.text.codePointCount(0,t.text.length)==1 ||
            t.type==TokType.Symbol && t.text.codePoints().noneMatch { it in specialChars } } or
            LaTeXParsing.disablingWithRaw(satisfyTok { it.type==TokType.Word }).flatMap { t ->
                val cut=if(t.text.isEmpty())0 else Character.charCount(t.text.codePointAt(0))
                input().flatMap { stream ->
                    setInput(TokStream(stream.expanded,cons(Tok(t.position,t.type,t.text.substring(0,cut)),
                        cons(Tok(t.position.copy(column=t.position.column+1),t.type,t.text.substring(cut)),stream.tokens))))
                        .then(anyTok())
                }
            }
    }
    fun primEscape(): LP<Int> = satisfyTok { it.type==TokType.Esc1 || it.type==TokType.Esc2 }.flatMap { t ->
        fun chr(n: Long): Int {
            require(n in 0..0x10ffff) { "Prelude.chr: bad argument: "+if(n<0)"($n)" else n.toString() }
            return n.toInt()
        }
        val points=t.text.codePoints().toArray();val tail=TextChars.fromPoints(points.drop(2).toIntArray())
        if(t.type==TokType.Esc1) {
            if(tail.isEmpty())fail("Empty content of Esc1")
            else {val c=tail.codePointAt(0);pure(chr((if(c in 64..127)c-64 else c+64).toLong()))}
        } else {
            val n=HaskellNumber.readInteger("0x$tail")?.toLong()
            if(n==null)fail("Could not read: ${t.text}")
            else pure(chr(n))
        }
    }
    fun bgroup(): LP<Tok> = defer { sp().optional().then(symbol('{') or controlSeq("bgroup") or controlSeq("begingroup"))
        .flatMap { t -> update { it.copy(macros=it.macros.push()) }.map { t } }.attempt() }
    fun egroup(): LP<Tok> = defer { (symbol('}') or controlSeq("egroup") or controlSeq("endgroup"))
        .flatMap { t -> update { it.copy(macros=it.macros.pop()) }.map { t } } }
    fun <A> grouped(p: LP<A>, empty: A, combine: (A,A) -> A): LP<A> = defer {
        bgroup().then(grouped(p,empty,combine).before(egroup()).attempt() or
            till(p,egroup()).map { it.fold(empty,combine) }).attempt()
    }
    fun braced(getTok: LP<Tok> = anyTok()): LP<TokenList> {
        fun go(depth: Long): LP<TokenList> = getTok.flatMap { t -> when {
            sym(t,"}") && depth<=1 -> pure(tokenList(emptyList()))
            else -> defer { go(if(sym(t,"{"))depth+1 else if(sym(t,"}"))depth-1 else depth) }.map { cons(t,it) }
        } }
        return defer { symbol('{').then(go(1)) }
    }
    fun bracedUrl(): LP<TokenList> = braced(defer {
        (satisfyTok { it.type==TokType.Comment }.flatMap { t ->
            val cut=if(t.text.isEmpty())throw NoSuchElementException("Data.Text.tail") else Character.charCount(t.text.codePointAt(0))
            val rest=LaTeXTokens.tokenize(t.position.copy(column=t.position.column+1),t.text.substring(cut))
            input().flatMap { setInput(TokStream(it.expanded,cons(Tok(t.position,TokType.Symbol,"%"),append(rest,it.tokens)))) }
        } or pure(Unit)).then(anyTok())
    })
    fun bracedOrToken(): LP<TokenList> = defer { braced() or one(anyControlSeq() or singleChar()) }
    fun <A> bracketed(p: LP<A>, empty: A, combine: (A,A)->A): LP<A> = defer {
        symbol('[').then(till(p,symbol(']'))).map { it.fold(empty,combine) }.attempt()
    }
    fun bracketedToks(): LP<TokenList> = defer {
        symbol('[').then(till(LaTeXParsing.withRaw(braced().attempt()).map { it.second } or one(anyTok()),symbol(']'))).map(::concat)
    }
    fun delimitedToks(open: Tok,close: Tok): LP<TokenList> {
        fun go(n: Long): LP<TokenList> =
            LaTeXParsing.withRaw(braced().attempt()).flatMap { ts -> defer { go(n) }.map { append(ts.second,it) } } or
                match(close).flatMap { t -> if(n==1L)pure(tokenList(emptyList())) else defer { go(n-1) }.map { cons(t,it) } } or
                match(open).flatMap { t -> defer { go(n+1) }.map { cons(t,it) } } or
                anyTok().flatMap { t -> defer { go(n) }.map { cons(t,it) } }
        return defer { match(open).then(go(1)) }
    }
    fun verbTok(stop: Int): LP<Tok> = anyTok().flatMap { t ->
        val i=t.text.codePoints().toArray().indexOf(stop)
        if(i<0)pure(t) else {
            val chars=t.text.codePoints().toArray()
            val remainder=LaTeXTokens.tokenize(t.position.copy(column=t.position.column+i+1),TextChars.fromPoints(chars.drop(i+1).toIntArray()))
            input().flatMap { s -> setInput(TokStream(s.expanded,cons(Tok(t.position.copy(column=t.position.column+i),TokType.Symbol,
                TextChars.fromPoints(intArrayOf(stop))),append(remainder,s.tokens)))) }.map {
                Tok(t.deferredPosition,t.deferredType,Deferred.now(TextChars.fromPoints(chars.take(i).toIntArray())))
            }
        }
    }
    fun verbatimArg(): LP<TokenList> = defer {
        sp().optional().then(braced() or anySymbol().flatMap { t ->
            if(t.text.codePointCount(0,t.text.length)!=1)zero() else {
                val c=t.text.codePointAt(0)
                till(newlineTok().notFollowedBy { "()" }.then(verbTok(c)),symbol(c)).map(::tokenList)
            }
        }).map { ts -> head(ts)?.let { tokenList(listOf(Tok(it.head.value().position,TokType.Word,LaTeXTokens.untokenize(ts)))) }
            ?: tokenList(emptyList()) }.attempt()
    }
    fun <A> parenWrapped(p: LP<A>,empty: A,combine:(A,A)->A): LP<A> = defer {
        symbol('(').then(till(p,symbol(')'))).map { it.fold(empty,combine) }.attempt()
    }
    fun dimenarg(): LP<String> = defer {
        sp().optional().then(symbol('=').map { "=" }.option("")).flatMap { eq ->
            symbol('-').map { "-" }.option("").flatMap { minus ->
                satisfyTok { it.type==TokType.Word }.flatMap { t ->
                    if(t.text.firstOrNull() in '0'..'9')pure(t.text) else zero()
                }.option("").flatMap { first ->
                    symbol('.').then(satisfyTok { it.type==TokType.Word }).map { "."+it.text }.attempt().option("").flatMap { second ->
                        val s=first+second;val n=s.takeWhile { it in '0'..'9' || it=='.' }
                        if(n.isEmpty() || s.drop(n.length) !in dimensionUnits)zero() else pure(eq+minus+s)
                    }
                }
            }
        }.attempt()
    }
    fun <A> ignore(raw: String,empty: A): LP<A> = position().flatMap { report(raw,it) }.map { empty }

    private fun keyval(): LP<Pair<String,String>> = defer {
        sp().then(satisfyTok { !sym(it,"=") && (it.type==TokType.Word || sym(it,"-") || sym(it,"_")) }.many1())
            .flatMap { key -> sp().then(symbol('=').then(sp()).then(
                braced().map(LaTeXTokens::untokenize) or
                    (LaTeXParsing.withRaw(braced()).map { LaTeXTokens.untokenize(it.second) } or
                        satisfyTok { t -> t.type!=TokType.Symbol || t.text !in listOf("]",",","{","}") }
                            .many1().map { LaTeXTokens.untokenize(it) }).many1().map { it.joinToString("") }
            ).option("")).flatMap { value ->
                sp().then(symbol(',').optional()).then(sp()).map { LaTeXTokens.untokenize(key) to value.trim { ParserCharacters.isSpace(it.code) } }
            } }.attempt()
    }
    fun keyvals(): LP<List<Pair<String,String>>> = defer { symbol('[').then(till(keyval(),symbol(']'))).before(sp()).attempt() }
    fun begin(name: String): LP<Unit> = environmentBoundary("begin",name)
    fun end(name: String): LP<Unit> = environmentBoundary("end",name)
    private fun environmentBoundary(command: String,name: String): LP<Unit> = defer {
        controlSeq(command).then(spaces()).then(braced()).flatMap { if(LaTeXTokens.untokenize(it)==name)pure(Unit) else zero() }
            .attempt().label("\\$command{$name}")
    }
    fun verbEnv(name: String): LP<String> = LaTeXParsing.withVerbatimMode(blankline().optional().then(till(anyTok(),end(name))).map { ts ->
        val text=LaTeXTokens.untokenize(ts);val at=text.lastIndexOf('\n')
        if(text.substring(at+1).all { it==' ' })text.take(if(at<0)0 else at) else text
    })
    fun overlaySpecification(): LP<String> = defer {
        symbol('<').then(till(satisfyTok { t -> t.type==TokType.Word || t.type==TokType.Spaces ||
            t.type==TokType.Symbol && t.text in listOf("-","+","@","|",":",",") },symbol('>'))).flatMap { ts ->
            val text=LaTeXTokens.untokenize(ts)
            if(!text.codePoints().allMatch(ParserCharacters::isAlpha) || text in setOf("beamer","presentation","trans","handout","article","second"))
                pure("<$text>") else zero()
        }.attempt()
    }
    fun rawopt(): LP<String> = defer { sp().then(bracketedToks()).before(sp()).map { "["+LaTeXTokens.untokenize(it)+"]" }.attempt() }
    fun skipopts(): LP<Unit> = defer { (overlaySpecification().map { Unit } or rawopt().map { Unit }).skipMany() }
    fun getRawCommand(name: String,text: String): LP<String> = defer {
        val args: LP<Unit> = when {
            name=="write" -> satisfyTok { it.type==TokType.Word && it.text.all { c -> c in '0'..'9' } }.many().then(braced()).map { Unit }
            name=="titleformat" -> braced().then(skipopts()).then(sequence(List(4) { braced() })).map { Unit }
            name=="def" -> till(anyTok(),braced()).map { Unit }
            name=="vadjust" -> till(anyTok(),braced()).map { Unit } or satisfyTok { it.type==TokType.Word && it.text=="pre" }.map { Unit }
            name in fontSizeCommands || name in setOf("hfil","hfill","vfil","vfill","hfilneg","vfilneg") -> pure(Unit)
            name in setOf("hskip","vskip","mskip") -> dimenarg().then(sp().then(satisfyTok { it.type==TokType.Word && it.text in listOf("plus","minus") })
                .then(dimenarg()).attempt().skipMany())
            else -> skipopts().then(dimenarg().attempt().option("")).then(braced().many()).map { Unit }
        }
        LaTeXParsing.withRaw(args).map { text+LaTeXTokens.untokenize(it.second) }
    }
    fun label(): LP<Unit> = controlSeq("label").then(braced()).flatMap { ts -> update { it.copy(lastLabel=LaTeXTokens.untokenize(ts)) } }
    fun tokWith(inline: LP<Inlines>): LP<Inlines> = defer {
        spaces().then(grouped(inline,Inlines(),Inlines::plus) or anyControlSeq().lookAhead().then(inline) or
            singleChar().map { Builder.str(it.text) }).attempt()
    }
    fun setCaption(inline: LP<Inlines>): LP<Unit> = defer {
        bracketed(inline,Inlines(),Inlines::plus).map<List<Inline>?> { it.values }.option(null).flatMap { short ->
            tokWith(inline).flatMap { content -> spaces().then(label()).attempt().optional().then(
                update { it.copy(caption=Caption(short,listOf(Block.Plain(content.values)))) }) }
        }.attempt()
    }
    fun <A> env(name: String,p: LP<A>): LP<A> = update { it.copy(macros=it.macros.push()) }.then(p).flatMap { result ->
        update { it.copy(macros=it.macros.pop()) }.then(end(name)).map { result }
    }

    /** Explicit Walkable dictionary keeps the upstream operation polymorphic. */
    fun <A> removeLabel(label: String,value: A,walk: (PandocVisitor,A)->A): A = walk(object : PandocVisitor() {
        override fun transformInlines(value: List<Inline>): List<Inline> {
            val out=ArrayList<Inline>();var i=0
            while(i<value.size) {
                val x=value[i++]
                if(x is Inline.Span && x.attr.keyValues.firstOrNull { it.first=="label" }?.second==label) {
                    while(i<value.size && (value[i]===Inline.Space || value[i]===Inline.SoftBreak))i++
                } else out.add(x)
            }
            return out
        }
    },value)

    fun doMacros(): LP<Unit> = defer { input().flatMap { s ->
        if(s.expanded || head(s.tokens)?.head?.value()?.type !is TokType.CtrlSeq)pure(Unit)
        else state().flatMap { if(it.user.verbatimMode)pure(Unit) else doMacros(1,s.tokens).flatMap { ts -> setInput(TokStream(true,ts)) } }
    } }
    fun doMacros(depth: Long,tokens: TokenList): LP<TokenList> = defer {
        val first=head(tokens) ?: return@defer pure(tokens)
        val t=first.head.value();val command=t.type as? TokType.CtrlSeq ?: return@defer pure(tokens)
        if(command.text=="begin" || command.text=="end") {
            val a=head(first.tail)
            if(a!=null && sym(a.head.value(),"{")) {
                val b=head(a.tail)
                if(b!=null && b.head.value().type==TokType.Word) {
                    val c=head(b.tail)
                    if(c!=null && sym(c.head.value(),"}"))return@defer handleMacro(depth,t.deferredPosition,
                        (if(command.text=="end")"end" else "")+b.head.value().text,c.tail) or pure(tokens)
                }
            }
        }
        if(command.text=="expandafter") {
            val next=head(first.tail)
            if(next!=null)return@defer doMacros(depth,next.tail).map { combineTok(next.head.value(),it) }
        }
        handleMacro(depth,t.deferredPosition,command.text,first.tail) or pure(tokens)
    }
    private fun combineTok(t: Tok,ts: TokenList): TokenList {
        val command=t.type as? TokType.CtrlSeq;val next=head(ts)
        if(command!=null && next!=null) {
            val w=next.head.value()
            if(w.type==TokType.Word && w.text.codePoints().allMatch { ParserCharacters.isAlpha(it) || it==64 }) {
                val cut=t.text.indexOfFirst { it==' ' || it=='\t' }.let { if(it<0)t.text.length else it }
                return cons(Tok(t.deferredPosition,Deferred.now(TokType.CtrlSeq(command.text+w.text)),
                    Deferred.now(t.text.take(cut)+w.text+t.text.drop(cut))),next.tail)
            }
        }
        return cons(t,ts)
    }
    private fun <A> nested(p: LP<A>,ts: TokenList,initial: LaTeXState,name: String,message: String): LP<A> =
        ReaderTokenParsec.askDeferred<LaTeXEnv,Tok,LaTeXState>().flatMap { env -> effect {
            p.runReader(env).run(TokenParseState(TokStream(false,ts),SourcePos(name),initial))
        }.flatMap { if(it.success)pure(it.value!!) else fail(message) } }

    private fun getargs(num: Long,args: Map<Long,TokenList>,specs: ArgumentList): LP<Map<Long,TokenList>> = defer {
        val node=specs.value() as? DeferredChain.Cons ?: return@defer pure(args)
        val spec=node.head.value();val rest=node.tail
        fun more(n: Long,x: Map<Long,TokenList>) = getargs(n,x,rest)
        fun put(i: Long,x: TokenList) = args+(i to x)
        fun delimitedBody(i: Long,trim: Boolean,verb: Boolean,raw: Boolean): LP<Map<Long,TokenList>>? {
            val next=rest.value() as? DeferredChain.Cons ?: return null
            val end=next.head.value() as? ArgSpec.Pattern ?: return null
            val item=if(raw)LaTeXParsing.withRaw(braced().attempt()).map { it.second } else braced()
            return till(item or one(anyTok()),pattern(end.tokens)).map(::concat).flatMap { x ->
                val value=if(verb)verbatimBodyToks(trim,x) else if(trim)trimSpaceToks(x) else x
                getargs(i+1,put(i,value),next.tail)
            }.attempt()
        }
        when(spec) {
            is ArgSpec.Pattern -> pattern(spec.tokens).then(more(num,args)).attempt()
            is ArgSpec.ArgNum -> delimitedBody(spec.number,false,false,false) ?:
                spaces().then(bracedOrToken()).attempt().flatMap { more(spec.number+1,put(spec.number,it)) }
            is ArgSpec.BoolArg -> {
                val t=spec.deferredToken.value();val before=if(spec.deferredSkipSpace.value())sp() else pure(Unit)
                before.then(match(t)).map { boolean(t.deferredPosition,true) }.attempt().option(boolean(t.deferredPosition,false))
                    .flatMap { more(num+1,put(num,it)) }
            }
            is ArgSpec.DelimArg -> {
                val open=spec.deferredOpen.value();val before=if(spec.deferredSkipSpace.value())sp() else pure(Unit)
                before.then(delimitedToks(open,spec.deferredClose.value())).attempt().option(default(spec.default) ?: noValue(open.deferredPosition))
                    .flatMap { more(num+1,put(num,it)) }
            }
            ArgSpec.VerbArg -> verbatimArg().flatMap { more(num+1,put(num,it)) }
            is ArgSpec.BodyArg -> delimitedBody(spec.deferredNumber.value(),spec.deferredTrim.value(),false,true) ?:
                getargs(num,args,Deferred.now(DeferredChain.Cons(Deferred.now(ArgSpec.ArgNum(spec.deferredNumber)),rest)))
            is ArgSpec.VerbBodyArg -> delimitedBody(spec.deferredNumber.value(),spec.deferredTrim.value(),true,true) ?:
                getargs(num,args,Deferred.now(DeferredChain.Cons(Deferred.now(ArgSpec.ArgNum(spec.deferredNumber)),rest)))
            is ArgSpec.EmbellishArg -> {
                val embs=materialize(spec.embellishments)
                fun grab(seen: Map<Int,TokenList>): LP<Map<Int,TokenList>> {
                    if(seen.size==embs.size)return pure(seen)
                    val choices=embs.indices.filter { it !in seen }.map { i -> match(embs[i].token).map { i } }
                        .fold(zero<Int>()) { a,b -> a or b }
                    return sp().then(choices).flatMap { i -> (braced() or one(anyTok())).flatMap { x -> defer { grab(seen+(i to x)) } } }
                        .attempt().option(seen)
                }
                grab(emptyMap()).flatMap { seen -> more(num+embs.size,args+embs.mapIndexed { i,e ->
                    (num+i) to (seen[i] ?: default(e.default) ?: noValue(e.deferredToken.flatMapValue { it.deferredPosition }))
                }) }
            }
            is ArgSpec.ProcessedArg -> getargs(num,emptyMap(),Deferred.now(DeferredChain.Cons(spec.deferredArgument,Deferred.now(DeferredChain.Nil))))
                .flatMap { new -> sequence(new.keys.sorted().map { i -> applyArgProcessors(spec.processors,new.getValue(i)).map { i to it } })
                    .flatMap { more(num+new.size,args+it.toMap()) } }
        }
    }
    private fun default(d: TokenDefault): TokenList? = when(val value=d.value()) {
        TeXMaybe.Nothing -> null; is TeXMaybe.Just -> value.value
    }
    private fun noValue(pos: Deferred<SourcePos>) = cons(Tok(pos,Deferred.now(TokType.CtrlSeq("NoValue")),Deferred.now("\\NoValue ")),tokenList(emptyList()))
    private fun boolean(pos: Deferred<SourcePos>,b: Boolean): TokenList {
        val name=if(b)"BooleanTrue" else "BooleanFalse"
        return cons(Tok(pos,Deferred.now(TokType.CtrlSeq(name)),Deferred.now("\\$name ")),tokenList(emptyList()))
    }
    private fun handleMacro(n: Long,pos: Deferred<SourcePos>,name: String,ts: TokenList): LP<TokenList> = defer {
        if(n>20)throw PandocError.PandocMacroLoop(name)
        state().flatMap { state ->
            val macro=state.user.macros.head[name]
            if(macro==null)specialMacro(name,ts).flatMap { doMacros(n+1,it) }
            else {
                val opt=default(macro.default)
                val argsParser=if(opt==null)getargs(1,emptyMap(),macro.arguments) else
                    bracketedToks().option(opt).flatMap { first ->
                        val rest=(macro.arguments.value() as? DeferredChain.Cons)?.tail ?: macro.arguments
                        getargs(2,mapOf(1L to first),rest)
                    }
                val p=if(macro.expansion==ExpansionPoint.ExpandWhenUsed)LaTeXParsing.withVerbatimMode(argsParser) else argsParser
                nested(p.flatMap { args -> input().map { args to it.tokens } },ts,state.user,"args","Could not parse arguments for $name")
                    .flatMap { (original,rest) ->
                        fun resolve(toks: TokenList,visited: Set<Long>): TokenList = toks.flatMapValue { chain ->
                            if(chain !is DeferredChain.Cons)tokenList(emptyList()) else {
                                val t=chain.head.value();val arg=t.type as? TokType.Arg
                                val tail=Deferred.defer { resolve(chain.tail,visited) }
                                if(arg!=null && arg.number !in visited && arg.number in original)
                                    append(Deferred.defer { resolve(original.getValue(arg.number),visited+arg.number) },tail)
                                else cons(t,tail)
                            }
                        }
                        val args=original.mapValues { (i,x) -> Deferred.defer { resolve(x,setOf(i)) } }
                        val body: TokenList=Deferred.later {
                            // reverse demands the spine, but not all token fields.
                            val heads=ArrayList<Deferred<Tok>>();var node=macro.replacement.value()
                            while(node is DeferredChain.Cons) {heads.add(node.head);node=node.tail.value()}
                            if(heads.lastOrNull()?.value()?.type==TokType.Newline && heads.getOrNull(heads.lastIndex-1)?.value()?.type!=TokType.Newline) {
                                var current=rest;var t=head(current)
                                while(t!=null && t.head.value().type in listOf(TokType.Spaces,TokType.Comment)) {current=t.tail;t=head(current)}
                                if(t?.head?.value()?.type==TokType.Newline) {
                                    val old=heads.last().value();heads[heads.lastIndex]=Deferred.now(Tok(old.deferredPosition,Deferred.now(TokType.Spaces),Deferred.now(" ")))
                                }
                            }
                            var out=tokenList(emptyList())
                            for(h in heads.asReversed())out=Deferred.now(DeferredChain.Cons(h,out))
                            out.value()
                        }
                        fun add(argument: Boolean,toks: TokenList,rest: TokenList): TokenList = toks.flatMapValue { chain ->
                            if(chain !is DeferredChain.Cons)return@flatMapValue rest
                            val t=chain.head.value()
                            val tail=Deferred.defer { add(argument,chain.tail,rest) }
                            val type=t.type
                            when {
                                !argument && type is TokType.DeferredArg -> cons(Tok(pos,Deferred.now(TokType.Arg(type.deferredNumber)),t.deferredText),tail)
                                !argument && type is TokType.Arg -> {
                                    val xs=args[type.number]
                                    if(xs==null)tokenList(emptyList()) else add(true,xs,tail)
                                }
                                type is TokType.CtrlSeq && t.text.isNotEmpty() && ParserCharacters.isAlpha(t.text.codePointBefore(t.text.length)) && head(tail)?.head?.value()?.type==TokType.Word ->
                                    cons(Tok(pos,t.deferredType,Deferred.now(t.text+" ")),tail)
                                else -> cons(Tok(pos,t.deferredType,t.deferredText),tail)
                            }
                        }
                        val result=Deferred.defer { add(false,body,rest) }
                        if(macro.expansion==ExpansionPoint.ExpandWhenUsed)doMacros(n+1,result) else pure(result)
                    }
            }
        }
    }

    private fun specialMacro(name: String,ts: TokenList): LP<TokenList> = when(name) {
        "xspace" -> doMacros(1,ts).map { rest ->
            val t=head(rest)?.head?.value()
            if(t!=null && t.type==TokType.Word && t.text.isNotEmpty() && ParserCharacters.isAlphaNum(t.text.codePointAt(0)))
                cons(Tok(t.deferredPosition,Deferred.now(TokType.Spaces),Deferred.now(" ")),rest) else rest
        }
        "iftrue" -> doIf(true,ts)
        "iffalse" -> doIf(false,ts)
        "ifmmode" -> state().flatMap { doIf(it.user.mathMode,ts) }
        "ifstrequal" -> handleIf(ifStrequal(),ts)
        "ProcessList" -> handleIf(processList(),ts)
        "UseName" -> handleIf(useName(),ts)
        "ExpandArgs" -> handleIf(expandArgs(),ts)
        "inteval" -> handleEval(LaTeXEvaluators::evalInteval,ts)
        "fpeval" -> handleEval(LaTeXEvaluators::evalFpeval,ts)
        "dimeval","skipeval" -> handleEval({ it.trim { c -> ParserCharacters.isSpace(c.code) } },ts)
        else -> {
            val prefix=listOf("IfNoValue","IfValue","IfBoolean","IfBlank").firstOrNull { name.startsWith(it) }
            val suffix=prefix?.let { name.removePrefix(it) }
            if(prefix==null || suffix !in listOf("TF","T","F"))zero() else {
                val test: (TokenList)->Boolean = when(prefix) {
                    "IfNoValue" -> ::isNoValueArg;"IfValue" -> { x -> !isNoValueArg(x) }
                    "IfBoolean" -> ::isBooleanTrueArg;else -> { x -> materialize(x).all(::spaceLike) }
                }
                handleIf(xparseIf(test,'T' in suffix!!,'F' in suffix),ts)
            }
        }
    }
    private fun handleIf(p: LP<TokenList>,ts: TokenList) = nested(p,ts,LaTeXState(),"tokens","Could not parse conditional")
    private fun grabArg() = LaTeXParsing.withVerbatimMode(spaces().then(braced() or one(anyTok())))
    private fun xparseIf(test: (TokenList)->Boolean,hasTrue: Boolean,hasFalse: Boolean): LP<TokenList> = grabArg().flatMap { arg ->
        (if(hasTrue)grabArg() else pure(tokenList(emptyList()))).flatMap { yes ->
            (if(hasFalse)grabArg() else pure(tokenList(emptyList()))).flatMap { no ->
                input().map { append(if(test(arg))yes else no,it.tokens) }
            }
        }
    }
    private fun isNoValueArg(ts: TokenList): Boolean {
        val xs=materialize(ts).filterNot(::spaceLike)
        return xs.size==1 && named(xs[0],"NoValue") || xs.size==3 && sym(xs[0],"-") && xs[1].type==TokType.Word && xs[1].text=="NoValue" && sym(xs[2],"-")
    }
    private fun isBooleanTrueArg(ts: TokenList) = materialize(ts).filterNot(::spaceLike).let { it.size==1 && named(it[0],"BooleanTrue") }
    private fun brace(pos: SourcePos,ts: TokenList) = cons(Tok(pos,TokType.Symbol,"{"),append(ts,tokenList(listOf(Tok(pos,TokType.Symbol,"}")))))
    private fun processList(): LP<TokenList> = LaTeXParsing.withVerbatimMode(position().flatMap { pos ->
        spaces().then(braced() or one(anyTok())).flatMap { list -> spaces().then(braced() or one(anyTok())).flatMap { fn ->
            input().map { stream ->
                val out=ArrayList<TokenList>();var rest=list
                while(true) {val group=tokGroup(rest) ?: break;out.add(append(fn,brace(pos,group.first)));rest=group.second}
                append(concat(out),stream.tokens)
            }
        } }
    })
    private fun useName(): LP<TokenList> = position().flatMap { p ->
        LaTeXParsing.withVerbatimMode(spaces().then(braced())).flatMap { ts ->
            val name=LaTeXTokens.untokenize(ts)
            input().map { cons(Tok(p,TokType.CtrlSeq(name),"\\$name "),it.tokens) }
        }
    }
    private fun expandArgs(): LP<TokenList> = LaTeXParsing.withVerbatimMode(spaces().then(braced()).flatMap { ts ->
        spaces().then(anyTok()).flatMap { cmd ->
            sequence(LaTeXTokens.untokenize(ts).codePoints().toArray().map { c ->
                when(c) {
                    99 -> position().flatMap { p -> spaces().then(braced()).map { x ->
                        val name=LaTeXTokens.untokenize(x);tokenList(listOf(Tok(p,TokType.CtrlSeq(name),"\\$name ")))
                    } }
                    110 -> position().flatMap { p -> spaces().then(braced()).map { brace(p,it) } }
                    78 -> spaces().then(one(anyTok()))
                    else -> zero()
                }
            }).flatMap { args -> input().map { cons(cmd,append(concat(args),it.tokens)) } }
        }
    })
    private fun ifStrequal(): LP<TokenList> = (braced() or one(anyTok())).flatMap { a ->
        (braced() or one(anyTok())).flatMap { b -> LaTeXParsing.withVerbatimMode(braced() or one(anyTok())).flatMap { yes ->
            LaTeXParsing.withVerbatimMode(braced() or one(anyTok())).flatMap { no ->
                input().map { append(if(LaTeXTokens.untokenize(a)==LaTeXTokens.untokenize(b))yes else no,it.tokens) }
            }
        } }
    }
    private fun doIf(b: Boolean,ts: TokenList): LP<TokenList> = state().flatMap { state ->
        val conditionals=primitiveConditionalNames.toMutableSet()
        for(scope in sequenceOf(state.user.macros.head)+state.user.macros.tail.asSequence())for((k,v) in scope) {
            if(v.arguments.value()===DeferredChain.Nil && v.default.value()===TeXMaybe.Nothing) {
                val t=head(v.replacement)
                if(t!=null && t.tail.value()===DeferredChain.Nil && (t.head.value().type as? TokType.CtrlSeq)?.text in primitiveConditionalNames)conditionals.add(k)
            }
        }
        val split=splitConditional(conditionals,ts)
        if(split==null)fail("Could not parse conditional") else pure(append(if(b)split.first else split.second,split.third))
    }
    fun splitConditional(names: Set<String>,ts: TokenList): Triple<TokenList,TokenList,TokenList>? {
        var current=ts;var depth=0L;var inElse=false
        val yes=ArrayList<Tok>();val no=ArrayList<Tok>()
        while(true) {
            val node=head(current) ?: return null;val t=node.head.value();current=node.tail
            val name=(t.type as? TokType.CtrlSeq)?.text
            if(name=="fi" && depth==0L)return Triple(tokenList(yes),tokenList(no),current)
            if(name=="else" && depth==0L && !inElse) {inElse=true;continue}
            if(name=="fi")depth-- else if(name in names)depth++
            (if(inElse)no else yes).add(t)
        }
    }
    private fun handleEval(evaluator: (String)->String?,ts: TokenList): LP<TokenList> = state().flatMap { state ->
        val p=position().flatMap { pos -> braced().flatMap { arg -> input().flatMap { rest ->
            val text=LaTeXTokens.untokenize(arg);val result=evaluator(text)
            (if(result==null)report("evaluation of $text",pos) else pure(Unit)).map {
                append(LaTeXTokens.tokenize(pos,result ?: text),rest.tokens)
            }
        } } }
        nested(p,ts,state.user,"eval","Could not parse evaluator argument")
    }

    fun trimSpaceToks(ts: TokenList): TokenList = tokenList(materialize(ts).dropWhile { it.type==TokType.Spaces || it.type==TokType.Newline }
        .dropLastWhile { it.type==TokType.Spaces || it.type==TokType.Newline })
    fun verbatimBodyToks(trim: Boolean,ts: TokenList): TokenList {
        val first=head(ts) ?: return ts
        val text=LaTeXTokens.untokenize(ts)
        var lines=if(text.isEmpty())emptyList() else text.split('\n').let { if(text.endsWith('\n'))it.dropLast(1) else it }
        if(trim)lines=lines.dropWhile { it.all { c -> c==' ' || c=='\t' } }.dropLastWhile { it.all { c -> c==' ' || c=='\t' } }
        val out=ArrayList<Tok>();val pos=first.head.value().deferredPosition
        for((i,line) in lines.withIndex()) {
            if(i>0)out.add(Tok(pos,Deferred.now(TokType.CtrlSeq("\\")),Deferred.now("\\\\")))
            if(line.isNotEmpty())out.add(Tok(pos,Deferred.now(TokType.Word),Deferred.now(line.replace(' ','\u2423'))))
        }
        return tokenList(out)
    }
    fun tokGroup(ts: TokenList): Pair<TokenList,TokenList>? {
        var current=ts;var node=head(current)
        while(node!=null && spaceLike(node.head.value())) {current=node.tail;node=head(current)}
        node ?: return null
        if(!sym(node.head.value(),"{"))return tokenList(listOf(node.head.value())) to node.tail
        var depth=1L;val out=ArrayList<Tok>();current=node.tail
        while(true) {
            val next=head(current) ?: return null;val t=next.head.value();current=next.tail
            if(sym(t,"{"))depth++
            if(sym(t,"}")) {if(depth==1L)return tokenList(out) to current;depth--}
            out.add(t)
        }
    }
    fun splitToksOn(delim: Tok,ts: TokenList): List<TokenList> {
        val out=ArrayList<TokenList>();var current=ArrayList<Tok>();var depth=0L
        for(t in materialize(ts)) {
            if(depth==0L && same(t,delim)) {out.add(tokenList(current));current=ArrayList()}
            else {if(sym(t,"{"))depth++;if(sym(t,"}"))depth=maxOf(0,depth-1);current.add(t)}
        }
        out.add(tokenList(current));return out
    }
    fun applyArgProcessors(processors: ProcessorList,ts: TokenList): LP<TokenList> = defer {
        var p=pure(ts)
        for(proc in materialize(processors).asReversed())p=p.flatMap { applyArgProcessor(proc,it) }
        p
    }
    private fun applyArgProcessor(proc: TokenList,ts: TokenList): LP<TokenList> = defer {
        var current=proc;var node=head(current)
        while(node!=null && spaceLike(node.head.value())) {current=node.tail;node=head(current)}
        val first=node ?: return@defer pure(ts)
        val t=first.head.value()
        when((t.type as? TokType.CtrlSeq)?.text) {
            "TrimSpaces" -> return@defer pure(trimSpaceToks(ts))
            "ReverseBoolean" -> {
                val xs=materialize(ts).filterNot(::spaceLike)
                return@defer pure(if(xs.size==1 && named(xs[0],"BooleanTrue"))boolean(t.deferredPosition,false)
                    else if(xs.size==1 && named(xs[0],"BooleanFalse"))boolean(t.deferredPosition,true) else ts)
            }
            "SplitArgument" -> {
                val n=tokGroup(first.tail)
                val parts=n?.let { HaskellNumber.readInteger(LaTeXTokens.untokenize(it.first))?.toLong() }
                val delimiter=n?.let { tokGroup(it.second) }?.first?.let { materialize(it).firstOrNull { x -> !spaceLike(x) } }
                if(parts!=null && delimiter!=null) {
                    val pieces=splitToksOn(delimiter,ts).map(::trimSpaceToks)
                    // Haskell Int addition wraps before take; take <= 0 yields [].
                    val count=parts+1
                    val out=ArrayList<TokenList>();var i=0L
                    while(i<count) {out.add(brace(t.position,if(i<pieces.size)pieces[i.toInt()] else noValue(t.deferredPosition)));i++}
                    return@defer pure(concat(out))
                }
            }
            "SplitList" -> {
                val delimiter=tokGroup(first.tail)?.first?.let { materialize(it).firstOrNull { x -> !spaceLike(x) } }
                if(delimiter!=null)return@defer pure(concat(splitToksOn(delimiter,ts).map { brace(t.position,trimSpaceToks(it)) }))
            }
        }
        report("processor "+LaTeXTokens.untokenize(proc),t.position).map { ts }
    }

    companion object {
        val specialChars="#$%&~_^\\{}".codePoints().toArray().toSet()
        fun isNewlineTok(t: Tok) = t.type==TokType.Newline
        fun isWordTok(t: Tok) = t.type==TokType.Word
        fun isArgTok(t: Tok) = t.type is TokType.Arg
        fun tokTypeIn(types: List<TokType>,t: Tok) = t.type in types
        private val dimensionUnits=setOf("","pt","pc","in","bp","cm","mm","dd","cc","sp","ex","em","mu","px")
        private val fontSizeCommands=setOf("tiny","scriptsize","footnotesize","small","normalsize","large","Large","LARGE","huge","Huge")
        val primitiveConditionalNames=setOf("if","ifcase","ifcat","ifcsname","ifdefined","ifdim","ifeof","iffalse","iffontchar","ifhbox","ifhmode",
            "ifincsname","ifinner","ifmmode","ifnum","ifodd","iftrue","ifvbox","ifvmode","ifvoid","ifx")
    }
}
