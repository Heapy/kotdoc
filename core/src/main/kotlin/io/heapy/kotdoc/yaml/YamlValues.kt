// Data.Yaml.Internal 0.11.11.2, © 2008 Michael Snoyman, BSD-2-Clause.
// Text.Libyaml 0.1.4 and Aeson 2.2.3.0; complete original notices in licenses/.
package io.heapy.kotdoc.yaml

import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.json.PrettyJson
import io.heapy.kotdoc.json.PrettyValue
import io.heapy.kotdoc.json.ScientificFormatter
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.parsing.ParserCharacters
import io.heapy.kotdoc.parsing.Utf8Text
import java.util.Collections

/** Text.Libyaml's event model, distinct from the C event's marks/directives. */
sealed interface YamlValueEvent {
    data object StreamStart : YamlValueEvent
    data object StreamEnd : YamlValueEvent
    data object DocumentStart : YamlValueEvent
    data object DocumentEnd : YamlValueEvent
    data class Alias(val anchor: CharacterString) : YamlValueEvent
    class Scalar(val scalar: YamlScalarEvent) : YamlValueEvent {
        override fun equals(other: Any?) = other is Scalar && scalar.contents.contentEquals(other.scalar.contents) &&
            scalar.tag == other.scalar.tag && scalar.style == other.scalar.style && scalar.anchor == other.scalar.anchor
        override fun hashCode() = listOf(scalar.contents.contentHashCode(),scalar.tag,scalar.style,scalar.anchor).hashCode()
    }
    data class SequenceStart(val tag: YamlTag, val style: YamlCollectionStyle, val anchor: CharacterString?) : YamlValueEvent
    data object SequenceEnd : YamlValueEvent
    data class MappingStart(val tag: YamlTag, val style: YamlCollectionStyle, val anchor: CharacterString?) : YamlValueEvent
    data object MappingEnd : YamlValueEvent

    companion object {
        /** Tags use ByteString.Char8, while libyaml restricts anchors to ASCII. */
        fun fromParser(event: YamlEvent): YamlValueEvent? {
            fun chars(bytes: ByteArray?) = bytes?.let { CharacterString(it.map { b -> b.toInt() and 255 }.toIntArray()) }
            val anchor = chars(event.anchor)
            val tag = tagFromChars(chars(event.tag) ?: CharacterStringChars.empty)
            return when (event.type) {
                YamlEventType.None -> null
                YamlEventType.StreamStart -> StreamStart
                YamlEventType.StreamEnd -> StreamEnd
                YamlEventType.DocumentStart -> DocumentStart
                YamlEventType.DocumentEnd -> DocumentEnd
                YamlEventType.Alias -> Alias(checkNotNull(anchor))
                YamlEventType.Scalar -> Scalar(YamlScalarEvent(event.value, tag, event.scalarStyle, anchor))
                YamlEventType.SequenceStart -> SequenceStart(tag, event.collectionStyle, anchor)
                YamlEventType.SequenceEnd -> SequenceEnd
                YamlEventType.MappingStart -> MappingStart(tag, event.collectionStyle, anchor)
                YamlEventType.MappingEnd -> MappingEnd
            }
        }
    }
}

fun tagFromChars(value: CharacterString): YamlTag = when (value) {
    CharacterStringChars.fromString("") -> YamlTag.NoTag
    CharacterStringChars.fromString("tag:yaml.org,2002:str") -> YamlTag.StrTag
    CharacterStringChars.fromString("tag:yaml.org,2002:float") -> YamlTag.FloatTag
    CharacterStringChars.fromString("tag:yaml.org,2002:null") -> YamlTag.NullTag
    CharacterStringChars.fromString("tag:yaml.org,2002:bool") -> YamlTag.BoolTag
    CharacterStringChars.fromString("tag:yaml.org,2002:set") -> YamlTag.SetTag
    CharacterStringChars.fromString("tag:yaml.org,2002:int") -> YamlTag.IntTag
    CharacterStringChars.fromString("tag:yaml.org,2002:seq") -> YamlTag.SeqTag
    CharacterStringChars.fromString("tag:yaml.org,2002:map") -> YamlTag.MapTag
    else -> YamlTag.UriTag(value)
}

sealed interface YamlPathElement {
    class Key(value: String) : YamlPathElement {
        val value = TextChars.fromString(value)
        override fun equals(other: Any?) = other is Key && value == other.value
        override fun hashCode() = value.hashCode()
    }
    data class Index(val value: Long) : YamlPathElement
}
class YamlDuplicateKey(path: List<YamlPathElement>) {
    val path: List<YamlPathElement> = Collections.unmodifiableList(ArrayList(path))
    override fun equals(other: Any?) = other is YamlDuplicateKey && path == other.path
    override fun hashCode() = path.hashCode()
    fun show(precedence: Long = 0): String {
        val body = "DuplicateKey " + path.joinToString(",","[","]") { element -> when (element) {
            is YamlPathElement.Key -> "Key ${HaskellString.quote(element.value)}"
            is YamlPathElement.Index -> "Index " + if (element.value < 0) "(${element.value})" else element.value.toString()
        } }
        return if (precedence > 10) "($body)" else body
    }
    override fun toString() = show()
}

sealed interface YamlExceptionDetail {
    data class Generic(val detail: CharacterString) : YamlExceptionDetail
    data class Parse(val problem: CharacterString, val context: CharacterString, val mark: YamlMark) : YamlExceptionDetail
}

/** All original ParseException cases. Host exception Show is supplied at the IO boundary. */
sealed class YamlValueException : Exception() {
    data object NonScalarKey : YamlValueException()
    data class UnknownAlias(val anchor: CharacterString) : YamlValueException()
    data class UnexpectedEvent(val received: YamlValueEvent?, val expected: YamlValueEvent?) : YamlValueException()
    data class InvalidYaml(val error: YamlExceptionDetail?) : YamlValueException()
    data object MultipleDocuments : YamlValueException()
    data class AesonException(val detail: CharacterString) : YamlValueException()
    data class OtherParseException(val shownException: CharacterString) : YamlValueException()
    class NonStringKey(path: List<YamlPathElement>) : YamlValueException() {
        val path: List<YamlPathElement> = Collections.unmodifiableList(ArrayList(path))
    }
    data class NonStringKeyAlias(val anchor: CharacterString, val value: PrettyValue) : YamlValueException()
    data object CyclicIncludes : YamlValueException()
    data class LoadSettingsException(val file: CharacterString, val error: YamlValueException) : YamlValueException()

    override val message: String get() = TextChars.fromPoints(pretty().points())
    fun pretty(): CharacterString {
        val pieces = mutableListOf<CharacterString>()
        fun text(s: String) { pieces.add(CharacterStringChars.fromString(s)) }
        fun raw(s: CharacterString) { pieces.add(s) }
        // LoadSettingsException can be arbitrarily nested without consuming the JVM stack.
        var exception: YamlValueException = this
        while (exception is LoadSettingsException) {
            text("Could not parse file as YAML: "); raw(exception.file); text("\n"); exception = exception.error
        }
        when (exception) {
            NonScalarKey -> text("Non scalar key")
            is UnknownAlias -> { text("Unknown alias `"); raw(exception.anchor); text("`") }
            is UnexpectedEvent -> text("Unexpected event: expected\n  ${showMaybeEvent(exception.expected)}\nbut received\n  ${showMaybeEvent(exception.received)}\n")
            is InvalidYaml -> when (val e = exception.error) {
                null -> text("Unspecified YAML error")
                is YamlExceptionDetail.Generic -> { text("YAML exception:\n"); raw(e.detail) }
                is YamlExceptionDetail.Parse -> {
                    text("YAML parse exception at line ${e.mark.line.toLong()}, column ${e.mark.column.toLong()}")
                    if (e.context.points().isEmpty()) text(":\n") else { text(",\n"); raw(e.context); text(":\n") }
                    raw(e.problem)
                }
            }
            MultipleDocuments -> text("Multiple YAML documents encountered")
            is AesonException -> { text("Aeson exception:\n"); raw(exception.detail) }
            is OtherParseException -> { text("Generic parse exception:\n"); raw(exception.shownException) }
            is NonStringKey -> text("Error in ${formatYamlPath(exception.path)}: Non-string keys are not supported")
            is NonStringKeyAlias -> { text("Non-string key alias:\n  Anchor name: "); raw(exception.anchor); text("\n  Value: ${showYamlValue(exception.value)}\n") }
            CyclicIncludes -> text("Cyclic includes")
            is LoadSettingsException -> error("unreachable")
        }
        return CharacterStringChars.concat(pieces)
    }
}

/** JSONPath formatting uses the pinned Haskell Unicode classification table. */
fun formatYamlPath(path: List<YamlPathElement>): String = buildString {
    append('$')
    path.forEach { part -> when (part) {
        is YamlPathElement.Index -> append("[${part.value}]")
        is YamlPathElement.Key -> {
            val cps = part.value.codePoints().toArray()
            if (cps.isNotEmpty() && ParserCharacters.isAlpha(cps[0]) && cps.drop(1).all(ParserCharacters::isAlphaNum)) append(".${part.value}")
            else { append("['"); part.value.forEach { if (it == '\'' || it == '\\') append('\\'); append(it) }; append("']") }
        }
    } }
}

private fun showTag(tag: YamlTag): String = when (tag) {
    is YamlTag.UriTag -> "(UriTag ${HaskellString.quoteCodePoints(tag.value.points())})"
    else -> tag.javaClass.simpleName
}
private fun showAnchor(anchor: CharacterString?): String = anchor?.let { "(Just ${HaskellString.quoteCodePoints(it.points())})" } ?: "Nothing"
private fun showEvent(event: YamlValueEvent): String = when (event) {
    YamlValueEvent.StreamStart -> "EventStreamStart"
    YamlValueEvent.StreamEnd -> "EventStreamEnd"
    YamlValueEvent.DocumentStart -> "EventDocumentStart"
    YamlValueEvent.DocumentEnd -> "EventDocumentEnd"
    is YamlValueEvent.Alias -> "EventAlias ${HaskellString.quoteCodePoints(event.anchor.points())}"
    is YamlValueEvent.Scalar -> event.scalar.let { "EventScalar ${HaskellString.quoteCodePoints(it.contents.map { b -> b.toInt() and 255 }.toIntArray())} ${showTag(it.tag)} ${it.style} ${showAnchor(it.anchor)}" }
    is YamlValueEvent.SequenceStart -> "EventSequenceStart ${showTag(event.tag)} ${event.style.name}Sequence ${showAnchor(event.anchor)}"
    YamlValueEvent.SequenceEnd -> "EventSequenceEnd"
    is YamlValueEvent.MappingStart -> "EventMappingStart ${showTag(event.tag)} ${event.style.name}Mapping ${showAnchor(event.anchor)}"
    YamlValueEvent.MappingEnd -> "EventMappingEnd"
}
private fun showMaybeEvent(event: YamlValueEvent?) = if (event == null) "Nothing" else {
    val shown = showEvent(event)
    "Just " + if (' ' in shown) "($shown)" else shown
}

/** Aeson's custom Show Value; object keys are sorted in scalar Text order. */
fun showYamlValue(value: PrettyValue): String = buildString {
    data class Task(val value: PrettyValue?, val text: String = "")
    val stack = ArrayDeque<Task>(); stack.addLast(Task(value))
    while (stack.isNotEmpty()) {
        val task = stack.removeLast()
        when (val v = task.value) {
            null -> append(task.text)
            PrettyValue.Null -> append("Null")
            is PrettyValue.Bool -> append(if (v.value) "Bool True" else "Bool False")
            is PrettyValue.Text -> append("String ${HaskellString.quote(v.value)}")
            is PrettyValue.Number -> {
                val number = ScientificFormatter.format(v.value)
                append("Number "); append(if (number.startsWith('-')) "($number)" else number)
            }
            is PrettyValue.Array -> {
                append("Array ["); stack.addLast(Task(null, "]"))
                v.values.indices.reversed().forEach { i -> stack.addLast(Task(v.values[i])); if (i > 0) stack.addLast(Task(null, ",")) }
            }
            is PrettyValue.Object -> {
                append("Object (fromList ["); stack.addLast(Task(null, "])"))
                val entries = v.values.entries.sortedWith { a,b -> PrettyJson.compare.compare(a.key,b.key) }
                entries.indices.reversed().forEach { i ->
                    val entry = entries[i]; stack.addLast(Task(null, ")")); stack.addLast(Task(entry.value))
                    stack.addLast(Task(null, "(${HaskellString.quote(entry.key)},")); if (i > 0) stack.addLast(Task(null, ","))
                }
            }
        }
    }
}

sealed interface YamlValueResult<out A> {
    class Success<A>(warnings: List<YamlDuplicateKey>, val value: A) : YamlValueResult<A> {
        val warnings: List<YamlDuplicateKey> = Collections.unmodifiableList(ArrayList(warnings))
    }
    data class Failure(val error: YamlValueException) : YamlValueResult<Nothing>
}
sealed interface YamlFromJsonResult<out A> {
    data class Success<A>(val value: A) : YamlFromJsonResult<A>
    data class Failure(val error: CharacterString) : YamlFromJsonResult<Nothing>
}

/** Demand-driven event parser, including document-wide anchors and original merge rules. */
object YamlValues {
    fun decode(bytes: ByteArray): YamlValueResult<PrettyValue> = decodeEvents(parserSource(bytes))
    fun decodeAll(bytes: ByteArray): YamlValueResult<List<PrettyValue>> = decodeAllEvents(parserSource(bytes))
    fun parserSource(bytes: ByteArray): () -> YamlValueEvent? {
        // Text.Libyaml.decodeMarked bypasses parser allocation for empty bytes.
        if (bytes.isEmpty()) return { null }
        val owned = bytes.copyOf(); var position = 0
        val parser = YamlParser(YamlScanner(YamlReader(YamlReadHandler { maximum ->
            val size = minOf(maximum, owned.size - position)
            owned.copyOfRange(position, position + size).also { position += size }
        })))
        return { when (val result = parser.next()) {
            YamlParseResult.End -> null
            is YamlParseResult.Event -> YamlValueEvent.fromParser(result.value)
            is YamlParseResult.Failure -> throw YamlValueException.InvalidYaml(YamlExceptionDetail.Parse(
                CharacterStringChars.fromString(result.error.problem ?: ""), CharacterStringChars.fromString(result.error.context ?: ""), result.error.problemMark,
            ))
        } }
    }
    fun decodeEvents(source: () -> YamlValueEvent?): YamlValueResult<PrettyValue> = when (val all = decodeAllEvents(source)) {
        is YamlValueResult.Failure -> all
        is YamlValueResult.Success -> when (all.value.size) {
            0 -> YamlValueResult.Success(all.warnings, PrettyValue.Null)
            1 -> YamlValueResult.Success(all.warnings, all.value.single())
            else -> YamlValueResult.Failure(YamlValueException.MultipleDocuments)
        }
    }
    fun decodeAllEvents(source: () -> YamlValueEvent?): YamlValueResult<List<PrettyValue>> = try {
        Decoder(source).run()
    } catch (error: YamlValueException) { YamlValueResult.Failure(error) }

    fun <A> decodeAs(bytes: ByteArray, convert: (PrettyValue) -> YamlFromJsonResult<A>): YamlValueResult<A> = when (val parsed = decode(bytes)) {
        is YamlValueResult.Failure -> parsed
        is YamlValueResult.Success -> when (val converted = convert(parsed.value)) {
            is YamlFromJsonResult.Success -> YamlValueResult.Success(parsed.warnings,converted.value)
            is YamlFromJsonResult.Failure -> YamlValueResult.Failure(YamlValueException.AesonException(converted.error))
        }
    }

    /** All documents are parsed before FromJSON is called; conversion stops at its first Left. */
    fun <A> decodeAllAs(bytes: ByteArray, convert: (PrettyValue) -> YamlFromJsonResult<A>): YamlValueResult<List<A>> = when (val parsed = decodeAll(bytes)) {
        is YamlValueResult.Failure -> parsed
        is YamlValueResult.Success -> {
            val values = mutableListOf<A>(); var error: CharacterString? = null
            for (value in parsed.value) when (val converted = convert(value)) {
                is YamlFromJsonResult.Success -> values.add(converted.value)
                is YamlFromJsonResult.Failure -> { error = converted.error; break }
            }
            if (error != null) YamlValueResult.Failure(YamlValueException.AesonException(error)) else YamlValueResult.Success(parsed.warnings, values.toList())
        }
    }

    private class Decoder(private val source: () -> YamlValueEvent?) {
        private val anchors = mutableMapOf<CharacterString, PrettyValue>()
        private val warnings = ArrayDeque<YamlDuplicateKey>()
        private var peeked: YamlValueEvent? = null
        private var available = false
        private fun peek(): YamlValueEvent? { if (!available) { peeked = source(); available = true }; return peeked }
        private fun next(): YamlValueEvent? = if (available) peeked.also { available = false; peeked = null } else source()
        private fun require(event: YamlValueEvent) { val received = next(); if (received != event) throw YamlValueException.UnexpectedEvent(received,event) }
        fun run(): YamlValueResult.Success<List<PrettyValue>> {
            val start = next()
            if (start == null) return YamlValueResult.Success(emptyList(),emptyList())
            if (start != YamlValueEvent.StreamStart) throw YamlValueException.UnexpectedEvent(start,null)
            val documents = mutableListOf<PrettyValue>()
            while (true) when (val event = next()) {
                YamlValueEvent.StreamEnd -> return YamlValueResult.Success(warnings.toList(),documents.toList())
                YamlValueEvent.DocumentStart -> { documents.add(node()); require(YamlValueEvent.DocumentEnd) }
                else -> throw YamlValueException.UnexpectedEvent(event,null)
            }
        }
        // ReaderT's source JSONPath is a persistent reverse list. Share ancestor
        // tails and materialize paths only when a warning/error is produced.
        private class Path(val element: YamlPathElement, val parent: Path?)
        private fun reversePath(path: Path?): List<YamlPathElement> = buildList {
            var current = path
            while (current != null) { add(current.element); current = current.parent }
        }
        private sealed class Frame(val path: Path?, val anchor: CharacterString?) {
            class Sequence(path: Path?, anchor: CharacterString?) : Frame(path,anchor) { val values = mutableListOf<PrettyValue>() }
            class Mapping(path: Path?, anchor: CharacterString?) : Frame(path,anchor) {
                val values = mutableMapOf<String,PrettyValue>(); var merged = emptySet<String>(); var key: String? = null
            }
        }
        private fun scalar(event: YamlScalarEvent): PrettyValue = YamlScalars.resolve(event).also { value -> event.anchor?.let { anchors[it] = value } }
        private fun alias(anchor: CharacterString) = anchors[anchor] ?: throw YamlValueException.UnknownAlias(anchor)
        private fun node(): PrettyValue {
            val frames = ArrayDeque<Frame>(); var path: Path? = null; var result: PrettyValue? = null
            while (true) {
                if (result == null) when (val event = next()) {
                    is YamlValueEvent.Scalar -> result = scalar(event.scalar)
                    is YamlValueEvent.Alias -> result = alias(event.anchor)
                    is YamlValueEvent.SequenceStart -> frames.addLast(Frame.Sequence(path,event.anchor))
                    is YamlValueEvent.MappingStart -> frames.addLast(Frame.Mapping(path,event.anchor))
                    else -> throw YamlValueException.UnexpectedEvent(event,null)
                }
                if (result != null) {
                    if (frames.isEmpty()) return result
                    when (val frame = frames.last()) {
                        is Frame.Sequence -> frame.values.add(result)
                        is Frame.Mapping -> {
                            val key = checkNotNull(frame.key)
                            val merge = when {
                                key != "<<" -> null
                                result is PrettyValue.Object -> result.values
                                result is PrettyValue.Array -> buildMap { result.values.forEach { v -> if (v is PrettyValue.Object) v.values.forEach { (k,v) -> putIfAbsent(k,v) } } }
                                else -> null
                            }
                            if (merge != null) {
                                // Source replaces, rather than unions, the merged-key tracking set.
                                frame.merged = merge.keys - frame.values.keys
                                merge.forEach { (k,v) -> frame.values.putIfAbsent(k,v) }
                            } else {
                                if (key in frame.values && key !in frame.merged) warnings.addFirst(YamlDuplicateKey(reversePath(Path(YamlPathElement.Key(key),frame.path)).asReversed()))
                                frame.merged = frame.merged - key; frame.values[key] = result
                            }
                            frame.key = null
                        }
                    }
                    result = null
                }
                when (val frame = frames.last()) {
                    is Frame.Sequence -> if (peek() == YamlValueEvent.SequenceEnd) {
                        next(); frames.removeLast(); result = PrettyValue.Array(frame.values); frame.anchor?.let { anchors[it] = result }
                    } else path = Path(YamlPathElement.Index(frame.values.size.toLong()),frame.path)
                    is Frame.Mapping -> when (val event = next()) {
                        YamlValueEvent.MappingEnd -> {
                            frames.removeLast(); result = PrettyValue.Object(frame.values); frame.anchor?.let { anchors[it] = result }
                        }
                        is YamlValueEvent.Scalar -> { scalar(event.scalar); frame.key = Utf8Text.decodeLenient(event.scalar.contents) }
                        is YamlValueEvent.Alias -> {
                            val v = alias(event.anchor)
                            if (v !is PrettyValue.Text) throw YamlValueException.NonStringKeyAlias(event.anchor,v)
                            frame.key = v.value
                        }
                        else -> throw YamlValueException.NonStringKey(reversePath(frame.path))
                    }.also { if (frame.key != null) path = Path(YamlPathElement.Key(checkNotNull(frame.key)),frame.path) }
                }
            }
        }
    }
}
