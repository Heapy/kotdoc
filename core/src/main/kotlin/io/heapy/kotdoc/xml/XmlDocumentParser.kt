// Text.XML/Unresolved/Stream.Parse 1.10.1.0, xml-types 0.3.8,
// Data.Conduit.Attoparsec 1.3.8. Copyright Michael Snoyman, John Millikin,
// Aristid Breitkreuz and contributors. MIT; licenses/xml-conduit-MIT.txt.
package io.heapy.kotdoc.xml

import io.heapy.kotdoc.ast.UnicodeTextOrder
import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.parsing.ParserCharacters
import java.util.TreeMap
import java.util.TreeSet

/** Text.XML.parseText with default ParseSettings. XML.Light remains a separate API. */
object XmlDocumentParser {
    fun parseText(text: String): XmlDocumentResult = parseTextChunks(pack(text))
    fun parseTextChunks(chunks: List<String>): XmlDocumentResult = Parser(chunks, {}).parse()
    internal fun parseTextChunks(chunks: List<String>, inputEnd: () -> Unit): XmlDocumentResult = Parser(chunks, inputEnd).parse()
    private fun safeText(text: String) = buildString {
        text.codePoints().forEach { appendCodePoint(if (it in 0xd800..0xdfff) 0xfffd else it) }
    }
    /** Data.Text.Lazy.pack's original UTF-8 chunk allocation boundary. */
    private fun pack(text: String): List<String> {
        val input = safeText(text); val chunks = mutableListOf<String>(); var start = 0; var bytes = 0; var i = 0
        while (i < input.length) {
            val point = input.codePointAt(i); i += Character.charCount(point)
            bytes += when { point < 0x80 -> 1; point < 0x800 -> 2; point < 0x10000 -> 3; else -> 4 }
            if (bytes + 3 >= 16368) { chunks.add(input.substring(start, i)); start = i; bytes = 0 }
        }
        if (start < input.length) chunks.add(input.substring(start))
        return chunks
    }
    private class DocumentFailure(val error: String) : RuntimeException(null, null, false, false)
    private data class Position(val line: Int, val column: Int, val offset: Int) {
        override fun toString() = "$line:$column ($offset)"
    }
    private data class Range(val start: Position, val end: Position) {
        override fun toString() = "$start-$end"
    }
    private sealed interface Event {
        data class Begin(val name: XmlDocName, val attributes: List<Pair<XmlDocName, List<XmlPiece>>>) : Event
        data class End(val name: XmlDocName) : Event
        data class Content(val content: XmlPiece) : Event
        data class Comment(val text: String) : Event
        data class Instruction(val instruction: XmlInstruction) : Event
        data class CData(val text: String) : Event
        data class Doctype(val doctype: XmlDoctype) : Event
        data object EndDoctype : Event
        data object EndDocument : Event
    }
    private data class Located(val range: Range?, val event: Event)
    private class Namespace(val default: String? = null, val prefixes: Map<String, String> = emptyMap()) {
        fun qualify(name: XmlTokenName, attribute: Boolean = false): XmlDocName = when (name.prefix) {
            "xml" -> XmlDocName(name.local, "http://www.w3.org/XML/1998/namespace", "xml")
            null -> XmlDocName(name.local, if (attribute) null else default)
            else -> XmlDocName(name.local, prefixes[name.prefix], name.prefix)
        }
    }
    private class Parser(sourceChunks: List<String>, inputEnd: () -> Unit) {
        // Haskell Text.pack replaces unpaired surrogates; the upstream BOM typo is intentional.
        val originalChunks = sourceChunks.map(::safeText).filter { it.isNotEmpty() }
        val droppedBom = originalChunks.firstOrNull()?.startsWith('\uFEEF') == true
        val chunks = originalChunks.mapIndexed { i, text -> if (i == 0 && droppedBom) text.drop(1) else text }.filter { it.isNotEmpty() }
        val input = chunks.joinToString("")
        val chunkEnds = IntArray(chunks.size).also { ends -> var end = 0; chunks.forEachIndexed { i, s -> end += s.length; ends[i] = end } }
        val lexer = XmlDocumentLexer(input, inputEnd, chunkEnds)
        var emptyBomToken = droppedBom && input.isEmpty()
        val entities = mutableListOf<Pair<String, String>>()
        val namespaces = ArrayDeque<Namespace>()
        val pending = ArrayDeque<Located>()
        val unresolved = TreeSet(UnicodeTextOrder)
        val positions: Array<Position?> = arrayOfNulls(input.length + 1)
        init {
            var line = 1; var column = 1; var offset = 0; var i = 0
            while (i < input.length) {
                positions[i] = Position(line, column, offset)
                val point = input.codePointAt(i); val count = Character.charCount(point)
                if (count == 2) positions[i + 1] = positions[i]
                i += count; offset++
                if (point == 10) { line++; column = 1 } else column++
            }
            positions[input.length] = Position(line, column, offset)
        }
        fun pos(index: Int) = checkNotNull(positions[index])
        fun fail(error: String): Nothing = throw DocumentFailure(error)
        fun parse(): XmlDocumentResult = try {
            val before = misc()
            val dt = (peek().event as? Event.Doctype)?.doctype?.also { take(); check(take().event == Event.EndDoctype) }
            val after = misc()
            if (peek().event !is Event.Begin) {
                val event = take()
                if (event.event == Event.EndDocument) fail("Missing root element") else fail(contentAfter(event))
            }
            val root = element()
            val epilogue = misc()
            val last = take()
            if (last.event != Event.EndDocument) fail(contentAfter(last))
            if (unresolved.isNotEmpty()) fail("UnresolvedEntityException (fromList " + unresolved.joinToString(",", "[", "]", transform = HaskellString::quote) + ")")
            XmlDocumentResult.Success(XmlDocument(before, dt, after, root, epilogue))
        } catch (error: DocumentFailure) { XmlDocumentResult.Failure(error.error) }
          catch (error: XmlLexFailure) {
              XmlDocumentResult.Failure("ParseError {errorContexts = " + error.contexts.joinToString(",", "[", "]", transform = HaskellString::quote) +
                  ", errorMessage = ${HaskellString.quote(error.reason)}, errorPosition = ${pos(error.at)}}")
          }
        fun peek(): Located {
            while (pending.isEmpty()) {
                if (emptyBomToken) { emptyBomToken = false; lexer.token() }
                if (lexer.done) return Located(null, Event.EndDocument)
                val start = lexer.position; val token = lexer.token(); val range = Range(pos(start), pos(lexer.position))
                convert(token, range)
            }
            return pending.first()
        }
        fun take() = peek().also { if (pending.isNotEmpty()) pending.removeFirst() }
        fun misc(): List<XmlMisc> = buildList {
            while (true) when (val event = peek().event) {
                is Event.Comment -> { take(); add(XmlMisc.Comment(event.text)) }
                is Event.Instruction -> { take(); add(XmlMisc.Instruction(event.instruction)) }
                is Event.Content -> {
                    val text = (event.content as? XmlPiece.Text)?.text
                    if (text != null && text.codePoints().allMatch(ParserCharacters::isSpace)) take() else break
                }
                else -> break
            }
        }
        fun contentAfter(event: Located) = (event.range?.let { "$it: " } ?: "") + "Found content after root element: ${show(event.event)}"
        fun missingEnd(name: XmlDocName, event: Located): Nothing = fail((event.range?.let { "$it: " } ?: "") +
            "Expected end element for: $name, but received: ${show(event.event)}")
        class Frame(val name: XmlDocName, val attrs: Map<XmlDocName, String>) {
            val nodes = mutableListOf<XmlDocNode>()
            var text: StringBuilder? = null
            fun flush() { text?.let { nodes.add(XmlDocNode.Text(it.toString())) }; text = null }
            fun emit(node: XmlDocNode) {
                if (node is XmlDocNode.Text) {
                    val buffer = text ?: StringBuilder().also { text = it }; buffer.append(node.text)
                } else { flush(); nodes.add(node) }
            }
        }
        fun element(): XmlDocElement {
            val stack = ArrayDeque<Frame>()
            fun textValue(pieces: List<XmlPiece>): String = buildString {
                for (piece in pieces) when (piece) { is XmlPiece.Text -> append(piece.text); is XmlPiece.Entity -> unresolved.add(piece.name) }
            }
            fun emit(node: XmlDocNode) = stack.last().emit(node)
            while (true) when (val located = take()) {
                else -> when (val event = located.event) {
                    is Event.Begin -> {
                        val attrs = TreeMap<XmlDocName, String>()
                        for ((name, values) in event.attributes) {
                            val value = textValue(values)
                            attrs.remove(name); attrs[name] = value // Map.fromList keeps the final equal key's prefix too.
                        }
                        stack.addLast(Frame(event.name, attrs))
                    }
                    is Event.End -> {
                        val frame = stack.last()
                        if (frame.name != event.name) missingEnd(frame.name, located)
                        stack.removeLast()
                        frame.flush()
                        val element = XmlDocElement(frame.name, frame.attrs, frame.nodes)
                        if (stack.isEmpty()) return element
                        emit(XmlDocNode.Element(element))
                    }
                    is Event.Content -> when (val content = event.content) {
                        is XmlPiece.Text -> emit(XmlDocNode.Text(content.text))
                        is XmlPiece.Entity -> unresolved.add(content.name)
                    }
                    is Event.CData -> emit(XmlDocNode.Text(event.text))
                    is Event.Comment -> emit(XmlDocNode.Comment(event.text))
                    is Event.Instruction -> emit(XmlDocNode.Instruction(event.instruction))
                    else -> missingEnd(stack.last().name, located)
                }
            }
        }
        fun convert(token: XmlDocToken, range: Range, fixed: Namespace? = null) {
            val namespace = fixed ?: namespaces.lastOrNull() ?: Namespace()
            fun emit(event: Event) { pending.addLast(Located(range, event)) }
            when (token) {
                is XmlDocToken.Declaration -> Unit
                is XmlDocToken.Begin -> {
                    var next = namespace
                    val attrs = mutableListOf<Pair<XmlTokenName, List<XmlPiece>>>()
                    for ((name, values) in token.attributes) {
                        fun spelling() = values.joinToString("") { when (it) { is XmlPiece.Text -> it.text; is XmlPiece.Entity -> "&${it.name};" } }
                        if (name.prefix == "xmlns") next = Namespace(next.default, next.prefixes + (name.local to spelling()))
                        else if (name.prefix == null && name.local == "xmlns") next = Namespace(spelling().ifEmpty { null }, next.prefixes)
                        else attrs.add(name to values.flatMap { piece ->
                            val resolved = if (fixed == null) resolve(piece) else listOf(XmlDocToken.Content(piece))
                            resolved.mapNotNull { (it as? XmlDocToken.Content)?.content }
                        })
                    }
                    val name = next.qualify(token.name)
                    emit(Event.Begin(name, attrs.map { next.qualify(it.first, true) to it.second }))
                    if (token.closed) emit(Event.End(name)) else if (fixed == null) namespaces.addLast(next)
                }
                is XmlDocToken.End -> {
                    emit(Event.End(namespace.qualify(token.name)))
                    if (fixed == null && namespaces.isNotEmpty()) namespaces.removeLast()
                }
                is XmlDocToken.Content -> {
                    val expanded = if (fixed == null) resolve(token.content) else listOf(token)
                    if (expanded.size == 1 && expanded[0] == token) emit(Event.Content(token.content))
                    else expanded.forEach { convert(it, range, namespace) }
                }
                is XmlDocToken.CData -> emit(Event.CData(token.text))
                is XmlDocToken.Comment -> emit(Event.Comment(token.text))
                is XmlDocToken.Instruction -> emit(Event.Instruction(token.instruction))
                is XmlDocToken.Doctype -> {
                    if (fixed == null) entities.addAll(token.entities)
                    emit(Event.Doctype(token.doctype)); emit(Event.EndDoctype)
                }
            }
        }
        fun resolve(content: XmlPiece): List<XmlDocToken> =
            if (content is XmlPiece.Entity) expand(content.name, emptySet()) ?: listOf(XmlDocToken.Content(content))
            else listOf(XmlDocToken.Content(content))
        fun expand(name: String, seen: Set<String>): List<XmlDocToken>? {
            class Expansion(val name: String, val tokens: List<XmlDocToken>) {
                var index = 0
                val result = mutableListOf<XmlDocToken>()
                var size = 0
            }
            val work = ArrayDeque<Expansion>(); val active = seen.toMutableSet()
            fun push(name: String): Boolean {
                if (name in active) return false
                val value = entities.firstOrNull { it.first == name }?.second ?: return false
                val tokens = try { val lexer = XmlDocumentLexer(value); buildList<XmlDocToken> { while (!lexer.done) add(lexer.token()) } }
                    catch (_: XmlLexFailure) { return false }
                active.add(name); work.addLast(Expansion(name, tokens)); return true
            }
            if (!push(name)) return null
            while (work.isNotEmpty()) {
                val frame = work.last()
                if (frame.index == frame.tokens.size) {
                    work.removeLast(); active.remove(frame.name)
                    if (work.isEmpty()) return frame.result
                    val parent = work.last(); parent.size += frame.size
                    if (parent.size > 8192) return null
                    parent.result.addAll(frame.result)
                    continue
                }
                val token = frame.tokens[frame.index++]
                val entity = (token as? XmlDocToken.Content)?.content as? XmlPiece.Entity
                if (entity != null) {
                    if (!push(entity.name)) return null
                } else {
                    frame.size += token.serialized().toByteArray(Charsets.UTF_8).size
                    if (frame.size > 8192) return null
                    frame.result.add(token)
                }
            }
            error("No entity expansion frame")
        }
        fun show(event: Event): String {
            fun text(value: String) = HaskellString.quote(value)
            fun instruction(value: XmlInstruction) = "Instruction {instructionTarget = ${text(value.target)}, instructionData = ${text(value.data)}}"
            fun piece(piece: XmlPiece) = when (piece) { is XmlPiece.Text -> "ContentText ${text(piece.text)}"; is XmlPiece.Entity -> "ContentEntity ${text(piece.name)}" }
            fun external(id: XmlExternalId) = when (id) { is XmlExternalId.SystemId -> "SystemID ${text(id.system)}"; is XmlExternalId.PublicId -> "PublicID ${text(id.public)} ${text(id.system)}" }
            return when (event) {
                is Event.Begin -> "EventBeginElement ${event.name.haskellShow(11)} " + event.attributes.joinToString(",", "[", "]") { (name, values) ->
                    "($name," + values.joinToString(",", "[", "]", transform = ::piece) + ")"
                }
                is Event.End -> "EventEndElement ${event.name.haskellShow(11)}"
                is Event.Content -> "EventContent (${piece(event.content)})"
                is Event.CData -> "EventCDATA ${text(event.text)}"
                is Event.Comment -> "EventComment ${text(event.text)}"
                is Event.Instruction -> "EventInstruction (${instruction(event.instruction)})"
                is Event.Doctype -> "EventBeginDoctype ${text(event.doctype.name)} " + (event.doctype.externalId?.let { "(Just (${external(it)}))" } ?: "Nothing")
                Event.EndDoctype -> "EventEndDoctype"
                Event.EndDocument -> "EventEndDocument"
            }
        }
    }
}
