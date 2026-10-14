package io.heapy.kotdoc.highlighting

import io.heapy.kotdoc.highlighting.regex.*
import io.heapy.kotdoc.formats.HaskellString
import java.security.MessageDigest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.*

class SyntaxBinaryTest {
    private fun JsonElement.s() = jsonPrimitive.content
    private fun JsonElement.b() = jsonPrimitive.boolean
    private fun JsonElement.n() = jsonPrimitive.int
    private fun j(vararg values: JsonElement) = JsonArray(values.toList())
    private fun chars(v: JsonElement) = v.jsonArray.map { it.n() }
    private fun re(v: JsonElement): RE { val a = v.jsonArray; return RE(a[0].s().hexToByteArray(), a[1].b(), a[2].b()) }
    private fun kw(v: JsonElement): KeywordAttr { val a = v.jsonArray; return KeywordAttr(a[0].b(), chars(a[1])) }
    private fun ws(v: JsonElement): WordSet<String> {
        val a = v.jsonArray; val words = a[1].jsonArray.map { it.s() }
        return if (a[0].b()) WordSet.CaseSensitiveWords(words, TextWordOperations) else WordSet.CaseInsensitiveWords(words, TextWordOperations)
    }
    private fun ctx(v: JsonElement): ContextName { val a = v.jsonArray; return ContextName(a[0].s(), a[1].s()) }
    private fun matcher(v: JsonElement): Matcher {
        val a = v.jsonArray
        return when (a[0].s()) {
            "DetectChar" -> Matcher.DetectChar(a[1].n())
            "Detect2Chars" -> Matcher.Detect2Chars(a[1].n(), a[2].n())
            "AnyChar" -> Matcher.AnyChar(chars(a[1]))
            "RangeDetect" -> Matcher.RangeDetect(a[1].n(), a[2].n())
            "StringDetect" -> Matcher.StringDetect(a[1].s())
            "WordDetect" -> Matcher.WordDetect(a[1].s())
            "RegExpr" -> Matcher.RegExpr(re(a[1]))
            "Keyword" -> { val w = a[2].jsonArray; Matcher.Keyword(kw(a[1]), if (w[0].s() == "Left") KeywordWords.Unresolved(w[1].s()) else KeywordWords.Resolved(ws(w[1]))) }
            "Int" -> Matcher.Int; "Float" -> Matcher.Float; "HlCOct" -> Matcher.HlCOct; "HlCHex" -> Matcher.HlCHex
            "HlCStringChar" -> Matcher.HlCStringChar; "HlCChar" -> Matcher.HlCChar
            "LineContinue" -> Matcher.LineContinue(a[1].n())
            "IncludeRules" -> Matcher.IncludeRules(ctx(a[1]))
            "DetectSpaces" -> Matcher.DetectSpaces; "DetectIdentifier" -> Matcher.DetectIdentifier
            else -> error(a[0].s())
        }
    }
    private fun switch(v: JsonElement): ContextSwitch = if (v.jsonArray.isEmpty()) ContextSwitch.Pop else ContextSwitch.Push(ctx(v.jsonArray[0]))
    private fun item(v: JsonElement): ListItem { val a = v.jsonArray; return if (a[0].s() == "Item") ListItem.Item(a[1].s()) else ListItem.IncludeList(ctx(a[1])) }
    private fun rule(v: JsonElement): Rule { val a = v.jsonArray; return Rule(matcher(a[0]), TokenType.valueOf(a[1].s()), a[2].b(), chars(a[3]), a[4].b(), a[5].b(), a[6].jsonArray.map(::rule), a[7].b(), a[8].b(), a[9].takeUnless { it == JsonNull }?.jsonPrimitive?.long, a[10].jsonArray.map(::switch)) }
    private fun context(v: JsonElement): Context { val a = v.jsonArray; return Context(a[0].s(), a[1].s(), a[2].jsonArray.map(::rule), TokenType.valueOf(a[3].s()), a[4].jsonArray.map(::switch), a[5].jsonArray.map(::switch), a[6].b(), a[7].jsonArray.map(::switch), a[8].b()) }
    private fun <A> map(v: JsonElement, f: (JsonElement) -> A): Map<String, A> = v.jsonArray.associate { val a = it.jsonArray; a[0].s() to f(a[1]) }
    private fun syntax(v: JsonElement): Syntax { val a = v.jsonArray; return Syntax(a[0].s(), a[1].s(), a[2].s(), map(a[3]) { it.jsonArray.map(::item) }, map(a[4], ::context), a[5].s(), a[6].s(), a[7].s(), a[8].jsonArray.map { it.s() }, a[9].s()) }
    private fun encode(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is String -> JsonPrimitive(v)
        is HaskellChars -> JsonPrimitive(buildString { v.points.forEach { appendCodePoint(if (it in 0xd800..0xdfff) 0xfffd else it) } })
        is Boolean -> JsonPrimitive(v)
        is Int -> JsonPrimitive(v)
        is Long -> JsonPrimitive(v)
        is TokenType -> JsonPrimitive(v.name)
        is RE -> j(JsonPrimitive(v.bytes.toHexString()), JsonPrimitive(v.caseSensitive), JsonPrimitive(v.minimal))
        is ContextName -> j(encode(v.syntax), encode(v.context))
        is KeywordAttr -> j(encode(v.caseSensitive), encode(v.delimiters))
        is WordSet<*> -> j(encode(v.caseSensitive), encode(v.words))
        is KeywordWords.Unresolved -> j(encode("Left"), encode(v.name))
        is KeywordWords.Resolved -> j(encode("Right"), encode(v.words))
        is Matcher.DetectChar -> j(encode("DetectChar"), encode(v.char))
        is Matcher.Detect2Chars -> j(encode("Detect2Chars"), encode(v.char), encode(v.char1))
        is Matcher.AnyChar -> j(encode("AnyChar"), encode(v.chars))
        is Matcher.RangeDetect -> j(encode("RangeDetect"), encode(v.char), encode(v.char1))
        is Matcher.StringDetect -> j(encode("StringDetect"), encode(v.text))
        is Matcher.WordDetect -> j(encode("WordDetect"), encode(v.text))
        is Matcher.RegExpr -> j(encode("RegExpr"), encode(v.regex))
        is Matcher.Keyword -> j(encode("Keyword"), encode(v.attributes), encode(v.words))
        is Matcher.LineContinue -> j(encode("LineContinue"), encode(v.char))
        is Matcher.IncludeRules -> j(encode("IncludeRules"), encode(v.context))
        is Matcher -> j(encode(v.javaClass.simpleName))
        ContextSwitch.Pop -> JsonArray(emptyList())
        is ContextSwitch.Push -> j(encode(v.context))
        is ListItem.Item -> j(encode("Item"), encode(v.text))
        is ListItem.IncludeList -> j(encode("IncludeList"), encode(v.context))
        is SyntaxValue<*> -> encode(v.fields)
        is Map<*, *> -> JsonArray(v.entries.map { j(encode(it.key), encode(it.value)) })
        is Iterable<*> -> JsonArray(v.map(::encode))
        else -> error(v)
    }
    private object LongWords : WordOperations<Long> {
        override fun compare(a: Long, b: Long) = a.compareTo(b)
        override fun foldCase(value: Long) = value
        override fun haskellShow(value: Long) = value.toString()
    }
    @Suppress("UNCHECKED_CAST")
    private fun codec(kind: String): SyntaxBinaryCodec<Any> = when (kind) {
        "char" -> SyntaxBinary.char; "string" -> SyntaxBinary.string; "text" -> SyntaxBinary.text
        "context-name" -> SyntaxBinary.contextName; "keyword" -> SyntaxBinary.keyword
        "word-set" -> SyntaxBinary.wordSet; "word-set-int" -> SyntaxBinary.wordSet(SyntaxBinary.int64, LongWords)
        "matcher" -> SyntaxBinary.matcher; "switch" -> SyntaxBinary.contextSwitch; "item" -> SyntaxBinary.listItem
        "token" -> SyntaxBinary.tokenType; "rule" -> SyntaxBinary.rule; "context" -> SyntaxBinary.context
        "syntax" -> SyntaxBinary.syntax; "syntax-map" -> SyntaxBinary.syntaxMap
        "set-char" -> SyntaxBinary.characterSet; "map-int" -> SyntaxBinary.textIntMap
        else -> error(kind)
    } as SyntaxBinaryCodec<Any>
    private fun model(kind: String, v: JsonElement): Any = when (kind) {
        "char" -> v.n(); "string" -> chars(v); "text" -> v.s(); "context-name" -> ctx(v)
        "keyword" -> kw(v); "word-set" -> ws(v)
        "word-set-int" -> v.jsonArray.let { a ->
            val values = a[1].jsonArray.map { it.jsonPrimitive.long }
            if (a[0].b()) WordSet.CaseSensitiveWords(values, LongWords) else WordSet.CaseInsensitiveWords(values, LongWords)
        }
        "matcher" -> matcher(v); "switch" -> switch(v); "item" -> item(v); "rule" -> rule(v)
        "context" -> context(v); "syntax" -> syntax(v); "syntax-map" -> map(v, ::syntax)
        "token" -> TokenType.valueOf(v.s()); "set-char" -> chars(v).toSet()
        "map-int" -> map(v) { it.jsonPrimitive.long }
        else -> error(kind)
    }
    private fun shown(kind: String, value: Any): String = when (kind) {
        "char" -> (value as Int).let { point -> "'" + when (point) { 39 -> "\\'"; 34 -> "\""; else -> HaskellString.quoteCodePoints(intArrayOf(point)).drop(1).dropLast(1) } + "'" }
        "string" -> HaskellString.quoteCodePoints((value as List<*>).map { it as Int }.toIntArray())
        "text" -> HaskellString.quote(value as String)
        "token" -> (value as TokenType).name
        "set-char" -> "fromList " + HaskellString.quoteCodePoints((value as Set<*>).map { it as Int }.toIntArray())
        "context-name" -> (value as ContextName).toString()
        "map-int", "syntax-map" -> syntaxShow(value)
        else -> value.toString()
    }
    private fun observed(kind: String, bytes: ByteArray): JsonObject {
        val c = codec(kind)
        return when (val result = c.decode(bytes)) {
            is SyntaxBinaryResult.Failure -> buildJsonObject { put("error", result.error); put("consumed", result.consumed); put("remaining", result.remaining.toHexString()) }
            is SyntaxBinaryResult.Success -> buildJsonObject {
                put("value", encode(result.value)); put("show", shown(kind, result.value)); put("encoded", c.encode(result.value).toHexString())
                put("consumed", result.consumed); put("remaining", result.remaining.toHexString())
            }
        }
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()
    @TestFactory fun originalBinaryInstances() = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/highlighting/syntax-binary.jsonl.gz")))
        .bufferedReader().use { it.readLines() }.drop(1).mapIndexed { index, line ->
            val c = Json.parseToJsonElement(line).jsonObject; val op = c.getValue("op").s(); val kind = c.getValue("kind").s()
            DynamicTest.dynamicTest("$index $op $kind") {
                val v = c.getValue("input"); val expected = c.getValue("expected")
                val actual = when (op) {
                    "encode" -> JsonPrimitive(codec(kind).encode(model(kind, v)).toHexString())
                    "decode" -> observed(kind, v.s().hexToByteArray())
                    "equal" -> {
                        val a = v.jsonArray
                        val left = assertIs<SyntaxBinaryResult.Success<Any>>(codec(kind).decode(a[0].s().hexToByteArray())).value
                        val right = assertIs<SyntaxBinaryResult.Success<Any>>(codec(kind).decode(a[1].s().hexToByteArray())).value
                        assertEquals(left == right, right == left)
                        if (left == right) assertEquals(left.hashCode(), right.hashCode())
                        JsonPrimitive(left == right)
                    }
                    "set-members" -> {
                        val a = v.jsonArray; val set = assertIs<SyntaxBinaryResult.Success<Set<Int>>>(SyntaxBinary.characterSet.decode(a[0].s().hexToByteArray())).value
                        JsonArray(a[1].jsonArray.map { j(it, JsonPrimitive(it.n() in set)) })
                    }
                    "map-lookups" -> {
                        val a = v.jsonArray; val map = assertIs<SyntaxBinaryResult.Success<Map<String, Long>>>(SyntaxBinary.textIntMap.decode(a[0].s().hexToByteArray())).value
                        JsonArray(a[1].jsonArray.map { j(it, encode(map[it.s()])) })
                    }
                    "word-members" -> {
                        val a = v.jsonArray; val words = assertIs<SyntaxBinaryResult.Success<WordSet<String>>>(SyntaxBinary.wordSet.decode(a[0].s().hexToByteArray())).value
                        JsonArray(a[1].jsonArray.map { j(it, JsonPrimitive(it.s() in words)) })
                    }
                    "baked" -> {
                        val value = DefaultSyntaxes.syntaxMap.getValue(v.s()); val binary = SyntaxBinary.syntax.encode(value)
                        observed("syntax", binary)
                    }
                    "syntax-filenames" -> {
                        val a = v.jsonArray; val syntax = assertIs<SyntaxBinaryResult.Success<Syntax>>(SyntaxBinary.syntax.decode(a[0].s().hexToByteArray())).value
                        JsonArray(a[1].jsonArray.map { j(it, encode(Syntaxes.syntaxesByFilename(mapOf(syntax.name to syntax), it.s()).map { s -> s.name })) })
                    }
                    "default" -> buildJsonObject {
                        val map = DefaultSyntaxes.syntaxMap
                        put("names", encode(map.keys)); put("missing", encode(Syntaxes.missingIncludes(map.values.toList())))
                        put("binarySha256", hash(SyntaxBinary.syntaxMap.encode(map)))
                    }
                    else -> error(op)
                }
                assertEquals(expected, actual)
            }
        }
    @Test fun deepRulesOwnershipAndCache() {
        val fresh = DefaultSyntaxes.readMap()
        assertEquals(195, fresh.size); assertEquals(0, fresh.initializedValues)
        assertTrue("ABC" in fresh); assertEquals(0, fresh.initializedValues)
        assertEquals("ABC", Syntaxes.syntaxByName(fresh, "ABC")?.name); assertEquals(1, fresh.initializedValues)
        assertEquals("ABC", Syntaxes.syntaxByName(fresh, "abc")?.name); assertEquals(1, fresh.initializedValues)
        val changed = Syntaxes.addSyntaxDefinition(fresh.getValue("ABC"), fresh)
        assertEquals(195, changed.size); assertEquals(1, fresh.initializedValues)
        assertEquals("ABC", changed.getValue("ABC").name); assertEquals(1, fresh.initializedValues)
        assertSame(fresh.getValue("ABC"), fresh.getValue("ABC"))
        assertFailsWith<UnsupportedOperationException> { (fresh as MutableMap<*, *>).clear() }
        var value = Rule(Matcher.Float, TokenType.NormalTok, false, emptyList(), false, true, emptyList(), false, false, null, emptyList())
        repeat(10_000) { value = Rule(Matcher.Int, TokenType.NormalTok, false, emptyList(), false, true, listOf(value), false, false, null, emptyList()) }
        val binary = SyntaxBinary.rule.encode(value)
        val decoded = assertIs<SyntaxBinaryResult.Success<Rule>>(SyntaxBinary.rule.decode(binary))
        assertEquals(value, decoded.value); assertContentEquals(binary, SyntaxBinary.rule.encode(decoded.value))
        assertTrue(decoded.remaining.isEmpty()); assertEquals(binary.size.toLong(), decoded.consumed)
        val appended = binary + byteArrayOf(1, 2)
        val rest = assertIs<SyntaxBinaryResult.Success<Rule>>(SyntaxBinary.rule.decode(appended))
        appended.fill(0); rest.remaining.fill(0); assertContentEquals(byteArrayOf(1, 2), rest.remaining)
        assertSame(DefaultSyntaxes.syntaxMap, DefaultSyntaxes.syntaxMap)
        assertFailsWith<UnsupportedOperationException> { (DefaultSyntaxes.syntaxMap as MutableMap).clear() }
        val regex = (DefaultSyntaxes.syntaxMap.values.asSequence().flatMap { it.contexts.values }.flatMap { it.rules }.mapNotNull { (it.matcher as? Matcher.RegExpr)?.regex }.first())
        assertSame(regex.compile(), regex.compile())
    }
}
