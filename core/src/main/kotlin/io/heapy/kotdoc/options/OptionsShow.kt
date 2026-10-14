// Derived Show of Text.Pandoc.Options/Extensions 3.12.1.
// Copyright (C) 2012-2024 John MacFarlane, GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.options

import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.highlighting.syntaxShow
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.templates.TemplateShow

private fun text(value: String) = HaskellString.quote(TextChars.fromString(value))
private fun wrap(value: String, precedence: Int) = if (precedence > 10) "($value)" else value
private fun number(value: Long, precedence: Int = 0) = if (value < 0 && precedence > 6) "($value)" else value.toString()
private fun maybeText(value: String?) = value?.let { "Just ${text(it)}" } ?: "Nothing"
private fun maybePath(value: CharacterString?) = value?.let { "Just ${HaskellString.quoteCodePoints(it.points())}" } ?: "Nothing"
private fun bool(value: Boolean) = if (value) "True" else "False"

fun MathMethod.haskellShow(precedence: Int = 0): String = when (this) {
    MathMethod.PlainMath -> "PlainMath"
    is MathMethod.WebTeX -> wrap("WebTeX ${text(url)}", precedence)
    MathMethod.GladTeX -> "GladTeX"
    MathMethod.MathML -> "MathML"
    is MathMethod.MathJax -> wrap("MathJax ${text(url)}", precedence)
    is MathMethod.KaTeX -> wrap("KaTeX ${text(url)}", precedence)
}
fun HighlightMethod.haskellShow(precedence: Int = 0): String = when (this) {
    is HighlightMethod.Skylighting -> wrap("Skylighting ($style)", precedence)
    HighlightMethod.IdiomaticHighlighting -> "IdiomaticHighlighting"
    HighlightMethod.DefaultHighlighting -> "DefaultHighlighting"
    HighlightMethod.NoHighlighting -> "NoHighlighting"
}
fun Extension.haskellShow(precedence: Int = 0): String = when (this) {
    is Extension.Builtin -> "Ext_$wireName"
    is Extension.Custom -> wrap("CustomExtension ${text(wireName)}", precedence)
}
fun Extensions.haskellShow(precedence: Int = 0): String = wrap(
    toList().joinToString(",", "Extensions (fromList [", "])") { it.haskellShow() }, precedence)

internal fun writerOptionsShow(value: WriterOptions, precedence: Int): String = with(value) {
    val fields = listOf(
        "writerTemplate" to (template?.let { "Just ${TemplateShow.Text.template(it, 11)}" } ?: "Nothing"),
        "writerVariables" to TemplateShow.Text.context(variables),
        "writerTabStop" to number(tabStop),
        "writerTableOfContents" to bool(tableOfContents),
        "writerListOfFigures" to bool(listOfFigures),
        "writerListOfTables" to bool(listOfTables),
        "writerIncremental" to bool(incremental),
        "writerMathMethod" to mathMethod.haskellShow(),
        "writerNumberSections" to bool(numberSections),
        "writerNumberOffset" to numberOffset.joinToString(",", "[", "]"),
        "writerSectionDivs" to bool(sectionDivs),
        "writerExtensions" to extensions.haskellShow(),
        "writerReferenceLinks" to bool(referenceLinks),
        "writerDpi" to number(dpi),
        "writerWrapText" to wrapText.name,
        "writerColumns" to number(columns),
        "writerEmailObfuscation" to emailObfuscation.name,
        "writerIdentifierPrefix" to text(identifierPrefix),
        "writerCiteMethod" to citeMethod.name,
        "writerHtmlQTags" to bool(htmlQTags),
        "writerSlideLevel" to (slideLevel?.let { "Just ${number(it, 11)}" } ?: "Nothing"),
        "writerTopLevelDivision" to topLevelDivision.name,
        "writerHighlightMethod" to highlightMethod.haskellShow(),
        "writerSetextHeaders" to bool(setextHeaders),
        "writerListTables" to bool(listTables),
        "writerEpubSubdirectory" to text(epubSubdirectory),
        "writerEpubMetadata" to maybeText(epubMetadata),
        "writerEpubFonts" to epubFonts.joinToString(",", "[", "]") { HaskellString.quoteCodePoints(it.points()) },
        "writerEpubTitlePage" to bool(epubTitlePage),
        "writerSplitLevel" to number(splitLevel),
        "writerChunkTemplate" to "PathTemplate {unPathTemplate = ${text(chunkTemplate.value)}}",
        "writerTOCDepth" to number(tocDepth),
        "writerReferenceDoc" to maybePath(referenceDoc),
        "writerReferenceLocation" to referenceLocation.name,
        "writerFigureCaptionPosition" to figureCaptionPosition.name,
        "writerTableCaptionPosition" to tableCaptionPosition.name,
        "writerSyntaxMap" to syntaxShow(syntaxMap),
        "writerPreferAscii" to bool(preferAscii),
        "writerLinkImages" to bool(linkImages),
    )
    wrap(fields.joinToString(", ", "WriterOptions {", "}") { (key, shown) -> "$key = $shown" }, precedence)
}
