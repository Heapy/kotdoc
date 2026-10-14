// Eq/Ord of pandoc-types 1.23.1.2 Text.Pandoc.Definition.
// Copyright (c) 2006-2023 John MacFarlane. BSD-3-Clause; see licenses/.
// Lists, tuples, Maybe and defaults follow GHC 9.12.2 GHC.Classes / Maybe.
// Copyright 2004 The University Court of the University of Glasgow; BSD-style.
// Map instances: containers 0.7, Copyright (c) Daan Leijen 2002 and
// Andriy Palamarchuk 2008. See the retained original notices in licenses/.
package io.heapy.kotdoc.ast

/** Source dictionaries are separate from Kotlin data-class equality and ordering. */
class AstComparison<A> private constructor() {
    fun equal(left: A, right: A): Boolean = AstOrderEngine.evaluate(left, right, AstOp.Equal) != 0
    fun notEqual(left: A, right: A): Boolean = !equal(left, right)
    fun compare(left: A, right: A): Int = AstOrderEngine.evaluate(left, right, AstOp.Compare)
    fun less(left: A, right: A): Boolean = AstOrderEngine.evaluate(left, right, AstOp.Less) != 0
    fun lessEqual(left: A, right: A): Boolean = AstOrderEngine.evaluate(left, right, AstOp.LessEqual) != 0
    fun greater(left: A, right: A): Boolean = AstOrderEngine.evaluate(left, right, AstOp.Greater) != 0
    fun greaterEqual(left: A, right: A): Boolean = AstOrderEngine.evaluate(left, right, AstOp.GreaterEqual) != 0
    fun minimum(left: A, right: A): A = if (lessEqual(left, right)) left else right
    fun maximum(left: A, right: A): A = if (lessEqual(left, right)) right else left

    companion object {
        val Pandoc = AstComparison<io.heapy.kotdoc.ast.Pandoc>()
        val Meta = AstComparison<Map<String, MetaValue>>()
        val MetaValue = AstComparison<io.heapy.kotdoc.ast.MetaValue>()
        val Inline = AstComparison<io.heapy.kotdoc.ast.Inline>()
        val Block = AstComparison<io.heapy.kotdoc.ast.Block>()
        val Attr = AstComparison<io.heapy.kotdoc.ast.Attr>()
        val Target = AstComparison<io.heapy.kotdoc.ast.Target>()
        val ListAttributes = AstComparison<io.heapy.kotdoc.ast.ListAttributes>()
        val ListNumberStyle = AstComparison<io.heapy.kotdoc.ast.ListNumberStyle>()
        val ListNumberDelim = AstComparison<io.heapy.kotdoc.ast.ListNumberDelim>()
        val Alignment = AstComparison<io.heapy.kotdoc.ast.Alignment>()
        val QuoteType = AstComparison<io.heapy.kotdoc.ast.QuoteType>()
        val MathType = AstComparison<io.heapy.kotdoc.ast.MathType>()
        val CitationMode = AstComparison<io.heapy.kotdoc.ast.CitationMode>()
        val Citation = AstComparison<io.heapy.kotdoc.ast.Citation>()
        val Format = AstComparison<io.heapy.kotdoc.ast.Format>()
        val ColWidth = AstComparison<io.heapy.kotdoc.ast.ColWidth>()
        val ColSpec = AstComparison<io.heapy.kotdoc.ast.ColSpec>()
        val Caption = AstComparison<io.heapy.kotdoc.ast.Caption>()
        val Row = AstComparison<io.heapy.kotdoc.ast.Row>()
        val Cell = AstComparison<io.heapy.kotdoc.ast.Cell>()
        val TableHead = AstComparison<io.heapy.kotdoc.ast.TableHead>()
        val TableBody = AstComparison<io.heapy.kotdoc.ast.TableBody>()
        val TableFoot = AstComparison<io.heapy.kotdoc.ast.TableFoot>()
        val Int64 = AstComparison<Long>()
        val Text = AstComparison<String>()
        val Double = AstComparison<kotlin.Double>()
        // These newtypes have the same Eq/Ord dictionary as their Int payload.
        val RowHeadColumns = AstComparison<Long>()
        val RowSpan = AstComparison<Long>()
        val ColSpan = AstComparison<Long>()
        /** List instances for the supported AST element dictionaries. */
        @Suppress("UNUSED_PARAMETER")
        fun <A> list(element: AstComparison<A>): AstComparison<List<A>> = AstComparison()
    }
}

private enum class AstOp { Equal, Compare, Less, LessEqual, Greater, GreaterEqual }

/** Continuations retain each nested instance's own relational operators. */
private object AstOrderEngine {
    private class Work(val left: Any?, val right: Any?, val op: AstOp)
    private class Predicate(val operation: (Int) -> Int)
    private class Fields(val left: List<Any?>, val right: List<Any?>, val index: Int, val op: AstOp)
    private class FieldsAfter(val fields: Fields)
    private class Lists(val left: Iterator<*>, val right: Iterator<*>, val op: AstOp)
    private class ListAfter(val lists: Lists)
    private class Optional(val value: Any?)
    private class Later(val value: () -> Any?)
    private class Shape(val tag: Int = 0, vararg val fields: Any?)

    private fun shape(v: Any, op: AstOp): Shape = when (v) {
        is Pandoc -> Shape(0, v.meta, v.blocks)
        is MetaValue.MetaMap -> Shape(0, v.values)
        is MetaValue.MetaList -> Shape(1, v.values)
        is MetaValue.MetaBool -> Shape(2, v.value)
        is MetaValue.MetaString -> Shape(3, v.value)
        is MetaValue.MetaInlines -> Shape(4, v.content)
        is MetaValue.MetaBlocks -> Shape(5, v.content)
        is Attr -> Shape(0, v.identifier, v.classes, v.keyValues)
        is Target -> Shape(0, v.url, v.title)
        is ListAttributes -> Shape(0, v.start, v.style, v.delimiter)
        is ColSpec -> Shape(0, v.alignment, v.width)
        is Pair<*, *> -> Shape(0, v.first, v.second)
        is Optional -> if (v.value == null) Shape(0) else Shape(1, v.value)
        is Caption -> Shape(0, Optional(v.short), v.long)
        is ColWidth.Width -> Shape(0, v.value)
        ColWidth.Default -> Shape(1)
        is Row -> Shape(0, v.attr, v.cells)
        is Cell -> Shape(0, v.attr, v.alignment, v.rowSpan, v.colSpan, v.blocks)
        is TableHead -> Shape(0, v.attr, v.rows)
        is TableBody -> Shape(0, v.attr, v.rowHeadColumns, v.head, v.body)
        is TableFoot -> Shape(0, v.attr, v.rows)
        is Citation -> if (op == AstOp.Equal) Shape(0, v.citationId, v.citationPrefix, v.citationSuffix, v.citationMode, v.citationNoteNum, v.citationHash)
            else Shape(0, v.citationHash)
        is Inline.Str -> Shape(0, v.text)
        is Inline.Emph -> Shape(1, v.content)
        is Inline.Underline -> Shape(2, v.content)
        is Inline.Strong -> Shape(3, v.content)
        is Inline.Strikeout -> Shape(4, v.content)
        is Inline.Superscript -> Shape(5, v.content)
        is Inline.Subscript -> Shape(6, v.content)
        is Inline.SmallCaps -> Shape(7, v.content)
        is Inline.Quoted -> Shape(8, v.type, v.content)
        is Inline.Cite -> Shape(9, v.citations, v.content)
        is Inline.Code -> Shape(10, v.attr, v.text)
        Inline.Space -> Shape(11)
        Inline.SoftBreak -> Shape(12)
        Inline.LineBreak -> Shape(13)
        is Inline.Math -> Shape(14, v.type, v.text)
        is Inline.RawInline -> Shape(15, v.format, v.text)
        is Inline.Link -> Shape(16, v.attr, v.content, v.target)
        is Inline.Image -> Shape(17, v.attr, v.content, v.target)
        is Inline.Note -> Shape(18, v.blocks)
        is Inline.Span -> Shape(19, v.attr, v.content)
        is Block.Plain -> Shape(0, v.content)
        is Block.Para -> Shape(1, v.content)
        is Block.LineBlock -> Shape(2, v.lines)
        is Block.CodeBlock -> Shape(3, v.attr, v.text)
        is Block.RawBlock -> Shape(4, v.format, v.text)
        is Block.BlockQuote -> Shape(5, v.blocks)
        is Block.OrderedList -> Shape(6, v.attributes, v.items)
        is Block.BulletList -> Shape(7, v.items)
        is Block.DefinitionList -> Shape(8, v.items)
        is Block.Header -> Shape(9, v.level, v.attr, v.content)
        Block.HorizontalRule -> Shape(10)
        is Block.Table -> Shape(11, v.attr, v.caption, v.columns, v.head, v.bodies, v.foot)
        is Block.Figure -> Shape(12, v.attr, v.caption, v.blocks)
        is Block.Div -> Shape(13, v.attr, v.blocks)
        else -> error("Unsupported AST comparison: ${v.javaClass}")
    }
    private fun compareOnly(v: Any) = v is Inline || v is Block || v is MetaValue ||
        v is List<*> || v is Map<*, *> || v is Citation || v is Format

    private fun relation(sign: Int, op: AstOp): Int = when (op) {
        AstOp.Compare -> sign.compareTo(0)
        AstOp.Equal -> if (sign == 0) 1 else 0
        AstOp.Less -> if (sign < 0) 1 else 0
        AstOp.LessEqual -> if (sign <= 0) 1 else 0
        AstOp.Greater -> if (sign > 0) 1 else 0
        AstOp.GreaterEqual -> if (sign >= 0) 1 else 0
    }
    private fun textCompare(a: String, b: String): Int {
        var i = 0; var j = 0
        while (i < a.length && j < b.length) {
            val x = a.codePointAt(i); val y = b.codePointAt(j)
            val sx = if (x in 0xd800..0xdfff) 0xfffd else x
            val sy = if (y in 0xd800..0xdfff) 0xfffd else y
            if (sx != sy) return sx.compareTo(sy)
            i += Character.charCount(x); j += Character.charCount(y)
        }
        return (a.length - i).compareTo(b.length - j)
    }
    private fun orderedMap(v: Map<*, *>): List<Pair<String, Later>> {
        @Suppress("UNCHECKED_CAST") val m = v as Map<String, *>
        return m.keys.sortedWith(::textCompare).map { key -> key to Later { m.getValue(key) } }
    }

    fun evaluate(left: Any?, right: Any?, requested: AstOp): Int {
        val todo = ArrayDeque<Any>()
        todo.addLast(Work(left, right, requested))
        var result = 0
        while (todo.isNotEmpty()) when (val task = todo.removeLast()) {
            is Predicate -> result = task.operation(result)
            is Fields -> {
                if (task.index == task.left.size) result = relation(0, task.op)
                else {
                    val last = task.index == task.left.lastIndex
                    if (!last) todo.addLast(FieldsAfter(task))
                    todo.addLast(Work(task.left[task.index], task.right[task.index],
                        if (task.op == AstOp.Equal || last) task.op else AstOp.Compare))
                }
            }
            is FieldsAfter -> {
                val f = task.fields
                val keepGoing = if (f.op == AstOp.Equal) result != 0 else result == 0
                if (keepGoing) todo.addLast(Fields(f.left, f.right, f.index + 1, f.op))
                else if (f.op != AstOp.Equal) result = relation(result, f.op)
            }
            is Lists -> {
                val a = task.left.hasNext(); val b = task.right.hasNext()
                if (!a || !b) result = relation(a.compareTo(b), task.op)
                else {
                    todo.addLast(ListAfter(task))
                    todo.addLast(Work(task.left.next(), task.right.next(), task.op))
                }
            }
            is ListAfter -> {
                if (if (task.lists.op == AstOp.Equal) result != 0 else result == 0) todo.addLast(task.lists)
            }
            is Work -> {
                val a = if (task.left is Later) task.left.value() else task.left
                val b = if (task.right is Later) task.right.value() else task.right
                val op = task.op
                check(a != null && b != null) { "AST comparison received null" }
                val atomic = when {
                    a is String && b is String -> relation(textCompare(a, b), op)
                    a is Long && b is Long -> relation(a.compareTo(b), op)
                    a is Boolean && b is Boolean -> relation(a.compareTo(b), op)
                    a is Enum<*> && b is Enum<*> -> relation(a.ordinal.compareTo(b.ordinal), op)
                    a is Format && b is Format -> relation(a.compareTo(b), op)
                    a is Double && b is Double -> when (op) {
                        AstOp.Compare -> if (a < b) -1 else if (a == b) 0 else 1
                        AstOp.Equal -> if (a == b) 1 else 0
                        AstOp.Less -> if (a < b) 1 else 0
                        AstOp.LessEqual -> if (a <= b) 1 else 0
                        AstOp.Greater -> if (a > b) 1 else 0
                        AstOp.GreaterEqual -> if (a >= b) 1 else 0
                    }
                    else -> null
                }
                if (atomic != null) { result = atomic; continue }
                if (op != AstOp.Equal && op != AstOp.Compare && (op != AstOp.Less || compareOnly(a))) {
                    val comparison = compareOnly(a)
                    val reverse = if (comparison) op == AstOp.Less || op == AstOp.GreaterEqual
                        else op == AstOp.LessEqual || op == AstOp.Greater
                    val child = if (comparison) AstOp.Compare else AstOp.Less
                    todo.addLast(Predicate { sign ->
                        if (comparison) {
                            if (op == AstOp.Less || op == AstOp.Greater) if (sign > 0) 1 else 0
                            else if (sign <= 0) 1 else 0
                        } else if (op == AstOp.LessEqual || op == AstOp.GreaterEqual) 1 - sign else sign
                    })
                    todo.addLast(if (reverse) Work(b, a, child) else Work(a, b, child))
                } else if (a is List<*> && b is List<*>) {
                    todo.addLast(Lists(a.iterator(), b.iterator(), op))
                } else if (a is Map<*, *> && b is Map<*, *>) {
                    if (op == AstOp.Equal && a.size != b.size) result = 0
                    else todo.addLast(Work(orderedMap(a), orderedMap(b), op))
                } else {
                    val x = shape(a, op); val y = shape(b, op)
                    if (x.tag != y.tag) result = relation(x.tag.compareTo(y.tag), op)
                    else todo.addLast(Fields(x.fields.toList(), y.fields.toList(), 0, op))
                }
            }
        }
        return result
    }
}
