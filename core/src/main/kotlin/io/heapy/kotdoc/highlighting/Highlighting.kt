// Text.Pandoc.Highlighting / Shared.safeRead, Copyright (C) 2008-2025 John MacFarlane.
// GPL-2.0-or-later; licenses/pandoc-GPL-2.0.md.
package io.heapy.kotdoc.highlighting

import io.heapy.kotdoc.ast.Attr
import io.heapy.kotdoc.ast.UnicodeText
import io.heapy.kotdoc.formats.HaskellNumber
import io.heapy.kotdoc.highlighting.format.safeFormatText
import java.nio.file.Files
import java.nio.file.Path

sealed interface Highlighted<out A> {
    data class Success<A>(val value: A) : Highlighted<A>
    data class Failure(val error: String) : Highlighted<Nothing>
}

/** The shared Pandoc layer used by code-block and inline-code writers. */
object Highlighting {
    val defaultStyle get() = HighlightStyles.defaultStyle
    val highlightingStyles: List<Pair<String,HighlightStyle>> = syntaxList(HighlightStyles.all.entries.map { it.key to it.value })
    private val toListings = syntaxMap(listingsLanguages.toMap())
    private val fromListings = syntaxMap(listingsLanguages.map { (a,b) -> b to a }.toMap())
    fun toListingsLanguage(language: String): String? = toListings[UnicodeText.toLower(language)]
    fun fromListingsLanguage(language: String): String? = fromListings[language]
    fun languages(syntaxes: SyntaxMap): List<String> = syntaxList(syntaxMap(syntaxes).values.map { UnicodeText.toLower(it.name) })
    fun languagesByExtension(syntaxes: SyntaxMap, extension: String): List<String> = syntaxList(Syntaxes.syntaxesByExtension(syntaxes,extension).map { UnicodeText.toLower(it.name) })
    fun lookupHighlightingStyle(name: String, readTheme: (String) -> String = { Files.readString(Path.of(it)) }) = HighlightStyles.lookup(name,readTheme)
    internal fun safeInt(text: String): Long? = HaskellNumber.readInteger(text)?.toLong()

    fun <A> highlight(syntaxes: SyntaxMap, formatter: (FormatOptions,List<SourceLine>) -> A, attributes: Attr, rawCode: String): Highlighted<A> {
        val classes = attributes.classes.toList()
        val numbering = classes.any { it in listOf("number","numberLines","number-lines") }
        val anchors = classes.any { it in listOf("line-anchors","lineAnchors") }
        val syntax = classes.firstNotNullOfOrNull { Syntaxes.lookupSyntax(it,syntaxes) }
        if (syntax==null && !numbering) return Highlighted.Failure("")
        val first = safeInt(attributes.keyValues.firstOrNull { it.first=="startFrom" }?.second ?: "1") ?: 1
        val options = FormatOptions(numberLines=numbering,startNumber=first,lineAnchors=anchors,
            codeClasses=syntax?.let { listOf(UnicodeText.toLower(it.shortname)) } ?: emptyList(),
            containerClasses=classes,lineIdPrefix=if (attributes.identifier.isEmpty()) "" else attributes.identifier+"-")
        if (syntax==null) {
            val text = safeFormatText(rawCode)
            val lines = if (text.isEmpty()) emptyList() else text.split('\n').let { if (text.endsWith('\n')) it.dropLast(1) else it }
            val tokens = syntaxList(lines.map { syntaxList(listOf(HighlightToken(TokenType.NormalTok,it))) })
            return Highlighted.Success(formatter(options,tokens))
        }
        return when (val result=Tokenizer.tokenize(TokenizerConfig(syntaxes),syntax,rawCode)) {
            is Tokenization.Failure -> Highlighted.Failure(result.error)
            is Tokenization.Success -> Highlighted.Success(formatter(options,result.lines))
        }
    }
}
