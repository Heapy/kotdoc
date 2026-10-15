package io.heapy.kotdoc.html

import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.logging.assertLogJsonEquals
import io.heapy.kotdoc.parsing.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Assertions.*
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/** Transport/caller setup only; all expected observations come from original code. */
class HtmlTagParsingTest {
    private fun text(v: JsonElement) = TextChars.fromString(v.jsonPrimitive.content)
    private fun attrs(v: JsonElement) = v.jsonArray.map { it.jsonArray.let { a -> text(a[0]) to text(a[1]) } }
    private fun attrs(v: List<Pair<String, String>>) = JsonArray(v.map { (k, v) -> buildJsonArray { add(k); add(v) } })
    private fun tag(v: JsonElement): HtmlTag = v.jsonArray.let { a -> when (a[0].jsonPrimitive.int) {
        0 -> HtmlTag.Open(text(a[1]), attrs(a[2]))
        1 -> HtmlTag.Close(text(a[1]))
        2 -> HtmlTag.Text(text(a[1]))
        3 -> HtmlTag.Comment(text(a[1]))
        4 -> HtmlTag.Warning(text(a[1]))
        5 -> HtmlTag.Position(a[1].jsonPrimitive.long, a[2].jsonPrimitive.long)
        else -> error("tag")
    } }
    private fun tag(v: HtmlTag): JsonElement = buildJsonArray { when (v) {
        is HtmlTag.Open -> { add(0); add(v.name); add(attrs(v.attributes)) }
        is HtmlTag.Close -> { add(1); add(v.name) }
        is HtmlTag.Text -> { add(2); add(v.text) }
        is HtmlTag.Comment -> { add(3); add(v.text) }
        is HtmlTag.Warning -> { add(4); add(v.message) }
        is HtmlTag.Position -> { add(5); add(v.row); add(v.column) }
    } }
    private fun predicate(v: JsonElement): (HtmlTag) -> Boolean = { tag -> val a = v.jsonArray; when (text(a[0])) {
        "any" -> true
        "never" -> false
        "kind" -> when (a[1].jsonPrimitive.int) { 0 -> tag is HtmlTag.Open; 1 -> tag is HtmlTag.Close; 2 -> tag is HtmlTag.Text; 3 -> tag is HtmlTag.Comment; 4 -> tag is HtmlTag.Warning; 5 -> tag is HtmlTag.Position; else -> false }
        "open" -> HtmlTagParsing.matchTagOpen(text(a[1]), attrs(a[2]), tag)
        "close" -> HtmlTagParsing.matchTagClose(text(a[1]), tag)
        "match" -> HtmlTagMatching.matches(tag, tag(a[1]))
        "attribute" -> HtmlTagParsing.maybeFromAttrib(text(a[1]), tag) == text(a[2])
        else -> error("predicate")
    } }
    private fun parser(v: JsonElement): HtmlTagParser<Long, JsonElement> {
        val a = v.jsonArray
        fun p(i: Int) = parser(a[i])
        return when (text(a[0])) {
            "any" -> HtmlTagParsing.pAny<Long>().map(::tag)
            "raw" -> TokenParsec.anyToken<HtmlTag, Long>(HtmlTagMatching::show).map(::tag)
            "prim" -> TokenParsec.tokenPrimEx<HtmlTag, Long, HtmlTag>(HtmlTagMatching::show,
                { position, _, rest -> var n = 0L; var stream = rest; while (true) { val step = stream.uncons() ?: break; n++; stream = step.tail }; position.copy(line = position.line + n, column = position.column + 1) },
                if (a[1].jsonPrimitive.boolean) { _, _, _, u -> u + 1 } else null,
                { if (predicate(a[2])(it)) TokenMatch.Accept(it) else TokenMatch.Reject }).map(::tag)
            "blank" -> HtmlTagParsing.pBlank<Long>().map { JsonPrimitive("()") }
            "satisfy" -> HtmlTagParsing.pSatisfy<Long>(predicate(a[1])).map(::tag)
            "closes" -> HtmlTagParsing.pCloses<Long>(text(a[1])).map { JsonPrimitive("()") }
            "in-tags", "in-tags-where" -> {
                val custom = text(a[0]) == "in-tags-where"
                val content = p(if (custom) 3 else 2).map { listOf(it) }
                val combined = if (custom) HtmlTagParsing.pInTagsWhere(text(a[1]), predicate(a[2]), content, emptyList()) { x, y -> x + y }
                    else HtmlTagParsing.pInTags(text(a[1]), content, emptyList()) { x, y -> x + y }
                combined.map(::JsonArray)
            }
            "in-tag" -> HtmlTagParsing.pInTag(TagOmission.entries[a[1].jsonPrimitive.int], text(a[2]), p(3))
            "in-attr" -> HtmlTagParsing.pInTagWithAttribs(TagOmission.entries[a[1].jsonPrimitive.int], text(a[2]), p(3)).map { (attributes, value) -> buildJsonArray { add(attrs(attributes)); add(value) } }
            "pure" -> TokenParsec.pure(a[1])
            "fail" -> TokenParsec.fail(text(a[1]))
            "zero" -> TokenParsec.zero()
            "eof" -> TokenParsec.eof<HtmlTag, Long>(HtmlTagMatching::show).map { JsonPrimitive("()") }
            "attempt" -> p(1).attempt()
            "look" -> p(1).lookAhead()
            "label" -> p(2).label(text(a[1]))
            "fallback" -> p(1) or TokenParsec.pure(JsonPrimitive("fallback"))
            "or" -> p(1) or p(2)
            "many" -> p(1).many().map(::JsonArray)
            "sequence" -> a[1].jsonArray.fold(TokenParsec.pure<HtmlTag, Long, List<JsonElement>>(emptyList())) { result, next -> result.flatMap { values -> parser(next).map { values + it } } }.map(::JsonArray)
            "before" -> p(1).before(p(2))
            "tick" -> p(1).flatMap { value -> TokenParsec.modify<HtmlTag, Long> { it.copy(user = it.user + 1) }.map { value } }
            "nest" -> {
                val n = a[1].jsonPrimitive.int
                if (n == 0) HtmlTagParsing.pSatisfy<Long> { it is HtmlTag.Text }.map(::tag)
                else HtmlTagParsing.pInTag(TagOmission.TagsRequired, "div", TokenParsec.defer { parser(buildJsonArray { add("nest"); add(n - 1) }) })
            }
            else -> error("parser")
        }
    }
    private fun pos(v: JsonElement) = v.jsonArray.let { a -> SourcePos(CharacterString(a[0].jsonArray.map { it.jsonPrimitive.int }.toIntArray()), a[1].jsonPrimitive.long, a[2].jsonPrimitive.long) }
    private fun pos(v: SourcePos) = buildJsonArray { add(JsonArray(v.nameRaw.points().map(::JsonPrimitive))); add(v.line); add(v.column) }
    private fun error(e: ParseError) = buildJsonObject {
        put("position", pos(e.position)); put("show", e.showText()); put("characters", JsonArray(e.showCharacters().points().map(::JsonPrimitive)))
        put("messages", JsonArray(e.messages.sortedBy { it.kind }.map { buildJsonArray { add(it.kind.ordinal); add(it.text) } }))
    }
    private fun observe(a: JsonArray): JsonElement {
        val r = parser(a[1]).run(TokenParseState(ListTokenStream(a[2].jsonArray.map(::tag)), pos(a[3]), a[4].jsonPrimitive.long))
        return buildJsonObject {
            put("ok", r.success); put("consumed", r.consumed); put("error", error(r.error))
            if (r.success) {
                put("value", checkNotNull(r.value)); put("position", pos(r.state.position)); put("user", r.state.user)
                val rest = mutableListOf<JsonElement>(); var stream = r.state.input
                while (true) { val step = stream.uncons() ?: break; rest.add(tag(step.token)); stream = step.tail }
                put("remaining", JsonArray(rest))
            }
        }
    }
    private fun run(a: JsonArray): JsonElement = when (text(a[0])) {
        "sets" -> JsonArray(listOf(HtmlTagCategories.blockHtmlTags.sorted(), HtmlTagCategories.blockDocBookTags.sorted(), HtmlTagCategories.eitherBlockOrInline.sorted(), HtmlTagCategories.epubTags.sorted(), HtmlTagCategories.blockTags.sorted(), HtmlTagCategories.voidTags.sorted(), HtmlTagCategories.sectioningContent, HtmlTagCategories.groupingContent).map { JsonArray(it.map(::JsonPrimitive)) })
        "space" -> JsonPrimitive(HtmlTagParsing.isSpace(a[1].jsonPrimitive.int))
        "match" -> JsonPrimitive(HtmlTagMatching.matches(tag(a[1]), tag(a[2])))
        "open" -> JsonPrimitive(HtmlTagParsing.matchTagOpen(text(a[1]), attrs(a[2]), tag(a[3])))
        "close" -> JsonPrimitive(HtmlTagParsing.matchTagClose(text(a[1]), tag(a[2])))
        "maybe" -> HtmlTagParsing.maybeFromAttrib(text(a[1]), tag(a[2]))?.let(::JsonPrimitive) ?: JsonNull
        "attrs" -> HtmlTagParsing.toAttr(attrs(a[1])).let { buildJsonArray { add(it.identifier); add(JsonArray(it.classes.map(::JsonPrimitive))); add(attrs(it.keyValues)) } }
        "string-attrs" -> attrs(HtmlTagParsing.toStringAttr(attrs(a[1])))
        "show" -> JsonPrimitive(HtmlTagMatching.show(tag(a[1])))
        "run" -> observe(a)
        "unicode-space", "unicode-show" -> {
            val digest = MessageDigest.getInstance("SHA-256")
            fun word(n: Int) { digest.update(byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte())) }
            val start = a[1].jsonPrimitive.int; val count = a[2].jsonPrimitive.int
            for (c in start until start + count) {
                if (text(a[0]) == "unicode-space") digest.update(if (HtmlTagParsing.isSpace(c)) 1.toByte() else 0.toByte())
                else {
                    val s = TextChars.fromPoints(intArrayOf(c))
                    for (t in listOf(HtmlTag.Open(s, listOf(s to s)), HtmlTag.Close(s), HtmlTag.Text(s), HtmlTag.Comment(s), HtmlTag.Warning(s))) {
                        val shown = HtmlTagMatching.show(t); word(shown.codePointCount(0, shown.length)); shown.codePoints().forEach(::word)
                    }
                }
            }
            JsonPrimitive(digest.digest().toHexString())
        }
        else -> error("request")
    }
    @TestFactory
    fun originalHtmlParsingObservations(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/html-parsing/data.jsonl.gz"))).bufferedReader().use { reader ->
        reader.readLine(); reader.lineSequence().mapIndexed { i, line -> var transport: String? = line
            DynamicTest.dynamicTest("HTML token parser ${i + 1}") {
                val c = Json.parseToJsonElement(checkNotNull(transport)).jsonObject; transport = null
                assertLogJsonEquals(c.getValue("expected"), run(c.getValue("request").jsonArray))
            }
        }.toList()
    }
    @Test
    fun tokenStreamsOwnTheirInputAndPermitNullableMatchesAndState() {
        val source = mutableListOf("first", "second")
        val stream = ListTokenStream(source); source.clear()
        val state = TokenParseState(stream, SourcePos("owned"), "initial" as String?)
        val parser = TokenParsec.tokenPrimEx<String, String?, String?>({ it }, { p, _, _ -> p }, { _, _, _, _ -> null }, { TokenMatch.Accept(null) })
        val result = parser.run(state)
        assertTrue(result.success); assertTrue(result.consumed); assertNull(result.value); assertNull(result.state.user)
        assertEquals("second", result.state.input.uncons()?.token); assertEquals("first", state.input.uncons()?.token); assertEquals("initial", state.user)
    }
}
