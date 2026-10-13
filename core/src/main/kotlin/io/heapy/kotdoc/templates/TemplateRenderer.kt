// Text.DocTemplates.Internal 0.11.1, Copyright (C) 2009-2019 John MacFarlane.
// BSD-3-Clause; see licenses/Doctemplates-BSD-3-Clause.txt.
package io.heapy.kotdoc.templates

import io.heapy.kotdoc.ast.UnicodeText
import io.heapy.kotdoc.layout.*
import java.util.ArrayDeque

class TemplateRenderer<A : Any>(val values: TemplateValues<A>) {
    private val layout get() = values.layout
    private fun literal(text: String) = layout.literal(values.fromText(text))
    private fun renderedText(doc: Doc<A>) = values.toText(layout.render(null, doc))

    fun applyPipes(pipes: List<Pipe>, value: Val<A>): Val<A> = pipes.fold(value) { v, pipe -> applyPipe(pipe, v) }
    fun applyPipe(pipe: Pipe, value: Val<A>): Val<A> = when (pipe) {
        Pipe.ToLength -> Val.SimpleVal(literal(when (value) {
            is Val.SimpleVal -> renderedText(value.doc).let { it.codePointCount(0, it.length) }.toLong()
            is Val.MapVal -> value.context.entries.size.toLong()
            is Val.ListVal -> value.values.size.toLong()
            else -> 0L
        }.toString()))
        Pipe.ToUppercase -> value.mapValues { values.fromText(UnicodeText.toUpper(values.toText(it))) }
        Pipe.ToLowercase -> value.mapValues { values.fromText(UnicodeText.toLower(values.toText(it))) }
        Pipe.ToPairs -> {
            fun pair(key: String, v: Val<A>) = Val.MapVal(Context(mapOf("key" to Val.SimpleVal(literal(key)), "value" to v)))
            when (value) {
                is Val.MapVal -> Val.ListVal(value.context.entries.map { pair(it.key, it.value) })
                is Val.ListVal -> Val.ListVal(value.values.mapIndexed { i, v -> pair((i.toLong() + 1).toString(), v) })
                else -> value
            }
        }
        Pipe.FirstItem -> if (value is Val.ListVal && value.values.isNotEmpty()) value.values.first() else value
        Pipe.LastItem -> if (value is Val.ListVal && value.values.isNotEmpty()) value.values.last() else value
        Pipe.Rest -> if (value is Val.ListVal && value.values.isNotEmpty()) Val.ListVal(value.values.drop(1)) else value
        Pipe.AllButLast -> if (value is Val.ListVal && value.values.isNotEmpty()) Val.ListVal(value.values.dropLast(1)) else value
        Pipe.Reverse -> when (value) {
            is Val.ListVal -> Val.ListVal(value.values.reversed())
            is Val.SimpleVal -> Val.SimpleVal(literal(buildString {
                renderedText(value.doc).codePoints().toArray().reversed().forEach(::appendCodePoint)
            }))
            else -> value
        }
        Pipe.Chomp -> value.mapDocs(layout::chomp)
        Pipe.ToAlpha -> value.mapDocs { d ->
            val n = decimalInt(renderedText(d))
            if (n != null && n > 0) literal(('a'.code + (n - 1) % 26).toInt().toChar().toString()) else d
        }
        Pipe.ToRoman -> value.mapDocs { d -> decimalInt(renderedText(d))?.let(::roman)?.let(::literal) ?: d }
        Pipe.NoWrap -> value.mapDocs(layout::nowrap)
        is Pipe.Block -> {
            val v = if (value === Val.NullVal) Val.SimpleVal(layout.empty) else value
            if (v is Val.SimpleVal) {
                fun border(text: String) = if (text.isEmpty()) layout.empty else layout.vfill(values.fromText(text))
                val block = when (pipe.alignment) {
                    Alignment.LeftAligned -> layout.lblock(pipe.width, v.doc)
                    Alignment.Centered -> layout.cblock(pipe.width, v.doc)
                    Alignment.RightAligned -> layout.rblock(pipe.width, v.doc)
                }
                Val.SimpleVal(border(pipe.border.left) + (block + border(pipe.border.right)))
            } else value
        }
    }

    /** Original State Int order, evaluated with an explicit finite work stack. */
    fun render(template: Template<A>, context: Context<A>): Doc<A> {
        val todo = ArrayDeque<() -> Unit>(); val done = ArrayDeque<Doc<A>>()
        var column = 0L
        fun output(doc: Doc<A>) { column = layout.updateColumn(doc, column); done.push(doc) }
        fun visit(t: Template<A>, ctx: Context<A>) {
            when (t) {
                is Template.Literal -> output(t.doc)
                is Template.Interpolate -> output(layout.hcat(resolve(t.variable, ctx).docs))
                is Template.Conditional -> todo.push { visit(if (resolve(t.variable, ctx).truth) t.yes else t.no, ctx) }
                is Template.Concat -> {
                    todo.push { val right = done.pop(); done.push(done.pop() + right) }
                    todo.push { visit(t.right, ctx) }; todo.push { visit(t.left, ctx) }
                }
                is Template.Nested -> {
                    val n = column
                    todo.push { done.push(layout.nest(n, done.pop())) }; todo.push { visit(t.template, ctx) }
                }
                is Template.Partial -> {
                    todo.push {
                        val transformed = applyPipes(t.pipes, Val.SimpleVal(done.pop()))
                        done.push(if (transformed is Val.SimpleVal) transformed.doc else layout.empty)
                    }
                    todo.push { visit(t.template, ctx) }
                }
                is Template.Iterate -> {
                    val selected = lookup(t.variable, ctx)
                    val items = when (selected) {
                        Val.NullVal -> emptyList()
                        is Val.ListVal -> selected.values
                        else -> listOf(selected)
                    }
                    todo.push {
                        val separator = done.pop()
                        val bodies = List(items.size) { done.pop() }.asReversed()
                        val parts = ArrayList<Doc<A>>()
                        bodies.forEachIndexed { i, d -> if (i > 0) parts.add(separator); parts.add(d) }
                        done.push(layout.hcat(parts))
                    }
                    // The separator is evaluated once, after every body, even for an empty loop.
                    todo.push { visit(t.separator, ctx) }
                    items.asReversed().forEach { item -> todo.push { visit(t.body, bind(t.variable, item, ctx)) } }
                }
                Template.Empty -> done.push(layout.empty)
            }
        }
        todo.push { visit(template, context) }
        while (todo.isNotEmpty()) todo.pop().invoke()
        return done.pop()
    }
    fun <B> render(template: Template<A>, input: B, converter: ToContext<A, B>) = render(template, converter.toContext(input))

    data class Resolved<A>(val truth: Boolean, val docs: List<Doc<A>>)
    fun resolve(variable: Variable, context: Context<A>): Resolved<A> {
        val todo = ArrayDeque<Val<A>>(); todo.push(lookup(variable, context))
        var truth = false; val docs = mutableListOf<Doc<A>>()
        while (todo.isNotEmpty()) when (val v = todo.pop()) {
            is Val.ListVal -> v.values.asReversed().forEach(todo::push)
            is Val.SimpleVal -> if (!layout.isEmpty(v.doc)) { truth = true; docs.add(removeFinalNl(v.doc)) }
            is Val.MapVal -> { truth = true; docs.add(literal("true")) }
            is Val.BoolVal -> { truth = truth || v.value; docs.add(literal(if (v.value) "true" else "false")) }
            Val.NullVal -> Unit
        }
        return Resolved(truth, docs)
    }
    private fun lookup(variable: Variable, context: Context<A>): Val<A> {
        var v: Val<A> = Val.MapVal(context)
        for (part in variable.parts) v = if (v is Val.MapVal) v.context[part] ?: Val.NullVal else Val.NullVal
        return applyPipes(variable.pipes, v)
    }
    private fun bind(variable: Variable, value: Val<A>, context: Context<A>): Context<A> {
        val root = Context(context.entries + ("it" to value))
        if (variable.parts.isEmpty()) return root
        val parents = mutableListOf<Pair<Context<A>, String>>()
        var current = root
        for ((i, key) in variable.parts.withIndex()) {
            val old = current[key] ?: return root // M.adjust never creates a missing key.
            if (i == variable.parts.lastIndex) {
                var result = Context(current.entries + (key to value))
                for ((parent, name) in parents.asReversed()) result = Context(parent.entries + (name to Val.MapVal(result)))
                return result
            }
            if (old !is Val.MapVal) return root
            parents.add(current to key); current = old.context
        }
        return root
    }
    private fun removeFinalNl(doc: Doc<A>): Doc<A> {
        val lefts = mutableListOf<Doc<A>>(); var d = doc
        while (d is Doc.Concat) { lefts.add(d.left); d = d.right }
        if (d === Doc.NewLine || d === Doc.CarriageReturn) d = layout.empty
        for (left in lefts.asReversed()) d = left + d
        return d
    }
    companion object {
        val Text = TemplateRenderer(TemplateValues.Text)
        val String = TemplateRenderer(TemplateValues.String)
        private fun decimalInt(text: kotlin.String): Long? {
            if (text.isEmpty() || text.any { it !in '0'..'9' }) return null
            var n = 0L
            for (c in text) n = n * 10 + (c - '0') // Data.Text.Read.decimal at the original Int width.
            return n
        }
        private fun roman(n: Long): kotlin.String? {
            if (n !in 0..3999) return null
            var rest = n
            return buildString {
                for ((value, word) in listOf(1000L to "m", 900L to "cm", 500L to "d", 400L to "cd", 100L to "c",
                    90L to "xc", 50L to "l", 40L to "xl", 10L to "x", 9L to "ix", 5L to "v", 4L to "iv", 1L to "i")) {
                    while (rest >= value) { append(word); rest -= value }
                }
            }
        }
    }
}
