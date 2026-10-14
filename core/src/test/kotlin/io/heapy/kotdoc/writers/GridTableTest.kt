package io.heapy.kotdoc.writers

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.layout.*
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.writers.GridTable as G
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** Transport adapter only; expectations are captured from unchanged Haskell. */
class GridTableTest {
    private val layout = DocLayout.Text
    private fun j(vararg xs: JsonElement) = JsonArray(xs.toList())
    private fun tag(t: String, vararg xs: JsonElement) = j(JsonPrimitive(t),*xs)
    private fun JsonElement.s() = jsonPrimitive.content
    private fun JsonElement.n() = jsonPrimitive.long
    private fun <A> list(xs: List<A>, f: (A)->JsonElement) = JsonArray(xs.map(f))
    private fun nullable(x: String?) = x?.let(::JsonPrimitive) ?: JsonNull
    private fun color(x: JsonElement) = x.takeUnless { it==JsonNull }?.jsonPrimitive?.int?.let { Color8.entries[it] }
    private fun font(v: JsonElement) = v.jsonArray.let { Font(it[0].jsonPrimitive.boolean,it[1].jsonPrimitive.boolean,
        Underline.entries[it[2].jsonPrimitive.int],it[3].jsonPrimitive.boolean,color(it[4]),color(it[5]),it[6].takeUnless { x->x==JsonNull }?.s()) }
    private fun encFont(f: Font) = j(JsonPrimitive(f.bold),JsonPrimitive(f.italic),JsonPrimitive(f.underline.ordinal),
        JsonPrimitive(f.strikeout),f.foreground?.let { JsonPrimitive(it.ordinal) } ?: JsonNull,
        f.background?.let { JsonPrimitive(it.ordinal) } ?: JsonNull,nullable(f.link))
    private fun attributed(v: JsonElement) = Attributed(v.jsonArray.map { a -> a.jsonArray.let {
        AttributedPart(it[0].takeUnless { x->x==JsonNull }?.s(),font(it[1]),it[2].s())
    } })
    private fun encAttributed(a: Attributed<String>) = list(a.parts) { j(nullable(it.link),encFont(it.font),JsonPrimitive(it.value)) }
    private fun style(v: JsonElement): StyleRequest = v.jsonArray.let { a -> when(a[0].s()) {
        "weight" -> StyleRequest.Weight(a[1].jsonPrimitive.boolean); "shape" -> StyleRequest.Shape(a[1].jsonPrimitive.boolean)
        "strikeout" -> StyleRequest.Strikeout(a[1].jsonPrimitive.boolean); "underline" -> StyleRequest.Underlined(Underline.entries[a[1].jsonPrimitive.int])
        "fg" -> StyleRequest.Foreground(color(a[1])); "bg" -> StyleRequest.Background(color(a[1])); else -> error(v)
    } }
    private fun encStyle(v: StyleRequest) = when(v) {
        is StyleRequest.Weight -> tag("weight",JsonPrimitive(v.bold)); is StyleRequest.Shape -> tag("shape",JsonPrimitive(v.italic))
        is StyleRequest.Strikeout -> tag("strikeout",JsonPrimitive(v.struck)); is StyleRequest.Underlined -> tag("underline",JsonPrimitive(v.underline.ordinal))
        is StyleRequest.Foreground -> tag("fg",v.color?.let { JsonPrimitive(it.ordinal) } ?: JsonNull)
        is StyleRequest.Background -> tag("bg",v.color?.let { JsonPrimitive(it.ordinal) } ?: JsonNull)
    }
    private fun doc(v: JsonElement): Doc<String> {
        if(v is JsonPrimitive && v.isString) return layout.literal(v.content)
        val a=v.jsonArray
        fun d(i: Int)=doc(a[i])
        return when(a[0].s()) {
            "empty" -> Doc.Empty; "space" -> Doc.BreakingSpace; "cr" -> Doc.CarriageReturn; "newline" -> Doc.NewLine
            "blanklines" -> Doc.BlankLines(a[1].n()); "text","raw" -> Doc.Text(a[1].n(),a[2].s()); "vfill" -> Doc.VFill(a[1].n(),a[2].s())
            "literal" -> layout.literal(a[1].s()); "concat" -> Doc.Concat(d(1),d(2)); "hsep" -> layout.hsep(a[1].jsonArray.map(::doc))
            "vcat" -> layout.vcat(a[1].jsonArray.map(::doc)); "prefix" -> layout.prefixed(a[1].s(),d(2)); "bold" -> layout.bold(d(1))
            "before" -> Doc.BeforeNonBlank(d(1)); "flush" -> Doc.Flush(d(1)); "after" -> Doc.AfterBreak(a[1].s())
            "styled" -> Doc.Styled(style(a[1]),d(2)); "link" -> Doc.Linked(a[1].s(),d(2))
            "block" -> Doc.Block(a[1].n(),a[2].jsonArray.map(::attributed)); "cooked" -> Doc.CookedText(a[1].n(),attributed(a[2]))
            else -> error(v)
        }
    }
    private fun encDoc(d: Doc<String>): JsonElement = when(d) {
        is Doc.Text -> tag("text",JsonPrimitive(d.width),JsonPrimitive(d.value)); is Doc.Block -> tag("block",JsonPrimitive(d.width),list(d.lines,::encAttributed))
        is Doc.VFill -> tag("vfill",JsonPrimitive(d.width),JsonPrimitive(d.value)); is Doc.CookedText -> tag("cooked",JsonPrimitive(d.width),encAttributed(d.value))
        is Doc.Prefixed -> tag("prefix",JsonPrimitive(d.prefix),encDoc(d.doc)); is Doc.BeforeNonBlank -> tag("before",encDoc(d.doc)); is Doc.Flush -> tag("flush",encDoc(d.doc))
        is Doc.AfterBreak -> tag("after",JsonPrimitive(d.value)); is Doc.BlankLines -> tag("blanklines",JsonPrimitive(d.count)); is Doc.Concat -> tag("concat",encDoc(d.left),encDoc(d.right))
        is Doc.Styled -> tag("styled",encStyle(d.style),encDoc(d.doc)); is Doc.Linked -> tag("link",JsonPrimitive(d.link),encDoc(d.doc))
        Doc.Empty -> tag("empty"); Doc.NewLine -> tag("newline"); Doc.CarriageReturn -> tag("cr"); Doc.BreakingSpace -> tag("space")
    }
    private fun sty(v: JsonElement) = G.LineStyle.entries[v.jsonPrimitive.int]
    private fun opts(n: JsonElement,w: JsonElement) = WriterOptions(columns=n.n(),wrapText=WrapOption.entries[w.jsonPrimitive.int],
        tabStop=13,identifierPrefix="forwarded",syntaxMap=emptyMap())
    private fun cell(v: JsonElement): G.RenderedCell<String> = v.jsonArray.let { a ->
        val specs=a[2].jsonArray.map(PandocJson::decodeColSpec)
        G.RenderedCell(a[0].n(),a[1].n(),AnnotatedTable.ColumnSpecs(specs.first(),specs.drop(1)),
            Alignment.valueOf(a[3].jsonObject.getValue("t").s()),a[4].n(),a[5].n(),doc(a[6]),sty(a[7]),sty(a[8]))
    }
    private fun encCell(c: G.RenderedCell<String>) = j(JsonPrimitive(c.column),JsonPrimitive(c.colSpan),
        list(c.columns.toList(),PandocJson::encodeColSpec),buildJsonObject { put("t",c.align.name) },JsonPrimitive(c.rowSpan),
        JsonPrimitive(c.width),encDoc(c.contents),JsonPrimitive(c.bottom.ordinal),JsonPrimitive(c.top.ordinal))
    private fun rows(v: JsonElement) = v.jsonArray.map { it.jsonArray.map(::cell) }
    private fun encRows(v: List<List<G.RenderedCell<String>>>) = list(v) { list(it,::encCell) }
    private fun info(v: JsonElement) = v.jsonArray.let { G.ColWidthInfo(it[0].n(),it[1].n(),it[2].n(),it[3].n()) }
    private fun encInfo(v: G.ColWidthInfo) = j(JsonPrimitive(v.specified),JsonPrimitive(v.full),JsonPrimitive(v.min),JsonPrimitive(v.used))
    private fun observeDoc(n: Long,d: Doc<String>) = buildJsonObject {
        put("doc",encDoc(d));put("render",layout.render(null,d));put("wrapped",layout.render(n,d));put("offset",layout.offset(d));put("minOffset",layout.minOffset(d))
    }
    private fun spans(v: JsonElement) = v.jsonArray.associate { it.jsonArray.let { x->x[0].n() to WriterTables.Span(x[1].n(),x[2].n()) } }
    private fun encSpans(v: Map<Long,WriterTables.Span>) = JsonArray(v.toSortedMap().map { (n,s)->j(JsonPrimitive(n),JsonPrimitive(s.rowSpan),JsonPrimitive(s.colSpan)) })
    private class RendererFailure : RuntimeException()
    private fun run(op: String,v: JsonElement): JsonElement {
        val a=v.jsonArray
        return when(op) {
            "grid" -> {
                val options=opts(a[0],a[1]);val table=PandocJson.decodeBlock(a[2]) as Block.Table;val docs=a[3].jsonArray
                val trace=mutableListOf<JsonElement>()
                fun callback(forwarded: WriterOptions,blocks: List<Block>): Doc<String> {
                    assertSame(options,forwarded)
                    val i=trace.size
                    trace.add(j(JsonPrimitive(i),list(blocks,PandocJson::encodeBlock),JsonPrimitive(forwarded.columns),
                        JsonPrimitive(forwarded.wrapText.name),JsonPrimitive(forwarded.tabStop),JsonPrimitive(forwarded.identifierPrefix)))
                    return doc(docs[i%docs.size])
                }
                val d=G.gridTable(options,::callback,table)
                buildJsonObject { put("result",observeDoc(options.columns,d));put("trace",JsonArray(trace)) }
            }
            "pipeline" -> {
                val o=opts(a[0],a[1]);val dummy=G.addDummies(rows(a[3]));val updated=G.redoWidths(o,a[2].jsonArray.map(PandocJson::decodeColSpec),dummy)
                buildJsonObject { put("dummy",encRows(dummy));put("widths",list(G.extractColWidths(o,dummy),::encInfo));put("updated",encRows(updated));put("result",observeDoc(o.columns,G.gridRows(updated))) }
            }
            "reset" -> list(G.resetWidths(a[0].jsonArray.map { it.n() },a[1].jsonArray.map(::cell)),::encCell)
            "widths" -> list(G.extractColWidths(opts(a[0],a[1]),rows(a[2])),::encInfo)
            "recalculate" -> list(G.recalculateWidths(a[0].n(),a[1].jsonArray.map(::info)),::encInfo)
            "borders" -> {
                val s=sty(a[0]);val markers=a[1].jsonPrimitive.boolean;val rs=rows(a[2])
                buildJsonObject { put("border",list(rs) { encDoc(G.formatBorder({s},markers,it)) });put("header",encDoc(G.formatHeaderLine(s,rs)))
                    put("isHeader",G.isHeaderStyle(s));put("top",encRows(G.setTopBorder(s,rs)));put("bottom",encRows(G.setBottomBorder(s,rs))) }
            }
            "combine" -> JsonPrimitive(G.combineBorders(a[0].s(),a[1].s()))
            "char-width" -> JsonPrimitive(G.toCharWidth(WriterOptions(columns=a[0].n(),syntaxMap=emptyMap()),Double.fromBits(a[1].s().toULong().toLong())))
            "row" -> {
                val o=opts(a[0],a[1]);val ds=a[3].jsonArray;var i=0
                val cells=a[2].jsonArray.map { x->x.jsonArray.let { c->
                    val specs=c[0].jsonArray.map(PandocJson::decodeColSpec)
                    AnnotatedTable.Cell(AnnotatedTable.ColumnSpecs(specs.first(),specs.drop(1)),c[1].n(),PandocJson.decodeCell(c[2]))
                } }
                list(G.gridRow(o,{ forwarded,_->assertSame(o,forwarded);doc(ds[i++%ds.size]) },cells),::encCell)
            }
            "failure" -> {
                val o=opts(a[0],a[1]);val table=PandocJson.decodeBlock(a[2]) as Block.Table
                val trace=mutableListOf<JsonElement>()
                val result=try {
                    observeDoc(o.columns,G.gridTable(o,{ forwarded,blocks->
                        assertSame(o,forwarded);val i=trace.size;trace.add(list(blocks,PandocJson::encodeBlock))
                        if(i.toLong()==a[3].n()) throw RendererFailure()
                        layout.literal("ok")
                    },table))
                } catch (_: RendererFailure) { JsonPrimitive("renderer failed") }
                buildJsonObject { put("trace",JsonArray(trace));put("result",result) }
            }
            "spans" -> {
                val previous=spans(a[2]);val column=a[1].n()
                when(a[0].s()) {
                    "insert" -> encSpans(WriterTables.insertCurrentSpansAtColumn(column,previous,a[3].n(),a[4].n()))
                    "take" -> WriterTables.takePreviousSpansAtColumn(column,previous)?.let { j(JsonPrimitive(it.first),encSpans(it.second)) } ?: JsonNull
                    "trailing" -> encSpans(WriterTables.decrementTrailingRowSpans(column,previous));else -> error(v)
                }
            }
            "tables" -> {
                val table=PandocJson.decodeBlock(a[0]) as Block.Table;val body=WriterTables.tableBodiesToRows(table.bodies)
                buildJsonObject { put("body",list(body,PandocJson::encodeRow));put("emptyHead",WriterTables.allRowsEmpty(table.head.rows))
                    put("emptyBody",WriterTables.allRowsEmpty(body));put("emptyFoot",WriterTables.allRowsEmpty(table.foot.rows)) }
            }
            else -> error(op)
        }
    }
    private fun canonical(v: JsonElement): JsonElement = when(v) {
        is JsonObject -> JsonObject(v.mapValues { canonical(it.value) }); is JsonArray -> JsonArray(v.map(::canonical))
        is JsonPrimitive -> if(v.isString || v==JsonNull || v.booleanOrNull!=null) v else Json.parseToJsonElement(v.content.toBigDecimal().stripTrailingZeros().toPlainString())
    }
    @TestFactory fun originalObservations(): List<DynamicTest> =
        GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/tables/grid.jsonl.gz"))).bufferedReader().use { reader ->
            reader.lineSequence().drop(1).mapIndexed { i,line -> DynamicTest.dynamicTest("original grid $i") {
                val c=Json.parseToJsonElement(line).jsonObject
                assertEquals(canonical(c.getValue("expected")),canonical(run(c.getValue("op").s(),c.getValue("input"))),"case $i ${c.getValue("op")}")
            } }.toList()
        }
}
