// GHC.Internal.Unicode 9.12.2; BSD-3-Clause, licenses/GHC-base-BSD-3-Clause.txt.
// Unicode 16.0.0 categories; licenses/Unicode-3.0.txt.
package io.heapy.kotdoc.parsing

import kotlinx.serialization.json.*

object ParserCharacters {
    private val rows = Json.parseToJsonElement(checkNotNull(javaClass.getResource("/io/heapy/kotdoc/parser-char-flags.json")).readText())
        .jsonObject.getValue("ranges").jsonArray
    private val starts = IntArray(rows.size) { rows[it].jsonArray[0].jsonPrimitive.int }
    private val bits = IntArray(rows.size) { rows[it].jsonArray[1].jsonPrimitive.int }
    fun flags(point: Int): Int {
        require(point in 0..0x10ffff) { "Not a Haskell Char: $point" }
        val at = starts.binarySearch(point)
        return bits[if (at >= 0) at else -at - 2]
    }
    fun isAlpha(point: Int) = flags(point) and 1 != 0
    fun isAlphaNum(point: Int) = flags(point) and 2 != 0
    fun isSpace(point: Int) = flags(point) and 4 != 0
    fun isDigit(point: Int) = flags(point) and 8 != 0
}
