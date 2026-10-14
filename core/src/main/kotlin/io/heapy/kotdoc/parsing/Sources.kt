// Text.Pandoc.Sources, Pandoc 3.12.1.
// Copyright (C) 2021-2024 John MacFarlane; GPL-2.0-or-later.
// Text.concat demand rules: (c) 2008-2012 Bryan O'Sullivan, Duncan Coutts,
// Tom Harper; (c) 2021 Andrew Lelechenko. BSD-2-Clause, licenses/text-BSD-2-Clause.txt.
package io.heapy.kotdoc.parsing

import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.layout.CharacterString

fun interface ToSources<in A> {
    fun toSources(value: A): Sources
}
/** Dictionaries retain ignored input demand for the ordinary stream instances. */
fun interface UpdateSourcePos<in A> {
    fun updateSourcePos(position: Deferred<SourcePos>, point: Deferred<Int>, remaining: Deferred<A>): Deferred<SourcePos>
    fun updateSourcePos(position: SourcePos, point: Int, remaining: Deferred<A>): SourcePos =
        updateSourcePos(Deferred.now(position), Deferred.now(point), remaining).value()
}
object SourceConversions {
    val text = ToSources<String>(Sources::fromText)
    val characters = ToSources<CharacterString> { Sources.fromPoints(it.points()) }
    val files = ToSources<List<Pair<String, String>>>(Sources::fromFiles)
    val sources = ToSources<Sources>(Sources::toSources)
}
object SourcePositionInstances {
    val text = UpdateSourcePos<TextStream> { p,c,_ -> p.flatMapValue { pos -> c.mapValue(pos::advance) } }
    val characters = UpdateSourcePos<CharacterListStream> { p,c,_ -> p.flatMapValue { pos -> c.mapValue(pos::advance) } }
    val bytes = UpdateSourcePos<ByteStream> { p,c,_ -> p.flatMapValue { pos -> c.mapValue(pos::advance) } }
    val lazyBytes = UpdateSourcePos<LazyByteStream> { p,c,_ -> p.flatMapValue { pos -> c.mapValue(pos::advance) } }
    val sources = UpdateSourcePos<Sources> { p,c,s -> s.flatMapValue { it.updateSourcePos(p,c) } }
}

/** A persistent list whose spine and elements can be demanded independently. */
sealed interface DeferredChain<out A> {
    data object Nil : DeferredChain<Nothing>
    class Cons<A>(val head: Deferred<A>, val tail: Deferred<DeferredChain<A>>) : DeferredChain<A>
    companion object {
        fun <A> of(values: List<A>): Deferred<DeferredChain<A>> {
            val snapshot = values.toList()
            var result: Deferred<DeferredChain<A>> = Deferred.now(Nil)
            // Materialized Kotlin lists already have a finite spine. Build its
            // persistent counterpart without closures retaining consumed prefixes.
            for (index in snapshot.indices.reversed()) result = Deferred.now(Cons(Deferred.now(snapshot[index]), result))
            return result
        }
    }
}

data class CharacterStep(val token: Deferred<Int>, val tail: CharacterStream)
interface CharacterStream {
    fun uncons(): CharacterStep?
    /** Called on the remaining stream, as in Parsec.tokenPrim. */
    fun updateSourcePos(position: SourcePos, point: Int): SourcePos = position.advance(point)
}

/** Strict Text slices share backing storage while characters are consumed. */
internal class SourceText(val backing: String, val start: Int = 0, val end: Int = backing.length) {
    val empty get() = start == end
    val point get() = backing.codePointAt(start)
    fun tail() = SourceText(backing, start + Character.charCount(point), end)
    fun materialize() = backing.substring(start, end)
}
class TextStream private constructor(private val text: SourceText) : CharacterStream {
    constructor(text: String) : this(SourceText(TextChars.fromString(text)))
    private val step = Deferred.later {
        if (text.empty) null else CharacterStep(Deferred.now(text.point), TextStream(text.tail()))
    }
    override fun uncons() = step.value()
    fun text() = text.materialize()
}
/** Unlike Text, raw [Char] retains individual surrogate code points. */
class CharacterListStream(val characters: Deferred<DeferredChain<Int>>) : CharacterStream {
    constructor(points: IntArray) : this(DeferredChain.of(points.toList().also { require(it.all { p -> p in 0..0x10ffff }) }))
    private val step = characters.mapValue { chain -> when (chain) {
        DeferredChain.Nil -> null
        is DeferredChain.Cons -> CharacterStep(chain.head, CharacterListStream(chain.tail))
    } }
    override fun uncons() = step.value()
    fun points(): IntArray {
        val points = mutableListOf<Int>(); var chain = characters.value()
        while (chain is DeferredChain.Cons) { points.add(chain.head.value()); chain = chain.tail.value() }
        return points.toIntArray()
    }
}
class ByteStream private constructor(private val bytes: ByteArray, private val offset: Int) : CharacterStream {
    constructor(bytes: ByteArray) : this(bytes.copyOf(), 0)
    override fun uncons() = if (offset == bytes.size) null else
        CharacterStep(Deferred.now(bytes[offset].toInt() and 255), ByteStream(bytes, offset + 1))
    fun bytes() = bytes.copyOfRange(offset, bytes.size)
    internal val empty get() = offset == bytes.size
}
/** Lazy ByteString's chunks; empty chunks are eliminated by fromChunks. */
class LazyByteStream private constructor(val chunks: Deferred<DeferredChain<ByteStream>>) : CharacterStream {
    private val step = Deferred.later {
        var chain = chunks.value()
        while (chain is DeferredChain.Cons) {
            val first = chain.head.value()
            val token = first.uncons()
            if (token != null) {
                val rest = token.tail as ByteStream
                val tail = if (rest.empty) chain.tail else Deferred.now(DeferredChain.Cons(Deferred.now(rest), chain.tail))
                return@later CharacterStep(token.token, LazyByteStream(tail))
            }
            chain = chain.tail.value()
        }
        null
    }
    override fun uncons() = step.value()
    fun byteChunks(): List<ByteArray> {
        val result = mutableListOf<ByteArray>(); var chain = chunks.value()
        while (chain is DeferredChain.Cons) { chain.head.value().bytes().takeIf { it.isNotEmpty() }?.let(result::add); chain = chain.tail.value() }
        return result
    }
    companion object {
        fun fromChunks(chunks: List<ByteArray>) = fromDeferredChunks(DeferredChain.of(chunks.map(::ByteStream)))
        fun fromDeferredChunks(chunks: Deferred<DeferredChain<ByteStream>>): LazyByteStream {
            fun normalize(input: Deferred<DeferredChain<ByteStream>>): Deferred<DeferredChain<ByteStream>> = Deferred.later {
                var chain = input.value()
                while (chain is DeferredChain.Cons) {
                    if (!chain.head.value().empty) return@later DeferredChain.Cons(chain.head, normalize(chain.tail))
                    chain = chain.tail.value()
                }
                DeferredChain.Nil
            }
            return LazyByteStream(normalize(chunks))
        }
    }
}

/** Source tuple fields remain independent, including undefined unused positions. */
class SourceChunk private constructor(val deferredPosition: Deferred<SourcePos>, internal val deferredText: Deferred<SourceText>) {
    constructor(position: SourcePos, text: String) : this(Deferred.now(position), Deferred.now(SourceText(TextChars.fromString(text))))
    val position get() = deferredPosition.value()
    val text get() = deferredText.value().materialize()
    internal fun withText(text: SourceText) = SourceChunk(deferredPosition, Deferred.now(text))
    internal fun withPosition(position: SourcePos) = SourceChunk(Deferred.now(position), deferredText)
    companion object {
        fun deferred(position: Deferred<SourcePos>, text: Deferred<String>) = SourceChunk(position, text.mapValue { SourceText(TextChars.fromString(it)) })
    }
}

/** The original list of (position, Text), retaining empty chunks and lazy tails. */
class Sources(val unSources: Deferred<DeferredChain<SourceChunk>>) : CharacterStream {
    constructor(chunks: List<SourceChunk>) : this(DeferredChain.of(chunks))
    private val step = Deferred.later {
        var chain = unSources.value()
        while (chain is DeferredChain.Cons) {
            val chunk = chain.head.value(); val text = chunk.deferredText.value()
            if (!text.empty) return@later CharacterStep(Deferred.now(text.point),
                Sources(Deferred.now(DeferredChain.Cons(Deferred.now(chunk.withText(text.tail())), chain.tail))))
            chain = chain.tail.value()
        }
        null
    }
    override fun uncons() = step.value()
    override fun updateSourcePos(position: SourcePos, point: Int): SourcePos =
        updateSourcePos(Deferred.now(position), Deferred.now(point)).value()
    fun updateSourcePos(position: Deferred<SourcePos>, point: Deferred<Int>): Deferred<SourcePos> = Deferred.defer {
        val chain = unSources.value()
        if (chain === DeferredChain.Nil) return@defer position.flatMapValue { pos -> point.mapValue(pos::advance) }
        chain as DeferredChain.Cons
        // The original two-tuple pattern demands both constructors before testing Text.null.
        val first = chain.head.value()
        val rest = chain.tail.value()
        if (rest is DeferredChain.Cons) {
            val next = rest.head.value()
            if (first.deferredText.value().empty) return@defer next.deferredPosition
        }
        position.flatMapValue { pos -> point.mapValue { advanceSourceCharacter(pos, it) } }
    }
    fun chunks(): List<SourceChunk> {
        val result = mutableListOf<SourceChunk>(); var chain = unSources.value()
        while (chain is DeferredChain.Cons) { result.add(chain.head.value()); chain = chain.tail.value() }
        return result
    }
    fun sourcesToText(): String {
        val first = unSources.value()
        if (first === DeferredChain.Nil) return ""
        first as DeferredChain.Cons
        // Text.mconcat inspects the singleton-list pattern before reading Text.
        if (first.tail.value() === DeferredChain.Nil) return first.head.value().text
        return buildString {
            var chain: DeferredChain<SourceChunk> = first
            while (chain is DeferredChain.Cons) { append(chain.head.value().text); chain = chain.tail.value() }
        }
    }
    fun initialSourceName(): String = when (val chain = unSources.value()) {
        DeferredChain.Nil -> ""
        is DeferredChain.Cons -> chain.head.value().position.name
    }
    operator fun plus(other: Sources): Sources {
        fun append(input: Deferred<DeferredChain<SourceChunk>>): Deferred<DeferredChain<SourceChunk>> = input.flatMapValue { chain ->
            when (chain) {
                DeferredChain.Nil -> other.unSources
                is DeferredChain.Cons -> Deferred.now(DeferredChain.Cons(chain.head, Deferred.defer { append(chain.tail) }))
            }
        }
        return Sources(Deferred.defer { append(unSources) })
    }
    fun ensureFinalNewlines(count: Long): Sources {
        // NonEmpty.last demands the finite spine, but not any earlier tuple fields.
        val prefix = mutableListOf<Deferred<SourceChunk>>(); var chain = unSources.value()
        while (chain is DeferredChain.Cons) { prefix.add(chain.head); chain = chain.tail.value() }
        if (prefix.isEmpty()) return Sources(listOf(SourceChunk(SourcePos(""), newlines(count))))
        val last = prefix.last().value(); val text = last.text
        val have = text.length - text.trimEnd('\n').length
        if (have >= count) return this
        prefix[prefix.lastIndex] = Deferred.now(SourceChunk.deferred(last.deferredPosition, Deferred.now(text + newlines(count - have))))
        var result: Deferred<DeferredChain<SourceChunk>> = Deferred.now(DeferredChain.Nil)
        for (head in prefix.asReversed()) result = Deferred.now(DeferredChain.Cons(head, result))
        return Sources(result)
    }
    fun show(precedence: Int = 0): String {
        val body = "Sources {unSources = " + chunks().joinToString(",", "[", "]") {
            "(${it.position},${HaskellString.quote(it.text)})"
        } + "}"
        return if (precedence > 10) "($body)" else body
    }
    override fun toString() = show()
    companion object {
        fun empty() = Sources(emptyList())
        fun concat(values: List<Sources>) = concat(DeferredChain.of(values))
        fun concat(values: Deferred<DeferredChain<Sources>>): Sources = Sources(values.flatMapValue { chain ->
            when (chain) {
                DeferredChain.Nil -> Deferred.now(DeferredChain.Nil)
                is DeferredChain.Cons -> Deferred.defer { (chain.head.value() + concat(chain.tail)).unSources }
            }
        })
        fun fromString(text: String) = fromText(text)
        fun fromPoints(points: IntArray) = fromText(TextChars.fromPoints(points.filter { it != 13 }.toIntArray()))
        fun fromText(text: String) = Sources(listOf(SourceChunk(SourcePos(""), text.replace("\r", ""))))
        fun fromFiles(files: List<Pair<String, String>>) = Sources(files.map { (name, text) -> SourceChunk(SourcePos(name), text.replace("\r", "") + "\n") })
        fun toSources(sources: Sources) = sources
        private fun newlines(count: Long): String {
            if (count <= 0) return ""
            require(count <= Int.MAX_VALUE) { "Sources exceeds JVM string capacity" }
            return "\n".repeat(count.toInt())
        }
    }
}

private fun advanceSourceCharacter(position: SourcePos, point: Int) = when (point) {
    10 -> position.copy(line = position.line + 1, column = 1)
    9 -> position.copy(column = position.column + 4 - Math.floorMod(position.column - 1, 4))
    else -> position.copy(column = position.column + 1)
}

/** All Sources character parsers use the remaining stream's UpdateSourcePos rule. */
object SourceParsers {
    fun <U> satisfy(predicate: (Int) -> Boolean) = PandocTextCharacters.satisfy<U>(predicate)
    fun <U> oneOf(points: IntArray): Parsec<U, Int> { val copy = points.copyOf(); return satisfy { it in copy } }
    fun <U> noneOf(points: IntArray): Parsec<U, Int> { val copy = points.copyOf(); return satisfy { it !in copy } }
    fun <U> anyChar() = satisfy<U> { true }
    fun <U> char(point: Int) = satisfy<U> { it == point }
    fun <U> string(points: IntArray): Parsec<U, List<Int>> {
        class Link(val point: Int, val previous: Link?)
        var parser = Parsec.pure<U, Link?>(null)
        for (point in points) parser = parser.flatMap { previous -> char<U>(point).map { Link(it, previous) } }
        return parser.map { last ->
            val result = ArrayList<Int>(points.size); var link = last
            while (link != null) { result.add(link.point); link = link.previous }; result.reverse(); result
        }
    }
    fun <U> string(text: String) = string<U>(text.codePoints().toArray()).map { buildString { it.forEach(::appendCodePoint) } }
    fun <U> newline() = char<U>(10)
    fun <U> space() = satisfy<U>(ParserCharacters::isSpace)
    fun <U> spaces() = space<U>().skipMany().label("white space")
    fun <U> letter() = satisfy<U>(ParserCharacters::isAlpha)
    fun <U> alphaNum() = satisfy<U>(ParserCharacters::isAlphaNum)
    fun <U> digit() = satisfy<U>(ParserCharacters::isDigit)
    fun <U> hexDigit() = satisfy<U> { it in 48..57 || it in 65..70 || it in 97..102 }
    fun <U> addToSources(position: SourcePos, text: String): Parsec<U, Unit> = Parsec.modify { state ->
        val sources = requireSources(state.input)
        val tail = sources.unSources.mapValue { chain -> when (chain) {
            DeferredChain.Nil -> chain
            is DeferredChain.Cons -> DeferredChain.Cons(chain.head.mapValue { it.withPosition(state.position) }, chain.tail)
        } }
        state.copy(input = ParseInput(Sources(Deferred.now(DeferredChain.Cons(
            Deferred.now(SourceChunk(position, text.replace("\r", ""))), tail)))))
    }
    fun <U> addToInput(text: String): Parsec<U, Unit> = Parsec.modify { state ->
        val sources = requireSources(state.input)
        val chain = when (val input = sources.unSources.value()) {
            DeferredChain.Nil -> DeferredChain.Cons(Deferred.now(SourceChunk(SourcePos(""), text)), Deferred.now(DeferredChain.Nil))
            is DeferredChain.Cons -> {
                val chunk = input.head.value()
                DeferredChain.Cons(Deferred.now(SourceChunk.deferred(chunk.deferredPosition,
                    chunk.deferredText.mapValue { TextChars.fromString(text) + it.materialize() })), input.tail)
            }
        }
        state.copy(input = ParseInput(Sources(Deferred.now(chain))))
    }
    private fun requireSources(input: ParseInput) = input.stream as? Sources ?: error("This parser requires a Sources stream")
    fun <U> takeWhileP(predicate: (Int) -> Boolean): Parsec<U, String> = span(predicate, false)
    fun <U> takeWhile1P(predicate: (Int) -> Boolean): Parsec<U, String> = span(predicate, true)
    private fun <U> span(predicate: (Int) -> Boolean, requireOne: Boolean): Parsec<U, String> = Parsec.primitive { state ->
        var committed = requireSources(state.input).unSources
        var position = state.position
        val result = StringBuilder()
        var message = ""
        while (true) {
            var inspected = committed.value()
            while (inspected is DeferredChain.Cons && inspected.head.value().deferredText.value().empty) inspected = inspected.tail.value()
            if (inspected === DeferredChain.Nil) break
            inspected as DeferredChain.Cons
            val chunk = inspected.head.value(); val text = chunk.deferredText.value()
            var at = text.start
            while (at < text.end) {
                val point = text.backing.codePointAt(at)
                if (!predicate(point)) break
                at += Character.charCount(point)
            }
            if (at == text.start) { message = Parsec.showChar(text.point); break }
            result.append(text.backing, text.start, at)
            if (at == text.end) {
                position = when (val rest = inspected.tail.value()) {
                    DeferredChain.Nil -> advance(position, text.backing, text.start, at)
                    is DeferredChain.Cons -> rest.head.value().position
                }
                committed = Deferred.now(DeferredChain.Cons(Deferred.now(chunk.withText(SourceText(text.backing, at, text.end))), inspected.tail))
            } else {
                position = advance(position, text.backing, text.start, at)
                committed = Deferred.now(DeferredChain.Cons(Deferred.now(chunk.withText(SourceText(text.backing, at, text.end))), inspected.tail))
                message = Parsec.showChar(text.backing.codePointAt(at)); break
            }
        }
        val consumed = result.isNotEmpty()
        val next = state.copy(input = ParseInput(Sources(committed)), position = position)
        val error = ParseError(position, listOf(ParseMessage(MessageKind.SysUnExpect, message)))
        ParseReply(!requireOne || consumed, if (!requireOne || consumed) result.toString() else null, next, consumed, error)
    }
    private fun advance(position: SourcePos, text: String, start: Int, end: Int): SourcePos {
        var result = position; var at = start
        while (at < end) { val point = text.codePointAt(at); result = advanceSourceCharacter(result, point); at += Character.charCount(point) }
        return result
    }
}
