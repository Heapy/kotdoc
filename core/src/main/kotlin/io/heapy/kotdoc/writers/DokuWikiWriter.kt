// Text.Pandoc.Writers.DokuWiki, Pandoc 3.12.1.
// Copyright (C) 2008-2024 John MacFarlane. GNU GPL version 2 or later.
// Maintainer of original: Clare Macrae <clare.macrae@googlemail.com>.
package io.heapy.kotdoc.writers

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.ast.Alignment
import io.heapy.kotdoc.images.Dimension
import io.heapy.kotdoc.images.Dimensions
import io.heapy.kotdoc.images.Direction
import io.heapy.kotdoc.layout.DocLayout
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.templates.*
import io.heapy.kotdoc.uri.PandocURI

/** Original DokuWiki writer. Reports are emitted in source traversal order. */
object DokuWikiWriter {
    fun write(options: WriterOptions, document: Pandoc, report: (LogMessage) -> Unit = {}): String =
        Session(options, report).document(document)

    private data class Environment(val indent: String = "", val backSlash: Boolean = false, val quote: Int = 0)
    // Concatenation retains shared output pieces rather than copying every
    // completed descendant into all suspended parent frames.
    private sealed interface Output {
        data class Text(val value: String) : Output
        data class Append(val left: Output, val right: Output) : Output
        operator fun plus(other: Output): Output = Append(this, other)
        operator fun plus(other: String): Output = this + Text(other)
        fun text(): String = buildString {
            val pending = ArrayDeque<Output>()
            pending.addLast(this@Output)
            while (pending.isNotEmpty()) when (val next = pending.removeLast()) {
                is Text -> append(next.value)
                is Append -> { pending.addLast(next.right); pending.addLast(next.left) }
            }
        }
    }
    private fun out(value: String): Output = Output.Text(value)
    private fun join(values: List<Output>, separator: String = ""): Output {
        var result: Output = out("")
        for ((index, value) in values.withIndex()) result = result + (if (index == 0) "" else separator) + value
        return result
    }
    private sealed interface Work {
        data class Blocks(val values: List<Block>, val env: Environment) : Work
        data class BlockValue(val value: Block, val env: Environment) : Work
        data class Inlines(val values: List<Inline>, val env: Environment) : Work
        data class InlineValue(val value: Inline, val env: Environment) : Work
    }
    private class Session(val options: WriterOptions, val report: (LogMessage) -> Unit) {
        // Preserve the original monadic traversal order on heap continuations,
        // rather than consuming the JVM stack for each nested AST constructor.
        private val renderer = DeepRecursiveFunction<Work, Output> { work -> when (work) {
            is Work.Blocks -> renderBlocks(work.values, work.env)
            is Work.BlockValue -> renderBlock(work.value, work.env)
            is Work.Inlines -> renderInlines(work.values, work.env)
            is Work.InlineValue -> renderInline(work.value, work.env)
        } }
        private suspend fun DeepRecursiveScope<Work, Output>.blocks(xs: List<Block>, e: Environment): Output = callRecursive(Work.Blocks(xs, e))
        private suspend fun DeepRecursiveScope<Work, Output>.block(b: Block, e: Environment): Output = callRecursive(Work.BlockValue(b, e))
        private suspend fun DeepRecursiveScope<Work, Output>.inlines(xs: List<Inline>, e: Environment): Output = callRecursive(Work.Inlines(xs, e))
        private suspend fun DeepRecursiveScope<Work, Output>.inline(i: Inline, e: Environment): Output = callRecursive(Work.InlineValue(i, e))
        fun document(document: Pandoc): String {
            val values = TemplateValues.Text
            val context = WriterMetadata.metaToContext(options, values,
                { DocLayout.Text.literal(SharedText.trimr(renderer(Work.Blocks(it, Environment())).text())) },
                { DocLayout.Text.literal(SharedText.trimr(renderer(Work.Inlines(it, Environment())).text())) }, document.meta)
            val body = renderer(Work.Blocks(document.blocks, Environment())).text()
            val ctx = WriterMetadata.defField("body", body,
                WriterMetadata.defField("toc", options.tableOfContents, context, values.boolean), values.target)
            return options.template?.let { DocLayout.Text.render(null, TemplateRenderer.Text.render(it, ctx)) } ?: body
        }
        fun prefix(e: Environment) = if (e.quote == 0) "" else ">".repeat(e.quote) + " "
        private suspend fun DeepRecursiveScope<Work, Output>.renderBlocks(xs: List<Block>, e: Environment): Output {
            val consolidated = mutableListOf<Block>()
            for (x in xs) {
                val previous = consolidated.lastOrNull()
                if (x is Block.RawBlock && previous is Block.RawBlock && x.format == previous.format)
                    consolidated[consolidated.lastIndex] = previous.copy(text = previous.text + "\n" + x.text)
                else consolidated.add(x)
            }
            val rendered = consolidated.map { block(it, e) }
            return if (e.backSlash) join(rendered.map { out(it.text().removeSuffix("\n").replace("\n", "\\\\ ")) }, "\\\\ \\\\ ")
                else join(rendered, "\n")
        }
        private suspend fun DeepRecursiveScope<Work, Output>.renderBlock(b: Block, e: Environment): Output = when (b) {
            is Block.Div -> blocks(b.blocks, e) + if (e.indent.isEmpty()) "\n" else ""
            is Block.Plain -> inlines(b.content, e)
            is Block.Para -> out(prefix(e)) + inlines(b.content, e) + if (e.indent.isEmpty()) "\n" else ""
            is Block.LineBlock -> block(Block.Para(b.lines.flatMapIndexed { i, line -> if (i == 0) line else listOf(Inline.LineBreak) + line }), e)
            is Block.RawBlock -> when {
                b.format == Format("dokuwiki") -> out(b.text)
                b.format == Format("html") && options.isEnabled(ExtensionName.RawHtml) -> out("<HTML>\n${b.text}\n</HTML>")
                else -> { report(LogMessage.BlockNotRendered(b)); out("") }
            }
            Block.HorizontalRule -> out("\n----\n")
            is Block.Header -> {
                val marks = "=".repeat((7L - b.level).coerceAtLeast(0).toInt())
                out("$marks ") + inlines(removeFormatting(b.content), e) + " $marks\n"
            }
            is Block.CodeBlock -> out(prefix(e) + "<code" + (b.attr.classes.firstOrNull()?.let { " " + (languageNames[it] ?: it) } ?: "") +
                ">\n" + b.text + (if (b.text.endsWith('\n')) "" else "\n") + "</code>\n")
            is Block.BlockQuote -> blocks(b.blocks, e.copy(quote = e.quote + 1))
            is Block.Table -> table(b, e)
            is Block.BulletList -> join(b.items.map { bullet(it, e.copy(indent = e.indent + "  ")) }, "\n") + if (e.indent.isEmpty()) "\n" else ""
            is Block.OrderedList -> join(b.items.map { ordered(it, e.copy(indent = e.indent + "  ")) }, "\n") + if (e.indent.isEmpty()) "\n" else ""
            is Block.DefinitionList -> join(b.items.map { definition(it, e.copy(indent = e.indent + "  ")) }, "\n") + if (e.indent.isEmpty()) "\n" else ""
            is Block.Figure -> block(Block.Div(b.attr, b.blocks + if (b.caption.long.isEmpty()) emptyList() else listOf(Block.Div(Attr(classes = listOf("caption")), b.caption.long))), e)
        }
        fun itemIndent(e: Environment) = if (e.backSlash) e.indent.drop(2) else e.indent
        fun wrap(content: Output, simple: Boolean) = if (simple) content else out("<WRAP>\n") + content + "\n</WRAP>"
        private suspend fun DeepRecursiveScope<Work, Output>.bullet(xs: List<Block>, e: Environment): Output {
            val bs = xs.map { block(it, e) }
            val content = if (xs.size == 2 && xs[1] is Block.CodeBlock) join(bs) else join(bs, "\n")
            return out(prefix(e) + itemIndent(e) + "* ") + wrap(content, simpleItem(xs))
        }
        private suspend fun DeepRecursiveScope<Work, Output>.ordered(xs: List<Block>, e: Environment) = out(prefix(e) + itemIndent(e) + "- ") + wrap(blocks(xs, e.copy(quote = 0)), simpleItem(xs))
        private suspend fun DeepRecursiveScope<Work, Output>.definition(item: Pair<List<Inline>, List<List<Block>>>, e: Environment): Output {
            val label = inlines(item.first, e)
            val contents = item.second.map { blocks(it, e.copy(quote = 0)) }
            val simple = item.second.all(::simpleItem)
            return out(prefix(e) + itemIndent(e) + "* **") + label + "** " + if (simple) join(contents, "; ") else wrap(join(contents, "\n"), false)
        }
        fun plain(b: Block) = b is Block.Plain || b is Block.Para
        fun simpleItem(xs: List<Block>): Boolean {
            val pending = ArrayDeque<List<Block>>()
            pending.addLast(xs)
            while (pending.isNotEmpty()) {
                val item = pending.removeLast()
                if (item.isEmpty() || item.size == 2 && plain(item[0]) && item[1] is Block.CodeBlock) continue
                when (val first = item[0]) {
                    is Block.Div -> pending.addLast(first.blocks)
                    else -> if (!plain(first)) return false
                }
                for (block in item.drop(1)) when (block) {
                    is Block.BulletList -> pending.addAll(block.items)
                    is Block.OrderedList -> if (block.attributes.start == 1L) pending.addAll(block.items) else return false
                    is Block.DefinitionList -> block.items.forEach { pending.addAll(it.second) }
                    else -> return false
                }
            }
            return true
        }
        private suspend fun DeepRecursiveScope<Work, Output>.table(b: Block.Table, e: Environment): Output {
            val t = DocumentShared.toLegacyTable(b)
            suspend fun DeepRecursiveScope<Work, Output>.cell(a: Alignment, xs: List<Block>): String = (if (a == Alignment.AlignRight || a == Alignment.AlignCenter) "  " else "") +
                blocks(xs, e.copy(backSlash = true, quote = 0)).text() + (if (a == Alignment.AlignLeft || a == Alignment.AlignCenter) "  " else "")
            val caption = if (t.caption.isEmpty()) out("") else inlines(t.caption, e) + "\n"
            val header = if (t.headers.all { it.isEmpty() }) emptyList() else t.alignments.zip(t.headers) { a, xs -> cell(a, xs) }
            val rows = t.rows.map { row -> t.alignments.zip(row) { a, xs -> cell(a, xs) } }
            // Data.List.transpose retains cells from ragged rows.
            val widths = (header.size.coerceAtLeast(rows.maxOfOrNull { it.size } ?: 0)).let { size ->
                List(size) { i -> (listOf(header) + rows).mapNotNull { it.getOrNull(i) }.maxOfOrNull { it.codePointCount(0, it.length) } ?: 0 }
            }
            fun row(sep: String, cells: List<String>) = sep + cells.mapIndexed { i, s ->
                val extra = widths[i] - s.codePointCount(0, s.length)
                val a = t.alignments[i]
                if (extra <= 0) s else when (a) {
                    Alignment.AlignLeft, Alignment.AlignDefault -> s + " ".repeat(extra)
                    Alignment.AlignRight -> " ".repeat(extra) + s
                    Alignment.AlignCenter -> " ".repeat(extra / 2) + s + " ".repeat(extra - extra / 2)
                }
            }.joinToString(sep) + sep
            return caption + (if (header.isEmpty()) "" else row("^", header) + "\n") + rows.joinToString("") { row("|", it) + "\n" }
        }
        private suspend fun DeepRecursiveScope<Work, Output>.renderInlines(xs: List<Inline>, e: Environment) = join(xs.map { inline(it, e) })
        private suspend fun DeepRecursiveScope<Work, Output>.renderInline(i: Inline, e: Environment): Output = when (i) {
            is Inline.Span -> inlines(i.content, e)
            is Inline.Emph -> out("//") + inlines(i.content, e) + "//"
            is Inline.Underline -> out("__") + inlines(i.content, e) + "__"
            is Inline.Strong -> out("**") + inlines(i.content, e) + "**"
            is Inline.Strikeout -> out("<del>") + inlines(i.content, e) + "</del>"
            is Inline.Superscript -> out("<sup>") + inlines(i.content, e) + "</sup>"
            is Inline.Subscript -> out("<sub>") + inlines(i.content, e) + "</sub>"
            is Inline.SmallCaps -> inlines(i.content, e)
            is Inline.Quoted -> if (i.type == QuoteType.SingleQuote) out("‘") + inlines(i.content, e) + "’" else out("“") + inlines(i.content, e) + "”"
            is Inline.Cite -> inlines(i.content, e)
            is Inline.Code -> out("''%%${i.text}%%''")
            is Inline.Str -> out(i.text.replace("//", "%%//%%").replace("**", "%%**%%").replace("__", "%%__%%"))
            is Inline.Math -> out((if (i.type == MathType.DisplayMath) "$$" else "$").let { it + i.text + it })
            is Inline.RawInline -> when {
                i.format == Format("dokuwiki") -> out(i.text)
                i.format == Format("html") && options.isEnabled(ExtensionName.RawHtml) -> out("<html>${i.text}</html>")
                else -> { report(LogMessage.InlineNotRendered(i)); out("") }
            }
            Inline.LineBreak -> out(if (e.backSlash) "\n" else "\\\\\n")
            Inline.SoftBreak -> out(if (options.wrapText == WrapOption.WrapPreserve) "\n" else " ")
            Inline.Space -> out(" ")
            is Inline.Link -> {
                val label = inlines(i.content, e)
                val single = (i.content.singleOrNull() as? Inline.Str)?.text
                when {
                    single != null && i.target.url.startsWith("mailto:") -> out("<$single>")
                    single != null && PandocURI.escapeURI(single) == i.target.url -> out(i.target.url)
                    else -> out("[[" + (if (PandocURI.isURI(i.target.url)) i.target.url else i.target.url.removePrefix("/")) + "|") + label + "]]"
                }
            }
            is Inline.Image -> {
                val alt = inlines(i.content, e)
                val text = when { i.target.title.isNotEmpty() -> out("|${i.target.title}"); i.content.isNotEmpty() -> out("|") + alt; else -> out("") }
                out("{{${i.target.url}${imageDims(i.attr)}") + text + "}}"
            }
            is Inline.Note -> out("((") + blocks(i.blocks, e.copy(quote = 0)) + "))"
        }
        fun imageDims(attr: Attr): String {
            fun px(d: Direction) = Dimensions.dimension(d, attr)?.takeUnless { it is Dimension.Percent }?.let { Dimensions.showInPixel(it, options.dpi.toBigInteger()) }
            val w = px(Direction.Width); val h = px(Direction.Height)
            return when { w != null -> "?$w" + (h?.let { "x$it" } ?: ""); h != null -> "?0x$h"; else -> "" }
        }
        fun removeFormatting(xs: List<Inline>): List<Inline> {
            val fixed = object : PandocVisitor() {
                override fun transformInline(value: Inline): Inline = when (value) {
                    is Inline.Note -> Inline.Str("")
                    is Inline.Quoted -> Inline.Span(Attr(), listOf(Inline.Str(if (value.type == QuoteType.SingleQuote) "‘" else "“")) + value.content + Inline.Str(if (value.type == QuoteType.SingleQuote) "’" else "”"))
                    else -> value
                }
            }.walkInlines(xs)
            val result = mutableListOf<Inline>()
            object : PandocVisitor() {
                override fun visitInline(value: Inline) { when (value) {
                    is Inline.Str, Inline.Space, Inline.SoftBreak -> result.add(value)
                    is Inline.Code -> result.add(Inline.Str(value.text))
                    is Inline.Math -> result.add(Inline.Str(value.text))
                    Inline.LineBreak -> result.add(Inline.Space)
                    else -> Unit
                } }
            }.walkInlines(fixed)
            return result
        }
    }
    private val languageNames = mapOf("cs" to "csharp", "coffee" to "cofeescript", "commonlisp" to "lisp", "gcc" to "c", "html" to "html5", "makefile" to "make", "objectivec" to "objc", "r" to "rsplus", "sqlmysql" to "mysql", "sqlpostgresql" to "postgresql", "sci" to "scilab", "xorg" to "xorgconf")
}
