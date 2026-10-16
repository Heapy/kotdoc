package io.heapy.kotdoc.ast

import io.heapy.kotdoc.layout.CharacterString
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.*

class PandocReadTest {
    @Suppress("UNCHECKED_CAST") private fun dictionary(k:String):ReadDictionary<Any> = when(k) {
        "pandoc"->PandocRead.Pandoc;"meta"->PandocRead.Meta;"metaValue"->PandocRead.MetaValue
        "inline"->PandocRead.Inline;"block"->PandocRead.Block;"style"->PandocRead.ListNumberStyle
        "delim"->PandocRead.ListNumberDelim;"format"->PandocRead.Format;"rowHeadColumns"->PandocRead.RowHeadColumns
        "alignment"->PandocRead.Alignment;"colWidth"->PandocRead.ColWidth;"row"->PandocRead.Row
        "head"->PandocRead.TableHead;"body"->PandocRead.TableBody;"foot"->PandocRead.TableFoot
        "caption"->PandocRead.Caption;"cell"->PandocRead.Cell;"rowSpan"->PandocRead.RowSpan;"colSpan"->PandocRead.ColSpan
        "quote"->PandocRead.QuoteType;"math"->PandocRead.MathType;"citation"->PandocRead.Citation;"mode"->PandocRead.CitationMode
        "inlines"->PandocRead.Inlines;"blocks"->PandocRead.Blocks;"attr"->PandocRead.Attr;"target"->PandocRead.Target
        "listAttributes"->PandocRead.ListAttributes;"colSpec"->PandocRead.ColSpec;"shortCaption"->PandocRead.ShortCaption
        "text"->PandocRead.Text;"int"->PandocRead.Int;"double"->PandocRead.Double;"bool"->PandocRead.Bool;"char"->PandocRead.Char
        else->error(k)
    } as ReadDictionary<Any>
    @Suppress("UNCHECKED_CAST") private fun encode(k:String,v:Any):JsonElement=when(k) {
        "pandoc"->PandocJson.toJson(v as Pandoc);"meta"->PandocJson.encodeMeta(v as Map<String,MetaValue>)
        "metaValue"->PandocJson.encodeMetaValue(v as MetaValue);"inline"->PandocJson.encodeInline(v as Inline);"block"->PandocJson.encodeBlock(v as Block)
        "style","delim","alignment","quote","math","mode"->buildJsonObject {put("t",(v as Enum<*>).name)}
        "format"->JsonPrimitive((v as Format).value);"rowHeadColumns","rowSpan","colSpan","int"->JsonPrimitive(v as Long)
        "colWidth"->PandocJson.encodeColWidth(v as ColWidth);"row"->PandocJson.encodeRow(v as Row)
        "head"->PandocJson.encodeTableHead(v as TableHead);"body"->PandocJson.encodeTableBody(v as TableBody);"foot"->PandocJson.encodeTableFoot(v as TableFoot)
        "caption"->PandocJson.encodeCaption(v as Caption);"cell"->PandocJson.encodeCell(v as Cell);"citation"->PandocJson.encodeCitation(v as Citation)
        "inlines"->JsonArray((v as Inlines).values.map(PandocJson::encodeInline));"blocks"->JsonArray((v as Blocks).values.map(PandocJson::encodeBlock))
        "attr"->PandocJson.encodeAttr(v as Attr);"target"->PandocJson.encodeTarget(v as Target);"listAttributes"->PandocJson.encodeListAttributes(v as ListAttributes)
        "colSpec"->PandocJson.encodeColSpec(v as ColSpec);"shortCaption"->JsonArray((v as List<Inline>).map(PandocJson::encodeInline))
        "text"->JsonPrimitive(v as String);"bool"->JsonPrimitive(v as Boolean);"char"->JsonPrimitive(v as Int)
        "double"->PandocJson.encodeColWidth(ColWidth.Width(v as Double)).jsonObject.getValue("c")
        else->error(k)
    }
    private fun shown(k:String,v:Any):String=when(k) {
        "meta"->@Suppress("UNCHECKED_CAST") AstShow.meta(v as Map<String,MetaValue>)
        "rowHeadColumns"->AstShow.rowHeadColumns(v as Long);"rowSpan"->AstShow.rowSpan(v as Long);"colSpan"->AstShow.colSpan(v as Long)
        "inlines"->"Many {unMany = fromList "+AstShow.show((v as Inlines).values)+"}"
        "blocks"->"Many {unMany = fromList "+AstShow.show((v as Blocks).values)+"}"
        else->AstShow.show(v)
    }
    private fun sameJson(expected:JsonElement,actual:JsonElement) {
        when {
            expected is JsonObject && actual is JsonObject -> {assertEquals(expected.keys,actual.keys);for(k in expected.keys)sameJson(expected.getValue(k),actual.getValue(k))}
            expected is JsonArray && actual is JsonArray -> {assertEquals(expected.size,actual.size);for(i in expected.indices)sameJson(expected[i],actual[i])}
            expected is JsonPrimitive && actual is JsonPrimitive && !expected.isString && !actual.isString && expected.content.toBigDecimalOrNull()!=null && actual.content.toBigDecimalOrNull()!=null -> {
                // Aeson's Scientific decimal and the JVM's shortest Double decimal
                // need not be text-identical. Compare the transported IEEE value;
                // independent original Show below still checks its signed-zero form.
                if((expected.content+actual.content).any{it in ".eE"}) assertTrue(expected.double==actual.double,"transported IEEE numeric value")
                else assertEquals(0,expected.content.toBigDecimal().compareTo(actual.content.toBigDecimal()))
            }
            else->assertEquals(expected,actual)
        }
    }
    internal fun verifyRecord(r:JsonObject) {
        val req=r.getValue("request").jsonObject;val k=req.getValue("kind").jsonPrimitive.content
        val op=req.getValue("operation").jsonPrimitive.content;val p=req.getValue("precedence").jsonPrimitive.long
        val input=CharacterString(req.getValue("input").jsonArray.map {it.jsonPrimitive.int}.toIntArray())
        val dict=dictionary(k);val expected=Json.parseToJsonElement(r.getValue("expectedWire").jsonPrimitive.content)
        fun value(e:JsonObject,a:Any,list:Boolean=false) {
            fun bits(v:Any):List<String> = when(k) {
                "double"->listOf((v as Double).toRawBits().toULong().toString())
                "colWidth"->if(v is ColWidth.Width)listOf(v.value.toRawBits().toULong().toString()) else emptyList()
                else->emptyList()
            }
            @Suppress("UNCHECKED_CAST") val actualBits=if(list)(a as List<Any>).flatMap(::bits) else bits(a)
            assertEquals(e.getValue("ieeeBits").jsonArray.map{it.jsonPrimitive.content},actualBits,"original IEEE bits")
            if(req["lean"]?.jsonPrimitive?.boolean!=true) {
                @Suppress("UNCHECKED_CAST") val actual=if(list)JsonArray((a as List<Any>).map{encode(k,it)}) else encode(k,a)
                sameJson(e.getValue("value"),actual)
            }
            // Primitive Char is transported by its exact ordinal (including surrogates).
            if(k!="char") {
                @Suppress("UNCHECKED_CAST") val actual=if(list)(a as List<Any>).joinToString(",","[","]"){shown(k,it)} else shown(k,a)
                assertEquals(e.getValue("shown").jsonPrimitive.content,actual)
            }
        }
        if(op=="read") {
            val exp=expected.jsonObject;val actual=runCatching {dict.read(input)}
            if("exception" in exp) assertEquals(exp.getValue("exception").jsonPrimitive.content,actual.exceptionOrNull()?.message)
            else {assertTrue(actual.isSuccess);value(exp,actual.getOrThrow())}
        } else {
            val actual=when(op){"readsPrec"->dict.readsPrec(p,input);"readPrec"->dict.readPrec(p,input);"readList"->dict.readList(input);"readListPrec"->dict.readListPrec(p,input);else->error(op)}
            val exp=expected.jsonArray;assertEquals(exp.size,actual.size,"parse count")
            for((e,a) in exp.zip(actual)) {
                val ex=e.jsonObject;value(ex,a.first,op in setOf("readList","readListPrec"))
                assertEquals(ex.getValue("remainder").jsonArray.map{it.jsonPrimitive.int},a.second.points().toList(),"untouched remainder")
            }
        }
    }
    @TestFactory fun actualOriginalTypedRead():List<DynamicTest> {
        val stream=javaClass.getResourceAsStream("/pandoc-types/read.jsonl.gz")!!
        return GZIPInputStream(stream).bufferedReader().use{it.readLines()}.mapIndexed { i,line ->
            val r=Json.parseToJsonElement(line).jsonObject;val q=r.getValue("request").jsonObject
            val name="${q["kind"]} ${q["operation"]} ${r["name"]} case=$i"
            DynamicTest.dynamicTest(name) {verifyRecord(Json.parseToJsonElement(line).jsonObject)}
        }
    }
}
