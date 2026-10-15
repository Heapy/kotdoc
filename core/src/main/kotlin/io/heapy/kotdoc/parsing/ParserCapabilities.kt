// Text.Pandoc.Parsing.Capabilities, © 2006-2024 John MacFarlane.
// GPL-2.0-or-later; original source retained in upstream/pandoc.
package io.heapy.kotdoc.parsing

import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.options.Extension
import io.heapy.kotdoc.options.ReaderOptions
import io.heapy.kotdoc.state.PandocState
import io.heapy.kotdoc.tex.Macro

interface IdentifierListAccess<U> {
    fun extractIdentifierList(state: U): Set<String>
    fun updateIdentifierList(update: (Set<String>) -> Set<String>, state: U): U
}
interface MacrosAccess<U> {
    fun extractMacros(state: U): Map<String, Macro>
    fun updateMacros(update: (Map<String, Macro>) -> Map<String, Macro>, state: U): U
}
interface LastStrPositionAccess<U> {
    fun setLastStrPos(position: SourcePos?, state: U): U
    fun getLastStrPos(state: U): SourcePos?
}
interface LogMessagesAccess<U> {
    fun addLogMessage(message: LogMessage, state: U): U
    fun getLogMessages(state: U): List<LogMessage>
}
interface IncludeFilesAccess<U> {
    fun getIncludeFiles(state: U): List<String>
    fun addIncludeFile(file: String, state: U): U
    fun dropLatestIncludeFile(state: U): U
}
interface QuoteContextAccess<U> {
    fun getQuoteContext(): Parsec<U, QuoteContext>
    fun <A> withQuoteContext(context: QuoteContext, parser: Parsec<U, A>): Parsec<U, A>
}
interface TokenQuoteContextAccess<T, U> {
    fun getQuoteContext(): TokenParsec<T, U, QuoteContext>
    fun <A> withQuoteContext(context: QuoteContext, parser: TokenParsec<T, U, A>): TokenParsec<T, U, A>
}

/** Token-stream dictionary also permits an independently overridden parser getter. */
fun interface TokenReaderOptionsAccess<T, U> {
    fun extractReaderOptions(state: U): ReaderOptions
    fun <A> getOption(select: (ReaderOptions) -> A): TokenParsec<T, U, A> =
        TokenParsec.state<T, U>().map { select(extractReaderOptions(it.user)) }
}

object ParserCapabilities {
    fun <U> failIfInQuoteContext(access: QuoteContextAccess<U>, context: QuoteContext): Parsec<U, Unit> =
        access.getQuoteContext().flatMap { if (it == context) Parsec.fail("already inside quotes") else Parsec.pure(Unit) }
    fun <U> logMessage(access: LogMessagesAccess<U>, message: LogMessage): Parsec<U, Unit> =
        Parsec.modify { it.copy(user = access.addLogMessage(message, it.user)) }
    fun <U> reportLogMessages(access: LogMessagesAccess<U>, effects: PandocState): Parsec<U, Unit> =
        Parsec.state<U>().flatMap { state ->
            Parsec.lift { access.getLogMessages(state.user).forEach(effects::report) }
        }
    fun <U> updateLastStrPos(access: LastStrPositionAccess<U>): Parsec<U, Unit> = Parsec.state<U>().flatMap { state ->
        val position = state.position
        Parsec.modify { it.copy(user = access.setLastStrPos(position, it.user)) }
    }
    fun <U> notAfterString(access: LastStrPositionAccess<U>): Parsec<U, Boolean> =
        Parsec.state<U>().map { access.getLastStrPos(it.user) != it.position }
}

object TokenParserCapabilities {
    fun <T, U> failIfInQuoteContext(access: TokenQuoteContextAccess<T, U>, context: QuoteContext): TokenParsec<T, U, Unit> =
        access.getQuoteContext().flatMap { if (it == context) TokenParsec.fail("already inside quotes") else TokenParsec.pure(Unit) }
    fun <T, U> logMessage(access: LogMessagesAccess<U>, message: LogMessage): TokenParsec<T, U, Unit> =
        TokenParsec.modify { it.copy(user = access.addLogMessage(message, it.user)) }
    fun <T, U> reportLogMessages(access: LogMessagesAccess<U>, effects: PandocState): TokenParsec<T, U, Unit> =
        TokenParsec.state<T, U>().flatMap { state ->
            TokenParsec.lift { access.getLogMessages(state.user).forEach(effects::report) }
        }
    fun <T, U> updateLastStrPos(access: LastStrPositionAccess<U>): TokenParsec<T, U, Unit> = TokenParsec.state<T, U>().flatMap { state ->
        val position = state.position
        TokenParsec.modify { it.copy(user = access.setLastStrPos(position, it.user)) }
    }
    fun <T, U> notAfterString(access: LastStrPositionAccess<U>): TokenParsec<T, U, Boolean> =
        TokenParsec.state<T, U>().map { access.getLastStrPos(it.user) != it.position }
    fun <T, U> guardEnabled(access: TokenReaderOptionsAccess<T, U>, extension: Extension): TokenParsec<T, U, Unit> =
        access.getOption { extension in it.extensions }.flatMap { if (it) TokenParsec.pure(Unit) else TokenParsec.zero() }
    fun <T, U> guardDisabled(access: TokenReaderOptionsAccess<T, U>, extension: Extension): TokenParsec<T, U, Unit> =
        access.getOption { extension in it.extensions }.flatMap { if (!it) TokenParsec.pure(Unit) else TokenParsec.zero() }
}
