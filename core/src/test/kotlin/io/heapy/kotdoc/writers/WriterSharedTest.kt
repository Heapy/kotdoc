package io.heapy.kotdoc.writers

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.layout.*
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.writers.WriterShared as W
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals

/** Transport adapter only; every expectation is captured from original Haskell. */
class WriterSharedTest {
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
    private fun maybeInline(value: Inline?) = value?.let(PandocJson::encodeInline) ?: JsonNull
    private fun taskResult(value: Pair<Boolean,List<Block>>) = j(JsonPrimitive(value.first),list(value.second,PandocJson::encodeBlock))
    private fun task(blocks: List<Block>): JsonElement {
        val maybe=W.toTaskListItem(blocks)
        var pureCalls=0;var zeroCalls=0
        val results=W.toTaskListItem(blocks,{ pureCalls++;listOf(it) },{ zeroCalls++;emptyList() })
        assertEquals(if(maybe==null) 0 else 1,pureCalls)
        assertEquals(if(maybe==null) 1 else 0,zeroCalls)
        return buildJsonObject { put("maybe",maybe?.let(::taskResult) ?: JsonNull);put("list",list(results,::taskResult)) }
    }
    private fun inline(value: Inline) = buildJsonObject {
        put("display",W.isDisplayMath(value));put("sub",maybeInline(W.toSubscriptInline(value)))
        put("super",maybeInline(W.toSuperscriptInline(value)));put("remove",list(W.removeLinks(listOf(value)),PandocJson::encodeInline))
    }
    private fun block(value: Block) = buildJsonObject {
        put("math",PandocJson.encodeBlock(W.fixDisplayMath(value)));put("ends",W.endsWithPlain(listOf(value)));put("task",task(listOf(value)))
    }
    private fun observeDoc(d: Doc<String>) = buildJsonObject {
        put("doc",encDoc(d));put("render",layout.render(null,d));put("wrapped",layout.render(17,d));put("offset",layout.offset(d));put("minOffset",layout.minOffset(d))
    }
    private fun probe(point: Int): JsonElement {
        val text=TextChars.fromPoints(intArrayOf(point))
        val p=Pandoc(emptyMap(),listOf(Block.Plain(listOf(Inline.Code(Attr(text),"x")))))
        val xml=W.ensureValidXmlIdentifiers(p).blocks.single() as Block.Plain
        val accepted=(xml.content.single() as Inline.Code).attr.identifier==text
        val d=Doc.Text(2L,text+".")+Doc.BreakingSpace
        return j(W.toSubscript(point)?.let(::JsonPrimitive) ?: JsonNull,W.toSuperscript(point)?.let(::JsonPrimitive) ?: JsonNull,
            JsonPrimitive(accepted),JsonPrimitive(W.splitSentences(d)==d))
    }
    private fun peel(value: Inline?): JsonElement {
        if(value==null)return JsonNull
        val attrs=mutableListOf<Attr>();var leaf=value
        while(leaf is Inline.Span && leaf.content.size==1) { attrs.add(leaf.attr);leaf=leaf.content.single() }
        return j(list(attrs,PandocJson::encodeAttr),PandocJson.encodeInline(leaf))
    }
    private fun partial(kind: String): JsonElement = when(kind) {
        "script-stop" -> {
            val xs=object: AbstractList<Inline>() {
                override val size=2
                override fun get(index: Int): Inline=if(index==0) Inline.Str("unsupported") else error("later inline")
            }
            maybeInline(W.toSubscriptInline(Inline.Span(Attr(),xs)))
        }
        "plain-last" -> {
            val content=object: AbstractList<Inline>() { override val size=1;override fun get(index: Int): Inline=error("plain contents") }
            val xs=object: AbstractList<Block>() { override val size=2;override fun get(index: Int): Block=if(index==1) Block.Plain(content) else error("earlier block") }
            JsonPrimitive(W.endsWithPlain(xs))
        }
        "task-zero" -> {
            val xs=object: AbstractList<Block>() { override val size=2;override fun get(index: Int): Block=if(index==0) Block.HorizontalRule else error("later block") }
            task(xs)
        }
        "task-success" -> {
            val inlines=object: AbstractList<Inline>() {
                override val size=3
                override fun get(index: Int): Inline=when(index) { 0->Inline.Str("☐");1->Inline.Space;else->error("contents") }
            }
            val blocks=object: AbstractList<Block>() {
                override val size=2
                override fun get(index: Int): Block=if(index==0) Block.Plain(inlines) else error("later block")
            }
            W.toTaskListItem(blocks,{ it.first },{ null })?.let(::JsonPrimitive) ?: JsonNull
        }
        "unsmartify-no-extension" -> JsonPrimitive(W.unsmartify("no smart dashes") { error("options") })
        "display-direct" -> JsonPrimitive(W.isDisplayMath(Inline.Math(MathType.DisplayMath,"")))
        "display-span" -> JsonPrimitive(W.isDisplayMath(Inline.Span(Attr(),listOf(Inline.Math(MathType.DisplayMath,"")))))
        "math-all" -> PandocJson.encodeBlock(W.fixDisplayMath(Block.Para(listOf(Inline.Math(MathType.DisplayMath,"x"),Inline.Math(MathType.DisplayMath,"y")))))
        else -> error(kind)
    }
    private fun run(op: String,v: JsonElement): JsonElement = when(op) {
        "inline" -> inline(PandocJson.decodeInline(v)); "block" -> block(PandocJson.decodeBlock(v))
        "inlines" -> v.jsonArray.map(PandocJson::decodeInline).let { xs->buildJsonObject {
            put("strip",list(W.stripLeadingTrailingSpace(xs),PandocJson::encodeInline));put("remove",list(W.removeLinks(xs),PandocJson::encodeInline))
        } }
        "blocks" -> v.jsonArray.map(PandocJson::decodeBlock).let { xs->buildJsonObject { put("ends",W.endsWithPlain(xs));put("task",task(xs)) } }
        "document" -> {
            val p=PandocJson.decode(v)
            val math=object: PandocVisitor() { override fun transformBlock(value: Block)=W.fixDisplayMath(value) }.walk(p)
            buildJsonObject { put("xml",PandocJson.toJson(W.ensureValidXmlIdentifiers(p)));put("math",PandocJson.toJson(math)) }
        }
        "unsmartify" -> v.jsonArray.let { a->
            val o=WriterOptions(extensions=if(a[0].jsonPrimitive.boolean) Extensions.of(ExtensionName.OldDashes) else Extensions.Empty,syntaxMap=emptyMap())
            val text=TextChars.fromPoints(a[1].jsonArray.map { it.jsonPrimitive.int }.toIntArray())
            JsonArray(TextChars.points(W.unsmartify(o,text)).map(::JsonPrimitive))
        }
        "layout","layout-structure" -> v.jsonArray.let { a->
            val delimited=W.delimited(doc(a[0]),doc(a[1]),doc(a[2]));val sentences=W.splitSentences(doc(a[2]))
            if(op=="layout-structure") j(encDoc(delimited),encDoc(sentences)) else buildJsonObject { put("delimited",observeDoc(delimited));put("sentences",observeDoc(sentences)) }
        }
        "deep-inline" -> v.jsonArray.let { a->
            val depth=a[0].jsonPrimitive.int
            var value: Inline=Inline.Str(if(a[1].jsonPrimitive.boolean) "x" else "12+")
            for(n in depth downTo 1)value=Inline.Span(Attr(n.toString(),listOf("depth"),listOf("x" to "y")),listOf(value))
            buildJsonObject { put("sub",peel(W.toSubscriptInline(value)));put("super",peel(W.toSuperscriptInline(value))) }
        }
        "deep-ends" -> v.jsonArray.let { a->
            var blocks: List<Block> = listOf(if(a[1].jsonPrimitive.boolean) Block.Plain(emptyList()) else Block.Para(emptyList()))
            repeat(a[0].jsonPrimitive.int) { blocks=listOf(Block.BulletList(listOf(blocks))) }
            JsonPrimitive(W.endsWithPlain(blocks))
        }
        "deep-layout" -> v.jsonArray.let { a->
            val xs=List(a[0].jsonPrimitive.int) { when(it%3) { 0->Doc.BreakingSpace;1->Doc.CarriageReturn;else->Doc.BlankLines(2) } }
            val d=if(a[1].jsonPrimitive.boolean) xs.fold(Doc.Empty as Doc<String>) { acc,x->acc+x } else xs.asReversed().fold(Doc.Empty as Doc<String>) { acc,x->x+acc }
            buildJsonObject { put("delimited",layout.render(null,W.delimited(layout.literal("["),layout.literal("]"),d)));put("sentences",layout.render(null,W.splitSentences(d))) }
        }
        "partial" -> partial(v.s())
        else -> error(op)
    }
    private fun canonical(v: JsonElement): JsonElement = when(v) {
        is JsonObject -> JsonObject(v.mapValues { canonical(it.value) });is JsonArray->JsonArray(v.map(::canonical))
        is JsonPrimitive->if(v.isString || v==JsonNull || v.booleanOrNull!=null) v else Json.parseToJsonElement(v.content.toBigDecimal().stripTrailingZeros().toPlainString())
    }
    @TestFactory fun originalObservations(): List<DynamicTest> =
        GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/writers/shared.jsonl.gz"))).bufferedReader().use { reader->
            reader.lineSequence().drop(1).mapIndexed { index,line->DynamicTest.dynamicTest("original pure writer $index") {
                val c=Json.parseToJsonElement(line).jsonObject;val op=c.getValue("op").s();val v=c.getValue("input")
                if(op=="char-range") {
                    val start=v.jsonArray[0].jsonPrimitive.int;val end=v.jsonArray[1].jsonPrimitive.int;val expected=c.getValue("expected").jsonArray
                    assertEquals(end-start,expected.size)
                    for(point in start..<end) assertEquals(expected[point-start],probe(point),"GHC Char U+${point.toString(16)}")
                } else assertEquals(canonical(c.getValue("expected")),canonical(run(op,v)),"case $index $op")
            } }.toList()
        }
}
