// Text.Pandoc.Error, © 2006–2024 John MacFarlane. GPL-2.0-or-later; licenses/.
package io.heapy.kotdoc.errors

import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.formats.HaskellString

/** The exact common error family caught by PandocMonad's catchError. */
sealed class PandocError : Exception() {
    class PandocIOError(val context: String, val error: GhcIoError) : PandocError()
    class PandocHttpError(val url: String, val detail: String) : PandocError()
    class PandocShouldNeverHappenError(val detail: String) : PandocError()
    class PandocSomeError(val detail: String) : PandocError()
    class PandocParseError(val detail: String) : PandocError()
    class PandocMakePDFError(val detail: String) : PandocError()
    class PandocOptionError(val detail: String) : PandocError()
    class PandocSyntaxMapError(val detail: String) : PandocError()
    object PandocFailOnWarningError : PandocError()
    class PandocPDFProgramNotFoundError(val program: String) : PandocError()
    class PandocPDFError(val detail: String) : PandocError()
    class PandocXMLError(val file: String, val detail: String) : PandocError()
    class PandocFilterError(val filter: String, val detail: String) : PandocError()
    class PandocLuaError(val detail: String) : PandocError()
    object PandocNoScriptingEngine : PandocError()
    class PandocCouldNotFindDataFileError(val file: String) : PandocError()
    class PandocCouldNotFindMetadataFileError(val file: String) : PandocError()
    class PandocResourceNotFound(val file: String) : PandocError()
    class PandocTemplateError(val template: String) : PandocError()
    class PandocNoTemplateError(val file: String) : PandocError()
    class PandocAppError(val detail: String) : PandocError()
    class PandocEpubSubdirectoryError(val directory: String) : PandocError()
    class PandocMacroLoop(val macro: String) : PandocError()
    class PandocUTF8DecodingError(val file: String, val offset: Long, val byte: Int) : PandocError() { init { require(byte in 0..255) } }
    class PandocIpynbDecodingError(val detail: String) : PandocError()
    class PandocUnsupportedCharsetError(val charset: String) : PandocError()
    class PandocFormatError(val format: String, val detail: String) : PandocError()
    class PandocUnknownReaderError(val reader: String) : PandocError()
    class PandocUnknownWriterError(val writer: String) : PandocError()
    class PandocUnsupportedExtensionError(val extension: String, val format: String) : PandocError()
    class PandocCiteprocError(val error: CiteprocError) : PandocError()
    class PandocBibliographyError(val file: String, val detail: String) : PandocError()
    class PandocInputNotTextError(val file: String) : PandocError()

    override val message: String get() = renderError()
    private fun text(value: String) = TextChars.fromString(value)
    private fun quote(value: String) = "'${text(value)}'"
    fun renderError(): String = when (this) {
        is PandocIOError -> error.displayedText()
        is PandocHttpError -> "Could not fetch ${text(url)}\n${text(detail)}"
        is PandocShouldNeverHappenError -> "Something we thought was impossible happened!\nPlease report this to pandoc's developers: ${text(detail)}"
        is PandocSomeError -> text(detail)
        is PandocParseError -> text(detail)
        is PandocMakePDFError -> text(detail)
        is PandocOptionError -> text(detail)
        is PandocSyntaxMapError -> text(detail)
        PandocFailOnWarningError -> "Failing because there were warnings."
        is PandocPDFProgramNotFoundError -> "${quote(program)} not found. Please select a different --pdf-engine or install ${quote(program)}"
        is PandocPDFError -> "Error producing PDF.\n${text(detail)}"
        is PandocXMLError -> "Invalid XML" + (if (text(file).isEmpty()) "" else " in ${quote(file)}") + ":\n${text(detail)}"
        is PandocFilterError -> "Error running filter ${quote(filter)}:\n${text(detail)}"
        is PandocLuaError -> "Error running Lua:\n${text(detail)}"
        PandocNoScriptingEngine -> "This version of pandoc has been compiled without Lua support."
        is PandocCouldNotFindDataFileError -> "Could not find data file ${quote(file)}"
        is PandocCouldNotFindMetadataFileError -> "Could not find metadata file ${quote(file)}"
        is PandocResourceNotFound -> "File ${quote(file)} not found in resource path"
        is PandocTemplateError -> "Error compiling template ${quote(template)}"
        is PandocNoTemplateError -> "No template defined in ${quote(file)}"
        is PandocAppError -> text(detail)
        is PandocEpubSubdirectoryError -> "EPUB subdirectory name ${quote(directory)} contains illegal characters"
        is PandocMacroLoop -> "Loop encountered in expanding macro ${text(macro)}"
        is PandocUTF8DecodingError -> "UTF-8 decoding error in ${quote(file)} at byte offset $offset (${byte.toString(16).padStart(2,' ')}).\nThe input must be a UTF-8 encoded text."
        is PandocIpynbDecodingError -> "ipynb decoding error: ${text(detail)}"
        is PandocUnsupportedCharsetError -> "Unsupported charset ${quote(charset)}"
        is PandocFormatError -> "Error parsing format ${quote(format)}: ${text(detail)}"
        is PandocUnknownReaderError -> "Unknown input format ${quote(reader)}" + when (text(reader)) {
            "doc" -> "\nPandoc can convert from DOCX, but not from DOC.\nTry using Word to save your DOC file as DOCX, and convert that with pandoc."
            "pdf" -> "\nPandoc can convert to PDF, but not from PDF."
            else -> ""
        }
        is PandocUnknownWriterError -> "Unknown output format ${quote(writer)}" + when (text(writer)) {
            "pdf" -> "To create a pdf using pandoc, use -t latex|beamer|context|ms|html5|typst\nand specify an output file with .pdf extension (-o filename.pdf)."
            "doc" -> "\nPandoc can convert to DOCX, but not to DOC."
            else -> ""
        }
        is PandocUnsupportedExtensionError -> "The extension ${quote(extension)} is not supported for ${text(format)}.\nUse --list-extensions=${text(format)} to list supported extensions."
        is PandocCiteprocError -> error.pretty()
        is PandocBibliographyError -> "Error reading bibliography file ${quote(file)}:\n${text(detail)}"
        is PandocInputNotTextError -> "Expected text as an input, but received binary data from " +
            (if (text(file).isEmpty()) "stdin" else "file ${quote(file)}") +
            ".\nIf you intended to convert from binary format, verify that it's supported and use\nexplicit -f FORMAT."
    }
    fun exitCode(): Int = when (this) {
        is PandocIOError -> 1; PandocFailOnWarningError -> 3; is PandocAppError -> 4; is PandocTemplateError -> 5
        is PandocOptionError -> 6; is PandocFormatError -> 20; is PandocUnknownReaderError -> 21; is PandocUnknownWriterError -> 22
        is PandocUnsupportedExtensionError -> 23; is PandocCiteprocError -> 24; is PandocBibliographyError -> 25
        is PandocEpubSubdirectoryError -> 31; is PandocPDFError -> 43; is PandocXMLError -> 44; is PandocPDFProgramNotFoundError -> 47
        is PandocHttpError -> 61; is PandocShouldNeverHappenError -> 62; is PandocSomeError -> 63; is PandocParseError -> 64
        is PandocMakePDFError -> 66; is PandocSyntaxMapError -> 67; is PandocFilterError -> 83; is PandocLuaError -> 84
        is PandocNoTemplateError -> 87; PandocNoScriptingEngine -> 89; is PandocMacroLoop -> 91; is PandocUTF8DecodingError -> 92
        is PandocIpynbDecodingError -> 93; is PandocUnsupportedCharsetError -> 94; is PandocInputNotTextError -> 95
        is PandocCouldNotFindDataFileError -> 97; is PandocCouldNotFindMetadataFileError -> 98; is PandocResourceNotFound -> 99
    }
    /** Exact derived Show field order; IOException's custom Show is not quoted. */
    fun show(precedence: Long = 0): CharacterString {
        val shape = errorShape(this)
        val parts = mutableListOf(CharacterStringChars.fromString(shape.first))
        for (field in shape.second) {
            parts.add(CharacterStringChars.fromString(" "))
            parts.add(when (field) {
                is String -> CharacterStringChars.fromString(HaskellString.quote(text(field)))
                is Long -> CharacterStringChars.fromString(if (field < 0) "($field)" else field.toString())
                is Int -> CharacterStringChars.fromString(field.toString())
                is GhcIoError -> field.show()
                is CiteprocError -> CharacterStringChars.fromString(field.show(11))
                else -> error("Error field")
            })
        }
        if (shape.second.isNotEmpty() && precedence > 10) { parts.add(0,CharacterStringChars.fromString("(")); parts.add(CharacterStringChars.fromString(")")) }
        return CharacterStringChars.concat(parts)
    }
    override fun toString() = TextChars.fromPoints(show().points())
}

sealed interface PandocErrorResult<out A> {
    data class Success<A>(val value: A) : PandocErrorResult<A>
    data class Failure(val error: PandocError) : PandocErrorResult<Nothing>
}

/** IO effects remain at the boundary; the native-String and UTF8-Text paths differ. */
interface PandocErrorRuntime {
    fun writeNativeLine(value: CharacterString)
    fun writeUtf8Line(value: String)
    fun exit(code: Int): Nothing
}
fun <A> handleError(result: PandocErrorResult<A>, runtime: PandocErrorRuntime): A = when (result) {
    is PandocErrorResult.Success -> result.value
    is PandocErrorResult.Failure -> {
        val error = result.error
        if (error is PandocError.PandocIOError) runtime.writeNativeLine(error.error.show()) else runtime.writeUtf8Line(error.renderError())
        runtime.exit(error.exitCode())
    }
}
