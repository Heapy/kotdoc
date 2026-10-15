// libyaml 0.2.5 event values; complete original MIT notice in licenses/.
package io.heapy.kotdoc.yaml

import java.util.Collections

enum class YamlEventType { None, StreamStart, StreamEnd, DocumentStart, DocumentEnd, Alias, Scalar, SequenceStart, SequenceEnd, MappingStart, MappingEnd }
enum class YamlCollectionStyle { Any, Block, Flow }
data class YamlVersionDirective(val major: Int, val minor: Int)
class YamlTagDirective(handle: ByteArray, prefix: ByteArray) {
    private val handleBytes=cString(handle)
    private val prefixBytes=cString(prefix)
    val handle: ByteArray get()=handleBytes.copyOf()
    val prefix: ByteArray get()=prefixBytes.copyOf()
}
private fun cString(bytes: ByteArray)=bytes.copyOfRange(0,bytes.indexOf(0).takeIf { it>=0 } ?: bytes.size)

/** Owned source event fields. Absent and present empty anchor/tag stay distinct. */
class YamlEvent(
    val type: YamlEventType,
    val start: YamlMark,
    val end: YamlMark,
    value: ByteArray = byteArrayOf(),
    anchor: ByteArray? = null,
    tag: ByteArray? = null,
    val encoding: YamlEncoding = YamlEncoding.Any,
    val scalarStyle: YamlStyle = YamlStyle.Any,
    val collectionStyle: YamlCollectionStyle = YamlCollectionStyle.Any,
    val implicit: Boolean = false,
    val plainImplicit: Boolean = false,
    val quotedImplicit: Boolean = false,
    val version: YamlVersionDirective? = null,
    directives: List<YamlTagDirective> = emptyList(),
) {
    private val bytes=value.copyOf()
    private val anchorBytes=anchor?.let(::cString)
    private val tagBytes=tag?.let(::cString)
    val value: ByteArray get()=bytes.copyOf()
    val anchor: ByteArray? get()=anchorBytes?.copyOf()
    val tag: ByteArray? get()=tagBytes?.copyOf()
    val directives: List<YamlTagDirective> = Collections.unmodifiableList(ArrayList(directives))
}
enum class YamlParserState {
    StreamStart, ImplicitDocumentStart, DocumentStart, DocumentContent, DocumentEnd,
    BlockNode, BlockNodeOrIndentlessSequence, FlowNode, BlockSequenceFirstEntry,
    BlockSequenceEntry, IndentlessSequenceEntry, BlockMappingFirstKey, BlockMappingKey,
    BlockMappingValue, FlowSequenceFirstEntry, FlowSequenceEntry, FlowSequenceEntryMappingKey,
    FlowSequenceEntryMappingValue, FlowSequenceEntryMappingEnd, FlowMappingFirstKey,
    FlowMappingKey, FlowMappingValue, FlowMappingEmptyValue, End,
}
sealed interface YamlParseResult {
    data class Event(val value: YamlEvent) : YamlParseResult
    data class Failure(val error: YamlScanFailure) : YamlParseResult
    data object End : YamlParseResult
}
