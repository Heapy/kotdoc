package io.heapy.kotdoc.logging

import io.heapy.kotdoc.formats.ReaderMessage
import io.heapy.kotdoc.formats.ReaderResult
import io.heapy.kotdoc.formats.WriterMessage
import io.heapy.kotdoc.formats.WriterResult
import io.heapy.kotdoc.resources.ResourceEvent

/** Preserve every existing format/resource effect and its order in the shared diagnostic model. */
fun ReaderMessage.toLogMessage(): LogMessage = when(this) {
    is ReaderMessage.IgnoredElement -> LogMessage.IgnoredElement(name)
    is ReaderMessage.UnexpectedXmlElement -> LogMessage.UnexpectedXmlElement(name,parent)
}
fun ResourceEvent.toLogMessage(): LogMessage = when(this) {
    is ResourceEvent.Loaded -> LogMessage.LoadedResource(requested,found)
    is ResourceEvent.Fetching -> LogMessage.Fetching(url)
    is ResourceEvent.Extracting -> LogMessage.Extracting(path)
    is ResourceEvent.CouldNotFetch -> LogMessage.CouldNotFetchResource(path,message)
    is ResourceEvent.IgnoredIOError -> LogMessage.IgnoredIOError(message)
}
fun WriterMessage.toLogMessage(): LogMessage = when(this) {
    is WriterMessage.InlineNotRendered -> LogMessage.InlineNotRendered(inline)
    is WriterMessage.BlockNotRendered -> LogMessage.BlockNotRendered(block)
    is WriterMessage.CouldNotFetchResource -> LogMessage.CouldNotFetchResource(path,message)
    is WriterMessage.CouldNotDetermineMimeType -> LogMessage.CouldNotDetermineMimeType(path)
    is WriterMessage.Resource -> event.toLogMessage()
}
fun ReaderResult.logMessages(): List<LogMessage> = messages.map { it.toLogMessage() }
fun WriterResult.logMessages(): List<LogMessage> = messages.map { it.toLogMessage() }
