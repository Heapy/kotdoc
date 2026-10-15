package io.heapy.kotdoc.html

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.formats.NativeWriter
import io.heapy.kotdoc.writers.HtmlWriter
import io.heapy.kotdoc.options.WriterOptions
import io.heapy.kotdoc.options.WrapOption
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.logging.Logging
import io.heapy.kotdoc.json.PrettyJson
import io.heapy.kotdoc.options.ReaderOptions
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.resources.*
import io.heapy.kotdoc.state.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.streams.asStream

class HtmlReaderTest {
    private fun lines(s: String)=if(s.isEmpty())emptyList() else s.split('\n').let { if(s.endsWith('\n'))it.dropLast(1) else it }
    private fun raw(v: JsonElement)=TextChars.fromPoints(v.jsonArray.map { it.jsonPrimitive.int }.toIntArray())
    private inner class Runtime(val state: PandocStateContext,files: JsonArray,environment: JsonArray): PandocDataRuntime,PandocState by state {
        private val fs=files.associate { row -> val pair=row.jsonArray
            PandocFilePaths.canonical(CharacterStringChars.fromString(raw(pair[0])),filePaths) to pair[1].jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray()
        }
        private val env=environment.associate { val p=it.jsonArray;raw(p[0]) to raw(p[1]) }
        override fun fileExists(path: CharacterString)=PandocFilePaths.canonical(path,filePaths) in fs
        override fun readFileStrict(path: CharacterString)=fs[PandocFilePaths.canonical(path,filePaths)]?.copyOf() ?: throw PandocError.PandocResourceNotFound(TextChars.fromPoints(path.points()))
        override fun readFileLazy(path: CharacterString)=readFileStrict(path)
        override fun getDataFileName(path: CharacterString)=path
        override fun lookupEnv(name: String)=env[name]
        override fun getCurrentTime()=PandocUtcTime.fromPosixPicoseconds(BigInteger.ZERO)
    }
    private fun runCase(line: String) {
        val case=Json.parseToJsonElement(line).jsonObject
        val req=case.getValue("request").jsonArray
        val expected=case.getValue("expected")
        fun pt(s: String)=JsonArray(s.codePoints().toArray().map(::JsonPrimitive))
        if(req[0].jsonPrimitive.content=="render") {
            val tags=req[1].jsonArray.map(::tag)
            val actual=buildJsonObject {
                put("canonical",JsonArray(HtmlRendering.canonicalize(tags).map(::tagJson)))
                put("default",pt(HtmlRendering.render(tags)))
                put("pandoc",pt(HtmlRendering.renderPandoc(tags)))
                put("custom",pt(HtmlRendering.render(tags,HtmlRendering.Options(escape={"[$it]"},minimize={true},rawTag={it=="raw"}))))
            }
            assertEquals(expected,actual);return
        }
        if(req[0].jsonPrimitive.content in listOf("source","smart")) {
            val sources=decodeSources(req[3]);val options=ReaderOptions.fromJson(req[2])
            val initial=ParserState(options=options,quoteContext=if(req[0].jsonPrimitive.content=="source")QuoteContext.NoQuote else when(req[4].jsonPrimitive.int) {1->QuoteContext.InSingleQuote;2->QuoteContext.InDoubleQuote;else->QuoteContext.NoQuote},lastStrPos=if(req[0].jsonPrimitive.content=="smart" && req[5].jsonPrimitive.boolean)SourcePos("outer") else null)
            val parser: Parsec<ParserState,JsonElement>
            if(req[0].jsonPrimitive.content=="source") {
                val predicate: (HtmlTag)->Boolean = { t -> when(req[4].jsonPrimitive.content) {
                    "any"->true;"block"->HtmlReader.isBlockTag(t);"inline"->HtmlReader.isInlineTag(t);"comment"->HtmlReader.isCommentTag(t);"text"->HtmlReader.isTextTag(t);"div"->t is HtmlTag.Open && t.name=="div";else->false
                } }
                parser=if(req[1].jsonPrimitive.content=="tag")HtmlReader.htmlTag(predicate,ParserStateAccess).map { (t,text)->JsonArray(listOf(pt(HtmlTagMatching.show(t)),pt(text))) } else HtmlReader.htmlInBalanced<ParserState>(predicate).map(::pt)
            } else {
                val smart=SmartParsing<Unit,ParserState>(ParserStateAccess,ParserStateAccess,ReaderParsec.lift(ParserStateAccess.getQuoteContext())) { context,p -> ReaderParsec { env -> ParserStateAccess.withQuoteContext(context,p.runReader(env)) } }
                fun atom(): ReaderParsec<Unit,ParserState,Inlines> = ReaderParsec.defer { smart.smartPunctuation(atom()) or ReaderParsec.lift(SourceParsers.anyChar<ParserState>().flatMap { c ->
                    (if(ParserCharacters.isAlphaNum(c))ParserCapabilities.updateLastStrPos(ParserStateAccess) else Parsec.pure(Unit)).map { Builder.str(TextChars.fromPoints(intArrayOf(c))) }
                }) }
                fun unit(p: ReaderParsec<Unit,ParserState,Unit>): ReaderParsec<Unit,ParserState,JsonElement> = p.map { JsonPrimitive("()") }
                val p: ReaderParsec<Unit,ParserState,JsonElement> = when(req[1].jsonPrimitive.content) {
                    "single-start"->unit(smart.singleQuoteStart());"single-end"->unit(smart.singleQuoteEnd());"double-start"->unit(smart.doubleQuoteStart());"double-end"->unit(smart.doubleQuoteEnd())
                    else->{ val q=when(req[1].jsonPrimitive.content) { "single-quoted"->smart.singleQuoted(atom());"double-quoted"->smart.doubleQuoted(atom());"apostrophe"->smart.apostrophe();"close"->smart.doubleCloseQuote();"ellipsis"->smart.ellipses();"dash"->smart.dash();"smart"->smart.smartPunctuation(atom());else->error(req) };q.map { JsonArray(it.values.map(PandocJson::encodeInline)) } }
                }
                parser=p.runReader(Deferred.now(Unit))
            }
            val reply=parser.run(ParseState(ParseInput(sources),SourcePos("outer"),initial))
            val actual=if(!reply.success)buildJsonObject { put("ok",false);put("error",reply.error.showText()) } else buildJsonObject {
                put("ok",true);put("value",checkNotNull(reply.value));put("position",positionJson(reply.state.position));put("rest",sourcesJson(reply.state.input.stream as Sources));put("quote",reply.state.user.quoteContext.toString());put("last",reply.state.user.lastStrPos?.let(::positionJson) ?: JsonNull)
            }
            assertEquals(expected,actual,case["group"].toString());return
        }
        if(req[0].jsonPrimitive.content=="utf8-all-scalars") {
            val hash=java.security.MessageDigest.getInstance("SHA-256")
            fun word(n: Int) { hash.update(byteArrayOf(n.toByte(),(n ushr 8).toByte(),(n ushr 16).toByte(),(n ushr 24).toByte())) }
            for(cp in 0..0x10ffff) {
                val output=PandocUtf8.toText(TextChars.fromPoints(intArrayOf(cp)).toByteArray(Charsets.UTF_8)).codePoints().toArray()
                word(output.size);output.forEach(::word)
            }
            assertEquals(expected.jsonPrimitive.content,hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) });return
        }
        fun utf8(data: ByteArray): JsonElement = try { buildJsonObject { put("text",pt(PandocUtf8.toText(data))) } } catch(e: UnicodeDecodeException) { buildJsonObject { put("unicodeException",e.message) } }
        if(req[0].jsonPrimitive.content=="utf8-row") {
            val lead=req[1].jsonPrimitive.int.toByte();assertEquals(expected,JsonArray((0..255).map { utf8(byteArrayOf(lead,it.toByte())) }));return
        }
        if(req[0].jsonPrimitive.content=="utf8") { assertEquals(expected,utf8(req[1].jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray()));return }
        if(req[0].jsonPrimitive.content=="roundtrip") {
            val original=PandocJson.decode(req[1]);val input=Pandoc(blocks=original.blocks)
            val blockVisitor=object : PandocVisitor() {
                override fun transformBlock(value: Block): Block = when(value) {
                    is Block.CodeBlock->Block.Para(listOf(Inline.Str("code block was here")))
                    is Block.LineBlock->Block.Para(listOf(Inline.Str("line block was here")))
                    is Block.RawBlock->Block.Para(listOf(Inline.Str("raw block was here")))
                    is Block.Table->Block.Para(listOf(Inline.Str("table block was here")))
                    is Block.Div->value.copy(blocks=value.blocks.filter { it !is Block.Header })
                    else->value
                }
            }
            var document=object : PandocVisitor() {
                override fun transformInline(value: Inline): Inline = if(value is Inline.RawInline)Inline.Str("raw inline was here") else value
            }.walk(blockVisitor.walk(input))
            assertEquals(PandocJson.decode(expected.jsonObject.getValue("normalized")),document)
            val docs=ArrayList<Pandoc>()
            for(step in expected.jsonObject.getValue("rewrites").jsonArray) {
                val state=PandocStateContext();val runtime=Runtime(state,JsonArray(emptyList()),JsonArray(emptyList()))
                val html=HtmlWriter.writeHtml5String(WriterOptions(wrapText=WrapOption.WrapPreserve),document,runtime)
                assertEquals(step.jsonArray[0].jsonPrimitive.content,html,"original HTML rewrite")
                document=HtmlReader(runtime,{ throw PandocError.PandocResourceNotFound(it) }) { "1970-01-01" }.read(ReaderOptions(),html+"\n")
                assertEquals(PandocJson.decode(step.jsonArray[1]),document,"original rewrite AST")
                docs.add(document)
            }
            assertEquals(expected.jsonObject.getValue("verdict").jsonPrimitive.boolean,docs[1]==docs[2],"original Round trip property")
            return
        }
        val state=PandocStateContext()
        val urlMode=req[0].jsonPrimitive.content=="read-urls"
        val runtime=Runtime(state,if(urlMode)JsonArray(emptyList()) else req[3].jsonArray,if(urlMode)JsonArray(emptyList()) else req[4].jsonArray)
        val calls=ArrayList<String>()
        val urls=if(urlMode)req[3].jsonArray.associate { row -> val p=row.jsonArray
            raw(p[0]) to FetchedResource(p[1].jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray(),p[2].jsonPrimitive.contentOrNull)
        } else emptyMap()
        // Fixture's StateT loses the attempted append when openURL throws.
        val reader=HtmlReader(runtime,{ url -> val result=urls[url] ?: throw PandocError.PandocResourceNotFound(url);calls.add(url);result }) { "1970-01-01" }
        val sources=decodeSources(req[2])
        val actual=try {
            val doc=reader.read(ReaderOptions.fromJson(req[1]),sources)
            case["nativeGolden"]?.let { golden ->
                assertEquals(lines(golden.jsonPrimitive.content),lines(NativeWriter.write(doc,standalone=case["nativeStandalone"]?.jsonPrimitive?.boolean ?: false)),case["name"]?.jsonPrimitive?.content)
            }
            buildJsonObject {
                put("document",Json.parseToJsonElement(PandocJson.encode(doc)))
                if(urlMode)put("calls",JsonArray(calls.map(::JsonPrimitive)))
                if("common" in expected.jsonObject)put("common",buildJsonObject {
                    put("log",JsonArray(runtime.getLog().map { Json.parseToJsonElement(PrettyJson.encode(Logging.toValue(it))) }))
                    put("resources",JsonArray(runtime.getResourcePath().map { JsonPrimitive(CharacterStringChars.build(it)) }))
                    put("cache",runtime.getCommonState().translations?.let { (l,t)->JsonPrimitive("Just ("+l.show()+","+(if(t==null)"Nothing" else "Just "+t.show(11))+")") } ?: JsonPrimitive("Nothing"))
                })
            }
        } catch(e: PandocError) { buildJsonObject { put("error",CharacterStringChars.build(e.show())) } }
          catch(e: UnicodeDecodeException) { buildJsonObject { put("unicodeException",e.message) } }
        val label=case["name"]?.toString() ?: case["group"].toString()
        if("document" in expected.jsonObject && "document" in actual) {
            assertEquals(PandocJson.decode(expected.jsonObject.getValue("document")),PandocJson.decode(actual.getValue("document")),label)
            assertEquals(expected.jsonObject-"document",actual-"document",label)
        } else assertEquals(expected,actual,label)
    }
    private fun decodeSources(value: JsonElement)=Sources(value.jsonArray.map { val p=it.jsonArray;val pos=p[0].jsonArray
        SourceChunk(SourcePos(CharacterStringChars.fromString(raw(pos[0])),pos[1].jsonPrimitive.long,pos[2].jsonPrimitive.long),raw(p[1]))
    })
    private fun positionJson(p: SourcePos)=JsonArray(listOf(JsonArray(p.nameRaw.points().map(::JsonPrimitive)),JsonPrimitive(p.line),JsonPrimitive(p.column)))
    private fun sourcesJson(s: Sources)=JsonArray(s.chunks().map { JsonArray(listOf(positionJson(it.position),JsonArray(it.text.codePoints().toArray().map(::JsonPrimitive)))) })
    private fun tag(v: JsonElement): HtmlTag {
        val a=v.jsonArray
        return when(a[0].jsonPrimitive.int) {
            0->HtmlTag.Open(raw(a[1]),a[2].jsonArray.map { val p=it.jsonArray;raw(p[0]) to raw(p[1]) })
            1->HtmlTag.Close(raw(a[1]));2->HtmlTag.Text(raw(a[1]));3->HtmlTag.Comment(raw(a[1]));4->HtmlTag.Warning(raw(a[1]));5->HtmlTag.Position(a[1].jsonPrimitive.long,a[2].jsonPrimitive.long);else->error(a)
        }
    }
    private fun tagJson(t: HtmlTag): JsonElement {
        fun pt(s: String)=JsonArray(s.codePoints().toArray().map(::JsonPrimitive))
        return JsonArray(when(t) {
            is HtmlTag.Open->listOf(JsonPrimitive(0),pt(t.name),JsonArray(t.attributes.map { (k,v)->JsonArray(listOf(pt(k),pt(v))) }))
            is HtmlTag.Close->listOf(JsonPrimitive(1),pt(t.name));is HtmlTag.Text->listOf(JsonPrimitive(2),pt(t.text));is HtmlTag.Comment->listOf(JsonPrimitive(3),pt(t.text));is HtmlTag.Warning->listOf(JsonPrimitive(4),pt(t.message));is HtmlTag.Position->listOf(JsonPrimitive(5),JsonPrimitive(t.row),JsonPrimitive(t.column))
        })
    }
    @TestFactory fun originalReader(): Stream<DynamicTest> {
        val reader=GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/html-reader/data.jsonl.gz"))).bufferedReader()
        reader.readLine()
        return reader.lineSequence().mapIndexed { i,line ->
            var payload: String?=line
            DynamicTest.dynamicTest("${i+1}: complete original HTML reader") { try { runCase(checkNotNull(payload)) } finally { payload=null } }
        }.asStream().onClose(reader::close)
    }
}
