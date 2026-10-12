package io.heapy.kotdoc.formats

import io.heapy.kotdoc.ast.Pandoc

/** Observable reader effects, before the application applies its media bag policy. */
data class ReaderResult(
    val document: Pandoc,
    val messages: List<ReaderMessage>,
    val mediaInsertions: List<MediaInsertion>,
)

sealed interface ReaderMessage {
    data class IgnoredElement(val name: String) : ReaderMessage
    data class UnexpectedXmlElement(val name: String, val parent: String) : ReaderMessage
}

/** One PandocMonad.insertMedia call; order and duplicate keys are significant. */
class MediaInsertion(val path: String, val mimeType: String, contents: ByteArray) {
    private val bytes = contents.copyOf()
    val contents: ByteArray get() = bytes.copyOf()
}
