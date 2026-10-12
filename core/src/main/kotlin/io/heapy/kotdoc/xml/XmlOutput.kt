// Port of Text.Pandoc.XML.Light.Output.
// Copyright (C) 2007 Galois, Inc.; 2021-2024 John MacFarlane.
// GPL-2.0-or-later; see licenses/ and upstream/pandoc/xml-light/.
package io.heapy.kotdoc.xml

data class XmlPrintConfig(
    val shortEmptyTag: (XmlName) -> Boolean = { true },
    val inlineTag: (XmlName) -> Boolean = { false },
    val prettify: Boolean = false,
)

object XmlOutput {
    const val header = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
    val defaultConfig = XmlPrintConfig()
    val prettyConfig = XmlPrintConfig(prettify = true)

    fun showContent(content: XmlContent): String = printContent(content, defaultConfig)
    fun showElement(element: XmlElement): String = showContent(element)
    fun showTopElement(element: XmlElement): String = header + showElement(element)
    fun prettyContent(content: XmlContent): String = printContent(content, prettyConfig)
    fun prettyElement(element: XmlElement): String = prettyContent(element)
    fun prettyTopElement(element: XmlElement): String = printTopElement(element, prettyConfig)
    fun printTopElement(element: XmlElement, config: XmlPrintConfig): String = "$header\n${printContent(element, config)}\n"
    fun printContent(content: XmlContent, config: XmlPrintConfig): String = buildString { content(content, config, "") }

    private fun StringBuilder.content(value: XmlContent, config: XmlPrintConfig, indent: String) {
        when (value) {
            is XmlElement -> { append(indent); element(value, config, indent) }
            is XmlText -> { append(indent); text(value, config, indent) }
            is XmlCRef -> append('&').append(value.reference).append(';')
        }
    }

    private fun StringBuilder.element(value: XmlElement, config: XmlPrintConfig, indent: String) {
        append('<').append(value.name)
        for (attr in value.attributes) {
            append(' ').append(attr.name).append("=\"")
            escape(attr.value)
            append('"')
        }
        val children = value.content
        when {
            children.isEmpty() && value.localName.startsWith('?') -> append(" ?>")
            children.isEmpty() && config.shortEmptyTag(value.name) -> append(" />")
            children.size == 1 && children[0] is XmlText -> {
                append('>')
                text(children[0] as XmlText, config, if (config.inlineTag(value.name)) indent else "")
                end(value.name)
            }
            config.inlineTag(value.name) -> {
                append('>')
                for (child in children) when (child) {
                    is XmlElement -> element(child, config, indent)
                    is XmlText -> text(child, config, indent)
                    is XmlCRef -> append('&').append(child.reference).append(';')
                }
                end(value.name)
            }
            else -> {
                append('>')
                val newline = if (config.prettify) "\n" else ""
                val space = if (config.prettify) "  " else ""
                append(newline)
                for (child in children) { content(child, config, space + indent); append(newline) }
                append(indent)
                end(value.name)
            }
        }
    }

    private fun StringBuilder.end(name: XmlName) { append("</").append(name).append('>') }
    private fun StringBuilder.text(value: XmlText, config: XmlPrintConfig, indent: String) {
        when (value.kind) {
            XmlTextKind.Raw -> append(value.text)
            XmlTextKind.Verbatim -> append("<![CDATA[").append(value.text.replace("]]>", "]]]]><![CDATA[>")).append("]]>")
            XmlTextKind.Text -> {
                if (config.prettify) value.text.split('\n').forEachIndexed { index, line ->
                    if (index != 0) append('\n').append(indent)
                    escape(line)
                } else escape(value.text)
            }
        }
    }

    private fun StringBuilder.escape(value: String) {
        for (char in value) append(when (char) {
            '<' -> "&lt;"; '>' -> "&gt;"; '&' -> "&amp;"; '"' -> "&quot;"; '\'' -> "&#39;"
            else -> char.toString()
        })
    }
}
