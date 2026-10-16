// Exact predicates from pandoc-types/test/test-pandoc-types.hs.
// Copyright (c) 2006-2023 John MacFarlane. BSD-3-Clause; licenses/.
package io.heapy.kotdoc.ast

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.TestInstance
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OriginalTypesRemainingPropertiesTest {
    private data class Wire(val kind:String,val seed:String,val width:Int,val size:String,val witness:String,val line:String)
    // Keep wire strings rather than thousands of complete duplicate AST JSON trees.
    // Each assertion independently decodes its original record when it executes.
    private val observations by lazy {
        val stream=javaClass.getResourceAsStream("/pandoc-types/remaining-properties.jsonl.gz")!!
        GZIPInputStream(stream).bufferedReader().use { it.readLines() }.map { line ->
            val r=Json.parseToJsonElement(line).jsonObject
            Wire(r.getValue("kind").jsonPrimitive.content,r.getValue("seed").toString(),
                r["width"]?.jsonPrimitive?.int ?: 0,r["size"].toString(),r["witness"].toString(),line)
        }
    }
    private fun rows(resource:String):List<JsonObject> {
        val stream=javaClass.getResourceAsStream("/pandoc-types/$resource.jsonl.gz")!!
        return GZIPInputStream(stream).bufferedReader().use { it.readLines() }.map { Json.parseToJsonElement(it).jsonObject }
    }
    private fun cellSubset(x:Cell,y:Cell):Boolean {
        fun dim(a:Long,b:Long) = (b<1 && a==1L) || (a>=1 && a<=b)
        return x.attr==y.attr && x.alignment==y.alignment && dim(x.rowSpan,y.rowSpan) && dim(x.colSpan,y.colSpan) && x.blocks==y.blocks
    }
    private fun cellsSubsetPad(xs:List<Cell>,ys:List<Cell>):Boolean {
        var i=0
        while(i<xs.size && i<ys.size) { if(!cellSubset(xs[i],ys[i])) return false; i++ }
        return xs.drop(i).all { it==Tables.emptyCell }
    }
    private fun rowsSubset(xs:List<Row>,ys:List<Row>) = xs.indices.all { i ->
        val y=ys.getOrNull(i)
        y!=null && xs[i].attr==y.attr && cellsSubsetPad(xs[i].cells,y.cells)
    }
    private fun firstWidth(n:Int,xs:List<Row>,ys:List<Row>):Boolean = when {
        xs.isNotEmpty() && ys.isNotEmpty() -> n.toLong()==xs.first().cells.sumOf { it.colSpan }
        xs.isEmpty() && ys.isEmpty() -> true
        else -> false
    }
    private fun gather(n:Long,cells:List<Cell>):Pair<List<Cell>,List<Cell>> {
        var count=0L; var i=0
        while(i<cells.size && count<n) count+=cells[i++].colSpan
        return cells.take(i) to cells.drop(i)
    }
    private fun bodySubset(n:Int,x:TableBody,y:TableBody):Boolean {
        fun firstRows():Boolean {
            if(x.body.isEmpty() || y.body.isEmpty()) return x.body.isEmpty() && y.body.isEmpty()
            val (newHead,newBody)=gather(x.rowHeadColumns,x.body.first().cells)
            val (oldHead,oldBody)=gather(x.rowHeadColumns,y.body.first().cells)
            return newHead.sumOf { it.colSpan }==x.rowHeadColumns && x.rowHeadColumns+newBody.sumOf { it.colSpan }==n.toLong() &&
                cellsSubsetPad(newHead,oldHead) && cellsSubsetPad(newBody,oldBody)
        }
        return rowsSubset(x.head,y.head) && firstRows()
    }
    @TestFactory fun originalTablePredicates():List<DynamicTest> = observations.filter { it.kind in setOf("head","body","foot") }.flatMap { wire ->
        val kind=wire.kind
        val width=wire.width
        val family=kind.replaceFirstChar { it.uppercaseChar() }
        listOf("Idempotent" to "idempotent","IsSubset" to "subset","RowWidth" to "rowWidth").map { (property,field) ->
            DynamicTest.dynamicTest("p_tableNorm$family$property width=$width seed=${wire.seed}") {
                val r=Json.parseToJsonElement(wire.line).jsonObject
                val native=r.getValue(field).jsonPrimitive.boolean
                assertTrue(native,"original predicate failed on original generated input")
                val predicate=when(kind) {
                    "head" -> {
                        val input=PandocJson.decodeTableHead(r.getValue("input")); val actual=Tables.normalizeHead(width,input)
                        assertEquals(PandocJson.decodeTableHead(r.getValue("expected")),actual)
                        when(field) { "idempotent" -> Tables.normalizeHead(width,actual)==actual; "subset" -> rowsSubset(actual.rows,input.rows); else -> firstWidth(width,actual.rows,input.rows) }
                    }
                    "foot" -> {
                        val input=PandocJson.decodeTableFoot(r.getValue("input")); val actual=Tables.normalizeFoot(width,input)
                        assertEquals(PandocJson.decodeTableFoot(r.getValue("expected")),actual)
                        when(field) { "idempotent" -> Tables.normalizeFoot(width,actual)==actual; "subset" -> rowsSubset(actual.rows,input.rows); else -> firstWidth(width,actual.rows,input.rows) }
                    }
                    else -> {
                        val input=PandocJson.decodeTableBody(r.getValue("input")); val actual=Tables.normalizeBody(width,input)
                        assertEquals(PandocJson.decodeTableBody(r.getValue("expected")),actual)
                        when(field) { "idempotent" -> Tables.normalizeBody(width,actual)==actual; "subset" -> bodySubset(width,actual,input); else -> firstWidth(width,actual.head,input.head) && firstWidth(width,actual.body,input.body) }
                    }
                }
                assertEquals(native,predicate)
            }
        }
    }
    @TestFactory fun originalFigureRepresentation():List<DynamicTest> = observations.filter { it.kind=="figure" }.map { wire ->
        DynamicTest.dynamicTest("p_figureRepresentation figure representation seed=${wire.seed} size=${wire.size} witness=${wire.witness}") {
            val r=Json.parseToJsonElement(wire.line).jsonObject
            val caption=r.getValue("input").jsonArray.map(PandocJson::decodeInline)
            val actual=Builder.simpleFigureWith(Attr("test"),Inlines(caption),"url","title").values
            assertEquals(r.getValue("expected").jsonArray.map(PandocJson::decodeBlock),actual)
            val right=listOf(Block.Figure(Attr(),Caption(null,if(caption.isEmpty()) emptyList() else listOf(Block.Plain(caption))),
                listOf(Block.Plain(listOf(Inline.Image(Attr("test"),emptyList(),Target("url","title")))))))
            val native=r.getValue("predicate").jsonPrimitive.boolean
            assertTrue(native); assertEquals(native,actual==right)
        }
    }
    @TestFactory fun originalJsonRoundTrip():List<DynamicTest> = observations.filter { it.kind=="json" }.map { wire ->
        DynamicTest.dynamicTest("round-trip seed=${wire.seed} size=${wire.size}") {
            val r=Json.parseToJsonElement(wire.line).jsonObject
            val input=PandocJson.decode(r.getValue("input")); val wire=PandocJson.encode(input)
            assertEquals(r.getValue("encoded").jsonPrimitive.content,wire)
            val decoded=PandocJson.decode(wire)
            assertEquals(PandocJson.decode(r.getValue("decoded")),decoded)
            assertTrue(r.getValue("predicate").jsonPrimitive.boolean)
            assertEquals(input,decoded)
        }
    }
    @TestFactory fun originalHaskellPropertyRuns():List<DynamicTest> = rows("remaining-property-suite").map { r ->
        DynamicTest.dynamicTest("original Haskell ${r["name"]}") {
            assertTrue(r.getValue("success").jsonPrimitive.boolean)
            assertEquals(1000,r.getValue("tests").jsonPrimitive.int)
        }
    }
}
