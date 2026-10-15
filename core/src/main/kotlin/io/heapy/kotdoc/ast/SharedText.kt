// Text.Pandoc.Shared text whitespace helpers, Copyright (C) 2006-2024
// John MacFarlane. GPL-2.0-or-later; licenses/pandoc-GPL-2.0.md.
package io.heapy.kotdoc.ast

object SharedText {
    fun isWS(point: Int): Boolean = point == 32 || point == 13 || point == 10 || point == 9
    fun trim(text: String): String = text.trim { isWS(it.code) }
    fun triml(text: String): String = text.trimStart { isWS(it.code) }
    fun trimr(text: String): String = text.trimEnd { isWS(it.code) }
    fun stripTrailingNewlines(text: String): String = text.trimEnd { it == '\n' }
}
