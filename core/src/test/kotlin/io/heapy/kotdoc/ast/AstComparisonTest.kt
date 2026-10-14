package io.heapy.kotdoc.ast

import io.heapy.kotdoc.layout.TextChars
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals

/** Transport only; comparisons and expected extrema are actual original instances. */
class AstComparisonTest {
    private fun JsonElement.s() = jsonPrimitive.content
    private fun JsonElement.i() = jsonPrimitive.int
    private inline fun <reified A : Enum<A>> enum(v: JsonElement): A = enumValueOf(v.jsonObject.getValue("t").s())
    private fun value(kind: String, v: JsonElement): Any = when (kind) {
        "meta" -> PandocJson.decodeMeta(v)
        "text" -> v.s()
        "rowHeadColumns", "rowSpan", "colSpan" -> v.jsonPrimitive.long
        "pandoc" -> PandocJson.decode(v)
        "metaValue" -> PandocJson.decodeMetaValue(v)
        "inline" -> PandocJson.decodeInline(v)
        "block" -> PandocJson.decodeBlock(v)
        "attr" -> PandocJson.decodeAttr(v)
        "target" -> PandocJson.decodeTarget(v)
        "listAttributes" -> PandocJson.decodeListAttributes(v)
        "style" -> enum<ListNumberStyle>(v)
        "delim" -> enum<ListNumberDelim>(v)
        "alignment" -> enum<Alignment>(v)
        "quote" -> enum<QuoteType>(v)
        "math" -> enum<MathType>(v)
        "citationMode" -> enum<CitationMode>(v)
        "citation" -> PandocJson.decodeCitation(v)
        "format" -> Format(v.s())
        "colWidth" -> PandocJson.decodeColWidth(v)
        "colSpec" -> PandocJson.decodeColSpec(v)
        "row" -> PandocJson.decodeRow(v)
        "cell" -> PandocJson.decodeCell(v)
        "caption" -> PandocJson.decodeCaption(v)
        "tableHead" -> PandocJson.decodeTableHead(v)
        "tableBody" -> PandocJson.decodeTableBody(v)
        "tableFoot" -> PandocJson.decodeTableFoot(v)
        else -> error(kind)
    }
    @Suppress("UNCHECKED_CAST")
    private fun dictionary(kind: String): AstComparison<Any> = when (kind) {
        "pandoc" -> AstComparison.Pandoc
        "meta" -> AstComparison.Meta
        "metaValue" -> AstComparison.MetaValue
        "inline" -> AstComparison.Inline
        "block" -> AstComparison.Block
        "attr" -> AstComparison.Attr
        "target" -> AstComparison.Target
        "listAttributes" -> AstComparison.ListAttributes
        "style" -> AstComparison.ListNumberStyle
        "delim" -> AstComparison.ListNumberDelim
        "alignment" -> AstComparison.Alignment
        "quote" -> AstComparison.QuoteType
        "math" -> AstComparison.MathType
        "citationMode" -> AstComparison.CitationMode
        "citation" -> AstComparison.Citation
        "format" -> AstComparison.Format
        "colWidth" -> AstComparison.ColWidth
        "colSpec" -> AstComparison.ColSpec
        "row" -> AstComparison.Row
        "cell" -> AstComparison.Cell
        "caption" -> AstComparison.Caption
        "tableHead" -> AstComparison.TableHead
        "tableBody" -> AstComparison.TableBody
        "tableFoot" -> AstComparison.TableFoot
        "rowHeadColumns" -> AstComparison.RowHeadColumns
        "rowSpan" -> AstComparison.RowSpan
        "colSpan" -> AstComparison.ColSpan
        "text" -> AstComparison.Text
        "double" -> AstComparison.Double
        else -> error(kind)
    } as AstComparison<Any>
    private fun show(kind: String, v: Any): String = when (kind) {
        "meta" -> { @Suppress("UNCHECKED_CAST") AstShow.meta(v as Map<String, MetaValue>) }
        "rowHeadColumns" -> AstShow.rowHeadColumns(v as Long)
        "rowSpan" -> AstShow.rowSpan(v as Long)
        "colSpan" -> AstShow.colSpan(v as Long)
        else -> AstShow.show(v)
    }
    private fun <A> fields(d: AstComparison<A>, a: A, b: A): JsonObject = buildJsonObject {
        put("eq", d.equal(a,b)); put("ne", d.notEqual(a,b))
        put("compare", when { d.compare(a,b)<0 -> "LT"; d.compare(a,b)>0 -> "GT"; else -> "EQ" })
        put("lt",d.less(a,b)); put("le",d.lessEqual(a,b)); put("gt",d.greater(a,b)); put("ge",d.greaterEqual(a,b))
    }
    private fun relations(kind: String, a: Any, b: Any, short: Boolean = false, extract: ((Any)->Double)? = null): JsonElement {
        val d = dictionary(kind)
        return JsonObject(fields(d,a,b) + if (short) emptyMap() else buildMap {
            val lo=d.minimum(a,b); val hi=d.maximum(a,b)
            put("minimum",JsonPrimitive(show(kind,lo))); put("maximum",JsonPrimitive(show(kind,hi)))
            if (extract!=null) {
                put("minimumBits",JsonPrimitive(extract(lo).toRawBits().toULong().toString()).let { Json.parseToJsonElement(it.content) })
                put("maximumBits",JsonPrimitive(extract(hi).toRawBits().toULong().toString()).let { Json.parseToJsonElement(it.content) })
            }
        })
    }
    private fun one(kind: String, op: String, a: Any, b: Any): JsonElement {
        val d=dictionary(kind)
        return when(op) {
            "eq" -> JsonPrimitive(d.equal(a,b)); "ne" -> JsonPrimitive(d.notEqual(a,b))
            "lt" -> JsonPrimitive(d.less(a,b)); "le" -> JsonPrimitive(d.lessEqual(a,b))
            "gt" -> JsonPrimitive(d.greater(a,b)); "ge" -> JsonPrimitive(d.greaterEqual(a,b))
            "compare" -> JsonPrimitive(when { d.compare(a,b)<0 -> "LT"; d.compare(a,b)>0 -> "GT"; else -> "EQ" })
            else -> error(op)
        }
    }
    private fun <A> badHead(message: String): List<A> = object : AbstractList<A>() {
        override val size=1
        override fun get(index: Int): A = error(message)
    }
    private fun badTail(first: Inline, message: String): List<Inline> = object : AbstractList<Inline>() {
        override val size: Int get() = error(message)
        override fun get(index: Int): Inline = error("unused get")
        override fun iterator() = object : Iterator<Inline> {
            private var beginning=true
            override fun hasNext() = if(beginning) true else error(message)
            override fun next(): Inline { beginning=false; return first }
        }
    }
    private fun lazyMeta(vararg entries: Pair<String,()->MetaValue>): Map<String,MetaValue> {
        val definitions=entries.toMap()
        return object : AbstractMap<String,MetaValue>() {
            override val size=definitions.size
            override val keys=definitions.keys
            override fun get(key: String)=definitions[key]?.invoke()
            override val entries: Set<Map.Entry<String,MetaValue>> = object : AbstractSet<Map.Entry<String,MetaValue>>() {
                override val size=definitions.size
                override fun iterator()=definitions.keys.map { key -> object : Map.Entry<String,MetaValue> {
                    override val key=key
                    override val value: MetaValue get()=definitions.getValue(key)()
                } }.iterator()
            }
        }
    }
    private fun partial(name: String, op: String): JsonElement {
        fun run(kind: String,a: Any,b: Any)=one(kind,op,a,b)
        return when(name) {
            "inline-tag" -> run("inline",Inline.Emph(badHead("inline head")),Inline.Str("x"))
            "inline-element" -> run("inline",Inline.Emph(badHead("inline head")),Inline.Emph(listOf(Inline.Space)))
            "inline-length" -> run("inline",Inline.Emph(emptyList()),Inline.Emph(badHead("unused head")))
            "inline-first" -> run("inline",Inline.Emph(badTail(Inline.Space,"left tail")),Inline.Emph(badTail(Inline.LineBreak,"right tail")))
            "inline-self" -> { val x=Inline.Emph(badHead("same head"));run("inline",x,x) }
            "inline-tail" -> run("inline",Inline.Emph(badTail(Inline.Space,"inline tail")),Inline.Emph(listOf(Inline.Space)))
            "citation-hash" -> run("citation",Citation("id",badHead("citation head"),citationHash=7),Citation("id",listOf(Inline.Space),citationHash=8))
            "citation-self" -> { val x=Citation("id",badHead("citation head"),citationHash=7);run("citation",x,x) }
            "meta-size" -> run("meta",lazyMeta("a" to { error("unused value") }),emptyMap<String,MetaValue>())
            "meta-size-more" -> run("meta",lazyMeta("a" to { error("first value") }),mapOf("a" to MetaValue.MetaBool(true),"b" to MetaValue.MetaBool(false)))
            "meta-key" -> run("meta",lazyMeta("a" to { error("unused left") }),lazyMeta("b" to { error("unused right") }))
            "meta-first" -> run("meta",lazyMeta("a" to { MetaValue.MetaBool(false) },"b" to { error("unused value") }),lazyMeta("a" to { MetaValue.MetaBool(true) },"b" to { error("unused value") }))
            "meta-demand" -> run("meta",lazyMeta("a" to { error("map value") }),mapOf("a" to MetaValue.MetaBool(false)))
            "pandoc-meta" -> run("pandoc",Pandoc(mapOf("a" to MetaValue.MetaBool(false)),badHead("unused block")),Pandoc(mapOf("b" to MetaValue.MetaBool(false)),badHead("unused block")))
            "caption-short" -> run("caption",Caption(listOf(Inline.Space),badHead("unused block")),Caption(listOf(Inline.LineBreak),badHead("unused block")))
            "caption-short-length" -> run("caption",Caption(null,badHead("unused block")),Caption(badHead("unused inline"),badHead("unused block")))
            "row-attr" -> run("row",Row(Attr("a"),badHead("unused cell")),Row(Attr("b"),badHead("unused cell")))
            "cell-attr" -> run("cell",Cell(Attr("a"),Alignment.AlignDefault,1,1,badHead("unused block")),Cell(Attr("b"),Alignment.AlignDefault,1,1,badHead("unused block")))
            "block-attr" -> run("block",Block.Div(Attr("a"),badHead("unused block")),Block.Div(Attr("b"),badHead("unused block")))
            else -> error(name)
        }
    }
    private fun table(d: Double)=Block.Table(Attr(),Caption(null,emptyList()),listOf(ColSpec(Alignment.AlignDefault,ColWidth.Width(d))),TableHead(Attr(),emptyList()),emptyList(),TableFoot(Attr(),emptyList()))
    private fun tableWidth(b: Block) = (((b as Block.Table).columns.first().width) as ColWidth.Width).value
    private fun meta(d: Double)=mapOf("x" to MetaValue.MetaBlocks(listOf(table(d))))
    private fun metaWidth(m: Map<String,MetaValue>)=tableWidth((m.getValue("x") as MetaValue.MetaBlocks).content.first())
    private fun widthCell(d: Double)=Cell(Attr(),Alignment.AlignDefault,1,1,listOf(table(d)))
    private fun cellWidth(c: Cell)=tableWidth(c.blocks.first())
    private fun widthRow(d: Double)=Row(Attr(),listOf(widthCell(d)))
    private fun rowWidth(r: Row)=cellWidth(r.cells.first())
    @Suppress("UNCHECKED_CAST")
    private fun width(kind: String, a: Double, b: Double): JsonElement {
        val make: (Double)->Any
        val extract: (Any)->Double
        val dictionaryKind: String
        when(kind) {
            "double" -> { make={it};extract={it as Double};dictionaryKind=kind }
            "colWidth" -> { make={ColWidth.Width(it)};extract={(it as ColWidth.Width).value};dictionaryKind=kind }
            "colSpec" -> { make={ColSpec(Alignment.AlignDefault,ColWidth.Width(it))};extract={((it as ColSpec).width as ColWidth.Width).value};dictionaryKind=kind }
            "colSpecs" -> {
                val x=listOf(ColSpec(Alignment.AlignDefault,ColWidth.Width(a)));val y=listOf(ColSpec(Alignment.AlignDefault,ColWidth.Width(b)))
                val d=AstComparison.list(AstComparison.ColSpec)
                return JsonObject(fields(d,x,y)+buildJsonObject {
                    val lo=d.minimum(x,y);val hi=d.maximum(x,y)
                    put("minimum",AstShow.show(lo));put("maximum",AstShow.show(hi))
                    put("minimumBits",Json.parseToJsonElement((lo.first().width as ColWidth.Width).value.toRawBits().toULong().toString()))
                    put("maximumBits",Json.parseToJsonElement((hi.first().width as ColWidth.Width).value.toRawBits().toULong().toString()))
                })
            }
            "block" -> { make={table(it)};extract={tableWidth(it as Block)};dictionaryKind=kind }
            "inline" -> { make={Inline.Note(listOf(table(it)))};extract={tableWidth((it as Inline.Note).blocks.first())};dictionaryKind=kind }
            "metaValue" -> { make={MetaValue.MetaBlocks(listOf(table(it)))};extract={tableWidth((it as MetaValue.MetaBlocks).content.first())};dictionaryKind=kind }
            "meta" -> { make={meta(it)};extract={metaWidth(it as Map<String,MetaValue>)};dictionaryKind=kind }
            "pandoc" -> { make={Pandoc(meta(it),listOf(table(it)))};extract={metaWidth((it as Pandoc).meta)};dictionaryKind=kind }
            "pandoc-blocks" -> { make={Pandoc(emptyMap(),listOf(table(it)))};extract={tableWidth((it as Pandoc).blocks.first())};dictionaryKind="pandoc" }
            "caption" -> { make={Caption(null,listOf(table(it)))};extract={tableWidth((it as Caption).long.first())};dictionaryKind=kind }
            "caption-short" -> { make={Caption(listOf(Inline.Note(listOf(table(it)))),emptyList())};extract={tableWidth(((it as Caption).short!!.first() as Inline.Note).blocks.first())};dictionaryKind="caption" }
            "cell" -> { make={widthCell(it)};extract={cellWidth(it as Cell)};dictionaryKind=kind }
            "row" -> { make={widthRow(it)};extract={rowWidth(it as Row)};dictionaryKind=kind }
            "tableHead" -> { make={TableHead(Attr(),listOf(widthRow(it)))};extract={rowWidth((it as TableHead).rows.first())};dictionaryKind=kind }
            "tableFoot" -> { make={TableFoot(Attr(),listOf(widthRow(it)))};extract={rowWidth((it as TableFoot).rows.first())};dictionaryKind=kind }
            "tableBody" -> { make={TableBody(Attr(),0,emptyList(),listOf(widthRow(it)))};extract={rowWidth((it as TableBody).body.first())};dictionaryKind=kind }
            "tableBody-head" -> { make={TableBody(Attr(),0,listOf(widthRow(it)),emptyList())};extract={rowWidth((it as TableBody).head.first())};dictionaryKind="tableBody" }
            else -> error(kind)
        }
        return relations(dictionaryKind,make(a),make(b),extract=extract)
    }
    private fun deep(kind: String,n: Int,different: Boolean): JsonElement {
        fun inline(leaf: Inline): Inline { var x=leaf;repeat(n) { x=if(kind=="mixed") Inline.Note(listOf(Block.Plain(listOf(x)))) else Inline.Emph(listOf(x)) };return x }
        fun block(leaf: Block): Block { var x=leaf;repeat(n) { x=Block.BlockQuote(listOf(x)) };return x }
        fun meta(leaf: Boolean): MetaValue { var x: MetaValue=MetaValue.MetaBool(leaf);repeat(n) { x=MetaValue.MetaList(listOf(x)) };return x }
        return when(kind) {
            "inline","mixed" -> relations("inline",inline(Inline.Space),inline(if(different) Inline.LineBreak else Inline.Space),true)
            "block" -> relations("block",block(Block.HorizontalRule),block(if(different) Block.Plain(emptyList()) else Block.HorizontalRule),true)
            "metaValue" -> relations(kind,meta(false),meta(different),true)
            else -> error(kind)
        }
    }
    private fun operation(a: JsonArray): JsonElement = when(a[0].s()) {
        "relations" -> relations(a[1].s(),value(a[1].s(),a[2]),value(a[1].s(),a[3]))
        "list" -> {
            val kind=a[1].s();val d=AstComparison.list(dictionary(kind))
            val x=a[2].jsonArray.map { value(kind,it) };val y=a[3].jsonArray.map { value(kind,it) }
            JsonObject(fields(d,x,y)+buildJsonObject { put("minimum",AstShow.show(d.minimum(x,y)));put("maximum",AstShow.show(d.maximum(x,y))) })
        }
        "width" -> width(a[1].s(),Double.fromBits(a[2].s().toULong().toLong()),Double.fromBits(a[3].s().toULong().toLong()))
        "text" -> {
            fun t(v: JsonElement)=TextChars.fromPoints(v.jsonArray.map { it.i() }.toIntArray())
            val kind=a[1].s()
            fun v(s: String): Any = when(kind) { "text" -> s;"format" -> Format(s);"inline" -> Inline.Str(s);else -> error(kind) }
            relations(kind,v(t(a[2])),v(t(a[3])))
        }
        "partial" -> partial(a[1].s(),a[2].s())
        "deep" -> deep(a[1].s(),a[2].i(),a[3].jsonPrimitive.boolean)
        "infinite" -> {
            fun infinite(at: Int)=object : AbstractList<Inline>() {
                override val size: Int get()=error("infinite size")
                override fun get(index: Int)=if(index==at) Inline.LineBreak else Inline.Space
                override fun iterator()=object : Iterator<Inline> {
                    private var index=0
                    override fun hasNext()=true
                    override fun next()=get(index++)
                }
            }
            fields(AstComparison.list(AstComparison.Inline),infinite(-1),infinite(a[1].i()))
        }
        else -> error(a)
    }
    @TestFactory fun originalObservations(): List<DynamicTest> =
        GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/ast-order/data.jsonl.gz"))).bufferedReader().use { reader ->
            reader.lineSequence().drop(1).mapIndexed { index,line -> DynamicTest.dynamicTest("original AST comparison $index") {
                val c=Json.parseToJsonElement(line).jsonObject;val request=c.getValue("request").jsonArray
                val actual=try { operation(request) } catch(e: IllegalStateException) { buildJsonObject { put("exception",e.message.orEmpty().lineSequence().first()) } }
                assertEquals(c.getValue("expected"),actual,"case $index: ${request.toString().take(500)}")
            } }.toList()
        }
}
