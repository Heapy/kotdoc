// Text.Pandoc.Templates, Copyright (C) 2009-2024 John MacFarlane.
// Text.Pandoc.Data.readDataFile, Copyright (C) 2013-2024 John MacFarlane.
// GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.templates

import io.heapy.kotdoc.resources.*
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

class TemplateDataFileNotFound(val path: String) : ResourceException("Could not find data file '$path'")
class TemplateCompilationError(val detail: String) : ResourceException("Error compiling template '$detail'")

/** Pandoc data-file effect, shared by default templates and their partials. */
fun interface TemplateDataFiles {
    fun read(path: String): ByteArray
}

/** All pinned built-in templates, with readDataFile's user-directory precedence. */
class BundledTemplateDataFiles(val userDataDirectory: Path? = null) : TemplateDataFiles {
    override fun read(path: String): ByteArray {
        val canonical = PartialPaths.System.canonical(path)
        if (userDataDirectory != null && PartialPaths.System.isRelative(path) && canonical.split('/').firstOrNull() != "..") {
            val file = Path.of(PartialPaths.System.combine(userDataDirectory.toString(), path))
            if (Files.isRegularFile(file)) {
                return try { Files.readAllBytes(file) } catch (e: IOException) {
                    throw ResourceIOError(file.toString(), e.toString(), e)
                }
            }
        }
        if (canonical !in names) throw TemplateDataFileNotFound(path)
        return checkNotNull(javaClass.getResourceAsStream("/io/heapy/kotdoc/pandoc-data/$canonical"))
            { "Missing packaged Pandoc template: $canonical" }.use { it.readBytes() }
    }
    companion object {
        val names: Set<String> by lazy {
            checkNotNull(BundledTemplateDataFiles::class.java.getResourceAsStream("/io/heapy/kotdoc/pandoc-data/templates.txt"))
                .bufferedReader(Charsets.UTF_8).use { it.readLines().toSet() }
        }
    }
}

class PandocTemplates(
    val resources: ResourceContext = ResourceContext(),
    val dataFiles: TemplateDataFiles = BundledTemplateDataFiles(),
    val report: (ResourceEvent) -> Unit = {},
    val paths: PartialPaths = PartialPaths.System,
) {
    fun getTemplate(path: String): String {
        val bytes = try { resources.withoutSourceUrl().fetch(path, report).contents }
        catch (e: ResourceNotFound) { dataFiles.read(dataPath(path)) }
        catch (e: ResourceIOError) {
            if (e.cause is NoSuchFileException) dataFiles.read(dataPath(path)) else throw e
        }
        return PandocText.toText(path, bytes)
    }
    private fun dataPath(path: String): String = paths.combine("templates", paths.fileName(path))
    val defaultPartials: PartialResolver get() = PartialResolver { path -> PandocText.toText(path, dataFiles.read(dataPath(path))) }
    val partials: PartialResolver get() = PartialResolver(::getTemplate)

    fun getDefaultTemplate(format: String): String {
        if (format in emptyDefaults) return ""
        val name = paths.combine("templates", paths.addExtension("default", aliases[format] ?: format))
        return PandocText.toText(name, dataFiles.read(name))
    }
    fun compileWithPartials(path: String, text: String): CompiledTemplate<String> =
        TemplateCompiler(TemplateValues.Text, partials, paths).compile(path, text)
    fun compileWithDefaultPartials(path: String, text: String): CompiledTemplate<String> =
        TemplateCompiler(TemplateValues.Text, defaultPartials, paths).compile(path, text)
    fun compileDefaultTemplate(writer: String): Template<String> =
        when (val result = compileWithDefaultPartials("templates/default.$writer", getDefaultTemplate(writer))) {
            is CompiledTemplate.Success -> result.template
            is CompiledTemplate.Failure -> throw TemplateCompilationError(result.error)
        }
    companion object {
        private val emptyDefaults = setOf("native", "csljson", "json", "xml", "fb2", "pptx", "ipynb")
        private val aliases = mapOf(
            "asciidoctor" to "asciidoc", "asciidoc_legacy" to "asciidoc", "docx" to "openxml",
            "odt" to "opendocument", "fodt" to "opendocument", "html" to "html5",
            "docbook" to "docbook5", "epub" to "epub3", "jats" to "jats_archiving",
            "markdown_strict" to "markdown", "multimarkdown" to "markdown", "markdown_github" to "markdown",
            "markdown_mmd" to "markdown", "markdown_phpextra" to "markdown", "gfm" to "commonmark",
            "commonmark_x" to "commonmark", "bbcode_phpbb" to "bbcode", "bbcode_fluxbb" to "bbcode",
            "bbcode_steam" to "bbcode", "bbcode_hubzilla" to "bbcode", "bbcode_xenforo" to "bbcode",
        )
    }
}
