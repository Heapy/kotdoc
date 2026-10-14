// Text.XML.Stream.Parse/Token 1.10.1.0 and attoparsec 0.14.4.
// Copyright (c) Michael Snoyman and contributors; Bryan O'Sullivan.
// MIT/BSD-3-Clause; licenses/xml-conduit-MIT.txt and Attoparsec-BSD-3-Clause.txt.
package io.heapy.kotdoc.xml

internal data class XmlTokenName(val prefix: String?, val local: String) {
    override fun toString() = if (prefix == null) local else "$prefix:$local"
}
internal sealed interface XmlPiece {
    data class Text(val text: String) : XmlPiece
    data class Entity(val name: String) : XmlPiece
}
internal sealed interface XmlDocToken {
    data class Begin(val name: XmlTokenName, val attributes: List<Pair<XmlTokenName, List<XmlPiece>>>, val closed: Boolean) : XmlDocToken
    data class End(val name: XmlTokenName) : XmlDocToken
    data class Content(val content: XmlPiece) : XmlDocToken
    data class CData(val text: String) : XmlDocToken
    data class Comment(val text: String) : XmlDocToken
    data class Instruction(val instruction: XmlInstruction) : XmlDocToken
    data class Declaration(val attributes: List<Pair<XmlTokenName, List<XmlPiece>>>) : XmlDocToken
    data class Doctype(val doctype: XmlDoctype, val entities: List<Pair<String, String>>) : XmlDocToken
}
internal class XmlLexFailure(val at: Int, val contexts: List<String>, val reason: String) : RuntimeException(null, null, false, false)

/** Attoparsec alternatives restore the input even after consuming a prefix. */
internal class XmlDocumentLexer(val input: String, private val inputEnd: () -> Unit = {},
    private val chunkEnds: IntArray = intArrayOf(input.length)) {
    var position = 0
    private var lastFedStart = 0
    private fun touch(index: Int) {
        if (index == input.length) { inputEnd(); return }
        var chunk = chunkEnds.binarySearch(index)
        chunk = if (chunk >= 0) chunk + 1 else -chunk - 1
        if (chunk > 0) lastFedStart = maxOf(lastFedStart, chunkEnds[chunk - 1])
    }
    val done get(): Boolean { touch(position); return position == input.length }
    private fun fail(reason: String): Nothing = throw XmlLexFailure(maxOf(position, lastFedStart), emptyList(), reason)
    private fun peek(): Char? = if (done) null else input[position]
    private fun rawChar(): Int {
        if (done) fail("not enough input")
        return input.codePointAt(position).also { position += Character.charCount(it) }
    }
    private inline fun <A> label(name: String, body: () -> A): A = try { body() }
        catch (failure: XmlLexFailure) { throw XmlLexFailure(failure.at, listOf(name) + failure.contexts, failure.reason) }
    private inline fun <A> optional(body: () -> A): A? {
        val start = position
        return try { body() } catch (_: XmlLexFailure) { position = start; null }
    }
    private inline fun <A> alternative(first: () -> A, second: () -> A): A {
        val start = position
        return try { first() } catch (_: XmlLexFailure) { position = start; second() }
    }
    private fun char(char: Char) = label("'$char'") {
        if (done) fail("not enough input")
        if (peek() != char) fail("Failed reading: satisfy")
        position++
    }
    private fun string(value: String) {
        for (i in value.indices) {
            val at = position + i
            if (at >= input.length) { inputEnd(); fail("not enough input") }
            touch(at)
            if (input[at] != value[i]) fail("string")
        }
        position += value.length
    }
    private fun takeWhile(predicate: (Char) -> Boolean): String {
        val start = position
        while (!done && predicate(input[position])) position++
        return input.substring(start, position)
    }
    private fun takeWhile1(predicate: (Char) -> Boolean): String {
        if (done) fail("not enough input")
        return takeWhile(predicate).also { if (it.isEmpty()) fail("Failed reading: takeWhile1") }
    }
    private fun space() { takeWhile { it in " \t\r\n" } }
    private fun newline() { optional { alternative({ char('\r'); char('\n') }, { char('\n') }) } }
    private fun ident(): String = label("identifier") { takeWhile1 { it !in "&<>:?=\"'/;#[] \t\r\n" } }
    private fun name(): XmlTokenName = label("name") {
        val first = ident(); val second = optional { char(':'); ident() }
        if (second == null) XmlTokenName(null, first) else XmlTokenName(first, second)
    }
    private fun until(end: String): String {
        val start = position
        while (optional { string(end) } == null) rawChar()
        return input.substring(start, position - end.length)
    }
    private fun quoted(): String = label("quoted text") {
        space()
        alternative({ char('"'); val text = takeWhile { it != '"' }; char('"'); text },
            { char('\''); val text = takeWhile { it != '\'' }; char('\''); text })
    }
    private fun attribute(): Pair<XmlTokenName, List<XmlPiece>> = label("attribute") {
        space(); val name = name(); space(); char('='); space()
        val value = alternative({ quotedContent('\'') }, { quotedContent('"') })
        name to value
    }
    private fun quotedContent(quote: Char): List<XmlPiece> {
        char(quote)
        return buildList { while (optional { char(quote) } == null) add(content(quote)) }
    }
    private fun attributes(): List<Pair<XmlTokenName, List<XmlPiece>>> = buildList {
        while (true) add(optional { attribute() } ?: break)
    }
    private fun validXmlChar(number: Long) = number in 9L..10L || number == 13L || number in 32L..0xd7ffL ||
        number in 0xe000L..0xfffdL || number in 0x10000L..0x10ffffL
    private fun number(radix: Int): Long {
        val digits = takeWhile1 { if (radix == 10) it in '0'..'9' else it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        var result = 0L
        for (digit in digits) result = result * radix + digit.digitToInt(radix)
        return result
    }
    private fun reference(): XmlPiece {
        char('&')
        val result = alternative({
            val name = name().toString()
            when (name) { "lt" -> XmlPiece.Text("<"); "gt" -> XmlPiece.Text(">"); "amp" -> XmlPiece.Text("&")
                "quot" -> XmlPiece.Text("\""); "apos" -> XmlPiece.Text("'"); else -> XmlPiece.Entity(name) }
        }, { alternative({
            string("#x"); val number = number(16)
            if (!validXmlChar(number)) fail("Failed reading: Invalid character from hexadecimal character reference.")
            XmlPiece.Text(buildString { appendCodePoint(number.toInt()) })
        }, {
            string("#"); val number = number(10)
            if (!validXmlChar(number)) fail("Failed reading: Invalid character from decimal character reference.")
            XmlPiece.Text(buildString { appendCodePoint(number.toInt()) })
        }) })
        char(';'); return result
    }
    private fun content(quote: Char? = null): XmlPiece = alternative({ reference() }, { label("text content") {
        val start = position
        while (!done && peek() != '&' && peek() != '<' && (quote == null || peek() != quote)) position++
        val text = input.substring(start, position).replace("\r\n", "\n").replace('\r', '\n')
        if (text.isEmpty()) fail("Failed reading: parseTextContent")
        XmlPiece.Text(text)
    } })
    fun token(): XmlDocToken {
        lastFedStart = position
        if (peek() != '<') return XmlDocToken.Content(content())
        char('<')
        return when (peek()) {
            '?' -> { char('?'); label("instruction") {
                val target = ident()
                if (target == "xml") {
                    val attrs = attributes(); space(); char('?'); char('>'); newline(); XmlDocToken.Declaration(attrs)
                } else { space(); XmlDocToken.Instruction(XmlInstruction(target, until("?>"))) }
            } }
            '!' -> { char('!'); alternative({ label("comment") { char('-'); char('-'); XmlDocToken.Comment(until("-->")) } }, {
                alternative({ label("CDATA") { string("[CDATA["); XmlDocToken.CData(until("]]>")) } }, { doctype() })
            }) }
            '/' -> { char('/'); label("close tag") { space(); val name = name(); space(); char('>'); XmlDocToken.End(name) } }
            else -> label("open tag") {
                space(); val name = name(); val attrs = attributes(); space()
                val closed = optional { char('/'); space(); true } ?: false
                char('>'); XmlDocToken.Begin(name, attrs, closed)
            }
        }
    }
    private fun entity(): Pair<String, String>? = label("entity") {
        string("<!ENTITY"); space(); val parameter = optional { char('%'); space(); true } ?: false
        val ident = ident(); val value = quoted(); space(); char('>')
        if (parameter) null else ident to value
    }
    private fun declarations(): List<Pair<String, String>> {
        val beginning = position; val entities = mutableListOf<Pair<String, String>>()
        try {
            while (true) {
                if (optional { char(']') } != null) return entities
                var matched = false
                optional { val entity = entity(); if (entity != null) entities.add(entity); matched = true }
                if (matched) continue
                if (optional { string("<!--"); until("-->"); true } == true) continue
                if (optional {
                    char('<')
                    while (optional { alternative({ takeWhile1 { it !in "]<>'\"" }; Unit }, { quoted(); Unit }) } != null) { }
                    char('>'); true
                } == true) continue
                // skipMany1 (satisfy (notInClass "]<>"))
                if (done) fail("not enough input")
                if (peek() in listOf(']', '<', '>')) fail("Failed reading: satisfy")
                takeWhile { it !in "]<>" }
            }
        } catch (_: XmlLexFailure) {
            // A failed recursive suffix backtracks through every prior directive;
            // the final skipMany1 alternative determines the observable failure.
            position = beginning; takeWhile { it !in "]<>" }
            fail(if (done) "not enough input" else "Failed reading: satisfy")
        }
    }
    private fun doctype(): XmlDocToken = label("DOCTYPE") {
        string("DOCTYPE"); space(); val name = name().toString(); space()
        val id = alternative({ string("PUBLIC"); XmlExternalId.PublicId(quoted(), quoted()) }, {
            optional { string("SYSTEM"); XmlExternalId.SystemId(quoted()) }
        })
        space()
        val entities = if (peek() == '[') { char('['); declarations().also { space() } } else emptyList()
        char('>'); newline(); XmlDocToken.Doctype(XmlDoctype(name, id), entities)
    }
}

internal fun XmlDocToken.serialized(): String {
    fun escaped(text: String, attribute: Boolean = false) = text.replace("&", "&amp;").replace("<", "&lt;")
        .let { if (attribute) it.replace("\"", "&quot;") else it.replace(">", "&gt;") }
    fun piece(value: XmlPiece, attribute: Boolean) = when (value) {
        is XmlPiece.Text -> escaped(value.text, attribute); is XmlPiece.Entity -> "&${value.name};"
    }
    fun attrs(values: List<Pair<XmlTokenName, List<XmlPiece>>>) = values.joinToString("") { (name, pieces) ->
        " $name=\"" + pieces.joinToString("") { piece(it, true) } + "\""
    }
    return when (this) {
        is XmlDocToken.Begin -> "<$name" + attrs(attributes.distinctBy { it.first }) + if (closed) "/>" else ">"
        is XmlDocToken.End -> "</$name>"
        is XmlDocToken.Content -> piece(content, false)
        is XmlDocToken.CData -> "<![CDATA[" + text.replace("]]>", "]]]]><![CDATA[>") + "]]>"
        is XmlDocToken.Comment -> "<!--$text-->"
        is XmlDocToken.Instruction -> "<?${instruction.target} ${instruction.data}?>"
        is XmlDocToken.Declaration -> "<?xml" + attrs(attributes) + "?>"
        is XmlDocToken.Doctype -> "<!DOCTYPE ${doctype.name}" + when (val id = doctype.externalId) {
            null -> ""; is XmlExternalId.SystemId -> " SYSTEM \"${id.system}\""
            is XmlExternalId.PublicId -> " PUBLIC \"${id.public}\" \"${id.system}\""
        } + ">"
    }
}
