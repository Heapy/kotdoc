// Port of Text.Pandoc.Asciify.
// Copyright (C) 2013-2024 John MacFarlane. GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.ast

import com.ibm.icu.lang.UCharacter
import com.ibm.icu.lang.UCharacterCategory
import com.ibm.icu.text.Normalizer2

object Asciify {
    private val nfd = Normalizer2.getNFDInstance()

    fun toAsciiText(text: String): String = buildString {
        nfd.normalize(text).codePoints().forEach { codePoint ->
            when {
                codePoint < 128 -> append(codePoint.toChar())
                codePoint == 0x131 -> append('i')
            }
        }
    }

    /** Takes a Unicode code point, so supplementary characters are not split. */
    fun toAsciiChar(codePoint: Int): Char? {
        require(Character.isValidCodePoint(codePoint)) { "Invalid Unicode code point: $codePoint" }
        val decomposed = nfd.normalize(String(Character.toChars(codePoint))).codePoints().toArray()
        return when {
            decomposed.size == 1 && decomposed[0] == 0x131 -> 'i'
            decomposed.isNotEmpty() && decomposed[0] < 128 && decomposed.drop(1).all {
                UCharacter.getType(it).toByte() in setOf(UCharacterCategory.NON_SPACING_MARK,
                    UCharacterCategory.COMBINING_SPACING_MARK, UCharacterCategory.ENCLOSING_MARK)
            } -> decomposed[0].toChar()
            else -> null
        }
    }
}
