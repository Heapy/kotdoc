package io.heapy.kotdoc.latex

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.json.PrettyJson
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.logging.Logging
import io.heapy.kotdoc.options.ReaderOptions
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.tex.*
import io.heapy.kotdoc.tex.SourcePos
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/** Expectations come from the complete unchanged source grammar and instances. */
class LaTeXParsingTest {
    private fun JsonElement.s() = jsonPrimitive.content
    private fun JsonElement.n() = jsonPrimitive.long
    private fun JsonElement.b() = jsonPrimitive.boolean
    private fun a(vararg values: JsonElement) = JsonArray(values.toList())
    private fun raw(v: JsonElement) = TextChars.fromPoints(v.jsonArray.map { it.jsonPrimitive.int }.toIntArray())
    private fun pt(s: String) = JsonArray(s.codePoints().toArray().map(::JsonPrimitive))
    private fun pos(v: JsonElement): SourcePos = v.jsonArray.let {
        SourcePos(CharacterString(it[0].jsonArray.map { p -> p.jsonPrimitive.int }.toIntArray()), it[1].n(), it[2].n())
    }
    private fun pv(p: SourcePos) = a(JsonArray(p.nameRaw.points().map(::JsonPrimitive)), JsonPrimitive(p.line), JsonPrimitive(p.column))
    private fun tt(v: JsonElement): TokType = v.jsonArray.let { when (it[0].s()) {
        "CtrlSeq" -> TokType.CtrlSeq(raw(it[1])); "Arg" -> TokType.Arg(it[1].n()); "DeferredArg" -> TokType.DeferredArg(it[1].n())
        "Spaces" -> TokType.Spaces; "Newline" -> TokType.Newline; "Symbol" -> TokType.Symbol; "Word" -> TokType.Word
        "Comment" -> TokType.Comment; "Esc1" -> TokType.Esc1; "Esc2" -> TokType.Esc2; else -> error(it)
    } }
    private fun ttv(t: TokType): JsonElement = when (t) {
        is TokType.CtrlSeq -> a(JsonPrimitive("CtrlSeq"), pt(t.text))
        is TokType.Arg -> a(JsonPrimitive("Arg"), JsonPrimitive(t.number))
        is TokType.DeferredArg -> a(JsonPrimitive("DeferredArg"), JsonPrimitive(t.number))
        else -> a(JsonPrimitive(t.show()))
    }
    private fun tok(v: JsonElement) = v.jsonArray.let { Tok(pos(it[0]), tt(it[1]), raw(it[2])) }
    private fun tv(t: Tok) = a(pv(t.position), ttv(t.type), pt(t.text))
    private fun tokens(v: JsonElement) = tokenList(v.jsonArray.map(::tok))
    private fun <A> list(xs: Deferred<DeferredChain<A>>): List<A> {
        var current = xs.value(); val out = ArrayList<A>()
        while (current is DeferredChain.Cons) { out.add(current.head.value()); current = current.tail.value() }
        return out
    }
    private fun tvs(xs: TokenList) = JsonArray(list(xs).map(::tv))
    private fun sources(v: JsonElement) = Sources(v.jsonArray.map { it.jsonArray.let { c -> SourceChunk(pos(c[0]), raw(c[1])) } })
    private fun quote(v: JsonElement) = QuoteContext.entries[v.jsonPrimitive.int]
    private fun theorem(v: JsonElement) = v.jsonArray.let {
        TheoremSpec(tokens(it[0]), TheoremStyle.entries[it[1].jsonPrimitive.int], it[2].takeUnless { x -> x == JsonNull }?.s(),
            it[3].takeUnless { x -> x == JsonNull }?.s(), it[4].b(), DottedNum(it[5].jsonArray.map { n -> n.n() }))
    }
    private fun thv(t: TheoremSpec) = a(tvs(t.name), JsonPrimitive(t.style.name), t.series?.let(::JsonPrimitive) ?: JsonNull,
        t.syncTo?.let(::JsonPrimitive) ?: JsonNull, JsonPrimitive(t.number), JsonPrimitive(t.lastNum.render()))
    private fun macro(k: String, n: Long): Macro {
        val token = Tok(SourcePos("macro", -1, 0), TokType.CtrlSeq(k), k)
        return Macro(MacroScope.GlobalScope, ExpansionPoint.ExpandWhenUsed, listOf(ArgSpec.ArgNum(n), ArgSpec.Pattern(listOf(token))), emptyList(), listOf(token))
    }
    private fun lv(log: LogMessage) = Json.parseToJsonElement(PrettyJson.encode(Logging.toValue(log)))
    private fun <V> pairs(m: Map<String,V>, value: (V) -> JsonElement) = JsonArray(m.keys.sortedWith(UnicodeTextOrder).map { a(JsonPrimitive(it), value(m.getValue(it))) })
    private fun sv(s: LaTeXState) = buildJsonObject {
        put("options", s.options.toJson()); put("meta", PandocJson.encodeMeta(s.meta)); put("quoteContext", s.quoteContext.name)
        put("macros", JsonArray((listOf(s.macros.head) + s.macros.tail).map { pairs(it) { v -> JsonPrimitive(v.show()) } }))
        put("containers", JsonArray(s.containers.map(::JsonPrimitive))); put("logMessages", JsonArray(s.logMessages.map(::lv)))
        put("identifiers", JsonArray(s.identifiers.sortedWith(UnicodeTextOrder).map(::JsonPrimitive)))
        put("verbatimMode", s.verbatimMode); put("mathMode", s.mathMode); put("caption", s.caption?.let(PandocJson::encodeCaption) ?: JsonNull)
        put("inListItem", s.inListItem); put("inTableCell", s.inTableCell)
        put("lastHeaderNum", s.lastHeaderNum.render()); put("lastFigureNum", s.lastFigureNum.render()); put("lastTableNum", s.lastTableNum.render()); put("lastNoteNum", s.lastNoteNum)
        put("footnoteTexts", JsonArray(s.footnoteTexts.keys.sorted().map { a(JsonPrimitive(it), JsonArray(s.footnoteTexts.getValue(it).values.map(PandocJson::encodeBlock))) }))
        put("theoremMap", pairs(s.theoremMap, ::thv)); put("lastTheoremStyle", s.lastTheoremStyle.name); put("lastLabel", s.lastLabel?.let(::JsonPrimitive) ?: JsonNull)
        put("labels", pairs(s.labels) { JsonArray(it.map(PandocJson::encodeInline)) }); put("hasChapters", s.hasChapters)
        put("toggles", pairs(s.toggles, ::JsonPrimitive)); put("fileContents", pairs(s.fileContents, ::JsonPrimitive))
        put("enableWithRaw", s.enableWithRaw); put("rawTokens", tvs(s.rawTokens)); put("rawTokenCount", s.rawTokenCount); put("rawScopes", s.rawScopes)
        put("ligatures", s.ligatures); put("caseExclusions", pairs(s.caseExclusions) { JsonArray(it.sortedWith(UnicodeTextOrder).map(::JsonPrimitive)) })
        put("reportedLogs", JsonArray(LaTeXStateAccess.getLogMessages(s).map(::lv)))
    }
    private fun change(s: LaTeXState, value: JsonElement): LaTeXState {
        val v = value.jsonArray
        return when (v[0].s()) {
            "options" -> s.copy(options = ReaderOptions.fromJson(v[1]))
            "meta" -> s.setMeta(v[1].s(), PandocJson.decodeMetaValue(v[2])); "delete-meta" -> s.deleteMeta(v[1].s())
            "quote" -> s.copy(quoteContext = quote(v[1]))
            "macro" -> LaTeXStateAccess.updateMacros({ it + (v[1].s() to macro(v[1].s(),v[2].n())) }, s)
            "push-macros" -> s.copy(macros = s.macros.push()); "pop-macros" -> s.copy(macros = s.macros.pop())
            "clear-macros" -> LaTeXStateAccess.updateMacros({ emptyMap() }, s)
            "include" -> LaTeXStateAccess.addIncludeFile(v[1].s(), s); "drop-include" -> LaTeXStateAccess.dropLatestIncludeFile(s)
            "log" -> LaTeXStateAccess.addLogMessage(LogMessage.SkippedContent(v[1].s(), SourcePos("log", 2, 3)), s)
            "identifier" -> LaTeXStateAccess.updateIdentifierList({ it + v[1].s() }, s)
            "verbatim" -> s.copy(verbatimMode = v[1].b()); "math" -> s.copy(mathMode = v[1].b())
            "caption" -> s.copy(caption = v[1].takeUnless { it == JsonNull }?.let(PandocJson::decodeCaption))
            "list-item" -> s.copy(inListItem = v[1].b()); "table-cell" -> s.copy(inTableCell = v[1].b())
            "header" -> s.copy(lastHeaderNum = DottedNum(v[1].jsonArray.map { it.n() }))
            "figure" -> s.copy(lastFigureNum = DottedNum(v[1].jsonArray.map { it.n() }))
            "table" -> s.copy(lastTableNum = DottedNum(v[1].jsonArray.map { it.n() }))
            "note-num" -> s.copy(lastNoteNum = v[1].n())
            "footnote" -> s.copy(footnoteTexts = s.footnoteTexts + (v[1].n() to Blocks(v[2].jsonArray.map(PandocJson::decodeBlock))))
            "theorem" -> s.copy(theoremMap = s.theoremMap + (v[1].s() to theorem(v[2])))
            "theorem-style" -> s.copy(lastTheoremStyle = TheoremStyle.entries[v[1].jsonPrimitive.int])
            "last-label" -> s.copy(lastLabel = v[1].takeUnless { it == JsonNull }?.s())
            "label" -> s.copy(labels = s.labels + (v[1].s() to v[2].jsonArray.map(PandocJson::decodeInline)))
            "chapters" -> s.copy(hasChapters = v[1].b()); "toggle" -> s.copy(toggles = s.toggles + (v[1].s() to v[2].b()))
            "file" -> s.copy(fileContents = s.fileContents + (v[1].s() to v[2].s()))
            "enable-raw" -> s.copy(enableWithRaw = v[1].b()); "raw" -> s.copy(rawTokens = tokens(v[1]), rawTokenCount = v[2].n(), rawScopes = v[3].n())
            "ligatures" -> s.copy(ligatures = v[1].b()); "exclusion" -> s.copy(caseExclusions = s.caseExclusions + (v[1].s() to v[2].jsonArray.map { it.s() }.toSet()))
            else -> error(v)
        }
    }
    private fun state(v: JsonElement) = v.jsonArray.fold(LaTeXState(), ::change)
    private fun parser(v: JsonElement): LP<JsonElement> {
        val a = v.jsonArray
        fun sub(i: Int) = parser(a[i])
        return when (a[0].s()) {
            "state" -> LaTeXParsing.state().map { sv(it.user) }
            "pure" -> ReaderTokenParsec.pure(a[1]); "fail" -> ReaderTokenParsec.fail("failure")
            "then" -> sub(1).then(sub(2)); "pair" -> sub(1).flatMap { x -> sub(2).map { y -> a(x,y) } }
            "or" -> sub(1) or sub(2); "try" -> sub(1).attempt(); "look" -> sub(1).lookAhead()
            "verbatim" -> LaTeXParsing.withVerbatimMode(sub(1)); "disable" -> LaTeXParsing.disablingWithRaw(sub(1))
            "raw" -> LaTeXParsing.withRaw(sub(1)).map { a(it.first, tvs(it.second)) }
            "from" -> LaTeXParsing.parseFromToks(sub(2), tokens(a[1]))
            "change" -> LaTeXParsing.update { change(it, a[1]) }.map { JsonNull }
            "number" -> LaTeXParsing.getNextNumber { when (a[1].jsonPrimitive.int) { 0->it.lastHeaderNum;1->it.lastFigureNum;else->it.lastTableNum } }.map { JsonPrimitive(it.render()) }
            "reset-caption" -> LaTeXParsing.resetCaption().map { JsonNull }
            "add-meta" -> LaTeXParsing.addMeta(a[1].s(), PandocJson.decodeMetaValue(a[2])).map { JsonNull }
            "quote" -> ReaderTokenParsec.lift(LaTeXStateAccess.tokenQuotes().withQuoteContext(quote(a[1]), sub(2).runReader(Deferred.now(LaTeXEnv()))))
            "consume" -> {
                var p: LP<List<Tok>> = ReaderTokenParsec.pure(emptyList())
                repeat(a[1].jsonPrimitive.int) { p = p.flatMap { xs -> ReaderTokenParsec.lift<LaTeXEnv,Tok,LaTeXState,Tok>(TokenParsec.anyToken { it.text }).map { xs + it } } }
                p.map { JsonArray(it.map(::tv)) }
            }
            "mark" -> LaTeXParsing.update { st -> st.copy(rawTokens = Deferred.now(DeferredChain.Cons(Deferred.now(tok(a[1])), st.rawTokens)), rawTokenCount = st.rawTokenCount + 1) }.map { JsonNull }
            else -> error(a)
        }
    }
    private fun run(changes: JsonElement, ts: JsonElement, expr: JsonElement): JsonElement {
        val p = parser(expr).flatMap { value -> LaTeXParsing.state().map { value to it } }
        val r = p.run(TokenParseState(TokStream(false,tokens(ts)), SourcePos("chunk"), state(changes)), LaTeXEnv())
        if (!r.success) return buildJsonObject { put("ok",false);put("error",r.error.toString()) }
        val (value, st) = r.value!!
        return buildJsonObject { put("ok",true);put("value",value);put("state",sv(st.user));put("position",pv(st.position))
            val input = st.input as TokStream;put("expanded",input.expanded);put("rest",tvs(input.tokens)) }
    }
    private fun unicode(start: Int, count: Int): JsonElement {
        val digest = MessageDigest.getInstance("SHA-256"); val p = SourcePos("unicode",2,3)
        for (cp in start..<start + count) {
            val s = TextChars.fromPoints(intArrayOf(cp)); val ts = LaTeXTokens.tokenize(p,s)
            val data = a(tvs(ts),tvs(LaTeXTokens.tokenize(p,"\\$s")),pt(LaTeXTokens.untokenize(ts))).toString().toByteArray()
            digest.update(byteArrayOf((data.size ushr 24).toByte(),(data.size ushr 16).toByte(),(data.size ushr 8).toByte(),data.size.toByte()));digest.update(data)
        }
        return JsonPrimitive(digest.digest().toHexString())
    }
    private fun demand(kind: Int): JsonElement {
        fun <A> bad(name: String) = Deferred.later<A> { error(name) }
        val p = SourcePos("demand"); val empty = Deferred.now(DeferredChain.Nil)
        return when (kind) {
            0 -> pt(LaTeXTokens.untoken(LaTeXTokens.tokenize(bad("bad-position"),Deferred.now("abc")).value().let { (it as DeferredChain.Cons).head.value() }))
            1 -> tvs(LaTeXTokens.tokenize(bad("bad-position"),Deferred.now("")))
            2 -> tvs(LaTeXTokens.tokenizeSources(Sources(listOf(SourceChunk.deferred(bad("bad-position"),Deferred.now("")),SourceChunk(p,"x")))))
            3 -> pt(LaTeXTokens.untokenize(listOf(Tok(Deferred.now(p),bad("bad-type"),Deferred.now("x")))))
            4 -> pt(LaTeXTokens.untokenize(Deferred.now(DeferredChain.Cons(Deferred.now(Tok(Deferred.now(p),Deferred.now(TokType.Word),bad("bad-text"))),bad("bad-tail")))))
            5 -> pt(LaTeXTokens.untokenize(listOf(Tok(p,TokType.CtrlSeq("x"),"\\x"),Tok(Deferred.now(p),bad("bad-type"),Deferred.now("y")))))
            6 -> JsonPrimitive(TokStream(false,Deferred.now(DeferredChain.Cons(bad("bad-head"),empty))).unconsLazy()!=null)
            7 -> JsonPrimitive((TokStream(true,Deferred.now(DeferredChain.Cons(bad("bad-head"),empty)))+TokStream.empty).expanded)
            8 -> pt(LaTeXTokens.untokenize(listOf(Tok(bad("bad-position"),Deferred.now(TokType.Word),Deferred.now("x")),Tok(bad("bad-position"),Deferred.now(TokType.Symbol),Deferred.now("y")))))
            else -> error(kind)
        }
    }
    private fun observe(v: JsonElement): JsonElement {
        val x = v.jsonArray
        return when (x[0].s()) {
            "tokens" -> { val ts = LaTeXTokens.tokenize(pos(x[1]),raw(x[2]));a(tvs(ts),pt(LaTeXTokens.untokenize(ts))) }
            "sources" -> { val ts = LaTeXTokens.tokenizeSources(sources(x[1]));a(tvs(ts),pt(LaTeXTokens.untokenize(ts))) }
            "get-input" -> { val r = LaTeXTokens.getInputTokens<Unit>().run(ParseState(ParseInput(stream=sources(x[2])),pos(x[1]),Unit));tvs(r.value!!) }
            "untokenize" -> pt(LaTeXTokens.untokenize(tokens(x[1])))
            "stream" -> {
                var stream = TokStream(x[1].b(),tokens(x[2]))+TokStream(x[3].b(),tokens(x[4]))
                fun snapshot(s: TokStream) = a(JsonPrimitive(s.expanded),tvs(s.tokens))
                val initial = snapshot(stream);val tails = buildJsonArray { while (true) { val step=stream.unconsLazy() ?: break;stream=step.tail;add(snapshot(stream)) } }
                a(initial,tails)
            }
            "dotted" -> { val d=DottedNum(x[1].jsonArray.map { it.n() });val i=d.increment(x[2].n());a(JsonPrimitive(d.render()),JsonPrimitive(i.render()),JsonPrimitive(d.show(x[3].jsonPrimitive.int)),JsonPrimitive(i==d)) }
            "theorem" -> JsonPrimitive(theorem(x[1])==theorem(x[2]))
            "state" -> sv(state(x[1])); "parse" -> run(x[1],x[2],x[3]); "unicode" -> unicode(x[1].jsonPrimitive.int,x[2].jsonPrimitive.int)
            "demand" -> try { demand(x[1].jsonPrimitive.int) } catch (e: IllegalStateException) { buildJsonObject { put("error",e.message) } }
            "macro-stack" -> {
                val n=x[1].jsonPrimitive.int
                val original=LaTeXStateAccess.updateMacros({ it+("outer" to macro("outer",1)) },LaTeXState())
                var nested=original
                repeat(n) { nested=nested.copy(macros=nested.macros.push()) }
                var popped=LaTeXStateAccess.updateMacros({ it+("inner" to macro("inner",2)) },nested)
                val keys=JsonArray(popped.macros.head.keys.sortedWith(UnicodeTextOrder).map(::JsonPrimitive))
                repeat(n+1) { popped=popped.copy(macros=popped.macros.pop()) }
                a(JsonPrimitive(nested.macros.tail.size+1),keys,sv(popped))
            }
            "deep-scopes" -> {
                var p: LP<JsonElement> = ReaderTokenParsec.pure(JsonNull)
                repeat(x[1].jsonPrimitive.int) { p=LaTeXParsing.withRaw(p).map { it.first } }
                val r=p.flatMap { v->LaTeXParsing.state().map { a(v,sv(it.user)) } }.run(TokenParseState(TokStream.empty,SourcePos("deep"),LaTeXState()),LaTeXEnv())
                assertTrue(r.success);r.value!!
            }
            "stress" -> {
                val n=x[2].jsonPrimitive.int;val p=SourcePos("stress")
                val ts=when (x[1].jsonPrimitive.int) {
                    0->LaTeXTokens.tokenize(p,"x ".repeat(n))
                    1->LaTeXTokens.tokenize(p,"\\makeatletter\\x@y\\makeatother\\x@y".repeat(n))
                    2->LaTeXTokens.tokenizeSources(Sources(List(n) { SourceChunk(p,"") }+SourceChunk(p,"x")))
                    3->{var stream=TokStream.empty;repeat(n) { stream=TokStream(true,listOf(Tok(p,TokType.Word,"x")))+stream };stream.tokens}
                    4->tokenList(List(n) { listOf(Tok(p,TokType.CtrlSeq("a"),"\\a"),Tok(p,TokType.Word,""),Tok(p,TokType.Word,"b")) }.flatten())
                    else->error(x)
                }
                val all=list(ts);val t=LaTeXTokens.untokenize(ts)
                a(JsonPrimitive(all.size),all.lastOrNull()?.let(::tv) ?: JsonNull,JsonPrimitive(t.codePointCount(0,t.length)),JsonPrimitive(MessageDigest.getInstance("SHA-256").digest(t.toByteArray()).toHexString()))
            }
            else -> error(x)
        }
    }
    @TestFactory
    fun originalObservations(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/latex-parsing/data.jsonl.gz")))
        .bufferedReader().useLines { lines -> lines.drop(1).mapIndexed { i,line ->
            val c=Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest("$i ${c.getValue("group").s()}") { assertEquals(c.getValue("expected"),observe(c.getValue("request")),c.getValue("request").toString().take(300)) }
        }.toList() }
}
