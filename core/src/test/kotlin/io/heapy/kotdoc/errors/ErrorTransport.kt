package io.heapy.kotdoc.errors

import io.heapy.kotdoc.layout.*
import kotlinx.serialization.json.*

internal class ErrorTransport {
    private val identities=Array(8) { Any() }
    fun chars(v: JsonElement)=CharacterString(v.jsonArray.map { it.jsonPrimitive.int }.toIntArray())
    fun text(v: JsonElement)=TextChars.fromPoints(chars(v).points())
    fun io(v: JsonElement): GhcIoError {
        val a=v.jsonArray;val h=a[5].takeUnless { it==JsonNull }?.jsonArray
        val handle=h?.let { if(it[0].jsonPrimitive.content=="file") ErrorHandle.File(chars(it[1]),identities[it[2].jsonPrimitive.int]) else ErrorHandle.Duplex(chars(it[1]),identities[it[2].jsonPrimitive.int],identities[it[3].jsonPrimitive.int]) }
        return GhcIoError(IoErrorType.entries[a[0].jsonPrimitive.int],chars(a[1]),chars(a[2]),a[4].takeUnless { it==JsonNull }?.let(::chars),a[3].takeUnless { it==JsonNull }?.jsonPrimitive?.long?.toInt(),handle)
    }
    fun cite(v: JsonElement): CiteprocError { val a=v.jsonArray;return when(a[0].jsonPrimitive.content) {
        "CiteprocXMLError" -> CiteprocError.CiteprocXMLError(text(a[1]))
        "CiteprocParseError" -> CiteprocError.CiteprocParseError(text(a[1]))
        "CiteprocLocaleNotFound" -> CiteprocError.CiteprocLocaleNotFound(text(a[1]))
        else -> kotlin.error(a[0])
    } }
    fun error(v: JsonElement): PandocError {
        val a=v.jsonArray
        return when(a[0].jsonPrimitive.content) {
            "PandocIOError" -> PandocError.PandocIOError(text(a[1]), io(a[2]))
            "PandocHttpError" -> PandocError.PandocHttpError(text(a[1]), text(a[2]))
            "PandocShouldNeverHappenError" -> PandocError.PandocShouldNeverHappenError(text(a[1]))
            "PandocSomeError" -> PandocError.PandocSomeError(text(a[1]))
            "PandocParseError" -> PandocError.PandocParseError(text(a[1]))
            "PandocMakePDFError" -> PandocError.PandocMakePDFError(text(a[1]))
            "PandocOptionError" -> PandocError.PandocOptionError(text(a[1]))
            "PandocSyntaxMapError" -> PandocError.PandocSyntaxMapError(text(a[1]))
            "PandocFailOnWarningError" -> PandocError.PandocFailOnWarningError
            "PandocPDFProgramNotFoundError" -> PandocError.PandocPDFProgramNotFoundError(text(a[1]))
            "PandocPDFError" -> PandocError.PandocPDFError(text(a[1]))
            "PandocXMLError" -> PandocError.PandocXMLError(text(a[1]), text(a[2]))
            "PandocFilterError" -> PandocError.PandocFilterError(text(a[1]), text(a[2]))
            "PandocLuaError" -> PandocError.PandocLuaError(text(a[1]))
            "PandocNoScriptingEngine" -> PandocError.PandocNoScriptingEngine
            "PandocCouldNotFindDataFileError" -> PandocError.PandocCouldNotFindDataFileError(text(a[1]))
            "PandocCouldNotFindMetadataFileError" -> PandocError.PandocCouldNotFindMetadataFileError(text(a[1]))
            "PandocResourceNotFound" -> PandocError.PandocResourceNotFound(text(a[1]))
            "PandocTemplateError" -> PandocError.PandocTemplateError(text(a[1]))
            "PandocNoTemplateError" -> PandocError.PandocNoTemplateError(text(a[1]))
            "PandocAppError" -> PandocError.PandocAppError(text(a[1]))
            "PandocEpubSubdirectoryError" -> PandocError.PandocEpubSubdirectoryError(text(a[1]))
            "PandocMacroLoop" -> PandocError.PandocMacroLoop(text(a[1]))
            "PandocUTF8DecodingError" -> PandocError.PandocUTF8DecodingError(text(a[1]), a[2].jsonPrimitive.long, a[3].jsonPrimitive.int)
            "PandocIpynbDecodingError" -> PandocError.PandocIpynbDecodingError(text(a[1]))
            "PandocUnsupportedCharsetError" -> PandocError.PandocUnsupportedCharsetError(text(a[1]))
            "PandocFormatError" -> PandocError.PandocFormatError(text(a[1]), text(a[2]))
            "PandocUnknownReaderError" -> PandocError.PandocUnknownReaderError(text(a[1]))
            "PandocUnknownWriterError" -> PandocError.PandocUnknownWriterError(text(a[1]))
            "PandocUnsupportedExtensionError" -> PandocError.PandocUnsupportedExtensionError(text(a[1]), text(a[2]))
            "PandocCiteprocError" -> PandocError.PandocCiteprocError(cite(a[1]))
            "PandocBibliographyError" -> PandocError.PandocBibliographyError(text(a[1]), text(a[2]))
            "PandocInputNotTextError" -> PandocError.PandocInputNotTextError(text(a[1]))
            else -> kotlin.error(a[0])
        }
    }
}
