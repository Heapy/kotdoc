// Port of Text.Pandoc.MediaBag. Copyright (C) 2014-2024 John MacFarlane.
// GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.resources

import io.heapy.kotdoc.formats.MediaInsertion
import io.heapy.kotdoc.ast.UnicodeTextOrder
import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.parsing.Deferred

class MediaItem private constructor(mimeType: String, val pathRaw: CharacterString, private val bytes: Deferred<ByteArray>) : Comparable<MediaItem> {
    val mimeType = TextChars.fromString(mimeType)
    val path: String = buildString { pathRaw.points().forEach(::appendCodePoint) }
    constructor(mimeType: String, path: String, contents: ByteArray) : this(mimeType, CharacterStringChars.fromString(path), Deferred.now(contents.copyOf()))
    constructor(mimeType: String, path: CharacterString, contents: ByteArray) : this(mimeType, path, Deferred.now(contents.copyOf()))
    val contents: ByteArray get() = bytes.value().copyOf()
    val size: Int get() = bytes.value().size
    override fun equals(other: Any?) = other is MediaItem && mimeType == other.mimeType && pathRaw == other.pathRaw && bytes.value().contentEquals(other.bytes.value())
    override fun hashCode() = 31 * (31 * mimeType.hashCode() + pathRaw.hashCode()) + bytes.value().contentHashCode()
    override fun compareTo(other: MediaItem): Int {
        UnicodeTextOrder.compare(mimeType, other.mimeType).let { if (it != 0) return it }
        pathRaw.compareTo(other.pathRaw).let { if (it != 0) return it }
        val a = bytes.value(); val b = other.bytes.value()
        for (i in 0..<minOf(a.size, b.size)) (a[i].toInt() and 255).compareTo(b[i].toInt() and 255).let { if (it != 0) return it }
        return a.size.compareTo(b.size)
    }
    fun minimum(other: MediaItem) = if (this <= other) this else other
    fun maximum(other: MediaItem) = if (this <= other) other else this
    fun show(precedence: Long = 0): String {
        val body = "MediaItem {mediaMimeType = ${HaskellString.quote(mimeType)}, mediaPath = ${HaskellString.quoteCodePoints(pathRaw.points())}, " +
            "mediaContents = ${HaskellString.quoteCodePoints(bytes.value().map { it.toInt() and 255 }.toIntArray())}}"
        return if (precedence > 10) "($body)" else body
    }
    override fun toString() = show()
    companion object {
        internal fun deferred(mime: String, path: CharacterString, contents: Deferred<ByteArray>) = MediaItem(mime, path, contents)
    }
}

/** Existing mutable API, backed by the original immutable value semantics. */
class MediaBag(initial: MediaBagValue = MediaBagValue.empty) {
    private var value = initial
    fun snapshot(): MediaBagValue = value
    fun insert(path: String, mimeType: String?, contents: ByteArray) { value = value.insert(path, mimeType, contents) }
    fun insert(path: CharacterString, mimeType: String?, contents: ByteArray) { value = value.insert(path, mimeType, contents) }
    fun insert(effect: MediaInsertion) = insert(effect.path, effect.mimeType, effect.contents)
    operator fun get(path: String): MediaItem? = value[path]
    fun delete(path: String) { value = value.delete(path) }
    fun clear() { value = MediaBagValue.empty }
    fun items(): List<MediaItem> = value.items()
    /** Map's Semigroup union is left-biased. */
    operator fun plus(other: MediaBag): MediaBag = MediaBag(value + other.value)

    companion object {
        fun canonicalize(path: String): String = canonicalize(CharacterStringChars.fromString(path))
        fun canonicalize(path: CharacterString): String {
            val text = TextChars.fromPoints(path.points())
            if (text.startsWith("data:", ignoreCase = true) || MediaBagUri.isKnown(text)) return text
            val path = text
            val normalized = path.replace('\\', '/')
            val parts = mutableListOf<String>()
            // System.FilePath.splitDirectories retains a root component, but not trailing separators.
            val drive = normalized.takeWhile { it == '/' }
            if (drive.isNotEmpty()) parts += drive
            for (part in normalized.split('/').filter { it.isNotEmpty() }) when {
                part == "." -> Unit
                part == ".." && parts.isNotEmpty() && parts.last() != ".." -> parts.removeLast()
                else -> parts += part
            }
            return if (parts.firstOrNull()?.all { it == '/' } == true) parts.first() + parts.drop(1).joinToString("/") else parts.joinToString("/")
        }
    }
}
