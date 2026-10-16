package io.heapy.kotdoc.ast

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.*

class OriginalTypesOrderingClosureTest {
    @Suppress("UNCHECKED_CAST") private fun dictionary(k:String):AstComparison<Any> = when(k) {
        "pandoc"->AstComparison.Pandoc;"meta"->AstComparison.Meta;"metaValue"->AstComparison.MetaValue
        "inline"->AstComparison.Inline;"block"->AstComparison.Block;"style"->AstComparison.ListNumberStyle
        "delim"->AstComparison.ListNumberDelim;"format"->AstComparison.Format;"rowHeadColumns"->AstComparison.RowHeadColumns
        "alignment"->AstComparison.Alignment;"colWidth"->AstComparison.ColWidth;"row"->AstComparison.Row
        "head"->AstComparison.TableHead;"body"->AstComparison.TableBody;"foot"->AstComparison.TableFoot
        "caption"->AstComparison.Caption;"cell"->AstComparison.Cell;"rowSpan"->AstComparison.RowSpan;"colSpan"->AstComparison.ColSpan
        "quote"->AstComparison.QuoteType;"math"->AstComparison.MathType;"citation"->AstComparison.Citation;"mode"->AstComparison.CitationMode
        "inlines"->AstComparison.Inlines;"blocks"->AstComparison.Blocks;else->error(k)
    } as AstComparison<Any>
    private fun value(k:String,s:String):Any = when(k) {
        "pandoc"->PandocRead.Pandoc.read(s);"meta"->PandocRead.Meta.read(s);"metaValue"->PandocRead.MetaValue.read(s)
        "inline"->PandocRead.Inline.read(s);"block"->PandocRead.Block.read(s);"style"->PandocRead.ListNumberStyle.read(s)
        "delim"->PandocRead.ListNumberDelim.read(s);"format"->PandocRead.Format.read(s);"rowHeadColumns"->PandocRead.RowHeadColumns.read(s)
        "alignment"->PandocRead.Alignment.read(s);"colWidth"->PandocRead.ColWidth.read(s);"row"->PandocRead.Row.read(s)
        "head"->PandocRead.TableHead.read(s);"body"->PandocRead.TableBody.read(s);"foot"->PandocRead.TableFoot.read(s)
        "caption"->PandocRead.Caption.read(s);"cell"->PandocRead.Cell.read(s);"rowSpan"->PandocRead.RowSpan.read(s);"colSpan"->PandocRead.ColSpan.read(s)
        "quote"->PandocRead.QuoteType.read(s);"math"->PandocRead.MathType.read(s);"citation"->PandocRead.Citation.read(s);"mode"->PandocRead.CitationMode.read(s)
        "inlines"->PandocRead.Inlines.read(s);"blocks"->PandocRead.Blocks.read(s);else->error(k)
    }
    private fun shown(k:String,v:Any):String=when(k) {
        "meta"->@Suppress("UNCHECKED_CAST") AstShow.meta(v as Map<String,MetaValue>)
        "rowHeadColumns"->AstShow.rowHeadColumns(v as Long);"rowSpan"->AstShow.rowSpan(v as Long);"colSpan"->AstShow.colSpan(v as Long)
        "inlines"->"Many {unMany = fromList "+AstShow.show((v as Inlines).values)+"}"
        "blocks"->"Many {unMany = fromList "+AstShow.show((v as Blocks).values)+"}"
        else->AstShow.show(v)
    }
    private fun compare(k:String,left:String,right:String,expected:JsonObject) {
        val a=value(k,left);val b=value(k,right);val d=dictionary(k)
        val actual=buildJsonObject {
            put("eq",d.equal(a,b));put("ne",d.notEqual(a,b));put("compare",when(d.compare(a,b)){ -1->"LT";0->"EQ";else->"GT" })
            put("lt",d.less(a,b));put("le",d.lessEqual(a,b));put("gt",d.greater(a,b));put("ge",d.greaterEqual(a,b))
            put("minimum",shown(k,d.minimum(a,b)));put("maximum",shown(k,d.maximum(a,b)))
        }
        assertEquals(expected,actual)
        // Source class/Seq method bodies select caller values using original <=.
        val sourceLe=expected.getValue("le").jsonPrimitive.boolean
        assertSame(if(sourceLe)a else b,d.minimum(a,b),"original minimum caller")
        assertSame(if(sourceLe)b else a,d.maximum(a,b),"original maximum caller")
    }
    private fun partial(k:String,probe:String,method:String):JsonElement {
        val small:Any=if(k=="inlines")Inline.Space else Block.HorizontalRule
        val large:Any=if(k=="inlines")Inline.LineBreak else Block.Plain(emptyList())
        fun elements(vararg labels:String)=object:AbstractList<Any>() {
            override val size=labels.size
            override fun get(index:Int):Any=when(labels[index]) {"small"->small;"large"->large;"left"->error("left element");else->error("right element")}
        }
        val (xs,ys)=when(probe) {
            "empty-bad"->elements() to elements("left");"length-bad"->elements("left") to elements("small","large")
            "both-bad"->elements("left") to elements("right");"later-different"->elements("small","left") to elements("large","right")
            "later-equal"->elements("small","left") to elements("small","right");"left-bad"->elements("left") to elements("small");"right-bad"->elements("small") to elements("right");else->error(probe)
        }
        @Suppress("UNCHECKED_CAST") fun wrap(v:List<Any>):Any=if(k=="inlines")Inlines(v as List<Inline>)else Blocks(v as List<Block>)
        val a=wrap(xs);val b=wrap(ys);val d=dictionary(k)
        fun first(v:Any):String {val list=if(v is Inlines)v.values else (v as Blocks).values;return if(list.isEmpty())"[]" else AstShow.show(list.first())}
        return when(method) {
            "eq"->JsonPrimitive(d.equal(a,b));"ne"->JsonPrimitive(d.notEqual(a,b));"compare"->JsonPrimitive(when(d.compare(a,b)){-1->"LT";0->"EQ";else->"GT"})
            "lt"->JsonPrimitive(d.less(a,b));"le"->JsonPrimitive(d.lessEqual(a,b));"gt"->JsonPrimitive(d.greater(a,b));"ge"->JsonPrimitive(d.greaterEqual(a,b))
            "minimum"->JsonPrimitive(first(d.minimum(a,b)));"maximum"->JsonPrimitive(first(d.maximum(a,b)));else->error(method)
        }
    }
    @TestFactory fun actualOriginalOrderingClosure():List<DynamicTest> =
        GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/pandoc-types/order-closure.jsonl.gz"))).bufferedReader().use{it.readLines()}.mapIndexed { i,line ->
            val row=Json.parseToJsonElement(line).jsonObject;val req=row.getValue("request").jsonObject
            val kind=req.getValue("kind").jsonPrimitive.content;val op=req.getValue("operation").jsonPrimitive.content
            DynamicTest.dynamicTest("$i $kind $op "+row.getValue("name").jsonPrimitive.content) {
                val expected=Json.parseToJsonElement(row.getValue("expectedWire").jsonPrimitive.content)
                if(op=="partial") {
                    val actual=try{partial(kind,req.getValue("probe").jsonPrimitive.content,req.getValue("method").jsonPrimitive.content)}catch(e:IllegalStateException){buildJsonObject{put("exception",e.message.orEmpty())}}
                    assertEquals(expected,actual)
                } else if(op=="fields") {
                    for(v in expected.jsonArray) {val p=v.jsonObject;compare(kind,p.getValue("left").jsonPrimitive.content,p.getValue("right").jsonPrimitive.content,p.getValue("expected").jsonObject)}
                } else {
                    val ex=expected.jsonObject
                    if(op=="seeded")compare(kind,ex.getValue("left").jsonPrimitive.content,ex.getValue("right").jsonPrimitive.content,ex.getValue("expected").jsonObject)
                    else compare(kind,req.getValue("left").jsonPrimitive.content,req.getValue("right").jsonPrimitive.content,ex)
                }
            }
        }
}
