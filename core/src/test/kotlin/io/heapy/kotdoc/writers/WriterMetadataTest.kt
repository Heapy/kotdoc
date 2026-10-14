package io.heapy.kotdoc.writers

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.layout.*
import io.heapy.kotdoc.options.WriterOptions
import io.heapy.kotdoc.templates.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals

/** Observation protocol only; all expected outputs come from original Haskell. */
class WriterMetadataTest {
    private fun j(vararg values: JsonElement) = JsonArray(values.toList())
    private fun tag(name: String, vararg fields: JsonElement) = j(JsonPrimitive(name), *fields)
    private fun JsonElement.s() = jsonPrimitive.content
    private fun JsonElement.n() = jsonPrimitive.long
    private fun JsonElement.ps() = jsonArray.map { it.jsonPrimitive.int }.toIntArray()
    private fun string(x: String?) = x?.let(::JsonPrimitive) ?: JsonNull
    private val text = Adapter(TemplateValues.Text, TextChars::fromString, TextChars::fromPoints, ::JsonPrimitive)
    private val chars = Adapter(TemplateValues.String, CharacterStringChars::fromString, ::CharacterString) { JsonArray(it.points().map(::JsonPrimitive)) }
    private data class Wrapped(val value: String)
    private val wrappedChars = object : HasChars<Wrapped> {
        override val empty = Wrapped("")
        override fun fromString(text: String) = Wrapped(TextChars.fromString(text))
        override fun points(value: Wrapped) = TextChars.points(value.value)
        override fun append(a: Wrapped, b: Wrapped) = Wrapped(a.value+b.value)
        override fun splitLines(value: Wrapped) = TextChars.splitLines(value.value).map(::Wrapped)
    }
    private val wrapped = Adapter(TemplateValues(DocLayout(wrappedChars), { Wrapped("!"+it) }, { "?"+it.value }),
        wrappedChars::fromString, { Wrapped(TextChars.fromPoints(it)) }, { JsonPrimitive(it.value) })

    private inner class Adapter<A : Any>(val values: TemplateValues<A>, val raw: (String)->A,
                                         val points: (IntArray)->A, val encode: (A)->JsonElement) {
        val layout = values.layout
        private fun color(x: JsonElement) = x.takeUnless { it == JsonNull }?.jsonPrimitive?.int?.let { Color8.entries[it] }
        private fun font(v: JsonElement): Font = v.jsonArray.let {
            Font(it[0].jsonPrimitive.boolean,it[1].jsonPrimitive.boolean,Underline.entries[it[2].jsonPrimitive.int],
                it[3].jsonPrimitive.boolean,color(it[4]),color(it[5]),it[6].takeUnless { x->x==JsonNull }?.s())
        }
        private fun fontJson(f: Font) = j(JsonPrimitive(f.bold),JsonPrimitive(f.italic),JsonPrimitive(f.underline.ordinal),
            JsonPrimitive(f.strikeout),f.foreground?.let { JsonPrimitive(it.ordinal) } ?: JsonNull,
            f.background?.let { JsonPrimitive(it.ordinal) } ?: JsonNull,string(f.link))
        private fun attributed(v: JsonElement) = Attributed(v.jsonArray.map { a ->
            val xs=a.jsonArray;AttributedPart(xs[0].takeUnless { it==JsonNull }?.s(),font(xs[1]),raw(xs[2].s()))
        })
        private fun attributedJson(v: Attributed<A>) = JsonArray(v.parts.map { j(string(it.link),fontJson(it.font),encode(it.value)) })
        private fun style(v: JsonElement): StyleRequest = v.jsonArray.let { a -> when(a[0].s()) {
            "weight" -> StyleRequest.Weight(a[1].jsonPrimitive.boolean); "shape" -> StyleRequest.Shape(a[1].jsonPrimitive.boolean)
            "strikeout" -> StyleRequest.Strikeout(a[1].jsonPrimitive.boolean); "underline" -> StyleRequest.Underlined(Underline.entries[a[1].jsonPrimitive.int])
            "fg" -> StyleRequest.Foreground(color(a[1])); "bg" -> StyleRequest.Background(color(a[1])); else -> error(v)
        } }
        private fun styleJson(v: StyleRequest): JsonElement = when(v) {
            is StyleRequest.Weight -> tag("weight",JsonPrimitive(v.bold)); is StyleRequest.Shape -> tag("shape",JsonPrimitive(v.italic))
            is StyleRequest.Strikeout -> tag("strikeout",JsonPrimitive(v.struck)); is StyleRequest.Underlined -> tag("underline",JsonPrimitive(v.underline.ordinal))
            is StyleRequest.Foreground -> tag("fg",v.color?.let { JsonPrimitive(it.ordinal) } ?: JsonNull)
            is StyleRequest.Background -> tag("bg",v.color?.let { JsonPrimitive(it.ordinal) } ?: JsonNull)
        }
        fun doc(v: JsonElement): Doc<A> {
            if(v is JsonPrimitive && v.isString) return layout.literal(raw(v.content))
            val a=v.jsonArray
            fun d(i: Int)=doc(a[i])
            return when(a[0].s()) {
                "point-text" -> Doc.Text(a[1].n(),points(a[2].ps()))
                "empty" -> Doc.Empty; "space" -> Doc.BreakingSpace; "cr" -> Doc.CarriageReturn; "newline" -> Doc.NewLine
                "blanklines" -> Doc.BlankLines(a[1].n()); "raw","text" -> Doc.Text(a[1].n(),raw(a[2].s()))
                "vfill" -> Doc.VFill(a[1].n(),raw(a[2].s())); "literal" -> layout.literal(raw(a[1].s()))
                "concat" -> Doc.Concat(d(1),d(2)); "hsep" -> layout.hsep(a[1].jsonArray.map(::doc)); "vcat" -> layout.vcat(a[1].jsonArray.map(::doc))
                "prefix" -> Doc.Prefixed(a[1].s(),d(2)); "bold" -> layout.bold(d(1)); "link" -> Doc.Linked(a[1].s(),d(2))
                "before" -> Doc.BeforeNonBlank(d(1)); "after" -> Doc.AfterBreak(a[1].s()); "flush" -> Doc.Flush(d(1)); "styled" -> Doc.Styled(style(a[1]),d(2))
                "block" -> Doc.Block(a[1].n(),a[2].jsonArray.map(::attributed)); "cooked" -> Doc.CookedText(a[1].n(),attributed(a[2]))
                else -> error(v)
            }
        }
        fun docJson(d: Doc<A>): JsonElement = when(d) {
            is Doc.Text -> tag("text",JsonPrimitive(d.width),encode(d.value)); is Doc.Block -> tag("block",JsonPrimitive(d.width),JsonArray(d.lines.map(::attributedJson)))
            is Doc.VFill -> tag("vfill",JsonPrimitive(d.width),encode(d.value)); is Doc.CookedText -> tag("cooked",JsonPrimitive(d.width),attributedJson(d.value))
            is Doc.Prefixed -> tag("prefix",JsonPrimitive(d.prefix),docJson(d.doc)); is Doc.BeforeNonBlank -> tag("before",docJson(d.doc)); is Doc.Flush -> tag("flush",docJson(d.doc))
            is Doc.AfterBreak -> tag("after",JsonPrimitive(d.value)); is Doc.BlankLines -> tag("blanklines",JsonPrimitive(d.count)); is Doc.Concat -> tag("concat",docJson(d.left),docJson(d.right))
            is Doc.Styled -> tag("styled",styleJson(d.style),docJson(d.doc)); is Doc.Linked -> tag("link",JsonPrimitive(d.link),docJson(d.doc))
            Doc.Empty -> tag("empty"); Doc.NewLine -> tag("newline"); Doc.CarriageReturn -> tag("cr"); Doc.BreakingSpace -> tag("space")
        }
        fun value(v: JsonElement): Val<A> = v.jsonArray.let { a -> when(a[0].s()) {
            "simple" -> Val.SimpleVal(doc(a[1])); "null" -> Val.NullVal; "bool" -> Val.BoolVal(a[1].jsonPrimitive.boolean)
            "list" -> Val.ListVal(a[1].jsonArray.map(::value)); "map" -> Val.MapVal(Context(a[1].jsonArray.associate { it.jsonArray[0].s() to value(it.jsonArray[1]) }))
            else -> error(v)
        } }
        fun context(v: JsonElement) = (value(v) as Val.MapVal).context
        private fun valueJson(v: Val<A>): JsonElement = when(v) {
            Val.NullVal -> tag("null"); is Val.BoolVal -> tag("bool",JsonPrimitive(v.value)); is Val.SimpleVal -> tag("simple",docJson(v.doc))
            is Val.ListVal -> tag("list",JsonArray(v.values.map(::valueJson)))
            is Val.MapVal -> tag("map",JsonArray(v.context.entries.map { j(JsonPrimitive(it.key),valueJson(it.value)) }))
        }
        private fun observe(c: Context<A>) = buildJsonObject {
            val json=values.toJson(c)
            put("context",valueJson(Val.MapVal(c))); put("json",json); put("jsonBytes",WriterMetadata.contextJson(values,c))
            put("keys",JsonArray(c.entries.keys.map(::JsonPrimitive)))
        }
        fun run(op: String, v: JsonElement): JsonElement = when(op) {
            "metadata" -> v.jsonArray.let { a ->
                val options=WriterOptions(template=if(a[0].jsonPrimitive.boolean) Template.Empty else null,variables=text.context(a[1]),syntaxMap=emptyMap())
                val meta=PandocJson.decodeMeta(a[2]); val trace=mutableListOf<JsonElement>()
                fun bw(xs: List<Block>): Doc<A> { val n=trace.size;trace.add(j(JsonPrimitive("blocks"),JsonArray(xs.map(PandocJson::encodeBlock))));return layout.literal(raw("B$n"))+doc(a[3]) }
                fun iw(xs: List<Inline>): Doc<A> { val n=trace.size;trace.add(j(JsonPrimitive("inlines"),JsonArray(xs.map(PandocJson::encodeInline))));return layout.literal(raw("I$n"))+doc(a[4]) }
                val normal=WriterMetadata.metaToContext(options,values,::bw,::iw,meta); val normalTrace=trace.toList(); trace.clear()
                val prime=WriterMetadata.metaToContextPrime(::bw,::iw,meta)
                buildJsonObject { put("normal",observe(normal));put("prime",observe(prime));put("trace",JsonArray(normalTrace));put("primeTrace",JsonArray(trace));put("lang",string(WriterMetadata.getLang(options,meta))) }
            }
            "add" -> v.jsonArray.let { a -> observe(WriterMetadata.addVariablesToContext(WriterOptions(variables=text.context(a[0]),syntaxMap=emptyMap()),values,context(a[1]))) }
            "field" -> v.jsonArray.let { a ->
                val original=context(a[1]); val key=a[2].s(); val x=a[3].jsonArray
                fun <B> apply(value: B, converter: ToContext<A,B>): Context<A> = when(a[0].s()) {
                    "set" -> WriterMetadata.setField(key,value,original,converter)
                    "reset" -> WriterMetadata.resetField(key,value,original,converter)
                    "default" -> WriterMetadata.defField(key,value,original,converter)
                    else -> error(a[0])
                }
                val changed=when(x[0].s()) {
                    "val" -> apply(value(x[1]),values.value); "target" -> apply(raw(x[1].s()),values.target)
                    "doc" -> apply(doc(x[1]),values.document); "bool" -> apply(x[1].jsonPrimitive.boolean,values.boolean)
                    "list" -> apply(x[1].jsonArray.map(::value),values.list(values.value))
                    "map" -> apply(context(x[1]).entries,values.map(values.value)); "json" -> apply(x[1],values.json)
                    else -> error(x)
                }
                buildJsonObject {
                    put("changed",observe(changed))
                    put("target",WriterMetadata.getField(key,changed,values.targetDecoder)?.let(encode) ?: JsonNull)
                    put("targets",WriterMetadata.getField(key,changed,values.listDecoder(values.targetDecoder))?.let { JsonArray(it.map(encode)) } ?: JsonNull)
                    put("document",WriterMetadata.getField(key,changed,values.documentDecoder)?.let(::docJson) ?: JsonNull)
                    put("value",WriterMetadata.getField(key,changed,values.valueDecoder)?.let(::valueJson) ?: JsonNull)
                }
            }
            "html-dictionary" -> v.jsonArray.let { a ->
                val attr=PandocJson.decodeAttr(a[1]); val d=WriterHtml.tagWithAttrs(layout,raw(a[0].s()),attr,values.fromText)
                buildJsonObject { put("tag",docJson(d));put("attrs",docJson(WriterHtml.htmlAttrs(layout,attr,values.fromText)));put("render",encode(layout.render(null,d))) }
            }
            "unicode" -> {
                val ps=v.ps(); val s=TextChars.fromPoints(ps)
                val c=Context(mapOf(s to Val.SimpleVal(layout.literal(points(ps)))))
                val result=WriterMetadata.addVariablesToContext(WriterOptions(syntaxMap=emptyMap()),values,c)
                val d=checkNotNull(WriterMetadata.getField("meta-json",result,values.documentDecoder))
                buildJsonObject { put("doc",docJson(d));put("render",encode(layout.render(null,d))) }
            }
            "deep" -> {
                var mv: MetaValue=MetaValue.MetaString("leaf")
                repeat(v.jsonPrimitive.int) { mv=MetaValue.MetaMap(mapOf("child" to mv)) }
                val c=WriterMetadata.metaToContextPrime<A>({ Doc.Empty },{ layout.literal(raw("leaf")) },mapOf("root" to mv))
                val result=WriterMetadata.addVariablesToContext(WriterOptions(syntaxMap=emptyMap()),values,c)
                encode(checkNotNull(WriterMetadata.getField("meta-json",result,values.targetDecoder)))
            }
            else -> error(op)
        }
    }
    private fun lookup(v: JsonElement): JsonElement = v.jsonArray.let { a ->
        val key=a[0].s();val meta=PandocJson.decodeMeta(a[1])
        buildJsonObject { put("bool",WriterMetadata.lookupMetaBool(key,meta));put("blocks",JsonArray(WriterMetadata.lookupMetaBlocks(key,meta).map(PandocJson::encodeBlock)));put("inlines",JsonArray(WriterMetadata.lookupMetaInlines(key,meta).map(PandocJson::encodeInline)));put("string",WriterMetadata.lookupMetaString(key,meta)) }
    }
    private fun partial(v: JsonElement): JsonElement = when(v.s()) {
        "default-present" -> {
            val c=Context<String>(mapOf("key" to Val.BoolVal(false)))
            val result=WriterMetadata.defField("key",Unit,c,ToContext { error("new value") })
            // A single bool context has the same structural protocol for every target.
            tag("map",j(j(JsonPrimitive("key"),tag("bool",JsonPrimitive((result["key"] as Val.BoolVal).value)))))
        }
        "missing-field" -> WriterMetadata.getField("missing",Context<String>(mapOf("key" to Val.NullVal)),FromContext<String,Val<String>> { error("old value") })?.let { error("unexpected value") } ?: JsonNull
        "no-template" -> {
            val hidden=object: AbstractMap<String,MetaValue>() { override val entries: Set<Map.Entry<String,MetaValue>> get()=error("metadata") }
            val values=TemplateValues(DocLayout.Text,{ _:String -> error("variables") },{ it })
            val c=WriterMetadata.metaToContext(WriterOptions(syntaxMap=emptyMap()),values,{ error("blocks") },{ error("inlines") },hidden)
            tag("map",JsonArray(c.entries.map { error("unexpected entry") }))
        }
        else -> error(v)
    }
    private fun canonical(v: JsonElement): JsonElement = when(v) {
        is JsonObject -> JsonObject(v.mapValues { canonical(it.value) }); is JsonArray -> JsonArray(v.map(::canonical))
        is JsonPrimitive -> if(v.isString || v==JsonNull || v.booleanOrNull!=null) v else Json.parseToJsonElement(v.content.toBigDecimal().stripTrailingZeros().toPlainString())
    }
    @TestFactory fun originalObservations(): List<DynamicTest> =
        GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/writers/metadata.jsonl.gz"))).bufferedReader().use { reader ->
            reader.lineSequence().drop(1).mapIndexed { index,line ->
                // Parse at execution time: JUnit retains every dynamic test,
                // but need not retain millions of Unicode JsonPrimitive nodes.
                DynamicTest.dynamicTest("original metadata $index") {
                    val c=Json.parseToJsonElement(line).jsonObject;val op=c.getValue("op").s();val kind=c.getValue("target").s()
                    val v=c.getValue("input")
                    val actual=when(op) { "lookup" -> lookup(v); "partial" -> partial(v); else -> when(kind) { "text" -> text.run(op,v);"string" -> chars.run(op,v);"wrapped" -> wrapped.run(op,v);else -> error(kind) } }
                    assertEquals(canonical(c.getValue("expected")),canonical(actual),"case $index $kind $op")
                }
            }.toList()
        }
}
