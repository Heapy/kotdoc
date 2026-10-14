// Text.Pandoc.Writers.Shared HTML helpers, Copyright (C) 2013-2024
// John MacFarlane. GPL-2.0-or-later; licenses/pandoc-GPL-2.0.md.
package io.heapy.kotdoc.writers

import io.heapy.kotdoc.ast.Alignment
import io.heapy.kotdoc.ast.Attr
import io.heapy.kotdoc.html.PandocCss
import io.heapy.kotdoc.layout.*
import io.heapy.kotdoc.xml.PandocXml

/** Shared writer utilities; the stateful Pandoc HTML writer is a separate port. */
object WriterHtml {
    fun <A> tagWithAttrs(layout: DocLayout<A>, tag: A, attr: Attr): Doc<A> =
        layout.text("<") + (layout.literal(tag) + (htmlAttrs(layout, attr) + layout.text(">")))

    fun <A> htmlAttrs(layout: DocLayout<A>, attr: Attr): Doc<A> {
        fun escaped(value: String) = layout.doubleQuotes(layout.text(PandocXml.escapeStringForXML(value)))
        fun key(input: String): String {
            val value = TextChars.fromString(input)
            return if (
                ((value in PandocXml.html5Attributes || value in PandocXml.rdfaAttributes) && value != "label") ||
                ':' in value || value.startsWith("data-") || value.startsWith("aria-") || value == "markdown"
            ) value else "data-$value"
        }
        val fields = layout.hsep(listOf(
            if (attr.identifier.isEmpty()) layout.empty else layout.text("id=") + escaped(attr.identifier),
            if (attr.classes.isEmpty()) layout.empty else layout.text("class=") + escaped(attr.classes.joinToString(" ")),
            layout.hsep(attr.keyValues.map { (k, v) -> layout.text(key(k)) + (layout.text("=") + escaped(v)) }),
        ))
        return if (layout.isEmpty(fields)) fields else layout.text(" ") + fields
    }

    fun htmlAddStyle(property: Pair<String, String>, attrs: List<Pair<String, String>>): List<Pair<String, String>> {
        fun style(values: List<Pair<String, String>>) = values.joinToString(" ") { (key, value) -> "$key: $value;" }
        val index = attrs.indexOfFirst { it.first == "style" }
        if (index < 0) return listOf("style" to style(listOf(property))) + attrs
        val values = PandocCss.cssAttributes(attrs[index].second)
        val at = values.indexOfFirst { it.first == property.first }
        val modified = if (at < 0) listOf(property) + values else values.toMutableList().also { it[at] = property }
        return attrs.toMutableList().also { it[index] = "style" to style(modified) }
    }

    fun htmlAlignmentToString(alignment: Alignment): String? = when (alignment) {
        Alignment.AlignLeft -> "left"; Alignment.AlignRight -> "right"; Alignment.AlignCenter -> "center"
        Alignment.AlignDefault -> null
    }
}
