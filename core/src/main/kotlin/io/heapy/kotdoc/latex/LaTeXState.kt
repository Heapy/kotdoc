// Text.Pandoc.Readers.LaTeX.Parsing, Copyright (C) 2006-2024 John MacFarlane.
// GPL-2.0-or-later; original source retained under upstream/pandoc.
package io.heapy.kotdoc.latex

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.options.ReaderOptions
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.tex.*

typealias LP<A> = ReaderTokenParsec<LaTeXEnv, Tok, LaTeXState, A>

class DottedNum(numbers: List<Long>) {
    val numbers: List<Long> = java.util.Collections.unmodifiableList(numbers.toList())
    fun render(): String = numbers.joinToString(".")
    fun increment(level: Long): DottedNum {
        if (level <= 0) return DottedNum(emptyList())
        val count = Math.toIntExact(level)
        return DottedNum(List(count) { i -> numbers.getOrElse(i) { 0L } + if (i == count - 1) 1 else 0 })
    }
    fun show(precedence: Int = 0): String {
        val body = "DottedNum " + numbers.joinToString(",", "[", "]")
        return if (precedence > 10) "($body)" else body
    }
    override fun equals(other: Any?) = other is DottedNum && numbers == other.numbers
    override fun hashCode() = numbers.hashCode()
    override fun toString() = show()
}
enum class TheoremStyle { PlainStyle, DefinitionStyle, RemarkStyle }
class TheoremSpec(val name: TokenList, val style: TheoremStyle, val series: String?, val syncTo: String?,
                  val number: Boolean, val lastNum: DottedNum) {
    override fun equals(other: Any?): Boolean {
        if (other !is TheoremSpec) return false
        var a = name.value(); var b = other.name.value()
        while (a is DeferredChain.Cons && b is DeferredChain.Cons) {
            if (a.head.value() != b.head.value()) return false
            a = a.tail.value(); b = b.tail.value()
        }
        return a === DeferredChain.Nil && b === DeferredChain.Nil && style == other.style && series == other.series &&
            syncTo == other.syncTo && number == other.number && lastNum == other.lastNum
    }
    override fun hashCode(): Int {
        var h = 1; var tokens = name.value()
        while (tokens is DeferredChain.Cons) { h = 31*h + tokens.head.value().hashCode(); tokens = tokens.tail.value() }
        return listOf(style, series, syncTo, number, lastNum).fold(h) { acc, v -> 31*acc + (v?.hashCode() ?: 0) }
    }
}
/** NonEmpty macro table stack: updating the active scope preserves outer scopes. */
class MacroScopes private constructor(private val current: Deferred<Map<String, Macro>>,
                                     private val outer: Deferred<Scope?>, private val depth: Int) {
    private class Scope(val value: Deferred<Map<String, Macro>>, val next: Deferred<Scope?>)
    constructor(head: Map<String, Macro> = emptyMap(), tail: List<Map<String, Macro>> = emptyList()) :
        this(Deferred.now(head), chain(tail), tail.size)
    val head: Map<String, Macro> get() = current.value()
    val tail: List<Map<String, Macro>> = object : AbstractList<Map<String, Macro>>() {
        override val size: Int get() = depth
        override fun get(index: Int): Map<String, Macro> {
            if (index !in indices) throw IndexOutOfBoundsException("index: $index, size: $size")
            var scope = outer.value()!!
            repeat(index) { scope = scope.next.value()!! }
            return scope.value.value()
        }
        override fun iterator(): Iterator<Map<String, Macro>> = object : Iterator<Map<String, Macro>> {
            private var current = outer
            private var remaining = depth
            override fun hasNext() = remaining > 0
            override fun next(): Map<String, Macro> {
                if(remaining==0)throw NoSuchElementException()
                val next = current.value() ?: throw NoSuchElementException()
                current = next.next
                remaining--
                return next.value.value()
            }
        }
    }
    fun replaceHead(head: Map<String, Macro>) = MacroScopes(Deferred.now(head), outer, depth)
    fun push() = MacroScopes(current, Deferred.now(Scope(current, outer)), depth+1)
    fun pop() = if(depth==0)this else outer.value()!!.let { MacroScopes(it.value,it.next,depth-1) }
    /** NonEmpty.map: both outer tables and their transformations remain deferred. */
    fun mapAll(update: (Map<String, Macro>)->Map<String, Macro>): MacroScopes {
        fun map(xs: Deferred<Scope?>): Deferred<Scope?> = xs.mapValue { scope ->
            scope?.let { Scope(it.value.mapValue(update), map(it.next)) }
        }
        return MacroScopes(current.mapValue(update),map(outer),depth)
    }
    companion object {
        private fun chain(values: List<Map<String, Macro>>): Deferred<Scope?> {
            var result: Deferred<Scope?> = Deferred.now(null)
            for (value in values.asReversed()) result = Deferred.now(Scope(Deferred.now(value), result))
            return result
        }
    }
}

/** All 30 fields/defaults. Like the source, state has no structural Eq instance. */
class LaTeXState(
    val options: ReaderOptions = ReaderOptions(),
    val meta: Map<String, MetaValue> = emptyMap(),
    val quoteContext: QuoteContext = QuoteContext.NoQuote,
    val macros: MacroScopes = MacroScopes(),
    val containers: List<String> = emptyList(),
    val logMessages: List<LogMessage> = emptyList(),
    val identifiers: Set<String> = emptySet(),
    val verbatimMode: Boolean = false,
    val mathMode: Boolean = false,
    val caption: Caption? = null,
    val inListItem: Boolean = false,
    val inTableCell: Boolean = false,
    val lastHeaderNum: DottedNum = DottedNum(emptyList()),
    val lastFigureNum: DottedNum = DottedNum(emptyList()),
    val lastTableNum: DottedNum = DottedNum(emptyList()),
    val lastNoteNum: Long = 0,
    val footnoteTexts: Map<Long, Blocks> = emptyMap(),
    val theoremMap: Map<String, TheoremSpec> = emptyMap(),
    val lastTheoremStyle: TheoremStyle = TheoremStyle.PlainStyle,
    val lastLabel: String? = null,
    val labels: Map<String, List<Inline>> = emptyMap(),
    val hasChapters: Boolean = false,
    val toggles: Map<String, Boolean> = emptyMap(),
    val fileContents: Map<String, String> = emptyMap(),
    val enableWithRaw: Boolean = true,
    val rawTokens: TokenList = tokenList(emptyList()),
    val rawTokenCount: Long = 0,
    val rawScopes: Long = 0,
    val ligatures: Boolean = true,
    val caseExclusions: Map<String, Set<String>> = emptyMap(),
) {
    fun copy(
        options: ReaderOptions = this.options,
        meta: Map<String, MetaValue> = this.meta,
        quoteContext: QuoteContext = this.quoteContext,
        macros: MacroScopes = this.macros,
        containers: List<String> = this.containers,
        logMessages: List<LogMessage> = this.logMessages,
        identifiers: Set<String> = this.identifiers,
        verbatimMode: Boolean = this.verbatimMode,
        mathMode: Boolean = this.mathMode,
        caption: Caption? = this.caption,
        inListItem: Boolean = this.inListItem,
        inTableCell: Boolean = this.inTableCell,
        lastHeaderNum: DottedNum = this.lastHeaderNum,
        lastFigureNum: DottedNum = this.lastFigureNum,
        lastTableNum: DottedNum = this.lastTableNum,
        lastNoteNum: Long = this.lastNoteNum,
        footnoteTexts: Map<Long, Blocks> = this.footnoteTexts,
        theoremMap: Map<String, TheoremSpec> = this.theoremMap,
        lastTheoremStyle: TheoremStyle = this.lastTheoremStyle,
        lastLabel: String? = this.lastLabel,
        labels: Map<String, List<Inline>> = this.labels,
        hasChapters: Boolean = this.hasChapters,
        toggles: Map<String, Boolean> = this.toggles,
        fileContents: Map<String, String> = this.fileContents,
        enableWithRaw: Boolean = this.enableWithRaw,
        rawTokens: TokenList = this.rawTokens,
        rawTokenCount: Long = this.rawTokenCount,
        rawScopes: Long = this.rawScopes,
        ligatures: Boolean = this.ligatures,
        caseExclusions: Map<String, Set<String>> = this.caseExclusions,
    ) = LaTeXState(options, meta, quoteContext, macros, containers, logMessages, identifiers, verbatimMode, mathMode, caption, inListItem, inTableCell, lastHeaderNum, lastFigureNum, lastTableNum, lastNoteNum, footnoteTexts, theoremMap, lastTheoremStyle, lastLabel, labels, hasChapters, toggles, fileContents, enableWithRaw, rawTokens, rawTokenCount, rawScopes, ligatures, caseExclusions)
    fun setMeta(field: String, value: MetaValue) = copy(meta = meta + (field to value))
    fun deleteMeta(field: String) = copy(meta = meta - field)
    companion object { val default get() = LaTeXState() }
}

class LaTeXEnv(val inlineCommands: Map<String, LP<Inlines>> = emptyMap(),
               val blockCommands: Map<String, LP<Blocks>> = emptyMap(),
               val environments: Map<String, LP<Blocks>> = emptyMap())

object LaTeXStateAccess : IdentifierListAccess<LaTeXState>, MacrosAccess<LaTeXState>,
    ReaderOptionsAccess<LaTeXState>, IncludeFilesAccess<LaTeXState>, LogMessagesAccess<LaTeXState>, QuoteContextAccess<LaTeXState> {
    override fun extractReaderOptions(state: LaTeXState) = state.options
    override fun extractIdentifierList(state: LaTeXState) = state.identifiers
    override fun updateIdentifierList(update: (Set<String>) -> Set<String>, state: LaTeXState) = state.copy(identifiers = update(state.identifiers))
    override fun extractMacros(state: LaTeXState) = state.macros.head
    override fun updateMacros(update: (Map<String, Macro>) -> Map<String, Macro>, state: LaTeXState) = state.copy(macros = state.macros.replaceHead(update(state.macros.head)))
    override fun getIncludeFiles(state: LaTeXState) = state.containers
    override fun addIncludeFile(file: String, state: LaTeXState) = state.copy(containers = listOf(file) + state.containers)
    override fun dropLatestIncludeFile(state: LaTeXState) = state.copy(containers = state.containers.drop(1))
    override fun addLogMessage(message: LogMessage, state: LaTeXState) = state.copy(logMessages = listOf(message) + state.logMessages)
    override fun getLogMessages(state: LaTeXState) = state.logMessages.reversed()
    override fun getQuoteContext() = Parsec.state<LaTeXState>().map { it.user.quoteContext }
    override fun <A> withQuoteContext(context: QuoteContext, parser: Parsec<LaTeXState, A>): Parsec<LaTeXState, A> =
        getQuoteContext().flatMap { old -> Parsec.modify<LaTeXState> { it.copy(user = it.user.copy(quoteContext = context)) }.then(parser).flatMap { result ->
            Parsec.modify<LaTeXState> { it.copy(user = it.user.copy(quoteContext = old)) }.map { result }
        } }
    fun tokenQuotes(): TokenQuoteContextAccess<Tok, LaTeXState> = object : TokenQuoteContextAccess<Tok, LaTeXState> {
        override fun getQuoteContext() = TokenParsec.state<Tok, LaTeXState>().map { it.user.quoteContext }
        override fun <A> withQuoteContext(context: QuoteContext, parser: TokenParsec<Tok, LaTeXState, A>): TokenParsec<Tok, LaTeXState, A> =
            getQuoteContext().flatMap { old -> TokenParsec.modify<Tok, LaTeXState> { it.copy(user = it.user.copy(quoteContext = context)) }.then(parser).flatMap { result ->
                TokenParsec.modify<Tok, LaTeXState> { it.copy(user = it.user.copy(quoteContext = old)) }.map { result }
            } }
    }
    fun tokenOptions(): TokenReaderOptionsAccess<Tok, LaTeXState> = TokenReaderOptionsAccess { it.options }
}
