// State/logging helpers from Text.Pandoc.Class.PandocMonad, Pandoc 3.12.1.
// GPL-2.0-or-later; complete original notice in licenses/pandoc-GPL-2.0.md.
package io.heapy.kotdoc.state

import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.language.Lang
import io.heapy.kotdoc.language.LangParseResult
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.logging.Logging
import io.heapy.kotdoc.logging.Verbosity
import io.heapy.kotdoc.resources.MediaBagUri
import io.heapy.kotdoc.resources.MediaBagValue
import java.util.Collections

/** State/effect portion of PandocMonad; IO, parsing and scripting are separate. */
interface PandocState {
    fun getCommonState(): CommonState
    fun putCommonState(state: CommonState)
    fun logOutput(message: LogMessage)

    fun <A> getsCommonState(select: (CommonState) -> A): A = select(getCommonState())
    fun modifyCommonState(update: (CommonState) -> CommonState) = putCommonState(update(getCommonState()))
    fun setVerbosity(value: Verbosity) = modifyCommonState { it.copy(verbosity = value) }
    fun getVerbosity(): Verbosity = getsCommonState { it.verbosity }
    fun setTrace(value: Boolean) = modifyCommonState { it.copy(trace = value) }
    fun getTrace(): Boolean = getsCommonState { it.trace }

    /** GHC traceIO removes NUL bytes and emits a second warning when any occur. */
    fun trace(message: String) {
        if (getTrace()) {
            val text = TextChars.fromString(message)
            traceOutput("[trace] " + text.replace("\u0000", ""))
            if ('\u0000' in text) traceOutput("WARNING: previous trace message had null bytes")
        }
    }
    fun traceOutput(line: String) { System.err.println(line) }

    fun getLog(): List<LogMessage> {
        val result = ArrayList(getsCommonState { it.log })
        result.reverse()
        return Collections.unmodifiableList(result)
    }
    fun report(message: LogMessage) {
        if (Logging.messageVerbosity(message) <= getVerbosity()) logOutput(message)
        // logOutput may modify state or re-enter report. Read the current state.
        modifyCommonState { it.copy(log = LogEntries.snapshot(it.log).prepend(message)) }
    }
    fun <A> runSilently(action: () -> A): Pair<A, List<LogMessage>> {
        val originalLog = getsCommonState { it.log }
        val originalVerbosity = getVerbosity()
        fun restore() = modifyCommonState { it.copy(log = originalLog, verbosity = originalVerbosity) }
        modifyCommonState { it.copy(log = LogEntries.empty, verbosity = Verbosity.ERROR) }
        val result = try { action() } catch (error: PandocError) { restore(); throw error }
        val newLog = getsCommonState { it.log }
        restore()
        return result to newLog
    }
    fun setRequestHeader(name: String, value: String) {
        val key = TextChars.fromString(name)
        val text = TextChars.fromString(value)
        modifyCommonState { it.copy(requestHeaders = listOf(key to text) + it.requestHeaders.filter { h -> h.first != key }) }
    }
    fun setRequestHeaders(headers: List<Pair<String, String>>) = modifyCommonState { it.copy(requestHeaders = headers) }
    fun getRequestHeaders(): List<Pair<String, String>> = getsCommonState { it.requestHeaders }
    fun setNoCheckCertificate(value: Boolean) = modifyCommonState {
        it.copy(noCheckCertificate = value, manager = if (it.noCheckCertificate == value) it.manager else null)
    }
    fun setMediaBag(bag: MediaBagValue) = modifyCommonState { it.copy(mediaBag = bag) }
    fun getMediaBag(): MediaBagValue = getsCommonState { it.mediaBag }
    fun insertMedia(path: CharacterString, mime: String?, contents: ByteArray) = setMediaBag(getMediaBag().insert(path, mime, contents))
    fun getInputFiles(): List<CharacterString> = getsCommonState { it.inputFiles }
    fun setInputFiles(files: List<CharacterString>) {
        val raw = files.firstOrNull()?.let { buildString { it.points().forEach(::appendCodePoint) } }
        val uri = raw?.let(MediaBagUri::parse)
        val source = if (uri?.scheme in listOf("http", "https")) raw!!.substringBefore('#').substringBefore('?') else null
        modifyCommonState { it.copy(inputFiles = files, sourceURL = source) }
    }
    fun getSourceURL(): String? = getsCommonState { it.sourceURL }
    fun setOutputFile(file: CharacterString?) = modifyCommonState { it.copy(outputFile = file) }
    fun getOutputFile(): CharacterString? = getsCommonState { it.outputFile }
    fun setResourcePath(paths: List<CharacterString>) = modifyCommonState { it.copy(resourcePath = paths) }
    fun getResourcePath(): List<CharacterString> = getsCommonState { it.resourcePath }
    fun setUserDataDir(path: CharacterString?) = modifyCommonState { it.copy(userDataDir = path) }
    fun getUserDataDir(): CharacterString? = getsCommonState { it.userDataDir }
    fun toLang(text: String?): Lang? = if (text == null) null else when (val result = Lang.parseLang(text)) {
        is LangParseResult.Success -> result.value
        is LangParseResult.Failure -> { report(LogMessage.InvalidLang(text)); null }
    }
}

/** In-memory state owner; callbacks are effect boundaries and may re-enter it. */
class PandocStateContext(
    initialState: CommonState = CommonState.default,
    private val output: (PandocStateContext, LogMessage) -> Unit = { _, _ -> },
    private val tracing: (String) -> Unit = { System.err.println(it) },
) : PandocState {
    private var state = initialState
    override fun getCommonState(): CommonState = state
    override fun putCommonState(state: CommonState) { this.state = state }
    override fun logOutput(message: LogMessage) = output(this, message)
    override fun traceOutput(line: String) = tracing(line)
}
