// Derived Show of Text.DocTemplates.Internal 0.11.1 and Text.DocLayout 0.6.
// Copyright (C) 2009-2024 John MacFarlane. BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.templates

import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.layout.*

/** Original constructor syntax; the target supplies its own showsPrec instance. */
class TemplateShow<A>(private val showTarget: (A, Int) -> String) {
    fun template(value: Template<A>, precedence: Int = 0) = show(value, precedence)
    fun context(value: Context<A>, precedence: Int = 0) = show(value, precedence)
    fun value(value: Val<A>, precedence: Int = 0) = show(value, precedence)
    fun doc(value: Doc<A>, precedence: Int = 0) = show(value, precedence)
    fun attributed(value: Attributed<A>, precedence: Int = 0) = show(value, precedence)
    fun part(value: AttributedPart<A>, precedence: Int = 0) = show(value, precedence)
    fun variable(value: Variable, precedence: Int = 0) = show(value, precedence)
    fun pipe(value: Pipe, precedence: Int = 0) = show(value, precedence)
    fun border(value: Border, precedence: Int = 0) = show(value, precedence)
    fun font(value: Font, precedence: Int = 0) = show(value, precedence)
    fun style(value: StyleRequest, precedence: Int = 0) = show(value, precedence)

    private class Work(val value: Any?, val precedence: Int = 0)
    private class Target(val value: Any?)
    private class Maybe(val value: Any?)
    private class MapValue(val entries: List<Pair<String, Any?>>)
    private class Tuple(val values: List<Any?>)
    private class Sequence(val values: List<Any?>)

    /** Tasks keep deep Concat/Nested/MapVal chains off the JVM call stack. */
    private fun show(value: Any, precedence: Int): String = buildString {
        val todo = ArrayDeque<Any>()
        todo.addLast(Work(value, precedence))
        fun schedule(parts: List<Any>) { parts.asReversed().forEach(todo::addLast) }
        fun application(name: String, p: Int, vararg fields: Any?) {
            val parts = mutableListOf<Any>()
            if (p > 10) parts.add("(")
            parts.add(name)
            for (field in fields) { parts.add(" "); parts.add(Work(field, 11)) }
            if (p > 10) parts.add(")")
            schedule(parts)
        }
        fun record(name: String, p: Int, vararg fields: Pair<String, Any?>) {
            val parts = mutableListOf<Any>()
            if (p > 10) parts.add("(")
            parts.add("$name {")
            fields.forEachIndexed { i, (key, field) ->
                if (i != 0) parts.add(", ")
                parts.add("$key = "); parts.add(Work(field))
            }
            parts.add("}")
            if (p > 10) parts.add(")")
            schedule(parts)
        }
        fun list(xs: List<Any?>, start: String = "[", end: String = "]") {
            val parts = mutableListOf<Any>(start)
            xs.forEachIndexed { i, x -> if (i != 0) parts.add(","); parts.add(Work(x)) }
            parts.add(end); schedule(parts)
        }
        while (todo.isNotEmpty()) when (val task = todo.removeLast()) {
            is String -> append(task)
            is Work -> {
                val p = task.precedence
                when (val v = task.value) {
                    is Target -> {
                        @Suppress("UNCHECKED_CAST")
                        append(showTarget(v.value as A, p))
                    }
                    is String -> append(HaskellString.quote(TextChars.fromString(v)))
                    is Long -> { if (v < 0 && p > 6) append("($v)") else append(v) }
                    is Boolean -> append(if (v) "True" else "False")
                    is Maybe -> if (v.value == null) append("Nothing") else application("Just", p, v.value)
                    is List<*> -> list(v)
                    is Tuple -> list(v.values, "(", ")")
                    is Sequence -> application("fromList", p, v.values)
                    is MapValue -> application("fromList", p, v.entries.map { Tuple(listOf(it.first, it.second)) })
                    is Template.Interpolate -> application("Interpolate", p, v.variable)
                    is Template.Conditional<*> -> application("Conditional", p, v.variable, v.yes, v.no)
                    is Template.Iterate<*> -> application("Iterate", p, v.variable, v.body, v.separator)
                    is Template.Nested<*> -> application("Nested", p, v.template)
                    is Template.Partial<*> -> application("Partial", p, v.pipes, v.template)
                    is Template.Literal<*> -> application("Literal", p, v.doc)
                    is Template.Concat<*> -> application("Concat", p, v.left, v.right)
                    Template.Empty -> append("Empty")
                    is Variable -> record("Variable", p, "varParts" to v.parts, "varPipes" to v.pipes)
                    is Border -> record("Border", p, "borderLeft" to v.left, "borderRight" to v.right)
                    is Pipe.Block -> application("Block", p, v.alignment, v.width, v.border)
                    Pipe.ToPairs -> append("ToPairs")
                    Pipe.ToUppercase -> append("ToUppercase")
                    Pipe.ToLowercase -> append("ToLowercase")
                    Pipe.ToLength -> append("ToLength")
                    Pipe.Reverse -> append("Reverse")
                    Pipe.FirstItem -> append("FirstItem")
                    Pipe.LastItem -> append("LastItem")
                    Pipe.Rest -> append("Rest")
                    Pipe.AllButLast -> append("AllButLast")
                    Pipe.Chomp -> append("Chomp")
                    Pipe.ToAlpha -> append("ToAlpha")
                    Pipe.ToRoman -> append("ToRoman")
                    Pipe.NoWrap -> append("NoWrap")
                    is Alignment -> append(v.name)
                    is Context<*> -> record("Context", p, "unContext" to MapValue(v.entries.entries.map { it.key to it.value }))
                    is Val.SimpleVal<*> -> application("SimpleVal", p, v.doc)
                    is Val.ListVal<*> -> application("ListVal", p, v.values)
                    is Val.MapVal<*> -> application("MapVal", p, v.context)
                    is Val.BoolVal -> application("BoolVal", p, v.value)
                    Val.NullVal -> append("NullVal")
                    is Doc.Text<*> -> application("Text", p, v.width, Target(v.value))
                    is Doc.Block<*> -> application("Block", p, v.width, v.lines)
                    is Doc.VFill<*> -> application("VFill", p, v.width, Target(v.value))
                    is Doc.CookedText<*> -> application("CookedText", p, v.width, v.value)
                    is Doc.Prefixed<*> -> application("Prefixed", p, v.prefix, v.doc)
                    is Doc.BeforeNonBlank<*> -> application("BeforeNonBlank", p, v.doc)
                    is Doc.Flush<*> -> application("Flush", p, v.doc)
                    Doc.BreakingSpace -> append("BreakingSpace")
                    is Doc.AfterBreak -> application("AfterBreak", p, v.value)
                    Doc.CarriageReturn -> append("CarriageReturn")
                    Doc.NewLine -> append("NewLine")
                    is Doc.BlankLines -> application("BlankLines", p, v.count)
                    is Doc.Concat<*> -> application("Concat", p, v.left, v.right)
                    is Doc.Styled<*> -> application("Styled", p, v.style, v.doc)
                    is Doc.Linked<*> -> application("Linked", p, v.link, v.doc)
                    Doc.Empty -> append("Empty")
                    is Attributed<*> -> application("Attributed", p, Sequence(v.parts))
                    is AttributedPart<*> -> application("Attr", p, Maybe(v.link), v.font, Target(v.value))
                    is Font -> {
                        val weight = if (v.bold) "Bold" else "Normal"
                        val shape = if (v.italic) "Italic" else "Roman"
                        val strike = if (v.strikeout) "Struck" else "Unstruck"
                        val fields = listOf("ftWeight = $weight", "ftShape = $shape", "ftUnderline = ${underline(v.underline)}", "ftStrikeout = $strike",
                            "ftForeground = ${color(v.foreground, true)}", "ftBackground = ${color(v.background, false)}")
                        val parts = mutableListOf<Any>()
                        if (p > 10) parts.add("(")
                        parts.add("Font {" + fields.joinToString(", ") + ", ftLink = ")
                        parts.add(Work(Maybe(v.link))); parts.add("}")
                        if (p > 10) parts.add(")")
                        schedule(parts)
                    }
                    is StyleRequest -> {
                        val (name, text) = when (v) {
                            is StyleRequest.Weight -> "RWeight" to if (v.bold) "Bold" else "Normal"
                            is StyleRequest.Shape -> "RShape" to if (v.italic) "Italic" else "Roman"
                            is StyleRequest.Strikeout -> "RStrikeout" to if (v.struck) "Struck" else "Unstruck"
                            is StyleRequest.Underlined -> "RUnderline" to underline(v.underline)
                            is StyleRequest.Foreground -> "RForeground" to color(v.color, true, 11)
                            is StyleRequest.Background -> "RBackground" to color(v.color, false, 11)
                        }
                        if (p > 10) append('(')
                        append(name).append(' ').append(text)
                        if (p > 10) append(')')
                    }
                    else -> error("Unsupported Show value: ${v?.javaClass}")
                }
            }
        }
    }
    companion object {
        val Text = TemplateShow<String> { value, _ -> HaskellString.quote(TextChars.fromString(value)) }
        val String = TemplateShow<CharacterString> { value, _ -> HaskellString.quoteCodePoints(value.points()) }
        private fun underline(value: Underline) = when (value) {
            Underline.None -> "ULNone"; Underline.Single -> "ULSingle"; Underline.Double -> "ULDouble"; Underline.Curly -> "ULCurly"
        }
        private fun color(value: Color8?, foreground: Boolean, p: Int = 0): kotlin.String {
            val name = if (foreground) "FG" else "BG"
            return if (value == null) name + "Default" else "$name ${value.name}".let { if (p > 10) "($it)" else it }
        }
    }
}
