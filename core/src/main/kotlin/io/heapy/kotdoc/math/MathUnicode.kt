// Text.TeXMath.Unicode.ToUnicode, © 2009 John MacFarlane; GPL-2.0-or-later.
package io.heapy.kotdoc.math

import io.heapy.kotdoc.layout.TextChars

object MathUnicode {
    private val forward=mathUnicodeTable.associate { (t,a,b)->(t to a) to b }
    private val reverse=mathUnicodeTable.associate { (t,a,b)->b to (t to a) }
    fun toUnicodeChar(type: MathTextType,character: Int): Int?=forward[type to character]
    fun fromUnicodeChar(character: Int): Pair<MathTextType,Int>?=reverse[character]
    fun toUnicode(type: MathTextType,text: String): String {
        val s=TextChars.fromString(text)
        return if(type==MathTextType.TextNormal) s else buildString {s.codePoints().forEach {appendCodePoint(toUnicodeChar(type,it) ?: it)}}
    }
    fun fromUnicode(type: MathTextType,text: String)=buildString {
        TextChars.fromString(text).codePoints().forEach {cp->val v=fromUnicodeChar(cp);appendCodePoint(if(v?.first==type) v.second else cp)}
    }
}
