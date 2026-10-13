// Port of Text.Pandoc.Shared identifier helpers.
// Copyright (C) 2006-2024 John MacFarlane. GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.ast

import com.ibm.icu.lang.UCharacter
import com.ibm.icu.lang.UCharacterCategory
import io.heapy.kotdoc.options.ExtensionName
import io.heapy.kotdoc.options.Extensions

object Identifiers {
    private val gfmPunctuation = setOf(UCharacterCategory.NON_SPACING_MARK, UCharacterCategory.COMBINING_SPACING_MARK,
        UCharacterCategory.ENCLOSING_MARK, UCharacterCategory.CONNECTOR_PUNCTUATION)
    private val numbers = setOf(UCharacterCategory.DECIMAL_DIGIT_NUMBER, UCharacterCategory.LETTER_NUMBER, UCharacterCategory.OTHER_NUMBER)
    fun inlineListToIdentifier(inlines: List<Inline>, extensions: Extensions = Extensions.Empty): String {
        val converted = if (ExtensionName.GfmAutoIdentifiers in extensions || ExtensionName.AsciiIdentifiers in extensions)
            object : PandocVisitor() {
                override fun transformInline(value: Inline): Inline = when {
                    value is Inline.Span && value.attr.identifier.isEmpty() && value.attr.classes == listOf("emoji") &&
                        value.attr.keyValues.size == 1 && value.attr.keyValues[0].first == "data-emoji" -> Inline.Str(value.attr.keyValues[0].second)
                    value is Inline.Str -> Inline.Str(Emoji.replaceEmojis(value.text) { original, aliases -> aliases.firstOrNull() ?: original })
                    else -> value
                }
            }.walkInlines(inlines)
        else inlines
        return textToIdentifier(DocumentShared.stringifyInlines(converted), extensions)
    }

    fun textToIdentifier(text: String, extensions: Extensions = Extensions.Empty): String {
        val gfm = ExtensionName.GfmAutoIdentifiers in extensions
        val lowered = UnicodeText.toLower(text)
        val filtered = buildString {
            lowered.codePoints().forEach { original ->
                val cp = if (gfm && isSpace(original)) '-'.code else original
                val type = UCharacter.getType(cp).toByte()
                val allowed = cp == '-'.code || cp == '_'.code || if (gfm)
                    type in gfmPunctuation
                else cp == '.'.code
                if (isSpace(cp) || UCharacter.isLetter(cp) || type in numbers || allowed) appendCodePoint(cp)
            }
        }
        val separated = if (gfm) filtered else buildString {
            var pendingSpace = false
            filtered.codePoints().forEach { cp ->
                if (isSpace(cp)) pendingSpace = isNotEmpty()
                else {
                    if (pendingSpace) append('-')
                    appendCodePoint(cp)
                    pendingSpace = false
                }
            }
        }
        val ascii = if (ExtensionName.AsciiIdentifiers in extensions) Asciify.toAsciiText(separated) else separated
        if (gfm) return ascii
        var start = 0
        while (start < ascii.length && !UCharacter.isLetter(ascii.codePointAt(start))) start += Character.charCount(ascii.codePointAt(start))
        return ascii.substring(start)
    }

    fun uniqueIdent(inlines: List<Inline>, used: Set<String> = emptySet(), extensions: Extensions = Extensions.Empty): String {
        val base = inlineListToIdentifier(inlines, extensions).ifEmpty { "section" }
        if (base !in used) return base
        for (i in 1..60000) {
            val candidate = "$base-$i"
            if (candidate !in used) return candidate
        }
        return base
    }

    private fun isSpace(cp: Int): Boolean = cp in 9..13 || UCharacter.getType(cp).toByte() == UCharacterCategory.SPACE_SEPARATOR
}
