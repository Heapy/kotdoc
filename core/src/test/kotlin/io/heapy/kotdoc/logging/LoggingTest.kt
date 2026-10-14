package io.heapy.kotdoc.logging

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.json.*
import io.heapy.kotdoc.layout.*
import io.heapy.kotdoc.parsing.SourcePos
import io.heapy.kotdoc.formats.*
import io.heapy.kotdoc.resources.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.util.Base64
import java.util.zip.GZIPInputStream
import kotlin.test.*

/** Constructor/transport adapter only; every expected field is original upstream output. */
class LoggingTest {
    private fun JsonElement.s()=jsonPrimitive.content
    private fun JsonElement.l()=jsonPrimitive.long
    private fun chars(v: JsonElement)=CharacterString(v.jsonArray.map { it.jsonPrimitive.int }.toIntArray())
    private fun text(v: JsonElement)=TextChars.fromPoints(chars(v).points())
    private fun long(v: JsonElement)=v.l()
    private fun integer(v: JsonElement)=BigInteger(v.s())
    private fun inline(v: JsonElement)=PandocJson.decodeInline(v)
    private fun block(v: JsonElement)=PandocJson.decodeBlock(v)
    private fun texts(v: JsonElement)=v.jsonArray.map(::text)
    private fun position(v: JsonElement)=v.jsonArray.let { SourcePos(chars(it[0]),it[1].l(),it[2].l()) }
    private fun optional(v: JsonElement)=if(v==JsonNull) null else position(v)
    internal fun message(v: JsonElement): LogMessage {
        val a=v.jsonArray
        return when(a[0].s()) {
            "SkippedContent" -> LogMessage.SkippedContent(text(a[1]), position(a[2]))
            "IgnoredElement" -> LogMessage.IgnoredElement(text(a[1]))
            "DuplicateLinkReference" -> LogMessage.DuplicateLinkReference(text(a[1]), position(a[2]))
            "DuplicateNoteReference" -> LogMessage.DuplicateNoteReference(text(a[1]), position(a[2]))
            "NoteDefinedButNotUsed" -> LogMessage.NoteDefinedButNotUsed(text(a[1]), position(a[2]))
            "DuplicateIdentifier" -> LogMessage.DuplicateIdentifier(text(a[1]), position(a[2]))
            "ReferenceNotFound" -> LogMessage.ReferenceNotFound(text(a[1]), position(a[2]))
            "CircularReference" -> LogMessage.CircularReference(text(a[1]), position(a[2]))
            "UndefinedToggle" -> LogMessage.UndefinedToggle(text(a[1]), position(a[2]))
            "ParsingUnescaped" -> LogMessage.ParsingUnescaped(text(a[1]), position(a[2]))
            "CouldNotLoadIncludeFile" -> LogMessage.CouldNotLoadIncludeFile(text(a[1]), position(a[2]))
            "CouldNotParseIncludeFile" -> LogMessage.CouldNotParseIncludeFile(text(a[1]), position(a[2]))
            "MacroAlreadyDefined" -> LogMessage.MacroAlreadyDefined(text(a[1]), position(a[2]))
            "InlineNotRendered" -> LogMessage.InlineNotRendered(inline(a[1]))
            "BlockNotRendered" -> LogMessage.BlockNotRendered(block(a[1]))
            "DocxParserWarning" -> LogMessage.DocxParserWarning(text(a[1]))
            "PowerpointTemplateWarning" -> LogMessage.PowerpointTemplateWarning(text(a[1]))
            "IgnoredIOError" -> LogMessage.IgnoredIOError(text(a[1]))
            "CouldNotFetchResource" -> LogMessage.CouldNotFetchResource(text(a[1]), text(a[2]))
            "CouldNotDetermineImageSize" -> LogMessage.CouldNotDetermineImageSize(text(a[1]), text(a[2]))
            "CouldNotConvertImage" -> LogMessage.CouldNotConvertImage(text(a[1]), text(a[2]))
            "CouldNotDetermineMimeType" -> LogMessage.CouldNotDetermineMimeType(text(a[1]))
            "CouldNotConvertTeXMath" -> LogMessage.CouldNotConvertTeXMath(text(a[1]), text(a[2]))
            "CouldNotParseCSS" -> LogMessage.CouldNotParseCSS(text(a[1]))
            "Fetching" -> LogMessage.Fetching(text(a[1]))
            "Extracting" -> LogMessage.Extracting(text(a[1]))
            "LoadedResource" -> LogMessage.LoadedResource(chars(a[1]), chars(a[2]))
            "ScriptingInfo" -> LogMessage.ScriptingInfo(text(a[1]), optional(a[2]))
            "ScriptingWarning" -> LogMessage.ScriptingWarning(text(a[1]), optional(a[2]))
            "NoTitleElement" -> LogMessage.NoTitleElement(text(a[1]))
            "NoLangSpecified" -> LogMessage.NoLangSpecified
            "InvalidLang" -> LogMessage.InvalidLang(text(a[1]))
            "CouldNotHighlight" -> LogMessage.CouldNotHighlight(text(a[1]))
            "MissingCharacter" -> LogMessage.MissingCharacter(text(a[1]))
            "Deprecated" -> LogMessage.Deprecated(text(a[1]), text(a[2]))
            "NoTranslation" -> LogMessage.NoTranslation(text(a[1]))
            "CouldNotLoadTranslations" -> LogMessage.CouldNotLoadTranslations(text(a[1]), text(a[2]))
            "UnusualConversion" -> LogMessage.UnusualConversion(text(a[1]))
            "UnexpectedXmlElement" -> LogMessage.UnexpectedXmlElement(text(a[1]), text(a[2]))
            "UnknownOrgExportOption" -> LogMessage.UnknownOrgExportOption(text(a[1]))
            "CouldNotDeduceFormat" -> LogMessage.CouldNotDeduceFormat(texts(a[1]), text(a[2]))
            "RunningFilter" -> LogMessage.RunningFilter(chars(a[1]))
            "FilterCompleted" -> LogMessage.FilterCompleted(chars(a[1]), integer(a[2]))
            "CiteprocWarning" -> LogMessage.CiteprocWarning(text(a[1]))
            "ATXHeadingInLHS" -> LogMessage.ATXHeadingInLHS(long(a[1]), text(a[2]))
            "EnvironmentVariableUndefined" -> LogMessage.EnvironmentVariableUndefined(text(a[1]))
            "DuplicateAttribute" -> LogMessage.DuplicateAttribute(text(a[1]), text(a[2]))
            "NotUTF8Encoded" -> LogMessage.NotUTF8Encoded(chars(a[1]))
            "MakePDFInfo" -> LogMessage.MakePDFInfo(text(a[1]), text(a[2]))
            "MakePDFWarning" -> LogMessage.MakePDFWarning(text(a[1]))
            "UnclosedDiv" -> LogMessage.UnclosedDiv(position(a[1]), position(a[2]))
            "UnsupportedCodePage" -> LogMessage.UnsupportedCodePage(long(a[1]))
            "YamlWarning" -> LogMessage.YamlWarning(position(a[1]), text(a[2]))
            "UnsupportedPdfStandard" -> LogMessage.UnsupportedPdfStandard(text(a[1]))
            else -> error(a[0])
        }
    }
    private fun points(a: IntArray)=JsonArray(a.map(::JsonPrimitive))
    private fun project(v: PrettyValue): JsonElement = when(v) {
        PrettyValue.Null -> JsonArray(listOf(JsonPrimitive("z")))
        is PrettyValue.Bool -> JsonArray(listOf(JsonPrimitive("b"),JsonPrimitive(v.value)))
        is PrettyValue.Text -> JsonArray(listOf(JsonPrimitive("s"),JsonPrimitive(v.value)))
        is PrettyValue.Number -> JsonArray(listOf(JsonPrimitive("n"),JsonPrimitive(v.value.coefficient.toString()),JsonPrimitive(v.value.base10Exponent)))
        is PrettyValue.Array -> JsonArray(listOf(JsonPrimitive("a"),JsonArray(v.values.map(::project))))
        is PrettyValue.Object -> JsonArray(listOf(JsonPrimitive("o"),JsonArray(v.values.entries.sortedWith { a,b -> PrettyJson.compare.compare(a.key,b.key) }.map { JsonArray(listOf(JsonPrimitive(it.key),project(it.value))) })))
    }
    private fun fields(m: LogMessage,p: Long)=buildJsonObject {
        put("verbosity",Logging.messageVerbosity(m).name);put("pretty",Logging.showLogMessage(m))
        put("shown",Logging.shownText(m,p));put("value",project(Logging.toValue(m)))
        put("encodedBase64",Base64.getEncoder().encodeToString(Logging.encodeLogMessages(listOf(m))))
    }
    private fun relations(a: LogMessage,b: LogMessage)=buildJsonObject {
        put("eq",Logging.equal(a,b));put("ne",Logging.notEqual(a,b));put("compare",order(Logging.compare(a,b)))
        put("lt",Logging.less(a,b));put("le",Logging.lessEqual(a,b));put("gt",Logging.greater(a,b));put("ge",Logging.greaterEqual(a,b))
        put("minimum",Logging.shownText(Logging.minimum(a,b)));put("maximum",Logging.shownText(Logging.maximum(a,b)))
        assertSame(if(Logging.lessEqual(a,b)) a else b,Logging.minimum(a,b))
        assertSame(if(Logging.lessEqual(a,b)) b else a,Logging.maximum(a,b))
    }
    private fun order(v: Int)=if(v<0) "LT" else if(v>0) "GT" else "EQ"
    private fun width(kind: String,bits: String): LogMessage {
        val table=Block.Table(Attr(),Caption(null,emptyList()),listOf(ColSpec(Alignment.AlignDefault,ColWidth.Width(Double.fromBits(bits.toULong().toLong())))),TableHead(Attr(),emptyList()),emptyList(),TableFoot(Attr(),emptyList()))
        return if(kind=="block") LogMessage.BlockNotRendered(table) else LogMessage.InlineNotRendered(Inline.Note(listOf(table)))
    }
    private fun pos(p: SourcePos)=buildJsonObject {
        put("name",points(p.nameRaw.points()));put("line",p.line);put("column",p.column);put("shown",points(p.showCharacters().points()))
    }
    private fun run(a: JsonArray): JsonElement = when(a[0].s()) {
        "message" -> fields(message(a[1]),a[2].l())
        "array" -> JsonPrimitive(Base64.getEncoder().encodeToString(Logging.encodeLogMessages(a[1].jsonArray.map(::message))))
        "relations" -> relations(message(a[1]),message(a[2]))
        "rawShow" -> points(Logging.showPrefix(message(a[1]),a[3].jsonPrimitive.int,a[2].l()).points())
        "width" -> {
            val x=width(a[1].s(),a[2].s());val y=width(a[1].s(),a[3].s())
            buildJsonObject { put("left",fields(x,0));put("right",fields(y,0));put("relations",relations(x,y)) }
        }
        "ast" -> project(AstJsonValues.toValue(when(a[1].s()) {
            "pandoc" -> PandocJson.decode(a[2]);"inline" -> inline(a[2]);"block" -> block(a[2]);"meta" -> PandocJson.decodeMeta(a[2]);else -> error(a[1])
        }))
        "position" -> {
            val p=position(a[1]);val q=position(a[2]);var u=p
            chars(a[3]).points().forEach { u=u.advance(it) }
            buildJsonObject {
                put("original",pos(p));put("updated",pos(u));put("incremented",pos(p.copy(line=p.line+a[4].l(),column=p.column+a[5].l())))
                put("renamed",pos(p.copyRaw(name=chars(a[6]))));put("eq",p==q);put("compare",order(p.compareTo(q)))
            }
        }
        "verbosityJSON" -> try { JsonArray(listOf(JsonPrimitive("success"),JsonPrimitive(Verbosity.fromJson(a[1]).name))) }
            catch(e: IllegalArgumentException) { JsonArray(listOf(JsonPrimitive("error"),JsonPrimitive(e.message!!))) }
        "verbosityRead" -> JsonArray(Verbosity.reads(chars(a[2]),a[1].l()).map { (v,rest) -> JsonArray(listOf(JsonPrimitive(v.name),points(rest.points()))) })
        "enum" -> try {
            val op=a[1].s();val limit=a[5].jsonPrimitive.int
            val values=when(op) {
                "bounds" -> sequenceOf(Verbosity.minimum,Verbosity.maximum)
                "toEnum" -> sequenceOf(Verbosity.fromOrdinal(a[2].l()))
                "succ" -> sequenceOf(Verbosity.fromOrdinal(a[2].l()).successor())
                "pred" -> sequenceOf(Verbosity.fromOrdinal(a[2].l()).predecessor())
                "from" -> Verbosity.fromOrdinal(a[2].l()).enumerate()
                "fromTo" -> Verbosity.fromOrdinal(a[2].l()).enumerate(to=Verbosity.fromOrdinal(a[4].l()))
                "fromThen" -> Verbosity.fromOrdinal(a[2].l()).enumerate(then=Verbosity.fromOrdinal(a[3].l()))
                "fromThenTo" -> Verbosity.fromOrdinal(a[2].l()).enumerate(then=Verbosity.fromOrdinal(a[3].l()),to=Verbosity.fromOrdinal(a[4].l()))
                else -> error(op)
            }
            buildJsonObject { put("values",JsonArray(values.take(limit).map { JsonPrimitive(it.name) }.toList())) }
        } catch(e: IllegalArgumentException) { buildJsonObject { put("error",e.message!!) } }
        "deep" -> {
            val n=a[2].jsonPrimitive.int;val m=if(a[1].s()=="inline") {
                var v: Inline=Inline.Space;repeat(n) { v=Inline.Emph(listOf(v)) };LogMessage.InlineNotRendered(v)
            } else { var v: Block=Block.HorizontalRule;repeat(n) { v=Block.BlockQuote(listOf(v)) };LogMessage.BlockNotRendered(v) }
            buildJsonObject {
                put("prefix",points(Logging.showPrefix(m,a[3].jsonPrimitive.int).points()))
                put("compact",PrettyJson.encode(Logging.toValue(m),PrettyJsonConfig(indent=JsonIndent.Spaces(0))))
            }
        }
        else -> error(a[0])
    }
    @TestFactory fun originalLogging(): List<DynamicTest> = GZIPInputStream(javaClass.getResourceAsStream("/logging/data.jsonl.gz")!!).bufferedReader().use { reader ->
        val lines=reader.lineSequence().iterator();val metadata=Json.parseToJsonElement(lines.next()).jsonObject
        val tests=lines.asSequence().mapIndexed { i,line ->
            val operation=line.substringAfter("\"request\":[\"").substringBefore('"')
            DynamicTest.dynamicTest("$i $operation") {
                val c=Json.parseToJsonElement(line).jsonObject
                assertEquals(c.getValue("expected"),run(c.getValue("request").jsonArray))
            }
        }.toList()
        assertEquals(metadata.getValue("cases").jsonPrimitive.int,tests.size);tests
    }

    @Test fun readerWriterAndResourceDiagnosticsUseTheSharedModel() {
        val input="<FictionBook><body><section><unknown/><p><other/></p></section></body></FictionBook>"
        val result=FB2Reader.readWithEffects(input)
        assertEquals(result.messages.map { it.toLogMessage() },result.logMessages())
        val tree=Json.parseToJsonElement(Logging.encodeLogMessagesText(result.logMessages())).jsonArray
        assertEquals(listOf("UnexpectedXmlElement","IgnoredElement"),tree.map { it.jsonObject.getValue("type").s() })
        assertEquals(listOf("WARNING","INFO"),tree.map { it.jsonObject.getValue("verbosity").s() })
        val writer=WriterResult("",listOf(WriterMessage.InlineNotRendered(Inline.RawInline("html","x")),WriterMessage.BlockNotRendered(Block.Header(1,Attr(),emptyList())),
            WriterMessage.Resource(ResourceEvent.Loaded("a","b")),WriterMessage.Resource(ResourceEvent.Fetching("u")),WriterMessage.Resource(ResourceEvent.Extracting("p")),
            WriterMessage.Resource(ResourceEvent.CouldNotFetch("p","m")),WriterMessage.Resource(ResourceEvent.IgnoredIOError("m")),WriterMessage.CouldNotDetermineMimeType("p")))
        assertEquals(listOf("InlineNotRendered","BlockNotRendered","LoadedResource","Fetching","Extracting","CouldNotFetchResource","IgnoredIOError","CouldNotDetermineMimeType"),writer.logMessages().map { shape(it).name })
    }
}
