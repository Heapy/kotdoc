package io.heapy.kotdoc.tex

import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.parsing.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.*

/** JSON transport only; expectations are from unchanged upstream TeX/Sources. */
class TeXTest {
    private fun JsonElement.s() = jsonPrimitive.content
    private fun JsonElement.n() = jsonPrimitive.long
    private fun JsonElement.b() = jsonPrimitive.boolean
    private fun text(v: JsonElement) = TextChars.fromPoints(v.jsonArray.map { it.jsonPrimitive.int }.toIntArray())
    private fun j(vararg values: JsonElement) = JsonArray(values.toList())
    private fun points(text: String) = JsonArray(text.codePoints().toArray().map(::JsonPrimitive))
    private fun pos(v: JsonElement) = v.jsonArray.let { SourcePos(it[0].s(),it[1].n(),it[2].n()) }
    private fun position(p: SourcePos) = j(JsonPrimitive(p.name),JsonPrimitive(p.line),JsonPrimitive(p.column))
    private fun type(v: JsonElement): TokType = v.jsonArray.let { a -> when(a[0].s()) {
        "CtrlSeq" -> TokType.CtrlSeq(text(a[1]))
        "Arg" -> TokType.Arg(a[1].n())
        "DeferredArg" -> TokType.DeferredArg(a[1].n())
        "Spaces" -> TokType.Spaces
        "Newline" -> TokType.Newline
        "Symbol" -> TokType.Symbol
        "Word" -> TokType.Word
        "Comment" -> TokType.Comment
        "Esc1" -> TokType.Esc1
        "Esc2" -> TokType.Esc2
        else -> error(v)
    } }
    private fun token(v: JsonElement) = v.jsonArray.let { Tok(pos(it[0]),type(it[1]),text(it[2])) }
    private fun tokens(v: JsonElement) = v.jsonArray.map(::token)
    private fun defaults(v: JsonElement) = if(v==JsonNull) null else tokens(v)
    private fun spec(v: JsonElement): ArgSpec = v.jsonArray.let { a -> when(a[0].s()) {
        "ArgNum" -> ArgSpec.ArgNum(a[1].n())
        "Pattern" -> ArgSpec.Pattern(tokens(a[1]))
        "BoolArg" -> ArgSpec.BoolArg(a[1].b(),token(a[2]))
        "DelimArg" -> ArgSpec.DelimArg(a[1].b(),token(a[2]),token(a[3]),defaults(a[4]))
        "VerbArg" -> ArgSpec.VerbArg
        "EmbellishArg" -> ArgSpec.EmbellishArg(a[1].jsonArray.map { it.jsonArray.let { e -> Embellishment(token(e[0]),defaults(e[1])) } })
        "ProcessedArg" -> ArgSpec.ProcessedArg(a[1].jsonArray.map(::tokens),spec(a[2]))
        "BodyArg" -> ArgSpec.BodyArg(a[1].b(),a[2].n())
        "VerbBodyArg" -> ArgSpec.VerbBodyArg(a[1].b(),a[2].n())
        else -> error(v)
    } }
    private fun macro(v: JsonElement) = v.jsonArray.let { a -> Macro(MacroScope.valueOf(a[0].s()),ExpansionPoint.valueOf(a[1].s()),a[2].jsonArray.map(::spec),defaults(a[3]),tokens(a[4])) }
    private fun value(kind: String,v: JsonElement): Any = when(kind) {
        "type" -> type(v)
        "token" -> token(v)
        "spec" -> spec(v)
        "macro" -> macro(v)
        "scope" -> MacroScope.valueOf(v.s())
        "expansion" -> ExpansionPoint.valueOf(v.s())
        else -> error(kind)
    }
    private fun <A:Comparable<A>> relations(a: A,b: A) = buildJsonObject {
        put("eq",a==b);put("ne",a!=b);put("compare",when { a.compareTo(b)<0 -> "LT";a.compareTo(b)>0 -> "GT";else -> "EQ" })
        put("lt",a<b);put("le",a<=b);put("gt",a>b);put("ge",a>=b)
    }
    private fun source(tokens: List<Tok>): JsonElement {
        val s=TeXSourceConversions.toSources(tokens)
        return buildJsonObject {
            put("chunks",JsonArray(s.chunks().map { j(position(it.position),points(it.text)) }))
            put("text",s.sourcesToText());put("name",s.initialSourceName());put("show",s.show())
        }
    }
    private fun partial(kind: String,count: Int): JsonElement {
        fun <A> bad(message: String): Deferred<A> = Deferred.later { error(message) }
        val p=SourcePos("first");val t=Tok(p,TokType.Word,"abc");val other=Tok(SourcePos("next"),TokType.Symbol,"!")
        fun lazyToken(position: Deferred<SourcePos>,type: Deferred<TokType>,text: Deferred<String>) = Tok(position,type,text)
        fun source(ts: TokenList) = TeXSourceConversions.toSources(ts)
        fun list(vararg ts: Tok) = tokenList(ts.toList())
        fun initial(ts: TokenList) = JsonPrimitive(source(ts).initialSourceName())
        fun shown(value: Any) = JsonPrimitive(TeXShow.prefix(value,count))
        return when(kind) {
            "type-tag-eq" -> JsonPrimitive(TokType.CtrlSeq(bad("unused text")) == TokType.Spaces)
            "type-tag-ord" -> relations<TokType>(TokType.CtrlSeq(bad("unused text")),TokType.Spaces)
            "type-arg-demand" -> JsonPrimitive(TokType.Arg(bad("demanded number")) == TokType.Arg(1))
            "type-self-demand" -> { val a=TokType.CtrlSeq(bad("demanded text"));JsonPrimitive(a==a) }
            "token-position-eq" -> JsonPrimitive(lazyToken(Deferred.now(SourcePos("a")),bad("unused type"),bad("unused text")) == lazyToken(Deferred.now(SourcePos("b")),bad("unused type"),bad("unused text")))
            "token-position-ord" -> relations(lazyToken(Deferred.now(SourcePos("a")),bad("unused type"),bad("unused text")),lazyToken(Deferred.now(SourcePos("b")),bad("unused type"),bad("unused text")))
            "token-type-eq" -> JsonPrimitive(lazyToken(Deferred.now(p),Deferred.now(TokType.Spaces),bad("unused text")) == lazyToken(Deferred.now(p),Deferred.now(TokType.Word),bad("unused text")))
            "token-type-ord" -> relations(lazyToken(Deferred.now(p),Deferred.now(TokType.Spaces),bad("unused text")),lazyToken(Deferred.now(p),Deferred.now(TokType.Word),bad("unused text")))
            "source-ignore-types" -> JsonPrimitive(source(list(lazyToken(Deferred.now(p),bad("unused type"),Deferred.now("a")),lazyToken(Deferred.now(p),bad("unused type"),Deferred.now("b")))).sourcesToText())
            "source-single-ignore-pos" -> JsonPrimitive(source(list(lazyToken(bad("unused pos"),bad("unused type"),Deferred.now("a")))).sourcesToText())
            "source-initial-ignore-tail" -> initial(Deferred.now(DeferredChain.Cons(Deferred.now(lazyToken(Deferred.now(p),bad("unused type"),bad("unused text"))),bad("unused tail"))))
            "source-initial-ignore-fields" -> initial(list(lazyToken(Deferred.now(p),bad("unused type"),bad("unused text"))))
            "source-count-ignore-text" -> JsonPrimitive(source(list(lazyToken(Deferred.now(p),bad("unused type"),bad("unused text")),other,lazyToken(Deferred.now(p),bad("unused type"),bad("unused text")))).chunks().size)
            "source-text-before-next-pos" -> JsonPrimitive(source(list(lazyToken(Deferred.now(p),Deferred.now(TokType.Word),bad("first text")),lazyToken(bad("next pos"),Deferred.now(TokType.Word),Deferred.now("b")))).sourcesToText())
            "source-two-text-before-third-pos" -> JsonPrimitive(source(list(lazyToken(Deferred.now(p),Deferred.now(TokType.Word),bad("first text")),Tok(p,TokType.Word,"b"),lazyToken(bad("third pos"),Deferred.now(TokType.Word),Deferred.now("c")))).sourcesToText())
            "source-single-text-before-tail" -> JsonPrimitive(source(Deferred.now(DeferredChain.Cons(Deferred.now(lazyToken(Deferred.now(p),Deferred.now(TokType.Word),bad("first text"))),bad("token tail")))).sourcesToText())
            "source-name-demand-token" -> initial(Deferred.now(DeferredChain.Cons(bad("demanded token"),Deferred.now(DeferredChain.Nil))))
            "prefix-type-text" -> shown(TokType.CtrlSeq(bad("demanded text")))
            "prefix-type-number" -> shown(TokType.Arg(bad("demanded number")))
            "prefix-token-fields" -> shown(lazyToken(bad("demanded pos"),bad("unused type"),bad("unused text")))
            "prefix-pattern-list" -> shown(ArgSpec.Pattern(bad("demanded list")))
            "prefix-pattern-head" -> shown(ArgSpec.Pattern(Deferred.now(DeferredChain.Cons(bad("demanded token"),Deferred.now(DeferredChain.Nil)))))
            "prefix-maybe" -> shown(Macro(Deferred.now(MacroScope.GlobalScope),Deferred.now(ExpansionPoint.ExpandWhenDefined),Deferred.now(DeferredChain.Nil),Deferred.now(TeXMaybe.Just(bad("demanded default"))),Deferred.now(DeferredChain.Nil)))
            "prefix-macro-scope" -> shown(Macro(bad("demanded scope"),bad("unused expansion"),bad("unused args"),bad("unused default"),bad("unused replacement")))
            "infinite-processed" -> {
                lateinit var argument: ArgSpec
                argument=ArgSpec.ProcessedArg(Deferred.now(DeferredChain.Nil),Deferred.later { argument })
                shown(argument)
            }
            "infinite-token-list", "infinite-source-name" -> {
                lateinit var chain: TokenList
                chain=Deferred.later { DeferredChain.Cons(Deferred.now(t),chain) }
                if(kind=="infinite-token-list") shown(ArgSpec.Pattern(chain)) else initial(chain)
            }
            "infinite-source-groups" -> {
                lateinit var a: TokenList;lateinit var b: TokenList
                a=Deferred.later { DeferredChain.Cons(Deferred.now(t),b) };b=Deferred.later { DeferredChain.Cons(Deferred.now(other),a) }
                val s=source(a);val result=mutableListOf<JsonElement>();var chain=s.unSources
                repeat(count) { index -> val c=chain.value() as DeferredChain.Cons;result.add(JsonPrimitive(c.head.value().position.name));if(index+1<count) chain=c.tail }
                JsonArray(result)
            }
            else -> error(kind)
        }
    }
    private fun operation(a: JsonArray): JsonElement = when(a[0].s()) {
        "show" -> JsonPrimitive(TeXShow.show(value(a[1].s(),a[3]),a[2].jsonPrimitive.int))
        "prefix" -> JsonPrimitive(TeXShow.prefix(value(a[1].s(),a[4]),a[3].jsonPrimitive.int,a[2].jsonPrimitive.int))
        "relations" -> when(a[1].s()) {
            "type" -> relations(type(a[2]),type(a[3]))
            "token" -> relations(token(a[2]),token(a[3]))
            "scope" -> relations(MacroScope.valueOf(a[2].s()),MacroScope.valueOf(a[3].s()))
            "expansion" -> relations(ExpansionPoint.valueOf(a[2].s()),ExpansionPoint.valueOf(a[3].s()))
            else -> error(a)
        }
        "sources" -> source(tokens(a[1]))
        "partial" -> partial(a[1].s(),a[2].jsonPrimitive.int)
        "plain" -> {
            val n=a[1].jsonPrimitive.int;val ft=a[2].jsonPrimitive.int;val fp=a[3].jsonPrimitive.int;val fs=a[4].jsonPrimitive.int
            fun chain(index: Int): Deferred<DeferredChain<SourceChunk>> = Deferred.later {
                if(index==fs) error("spine:$index")
                if(index==n) DeferredChain.Nil else DeferredChain.Cons(Deferred.later {
                    if(index==fp) error("pair:$index")
                    SourceChunk.deferred(Deferred.later { error("unused pos") },Deferred.later { if(index==ft) error("text:$index") else "x" })
                },chain(index+1))
            }
            JsonPrimitive(Sources(chain(0)).sourcesToText())
        }
        "group-text", "priority" -> {
            val n=a[1].jsonPrimitive.int
            val ft=if(a[0].s()=="priority") 0 else a[2].jsonPrimitive.int
            val fp=if(a[0].s()=="priority") a[2].jsonPrimitive.int else a[3].jsonPrimitive.int
            val s=TeXSourceConversions.toSources(List(n) { index -> Tok(Deferred.later { if(index==fp) error("position:$index") else SourcePos("same") },Deferred.now(TokType.Word),Deferred.later { if(index==ft) error(if(a[0].s()=="priority") "first text" else "text:$index") else "x" }) })
            if(a[0].s()=="priority") JsonPrimitive(s.sourcesToText()) else when(val chain=s.unSources.value()) {
                DeferredChain.Nil -> JsonNull
                is DeferredChain.Cons -> JsonPrimitive(chain.head.value().text)
            }
        }
        "deep" -> {
            var value: ArgSpec=ArgSpec.VerbArg
            repeat(a[1].jsonPrimitive.int) { value=ArgSpec.ProcessedArg(emptyList(),value) }
            JsonPrimitive(value.show())
        }
        else -> error(a)
    }
    @TestFactory fun originalObservations(): List<DynamicTest> =
        GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/tex/data.jsonl.gz"))).bufferedReader().use { reader ->
            reader.lineSequence().drop(1).mapIndexed { index,line -> DynamicTest.dynamicTest("original TeX data $index") {
                val c=Json.parseToJsonElement(line).jsonObject;val request=c.getValue("request").jsonArray
                val actual=try { operation(request) } catch(e:IllegalStateException) { buildJsonObject { put("exception",e.message.orEmpty().lineSequence().first()) } }
                assertEquals(c.getValue("expected"),actual,"case $index: ${request.toString().take(500)}")
            } }.toList()
        }
    @Test fun callerSnapshotsAndTypedDeferredFields() {
        val ts=mutableListOf(Tok(SourcePos("a"),TokType.Word,"first"))
        val frozen=tokenList(ts);ts.clear()
        assertEquals("first",TeXSourceConversions.tokens.toSources(frozen).sourcesToText())
        val macro=Macro(MacroScope.GlobalScope,ExpansionPoint.ExpandWhenUsed,emptyList(),null,emptyList())
        assertSame(MacroScope.GlobalScope,macro.scope);assertSame(ExpansionPoint.ExpandWhenUsed,macro.expansion)
        assertEquals("Macro GlobalScope ExpandWhenUsed [] Nothing []",macro.show())
        assertEquals(TokType.Arg(1),TokType.Arg(1));assertEquals(TokType.Arg(1).hashCode(),TokType.Arg(1).hashCode())
        val a=Tok(SourcePos("a"),TokType.CtrlSeq("x"),"text")
        val b=Tok(SourcePos("a"),TokType.CtrlSeq("x"),"text")
        assertEquals(a,b);assertEquals(a.hashCode(),b.hashCode())
    }
}
