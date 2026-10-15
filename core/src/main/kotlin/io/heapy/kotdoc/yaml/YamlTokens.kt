// libyaml 0.2.5 yaml.h/scanner.c; complete notice: licenses/Libyaml-MIT.txt.
package io.heapy.kotdoc.yaml

data class YamlMark(val index: ULong = 0u, val line: ULong = 0u, val column: ULong = 0u)
enum class YamlTokenType {
    None, StreamStart, StreamEnd, VersionDirective, TagDirective, DocumentStart, DocumentEnd,
    BlockSequenceStart, BlockMappingStart, BlockEnd, FlowSequenceStart, FlowSequenceEnd,
    FlowMappingStart, FlowMappingEnd, BlockEntry, FlowEntry, Key, Value, Alias, Anchor, Tag, Scalar,
}
/** Byte-valued token fields remain bytes, including percent-decoded invalid UTF8. */
class YamlToken(
    val type: YamlTokenType, val start: YamlMark, val end: YamlMark,
    value: ByteArray = byteArrayOf(), suffix: ByteArray = byteArrayOf(),
    val style: YamlStyle = YamlStyle.Any, val encoding: YamlEncoding = YamlEncoding.Any,
    val major: Int = 0, val minor: Int = 0,
) {
    private val bytes = if (type in listOf(YamlTokenType.Alias, YamlTokenType.Anchor, YamlTokenType.Tag, YamlTokenType.TagDirective)) cString(value) else value.copyOf()
    private val suffixBytes = if (type == YamlTokenType.Tag || type == YamlTokenType.TagDirective) cString(suffix) else suffix.copyOf()
    val value: ByteArray get() = bytes.copyOf()
    val suffix: ByteArray get() = suffixBytes.copyOf()
    private fun cString(bytes: ByteArray): ByteArray = bytes.copyOfRange(0, bytes.indexOf(0).takeIf { it >= 0 } ?: bytes.size)
}
data class YamlScanFailure(
    val type: Int, val problem: String?, val context: String?,
    val problemMark: YamlMark = YamlMark(), val contextMark: YamlMark = YamlMark(),
    val readerFailure: YamlReaderFailure? = null,
)
sealed interface YamlScanResult {
    data class Token(val value: YamlToken) : YamlScanResult
    data class Failure(val error: YamlScanFailure) : YamlScanResult
    data object End : YamlScanResult
}
