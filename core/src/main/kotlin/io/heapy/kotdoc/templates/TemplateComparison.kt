// Eq/Ord derivations in doctemplates 0.11.1 and doclayout 0.6.
// Copyright (C) 2009-2024 John MacFarlane, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.templates

import io.heapy.kotdoc.layout.*

/** Compare scalar Text without allocating or inspecting an unreachable suffix. */
private fun compareText(left: String,right: String): Int {
    var i=0;var j=0
    while (i < left.length && j < right.length) {
        val a=left.codePointAt(i);val b=right.codePointAt(j)
        val x=if (a in 0xd800..0xdfff) 0xfffd else a
        val y=if (b in 0xd800..0xdfff) 0xfffd else b
        if (x != y) return x.compareTo(y)
        i+=Character.charCount(a);j+=Character.charCount(b)
    }
    return (left.length-i).compareTo(right.length-j)
}

/** The target's Eq and all Ord operations are distinct source contracts. */
interface TargetComparison<A> {
    fun equal(left: A, right: A): Boolean
    fun notEqual(left: A, right: A) = !equal(left,right)
    fun compare(left: A, right: A): Int
    fun less(left: A, right: A) = compare(right,left) > 0
    fun lessEqual(left: A, right: A) = compare(left,right) <= 0
    fun greater(left: A, right: A) = compare(left,right) > 0
    fun greaterEqual(left: A, right: A) = compare(right,left) <= 0
    companion object {
        val Text = object : TargetComparison<kotlin.String> {
            override fun equal(left: kotlin.String, right: kotlin.String) = compareText(left,right) == 0
            override fun compare(left: kotlin.String, right: kotlin.String) = compareText(left,right)
        }
        val String = object : TargetComparison<CharacterString> {
            override fun equal(left: CharacterString, right: CharacterString) = left == right
            override fun compare(left: CharacterString, right: CharacterString) = left.compareTo(right)
        }
        val Int64 = object : TargetComparison<Long> {
            override fun equal(left: Long, right: Long) = left == right
            override fun compare(left: Long, right: Long) = left.compareTo(right)
        }
        val Double = object : TargetComparison<kotlin.Double> {
            override fun equal(left: kotlin.Double, right: kotlin.Double) = left == right
            override fun compare(left: kotlin.Double, right: kotlin.Double) = if (left == right) 0 else if (left <= right) -1 else 1
            override fun less(left: kotlin.Double, right: kotlin.Double) = left < right
            override fun lessEqual(left: kotlin.Double, right: kotlin.Double) = left <= right
            override fun greater(left: kotlin.Double, right: kotlin.Double) = left > right
            override fun greaterEqual(left: kotlin.Double, right: kotlin.Double) = left >= right
        }
    }
}

/** Structural comparisons retain constructors, widths, attributes and field order. */
class TemplateComparison<A>(private val target: TargetComparison<A>) {
    fun compare(left: Template<A>, right: Template<A>): Int = evaluate(left,right,Op.Compare)
    fun equal(left: Template<A>, right: Template<A>): Boolean = evaluate(left,right,Op.Equal) != 0
    fun notEqual(left: Template<A>, right: Template<A>): Boolean = !equal(left,right)
    fun less(left: Template<A>, right: Template<A>): Boolean = evaluate(left,right,Op.Less) != 0
    fun lessEqual(left: Template<A>, right: Template<A>): Boolean = evaluate(left,right,Op.LessEqual) != 0
    fun greater(left: Template<A>, right: Template<A>): Boolean = evaluate(left,right,Op.Greater) != 0
    fun greaterEqual(left: Template<A>, right: Template<A>): Boolean = evaluate(left,right,Op.GreaterEqual) != 0
    fun minimum(left: Template<A>, right: Template<A>): Template<A> = if (lessEqual(left,right)) left else right
    fun maximum(left: Template<A>, right: Template<A>): Template<A> = if (lessEqual(left,right)) right else left

    fun compare(left: Doc<A>, right: Doc<A>): Int = evaluate(left,right,Op.Compare)
    fun equal(left: Doc<A>, right: Doc<A>): Boolean = evaluate(left,right,Op.Equal) != 0
    fun notEqual(left: Doc<A>, right: Doc<A>): Boolean = !equal(left,right)
    fun less(left: Doc<A>, right: Doc<A>): Boolean = evaluate(left,right,Op.Less) != 0
    fun lessEqual(left: Doc<A>, right: Doc<A>): Boolean = evaluate(left,right,Op.LessEqual) != 0
    fun greater(left: Doc<A>, right: Doc<A>): Boolean = evaluate(left,right,Op.Greater) != 0
    fun greaterEqual(left: Doc<A>, right: Doc<A>): Boolean = evaluate(left,right,Op.GreaterEqual) != 0
    fun minimum(left: Doc<A>, right: Doc<A>): Doc<A> = if (lessEqual(left,right)) left else right
    fun maximum(left: Doc<A>, right: Doc<A>): Doc<A> = if (lessEqual(left,right)) right else left

    fun compare(left: Attributed<A>, right: Attributed<A>): Int = evaluate(left,right,Op.Compare)
    fun equal(left: Attributed<A>, right: Attributed<A>): Boolean = evaluate(left,right,Op.Equal) != 0
    fun notEqual(left: Attributed<A>, right: Attributed<A>): Boolean = !equal(left,right)
    fun less(left: Attributed<A>, right: Attributed<A>): Boolean = evaluate(left,right,Op.Less) != 0
    fun lessEqual(left: Attributed<A>, right: Attributed<A>): Boolean = evaluate(left,right,Op.LessEqual) != 0
    fun greater(left: Attributed<A>, right: Attributed<A>): Boolean = evaluate(left,right,Op.Greater) != 0
    fun greaterEqual(left: Attributed<A>, right: Attributed<A>): Boolean = evaluate(left,right,Op.GreaterEqual) != 0
    fun minimum(left: Attributed<A>, right: Attributed<A>): Attributed<A> = if (lessEqual(left,right)) left else right
    fun maximum(left: Attributed<A>, right: Attributed<A>): Attributed<A> = if (lessEqual(left,right)) right else left

    fun compare(left: AttributedPart<A>, right: AttributedPart<A>): Int = evaluate(left,right,Op.Compare)
    fun equal(left: AttributedPart<A>, right: AttributedPart<A>): Boolean = evaluate(left,right,Op.Equal) != 0
    fun notEqual(left: AttributedPart<A>, right: AttributedPart<A>): Boolean = !equal(left,right)
    fun less(left: AttributedPart<A>, right: AttributedPart<A>): Boolean = evaluate(left,right,Op.Less) != 0
    fun lessEqual(left: AttributedPart<A>, right: AttributedPart<A>): Boolean = evaluate(left,right,Op.LessEqual) != 0
    fun greater(left: AttributedPart<A>, right: AttributedPart<A>): Boolean = evaluate(left,right,Op.Greater) != 0
    fun greaterEqual(left: AttributedPart<A>, right: AttributedPart<A>): Boolean = evaluate(left,right,Op.GreaterEqual) != 0
    fun minimum(left: AttributedPart<A>, right: AttributedPart<A>): AttributedPart<A> = if (lessEqual(left,right)) left else right
    fun maximum(left: AttributedPart<A>, right: AttributedPart<A>): AttributedPart<A> = if (lessEqual(left,right)) right else left

    fun compare(left: Font, right: Font): Int = evaluate(left,right,Op.Compare)
    fun equal(left: Font, right: Font): Boolean = evaluate(left,right,Op.Equal) != 0
    fun notEqual(left: Font, right: Font): Boolean = !equal(left,right)
    fun less(left: Font, right: Font): Boolean = evaluate(left,right,Op.Less) != 0
    fun lessEqual(left: Font, right: Font): Boolean = evaluate(left,right,Op.LessEqual) != 0
    fun greater(left: Font, right: Font): Boolean = evaluate(left,right,Op.Greater) != 0
    fun greaterEqual(left: Font, right: Font): Boolean = evaluate(left,right,Op.GreaterEqual) != 0
    fun minimum(left: Font, right: Font): Font = if (lessEqual(left,right)) left else right
    fun maximum(left: Font, right: Font): Font = if (lessEqual(left,right)) right else left

    fun compare(left: StyleRequest, right: StyleRequest): Int = evaluate(left,right,Op.Compare)
    fun equal(left: StyleRequest, right: StyleRequest): Boolean = evaluate(left,right,Op.Equal) != 0
    fun notEqual(left: StyleRequest, right: StyleRequest): Boolean = !equal(left,right)
    fun less(left: StyleRequest, right: StyleRequest): Boolean = evaluate(left,right,Op.Less) != 0
    fun lessEqual(left: StyleRequest, right: StyleRequest): Boolean = evaluate(left,right,Op.LessEqual) != 0
    fun greater(left: StyleRequest, right: StyleRequest): Boolean = evaluate(left,right,Op.Greater) != 0
    fun greaterEqual(left: StyleRequest, right: StyleRequest): Boolean = evaluate(left,right,Op.GreaterEqual) != 0
    fun minimum(left: StyleRequest, right: StyleRequest): StyleRequest = if (lessEqual(left,right)) left else right
    fun maximum(left: StyleRequest, right: StyleRequest): StyleRequest = if (lessEqual(left,right)) right else left

    fun compare(left: Variable, right: Variable): Int = evaluate(left,right,Op.Compare)
    fun equal(left: Variable, right: Variable): Boolean = evaluate(left,right,Op.Equal) != 0
    fun notEqual(left: Variable, right: Variable): Boolean = !equal(left,right)
    fun less(left: Variable, right: Variable): Boolean = evaluate(left,right,Op.Less) != 0
    fun lessEqual(left: Variable, right: Variable): Boolean = evaluate(left,right,Op.LessEqual) != 0
    fun greater(left: Variable, right: Variable): Boolean = evaluate(left,right,Op.Greater) != 0
    fun greaterEqual(left: Variable, right: Variable): Boolean = evaluate(left,right,Op.GreaterEqual) != 0
    fun minimum(left: Variable, right: Variable): Variable = if (lessEqual(left,right)) left else right
    fun maximum(left: Variable, right: Variable): Variable = if (lessEqual(left,right)) right else left

    fun compare(left: Pipe, right: Pipe): Int = evaluate(left,right,Op.Compare)
    fun equal(left: Pipe, right: Pipe): Boolean = evaluate(left,right,Op.Equal) != 0
    fun notEqual(left: Pipe, right: Pipe): Boolean = !equal(left,right)
    fun less(left: Pipe, right: Pipe): Boolean = evaluate(left,right,Op.Less) != 0
    fun lessEqual(left: Pipe, right: Pipe): Boolean = evaluate(left,right,Op.LessEqual) != 0
    fun greater(left: Pipe, right: Pipe): Boolean = evaluate(left,right,Op.Greater) != 0
    fun greaterEqual(left: Pipe, right: Pipe): Boolean = evaluate(left,right,Op.GreaterEqual) != 0
    fun minimum(left: Pipe, right: Pipe): Pipe = if (lessEqual(left,right)) left else right
    fun maximum(left: Pipe, right: Pipe): Pipe = if (lessEqual(left,right)) right else left

    fun compare(left: Border, right: Border): Int = evaluate(left,right,Op.Compare)
    fun equal(left: Border, right: Border): Boolean = evaluate(left,right,Op.Equal) != 0
    fun notEqual(left: Border, right: Border): Boolean = !equal(left,right)
    fun less(left: Border, right: Border): Boolean = evaluate(left,right,Op.Less) != 0
    fun lessEqual(left: Border, right: Border): Boolean = evaluate(left,right,Op.LessEqual) != 0
    fun greater(left: Border, right: Border): Boolean = evaluate(left,right,Op.Greater) != 0
    fun greaterEqual(left: Border, right: Border): Boolean = evaluate(left,right,Op.GreaterEqual) != 0
    fun minimum(left: Border, right: Border): Border = if (lessEqual(left,right)) left else right
    fun maximum(left: Border, right: Border): Border = if (lessEqual(left,right)) right else left

    fun compare(left: Alignment, right: Alignment): Int = evaluate(left,right,Op.Compare)
    fun equal(left: Alignment, right: Alignment): Boolean = evaluate(left,right,Op.Equal) != 0
    fun notEqual(left: Alignment, right: Alignment): Boolean = !equal(left,right)
    fun less(left: Alignment, right: Alignment): Boolean = evaluate(left,right,Op.Less) != 0
    fun lessEqual(left: Alignment, right: Alignment): Boolean = evaluate(left,right,Op.LessEqual) != 0
    fun greater(left: Alignment, right: Alignment): Boolean = evaluate(left,right,Op.Greater) != 0
    fun greaterEqual(left: Alignment, right: Alignment): Boolean = evaluate(left,right,Op.GreaterEqual) != 0
    fun minimum(left: Alignment, right: Alignment): Alignment = if (lessEqual(left,right)) left else right
    fun maximum(left: Alignment, right: Alignment): Alignment = if (lessEqual(left,right)) right else left

    private enum class Op { Compare, Equal, Less, LessEqual, Greater, GreaterEqual }
    private sealed interface Task
    private class Work(val left: Any, val right: Any, val op: Op) : Task
    private class ListWork(val left: List<*>,val right: List<*>,val index: Int,val op: Op) : Task
    private class Target(val value: Any?)
    private class Maybe(val value: String?)
    private class Sequence(val values: List<Any>)
    private class Shape(val tag: Int, val fields: List<Any> = emptyList())

    private fun shape(value: Any): Shape = when (value) {
        is Template.Interpolate -> Shape(0,listOf(value.variable))
        is Template.Conditional<*> -> Shape(1,listOf(value.variable,value.yes,value.no))
        is Template.Iterate<*> -> Shape(2,listOf(value.variable,value.body,value.separator))
        is Template.Nested<*> -> Shape(3,listOf(value.template))
        is Template.Partial<*> -> Shape(4,listOf(value.pipes,value.template))
        is Template.Literal<*> -> Shape(5,listOf(value.doc))
        is Template.Concat<*> -> Shape(6,listOf(value.left,value.right))
        Template.Empty -> Shape(7)
        is Doc.Text<*> -> Shape(0,listOf(value.width,Target(value.value)))
        is Doc.Block<*> -> Shape(1,listOf(value.width,value.lines))
        is Doc.VFill<*> -> Shape(2,listOf(value.width,Target(value.value)))
        is Doc.CookedText<*> -> Shape(3,listOf(value.width,value.value))
        is Doc.Prefixed<*> -> Shape(4,listOf(value.prefix,value.doc))
        is Doc.BeforeNonBlank<*> -> Shape(5,listOf(value.doc))
        is Doc.Flush<*> -> Shape(6,listOf(value.doc))
        Doc.BreakingSpace -> Shape(7)
        is Doc.AfterBreak -> Shape(8,listOf(value.value))
        Doc.CarriageReturn -> Shape(9)
        Doc.NewLine -> Shape(10)
        is Doc.BlankLines -> Shape(11,listOf(value.count))
        is Doc.Concat<*> -> Shape(12,listOf(value.left,value.right))
        is Doc.Styled<*> -> Shape(13,listOf(value.style,value.doc))
        is Doc.Linked<*> -> Shape(14,listOf(value.link,value.doc))
        Doc.Empty -> Shape(15)
        is Attributed<*> -> Shape(0,listOf(Sequence(value.parts)))
        is AttributedPart<*> -> Shape(0,listOf(Maybe(value.link),value.font,Target(value.value)))
        is Font -> Shape(0,listOf(value.bold,value.italic,value.underline.ordinal.toLong(),value.strikeout,
            (value.foreground?.ordinal ?: -1).toLong(),(value.background?.ordinal ?: -1).toLong(),Maybe(value.link)))
        is StyleRequest.Weight -> Shape(0,listOf(value.bold))
        is StyleRequest.Shape -> Shape(1,listOf(value.italic))
        is StyleRequest.Foreground -> Shape(2,listOf((value.color?.ordinal ?: -1).toLong()))
        is StyleRequest.Background -> Shape(3,listOf((value.color?.ordinal ?: -1).toLong()))
        is StyleRequest.Underlined -> Shape(4,listOf(value.underline.ordinal.toLong()))
        is StyleRequest.Strikeout -> Shape(5,listOf(value.struck))
        is Variable -> Shape(0,listOf(value.parts,value.pipes))
        is Border -> Shape(0,listOf(value.left,value.right))
        is Alignment -> Shape(value.ordinal)
        Pipe.ToPairs -> Shape(0); Pipe.ToUppercase -> Shape(1); Pipe.ToLowercase -> Shape(2)
        Pipe.ToLength -> Shape(3); Pipe.Reverse -> Shape(4); Pipe.FirstItem -> Shape(5)
        Pipe.LastItem -> Shape(6); Pipe.Rest -> Shape(7); Pipe.AllButLast -> Shape(8)
        Pipe.Chomp -> Shape(9); Pipe.ToAlpha -> Shape(10); Pipe.ToRoman -> Shape(11); Pipe.NoWrap -> Shape(12)
        is Pipe.Block -> Shape(13,listOf(value.alignment,value.width,value.border))
        is Maybe -> if (value.value == null) Shape(0) else Shape(1,listOf(value.value))
        else -> error("Unsupported structural comparison: ${value.javaClass}")
    }
    private fun relation(sign: Int, op: Op): Int = when (op) {
        Op.Compare -> sign.compareTo(0)
        Op.Equal -> if (sign == 0) 1 else 0
        Op.Less -> if (sign < 0) 1 else 0
        Op.LessEqual -> if (sign <= 0) 1 else 0
        Op.Greater -> if (sign > 0) 1 else 0
        Op.GreaterEqual -> if (sign >= 0) 1 else 0
    }
    /** GHC emits compare-only instances for large types and compare/< for Attr. */
    private fun evaluate(left: Any, right: Any, requested: Op): Int {
        if (requested == Op.Compare || requested == Op.Equal) return structural(left,right,requested)
        if (left is Template<*> || left is Doc<*> || left is Attributed<*>) {
            val reversed = requested == Op.Less || requested == Op.GreaterEqual
            val sign = if (reversed) structural(right,left,Op.Compare) else structural(left,right,Op.Compare)
            return if (requested == Op.Less || requested == Op.Greater) {
                if (sign > 0) 1 else 0
            } else if (sign <= 0) 1 else 0
        }
        val reversed = requested == Op.LessEqual || requested == Op.Greater
        val result = if (reversed) structural(right,left,Op.Less) else structural(left,right,Op.Less)
        return if (requested == Op.LessEqual || requested == Op.GreaterEqual) 1-result else result
    }
    private fun structural(left: Any, right: Any, requested: Op): Int {
        val todo=ArrayDeque<Task>();todo.addLast(Work(left,right,requested))
        fun fields(a: List<Any>, b: List<Any>, op: Op) {
            for (i in a.indices.reversed()) todo.addLast(Work(a[i],b[i],
                if (op == Op.Equal || i == a.lastIndex) op else Op.Compare))
        }
        fun list(a: List<*>, b: List<*>, op: Op) {
            todo.addLast(ListWork(a,b,0,if (op == Op.Equal) Op.Equal else Op.Compare))
        }
        while (todo.isNotEmpty()) {
            val task=todo.removeLast()
            if (task is ListWork) {
                val i=task.index
                if (i < minOf(task.left.size,task.right.size)) {
                    todo.addLast(ListWork(task.left,task.right,i+1,task.op))
                    todo.addLast(Work(checkNotNull(task.left[i]),checkNotNull(task.right[i]),task.op))
                } else todo.addLast(Work(task.left.size.toLong(),task.right.size.toLong(),task.op))
                continue
            }
            val w=task as Work;val a=w.left;val b=w.right
            if (a is List<*> && b is List<*>) { list(a,b,w.op);continue }
            if (a is Sequence && b is Sequence) {
                if (w.op == Op.Equal && a.values.size != b.values.size) return 0
                list(a.values,b.values,w.op);continue
            }
            val result: Int = when {
                a is Target && b is Target -> {
                    @Suppress("UNCHECKED_CAST") val x=a.value as A
                    @Suppress("UNCHECKED_CAST") val y=b.value as A
                    when (w.op) {
                        Op.Compare -> target.compare(x,y).compareTo(0)
                        Op.Equal -> if (target.equal(x,y)) 1 else 0
                        Op.Less -> if (target.less(x,y)) 1 else 0
                        Op.LessEqual -> if (target.lessEqual(x,y)) 1 else 0
                        Op.Greater -> if (target.greater(x,y)) 1 else 0
                        Op.GreaterEqual -> if (target.greaterEqual(x,y)) 1 else 0
                    }
                }
                a is Long && b is Long -> relation(a.compareTo(b),w.op)
                a is Boolean && b is Boolean -> relation(a.compareTo(b),w.op)
                a is String && b is String -> relation(compareText(a,b),w.op)
                else -> {
                    val x=shape(a);val y=shape(b)
                    if (x.tag != y.tag) relation(x.tag.compareTo(y.tag),w.op)
                    else { fields(x.fields,y.fields,w.op);continue }
                }
            }
            when (w.op) {
                Op.Compare -> if (result != 0) return relation(result,requested)
                Op.Equal -> if (result == 0) return 0
                else -> return result
            }
        }
        return relation(0,requested)
    }
    companion object {
        val Text = TemplateComparison(TargetComparison.Text)
        val String = TemplateComparison(TargetComparison.String)
        val Int64 = TemplateComparison(TargetComparison.Int64)
        val Double = TemplateComparison(TargetComparison.Double)
    }
}
