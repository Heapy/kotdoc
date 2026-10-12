// Port of Text.Pandoc.MediaBag. Copyright (C) 2014-2024 John MacFarlane.
// GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.resources

import io.heapy.kotdoc.ast.UnicodeTextOrder
import io.heapy.kotdoc.formats.MediaInsertion
import java.security.MessageDigest

class MediaItem(val mimeType: String, val path: String, contents: ByteArray) {
    private val bytes = contents.copyOf()
    val contents: ByteArray get() = bytes.copyOf()
    val size: Int get() = bytes.size
}

class MediaBag {
    private val entries = sortedMapOf<String, MediaItem>(UnicodeTextOrder)
    fun insert(path: String, mimeType: String?, contents: ByteArray) {
        val key = canonicalize(path)
        val decoded = ResourceUri.unescape(key)
        val uri = ResourceUri.parse(path)?.takeIf { it.isAbsolute }
        val fallback = if (MimeTypes.extension(decoded) == ".gz") decoded.dropLast(3) else decoded
        val mime = mimeType ?: MimeTypes.getOrDefault(fallback)
        val extension = MimeTypes.extensionFromMimeType(mime)?.let { ".$it" } ?: MimeTypes.extension(
            uri?.rawPath?.let(ResourceUri::unescape) ?: decoded).takeUnless { '%' in it }.orEmpty()
        val safe = !(path.startsWith("data:", ignoreCase = true) && mimeType != null) &&
            !decoded.startsWith('/') && !decoded.startsWith('\\') && !Regex("^[A-Za-z]:").containsMatchIn(decoded) &&
            uri == null && decoded.replace('\\', '/').split('/').none { it == ".." } && '%' !in key
        val outputPath = if (safe) decoded else MessageDigest.getInstance("SHA-1").digest(contents).toHexString() + extension
        entries[key] = MediaItem(mime, outputPath, contents)
    }
    fun insert(effect: MediaInsertion) = insert(effect.path, effect.mimeType, effect.contents)
    operator fun get(path: String): MediaItem? = entries[canonicalize(path)]
    fun delete(path: String) { entries.remove(canonicalize(path)) }
    fun items(): List<MediaItem> = entries.values.toList()
    /** Map's Semigroup union is left-biased. */
    operator fun plus(other: MediaBag): MediaBag = MediaBag().also { it.entries.putAll(other.entries); it.entries.putAll(entries) }

    companion object {
        fun canonicalize(path: String): String {
            if (path.startsWith("data:", ignoreCase = true) || ResourceUri.isUri(path)) return path
            val normalized = path.replace('\\', '/')
            val parts = mutableListOf<String>()
            // System.FilePath.splitDirectories retains a root component, but not trailing separators.
            if (normalized.startsWith('/')) parts += "/"
            for (part in normalized.split('/').filter { it.isNotEmpty() }) when {
                part == "." -> Unit
                part == ".." && parts.isNotEmpty() && parts.last() != ".." -> parts.removeLast()
                else -> parts += part
            }
            return if (parts.firstOrNull() == "/") "/" + parts.drop(1).joinToString("/") else parts.joinToString("/")
        }
    }
}
