// Original Pandoc CSS/XML/Shared observations, GPL-2.0-or-later; licenses/.
package io.heapy.kotdoc.writers

import io.heapy.kotdoc.ast.PandocJson
import io.heapy.kotdoc.ast.Alignment
import io.heapy.kotdoc.html.PandocCss
import io.heapy.kotdoc.layout.*
import io.heapy.kotdoc.xml.PandocXml as X
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals

class WriterHtmlTest {
    private fun array(vararg values: JsonElement) = JsonArray(values.toList())
    private fun string(s: String?) = s?.let(::JsonPrimitive) ?: JsonNull
    private fun points(v: JsonElement) = v.jsonArray.map { it.jsonPrimitive.int }.toIntArray()
    private fun text(v: JsonElement) = TextChars.fromPoints(points(v))
    private fun pairs(v: JsonElement) = v.jsonArray.map { it.jsonArray[0].jsonPrimitive.content to it.jsonArray[1].jsonPrimitive.content }
    private fun jsonPairs(v: List<Pair<String, String>>) = JsonArray(v.map { array(JsonPrimitive(it.first),JsonPrimitive(it.second)) })
    private fun strings(v: JsonElement) = v.jsonArray.map { it.jsonPrimitive.content }

    private fun <A> doc(layout: DocLayout<A>, value: (JsonElement) -> A, v: JsonElement): Doc<A> {
        val a = v.jsonArray
        return when (a[0].jsonPrimitive.content) {
            "literal" -> layout.literal(value(a[1]))
            "raw" -> Doc.Text(a[1].jsonPrimitive.long,value(a[2]))
            "concat" -> Doc.Concat(doc(layout,value,a[1]),doc(layout,value,a[2]))
            "prefix" -> Doc.Prefixed(a[1].jsonPrimitive.content,doc(layout,value,a[2]))
            "flush" -> Doc.Flush(doc(layout,value,a[1]))
            "before" -> Doc.BeforeNonBlank(doc(layout,value,a[1]))
            "after" -> Doc.AfterBreak(a[1].jsonPrimitive.content)
            "blank" -> Doc.BlankLines(a[1].jsonPrimitive.long)
            "space" -> Doc.BreakingSpace; "cr" -> Doc.CarriageReturn; "newline" -> Doc.NewLine; "empty" -> Doc.Empty
            else -> error("doc adapter")
        }
    }
    private fun <A> jsonDoc(d: Doc<A>, value: (A) -> JsonElement): JsonElement {
        fun node(key: String, vararg fields: JsonElement) = array(JsonPrimitive(key), *fields)
        return when (d) {
            is Doc.Text -> node("text",JsonPrimitive(d.width),value(d.value))
            is Doc.Prefixed -> node("prefix",JsonPrimitive(d.prefix),jsonDoc(d.doc,value))
            is Doc.Flush -> node("flush",jsonDoc(d.doc,value))
            is Doc.BeforeNonBlank -> node("before",jsonDoc(d.doc,value))
            is Doc.AfterBreak -> node("after",JsonPrimitive(d.value))
            is Doc.BlankLines -> node("blank",JsonPrimitive(d.count))
            is Doc.Concat -> node("concat",jsonDoc(d.left,value),jsonDoc(d.right,value))
            Doc.BreakingSpace -> node("space"); Doc.CarriageReturn -> node("cr"); Doc.NewLine -> node("newline"); Doc.Empty -> node("empty")
            else -> error("unexpected adapter document")
        }
    }
    private fun <A> observe(layout: DocLayout<A>, d: Doc<A>, value: (A) -> JsonElement) = buildJsonObject {
        put("doc",jsonDoc(d,value))
        put("renders",JsonArray(listOf(null,0L,1L,12L,40L).map { value(layout.render(it,d)) }))
    }
    private val textLayout = DocLayout(TextChars)
    private val stringLayout = DocLayout(CharacterStringChars)
    private fun textObservation(d: Doc<String>) = observe(textLayout,d,::JsonPrimitive)
    private fun stringObservation(d: Doc<CharacterString>) = observe(stringLayout,d) { JsonArray(it.points().map(::JsonPrimitive)) }

    private fun operation(op: String, v: JsonElement): JsonElement = when (op) {
        "registries" -> buildJsonObject {
            put("html4Attributes",JsonArray(X.html4Attributes.sorted().map(::JsonPrimitive)))
            put("html5Attributes",JsonArray(X.html5Attributes.sorted().map(::JsonPrimitive)))
            put("rdfaAttributes",JsonArray(X.rdfaAttributes.sorted().map(::JsonPrimitive)))
        }
        "css" -> v.jsonArray.let { a ->
            val p = strings(a[0]); val s = text(a[1])
            buildJsonObject {
                put("attributes",jsonPairs(PandocCss.cssAttributes(s)))
                put("picked",jsonPairs(PandocCss.pickStylesToKVs(p,s)))
                put("first",string(PandocCss.pickStyleAttrProps(p,s)))
            }
        }
        "style" -> v.jsonArray.let { a -> jsonPairs(WriterHtml.htmlAddStyle(pairs(array(a[0])).single(),pairs(a[1]))) }
        "alignment" -> string(WriterHtml.htmlAlignmentToString(Alignment.valueOf(v.jsonObject.getValue("t").jsonPrimitive.content)))
        "attrs" -> v.jsonArray.let { a ->
            val attr = PandocJson.decodeAttr(a[1])
            buildJsonObject {
                put("text",textObservation(WriterHtml.tagWithAttrs(textLayout,text(a[0]),attr)))
                put("string",stringObservation(WriterHtml.tagWithAttrs(stringLayout,CharacterString(points(a[0])),attr)))
                put("attributes",textObservation(WriterHtml.htmlAttrs(textLayout,attr)))
            }
        }
        "xml-tag" -> v.jsonArray.let { a ->
            val name = text(a[0]); val attrs = pairs(a[1])
            fun <A> tags(layout: DocLayout<A>, d: Doc<A>) = listOf(
                X.inTags(layout,false,name,attrs,d), X.inTags(layout,true,name,attrs,d),
                X.selfClosingTag(layout,name,attrs), X.inTagsSimple(layout,name,d), X.inTagsIndented(layout,name,d),
            )
            buildJsonObject {
                put("text",JsonArray(tags(textLayout,doc(textLayout,::text,a[2])).map(::textObservation)))
                put("string",JsonArray(tags(stringLayout,doc(stringLayout,{ CharacterString(points(it)) },a[2])).map(::stringObservation)))
            }
        }
        "xml-text" -> text(v).let { s -> buildJsonObject {
            put("escaped",X.escapeStringForXML(s)); put("ncname",X.escapeNCName(s))
            put("entities",X.toEntities(s)); put("html5",X.toHtml5Entities(s))
            put("decoded",X.fromEntities(s)); put("lookup",string(X.lookupEntity(s)))
        } }
        "unicode" -> points(v).let { ps ->
            val s = TextChars.fromPoints(ps)
            buildJsonObject {
                put("escaped",X.escapeStringForXML(s))
                put("char",ps.joinToString("") { X.escapeCharForXML(it) })
                put("start",ps.joinToString("") { X.escapeNCName(TextChars.fromPoints(intArrayOf(it))) })
                put("body",ps.joinToString("") { X.escapeNCName("a" + TextChars.fromPoints(intArrayOf(it))).drop(1) })
                put("entities",X.toEntities(s)); put("html5",X.toHtml5Entities(s))
                put("numeric",ps.joinToString("") { checkNotNull(X.lookupEntity("#$it;")) })
            }
        }
        else -> error("operation $op")
    }
    @TestFactory fun originalObservations(): List<DynamicTest> =
        GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/writers/html-shared.jsonl.gz"))).bufferedReader().use { f ->
            f.lineSequence().drop(1).mapIndexed { index, line ->
                val c = Json.parseToJsonElement(line).jsonObject
                val op = c.getValue("op").jsonPrimitive.content
                DynamicTest.dynamicTest("original writer HTML/XML/CSS $index $op") {
                    assertEquals(c.getValue("expected"),operation(op,c.getValue("input")),"case $index $op")
                }
            }.toList()
        }
}
