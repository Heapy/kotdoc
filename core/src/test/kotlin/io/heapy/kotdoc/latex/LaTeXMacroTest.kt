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

class LaTeXMacroTest {
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
    private fun kernelParser(k: LaTeXKernel,v: JsonElement): LP<JsonElement> {
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
    private fun parser(k: LaTeXKernel,v: JsonElement): LP<JsonElement> {
        val a=v.jsonArray;val m=LaTeXMacros(k)
        return when(a[0].s()) {
            "definition"->m.macroDef({ listOf(pt(it)) },emptyList()).map { JsonArray(it) }
            "program"->{
                val definition=m.macroDef({ listOf<JsonElement>(buildJsonObject { put("definition",pt(it)) }) },emptyList())
                val token=(k.bgroup() or k.egroup() or k.anyTok()).map { listOf<JsonElement>(tv(it)) }
                (definition or token).many().map { JsonArray(it.flatten()) }
            }
            "specs"->m.xparseArgSpecs(a[1].b()).map { a(JsonArray(list(it.arguments).map { s->JsonPrimitive(s.show()) }),JsonPrimitive(it.usesBody)) }
            else->kernelParser(k,v)
        }
    }
    private fun deep(depth: Int,updates: Int): JsonElement {
        val context=PandocStateContext();val k=LaTeXKernel(context)
        val options=ReaderOptions().let { it.copy(extensions=it.extensions.enable(io.heapy.kotdoc.options.Extension.read("latex_macros"))) }
        var scopes=MacroScopes();repeat(depth) { scopes=scopes.push() }
        val initial=LaTeXState(options=options,macros=scopes)
        val text="\\global\\def\\a{G}".repeat(updates)+"}".repeat(depth)
        val p=parser(k,a(JsonPrimitive("program"))).flatMap { value->LaTeXParsing.state().map { value to it } }
        val result=p.run(TokenParseState(TokStream(false,LaTeXTokens.tokenize(SourcePos("deep"),text)),SourcePos("deep"),initial),LaTeXEnv())
        check(result.success) { result.error }
        val (value,st)=result.value!!;val rest=st.input as TokStream
        val hash=java.security.MessageDigest.getInstance("SHA-256").digest(value.toString().toByteArray()).joinToString("") { "%02x".format(it) }
        return buildJsonObject {
            put("count",value.jsonArray.size);put("hash",hash);put("state",sv(st.user));put("position",pv(st.position));put("expanded",rest.expanded);put("rest",tvs(rest.tokens))
            put("logs",JsonArray(context.getLog().map { Json.parseToJsonElement(PrettyJson.encode(Logging.toValue(it))) }))
        }
    }
    private fun observe(request: JsonArray): JsonElement = when(request[0].s()) {
        "parse"->run(request[1],request[2],request[3])
        "tokens"->tvs(LaTeXTokens.tokenize(pos(request[1]),raw(request[2])))
        "letterize"->pt(LaTeXMacros.letterize(raw(request[1])))
        "deep"->deep(request[1].jsonPrimitive.int,request[2].jsonPrimitive.int)
        "unicode"->{
            val md=java.security.MessageDigest.getInstance("SHA-256")
            for(cp in request[1].jsonPrimitive.int until request[1].jsonPrimitive.int+request[2].jsonPrimitive.int) {
                val bytes=pt(LaTeXMacros.letterize(TextChars.fromPoints(intArrayOf(cp)))).toString().toByteArray()
                md.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array());md.update(bytes)
            }
            JsonPrimitive(md.digest().joinToString("") { "%02x".format(it) })
        }
        else->error(request)
    }
    @TestFactory fun originalMacros(): List<DynamicTest> = GZIPInputStream(javaClass.getResourceAsStream("/latex-macros/data.jsonl.gz")!!).bufferedReader().use { reader ->
        reader.readLine()
        reader.lineSequence().mapIndexed { i,line->
            val c=Json.parseToJsonElement(line).jsonObject;val request=c.getValue("request").jsonArray
            DynamicTest.dynamicTest("${i+1}: ${c.getValue("group").s()} ${request.toString().take(180)}") {
                assertEquals(c.getValue("expected"),observe(request),request.toString())
            }
        }.toList()
    }
}
