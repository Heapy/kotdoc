package io.heapy.kotdoc.math

import io.heapy.kotdoc.layout.*
import io.heapy.kotdoc.logging.assertLogJsonEquals
import io.heapy.kotdoc.xml.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/** Observation transport only; no expectations are calculated by Kotlin. */
class MathMLReaderTest {
    private fun str(v: JsonElement) = if (v is JsonArray) TextChars.fromPoints(v.map { it.jsonPrimitive.int }.toIntArray()) else v.jsonPrimitive.content
    private fun raw(v: JsonElement) = CharacterStringChars.fromPoints(v.jsonArray.map { it.jsonPrimitive.int }.toIntArray())
    private fun cps(v: CharacterString) = JsonArray(v.points().map(::JsonPrimitive))
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
    private fun name(n: LegacyXmlName) = buildJsonArray { add(cps(n.local)); add(n.uri?.let(::cps) ?: JsonNull); add(n.prefix?.let(::cps) ?: JsonNull) }
    private fun attr(a: LegacyXmlAttribute) = buildJsonArray { add(name(a.name)); add(cps(a.value)) }
    private fun line(n: BigInteger?) = n?.let { JsonPrimitive(it.toString()) } ?: JsonNull
    private fun tree(c: LegacyXmlContent): JsonElement = when (c) {
        is LegacyXmlElement -> buildJsonArray { add("element"); add(name(c.name)); add(JsonArray(c.attributes.map(::attr))); add(JsonArray(c.content.map(::tree))); add(line(c.line)) }
        is LegacyXmlText -> buildJsonArray { add("text"); add(c.kind.name); add(cps(c.text)); add(line(c.line)) }
        is LegacyXmlCRef -> buildJsonArray { add("ref"); add(cps(c.reference)) }
    }
    private fun token(t: LegacyXmlToken): JsonElement = when (t) {
        is LegacyXmlToken.Start -> buildJsonArray { add("start"); add(t.line.toString()); add(name(t.name)); add(JsonArray(t.attributes.map(::attr))); add(t.empty) }
        is LegacyXmlToken.End -> buildJsonArray { add("end"); add(t.line.toString()); add(name(t.name)) }
        is LegacyXmlToken.Text -> tree(t.data)
        is LegacyXmlToken.Ref -> buildJsonArray { add("ref"); add(cps(t.reference)) }
    }
    private fun tokens(ts: List<LegacyXmlToken>) = JsonArray(ts.map(::token))
    private fun lines(xs: List<LegacyXmlLChar>) = JsonArray(xs.map { buildJsonArray { add(it.line.toString()); add(it.character) } })
    private fun result(input: String): JsonElement = when (val r = MathMLReader.readMathML(input)) {
        is MathMLResult.Success -> buildJsonArray { add("Right"); add(wire(r.expressions)) }
        is MathMLResult.Failure -> buildJsonArray { add("Left"); add(r.error) }
    }
    private fun run(v: JsonArray): JsonElement = when (str(v[0])) {
        "read" -> result(str(v[1]))
        "read-show" -> JsonPrimitive(when (val r = MathMLReader.readMathML(str(v[1]))) {
            is MathMLResult.Success -> "R" + MathShow.show(r.expressions)
            is MathMLResult.Failure -> "L" + r.error
        })
        "entities" -> JsonArray(v[1].jsonArray.map { buildJsonArray { add(str(it)); add(MathMLEntities.getUnicode(str(it))?.let(::JsonPrimitive) ?: JsonNull) } })
        "scanner-first" -> {
            val scanner = LegacyXmlLexer.customScanner({ end: Boolean -> if (!end) 65 to true else error("forced scanner tail") }, false)
            scanner.uncons()?.first?.let(::JsonPrimitive) ?: JsonNull
        }
        "xml", "xml-text", "xml-bytes", "xml-scanner" -> {
            val source = when (str(v[0])) {
                "xml-text" -> CharacterStringChars.fromString(str(v[1]))
                "xml-bytes" -> CharacterStringChars.fromPoints(v[1].jsonArray.map { it.jsonPrimitive.int and 255 }.toIntArray())
                else -> raw(v[1])
            }
            if (str(v[0]) == "xml-scanner") {
                val points = source.points()
                val scanner = LegacyXmlLexer.customScanner({ i: Int -> if (i < points.size) points[i] to (i + 1) else null }, 0)
                buildJsonObject {
                    put("tokens", tokens(LegacyXmlLexer.tokens(scanner) { it.uncons() }))
                    put("contents", JsonArray(LegacyXmlInput.parseXML(scanner) { it.uncons() }.map(::tree)))
                    put("root", LegacyXmlInput.parseXMLDoc(scanner) { it.uncons() }?.let(::tree) ?: JsonNull)
                }
            } else buildJsonObject {
                put("tokens", tokens(LegacyXmlLexer.tokens(source)))
                put("contents", JsonArray(LegacyXmlInput.parseXML(source).map(::tree)))
                put("root", LegacyXmlInput.parseXMLDoc(source)?.let(::tree) ?: JsonNull)
            }
        }
        "lex-utils" -> {
            val source = raw(v[1])
            buildJsonObject {
                put("decoded", JsonArray(LegacyXmlLexer.decodeText(source).map { when (it) {
                    is LegacyXmlTextBit.Text -> buildJsonArray { add("text"); add(cps(it.text)) }
                    is LegacyXmlTextBit.Ref -> buildJsonArray { add("ref"); add(cps(it.reference)) }
                } }))
                put("attribute", cps(LegacyXmlLexer.decodeAttribute(source)))
                put("reference", LegacyXmlLexer.referenceToChar(source)?.let(::JsonPrimitive) ?: JsonNull)
                put("numeric", LegacyXmlLexer.numericEscape(source)?.let(::JsonPrimitive) ?: JsonNull)
            }
        }
        "lexer" -> {
            val first = BigInteger(str(v[1])); val ls = LegacyXmlLexer.lineNumber(first, raw(v[2]))
            fun <A> pair(r: LegacyXmlScan<A>, f: (A) -> JsonElement) = buildJsonArray { add(f(r.value)); add(lines(r.rest)) }
            val attributes = LegacyXmlLexer.attribs(ls)
            buildJsonObject {
                put("lines", lines(ls)); put("tokens", tokens(LegacyXmlLexer.tokensFromLines(ls)))
                put("name", pair(LegacyXmlLexer.qualName(ls), ::name)); put("attr", pair(LegacyXmlLexer.attrib(ls), ::attr))
                put("value", pair(LegacyXmlLexer.attrValue(ls), ::cps)); put("string", pair(LegacyXmlLexer.string(ls), ::cps))
                put("space", lines(LegacyXmlLexer.dropSpace(ls))); put("tag", tokens(LegacyXmlLexer.tag(ls)))
                put("attrs", buildJsonArray { add(JsonArray(attributes.attributes.map(::attr))); add(attributes.empty); add(tokens(attributes.tokens)) })
                put("special", tokens(LegacyXmlLexer.special(LegacyXmlLChar(first, 33), ls)))
                put("break", pair(LegacyXmlLexer.breakn({ it == 38 }, ls), ::cps)); put("breakAfter", pair(LegacyXmlLexer.breakAfter({ it == 38 }, ls), ::cps))
            }
        }
        "read-range" -> {
            val digest = MessageDigest.getInstance("SHA-256"); var success = 0; var failure = 0
            fun word(c: Int) { digest.update(byteArrayOf((c ushr 24).toByte(), (c ushr 16).toByte(), (c ushr 8).toByte(), c.toByte())) }
            val first = v[1].jsonPrimitive.int; val count = v[2].jsonPrimitive.int
            for (c in first until first + count) {
                val out = when (val r = MathMLReader.readMathML("<math><mi>" + TextChars.fromPoints(intArrayOf(c)) + "</mi></math>")) {
                    is MathMLResult.Success -> { success++; "R" + MathShow.show(r.expressions) }
                    is MathMLResult.Failure -> { failure++; "L" + r.error }
                }
                word(out.codePointCount(0, out.length)); out.codePoints().forEach(::word)
            }
            buildJsonObject { put("sha", digest.digest().toHexString()); put("success", success); put("failure", failure) }
        }
        else -> error("request")
    }
    @TestFactory
    fun originalMathMLAndLegacyXmlObservations(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/mathml-reader/data.jsonl.gz"))).bufferedReader().use { reader ->
        reader.readLine(); reader.lineSequence().mapIndexed { i, line -> var transport: String? = line
            DynamicTest.dynamicTest("MathML reader/XML ${i + 1}") {
                val c = Json.parseToJsonElement(checkNotNull(transport)).jsonObject; transport = null
                assertLogJsonEquals(c.getValue("expected"), run(c.getValue("request").jsonArray))
            }
        }.toList()
    }
}
