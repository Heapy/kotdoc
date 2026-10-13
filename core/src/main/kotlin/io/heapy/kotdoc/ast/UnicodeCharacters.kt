package io.heapy.kotdoc.ast

import com.ibm.icu.lang.UCharacter
import com.ibm.icu.lang.UCharacterCategory

/** Data.Char categories: letters and all three Unicode number categories. */
internal object UnicodeCharacters {
    fun isAlpha(point: Int): Boolean = UCharacter.isLetter(point)

    fun isAlphaNum(point: Int): Boolean = isAlpha(point) || when (UCharacter.getType(point)) {
        UCharacterCategory.DECIMAL_DIGIT_NUMBER.toInt(),
        UCharacterCategory.LETTER_NUMBER.toInt(),
        UCharacterCategory.OTHER_NUMBER.toInt() -> true
        else -> false
    }
}
