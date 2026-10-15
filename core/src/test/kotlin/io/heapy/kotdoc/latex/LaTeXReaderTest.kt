package io.heapy.kotdoc.latex

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.formats.NativeWriter
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

class LaTeXReaderTest {
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
        when(req[0].jsonPrimitive.content) {
            "tabs"->{ assertEquals(expected,pt(SharedText.tabFilter(req[1].jsonPrimitive.long,raw(req[2]))));return }
            "date"->{ assertEquals(expected,JsonPrimitive(PandocGregorian.zonedDay(PandocUtcTime(req[1].jsonPrimitive.content.toBigInteger(),req[2].jsonPrimitive.content.toBigInteger()),req[3].jsonPrimitive.long)));return }
            "date-range"->{
                val start=req[1].jsonPrimitive.content.toBigInteger()
                expected.jsonArray.forEachIndexed { i,date -> assertEquals(date.jsonPrimitive.content,PandocGregorian.showGregorian(start+BigInteger.valueOf(i.toLong())),"Gregorian offset $i") };return
            }
            "case-data"->{
                val actual=buildList<JsonElement> {
                    for(cp in 0..0x10ffff) {
                        val text=TextChars.fromPoints(intArrayOf(cp))
                        val upper=LaTeXReaderText.toUpper(text);val lower=LaTeXReaderText.toLower(text);val title=LaTeXReaderText.titleCharacter(text.codePointAt(0))
                        if(upper!=text || lower!=text || title!=text)add(JsonArray(listOf(JsonPrimitive(cp),pt(upper),pt(lower),pt(title))))
                    }
                }
                assertEquals(expected,JsonArray(actual));return
            }
            "bridge"->{
                val runtime=Runtime(PandocStateContext(),JsonArray(emptyList()),JsonArray(emptyList()))
                val reader=LaTeXReader(runtime) { "1970-01-01" }
                val sources=decodeSources(req[3])
                val parser: Parsec<LaTeXState,JsonElement> = when(req[1].jsonPrimitive.content) {
                    "inline"->reader.rawLaTeXInline(LaTeXStateAccess,LaTeXStateAccess).map(::JsonPrimitive)
                    "block"->reader.rawLaTeXBlock(LaTeXStateAccess,LaTeXStateAccess).map(::JsonPrimitive)
                    "command"->reader.inlineCommand(LaTeXStateAccess,LaTeXStateAccess).map { JsonArray(it.values.map(PandocJson::encodeInline)) }
                    else->error(req)
                }
                val r=parser.run(ParseState(ParseInput(sources),SourcePos("outer"),LaTeXState(options=ReaderOptions.fromJson(req[2]))))
                val actual=if(!r.success)buildJsonObject { put("ok",false);put("error",r.error.showText()) } else buildJsonObject {
                    put("ok",true);put("value",checkNotNull(r.value));val p=r.state.position
                    put("position",JsonArray(listOf(JsonArray(p.nameRaw.points().map(::JsonPrimitive)),JsonPrimitive(p.line),JsonPrimitive(p.column))))
                    put("rest",JsonArray((r.state.input.stream as Sources).chunks().map { chunk -> JsonArray(listOf(JsonArray(listOf(JsonArray(chunk.position.nameRaw.points().map(::JsonPrimitive)),JsonPrimitive(chunk.position.line),JsonPrimitive(chunk.position.column))),pt(chunk.text))) }))
                    put("macros",JsonArray(r.state.user.macros.head.entries.sortedWith { a,b->UnicodeTextOrder.compare(a.key,b.key) }.map { JsonArray(listOf(JsonPrimitive(it.key),JsonPrimitive(it.value.show()))) }))
                }
                assertEquals(expected,actual,case["group"].toString());return
            }
        }
        val state=PandocStateContext()
        val runtime=Runtime(state,req[3].jsonArray,req[4].jsonArray)
        val reader=LaTeXReader(runtime) { PandocGregorian.today(runtime,0) }
        val sources=decodeSources(req[2])
        val actual=try {
            val doc=reader.read(ReaderOptions.fromJson(req[1]),sources)
            case["nativeGolden"]?.let { golden ->
                assertEquals(golden.jsonPrimitive.content.split('\n'),NativeWriter.write(doc,standalone=case["nativeStandalone"]?.jsonPrimitive?.boolean ?: false).removeSuffix("\n").split('\n'),case["name"]?.jsonPrimitive?.content)
            }
            buildJsonObject {
                put("document",Json.parseToJsonElement(PandocJson.encode(doc)))
                if("common" in expected.jsonObject)put("common",buildJsonObject {
                    put("log",JsonArray(runtime.getLog().map { Json.parseToJsonElement(PrettyJson.encode(Logging.toValue(it))) }))
                    put("resources",JsonArray(runtime.getResourcePath().map { JsonPrimitive(CharacterStringChars.build(it)) }))
                    put("cache",runtime.getCommonState().translations?.let { (l,t)->JsonPrimitive("Just ("+l.show()+","+(if(t==null)"Nothing" else "Just "+t.show(11))+")") } ?: JsonPrimitive("Nothing"))
                })
            }
        } catch(e: PandocError) { buildJsonObject { put("error",CharacterStringChars.build(e.show())) } }
        val label=case["name"]?.toString() ?: case["group"].toString()
        if("document" in expected.jsonObject && "document" in actual) {
            assertEquals(PandocJson.decode(expected.jsonObject.getValue("document")),PandocJson.decode(actual.getValue("document")),label)
            assertEquals(expected.jsonObject-"document",actual-"document",label)
        } else assertEquals(expected,actual,label)
    }
    private fun decodeSources(value: JsonElement)=Sources(value.jsonArray.map { val p=it.jsonArray;val pos=p[0].jsonArray
        SourceChunk(SourcePos(CharacterStringChars.fromString(raw(pos[0])),pos[1].jsonPrimitive.long,pos[2].jsonPrimitive.long),raw(p[1]))
    })
    @TestFactory fun originalReader(): Stream<DynamicTest> {
        val reader=GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/latex-reader/data.jsonl.gz"))).bufferedReader()
        reader.readLine()
        return reader.lineSequence().mapIndexed { i,line ->
            var payload: String?=line
            DynamicTest.dynamicTest("${i+1}: complete original LaTeX reader") { try { runCase(checkNotNull(payload)) } finally { payload=null } }
        }.asStream().onClose(reader::close)
    }
}
