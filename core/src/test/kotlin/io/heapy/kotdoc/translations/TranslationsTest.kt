package io.heapy.kotdoc.translations

import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.TextChars
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.util.zip.GZIPInputStream
import kotlin.test.*

/** All expected values come from the unchanged pinned Haskell implementation. */
class TranslationsTest {
    private fun JsonElement.s() = jsonPrimitive.content
    private fun JsonElement.l() = jsonPrimitive.long
    private fun chars(v: JsonElement) = CharacterString(v.jsonArray.map { it.jsonPrimitive.int }.toIntArray())
    private fun text(v: JsonElement) = TextChars.fromPoints(chars(v).points())
    private fun points(v: CharacterString) = JsonArray(v.points().map(::JsonPrimitive))
    private fun value(v: JsonElement): JsonElement {
        val a = v.jsonArray
        return when (a[0].s()) {
            "s" -> JsonPrimitive(text(a[1]))
            "z" -> JsonNull
            "b", "n" -> a[1]
            "a" -> JsonArray(a[1].jsonArray.map(::value))
            "o" -> JsonObject(a[1].jsonArray.associate { pair -> pair.jsonArray.let { text(it[0]) to value(it[1]) } })
            else -> error(a[0])
        }
    }
    private fun table(t: Translations, p: Long) = buildJsonObject {
        put("shown", t.show(p))
        put("terms", JsonArray(Term.entries.map { term -> JsonArray(listOf(JsonPrimitive(term.name), t.lookupTerm(term)?.let(::JsonPrimitive) ?: JsonNull)) }))
    }
    private fun caught(f: () -> JsonElement): JsonElement = try { f() }
        catch (e: IllegalArgumentException) { buildJsonObject { put("error", e.message!!) } }
    private fun parsed(v: JsonElement, p: Long) = caught { buildJsonObject { put("success", table(Translations.fromJson(v), p)) } }
    private fun run(a: JsonArray): JsonElement = when (a[0].s()) {
        "termJSON" -> caught { buildJsonObject { put("success", Term.fromJson(value(a[1])).name) } }
        "translations" -> parsed(value(a[1]), a[2].l())
        "read" -> JsonArray(Term.reads(chars(a[2]), a[1].l()).map { (t, rest) -> JsonArray(listOf(JsonPrimitive(t.name), points(rest))) })
        "readList" -> JsonArray(Term.readsList(chars(a[1])).map { (ts, rest) -> JsonArray(listOf(JsonArray(ts.map { JsonPrimitive(it.name) }), points(rest))) })
        "relation" -> {
            val x = Term.fromOrdinal(a[1].l()); val y = Term.fromOrdinal(a[2].l())
            buildJsonObject {
                put("eq", x == y); put("ne", x != y); put("lt", x < y); put("le", x <= y); put("gt", x > y); put("ge", x >= y)
                put("compare", if (x < y) "LT" else if (x > y) "GT" else "EQ")
                put("min", minOf(x, y).name); put("max", maxOf(x, y).name); put("shown", x.show(a[3].l())); put("ordinal", x.ordinal)
            }
        }
        "combine" -> caught {
            val ts = a[2].jsonArray.map { Translations.fromJson(value(it)) }
            val result = when (a[1].s()) {
                "append" -> ts[0] + ts[1]
                "concat", "sconcat" -> Translations.concatenate(ts)
                "stimes" -> ts[0].repeat(BigInteger(a[4].s()))
                else -> error(a[1])
            }
            table(result, a[3].l())
        }
        "enum" -> caught {
            val op = a[1].s()
            val values = when (op) {
                "bounds" -> sequenceOf(Term.minimum, Term.maximum)
                "toEnum" -> sequenceOf(Term.fromOrdinal(a[2].l()))
                "succ" -> sequenceOf(Term.fromOrdinal(a[2].l()).successor())
                "pred" -> sequenceOf(Term.fromOrdinal(a[2].l()).predecessor())
                "from" -> Term.fromOrdinal(a[2].l()).enumerate()
                "fromTo" -> Term.fromOrdinal(a[2].l()).enumerate(to = Term.fromOrdinal(a[4].l()))
                "fromThen" -> Term.fromOrdinal(a[2].l()).enumerate(then = Term.fromOrdinal(a[3].l()))
                "fromThenTo" -> Term.fromOrdinal(a[2].l()).enumerate(then = Term.fromOrdinal(a[3].l()), to = Term.fromOrdinal(a[4].l()))
                else -> error(op)
            }
            buildJsonObject { put("values", JsonArray(values.take(a[5].jsonPrimitive.int).map { JsonPrimitive(it.name) }.toList())) }
        }
        else -> error(a[0])
    }
    @TestFactory
    fun originalObservations(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/translations/data.jsonl.gz")))
        .bufferedReader().use { stream ->
            stream.readLine() // manifest verified separately by the source checker
            stream.lineSequence().mapIndexed { i, line -> DynamicTest.dynamicTest("Translations.Types $i") {
                val c = Json.parseToJsonElement(line).jsonObject
                val a = c.getValue("request").jsonArray
                if (a[0].s() == "yaml") {
                    // Original YAML library supplies the JSON Value transport. The Kotlin YAML
                    // parser/readTranslations runtime is still pending and is not claimed here.
                    val expected = c.getValue("expected").jsonObject
                    assertEquals(expected.getValue("parsed"), parsed(expected.getValue("value"), a[2].l()), c.getValue("path").s())
                } else assertEquals(c.getValue("expected"), run(a), a.toString().take(500))
            } }.toList()
        }

    @Test
    fun completeReadConvenienceAndImmutableMap() {
        for (term in Term.entries) {
            assertEquals(term, Term.read(" ( (${term.name}) ) "))
            assertEquals(listOf(term), Term.readList("([(${term.name})])"))
        }
        assertEquals(emptyList(), Term.readList("[]"))
        for (s in listOf("figure", "Figure rest", "Figure {-comment-}"))
            assertEquals("Prelude.read: no parse", assertFailsWith<IllegalArgumentException> { Term.read(s) }.message)
        assertEquals("Prelude.read: no parse", assertFailsWith<IllegalArgumentException> { Term.readList("[Figure,]") }.message)
        val source = linkedMapOf<String, JsonElement>("Figure" to JsonPrimitive(" first "))
        val t = Translations.fromJson(JsonObject(source))
        source["Figure"] = JsonPrimitive("changed")
        assertEquals("first", t.lookupTerm(Term.Figure))
        assertSame(t, t.repeat(BigInteger.TEN.pow(100)))
    }
}
