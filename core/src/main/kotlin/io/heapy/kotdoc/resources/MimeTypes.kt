// Port of Text.Pandoc.MIME, copyright (C) 2011-2024 John MacFarlane.
// GPL-2.0-or-later. Network.Mime table: Michael Snoyman, MIT; see licenses/.
package io.heapy.kotdoc.resources

import java.util.Locale

object MimeTypes {
    private val entries: List<Pair<String, String>> = checkNotNull(javaClass.getResource("/io/heapy/kotdoc/mime-types.tsv"))
        .readText().lineSequence().filter { it.isNotEmpty() }.map { it.substringBefore('\t') to it.substringAfter('\t') }.toList()
    val byExtension: Map<String, String> = entries.toMap()
    private val reverse = entries.associate { it.second to it.first }

    fun get(path: String): String? = when {
        path == "layout-cache" -> "application/binary"
        path.startsWith("Formula-") && path.endsWith('/') -> "application/vnd.oasis.opendocument.formula"
        else -> byExtension[extension(path).drop(1).lowercase(Locale.ROOT)]
    }
    fun getOrDefault(path: String): String = get(path) ?: "application/octet-stream"
    fun extensionFromMimeType(mime: String): String? = when (val base = mime.substringBefore(';')) {
        "text/plain" -> "txt"; "video/quicktime" -> "mov"; "video/mpeg" -> "mpeg"; "video/dv" -> "dv"
        "image/vnd.djvu" -> "djvu"; "image/tiff" -> "tiff"; "image/jpeg" -> "jpg"; "application/xml" -> "xml"
        "application/ogg" -> "ogg"; "image/svg+xml" -> "svg"
        else -> reverse[base]
    }
    fun mediaCategory(path: String): String? = get(path)?.substringBefore('/')
    fun charset(mime: String): String? = if ("charset=" !in mime) null else mime.substringAfter("charset=").substringBefore(';').uppercase(Locale.ROOT)

    /** POSIX takeExtension: a leading dot is an extension; trailing separators have none. */
    internal fun extension(path: String): String {
        val name = path.substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        return if (dot < 0) "" else name.substring(dot)
    }
}
