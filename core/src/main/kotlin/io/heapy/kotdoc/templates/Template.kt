// Text.DocTemplates.Internal 0.11.1, Copyright (C) 2009-2019 John MacFarlane.
// BSD-3-Clause; see licenses/Doctemplates-BSD-3-Clause.txt.
package io.heapy.kotdoc.templates

import io.heapy.kotdoc.ast.UnicodeTextOrder
import io.heapy.kotdoc.layout.Doc
import io.heapy.kotdoc.layout.forEachValue
import io.heapy.kotdoc.layout.mapValues
import java.util.ArrayDeque
import java.util.Collections

enum class Alignment { LeftAligned, Centered, RightAligned }
data class Border(val left: String = "", val right: String = "") : Comparable<Border> {
    override fun compareTo(other: Border): Int = UnicodeTextOrder.compare(left, other.left).takeIf { it != 0 }
        ?: UnicodeTextOrder.compare(right, other.right)
}
sealed interface Pipe : Comparable<Pipe> {
    data object ToPairs : Pipe
    data object ToUppercase : Pipe
    data object ToLowercase : Pipe
    data object ToLength : Pipe
    data object Reverse : Pipe
    data object FirstItem : Pipe
    data object LastItem : Pipe
    data object Rest : Pipe
    data object AllButLast : Pipe
    data object Chomp : Pipe
    data object ToAlpha : Pipe
    data object ToRoman : Pipe
    data object NoWrap : Pipe
    data class Block(val alignment: Alignment, val width: Long, val border: Border = Border()) : Pipe

    private val constructor: Int get() = when (this) {
        ToPairs -> 0; ToUppercase -> 1; ToLowercase -> 2; ToLength -> 3; Reverse -> 4
        FirstItem -> 5; LastItem -> 6; Rest -> 7; AllButLast -> 8; Chomp -> 9
        ToAlpha -> 10; ToRoman -> 11; NoWrap -> 12; is Block -> 13
    }
    override fun compareTo(other: Pipe): Int {
        val tag = constructor.compareTo(other.constructor)
        if (tag != 0 || this !is Block || other !is Block) return tag
        return alignment.compareTo(other.alignment).takeIf { it != 0 }
            ?: width.compareTo(other.width).takeIf { it != 0 } ?: border.compareTo(other.border)
    }
}
class Variable(parts: List<String> = emptyList(), pipes: List<Pipe> = emptyList()) : Comparable<Variable> {
    val parts: List<String> = Collections.unmodifiableList(parts.toList())
    val pipes: List<Pipe> = Collections.unmodifiableList(pipes.toList())
    operator fun plus(other: Variable) = Variable(parts + other.parts, pipes + other.pipes)
    override fun equals(other: Any?) = other is Variable && parts == other.parts && pipes == other.pipes
    override fun hashCode() = 31 * parts.hashCode() + pipes.hashCode()
    override fun toString() = "Variable($parts, $pipes)"
    override fun compareTo(other: Variable) = compareLists(parts, other.parts, UnicodeTextOrder).takeIf { it != 0 }
        ?: compareLists(pipes, other.pipes, Comparator.naturalOrder())
}
private fun <A> compareLists(a: List<A>, b: List<A>, order: Comparator<A>): Int {
    for (i in 0..<minOf(a.size, b.size)) order.compare(a[i], b[i]).let { if (it != 0) return it }
    return a.size.compareTo(b.size)
}

sealed interface Template<out A> {
    data class Interpolate(val variable: Variable) : Template<Nothing>
    data class Conditional<A>(val variable: Variable, val yes: Template<A>, val no: Template<A>) : Template<A>
    data class Iterate<A>(val variable: Variable, val body: Template<A>, val separator: Template<A>) : Template<A>
    data class Nested<A>(val template: Template<A>) : Template<A>
    class Partial<out A>(pipes: List<Pipe>, val template: Template<A>) : Template<A> {
        val pipes: List<Pipe> = Collections.unmodifiableList(pipes.toList())
        override fun equals(other: Any?) = other is Partial<*> && pipes == other.pipes && template == other.template
        override fun hashCode() = 31 * pipes.hashCode() + template.hashCode()
    }
    data class Literal<A>(val doc: Doc<A>) : Template<A>
    data class Concat<A>(val left: Template<A>, val right: Template<A>) : Template<A>
    data object Empty : Template<Nothing>
}
operator fun <A> Template<A>.plus(other: Template<A>): Template<A> = when {
    other === Template.Empty -> this
    this === Template.Empty -> other
    else -> Template.Concat(this, other)
}

/** Free variables in first-occurrence order; a loop binds only its body. */
fun Template<*>.getVariables(): List<String> {
    data class Bound(val parts: List<String>, val parent: Bound?)
    data class Frame(val template: Template<*>, val bound: Bound?)
    val todo = ArrayDeque<Frame>(); todo.push(Frame(this, null))
    val names = linkedSetOf<String>()
    fun add(v: Variable, bound: Bound?) {
        var b = bound
        while (b != null) {
            if (v.parts.take(1) == listOf("it") || v.parts.startsWith(b.parts)) return
            b = b.parent
        }
        names.add(v.parts.joinToString("."))
    }
    while (todo.isNotEmpty()) {
        val (t, bound) = todo.pop()
        when (t) {
            is Template.Interpolate -> add(t.variable, bound)
            is Template.Conditional -> {
                add(t.variable, bound); todo.push(Frame(t.no, bound)); todo.push(Frame(t.yes, bound))
            }
            is Template.Iterate -> {
                add(t.variable, bound); todo.push(Frame(t.separator, bound))
                todo.push(Frame(t.body, Bound(t.variable.parts, bound)))
            }
            is Template.Nested -> todo.push(Frame(t.template, bound))
            is Template.Partial -> todo.push(Frame(t.template, bound))
            is Template.Concat -> { todo.push(Frame(t.right, bound)); todo.push(Frame(t.left, bound)) }
            else -> Unit
        }
    }
    return names.toList()
}
private fun <A> List<A>.startsWith(prefix: List<A>) = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

fun <A, B> Template<A>.mapValues(f: (A) -> B): Template<B> {
    data class Frame<A>(val template: Template<A>, val finish: Boolean = false)
    val todo = ArrayDeque<Frame<A>>(); val done = ArrayDeque<Template<B>>()
    todo.push(Frame(this))
    while (todo.isNotEmpty()) {
        val (t, finish) = todo.pop()
        if (!finish) when (t) {
            is Template.Concat -> { todo.push(Frame(t, true)); todo.push(Frame(t.right)); todo.push(Frame(t.left)); continue }
            is Template.Conditional -> { todo.push(Frame(t, true)); todo.push(Frame(t.no)); todo.push(Frame(t.yes)); continue }
            is Template.Iterate -> { todo.push(Frame(t, true)); todo.push(Frame(t.separator)); todo.push(Frame(t.body)); continue }
            is Template.Nested -> { todo.push(Frame(t, true)); todo.push(Frame(t.template)); continue }
            is Template.Partial -> { todo.push(Frame(t, true)); todo.push(Frame(t.template)); continue }
            else -> Unit
        }
        done.push(when (t) {
            is Template.Literal -> Template.Literal(t.doc.mapValues(f))
            is Template.Interpolate -> t
            is Template.Concat -> { val right = done.pop(); Template.Concat(done.pop(), right) }
            is Template.Conditional -> { val no = done.pop(); Template.Conditional(t.variable, done.pop(), no) }
            is Template.Iterate -> { val sep = done.pop(); Template.Iterate(t.variable, done.pop(), sep) }
            is Template.Nested -> Template.Nested(done.pop())
            is Template.Partial -> Template.Partial(t.pipes, done.pop())
            Template.Empty -> Template.Empty
        })
    }
    return done.pop()
}
fun <A> Template<A>.forEachValue(f: (A) -> Unit) {
    val todo = ArrayDeque<Template<A>>(); todo.push(this)
    while (todo.isNotEmpty()) when (val t = todo.pop()) {
        is Template.Literal -> t.doc.forEachValue(f)
        is Template.Concat -> { todo.push(t.right); todo.push(t.left) }
        is Template.Conditional -> { todo.push(t.no); todo.push(t.yes) }
        is Template.Iterate -> { todo.push(t.separator); todo.push(t.body) }
        is Template.Nested -> todo.push(t.template)
        is Template.Partial -> todo.push(t.template)
        else -> Unit
    }
}
fun <A, B> Template<A>.foldValues(initial: B, f: (B, A) -> B): B {
    var result = initial
    forEachValue { result = f(result, it) }
    return result
}
