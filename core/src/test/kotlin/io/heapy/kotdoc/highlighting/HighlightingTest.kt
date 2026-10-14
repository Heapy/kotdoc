package io.heapy.kotdoc.highlighting

import io.heapy.kotdoc.highlighting.regex.*
import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.ast.Attr
import java.security.MessageDigest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.*

class HighlightingTest {
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
    private val builtins=Syntaxes.defaultSyntaxMap
    private fun attr(v: JsonElement): Attr { val a=v.jsonArray;return Attr(a[0].s(),a[1].jsonArray.map { it.s() },a[2].jsonArray.map { it.jsonArray.let { it[0].s() to it[1].s() } }) }
    private fun options(o: FormatOptions)=j(JsonPrimitive(o.numberLines),JsonPrimitive(o.startNumber),JsonPrimitive(o.lineAnchors),JsonPrimitive(o.titleAttributes),JsonArray(o.codeClasses.map(::JsonPrimitive)),JsonArray(o.containerClasses.map(::JsonPrimitive)),JsonPrimitive(o.lineIdPrefix),JsonPrimitive(o.ansiColorLevel.name))
    private fun callback(o: FormatOptions,ls: List<SourceLine>)=buildJsonObject { put("options",options(o));put("lines",JsonArray(ls.map { line -> JsonArray(line.map { j(JsonPrimitive(it.type.name),JsonPrimitive(it.text)) }) })) }
    private fun highlight(mp: SyntaxMap,a: JsonElement,input: String): JsonObject=when(val result=Highlighting.highlight(mp,::callback,attr(a),input)) {
        is Highlighted.Failure->buildJsonObject { put("error",result.error) }
        is Highlighted.Success->buildJsonObject { put("value",result.value) }
    }
    private fun observe(c: JsonObject): JsonElement {
        val v=c.getValue("input");val a=v as? JsonArray
        return when(c.getValue("op").s()) {
            "builtin"->highlight(builtins,a!![0],a[1].s())
            "model"->highlight(map(a!![0],::syntax),a[1],a[2].s())
            "points"->highlight(builtins,a!![0],buildString { a[1].jsonArray.forEach { val p=it.n();appendCodePoint(if(p in 0xd800..0xdfff) 0xfffd else p) } })
            "languages"->JsonArray(Highlighting.languages(builtins).map(::JsonPrimitive))
            "model-languages"->JsonArray(Highlighting.languages(map(v,::syntax)).map(::JsonPrimitive))
            "extension"->JsonArray(Highlighting.languagesByExtension(builtins,v.s()).map(::JsonPrimitive))
            "model-extension"->JsonArray(Highlighting.languagesByExtension(map(a!![0],::syntax),a[1].s()).map(::JsonPrimitive))
            "to-listings"->Highlighting.toListingsLanguage(v.s())?.let(::JsonPrimitive) ?: JsonNull
            "from-listings"->Highlighting.fromListingsLanguage(v.s())?.let(::JsonPrimitive) ?: JsonNull
            "safe-int"->Highlighting.safeInt(v.s())?.let(::JsonPrimitive) ?: JsonNull
            "styles"->JsonArray(Highlighting.highlightingStyles.map { JsonPrimitive(it.first) })
            else->error(c)
        }
    }
    @TestFactory fun originalHighlighting(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/highlighting/highlighting.jsonl.gz"))).bufferedReader().use { reader ->
        val m=Json.parseToJsonElement(checkNotNull(reader.readLine())).jsonObject;val rows=reader.readLines()
        assertEquals(m.getValue("cases").jsonPrimitive.int,rows.size)
        rows.mapIndexed { index,row ->DynamicTest.dynamicTest("$index highlighting") { val c=Json.parseToJsonElement(row).jsonObject;assertEquals(c.getValue("expected"),observe(c),"case $index: $row") } }
    }
    @Test fun callbackInvocationsOwnershipAndExceptions() {
        var calls=0
        assertEquals(Highlighted.Failure(""),Highlighting.highlight(builtins,{ _,_->calls++ },Attr(classes=listOf("unknown")),"x"))
        assertEquals(0,calls)
        val classes=mutableListOf("unknown","number")
        val captured=assertIs<Highlighted.Success<Pair<FormatOptions,List<SourceLine>>>>(Highlighting.highlight(builtins,{o,ls->calls++;o to ls},Attr(classes=classes),"x\n\ny"))
        assertEquals(1,calls);classes.clear();assertEquals(listOf("unknown","number"),captured.value.first.containerClasses)
        assertEquals(listOf(HighlightToken(TokenType.NormalTok,"")),captured.value.second[1])
        assertFailsWith<UnsupportedOperationException> { (captured.value.second as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (captured.value.second[0] as MutableList).clear() }
        val failure=IllegalStateException("formatter failure")
        assertSame(failure,assertFailsWith<IllegalStateException> { Highlighting.highlight(builtins,{_,_->throw failure},Attr(classes=listOf("cpp")),"x") })
        assertEquals(Highlighted.Success("value"),Highlighting.highlight(builtins,{_,_->"value"},Attr(classes=listOf("cpp")),"x"))
    }
}
