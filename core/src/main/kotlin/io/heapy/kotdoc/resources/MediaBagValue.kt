// Text.Pandoc.MediaBag; GPL-2.0-or-later, original complete notice in licenses/.
package io.heapy.kotdoc.resources

import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.parsing.Deferred
import java.math.BigInteger
import java.security.MessageDigest

/** Original immutable MediaBag, preserving older snapshots by structural sharing. */
class MediaBagValue private constructor(private val entries: PersistentTextMap<MediaItem>) {
    val count: Long get() = entries.size
    fun insert(path: String, mimeType: String?, contents: ByteArray): MediaBagValue = insert(CharacterStringChars.fromString(path), mimeType, contents)
    fun insert(path: CharacterString, mimeType: String?, contents: ByteArray): MediaBagValue = insertOwned(path, mimeType, Deferred.now(contents.copyOf()))
    /** Safe paths leave the payload delayed; paths needing SHA-1 demand it at insert. */
    fun insertDeferred(path: CharacterString, mimeType: String?, contents: () -> ByteArray): MediaBagValue =
        insertOwned(path, mimeType, Deferred.later { contents().copyOf() })
    private fun insertOwned(path: CharacterString, mimeType: String?, contents: Deferred<ByteArray>): MediaBagValue {
        val key = MediaBag.canonicalize(path)
        val raw = buildString { path.points().forEach(::appendCodePoint) }
        val decoded = ResourceUri.unescape(key)
        // Network.URI.parseURI receives the raw FilePath here, unlike isURI's
        // separately escaped Text test. Its URI grammar rejects non-ASCII Chars.
        val uri = MediaBagUri.parse(raw)
        val fallback = if (MimeTypes.extension(decoded) == ".gz") decoded.dropLast(3) else decoded
        val mime = mimeType?.let(TextChars::fromString) ?: MimeTypes.getOrDefault(fallback)
        val extension = MimeTypes.extensionFromMimeType(mime)?.let { ".$it" } ?: MimeTypes.extension(
            uri?.path?.let(ResourceUri::unescape) ?: decoded).takeUnless { '%' in it }.orEmpty()
        val safe = !(key.startsWith("data:", ignoreCase = true) && mimeType != null) &&
            !decoded.startsWith('/') && !decoded.startsWith('\\') && !Regex("^[A-Za-z]:").containsMatchIn(decoded) &&
            uri == null && decoded.replace('\\', '/').split('/').none { it == ".." } && '%' !in key
        val output = if (safe) decoded else MessageDigest.getInstance("SHA-1").digest(contents.value()).toHexString() + extension
        val item = MediaItem.deferred(mime, CharacterStringChars.fromString(output), contents)
        return MediaBagValue(entries.insert(key, item))
    }
    operator fun get(path: String): MediaItem? = get(CharacterStringChars.fromString(path))
    operator fun get(path: CharacterString): MediaItem? = entries[MediaBag.canonicalize(path)]
    fun delete(path: String) = delete(CharacterStringChars.fromString(path))
    fun delete(path: CharacterString) = MediaBagValue(entries.delete(MediaBag.canonicalize(path)))
    fun items(): List<MediaItem> = entries.map { it.second }
    fun mediaItems(): List<Triple<CharacterString, String, ByteArray>> = entries.map { (_, item) -> Triple(item.pathRaw, item.mimeType, item.contents) }
    fun mediaDirectory(): List<Triple<CharacterString, String, Long>> = entries.map { (_, item) -> Triple(item.pathRaw, item.mimeType, item.size.toLong()) }
    operator fun plus(other: MediaBagValue) = MediaBagValue(entries + other.entries)
    fun repeat(multiplier: BigInteger): MediaBagValue {
        require(multiplier.signum() >= 0) { "stimesIdempotentMonoid: negative multiplier" }
        return if (multiplier.signum() == 0) empty else this
    }
    fun show(precedence: Long = 0): String {
        @Suppress("UNUSED_VARIABLE") val sourcePrecedence = precedence
        return "MediaBag " + mediaDirectory().joinToString(",", "[", "]") { (path, mime, length) ->
            "(${HaskellString.quoteCodePoints(path.points())},${HaskellString.quote(mime)},$length)"
        }
    }
    override fun toString() = show()
    companion object {
        val empty = MediaBagValue(PersistentTextMap.empty<MediaItem>())
        fun concatenate(values: Iterable<MediaBagValue>) = values.fold(empty) { acc, next -> acc + next }
    }
}
