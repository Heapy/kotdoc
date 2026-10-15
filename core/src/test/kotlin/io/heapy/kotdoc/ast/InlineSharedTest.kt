package io.heapy.kotdoc.ast

import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.logging.assertLogJsonEquals
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/** Expectations are from original Shared functions and original Walk instances. */
class InlineSharedTest {
    private fun inlines(v: JsonElement) = v.jsonArray.map(PandocJson::decodeInline)
    private fun output(xs: List<Inline>) = JsonArray(xs.map(PandocJson::encodeInline))
    private fun wrapper(kind: Int,a: Attr,xs: Inlines): Inlines = when(kind) {
        0->xs;1->Builder.emph(xs);2->Builder.spanWith(a,xs);3->Builder.linkWith(a,"target","title",xs)
        4->Builder.codeWith(a,DocumentShared.stringifyInlines(xs.values));5->Builder.space+xs+Builder.softbreak
        6->Inlines();7->Builder.str("replacement");8->Builder.doubleQuoted(xs);else->error(kind)
    }
    private fun genericStringify(xs: List<Inline>) = DocumentShared.stringifyBlocks(listOf(Block.Plain(xs)))
    private fun observe(a: Attr,xs: List<Inline>) = buildJsonObject {
        put("code",output(InlineShared.formatCode(a,Inlines(xs)).values))
        put("stringify",DocumentShared.stringifyInlines(xs));put("genericStringify",genericStringify(xs))
    }
    private fun raw(v: JsonElement) = CharacterString(v.jsonArray.map { it.jsonPrimitive.int }.toIntArray())
    private fun metadata(key: String,kind: Int,v: JsonElement,m: Map<String,MetaValue>): Map<String,MetaValue> = when(kind) {
        0->InlineShared.addMetaField(key,PandocJson.decodeMetaValue(v),m)
        1->InlineShared.addMetaField(key,v.jsonPrimitive.content,m)
        2->InlineShared.addMetaField(key,raw(v),m)
        3->InlineShared.addMetaField(key,v.jsonPrimitive.boolean,m)
        4->InlineShared.addMetaField(key,Inlines(inlines(v)),m)
        5->InlineShared.addMetaField(key,Blocks(v.jsonArray.map(PandocJson::decodeBlock)),m)
        6->InlineShared.addMetaField(key,v.jsonArray.map(PandocJson::decodeMetaValue),m) { it.toMetaValue { item->item } }
        7->InlineShared.addMetaField(key,PandocJson.decodeMeta(v),m) { it.toMetaValue { item->item } }
        else->error(kind)
    }
    private fun walk(v: JsonElement): JsonElement {
        val doc=PandocJson.decode(v);val before=mutableListOf<Int>();val after=mutableListOf<Int>()
        object : PandocVisitor() {
            override fun visitInlines(value: List<Inline>) { before.add(value.size) }
            override fun transformInlines(value: List<Inline>): List<Inline> { after.add(value.size);return value }
        }.walk(doc)
        val changed=object : PandocVisitor() {
            override fun transformInline(value: Inline) = if(value is Inline.Str)value.copy(text=value.text+"!") else value
        }.walk(doc)
        val changedLists=object : PandocVisitor() {
            override fun transformInlines(value: List<Inline>) = value+Inline.Str("!")
        }.walk(doc)
        return buildJsonObject {
            put("before",JsonArray(before.map(::JsonPrimitive)));put("after",JsonArray(after.map(::JsonPrimitive)))
            put("inline",PandocJson.toJson(changed));put("lists",PandocJson.toJson(changedLists))
        }
    }
    private fun deep(kind: Int,depth: Int): JsonElement {
        var value: Inline=Inline.Str("leaf")
        for(i in depth downTo 1)value=when(kind) {
            0->Inline.Span(Attr(identifier=i.toString()),listOf(value))
            1->if(i%2==0)Inline.Note(listOf(Block.Para(listOf(value)))) else Inline.Emph(listOf(value))
            2->Inline.Quoted(QuoteType.DoubleQuote,listOf(value));else->error(kind)
        }
        val input=listOf(value);var xs=InlineShared.formatCode(Attr(),Inlines(input)).values;val path=mutableListOf<Int>()
        while(xs.size==1) {
            val next=xs.single()
            when {
                next is Inline.Span->{path.add(0);xs=next.content}
                next is Inline.Emph->{path.add(1);xs=next.content}
                next is Inline.Note && next.blocks.size==1 && next.blocks.single() is Block.Para->{path.add(2);xs=(next.blocks.single() as Block.Para).content}
                else->break
            }
        }
        return buildJsonObject {
            put("code",JsonArray(listOf(JsonArray(path.map(::JsonPrimitive)),output(xs))))
            put("stringify",DocumentShared.stringifyInlines(input))
        }
    }
    private fun wire(v: JsonElement): JsonElement = when(v) {
        JsonNull->JsonArray(listOf(JsonPrimitive(0)))
        is JsonPrimitive->if(v.isString)JsonArray(listOf(JsonPrimitive(3),JsonArray(v.content.codePoints().toArray().map(::JsonPrimitive))))
            else JsonArray(listOf(JsonPrimitive(if(v.booleanOrNull!=null)1 else 2),v))
        is JsonArray->JsonArray(listOf(JsonPrimitive(4),JsonArray(v.map(::wire))))
        is JsonObject->JsonArray(listOf(JsonPrimitive(5),JsonArray(v.toSortedMap(UnicodeTextOrder).map { (k,x)->JsonArray(listOf(JsonArray(k.codePoints().toArray().map(::JsonPrimitive)),wire(x))) })))
    }
    private fun unicode(start: Int,count: Int): JsonElement {
        val digest=MessageDigest.getInstance("SHA-256");val prefix=ByteArray(4)
        for(cp in start..<start+count) {
            val t=if(cp in 0xd800..0xdfff)"\ufffd" else String(Character.toChars(cp));val str=Inline.Str(t)
            val values=JsonArray(listOf(
                output(Builder.text(t).values),JsonPrimitive(DocumentShared.stringifyInlines(listOf(Inline.Quoted(QuoteType.DoubleQuote,listOf(str))))),
                output(InlineShared.formatCode(Attr(),Inlines(listOf(str))).values),
                output(InlineShared.extractSpaces(Builder::emph,Inlines(listOf(Inline.Space,str,Inline.SoftBreak))).values),
                PandocJson.encodeMeta(InlineShared.addMetaField("k",CharacterString(intArrayOf(cp)),mapOf("k" to MetaValue.MetaList(emptyList()))))
            ))
            val bytes=wire(values).toString().toByteArray(Charsets.UTF_8);val n=bytes.size
            prefix[0]=(n ushr 24).toByte();prefix[1]=(n ushr 16).toByte();prefix[2]=(n ushr 8).toByte();prefix[3]=n.toByte()
            digest.update(prefix);digest.update(bytes)
        }
        return JsonPrimitive(digest.digest().toHexString())
    }
    private fun response(v: JsonElement): JsonElement {
        val x=v.jsonArray
        fun n(i: Int)=x[i].jsonPrimitive.int
        return when(x[0].jsonPrimitive.content) {
            "span-tags"->JsonArray(InlineShared.htmlSpanLikeElements.map(::JsonPrimitive))
            "inlines"->observe(PandocJson.decodeAttr(x[1]),inlines(x[2]))
            "extract"->output(InlineShared.extractSpaces({ wrapper(n(1),PandocJson.decodeAttr(x[2]),it) },Inlines(inlines(x[3]))).values)
            "meta"->PandocJson.encodeMeta(metadata(x[1].jsonPrimitive.content,n(2),x[3],PandocJson.decodeMeta(x[4])))
            "walk"->walk(x[1])
            "property"->{val xs=inlines(x[1]);JsonPrimitive(DocumentShared.stringifyInlines(xs)==genericStringify(xs))}
            "deep"->deep(n(1),n(2))
            "unicode"->unicode(n(1),n(2))
            else->error(v)
        }
    }
    @TestFactory fun originalObservations(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/shared-inline/data.jsonl.gz"))).bufferedReader().use { reader->
        reader.readLine();reader.lineSequence().mapIndexed { index,line->var transport: String?=line
            DynamicTest.dynamicTest("original Shared $index") {
                val c=Json.parseToJsonElement(checkNotNull(transport)).jsonObject;transport=null
                assertLogJsonEquals(c.getValue("expected"),response(c.getValue("request")),"case $index: ${c.getValue("request").jsonArray.take(2)}")
            }
        }.toList()
    }
    @Test fun immutableSpanSetAndMetaPreservation() {
        assertThrows(UnsupportedOperationException::class.java) { (InlineShared.htmlSpanLikeElements as MutableSet<String>).clear() }
        val old=MetaValue.MetaList(listOf(MetaValue.MetaString("old")));val original=mapOf("k" to old)
        val added=InlineShared.addMetaField("k",MetaValue.MetaList(listOf(MetaValue.MetaBool(true))),original)
        assertSame(old,original["k"]);assertEquals(1,old.values.size);assertEquals(2,(added.getValue("k") as MetaValue.MetaList).values.size)
        var calls=0
        InlineShared.extractSpaces({calls++;it},Inlines(listOf(Inline.Space)))
        assertEquals(1,calls)
    }
}
