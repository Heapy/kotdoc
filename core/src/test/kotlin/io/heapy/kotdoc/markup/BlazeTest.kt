package io.heapy.kotdoc.markup

import io.heapy.kotdoc.layout.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.*

class BlazeTest {
    private class Probe(val reason: String) : RuntimeException(reason)
    private fun JsonElement.s()=jsonPrimitive.content
    private fun JsonElement.n()=jsonPrimitive.long
    private fun j(vararg x: JsonElement)=JsonArray(x.toList())
    private fun points(v: JsonElement)=CharacterString(v.jsonArray.map { it.jsonPrimitive.int }.toIntArray())
    private fun p(s: CharacterString)=JsonArray(s.points().map(::JsonPrimitive))
    private fun static(v: JsonElement): StaticString {
        if(v is JsonPrimitive) return StaticString.fromString(v.s())
        val a=v.jsonArray;val prefix=points(a[0])
        return StaticString({ tail -> when(a[3].s()) {
            "prefix" -> CharacterStringChars.append(prefix,tail.value)
            "constant" -> prefix
            "reverse" -> CharacterStringChars.append(prefix,CharacterString(tail.value.points().reversedArray()))
            "bomb" -> throw Probe("static function")
            else -> error(a)
        } },a[1].s().hexToByteArray(),a[2].s())
    }
    private fun choice(v: JsonElement): ChoiceString {
        val a=v.jsonArray
        return when(a[0].s()) {
            "static" -> ChoiceString.Static(static(a[1]))
            "string" -> ChoiceString.String(points(a[1]))
            "text" -> ChoiceString.Text(a[1].s())
            "bytes" -> ChoiceString.Bytes(a[1].s().hexToByteArray())
            "pre" -> ChoiceString.PreEscaped(choice(a[1]))
            "external" -> ChoiceString.External(choice(a[1]))
            "append" -> ChoiceString.Append(choice(a[1]),choice(a[2]))
            "empty" -> ChoiceString.Empty
            else -> error(a)
        }
    }
    private fun ret(v: JsonElement): Long = if(v is JsonPrimitive && v.isString && v.s()=="bomb") throw Probe("return value") else v.n()
    private fun function(v: JsonElement,x: Lazy<Long>): Long { val a=v.jsonArray;return when(a[0].s()) {
        "add" -> x.value+a[1].n();"const" -> ret(a[1]);"bomb" -> throw Probe("map value");else -> error(a)
    } }
    private fun node(v: JsonElement): MarkupM<Long> {
        val a=v.jsonArray
        return when(a[0].s()) {
            "chunks" -> {
                val chunks=a[2].jsonArray.asSequence()
                fun texts()=chunks.map { if(it.s()=="!bottom!") throw Probe("chunk value") else it.s() }
                fun bytes()=chunks.map { if(it.s()=="!bottom!") throw Probe("chunk value") else it.s().hexToByteArray() }
                val m=when(a[1].s()) {
                    "text" -> Blaze.lazyText(texts());"pre-text" -> Blaze.preEscapedLazyText(texts());"bytes" -> Blaze.unsafeLazyByteString(bytes())
                    "text-comment" -> Blaze.lazyTextComment(texts());"bytes-comment" -> Blaze.unsafeLazyByteStringComment(bytes())
                    "text-value" -> Blaze.content(Blaze.lazyTextValue(texts()).value)
                    "pre-text-value" -> Blaze.content(Blaze.preEscapedLazyTextValue(texts()).value)
                    "bytes-value" -> Blaze.content(Blaze.unsafeLazyByteStringValue(bytes()).value)
                    else -> error(a)
                }
                m.mapLazy { 0L }
            }
            "parent" -> MarkupM.Parent(static(a[1]),static(a[2]),static(a[3]),node(a[4]))
            "custom-parent" -> MarkupM.CustomParent(choice(a[1]),node(a[2]))
            "leaf" -> MarkupM.Leaf(static(a[1]),static(a[2]),static(a[3]),lazy { ret(a[4]) })
            "custom-leaf" -> MarkupM.CustomLeaf(choice(a[1]),a[2].jsonPrimitive.boolean,lazy { ret(a[3]) })
            "content" -> MarkupM.Content(choice(a[1]),lazy { ret(a[2]) })
            "comment" -> MarkupM.Comment(choice(a[1]),lazy { ret(a[2]) })
            "append" -> MarkupM.Append(node(a[1]),node(a[2]))
            "attribute" -> MarkupM.AddAttribute(static(a[1]),static(a[2]),choice(a[3]),node(a[4]))
            "custom-attribute" -> MarkupM.AddCustomAttribute(choice(a[1]),choice(a[2]),node(a[3]))
            "empty" -> pureMarkupLazy { ret(a[1]) }
            "map" -> node(a[2]).mapLazy { function(a[1],it) }
            "apply" -> node(a[2]).mapLazy { x -> { y: Lazy<Long> -> function(a[1],lazy { x.value+y.value }) } }.applyLazy(node(a[3]))
            "apply-ignore" -> node(a[1]).mapLazy { x -> { _: Lazy<Long> -> x.value } }.applyLazy(node(a[2]))
            "before" -> node(a[1]).before(node(a[2]))
            "then" -> node(a[1]).then(node(a[2]))
            "concat" -> a[1].jsonArray.map(::node).asReversed().fold(pureMarkupLazy { ret(a[2]) }) { tail,x -> MarkupM.Append(x,tail) }
            "contents" -> node(a[1]).contents()
            "external" -> node(a[1]).external()
            "bind" -> node(a[1]).flatMapLazy { x -> val n=x.value+a[2].n();MarkupM.Content(ChoiceString.String(CharacterStringChars.fromString(n.toString())),lazyOf(n)) }
            "bind-ignore" -> node(a[1]).flatMapLazy { node(a[2]) }
            else -> error(a)
        }
    }
    private fun observation(f: ()->JsonElement): JsonElement = try { f() } catch(e: Probe) { buildJsonObject { put("error",e.reason) } }
        catch(e: BlazeUtf8Exception) { buildJsonObject { put("error",e.message) } }
    private fun ss(s: StaticString)=j(JsonPrimitive(s.text),JsonPrimitive(s.utf8.toHexString()))
    private fun cs(s: ChoiceString): JsonElement=when(s) {
        is ChoiceString.Deferred -> cs(s.value.value)
        is ChoiceString.Static -> j(JsonPrimitive("static"),JsonPrimitive(s.value.text),JsonPrimitive(s.value.utf8.toHexString()))
        is ChoiceString.String -> j(JsonPrimitive("string"),p(s.value))
        is ChoiceString.Text -> j(JsonPrimitive("text"),JsonPrimitive(s.value))
        is ChoiceString.Bytes -> j(JsonPrimitive("bytes"),JsonPrimitive(s.bytes.toHexString()))
        is ChoiceString.PreEscaped -> j(JsonPrimitive("pre"),cs(s.value))
        is ChoiceString.External -> j(JsonPrimitive("external"),cs(s.value))
        is ChoiceString.Append -> j(JsonPrimitive("append"),cs(s.left),cs(s.right))
        ChoiceString.Empty -> j(JsonPrimitive("empty"))
    }
    private fun shape(n: MarkupM<*>): JsonElement=when(n) {
        is MarkupM.Parent -> j(JsonPrimitive("parent"),ss(n.tag),ss(n.open),ss(n.close),shape(n.content))
        is MarkupM.CustomParent -> j(JsonPrimitive("custom-parent"),cs(n.tag),shape(n.content))
        is MarkupM.Leaf -> j(JsonPrimitive("leaf"),ss(n.tag),ss(n.open),ss(n.close))
        is MarkupM.CustomLeaf -> j(JsonPrimitive("custom-leaf"),cs(n.tag),JsonPrimitive(n.close))
        is MarkupM.Content -> j(JsonPrimitive("content"),cs(n.content))
        is MarkupM.Comment -> j(JsonPrimitive("comment"),cs(n.comment))
        is MarkupM.Append -> j(JsonPrimitive("append"),shape(n.left),shape(n.right))
        is MarkupM.AddAttribute -> j(JsonPrimitive("attribute"),ss(n.rawKey),ss(n.key),cs(n.value),shape(n.content))
        is MarkupM.AddCustomAttribute -> j(JsonPrimitive("custom-attribute"),cs(n.key),cs(n.value),shape(n.content))
        is MarkupM.Empty -> j(JsonPrimitive("empty"))
        is MarkupM.Deferred -> shape(n.node.value)
    }
    private fun doc(d: Doc<String>): JsonElement=when(d) {
        is Doc.Text -> j(JsonPrimitive("text"),JsonPrimitive(d.width),JsonPrimitive(d.value))
        is Doc.Flush -> j(JsonPrimitive("flush"),doc(d.doc))
        Doc.BreakingSpace -> j(JsonPrimitive("space"))
        Doc.CarriageReturn -> j(JsonPrimitive("cr"))
        Doc.NewLine -> j(JsonPrimitive("newline"))
        is Doc.Concat -> j(JsonPrimitive("concat"),doc(d.left),doc(d.right))
        Doc.Empty -> j(JsonPrimitive("empty"))
        else -> error("Unexpected layout constructor")
    }
    private fun render(n: MarkupM<Long>): JsonElement=buildJsonObject {
        put("string",observation { p(BlazeRenderer.renderString(n)) })
        put("text",observation { JsonPrimitive(BlazeRenderer.renderText(n)) })
        put("utf8",observation { JsonPrimitive(BlazeRenderer.renderUtf8(n).toHexString()) })
        put("pretty",observation { p(BlazeRenderer.renderPretty(n)) })
        put("lenient",observation { JsonPrimitive(BlazeRenderer.renderTextLenient(n)) })
        put("value",observation { JsonPrimitive(n.markupValue()) })
        put("null",observation { JsonPrimitive(n.isEmpty()) })
        put("shape",observation { shape(n) })
        put("doc",observation { doc(BlazeLayout.layoutMarkup(n)) })
        put("layout",observation { JsonArray(listOf(null,0L,1L,12L,40L).map { width -> j(JsonPrimitive(DocLayout.Text.render(width,BlazeLayout.layoutMarkup(n)))) }) })
    }
    private fun observe(c: JsonObject): JsonElement {
        val v=c.getValue("input")
        return when(c.getValue("op").s()) {
            "render" -> render(node(v))
            "choice" -> buildJsonObject {
                val a=v.jsonArray;val s=choice(a[0])
                put("suffix",observation { p(BlazeRenderer.fromChoiceString(s,points(a[1]))) })
                put("render",render(MarkupM.Content(s,lazyOf(1))))
            }
            else -> error(c)
        }
    }
    @TestFactory fun originalBlazeCases(): List<DynamicTest> {
        val rows=GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/markup/blaze.jsonl.gz"))).bufferedReader().use { it.readLines().drop(1) }
        return rows.mapIndexed { i,raw -> DynamicTest.dynamicTest("native Blaze $i") {
            val c=Json.parseToJsonElement(raw).jsonObject
            assertEquals(c.getValue("expected"),observe(c),"case $i ${c.getValue("op")} input=${c.getValue("input")}")
        } }
    }
    @Test fun lazyValuesDeepTreesAndOwnership() {
        val source=byteArrayOf(65);val choice=ChoiceString.Bytes(source);source[0]=66;choice.bytes[0]=67
        assertContentEquals(byteArrayOf(65),choice.bytes)
        var calls=0
        val n=pureMarkupLazy<Long> { calls++;error("must not force") }
        assertEquals("",BlazeRenderer.renderText(n));assertTrue(n.isEmpty());assertEquals(0,calls)
        assertEquals(7,n.mapLazy { 7 }.markupValue())
        assertEquals("ok",BlazeRenderer.renderText(n.flatMapLazy { Blaze.text("ok") }))
        assertEquals(0,calls)
        var m: Markup=Blaze.text("x")
        repeat(10_000) { m=MarkupM.Parent(StaticString.fromString("p"),StaticString.fromString("<p"),StaticString.fromString("</p>"),m) }
        val expected="<p>".repeat(10_000)+"x"+"</p>".repeat(10_000)
        assertEquals(expected,BlazeRenderer.renderText(m));assertEquals(expected,CharacterStringChars.build(BlazeRenderer.renderString(m)))
        assertEquals("x",BlazeRenderer.renderText(m.contents()))
        assertEquals(expected,DocLayout.Text.render(null,BlazeLayout.layoutMarkup(m)))
        var conditionCalls=0
        assertSame(m,m.withIf(false) { conditionCalls++;error("unused") });assertEquals(0,conditionCalls)
    }
}
