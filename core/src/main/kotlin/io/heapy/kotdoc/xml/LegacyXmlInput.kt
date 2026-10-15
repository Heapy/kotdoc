// Text.XML.Light.Input, xml 1.3.14, (c) 2007 Galois Inc.; BSD-3-Clause.
package io.heapy.kotdoc.xml

import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars

/** Original permissive XML.Light forests, namespace scope, and first-root rule. */
object LegacyXmlInput {
    fun parseXML(source: CharacterString) = parse(LegacyXmlLexer.tokens(source))
    fun <S> parseXML(source: S, next: (S) -> Pair<Int, S>?) = parse(LegacyXmlLexer.tokens(source, next))
    fun parseXML(source: String) = parseXML(legacyChars(source))
    fun parseXML(source: ByteArray) = parse(LegacyXmlLexer.tokens(source))
    fun parseXMLDoc(source: CharacterString) = firstRoot(parseXML(source))
    fun <S> parseXMLDoc(source: S, next: (S) -> Pair<Int, S>?) = firstRoot(parseXML(source, next))
    fun parseXMLDoc(source: String) = parseXMLDoc(legacyChars(source))
    fun parseXMLDoc(source: ByteArray) = firstRoot(parseXML(source))
    private fun firstRoot(contents: List<LegacyXmlContent>): LegacyXmlElement? = contents.filterIsInstance<LegacyXmlElement>()
        .firstOrNull { !CharacterStringChars.build(it.name.local).startsWith("?xml") }
    private data class Namespace(val prefixes: List<Pair<CharacterString, CharacterString>> = emptyList(), val default: CharacterString? = null) {
        fun annotate(n: LegacyXmlName) = LegacyXmlName(n.local, if (n.prefix == null) default else prefixes.firstOrNull { it.first == n.prefix }?.second, n.prefix)
        fun add(a: LegacyXmlAttribute): Namespace = when {
            a.name.prefix == null && a.name.local == legacyChars("xmlns") -> copy(default = a.value.takeIf { it.points().isNotEmpty() })
            a.name.prefix == legacyChars("xmlns") -> copy(prefixes = listOf(a.name.local to a.value) + prefixes)
            else -> this
        }
    }
    private class Item(val content: LegacyXmlContent, val mergeFollowing: Boolean = false)
    private class Frame(val start: LegacyXmlToken.Start?, val name: LegacyXmlName?, val ns: Namespace, val attributes: List<LegacyXmlAttribute> = emptyList()) {
        val items = mutableListOf<Item>()
        fun contents(): List<LegacyXmlContent> {
            val out = ArrayDeque<LegacyXmlContent>()
            for (item in items.asReversed()) {
                val text = item.content as? LegacyXmlText; val next = out.firstOrNull() as? LegacyXmlText
                if (item.mergeFollowing && text != null && next?.kind == text.kind) {
                    out.removeFirst(); out.addFirst(LegacyXmlText(CharacterStringChars.append(text.text, next.text), text.kind, text.line))
                } else out.addFirst(item.content)
            }
            return legacyOwned(out.toList())
        }
    }
    fun parse(tokens: List<LegacyXmlToken>): List<LegacyXmlContent> {
        val frames = ArrayDeque<Frame>(); frames.addLast(Frame(null, null, Namespace()))
        fun close() {
            val frame = frames.removeLast(); val start = checkNotNull(frame.start)
            frames.last().items.add(Item(LegacyXmlElement(checkNotNull(frame.name), frame.attributes, frame.contents(), start.line)))
        }
        for (token in tokens) when (token) {
            is LegacyXmlToken.Start -> {
                var ns = frames.last().ns
                for (a in token.attributes.asReversed()) ns = ns.add(a)
                val name = ns.annotate(token.name)
                val attrs = token.attributes.map { if (it.name.prefix == null) it else LegacyXmlAttribute(ns.annotate(it.name), it.value) }
                frames.addLast(Frame(token, name, ns, attrs)); if (token.empty) close()
            }
            is LegacyXmlToken.End -> {
                val name = frames.last().ns.annotate(token.name)
                val index = frames.indexOfLast { it.name == name }
                if (index > 0) while (frames.size > index) close()
                else frames.last().items.add(Item(LegacyXmlText(LegacyXmlOutput.tagEnd(token.name, CharacterStringChars.empty), line = token.line)))
            }
            is LegacyXmlToken.Text -> frames.last().items.add(Item(token.data, true))
            is LegacyXmlToken.Ref -> frames.last().items.add(Item(LegacyXmlCRef(token.reference)))
        }
        while (frames.size > 1) close()
        return frames.single().contents()
    }
}
