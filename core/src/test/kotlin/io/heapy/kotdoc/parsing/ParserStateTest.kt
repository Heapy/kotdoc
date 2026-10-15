package io.heapy.kotdoc.parsing

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.tex.*
import io.heapy.kotdoc.parsing.SourcePos
import io.heapy.kotdoc.formats.NativeReader
import io.heapy.kotdoc.json.PrettyJson
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.logging.Logging
import io.heapy.kotdoc.logging.assertLogJsonEquals
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.state.PandocStateContext
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/** JSON action/observation transport; expected values are captured from original Pandoc. */
class ParserStateTest {
    private fun JsonElement.s() = TextChars.fromString(jsonPrimitive.content)
    private fun JsonElement.n() = jsonPrimitive.long
    private fun JsonElement.b() = jsonPrimitive.boolean
    private fun a(vararg x: JsonElement) = JsonArray(x.toList())
    private fun points(x: JsonElement) = x.jsonArray.map { it.jsonPrimitive.int }.toIntArray()
    private fun pv(p: SourcePos) = a(JsonArray(p.nameRaw.points().map(::JsonPrimitive)), JsonPrimitive(p.line), JsonPrimitive(p.column))
    private fun pos(x: JsonElement): SourcePos = x.jsonArray.let { SourcePos(CharacterString(points(it[0])), it[1].n(), it[2].n()) }
    private fun quote(x: JsonElement) = QuoteContext.entries[x.jsonPrimitive.int]
    private fun blocks(x: JsonElement) = Blocks(x.jsonArray.map(PandocJson::decodeBlock))
    private fun bv(x: Blocks) = JsonArray(x.values.map(PandocJson::encodeBlock))
    private fun log(x: JsonElement) = LogMessage.SkippedContent(x.s(), SourcePos("log", 2, 3))
    private fun lv(x: LogMessage) = Json.parseToJsonElement(PrettyJson.encode(Logging.toValue(x)))
    private fun <K, V> pairs(m: Map<K, V>, compare: Comparator<K>, key: (K) -> JsonElement, value: (V) -> JsonElement) =
        JsonArray(m.keys.sortedWith(compare).map { a(key(it), value(m.getValue(it))) })
    private fun <V> texts(m: Map<String, V>, value: (V) -> JsonElement) = pairs(m, UnicodeTextOrder, ::JsonPrimitive, value)
    private fun sv(s: ParserState): JsonObject = buildJsonObject {
        put("options", s.options.toJson()); put("parserContext", s.parserContext.name); put("quoteContext", s.quoteContext.name)
        put("allowLinks", s.allowLinks); put("allowLineBreaks", s.allowLineBreaks); put("lastStrPos", s.lastStrPos?.let(::pv) ?: JsonNull)
        fun keys(m: KeyTable) = pairs(m, naturalOrder(), { JsonPrimitive(it.text) }, { a(PandocJson.encodeTarget(it.first), PandocJson.encodeAttr(it.second)) })
        put("keys", keys(s.keys)); put("headerKeys", keys(s.headerKeys))
        put("substitutions", pairs(s.substitutions, naturalOrder(), { JsonPrimitive(it.text) }, ::bv))
        put("notes", JsonArray(s.notes.map { a(JsonPrimitive(it.first), JsonPrimitive(it.second)) }))
        put("parsedNotes", JsonArray(s.parsedNotes.keys.sortedWith(UnicodeTextOrder).map { k ->
            val (p, f) = s.parsedNotes.getValue(k); a(JsonPrimitive(k), pv(p), bv(f.runF(s)))
        }))
        put("noteRefs", JsonArray(s.noteRefs.sortedWith(UnicodeTextOrder).map(::JsonPrimitive)))
        put("inNote", s.inNote); put("noteNumber", s.noteNumber); put("meta", PandocJson.encodeMeta(s.meta)); put("futureMeta", PandocJson.encodeMeta(s.futureMeta.runF(s)))
        put("citations", texts(s.citations, ::JsonPrimitive)); put("headerTable", JsonArray(s.headerTable.map { JsonPrimitive(it.show()) }))
        put("identifiers", JsonArray(ParserStateAccess.extractIdentifierList(s).sortedWith(UnicodeTextOrder).map(::JsonPrimitive)))
        put("nextExample", s.nextExample); put("examples", texts(s.examples, ::JsonPrimitive)); put("macros", texts(ParserStateAccess.extractMacros(s)) { JsonPrimitive(it.show()) })
        put("rstDefaultRole", s.rstDefaultRole); put("rstHighlight", s.rstHighlight?.let(::JsonPrimitive) ?: JsonNull)
        put("rstCustomRoles", texts(s.rstCustomRoles) { a(JsonPrimitive(it.first), it.second?.let(::JsonPrimitive) ?: JsonNull, PandocJson.encodeAttr(it.third)) })
        put("caption", s.caption?.let { JsonArray(it.values.map(PandocJson::encodeInline)) } ?: JsonNull)
        put("inHtmlBlock", s.inHtmlBlock?.let(::JsonPrimitive) ?: JsonNull); put("fencedDivLevel", s.fencedDivLevel)
        put("containers", JsonArray(s.containers.map(::JsonPrimitive))); put("logMessages", JsonArray(s.logMessages.map(::lv))); put("markdownAttribute", s.markdownAttribute)
    }
    private fun change(s: ParserState, v: JsonElement): ParserState {
        val x = v.jsonArray
        return when (x[0].s()) {
            "options" -> s.copy(options = ReaderOptions.fromJson(x[1]))
            "quote" -> s.copy(quoteContext = quote(x[1]))
            "context" -> s.copy(parserContext = if (x[1].b()) ParserContext.ListItemState else ParserContext.NullState)
            "allow-links" -> s.copy(allowLinks = x[1].b()); "allow-line-breaks" -> s.copy(allowLineBreaks = x[1].b())
            "position" -> ParserStateAccess.setLastStrPos(x[1].takeUnless { it == JsonNull }?.let(::pos), s)
            "key" -> s.copy(keys = s.keys + (toKey(x[1].s()) to (PandocJson.decodeTarget(x[2]) to PandocJson.decodeAttr(x[3]))))
            "header-key" -> s.copy(headerKeys = s.headerKeys + (toKey(x[1].s()) to (PandocJson.decodeTarget(x[2]) to PandocJson.decodeAttr(x[3]))))
            "substitution" -> s.copy(substitutions = s.substitutions + (toKey(x[1].s()) to blocks(x[2])))
            "note" -> s.copy(notes = s.notes + (x[1].s() to x[2].s()))
            "parsed-note" -> s.copy(parsedNotes = s.parsedNotes + (x[1].s() to (pos(x[2]) to Future.pure(blocks(x[3])))))
            "parsed-note-state" -> s.copy(parsedNotes = s.parsedNotes + (x[1].s() to (pos(x[2]) to Future.asksValue { Builder.plain(Builder.str(it.rstDefaultRole)) })))
            "note-ref" -> s.copy(noteRefs = s.noteRefs + x[1].s()); "in-note" -> s.copy(inNote = x[1].b()); "note-number" -> s.copy(noteNumber = x[1].n())
            "set-meta" -> s.setMeta(x[1].s(), PandocJson.decodeMetaValue(x[2])); "delete-meta" -> s.deleteMeta(x[1].s())
            "future-meta" -> s.copy(futureMeta = Future.pure(PandocJson.decodeMeta(x[1]))); "future-meta-state" -> s.copy(futureMeta = Future.asksValue { it.meta })
            "citation" -> s.copy(citations = s.citations + (x[1].s() to x[2].s()))
            "header" -> s.copy(headerTable = s.headerTable + if (x[1].b()) HeaderType.DoubleHeader(x[2].jsonPrimitive.int) else HeaderType.SingleHeader(x[2].jsonPrimitive.int))
            "identifier" -> ParserStateAccess.updateIdentifierList({ it + x[1].s() }, s); "clear-identifiers" -> ParserStateAccess.updateIdentifierList({ emptySet() }, s)
            "next-example" -> s.copy(nextExample = x[1].n()); "example" -> s.copy(examples = s.examples + (x[1].s() to x[2].n()))
            "macro" -> {
                val token = Tok(SourcePos("macro", -1, 0), TokType.CtrlSeq(x[1].s()), x[1].s())
                val macro = Macro(MacroScope.GlobalScope, ExpansionPoint.ExpandWhenUsed, listOf(ArgSpec.ArgNum(x[2].n()), ArgSpec.Pattern(listOf(token))), emptyList(), listOf(token))
                ParserStateAccess.updateMacros({ it + (x[1].s() to macro) }, s)
            }
            "clear-macros" -> ParserStateAccess.updateMacros({ emptyMap() }, s)
            "role" -> s.copy(rstDefaultRole = x[1].s()); "highlight" -> s.copy(rstHighlight = x[1].takeUnless { it == JsonNull }?.s())
            "custom-role" -> x[2].jsonArray.let { s.copy(rstCustomRoles = s.rstCustomRoles + (x[1].s() to Triple(it[0].s(), it[1].takeUnless { it == JsonNull }?.s(), PandocJson.decodeAttr(it[2])))) }
            "caption" -> s.copy(caption = x[1].takeUnless { it == JsonNull }?.jsonArray?.map(PandocJson::decodeInline)?.let(::Inlines))
            "html-block" -> s.copy(inHtmlBlock = x[1].takeUnless { it == JsonNull }?.s()); "fenced-level" -> s.copy(fencedDivLevel = x[1].n())
            "include" -> ParserStateAccess.addIncludeFile(x[1].s(), s); "drop-include" -> ParserStateAccess.dropLatestIncludeFile(s)
            "log" -> ParserStateAccess.addLogMessage(log(x[1]), s); "markdown" -> s.copy(markdownAttribute = x[1].b())
            else -> error(v)
        }
    }
    private fun err(e: ParseError) = buildJsonObject {
        put("position", pv(e.position)); put("characters", JsonArray(e.showCharacters().points().map(::JsonPrimitive)))
        put("messages", JsonArray(e.messages.sortedBy { it.kind }.map { a(JsonPrimitive(it.kind.ordinal), JsonPrimitive(it.text)) }))
    }
    private fun core(v: JsonElement, effects: PandocStateContext): Parsec<ParserState, JsonElement> {
        val x = v.jsonArray
        fun go(v: JsonElement) = Parsec.defer { core(v, effects) }
        val access = ParserStateAccess
        return when (x[0].s()) {
            "nest-quotes" -> if (x[1].n() == 0L) access.getQuoteContext().map { JsonPrimitive(it.name) } else
                access.withQuoteContext(QuoteContext.entries[(x[1].n() % 3).toInt()], go(a(JsonPrimitive("nest-quotes"), JsonPrimitive(x[1].n() - 1)))).flatMap { value ->
                    Parsec.modify<ParserState> { it.copy(user = it.user.copy(nextExample = it.user.nextExample + 1)) }.map { value }
                }
            "pure" -> Parsec.pure(x[1])
            "state" -> Parsec.state<ParserState>().map { sv(it.user) }
            "change" -> Parsec.modify<ParserState> { it.copy(user = change(it.user, x[1])) }.map { JsonNull }
            "consume" -> Parsec.satisfy<ParserState> { true }.map { JsonPrimitive(Parsec.showChar(it)) }
            "set-position" -> Parsec.modify<ParserState> { it.copy(position = pos(x[1])) }.map { JsonNull }
            "update-last" -> ParserCapabilities.updateLastStrPos(access).map { JsonNull }
            "boundary" -> ParserCapabilities.notAfterString(access).map(::JsonPrimitive)
            "get-quote" -> access.getQuoteContext().map { JsonPrimitive(it.name) }
            "with-quote" -> access.withQuoteContext(quote(x[1]), go(x[2]))
            "fail-in-quote" -> ParserCapabilities.failIfInQuoteContext(access, quote(x[1])).map { JsonNull }
            "get-columns" -> access.getOption { it.columns }.map(::JsonPrimitive)
            "guard" -> (if (x[1].b()) ReaderOptionGuards.guardEnabled(access, Extension.read(x[2].s())) else ReaderOptionGuards.guardDisabled(access, Extension.read(x[2].s()))).map { JsonNull }
            "log" -> ParserCapabilities.logMessage(access, log(x[1])).map { JsonNull }
            "get-logs" -> Parsec.state<ParserState>().map { JsonArray(access.getLogMessages(it.user).map(::lv)) }
            "report" -> ParserCapabilities.reportLogMessages(access, effects).map { JsonNull }
            "get-includes" -> Parsec.state<ParserState>().map { JsonArray(access.getIncludeFiles(it.user).map(::JsonPrimitive)) }
            "fail" -> Parsec.fail(x[1].s()); "zero" -> Parsec.zero()
            "attempt" -> go(x[1]).attempt(); "look" -> go(x[1]).lookAhead(); "label" -> go(x[2]).label(x[1].s())
            "or" -> go(x[1]) or go(x[2])
            "sequence" -> x[1].jsonArray.fold(Parsec.pure<ParserState, List<JsonElement>>(emptyList())) { p, value -> p.flatMap { list -> go(value).map { list + it } } }.map(::JsonArray)
            "before" -> go(x[1]).before(go(x[2]))
            else -> error(v)
        }
    }
    private fun tokenCore(v: JsonElement, effects: PandocStateContext): TokenParsec<Long, ParserState, JsonElement> {
        val x = v.jsonArray
        fun go(v: JsonElement) = TokenParsec.defer { tokenCore(v, effects) }
        val access = ParserStateAccess
        return when (x[0].s()) {
            "nest-quotes" -> if (x[1].n() == 0L) ParserStateAccess.tokenQuotes<Long>().getQuoteContext().map { JsonPrimitive(it.name) } else
                ParserStateAccess.tokenQuotes<Long>().withQuoteContext(QuoteContext.entries[(x[1].n() % 3).toInt()], go(a(JsonPrimitive("nest-quotes"), JsonPrimitive(x[1].n() - 1)))).flatMap { value ->
                    TokenParsec.modify<Long, ParserState> { it.copy(user = it.user.copy(nextExample = it.user.nextExample + 1)) }.map { value }
                }
            "pure" -> TokenParsec.pure(x[1])
            "state" -> TokenParsec.state<Long, ParserState>().map { sv(it.user) }
            "change" -> TokenParsec.modify<Long, ParserState> { it.copy(user = change(it.user, x[1])) }.map { JsonNull }
            "consume" -> TokenParsec.tokenPrim<Long, ParserState, JsonElement>({ it.toString() }, { p, _, rest -> var n = 0L; var tail = rest; while (true) { val step = tail.uncons() ?: break; n++; tail = step.tail }; p.copy(line = p.line + n, column = p.column + 1) }, { TokenMatch.Accept(JsonPrimitive(it.toString())) })
            "set-position" -> TokenParsec.modify<Long, ParserState> { it.copy(position = pos(x[1])) }.map { JsonNull }
            "update-last" -> TokenParserCapabilities.updateLastStrPos<Long, ParserState>(access).map { JsonNull }
            "boundary" -> TokenParserCapabilities.notAfterString<Long, ParserState>(access).map(::JsonPrimitive)
            "get-quote" -> ParserStateAccess.tokenQuotes<Long>().getQuoteContext().map { JsonPrimitive(it.name) }
            "with-quote" -> ParserStateAccess.tokenQuotes<Long>().withQuoteContext(quote(x[1]), go(x[2]))
            "fail-in-quote" -> TokenParserCapabilities.failIfInQuoteContext(ParserStateAccess.tokenQuotes<Long>(), quote(x[1])).map { JsonNull }
            "get-columns" -> ParserStateAccess.tokenOptions<Long>().getOption { it.columns }.map(::JsonPrimitive)
            "guard" -> (if (x[1].b()) TokenParserCapabilities.guardEnabled(ParserStateAccess.tokenOptions<Long>(), Extension.read(x[2].s())) else TokenParserCapabilities.guardDisabled(ParserStateAccess.tokenOptions<Long>(), Extension.read(x[2].s()))).map { JsonNull }
            "log" -> TokenParserCapabilities.logMessage<Long, ParserState>(access, log(x[1])).map { JsonNull }
            "get-logs" -> TokenParsec.state<Long, ParserState>().map { JsonArray(access.getLogMessages(it.user).map(::lv)) }
            "report" -> TokenParserCapabilities.reportLogMessages<Long, ParserState>(access, effects).map { JsonNull }
            "get-includes" -> TokenParsec.state<Long, ParserState>().map { JsonArray(access.getIncludeFiles(it.user).map(::JsonPrimitive)) }
            "fail" -> TokenParsec.fail(x[1].s()); "zero" -> TokenParsec.zero()
            "attempt" -> go(x[1]).attempt(); "look" -> go(x[1]).lookAhead(); "label" -> go(x[2]).label(x[1].s())
            "or" -> go(x[1]) or go(x[2])
            "sequence" -> x[1].jsonArray.fold(TokenParsec.pure<Long, ParserState, List<JsonElement>>(emptyList())) { p, value -> p.flatMap { list -> go(value).map { list + it } } }.map(::JsonArray)
            "before" -> go(x[1]).before(go(x[2]))
            else -> error(v)
        }
    }
    private data class Override(val original: ReaderOptions, val alternate: ReaderOptions, val calls: Long, val mode: String)
    private fun overrideRun(original: ReaderOptions, alternate: ReaderOptions, mode: String, wrapper: String, input: List<Long>): JsonElement {
        val access = object : TokenReaderOptionsAccess<Long, Override> {
            override fun extractReaderOptions(state: Override) = state.original
            override fun <A> getOption(select: (ReaderOptions) -> A): TokenParsec<Long, Override, A> = TokenParsec.state<Long, Override>().flatMap { state ->
                TokenParsec.modify<Long, Override> { it.copy(user = it.user.copy(calls = it.user.calls + 1)) }.flatMap {
                    val consume = if (state.user.mode in listOf("consume", "consume-fail")) TokenParsec.anyToken<Long, Override> { "" }.map { Unit } else TokenParsec.pure(Unit)
                    consume.flatMap {
                        if (state.user.mode in listOf("fail", "consume-fail")) TokenParsec.fail("getter failed")
                        else TokenParsec.pure(select(state.user.alternate))
                    }
                }
            }
        }
        val base: TokenParsec<Long, Override, JsonElement> = when (wrapper) {
            "enabled" -> TokenParserCapabilities.guardEnabled(access, Extension.read("smart")).map { JsonNull }
            "disabled" -> TokenParserCapabilities.guardDisabled(access, Extension.read("smart")).map { JsonNull }
            else -> access.getOption { it.columns }.map(::JsonPrimitive)
        }
        val action = when (wrapper) {
            "fallback" -> base or TokenParsec.pure(JsonPrimitive("fallback"))
            "attempt-fallback" -> base.attempt() or TokenParsec.pure(JsonPrimitive("fallback"))
            else -> base
        }
        val r = action.run(TokenParseState(ListTokenStream(input), SourcePos("override"), Override(original, alternate, 0, mode)))
        return buildJsonObject {
            put("ok", r.success); put("consumed", r.consumed); put("error", err(r.error))
            if (r.success) {
                put("value", checkNotNull(r.value))
                put("user", buildJsonObject { put("original", r.state.user.original.toJson()); put("alternate", r.state.user.alternate.toJson()); put("calls", r.state.user.calls); put("mode", r.state.user.mode) })
                put("remaining", JsonArray((r.state.input as ListTokenStream).remaining().map(::JsonPrimitive)))
            }
        }
    }
    private fun response(v: JsonElement): JsonElement {
        val x = v.jsonArray
        return when (x[0].s()) {
            "native-state" -> {
                val doc = NativeReader.read(x[1].s()); val bs = Blocks(doc.blocks)
                sv(ParserState(meta = doc.meta, futureMeta = Future.asksValue { it.meta }, substitutions = mapOf(toKey("[BOOK]") to bs), parsedNotes = mapOf("book" to (SourcePos("native") to Future.pure(bs)))))
            }
            "state" -> sv(x[1].jsonArray.fold(ParserState.default, ::change))
            "header" -> JsonPrimitive((if (x[1].b()) HeaderType.DoubleHeader(x[2].jsonPrimitive.int) else HeaderType.SingleHeader(x[2].jsonPrimitive.int)).show(x[3].n()))
            "override" -> overrideRun(ReaderOptions.fromJson(x[1]), ReaderOptions.fromJson(x[2]), x[3].s(), x[4].s(), x[5].jsonArray.map { it.n() })
            "key" -> toKey(x[1].s()).let { buildJsonObject { put("text", it.text); put("show", it.show(x[2].n())) } }
            "compare" -> { val left = Key(x[1].s()); val right = Key(x[2].s()); a(JsonPrimitive(when { left < right -> "LT"; left > right -> "GT"; else -> "EQ" }), JsonPrimitive(if (left == right) "True" else "False")) }
            "read" -> JsonArray(Key.reads(CharacterString(points(x[1])), x[2].n()).map { (key, rest) -> a(JsonPrimitive(key.text), JsonArray(rest.points().map(::JsonPrimitive))) })
            "read-list" -> JsonArray(Key.readsList(CharacterString(points(x[1]))).map { (keys, rest) -> a(JsonArray(keys.map { JsonPrimitive(it.text) }), JsonArray(rest.points().map(::JsonPrimitive))) })
            "unicode" -> {
                val digest = MessageDigest.getInstance("SHA-256")
                fun number(n: Int) { digest.update(ByteBuffer.allocate(4).putInt(n).array()) }
                repeat(x[2].jsonPrimitive.int) { offset ->
                    val cp = x[1].jsonPrimitive.int + offset
                    val c = TextChars.fromPoints(intArrayOf(cp)); val output = toKey(c + " A " + c).text.codePoints().toArray()
                    number(output.size); output.forEach(::number)
                }
                JsonPrimitive(digest.digest().joinToString("") { "%02x".format(it) })
            }
            "run" -> {
                val effects = PandocStateContext(); val user = x[4].jsonArray.fold(ParserState.default, ::change)
                if (x[1].s() == "characters") {
                    val reply = core(x[5], effects).run(ParseState(ParseInput(CharacterListStream(points(x[2]))), pos(x[3]), user))
                    buildJsonObject {
                        put("ok", reply.success); put("consumed", reply.consumed); put("error", err(reply.error)); put("effects", JsonArray(effects.getLog().map(::lv)))
                        if (reply.success) {
                            put("value", checkNotNull(reply.value)); put("position", pv(reply.state.position)); put("user", sv(reply.state.user))
                            val rest = mutableListOf<JsonElement>(); var input = reply.state.input
                            while (input.point != null) { rest.add(JsonPrimitive(Parsec.showChar(checkNotNull(input.point)))); input = input.tail() }
                            put("remaining", JsonArray(rest))
                        }
                    }
                } else {
                    val reply = tokenCore(x[5], effects).run(TokenParseState(ListTokenStream(x[2].jsonArray.map { it.n() }), pos(x[3]), user))
                    buildJsonObject {
                        put("ok", reply.success); put("consumed", reply.consumed); put("error", err(reply.error)); put("effects", JsonArray(effects.getLog().map(::lv)))
                        if (reply.success) {
                            put("value", checkNotNull(reply.value)); put("position", pv(reply.state.position)); put("user", sv(reply.state.user))
                            val rest = mutableListOf<JsonElement>(); var input = reply.state.input
                            while (true) { val step = input.uncons() ?: break; rest.add(JsonPrimitive(step.token.toString())); input = step.tail }
                            put("remaining", JsonArray(rest))
                        }
                    }
                }
            }
            else -> error(v)
        }
    }
    @TestFactory fun originalObservations(): List<DynamicTest> =
        GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/parsing/state.jsonl.gz"))).bufferedReader().use { reader ->
            reader.lineSequence().drop(1).mapIndexed { index, line -> DynamicTest.dynamicTest("original ParserState $index") {
                val c = Json.parseToJsonElement(line).jsonObject
                assertLogJsonEquals(c.getValue("expected"), response(c.getValue("request")), "case $index: ${c.getValue("request")}")
            } }.toList()
        }
}
