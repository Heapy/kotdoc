// Derived Doc/Attr/Attributed Functor and Foldable field order, DocLayout 0.6.
// Copyright (C) 2010-2024 John MacFarlane, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.layout

import java.util.ArrayDeque

/** Maps each occurrence; cached widths, prefixes, links and fonts remain unchanged. */
fun <A, B> Doc<A>.mapValues(f: (A) -> B): Doc<B> {
    data class Work<A>(val doc: Doc<A>, val ready: Boolean = false)
    val todo = ArrayDeque<Work<A>>(); val done = ArrayDeque<Doc<B>>()
    todo.push(Work(this))
    fun attributed(a: Attributed<A>) = Attributed(a.parts.map { AttributedPart(it.link, it.font, f(it.value)) })
    while (todo.isNotEmpty()) {
        val (d, ready) = todo.pop()
        if (!ready) when (d) {
            is Doc.Concat -> { todo.push(Work(d, true)); todo.push(Work(d.right)); todo.push(Work(d.left)); continue }
            is Doc.Prefixed -> { todo.push(Work(d, true)); todo.push(Work(d.doc)); continue }
            is Doc.BeforeNonBlank -> { todo.push(Work(d, true)); todo.push(Work(d.doc)); continue }
            is Doc.Flush -> { todo.push(Work(d, true)); todo.push(Work(d.doc)); continue }
            is Doc.Styled -> { todo.push(Work(d, true)); todo.push(Work(d.doc)); continue }
            is Doc.Linked -> { todo.push(Work(d, true)); todo.push(Work(d.doc)); continue }
            else -> Unit
        }
        val value: Doc<B> = when (d) {
            is Doc.Text -> Doc.Text(d.width, f(d.value))
            is Doc.Block -> Doc.Block(d.width, d.lines.map(::attributed))
            is Doc.VFill -> Doc.VFill(d.width, f(d.value))
            is Doc.CookedText -> Doc.CookedText(d.width, attributed(d.value))
            is Doc.Prefixed -> Doc.Prefixed(d.prefix, done.pop())
            is Doc.BeforeNonBlank -> Doc.BeforeNonBlank(done.pop())
            is Doc.Flush -> Doc.Flush(done.pop())
            is Doc.Styled -> Doc.Styled(d.style, done.pop())
            is Doc.Linked -> Doc.Linked(d.link, done.pop())
            is Doc.Concat -> { val right = done.pop(); Doc.Concat(done.pop(), right) }
            Doc.BreakingSpace -> Doc.BreakingSpace; is Doc.AfterBreak -> d
            Doc.CarriageReturn -> Doc.CarriageReturn; Doc.NewLine -> Doc.NewLine
            is Doc.BlankLines -> d; Doc.Empty -> Doc.Empty
        }
        done.push(value)
    }
    return done.pop()
}

fun <A> Doc<A>.forEachValue(f: (A) -> Unit) {
    val todo = ArrayDeque<Doc<A>>(); todo.push(this)
    while (todo.isNotEmpty()) when (val d = todo.pop()) {
        is Doc.Text -> f(d.value); is Doc.VFill -> f(d.value)
        is Doc.Block -> d.lines.forEach { line -> line.parts.forEach { f(it.value) } }
        is Doc.CookedText -> d.value.parts.forEach { f(it.value) }
        is Doc.Concat -> { todo.push(d.right); todo.push(d.left) }
        is Doc.Prefixed -> todo.push(d.doc); is Doc.BeforeNonBlank -> todo.push(d.doc)
        is Doc.Flush -> todo.push(d.doc); is Doc.Styled -> todo.push(d.doc); is Doc.Linked -> todo.push(d.doc)
        else -> Unit
    }
}
fun <A, B> Doc<A>.foldValues(initial: B, f: (B, A) -> B): B {
    var value = initial
    forEachValue { value = f(value, it) }
    return value
}
