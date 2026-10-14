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

class TokenizerTest {
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
    private val builtins = Syntaxes.defaultSyntaxMap
    private fun output(result: Tokenization): JsonObject = when (result) {
        is Tokenization.Failure -> buildJsonObject { put("error", result.error) }
        is Tokenization.Success -> buildJsonObject { put("tokens", JsonArray(result.lines.map { line -> JsonArray(line.map { j(JsonPrimitive(it.type.name), JsonPrimitive(it.text)) }) })) }
    }
    private fun observe(case: JsonObject): JsonObject {
        val a = case.getValue("input").jsonArray
        val trace = mutableListOf<String>()
        val result: JsonElement = try {
            when (case.getValue("op").s()) {
                "config-show" -> JsonPrimitive(TokenizerConfig(map(a[0], ::syntax), a[1].b(), trace::add).haskellShow(a[2].n()))
                "builtin" -> output(Tokenizer.tokenize(TokenizerConfig(builtins, a[2].b(), trace::add), checkNotNull(Syntaxes.lookupSyntax(a[0].s(), builtins)), a[1].s()))
                "model" -> output(Tokenizer.tokenize(TokenizerConfig(map(a[1], ::syntax), a[3].b(), trace::add), syntax(a[0]), a[2].s()))
                "model-points" -> output(Tokenizer.tokenize(TokenizerConfig(map(a[1], ::syntax), a[3].b(), trace::add), syntax(a[0]), buildString { a[2].jsonArray.forEach { appendCodePoint(it.n()) } }))
                "xml" -> when (val parsed = SyntaxParser.parseSyntaxDefinitionFromText(a[0].s(), a[1].s())) {
                    is SyntaxParseResult.Failure -> buildJsonObject { put("parseError", parsed.error) }
                    is SyntaxParseResult.Success -> {
                        val mp = Syntaxes.addSyntaxDefinition(parsed.syntax, builtins)
                        output(Tokenizer.tokenize(TokenizerConfig(mp, a[3].b(), trace::add), Syntaxes.resolveKeywords(mp, parsed.syntax), a[2].s()))
                    }
                }
                else -> error(case)
            }
        } catch (e: InvalidIncludeRulesException) { buildJsonObject { put("exception", e.message) } }
        return buildJsonObject { put("result", result); put("trace", JsonArray(trace.map(::JsonPrimitive))) }
    }
    @TestFactory fun originalTokenization(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/highlighting/tokenizer.jsonl.gz"))).bufferedReader().use { reader ->
        val manifest = Json.parseToJsonElement(checkNotNull(reader.readLine())).jsonObject
        val rows = reader.readLines()
        assertEquals(manifest.getValue("cases").jsonPrimitive.int, rows.size)
        rows.mapIndexed { index, row ->
            val case = Json.parseToJsonElement(row).jsonObject
            DynamicTest.dynamicTest("$index ${case.getValue("group").s()} ${case["name"] ?: case["file"] ?: ""}") {
                val actual = observe(case)
                assertEquals(case.getValue("expected"), actual, "case $index: $row")
                case["original"]?.let { assertEquals(it, actual.getValue("result")) }
                if (case["noDrop"]?.jsonPrimitive?.boolean == true) {
                    val a = case.getValue("input").jsonArray
                    val text = a[a.size - 2].s()
                    val lines = if (text.isEmpty()) emptyList() else text.split('\n').let { if (text.endsWith('\n')) it.dropLast(1) else it }
                    assertEquals(lines, actual.getValue("result").jsonObject.getValue("tokens").jsonArray.map { line -> line.jsonArray.joinToString("") { it.jsonArray[1].s() } })
                }
            }
        }
    }
    private fun rule(m: Matcher, children: List<Rule> = emptyList(), switches: List<ContextSwitch> = emptyList()) =
        Rule(m, TokenType.KeywordTok, false, listOf(10,32), false, true, children, false, false, null, switches)
    private fun context(name: String, rules: List<Rule>) = Context(name,"A",rules,TokenType.NormalTok,emptyList(),emptyList(),false,emptyList(),false)
    private fun syntax(contexts: Map<String, Context>) = Syntax("A","a.xml","A",emptyMap(),contexts,"","","",emptyList(),"start")
    @Test fun finiteDeepChildrenAndIncludesAndOwnership() {
        var r = rule(Matcher.DetectChar(120))
        repeat(10000) { r = rule(Matcher.DetectChar(120), listOf(r)) }
        val syn = syntax(mapOf("start" to context("start",listOf(r))))
        val result = assertIs<Tokenization.Success>(Tokenizer.tokenize(TokenizerConfig(mapOf("A" to syn)),syn,"x".repeat(10001)))
        assertEquals(listOf(listOf(HighlightToken(TokenType.KeywordTok,"x".repeat(10001)))),result.lines)
        val contexts = mutableMapOf<String,Context>()
        contexts["10000"] = context("10000",listOf(rule(Matcher.DetectChar(120))))
        for (i in 9999 downTo 0) contexts[if (i==0) "start" else i.toString()] = context(i.toString(),listOf(rule(Matcher.IncludeRules(ContextName("A",(i+1).toString())))))
        val inc = syntax(contexts)
        val configMap = mutableMapOf("A" to inc)
        val config = TokenizerConfig(configMap); configMap.clear()
        assertEquals(listOf(listOf(HighlightToken(TokenType.KeywordTok,"x"))),assertIs<Tokenization.Success>(Tokenizer.tokenize(config,inc,"x")).lines)
        assertFailsWith<UnsupportedOperationException> { (result.lines as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (result.lines[0] as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (config.syntaxMap as MutableMap).clear() }
    }
    @Test fun normalizationKeepsEmptyDifferentAttributeBoundary() {
        val a = HighlightToken(TokenType.NormalTok,"x")
        val b = HighlightToken(TokenType.KeywordTok,"")
        assertEquals(listOf(a,a),Tokenizer.normalizeHighlighting(listOf(a,b,a)))
        assertEquals(listOf(HighlightToken(TokenType.NormalTok,"xx")),Tokenizer.normalizeHighlighting(listOf(a,a.copy(text=""),a)))
    }
}
