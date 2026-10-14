package io.heapy.kotdoc.highlighting

import io.heapy.kotdoc.highlighting.regex.*
import io.heapy.kotdoc.highlighting.regex.KdeRegex.*
import io.heapy.kotdoc.parsing.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.TreeMap
import java.util.zip.GZIPInputStream
import kotlin.test.*

class KdeRegexTest {
    private fun JsonElement.s() = jsonPrimitive.content
    private fun JsonElement.n() = jsonPrimitive.long
    private fun JsonElement.b() = jsonPrimitive.boolean
    private fun j(vararg xs: JsonElement) = JsonArray(xs.toList())
    private fun groups(groups: Map<Long, KdeRegex>) = JsonArray(groups.entries.map { j(JsonPrimitive(it.key), JsonPrimitive(it.value.haskellShow())) })
    private fun result(input: ByteArray, result: KdeMatcher.Result?): JsonElement = if (result == null) JsonNull else buildJsonObject {
        put("bytes", result.bytes.toHexString())
        put("captures", JsonArray(result.captures.map { (n, capture) ->
            val start = capture.offset.coerceIn(0, input.size)
            val bytes = input.copyOfRange(start, (start + capture.length.coerceAtLeast(0)).coerceAtMost(input.size))
            j(JsonPrimitive(n), JsonPrimitive(capture.offset), JsonPrimitive(capture.length), JsonPrimitive(bytes.toHexString()), JsonPrimitive(Utf8String.toString(bytes)))
        }))
    }
    private fun compile(v: JsonElement, compact: Boolean = false): JsonElement {
        val a = v.jsonArray
        return when (val compiled = KdeCompiler.compile(a[1].b(), a[2].b(), a[0].s().hexToByteArray())) {
            is CompiledKdeRegex.Failure -> buildJsonObject { put("error", compiled.error) }
            is CompiledKdeRegex.Success -> {
                val regex = compiled.regex; val groups = KdeMatcher.extractCapturingGroups(regex)
                buildJsonObject {
                    if (compact) { put("flat", flatHash(regex)); put("groups", JsonArray(groups.map { (n, r) -> j(JsonPrimitive(n), JsonPrimitive(flatHash(r))) })) }
                    else { put("show", regex.haskellShow()); put("groups", groups(groups)) }
                    put("matches", JsonArray(a[3].jsonArray.map { val bytes = it.s().hexToByteArray(); result(bytes, KdeMatcher.match(regex, bytes)) }))
                    put("cachedMatches", JsonArray(a[3].jsonArray.map { val bytes = it.s().hexToByteArray(); result(bytes, KdeMatcher.matchWithGroups(groups, regex, bytes)) }))
                }
            }
        }
    }
    private fun flatHash(regex: KdeRegex): String {
        val rows = mutableListOf<JsonElement>(); val stack = ArrayDeque<KdeRegex>(); stack.addLast(regex)
        fun tag(name: String, vararg args: JsonElement) = j(JsonPrimitive(name), *args)
        while (stack.isNotEmpty()) {
            val row = when (val r = stack.removeLast()) {
                MatchAnyChar -> tag("any")
                MatchNull -> tag("null")
                AssertBeginning -> tag("start")
                AssertEnd -> tag("end")
                AssertWordBoundary -> tag("boundary")
                is MatchChar -> tag("char")
                is MatchDynamic -> tag("dyn", JsonPrimitive(r.number))
                is MatchCaptured -> tag("ref", JsonPrimitive(r.number), JsonPrimitive(r.caseSensitive))
                is Subroutine -> tag("sub", JsonPrimitive(r.number))
                is MatchSome -> { stack.addLast(r.regex); tag("some") }
                is Lazy -> { stack.addLast(r.regex); tag("lazy") }
                is Possessive -> { stack.addLast(r.regex); tag("poss") }
                is MatchCapture -> { stack.addLast(r.regex); tag("cap", JsonPrimitive(r.number)) }
                is AssertPositive -> { stack.addLast(r.regex); tag("positive", JsonPrimitive(r.direction.name)) }
                is AssertNegative -> { stack.addLast(r.regex); tag("negative", JsonPrimitive(r.direction.name)) }
                is MatchAlt -> { stack.addLast(r.right); stack.addLast(r.left); tag("alt") }
                is MatchConcat -> { stack.addLast(r.right); stack.addLast(r.left); tag("concat") }
            }
            rows.add(row)
        }
        return MessageDigest.getInstance("SHA-256").digest(JsonArray(rows).toString().toByteArray()).toHexString()
    }
    private fun node(v: JsonElement): KdeRegex {
        val a = v.jsonArray
        return when (a[0].s()) {
            "any" -> MatchAnyChar; "null" -> MatchNull; "start" -> AssertBeginning; "end" -> AssertEnd; "boundary" -> AssertWordBoundary
            "char" -> MatchChar { it == a[1].jsonPrimitive.int }
            "sat" -> MatchChar(when (a[1].s()) { "word" -> RegexCharacters::isWordChar; "alpha" -> ParserCharacters::isAlpha; "space" -> ParserCharacters::isSpace; "digit" -> ParserCharacters::isDigit; else -> { _: Int -> true } })
            "dyn" -> MatchDynamic(a[1].n()); "ref" -> MatchCaptured(a[1].n(), a[2].b()); "sub" -> Subroutine(a[1].n())
            "cap" -> MatchCapture(a[1].n(), node(a[2])); "concat" -> MatchConcat(node(a[1]), node(a[2])); "alt" -> MatchAlt(node(a[1]), node(a[2]))
            "some" -> MatchSome(node(a[1])); "lazy" -> Lazy(node(a[1])); "poss" -> Possessive(node(a[1]))
            "positive" -> AssertPositive(if (a[1].s() == "backward") Direction.Backward else Direction.Forward, node(a[2]))
            "negative" -> AssertNegative(if (a[1].s() == "backward") Direction.Backward else Direction.Forward, node(a[2]))
            else -> error(a.toString())
        }
    }
    private fun predicates(regex: KdeRegex): List<(Int) -> Boolean> {
        val result = mutableListOf<(Int) -> Boolean>(); val stack = ArrayDeque<KdeRegex>(); stack.addLast(regex)
        while (stack.isNotEmpty()) when (val r = stack.removeLast()) {
            is MatchChar -> result.add(r.predicate)
            is MatchConcat -> { stack.addLast(r.right); stack.addLast(r.left) }
            is MatchAlt -> { stack.addLast(r.right); stack.addLast(r.left) }
            is MatchSome -> stack.addLast(r.regex)
            is MatchCapture -> stack.addLast(r.regex)
            is AssertPositive -> stack.addLast(r.regex)
            is AssertNegative -> stack.addLast(r.regex)
            is Possessive -> stack.addLast(r.regex)
            is Lazy -> stack.addLast(r.regex)
            else -> Unit
        }
        return result
    }
    private fun flags(cp: Int): Int = listOf(
        ParserCharacters.isAlpha(cp), ParserCharacters.isAlphaNum(cp), ParserCharacters.isSpace(cp), ParserCharacters.isDigit(cp),
        RegexCharacters.isLower(cp), RegexCharacters.isUpper(cp), RegexCharacters.isPrint(cp), RegexCharacters.isPunctuation(cp),
        RegexCharacters.isSymbol(cp), RegexCharacters.isControl(cp), RegexCharacters.isWordChar(cp), RegexCharacters.isHorizSpace(cp),
        cp < 128, cp in 48..57 || cp in 65..70 || cp in 97..102,
    ).foldIndexed(0) { index, acc, bit -> acc or (if (bit) 1 shl index else 0) }
    private fun actual(c: JsonObject): JsonElement {
        val v = c.getValue("input")
        return when (c.getValue("op").s()) {
            "compile" -> compile(v)
            "compile-compact" -> compile(v, true)
            "ast" -> {
                val a = v.jsonArray; val regex = node(a[0])
                val groupMap = if (a[1] == JsonNull) KdeMatcher.extractCapturingGroups(regex) else TreeMap<Long, KdeRegex>().also { map ->
                    a[1].jsonArray.forEach { row -> map[row.jsonArray[0].n()] = node(row.jsonArray[1]) }
                }
                val bytes = a[2].s().hexToByteArray()
                buildJsonObject { put("show", regex.haskellShow()); put("groups", groups(groupMap)); put("match", result(bytes, KdeMatcher.matchWithGroups(groupMap, regex, bytes))) }
            }
            "utf8" -> JsonArray(v.jsonArray.map { hex ->
                val bytes = hex.s().hexToByteArray(); val decoded = Utf8String.decode(bytes)
                buildJsonObject { put("text", Utf8String.toString(bytes)); put("decoded", decoded?.let { j(JsonPrimitive(buildString { appendCodePoint(it.point) }), JsonPrimitive(it.bytes)) } ?: JsonNull) }
            })
            "text-decode" -> JsonArray(v.jsonArray.map { JsonPrimitive(Utf8Text.decodeLenient(it.s().hexToByteArray())) })
            "chars" -> {
                val a = v.jsonArray; val buffer = ByteBuffer.allocate((a[1].jsonPrimitive.int - a[0].jsonPrimitive.int) * 16).order(ByteOrder.LITTLE_ENDIAN)
                for (cp in a[0].jsonPrimitive.int..<a[1].jsonPrimitive.int) {
                    buffer.putInt(RegexCharacters.category(cp)); buffer.putInt(RegexCharacters.toLower(cp)); buffer.putInt(RegexCharacters.toUpper(cp)); buffer.putInt(flags(cp))
                }
                JsonPrimitive(MessageDigest.getInstance("SHA-256").digest(buffer.array()).toHexString())
            }
            "predicates" -> {
                val a = v.jsonArray; val regex = assertIs<CompiledKdeRegex.Success>(KdeCompiler.compile(a[1].b(), false, a[0].s().hexToByteArray())).regex
                val ps = predicates(regex); val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(4096 * ps.size)
                val start = a[2].jsonPrimitive.int; val end = a[3].jsonPrimitive.int
                for (chunk in start..<end step 4096) {
                    var at = 0
                    for (cp in chunk..<minOf(chunk + 4096, end)) for (p in ps) buffer[at++] = if (p(cp)) 1 else 0
                    digest.update(buffer, 0, at)
                }
                buildJsonObject { put("count", ps.size); put("sha256", digest.digest().toHexString()) }
            }
            else -> error(c.toString())
        }
    }
    @TestFactory fun `original KDE regex compiler matcher and dependencies`(): List<DynamicTest> =
        GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/highlighting/regex.jsonl.gz"))).bufferedReader().use { reader ->
            reader.lineSequence().drop(1).mapIndexed { index, line ->
                val c = Json.parseToJsonElement(line).jsonObject
                DynamicTest.dynamicTest("$index ${c.getValue("op").s()} ${c["original"] ?: c["originalError"] ?: c["xml"] ?: ""}") {
                    val expected = c.getValue("expected"); val observed = actual(c)
                    if (expected != observed) {
                        val e = expected.toString(); val o = observed.toString(); val at = e.indices.firstOrNull { it >= o.length || e[it] != o[it] } ?: minOf(e.length, o.length)
                        fail("case $index ${c.getValue("op")} at $at: expected ${e.drop(maxOf(0, at - 50)).take(220)}; actual ${o.drop(maxOf(0, at - 50)).take(220)}")
                    }
                }
            }.toList()
        }
    @Test fun `large finite regexes use explicit work stacks and byte results are isolated`() {
        val regex = assertIs<CompiledKdeRegex.Success>(KdeCompiler.compile(true, false, "a{65535}".toByteArray())).regex
        assertNull(KdeMatcher.match(regex, ByteArray(0)))
        val optional = assertIs<CompiledKdeRegex.Success>(KdeCompiler.compile(true, false, "a{0,2}".toByteArray())).regex
        val first = assertIs<MatchAlt>(assertIs<MatchConcat>(optional).left)
        assertIs<MatchAlt>(assertIs<MatchConcat>(first.left).right)
        val nested = (1..20000).fold(MatchChar { it == 97 } as KdeRegex) { r, _ -> MatchCapture(1, r) }
        val result = assertNotNull(KdeMatcher.match(nested, byteArrayOf(97)))
        assertEquals(KdeMatcher.Capture(0, 1), result.captures[1])
        result.bytes[0] = 98
        assertContentEquals(byteArrayOf(97), result.bytes)
        assertTrue(nested.haskellShow().endsWith(")"))
        val input = byteArrayOf(97, 98)
        val sourceGroups = mutableMapOf<Long, KdeRegex>(1L to MatchChar { it == 98 })
        val effects = MatchConcat(MatchChar { cp ->
            input[1] = 99; sourceGroups[1] = MatchChar { it == 99 }; cp == 97
        }, Subroutine(1))
        assertContentEquals(byteArrayOf(97, 98), assertNotNull(KdeMatcher.matchWithGroups(sourceGroups, effects, input)).bytes)
        val flat = (1..20000).fold(MatchNull as KdeRegex) { r, _ -> MatchConcat(r, MatchNull) }
        assertContentEquals(byteArrayOf(), assertNotNull(KdeMatcher.match(flat, byteArrayOf(97))).bytes)
    }
}
