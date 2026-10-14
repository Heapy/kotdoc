package io.heapy.kotdoc.language

import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.TextChars
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.*

/** Transport/constructor adapters only; expectations are original upstream output. */
class LangTest {
    private fun JsonElement.s() = jsonPrimitive.content
    private fun JsonElement.l() = jsonPrimitive.long
    private fun chars(v: JsonElement) = CharacterString(v.jsonArray.map { it.jsonPrimitive.int }.toIntArray())
    private fun text(v: JsonElement) = TextChars.fromPoints(chars(v).points())
    private fun lang(v: JsonElement): Lang {
        val a = v.jsonArray
        fun optional(v: JsonElement) = if (v == JsonNull) null else text(v)
        return Lang(text(a[0]), optional(a[1]), optional(a[2]), a[3].jsonArray.map(::text),
            a[4].jsonArray.map { e -> e.jsonArray.let { text(it[0]) to it[1].jsonArray.map { pair -> pair.jsonArray.let { p -> text(p[0]) to text(p[1]) } } } },
            a[5].jsonArray.map(::text))
    }
    private fun project(l: Lang) = buildJsonArray {
        add(l.language); add(l.script?.let(::JsonPrimitive) ?: JsonNull); add(l.region?.let(::JsonPrimitive) ?: JsonNull)
        add(JsonArray(l.variants.map(::JsonPrimitive)))
        add(JsonArray(l.extensions.map { (k, vs) -> buildJsonArray {
            add(k); add(JsonArray(vs.map { (a, b) -> buildJsonArray { add(a); add(b) } }))
        } }))
        add(JsonArray(l.privateUse.map(::JsonPrimitive)))
    }
    private fun fields(l: Lang, p: Long = 0) = buildJsonObject {
        put("fields", project(l)); put("rendered", l.renderLang()); put("shown", l.show(p)); put("encoded", LangBinary.encode(l).toHexString())
    }
    private fun parsed(s: String) = when (val result = Lang.parseLang(s)) {
        is LangParseResult.Success -> buildJsonObject { put("success", fields(result.value)) }
        is LangParseResult.Failure -> buildJsonObject { put("error", result.error) }
    }
    private fun relation(x: Lang, y: Lang): JsonElement {
        assertSame(if (x <= y) x else y, x.minimum(y))
        assertSame(if (x <= y) y else x, x.maximum(y))
        if (x == y) assertEquals(x.hashCode(), y.hashCode())
        return buildJsonObject {
            put("eq", x == y); put("ne", x != y); put("lt", x < y); put("le", x <= y); put("gt", x > y); put("ge", x >= y)
            put("compare", if (x < y) "LT" else if (x > y) "GT" else "EQ")
            put("min", project(x.minimum(y))); put("max", project(x.maximum(y)))
        }
    }
    private fun run(a: JsonArray): JsonElement = when (a[0].s()) {
        "parse" -> parsed(text(a[1]))
        "fromString" -> fields(Lang.fromString(chars(a[1])))
        "record" -> fields(lang(a[1]), a[2].l())
        "relation" -> relation(lang(a[1]), lang(a[2]))
        "lookup" -> {
            val entries = a[2].jsonArray.map { it.jsonArray.let { p -> lang(p[0]) to p[1].l() } }
            val result = lang(a[1]).lookupLang(entries)
            if (result == null) JsonNull else {
                assertTrue(entries.any { it === result })
                buildJsonObject { put("fields", project(result.first)); put("value", result.second) }
            }
        }
        "decode" -> when (val result = LangBinary.decode(a[1].s().hexToByteArray())) {
            is LangBinaryResult.Success -> buildJsonObject {
                put("success", fields(result.value)); put("consumed", result.consumed); put("remaining", result.remaining.toHexString())
            }
            is LangBinaryResult.Failure -> buildJsonObject {
                put("error", result.error); put("consumed", result.consumed); put("remaining", result.remaining.toHexString())
            }
        }
        else -> error(a[0])
    }
    @TestFactory
    fun originalObservations(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/lang/data.jsonl.gz")))
        .bufferedReader().use { reader ->
            reader.readLine()
            reader.lineSequence().flatMapIndexed { i, line ->
                val original = Json.parseToJsonElement(line).jsonObject.getValue("request").jsonArray[0].s() == "original"
                if (original) listOf("parsed", "roundtrip").map { op -> DynamicTest.dynamicTest("Original Lang $i $op") {
                    val c = Json.parseToJsonElement(line).jsonObject; val e = c.getValue("expected").jsonObject
                    val input = if (op == "parsed") e.getValue("input").s() else e.getValue("roundtrip").jsonObject.getValue("success").jsonObject.getValue("rendered").s()
                    val result = parsed(input)
                    assertEquals(e.getValue(op), result)
                    assertEquals(e.getValue("expected"), result.jsonObject.getValue("success").jsonObject.getValue("fields"))
                } } else listOf(DynamicTest.dynamicTest("Lang $i") {
                    val c = Json.parseToJsonElement(line).jsonObject; val a = c.getValue("request").jsonArray
                    assertEquals(c.getValue("expected"), run(a), a.toString().take(500))
                })
            }.toList()
        }
    @Test
    fun immutableFieldsAndStableCallerIdentity() {
        val variants = mutableListOf("1996")
        val keywords = mutableListOf("co" to "phonebk")
        val extensions = mutableListOf("u" to keywords)
        val private = mutableListOf("private")
        val q = Lang("en", "Latn", "US", variants, extensions, private)
        variants[0] = "changed"; keywords.clear(); extensions.clear(); private.clear()
        assertEquals(listOf("1996"), q.variants)
        assertEquals(listOf("u" to listOf("co" to "phonebk")), q.extensions)
        assertEquals(listOf("private"), q.privateUse)
        assertFailsWith<UnsupportedOperationException> { (q.variants as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (q.extensions[0].second as MutableList).clear() }
        val a = q to Any(); val b = q.copy() to Any()
        assertSame(a, q.lookupLang(listOf(a, b)))
        assertSame(b, q.lookupLang(listOf(b, a)))
        val decoded = assertIs<LangBinaryResult.Success>(LangBinary.decode(LangBinary.encode(q) + byteArrayOf(1, 2)))
        val rest = decoded.remaining; rest[0] = 99
        assertContentEquals(byteArrayOf(1, 2), decoded.remaining)
    }
}
