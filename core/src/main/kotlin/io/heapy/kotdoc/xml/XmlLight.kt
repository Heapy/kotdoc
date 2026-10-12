// Ports Text.Pandoc.XML.Light (copyright 2007 Galois, Inc.; 2021-2024 John
// MacFarlane, GPL-2.0-or-later) and the
// tokenizer/namespace/entity rules of xml-conduit 1.10.1.0 Text.XML.Stream.Parse.
// xml-conduit copyright Michael Snoyman, Aristid Breitkreuz; MIT, see licenses/.
package io.heapy.kotdoc.xml

import java.math.BigInteger

/** Namespace-aware equality matches XML.Light, including unbound prefixes. */
class XmlName(val local: String, val uri: String? = null, val prefix: String? = null) {
    override fun equals(other: Any?): Boolean = other is XmlName && local == other.local && uri == other.uri &&
        (uri != null || prefix == other.prefix)
    override fun hashCode(): Int = 31 * (31 * local.hashCode() + (uri?.hashCode() ?: 0)) +
        (if (uri == null) prefix?.hashCode() ?: 0 else 0)
    override fun toString(): String = if (prefix == null) local else "$prefix:$local"
}

sealed interface XmlContent
enum class XmlTextKind { Text, Verbatim, Raw }
data class XmlText(val text: String, val kind: XmlTextKind = XmlTextKind.Text) : XmlContent
data class XmlCRef(val reference: String) : XmlContent
data class XmlAttribute(val name: XmlName, val value: String)
data class XmlElement(val name: XmlName, val attributes: List<XmlAttribute>, val content: List<XmlContent>) : XmlContent {
    val localName: String get() = name.local
    val children: List<XmlElement> get() = content.filterIsInstance<XmlElement>()
    /** XML.Light.strContent includes immediate text only, not descendant text. */
    val strContent: String get() = content.filterIsInstance<XmlText>().joinToString("") { it.text }
    fun attr(name: XmlName): String? = attributes.firstOrNull { it.name == name }?.value
    fun attr(name: String): String? = attr(XmlName(name))
}

class XmlParseException(message: String) : IllegalArgumentException("Invalid XML: $message")

object XmlLight {
    fun parseElement(input: String, entities: Map<String, String> = emptyMap()): XmlElement {
        val contents = parseContents(input, entities)
        if (contents.size == 1 && contents[0] is XmlElement) return contents[0] as XmlElement
        throw XmlParseException(if (contents.none { it is XmlElement }) "XML document has no root element"
            else "unexpected content after the root XML element")
    }

    fun parseContents(input: String, entities: Map<String, String> = emptyMap()): List<XmlContent> = Parser(entities).parse(input)

    private data class Name(val prefix: String?, val local: String)
    private sealed interface Token {
        data class Start(val name: Name, val attrs: List<Pair<Name, List<Token>>>, val closed: Boolean) : Token
        data class End(val name: Name) : Token
        data class Text(val value: String) : Token
        data class Entity(val name: String) : Token
        data class CData(val value: String) : Token
        data class Doctype(val declarations: List<Pair<String, String>>, val serialized: String) : Token
        data class Ignored(val serialized: String) : Token
    }

    private data class Namespace(val default: String? = null, val prefixes: Map<String, String> = emptyMap()) {
        fun qualify(name: Name, attribute: Boolean = false): XmlName = when (name.prefix) {
            "xml" -> XmlName(name.local, "http://www.w3.org/XML/1998/namespace", "xml")
            null -> XmlName(name.local, if (attribute) null else default)
            else -> XmlName(name.local, prefixes[name.prefix], name.prefix)
        }
    }

    private data class Frame(val name: XmlName, val attrs: List<XmlAttribute>, val content: MutableList<XmlContent>)

    private class Parser(val customEntities: Map<String, String>) {
        val declarations = mutableListOf<Pair<String, String>>()
        val namespaces = ArrayDeque<Namespace>()
        val frames = ArrayDeque<Frame>()
        val top = mutableListOf<XmlContent>()

        fun parse(input: String): List<XmlContent> {
            // This unusual codepoint is the upstream parseText.dropBOM behavior.
            val lexer = Lexer(input.removePrefix("\uFEEF"), customEntities)
            while (!lexer.done) process(lexer.token())
            if (frames.isNotEmpty()) throw XmlParseException("Missing close tag for ${frames.last().name}")
            val merged = mergeText(top)
            val nonWhitespace = merged.filterNot { it is XmlText && it.text.all(::haskellSpace) }
            return if (nonWhitespace.size == 1 && nonWhitespace[0] is XmlElement) nonWhitespace else merged
        }

        fun emit(content: XmlContent) {
            val dest = frames.lastOrNull()?.content ?: top
            dest += content
        }

        fun process(token: Token, fixedNamespace: Namespace? = null) {
            val ns = fixedNamespace ?: namespaces.lastOrNull() ?: Namespace()
            when (token) {
                is Token.Start -> {
                    var next = ns
                    for ((name, values) in token.attrs) {
                        // Namespace declarations use unexpanded entity spelling, as tokenToEvent does.
                        val text = values.joinToString("") { when (it) {
                            is Token.Text -> it.value
                            is Token.Entity -> "&${it.name};"
                            else -> ""
                        } }
                        if (name.prefix == "xmlns") next = next.copy(prefixes = next.prefixes + (name.local to text))
                        else if (name.prefix == null && name.local == "xmlns") next = next.copy(default = text.ifEmpty { null })
                    }
                    val attributes = token.attrs.map { (name, values) ->
                        val qname = if (name.prefix == "xmlns") XmlName(name.local, prefix = "xmlns") else next.qualify(name, true)
                        val text = values.flatMap { if (fixedNamespace == null) resolve(it) else listOf(it) }.mapNotNull {
                            when (it) {
                                is Token.Text -> it.value
                                is Token.Entity -> throw XmlParseException("Unresolved entity &${it.name};")
                                else -> null // Markup/CDATA inside an attribute entity is discarded upstream.
                            }
                        }.joinToString("")
                        XmlAttribute(qname, text)
                    }
                    val name = next.qualify(token.name)
                    frames.addLast(Frame(name, attributes, mutableListOf()))
                    if (token.closed) close(name)
                    else if (fixedNamespace == null) namespaces.addLast(next)
                }
                is Token.End -> {
                    close(ns.qualify(token.name))
                    if (fixedNamespace == null && namespaces.isNotEmpty()) namespaces.removeLast()
                }
                is Token.Text -> emit(XmlText(token.value))
                is Token.CData -> emit(XmlText(token.value))
                is Token.Entity -> {
                    val expanded = resolve(token)
                    if (expanded.size == 1 && expanded[0] == token) throw XmlParseException("Unresolved entity &${token.name};")
                    // xml-conduit converts each expanded token using the original namespace level.
                    expanded.forEach { process(it, ns) }
                }
                is Token.Doctype -> if (fixedNamespace == null) declarations += token.declarations
                is Token.Ignored -> Unit
            }
        }

        fun close(name: XmlName) {
            val frame = frames.lastOrNull()
            if (frame == null || frame.name != name) throw XmlParseException("Unexpected close tag $name")
            frames.removeLast()
            emit(XmlElement(frame.name, frame.attrs, mergeText(frame.content)))
        }

        fun resolve(token: Token): List<Token> = if (token is Token.Entity) expand(token.name, emptySet()) ?: listOf(token) else listOf(token)

        fun expand(name: String, seen: Set<String>): List<Token>? {
            if (name in seen) return null
            val value = declarations.firstOrNull { it.first == name }?.second ?: return null
            val tokens = try {
                val lexer = Lexer(value, customEntities)
                buildList<Token> { while (!lexer.done) add(lexer.token()) }
            } catch (_: XmlParseException) { return null }
            val result = mutableListOf<Token>()
            var size = 0
            for (token in tokens) {
                val expanded = if (token is Token.Entity) expand(token.name, seen + name) ?: return null else listOf(token)
                for (item in expanded) {
                    size += tokenSize(item)
                    if (size > 8192) return null
                    result += item
                }
            }
            return result
        }
    }

    /** Direct character tokenizer: unlike a validating JVM XML parser, upstream accepts
     * duplicate attributes, undeclared prefixes and non-standard identifier characters. */
    private class Lexer(val input: String, val entities: Map<String, String>) {
        var pos = 0
        val done: Boolean get() = pos == input.length
        fun fail(message: String): Nothing = throw XmlParseException("$message at character $pos")
        fun starts(value: String) = input.startsWith(value, pos)
        fun take(value: String) { if (!starts(value)) fail("Expected $value"); pos += value.length }
        fun skipSpace() { while (!done && xmlSpace(input[pos])) pos++ }
        fun newline() { if (starts("\r\n")) pos += 2 else if (starts("\n")) pos++ }
        fun until(end: String): String {
            val stop = input.indexOf(end, pos)
            if (stop < 0) fail("Missing $end")
            return input.substring(pos, stop).also { pos = stop + end.length }
        }
        fun ident(): String {
            val start = pos
            while (!done && input[pos] !in "&<>:?=\"'/;#[]" && !xmlSpace(input[pos])) pos++
            if (start == pos) fail("Expected identifier")
            return input.substring(start, pos)
        }
        fun name(): Name {
            val first = ident()
            return if (starts(":")) { pos++; Name(first, ident()) } else Name(null, first)
        }
        fun quoted(): String {
            skipSpace()
            if (done || input[pos] !in "\"'") fail("Expected quoted text")
            val quote = input[pos++].toString()
            return until(quote)
        }
        fun attributes(): List<Pair<Name, List<Token>>> = buildList {
            while (true) {
                skipSpace()
                if (done || input[pos] in "/>?") break
                val name = name()
                skipSpace(); take("="); skipSpace()
                if (done || input[pos] !in "\"'") fail("Expected quoted attribute")
                val quote = input[pos++]
                val values = mutableListOf<Token>()
                while (!done && input[pos] != quote) values += content(quote)
                take(quote.toString())
                add(name to values)
            }
        }
        fun token(): Token {
            if (!starts("<")) return content()
            take("<")
            return when {
                starts("?") -> {
                    pos++
                    val target = ident()
                    if (target == "xml") {
                        val attrs = attributes(); skipSpace(); take("?>"); newline()
                        Token.Ignored("<?xml${renderAttributes(attrs)}?>")
                    } else {
                        skipSpace()
                        Token.Ignored("<?$target ${until("?>")}?>")
                    }
                }
                starts("!--") -> { take("!--"); Token.Ignored("<!--${until("-->")}-->") }
                starts("![CDATA[") -> { take("![CDATA["); Token.CData(until("]]>")) }
                starts("!DOCTYPE") -> doctype()
                starts("/") -> { pos++; skipSpace(); val name = name(); skipSpace(); take(">"); Token.End(name) }
                else -> {
                    skipSpace()
                    val name = name()
                    val attrs = attributes()
                    skipSpace()
                    val closed = starts("/")
                    if (closed) { pos++; skipSpace() }
                    take(">")
                    Token.Start(name, attrs, closed)
                }
            }
        }
        fun content(quote: Char? = null): Token {
            if (starts("&")) {
                pos++
                if (starts("#")) {
                    pos++
                    val hex = starts("x")
                    if (hex) pos++
                    val start = pos
                    while (!done && input[pos].digitToIntOrNull(if (hex) 16 else 10) != null && input[pos].code < 128) pos++
                    if (start == pos) fail("Invalid character reference")
                    // Attoparsec accumulates into Haskell Int; overflow wraps at 64 bits.
                    val n = BigInteger(input.substring(start, pos), if (hex) 16 else 10).toLong()
                    take(";")
                    if (!(n in 9..10 || n == 13L || n in 0x20..0xD7FF || n in 0xE000..0xFFFD || n in 0x10000..0x10FFFF))
                        fail("Invalid XML character reference")
                    return Token.Text(String(Character.toChars(n.toInt())))
                }
                val name = name().let { if (it.prefix == null) it.local else "${it.prefix}:${it.local}" }
                take(";")
                val value = when (name) {
                    "lt" -> "<"; "gt" -> ">"; "amp" -> "&"; "quot" -> "\""; "apos" -> "'"
                    else -> entities[name]
                }
                return if (value == null) Token.Entity(name) else Token.Text(value)
            }
            val start = pos
            while (!done && input[pos] != '<' && input[pos] != '&' && input[pos] != quote) pos++
            if (start == pos) fail("Expected text content")
            return Token.Text(input.substring(start, pos).replace("\r\n", "\n").replace('\r', '\n'))
        }
        fun doctype(): Token {
            take("!DOCTYPE"); skipSpace(); val name = name(); skipSpace()
            val external = if (starts("PUBLIC")) { take("PUBLIC"); " PUBLIC \"${quoted()}\" \"${quoted()}\"" }
            else if (starts("SYSTEM")) { take("SYSTEM"); " SYSTEM \"${quoted()}\"" } else ""
            skipSpace()
            val entities = mutableListOf<Pair<String, String>>()
            if (starts("[")) {
                pos++
                while (!done && !starts("]")) {
                    when {
                        starts("<!--") -> { take("<!--"); until("-->") }
                        starts("<!ENTITY") -> {
                            val saved = pos
                            try {
                                take("<!ENTITY"); skipSpace()
                                val parameter = starts("%")
                                if (parameter) { pos++; skipSpace() }
                                val name = ident()
                                val value = quoted()
                                skipSpace(); take(">")
                                if (!parameter) entities += name to value
                            } catch (_: XmlParseException) { pos = saved; declaration() }
                        }
                        starts("<") -> declaration()
                        else -> {
                            if (starts(">")) fail("Unexpected > in DTD")
                            pos++
                        }
                    }
                }
                take("]"); skipSpace()
            }
            take(">"); newline()
            return Token.Doctype(entities, "<!DOCTYPE ${name.render()}$external>")
        }
        fun declaration() {
            take("<")
            while (!done && !starts(">")) {
                when (input[pos]) {
                    '\'', '"' -> quoted()
                    '<', ']' -> fail("Invalid DTD declaration")
                    else -> pos++
                }
            }
            take(">")
        }
    }

    private fun xmlSpace(c: Char) = c == ' ' || c == '\t' || c == '\r' || c == '\n'
    private fun haskellSpace(c: Char) = c in '\t'..'\r' || c == ' ' || Character.getType(c) == Character.SPACE_SEPARATOR.toInt()

    private fun mergeText(contents: List<XmlContent>): List<XmlContent> = buildList {
        var text: StringBuilder? = null
        for (content in contents) {
            if (content is XmlText) {
                if (text == null) text = StringBuilder()
                text.append(content.text)
            } else {
                text?.let { add(XmlText(it.toString())) }
                text = null
                add(content)
            }
        }
        text?.let { add(XmlText(it.toString())) }
    }

    private fun escaped(value: String, attribute: Boolean = false): String {
        val basic = value.replace("&", "&amp;").replace("<", "&lt;")
        return if (attribute) basic.replace("\"", "&quot;") else basic.replace(">", "&gt;")
    }
    private fun Name.render() = if (prefix == null) local else "$prefix:$local"
    private fun renderAttributes(attrs: List<Pair<Name, List<Token>>>): String = attrs.joinToString("") { (name, values) ->
        " ${name.render()}=\"" + values.joinToString("") { when (it) {
            is Token.Text -> escaped(it.value, attribute = true)
            is Token.Entity -> "&${it.name};"
            else -> ""
        } } + "\""
    }

    // UTF-8 serialized token size is the upstream entity expansion limit unit.
    private fun tokenSize(token: Token): Int {
        val text = when (token) {
            is Token.Text -> escaped(token.value)
            is Token.CData -> "<![CDATA[${token.value}]]>"
            is Token.Entity -> "&${token.name};"
            is Token.End -> "</${token.name.render()}>"
            is Token.Start -> "<${token.name.render()}" + renderAttributes(token.attrs.distinctBy { it.first }) + if (token.closed) "/>" else ">"
            is Token.Ignored -> token.serialized
            is Token.Doctype -> token.serialized
        }
        return text.toByteArray(Charsets.UTF_8).size
    }
}
