// Text.Pandoc.Shared text whitespace helpers, Copyright (C) 2006-2024
// John MacFarlane. GPL-2.0-or-later; licenses/pandoc-GPL-2.0.md.
package io.heapy.kotdoc.ast

object SharedText {
    fun isWS(point: Int): Boolean = point == 32 || point == 13 || point == 10 || point == 9
    fun trim(text: String): String = text.trim { isWS(it.code) }
    fun triml(text: String): String = text.trimStart { isWS(it.code) }
    fun trimr(text: String): String = text.trimEnd { isWS(it.code) }
    fun stripTrailingNewlines(text: String): String = text.trimEnd { it == '\n' }
    fun tabFilter(tabStop: Long,text: String): String {
        if(tabStop==0L || text.isEmpty())return text
        val lines=text.split('\n').let { if(text.endsWith('\n'))it.dropLast(1) else it }
        return buildString {
            for(line in lines) {
                var at=0
                while(true) {
                    val tab=line.indexOf('\t',at)
                    if(tab<0) { append(line,at,line.length);break }
                    val count=tabStop-Math.floorMod(line.codePointCount(at,tab).toLong(),tabStop)
                    append(line,at,tab)
                    if(count>0) { require(count<=Int.MAX_VALUE) { "Tab expansion exceeds JVM string capacity" };append(" ".repeat(count.toInt())) }
                    at=tab+1
                }
                append('\n')
            }
        }
    }
}
