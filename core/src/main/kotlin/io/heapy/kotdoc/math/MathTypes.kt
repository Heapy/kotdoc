// Text.TeXMath.Types, © 2009 John MacFarlane; GPL-2.0-or-later.
package io.heapy.kotdoc.math

import io.heapy.kotdoc.ast.UnicodeTextOrder
import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.layout.TextChars
import java.util.Collections

internal fun <T> ownMathList(xs: List<T>): List<T> = Collections.unmodifiableList(xs.toList())
enum class MathSymbolType { Ord, Op, Bin, Rel, Open, Close, Pun, Accent, Fence, TOver, TUnder, Alpha, BotAccent, Rad }
enum class MathAlignment { AlignLeft, AlignCenter, AlignRight }
enum class FractionType { NormalFrac, DisplayFrac, InlineFrac, NoLineFrac }
enum class StrokeType { ForwardSlash, BackSlash, XSlash }
enum class DisplayType { DisplayBlock, DisplayInline }
enum class MathTextType { TextNormal, TextBold, TextItalic, TextMonospace, TextSansSerif, TextDoubleStruck, TextScript, TextFraktur, TextBoldItalic, TextSansSerifBold, TextSansSerifBoldItalic, TextBoldScript, TextBoldFraktur, TextSansSerifItalic }
enum class FormType { FPrefix, FPostfix, FInfix }
enum class MathPosition { Under, Over }
sealed class DelimitedPart {
    class Middle(text: String):DelimitedPart() { val text=TextChars.fromString(text) }
    class Expression(val expression: MathExp):DelimitedPart()
}

/** Source constructor and field order, with owned finite expression lists. */
sealed class MathExp(val tag: Int) : Comparable<MathExp> {
    internal abstract fun fields(): List<Any>
    class ENumber(text: String) : MathExp(0) {
        val text=TextChars.fromString(text)
        override fun fields()=listOf(text)
    }
    class EGrouped(expressions: List<MathExp>) : MathExp(1) {
        val expressions=ownMathList(expressions)
        override fun fields()=listOf(expressions)
    }
    class EDelimited(open: String,close: String,parts: List<DelimitedPart>) : MathExp(2) {
        val open=TextChars.fromString(open)
        val close=TextChars.fromString(close)
        val parts=ownMathList(parts)
        override fun fields()=listOf(open,close,parts)
    }
    class EIdentifier(text: String) : MathExp(3) {
        val text=TextChars.fromString(text)
        override fun fields()=listOf(text)
    }
    class EMathOperator(text: String) : MathExp(4) {
        val text=TextChars.fromString(text)
        override fun fields()=listOf(text)
    }
    class ESymbol(val type: MathSymbolType,text: String) : MathExp(5) {
        val text=TextChars.fromString(text)
        override fun fields()=listOf(type,text)
    }
    class ESpace(val width: MathRational) : MathExp(6) {
        override fun fields()=listOf(width)
    }
    class ESub(val base: MathExp,val sub: MathExp) : MathExp(7) {
        override fun fields()=listOf(base,sub)
    }
    class ESuper(val base: MathExp,val sup: MathExp) : MathExp(8) {
        override fun fields()=listOf(base,sup)
    }
    class ESubsup(val base: MathExp,val sub: MathExp,val sup: MathExp) : MathExp(9) {
        override fun fields()=listOf(base,sub,sup)
    }
    class EOver(val convertible: Boolean,val base: MathExp,val over: MathExp) : MathExp(10) {
        override fun fields()=listOf(convertible,base,over)
    }
    class EUnder(val convertible: Boolean,val base: MathExp,val under: MathExp) : MathExp(11) {
        override fun fields()=listOf(convertible,base,under)
    }
    class EUnderover(val convertible: Boolean,val base: MathExp,val under: MathExp,val over: MathExp) : MathExp(12) {
        override fun fields()=listOf(convertible,base,under,over)
    }
    class EPhantom(val expression: MathExp) : MathExp(13) {
        override fun fields()=listOf(expression)
    }
    class EBoxed(val expression: MathExp) : MathExp(14) {
        override fun fields()=listOf(expression)
    }
    class ECancel(val stroke: StrokeType,val expression: MathExp) : MathExp(15) {
        override fun fields()=listOf(stroke,expression)
    }
    class EFraction(val type: FractionType,val numerator: MathExp,val denominator: MathExp) : MathExp(16) {
        override fun fields()=listOf(type,numerator,denominator)
    }
    class ERoot(val index: MathExp,val base: MathExp) : MathExp(17) {
        override fun fields()=listOf(index,base)
    }
    class ESqrt(val expression: MathExp) : MathExp(18) {
        override fun fields()=listOf(expression)
    }
    class EScaled(val scale: MathRational,val expression: MathExp) : MathExp(19) {
        override fun fields()=listOf(scale,expression)
    }
    class EArray(alignments: List<MathAlignment>,rows: List<List<List<MathExp>>>) : MathExp(20) {
        val alignments=ownMathList(alignments)
        val rows=ownMathList(rows.map { row -> ownMathList(row.map(::ownMathList)) })
        override fun fields()=listOf(alignments,rows)
    }
    class EText(val type: MathTextType,text: String) : MathExp(21) {
        val text=TextChars.fromString(text)
        override fun fields()=listOf(type,text)
    }
    class EStyled(val type: MathTextType,expressions: List<MathExp>) : MathExp(22) {
        val expressions=ownMathList(expressions)
        override fun fields()=listOf(type,expressions)
    }
    final override fun equals(other: Any?)=other is MathExp && compareTo(other)==0
    final override fun hashCode(): Int {
        var h=1;val todo=ArrayDeque<Any>();todo.add(this)
        while(todo.isNotEmpty()) when(val v=todo.removeLast()) {
            is MathExp -> { h=31*h+v.tag;v.fields().asReversed().forEach(todo::addLast) }
            is List<*> -> { h=31*h+v.size;v.asReversed().forEach { todo.addLast(checkNotNull(it)) } }
            is DelimitedPart.Middle -> { h=31*h;todo.addLast(v.text) }
            is DelimitedPart.Expression -> { h=31*h+1;todo.addLast(v.expression) }
            else -> h=31*h+v.hashCode()
        }
        return h
    }
    final override fun compareTo(other: MathExp): Int {
        val todo=ArrayDeque<Pair<Any,Any>>();todo.add(this to other)
        while(todo.isNotEmpty()) {
            val (a,b)=todo.removeLast()
            val c=when(a) {
                is MathExp -> { b as MathExp;val c=a.tag.compareTo(b.tag);if(c==0) todo.addLast(a.fields() to b.fields());c }
                is List<*> -> {
                    b as List<*>;val n=minOf(a.size,b.size)
                    todo.addLast(a.size to b.size)
                    for(i in n-1 downTo 0) todo.addLast(checkNotNull(a[i]) to checkNotNull(b[i]));0
                }
                is DelimitedPart -> { b as DelimitedPart
                    val ac=if(a is DelimitedPart.Middle) 0 else 1;val bc=if(b is DelimitedPart.Middle) 0 else 1
                    if(ac==bc) todo.addLast(if(a is DelimitedPart.Middle) a.text to (b as DelimitedPart.Middle).text else (a as DelimitedPart.Expression).expression to (b as DelimitedPart.Expression).expression)
                    ac.compareTo(bc)
                }
                is String -> UnicodeTextOrder.compare(a,b as String)
                is MathRational -> a.compareTo(b as MathRational)
                is Enum<*> -> a.ordinal.compareTo((b as Enum<*>).ordinal)
                is Boolean -> a.compareTo(b as Boolean)
                is Int -> a.compareTo(b as Int)
                else -> error("Unexpected math field")
            }
            if(c!=0) return c
        }
        return 0
    }
    fun show(precedence: Int=0)=MathShow.show(this,precedence)
    final override fun toString()=show()
}

object MathShow {
    private class Value(val value: Any,val precedence: Int=0)
    private class RawChar(val point: Int)
    private val names=listOf("ENumber","EGrouped","EDelimited","EIdentifier","EMathOperator","ESymbol","ESpace","ESub","ESuper","ESubsup","EOver","EUnder","EUnderover","EPhantom","EBoxed","ECancel","EFraction","ERoot","ESqrt","EScaled","EArray","EText","EStyled")
    fun show(value: Any,precedence: Int=0)=buildString {
        val todo=ArrayDeque<Any>();todo.addLast(Value(value,precedence))
        fun schedule(parts: List<Any>) { parts.asReversed().forEach(todo::addLast) }
        fun application(name: String,p: Int,fields: List<Any>) {
            val parts=mutableListOf<Any>();if(p>10) parts.add("(");parts.add(name)
            fields.forEach { parts.add(" ");parts.add(Value(it,11)) };if(p>10) parts.add(")");schedule(parts)
        }
        fun record(name: String,p: Int,fields: List<Pair<String,Any>>) {
            val parts=mutableListOf<Any>();if(p>10) parts.add("(");parts.add("$name {")
            fields.forEachIndexed {i,(key,value)->if(i>0) parts.add(", ");parts.add("$key = ");parts.add(Value(value))}
            parts.add("}");if(p>10) parts.add(")");schedule(parts)
        }
        while(todo.isNotEmpty()) when(val work=todo.removeLast()) {
            is String -> append(work)
            is Value -> when(val v=work.value) {
                is MathExp -> application(names[v.tag],work.precedence,v.fields())
                is DelimitedPart.Middle -> application("Left",work.precedence,listOf(v.text))
                is DelimitedPart.Expression -> application("Right",work.precedence,listOf(v.expression))
                is List<*> -> {
                    val parts=mutableListOf<Any>("[")
                    v.forEachIndexed { i,x->if(i>0) parts.add(",");parts.add(Value(checkNotNull(x))) };parts.add("]");schedule(parts)
                }
                is MathRational -> append(v.show(work.precedence))
                is MathOperatorRecord -> record("Operator",work.precedence,listOf("oper" to v.oper,"description" to v.description,"form" to v.form,"priority" to v.priority,"lspace" to v.leftSpace,"rspace" to v.rightSpace,"properties" to v.properties))
                is MathUnicodeRecord -> record("Record",work.precedence,listOf("uchar" to RawChar(v.character),"commands" to v.commands,"category" to v.category,"comments" to v.comments))
                is Pair<*,*> -> schedule(listOf("(",Value(checkNotNull(v.first)),",",Value(checkNotNull(v.second)),")"))
                is Long -> {val s=v.toString();append(if(v<0 && work.precedence>6) "($s)" else s)}
                is MathTeX.ControlSeq -> application("ControlSeq",work.precedence,listOf(v.text))
                is MathTeX.Token -> application("Token",work.precedence,listOf(RawChar(v.character)))
                is MathTeX.Literal -> application("Literal",work.precedence,listOf(v.text))
                is MathTeX.Grouped -> application("Grouped",work.precedence,listOf(v.items))
                MathTeX.Space -> append("Space")
                is RawChar -> {
                    val literal=when(v.point) {39->"\\'";34->"\"";else->HaskellString.quoteCodePoints(intArrayOf(v.point)).drop(1).dropLast(1)}
                    append('\'');append(literal);append('\'')
                }
                is String -> append(HaskellString.quote(v))
                is Enum<*> -> append(v.name)
                is Boolean -> append(if(v) "True" else "False")
                else -> error("Unexpected math value")
            }
        }
    }
}

class MathOperatorRecord(oper: String,description: String,val form: FormType,val priority: Long,val leftSpace: Long,val rightSpace: Long,properties: List<String>) {
    val oper=TextChars.fromString(oper);val description=TextChars.fromString(description)
    val properties=ownMathList(properties.map(TextChars::fromString))
}
class MathUnicodeRecord(val character: Int,commands: List<Pair<String,String>>,val category: MathSymbolType,comments: String) {
    init { require(character in 0..0x10ffff) }
    val commands=ownMathList(commands.map { TextChars.fromString(it.first) to TextChars.fromString(it.second) })
    val comments=TextChars.fromString(comments)
}
val defaultMathEnvironment: List<String> = ownMathList(listOf("amsmath","amssymb"))
