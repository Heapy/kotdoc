// Text.TeXMath.Readers.TeX.Commands, © 2009–2022 John MacFarlane, GPL-2.0-or-later.
package io.heapy.kotdoc.math

import io.heapy.kotdoc.ast.UnicodeTextOrder
import java.util.Collections
import java.util.TreeMap

object MathTeXCommands {
    private fun <A> ordered(rows: List<Pair<String,A>>): Map<String,A> = Collections.unmodifiableMap(TreeMap<String,A>(UnicodeTextOrder).apply {rows.forEach {(k,v)->put(k,v)}})
    val styleOps=ordered(mathCommandsStyleOps())
    val textOps=ordered(mathCommandsTextOps())
    val enclosures=ordered(mathCommandsEnclosures())
    val operators=ordered(mathCommandsOperators())
    val symbols=ordered(MathTeXUnicode.symbolMap.entries.map {it.key to it.value}+mathCommandsSymbolMapOverrides())
    val siUnitMap=ordered(mathCommandsSiUnitMap())
}
