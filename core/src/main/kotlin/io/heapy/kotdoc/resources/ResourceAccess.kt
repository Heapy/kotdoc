// Resource selection follows Text.Pandoc.Class.PandocMonad (GPL-2.0-or-later).
// Copyright (C) 2006-2025 John MacFarlane. JVM IO is at this boundary.
package io.heapy.kotdoc.resources

import io.heapy.kotdoc.formats.HaskellString
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream

data class FetchedResource(val contents: ByteArray, val mimeType: String?)
sealed interface ResourceEvent {
    data class Loaded(val requested: String, val found: String) : ResourceEvent
    data class Fetching(val url: String) : ResourceEvent
    data class Extracting(val path: String) : ResourceEvent
    data class CouldNotFetch(val path: String, val message: String) : ResourceEvent
    data class IgnoredIOError(val message: String) : ResourceEvent
}
open class ResourceException(val description: String, cause: Exception? = null) : Exception(description, cause)
class ResourceNotFound(val path: String, cause: Exception? = null) :
    ResourceException("PandocResourceNotFound ${HaskellString.quote(path)}", cause)
class ResourceIOError(val path: String, val detail: String, cause: Exception? = null) :
    ResourceException("PandocIOError ${HaskellString.quote(path)} $detail", cause)
class ResourceHttpError(val url: String, val detail: String, cause: Exception? = null) :
    ResourceException("PandocHttpError ${HaskellString.quote(url)} $detail", cause)

fun interface ResourceAccess {
    fun fetch(url: String, report: (ResourceEvent) -> Unit): FetchedResource
}

/** Media lookup, URI resolution and resource paths are separate from the writer. */
class ResourceContext(
    val mediaBag: MediaBag = MediaBag(),
    val resourcePaths: List<Path> = listOf(Path.of(".")),
    val sourceUrl: String? = null,
    val requestHeaders: List<Pair<String, String>> = emptyList(),
    private val httpClient: HttpClient = defaultHttpClient,
) : ResourceAccess {
    companion object {
        private val defaultHttpClient: HttpClient by lazy { HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build() }
    }
    override fun fetch(url: String, report: (ResourceEvent) -> Unit): FetchedResource {
        mediaBag[url]?.let { return FetchedResource(it.contents, it.mimeType) }
        ResourceUri.base64Data(url)?.let { return FetchedResource(it.contents, it.mime) }
        val escaped = ResourceUri.escaped(url)
        val source = sourceUrl?.let { ResourceUri.parse(ResourceUri.escaped(it)) }
        if (source != null) return openUrl(source.resolve(escaped).toString(), report)
        if (escaped.startsWith("//") && escaped.getOrNull(2) != '?' && escaped.length > 2)
            return openUrl("http:$escaped", report)
        val uri = ResourceUri.parse(escaped)?.takeIf { it.isAbsolute }
        return when {
            uri?.scheme.equals("file", ignoreCase = true) -> readFile(uri!!.rawPath.orEmpty(), url, report)
            uri?.scheme.equals("data", ignoreCase = true) -> ResourceUri.extractData(uri!!.rawSchemeSpecificPart.substringBefore('#').substringBefore('?')).let {
                FetchedResource(it.contents, it.mime)
            }
            uri != null && uri.scheme.length > 1 -> openUrl(uri.toString(), report)
            else -> readFile(ResourceUri.unescape(url), url, report)
        }
    }

    private fun readFile(path: String, original: String, report: (ResourceEvent) -> Unit): FetchedResource {
        val requested = try { Path.of(path) } catch (e: java.nio.file.InvalidPathException) {
            throw ResourceNotFound(path, e)
        }
        val locations = if (requested.isAbsolute) listOf(requested) else resourcePaths.map { it.resolve(requested) }
        for (location in locations) {
            val bytes = try { Files.readAllBytes(location) } catch (e: IOException) {
                if (requested.isAbsolute) throw ResourceIOError(path, e.toString(), e)
                continue
            }
            val found = location.toString().removePrefix("./")
            report(ResourceEvent.Loaded(path, found))
            val decoded = ResourceUri.unescape(original)
            val ext = MimeTypes.extension(decoded)
            val mimePath = when (ext) { ".gz" -> decoded.dropLast(3); ".svgz" -> decoded.dropLast(5) + ".svg"; else -> ext }
            return FetchedResource(bytes, MimeTypes.get(mimePath))
        }
        throw ResourceNotFound(path)
    }

    private fun openUrl(url: String, report: (ResourceEvent) -> Unit): FetchedResource {
        val uri = ResourceUri.parse(url)
        if (uri?.scheme == "data") return ResourceUri.extractData(uri.rawSchemeSpecificPart.substringBefore('?')).let {
            FetchedResource(it.contents, it.mime)
        }
        report(ResourceEvent.Fetching(url))
        try {
            val builder = HttpRequest.newBuilder(URI(url)).GET().header("Accept-Encoding", "gzip")
            for ((name, value) in requestHeaders) builder.header(name, value)
            val response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
            if (response.statusCode() !in 200..299) throw ResourceHttpError(url, "HTTP ${response.statusCode()}")
            val content = if (response.headers().firstValue("Content-Encoding").orElse("").equals("gzip", ignoreCase = true))
                GZIPInputStream(response.body().inputStream()).use { it.readBytes() } else response.body()
            return FetchedResource(content, response.headers().firstValue("Content-Type").orElse(null))
        } catch (e: IOException) {
            throw ResourceHttpError(url, e.message.orEmpty(), e)
        } catch (e: IllegalArgumentException) {
            throw ResourceHttpError(url, e.message.orEmpty(), e)
        }
    }
}
