// GHC.Internal.Unicode 9.12.2 / Unicode 16.0.0. BSD-3-Clause / Unicode-3.0.
package io.heapy.kotdoc.parsing

import kotlinx.serialization.json.*

object RegexCharacters {
    private val data = Json.parseToJsonElement(checkNotNull(javaClass.getResource("/io/heapy/kotdoc/regex-char-data.json")).readText()).jsonObject
    private val ranges = data.getValue("ranges").jsonArray
    private val starts = IntArray(ranges.size) { ranges[it].jsonArray[0].jsonPrimitive.int }
    private val categories = IntArray(ranges.size) { ranges[it].jsonArray[1].jsonPrimitive.int }
    private val names = data.getValue("categories").jsonArray.map { it.jsonPrimitive.content }
    private fun mapping(name: String) = data.getValue(name).jsonObject.map { it.key.toInt() to it.value.jsonPrimitive.int }.toMap()
    private val lower = mapping("lower")
    private val upper = mapping("upper")
    fun category(point: Int): Int {
        require(point in 0..0x10ffff)
        val at = starts.binarySearch(point)
        return categories[if (at >= 0) at else -at - 2]
    }
    fun categoryName(point: Int) = names[category(point)]
    fun toLower(point: Int) = lower[point] ?: point
    fun toUpper(point: Int) = upper[point] ?: point
    fun isWordChar(point: Int) = ParserCharacters.isAlphaNum(point) || category(point) == 11
    fun isPrint(point: Int) = category(point) !in 23..29
    fun isLower(point: Int) = category(point) == 1
    fun isUpper(point: Int) = category(point) in listOf(0, 2)
    fun isPunctuation(point: Int) = category(point) in 11..17
    fun isSymbol(point: Int) = category(point) in 18..21
    fun isControl(point: Int) = category(point) == 25
    fun isHorizSpace(point: Int) = point in listOf(9, 32, 160, 5760, 6158, 8239, 8287, 12288) || point in 8192..8202
}
