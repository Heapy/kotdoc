// Text.Pandoc.Logging, Pandoc 3.12.1; Copyright (C) 2006-2024 John MacFarlane, GPL-2.0-or-later.
package io.heapy.kotdoc.logging

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.json.*
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.parsing.SourcePos
import java.math.BigInteger

object Logging {
    private fun text(value: String) = TextChars.fromString(value)
    private fun raw(value: CharacterString) = TextChars.fromPoints(value.points())
    private fun showPos(p: SourcePos) =
        (if(p.name in listOf("source","","-")) "" else p.nameText + " ") + "line ${p.line} column ${p.column}"

    fun messageVerbosity(message: LogMessage): Verbosity = when(message) {
        is LogMessage.SkippedContent, is LogMessage.IgnoredElement, is LogMessage.ParsingUnescaped,
        is LogMessage.InlineNotRendered, is LogMessage.BlockNotRendered, is LogMessage.Fetching,
        is LogMessage.Extracting, is LogMessage.LoadedResource, is LogMessage.ScriptingInfo,
        is LogMessage.NoTitleElement, LogMessage.NoLangSpecified, is LogMessage.RunningFilter,
        is LogMessage.FilterCompleted, is LogMessage.MakePDFInfo -> Verbosity.INFO
        is LogMessage.CouldNotLoadIncludeFile -> if(text(message.path).endsWith(".sty")) Verbosity.INFO else Verbosity.WARNING
        else -> Verbosity.WARNING
    }

    fun showLogMessage(message: LogMessage): String = when(val m=message) {
        is LogMessage.SkippedContent -> "Skipped '${text(m.contents)}' at ${showPos(m.position)}"
        is LogMessage.IgnoredElement -> "Ignored element ${text(m.contents)}"
        is LogMessage.DuplicateLinkReference -> "Duplicate link reference '${text(m.contents)}' at ${showPos(m.position)}"
        is LogMessage.DuplicateNoteReference -> "Duplicate note reference '${text(m.contents)}' at ${showPos(m.position)}"
        is LogMessage.NoteDefinedButNotUsed -> "Note with key '${text(m.contents)}' defined at ${showPos(m.position)} but not used."
        is LogMessage.DuplicateIdentifier -> "Duplicate identifier '${text(m.contents)}' at ${showPos(m.position)}"
        is LogMessage.ReferenceNotFound -> "Reference not found for '${text(m.contents)}' at ${showPos(m.position)}"
        is LogMessage.CircularReference -> "Circular reference '${text(m.contents)}' at ${showPos(m.position)}"
        is LogMessage.UndefinedToggle -> "Undefined toggle '${text(m.contents)}' at ${showPos(m.position)}"
        is LogMessage.ParsingUnescaped -> "Parsing unescaped '${text(m.contents)}' at ${showPos(m.position)}"
        is LogMessage.CouldNotLoadIncludeFile -> "Could not load include file ${text(m.path)} at ${showPos(m.position)}"
        is LogMessage.CouldNotParseIncludeFile -> "Parsing include file ${text(m.path)} failed at ${showPos(m.position)}"
        is LogMessage.MacroAlreadyDefined -> "Macro '${text(m.name)}' already defined, ignoring at ${showPos(m.position)}"
        is LogMessage.InlineNotRendered -> "Not rendering " + AstShow.show(m.inline)
        is LogMessage.BlockNotRendered -> "Not rendering " + AstShow.show(m.block)
        is LogMessage.DocxParserWarning -> "Docx parser warning: ${text(m.contents)}"
        is LogMessage.PowerpointTemplateWarning -> "Powerpoint template warning: ${text(m.contents)}"
        is LogMessage.IgnoredIOError -> "IO Error (ignored): ${text(m.contents)}"
        is LogMessage.CouldNotFetchResource -> "Could not fetch resource ${text(m.path)}" + if(m.message.isEmpty()) "" else ": ${text(m.message)}"
        is LogMessage.CouldNotDetermineImageSize -> "Could not determine image size for ${text(m.path)}" + if(m.message.isEmpty()) "" else ": ${text(m.message)}"
        is LogMessage.CouldNotConvertImage -> "Could not convert image ${text(m.path)}" + if(m.message.isEmpty()) "" else ": ${text(m.message)}"
        is LogMessage.CouldNotDetermineMimeType -> "Could not determine mime type for ${text(m.path)}"
        is LogMessage.CouldNotConvertTeXMath -> "Could not convert TeX math ${text(m.contents)}, rendering as TeX" + if(m.message.isEmpty()) "" else ":\n${text(m.message)}"
        is LogMessage.CouldNotParseCSS -> "Could not parse CSS" + if(m.message.isEmpty()) "" else ":\n${text(m.message)}"
        is LogMessage.Fetching -> "Fetching ${text(m.path)}..."
        is LogMessage.Extracting -> "Extracting ${text(m.path)}..."
        is LogMessage.LoadedResource -> "Loaded ${raw(m.original)} from ${raw(m.found)}"
        is LogMessage.ScriptingInfo -> "Scripting info" + (m.position?.let { " at " + showPos(it) } ?: "") + ": ${text(m.message)}"
        is LogMessage.ScriptingWarning -> "Scripting warning" + (m.position?.let { " at " + showPos(it) } ?: "") + ": ${text(m.message)}"
        is LogMessage.NoTitleElement -> "This document format requires a nonempty <title> element.\nDefaulting to '${text(m.fallback)}' as the title.\nTo specify a title, use --variable pagetitle=\"...\"."
        LogMessage.NoLangSpecified -> "No value for 'lang' was specified in the metadata.\nIt is recommended that lang be specified for this format."
        is LogMessage.InvalidLang -> "Invalid 'lang' value '${text(m.lang)}'.\nUse an IETF language tag like 'en-US'."
        is LogMessage.CouldNotHighlight -> "Could not highlight code block:\n${text(m.message)}"
        is LogMessage.MissingCharacter -> "Missing character: ${text(m.message)}"
        is LogMessage.Deprecated -> "Deprecated: ${text(m.thing)}" + if(m.message.isEmpty()) "" else ". ${text(m.message)}"
        is LogMessage.NoTranslation -> "The term ${text(m.term)} has no translation defined."
        is LogMessage.CouldNotLoadTranslations -> "Could not load translations for ${text(m.lang)}" + if(m.message.isEmpty()) "" else "\n${text(m.message)}"
        is LogMessage.UnusualConversion -> "Unusual conversion: ${text(m.message)}"
        is LogMessage.UnexpectedXmlElement -> "Unexpected XML element ${text(m.element)} in ${text(m.parent)}"
        is LogMessage.UnknownOrgExportOption -> "Ignoring unknown Org export option: ${text(m.option)}"
        is LogMessage.CouldNotDeduceFormat -> "Could not deduce format from file extension " + m.extensions.joinToString(" or ",transform=::text) + "\nDefaulting to ${text(m.format)}"
        is LogMessage.RunningFilter -> "Running filter ${raw(m.path)}"
        is LogMessage.FilterCompleted -> "Completed filter ${raw(m.path)} in ${m.milliseconds} ms"
        is LogMessage.CiteprocWarning -> "Citeproc: ${text(m.message)}"
        is LogMessage.ATXHeadingInLHS -> "Rendering heading '${text(m.contents)}' as a paragraph.\nATX headings cannot be used in literate Haskell, because '#' is not\nallowed in column 1." + if(m.level < 3) " Consider using --markdown-headings=setext." else ""
        is LogMessage.EnvironmentVariableUndefined -> "Undefined environment variable ${text(m.variable)} in defaults file."
        is LogMessage.DuplicateAttribute -> "Ignoring duplicate attribute ${text(m.attribute)}=" + HaskellString.quote(text(m.value)) + "."
        is LogMessage.NotUTF8Encoded -> "${raw(m.source)} is not UTF-8 encoded: falling back to latin1."
        is LogMessage.MakePDFInfo -> "[makePDF] ${text(m.description)}" + if(m.contents.isEmpty()) "" else "\n${text(m.contents)}"
        is LogMessage.MakePDFWarning -> "[makePDF] ${text(m.message)}"
        is LogMessage.UnclosedDiv -> "Div at ${showPos(m.openPosition)} unclosed at ${showPos(m.closePosition)}, closing implicitly."
        is LogMessage.UnsupportedCodePage -> "Unsupported code page ${m.codepage}. Text will likely be garbled."
        is LogMessage.YamlWarning -> "YAML warning (${showPos(m.position)}): ${text(m.message)}"
        is LogMessage.UnsupportedPdfStandard -> "PDF standard '${text(m.contents)}' is not supported by LaTeX and will be ignored."
    }

    fun toValue(message: LogMessage): PrettyValue.Object = PrettyValue.Object(buildMap {
        fun s(key: String,value: String) { put(key,PrettyValue.Text(value)) }
        fun r(key: String,value: CharacterString) = s(key,raw(value))
        fun n(key: String,value: Long) { put(key,PrettyValue.Number(ScientificNumber(value))) }
        fun p(position: SourcePos) { s("source",position.nameText);n("line",position.line);n("column",position.column) }
        fun pos(position: SourcePos) = PrettyValue.Object(mapOf("source" to PrettyValue.Text(position.nameText),
            "line" to PrettyValue.Number(ScientificNumber(position.line)),"column" to PrettyValue.Number(ScientificNumber(position.column))))
        s("verbosity",messageVerbosity(message).name);s("type",shape(message).name);s("pretty",showLogMessage(message))
        when(val m=message) {
            is LogMessage.SkippedContent -> { s("contents",m.contents);p(m.position) }
            is LogMessage.IgnoredElement -> s("contents",m.contents)
            is LogMessage.DuplicateLinkReference -> { s("contents",m.contents);p(m.position) }
            is LogMessage.DuplicateNoteReference -> { s("contents",m.contents);p(m.position) }
            is LogMessage.NoteDefinedButNotUsed -> { s("key",m.contents);p(m.position) }
            is LogMessage.DuplicateIdentifier -> { s("contents",m.contents);p(m.position) }
            is LogMessage.ReferenceNotFound -> { s("contents",m.contents);p(m.position) }
            is LogMessage.CircularReference -> { s("contents",m.contents);p(m.position) }
            is LogMessage.UndefinedToggle -> { s("contents",m.contents);p(m.position) }
            is LogMessage.ParsingUnescaped -> { s("contents",m.contents);p(m.position) }
            is LogMessage.CouldNotLoadIncludeFile -> { s("path",m.path);p(m.position) }
            is LogMessage.CouldNotParseIncludeFile -> { s("path",m.path);p(m.position) }
            is LogMessage.MacroAlreadyDefined -> { s("name",m.name);p(m.position) }
            is LogMessage.InlineNotRendered -> put("contents",AstJsonValues.toValue(m.inline))
            is LogMessage.BlockNotRendered -> put("contents",AstJsonValues.toValue(m.block))
            is LogMessage.DocxParserWarning -> s("contents",m.contents)
            is LogMessage.PowerpointTemplateWarning -> s("contents",m.contents)
            is LogMessage.IgnoredIOError -> s("contents",m.contents)
            is LogMessage.CouldNotFetchResource -> { s("path",m.path);s("message",m.message) }
            is LogMessage.CouldNotDetermineImageSize -> { s("path",m.path);s("message",m.message) }
            is LogMessage.CouldNotConvertImage -> { s("path",m.path);s("message",m.message) }
            is LogMessage.CouldNotDetermineMimeType -> s("path",m.path)
            is LogMessage.CouldNotConvertTeXMath -> { s("contents",m.contents);s("message",m.message) }
            is LogMessage.CouldNotParseCSS -> s("message",m.message)
            is LogMessage.Fetching -> s("path",m.path)
            is LogMessage.Extracting -> s("path",m.path)
            is LogMessage.LoadedResource -> { r("for",m.original);r("from",m.found) }
            is LogMessage.ScriptingInfo -> { s("message",m.message);m.position?.let(::p) }
            is LogMessage.ScriptingWarning -> { s("message",m.message);m.position?.let(::p) }
            is LogMessage.NoTitleElement -> s("fallback",m.fallback)
            LogMessage.NoLangSpecified -> Unit
            is LogMessage.InvalidLang -> s("lang",m.lang)
            is LogMessage.CouldNotHighlight -> s("message",m.message)
            is LogMessage.MissingCharacter -> s("message",m.message)
            is LogMessage.Deprecated -> { s("thing",m.thing);s("message",m.message) }
            is LogMessage.NoTranslation -> s("term",m.term)
            is LogMessage.CouldNotLoadTranslations -> { s("lang",m.lang);s("message",m.message) }
            is LogMessage.UnusualConversion -> s("message",m.message)
            is LogMessage.UnexpectedXmlElement -> { s("element",m.element);s("parent",m.parent) }
            is LogMessage.UnknownOrgExportOption -> s("option",m.option)
            is LogMessage.CouldNotDeduceFormat -> { put("extensions",PrettyValue.Array(m.extensions.map { PrettyValue.Text(it) }));s("format",m.format) }
            is LogMessage.RunningFilter -> r("path",m.path)
            is LogMessage.FilterCompleted -> { r("path",m.path);s("milliseconds",m.milliseconds.toString()) }
            is LogMessage.CiteprocWarning -> s("message",m.message)
            is LogMessage.ATXHeadingInLHS -> { n("level",m.level);s("contents",m.contents) }
            is LogMessage.EnvironmentVariableUndefined -> s("variable",m.variable)
            is LogMessage.DuplicateAttribute -> { s("attribute",m.attribute);s("value",m.value) }
            is LogMessage.NotUTF8Encoded -> r("source",m.source)
            is LogMessage.MakePDFInfo -> { s("description",m.description);s("contents",m.contents) }
            is LogMessage.MakePDFWarning -> s("message",m.message)
            is LogMessage.UnclosedDiv -> { put("openpos",pos(m.openPosition));put("closepos",pos(m.closePosition)) }
            is LogMessage.UnsupportedCodePage -> n("codepage",m.codepage)
            is LogMessage.YamlWarning -> { p(m.position);s("message",m.message) }
            is LogMessage.UnsupportedPdfStandard -> s("contents",m.contents)
        }
    })
    private val config = PrettyJsonConfig(compare=PrettyJson.keyOrder(listOf("type","verbosity","contents","message","path","source","line","column")))
    fun encodeLogMessages(messages: List<LogMessage>): ByteArray = encodeLogMessagesText(messages).toByteArray(Charsets.UTF_8)
    fun encodeLogMessagesText(messages: List<LogMessage>): String = PrettyJson.encode(PrettyValue.Array(messages.map(::toValue)),config)

    /** Derived Show emits Haskell Chars, including the unescaped raw SourcePos name. */
    fun showChunks(message: LogMessage,precedence: Long = 0): Sequence<CharacterString> = sequence {
        val s=shape(message);val wrap=precedence>10 && s.fields.isNotEmpty()
        fun chars(t: String)=CharacterStringChars.fromString(t)
        if(wrap) yield(chars("("));yield(chars(s.name))
        s.fields.forEachIndexed { i,v ->
            yield(chars(" "))
            val optional=i==1 && (message is LogMessage.ScriptingInfo || message is LogMessage.ScriptingWarning)
            if(optional) {
                if(v==null) yield(chars("Nothing")) else {
                    yield(chars("(Just "));yield((v as SourcePos).showCharacters());yield(chars(")"))
                }
            } else when(v) {
                is SourcePos -> yield(v.showCharacters())
                is CharacterString -> yield(chars(HaskellString.quoteCodePoints(v.points())))
                is BigInteger -> yield(chars(if(v.signum()<0) "($v)" else v.toString()))
                else -> yieldAll(AstShow.chunks(checkNotNull(v),11).map(::chars))
            }
        }
        if(wrap) yield(chars(")"))
    }
    fun show(message: LogMessage,precedence: Long = 0): CharacterString =
        CharacterStringChars.concat(showChunks(message,precedence).asIterable())
    fun shownText(message: LogMessage,precedence: Long = 0): String = raw(show(message,precedence))
    fun showPrefix(message: LogMessage,count: Int,precedence: Long = 0): CharacterString {
        if(count<=0) return CharacterStringChars.empty
        val out=mutableListOf<Int>();var left=count
        for(chunk in showChunks(message,precedence)) {
            val points=chunk.points();val n=minOf(left,points.size);out.addAll(points.take(n));left-=n
            if(left==0) break
        }
        return CharacterString(out.toIntArray())
    }

    private fun equalField(a: Any?,b: Any?): Boolean = when {
        a==null || b==null -> a==null && b==null
        a is String && b is String -> AstComparison.Text.equal(a,b)
        a is Inline && b is Inline -> AstComparison.Inline.equal(a,b)
        a is Block && b is Block -> AstComparison.Block.equal(a,b)
        a is List<*> && b is List<*> -> { @Suppress("UNCHECKED_CAST") AstComparison.list(AstComparison.Text).equal(a as List<String>,b as List<String>) }
        else -> a==b
    }
    private fun compareField(a: Any?,b: Any?): Int = when {
        a==null || b==null -> (a!=null).compareTo(b!=null)
        a is String && b is String -> AstComparison.Text.compare(a,b)
        a is Inline && b is Inline -> AstComparison.Inline.compare(a,b)
        a is Block && b is Block -> AstComparison.Block.compare(a,b)
        a is SourcePos && b is SourcePos -> a.compareTo(b)
        a is CharacterString && b is CharacterString -> a.compareTo(b)
        a is BigInteger && b is BigInteger -> a.compareTo(b)
        a is Long && b is Long -> a.compareTo(b)
        a is List<*> && b is List<*> -> { @Suppress("UNCHECKED_CAST") AstComparison.list(AstComparison.Text).compare(a as List<String>,b as List<String>) }
        else -> error("Unsupported LogMessage comparison field")
    }
    fun equal(a: LogMessage,b: LogMessage): Boolean {
        val x=shape(a);val y=shape(b)
        return x.tag==y.tag && x.fields.indices.all { equalField(x.fields[it],y.fields[it]) }
    }
    fun notEqual(a: LogMessage,b: LogMessage)=!equal(a,b)
    fun compare(a: LogMessage,b: LogMessage): Int {
        val x=shape(a);val y=shape(b);val tag=x.tag.compareTo(y.tag)
        if(tag!=0) return tag
        for(i in x.fields.indices) compareField(x.fields[i],y.fields[i]).let { if(it!=0) return it }
        return 0
    }
    // LogMessage's generated Ord supplies compare only; these are GHC.Classes defaults.
    fun less(a: LogMessage,b: LogMessage)=compare(b,a)>0
    fun lessEqual(a: LogMessage,b: LogMessage)=compare(a,b)<=0
    fun greater(a: LogMessage,b: LogMessage)=compare(a,b)>0
    fun greaterEqual(a: LogMessage,b: LogMessage)=compare(b,a)<=0
    fun minimum(a: LogMessage,b: LogMessage)=if(lessEqual(a,b)) a else b
    fun maximum(a: LogMessage,b: LogMessage)=if(lessEqual(a,b)) b else a
}
