// Text.Pandoc.Parsing.State, Pandoc 3.12.1.
// Copyright (C) 2006-2024 John MacFarlane; GPL-2.0-or-later.
package io.heapy.kotdoc.parsing

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.ast.Target
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.options.ReaderOptions
import io.heapy.kotdoc.tex.Macro

typealias KeyTable = Map<Key, Pair<Target, Attr>>
typealias SubstTable = Map<Key, Blocks>
typealias NoteTable = List<Pair<String, String>>
typealias ParsedNoteTable = Map<String, Pair<SourcePos, Future<ParserState, Blocks>>>

enum class ParserContext { ListItemState, NullState }
enum class QuoteContext { InSingleQuote, InDoubleQuote, NoQuote }
sealed class HeaderType {
    abstract val character: Int
    data class SingleHeader(override val character: Int) : HeaderType() { init { require(character in 0..0x10ffff) } }
    data class DoubleHeader(override val character: Int) : HeaderType() { init { require(character in 0..0x10ffff) } }
    fun show(precedence: Long = 0): String {
        val name = if (this is SingleHeader) "SingleHeader" else "DoubleHeader"
        val body = name + " " + Parsec.showChar(character)
        return if (precedence > 10) "($body)" else body
    }
    final override fun toString() = show()
}

/** Complete original record; it has no derived Eq/Ord/Show instance. Future
 * values are evaluated against the final state, independently of eager metadata. */
class ParserState(
    val options: ReaderOptions = ReaderOptions(),
    val parserContext: ParserContext = ParserContext.NullState,
    val quoteContext: QuoteContext = QuoteContext.NoQuote,
    val allowLinks: Boolean = true,
    val allowLineBreaks: Boolean = true,
    val lastStrPos: SourcePos? = null,
    val keys: KeyTable = emptyMap(),
    val headerKeys: KeyTable = emptyMap(),
    val substitutions: SubstTable = emptyMap(),
    val notes: NoteTable = emptyList(),
    val parsedNotes: ParsedNoteTable = emptyMap(),
    val noteRefs: Set<String> = emptySet(),
    val inNote: Boolean = false,
    val noteNumber: Long = 0,
    val meta: Map<String, MetaValue> = emptyMap(),
    val futureMeta: Future<ParserState, Map<String, MetaValue>> = Future.pure(emptyMap()),
    val citations: Map<String, String> = emptyMap(),
    val headerTable: List<HeaderType> = emptyList(),
    val identifiers: Set<String> = emptySet(),
    val nextExample: Long = 1,
    val examples: Map<String, Long> = emptyMap(),
    val macros: Map<String, Macro> = emptyMap(),
    val rstDefaultRole: String = "title-reference",
    val rstHighlight: String? = null,
    val rstCustomRoles: Map<String, Triple<String, String?, Attr>> = emptyMap(),
    val caption: Inlines? = null,
    val inHtmlBlock: String? = null,
    val fencedDivLevel: Long = 0,
    val containers: List<String> = emptyList(),
    val logMessages: List<LogMessage> = emptyList(),
    val markdownAttribute: Boolean = false,
) {
    fun copy(
        options: ReaderOptions = this.options,
        parserContext: ParserContext = this.parserContext,
        quoteContext: QuoteContext = this.quoteContext,
        allowLinks: Boolean = this.allowLinks,
        allowLineBreaks: Boolean = this.allowLineBreaks,
        lastStrPos: SourcePos? = this.lastStrPos,
        keys: KeyTable = this.keys,
        headerKeys: KeyTable = this.headerKeys,
        substitutions: SubstTable = this.substitutions,
        notes: NoteTable = this.notes,
        parsedNotes: ParsedNoteTable = this.parsedNotes,
        noteRefs: Set<String> = this.noteRefs,
        inNote: Boolean = this.inNote,
        noteNumber: Long = this.noteNumber,
        meta: Map<String, MetaValue> = this.meta,
        futureMeta: Future<ParserState, Map<String, MetaValue>> = this.futureMeta,
        citations: Map<String, String> = this.citations,
        headerTable: List<HeaderType> = this.headerTable,
        identifiers: Set<String> = this.identifiers,
        nextExample: Long = this.nextExample,
        examples: Map<String, Long> = this.examples,
        macros: Map<String, Macro> = this.macros,
        rstDefaultRole: String = this.rstDefaultRole,
        rstHighlight: String? = this.rstHighlight,
        rstCustomRoles: Map<String, Triple<String, String?, Attr>> = this.rstCustomRoles,
        caption: Inlines? = this.caption,
        inHtmlBlock: String? = this.inHtmlBlock,
        fencedDivLevel: Long = this.fencedDivLevel,
        containers: List<String> = this.containers,
        logMessages: List<LogMessage> = this.logMessages,
        markdownAttribute: Boolean = this.markdownAttribute,
    ) = ParserState(options, parserContext, quoteContext, allowLinks, allowLineBreaks, lastStrPos, keys, headerKeys, substitutions, notes, parsedNotes, noteRefs, inNote, noteNumber, meta, futureMeta, citations, headerTable, identifiers, nextExample, examples, macros, rstDefaultRole, rstHighlight, rstCustomRoles, caption, inHtmlBlock, fencedDivLevel, containers, logMessages, markdownAttribute)

    fun setMeta(field: String, value: MetaValue): ParserState = copy(meta = meta + (field to value))
    fun deleteMeta(field: String): ParserState = copy(meta = meta - field)
    companion object { val default get() = ParserState() }
}

/** Original typeclass instances, shared by character and typed-token parsers. */
object ParserStateAccess : IdentifierListAccess<ParserState>, MacrosAccess<ParserState>,
    LastStrPositionAccess<ParserState>, LogMessagesAccess<ParserState>, IncludeFilesAccess<ParserState>,
    ReaderOptionsAccess<ParserState>, QuoteContextAccess<ParserState>, ExampleNumberState<ParserState> {
    override fun nextExample(state: ParserState) = state.nextExample
    override fun examples(state: ParserState) = state.examples
    override fun update(state: ParserState, nextExample: Long, examples: Map<String, Long>) = state.copy(nextExample = nextExample, examples = examples)
    override fun extractReaderOptions(state: ParserState) = state.options
    override fun extractIdentifierList(state: ParserState) = state.identifiers
    override fun updateIdentifierList(update: (Set<String>) -> Set<String>, state: ParserState) = state.copy(identifiers = update(state.identifiers))
    override fun extractMacros(state: ParserState) = state.macros
    override fun updateMacros(update: (Map<String, Macro>) -> Map<String, Macro>, state: ParserState) = state.copy(macros = update(state.macros))
    override fun getLastStrPos(state: ParserState) = state.lastStrPos
    override fun setLastStrPos(position: SourcePos?, state: ParserState) = state.copy(lastStrPos = position)
    override fun addLogMessage(message: LogMessage, state: ParserState) = state.copy(logMessages = listOf(message) + state.logMessages)
    override fun getLogMessages(state: ParserState) = state.logMessages.reversed()
    override fun getIncludeFiles(state: ParserState) = state.containers
    override fun addIncludeFile(file: String, state: ParserState) = state.copy(containers = listOf(file) + state.containers)
    override fun dropLatestIncludeFile(state: ParserState) = state.copy(containers = state.containers.drop(1))
    override fun getQuoteContext(): Parsec<ParserState, QuoteContext> = Parsec.state<ParserState>().map { it.user.quoteContext }
    override fun <A> withQuoteContext(context: QuoteContext, parser: Parsec<ParserState, A>): Parsec<ParserState, A> =
        getQuoteContext().flatMap { old ->
            Parsec.modify<ParserState> { it.copy(user = it.user.copy(quoteContext = context)) }.then(parser).flatMap { result ->
                Parsec.modify<ParserState> { it.copy(user = it.user.copy(quoteContext = old)) }.map { result }
            }
        }
    fun <T> tokenOptions(): TokenReaderOptionsAccess<T, ParserState> = TokenReaderOptionsAccess { it.options }
    fun <T> tokenQuotes(): TokenQuoteContextAccess<T, ParserState> = object : TokenQuoteContextAccess<T, ParserState> {
        override fun getQuoteContext() = TokenParsec.state<T, ParserState>().map { it.user.quoteContext }
        override fun <A> withQuoteContext(context: QuoteContext, parser: TokenParsec<T, ParserState, A>): TokenParsec<T, ParserState, A> =
            getQuoteContext().flatMap { old ->
                TokenParsec.modify<T, ParserState> { it.copy(user = it.user.copy(quoteContext = context)) }.then(parser).flatMap { result ->
                    TokenParsec.modify<T, ParserState> { it.copy(user = it.user.copy(quoteContext = old)) }.map { result }
                }
            }
    }
}
