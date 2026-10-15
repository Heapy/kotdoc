package io.heapy.kotdoc.math

import io.heapy.kotdoc.ast.AstShow
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.logging.assertLogJsonEquals
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.util.zip.GZIPInputStream
import kotlin.test.*

class MathTest {
    private fun str(v: JsonElement)=if(v is JsonArray) TextChars.fromPoints(v.map {it.jsonPrimitive.int}.toIntArray()) else v.jsonPrimitive.content
    private fun ratio(v: JsonElement)=v.jsonArray.let {MathRational(BigInteger(str(it[0])),BigInteger(str(it[1])))}
    private fun expression(v: JsonElement): MathExp {
        val xs=v.jsonArray
        fun e(i: Int)=expression(xs[i])
        fun s(i: Int)=str(xs[i])
        fun list(i: Int)=xs[i].jsonArray.map(::expression)
        return when(s(0)) {
            "ENumber" -> MathExp.ENumber(s(1))
            "EGrouped" -> MathExp.EGrouped(list(1))
            "EDelimited" -> MathExp.EDelimited(s(1),s(2),xs[3].jsonArray.map { it.jsonArray.let { d->if(str(d[0])=="Left") DelimitedPart.Middle(str(d[1])) else DelimitedPart.Expression(expression(d[1])) } })
            "EIdentifier" -> MathExp.EIdentifier(s(1))
            "EMathOperator" -> MathExp.EMathOperator(s(1))
            "ESymbol" -> MathExp.ESymbol(MathSymbolType.valueOf(s(1)),s(2))
            "ESpace" -> MathExp.ESpace(ratio(xs[1]))
            "ESub" -> MathExp.ESub(e(1),e(2))
            "ESuper" -> MathExp.ESuper(e(1),e(2))
            "ESubsup" -> MathExp.ESubsup(e(1),e(2),e(3))
            "EOver" -> MathExp.EOver(xs[1].jsonPrimitive.boolean,e(2),e(3))
            "EUnder" -> MathExp.EUnder(xs[1].jsonPrimitive.boolean,e(2),e(3))
            "EUnderover" -> MathExp.EUnderover(xs[1].jsonPrimitive.boolean,e(2),e(3),e(4))
            "EPhantom" -> MathExp.EPhantom(e(1))
            "EBoxed" -> MathExp.EBoxed(e(1))
            "ECancel" -> MathExp.ECancel(StrokeType.valueOf(s(1)),e(2))
            "EFraction" -> MathExp.EFraction(FractionType.valueOf(s(1)),e(2),e(3))
            "ERoot" -> MathExp.ERoot(e(1),e(2))
            "ESqrt" -> MathExp.ESqrt(e(1))
            "EScaled" -> MathExp.EScaled(ratio(xs[1]),e(2))
            "EArray" -> MathExp.EArray(xs[1].jsonArray.map {MathAlignment.valueOf(str(it))},xs[2].jsonArray.map {row->row.jsonArray.map {cell->cell.jsonArray.map(::expression)}})
            "EText" -> MathExp.EText(MathTextType.valueOf(s(1)),s(2))
            "EStyled" -> MathExp.EStyled(MathTextType.valueOf(s(1)),list(2))
            else->error("expression")
        }
    }
    private fun wire(v: Any?): JsonElement=when(v) {
        null -> JsonNull
        is MathExp -> JsonArray(listOf(JsonPrimitive(v.javaClass.simpleName))+v.fields().map(::wire))
        is DelimitedPart.Middle -> buildJsonArray {add("Left");add(v.text)}
        is DelimitedPart.Expression -> buildJsonArray {add("Right");add(wire(v.expression))}
        is MathRational -> buildJsonArray {add(v.numerator.toString());add(v.denominator.toString())}
        is Pair<*,*> -> JsonArray(listOf(wire(v.first),wire(v.second)))
        is Triple<*,*,*> -> JsonArray(listOf(wire(v.first),wire(v.second),wire(v.third)))
        is List<*> -> JsonArray(v.map(::wire))
        is String -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        is Int -> JsonPrimitive(v)
        is Enum<*> -> JsonPrimitive(v.name)
        is MathTeX -> when(v) {
            is MathTeX.ControlSeq -> buildJsonArray {add("ControlSeq");add(v.text)}
            is MathTeX.Literal -> buildJsonArray {add("Literal");add(v.text)}
            is MathTeX.Token -> buildJsonArray {add("Token");add(v.character)}
            is MathTeX.Grouped -> buildJsonArray {add("Grouped");add(wire(v.items))}
            MathTeX.Space -> buildJsonArray {add("Space")}
        }
        else -> error("wire $v")
    }
    private fun tex(v: JsonElement): MathTeX {
        val xs=v.jsonArray
        return when(str(xs[0])) {
            "ControlSeq"->MathTeX.ControlSeq(str(xs[1]))
            "Literal"->MathTeX.Literal(str(xs[1]))
            "Token"->MathTeX.Token(xs[1].jsonPrimitive.int)
            "Grouped"->MathTeX.Grouped(xs[1].jsonArray.map(::tex))
            "Space"->MathTeX.Space
            else->error("tex")
        }
    }
    private fun rewrite(e: MathExp): MathExp=when(e) {
        is MathExp.ENumber->MathExp.ENumber(e.text+"!")
        is MathExp.EGrouped->MathExp.EStyled(MathTextType.TextBold,e.expressions)
        is MathExp.ESpace->MathExp.EIdentifier("s")
        else->e
    }
    private fun rewriteList(xs: List<MathExp>): List<MathExp> = when {
        xs.isEmpty()->listOf(MathExp.ENumber("z"))
        xs.size>=2 && xs[0] is MathExp.ENumber && xs[1] is MathExp.ENumber->listOf(MathExp.ENumber((xs[0] as MathExp.ENumber).text+(xs[1] as MathExp.ENumber).text))+xs.drop(2)
        else->xs
    }
    private fun run(v: JsonArray): JsonElement=when(str(v[0])) {
        "expressions" -> {
            val es=v[1].jsonArray.map(::expression)
            buildJsonObject {
                put("show",MathShow.show(es));put("show11",wire(es.map {it.show(11)}))
                put("fixed",wire(es.map(MathShared::fixTree)));put("walk",wire(es.map {MathShared.everywhereExp(::rewrite,it)}))
                put("lists",wire(MathShared.everywhereExpList(::rewriteList,es)))
                put("downInline",wire(es.map {MathShared.handleDownup(DisplayType.DisplayInline,it)}));put("downBlock",wire(es.map {MathShared.handleDownup(DisplayType.DisplayBlock,it)}))
                put("pandoc",wire(MathPandocWriter.writePandoc(DisplayType.DisplayInline,es)?.let {AstShow.show(it)}))
                put("pandocBlock",wire(MathPandocWriter.writePandoc(DisplayType.DisplayBlock,es)?.let {AstShow.show(it)}))
                put("primes",wire(es.map(MathShared::toPrimes)));put("operators",wire(es.map(MathShared::getOperator)))
                put("sorted",wire(es.sorted()));put("ordering",wire(es.take(12).flatMap {a->es.take(12).map {b->when {a<b->"LT";a>b->"GT";else->"EQ"}}}))
            }
        }
        "shared" -> {
            val rs=v[1].jsonArray.map(::ratio);val ts=v[2].jsonArray.map(::str);val cs=v[3].jsonArray.map {it.jsonPrimitive.int}
            val als=v[4].jsonArray.map {xs->xs.jsonArray.map {MathAlignment.valueOf(str(it))}}
            buildJsonObject {
                put("widths",wire(cs.map(MathShared::getSpaceWidth)));put("spaces",wire(rs.map(MathShared::getSpaceChars)));put("scalerCommands",wire(rs.map(MathShared::getScalerCommand)))
                put("scalerValues",wire(ts.map(MathShared::getScalerValue)));put("lengths",wire(ts.map(MathShared::readLength)));put("textTypes",wire(ts.map(MathShared::getTextType)))
                put("greek",wire(ts.map(MathShared::isUppercaseGreek)));put("diacriticsUnder",wire(ts.map {MathShared.getDiacriticalCommand(MathPosition.Under,it)}));put("diacriticsOver",wire(ts.map {MathShared.getDiacriticalCommand(MathPosition.Over,it)}));put("rl",wire(als.map(MathShared::isRLSequence)))
            }
        }
        "styles" -> {
            val ts=v[1].jsonArray.map {MathTextType.valueOf(str(it))};val envs=v[2].jsonArray.map {it.jsonArray.map(::str)}
            buildJsonObject {put("mml",wire(ts.map(MathShared::getMMLType)));put("commands",wire(envs.map {env->ts.map {MathShared.getLaTeXTextCommand(env,it)}}))}
        }
        "unicode" -> {
            val type=MathTextType.valueOf(str(v[1]));val cs=v[2].jsonArray.map {it.jsonPrimitive.int};val text=TextChars.fromPoints(cs.toIntArray())
            buildJsonObject {put("forward",wire(cs.map {MathUnicode.toUnicodeChar(type,it)}));put("reverse",wire(cs.map(MathUnicode::fromUnicodeChar)));put("to",MathUnicode.toUnicode(type,text));put("from",MathUnicode.fromUnicode(type,text))}
        }
        "unicode-range" -> {
            val type=MathTextType.valueOf(str(v[1]));val first=v[2].jsonPrimitive.int;val count=v[3].jsonPrimitive.int
            buildJsonObject {
                put("forward",wire((first until first+count).mapNotNull {cp->MathUnicode.toUnicodeChar(type,cp)?.let {cp to it}}))
                put("reverse",wire((first until first+count).mapNotNull {cp->MathUnicode.fromUnicodeChar(cp)?.let {Triple(cp,it.first,it.second)}}))
            }
        }
        "tex"->wire(v[1].jsonArray.map {MathTeXRenderer.renderTeX(tex(it),str(v[2]))})
        "escape"->JsonArray(v[1].jsonArray.map {cp->val t=MathTeXRenderer.escapeLaTeX(cp.jsonPrimitive.int);buildJsonObject {put("node",wire(t));put("out",MathTeXRenderer.renderTeX(t,"rest"))}})
        "escape-range" -> {
            val first=v[1].jsonPrimitive.int;val count=v[2].jsonPrimitive.int
            wire((first until first+count).mapNotNull {cp->val t=MathTeXRenderer.escapeLaTeX(cp);if(t is MathTeX.Token && t.character==cp) null else Triple(cp,t,MathTeXRenderer.renderTeX(t,"rest"))})
        }
        "control"->wire(v[1].jsonArray.map {MathTeXRenderer.isControlSeq(str(it))})
        "tables"->buildJsonObject {put("diacriticals",wire(MathShared.diacriticals));put("scalers",wire(MathShared.scalers));put("environment",wire(defaultMathEnvironment))}
        "tex-meta"->{val ts=v[1].jsonArray.map(::tex);buildJsonObject {put("show",MathShow.show(ts));put("show11",wire(ts.map {it.show(11)}));put("eq",wire(ts.flatMap {a->ts.map {b->a==b}}))}}
        "records"->wire(v[1].jsonArray.map {row->val x=row.jsonArray
            val record=if(str(x[0])=="operator") MathOperatorRecord(str(x[1]),str(x[2]),FormType.valueOf(str(x[3])),str(x[4]).toLong(),str(x[5]).toLong(),str(x[6]).toLong(),x[7].jsonArray.map(::str))
            else MathUnicodeRecord(x[1].jsonPrimitive.int,x[2].jsonArray.map {it.jsonArray.let {p->str(p[0]) to str(p[1])}},MathSymbolType.valueOf(str(x[3])),str(x[4]))
            listOf(MathShow.show(record),MathShow.show(record,11))
        })
        else->error("request")
    }
    @TestFactory
    fun originalMathObservations(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/math/data.jsonl.gz"))).bufferedReader().use {r->
        r.readLine();r.lineSequence().mapIndexed {i,line->var transport: String?=line
            DynamicTest.dynamicTest("Math ${i+1}") {
                val c=Json.parseToJsonElement(checkNotNull(transport)).jsonObject;transport=null
                assertLogJsonEquals(c.getValue("expected"),run(c.getValue("request").jsonArray))
            }
        }.toList()
    }
    @Test
    fun finiteDeepTreesAndOwnedCollections() {
        val source=mutableListOf<MathExp>(MathExp.ENumber("1"));val grouped=MathExp.EGrouped(source);source.clear();assertEquals(1,grouped.expressions.size)
        var e: MathExp=MathExp.EIdentifier("x");repeat(10000) {e=MathExp.EGrouped(listOf(e))}
        assertEquals(MathExp.EIdentifier("x"),MathShared.fixTree(e));assertTrue(e==MathShared.everywhereExp({it},e));assertTrue(e.show().startsWith("EGrouped [EGrouped ["))
        val result=MathPandocWriter.writePandoc(DisplayType.DisplayInline,listOf(e));assertEquals("[Emph [Str \"x\"]]",AstShow.show(checkNotNull(result)))
        var t: MathTeX=MathTeX.Literal("x");repeat(10000) {t=MathTeX.Grouped(listOf(t))};assertEquals("{x}",MathTeXRenderer.renderTeX(t))
    }
}
