package io.heapy.kotdoc.highlighting

import io.heapy.kotdoc.highlighting.regex.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.*

class SyntaxTest {
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
    private val cases by lazy {
        GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/highlighting/syntax.jsonl.gz"))).bufferedReader().use { reader ->
            reader.lineSequence().drop(1).map { Json.parseToJsonElement(it).jsonObject }.toList()
        }
    }
    private val builtinMap by lazy { cases.filter { it.getValue("op").s() == "xml-model" }.map { syntax(it.getValue("input")) }.associateBy { it.name } }
    private fun <T : Comparable<T>> observe(input: JsonElement, parse: (JsonElement) -> T): JsonElement {
        val a = parse(input.jsonArray[0]); val b = parse(input.jsonArray[1]); val order = a.compareTo(b)
        assertEquals(a == b, order == 0)
        if (a == b) assertEquals(a.hashCode(), b.hashCode())
        val show11 = when (a) { is SyntaxValue<*> -> a.haskellShow(11); is RE -> a.haskellShow(11); is WordSet<*> -> a.haskellShow(11); else -> error(a) }
        return buildJsonObject { put("show", a.toString()); put("show11", show11); put("order", if (order < 0) "LT" else if (order > 0) "GT" else "EQ"); put("equal", a == b) }
    }
    private fun actual(c: JsonObject): JsonElement {
        val v = c.getValue("input")
        return when (val op = c.getValue("op").s()) {
            "xml-model" -> { val s = syntax(v); val resolved = Syntaxes.resolveKeywords(builtinMap, s)
                buildJsonObject { put("show", s.toString()); put("resolved", encode(resolved)); put("resolvedShow", resolved.toString()) }
            }
            "syntax" -> observe(v, ::syntax); "context" -> observe(v, ::context); "rule" -> observe(v, ::rule); "matcher" -> observe(v, ::matcher)
            "keyword" -> observe(v, ::kw); "switch" -> observe(v, ::switch); "item" -> observe(v, ::item); "word-set" -> observe(v, ::ws); "re" -> observe(v, ::re)
            "re-json", "re-json-decode" -> try { val r = RE.fromJson(v); buildJsonObject { put("show", r.toString()); put("json", r.toJson()) } }
                catch (e: HighlightJsonException) { buildJsonObject { put("error", if (op == "re-json") e.reason else e.message) } }
            "re-read" -> RE.readHaskell(v.s())?.let(::encode) ?: JsonNull
            "re-binary" -> encode(re(v).encodeBinary().toHexString())
            "re-binary-decode" -> when (val result = decodeREBinary(v.s().hexToByteArray())) {
                is REBinaryResult.Success -> buildJsonObject { put("value", encode(result.value)); put("consumed", result.consumed); put("remaining", result.remaining.toHexString()) }
                is REBinaryResult.Failure -> buildJsonObject { put("error", result.error); put("consumed", result.consumed); put("remaining", result.remaining.toHexString()) }
            }
            "re-compile" -> {
                val a = v.jsonArray; val r = re(a[0]); val compiled = r.compile(); assertSame(compiled, r.compile())
                when (compiled) {
                    is CompiledRE.Failure -> buildJsonObject { put("error", compiled.error) }
                    is CompiledRE.Success -> buildJsonObject {
                        put("regex", compiled.regex.haskellShow())
                        put("groups", JsonArray(compiled.groups.map { (n, regex) -> j(encode(n), encode(regex.haskellShow())) }))
                        put("matches", JsonArray(a[1].jsonArray.map { input ->
                            KdeMatcher.matchWithGroups(compiled.groups, compiled.regex, input.s().hexToByteArray())?.let { result ->
                                j(encode(result.bytes.toHexString()), JsonArray(result.captures.map { (n, c) -> j(encode(n), encode(c.offset), encode(c.length)) }))
                            } ?: JsonNull
                        }))
                    }
                }
            }
            "words" -> { val a = v.jsonArray; val w = if (a[3].b()) ws(j(a[0], a[1])) else WordSet.text(a[0].b(), a[1].jsonArray.map { it.s() })
                buildJsonObject { put("show", w.toString()); put("members", JsonArray(a[2].jsonArray.map { JsonPrimitive(it.s() in w) })) }
            }
            "words-int" -> { val a = v.jsonArray; val w = WordSet.make(a[0].b(), a[1].jsonArray.map { it.jsonPrimitive.long }, object : WordOperations<Long> {
                    override fun compare(a: Long, b: Long) = a.compareTo(b)
                    override fun foldCase(value: Long) = value
                    override fun haskellShow(value: Long) = value.toString()
                })
                buildJsonObject { put("show", w.toString()); put("order", "EQ") }
            }
            "map" -> { val a = v.jsonArray; val sm = syntaxMap(map(a[0], ::syntax))
                buildJsonObject {
                    put("resolved", encode(sm.mapValues { Syntaxes.resolveKeywords(sm, it.value) }))
                    put("missing", encode(Syntaxes.missingIncludes(sm.values.toList())))
                    put("lookups", JsonArray(a[1].jsonArray.map { q -> val (type, name) = q.jsonArray.map { it.s() }
                        when (type) {
                            "name" -> encode(Syntaxes.syntaxByName(sm, name)?.name)
                            "short" -> encode(Syntaxes.syntaxByShortName(sm, name)?.name)
                            "lookup" -> encode(Syntaxes.lookupSyntax(name, sm)?.name)
                            "extension" -> encode(Syntaxes.syntaxesByExtension(sm, name).map { it.name })
                            "filename" -> encode(Syntaxes.syntaxesByFilename(sm, name).map { it.name })
                            else -> error(type)
                        }
                    }))
                }
            }
            else -> error(op)
        }
    }
    @TestFactory fun originalSyntaxModels() = cases.mapIndexed { index, c ->
        DynamicTest.dynamicTest("$index ${c.getValue("op").s()} ${c["xml"]?.s().orEmpty()}") { assertEquals(c.getValue("expected"), actual(c)) }
    }
    @Test fun cachedRegexOwnsMetadataAndGroups() {
        val bytes = "(a)".toByteArray(); val r = RE(bytes, true, false); bytes.fill(0)
        r.bytes.fill(0)
        assertEquals("(a)", r.bytes.toString(Charsets.UTF_8))
        val compiled = assertIs<CompiledRE.Success>(r.compile()); assertSame(compiled, r.compile())
        assertEquals(listOf(0L, 1L), compiled.groups.keys.toList())
        assertEquals("a", KdeMatcher.matchWithGroups(compiled.groups, compiled.regex, "ab".toByteArray())?.bytes?.toString(Charsets.UTF_8))
        assertFailsWith<UnsupportedOperationException> { (compiled.groups as MutableMap).clear() }
        val allBytes = RE(ByteArray(256) { it.toByte() }, false, true)
        assertEquals(allBytes, RE.readHaskell(allBytes.toString()))
        assertNull(RE.readHaskell("RE {reString = \"\\" + String(Character.toChars(0x10061)) + "\", reCaseSensitive = True, reMinimal = False}"))
        val payload = allBytes.encodeBinary() + byteArrayOf(3, 4)
        val decoded = assertIs<REBinaryResult.Success>(decodeREBinary(payload))
        payload.fill(0); assertEquals(allBytes, decoded.value); decoded.remaining.fill(0)
        assertContentEquals(byteArrayOf(3, 4), decoded.remaining)
        val invalid = RE("(".toByteArray(), false, true)
        assertSame(invalid.compile(), invalid.compile()); assertIs<CompiledRE.Failure>(invalid.compile())
        assertEquals(invalid, RE("(".toByteArray(), false, true))
    }
    @Test fun syntaxCollectionsAreImmutableSnapshotsAndDeepRulesRemainUsable() {
        val chars = mutableListOf(32, 32, 9); val switches = mutableListOf<ContextSwitch>(ContextSwitch.Pop)
        var a = Rule(Matcher.AnyChar(chars), TokenType.NormalTok, false, chars, false, true, emptyList(), false, false, null, switches)
        var b = Rule(Matcher.AnyChar(listOf(9, 32)), TokenType.NormalTok, false, listOf(9, 32), false, true, emptyList(), false, false, null, listOf(ContextSwitch.Pop))
        chars.clear(); switches.clear(); assertEquals(a, b)
        assertFailsWith<UnsupportedOperationException> { (a.wordDelimiters as MutableSet).clear() }
        assertFailsWith<UnsupportedOperationException> { (a.contextSwitch as MutableList).clear() }
        repeat(10_000) {
            a = Rule(Matcher.Int, TokenType.NormalTok, false, emptyList(), false, true, listOf(a), false, false, null, emptyList())
            b = Rule(Matcher.Int, TokenType.NormalTok, false, emptyList(), false, true, listOf(b), false, false, null, emptyList())
        }
        assertEquals(0, a.compareTo(b)); assertEquals(a, b); assertEquals(a.hashCode(), b.hashCode()); assertEquals(a.toString(), b.toString())
        val ruleList = mutableListOf(a)
        val context = Context("start", "A", ruleList, TokenType.NormalTok, emptyList(), emptyList(), false, emptyList(), false)
        val contextMap = mutableMapOf("start" to context); val items = mutableListOf<ListItem>(ListItem.Item("one"))
        val lists = mutableMapOf<String, List<ListItem>>("words" to items); val extensions = mutableListOf("*.a")
        val syntax = Syntax("A", "a.xml", "A", lists, contextMap, "", "", "", extensions, "start")
        ruleList.clear(); contextMap.clear(); items.clear(); lists.clear(); extensions.clear()
        assertEquals(1, syntax.contexts.getValue("start").rules.size); assertEquals(1, syntax.lists.getValue("words").size); assertEquals(listOf("*.a"), syntax.extensions)
        assertFailsWith<UnsupportedOperationException> { (syntax.lists.getValue("words") as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (syntax.contexts as MutableMap).clear() }
        val map = Syntaxes.addSyntaxDefinition(syntax, mapOf("alias" to syntax)); assertEquals(setOf("A", "alias"), map.keys)
        assertFailsWith<UnsupportedOperationException> { (map as MutableMap).clear() }
    }
}
