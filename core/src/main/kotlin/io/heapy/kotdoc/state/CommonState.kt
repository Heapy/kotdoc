// Text.Pandoc.Class.CommonState, © 2016–2020 Jesse Rosenthal, John MacFarlane.
// GPL-2.0-or-later; complete original notice in licenses/pandoc-GPL-2.0.md.
package io.heapy.kotdoc.state

import io.heapy.kotdoc.language.Lang
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.logging.Verbosity
import io.heapy.kotdoc.resources.MediaBagValue
import io.heapy.kotdoc.translations.Translations
import java.net.http.HttpClient
import java.util.Collections

/** Immutable common state. The log is stored in reverse chronological order. */
class CommonState(
    log: List<LogMessage> = emptyList(),
    val userDataDir: CharacterString? = null,
    sourceURL: String? = null,
    requestHeaders: List<Pair<String, String>> = emptyList(),
    val noCheckCertificate: Boolean = false,
    val mediaBag: MediaBagValue = MediaBagValue.empty,
    val translations: Pair<Lang, Translations?>? = null,
    inputFiles: List<CharacterString> = emptyList(),
    val outputFile: CharacterString? = null,
    resourcePath: List<CharacterString> = listOf(CharacterStringChars.fromString(".")),
    /** Cached JVM transport identity; the complete upstream HTTP runtime is separate. */
    val manager: HttpClient? = null,
    val verbosity: Verbosity = Verbosity.WARNING,
    val trace: Boolean = false,
) {
    val log: List<LogMessage> = LogEntries.snapshot(log)
    val sourceURL: String? = sourceURL?.let(TextChars::fromString)
    val requestHeaders: List<Pair<String, String>> = immutable(requestHeaders.map { (k, v) ->
        TextChars.fromString(k) to TextChars.fromString(v)
    })
    val inputFiles: List<CharacterString> = immutable(inputFiles)
    val resourcePath: List<CharacterString> = immutable(resourcePath)

    fun copy(
        log: List<LogMessage> = this.log,
        userDataDir: CharacterString? = this.userDataDir,
        sourceURL: String? = this.sourceURL,
        requestHeaders: List<Pair<String, String>> = this.requestHeaders,
        noCheckCertificate: Boolean = this.noCheckCertificate,
        mediaBag: MediaBagValue = this.mediaBag,
        translations: Pair<Lang, Translations?>? = this.translations,
        inputFiles: List<CharacterString> = this.inputFiles,
        outputFile: CharacterString? = this.outputFile,
        resourcePath: List<CharacterString> = this.resourcePath,
        manager: HttpClient? = this.manager,
        verbosity: Verbosity = this.verbosity,
        trace: Boolean = this.trace,
    ) = CommonState(log, userDataDir, sourceURL, requestHeaders, noCheckCertificate,
        mediaBag, translations, inputFiles, outputFile, resourcePath, manager, verbosity, trace)

    companion object { val default = CommonState() }
}

private fun <T> immutable(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))

/** Persistent source list: report prepends in O(1), saved states retain old roots. */
internal class LogEntries private constructor(
    private val head: LogMessage?, private val tail: LogEntries?, override val size: Int,
) : AbstractList<LogMessage>() {
    fun prepend(message: LogMessage): LogEntries = LogEntries(message, this, Math.addExact(size, 1))
    override fun get(index: Int): LogMessage {
        if (index !in 0 until size) throw IndexOutOfBoundsException(index)
        var node = this
        repeat(index) { node = checkNotNull(node.tail) }
        return checkNotNull(node.head)
    }
    override fun iterator(): Iterator<LogMessage> = object : Iterator<LogMessage> {
        private var node = this@LogEntries
        override fun hasNext() = node.size != 0
        override fun next(): LogMessage {
            if (!hasNext()) throw NoSuchElementException()
            val value = checkNotNull(node.head)
            node = checkNotNull(node.tail)
            return value
        }
    }
    companion object {
        val empty = LogEntries(null, null, 0)
        fun snapshot(values: List<LogMessage>): LogEntries {
            if (values is LogEntries) return values
            val owned = ArrayList(values)
            var result = empty
            for (i in owned.indices.reversed()) result = result.prepend(owned[i])
            return result
        }
    }
}
