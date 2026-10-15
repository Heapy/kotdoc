// Text.Pandoc.Readers.HTML.Types, Pandoc 3.12.1.
// Copyright (C) 2006-2024 John MacFarlane; GPL-2.0-or-later.
package io.heapy.kotdoc.html

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.options.ReaderOptions
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.tex.Macro
import io.heapy.kotdoc.uri.URI

typealias HtmlReaderParser<T, A> = ReaderTokenParsec<HtmlLocal, T, HtmlState, A>
typealias HtmlReaderTagParser<A> = HtmlReaderParser<HtmlTag, A>
typealias HtmlReaderCharacterParser<A> = ReaderParsec<HtmlLocal, HtmlState, A>

/** Global state and ReaderT-local context remain separate. */
class HtmlState(
    val parserState: ParserState, val noteTable: Map<String, Blocks>, val noteRefPos: Map<String, SourcePos>,
    val baseHref: URI?, val identifiers: Set<String>, val logMessages: List<LogMessage>,
    val macros: Map<String, Macro>, val readerOpts: ReaderOptions, val inFootnotes: Boolean, val inPre: Boolean, val iframeDepth: Long,
) {
    fun copy(parserState: ParserState = this.parserState, noteTable: Map<String, Blocks> = this.noteTable,
             noteRefPos: Map<String, SourcePos> = this.noteRefPos, baseHref: URI? = this.baseHref,
             identifiers: Set<String> = this.identifiers, logMessages: List<LogMessage> = this.logMessages,
             macros: Map<String, Macro> = this.macros, readerOpts: ReaderOptions = this.readerOpts,
             inFootnotes: Boolean = this.inFootnotes, inPre: Boolean = this.inPre, iframeDepth: Long = this.iframeDepth) =
        HtmlState(parserState, noteTable, noteRefPos, baseHref, identifiers, logMessages, macros, readerOpts, inFootnotes, inPre, iframeDepth)
    fun setMeta(field: String, value: MetaValue) = copy(parserState = parserState.setMeta(field, value))
    fun deleteMeta(field: String) = copy(parserState = parserState.deleteMeta(field))
    companion object {
        /** Exact initialization used by readHtml; HTMLState itself has no Default instance. */
        fun initial(options: ReaderOptions, depth: Long = 0) = HtmlState(ParserState(options = options), emptyMap(), emptyMap(), null,
            emptySet(), emptyList(), emptyMap(), options, false, false, depth)
    }
}

data class HtmlLocal(val quoteContext: QuoteContext = QuoteContext.NoQuote, val inChapter: Boolean = false,
                     val inPlain: Boolean = false, val inListItem: Boolean = false)

object HtmlStateAccess : ReaderOptionsAccess<HtmlState>, IdentifierListAccess<HtmlState>, MacrosAccess<HtmlState>,
    LastStrPositionAccess<HtmlState>, LogMessagesAccess<HtmlState> {
    override fun extractReaderOptions(state: HtmlState) = state.parserState.options
    fun <T> tokenOptions(): TokenReaderOptionsAccess<T, HtmlState> = TokenReaderOptionsAccess { extractReaderOptions(it) }
    override fun extractIdentifierList(state: HtmlState) = state.identifiers
    override fun updateIdentifierList(update: (Set<String>) -> Set<String>, state: HtmlState) = state.copy(identifiers = update(state.identifiers))
    override fun extractMacros(state: HtmlState) = state.macros
    override fun updateMacros(update: (Map<String, Macro>) -> Map<String, Macro>, state: HtmlState) = state.copy(macros = update(state.macros))
    override fun getLastStrPos(state: HtmlState) = state.parserState.lastStrPos
    override fun setLastStrPos(position: SourcePos?, state: HtmlState) = state.copy(parserState = ParserStateAccess.setLastStrPos(position, state.parserState))
    override fun addLogMessage(message: LogMessage, state: HtmlState) = state.copy(logMessages = listOf(message) + state.logMessages)
    override fun getLogMessages(state: HtmlState) = state.logMessages.reversed()
}

/** HTML's HasQuoteContext instance is ReaderT.local, independent of user state. */
object HtmlReaderContext {
    fun quoteContextDeferred(): HtmlReaderCharacterParser<Deferred<QuoteContext>> =
        ReaderParsec.askDeferred<HtmlLocal, HtmlState>().map { it.mapValue(HtmlLocal::quoteContext) }
    fun <T> tokenQuoteContextDeferred(): HtmlReaderParser<T, Deferred<QuoteContext>> =
        ReaderTokenParsec.askDeferred<HtmlLocal, T, HtmlState>().map { it.mapValue(HtmlLocal::quoteContext) }
    fun getQuoteContext(): HtmlReaderCharacterParser<QuoteContext> = ReaderParsec.asksValue { it.quoteContext }
    fun <A> withQuoteContext(context: QuoteContext, parser: HtmlReaderCharacterParser<A>) = parser.localValue { it.copy(quoteContext = context) }
    fun failIfInQuoteContext(context: QuoteContext): HtmlReaderCharacterParser<Unit> = getQuoteContext().flatMap {
        if (it == context) ReaderParsec.fail("already inside quotes") else ReaderParsec.pure(Unit)
    }
    fun <T> tokenQuoteContext(): HtmlReaderParser<T, QuoteContext> = ReaderTokenParsec.asksValue { it.quoteContext }
    fun <T, A> withTokenQuoteContext(context: QuoteContext, parser: HtmlReaderParser<T, A>) = parser.localValue { it.copy(quoteContext = context) }
    fun <T> failIfInTokenQuoteContext(context: QuoteContext): HtmlReaderParser<T, Unit> = tokenQuoteContext<T>().flatMap {
        if (it == context) ReaderTokenParsec.fail("already inside quotes") else ReaderTokenParsec.pure(Unit)
    }
}
