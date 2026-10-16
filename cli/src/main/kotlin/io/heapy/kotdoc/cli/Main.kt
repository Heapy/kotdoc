package io.heapy.kotdoc.cli

import io.heapy.kotdoc.ast.PandocJson
import io.heapy.kotdoc.formats.FB2Reader
import io.heapy.kotdoc.formats.FB2Writer
import io.heapy.kotdoc.formats.NativeReader
import io.heapy.kotdoc.formats.NativeWriter
import io.heapy.kotdoc.formats.ReaderMessage
import io.heapy.kotdoc.formats.WriterMessage
import io.heapy.kotdoc.options.ExtensionRegistry
import io.heapy.kotdoc.options.FlavoredFormat
import io.heapy.kotdoc.options.FormatSpecificationException
import io.heapy.kotdoc.options.UnsupportedExtensionException
import io.heapy.kotdoc.options.MathMethod
import io.heapy.kotdoc.options.MathMethodArgumentException
import io.heapy.kotdoc.options.WriterOptions
import io.heapy.kotdoc.options.ReaderOptions
import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.options.DEFAULT_WEB_TEX_URL
import io.heapy.kotdoc.options.DEFAULT_MATH_JAX_URL
import io.heapy.kotdoc.options.DEFAULT_KA_TEX_URL
import io.heapy.kotdoc.resources.MediaBag
import io.heapy.kotdoc.resources.MediaOperations
import io.heapy.kotdoc.resources.ResourceContext
import io.heapy.kotdoc.resources.ResourceEvent
import java.io.File
import java.nio.file.Path
import kotlin.system.exitProcess

/** Entry point for ported formats. Full Pandoc CLI parity is tracked in docs/PORTING.md. */
fun main(args: Array<String>) {
    if (args.toList() == listOf("--help")) {
        println("Usage: kotdoc [--from dokuwiki|fb2|json|latex|native] [--to FORMAT] [--standalone] [--columns N] [--math-method METHOD] [--resource-path PATHS] [--extract-media PATH] [--output FILE] [FILE|-]")
        return
    }
    if (args.toList() == listOf("--list-input-formats")) {
        println("dokuwiki\nfb2\njson\nlatex\nnative")
        return
    }
    if (args.toList() == listOf("--list-output-formats")) {
        println((listOf("dokuwiki", "fb2", "json", "native") + HtmlCliWriter.formats).sorted().joinToString("\n"))
        return
    }
    try {
        var input: String? = null
        var output: String? = null
        var from = "json"
        var to = "json"
        var explicitReader = false
        var explicitWriter = false
        var trace = false
        var columns = 72
        var tabStop = 4L
        var preserveTabs = false
        var defaultImageExtension = ""
        val htmlOptions = HtmlCliOptions()
        var mathMethod: MathMethod = MathMethod.MathML
        fun legacyMath(option: String, url: String? = null): MathMethod {
            val name = option.removePrefix("--")
            val suffix = if (name == "mathml") "" else "[:URL]"
            System.err.println("[WARNING] Deprecated: $option. Use --math-method=$name$suffix instead.")
            return when (name) {
                "mathml" -> MathMethod.MathML
                "gladtex" -> MathMethod.GladTeX
                "webtex" -> MathMethod.WebTeX(url ?: DEFAULT_WEB_TEX_URL)
                "mathjax" -> MathMethod.MathJax(url ?: DEFAULT_MATH_JAX_URL)
                "katex" -> MathMethod.KaTeX(url ?: DEFAULT_KA_TEX_URL)
                else -> error("Unknown legacy math option: $option")
            }
        }
        var extractMedia: String? = null
        var resourcePaths = listOf(Path.of("."))
        fun addResourcePaths(value: String) {
            resourcePaths = value.split(File.pathSeparatorChar).map { Path.of(it.ifEmpty { "." }) } + resourcePaths
        }
        var i = 0
        while (i < args.size) {
            htmlOptions.consume(args, i)?.let { next -> i = next; continue }
            when (val arg = args[i++]) {
                "--from", "-f", "--read", "-r" -> { from = args.getOrNull(i++) ?: error("Missing input format"); explicitReader = true }
                "--to", "-t", "--write", "-w" -> { to = args.getOrNull(i++) ?: error("Missing output format"); explicitWriter = true }
                "--output", "-o" -> output = args.getOrNull(i++) ?: error("Missing output filename")
                "--columns" -> columns = (args.getOrNull(i++) ?: error("Missing column count")).toInt()
                "--math-method" -> mathMethod = MathMethod.fromArgument(args.getOrNull(i++) ?: error("Missing math method"))
                "--mathml", "--gladtex", "--webtex", "--mathjax", "--katex" -> mathMethod = legacyMath(arg)
                "--extract-media" -> extractMedia = args.getOrNull(i++) ?: error("Missing media output path")
                "--resource-path" -> addResourcePaths(args.getOrNull(i++) ?: error("Missing resource search path"))
                "--tab-stop" -> tabStop = (args.getOrNull(i++) ?: error("Missing tab stop")).toLong()
                "--preserve-tabs" -> preserveTabs = true
                "--trace" -> trace = true
                "--default-image-extension" -> defaultImageExtension = args.getOrNull(i++) ?: error("Missing default image extension")
                else -> {
                    if (arg.startsWith("--from=")) { from = arg.substringAfter('='); explicitReader = true; continue }
                    if (arg.startsWith("--to=")) { to = arg.substringAfter('='); explicitWriter = true; continue }
                    if (arg.startsWith("--math-method=")) { mathMethod = MathMethod.fromArgument(arg.substringAfter('=')); continue }
                    if (listOf("--webtex=", "--mathjax=", "--katex=").any(arg::startsWith)) {
                        mathMethod = legacyMath(arg.substringBefore('='), arg.substringAfter('=')); continue
                    }
                    if (arg.startsWith("--columns=")) { columns = arg.substringAfter('=').toInt(); continue }
                    if (arg.startsWith("--extract-media=")) { extractMedia = arg.substringAfter('='); continue }
                    if (arg.startsWith("--resource-path=")) { addResourcePaths(arg.substringAfter('=')); continue }
                    if (arg.startsWith("--tab-stop=")) { tabStop = arg.substringAfter('=').toLong(); continue }
                    if (arg.startsWith("--default-image-extension=")) { defaultImageExtension = arg.substringAfter('='); continue }
                    require(arg == "-" || !arg.startsWith("-")) { "Unsupported option: $arg" }
                    require(input == null) { "Only one input file is currently supported" }
                    input = arg
                }
            }
        }
        if (!explicitReader && input != null) {
            val inferred = FlavoredFormat.fromFilePaths(listOf(input))
            val dokuOutput = FlavoredFormat.parse(to).name == "dokuwiki" || output?.let { FlavoredFormat.fromFilePaths(listOf(it))?.name == "dokuwiki" } == true
            if (inferred != null && (inferred.name == "dokuwiki" || dokuOutput)) from = inferred.name
        }
        val inputFormat = FlavoredFormat.parse(from)
        if (!explicitWriter) {
            val inferred = output?.let { FlavoredFormat.fromFilePaths(listOf(it)) }
            if (inputFormat.name == "dokuwiki") to = inferred?.name ?: "html"
            else if (inferred?.name == "dokuwiki") to = "dokuwiki"
        }
        val outputFormat = FlavoredFormat.parse(to)
        require(inputFormat.name in listOf("dokuwiki", "fb2", "json", "latex", "native")) { "Supported input formats: dokuwiki, fb2, json, latex, native (requested $from)" }
        require(outputFormat.name in listOf("dokuwiki", "fb2", "json", "native") || outputFormat.name in HtmlCliWriter.formats) { "Unsupported output format: $to" }
        val inputExtensions = ExtensionRegistry.config(inputFormat.name).apply(inputFormat)
        val outputExtensions = ExtensionRegistry.config(outputFormat.name).apply(outputFormat)
        from = inputFormat.name
        to = outputFormat.name
        require(columns > 0) { "Column count must be positive" }
        val inputBytes = if (input == null || input == "-") System.`in`.readBytes() else File(input).readBytes()
        val text = if (from == "latex") "" else inputBytes.toString(Charsets.UTF_8)
        val media = MediaBag()
        var document = when (from) {
            "dokuwiki" -> DokuWikiCli.read(inputBytes, if (input == null || input == "-") "" else input,
                ReaderOptions(extensions = if (htmlOptions.options.tableOfContents) inputExtensions + io.heapy.kotdoc.options.Extensions.of(io.heapy.kotdoc.options.ExtensionName.AutoIdentifiers) else inputExtensions,
                    standalone = htmlOptions.standaloneFor(to), columns = columns.toLong(), tabStop = htmlOptions.options.tabStop, defaultImageExtension = defaultImageExtension), preserveTabs, trace)
            "json" -> PandocJson.decode(text)
            "native" -> NativeReader.read(text)
            "latex" -> LaTeXCliReader.read(inputBytes,if (input == null || input == "-") "" else input,
                ReaderOptions(extensions=inputExtensions,columns=columns.toLong(),tabStop=tabStop,defaultImageExtension=defaultImageExtension),preserveTabs,resourcePaths).also { resourcePaths=it.resourcePaths }.document
            else -> FB2Reader.readWithEffects(text.removePrefix("\uFEFF")).also { result ->
                result.mediaInsertions.forEach(media::insert)
                for (message in result.messages) if (message is ReaderMessage.UnexpectedXmlElement)
                    System.err.println("[WARNING] Unexpected XML element ${message.name} in ${message.parent}")
            }.document
        }
        val resources = ResourceContext(media, resourcePaths)
        extractMedia?.let { destination ->
            document = MediaOperations.fill(document, media, resources, ::reportResource)
            document = MediaOperations.extract(destination, document, media, ::reportResource)
        }
        val writerOptions = htmlOptions.finish(htmlOptions.options.copy(columns=columns.toLong(), mathMethod=mathMethod,
            extensions=outputExtensions), to, input, output, resources)
        val rendered = when (to) {
            "dokuwiki" -> DokuWikiCli.write(writerOptions, document)
            "json" -> PandocJson.encode(document)
            "native" -> NativeWriter.write(document, writerOptions)
            in HtmlCliWriter.formats -> HtmlCliWriter.write(to, writerOptions, document)
            else -> FB2Writer.writeWithEffects(document, writerOptions, resources = resources).also { result ->
                for (message in result.messages) when (message) {
                    is WriterMessage.CouldNotFetchResource -> System.err.println("[WARNING] Could not fetch resource ${message.path}" +
                        if (message.message.isEmpty()) "" else ": ${message.message}")
                    is WriterMessage.CouldNotDetermineMimeType -> System.err.println("[WARNING] Could not determine mime type for ${message.path}")
                    else -> Unit // Other FB2 writer messages have INFO severity upstream.
                }
            }.text
        }
        val converted = if (writerOptions.template != null || rendered.endsWith('\n')) rendered else "$rendered\n"
        if (output == null || output == "-") print(converted) else File(output).writeText(converted)
    } catch (e: MathMethodArgumentException) {
        System.err.println(e.message)
        exitProcess(6)
    } catch (e: FormatSpecificationException) {
        System.err.println(e.message)
        exitProcess(20)
    } catch (e: UnsupportedExtensionException) {
        System.err.println(e.message)
        exitProcess(23)
    } catch (e: PandocError) {
        System.err.println(e.renderError())
        exitProcess(e.exitCode())
    } catch (e: Exception) {
        System.err.println("kotdoc: ${e.message}")
        exitProcess(1)
    }
}

internal fun reportResource(event: ResourceEvent) {
    val message = when (event) {
        is ResourceEvent.CouldNotFetch -> "Could not fetch resource ${event.path}" + if (event.message.isEmpty()) "" else ": ${event.message}"
        is ResourceEvent.IgnoredIOError -> "IO Error (ignored): ${event.message}"
        else -> return
    }
    System.err.println("[WARNING] " + message.replace("\n", "\n  "))
}
