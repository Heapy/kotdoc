// Text.Pandoc.Parsing.Capabilities, © 2006-2024 John MacFarlane.
// GPL-2.0-or-later; original source retained in upstream/pandoc.
package io.heapy.kotdoc.parsing

import io.heapy.kotdoc.options.Extension
import io.heapy.kotdoc.options.ReaderOptions

/** HasReaderOptions dictionary; callers may override the parser-valued getter. */
fun interface ReaderOptionsAccess<U> {
    fun extractReaderOptions(state: U): ReaderOptions
    fun <A> getOption(select: (ReaderOptions) -> A): Parsec<U, A> =
        Parsec.state<U>().map { select(extractReaderOptions(it.user)) }

    companion object {
        val options = ReaderOptionsAccess<ReaderOptions> { it }
    }
}

object ReaderOptionGuards {
    fun <U> guardEnabled(access: ReaderOptionsAccess<U>, extension: Extension): Parsec<U, Unit> =
        access.getOption { extension in it.extensions }.flatMap { Parsec.guard(it) }
    fun <U> guardDisabled(access: ReaderOptionsAccess<U>, extension: Extension): Parsec<U, Unit> =
        access.getOption { extension in it.extensions }.flatMap { Parsec.guard(!it) }
}
