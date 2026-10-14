// Skylighting.Types 0.15, Copyright (c) 2016-2024 John MacFarlane.
// BSD-3-Clause; licenses/Skylighting-core-BSD-3-Clause.txt.
package io.heapy.kotdoc.highlighting

import io.heapy.kotdoc.formats.HaskellString

enum class ANSIColorLevel { ANSI16Color, ANSI256Color, ANSITrueColor }

class FormatOptions(
    val numberLines: Boolean = false,
    val startNumber: Long = 1,
    val lineAnchors: Boolean = false,
    val titleAttributes: Boolean = false,
    codeClasses: List<String> = emptyList(),
    containerClasses: List<String> = emptyList(),
    val lineIdPrefix: String = "",
    val ansiColorLevel: ANSIColorLevel = ANSIColorLevel.ANSI16Color,
) : Comparable<FormatOptions> {
    val codeClasses = syntaxList(codeClasses)
    val containerClasses = syntaxList(containerClasses)
    internal val fields get() = listOf(numberLines, startNumber, lineAnchors, titleAttributes, codeClasses, containerClasses, lineIdPrefix, ansiColorLevel)
    override fun compareTo(other: FormatOptions) = syntaxCompare(fields.dropLast(1) + ansiColorLevel.ordinal, other.fields.dropLast(1) + other.ansiColorLevel.ordinal)
    override fun equals(other: Any?) = other is FormatOptions && compareTo(other) == 0
    override fun hashCode() = fields.hashCode()
    fun haskellShow(precedence: Int = 0): String {
        fun bool(x: Boolean) = if (x) "True" else "False"
        val text = "FormatOptions {numberLines = ${bool(numberLines)}, startNumber = $startNumber, lineAnchors = ${bool(lineAnchors)}, titleAttributes = ${bool(titleAttributes)}, codeClasses = ${syntaxShow(codeClasses)}, containerClasses = ${syntaxShow(containerClasses)}, lineIdPrefix = ${HaskellString.quote(lineIdPrefix)}, ansiColorLevel = ${ansiColorLevel.name}}"
        return if (precedence > 10) "($text)" else text
    }
    override fun toString() = haskellShow()
    companion object { val Default = FormatOptions() }
}
