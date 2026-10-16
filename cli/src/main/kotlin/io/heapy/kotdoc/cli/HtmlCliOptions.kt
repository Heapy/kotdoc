// CLI writer options/variables from Text.Pandoc.App, GPL-2.0-or-later.
package io.heapy.kotdoc.cli

import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.highlighting.Highlighting
import io.heapy.kotdoc.layout.DocLayout
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.resources.PandocText
import io.heapy.kotdoc.resources.PackagedPandocData
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.resources.ResourceContext
import io.heapy.kotdoc.templates.*
import kotlinx.serialization.json.*

/** Writer-facing command options; reader/application parity remains separately tracked. */
internal class HtmlCliOptions {
    var options = WriterOptions()
        private set
    private var variables = Context<String>()
    private var templateFile: String? = null
    private val includes = mutableListOf<Pair<String, String>>()
    private val css = mutableListOf<String>()
    private var standalone = false

    /** Return the next argument index, or null if another parser owns this option. */
    fun consume(args: Array<String>, index: Int): Int? {
        val arg = args[index]
        val name = arg.substringBefore('=')
        var next = index + 1
        fun value(): String = if ('=' in arg) arg.substringAfter('=') else args.getOrNull(next++) ?: error("Missing argument for $name")
        fun flag(): Boolean = if ('=' !in arg) true else when (arg.substringAfter('=')) {
            "true" -> true
            "false" -> false
            else -> error("Argument of $name must be true or false")
        }
        fun positive(label: String, min: Long = 1, max: Long = Long.MAX_VALUE): Long = value().toLong().also {
            require(it in min..max) { "Invalid $label: $it" }
        }
        when (name) {
            "-s", "--standalone" -> standalone = flag()
            "--wrap" -> options = options.copy(wrapText = WrapOption.fromJson(JsonPrimitive(value())))
            "--toc", "--table-of-contents" -> options = options.copy(tableOfContents = flag())
            "--toc-depth" -> options = options.copy(tocDepth = positive("TOC depth", max = 6))
            "--lof", "--list-of-figures" -> options = options.copy(listOfFigures = flag())
            "--lot", "--list-of-tables" -> options = options.copy(listOfTables = flag())
            "-i", "--incremental" -> options = options.copy(incremental = flag())
            "-N", "--number-sections" -> options = options.copy(numberSections = flag())
            "--number-offset" -> options = options.copy(numberSections = true, numberOffset = value().let { text ->
                if (text.isBlank()) emptyList() else text.split(',').map { it.trim().toLong() }
            })
            "--section-divs" -> options = options.copy(sectionDivs = flag())
            "--html-q-tags" -> options = options.copy(htmlQTags = flag())
            "--slide-level" -> options = options.copy(slideLevel = positive("slide level", 0, 6))
            "--dpi" -> options = options.copy(dpi = positive("DPI"))
            "--tab-stop" -> options = options.copy(tabStop = positive("tab stop"))
            "--id-prefix" -> options = options.copy(identifierPrefix = value())
            "--email-obfuscation" -> options = options.copy(emailObfuscation = ObfuscationMethod.fromJson(JsonPrimitive(value())))
            "--reference-location" -> options = options.copy(referenceLocation = ReferenceLocation.fromJson(JsonPrimitive(value())))
            "--top-level-division" -> options = options.copy(topLevelDivision = TopLevelDivision.fromJson(JsonPrimitive(value())))
            "--figure-caption-position" -> options = options.copy(figureCaptionPosition = CaptionPosition.fromJson(JsonPrimitive(value())))
            "--table-caption-position" -> options = options.copy(tableCaptionPosition = CaptionPosition.fromJson(JsonPrimitive(value())))
            "--markdown-headings" -> options = options.copy(setextHeaders = when (value()) {
                "setext" -> true
                "atx" -> false
                else -> error("Argument of --markdown-headings must be setext or atx")
            })
            "--ascii" -> options = options.copy(preferAscii = flag())
            "--syntax-highlighting", "--highlight-style", "--no-highlight" -> {
                val method = if (name == "--no-highlight") "none" else value()
                if (name != "--syntax-highlighting") System.err.println("[WARNING] Deprecated: $name. Use --syntax-highlighting${if (name == "--no-highlight") "=none" else ""} instead.")
                options = options.copy(highlightMethod = when (method) {
                    "none" -> HighlightMethod.NoHighlighting
                    "default" -> HighlightMethod.DefaultHighlighting
                    "idiomatic" -> HighlightMethod.IdiomaticHighlighting
                    else -> HighlightMethod.Skylighting(Highlighting.lookupHighlightingStyle(method))
                })
            }
            "--template" -> templateFile = value()
            "-V", "--variable", "--variable-json" -> {
                val field = value()
                val separator = field.indexOfFirst { it == '=' || it == ':' }
                val key = if (separator < 0) field else field.take(separator)
                val text = if (separator < 0) "true" else field.drop(separator + 1)
                if (name == "--variable-json") variables = Context(variables.entries + (key to checkNotNull(TemplateValues.Text.contextFromJson(buildJsonObject { put(key, Json.parseToJsonElement(text)) })[key])))
                else append(key, text)
            }
            "-H", "--include-in-header" -> includes.add("header-includes" to value())
            "-B", "--include-before-body" -> includes.add("include-before" to value())
            "-A", "--include-after-body" -> includes.add("include-after" to value())
            "-c", "--css" -> css.add(value())
            "-T", "--title-prefix" -> { append("title-prefix", value()); standalone = true }
            else -> return null
        }
        return next
    }

    private fun append(key: String, text: String) {
        val v = Val.SimpleVal(DocLayout.Text.literal(text))
        val old = variables[key]
        variables = Context(variables.entries + (key to when (old) {
            null -> v
            is Val.ListVal -> Val.ListVal(old.values + v)
            else -> Val.ListVal(listOf(old, v))
        }))
    }
    private fun appendList(key: String, text: String) {
        if (variables[key] == null) variables = Context(variables.entries + (key to Val.ListVal(emptyList())))
        append(key, text)
    }

    fun standaloneFor(format: String) = standalone || (format in listOf("dokuwiki", "muse") && templateFile != null)

    fun finish(base: WriterOptions, format: String, input: String?, output: String?, resources: ResourceContext): WriterOptions {
        appendList("sourcefile", input ?: "-")
        if (variables["outputfile"] == null) append("outputfile", output ?: "-")
        if (variables["pandoc-version"] == null) append("pandoc-version", "3.12.1")
        if (variables["curdir"] == null) append("curdir", System.getProperty("user.dir"))
        for (key in listOf("include-before", "include-after", "header-includes")) {
            for ((_, file) in includes.filter { it.first == key }) appendList(key, PandocText.toText(file, resources.fetch(file, ::reportResource).contents))
        }
        for (file in css) appendList("css", file)
        if (format == "dzslides" && variables["dzslides-core"] == null) {
            val file = "dzslides/template.html"
            val text = PandocText.toText(file, checkNotNull(PackagedPandocData.read(CharacterStringChars.fromString(file))))
            val core = text.removeSuffix("\n").split('\n').dropWhile { !it.startsWith("<!-- {{{{ dzslides core") }
            append("dzslides-core", if (core.isEmpty()) "" else core.joinToString("\n", postfix = "\n"))
        }
        val templates = PandocTemplates(resources, report = ::reportResource)
        val template = if (!standaloneFor(format)) null else {
            templateFile?.let { path ->
                when (val r = templates.compileWithPartials(path, templates.getTemplate(path))) {
                    is CompiledTemplate.Success -> r.template
                    is CompiledTemplate.Failure -> if (format == "muse") throw PandocError.PandocTemplateError(r.error) else error(r.error)
                }
            } ?: if (format in HtmlCliWriter.formats || format in listOf("dokuwiki", "muse")) templates.compileDefaultTemplate(format) else Template.Empty
        }
        return base.copy(template = template, variables = variables)
    }
}
