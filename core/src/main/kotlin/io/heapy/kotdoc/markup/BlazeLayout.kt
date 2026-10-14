// Text.Pandoc.Writers.Blaze 3.12.1, Copyright (C) 2021-2024 John MacFarlane.
// GPL-2.0-or-later; licenses/pandoc-GPL-2.0.md.
package io.heapy.kotdoc.markup

import io.heapy.kotdoc.layout.*

/** Original wrapping rules operate on the structural markup tree. */
object BlazeLayout {
    private val d = DocLayout.Text
    private class Attr(val key: ChoiceString, val value: ChoiceString, val raw: StaticString?, val wrap: Boolean, val next: Attr?)
    private sealed interface Plan {
        class Atom(val value: () -> Doc<String>) : Plan
        class Join(val left: Plan, val right: Plan) : Plan
        class Deferred(val value: () -> Plan) : Plan
        class Flush(val value: Plan) : Plan
    }
    private class Work(val plan: Plan, val finish: Boolean = false)
    private val empty = Plan.Atom { Doc.Empty }
    private fun atom(f: () -> Doc<String>) = Plan.Atom(f)
    private fun literal(s: String) = atom { d.literal(s) }
    private fun char(c: Int) = atom { d.char(c) }
    private fun space(wrap: Boolean) = atom { if (wrap) d.space else d.char(32) }
    private fun cat(vararg parts: Plan): Plan = parts.asList().asReversed().fold(empty as Plan) { tail, part -> Plan.Join(part, tail) }
    private fun attrs(a: Attr?): Plan = atom {
        // In the pinned optimized native build, quoted values are forced while
        // accumulating attributes (innermost first); keys are forced while the
        // accumulated Doc spine is built (outermost first). Both orders matter
        // when several keys/values contain invalid UTF-8.
        val attributes = mutableListOf<Attr>()
        val values = mutableListOf<Doc<String>>()
        var current = a
        while (current != null) {
            val x = current
            attributes.add(x)
            values.add(evaluate(cat(char(34), choice(false, x.value), char(34))))
            current = x.next
        }
        var result: Doc<String> = Doc.Empty
        for (i in attributes.indices.reversed()) {
            val x = attributes[i]
            val key = x.raw?.let { d.literal(it.text) } ?: evaluate(choice(x.wrap, x.key))
            result = listOf(if (x.wrap) d.space else d.char(32), key, d.char(61), values[i]).asReversed()
                .fold(result) { rest, part -> part + rest }
        }
        result
    }
    private fun markup(n: MarkupM<*>, wrap: Boolean, attributes: Attr?): Plan = Plan.Deferred {
        when (n) {
            is MarkupM.Deferred -> markup(n.node.value, wrap, attributes)
            is MarkupM.AddAttribute -> markup(n.content, wrap, Attr(ChoiceString.Static(n.rawKey), n.value, n.rawKey, wrap, attributes))
            is MarkupM.AddCustomAttribute -> markup(n.content, wrap, Attr(n.key, n.value, null, wrap, attributes))
            is MarkupM.Parent -> cat(
                atom { d.literal(n.open.text) }, attrs(attributes), char(62),
                Plan.Deferred {
                    when (n.open.text) {
                        "<code" -> markup(n.content, false, null)
                        "<pre", "<style", "<script", "<textarea" -> Plan.Flush(markup(n.content, false, null))
                        else -> markup(n.content, wrap, null)
                    }
                }, atom { d.literal(n.close.text) },
            )
            is MarkupM.CustomParent -> cat(char(60), choice(wrap, n.tag), attrs(attributes), char(62), markup(n.content, wrap, null), literal("</"), choice(wrap, n.tag), char(62))
            is MarkupM.Leaf -> cat(atom { d.literal(n.open.text) }, attrs(attributes), atom { d.literal(n.close.text) })
            is MarkupM.CustomLeaf -> cat(char(60), choice(wrap, n.tag), attrs(attributes), literal(if (n.close) " />" else ">"))
            is MarkupM.Content -> choice(wrap, n.content)
            is MarkupM.Comment -> cat(literal("<!--"), space(wrap), choice(false, n.comment), space(wrap), literal("-->"))
            is MarkupM.Append -> Plan.Join(markup(n.left, wrap, attributes), markup(n.right, wrap, attributes))
            is MarkupM.Empty -> empty
        }
    }
    private fun choice(wrap: Boolean, input: ChoiceString): Plan = Plan.Deferred {
        val s = input.force()
        fun text(s: ChoiceString.String) = TextChars.fromPoints(s.value.points())
        fun contains(s: ChoiceString): Boolean = when (s) {
            is ChoiceString.String -> s.value.points().let { a -> (0..<a.size - 1).any { a[it] == 60 && a[it + 1] == 47 } }
            is ChoiceString.Text -> s.value.contains("</")
            is ChoiceString.Bytes -> s.rawBytes.let { a -> (0..<a.size - 1).any { a[it] == 60.toByte() && a[it + 1] == 47.toByte() } }
            else -> false
        }
        when (s) {
            is ChoiceString.Static -> atom { withWrap(wrap, s.value.text) }
            is ChoiceString.String -> atom { withWrap(wrap, BlazeRenderer.escapeText(text(s))) }
            is ChoiceString.Text -> atom { withWrap(wrap, BlazeRenderer.escapeText(s.value)) }
            is ChoiceString.Bytes -> atom { withWrap(wrap, BlazeRenderer.decodeUtf8(s.rawBytes)) }
            is ChoiceString.PreEscaped -> when (val x = s.value.force()) {
                is ChoiceString.String -> atom { d.literal(text(x)) }
                is ChoiceString.Text -> atom { d.literal(x.value) }
                else -> choice(false, x)
            }
            is ChoiceString.External -> when (val x = s.value.force()) {
                is ChoiceString.String -> if (contains(x)) empty else atom { withWrap(wrap, text(x)) }
                is ChoiceString.Text -> if (contains(x)) empty else atom { withWrap(wrap, x.value) }
                is ChoiceString.Bytes -> if (contains(x)) empty else atom { withWrap(wrap, BlazeRenderer.decodeUtf8(x.rawBytes)) }
                else -> choice(wrap, x)
            }
            is ChoiceString.Append -> Plan.Join(choice(wrap, s.left), choice(wrap, s.right))
            ChoiceString.Empty -> empty
            is ChoiceString.Deferred -> error("Unresolved choice thunk")
        }
    }
    /** Doc's first (<>) clause checks its right operand for Empty. Evaluate
     * plans in that order, including decoder failures, without JVM recursion. */
    private fun evaluate(root: Plan): Doc<String> {
        val todo = ArrayDeque<Work>()
        val done = ArrayDeque<Doc<String>>()
        todo.addLast(Work(root))
        while (todo.isNotEmpty()) {
            val w = todo.removeLast()
            when (val p = w.plan) {
                is Plan.Atom -> done.addLast(p.value())
                is Plan.Deferred -> todo.addLast(Work(p.value()))
                is Plan.Join -> if (w.finish) {
                    val left = done.removeLast()
                    val right = done.removeLast()
                    done.addLast(left + right)
                } else {
                    todo.addLast(Work(p, true))
                    todo.addLast(Work(p.left))
                    todo.addLast(Work(p.right))
                }
                is Plan.Flush -> if (w.finish) done.addLast(d.flush(done.removeLast())) else {
                    todo.addLast(Work(p, true))
                    todo.addLast(Work(p.value))
                }
            }
        }
        return done.removeLast()
    }
    fun layoutMarkup(markup: MarkupM<*>): Doc<String> = evaluate(markup(markup, true, null))
    fun fromChoiceString(wrap: Boolean, value: ChoiceString): Doc<String> = evaluate(choice(wrap, value))
    private fun withWrap(wrap: Boolean, text: String): Doc<String> {
        if (!wrap) return d.literal(text)
        val chunks = mutableListOf<Doc<String>>()
        var start = 0
        fun category(c: Char) = when (c) { ' ' -> 1; '\n' -> 2; else -> 0 }
        while (start < text.length) {
            var end = start + 1
            while (end < text.length && category(text[end]) == category(text[start])) end++
            val chunk = text.substring(start, end)
            chunks.add(when (chunk) { " " -> d.space; "\n" -> d.cr; else -> d.literal(chunk) })
            start = end
        }
        return chunks.asReversed().fold(Doc.Empty as Doc<String>) { tail, x -> x + tail }
    }
}
