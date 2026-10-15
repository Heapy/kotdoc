// Text.TeXMath.TeX, John MacFarlane; GPL-2.0-or-later, licenses/.
package io.heapy.kotdoc.math

import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.parsing.ParserCharacters

sealed class MathTeX {
    class ControlSeq(text: String):MathTeX() { val text=TextChars.fromString(text) }
    class Token(val character: Int):MathTeX() { init { require(character in 0..0x10ffff) } }
    class Literal(text: String):MathTeX() { val text=TextChars.fromString(text) }
    class Grouped(items: List<MathTeX>):MathTeX() { val items=ownMathList(items) }
    data object Space:MathTeX()
    final override fun equals(other: Any?): Boolean {
        if(this===other) return true
        if(other !is MathTeX) return false
        val todo=ArrayDeque<Pair<MathTeX,MathTeX>>();todo.addLast(this to other)
        while(todo.isNotEmpty()) {
            val (a,b)=todo.removeLast()
            if(a.javaClass!=b.javaClass) return false
            when(a) {
                is ControlSeq -> if(a.text!=(b as ControlSeq).text) return false
                is Literal -> if(a.text!=(b as Literal).text) return false
                is Token -> if(a.character!=(b as Token).character) return false
                is Grouped -> {
                    b as Grouped;if(a.items.size!=b.items.size) return false
                    for(i in a.items.indices.reversed()) todo.addLast(a.items[i] to b.items[i])
                }
                Space -> Unit
            }
        }
        return true
    }
    final override fun hashCode(): Int {
        var h=1;val todo=ArrayDeque<MathTeX>();todo.addLast(this)
        while(todo.isNotEmpty()) when(val x=todo.removeLast()) {
            is ControlSeq -> {h=31*h;h=31*h+x.text.hashCode()}
            is Token -> {h=31*h+1;h=31*h+x.character}
            is Literal -> {h=31*h+2;h=31*h+x.text.hashCode()}
            is Grouped -> {h=31*h+3;h=31*h+x.items.size;x.items.asReversed().forEach(todo::addLast)}
            Space -> h=31*h+4
        }
        return h
    }
    fun show(precedence: Int=0)=MathShow.show(this,precedence)
    final override fun toString()=show()
}

object MathTeXRenderer {
    private class Rope(val text: String?,val left: Rope?=null,val right: Rope?=null) {
        val prefix: String=if(text!=null) text.take(8) else ((left?.prefix ?: "")+(right?.prefix ?: "")).take(8)
    }
    private fun append(a: Rope?,b: Rope?): Rope?=if(a==null) b else if(b==null) a else Rope(null,a,b)
    private fun text(s: String): Rope?=if(s.isEmpty()) null else Rope(s)
    private class EndGroup(val rest: Rope?)
    fun renderTeX(tex: MathTeX,suffix: String=""): String = renderTeXList(listOf(tex),suffix)
    /** Original foldr renderTeX, retaining suffix lookahead across adjacent tokens. */
    fun renderTeXList(tex: List<MathTeX>,suffix: String=""): String {
        var out=text(TextChars.fromString(suffix));val todo=ArrayDeque<Any>();todo.addAll(tex)
        fun prepend(s: String) { out=append(text(s),out) }
        fun first()=out?.prefix?.codePointAt(0)
        while(todo.isNotEmpty()) when(val t=todo.removeLast()) {
            is EndGroup -> out=append(append(append(text("{"),out),text("}")),t.rest)
            is MathTeX.Token -> prepend(TextChars.fromPoints(intArrayOf(t.character)))
            is MathTeX.Literal -> {
                val end=t.text.takeIf(String::isNotEmpty)?.let { it.codePointBefore(it.length) }
                if((end==null || ParserCharacters.isAlpha(end)) && first()?.let(ParserCharacters::isAlpha)==true) prepend(" ")
                prepend(t.text)
            }
            is MathTeX.ControlSeq -> {
                if(t.text!="\\ " && first()?.let {ParserCharacters.isAlphaNum(it) || it>=128}==true) prepend(" ")
                prepend(t.text)
            }
            is MathTeX.Grouped -> {
                if(t.items.size==1 && t.items[0] is MathTeX.Grouped) todo.addLast(t.items[0])
                else {
                    todo.addLast(EndGroup(out));out=null
                    var start=0;var end=t.items.size
                    while(start<end && t.items[start]===MathTeX.Space) start++
                    while(end>start && t.items[end-1]===MathTeX.Space) end--
                    for(i in start until end) todo.addLast(t.items[i])
                }
            }
            MathTeX.Space -> if(out!=null && listOf("^","_"," ","\\limits").none {out!!.prefix.startsWith(it)}) prepend(" ")
        }
        return buildString {
            val stack=ArrayDeque<Rope>();out?.let(stack::addLast)
            while(stack.isNotEmpty()) {
                val r=stack.removeLast()
                if(r.text!=null) append(r.text) else {r.right?.let(stack::addLast);r.left?.let(stack::addLast)}
            }
        }
    }
    fun isControlSeq(input: String): Boolean {
        val s=TextChars.fromString(input);if(!s.startsWith('\\')) return false
        val xs=s.substring(1)
        return xs.codePointCount(0,xs.length)==1 && xs!=" " || xs.codePoints().allMatch(ParserCharacters::isAlpha)
    }
    fun escapeLaTeX(character: Int): MathTeX=when(character) {
        126->MathTeX.ControlSeq("\\textasciitilde")
        94->MathTeX.Literal("\\textasciicircum")
        92->MathTeX.ControlSeq("\\textbackslash")
        0x200b->MathTeX.Literal("\\!")
        0x200a,0x2006->MathTeX.Literal("\\,")
        0xa0->MathTeX.Literal("~")
        0x2005->MathTeX.Literal("\\:")
        0x2004->MathTeX.Literal("\\;")
        0x2001,0x2003->MathTeX.ControlSeq("\\quad")
        0x2032->MathTeX.Literal("'")
        0x2033->MathTeX.Literal("''")
        0x2034->MathTeX.Literal("'''")
        35,36,37,38,95,123,125,32->MathTeX.Literal("\\"+TextChars.fromPoints(intArrayOf(character)))
        else->MathTeX.Token(character)
    }
}
