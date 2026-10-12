// Port of Text.Pandoc.Class.PandocMonad.fillMediaBag and Class.IO media operations.
// Copyright (C) 2014-2025 John MacFarlane. GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.resources

import io.heapy.kotdoc.ast.*
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object MediaOperations {
    fun fill(document: Pandoc, bag: MediaBag, resources: ResourceAccess = ResourceContext(bag),
             report: (ResourceEvent) -> Unit = {}): Pandoc = object : PandocVisitor() {
        override fun transformInline(value: Inline): Inline {
            if (value !is Inline.Image || bag[value.target.url] != null) return value
            val src = value.target.url
            try {
                val fetched = resources.fetch(src, report)
                bag.insert(src, fetched.mimeType, fetched.contents)
                return value
            } catch (e: ResourceNotFound) {
                report(ResourceEvent.CouldNotFetch(src, "replacing image with description"))
            } catch (e: ResourceIOError) {
                report(ResourceEvent.CouldNotFetch(e.path, e.detail + "\nReplacing image with description."))
            } catch (e: ResourceHttpError) {
                report(ResourceEvent.CouldNotFetch(e.url, e.detail + "\nReplacing image with description."))
            }
            return Inline.Span(value.attr.copy(
                classes = listOf("image", "placeholder") + value.attr.classes,
                keyValues = listOf("original-image-src" to src, "original-image-title" to value.target.title) + value.attr.keyValues,
            ), value.content)
        }
    }.walk(document)

    fun extract(destination: String, document: Pandoc, bag: MediaBag,
                report: (ResourceEvent) -> Unit = {}): Pandoc {
        val items = bag.items()
        if (items.isEmpty()) return document
        val zip = MimeTypes.extension(destination) == ".zip"
        val dir = if (zip) destination.substringBeforeLast('/', ".").ifEmpty { "/" } else destination
        if (zip) writeMedia(dir, MediaItem("application/zip", destination.substringAfterLast('/'), MediaZip.encode(items)), report)
        else items.forEach { writeMedia(dir, it, report) }
        return object : PandocVisitor() {
            override fun transformInline(value: Inline): Inline {
                if (value !is Inline.Image) return value
                val item = bag[value.target.url] ?: return value
                return value.copy(target = value.target.copy(url = join(dir, item.path)))
            }
        }.walk(document)
    }

    /** Lua mediabag.write: all items, or just one selected canonical key. */
    fun write(directory: String, bag: MediaBag, path: String? = null, report: (ResourceEvent) -> Unit = {}) {
        val items = if (path == null) bag.items() else listOf(bag[path]
            ?: throw ResourceException("Resource not in mediabag: $path"))
        items.forEach { writeMedia(directory, it, report) }
    }

    private fun writeMedia(directory: String, item: MediaItem, report: (ResourceEvent) -> Unit) {
        // System.FilePath.normalise removes '.' and duplicate separators, but keeps '..'.
        val joined = join(directory, ResourceUri.unescape(item.path))
        val parts = joined.split('/').filter { it.isNotEmpty() && it != "." }
        val normal = ((if (joined.startsWith('/')) "/" else "") + parts.joinToString("/")).ifEmpty { "." }
        val path = Path.of(normal)
        val parent = path.parent ?: Path.of(".")
        try { Files.createDirectories(parent) }
        catch (e: IOException) { throw ResourceIOError(parent.toString(), e.toString(), e) }
        report(ResourceEvent.Extracting(normal))
        try { Files.write(path, item.contents) }
        catch (e: IOException) { report(ResourceEvent.IgnoredIOError(e.toString())) }
    }

    private fun join(directory: String, file: String): String = when {
        file.startsWith('/') || directory.isEmpty() -> file
        directory.endsWith('/') -> directory + file
        else -> "$directory/$file"
    }
}
