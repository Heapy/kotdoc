package io.heapy.kotdoc.latex

import io.heapy.kotdoc.formats.HaskellDouble
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.json.PrettyJson
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.logging.Logging
import io.heapy.kotdoc.options.ReaderOptions
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.state.PandocStateContext
import io.heapy.kotdoc.tex.*
import io.heapy.kotdoc.tex.SourcePos
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Test
import java.util.zip.GZIPInputStream

class LaTeXKernelTest {
    private val nativePlatform: String get() {
        val os=System.getProperty("os.name");val arch=System.getProperty("os.arch")
        return when {
            os.startsWith("Mac") && arch in listOf("aarch64","arm64")->"macos-aarch64"
            os.startsWith("Linux") && arch in listOf("amd64","x86_64")->"linux-x86_64"
            else->error("No original LaTeX libm reference for $os/$arch")
        }
    }
    private fun numeric(platform: String) = GZIPInputStream(javaClass.getResourceAsStream("/latex-kernel/numeric-$platform.jsonl.gz")!!)
        .bufferedReader().use { it.readLines().drop(1).map { line->Json.parseToJsonElement(line).jsonObject } }
    @Test fun independentNumericPlatforms() {
        val mac=numeric("macos-aarch64");val linux=numeric("linux-x86_64")
        assertEquals(mac.map { it.getValue("request") },linux.map { it.getValue("request") })
        for(i in mac.indices)if(mac[i].getValue("request").jsonArray[0].s()!="float")assertEquals(mac[i],linux[i])
    }
    private fun JsonElement.s() = jsonPrimitive.content
    private fun JsonElement.n() = jsonPrimitive.long
    private fun JsonElement.b() = jsonPrimitive.boolean
    private fun a(vararg xs: JsonElement) = JsonArray(xs.toList())
    private fun raw(x: JsonElement) = TextChars.fromPoints(x.jsonArray.map { it.jsonPrimitive.int }.toIntArray())
    private fun pt(s: String) = JsonArray(s.codePoints().toArray().map(::JsonPrimitive))
    private fun pos(v: JsonElement): SourcePos = v.jsonArray.let { SourcePos(CharacterString(it[0].jsonArray.map { p -> p.jsonPrimitive.int }.toIntArray()),it[1].n(),it[2].n()) }
    private fun pv(p: SourcePos) = a(JsonArray(p.nameRaw.points().map(::JsonPrimitive)),JsonPrimitive(p.line),JsonPrimitive(p.column))
    private fun tt(v: JsonElement): TokType = v.jsonArray.let { when(it[0].s()) {
        "CtrlSeq"->TokType.CtrlSeq(raw(it[1]));"Arg"->TokType.Arg(it[1].n());"DeferredArg"->TokType.DeferredArg(it[1].n())
        "Spaces"->TokType.Spaces;"Newline"->TokType.Newline;"Word"->TokType.Word;"Symbol"->TokType.Symbol
        "Comment"->TokType.Comment;"Esc1"->TokType.Esc1;"Esc2"->TokType.Esc2;else->error(it)
    } }
    private fun ttv(t: TokType): JsonElement = when(t) {
        is TokType.CtrlSeq->a(JsonPrimitive("CtrlSeq"),pt(t.text));is TokType.Arg->a(JsonPrimitive("Arg"),JsonPrimitive(t.number))
        is TokType.DeferredArg->a(JsonPrimitive("DeferredArg"),JsonPrimitive(t.number));else->a(JsonPrimitive(t.show()))
    }
    private fun tok(v: JsonElement) = v.jsonArray.let { Tok(pos(it[0]),tt(it[1]),raw(it[2])) }
    private fun tv(t: Tok) = a(pv(t.position),ttv(t.type),pt(t.text))
    private fun tokens(v: JsonElement) = tokenList(v.jsonArray.map(::tok))
    private fun <A> list(xs: Deferred<DeferredChain<A>>): List<A> {
        var node=xs.value();val out=ArrayList<A>()
        while(node is DeferredChain.Cons) {out.add(node.head.value());node=node.tail.value()};return out
    }
    private fun tvs(xs: TokenList) = JsonArray(list(xs).map(::tv))
    private fun spec(v: JsonElement): ArgSpec = v.jsonArray.let { when(it[0].s()) {
        "ArgNum"->ArgSpec.ArgNum(it[1].n());"Pattern"->ArgSpec.Pattern(tokens(it[1]))
        "BoolArg"->ArgSpec.BoolArg(it[1].b(),tok(it[2]))
        "DelimArg"->ArgSpec.DelimArg(it[1].b(),tok(it[2]),tok(it[3]),it[4].takeUnless { x->x==JsonNull }?.let(::tokens)?.let(::list))
        "VerbArg"->ArgSpec.VerbArg;"BodyArg"->ArgSpec.BodyArg(it[1].b(),it[2].n());"VerbBodyArg"->ArgSpec.VerbBodyArg(it[1].b(),it[2].n())
        "EmbellishArg"->ArgSpec.EmbellishArg(it[1].jsonArray.map { x->x.jsonArray.let { e->Embellishment(tok(e[0]),e[1].takeUnless { d->d==JsonNull }?.let(::tokens)?.let(::list)) } })
        "ProcessedArg"->ArgSpec.ProcessedArg(it[1].jsonArray.map { p->list(tokens(p)) },spec(it[2]));else->error(it)
    } }
    private fun macro(v: JsonElement) = v.jsonArray.let { Macro(MacroScope.entries[it[0].jsonPrimitive.int],ExpansionPoint.entries[it[1].jsonPrimitive.int],it[2].jsonArray.map(::spec),
        it[3].takeUnless { x->x==JsonNull }?.let(::tokens)?.let(::list),list(tokens(it[4]))) }
    private fun change(s: LaTeXState,v: JsonElement): LaTeXState = v.jsonArray.let { when(it[0].s()) {
        "options"->s.copy(options=ReaderOptions.fromJson(it[1]))
        "define"->s.copy(macros=s.macros.replaceHead(s.macros.head+(it[1].s() to macro(it[2]))))
        "push"->s.copy(macros=s.macros.push());"pop"->s.copy(macros=s.macros.pop())
        "verbatim"->s.copy(verbatimMode=it[1].b());"math"->s.copy(mathMode=it[1].b());"enable-raw"->s.copy(enableWithRaw=it[1].b())
        "raw"->s.copy(rawTokens=tokens(it[1]),rawTokenCount=it[2].n(),rawScopes=it[3].n());else->error(it)
    } }
    private fun sv(s: LaTeXState) = buildJsonObject {
        put("macros",JsonArray((listOf(s.macros.head)+s.macros.tail).map { sc->JsonArray(sc.keys.sortedWith(UnicodeTextOrder).map { a(JsonPrimitive(it),JsonPrimitive(sc.getValue(it).show())) }) }))
        put("verbatim",s.verbatimMode);put("math",s.mathMode);put("enableRaw",s.enableWithRaw)
        put("rawTokens",tvs(s.rawTokens));put("rawCount",s.rawTokenCount);put("rawScopes",s.rawScopes)
        put("caption",s.caption?.let(PandocJson::encodeCaption) ?: JsonNull);put("lastLabel",s.lastLabel?.let(::JsonPrimitive) ?: JsonNull)
    }
    private fun parser(k: LaTeXKernel,v: JsonElement): LP<JsonElement> {
        val x=v.jsonArray
        fun sub(i: Int)=parser(k,x[i])
        fun unit(p: LP<Unit>)=p.map<JsonElement> { JsonNull }
        fun token(p: LP<Tok>)=p.map<JsonElement>(::tv)
        fun toks(p: LP<TokenList>)=p.map<JsonElement>(::tvs)
        fun string(p: LP<String>)=p.map<JsonElement>(::pt)
        return when(x[0].s()) {
            "pure"->ReaderTokenParsec.pure(x[1]);"fail"->ReaderTokenParsec.fail("failure")
            "change"->unit(LaTeXParsing.update { change(it,x[1]) });"state"->LaTeXParsing.state().map { sv(it.user) }
            "then"->sub(1).then(sub(2));"pair"->sub(1).flatMap { p->sub(2).map { q->a(p,q) } }
            "or"->sub(1) or sub(2);"try"->sub(1).attempt();"look"->sub(1).lookAhead();"many"->sub(1).many().map { JsonArray(it) }
            "repeat"->{var p: LP<List<JsonElement>> = ReaderTokenParsec.pure(emptyList());repeat(x[1].jsonPrimitive.int) { p=p.flatMap { out->sub(2).map { out+it } } };p.map { JsonArray(it) }}
            "verbatim"->LaTeXParsing.withVerbatimMode(sub(1));"disable"->LaTeXParsing.disablingWithRaw(sub(1))
            "raw"->LaTeXParsing.withRaw(sub(1)).map { a(it.first,tvs(it.second)) };"from"->LaTeXParsing.parseFromToks(sub(2),tokens(x[1]))
            "any"->token(k.anyTok());"all"->k.anyTok().many().map { JsonArray(it.map(::tv)) };"peek"->token(k.peekTok())
            "control"->token(if(x.size==1)k.anyControlSeq() else k.controlSeq(raw(x[1])))
            "symbol"->token(if(x.size==1)k.anySymbol() else k.symbol(x[1].jsonPrimitive.int))
            "symbol-in"->token(k.symbolIn(x[1].jsonArray.map { it.jsonPrimitive.int }.toSet()))
            "infile"->token(k.infile(CharacterString(x[1].jsonArray.map { it.jsonPrimitive.int }.toIntArray())))
            "single"->token(k.singleChar());"escape"->k.primEscape().map { JsonPrimitive(it) }
            "spaces"->unit(k.spaces());"spaces1"->unit(k.spaces1());"sp"->unit(k.sp());"blankline"->unit(k.blankline());"endline"->unit(k.endline())
            "whitespace"->unit(k.whitespace());"newline"->unit(k.newlineTok());"comment"->unit(k.comment())
            "bgroup"->token(k.bgroup());"egroup"->token(k.egroup());"braced"->toks(k.braced());"braced-url"->toks(k.bracedUrl())
            "braced-or-token"->toks(k.bracedOrToken());"bracketed"->k.bracketed(k.anyTok().map { listOf(it) },emptyList(),List<Tok>::plus).map { JsonArray(it.map(::tv)) }
            "bracketed-toks"->toks(k.bracketedToks());"grouped"->k.grouped(k.singleChar().map { listOf(it) },emptyList(),List<Tok>::plus).map { JsonArray(it.map(::tv)) }
            "paren"->k.parenWrapped(k.anyTok().map { listOf(it) },emptyList(),List<Tok>::plus).map { JsonArray(it.map(::tv)) }
            "verb"->token(k.verbTok(x[1].jsonPrimitive.int));"verbatim-arg"->toks(k.verbatimArg());"delimited"->toks(k.delimitedToks(tok(x[1]),tok(x[2])))
            "dimen"->string(k.dimenarg());"keyvals"->k.keyvals().map { JsonArray(it.map { p->a(pt(p.first),pt(p.second)) }) }
            "begin"->unit(k.begin(raw(x[1])));"end"->unit(k.end(raw(x[1])));"verb-env"->string(k.verbEnv(raw(x[1])))
            "overlay"->string(k.overlaySpecification());"rawopt"->string(k.rawopt());"skipopts"->unit(k.skipopts());"raw-command"->string(k.getRawCommand(raw(x[1]),raw(x[2])))
            "label"->unit(k.label());"caption"->unit(k.setCaption(k.singleChar().map { Builder.str(it.text) }))
            "tok-with"->k.tokWith(k.singleChar().map { Builder.str(it.text) }).map { JsonArray(it.values.map(PandocJson::encodeInline)) }
            "env"->k.env(raw(x[1]),sub(2));"do-macros"->unit(k.doMacros())
            "expand"->LaTeXParsing.state().flatMap { st->toks(k.doMacros(x[1].n(),(st.input as TokStream).tokens)) }
            "processors"->toks(k.applyArgProcessors(DeferredChain.of(x[1].jsonArray.map(::tokens)),tokens(x[2])))
            else->error(x)
        }
    }
    private fun run(changes: JsonElement,ts: JsonElement,expression: JsonElement): JsonElement {
        val context=PandocStateContext();val k=LaTeXKernel(context)
        val p=parser(k,expression).flatMap { value->LaTeXParsing.state().map { value to it } }
        val initial=changes.jsonArray.fold(LaTeXState(),::change)
        val result=try { p.run(TokenParseState(TokStream(false,tokens(ts)),SourcePos("chunk"),initial),LaTeXEnv()) }
            catch(e: PandocError) { return buildJsonObject { put("pandocError",CharacterStringChars.build(e.show())) } }
        val logs=JsonArray(context.getLog().map { Json.parseToJsonElement(PrettyJson.encode(Logging.toValue(it))) })
        if(!result.success)return buildJsonObject { put("ok",false);put("error",result.error.toString());put("logs",logs) }
        val (value,st)=result.value!!;val rest=st.input as TokStream
        return buildJsonObject { put("ok",true);put("value",value);put("state",sv(st.user));put("position",pv(st.position));put("expanded",rest.expanded);put("rest",tvs(rest.tokens));put("logs",logs) }
    }
    private fun bridge(changes: JsonElement,chunks: JsonElement,action: JsonElement): JsonElement {
        val context=PandocStateContext();val k=LaTeXKernel(context);val bridge=LaTeXParsingBridge(k)
        val options=ReaderOptionsAccess<LaTeXState> { it.options }
        val x=action.jsonArray
        val p: Parsec<LaTeXState,JsonElement> = when(x[0].s()) {
            "apply"->bridge.applyMacros(raw(x[1]),LaTeXStateAccess,options).map(::pt)
            "raw"->LaTeXTokens.getInputTokens<LaTeXState>().flatMap { tokens ->
                bridge.rawLaTeXParser(LaTeXEnv(),tokens,parser(k,x[1]).map { Unit },parser(k,x[2]),LaTeXStateAccess,options)
                    .map { a(it.first,pt(it.second)) }
            }
            else->error(x)
        }
        val parser=p.flatMap { value->Parsec.state<LaTeXState>().map { value to it } }
        val sources=Sources(chunks.jsonArray.map { it.jsonArray.let { c->SourceChunk(pos(c[0]),raw(c[1])) } })
        val result=try { parser.run(ParseState(ParseInput(sources),SourcePos("outer"),changes.jsonArray.fold(LaTeXState(),::change))) }
            catch(e: PandocError) { return buildJsonObject { put("pandocError",CharacterStringChars.build(e.show())) } }
        val logs=JsonArray(context.getLog().map { Json.parseToJsonElement(PrettyJson.encode(Logging.toValue(it))) })
        if(!result.success)return buildJsonObject { put("ok",false);put("error",result.error.toString());put("logs",logs) }
        val (value,st)=result.value!!
        val rest=JsonArray(list((st.input.stream as Sources).unSources).map { a(pv(it.position),pt(it.text)) })
        return buildJsonObject { put("ok",true);put("value",value);put("state",sv(st.user));put("position",pv(st.position));put("rest",rest);put("logs",logs) }
    }
    private fun observe(request: JsonArray): JsonElement = try { observeRequest(request) }
        catch(e: IllegalArgumentException) { buildJsonObject { put("exception",e.message) } }
    private fun observeRequest(request: JsonArray): JsonElement {
        fun expression() = TextChars.fromPoints(request[1].jsonArray.map { it.jsonPrimitive.int }.toIntArray())
        fun number() = Double.fromBits(request[1].jsonPrimitive.content.toULong().toLong())
        when(request[0].s()) {
            "demand"->return demand(request[1].jsonPrimitive.int)
            "bridge"->return bridge(request[1],request[2],request[3])
            "parse"->return run(request[1],request[2],request[3])
            "tokens"->return tvs(LaTeXTokens.tokenize(pos(request[1]),raw(request[2])))
            "body"->return tvs(LaTeXKernel().verbatimBodyToks(request[1].b(),tokens(request[2])))
            "group"->return LaTeXKernel().tokGroup(tokens(request[1]))?.let { a(tvs(it.first),tvs(it.second)) } ?: JsonNull
            "split"->return JsonArray(LaTeXKernel().splitToksOn(tok(request[1]),tokens(request[2])).map(::tvs))
            "remove-label"->return Json.parseToJsonElement(PandocJson.encode(LaTeXKernel().removeLabel(raw(request[1]),PandocJson.decode(request[2]),PandocVisitor::walk)))
        }
        val result = when (request[0].jsonPrimitive.content) {
            "integer" -> LaTeXEvaluators.evalInteval(expression())
            "float" -> LaTeXEvaluators.evalFpeval(expression())
            "format" -> LaTeXEvaluators.formatFp(number())
            "exponent" -> HaskellDouble.showExponent(number(), request[2].jsonPrimitive.int)
            "fixed" -> HaskellDouble.showFixed(number())
            else -> error(request)
        }
        return result?.let(::JsonPrimitive) ?: JsonNull
    }
    private fun demand(kind: Int): JsonElement {
        fun <A> bad(message: String): Deferred<A> = Deferred.later { error(message) }
        val pos=SourcePos("demand")
        val a=Tok(bad("bad-position"),Deferred.now(TokType.CtrlSeq("a")),bad("bad-text"))
        val word=Tok(pos,TokType.Word,"x")
        val body: TokenList=when(kind) {
            1,2->bad("bad-body")
            4->Deferred.now(DeferredChain.Cons(bad("bad-head"),tokenList(listOf(word))))
            else->tokenList(listOf(word))
        }
        val default: TokenDefault=if(kind==5)Deferred.now(TeXMaybe.Just(Deferred.now(DeferredChain.Cons(bad("bad-default"),tokenList(emptyList())))))
            else tokenDefault(null)
        val macro=Macro(Deferred.now(MacroScope.GlobalScope),Deferred.now(if(kind==2)ExpansionPoint.ExpandWhenUsed else ExpansionPoint.ExpandWhenDefined),
            DeferredChain.of(emptyList()),default,body)
        val state=if(kind==0)LaTeXState() else LaTeXState(macros=MacroScopes(mapOf("a" to macro)),verbatimMode=kind==7)
        val k=LaTeXKernel()
        val p: LP<Boolean> = when(kind) {
            0,1,2->k.doMacros(1,tokenList(listOf(a))).map { true }
            3->k.doMacros(1,tokenList(listOf(a))).map { it.value()===DeferredChain.Nil }
            4,5->k.doMacros(1,tokenList(listOf(a))).map { (it.value() as DeferredChain.Cons).head.value().text=="x" }
            6,7->k.doMacros().map { true }
            8->k.doMacros(21,tokenList(listOf(a))).map { true }
            else->error(kind)
        }
        return try {
            val input=if(kind==6)TokStream(true,bad("bad-input")) else TokStream(false,listOf(a))
            val result=p.run(TokenParseState(input,pos,state),LaTeXEnv())
            if(result.success)JsonPrimitive(result.value!!) else buildJsonObject { put("parseError",result.error.toString()) }
        } catch(e: PandocError) { buildJsonObject { put("pandocError",CharacterStringChars.build(e.show())) } }
          catch(e: IllegalStateException) { buildJsonObject { put("exception",e.message) } }
    }
    @TestFactory
    fun originalKernel(): List<DynamicTest> = GZIPInputStream(javaClass.getResourceAsStream("/latex-kernel/data.jsonl.gz")!!)
        .bufferedReader().use { reader ->
            reader.readLine()
            val overrides=if(nativePlatform=="linux-x86_64")numeric(nativePlatform).associate { it.getValue("request").toString() to it.getValue("expected") } else emptyMap()
            reader.lineSequence().mapIndexed { index, line ->
                val c = Json.parseToJsonElement(line).jsonObject
                val request = c.getValue("request").jsonArray
                DynamicTest.dynamicTest("${index + 1}: ${c.getValue("group").jsonPrimitive.content} ${request.toString().take(200)}") {
                    assertEquals(overrides[request.toString()] ?: c.getValue("expected"), observe(request), request.toString())
                }
            }.toList()
        }
}
