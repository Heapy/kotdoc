// Derived Show for Text.Pandoc.TeX, Pandoc 3.12.1.
// Copyright (C) 2017-2024 John MacFarlane; GPL-2.0-or-later.
// Iterative scheduling follows TemplateShow's retained DocTemplates/DocLayout
// structure, (c) 2009-2024 John MacFarlane, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.tex

import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.parsing.*

/** A lazy, iterative showsPrec stream, including finite prefixes of infinite data. */
object TeXShow {
    private class Work(val value: Any?, val precedence: Int = 0)
    private class Text(val value: Deferred<String>)
    private class ListValue(val value: Deferred<DeferredChain<*>>)
    private class ListTail(val value: Deferred<DeferredChain<*>>)
    private class Optional(val value: TokenDefault)
    private class Tuple(val first: Any?, val second: Any?)
    fun show(value: Any, precedence: Int = 0) = chunks(value, precedence).joinToString("")
    fun prefix(value: Any, length: Int, precedence: Int = 0): String {
        if (length <= 0) return ""
        val out = StringBuilder(); val iterator = chunks(value, precedence).iterator()
        var remaining = length
        while (remaining > 0 && iterator.hasNext()) {
            val part = iterator.next(); var at = 0
            while (remaining > 0 && at < part.length) {
                val point = part.codePointAt(at); out.appendCodePoint(point)
                at += Character.charCount(point); remaining--
            }
        }
        return out.toString()
    }
    fun chunks(value: Any, precedence: Int = 0): Sequence<String> = sequence {
        val todo = ArrayDeque<Any>(); todo.addLast(Work(value, precedence))
        fun schedule(parts: List<Any>) { parts.asReversed().forEach(todo::addLast) }
        fun application(name: String, p: Int, vararg fields: Any?) {
            val parts = mutableListOf<Any>()
            if (p > 10) parts.add("(")
            parts.add(name)
            for (field in fields) { parts.add(" "); parts.add(Work(field, 11)) }
            if (p > 10) parts.add(")")
            schedule(parts)
        }
        while (todo.isNotEmpty()) when (val task = todo.removeLast()) {
            is String -> yield(task)
            is ListTail -> when (val chain = task.value.value()) {
                DeferredChain.Nil -> yield("]")
                is DeferredChain.Cons -> { schedule(listOf(",", Work(chain.head), ListTail(chain.tail))) }
            }
            is Work -> {
                val p = task.precedence
                when (val v = task.value) {
                    is Deferred<*> -> todo.addLast(Work(v.value(), p))
                    is Text -> {
                        yield("\"")
                        yield(HaskellString.quote(v.value.value()).drop(1).dropLast(1))
                        yield("\"")
                    }
                    is Long -> yield(if (v < 0 && p > 6) "($v)" else v.toString())
                    is Boolean -> yield(if (v) "True" else "False")
                    is SourcePos -> yield(v.toString())
                    is ExpansionPoint -> yield(v.name)
                    is MacroScope -> yield(v.name)
                    is ListValue -> when (val chain = v.value.value()) {
                        DeferredChain.Nil -> yield("[]")
                        is DeferredChain.Cons -> { schedule(listOf("[", Work(chain.head), ListTail(chain.tail))) }
                    }
                    is Optional -> when (val option = v.value.value()) {
                        TeXMaybe.Nothing -> yield("Nothing")
                        is TeXMaybe.Just -> application("Just", p, ListValue(option.value))
                    }
                    is Tuple -> schedule(listOf("(", Work(v.first), ",", Work(v.second), ")"))
                    is Embellishment -> todo.addLast(Work(Tuple(v.deferredToken, Optional(v.default))))
                    is TokType.CtrlSeq -> application("CtrlSeq", p, Text(v.deferredText.mapValue(io.heapy.kotdoc.layout.TextChars::fromString)))
                    is TokType.Arg -> application("Arg", p, v.deferredNumber)
                    is TokType.DeferredArg -> application("DeferredArg", p, v.deferredNumber)
                    TokType.Spaces -> yield("Spaces")
                    TokType.Newline -> yield("Newline")
                    TokType.Symbol -> yield("Symbol")
                    TokType.Word -> yield("Word")
                    TokType.Comment -> yield("Comment")
                    TokType.Esc1 -> yield("Esc1")
                    TokType.Esc2 -> yield("Esc2")
                    is Tok -> application("Tok", p, v.deferredPosition, v.deferredType, Text(v.deferredText))
                    is Macro -> application("Macro", p, v.deferredScope, v.deferredExpansion, ListValue(v.arguments), Optional(v.default), ListValue(v.replacement))
                    is ArgSpec.ArgNum -> application("ArgNum", p, v.deferredNumber)
                    is ArgSpec.Pattern -> application("Pattern", p, ListValue(v.tokens))
                    is ArgSpec.BoolArg -> application("BoolArg", p, v.deferredSkipSpace, v.deferredToken)
                    is ArgSpec.DelimArg -> application("DelimArg", p, v.deferredSkipSpace, v.deferredOpen, v.deferredClose, Optional(v.default))
                    ArgSpec.VerbArg -> yield("VerbArg")
                    is ArgSpec.EmbellishArg -> application("EmbellishArg", p, ListValue(v.embellishments))
                    is ArgSpec.ProcessedArg -> application("ProcessedArg", p, ProcessorValue(v.processors), v.deferredArgument)
                    is ProcessorValue -> when (val chain = v.value.value()) {
                        DeferredChain.Nil -> yield("[]")
                        is DeferredChain.Cons -> schedule(listOf("[", Work(Deferred.defer { chain.head.mapValue { ListValue(it) } }), ProcessorTail(chain.tail)))
                    }
                    is ArgSpec.BodyArg -> application("BodyArg", p, v.deferredTrim, v.deferredNumber)
                    is ArgSpec.VerbBodyArg -> application("VerbBodyArg", p, v.deferredTrim, v.deferredNumber)
                    else -> error("Unsupported TeX Show value: ${v?.javaClass}")
                }
            }
            is ProcessorTail -> when (val chain = task.value.value()) {
                DeferredChain.Nil -> yield("]")
                is DeferredChain.Cons -> schedule(listOf(",", Work(Deferred.defer { chain.head.mapValue { ListValue(it) } }), ProcessorTail(chain.tail)))
            }
        }
    }
    private class ProcessorValue(val value: ProcessorList)
    private class ProcessorTail(val value: ProcessorList)
}
