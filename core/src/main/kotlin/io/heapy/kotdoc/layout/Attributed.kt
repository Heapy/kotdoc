// Text.DocLayout.{ANSIFont,Attributed,HasChars} 0.6, BSD-3-Clause.
// Copyright (C) 2010-2024 John MacFarlane; see licenses/DocLayout-BSD-3-Clause.txt.
package io.heapy.kotdoc.layout

import java.util.Collections

enum class Color8 { Black, Red, Green, Yellow, Blue, Magenta, Cyan, White }
enum class Underline { None, Single, Double, Curly }

sealed interface StyleRequest {
    data class Weight(val bold: Boolean) : StyleRequest
    data class Shape(val italic: Boolean) : StyleRequest
    data class Foreground(val color: Color8?) : StyleRequest
    data class Background(val color: Color8?) : StyleRequest
    data class Underlined(val underline: Underline) : StyleRequest
    data class Strikeout(val struck: Boolean) : StyleRequest
}

data class Font(
    val bold: Boolean = false, val italic: Boolean = false, val underline: Underline = Underline.None,
    val strikeout: Boolean = false, val foreground: Color8? = null, val background: Color8? = null,
    val link: String? = null,
) {
    fun apply(request: StyleRequest): Font = when (request) {
        is StyleRequest.Weight -> copy(bold = request.bold)
        is StyleRequest.Shape -> copy(italic = request.italic)
        is StyleRequest.Foreground -> copy(foreground = request.color)
        is StyleRequest.Background -> copy(background = request.color)
        is StyleRequest.Underlined -> copy(underline = request.underline)
        is StyleRequest.Strikeout -> copy(strikeout = request.struck)
    }
    fun render(): String = if (this == Base) "\u001b[0m" else listOf(
        if (bold) "1" else "22", if (italic) "3" else "23",
        foreground?.let { (30 + it.ordinal).toString() } ?: "39",
        background?.let { (40 + it.ordinal).toString() } ?: "49",
        when (underline) { Underline.None -> "24"; Underline.Single -> "4"; Underline.Double -> "21"; Underline.Curly -> "4:3" },
        if (strikeout) "9" else "29",
    ).joinToString("") { "\u001b[${it}m" }
    companion object { val Base = Font() }
}
fun renderOSC8(target: String?) = "\u001b]8;;${target ?: ""}\u001b\\"

data class AttributedPart<A>(val link: String?, val font: Font, val value: A) {
    fun append(other: AttributedPart<A>, chars: HasChars<A>) = copy(value = chars.append(value, other.value))
}
class Attributed<A>(parts: List<AttributedPart<A>>) {
    val parts: List<AttributedPart<A>> = Collections.unmodifiableList(parts.toList())
    operator fun plus(other: Attributed<A>) = Attributed(parts + other.parts)
    override fun equals(other: Any?) = other is Attributed<*> && parts == other.parts
    override fun hashCode() = parts.hashCode()
}

/** The original abstraction, including attribute-preserving folds and line splitting. */
interface HasChars<A> {
    val empty: A
    fun fromString(text: String): A
    fun fromPoints(points: IntArray): A = fromString(buildString { points.forEach(::appendCodePoint) })
    fun append(a: A, b: A): A
    // Monoid.mconcat folds from the right. Attr's deliberately arbitrary
    // semigroup keeps the first font/link, so a left fold from mempty differs.
    fun concat(values: Iterable<A>): A = values.toList().asReversed().fold(empty) { acc, value -> append(value, acc) }
    fun points(value: A): IntArray
    fun isNull(value: A): Boolean = points(value).isEmpty()
    fun replicateChar(count: Long, point: Int): A = fromString(repeatPoint(count, point))
    fun splitLines(value: A): List<A>
    fun build(value: A): String = TextChars.fromPoints(points(value))
    fun <B> foldLeft(value: A, initial: B, f: (B, Int) -> B): B = points(value).fold(initial, f)
    fun <B> foldRight(value: A, initial: B, f: (Int, B) -> B): B = points(value).foldRight(initial, f)
}
internal fun repeatPoint(count: Long, point: Int): String {
    if (count <= 0) return ""
    val scalar = String(Character.toChars(point))
    require(count <= Int.MAX_VALUE / scalar.length) { "Document exceeds JVM string capacity" }
    return scalar.repeat(count.toInt())
}
private fun textScalars(text: String) = buildString {
    text.codePoints().forEach { appendCodePoint(if (it in 0xd800..0xdfff) 0xfffd else it) }
}
object TextChars : HasChars<String> {
    override val empty = ""
    override fun fromString(text: String) = textScalars(text)
    override fun fromPoints(points: IntArray) = buildString { points.forEach { appendCodePoint(if (it in 0xd800..0xdfff) 0xfffd else it) } }
    override fun append(a: String, b: String) = a + b
    override fun concat(values: Iterable<String>) = values.joinToString("")
    override fun points(value: String) = value.codePoints().toArray()
    override fun splitLines(value: String) = value.split('\n')
    override fun build(value: String) = value
}
/** Haskell String can distinguish surrogate Chars from a supplementary Char. */
class CharacterString(points: IntArray) {
    private val data = points.copyOf().also { a -> require(a.all { it in 0..0x10ffff }) }
    fun points() = data.copyOf()
    override fun equals(other: Any?) = other is CharacterString && data.contentEquals(other.data)
    override fun hashCode() = data.contentHashCode()
}
object CharacterStringChars : HasChars<CharacterString> {
    override val empty get() = CharacterString(intArrayOf())
    override fun fromString(text: String) = fromPoints(text.codePoints().toArray())
    override fun fromPoints(points: IntArray) = CharacterString(points)
    override fun append(a: CharacterString, b: CharacterString) = CharacterString(a.points() + b.points())
    override fun concat(values: Iterable<CharacterString>): CharacterString {
        val arrays = values.map { it.points() }
        val count = arrays.sumOf { it.size.toLong() }
        require(count <= Int.MAX_VALUE) { "Document exceeds JVM array capacity" }
        val result = IntArray(count.toInt()); var at = 0
        for (a in arrays) { a.copyInto(result, at); at += a.size }
        return CharacterString(result)
    }
    override fun points(value: CharacterString) = value.points()
    override fun splitLines(value: CharacterString): List<CharacterString> {
        val points = value.points(); val result = mutableListOf<CharacterString>(); var start = 0
        for (i in points.indices) if (points[i] == 10) { result.add(CharacterString(points.copyOfRange(start, i))); start = i + 1 }
        result.add(CharacterString(points.copyOfRange(start, points.size))); return result
    }
    override fun build(value: CharacterString) = TextChars.fromPoints(value.points())
}
class PartChars<A>(private val chars: HasChars<A>) : HasChars<AttributedPart<A>> {
    override val empty get() = fromString("")
    override fun fromString(text: String) = AttributedPart(null, Font.Base, chars.fromString(text))
    override fun fromPoints(points: IntArray) = AttributedPart(null, Font.Base, chars.fromPoints(points))
    override fun append(a: AttributedPart<A>, b: AttributedPart<A>) = a.append(b, chars)
    override fun points(value: AttributedPart<A>) = chars.points(value.value)
    override fun splitLines(value: AttributedPart<A>) = chars.splitLines(value.value).map { value.copy(value = it) }
    override fun build(value: AttributedPart<A>) = chars.build(value.value)
}
class AttributedChars<A>(private val chars: HasChars<A>) : HasChars<Attributed<A>> {
    override val empty get() = Attributed<A>(emptyList())
    override fun fromString(text: String) = Attributed(listOf(AttributedPart(null, Font.Base, chars.fromString(text))))
    override fun fromPoints(points: IntArray) = Attributed(listOf(AttributedPart(null, Font.Base, chars.fromPoints(points))))
    override fun append(a: Attributed<A>, b: Attributed<A>) = a + b
    override fun concat(values: Iterable<Attributed<A>>) = Attributed(values.flatMap { it.parts })
    override fun points(value: Attributed<A>) = value.parts.flatMap { chars.points(it.value).asIterable() }.toIntArray()
    override fun splitLines(value: Attributed<A>): List<Attributed<A>> {
        val lines = mutableListOf<Attributed<A>>(); var current = mutableListOf<AttributedPart<A>>()
        for (part in value.parts) {
            val split = chars.splitLines(part.value)
            if (split.isEmpty()) { lines.add(Attributed(current)); current = mutableListOf(); continue }
            current.add(part.copy(value = split.first()))
            if (split.size > 1) {
                lines.add(Attributed(current))
                for (i in 1..<split.lastIndex) lines.add(Attributed(listOf(part.copy(value = split[i]))))
                current = mutableListOf(part.copy(value = split.last()))
            }
        }
        lines.add(Attributed(current)); return lines
    }
    override fun build(value: Attributed<A>) = value.parts.joinToString("") { chars.build(it.value) }
}
