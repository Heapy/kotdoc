package io.heapy.kotdoc.math

import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.logging.assertLogJsonEquals
import io.heapy.kotdoc.parsing.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlin.test.*

class MathTeXTest {
    private fun points(v: JsonElement)=v.jsonArray.map {it.jsonPrimitive.int}.toIntArray()
    private fun str(v: JsonElement)=TextChars.fromPoints(points(v))
    private fun wire(v: Any?): JsonElement=when(v) {
        null->JsonNull
        is String->JsonPrimitive(v)
        is Boolean->JsonPrimitive(v)
        is Number->JsonPrimitive(v)
        is Enum<*>->JsonPrimitive(v.name)
        is IntArray->JsonArray(v.map(::JsonPrimitive))
        is Pair<*,*>->JsonArray(listOf(wire(v.first),wire(v.second)))
        is Iterable<*>->JsonArray(v.map(::wire))
        is MathTeX->when(v) {
            is MathTeX.ControlSeq->wire(listOf("ControlSeq",v.text))
            is MathTeX.Literal->wire(listOf("Literal",v.text))
            is MathTeX.Token->wire(listOf("Token",v.character))
            is MathTeX.Grouped->wire(listOf("Grouped",v.items))
            MathTeX.Space->wire(listOf("Space"))
        }
        is MathExp.ESymbol->wire(listOf("ESymbol",v.type,v.text))
        else->error("wire ${v.javaClass}")
    }
    private fun cp(s: String)=wire(s.codePoints().toArray())
    private fun position(p: SourcePos)=wire(listOf(p.nameRaw.points(),p.line.toString(),p.column.toString()))
    private fun error(e: ParseError)=buildJsonObject {
        put("position",position(e.position))
        put("messages",wire(e.messages.sortedBy {it.kind}.map {listOf(it.kind.name,it.text.codePoints().toArray())}))
        // Source names are raw Haskell Strings, including separate surrogate Chars.
        val suffix=e.toString().removePrefix(e.position.toString()).codePoints().toArray()
        put("pretty",wire(e.position.showCharacters().points()+suffix))
    }
    private fun macro(ms: MathMacroDefinitions)=ms.macros.map(MathMacro::toString)
    private fun parse(v: JsonArray): JsonElement {
        val state=ParseState(ParseInput(CharacterListStream(points(v[5]))),SourcePos(CharacterString(points(v[1])),v[2].jsonPrimitive.content.toLong(),v[3].jsonPrimitive.content.toLong()),BigInteger(v[4].jsonPrimitive.content))
        val r=MathMacros.pMacroDefinition<BigInteger>().run(state)
        return buildJsonObject {
            put("consumed",r.consumed);put("ok",r.success);put("error",error(r.error))
            if(r.success) {
                put("macro",checkNotNull(r.value).toString());put("position",position(r.state.position));put("user",r.state.user.toString())
                val rest=mutableListOf<Int>();var input=r.state.input
                while(input.point!=null) {rest.add(checkNotNull(input.point));input=input.tail()}
                put("rest",wire(rest))
            }
        }
    }
    private fun run(v: JsonArray): JsonElement=when(v[0].jsonPrimitive.content) {
        "definitions","document","round"->{
            val m=MathMacros.parseMacroDefinitions(str(v[1]))
            buildJsonObject {
                put("macros",wire(macro(m)));put("rest",cp(m.rest))
                if(v[0].jsonPrimitive.content!="definitions") put("out",cp(MathMacros.applyMacros(m.macros,if(v.size==3) str(v[2]) else m.rest)))
            }
        }
        "apply"->{
            val rs=v[1].jsonArray.map {MathMacros.pMacroDefinition<Unit>().run(ParseState(ParseInput(CharacterListStream(points(it))),SourcePos("definition"),Unit))}
            val invalid=rs.firstOrNull {!it.success}
            if(invalid!=null) buildJsonObject {put("invalid",error(invalid.error))}
            else {val ms=rs.map {checkNotNull(it.value)};buildJsonObject {put("macros",wire(ms.map(MathMacro::toString)));put("out",cp(MathMacros.applyMacros(ms,str(v[2]))))}}
        }
        "parse"->parse(v)
        "records"->JsonArray(MathTeXUnicode.records.map {r->buildJsonObject {put("char",r.character);put("commands",wire(r.commands));put("category",r.category.name);put("comments",r.comments);put("show",MathShow.show(r))}})
        "symbol-map"->wire(MathTeXUnicode.symbolMap.entries.map {it.key to it.value})
        "types"->wire(points(v[1]).map(MathTeXUnicode::getSymbolType))
        "types-range"->{val first=v[1].jsonPrimitive.int;val count=v[2].jsonPrimitive.int;wire((first until first+count).mapNotNull {c->MathTeXUnicode.getSymbolType(c).takeIf {it!=MathSymbolType.Ord}?.let {c to it}})}
        "unicode"->{
            val ts=MathTeXUnicode.getTeXMath(str(v[2]),v[1].jsonArray.map(::str))
            buildJsonObject {put("tokens",wire(ts));put("show",MathShow.show(ts));put("out",cp(MathTeXRenderer.renderTeXList(ts)))}
        }
        "unicode-range"->{
            val first=v[2].jsonPrimitive.int;val count=v[3].jsonPrimitive.int
            val ts=MathTeXUnicode.getTeXMath(TextChars.fromPoints(IntArray(count) {first+it}),v[1].jsonArray.map(::str))
            val out=MathTeXRenderer.renderTeXList(ts);val digest=MessageDigest.getInstance("SHA-256")
            out.codePoints().forEach {c->digest.update(byteArrayOf((c ushr 24).toByte(),(c ushr 16).toByte(),(c ushr 8).toByte(),c.toByte()))}
            buildJsonObject {put("tokens",ts.size);put("length",out.codePointCount(0,out.length));put("sha",digest.digest().joinToString("") {"%02x".format(it)})}
        }
        else->error("request")
    }
    @TestFactory
    fun originalTeXObservations(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/math-tex/data.jsonl.gz"))).bufferedReader().use {r->
        r.readLine();r.lineSequence().mapIndexed {i,line->var transport: String?=line
            DynamicTest.dynamicTest("TeX ${i+1}") {
                val c=Json.parseToJsonElement(checkNotNull(transport)).jsonObject;transport=null
                assertLogJsonEquals(c.getValue("expected"),run(c.getValue("request").jsonArray))
            }
        }.toList()
    }
    @Test
    fun ownedCollectionsAndFiniteDeepMacros() {
        val defs=MathMacros.parseMacroDefinitions("\\newcommand{\\x}[1]{#1}")
        val mutable=defs.macros.toMutableList();val snapshot=MathMacroDefinitions(mutable,"");mutable.clear();assertEquals(1,snapshot.macros.size)
        assertFailsWith<UnsupportedOperationException> {(MathTeXUnicode.records as MutableList).clear()}
        assertFailsWith<UnsupportedOperationException> {(MathTeXUnicode.symbolMap as MutableMap).clear()}
        val nested="{".repeat(1500)+"x"+"}".repeat(1500)
        assertEquals("{$nested}",MathMacros.applyMacros(defs.macros,"\\x{$nested}"))
        assertEquals("\\alpha x",MathTeXRenderer.renderTeXList(listOf(MathTeX.ControlSeq("\\alpha"),MathTeX.Token(120))))
    }
}
