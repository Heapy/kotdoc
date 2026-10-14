// Tests.Writers.AnnotatedTable, Copyright 2020 Christian Despres.
// GPL-2.0-or-later; licenses/pandoc-GPL-2.0.md.
package io.heapy.kotdoc.writers

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.writers.AnnotatedTable as A
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.random.Random
import kotlin.test.*

class AnnotatedTableTest {
    private fun j(vararg xs: JsonElement) = JsonArray(xs.toList())
    private fun <T> list(xs: List<T>, f: (T) -> JsonElement) = JsonArray(xs.map(f))
    private fun cell(c: A.Cell): JsonElement = j(list(c.columns.toList(),PandocJson::encodeColSpec),JsonPrimitive(c.columnNumber),PandocJson.encodeCell(c.cell))
    private fun header(r: A.HeaderRow): JsonElement = j(PandocJson.encodeAttr(r.attr),JsonPrimitive(r.rowNumber),list(r.cells,::cell))
    private fun bodyRow(r: A.BodyRow): JsonElement = j(PandocJson.encodeAttr(r.attr),JsonPrimitive(r.rowNumber),list(r.head,::cell),list(r.body,::cell))
    private fun head(h: A.TableHead): JsonElement = j(PandocJson.encodeAttr(h.attr),list(h.rows,::header))
    private fun body(b: A.TableBody): JsonElement = j(PandocJson.encodeAttr(b.attr),JsonPrimitive(b.rowHeadColumns),list(b.head,::header),list(b.body,::bodyRow))
    private fun foot(f: A.TableFoot): JsonElement = j(PandocJson.encodeAttr(f.attr),list(f.rows,::header))
    private fun table(t: A.Table): JsonElement = j(PandocJson.encodeAttr(t.attr),PandocJson.encodeCaption(t.caption),list(t.columns,PandocJson::encodeColSpec),head(t.head),list(t.bodies,::body),foot(t.foot))
    private fun uncell(v: JsonElement): A.Cell = v.jsonArray.let { a ->
        val cs=a[0].jsonArray.map(PandocJson::decodeColSpec)
        A.Cell(A.ColumnSpecs(cs.first(),cs.drop(1)),a[1].jsonPrimitive.long,PandocJson.decodeCell(a[2]))
    }
    private fun unheader(v: JsonElement): A.HeaderRow = v.jsonArray.let { A.HeaderRow(PandocJson.decodeAttr(it[0]),it[1].jsonPrimitive.long,it[2].jsonArray.map(::uncell)) }
    private fun unbodyrow(v: JsonElement): A.BodyRow = v.jsonArray.let { A.BodyRow(PandocJson.decodeAttr(it[0]),it[1].jsonPrimitive.long,it[2].jsonArray.map(::uncell),it[3].jsonArray.map(::uncell)) }
    private fun unhead(v: JsonElement): A.TableHead = v.jsonArray.let { A.TableHead(PandocJson.decodeAttr(it[0]),it[1].jsonArray.map(::unheader)) }
    private fun unbody(v: JsonElement): A.TableBody = v.jsonArray.let { A.TableBody(PandocJson.decodeAttr(it[0]),it[1].jsonPrimitive.long,it[2].jsonArray.map(::unheader),it[3].jsonArray.map(::unbodyrow)) }
    private fun unfoot(v: JsonElement): A.TableFoot = v.jsonArray.let { A.TableFoot(PandocJson.decodeAttr(it[0]),it[1].jsonArray.map(::unheader)) }
    private fun untable(v: JsonElement): A.Table = v.jsonArray.let { A.Table(PandocJson.decodeAttr(it[0]),PandocJson.decodeCaption(it[1]),it[2].jsonArray.map(PandocJson::decodeColSpec),unhead(it[3]),it[4].jsonArray.map(::unbody),unfoot(it[5])) }

    private fun allRows(t: A.Table) = t.head.rows + t.bodies.flatMap { b -> b.head + b.body.map { A.HeaderRow(it.attr,it.rowNumber,it.head+it.body) } } + t.foot.rows
    private fun <T> walks(value: T,encode: (T) -> JsonElement,walk: (T,PandocVisitor) -> T,query: (T,(Cell)->List<JsonElement>)->List<JsonElement>): JsonElement = buildJsonObject {
        for (kind in listOf("inline","block","inlines","blocks")) {
            val trace=mutableListOf<JsonElement>()
            val visitor=object : PandocVisitor() {
                override fun transformInline(value: Inline): Inline {
                    if(kind!="inline") return value
                    trace.add(PandocJson.encodeInline(value))
                    return if(value is Inline.Str) Inline.Strong(listOf(Inline.Str("walk:"+value.text))) else value
                }
                override fun transformBlock(value: Block): Block {
                    if(kind!="block") return value
                    trace.add(PandocJson.encodeBlock(value))
                    return if(value is Block.Para) Block.BlockQuote(listOf(value)) else value
                }
                override fun transformInlines(value: List<Inline>): List<Inline> {
                    if(kind!="inlines") return value
                    trace.add(list(value,PandocJson::encodeInline));return value+Inline.Str("walk-list")
                }
                override fun transformBlocks(value: List<Block>): List<Block> {
                    if(kind!="blocks") return value
                    trace.add(list(value,PandocJson::encodeBlock));return value+Block.HorizontalRule
                }
            }
            val walked=walk(value,visitor)
            val queried=query(value) { c ->
                val found=mutableListOf<JsonElement>()
                object : PandocVisitor() {
                    override fun visitInline(value: Inline) { if(kind=="inline") found.add(PandocJson.encodeInline(value)) }
                    override fun visitBlock(value: Block) { if(kind=="block") found.add(PandocJson.encodeBlock(value)) }
                    override fun visitInlines(value: List<Inline>) { if(kind=="inlines") found.add(list(value,PandocJson::encodeInline)) }
                    override fun visitBlocks(value: List<Block>) { if(kind=="blocks") found.add(list(value,PandocJson::encodeBlock)) }
                }.walkCell(c)
                found
            }
            put(kind,buildJsonObject { put("walked",encode(walked));put("trace",JsonArray(trace));put("query",JsonArray(queried)) })
        }
    }
    private fun walkTargets(t: A.Table): JsonElement {
        val rows=allRows(t)
        val h=A.TableHead(Attr("walk-head"),rows)
        val r=rows.firstOrNull() ?: A.HeaderRow(Attr(),-17,emptyList())
        val c=rows.flatMap { it.cells }.firstOrNull() ?: A.Cell(A.ColumnSpecs(ColSpec(Alignment.AlignDefault,ColWidth.Default)),-13,Tables.emptyCell)
        return buildJsonObject {
            put("cell",walks(c,::cell,{ x,v -> A.walk(x,v) },{ x,q -> A.query(x,q) }))
            put("row",walks(r,::header,{ x,v -> A.walk(x,v) },{ x,q -> A.query(x,q) }))
            put("head",walks(h,::head,{ x,v -> A.walk(x,v) },{ x,q -> A.query(x,q) }))
        }
    }
    private class Probe(reason: String): RuntimeException(reason)
    private fun partial(input: JsonElement): Block.Table {
        val a=input.jsonArray
        val kind=a[0].jsonPrimitive.content
        val table=PandocJson.decodeBlock(a[1]) as Block.Table
        val first=table.head.rows.first()
        val original=first.cells
        val cells=object : AbstractList<Cell>() {
            override val size: Int get()=when(kind) {
                "cell-list" -> throw Probe("cell list")
                "tail" -> original.size+2
                else -> original.size+1
            }
            override fun get(index: Int): Cell = when {
                kind=="cell-list" -> throw Probe("cell list")
                index<original.size -> original[index]
                kind=="tail" && index==original.size -> Tables.emptyCell
                kind=="tail" -> throw Probe("cell tail")
                else -> throw Probe("unused cell")
            }
        }
        return table.copy(head=table.head.copy(rows=listOf(first.copy(cells=cells))+table.head.rows.drop(1)))
    }
    // The native adapter encodes a Value produced by toJSON, rather than the
    // source type's toEncoding method. Scientific numbers such as 0 and 0.0
    // have the same value; their adapter spelling is not an AST distinction.
    private fun canonical(v: JsonElement): JsonElement = when(v) {
        is JsonObject -> JsonObject(v.mapValues { canonical(it.value) })
        is JsonArray -> JsonArray(v.map(::canonical))
        is JsonPrimitive -> if(v.isString || v == JsonNull || v.booleanOrNull != null) v else
            Json.parseToJsonElement(v.content.toBigDecimal().stripTrailingZeros().toPlainString())
    }
    @TestFactory fun originalObservations(): List<DynamicTest> {
        val rows=GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/tables/annotated.jsonl.gz"))).bufferedReader().use { it.readLines().drop(1) }
        return rows.mapIndexed { i,raw -> DynamicTest.dynamicTest("original annotated table $i") {
            val c=Json.parseToJsonElement(raw).jsonObject
            val op=c.getValue("op").jsonPrimitive.content
            val input=c.getValue("input")
            val observed=try {
                val core=if(op=="raw") null else if(op=="partial") partial(input) else PandocJson.decodeBlock(input) as Block.Table
                val t=if(op=="raw") untable(input) else A.toTable(checkNotNull(core))
                buildJsonObject {
                    put("from",PandocJson.encodeBlock(A.fromTable(t)))
                    put("walks",walkTargets(t))
                    if(op!="raw") put("annotated",table(t))
                    if(op=="annotate") put("normalized",PandocJson.encodeBlock(Tables.normalize(checkNotNull(core))))
                }
            } catch(e: Probe) { buildJsonObject { put("error",e.message) } }
            assertEquals(canonical(c.getValue("expected")),canonical(observed),"case $i $op")
        } }
    }

    @Test fun originalSampleTable() {
        val s1=ColSpec(Alignment.AlignRight,ColWidth.Default)
        val s2=ColSpec(Alignment.AlignLeft,ColWidth.Default)
        val s3=ColSpec(Alignment.AlignCenter,ColWidth.Default)
        val specs=listOf(s1,s2,s3)
        fun c(id: String,h: Long=1,w: Long=1)=Cell(Attr(id),Alignment.AlignDefault,h,w,emptyList())
        fun rows(vararg cs: List<Cell>)=cs.map { Row(Attr(),it) }
        val initial=rows(listOf(c("a"),c("b",3,2)),listOf(c("c",2,2),c("d")))
        val initialBody1=TableBody(Attr(),1,
            rows(emptyList(),listOf(c("e",5),c("f",-7,0))),
            rows(listOf(c("g",4,3),c("h",4,3)),emptyList(),listOf(Tables.emptyCell)))
        val initialBody2=TableBody(Attr(),2,emptyList(),rows(listOf(c("i",4,3),c("j",4,3))))
        val input=Block.Table(Attr(),Tables.emptyCaption,specs,TableHead(Attr(),initial),listOf(initialBody1,initialBody2),TableFoot(Attr(),initial))
        fun ac(cs: List<ColSpec>,n: Long,id: String="",h: Long=1,w: Long=1)=A.Cell(A.ColumnSpecs(cs.first(),cs.drop(1)),n,c(id,h,w))
        fun hr(n: Long,vararg cells: A.Cell)=A.HeaderRow(Attr(),n,cells.toList())
        val finalHead=A.TableHead(Attr(),listOf(
            hr(0,ac(listOf(s1),0,"a"),ac(listOf(s2,s3),1,"b",2,2)),
            hr(1,ac(listOf(s1),0,"c"))))
        val finalBody1=A.TableBody(Attr(),1,
            listOf(hr(2,ac(listOf(s1),0),ac(listOf(s2),1),ac(listOf(s3),2)),
                hr(3,ac(listOf(s1),0,"e"),ac(listOf(s2),1,"f"),ac(listOf(s3),2))),
            listOf(A.BodyRow(Attr(),4,listOf(ac(listOf(s1),0,"g",3)),listOf(ac(listOf(s2,s3),1,"h",3,2))),
                A.BodyRow(Attr(),5,emptyList(),emptyList()),A.BodyRow(Attr(),6,emptyList(),emptyList())))
        val finalBody2=A.TableBody(Attr(),2,emptyList(),listOf(
            A.BodyRow(Attr(),7,listOf(ac(listOf(s1,s2),0,"i",1,2)),listOf(ac(listOf(s3),2,"j")))))
        val finalFoot=A.TableFoot(Attr(),listOf(
            hr(8,ac(listOf(s1),0,"a"),ac(listOf(s2,s3),1,"b",2,2)),
            hr(9,ac(listOf(s1),0,"c"))))
        val expected=A.Table(Attr(),Tables.emptyCaption,specs,finalHead,listOf(finalBody1,finalBody2),finalFoot)
        assertEquals(expected,A.toTable(input))
    }

    private fun generated(r: Random): Block.Table {
        val columns=List(r.nextInt(1,7)) { ColSpec(Alignment.entries.random(r),listOf(ColWidth.Default,ColWidth.Width(1.0/3),ColWidth.Width(.25)).random(r)) }
        fun rows()=List(r.nextInt(8)) { Row(Attr(),List(r.nextInt(8)) { Cell(Attr(),Alignment.entries.random(r),r.nextLong(-8,10),r.nextLong(-8,10),emptyList()) }) }
        return Block.Table(Attr(),Tables.emptyCaption,columns,TableHead(Attr(),rows()),List(r.nextInt(4)) { TableBody(Attr(),r.nextLong(-8,10),rows(),rows()) },TableFoot(Attr(),rows()))
    }
    @TestFactory fun originalProperties(): List<DynamicTest> {
        val names=listOf("normalizes like the table builder","has valid final cell columns","has valid first row column data","has valid all row column data","has valid cell column data lengths")
        return names.mapIndexed { i,name -> DynamicTest.dynamicTest(name) {
            val r=Random(0xA6607+i)
            repeat(50) {
                val input=generated(r);val t=A.toTable(input)
                val firstRows=t.head.rows.take(1).map { it.cells }+t.bodies.flatMap { b -> b.head.take(1).map { it.cells }+b.body.take(1).map { it.head+it.body } }+t.foot.rows.take(1).map { it.cells }
                when(i) {
                    0 -> assertEquals(Tables.normalize(input),A.fromTable(t))
                    1 -> {
                        val sections=t.head.rows.take(1).map { it.cells }+t.bodies.flatMap { b -> b.head.take(1).map { it.cells }+b.body.take(1).map { it.body } }+t.foot.rows.take(1).map { it.cells }
                        for(cells in sections) cells.lastOrNull()?.let { assertEquals(t.columns.size.toLong(),it.columnNumber+it.cell.colSpan) }
                    }
                    2 -> for(cells in firstRows) assertEquals(t.columns,cells.flatMap { it.columns.toList() })
                    3 -> for(row in allRows(t)) {
                        var at=0
                        for(spec in row.cells.flatMap { it.columns.toList() }) {
                            while(at<t.columns.size && t.columns[at]!=spec) at++
                            assertTrue(at<t.columns.size);at++
                        }
                    }
                    4 -> for(row in allRows(t)) for(cell in row.cells) assertEquals(cell.cell.colSpan,cell.columns.toList().size.toLong())
                }
            }
        } }
    }
    @Test fun wideTablesAndAnnotationOwnership() {
        val columns=List(10_000) { i -> ColSpec(Alignment.entries[i%4],ColWidth.Width(i/10_000.0)) }
        val input=Block.Table(Attr(),Tables.emptyCaption,columns,TableHead(Attr(),listOf(Row(Attr(),listOf(Tables.emptyCell.copy(colSpan=Long.MAX_VALUE))))),emptyList(),TableFoot(Attr(),emptyList()))
        val result=A.toTable(input)
        val c=result.head.rows.single().cells.single()
        assertEquals(columns,c.columns.toList());assertEquals(10_000L,c.cell.colSpan);assertEquals(0L,c.columnNumber)
        assertEquals(Tables.normalize(input),A.fromTable(result))
        val tail=mutableListOf(columns[0]);val span=A.ColumnSpecs(columns[0],tail);tail.clear()
        assertEquals(2,span.toList().size)
        assertFailsWith<UnsupportedOperationException> { (span.tail as MutableList<ColSpec>).clear() }
        val walked=A.walk(c) { it.copy(blocks=listOf(Block.HorizontalRule)) }
        assertSame(c.columns,walked.columns);assertEquals(c.columnNumber,walked.columnNumber)
    }
}
