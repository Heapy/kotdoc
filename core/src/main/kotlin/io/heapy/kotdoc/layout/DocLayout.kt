// Text.DocLayout 0.6, BSD-3-Clause; Copyright (C) 2010-2024 John MacFarlane.
// See licenses/DocLayout-BSD-3-Clause.txt.
package io.heapy.kotdoc.layout

import java.util.ArrayDeque
import java.util.IdentityHashMap

/** Original finite document operations, parameterized by the HasChars representation. */
class DocLayout<A>(val chars: HasChars<A>) {
    private val attributed = AttributedChars(chars)
    val empty: Doc<A> get() = Doc.Empty
    val space: Doc<A> get() = Doc.BreakingSpace
    val cr: Doc<A> get() = Doc.CarriageReturn
    val blankline: Doc<A> get() = Doc.BlankLines(1)
    fun blanklines(n: Long): Doc<A> = Doc.BlankLines(n)
    fun isEmpty(doc: Doc<A>) = doc === Doc.Empty
    fun literal(value: A): Doc<A> = hcat(chars.splitLines(value).flatMapIndexed { i, line ->
        val d = if (chars.isNull(line)) empty else Doc.Text(length(line), line)
        if (i == 0) listOf(d) else listOf(Doc.NewLine, d)
    })
    fun text(value: String) = literal(chars.fromString(value))
    fun char(point: Int) = literal(chars.fromPoints(intArrayOf(point)))
    fun hcat(docs: Iterable<Doc<A>>): Doc<A> = docs.toList().asReversed().fold(empty) { a, d -> d + a }
    fun spaced(a: Doc<A>, b: Doc<A>): Doc<A> = if (isEmpty(a)) b else if (isEmpty(b)) a else a + space + b
    fun above(a: Doc<A>, b: Doc<A>): Doc<A> = if (isEmpty(a)) b else if (isEmpty(b)) a else a + cr + b
    fun separated(a: Doc<A>, b: Doc<A>): Doc<A> = if (isEmpty(a)) b else if (isEmpty(b)) a else a + blankline + b
    private fun join(docs: Iterable<Doc<A>>, f: (Doc<A>, Doc<A>) -> Doc<A>) = docs.toList().asReversed().fold(empty) { a, d -> f(d, a) }
    fun hsep(docs: Iterable<Doc<A>>) = join(docs, ::spaced)
    fun vcat(docs: Iterable<Doc<A>>) = join(docs, ::above)
    fun vsep(docs: Iterable<Doc<A>>) = join(docs, ::separated)
    fun prefixed(prefix: String, doc: Doc<A>): Doc<A> = if (isEmpty(doc)) empty else Doc.Prefixed(TextChars.fromString(prefix), doc)
    fun flush(doc: Doc<A>): Doc<A> = if (isEmpty(doc)) empty else Doc.Flush(doc)
    fun nest(n: Long, doc: Doc<A>) = prefixed(repeatPoint(n, 32), doc)
    fun hang(n: Long, start: Doc<A>, doc: Doc<A>) = start + nest(n, doc)
    fun beforeNonBlank(doc: Doc<A>): Doc<A> = Doc.BeforeNonBlank(doc)
    fun afterBreak(value: String): Doc<A> = Doc.AfterBreak(TextChars.fromString(value))
    fun inside(start: Doc<A>, end: Doc<A>, contents: Doc<A>) = start + contents + end
    fun braces(doc: Doc<A>) = inside(char(123), char(125), doc)
    fun brackets(doc: Doc<A>) = inside(char(91), char(93), doc)
    fun parens(doc: Doc<A>) = inside(char(40), char(41), doc)
    fun quotes(doc: Doc<A>) = inside(char(39), char(39), doc)
    fun doubleQuotes(doc: Doc<A>) = inside(char(34), char(34), doc)
    fun styled(style: StyleRequest, doc: Doc<A>): Doc<A> = if (isEmpty(doc)) empty else Doc.Styled(style, doc)
    fun bold(doc: Doc<A>) = styled(StyleRequest.Weight(true), doc)
    fun italic(doc: Doc<A>) = styled(StyleRequest.Shape(true), doc)
    fun underlined(doc: Doc<A>) = styled(StyleRequest.Underlined(Underline.Single), doc)
    fun strikeout(doc: Doc<A>) = styled(StyleRequest.Strikeout(true), doc)
    fun fg(color: Color8, doc: Doc<A>) = styled(StyleRequest.Foreground(color), doc)
    fun bg(color: Color8, doc: Doc<A>) = styled(StyleRequest.Background(color), doc)
    fun link(target: String, doc: Doc<A>): Doc<A> = Doc.Linked(TextChars.fromString(target), doc)
    fun vfill(value: A): Doc<A> = Doc.VFill(length(value), value)
    private fun length(value: A) = DisplayWidth.length(chars.points(value))

    /** Bottom-up transformations avoid JVM recursion for concatenation trees. */
    private fun transform(doc: Doc<A>, apply: (Doc<A>) -> Doc<A>): Doc<A> {
        val stack = ArrayDeque<Pair<Doc<A>, Boolean>>()
        val values = IdentityHashMap<Doc<A>, Doc<A>>()
        stack.push(doc to false)
        while (stack.isNotEmpty()) {
            val (d, ready) = stack.pop()
            fun child(x: Doc<A>) { if (!values.containsKey(x)) stack.push(x to false) }
            if (!ready) {
                stack.push(d to true)
                when (d) {
                    is Doc.Concat -> { child(d.right); child(d.left) }
                    is Doc.Prefixed -> child(d.doc); is Doc.BeforeNonBlank -> child(d.doc)
                    is Doc.Flush -> child(d.doc); is Doc.Styled -> child(d.doc); is Doc.Linked -> child(d.doc)
                    else -> Unit
                }
            } else {
                val rebuilt: Doc<A> = when (d) {
                    is Doc.Concat -> Doc.Concat(values.getValue(d.left), values.getValue(d.right))
                    is Doc.Prefixed -> d.copy(doc = values.getValue(d.doc))
                    is Doc.BeforeNonBlank -> d.copy(doc = values.getValue(d.doc))
                    is Doc.Flush -> d.copy(doc = values.getValue(d.doc))
                    is Doc.Styled -> d.copy(doc = values.getValue(d.doc)); is Doc.Linked -> d.copy(doc = values.getValue(d.doc))
                    else -> d
                }
                values[d] = apply(rebuilt)
            }
        }
        return values.getValue(doc)
    }
    fun nowrap(doc: Doc<A>): Doc<A> = transform(doc) { if (it === Doc.BreakingSpace) Doc.Text(1, chars.fromString(" ")) else it }
    fun nestle(doc: Doc<A>): Doc<A> {
        var d = doc
        while (true) when (d) {
            is Doc.BlankLines, Doc.NewLine -> return empty
            is Doc.Concat -> when (val left = d.left) {
                is Doc.Concat -> d = Doc.Concat(left.left, Doc.Concat(left.right, d.right))
                is Doc.BlankLines, Doc.NewLine, Doc.CarriageReturn -> d = d.right
                else -> return d
            }
            else -> return d
        }
    }
    fun chomp(doc: Doc<A>): Doc<A> {
        // Normalize the concatenation spine before applying the trailing rules.
        fun local(input: Doc<A>): Doc<A> {
            var d = input
            while (d is Doc.Concat && d.left is Doc.Concat) {
                val left = d.left
                d = Doc.Concat(left.left, Doc.Concat(left.right, d.right))
            }
            return d
        }
        // The source only descends into Prefixed and Concat for chomp.
        val todo = ArrayDeque<Pair<Doc<A>, Boolean>>(); val done = IdentityHashMap<Doc<A>, Doc<A>>()
        val root = local(doc); todo.push(root to false)
        while (todo.isNotEmpty()) {
            val (d, ready) = todo.pop()
            if (!ready) {
                todo.push(d to true)
                when (d) {
                    is Doc.Prefixed -> todo.push(d.doc to false)
                    is Doc.Concat -> { todo.push(d.left to false); todo.push(d.right to false) }
                    else -> Unit
                }
            } else done[d] = when (d) {
                is Doc.BlankLines, Doc.NewLine, Doc.CarriageReturn, Doc.BreakingSpace -> empty
                is Doc.Prefixed -> d.copy(doc = done.getValue(d.doc))
                is Doc.Concat -> if (done.getValue(d.right) === Doc.Empty) done.getValue(d.left) else d.left + done.getValue(d.right)
                else -> d
            }
        }
        return done.getValue(root)
    }

    private fun nonBlank(doc: Doc<A>): Boolean {
        var d = doc
        while (true) when (d) {
            is Doc.Text -> return true
            is Doc.BeforeNonBlank -> d = d.doc
            is Doc.Flush -> d = d.doc
            is Doc.Concat -> d = d.left
            else -> return false
        }
    }
    private fun measure(doc: Doc<A>, initial: Long, breaking: Boolean): Pair<Long, Long> {
        var longest = 0L; var column = initial
        data class Frame<A>(val doc: Doc<A>, val prefix: Long)
        val todo = ArrayDeque<Frame<A>>(); todo.push(Frame(doc, 0))
        while (todo.isNotEmpty()) {
            val (d, prefix) = todo.pop()
            fun push(x: Doc<A>, p: Long = prefix) { todo.push(Frame(x, p)) }
            when (d) {
                is Doc.Text -> column += d.width; is Doc.Block -> column += d.width
                is Doc.VFill -> column += d.width; is Doc.CookedText -> column += d.width
                is Doc.Styled -> push(d.doc); is Doc.Linked -> push(d.doc)
                Doc.CarriageReturn, Doc.NewLine, is Doc.BlankLines -> { longest = maxOf(longest, column); column = prefix }
                is Doc.Prefixed -> { val p = prefix + DisplayWidth.realLength(d.prefix); if (column <= prefix) column = p; push(d.doc, p) }
                is Doc.Flush -> push(d.doc, 0)
                Doc.BreakingSpace -> if (breaking && column > 0) { longest = maxOf(longest, column); column = prefix } else column++
                is Doc.AfterBreak -> if (column == prefix) column += DisplayWidth.realLength(d.value)
                is Doc.Concat -> {
                    val left = d.left
                    when (left) {
                        is Doc.Concat -> push(Doc.Concat(left.left, Doc.Concat(left.right, d.right)))
                        is Doc.BeforeNonBlank -> push(if (nonBlank(d.right)) Doc.Concat(left.doc, d.right) else d.right)
                        else -> { push(d.right); push(left) }
                    }
                }
                is Doc.BeforeNonBlank, Doc.Empty -> Unit
            }
        }
        return longest to column
    }
    fun offset(doc: Doc<A>) = measure(doc, 0, false).let { maxOf(it.first, it.second) }
    fun minOffset(doc: Doc<A>) = measure(doc, 0, true).let { maxOf(it.first, it.second) }
    fun updateColumn(doc: Doc<A>, initial: Long) = measure(doc, initial, false).second
    fun height(doc: Doc<A>) = chars.splitLines(render(null, doc)).size.toLong()
    fun render(width: Long?, doc: Doc<A>): A = renderPlain(width, doc)
    fun renderPlain(width: Long?, doc: Doc<A>): A = chars.concat(prerender(width, doc).parts.map { p ->
        if (chars.isNull(p.value)) chars.fromString("") else p.value
    })
    fun renderANSI(width: Long?, doc: Doc<A>): String = buildString {
        var font = Font.Base; var link: String? = null
        for (part in prerender(width, doc).parts) if (!chars.isNull(part.value)) {
            if (font != part.font) append(part.font.render())
            if (link != part.link) append(renderOSC8(part.link))
            append(chars.build(part.value)); font = part.font; link = part.link
        }
        append(Font.Base.render()); append(renderOSC8(null))
    }

    fun lblock(width: Long, doc: Doc<A>) = block(width, doc, 0)
    fun rblock(width: Long, doc: Doc<A>) = block(width, doc, 1)
    fun cblock(width: Long, doc: Doc<A>) = block(width, doc, 2)
    private fun block(requested: Long, doc: Doc<A>, align: Int): Doc<A> {
        val width = if (requested < 1 && !isEmpty(doc)) 1 else requested
        val lines = chop(width, prerender(width, doc)).map { line ->
            val pad = requested - DisplayWidth.length(attributed.points(line))
            when (align) { 1 -> attributed.append(attributed.replicateChar(pad, 32), line)
                2 -> attributed.append(attributed.replicateChar(Math.floorDiv(pad, 2), 32), line)
                else -> line }
        }
        return Doc.Block(width, lines)
    }
    private fun chop(width: Long, value: Attributed<A>): List<Attributed<A>> {
        val lines = attributed.splitLines(value).toMutableList()
        if (lines.lastOrNull()?.let { DisplayWidth.length(attributed.points(it)) == 0L } == true) lines.removeAt(lines.lastIndex)
        return lines.flatMap { line ->
            if (DisplayWidth.length(attributed.points(line)) <= width) listOf(line)
            else {
                val result = mutableListOf<Attributed<A>>(); var current = mutableListOf<AttributedPart<A>>(); var used = 0L
                for (part in line.parts) {
                    var pending = mutableListOf<Int>()
                    fun flush() { if (pending.isNotEmpty()) { current.add(part.copy(value = chars.fromPoints(pending.toIntArray()))); pending = mutableListOf() } }
                    for (c in chars.points(part.value)) {
                        val cw = DisplayWidth.charWidth(c)
                        if (used + cw > width && used > 0) { flush(); result.add(Attributed(current)); current = mutableListOf(); used = cw }
                        else used += cw
                        pending.add(c)
                    }
                    flush()
                }
                result.add(Attributed(current)); result
            }
        }
    }

    private enum class Kind { Text, Block, Fill, Cooked, Prefix, Before, Flush, Space, After, CR, NL, Blanks, StyleOpen, StyleClose, LinkOpen, LinkClose }
    private data class Flat<A>(val kind: Kind, val width: Long = 0, val value: A? = null,
        val attributed: Attributed<A>? = null, val lines: List<Attributed<A>> = emptyList(),
        val text: String = "", val children: List<Flat<A>> = emptyList(), val style: StyleRequest? = null)
    private fun flatten(doc: Doc<A>): List<Flat<A>> {
        data class Task<A>(val doc: Doc<A>? = null, val close: Kind? = null)
        data class Frame<A>(val todo: ArrayDeque<Task<A>>, val result: MutableList<Flat<A>>, val wrapper: Flat<A>?)
        val frames = ArrayDeque<Frame<A>>()
        frames.push(Frame(ArrayDeque<Task<A>>().apply { push(Task(doc)) }, mutableListOf(), null))
        while (true) {
            val frame = frames.peek()
            if (frame.todo.isEmpty()) {
                frames.pop()
                if (frames.isEmpty()) return frame.result
                if (frame.result.isNotEmpty()) frames.peek().result.add(checkNotNull(frame.wrapper).copy(children = frame.result))
                continue
            }
            val task = frame.todo.pop(); val out = frame.result
            if (task.close != null) { out.add(Flat(task.close)); continue }
            val d = checkNotNull(task.doc)
            fun nested(x: Doc<A>, wrapper: Flat<A>) { frames.push(Frame(ArrayDeque<Task<A>>().apply { push(Task(x)) }, mutableListOf(), wrapper)) }
            when (d) {
                is Doc.Text -> out.add(Flat(Kind.Text, d.width, d.value))
                is Doc.Block -> out.add(Flat(Kind.Block, d.width, lines = d.lines))
                is Doc.VFill -> out.add(Flat(Kind.Fill, d.width, d.value))
                is Doc.CookedText -> out.add(Flat(Kind.Cooked, d.width, attributed = d.value))
                is Doc.Prefixed -> nested(d.doc, Flat(Kind.Prefix, text = d.prefix))
                is Doc.BeforeNonBlank -> nested(d.doc, Flat(Kind.Before))
                is Doc.Flush -> nested(d.doc, Flat(Kind.Flush))
                Doc.BreakingSpace -> out.add(Flat(Kind.Space))
                is Doc.AfterBreak -> nested(text(d.value), Flat(Kind.After))
                Doc.CarriageReturn -> out.add(Flat(Kind.CR)); Doc.NewLine -> out.add(Flat(Kind.NL))
                is Doc.BlankLines -> out.add(Flat(Kind.Blanks, d.count))
                is Doc.Concat -> { frame.todo.push(Task(d.right)); frame.todo.push(Task(d.left)) }
                is Doc.Styled -> { out.add(Flat(Kind.StyleOpen, style = d.style)); frame.todo.push(Task(close = Kind.StyleClose)); frame.todo.push(Task(d.doc)) }
                is Doc.Linked -> { out.add(Flat(Kind.LinkOpen, text = d.link)); frame.todo.push(Task(close = Kind.LinkClose)); frame.todo.push(Task(d.doc)) }
                Doc.Empty -> Unit
            }
        }
    }

    private fun normalize(input: List<Flat<A>>): List<Flat<A>> {
        val todo = ArrayDeque(input); val result = mutableListOf<Flat<A>>()
        while (todo.isNotEmpty()) {
            val x = todo.removeFirst(); val y = todo.peekFirst()
            if (y == null) {
                when (x.kind) { Kind.NL, Kind.Blanks -> result.add(Flat(Kind.CR)); Kind.Space -> Unit; else -> result.add(x) }
                break
            }
            val replacement: Flat<A>? = when {
                x.kind == Kind.Blanks && y.kind == Kind.Blanks -> Flat(Kind.Blanks, maxOf(x.width, y.width))
                x.kind == Kind.Blanks && y.kind in setOf(Kind.Space, Kind.CR, Kind.NL) -> x
                x.kind == Kind.NL && y.kind == Kind.Blanks -> y
                x.kind == Kind.NL && y.kind in setOf(Kind.Space, Kind.CR) -> x
                x.kind == Kind.CR && y.kind == Kind.CR -> x
                x.kind == Kind.CR && y.kind == Kind.Blanks -> y
                x.kind == Kind.CR && y.kind == Kind.Space -> x
                x.kind == Kind.Space && y.kind in setOf(Kind.CR, Kind.NL, Kind.Blanks) -> y
                x.kind == Kind.Space && y.kind == Kind.Space -> x
                else -> null
            }
            if (replacement != null) { todo.removeFirst(); todo.addFirst(replacement) } else result.add(x)
        }
        return result
    }

    private fun prerender(width: Long?, doc: Doc<A>): Attributed<A> = Renderer(width).render(normalize(flatten(doc)))
    private inner class Renderer(val lineLength: Long?) {
        val output = mutableListOf<AttributedPart<A>>()
        var prefix = ""; var prefixA = chars.empty; var trimmed = chars.empty; var prefixLen = 0L
        var usePrefix = true; var column = 0L; var newlines = 2L
        val fonts = ArrayDeque<Font>(); var link: String? = null
        private val font get() = fonts.peek() ?: Font.Base
        private fun prefix(value: String) { prefix = value; prefixA = chars.fromString(value); trimmed = chars.fromString(value.trimEnd(::haskellSpace)); prefixLen = length(prefixA) }
        private fun newline() {
            if (column == 0L && usePrefix && prefix.isNotEmpty()) output.add(AttributedPart(null, Font.Base, trimmed))
            output.add(AttributedPart(null, Font.Base, chars.fromString("\n"))); column = 0; newlines++
        }
        private fun out(width: Long, value: A) {
            val pref = if (usePrefix) prefixA else chars.empty
            if (column == 0L && !(chars.isNull(pref) && font == Font.Base)) {
                output.add(AttributedPart(null, Font.Base, pref)); column = prefixLen + width
            } else column += width
            output.add(AttributedPart(link, font, value)); newlines = 0
        }
        private fun starts(value: A) = chars.points(value).firstOrNull()?.let { haskellSpace(it) } ?: false
        private fun startsBlank(f: Flat<A>): Boolean = when (f.kind) {
            Kind.Text -> starts(checkNotNull(f.value)); Kind.Cooked -> attributed.points(checkNotNull(f.attributed)).firstOrNull()?.let(::haskellSpace) ?: false
            Kind.Block -> f.width > 0 && f.lines.all { attributed.points(it).firstOrNull()?.let(::haskellSpace) ?: false }
            Kind.Fill -> f.width > 0 && starts(checkNotNull(f.value))
            Kind.Before, Kind.Prefix, Kind.Flush, Kind.After -> startsBlank(f.children.first())
            else -> true
        }
        private fun offset(f: Flat<A>) = when (f.kind) { Kind.Text, Kind.Block, Kind.Fill, Kind.Cooked -> f.width; Kind.Space -> 1L; else -> 0L }
        private fun breakable(f: Flat<A>) = f.kind in setOf(Kind.Space, Kind.CR, Kind.NL, Kind.Blanks)
        private fun printable(f: Flat<A>) = f.kind !in setOf(Kind.StyleOpen, Kind.StyleClose, Kind.LinkOpen, Kind.LinkClose)
        private fun block(f: Flat<A>) = f.kind == Kind.Block || f.kind == Kind.Fill
        fun render(input: List<Flat<A>>): Attributed<A> {
            // Continuations carry prefix/flush restoration without consuming JVM stack.
            data class Work<A>(var items: List<Flat<A>>, var index: Int = 0, val restore: (() -> Unit)? = null)
            val work = ArrayDeque<Work<A>>(); work.push(Work(input))
            fun push(items: List<Flat<A>>, restore: (() -> Unit)? = null) { work.push(Work(items, restore = restore)) }
            while (work.isNotEmpty()) {
                val current = work.peek()
                if (current.index == current.items.size) { work.pop(); current.restore?.invoke(); continue }
                val f = current.items[current.index++]
                fun rest() = current.items.subList(current.index, current.items.size)
                when (f.kind) {
                    Kind.Text -> out(f.width, checkNotNull(f.value))
                    Kind.Cooked -> {
                        val pref = if (usePrefix) prefixA else chars.empty
                        if (column == 0L && !chars.isNull(pref)) { output.add(AttributedPart(null, Font.Base, pref)); column = prefixLen + f.width } else column += f.width
                        output.addAll(checkNotNull(f.attributed).parts); newlines = 0
                    }
                    Kind.StyleOpen -> fonts.push(font.apply(checkNotNull(f.style)))
                    Kind.StyleClose -> if (fonts.isNotEmpty()) fonts.pop()
                    Kind.LinkOpen -> if (link == null) link = f.text else {
                        val tail = rest(); val close = tail.indexOfFirst { it.kind == Kind.LinkClose }
                        if (close >= 0) current.items = current.items.toMutableList().apply { removeAt(current.index + close) }
                    }
                    Kind.LinkClose -> link = null
                    Kind.Prefix -> {
                        val old = prefix; val oldA = prefixA; val oldTrimmed = trimmed; val oldLen = prefixLen
                        prefix(prefix + f.text)
                        push(normalize(f.children)) { prefix = old; prefixA = oldA; trimmed = oldTrimmed; prefixLen = oldLen }
                    }
                    Kind.Flush -> { val old = usePrefix; usePrefix = false; push(normalize(f.children)) { usePrefix = old } }
                    Kind.Before -> rest().firstOrNull(::printable)?.let { if (!startsBlank(it)) push(normalize(f.children)) }
                    Kind.Blanks -> if (newlines <= f.width) { var n = 1 + f.width - newlines; while (n-- > 0) newline() }
                    Kind.CR -> if (newlines <= 0) newline()
                    Kind.NL -> newline()
                    Kind.Space -> {
                        while (current.index < current.items.size && current.items[current.index].kind == Kind.Space) current.index++
                        val off = rest().takeWhile { !breakable(it) }.fold(0L) { a, x -> a + offset(x) }
                        if (lineLength != null && column + 1 + off > lineLength) newline() else if (column > 0) out(1, chars.fromString(" "))
                    }
                    Kind.After -> if (newlines > 0) push(f.children)
                    Kind.Block, Kind.Fill -> {
                        val blocks = mutableListOf(f)
                        while (current.index < current.items.size && block(current.items[current.index])) blocks.add(current.items[current.index++])
                        val height = blocks.maxOf { if (it.kind == Kind.Block) it.lines.size else 1 }
                        var combinedWidth = blocks.first().width
                        fun spec(x: Flat<A>): List<Pair<Long, Attributed<A>>> = if (x.kind == Kind.Block) x.lines.map { DisplayWidth.length(attributed.points(it)) to it }
                            else List(height) { length(checkNotNull(x.value)) to Attributed(listOf(AttributedPart<A>(link, font, checkNotNull(x.value)))) }
                        var combined = spec(blocks.first()).take(height)
                        for (b in blocks.drop(1)) {
                            val other = spec(b)
                            combined = List(height) { i ->
                                val left = combined.getOrNull(i) ?: (0L to attributed.empty)
                                val right = other.getOrNull(i) ?: (0L to attributed.empty)
                                (combinedWidth + right.first) to (left.second + attributed.replicateChar(combinedWidth - left.first, 32) + right.second)
                            }; combinedWidth += b.width
                        }
                        val old = prefix; val oldA = prefixA; val oldTrimmed = trimmed; val oldLen = prefixLen
                        val pad = column - oldLen
                        if (pad > 0) { prefix += repeatPoint(pad, 32); prefixA = chars.append(prefixA, chars.replicateChar(pad, 32)); prefixLen += pad }
                        val cooked = combined.mapNotNull { (_, line) -> if (attributed.isNull(line)) null else Flat(Kind.Cooked, DisplayWidth.length(attributed.points(line)), attributed = line) }
                        val lines = cooked.flatMapIndexed { i, x -> if (i == 0) listOf(x) else listOf(Flat(Kind.CR), x) }
                        push(lines) { prefix = old; prefixA = oldA; trimmed = oldTrimmed; prefixLen = oldLen }
                    }
                }
            }
            return Attributed(output)
        }
    }
    companion object {
        val Text = DocLayout(TextChars)
        val String = DocLayout(CharacterStringChars)
        val Characters get() = String
    }
}
private fun haskellSpace(c: Int) = c in 9..13 || c == 32 || c == 160 || c == 5760 || c in 8192..8202 || c == 8239 || c == 8287 || c == 12288
private fun haskellSpace(c: Char) = haskellSpace(c.code)
