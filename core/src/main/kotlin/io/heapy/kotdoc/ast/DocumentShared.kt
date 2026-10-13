// Ports of Text.Pandoc.Shared and Text.Pandoc.Writers.Shared.
// Copyright (C) 2006-2024 John MacFarlane. GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.ast

import com.ibm.icu.lang.UCharacter
import java.util.Locale

object DocumentShared {
    fun stringifyInlines(inlines: List<Inline>): String = buildString {
        fun go(inline: Inline) {
            when (inline) {
                is Inline.Str -> append(inline.text)
                is Inline.Code -> append(inline.text)
                is Inline.Math -> append(inline.text)
                Inline.Space, Inline.SoftBreak, Inline.LineBreak -> append(' ')
                is Inline.RawInline -> if (inline.format.value == "html" && inline.text.startsWith("<br")) append(' ')
                is Inline.Note -> Unit
                is Inline.Quoted -> {
                    append(if (inline.type == QuoteType.SingleQuote) '‘' else '“')
                    inline.content.forEach(::go)
                    append(if (inline.type == QuoteType.SingleQuote) '’' else '”')
                }
                else -> inlineChildren(inline).forEach(::go)
            }
        }
        inlines.forEach(::go)
    }

    fun stringifyBlocks(blocks: List<Block>): String {
        val stripped = object : PandocVisitor() {
            override fun transformInline(value: Inline): Inline = when (value) {
                is Inline.Note -> Inline.Note(emptyList())
                is Inline.Cite -> value.copy(citations = emptyList())
                is Inline.Quoted -> Inline.Span(Attr(), listOf(Inline.Str(if (value.type == QuoteType.SingleQuote) "‘" else "“")) +
                    value.content + Inline.Str(if (value.type == QuoteType.SingleQuote) "’" else "”"))
                else -> value
            }
        }.walkBlocks(blocks)
        return buildString {
            object : PandocVisitor() {
                override fun visitInline(value: Inline) {
                    when (value) {
                        is Inline.Str, is Inline.Code, is Inline.Math, is Inline.RawInline,
                        Inline.Space, Inline.SoftBreak, Inline.LineBreak -> append(stringifyInlines(listOf(value)))
                        else -> Unit
                    }
                }
            }.walkBlocks(stripped)
        }
    }

    fun lookupMetaString(key: String, meta: Map<String, MetaValue>): String = when (val value = meta[key]) {
        is MetaValue.MetaString -> value.value
        is MetaValue.MetaInlines -> stringifyInlines(value.content)
        is MetaValue.MetaBlocks -> stringifyBlocks(value.content)
        is MetaValue.MetaBool -> if (value.value) "True" else "False"
        else -> ""
    }

    fun capitalize(inlines: List<Inline>): List<Inline> = object : PandocVisitor() {
        override fun transformInline(value: Inline): Inline = if (value is Inline.Str)
            Inline.Str(UnicodeText.toUpper(value.text)) else value
    }.walkInlines(inlines)

    fun blocksToInlines(blocks: List<Block>, separator: Inlines = Builder.linebreak): Inlines =
        join(blocks.map(::blockToInlines), separator)

    private fun blockToInlines(block: Block): Inlines = when (block) {
        is Block.Plain -> Inlines(block.content)
        is Block.Para -> Inlines(block.content)
        is Block.LineBlock -> Inlines(block.lines.flatMapIndexed { index, line -> if (index == 0) line else listOf(Inline.LineBreak) + line })
        is Block.CodeBlock -> Builder.codeWith(block.attr, block.text)
        is Block.RawBlock -> Inlines(listOf(Inline.RawInline(block.format, block.text)))
        is Block.BlockQuote -> blocksToInlines(block.blocks)
        is Block.OrderedList -> join(block.items.map { blocksToInlines(it) })
        is Block.BulletList -> join(block.items.map { blocksToInlines(it) })
        is Block.DefinitionList -> join(block.items.map { (term, definitions) ->
            Inlines(term) + Builder.str(":") + Builder.space + join(definitions.map { blocksToInlines(it) })
        })
        is Block.Header -> Inlines(block.content)
        Block.HorizontalRule -> Inlines()
        is Block.Table -> join((block.head.rows + block.bodies.flatMap { it.head + it.body } + block.foot.rows).map { row ->
            join(row.cells.map { blocksToInlines(it.blocks) })
        }, Builder.linebreak)
        is Block.Div -> blocksToInlines(block.blocks)
        is Block.Figure -> blocksToInlines(block.blocks)
    }

    private fun join(values: List<Inlines>, separator: Inlines = Inlines()): Inlines =
        values.foldIndexed(Inlines()) { index, acc, value -> acc + (if (index == 0) Inlines() else separator) + value }

    fun romanNumeral(value: Long): String {
        if (value < 0 || value >= 4000) return "?"
        var n = value
        return buildString {
            for ((number, symbol) in listOf(1000L to "M", 900L to "CM", 500L to "D", 400L to "CD", 100L to "C",
                90L to "XC", 50L to "L", 40L to "XL", 10L to "X", 9L to "IX", 5L to "V", 4L to "IV", 1L to "I")) {
                while (n >= number) { append(symbol); n -= number }
            }
        }
    }

    fun orderedListMarkers(attributes: ListAttributes): Sequence<String> = sequence {
        var number = attributes.start
        var alpha = maxOf(0, attributes.start - 1) % 26
        while (true) {
            val marker = when (attributes.style) {
                ListNumberStyle.UpperAlpha -> ('A'.code + alpha.toInt()).toChar().toString()
                ListNumberStyle.LowerAlpha -> ('a'.code + alpha.toInt()).toChar().toString()
                ListNumberStyle.UpperRoman -> romanNumeral(number)
                ListNumberStyle.LowerRoman -> romanNumeral(number).lowercase(Locale.ROOT)
                else -> number.toString()
            }
            yield(when (attributes.delimiter) {
                ListNumberDelim.OneParen -> "$marker)"
                ListNumberDelim.TwoParens -> "($marker)"
                else -> "$marker."
            })
            if (number == Long.MAX_VALUE && attributes.style !in listOf(ListNumberStyle.UpperAlpha, ListNumberStyle.LowerAlpha)) break
            number++
            alpha = (alpha + 1) % 26
        }
    }

    fun combineAttr(first: Attr, second: Attr): Attr {
        val kvs = first.keyValues.toMutableList()
        for (entry in second.keyValues.asReversed()) if (kvs.none { it.first == entry.first }) kvs.add(0, entry)
        return Attr(first.identifier.ifEmpty { second.identifier }, (first.classes + second.classes).distinct(), kvs)
    }

    fun makeSections(blocks: List<Block>, numbering: Boolean = false, baseLevel: Long? = null, offsets: List<Long> = emptyList()): List<Block> {
        var minimum = Long.MAX_VALUE
        object : PandocVisitor() {
            override fun visitBlock(value: Block) { minimum = minOf(minimum, if (value is Block.Header) value.level else 99) }
        }.walkBlocks(blocks)
        if (offsets.any { it != 0L }) minimum = 1
        var lastNumber = offsets

        fun headerLtEq(level: Long, block: Block): Boolean = when (block) {
            is Block.Header -> block.level <= level
            is Block.Div -> block.blocks.firstOrNull()?.let { headerLtEq(level, it) } ?: false
            else -> false
        }

        fun go(input: List<Block>): List<Block> {
            val result = mutableListOf<Block>()
            var index = 0
            while (index < input.size) {
                val block = input[index++]
                when (block) {
                    is Block.Header -> {
                        val newNumber = (minimum..block.level).mapIndexed { i, level ->
                            (lastNumber.getOrElse(i) { 0 }) + if (level == block.level) 1 else 0
                        }
                        if (newNumber.isNotEmpty() && "unnumbered" !in block.attr.classes) lastNumber = newNumber
                        var end = index
                        while (end < input.size && !headerLtEq(block.level, input[end])) end++
                        val children = go(input.subList(index, end))
                        index = end
                        val kvs = if (numbering && "unnumbered" !in block.attr.classes && block.attr.keyValues.none { it.first == "number" })
                            listOf("number" to newNumber.joinToString(".")) + block.attr.keyValues else block.attr.keyValues
                        val divAttr = block.attr.copy(classes = listOf("section") + block.attr.classes, keyValues = kvs)
                        val headerAttr = Attr(classes = block.attr.classes, keyValues = kvs.filter { (key, value) ->
                            key != "epub:type" && (key != "role" || value in listOf("tab", "presentation", "none", "treeitem", "menuitem", "button", "heading"))
                        })
                        result += Block.Div(divAttr, listOf(block.copy(level = baseLevel?.let { it + block.level - minimum } ?: block.level, attr = headerAttr)) + children)
                    }
                    is Block.Div -> {
                        val header = block.blocks.firstOrNull() as? Block.Header
                        val merge = header != null && block.blocks.drop(1).all { it !is Block.Header || it.level > header.level } &&
                            ("section" in block.attr.classes || block.attr.classes.none { it in listOf("column", "columns", "fragment", "cell") })
                        val inner = go(block.blocks)
                        val section = inner.singleOrNull() as? Block.Div
                        val innerHeader = section?.blocks?.firstOrNull() as? Block.Header
                        if (merge && section != null && innerHeader != null) {
                            val combined = combineAttr(section.attr, block.attr)
                            result += if (block.attr.identifier == section.attr.identifier || block.attr.identifier.isEmpty()) section.copy(attr = combined)
                            else section.copy(attr = combined.copy(identifier = block.attr.identifier), blocks = listOf(
                                innerHeader.copy(attr = innerHeader.attr.copy(identifier = section.attr.identifier))) + section.blocks.drop(1))
                        } else result += block.copy(blocks = inner)
                    }
                    else -> result += block
                }
            }
            return result
        }
        return go(blocks)
    }

    data class LegacyTable(val caption: List<Inline>, val alignments: List<Alignment>, val widths: List<Double>,
        val headers: List<List<Block>>, val rows: List<List<List<Block>>>)

    fun toLegacyTable(table: Block.Table): LegacyTable {
        val normalized = Tables.normalize(table)
        val rows = normalized.head.rows + normalized.bodies.flatMap { it.head + it.body } + normalized.foot.rows
        var pending = emptyList<Long>()
        val cut = rows.map { row ->
            val pieces = mutableListOf<List<Block>>()
            val next = mutableListOf<Long>()
            var column = 0
            var cell = 0
            while (true) {
                val hang = pending.getOrElse(column) { 0 }
                if (hang > 0) { pieces.add(emptyList()); next += hang - 1; column++ }
                else if (cell < row.cells.size) {
                    val value = row.cells[cell++]
                    pieces.add(value.blocks)
                    repeat(value.colSpan.toInt() - 1) { pieces.add(emptyList()) }
                    repeat(value.colSpan.toInt()) { next += value.rowSpan - 1 }
                    column += value.colSpan.toInt()
                } else break
            }
            pending = next
            pieces.toList()
        }
        val hasHead = normalized.head.rows.isNotEmpty()
        return LegacyTable(blocksToInlines(table.caption.long).values, table.columns.map { it.alignment },
            table.columns.map { ((it.width as? ColWidth.Width)?.value ?: 0.0).let { w -> if (w > 0) w else 0.0 } },
            if (hasHead) cut.first() else emptyList(), if (hasHead) cut.drop(1) else cut)
    }

    fun ensureValidXmlIdentifiers(document: Pandoc): Pandoc {
        fun valid(id: String) = id.isEmpty() || UCharacter.isLetter(id.codePointAt(0))
        fun attr(value: Attr) = if (valid(value.identifier)) value else value.copy(identifier = "id_" + value.identifier)
        fun target(value: Target): Target = if (value.url.startsWith('#') && !valid(value.url.drop(1))) value.copy(url = "#id_" + value.url.drop(1)) else value
        fun row(value: Row) = value.copy(attr = attr(value.attr), cells = value.cells.map { it.copy(attr = attr(it.attr)) })
        return object : PandocVisitor() {
            override fun transformInline(value: Inline): Inline = when (value) {
                is Inline.Span -> value.copy(attr = attr(value.attr))
                is Inline.Code -> value.copy(attr = attr(value.attr))
                is Inline.Link -> value.copy(attr = attr(value.attr), target = target(value.target))
                is Inline.Image -> value.copy(attr = attr(value.attr), target = target(value.target))
                else -> value
            }
            override fun transformBlock(value: Block): Block = when (value) {
                is Block.Header -> value.copy(attr = attr(value.attr))
                is Block.CodeBlock -> value.copy(attr = attr(value.attr))
                is Block.Div -> value.copy(attr = attr(value.attr))
                is Block.Figure -> value.copy(attr = attr(value.attr))
                is Block.Table -> value.copy(attr = attr(value.attr), head = value.head.copy(attr = attr(value.head.attr), rows = value.head.rows.map(::row)),
                    bodies = value.bodies.map { it.copy(attr = attr(it.attr), head = it.head.map(::row), body = it.body.map(::row)) },
                    foot = value.foot.copy(attr = attr(value.foot.attr), rows = value.foot.rows.map(::row)))
                else -> value
            }
        }.walk(document)
    }

    internal fun inlineChildren(value: Inline): List<Inline> = when (value) {
        is Inline.Emph -> value.content; is Inline.Underline -> value.content; is Inline.Strong -> value.content
        is Inline.Strikeout -> value.content; is Inline.Superscript -> value.content; is Inline.Subscript -> value.content
        is Inline.SmallCaps -> value.content; is Inline.Quoted -> value.content; is Inline.Cite -> value.content
        is Inline.Link -> value.content; is Inline.Image -> value.content; is Inline.Span -> value.content
        else -> emptyList()
    }
}
