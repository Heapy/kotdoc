// Text.Pandoc.Readers.HTML.TagCategories; © 2006-2024 John MacFarlane.
// GPL-2.0-or-later; unchanged original source in upstream/pandoc.
package io.heapy.kotdoc.html

import java.util.Collections

object HtmlTagCategories {
    val eitherBlockOrInline: Set<String> = Collections.unmodifiableSet(linkedSetOf(
        "audio", "applet", "button", "iframe", "embed", "del", "ins", "progress",
        "map", "area", "noscript", "script", "object", "svg", "video", "source",
        "track",
    ))
    val blockHtmlTags: Set<String> = Collections.unmodifiableSet(linkedSetOf(
        "?xml", "!DOCTYPE", "address", "article", "aside", "blockquote", "body", "canvas",
        "caption", "center", "col", "colgroup", "dd", "details", "dir", "div",
        "dl", "dt", "fieldset", "figcaption", "figure", "footer", "form", "h1",
        "h2", "h3", "h4", "h5", "h6", "head", "header", "hgroup",
        "hr", "html", "isindex", "main", "menu", "meta", "noframes", "nav",
        "ol", "output", "p", "pre", "section", "summary", "table", "tbody",
        "textarea", "thead", "tfoot", "ul", "dd", "dt", "frameset", "li",
        "tbody", "td", "tfoot", "th", "thead", "tr", "script", "style",
    ))
    val blockDocBookTags: Set<String> = Collections.unmodifiableSet(linkedSetOf(
        "calloutlist", "bibliolist", "glosslist", "itemizedlist", "orderedlist", "segmentedlist", "simplelist", "variablelist",
        "caution", "important", "note", "tip", "warning", "address", "literallayout", "programlisting",
        "programlistingco", "screen", "screenco", "screenshot", "synopsis", "example", "informalexample", "figure",
        "informalfigure", "table", "informaltable", "para", "simpara", "formalpara", "equation", "informalequation",
        "figure", "screenshot", "mediaobject", "qandaset", "procedure", "task", "cmdsynopsis", "funcsynopsis",
        "classsynopsis", "blockquote", "epigraph", "msgset", "sidebar", "title",
    ))
    val epubTags: Set<String> = Collections.unmodifiableSet(linkedSetOf(
        "case", "switch", "default",
    ))
    val voidTags: Set<String> = Collections.unmodifiableSet(linkedSetOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr",
    ))
    val sectioningContent: List<String> = Collections.unmodifiableList(listOf(
        "article", "aside", "nav", "section",
    ))
    val groupingContent: List<String> = Collections.unmodifiableList(listOf(
        "p", "hr", "pre", "blockquote", "ol", "ul", "li", "dl",
        "dt", "dt", "dd", "figure", "figcaption", "div", "main",
    ))
    val blockTags: Set<String> = Collections.unmodifiableSet(blockHtmlTags + blockDocBookTags + epubTags)
}
