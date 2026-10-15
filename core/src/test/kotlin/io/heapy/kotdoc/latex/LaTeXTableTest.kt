package io.heapy.kotdoc.latex

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.json.PrettyJson
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.logging.Logging
import io.heapy.kotdoc.options.ReaderOptions
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.tex.*
import io.heapy.kotdoc.tex.SourcePos
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.stream.Stream
import kotlin.streams.asStream
import io.heapy.kotdoc.state.PandocStateContext
import io.heapy.kotdoc.state.CommonState
import io.heapy.kotdoc.language.Lang
import io.heapy.kotdoc.translations.Translations
import io.heapy.kotdoc.logging.Verbosity
import io.heapy.kotdoc.state.PandocState
import io.heapy.kotdoc.translations.TranslationRuntime
import io.heapy.kotdoc.translations.translateTerm
import io.heapy.kotdoc.translations.setTranslations
import io.heapy.kotdoc.resources.PandocDataRuntime
import io.heapy.kotdoc.resources.PandocFilePaths
import io.heapy.kotdoc.resources.PandocUtcTime
import java.math.BigInteger
import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.layout.CharacterStringChars

class LaTeXTableTest {
    private fun JsonElement.s() = jsonPrimitive.content
    private fun JsonElement.n() = jsonPrimitive.long
    private fun JsonElement.b() = jsonPrimitive.boolean
    private fun a(vararg values: JsonElement) = JsonArray(values.toList())
    private fun raw(v: JsonElement) = TextChars.fromPoints(v.jsonArray.map { it.jsonPrimitive.int }.toIntArray())
    private fun pt(s: String) = JsonArray(s.codePoints().toArray().map(::JsonPrimitive))
    private fun pos(v: JsonElement): SourcePos = v.jsonArray.let {
        SourcePos(CharacterString(it[0].jsonArray.map { p -> p.jsonPrimitive.int }.toIntArray()), it[1].n(), it[2].n())
    }
    private fun pv(p: SourcePos) = a(JsonArray(p.nameRaw.points().map(::JsonPrimitive)), JsonPrimitive(p.line), JsonPrimitive(p.column))
    private fun tt(v: JsonElement): TokType = v.jsonArray.let { when (it[0].s()) {
        "CtrlSeq" -> TokType.CtrlSeq(raw(it[1])); "Arg" -> TokType.Arg(it[1].n()); "DeferredArg" -> TokType.DeferredArg(it[1].n())
        "Spaces" -> TokType.Spaces; "Newline" -> TokType.Newline; "Symbol" -> TokType.Symbol; "Word" -> TokType.Word
        "Comment" -> TokType.Comment; "Esc1" -> TokType.Esc1; "Esc2" -> TokType.Esc2; else -> error(it)
    } }
    private fun ttv(t: TokType): JsonElement = when (t) {
        is TokType.CtrlSeq -> a(JsonPrimitive("CtrlSeq"), pt(t.text))
        is TokType.Arg -> a(JsonPrimitive("Arg"), JsonPrimitive(t.number))
        is TokType.DeferredArg -> a(JsonPrimitive("DeferredArg"), JsonPrimitive(t.number))
        else -> a(JsonPrimitive(t.show()))
    }
    private fun tok(v: JsonElement) = v.jsonArray.let { Tok(pos(it[0]), tt(it[1]), raw(it[2])) }
    private fun tv(t: Tok) = a(pv(t.position), ttv(t.type), pt(t.text))
    private fun tokens(v: JsonElement) = tokenList(v.jsonArray.map(::tok))
    private fun <A> list(xs: Deferred<DeferredChain<A>>): List<A> {
        var current = xs.value(); val out = ArrayList<A>()
        while (current is DeferredChain.Cons) { out.add(current.head.value()); current = current.tail.value() }
        return out
    }
    private fun tvs(xs: TokenList) = JsonArray(list(xs).map(::tv))
    private fun sources(v: JsonElement) = Sources(v.jsonArray.map { it.jsonArray.let { c -> SourceChunk(pos(c[0]), raw(c[1])) } })
    private fun quote(v: JsonElement) = QuoteContext.entries[v.jsonPrimitive.int]
    private fun theorem(v: JsonElement) = v.jsonArray.let {
        TheoremSpec(tokens(it[0]), TheoremStyle.entries[it[1].jsonPrimitive.int], it[2].takeUnless { x -> x == JsonNull }?.s(),
            it[3].takeUnless { x -> x == JsonNull }?.s(), it[4].b(), DottedNum(it[5].jsonArray.map { n -> n.n() }))
    }
    private fun thv(t: TheoremSpec) = a(tvs(t.name), JsonPrimitive(t.style.name), t.series?.let(::JsonPrimitive) ?: JsonNull,
        t.syncTo?.let(::JsonPrimitive) ?: JsonNull, JsonPrimitive(t.number), JsonPrimitive(t.lastNum.render()))
    private fun macro(k: String, n: Long): Macro {
        val token = Tok(SourcePos("macro", -1, 0), TokType.CtrlSeq(k), k)
        return Macro(MacroScope.GlobalScope, ExpansionPoint.ExpandWhenUsed, listOf(ArgSpec.ArgNum(n), ArgSpec.Pattern(listOf(token))), emptyList(), listOf(token))
    }
    private fun lv(log: LogMessage) = Json.parseToJsonElement(PrettyJson.encode(Logging.toValue(log)))
    private fun <V> pairs(m: Map<String,V>, value: (V) -> JsonElement) = JsonArray(m.keys.sortedWith(UnicodeTextOrder).map { a(JsonPrimitive(it), value(m.getValue(it))) })
    private fun sv(s: LaTeXState) = buildJsonObject {
        put("options", s.options.toJson()); put("meta", PandocJson.encodeMeta(s.meta)); put("quoteContext", s.quoteContext.name)
        put("macros", JsonArray((listOf(s.macros.head) + s.macros.tail).map { pairs(it) { v -> JsonPrimitive(v.show()) } }))
        put("containers", JsonArray(s.containers.map(::JsonPrimitive))); put("logMessages", JsonArray(s.logMessages.map(::lv)))
        put("identifiers", JsonArray(s.identifiers.sortedWith(UnicodeTextOrder).map(::JsonPrimitive)))
        put("verbatimMode", s.verbatimMode); put("mathMode", s.mathMode); put("caption", s.caption?.let(PandocJson::encodeCaption) ?: JsonNull)
        put("inListItem", s.inListItem); put("inTableCell", s.inTableCell)
        put("lastHeaderNum", s.lastHeaderNum.render()); put("lastFigureNum", s.lastFigureNum.render()); put("lastTableNum", s.lastTableNum.render()); put("lastNoteNum", s.lastNoteNum)
        put("footnoteTexts", JsonArray(s.footnoteTexts.keys.sorted().map { a(JsonPrimitive(it), JsonArray(s.footnoteTexts.getValue(it).values.map(PandocJson::encodeBlock))) }))
        put("theoremMap", pairs(s.theoremMap, ::thv)); put("lastTheoremStyle", s.lastTheoremStyle.name); put("lastLabel", s.lastLabel?.let(::JsonPrimitive) ?: JsonNull)
        put("labels", pairs(s.labels) { JsonArray(it.map(PandocJson::encodeInline)) }); put("hasChapters", s.hasChapters)
        put("toggles", pairs(s.toggles, ::JsonPrimitive)); put("fileContents", pairs(s.fileContents, ::JsonPrimitive))
        put("enableWithRaw", s.enableWithRaw); put("rawTokens", tvs(s.rawTokens)); put("rawTokenCount", s.rawTokenCount); put("rawScopes", s.rawScopes)
        put("ligatures", s.ligatures); put("caseExclusions", pairs(s.caseExclusions) { JsonArray(it.sortedWith(UnicodeTextOrder).map(::JsonPrimitive)) })
        put("reportedLogs", JsonArray(LaTeXStateAccess.getLogMessages(s).map(::lv)))
    }
    private fun change(s: LaTeXState, value: JsonElement): LaTeXState {
        val v = value.jsonArray
        return when (v[0].s()) {
            "options" -> s.copy(options = ReaderOptions.fromJson(v[1]))
            "meta" -> s.setMeta(v[1].s(), PandocJson.decodeMetaValue(v[2])); "delete-meta" -> s.deleteMeta(v[1].s())
            "quote" -> s.copy(quoteContext = quote(v[1]))
            "macro" -> LaTeXStateAccess.updateMacros({ it + (v[1].s() to macro(v[1].s(),v[2].n())) }, s)
            "push-macros" -> s.copy(macros = s.macros.push()); "pop-macros" -> s.copy(macros = s.macros.pop())
            "clear-macros" -> LaTeXStateAccess.updateMacros({ emptyMap() }, s)
            "include" -> LaTeXStateAccess.addIncludeFile(v[1].s(), s); "drop-include" -> LaTeXStateAccess.dropLatestIncludeFile(s)
            "log" -> LaTeXStateAccess.addLogMessage(LogMessage.SkippedContent(v[1].s(), SourcePos("log", 2, 3)), s)
            "identifier" -> LaTeXStateAccess.updateIdentifierList({ it + v[1].s() }, s)
            "verbatim" -> s.copy(verbatimMode = v[1].b()); "math" -> s.copy(mathMode = v[1].b())
            "caption" -> s.copy(caption = v[1].takeUnless { it == JsonNull }?.let(PandocJson::decodeCaption))
            "list-item" -> s.copy(inListItem = v[1].b()); "table-cell" -> s.copy(inTableCell = v[1].b())
            "header" -> s.copy(lastHeaderNum = DottedNum(v[1].jsonArray.map { it.n() }))
            "figure" -> s.copy(lastFigureNum = DottedNum(v[1].jsonArray.map { it.n() }))
            "table" -> s.copy(lastTableNum = DottedNum(v[1].jsonArray.map { it.n() }))
            "note-num" -> s.copy(lastNoteNum = v[1].n())
            "footnote" -> s.copy(footnoteTexts = s.footnoteTexts + (v[1].n() to Blocks(v[2].jsonArray.map(PandocJson::decodeBlock))))
            "theorem" -> s.copy(theoremMap = s.theoremMap + (v[1].s() to theorem(v[2])))
            "theorem-style" -> s.copy(lastTheoremStyle = TheoremStyle.entries[v[1].jsonPrimitive.int])
            "last-label" -> s.copy(lastLabel = v[1].takeUnless { it == JsonNull }?.s())
            "label" -> s.copy(labels = s.labels + (v[1].s() to v[2].jsonArray.map(PandocJson::decodeInline)))
            "chapters" -> s.copy(hasChapters = v[1].b()); "toggle" -> s.copy(toggles = s.toggles + (v[1].s() to v[2].b()))
            "file" -> s.copy(fileContents = s.fileContents + (v[1].s() to v[2].s()))
            "enable-raw" -> s.copy(enableWithRaw = v[1].b()); "raw" -> s.copy(rawTokens = tokens(v[1]), rawTokenCount = v[2].n(), rawScopes = v[3].n())
            "ligatures" -> s.copy(ligatures = v[1].b()); "exclusion" -> s.copy(caseExclusions = s.caseExclusions + (v[1].s() to v[2].jsonArray.map { it.s() }.toSet()))
            else -> error(v)
        }
    }
    private fun state(v: JsonElement) = v.jsonArray.fold(LaTeXState(), ::change)
    private fun stateParser(k: LaTeXKernel,v: JsonElement): LP<JsonElement> {
        val a = v.jsonArray
        fun sub(i: Int) = parser(k,a[i])
        return when (a[0].s()) {
            "state" -> LaTeXParsing.state().map { sv(it.user) }
            "pure" -> ReaderTokenParsec.pure(a[1]); "fail" -> ReaderTokenParsec.fail("failure")
            "then" -> sub(1).then(sub(2)); "pair" -> sub(1).flatMap { x -> sub(2).map { y -> a(x,y) } }
            "or" -> sub(1) or sub(2); "try" -> sub(1).attempt(); "look" -> sub(1).lookAhead()
            "verbatim" -> LaTeXParsing.withVerbatimMode(sub(1)); "disable" -> LaTeXParsing.disablingWithRaw(sub(1))
            "raw" -> LaTeXParsing.withRaw(sub(1)).map { a(it.first, tvs(it.second)) }
            "from" -> LaTeXParsing.parseFromToks(sub(2), tokens(a[1]))
            "change" -> LaTeXParsing.update { change(it, a[1]) }.map { JsonNull }
            "number" -> LaTeXParsing.getNextNumber { when (a[1].jsonPrimitive.int) { 0->it.lastHeaderNum;1->it.lastFigureNum;else->it.lastTableNum } }.map { JsonPrimitive(it.render()) }
            "reset-caption" -> LaTeXParsing.resetCaption().map { JsonNull }
            "add-meta" -> LaTeXParsing.addMeta(a[1].s(), PandocJson.decodeMetaValue(a[2])).map { JsonNull }
            "quote" -> ReaderTokenParsec.lift(LaTeXStateAccess.tokenQuotes().withQuoteContext(quote(a[1]), sub(2).runReader(Deferred.now(LaTeXEnv()))))
            "consume" -> {
                var p: LP<List<Tok>> = ReaderTokenParsec.pure(emptyList())
                repeat(a[1].jsonPrimitive.int) { p = p.flatMap { xs -> ReaderTokenParsec.lift<LaTeXEnv,Tok,LaTeXState,Tok>(TokenParsec.anyToken { it.text }).map { xs + it } } }
                p.map { JsonArray(it.map(::tv)) }
            }
            "mark" -> LaTeXParsing.update { st -> st.copy(rawTokens = Deferred.now(DeferredChain.Cons(Deferred.now(tok(a[1])), st.rawTokens)), rawTokenCount = st.rawTokenCount + 1) }.map { JsonNull }
            else -> error(a)
        }
    }
    private inner class Runtime(val common: PandocStateContext,fs: JsonElement): PandocDataRuntime, PandocState by common {
        private var files: Map<CharacterString,ByteArray> = emptyMap()
        init { setFiles(fs) }
        private fun canonical(path: CharacterString)=PandocFilePaths.canonical(path,filePaths)
        fun setFiles(fs: JsonElement) { files=fs.jsonArray.associate { item -> item.jsonArray.let {
            canonical(CharacterStringChars.fromString(raw(it[0]))) to it[1].jsonArray.map { b -> b.jsonPrimitive.int.toByte() }.toByteArray()
        } } }
        override fun fileExists(path: CharacterString) = canonical(path) in files
        override fun readFileStrict(path: CharacterString) = files[canonical(path)]?.copyOf()
            ?: throw PandocError.PandocResourceNotFound(TextChars.fromPoints(path.points()))
        override fun readFileLazy(path: CharacterString) = readFileStrict(path)
        override fun getDataFileName(path: CharacterString) = CharacterStringChars.fromString("data/"+CharacterStringChars.build(path))
        override fun lookupEnv(name: String) = if(name=="USER")"pandoc-user" else null
        override fun getCurrentTime() = PandocUtcTime.fromPosixPicoseconds(BigInteger.ZERO)
    }
    private val kinds=listOf("acronym","verb","char","accent","misc","name","biblatex","ref")
    private fun entries(k: LaTeXKernel,kind: String,tok: LP<Inlines>): Map<String,LP<Inlines>> {
        val i=LaTeXInlines(k,(k.context as TranslationRuntime)::translateTerm)
        return when(kind) {
            "acronym"->i.acronymCommands();"verb"->i.verbCommands();"char"->i.charCommands();"accent"->i.accentCommands(tok)
            "misc"->i.miscCommands();"name"->i.nameCommands();"biblatex"->i.biblatexInlineCommands(tok);"ref"->i.refCommands();else->emptyMap()
        }
    }
    private fun rich(k: LaTeXKernel): LP<Inlines> = ReaderTokenParsec.defer {
        val sub: LP<Inlines> = ReaderTokenParsec.defer { rich(k) }
        val command=k.anyControlSeq().flatMap { t ->
            val name=(t.type as TokType.CtrlSeq).text
            when(name) {
                "emph"->k.tokWith(sub).map(Builder::emph)
                "textbf"->k.tokWith(sub).map(Builder::strong)
                "setdefaultlanguage"->LaTeXLanguages(k).setDefaultLanguage().map { Inlines() }
                else->k.symbol('*').map { true }.option(false).flatMap { star ->
                    (LaTeXCitations(k).citationCommands(sub)+LaTeXLanguages(k).inlineLanguageCommands(k.tokWith(sub))+LaTeXLanguages(k).enquoteCommands(k.tokWith(sub))+LaTeXSIunitx(k).siunitxCommands(k.tokWith(sub))+kinds.flatMap { entries(k,it,k.tokWith(sub)).entries }.associate { it.key to it.value })[name+if(star)"*" else ""] ?: ReaderTokenParsec.fail("unknown callback command")
                }
            }
        }
        val atom=k.grouped(sub,Inlines(),Inlines::plus) or k.satisfyTok { it.type!=TokType.Symbol || it.text !in listOf("{","}") }.map {
            when(it.type) { TokType.Spaces->Builder.space;TokType.Newline->Builder.softbreak;TokType.Comment->Inlines();else->Builder.str(it.text) }
        }
        LaTeXMacros(k).macroDef({ Builder.rawInline("fixture-definition",it) },Inlines()) or command.attempt() or atom
    }
    private fun changes(k: LaTeXKernel,v: JsonElement): LP<Unit> {
        var p: LP<Unit> = ReaderTokenParsec.pure(Unit)
        for(c in v.jsonArray)p=p.then(if(c.jsonArray[0].s()=="report")k.ignore(raw(c.jsonArray[1]),Unit) else LaTeXParsing.update { change(it,c) })
        return p
    }
    private fun inline(k: LaTeXKernel,v: JsonElement): LP<Inlines> {
        val a=v.jsonArray
        fun value(i: Int)=Inlines(a[i].jsonArray.map(PandocJson::decodeInline))
        fun take(n: Int): LP<Unit> { var p: LP<Unit> = ReaderTokenParsec.pure(Unit);repeat(n) { p=p.then(k.anyTok().map { Unit }) };return p }
        return when(a[0].s()) {
            "cell"->cellInline(k)
            "atom"->rich(k)
            "tok"->k.tokWith(ReaderTokenParsec.defer { rich(k) })
            "quote"->LaTeXParsing.state().map { Builder.str(it.user.quoteContext.name) }
            "sequence"->rich(k).many().map { it.fold(Inlines(),Inlines::plus) }
            "bracket"->k.bracketed(inline(k,a[1]),Inlines(),Inlines::plus)
            "pure"->changes(k,a[2]).map { value(1) }
            "chunk"->k.anyTok().then(k.anyTok().skipMany()).then(changes(k,a[2])).map { value(1) }
            "fail"->take(a[1].jsonPrimitive.int).then(changes(k,a[2])).then(ReaderTokenParsec.fail("callback failure"))
            else->error(a)
        }
    }
    private fun blocks(k: LaTeXKernel,v: JsonElement): LP<Blocks> {
        val a=v.jsonArray
        fun value(i: Int)=Blocks(a[i].jsonArray.map(PandocJson::decodeBlock))
        fun take(n: Int): LP<Unit> { var p: LP<Unit> = ReaderTokenParsec.pure(Unit);repeat(n) { p=p.then(k.anyTok().map { Unit }) };return p }
        return when(a[0].s()) {
            "cell"->cellBlock(k)
            "pure"->changes(k,a[2]).map { value(1) }
            "take"->take(a[1].jsonPrimitive.int).then(changes(k,a[3])).map { value(2) }
            "fail"->take(a[1].jsonPrimitive.int).then(changes(k,a[2])).then(ReaderTokenParsec.fail("callback failure"))
            "scan"->k.controlSeq("end").notFollowedBy { it.show() }.then(rich(k)).many().map { Builder.para(it.fold(Inlines(),Inlines::plus)) }
            else->error(a)
        }
    }
    private fun parser(k: LaTeXKernel,v: JsonElement): LP<JsonElement> {
        val a=v.jsonArray;val t=LaTeXTables(k);val l=LaTeXLanguages(k);val si=LaTeXSIunitx(k);val i=LaTeXInlines(k,(k.context as TranslationRuntime)::translateTerm)
        fun ils(p: LP<Inlines>)=p.map<JsonElement> { JsonArray(it.values.map(PandocJson::encodeInline)) }
        fun bs(p: LP<Blocks>)=p.map<JsonElement> { JsonArray(it.values.map(PandocJson::encodeBlock)) }
        fun sub(i: Int)=parser(k,a[i])
        return when(a[0].s()) {
            "entry"->bs(t.environments(blocks(k,a[2]),inline(k,a[3]))[raw(a[1])] ?: ReaderTokenParsec.zero())
            "aligns"->t.parseAligns().map { cols->JsonArray(cols.map { a(JsonPrimitive(it.alignment.name).let { name->buildJsonObject { put("t",name) } },PandocJson.encodeColWidth(it.width),tvs(it.prefix),tvs(it.suffix)) }) }
            "split"->t.splitWordTok().map { JsonNull }
            "hline"->t.hline().map { JsonNull }
            "lbreak"->t.linebreak().map(::tv)
            "alignment"->t.cellAlignment().map { buildJsonObject { put("t",it.name) } }
            "cell"->t.parseTableCell(blocks(k,a[1])).map(PandocJson::encodeCell)
            "multirow"->t.multirowCell(blocks(k,a[1])).map(PandocJson::encodeCell)
            "multicolumn"->t.multicolumnCell(blocks(k,a[1])).map(PandocJson::encodeCell)
            "row"->t.parseTableRow(blocks(k,a[3]),inline(k,a[4]),raw(a[1]),a[2].jsonArray.map { it.jsonArray.let { tokens(it[0]) to tokens(it[1]) } }).map(PandocJson::encodeRow)
            "simple"->bs(t.simpTable(blocks(k,a[3]),inline(k,a[4]),raw(a[1]),a[2].b()))
            "caption"->bs(t.addTableCaption(Blocks(a[1].jsonArray.map(PandocJson::decodeBlock))))
            "component"->bs(cellBlock(k).many().map { it.fold(Blocks(),Blocks::plus) })
            "accent-direct"->ils(i.accentWith(inline(k,a[3]),a[1].jsonPrimitive.int,if(a[2]==JsonNull)null else a[2].jsonPrimitive.int))
            "raw-inline"->ils(i.rawInlineOr(raw(a[1]),inline(k,a[2])))
            "roman"->i.romanNumeralArg().map { JsonPrimitive(it) }
            "files"->ReaderTokenParsec.lift(TokenParsec.lift { (k.context as Runtime).setFiles(a[1]);JsonNull })
            "select"->ReaderTokenParsec.lift(TokenParsec.lift { k.context.setTranslations(lang(a[1]));JsonNull })
            "si"->ils(si.siunitxCommands(inline(k,a[2]))[raw(a[1])] ?: ReaderTokenParsec.zero())
            "si-unit"->ils(si.siUnit(a[1].jsonArray.map { it.jsonArray.let { raw(it[0]) to raw(it[1]) } },inline(k,a[2])))
            "default"->bs(l.setDefaultLanguage())
            "enquote"->ils(l.enquoteCommands(inline(k,a[2]))[raw(a[1])] ?: ReaderTokenParsec.zero())
            "language"->ils(l.inlineLanguageCommands(inline(k,a[2]))[raw(a[1])] ?: ReaderTokenParsec.zero())
            "rich"->ils(rich(k).many().map { it.fold(Inlines(),Inlines::plus) })
            "many"->sub(1).many().map { JsonArray(it) }
            "repeat"->{var p: LP<List<JsonElement>> = ReaderTokenParsec.pure(emptyList());repeat(a[1].jsonPrimitive.int) { p=p.flatMap { out->sub(2).map { out+it } } };p.map { JsonArray(it) }}
            else->stateParser(k,v)
        }
    }
    private fun lang(v: JsonElement): Lang = v.jsonArray.let { x ->
        fun optional(i: Int)=if(x[i]==JsonNull)null else raw(x[i])
        Lang(raw(x[0]),optional(1),optional(2),x[3].jsonArray.map(::raw),x[4].jsonArray.map { e ->
            val pair=e.jsonArray;raw(pair[0]) to pair[1].jsonArray.map { p -> p.jsonArray.let { raw(it[0]) to raw(it[1]) } }
        },x[5].jsonArray.map(::raw))
    }
    private fun langValue(l: Lang)=a(JsonPrimitive(l.show()),JsonPrimitive(l.renderLang()))
    private fun seed(v: JsonElement): CommonState = CommonState(
        translations=if(v==JsonNull)null else v.jsonArray.let { lang(it[0]) to if(it[1]==JsonNull)null else Translations.fromJson(it[1]) },
        log=listOf(LogMessage.NoLangSpecified),userDataDir=CharacterStringChars.fromString("user"),sourceURL="https://example.test/source",
        requestHeaders=listOf("X-Test" to "yes"),noCheckCertificate=true,inputFiles=listOf(CharacterStringChars.fromString("input")),
        outputFile=CharacterStringChars.fromString("output"),resourcePath=listOf("assets",".").map(CharacterStringChars::fromString),verbosity=Verbosity.ERROR)
    private fun common(s: CommonState): JsonElement = buildJsonObject {
        put("cache",s.translations?.let { (l,t)->a(langValue(l),t?.let { JsonPrimitive(it.show()) } ?: JsonNull) } ?: JsonNull)
        put("log",JsonArray(s.log.map(::lv)));put("user",s.userDataDir?.let { CharacterStringChars.build(it) }?.let(::JsonPrimitive) ?: JsonNull)
        put("source",s.sourceURL?.let(::JsonPrimitive) ?: JsonNull);put("headers",JsonArray(s.requestHeaders.map { a(JsonPrimitive(it.first),JsonPrimitive(it.second)) }))
        put("noCheckCertificate",s.noCheckCertificate);put("inputs",JsonArray(s.inputFiles.map { JsonPrimitive(CharacterStringChars.build(it)) }))
        put("output",s.outputFile?.let { JsonPrimitive(CharacterStringChars.build(it)) } ?: JsonNull)
        put("resources",JsonArray(s.resourcePath.map { JsonPrimitive(CharacterStringChars.build(it)) }));put("verbosity",s.verbosity.name)
        put("trace",s.trace);put("managerPresent",s.manager!=null);put("mediaEmpty",s.mediaBag.items().isEmpty())
    }
    private fun run(v: JsonArray): JsonElement {
        val context=Runtime(PandocStateContext(seed(v[2])),v[3]);val k=LaTeXKernel(context)
        val p=parser(k,v[5]).flatMap { value->LaTeXParsing.state().map { value to it } }
        val result=try { p.run(TokenParseState(TokStream(false,tokens(v[4])),SourcePos("chunk"),state(v[1])),LaTeXEnv()) }
            catch(e: PandocError) { return buildJsonObject { put("pandocError",CharacterStringChars.build(e.show())) } }
        val shared=common(context.getCommonState())
        if(!result.success)return buildJsonObject { put("ok",false);put("error",result.error.showText());put("common",shared) }
        val (value,st)=result.value!!;val rest=st.input as TokStream
        return buildJsonObject { put("ok",true);put("value",value);put("state",sv(st.user));put("position",pv(st.position));put("expanded",rest.expanded);put("rest",tvs(rest.tokens));put("common",shared) }
    }
    private fun observe(v: JsonArray): JsonElement = try { observeValue(v) }
        catch(e: IllegalStateException) { buildJsonObject { put("exception",e.message?.substringBefore('\n') ?: "") } }
    private fun observeValue(v: JsonArray): JsonElement = when(v[0].s()) {
        "stress"->stress(v[1].jsonPrimitive.int,v[2].jsonPrimitive.int)
        "parse"->run(v)
        "registries"->JsonArray(LaTeXTables().environments(ReaderTokenParsec.zero(),ReaderTokenParsec.zero()).keys.map(::JsonPrimitive))
        "fix-rows"->JsonArray(LaTeXTables.fixTableRows(v[1].jsonArray.map(PandocJson::decodeRow)).map(PandocJson::encodeRow))
        "fix-row"->{
            val h=v[1].jsonArray.map { if(it==JsonNull)null else it.jsonArray.let { LaTeXTableRows.Hang(it[0].n(),it[1].n()) } }
            val (hang,cells)=LaTeXTableRows.fixRow(h,v[2].jsonArray.map(PandocJson::decodeCell))
            a(JsonArray(hang.map { it?.let { a(JsonPrimitive(it.columns),JsonPrimitive(it.rows)) } ?: JsonNull }),JsonArray(cells.map(PandocJson::encodeCell)))
        }
        "fix-row-prefix"->{
            val h=v[2].jsonArray.map { if(it==JsonNull)null else it.jsonArray.let { LaTeXTableRows.Hang(it[0].n(),it[1].n()) } }
            val (hang,cells)=LaTeXTableRows.fixRowPrefix(h,v[3].jsonArray.map(PandocJson::decodeCell),v[1].n())
            a(JsonArray(hang.map { it?.let { a(JsonPrimitive(it.columns),JsonPrimitive(it.rows)) } ?: JsonNull }),JsonArray(cells.map(PandocJson::encodeCell)))
        }
        "fix-head"->PandocJson.decodeTableHead(v[1]).let { PandocJson.encodeTableHead(it.copy(rows=LaTeXTables.fixTableRows(it.rows))) }
        "fix-body"->PandocJson.decodeTableBody(v[1]).let { PandocJson.encodeTableBody(it.copy(head=LaTeXTables.fixTableRows(it.head),body=LaTeXTables.fixTableRows(it.body))) }
        "compact"->compact(v[1].s(),v[2])
        "shared-text"->raw(v[1]).let { text->a(pt(SharedText.trim(text)),pt(SharedText.triml(text)),pt(SharedText.trimr(text)),pt(SharedText.stripTrailingNewlines(text))) }
        "shared-text-chars"->{
            val hash=MessageDigest.getInstance("SHA-256")
            val functions=listOf(SharedText::trim,SharedText::triml,SharedText::trimr,SharedText::stripTrailingNewlines)
            for(cp in v[1].jsonPrimitive.int until v[1].jsonPrimitive.int+v[2].jsonPrimitive.int) {
                hash.update(if(SharedText.isWS(cp))1.toByte() else 0.toByte())
                for(points in listOf(intArrayOf(cp),intArrayOf(cp,120,cp),intArrayOf(cp,10)))for(f in functions) {
                    val bytes=f(TextChars.fromPoints(points)).toByteArray();val n=bytes.size
                    hash.update(byteArrayOf((n ushr 24).toByte(),(n ushr 16).toByte(),(n ushr 8).toByte(),n.toByte()));hash.update(bytes)
                }
            }
            JsonPrimitive(hash.digest().joinToString("") { "%02x".format(it) })
        }
        else->error(v)
    }
    private fun stress(kind: Int,n: Int): JsonElement {
        fun digest(s: String)=JsonPrimitive(MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) })
        return when(kind) {
            0->digest(AstShow.show(LaTeXTables.fixTableRows(List(n) { Row(Attr(),listOf(Cell(Attr(),Alignment.AlignDefault,2,1,listOf(Block.Plain(listOf(Inline.Str("x"))))),Builder.emptyCell)) })))
            1->{val (h,c)=LaTeXTableRows.fixRow(List(n) { LaTeXTableRows.Hang(1,2) },listOf(Builder.emptyCell));digest(AstShow.show(h.map { it?.let { it.columns to it.rows } ?: (-1L to -1L) } to c))}
            2->{var x: Inline=Inline.Note(listOf(Block.Plain(listOf(Inline.Str("x")))));repeat(n) { x=Inline.Emph(listOf(x)) };digest(AstShow.show(TableCompaction.compactify(x) { visitor,value->visitor.walkInline(value) }))}
            3->{val k=LaTeXKernel();val source="{"+"l".repeat(n)+"}tail";val result=LaTeXTables(k).parseAligns().run(TokenParseState(TokStream(false,LaTeXTokens.tokenize(SourcePos("stress"),source)),SourcePos("stress"),LaTeXState()),LaTeXEnv());check(result.success);digest(AstShow.show(result.value!!.map { it.alignment to it.width }))}
            else->error(kind)
        }
    }
    private fun compact(kind: String,value: JsonElement): JsonElement {
        fun <A> apply(x: A,walk: (PandocVisitor,A)->A)=TableCompaction.compactify(x,walk)
        return when(kind) {
            "document"->PandocJson.toJson(apply(PandocJson.decode(value)) { v,x->v.walk(x) })
            "blocks"->JsonArray(apply(value.jsonArray.map(PandocJson::decodeBlock)) { v,x->v.walkBlocks(x) }.map(PandocJson::encodeBlock))
            "many-blocks"->JsonArray(TableCompaction.compactify(Blocks(value.jsonArray.map(PandocJson::decodeBlock))).values.map(PandocJson::encodeBlock))
            "block"->PandocJson.encodeBlock(apply(PandocJson.decodeBlock(value)) { v,x->v.walkBlock(x) })
            "inlines"->JsonArray(apply(value.jsonArray.map(PandocJson::decodeInline)) { v,x->v.walkInlines(x) }.map(PandocJson::encodeInline))
            "inline"->PandocJson.encodeInline(apply(PandocJson.decodeInline(value)) { v,x->v.walkInline(x) })
            "cell"->PandocJson.encodeCell(apply(PandocJson.decodeCell(value)) { v,x->v.walkCell(x) })
            "row"->PandocJson.encodeRow(apply(PandocJson.decodeRow(value)) { v,x->v.walkRow(x) })
            "head"->PandocJson.encodeTableHead(apply(PandocJson.decodeTableHead(value)) { v,x->v.walkTableHead(x) })
            "body"->PandocJson.encodeTableBody(apply(PandocJson.decodeTableBody(value)) { v,x->v.walkTableBody(x) })
            "foot"->PandocJson.encodeTableFoot(apply(PandocJson.decodeTableFoot(value)) { v,x->v.walkTableFoot(x) })
            else->error(kind)
        }
    }
    private fun cellInline(k: LaTeXKernel): LP<Inlines> = ReaderTokenParsec.defer { LaTeXMathReader(k).dollarsMath().attempt() or LaTeXMathReader(k).inlineEnvironment().attempt() or rich(k) }
    private fun cellBlock(k: LaTeXKernel): LP<Blocks> = ReaderTokenParsec.defer {
        val sub=ReaderTokenParsec.defer { cellBlock(k) };val inline=ReaderTokenParsec.defer { cellInline(k) }
        val table=k.controlSeq("begin").then(k.braced()).flatMap { LaTeXTables(k).environments(sub,inline)[LaTeXTokens.untokenize(it)] ?: ReaderTokenParsec.zero() }
        val caption=k.controlSeq("caption").then(k.setCaption(inline)).map { Blocks() }
        val label=k.label().map { Blocks() }
        val parbox=k.controlSeq("parbox").then(k.skipopts()).then(k.braced()).then(k.grouped(sub,Blocks(),Blocks::plus))
        val stop=k.satisfyTok { (it.type as? TokType.CtrlSeq)?.text in listOf("begin","end","caption","label","parbox") || it.type==TokType.Symbol && it.text=="}" }
        val paragraph=stop.notFollowedBy { it.show() }.then(inline).many1().map { Builder.para(it.fold(Inlines(),Inlines::plus)) }
        table.attempt() or caption or label or parbox or paragraph
    }
    // Aeson Value compares Scientific values numerically. Preserve exact decimal
    // values while equating exponent/fixed spellings; no Double/tolerance step.
    private fun canonical(v: JsonElement): JsonElement = when(v) {
        is JsonObject->JsonObject(v.mapValues { canonical(it.value) })
        is JsonArray->JsonArray(v.map(::canonical))
        is JsonPrimitive->if(v.isString)v else v.content.toBigDecimalOrNull()?.let {
            Json.parseToJsonElement(it.stripTrailingZeros().toPlainString())
        } ?: v
    }
    private fun checkCase(line: String) {
        val c=Json.parseToJsonElement(line).jsonObject;val request=c.getValue("request").jsonArray
        val actual=observe(request)
        assertEquals(canonical(c.getValue("expected")),canonical(actual),request.toString())
        if("upstreamExpected" in c)assertEquals(c.getValue("upstreamPassed").jsonPrimitive.boolean,actual==c.getValue("upstreamExpected"),request.toString())
    }
    @TestFactory fun originalTable(): Stream<DynamicTest> {
        val reader=GZIPInputStream(javaClass.getResourceAsStream("/latex-table/data.jsonl.gz")!!).bufferedReader()
        reader.readLine()
        // JUnit retains completed dynamic nodes. Release each exact fixture line
        // after execution rather than keeping AST payloads alive for the worker.
        return reader.lineSequence().mapIndexed { i,line ->
            var payload: String?=line
            DynamicTest.dynamicTest("${i+1}: original table observation") {
                try { checkCase(checkNotNull(payload)) } finally { payload=null }
            }
        }.asStream().onClose(reader::close)
    }
}
