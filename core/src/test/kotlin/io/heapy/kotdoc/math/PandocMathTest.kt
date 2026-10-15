package io.heapy.kotdoc.math

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.logging.*
import io.heapy.kotdoc.state.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.*

class PandocMathTest {
    private fun text(v: JsonElement)=TextChars.fromPoints(v.jsonArray.map {it.jsonPrimitive.int}.toIntArray())
    private fun cp(s: CharacterString)=JsonArray(s.points().map(::JsonPrimitive))
    private fun path(s: String)=CharacterStringChars.fromString(s)
    private fun log(xs: List<LogMessage>)=Json.parseToJsonElement(Logging.encodeLogMessagesText(xs))
    private fun state(s: CommonState)=buildJsonObject {
        put("log",log(s.log));put("userDataDir",s.userDataDir?.let(::cp) ?: JsonNull);put("sourceURL",s.sourceURL?.let(::JsonPrimitive) ?: JsonNull)
        put("headers",JsonArray(s.requestHeaders.map {(k,v)->buildJsonArray {add(k);add(v)}}));put("noCheckCertificate",s.noCheckCertificate);put("mediaBag",s.mediaBag.show())
        put("translations",s.translations?.let {(l,t)->buildJsonArray {add(l.show());add(t?.show()?.let(::JsonPrimitive) ?: JsonNull)}} ?: JsonNull)
        put("inputFiles",JsonArray(s.inputFiles.map(::cp)));put("outputFile",s.outputFile?.let(::cp) ?: JsonNull);put("resourcePath",JsonArray(s.resourcePath.map(::cp)))
        put("manager",JsonNull);put("verbosity",s.verbosity.name);put("trace",s.trace)
    }
    private fun run(v: JsonArray): JsonElement {
        if(v[0].jsonPrimitive.content=="urls") return buildJsonArray {add(PandocMath.defaultMathJaxURL);add(PandocMath.defaultKaTeXURL)}
        val events=mutableListOf<JsonElement>();val callback=v[5].jsonPrimitive.int
        val context=PandocStateContext(CommonState(log=if(v[6].jsonPrimitive.boolean) listOf(LogMessage.NoLangSpecified) else emptyList(),userDataDir=path("seed-dir"),sourceURL="seed-source",requestHeaders=listOf("x-seed" to "value"),noCheckCertificate=true,inputFiles=listOf(path("seed-in")),outputFile=path("seed-out"),resourcePath=listOf(path("seed-resource")),verbosity=Verbosity.entries[v[4].jsonPrimitive.int],trace=true),output={c,msg->
            events.add(buildJsonArray {add("output");add(log(listOf(msg)).jsonArray.single());add(state(c.getCommonState()))})
            when(callback) {
                1->{c.modifyCommonState {it.copy(userDataDir=path("callback"),log=listOf(LogMessage.NoLangSpecified)+it.log)};c.setVerbosity(Verbosity.ERROR)}
                2->{c.setVerbosity(Verbosity.ERROR);c.report(LogMessage.ScriptingInfo("nested",null));c.setUserDataDir(path("nested-callback"))}
                3->throw PandocError.PandocSomeError("callback-error")
                4->throw IllegalStateException("host-error")
            }
        })
        val type=MathType.valueOf(v[2].jsonPrimitive.content);val input=text(v[3]);val kind=v[8].jsonPrimitive.content
        val writer:(DisplayType,List<MathExp>)->JsonElement={dt,es->
            events.add(buildJsonArray {add("writer");add(dt.name);add(MathShow.show(es));add(state(context.getCommonState()))})
            when(kind) {
                "probe"->buildJsonObject {put("display",dt.name);put("expressions",MathShow.show(es))}
                "pandoc"->MathPandocWriter.writePandoc(dt,es)?.let {JsonArray(it.map(PandocJson::encodeInline))} ?: JsonNull
                "nothing"->JsonNull
                "constant"->JsonPrimitive("constant")
                "throw"->throw IllegalStateException("writer-error")
                else->error("kind")
            }
        }
        var value: JsonElement=JsonNull
        val status=try {
            if(v[1].jsonPrimitive.content=="inline") {
                val ils=PandocMath.texMathToInlines(context,type,input)
                value=buildJsonArray {add("Inlines");add(JsonArray(ils.map(PandocJson::encodeInline)));add(AstShow.show(ils))}
            } else {
                val result=PandocMath.convertMath(context,writer,type,input)
                fun force(): JsonElement=when(result) {
                    is MathConversion.Fallback->buildJsonArray {add("Left");add(PandocJson.encodeInline(result.inline));add(AstShow.show(result.inline))}
                    is MathConversion.Converted->if(v[7].jsonPrimitive.boolean) buildJsonArray {add("Right");add(result.value)} else buildJsonArray {add("Right-undemanded")}
                }
                try {value=force()} finally {
                    // Original observer demands the same returned thunk twice, including failures.
                    try {force()} catch(_: IllegalStateException) { } catch(_: ArithmeticException) { }
                }
            }
            "success"
        } catch(e: PandocError) {e.toString()} catch(_: IllegalStateException) {"host"} catch(_: ArithmeticException) {"host"}
        return buildJsonObject {put("status",status);put("value",value);put("state",state(context.getCommonState()));put("events",JsonArray(events));put("chronology",log(context.getLog()))}
    }
    @TestFactory
    fun originalPandocMathObservations(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/pandoc-math/data.jsonl.gz"))).bufferedReader().use {r->
        r.readLine();r.lineSequence().mapIndexed {i,line->var transport: String?=line
            DynamicTest.dynamicTest("Pandoc math ${i+1}") {
                val c=Json.parseToJsonElement(checkNotNull(transport)).jsonObject;transport=null
                assertLogJsonEquals(c.getValue("expected"),run(c.getValue("request").jsonArray))
            }
        }.toList()
    }
    @Test
    fun deferredGenericResultsKeepIdentityAndNullableValues() {
        val context=PandocStateContext();val identity=Any();var calls=0
        val r=assertIs<MathConversion.Converted<Any>>(PandocMath.convertMath(context,{_,_->calls++;identity},MathType.InlineMath,"x"))
        assertEquals(0,calls);assertSame(identity,r.value);assertSame(identity,r.value);assertEquals(1,calls)
        val absent=assertIs<MathConversion.Converted<Any?>>(PandocMath.convertMath(context,{_,_->null},MathType.DisplayMath,"x"));assertNull(absent.value);assertTrue(context.getLog().isEmpty())
        val failure=IllegalStateException("identity");var failures=0
        val thrown=assertIs<MathConversion.Converted<Nothing>>(PandocMath.convertMath(context,{_,_->failures++;throw failure},MathType.InlineMath,"x"))
        repeat(2) {assertSame(failure,assertFailsWith<IllegalStateException> {thrown.value})};assertEquals(1,failures)
        val fallback=PandocMath.texMathToInlines(context,MathType.DisplayMath,"\\sqrt{x}")
        assertEquals(listOf(Inline.Str("$$\\sqrt{x}$$")),fallback);assertFailsWith<UnsupportedOperationException> {(fallback as MutableList).clear()}
    }
}
