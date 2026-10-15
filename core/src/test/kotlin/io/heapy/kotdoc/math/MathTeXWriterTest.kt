package io.heapy.kotdoc.math

import io.heapy.kotdoc.layout.*
import io.heapy.kotdoc.logging.assertLogJsonEquals
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/** Only transport/setup: all expected results come from unchanged upstream. */
class MathTeXWriterTest {
    private fun str(v: JsonElement)=if(v is JsonArray) TextChars.fromPoints(v.map {it.jsonPrimitive.int}.toIntArray()) else v.jsonPrimitive.content
    private fun chars(v: JsonElement)=CharacterString(v.jsonArray.map {it.jsonPrimitive.int}.toIntArray())
    private fun cps(s: CharacterString)=JsonArray(s.points().map(::JsonPrimitive))
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

    private fun wire(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is MathExp -> JsonArray(listOf(JsonPrimitive(v.javaClass.simpleName)) + v.fields().map(::wire))
        is DelimitedPart.Middle -> buildJsonArray { add("Left"); add(v.text) }
        is DelimitedPart.Expression -> buildJsonArray { add("Right"); add(wire(v.expression)) }
        is MathRational -> buildJsonArray { add(v.numerator.toString()); add(v.denominator.toString()) }
        is List<*> -> JsonArray(v.map(::wire))
        is String -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        is Enum<*> -> JsonPrimitive(v.name)
        else -> error("wire")
    }
    private fun run(v: JsonArray): JsonElement = when (str(v[0])) {
        "write" -> JsonPrimitive(MathTeXWriter.writeTeX(v[1].jsonArray.map(::expression)))
        "with" -> JsonPrimitive(MathTeXWriter.writeTeXWith(v[1].jsonArray.map(::str), v[2].jsonArray.map(::expression)))
        "environment" -> JsonPrimitive(MathTeXWriter.addLaTeXEnvironment(DisplayType.valueOf(str(v[1])), str(v[2])))
        "roundtrip" -> {
            val original = v[1].jsonArray.map(::expression)
            val result = MathTeXReader.readTeX(MathTeXWriter.writeTeX(original))
            val actual = when (result) {
                is MathTeXResult.Success -> buildJsonArray { add("Right"); add(wire(result.expressions)) }
                is MathTeXResult.Failure -> buildJsonArray { add("Left"); add(result.error) }
            }
            buildJsonObject {
                put("actual", actual); put("original", buildJsonArray { add("Right"); add(wire(original)) })
                put("matchesOriginal", result is MathTeXResult.Success && result.expressions == original)
            }
        }
        "from-mathml" -> when (val r = MathMLReader.readMathML(str(v[1]))) {
            is MathMLResult.Success -> buildJsonArray { add("Right"); add(MathTeXWriter.writeTeX(r.expressions)) }
            is MathMLResult.Failure -> buildJsonArray { add("Left"); add(r.error) }
        }
        "deep" -> {
            var e: MathExp = MathExp.EIdentifier("x")
            repeat(v[2].jsonPrimitive.int) { e = when (str(v[1])) {
                "group" -> MathExp.EGrouped(listOf(e))
                "sqrt" -> MathExp.ESqrt(e)
                "sub" -> MathExp.ESub(e, MathExp.ENumber("2"))
                "under" -> MathExp.EUnder(true, e, MathExp.ESymbol(MathSymbolType.Accent, "̲"))
                "narrow" -> MathExp.EGrouped(listOf(MathExp.EStyled(MathTextType.TextBold, listOf(e))))
                else -> error("deep")
            } }
            JsonPrimitive(MathTeXWriter.writeTeXWith(v[3].jsonArray.map(::str), listOf(e)))
        }
        "write-range" -> {
            val digest = MessageDigest.getInstance("SHA-256")
            fun word(c: Int) { digest.update(byteArrayOf((c ushr 24).toByte(), (c ushr 16).toByte(), (c ushr 8).toByte(), c.toByte())) }
            val env = v[1].jsonArray.map(::str); val first = v[2].jsonPrimitive.int; val count = v[3].jsonPrimitive.int
            for (c in first until first + count) {
                val s = TextChars.fromPoints(intArrayOf(c))
                for (e in listOf(MathExp.EIdentifier(s), MathExp.EText(MathTextType.TextNormal, s), MathExp.ESymbol(MathSymbolType.Ord, s), MathExp.ESymbol(MathSymbolType.Bin, s))) {
                    val out = MathTeXWriter.writeTeXWith(env, listOf(e))
                    word(out.codePointCount(0, out.length)); out.codePoints().forEach(::word)
                }
            }
            JsonPrimitive(digest.digest().toHexString())
        }
        else -> error("request")
    }
    @TestFactory
    fun originalTeXWriterAndRoundtrips(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/tex-writer/data.jsonl.gz"))).bufferedReader().use { reader ->
        reader.readLine(); reader.lineSequence().mapIndexed { i, line -> var transport: String? = line
            DynamicTest.dynamicTest("TeX writer ${i + 1}") {
                val c = Json.parseToJsonElement(checkNotNull(transport)).jsonObject; transport = null
                assertLogJsonEquals(c.getValue("expected"), run(c.getValue("request").jsonArray))
            }
        }.toList()
    }
}
