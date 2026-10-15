package io.heapy.kotdoc.math

import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.logging.assertLogJsonEquals
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlin.test.*

class MathTeXReaderTest {
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
    private fun value(e: MathExp)=buildJsonObject {put("tree",wire(e));put("show",e.show())}
    private fun entries(m: Map<String,MathExp>)=JsonArray(m.entries.map {buildJsonArray {add(it.key);add(value(it.value))}})
    private fun run(v: JsonArray): JsonElement=when(str(v[0])) {
        "read"->when(val r=MathTeXReader.readTeX(str(v[1]))) {
            is MathTeXResult.Success->buildJsonArray {add("Right");add(wire(r.expressions));add(MathShow.show(r.expressions))}
            is MathTeXResult.Failure->buildJsonArray {add("Left");add(wire(r.error.codePoints().toArray().toList()))}
        }
        "read-range"->{
            val digest=MessageDigest.getInstance("SHA-256");var success=0;var failure=0
            fun word(c: Int) {digest.update(byteArrayOf((c ushr 24).toByte(),(c ushr 16).toByte(),(c ushr 8).toByte(),c.toByte()))}
            val first=v[1].jsonPrimitive.int;val count=v[2].jsonPrimitive.int
            for(c in first until first+count) {
                val out=when(val r=MathTeXReader.readTeX(TextChars.fromPoints(intArrayOf(c)))) {
                    is MathTeXResult.Success->{success++;"R"+MathShow.show(r.expressions)}
                    is MathTeXResult.Failure->{failure++;"L"+r.error}
                }
                word(out.codePointCount(0,out.length));out.codePoints().forEach(::word)
            }
            buildJsonObject {put("sha",digest.digest().joinToString("") {"%02x".format(it)});put("success",success);put("failure",failure)}
        }
        "tables"->buildJsonObject {put("styleKeys",wire(MathTeXCommands.styleOps.keys.toList()));put("textKeys",wire(MathTeXCommands.textOps.keys.toList()));put("enclosures",entries(MathTeXCommands.enclosures));put("operators",entries(MathTeXCommands.operators));put("symbols",entries(MathTeXCommands.symbols));put("units",entries(MathTeXCommands.siUnitMap))}
        "style"->MathTeXCommands.styleOps[str(v[1])]?.invoke(v[2].jsonArray.map(::expression))?.let(::value) ?: JsonNull
        "text"->MathTeXCommands.textOps[str(v[1])]?.invoke(str(v[2]))?.let(::value) ?: JsonNull
        else->error("request")
    }
    @TestFactory
    fun originalTeXReaderObservations(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/math-reader/data.jsonl.gz"))).bufferedReader().use {r->
        r.readLine();r.lineSequence().mapIndexed {i,line->var transport: String?=line
            DynamicTest.dynamicTest("TeX reader ${i+1}") {
                val c=Json.parseToJsonElement(checkNotNull(transport)).jsonObject;transport=null
                assertLogJsonEquals(c.getValue("expected"),run(c.getValue("request").jsonArray))
            }
        }.toList()
    }
    @Test
    fun finiteDeepAndOwnedTables() {
        assertFailsWith<UnsupportedOperationException> {(MathTeXCommands.symbols as MutableMap).clear()}
        val input="{".repeat(1500)+"x"+"}".repeat(1500)
        assertIs<MathTeXResult.Success>(MathTeXReader.readTeX(input))
    }
}
