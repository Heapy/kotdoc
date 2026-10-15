// Text.TeXMath.Unicode.ToTeX, © 2014 Matthew Pickering, GPL-2.0-or-later.
// Mapping © 2011 Günter Milde; LPPL 1.3 or later, original source retained.
// Modified Kotlin work (2026): MathTeXUnicode.kt; see Math-TeX-Mapping-NOTICE.txt.
package io.heapy.kotdoc.math

import io.heapy.kotdoc.ast.UnicodeTextOrder
import io.heapy.kotdoc.layout.TextChars
import java.util.Collections
import java.util.TreeMap

object MathTeXUnicode {
    val records: List<MathUnicodeRecord> get()=mathTeXUnicodeRecords
    private val recordsMap=records.associateBy {it.character}
    val symbolMap: Map<String,MathExp> by lazy {
        val out=TreeMap<String,MathExp>(UnicodeTextOrder)
        for(record in records.asReversed()) for((_,command) in record.commands.asReversed()) {
            if(command.startsWith('\\') && '{' !in command)
                out[command]=MathExp.ESymbol(record.category,TextChars.fromPoints(intArrayOf(record.character)))
        }
        Collections.unmodifiableMap(out)
    }
    fun getSymbolType(character: Int)=recordsMap[character]?.category ?: MathSymbolType.Ord
    fun getTeXMath(input: String,environment: List<String>): List<MathTeX> {
        val env=environment.map(TextChars::fromString)
        val result=mutableListOf<MathTeX>()
        for(c in TextChars.fromString(input).codePoints().toArray()) {
            if(c==65024) continue
            val record=recordsMap[c]
            val command=record?.commands?.firstOrNull {it.first=="base"}?.second
                ?: env.firstNotNullOfOrNull {pkg->record?.commands?.firstOrNull {it.first==pkg}?.second}
            if(command!=null) {
                if(MathTeXRenderer.isControlSeq(command)) result.add(MathTeX.ControlSeq(command))
                else {
                    val cps=command.codePoints().toArray()
                    if(cps.size==1) result.add(MathTeX.Token(cps[0])) else if(cps.isNotEmpty()) result.add(MathTeX.Literal(command))
                }
                if(record!!.category in listOf(MathSymbolType.Accent,MathSymbolType.Rad,MathSymbolType.TOver,MathSymbolType.TUnder)) result.add(MathTeX.Grouped(emptyList()))
            } else {
                val style=MathUnicode.fromUnicodeChar(c)
                if(style==null) result.add(MathTeXRenderer.escapeLaTeX(c))
                else {result.add(MathTeX.ControlSeq(MathShared.getLaTeXTextCommand(env,style.first)));result.add(MathTeX.Grouped(listOf(MathTeX.Token(style.second))))}
            }
        }
        return ownMathList(result)
    }
}
