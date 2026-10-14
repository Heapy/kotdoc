// Derived Show of pandoc-types 1.23.1.2 Text.Pandoc.Definition.
// Copyright (c) 2006-2023 John MacFarlane. BSD-3-Clause; see licenses/.
// Iterative scheduling follows TemplateShow's DocTemplates/DocLayout structure,
// Copyright (c) 2009-2024 John MacFarlane. BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.ast

import io.heapy.kotdoc.formats.HaskellDouble
import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.layout.TextChars

/** Haskell constructor syntax, including showsPrec; independent of Native layout. */
object AstShow {
    private class Work(val value: Any?, val precedence: Int = 0)
    private class Tuple(val fields: List<Any?>)
    private class Optional(val value: Any?)
    private class Meta(val value: Map<String, MetaValue>)
    private class Count(val name: String, val value: Long)
    private class ListTail(val iterator: Iterator<*>)

    fun show(value: Any, precedence: Int = 0): String = chunks(value, precedence).joinToString("")
    fun meta(value: Map<String, MetaValue>, precedence: Int = 0) = show(Meta(value), precedence)
    fun rowHeadColumns(value: Long, precedence: Int = 0) = show(Count("RowHeadColumns", value), precedence)
    fun rowSpan(value: Long, precedence: Int = 0) = show(Count("RowSpan", value), precedence)
    fun colSpan(value: Long, precedence: Int = 0) = show(Count("ColSpan", value), precedence)

    /** Counts Unicode scalar values, as Haskell's take on the Show string does. */
    fun prefix(value: Any, length: Int, precedence: Int = 0): String {
        if (length <= 0) return ""
        val out = StringBuilder()
        val iterator = chunks(value, precedence).iterator()
        var remaining = length
        while (remaining > 0 && iterator.hasNext()) {
            val part = iterator.next()
            var at = 0
            while (remaining > 0 && at < part.length) {
                val point = part.codePointAt(at)
                out.appendCodePoint(point)
                at += Character.charCount(point)
                remaining--
            }
        }
        return out.toString()
    }

    /** Explicit tasks keep nested AST nodes off the JVM stack and demand lists incrementally. */
    fun chunks(value: Any, precedence: Int = 0): Sequence<String> = sequence {
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
            for ((index, field) in fields.withIndex()) {
                if (index != 0) parts.add(", ")
                parts.add("${field.first} = ")
                parts.add(Work(field.second))
            }
            parts.add("}")
            if (p > 10) parts.add(")")
            schedule(parts)
        }
        fun tuple(vararg fields: Any?) = Tuple(fields.toList())
        while (todo.isNotEmpty()) when (val task = todo.removeLast()) {
            is String -> yield(task)
            is ListTail -> if (task.iterator.hasNext()) {
                // Do not read the next element before its preceding comma is demanded.
                yield(",")
                todo.addLast(task)
                todo.addLast(Work(task.iterator.next()))
            } else yield("]")
            is Work -> {
                val p = task.precedence
                when (val v = task.value) {
                    is String -> yield(HaskellString.quote(TextChars.fromString(v)))
                    is Long -> yield(if (v < 0 && p > 6) "($v)" else v.toString())
                    is Double -> {
                        val text = HaskellDouble.show(v)
                        yield(if (p > 6 && v.toRawBits() < 0 && !v.isNaN()) "($text)" else text)
                    }
                    is Boolean -> yield(if (v) "True" else "False")
                    is Enum<*> -> yield(v.name)
                    is Optional -> if (v.value == null) yield("Nothing") else application("Just", p, v.value)
                    is Count -> application(v.name, p, v.value)
                    is List<*> -> {
                        val iterator = v.iterator()
                        if (!iterator.hasNext()) yield("[]") else {
                            yield("[")
                            todo.addLast(ListTail(iterator))
                            todo.addLast(Work(iterator.next()))
                        }
                    }
                    is Tuple -> {
                        val parts = mutableListOf<Any>("(")
                        for ((index, field) in v.fields.withIndex()) {
                            if (index != 0) parts.add(",")
                            parts.add(Work(field))
                        }
                        parts.add(")")
                        schedule(parts)
                    }
                    is Pair<*, *> -> todo.addLast(Work(tuple(v.first, v.second)))
                    is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        val entries = (v as Map<String, Any?>).entries.sortedWith { a, b ->
                            UnicodeTextOrder.compare(TextChars.fromString(a.key), TextChars.fromString(b.key))
                        }
                        application("fromList", p, entries.map { tuple(it.key, it.value) })
                    }
                    is Meta -> record("Meta", p, "unMeta" to v.value)
                    is Pandoc -> application("Pandoc", p, Meta(v.meta), v.blocks)
                    is MetaValue.MetaMap -> application("MetaMap", p, v.values)
                    is MetaValue.MetaList -> application("MetaList", p, v.values)
                    is MetaValue.MetaBool -> application("MetaBool", p, v.value)
                    is MetaValue.MetaString -> application("MetaString", p, v.value)
                    is MetaValue.MetaInlines -> application("MetaInlines", p, v.content)
                    is MetaValue.MetaBlocks -> application("MetaBlocks", p, v.content)
                    is Attr -> todo.addLast(Work(tuple(v.identifier, v.classes, v.keyValues)))
                    is Target -> todo.addLast(Work(tuple(v.url, v.title)))
                    is ListAttributes -> todo.addLast(Work(tuple(v.start, v.style, v.delimiter)))
                    is ColSpec -> todo.addLast(Work(tuple(v.alignment, v.width)))
                    is Format -> application("Format", p, v.value)
                    is ColWidth.Width -> application("ColWidth", p, v.value)
                    ColWidth.Default -> yield("ColWidthDefault")
                    is Caption -> application("Caption", p, Optional(v.short), v.long)
                    is Row -> application("Row", p, v.attr, v.cells)
                    is Cell -> application("Cell", p, v.attr, v.alignment, Count("RowSpan", v.rowSpan), Count("ColSpan", v.colSpan), v.blocks)
                    is TableHead -> application("TableHead", p, v.attr, v.rows)
                    is TableBody -> application("TableBody", p, v.attr, Count("RowHeadColumns", v.rowHeadColumns), v.head, v.body)
                    is TableFoot -> application("TableFoot", p, v.attr, v.rows)
                    is Citation -> record("Citation", p,
                        "citationId" to v.citationId,
                        "citationPrefix" to v.citationPrefix,
                        "citationSuffix" to v.citationSuffix,
                        "citationMode" to v.citationMode,
                        "citationNoteNum" to v.citationNoteNum,
                        "citationHash" to v.citationHash)
                    is Inline.Str -> application("Str", p, v.text)
                    is Inline.Emph -> application("Emph", p, v.content)
                    is Inline.Underline -> application("Underline", p, v.content)
                    is Inline.Strong -> application("Strong", p, v.content)
                    is Inline.Strikeout -> application("Strikeout", p, v.content)
                    is Inline.Superscript -> application("Superscript", p, v.content)
                    is Inline.Subscript -> application("Subscript", p, v.content)
                    is Inline.SmallCaps -> application("SmallCaps", p, v.content)
                    is Inline.Quoted -> application("Quoted", p, v.type, v.content)
                    is Inline.Cite -> application("Cite", p, v.citations, v.content)
                    is Inline.Code -> application("Code", p, v.attr, v.text)
                    Inline.Space -> yield("Space")
                    Inline.SoftBreak -> yield("SoftBreak")
                    Inline.LineBreak -> yield("LineBreak")
                    is Inline.Math -> application("Math", p, v.type, v.text)
                    is Inline.RawInline -> application("RawInline", p, v.format, v.text)
                    is Inline.Link -> application("Link", p, v.attr, v.content, v.target)
                    is Inline.Image -> application("Image", p, v.attr, v.content, v.target)
                    is Inline.Note -> application("Note", p, v.blocks)
                    is Inline.Span -> application("Span", p, v.attr, v.content)
                    is Block.Plain -> application("Plain", p, v.content)
                    is Block.Para -> application("Para", p, v.content)
                    is Block.LineBlock -> application("LineBlock", p, v.lines)
                    is Block.CodeBlock -> application("CodeBlock", p, v.attr, v.text)
                    is Block.RawBlock -> application("RawBlock", p, v.format, v.text)
                    is Block.BlockQuote -> application("BlockQuote", p, v.blocks)
                    is Block.OrderedList -> application("OrderedList", p, v.attributes, v.items)
                    is Block.BulletList -> application("BulletList", p, v.items)
                    is Block.DefinitionList -> application("DefinitionList", p, v.items)
                    is Block.Header -> application("Header", p, v.level, v.attr, v.content)
                    Block.HorizontalRule -> yield("HorizontalRule")
                    is Block.Table -> application("Table", p, v.attr, v.caption, v.columns, v.head, v.bodies, v.foot)
                    is Block.Figure -> application("Figure", p, v.attr, v.caption, v.blocks)
                    is Block.Div -> application("Div", p, v.attr, v.blocks)
                    else -> error("Unsupported AST Show value: ${v?.javaClass}")
                }
            }
        }
    }
}
