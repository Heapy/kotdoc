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

class LaTeXInlineTest {
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
            "pure"->changes(k,a[2]).map { value(1) }
            "take"->take(a[1].jsonPrimitive.int).then(changes(k,a[3])).map { value(2) }
            "fail"->take(a[1].jsonPrimitive.int).then(changes(k,a[2])).then(ReaderTokenParsec.fail("callback failure"))
            "scan"->k.controlSeq("end").notFollowedBy { it.show() }.then(rich(k)).many().map { Builder.para(it.fold(Inlines(),Inlines::plus)) }
            else->error(a)
        }
    }
    private fun parser(k: LaTeXKernel,v: JsonElement): LP<JsonElement> {
        val a=v.jsonArray;val l=LaTeXLanguages(k);val si=LaTeXSIunitx(k);val i=LaTeXInlines(k,(k.context as TranslationRuntime)::translateTerm)
        fun ils(p: LP<Inlines>)=p.map<JsonElement> { JsonArray(it.values.map(PandocJson::encodeInline)) }
        fun bs(p: LP<Blocks>)=p.map<JsonElement> { JsonArray(it.values.map(PandocJson::encodeBlock)) }
        fun sub(i: Int)=parser(k,a[i])
        return when(a[0].s()) {
            "entry"->ils(entries(k,a[1].s(),inline(k,a[3]))[raw(a[2])] ?: ReaderTokenParsec.zero())
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
        "registries"->{val k=LaTeXKernel(Runtime(PandocStateContext(),JsonArray(emptyList())))
            JsonArray(kinds.map { kind->a(JsonPrimitive(kind),JsonArray(entries(k,kind,ReaderTokenParsec.zero()).keys.map(::JsonPrimitive))) }) }
        "listings"->LaTeXInlines.listingsLanguage(v[1].jsonArray.map { it.jsonArray.let { raw(it[0]) to raw(it[1]) } })?.let(::JsonPrimitive) ?: JsonNull
        "normalize"->pt(LaTeXInlines.normalizeNfc(raw(v[1])))
        "unicode-nfc"->{
            val digest=MessageDigest.getInstance("SHA-256");val start=v[1].jsonPrimitive.int;val count=v[2].jsonPrimitive.int
            val marks=v[3].jsonArray.map { it.jsonPrimitive.int }
            for(cp in start..<start+count)for(mark in marks) {
                val bytes=LaTeXInlines.normalizeAccent(TextChars.fromPoints(intArrayOf(cp)),mark).toByteArray()
                digest.update(byteArrayOf((bytes.size ushr 24).toByte(),(bytes.size ushr 16).toByte(),(bytes.size ushr 8).toByte(),bytes.size.toByte()));digest.update(bytes)
            }
            JsonPrimitive(digest.digest().joinToString("") { "%02x".format(it) })
        }
        else->error(v)
    }
    private fun stress(kind: Int,n: Int): JsonElement {
        val context=Runtime(PandocStateContext(seed(JsonNull)),JsonArray(emptyList()));val k=LaTeXKernel(context);val i=LaTeXInlines(context)
        var body: List<Inline> = listOf(Inline.Str("x"));repeat(n) { body=listOf(Inline.Emph(body)) }
        val source=when(kind) {0->"|"+"abc".repeat(n)+"|tail";1->"{Python}|"+"a\nb".repeat(n)+"|tail";2->"{123}tail";3->"a".repeat(n);else->""}
        val action: LP<Inlines> = when(kind) {
            0->i.verbCommands().getValue("verb");1->i.verbCommands().getValue("mintinline")
            2->i.accentWith(ReaderTokenParsec.pure(Inlines(body)),769,null)
            3->i.accentWith(k.anyTok().map { Builder.str("e".repeat(n)) },769,null)
            else->error(kind)
        }
        val p=action.flatMap { value->LaTeXParsing.state().map { AstShow.show(value.values) to it } }
        val result=p.run(TokenParseState(TokStream(false,LaTeXTokens.tokenize(SourcePos("stress"),source)),SourcePos("stress"),LaTeXState()),LaTeXEnv())
        check(result.success) { result.error.showText() }
        val (value,st)=result.value!!;val rest=st.input as TokStream
        val hash=MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
        return buildJsonObject { put("hash",hash);put("state",sv(st.user));put("position",pv(st.position));put("expanded",rest.expanded);put("rest",tvs(rest.tokens));put("common",common(context.getCommonState())) }
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
    @TestFactory fun originalInline(): List<DynamicTest> = GZIPInputStream(javaClass.getResourceAsStream("/latex-inline/data.jsonl.gz")!!).bufferedReader().use { reader ->
        reader.readLine()
        // Retain exact fixture lines; decode expectations only while that case runs.
        // This keeps the complete corpus within the existing 2 GiB worker budget.
        reader.lineSequence().mapIndexed { i,line ->
            DynamicTest.dynamicTest("${i+1}: original inline observation") { checkCase(line) }
        }.toList()
    }
}
