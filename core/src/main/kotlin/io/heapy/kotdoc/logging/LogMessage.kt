// Text.Pandoc.Logging, Pandoc 3.12.1; Copyright (C) 2006-2024 John MacFarlane, GPL-2.0-or-later.
package io.heapy.kotdoc.logging

import io.heapy.kotdoc.ast.Inline
import io.heapy.kotdoc.ast.Block
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.parsing.SourcePos
import java.math.BigInteger

/** All original diagnostic constructors; source dictionaries are exposed by Logging. */
sealed interface LogMessage {
    data class SkippedContent(val contents: String, val position: SourcePos) : LogMessage
    data class IgnoredElement(val contents: String) : LogMessage
    data class DuplicateLinkReference(val contents: String, val position: SourcePos) : LogMessage
    data class DuplicateNoteReference(val contents: String, val position: SourcePos) : LogMessage
    data class NoteDefinedButNotUsed(val contents: String, val position: SourcePos) : LogMessage
    data class DuplicateIdentifier(val contents: String, val position: SourcePos) : LogMessage
    data class ReferenceNotFound(val contents: String, val position: SourcePos) : LogMessage
    data class CircularReference(val contents: String, val position: SourcePos) : LogMessage
    data class UndefinedToggle(val contents: String, val position: SourcePos) : LogMessage
    data class ParsingUnescaped(val contents: String, val position: SourcePos) : LogMessage
    data class CouldNotLoadIncludeFile(val path: String, val position: SourcePos) : LogMessage
    data class CouldNotParseIncludeFile(val path: String, val position: SourcePos) : LogMessage
    data class MacroAlreadyDefined(val name: String, val position: SourcePos) : LogMessage
    data class InlineNotRendered(val inline: Inline) : LogMessage
    data class BlockNotRendered(val block: Block) : LogMessage
    data class DocxParserWarning(val contents: String) : LogMessage
    data class PowerpointTemplateWarning(val contents: String) : LogMessage
    data class IgnoredIOError(val contents: String) : LogMessage
    data class CouldNotFetchResource(val path: String, val message: String) : LogMessage
    data class CouldNotDetermineImageSize(val path: String, val message: String) : LogMessage
    data class CouldNotConvertImage(val path: String, val message: String) : LogMessage
    data class CouldNotDetermineMimeType(val path: String) : LogMessage
    data class CouldNotConvertTeXMath(val contents: String, val message: String) : LogMessage
    data class CouldNotParseCSS(val message: String) : LogMessage
    data class Fetching(val path: String) : LogMessage
    data class Extracting(val path: String) : LogMessage
    data class LoadedResource(val original: CharacterString, val found: CharacterString) : LogMessage {
        constructor(original: String, found: String) : this(CharacterStringChars.fromString(original), CharacterStringChars.fromString(found))
    }
    data class ScriptingInfo(val message: String, val position: SourcePos?) : LogMessage
    data class ScriptingWarning(val message: String, val position: SourcePos?) : LogMessage
    data class NoTitleElement(val fallback: String) : LogMessage
    data object NoLangSpecified : LogMessage
    data class InvalidLang(val lang: String) : LogMessage
    data class CouldNotHighlight(val message: String) : LogMessage
    data class MissingCharacter(val message: String) : LogMessage
    data class Deprecated(val thing: String, val message: String) : LogMessage
    data class NoTranslation(val term: String) : LogMessage
    data class CouldNotLoadTranslations(val lang: String, val message: String) : LogMessage
    data class UnusualConversion(val message: String) : LogMessage
    data class UnexpectedXmlElement(val element: String, val parent: String) : LogMessage
    data class UnknownOrgExportOption(val option: String) : LogMessage
    data class CouldNotDeduceFormat(val extensions: List<String>, val format: String) : LogMessage
    data class RunningFilter(val path: CharacterString) : LogMessage {
        constructor(path: String) : this(CharacterStringChars.fromString(path))
    }
    data class FilterCompleted(val path: CharacterString, val milliseconds: BigInteger) : LogMessage {
        constructor(path: String, milliseconds: BigInteger) : this(CharacterStringChars.fromString(path), milliseconds)
    }
    data class CiteprocWarning(val message: String) : LogMessage
    data class ATXHeadingInLHS(val level: Long, val contents: String) : LogMessage
    data class EnvironmentVariableUndefined(val variable: String) : LogMessage
    data class DuplicateAttribute(val attribute: String, val value: String) : LogMessage
    data class NotUTF8Encoded(val source: CharacterString) : LogMessage {
        constructor(source: String) : this(CharacterStringChars.fromString(source))
    }
    data class MakePDFInfo(val description: String, val contents: String) : LogMessage
    data class MakePDFWarning(val message: String) : LogMessage
    data class UnclosedDiv(val openPosition: SourcePos, val closePosition: SourcePos) : LogMessage
    data class UnsupportedCodePage(val codepage: Long) : LogMessage
    data class YamlWarning(val position: SourcePos, val message: String) : LogMessage
    data class UnsupportedPdfStandard(val contents: String) : LogMessage
}

internal class LogShape(val tag: Int, val name: String, vararg val fields: Any?)
internal fun shape(message: LogMessage): LogShape = when(message) {
    is LogMessage.SkippedContent -> LogShape(0,"SkippedContent", message.contents, message.position)
    is LogMessage.IgnoredElement -> LogShape(1,"IgnoredElement", message.contents)
    is LogMessage.DuplicateLinkReference -> LogShape(2,"DuplicateLinkReference", message.contents, message.position)
    is LogMessage.DuplicateNoteReference -> LogShape(3,"DuplicateNoteReference", message.contents, message.position)
    is LogMessage.NoteDefinedButNotUsed -> LogShape(4,"NoteDefinedButNotUsed", message.contents, message.position)
    is LogMessage.DuplicateIdentifier -> LogShape(5,"DuplicateIdentifier", message.contents, message.position)
    is LogMessage.ReferenceNotFound -> LogShape(6,"ReferenceNotFound", message.contents, message.position)
    is LogMessage.CircularReference -> LogShape(7,"CircularReference", message.contents, message.position)
    is LogMessage.UndefinedToggle -> LogShape(8,"UndefinedToggle", message.contents, message.position)
    is LogMessage.ParsingUnescaped -> LogShape(9,"ParsingUnescaped", message.contents, message.position)
    is LogMessage.CouldNotLoadIncludeFile -> LogShape(10,"CouldNotLoadIncludeFile", message.path, message.position)
    is LogMessage.CouldNotParseIncludeFile -> LogShape(11,"CouldNotParseIncludeFile", message.path, message.position)
    is LogMessage.MacroAlreadyDefined -> LogShape(12,"MacroAlreadyDefined", message.name, message.position)
    is LogMessage.InlineNotRendered -> LogShape(13,"InlineNotRendered", message.inline)
    is LogMessage.BlockNotRendered -> LogShape(14,"BlockNotRendered", message.block)
    is LogMessage.DocxParserWarning -> LogShape(15,"DocxParserWarning", message.contents)
    is LogMessage.PowerpointTemplateWarning -> LogShape(16,"PowerpointTemplateWarning", message.contents)
    is LogMessage.IgnoredIOError -> LogShape(17,"IgnoredIOError", message.contents)
    is LogMessage.CouldNotFetchResource -> LogShape(18,"CouldNotFetchResource", message.path, message.message)
    is LogMessage.CouldNotDetermineImageSize -> LogShape(19,"CouldNotDetermineImageSize", message.path, message.message)
    is LogMessage.CouldNotConvertImage -> LogShape(20,"CouldNotConvertImage", message.path, message.message)
    is LogMessage.CouldNotDetermineMimeType -> LogShape(21,"CouldNotDetermineMimeType", message.path)
    is LogMessage.CouldNotConvertTeXMath -> LogShape(22,"CouldNotConvertTeXMath", message.contents, message.message)
    is LogMessage.CouldNotParseCSS -> LogShape(23,"CouldNotParseCSS", message.message)
    is LogMessage.Fetching -> LogShape(24,"Fetching", message.path)
    is LogMessage.Extracting -> LogShape(25,"Extracting", message.path)
    is LogMessage.LoadedResource -> LogShape(26,"LoadedResource", message.original, message.found)
    is LogMessage.ScriptingInfo -> LogShape(27,"ScriptingInfo", message.message, message.position)
    is LogMessage.ScriptingWarning -> LogShape(28,"ScriptingWarning", message.message, message.position)
    is LogMessage.NoTitleElement -> LogShape(29,"NoTitleElement", message.fallback)
    LogMessage.NoLangSpecified -> LogShape(30,"NoLangSpecified")
    is LogMessage.InvalidLang -> LogShape(31,"InvalidLang", message.lang)
    is LogMessage.CouldNotHighlight -> LogShape(32,"CouldNotHighlight", message.message)
    is LogMessage.MissingCharacter -> LogShape(33,"MissingCharacter", message.message)
    is LogMessage.Deprecated -> LogShape(34,"Deprecated", message.thing, message.message)
    is LogMessage.NoTranslation -> LogShape(35,"NoTranslation", message.term)
    is LogMessage.CouldNotLoadTranslations -> LogShape(36,"CouldNotLoadTranslations", message.lang, message.message)
    is LogMessage.UnusualConversion -> LogShape(37,"UnusualConversion", message.message)
    is LogMessage.UnexpectedXmlElement -> LogShape(38,"UnexpectedXmlElement", message.element, message.parent)
    is LogMessage.UnknownOrgExportOption -> LogShape(39,"UnknownOrgExportOption", message.option)
    is LogMessage.CouldNotDeduceFormat -> LogShape(40,"CouldNotDeduceFormat", message.extensions, message.format)
    is LogMessage.RunningFilter -> LogShape(41,"RunningFilter", message.path)
    is LogMessage.FilterCompleted -> LogShape(42,"FilterCompleted", message.path, message.milliseconds)
    is LogMessage.CiteprocWarning -> LogShape(43,"CiteprocWarning", message.message)
    is LogMessage.ATXHeadingInLHS -> LogShape(44,"ATXHeadingInLHS", message.level, message.contents)
    is LogMessage.EnvironmentVariableUndefined -> LogShape(45,"EnvironmentVariableUndefined", message.variable)
    is LogMessage.DuplicateAttribute -> LogShape(46,"DuplicateAttribute", message.attribute, message.value)
    is LogMessage.NotUTF8Encoded -> LogShape(47,"NotUTF8Encoded", message.source)
    is LogMessage.MakePDFInfo -> LogShape(48,"MakePDFInfo", message.description, message.contents)
    is LogMessage.MakePDFWarning -> LogShape(49,"MakePDFWarning", message.message)
    is LogMessage.UnclosedDiv -> LogShape(50,"UnclosedDiv", message.openPosition, message.closePosition)
    is LogMessage.UnsupportedCodePage -> LogShape(51,"UnsupportedCodePage", message.codepage)
    is LogMessage.YamlWarning -> LogShape(52,"YamlWarning", message.position, message.message)
    is LogMessage.UnsupportedPdfStandard -> LogShape(53,"UnsupportedPdfStandard", message.contents)
}
