package io.heapy.kotdoc.cli

import io.heapy.kotdoc.ast.Pandoc
import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.logging.Logging
import io.heapy.kotdoc.options.WriterOptions
import io.heapy.kotdoc.resources.PackagedPandocData
import io.heapy.kotdoc.state.PandocState
import io.heapy.kotdoc.state.PandocStateContext
import io.heapy.kotdoc.translations.TranslationRuntime
import io.heapy.kotdoc.writers.HtmlWriter

/** All HTML-family CLI writers use the Kotlin library and its ordinary log effects. */
internal object HtmlCliWriter {
    val formats = setOf("html", "html4", "html5", "s5", "slidy", "slideous", "dzslides", "revealjs")

    fun write(format: String, options: WriterOptions, document: Pandoc): String {
        val runtime = Runtime()
        return when (format) {
            "html", "html5" -> HtmlWriter.writeHtml5String(options, document, runtime)
            "html4" -> HtmlWriter.writeHtml4String(options, document, runtime)
            "s5" -> HtmlWriter.writeS5(options, document, runtime)
            "slidy" -> HtmlWriter.writeSlidy(options, document, runtime)
            "slideous" -> HtmlWriter.writeSlideous(options, document, runtime)
            "dzslides" -> HtmlWriter.writeDZSlides(options, document, runtime)
            "revealjs" -> HtmlWriter.writeRevealJs(options, document, runtime)
            else -> error("Unsupported HTML writer: $format")
        }
    }

    private class Runtime : TranslationRuntime, PandocState by PandocStateContext(output = { _, message ->
        System.err.println("[${Logging.messageVerbosity(message)}] " + Logging.showLogMessage(message).replace("\n", "\n  "))
    }) {
        override fun readDataFile(file: CharacterString): ByteArray = PackagedPandocData.read(file)
            ?: throw PandocError.PandocCouldNotFindDataFileError(TextChars.fromPoints(file.points()))
    }
}
