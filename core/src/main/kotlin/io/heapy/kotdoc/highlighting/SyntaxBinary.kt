// Skylighting.Types 0.15 / Data.Binary.Class, Generic and Get 0.8.9.3.
// Copyright John MacFarlane and binary/text contributors. BSD;
// licenses/Skylighting-core-BSD-3-Clause.txt, Binary-BSD-3-Clause.txt, text-BSD-2-Clause.txt.
package io.heapy.kotdoc.highlighting

import io.heapy.kotdoc.ast.UnicodeTextOrder
import io.heapy.kotdoc.highlighting.regex.RE
import java.io.ByteArrayOutputStream

sealed class SyntaxBinaryResult<out A>(remaining: ByteArray, val consumed: Long) {
    private val rest = remaining.copyOf()
    val remaining get() = rest.copyOf()
    class Success<A>(val value: A, remaining: ByteArray, consumed: Long) : SyntaxBinaryResult<A>(remaining, consumed)
    class Failure(val error: String, remaining: ByteArray, consumed: Long) : SyntaxBinaryResult<Nothing>(remaining, consumed)
}

/** Original Binary instances, including decodeOrFail's unconsumed input and offsets. */
class SyntaxBinaryCodec<A> internal constructor(internal val shape: BinaryShape) {
    fun encode(value: A): ByteArray = BinaryEngine.encode(shape, value)
    @Suppress("UNCHECKED_CAST")
    fun decode(bytes: ByteArray): SyntaxBinaryResult<A> = BinaryEngine.decode(shape, bytes) as SyntaxBinaryResult<A>
}

internal sealed interface BinaryShape {
    class Scalar(val put: (BinaryOutput, Any?) -> Unit, val get: (BinaryInput) -> Any?) : BinaryShape
    class Product(val fields: List<BinaryShape>, val split: (Any?) -> List<Any?>, val build: (List<Any?>) -> Any?) : BinaryShape
    class Sum(val constructors: List<BinaryShape>, val tag: (Any?) -> Int, val strict: Boolean = true) : BinaryShape
    class Sequence(val element: BinaryShape, val split: (Any?) -> List<Any?>, val build: (List<Any?>) -> Any?) : BinaryShape
    class Ref(val target: () -> BinaryShape) : BinaryShape
}
internal class BinaryOutput {
    private val output = ByteArrayOutputStream()
    fun byte(value: Int) { output.write(value) }
    fun bytes(value: ByteArray) { output.write(value) }
    fun int(value: Long) { for (shift in 56 downTo 0 step 8) byte((value ushr shift).toInt()) }
    fun char(point: Int) {
        require(point in 0..0x10ffff)
        when {
            point < 0x80 -> byte(point)
            point < 0x800 -> { byte(0xc0 or (point ushr 6)); byte(0x80 or (point and 63)) }
            point < 0x10000 -> { byte(0xe0 or (point ushr 12)); byte(0x80 or ((point ushr 6) and 63)); byte(0x80 or (point and 63)) }
            else -> { byte(0xf0 or (point ushr 18)); byte(0x80 or ((point ushr 12) and 63)); byte(0x80 or ((point ushr 6) and 63)); byte(0x80 or (point and 63)) }
        }
    }
    fun result() = output.toByteArray()
}
internal class BinaryInput(val bytes: ByteArray) {
    var position = 0
    class Failure(val error: String) : RuntimeException(null, null, false, false)
    fun fail(error: String): Nothing = throw Failure(error)
    fun raw(count: Long): ByteArray {
        val n = count.coerceAtLeast(0)
        if (n > bytes.size - position) fail("not enough bytes")
        return bytes.copyOfRange(position, position + n.toInt()).also { position += n.toInt() }
    }
    fun byte(): Int { if (position == bytes.size) fail("not enough bytes"); return bytes[position++].toInt() and 255 }
    fun int(): Long { val data = raw(8); var result = 0L; for (b in data) result = (result shl 8) or (b.toLong() and 255); return result }
    fun char(): Int {
        val w = byte()
        val point = when {
            w < 0x80 -> w
            w < 0xe0 -> (byte() xor 0x80) or ((w xor 0xc0) shl 6)
            w < 0xf0 -> { val x = byte() xor 0x80; val y = byte() xor 0x80; y or ((x or ((w xor 0xe0) shl 6)) shl 6) }
            else -> { val x = byte() xor 0x80; val y = byte() xor 0x80; val z = byte() xor 0x80; z or ((y or ((x or ((w xor 0xf0) shl 6)) shl 6)) shl 6) }
        }
        if (point > 0x10ffff) fail("Not a valid Unicode code point!")
        return point
    }
    fun text(): String {
        val data = raw(int()); var i = 0
        while (i < data.size) {
            val first = data[i].toInt() and 255
            val length = when (first) { in 0..127 -> 1; in 0xc2..0xdf -> 2; in 0xe0..0xef -> 3; in 0xf0..0xf4 -> 4; else -> 0 }
            fun bad(byte: Int = first): Nothing = fail("Cannot decode byte '\\x${byte.toString(16)}': Data.Text.Encoding: Invalid UTF-8 stream")
            if (length == 0) bad()
            for (k in 1..<length) {
                if (i + k == data.size) bad(data.last().toInt() and 255)
                val next = data[i + k].toInt() and 255
                val range = when { k != 1 -> 0x80..0xbf; first == 0xe0 -> 0xa0..0xbf; first == 0xed -> 0x80..0x9f
                    first == 0xf0 -> 0x90..0xbf; first == 0xf4 -> 0x80..0x8f; else -> 0x80..0xbf }
                if (next !in range) bad()
            }
            i += length
        }
        return data.toString(Charsets.UTF_8)
    }
}
private object BinaryEngine {
    private class Write(val shape: BinaryShape, val value: Any?)
    fun encode(shape: BinaryShape, value: Any?): ByteArray {
        val output = BinaryOutput(); val tasks = ArrayDeque<Write>(); tasks.addLast(Write(shape, value))
        while (tasks.isNotEmpty()) {
            val task = tasks.removeLast()
            when (val s = task.shape) {
                is BinaryShape.Scalar -> s.put(output, task.value)
                is BinaryShape.Ref -> tasks.addLast(Write(s.target(), task.value))
                is BinaryShape.Sum -> { val tag = s.tag(task.value); output.byte(tag); tasks.addLast(Write(s.constructors[tag], task.value)) }
                is BinaryShape.Product -> { val fields = s.split(task.value); for (i in s.fields.indices.reversed()) tasks.addLast(Write(s.fields[i], fields[i])) }
                is BinaryShape.Sequence -> { val items = s.split(task.value); output.int(items.size.toLong()); for (i in items.indices.reversed()) tasks.addLast(Write(s.element, items[i])) }
            }
        }
        return output.result()
    }
    private class Read(val shape: BinaryShape, val accept: (Any?) -> Unit)
    fun decode(shape: BinaryShape, source: ByteArray): SyntaxBinaryResult<Any?> {
        val input = BinaryInput(source.copyOf()); val tasks = ArrayDeque<Any>(); var result: Any? = null
        tasks.addLast(Read(shape) { result = it })
        return try {
            while (tasks.isNotEmpty()) when (val task = tasks.removeLast()) {
                is Read -> when (val s = task.shape) {
                    is BinaryShape.Scalar -> task.accept(s.get(input))
                    is BinaryShape.Ref -> tasks.addLast(Read(s.target(), task.accept))
                    is BinaryShape.Sum -> {
                        val tag = input.byte()
                        if (s.strict && tag >= s.constructors.size) input.fail("Unknown encoding for constructor")
                        tasks.addLast(Read(s.constructors[if (s.strict) tag else if (tag == 0) 0 else 1], task.accept))
                    }
                    is BinaryShape.Product -> {
                        val fields = arrayOfNulls<Any?>(s.fields.size)
                        tasks.addLast({ task.accept(s.build(fields.toList())) })
                        for (i in s.fields.indices.reversed()) tasks.addLast(Read(s.fields[i]) { fields[i] = it })
                    }
                    is BinaryShape.Sequence -> {
                        var remaining = input.int(); val values = mutableListOf<Any?>()
                        lateinit var next: () -> Unit
                        next = {
                            if (remaining == 0L) task.accept(s.build(values))
                            else { remaining--; tasks.addLast(next); tasks.addLast(Read(s.element) { values.add(it) }) }
                        }
                        tasks.addLast(next)
                    }
                }
                is Function0<*> -> task.invoke()
            }
            SyntaxBinaryResult.Success(result, input.bytes.copyOfRange(input.position, input.bytes.size), input.position.toLong())
        } catch (e: BinaryInput.Failure) {
            SyntaxBinaryResult.Failure(e.error, input.bytes.copyOfRange(input.position, input.bytes.size), input.position.toLong())
        }
    }
}

@Suppress("UNCHECKED_CAST")
object SyntaxBinary {
    private fun scalar(put: (BinaryOutput, Any?) -> Unit, get: (BinaryInput) -> Any?) = BinaryShape.Scalar(put, get)
    private fun record(fields: List<BinaryShape>, split: (Any?) -> List<Any?>, build: (List<Any?>) -> Any?) = BinaryShape.Product(fields, split, build)
    private fun unit(value: Any?) = record(emptyList(), { emptyList() }, { value })
    private fun list(shape: BinaryShape) = BinaryShape.Sequence(shape, { (it as Iterable<*>).toList() }, { syntaxList(it) })
    private fun <A> set(shape: BinaryShape, comparator: Comparator<A>) = BinaryShape.Sequence(shape, {
        (it as Set<A>).let { s -> if (s is BinaryAscSet<A>) s.toList() else s.sortedWith(comparator) }
    }, { BinaryAscSet(it as List<A>, comparator) })
    private fun map(shape: BinaryShape): BinaryShape {
        val pair = record(listOf(textShape, shape), { (it as Pair<*, *>).let { p -> listOf(p.first, p.second) } }, { it[0] to it[1] })
        return BinaryShape.Sequence(pair, { syntaxMap(it as Map<String, *>).entries.map { e -> e.key to e.value } }, { BinaryAscMap(it as List<Pair<String, Any?>>, UnicodeTextOrder) })
    }
    private val intShape = scalar({ o, v -> o.int(v as Long) }, { it.int() })
    private val charShape = scalar({ o, v -> o.char(v as Int) }, { it.char() })
    private val boolShape = scalar({ o, v -> o.byte(if (v as Boolean) 1 else 0) }, {
        val b = it.byte(); if (b > 1) it.fail("Could not map value $b to Bool"); b == 1
    })
    private val bytesShape = scalar({ o, v -> val b = v as ByteArray; o.int(b.size.toLong()); o.bytes(b) }, { it.raw(it.int()) })
    private val textShape = scalar({ o, v ->
        val safe = buildString { (v as String).codePoints().forEach { appendCodePoint(if (it in 0xd800..0xdfff) 0xfffd else it) } }
        val b = safe.toByteArray(Charsets.UTF_8); o.int(b.size.toLong()); o.bytes(b)
    }, { it.text() })
    private val charsShape = list(charShape)
    private val stringShape = record(listOf(charsShape), { listOf((it as HaskellChars).points) }, { HaskellChars(it[0] as List<Int>) })
    private val contextNameShape = record(listOf(textShape, textShape), { (it as ContextName).fields }, { ContextName(it[0] as String, it[1] as String) })
    private val charSetShape = set(charShape, Comparator.naturalOrder<Int>())
    private val keywordShape = record(listOf(boolShape, charSetShape), { (it as KeywordAttr).fields }, { KeywordAttr(it[0] as Boolean, it[1] as Set<Int>) })
    private fun <A> wordsShape(element: BinaryShape, operations: WordOperations<A>): BinaryShape {
        val set = set(element, operations)
        val sensitive = record(listOf(set), { listOf((it as WordSet<*>).words) }, { WordSet.CaseSensitiveWords(it[0] as Set<A>, operations) })
        val insensitive = record(listOf(set), { listOf((it as WordSet<*>).words) }, { WordSet.CaseInsensitiveWords(it[0] as Set<A>, operations) })
        return BinaryShape.Sum(listOf(sensitive, insensitive), { if ((it as WordSet<*>).caseSensitive) 0 else 1 })
    }
    private val wordsTextShape = wordsShape(textShape, TextWordOperations)
    private val keywordWordsShape = BinaryShape.Sum(listOf(
        record(listOf(textShape), { listOf((it as KeywordWords.Unresolved).name) }, { KeywordWords.Unresolved(it[0] as String) }),
        record(listOf(wordsTextShape), { listOf((it as KeywordWords.Resolved).words) }, { KeywordWords.Resolved(it[0] as WordSet<String>) })
    ), { if (it is KeywordWords.Unresolved) 0 else 1 }, strict = false)
    private val reShape = record(listOf(bytesShape, boolShape, boolShape), { (it as RE).let { re -> listOf(re.bytes, re.caseSensitive, re.minimal) } }, { RE(it[0] as ByteArray, it[1] as Boolean, it[2] as Boolean) })
    private val matcherShape: BinaryShape = BinaryShape.Sum(listOf(
        record(listOf(charShape), { (it as Matcher).fields.drop(1) }, { Matcher.DetectChar(it[0] as Int) }),
        record(listOf(charShape, charShape), { (it as Matcher).fields.drop(1) }, { Matcher.Detect2Chars(it[0] as Int, it[1] as Int) }),
        record(listOf(charSetShape), { (it as Matcher).fields.drop(1) }, { Matcher.AnyChar(it[0] as Set<Int>) }),
        record(listOf(charShape, charShape), { (it as Matcher).fields.drop(1) }, { Matcher.RangeDetect(it[0] as Int, it[1] as Int) }),
        record(listOf(textShape), { (it as Matcher).fields.drop(1) }, { Matcher.StringDetect(it[0] as String) }),
        record(listOf(textShape), { (it as Matcher).fields.drop(1) }, { Matcher.WordDetect(it[0] as String) }),
        record(listOf(reShape), { (it as Matcher).fields.drop(1) }, { Matcher.RegExpr(it[0] as RE) }),
        record(listOf(keywordShape, keywordWordsShape), { (it as Matcher).fields.drop(1) }, { Matcher.Keyword(it[0] as KeywordAttr, it[1] as KeywordWords) }),
        unit(Matcher.Int), unit(Matcher.Float), unit(Matcher.HlCOct), unit(Matcher.HlCHex), unit(Matcher.HlCStringChar), unit(Matcher.HlCChar),
        record(listOf(charShape), { (it as Matcher).fields.drop(1) }, { Matcher.LineContinue(it[0] as Int) }),
        record(listOf(contextNameShape), { (it as Matcher).fields.drop(1) }, { Matcher.IncludeRules(it[0] as ContextName) }),
        unit(Matcher.DetectSpaces), unit(Matcher.DetectIdentifier)
    ), { (it as Matcher).fields[0] as Int })
    private val switchShape = BinaryShape.Sum(listOf(unit(ContextSwitch.Pop),
        record(listOf(contextNameShape), { (it as ContextSwitch).fields.drop(1) }, { ContextSwitch.Push(it[0] as ContextName) })
    ), { if (it is ContextSwitch.Pop) 0 else 1 })
    private val itemShape = BinaryShape.Sum(listOf(
        record(listOf(textShape), { (it as ListItem).fields.drop(1) }, { ListItem.Item(it[0] as String) }),
        record(listOf(contextNameShape), { (it as ListItem).fields.drop(1) }, { ListItem.IncludeList(it[0] as ContextName) })
    ), { if (it is ListItem.Item) 0 else 1 })
    private val tokenShape = scalar({ o, v -> o.byte((v as TokenType).ordinal) }, { val b = it.byte(); TokenType.entries.getOrNull(b) ?: it.fail("Unknown encoding for constructor") })
    private val columnShape = BinaryShape.Sum(listOf(unit(null), record(listOf(intShape), { listOf(it) }, { it[0] })), { if (it == null) 0 else 1 }, strict = false)
    private val ruleShape: BinaryShape by lazy { record(listOf(matcherShape, tokenShape, boolShape, charSetShape, boolShape, boolShape, list(BinaryShape.Ref { ruleShape }), boolShape, boolShape, columnShape, list(switchShape)),
        { (it as Rule).fields }, { Rule(it[0] as Matcher, it[1] as TokenType, it[2] as Boolean, it[3] as Set<Int>, it[4] as Boolean, it[5] as Boolean, it[6] as List<Rule>, it[7] as Boolean, it[8] as Boolean, it[9] as Long?, it[10] as List<ContextSwitch>) }) }
    private val contextShape by lazy { record(listOf(textShape, textShape, list(ruleShape), tokenShape, list(switchShape), list(switchShape), boolShape, list(switchShape), boolShape),
        { (it as Context).fields }, { Context(it[0] as String, it[1] as String, it[2] as List<Rule>, it[3] as TokenType, it[4] as List<ContextSwitch>, it[5] as List<ContextSwitch>, it[6] as Boolean, it[7] as List<ContextSwitch>, it[8] as Boolean) }) }
    private val syntaxShape by lazy { record(listOf(textShape, stringShape, textShape, map(list(itemShape)), map(contextShape), textShape, textShape, textShape, list(stringShape), textShape),
        { (it as Syntax).fields }, { Syntax.fromBinary(it[0] as String, (it[1] as HaskellChars).points, it[2] as String, it[3] as Map<String, List<ListItem>>, it[4] as Map<String, Context>, it[5] as String, it[6] as String, it[7] as String, (it[8] as List<HaskellChars>).map { s -> s.points }, it[9] as String) }) }

    val int64 = SyntaxBinaryCodec<Long>(intShape)
    val char = SyntaxBinaryCodec<Int>(charShape)
    val string = SyntaxBinaryCodec<List<Int>>(charsShape)
    val text = SyntaxBinaryCodec<String>(textShape)
    val contextName = SyntaxBinaryCodec<ContextName>(contextNameShape)
    val keyword = SyntaxBinaryCodec<KeywordAttr>(keywordShape)
    val wordSet = SyntaxBinaryCodec<WordSet<String>>(wordsTextShape)
    fun <A> wordSet(element: SyntaxBinaryCodec<A>, operations: WordOperations<A>) = SyntaxBinaryCodec<WordSet<A>>(wordsShape(element.shape, operations))
    val matcher = SyntaxBinaryCodec<Matcher>(matcherShape)
    val contextSwitch = SyntaxBinaryCodec<ContextSwitch>(switchShape)
    val listItem = SyntaxBinaryCodec<ListItem>(itemShape)
    val tokenType = SyntaxBinaryCodec<TokenType>(tokenShape)
    val rule by lazy { SyntaxBinaryCodec<Rule>(ruleShape) }
    val context by lazy { SyntaxBinaryCodec<Context>(contextShape) }
    val syntax by lazy { SyntaxBinaryCodec<Syntax>(syntaxShape) }
    val syntaxMap by lazy { SyntaxBinaryCodec<SyntaxMap>(map(syntaxShape)) }
    internal val characterSet = SyntaxBinaryCodec<Set<Int>>(charSetShape)
    internal val textIntMap = SyntaxBinaryCodec<Map<String, Long>>(map(intShape))
}
