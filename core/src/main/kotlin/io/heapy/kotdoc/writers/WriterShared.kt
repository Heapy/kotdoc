// Text.Pandoc.Writers.Shared, Pandoc 3.12.1.
// Copyright (C) 2013-2024 John MacFarlane; GPL-2.0-or-later.
package io.heapy.kotdoc.writers

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.layout.*
import io.heapy.kotdoc.options.ExtensionName
import io.heapy.kotdoc.options.WriterOptions
import io.heapy.kotdoc.parsing.ExampleNumbers
import io.heapy.kotdoc.parsing.ListParsing
import io.heapy.kotdoc.parsing.Parsec
import io.heapy.kotdoc.parsing.ParseInput
import io.heapy.kotdoc.parsing.ParseState
import io.heapy.kotdoc.parsing.SourcePos
import io.heapy.kotdoc.parsing.ParserCharacters
import io.heapy.kotdoc.parsing.RegexCharacters
import java.util.ArrayDeque

/** Pure shared writer operations. Haskell Char arguments use Unicode code
 * points, including surrogate positions; Text arguments use scalar text. */
object WriterShared {
    fun isOrderedListMarker(text: String): Boolean = text.isNotEmpty() && text.last() in ".)" &&
        ListParsing.anyOrderedListMarker(ExampleNumbers).before(Parsec.eof())
            .run(ParseState(ParseInput(text),SourcePos(""),ExampleNumbers())).success

    fun isDisplayMath(value: Inline): Boolean = value is Inline.Math && value.type == MathType.DisplayMath ||
        value is Inline.Span && value.content.size == 1 && value.content[0] is Inline.Math &&
        (value.content[0] as Inline.Math).type == MathType.DisplayMath

    fun stripLeadingTrailingSpace(values: List<Inline>): List<Inline> {
        fun isSpace(x: Inline) = x === Inline.Space || x === Inline.SoftBreak
        var start = 0
        var end = values.size
        while (start < end && isSpace(values[start])) start++
        while (end > start && isSpace(values[end - 1])) end--
        return values.subList(start, end).toList()
    }

    fun fixDisplayMath(value: Block): Block {
        val inlines = when (value) { is Block.Plain -> value.content; is Block.Para -> value.content; else -> return value }
        if (!inlines.any(::isDisplayMath) || inlines.all(::isDisplayMath)) return value
        val blocks = mutableListOf<Block>()
        var start = 0
        while (start < inlines.size) {
            val display = isDisplayMath(inlines[start])
            var end = start + 1
            while (end < inlines.size && isDisplayMath(inlines[end]) == display) end++
            val group = stripLeadingTrailingSpace(inlines.subList(start, end))
            if (group.isNotEmpty()) blocks.add(if (value is Block.Plain) Block.Plain(group) else Block.Para(group))
            start = end
        }
        return Block.Div(Attr("", listOf("math"), emptyList()), blocks)
    }

    fun unsmartify(options: WriterOptions, text: String): String = unsmartify(text) { ExtensionName.OldDashes in options.extensions }

    internal fun unsmartify(text: String, oldDashes: () -> Boolean): String = buildString {
        for (point in TextChars.points(TextChars.fromString(text))) when (point) {
            0x2019, 0x2018 -> append('\'')
            0x2026 -> append("...")
            0x2013 -> append(if (oldDashes()) "-" else "--")
            0x2014 -> append(if (oldDashes()) "--" else "---")
            0x201c, 0x201d -> append('"')
            else -> appendCodePoint(point)
        }
    }

    fun toSuperscript(point: Int): Int? {
        require(point in 0..0x10ffff) { "Not a Haskell Char: $point" }
        return when (point) {
            49 -> 0xb9; 50 -> 0xb2; 51 -> 0xb3
            43 -> 0x207a; 45, 0x2212 -> 0x207b; 61 -> 0x207c; 40 -> 0x207d; 41 -> 0x207e
            in 48..57 -> 0x2070 + point - 48
            else -> if (ParserCharacters.isSpace(point)) point else null
        }
    }

    fun toSubscript(point: Int): Int? {
        require(point in 0..0x10ffff) { "Not a Haskell Char: $point" }
        return when (point) {
            43 -> 0x208a; 45, 0x2212 -> 0x208b; 61 -> 0x208c; 40 -> 0x208d; 41 -> 0x208e
            in 48..57 -> 0x2080 + point - 48
            else -> if (ParserCharacters.isSpace(point)) point else null
        }
    }

    fun toSubscriptInline(value: Inline): Inline? = scriptInline(value, ::toSubscript)
    fun toSuperscriptInline(value: Inline): Inline? = scriptInline(value, ::toSuperscript)

    private fun scriptInline(value: Inline, convert: (Int) -> Int?): Inline? {
        class Frame(val span: Inline.Span, var at: Int = 0, val converted: MutableList<Inline> = mutableListOf())
        val parents = ArrayDeque<Frame>()
        var current = value
        while (true) {
            var result: Inline = when (val inline = current) {
                Inline.Space, Inline.SoftBreak, Inline.LineBreak -> inline
                is Inline.Str -> {
                    val out = StringBuilder()
                    for (point in TextChars.points(TextChars.fromString(inline.text))) out.appendCodePoint(convert(point) ?: return null)
                    Inline.Str(out.toString())
                }
                is Inline.Span -> {
                    if (inline.content.isNotEmpty()) {
                        parents.push(Frame(inline, 1)); current = inline.content[0]; continue
                    }
                    inline.copy(content = emptyList())
                }
                else -> return null
            }
            while (true) {
                val parent = parents.peek() ?: return result
                parent.converted.add(result)
                if (parent.at < parent.span.content.size) { current = parent.span.content[parent.at++]; break }
                parents.pop()
                result = parent.span.copy(content = parent.converted.toList())
            }
        }
    }

    fun endsWithPlain(blocks: List<Block>): Boolean {
        var current = blocks
        while (true) when (val last = current.lastOrNull()) {
            is Block.Plain -> return true
            is Block.BulletList -> current = last.items.lastOrNull() ?: return false
            is Block.OrderedList -> current = last.items.lastOrNull() ?: return false
            is Block.DefinitionList -> current = last.items.lastOrNull()?.second?.lastOrNull() ?: return false
            else -> return false
        }
    }

    fun removeLinks(inlines: List<Inline>): List<Inline> = object : PandocVisitor() {
        override fun transformInline(value: Inline): Inline = if (value is Inline.Link) Inline.Span(value.attr, value.content) else value
    }.walkInlines(inlines)

    fun ensureValidXmlIdentifiers(document: Pandoc) = DocumentShared.ensureValidXmlIdentifiers(document)

    fun toTaskListItem(blocks: List<Block>): Pair<Boolean, List<Block>>? = toTaskListItem(blocks, { it }, { null })

    /** The source uses only pure/mzero from its MonadPlus dictionary. */
    fun <R> toTaskListItem(blocks: List<Block>, pure: (Pair<Boolean, List<Block>>) -> R, zero: () -> R): R {
        val first = blocks.firstOrNull()
        val inlines = when (first) { is Block.Plain -> first.content; is Block.Para -> first.content; else -> return zero() }
        val marker = (inlines.firstOrNull() as? Inline.Str)?.text ?: return zero()
        if (marker != "☐" && marker != "☒") return zero()
        val remaining = when {
            inlines.size == 1 -> emptyList()
            inlines[1] === Inline.Space -> inlines.subList(2, inlines.size)
            else -> return zero()
        }
        val changed = if (first is Block.Plain) Block.Plain(remaining) else Block.Para(remaining)
        // Share the source tail through a read-only view. The MonadPlus pure
        // callback may consume only the checkbox flag, without forcing either
        // the new block's inline contents or the remaining input blocks.
        val result = object : AbstractList<Block>() {
            override val size: Int get() = blocks.size
            override fun get(index: Int): Block = if (index == 0) changed else blocks[index]
        }
        return pure((marker == "☒") to result)
    }

    private fun docParts(content: Doc<String>): List<Doc<String>> {
        // The source normalizes Concat's spine but never descends through any
        // other constructor, including styles, links, prefixes or blocks.
        val pending = ArrayDeque<Doc<String>>()
        val parts = mutableListOf<Doc<String>>()
        pending.push(content)
        while (pending.isNotEmpty()) when (val d = pending.pop()) {
            is Doc.Concat -> { pending.push(d.right); pending.push(d.left) }
            else -> parts.add(d)
        }
        return parts
    }

    fun delimited(opener: Doc<String>, closer: Doc<String>, content: Doc<String>): Doc<String> {
        fun isWS(x: Doc<String>) = x === Doc.NewLine || x === Doc.CarriageReturn || x === Doc.BreakingSpace || x is Doc.BlankLines
        val parts = docParts(content)
        var start = 0
        var end = parts.size
        while (start < end && isWS(parts[start])) start++
        while (end > start && isWS(parts[end - 1])) end--
        val layout = DocLayout.Text
        return layout.hcat(parts.subList(0, start)) + (opener + (layout.hcat(parts.subList(start, end)) +
            (closer + layout.hcat(parts.subList(end, parts.size)))))
    }

    fun splitSentences(content: Doc<String>): Doc<String> {
        fun sentenceEnding(text: String): Boolean {
            val points = TextChars.points(TextChars.fromString(text))
            if (points.isEmpty()) return false
            fun initial(size: Int) = size == 1 && RegexCharacters.isUpper(points[0])
            val last = points.last()
            if (last == '.'.code) return !initial(points.size - 1)
            if (last == '!'.code || last == '?'.code) return true
            if (last in intArrayOf(')'.code, ']'.code, '"'.code, 0x201d) && points.size > 1) {
                val before = points[points.size - 2]
                return before == '.'.code && !initial(points.size - 2) || before == '!'.code || before == '?'.code
            }
            return false
        }
        val parts = docParts(content)
        val output = mutableListOf<Doc<String>>()
        var at = 0
        while (at < parts.size) {
            val current = parts[at]
            val spaceAt = if (parts.getOrNull(at + 1) is Doc.AfterBreak) at + 2 else at + 1
            if (current is Doc.Text && parts.getOrNull(spaceAt) === Doc.BreakingSpace && sentenceEnding(current.value)) {
                output.add(current); output.add(Doc.NewLine); at = spaceAt + 1
            } else { output.add(current); at++ }
        }
        return DocLayout.Text.hcat(output)
    }
}
