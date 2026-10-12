// Port of pandoc-types Text.Pandoc.Definition Format instances.
// Copyright (c) 2006-2023 John MacFarlane. BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.ast

import com.ibm.icu.lang.UCharacter

/** Constructor preserves spelling; equality and order use full Unicode case folding. */
class Format(val value: String) : Comparable<Format> {
    private val folded = UCharacter.foldCase(value, true)
    override fun equals(other: Any?): Boolean = other is Format && folded == other.folded
    override fun hashCode(): Int = folded.hashCode()
    override fun compareTo(other: Format): Int = UnicodeTextOrder.compare(folded, other.folded)
    override fun toString(): String = value

    companion object {
        /** Equivalent to Haskell's IsString instance, which folds the stored spelling too. */
        fun literal(value: String): Format = Format(UCharacter.foldCase(value, true))
    }
}

/** Haskell Text orders Unicode scalar values, not UTF-16 code units. */
internal object UnicodeTextOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val x = a.codePointAt(i)
            val y = b.codePointAt(j)
            if (x != y) return x.compareTo(y)
            i += Character.charCount(x)
            j += Character.charCount(y)
        }
        return (a.length - i).compareTo(b.length - j)
    }
}
