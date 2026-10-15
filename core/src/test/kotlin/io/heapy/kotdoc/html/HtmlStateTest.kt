package io.heapy.kotdoc.html

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.tex.*
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.parsing.SourcePos
import io.heapy.kotdoc.uri.URI
import io.heapy.kotdoc.uri.URIAuth
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
class HtmlStateTest {
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
    private fun raw(x: JsonElement) = CharacterString(points(x))
    private fun rv(s: CharacterString) = JsonArray(s.points().map(::JsonPrimitive))
    private fun uri(x: JsonElement): URI = x.jsonArray.let { URI(raw(it[0]), it[1].takeUnless { a -> a == JsonNull }?.jsonArray?.let { a -> URIAuth(raw(a[0]), raw(a[1]), raw(a[2])) }, raw(it[2]), raw(it[3]), raw(it[4])) }
    private fun uv(u: URI) = a(rv(u.scheme), u.authority?.let { a(rv(it.userInfo), rv(it.regName), rv(it.port)) } ?: JsonNull, rv(u.path), rv(u.query), rv(u.fragment))
    private fun local(x: JsonElement) = x.jsonArray.let { HtmlLocal(quote(it[0]), it[1].b(), it[2].b(), it[3].b()) }
    private fun localValue(x: HtmlLocal) = a(JsonPrimitive(x.quoteContext.ordinal), JsonPrimitive(x.inChapter), JsonPrimitive(x.inPlain), JsonPrimitive(x.inListItem))
    private fun localChange(s: HtmlLocal, v: JsonElement): HtmlLocal = v.jsonArray.let { x -> when (x[0].s()) {
        "quote" -> s.copy(quoteContext = quote(x[1])); "chapter" -> s.copy(inChapter = x[1].b()); "plain" -> s.copy(inPlain = x[1].b()); "list" -> s.copy(inListItem = x[1].b()); else -> error(v)
    } }
    private fun hlog(x: JsonElement) = LogMessage.SkippedContent(x.s(), SourcePos("html-log", 2, 3))
    private fun hsv(s: HtmlState) = buildJsonObject {
        put("parserState", sv(s.parserState)); put("notes", texts(s.noteTable, ::bv)); put("notePositions", texts(s.noteRefPos, ::pv))
        put("baseHref", s.baseHref?.let(::uv) ?: JsonNull); put("identifiers", JsonArray(HtmlStateAccess.extractIdentifierList(s).sortedWith(UnicodeTextOrder).map(::JsonPrimitive)))
        put("logMessages", JsonArray(s.logMessages.map(::lv))); put("macros", texts(HtmlStateAccess.extractMacros(s)) { JsonPrimitive(it.show()) })
        put("readerOpts", s.readerOpts.toJson()); put("inFootnotes", s.inFootnotes); put("inPre", s.inPre); put("iframeDepth", s.iframeDepth)
    }
    private fun hchange(s: HtmlState, v: JsonElement): HtmlState = v.jsonArray.let { x -> when (x[0].s()) {
        "nested" -> s.copy(parserState = change(s.parserState, x[1]))
        "note" -> s.copy(noteTable = s.noteTable + (x[1].s() to blocks(x[2])))
        "note-pos" -> s.copy(noteRefPos = s.noteRefPos + (x[1].s() to pos(x[2])))
        "base" -> s.copy(baseHref = x[1].takeUnless { it == JsonNull }?.let(::uri))
        "identifier" -> HtmlStateAccess.updateIdentifierList({ it + x[1].s() }, s)
        "clear-identifiers" -> HtmlStateAccess.updateIdentifierList({ emptySet() }, s)
        "macro" -> HtmlStateAccess.updateMacros({ it + (x[1].s() to change(ParserState.default, v).macros.getValue(x[1].s())) }, s)
        "clear-macros" -> HtmlStateAccess.updateMacros({ emptyMap() }, s)
        "log" -> HtmlStateAccess.addLogMessage(hlog(x[1]), s)
        "reader-opts" -> s.copy(readerOpts = ReaderOptions.fromJson(x[1]))
        "options" -> s.copy(parserState = s.parserState.copy(options = ReaderOptions.fromJson(x[1])))
        "set-meta" -> s.setMeta(x[1].s(), PandocJson.decodeMetaValue(x[2])); "delete-meta" -> s.deleteMeta(x[1].s())
        "position" -> HtmlStateAccess.setLastStrPos(x[1].takeUnless { it == JsonNull }?.let(::pos), s)
        "footnotes" -> s.copy(inFootnotes = x[1].b()); "pre" -> s.copy(inPre = x[1].b()); "depth" -> s.copy(iframeDepth = x[1].n())
        else -> error(v)
    } }
    private fun core(v: JsonElement, effects: PandocStateContext): HtmlReaderCharacterParser<JsonElement> {
        val x = v.jsonArray
        fun go(v: JsonElement) = ReaderParsec.defer { core(v, effects) }
        fun <A> lift(p: Parsec<HtmlState, A>): HtmlReaderCharacterParser<A> = ReaderParsec.lift(p)
        val access = HtmlStateAccess
        return when (x[0].s()) {
            "trace" -> ReaderParsec.asksValue<HtmlLocal, HtmlState, HtmlLocal> { it }.flatMap { c ->
                lift(Parsec.lift { effects.report(LogMessage.SkippedContent(traceString(c), SourcePos("trace", 2, 3))); localValue(c) })
            }
            "ask-ignored" -> ReaderParsec.askDeferred<HtmlLocal, HtmlState>().map { JsonPrimitive("ignored") }
            "quote-ignored" -> HtmlReaderContext.quoteContextDeferred().map { JsonPrimitive("ignored") }
            "deep" -> {
                var p: HtmlReaderCharacterParser<JsonElement> = ReaderParsec.asksValue { localValue(it) }
                repeat(x[2].jsonPrimitive.int) { p = when (x[1].s()) {
                    "map" -> p.map { it }; "bind" -> p.flatMap { ReaderParsec.pure(it) }
                    "then" -> ReaderParsec.pure<HtmlLocal, HtmlState, JsonElement>(JsonNull).then(p)
                    "before" -> p.before(ReaderParsec.pure<HtmlLocal, HtmlState, JsonElement>(JsonNull))
                    "local" -> p.localValue { it }; else -> error(v)
                } }; p
            }
            "pure" -> ReaderParsec.pure(x[1])
            "state" -> lift(Parsec.state<HtmlState>().map { hsv(it.user) })
            "local" -> ReaderParsec.asksValue { localValue(it) }
            "asks-constant" -> ReaderParsec.asks { JsonPrimitive("ignored") }
            "scope" -> go(x[2]).localValue { x[1].jsonArray.fold(it, ::localChange) }
            "scope-fail" -> go(x[1]).local { Deferred.later { error("local environment demanded") } }
            "quote" -> HtmlReaderContext.getQuoteContext().map { JsonPrimitive(it.name) }
            "with-quote" -> HtmlReaderContext.withQuoteContext(quote(x[1]), go(x[2]))
            "fail-in-quote" -> HtmlReaderContext.failIfInQuoteContext(quote(x[1])).map { JsonNull }
            "change" -> lift(Parsec.modify<HtmlState> { it.copy(user = hchange(it.user, x[1])) }).map { JsonNull }
            "consume" -> lift(Parsec.satisfy<HtmlState> { true }).map { JsonPrimitive(Parsec.showChar(it)) }
            "set-position" -> lift(Parsec.modify<HtmlState> { it.copy(position = pos(x[1])) }).map { JsonNull }
            "get-last" -> lift(Parsec.state<HtmlState>().map { access.getLastStrPos(it.user)?.let(::pv) ?: JsonNull })
            "update-last" -> lift(ParserCapabilities.updateLastStrPos(access)).map { JsonNull }
            "boundary" -> lift(ParserCapabilities.notAfterString(access)).map(::JsonPrimitive)
            "get-options" -> lift(access.getOption { it }).map { it.toJson() }
            "guard" -> lift(if (x[1].b()) ReaderOptionGuards.guardEnabled(access, Extension.read(x[2].s())) else ReaderOptionGuards.guardDisabled(access, Extension.read(x[2].s()))).map { JsonNull }
            "log" -> lift(ParserCapabilities.logMessage(access, hlog(x[1]))).map { JsonNull }
            "get-logs" -> lift(Parsec.state<HtmlState>().map { JsonArray(access.getLogMessages(it.user).map(::lv)) })
            "report" -> lift(ParserCapabilities.reportLogMessages(access, effects)).map { JsonNull }
            "fail" -> ReaderParsec.fail(x[1].s()); "zero" -> ReaderParsec.zero()
            "attempt" -> go(x[1]).attempt(); "look" -> go(x[1]).lookAhead(); "label" -> go(x[2]).label(x[1].s())
            "or" -> go(x[1]) or go(x[2])
            "sequence" -> x[1].jsonArray.fold(ReaderParsec.pure<HtmlLocal, HtmlState, List<JsonElement>>(emptyList())) { p, value -> p.flatMap { list -> go(value).map { list + it } } }.map(::JsonArray)
            "before" -> go(x[1]).before(go(x[2]))
            "many" -> go(x[1]).many().map(::JsonArray); "many1" -> go(x[1]).many1().map(::JsonArray)
            "skip-many" -> go(x[1]).skipMany().map { JsonNull }; "optional" -> go(x[1]).optional().map { JsonNull }; "option" -> go(x[2]).option(x[1])
            "eof" -> lift(Parsec.eof<HtmlState>()).map { JsonNull }
            "nest" -> if (x[1].n() == 0L) ReaderParsec.asksValue { localValue(it) } else
                HtmlReaderContext.withQuoteContext(QuoteContext.entries[(x[1].n() % 3).toInt()], go(a(JsonPrimitive("nest"), JsonPrimitive(x[1].n() - 1)))).flatMap { value ->
                    lift(Parsec.modify<HtmlState> { it.copy(user = it.user.copy(iframeDepth = it.user.iframeDepth + 1)) }).map { value }
                }
            else -> error(v)
        }
    }
    private fun tokenCore(v: JsonElement, effects: PandocStateContext): HtmlReaderParser<Long, JsonElement> {
        val x = v.jsonArray
        fun go(v: JsonElement) = ReaderTokenParsec.defer { tokenCore(v, effects) }
        fun <A> lift(p: TokenParsec<Long, HtmlState, A>): HtmlReaderParser<Long, A> = ReaderTokenParsec.lift(p)
        val access = HtmlStateAccess
        return when (x[0].s()) {
            "trace" -> ReaderTokenParsec.asksValue<HtmlLocal, Long, HtmlState, HtmlLocal> { it }.flatMap { c ->
                lift(TokenParsec.lift { effects.report(LogMessage.SkippedContent(traceString(c), SourcePos("trace", 2, 3))); localValue(c) })
            }
            "ask-ignored" -> ReaderTokenParsec.askDeferred<HtmlLocal, Long, HtmlState>().map { JsonPrimitive("ignored") }
            "quote-ignored" -> HtmlReaderContext.tokenQuoteContextDeferred<Long>().map { JsonPrimitive("ignored") }
            "deep" -> {
                var p: HtmlReaderParser<Long, JsonElement> = ReaderTokenParsec.asksValue { localValue(it) }
                repeat(x[2].jsonPrimitive.int) { p = when (x[1].s()) {
                    "map" -> p.map { it }; "bind" -> p.flatMap { ReaderTokenParsec.pure(it) }
                    "then" -> ReaderTokenParsec.pure<HtmlLocal, Long, HtmlState, JsonElement>(JsonNull).then(p)
                    "before" -> p.before(ReaderTokenParsec.pure<HtmlLocal, Long, HtmlState, JsonElement>(JsonNull))
                    "local" -> p.localValue { it }; else -> error(v)
                } }; p
            }
            "pure" -> ReaderTokenParsec.pure(x[1])
            "state" -> lift(TokenParsec.state<Long, HtmlState>().map { hsv(it.user) })
            "local" -> ReaderTokenParsec.asksValue { localValue(it) }
            "asks-constant" -> ReaderTokenParsec.asks { JsonPrimitive("ignored") }
            "scope" -> go(x[2]).localValue { x[1].jsonArray.fold(it, ::localChange) }
            "scope-fail" -> go(x[1]).local { Deferred.later { error("local environment demanded") } }
            "quote" -> HtmlReaderContext.tokenQuoteContext<Long>().map { JsonPrimitive(it.name) }
            "with-quote" -> HtmlReaderContext.withTokenQuoteContext(quote(x[1]), go(x[2]))
            "fail-in-quote" -> HtmlReaderContext.failIfInTokenQuoteContext<Long>(quote(x[1])).map { JsonNull }
            "change" -> lift(TokenParsec.modify<Long, HtmlState> { it.copy(user = hchange(it.user, x[1])) }).map { JsonNull }
            "consume" -> lift(TokenParsec.tokenPrim<Long, HtmlState, JsonElement>({ it.toString() }, { p, _, rest -> var n = 0L; var tail = rest; while (true) { val step = tail.uncons() ?: break; n++; tail = step.tail }; p.copy(line = p.line + n, column = p.column + 1) }, { TokenMatch.Accept(JsonPrimitive(it.toString())) }))
            "set-position" -> lift(TokenParsec.modify<Long, HtmlState> { it.copy(position = pos(x[1])) }).map { JsonNull }
            "get-last" -> lift(TokenParsec.state<Long, HtmlState>().map { access.getLastStrPos(it.user)?.let(::pv) ?: JsonNull })
            "update-last" -> lift(TokenParserCapabilities.updateLastStrPos<Long, HtmlState>(access)).map { JsonNull }
            "boundary" -> lift(TokenParserCapabilities.notAfterString<Long, HtmlState>(access)).map(::JsonPrimitive)
            "get-options" -> lift(access.tokenOptions<Long>().getOption { it }).map { it.toJson() }
            "guard" -> lift(if (x[1].b()) TokenParserCapabilities.guardEnabled(access.tokenOptions<Long>(), Extension.read(x[2].s())) else TokenParserCapabilities.guardDisabled(access.tokenOptions<Long>(), Extension.read(x[2].s()))).map { JsonNull }
            "log" -> lift(TokenParserCapabilities.logMessage<Long, HtmlState>(access, hlog(x[1]))).map { JsonNull }
            "get-logs" -> lift(TokenParsec.state<Long, HtmlState>().map { JsonArray(access.getLogMessages(it.user).map(::lv)) })
            "report" -> lift(TokenParserCapabilities.reportLogMessages<Long, HtmlState>(access, effects)).map { JsonNull }
            "fail" -> ReaderTokenParsec.fail(x[1].s()); "zero" -> ReaderTokenParsec.zero()
            "attempt" -> go(x[1]).attempt(); "look" -> go(x[1]).lookAhead(); "label" -> go(x[2]).label(x[1].s())
            "or" -> go(x[1]) or go(x[2])
            "sequence" -> x[1].jsonArray.fold(ReaderTokenParsec.pure<HtmlLocal, Long, HtmlState, List<JsonElement>>(emptyList())) { p, value -> p.flatMap { list -> go(value).map { list + it } } }.map(::JsonArray)
            "before" -> go(x[1]).before(go(x[2]))
            "many" -> go(x[1]).many().map(::JsonArray); "many1" -> go(x[1]).many1().map(::JsonArray)
            "skip-many" -> go(x[1]).skipMany().map { JsonNull }; "optional" -> go(x[1]).optional().map { JsonNull }; "option" -> go(x[2]).option(x[1])
            "eof" -> lift(TokenParsec.eof<Long, HtmlState> { it.toString() }).map { JsonNull }
            "nest" -> if (x[1].n() == 0L) ReaderTokenParsec.asksValue { localValue(it) } else
                HtmlReaderContext.withTokenQuoteContext(QuoteContext.entries[(x[1].n() % 3).toInt()], go(a(JsonPrimitive("nest"), JsonPrimitive(x[1].n() - 1)))).flatMap { value ->
                    lift(TokenParsec.modify<Long, HtmlState> { it.copy(user = it.user.copy(iframeDepth = it.user.iframeDepth + 1)) }).map { value }
                }
            else -> error(v)
        }
    }
    private fun traceString(c: HtmlLocal) = c.quoteContext.name + ":" + listOf(c.inChapter, c.inPlain, c.inListItem).joinToString(":") { if (it) "True" else "False" }
    private fun response(v: JsonElement): JsonElement {
        val x = v.jsonArray
        return when (x[0].s()) {
            "initial" -> hsv(HtmlState.initial(ReaderOptions.fromJson(x[1]), x[2].n()))
            "state" -> hsv(x[1].jsonArray.fold(HtmlState.initial(ReaderOptions()), ::hchange))
            "native-state" -> {
                val doc = NativeReader.read(x[1].s())
                hsv(HtmlState.initial(ReaderOptions()).copy(parserState = ParserState(meta = doc.meta), noteTable = mapOf("book" to Blocks(doc.blocks)), noteRefPos = mapOf("book" to SourcePos("native"))))
            }
            "uri-unicode" -> {
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(4)
                fun number(n: Int) { buffer[0] = (n ushr 24).toByte(); buffer[1] = (n ushr 16).toByte(); buffer[2] = (n ushr 8).toByte(); buffer[3] = n.toByte(); digest.update(buffer) }
                repeat(x[2].jsonPrimitive.int) { offset ->
                    val cp = x[1].jsonPrimitive.int + offset; val c = CharacterString(intArrayOf(cp))
                    val u = URI(c, URIAuth(CharacterString(intArrayOf(117, cp, 58, cp, 64)), c, CharacterString(intArrayOf(58, cp))), c, CharacterString(intArrayOf(63, cp)), CharacterString(intArrayOf(35, cp)))
                    val outputs = listOf(u.showCharacters().points(), u.uriToString({ it }).points(), URIAuth(c, c, c).show().codePoints().toArray())
                    for (output in outputs) { number(output.size); output.forEach(::number) }
                }
                JsonPrimitive(digest.digest().toHexString())
            }
            "uri" -> uri(x[1]).let { u -> buildJsonObject {
                put("parts", uv(u)); put("show", rv(u.showCharacters(x[2].n()))); put("full", rv(u.uriToString({ it })))
                put("authShow", u.authority?.let { JsonPrimitive(it.show(x[2].n())) } ?: JsonNull); put("rectified", uv(u.rectify())); put("absolute", u.isAbsolute()); put("relative", u.isRelative())
            } }
            "uri-compare" -> { val left = uri(x[1]); val right = uri(x[2]); buildJsonObject {
                put("compare", when { left < right -> "LT"; left > right -> "GT"; else -> "EQ" }); put("equal", left == right); put("min", uv(if (left <= right) left else right)); put("max", uv(if (left <= right) right else left))
            } }
            "uri-map" -> { val map: (CharacterString) -> CharacterString = when (x[1].s()) {
                "identity" -> { v -> v }; "empty" -> { _ -> CharacterString(intArrayOf()) }; "constant" -> { _ -> CharacterString("mapped".codePoints().toArray()) }; "fail" -> { _ -> error("user info demanded") }; else -> error(v)
            }; rv(uri(x[2]).uriToString(map, raw(x[3]))) }
            "run" -> {
                val effects = PandocStateContext(); val user = x[5].jsonArray.fold(HtmlState.initial(ReaderOptions()), ::hchange); val env = local(x[4])
                if (x[1].s() == "characters") {
                    val r = core(x[6], effects).run(ParseState(ParseInput(CharacterListStream(points(x[2]))), pos(x[3]), user), env)
                    buildJsonObject {
                        put("ok", r.success); put("consumed", r.consumed); put("error", err(r.error)); put("effects", JsonArray(effects.getLog().map(::lv))); put("environment", localValue(env))
                        if (r.success) {
                            put("value", checkNotNull(r.value)); put("position", pv(r.state.position)); put("user", hsv(r.state.user))
                            val rest = mutableListOf<JsonElement>(); var input = r.state.input
                            while (input.point != null) { rest.add(JsonPrimitive(Parsec.showChar(checkNotNull(input.point)))); input = input.tail() }
                            put("remaining", JsonArray(rest))
                        }
                    }
                } else {
                    val r = tokenCore(x[6], effects).run(TokenParseState(ListTokenStream(x[2].jsonArray.map { it.n() }), pos(x[3]), user), env)
                    buildJsonObject {
                        put("ok", r.success); put("consumed", r.consumed); put("error", err(r.error)); put("effects", JsonArray(effects.getLog().map(::lv))); put("environment", localValue(env))
                        if (r.success) {
                            put("value", checkNotNull(r.value)); put("position", pv(r.state.position)); put("user", hsv(r.state.user))
                            val rest = mutableListOf<JsonElement>(); var input = r.state.input
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
        GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/html-state/data.jsonl.gz"))).bufferedReader().use { reader ->
            reader.lineSequence().drop(1).mapIndexed { index, line -> DynamicTest.dynamicTest("original HTML state $index") {
                val c = Json.parseToJsonElement(line).jsonObject
                val result = try { response(c.getValue("request")) } catch (e: IllegalStateException) { buildJsonObject { put("exception", checkNotNull(e.message)) } }
                assertLogJsonEquals(c.getValue("expected"), result, "case $index: ${c.getValue("request")}")
                if ("literalExpected" in c) assertLogJsonEquals(c.getValue("literalExpected"), result.jsonObject.getValue(c.getValue("literalField").s()))
            } }.toList()
        }
}
