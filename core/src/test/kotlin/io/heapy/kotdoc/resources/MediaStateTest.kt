package io.heapy.kotdoc.resources

import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.TextChars
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.util.zip.GZIPInputStream
import kotlin.test.*

class MediaStateTest {
    private fun JsonElement.s() = jsonPrimitive.content
    private fun chars(v: JsonElement) = CharacterString(v.jsonArray.map { it.jsonPrimitive.int }.toIntArray())
    private fun text(v: JsonElement) = TextChars.fromPoints(chars(v).points())
    private fun points(p: CharacterString) = JsonArray(p.points().map(::JsonPrimitive))
    private fun optional(v: JsonElement) = if (v == JsonNull) null else text(v)
    private fun bag(v: JsonElement) = v.jsonArray.fold(MediaBagValue.empty) { b, e -> e.jsonArray.let { b.insert(chars(it[0]), optional(it[1]), it[2].s().hexToByteArray()) } }
    private fun item(v: MediaItem) = buildJsonObject {
        put("mime",v.mimeType);put("path",points(v.pathRaw));put("bytes",v.contents.toHexString());put("shown",v.show())
    }
    private fun fields(b: MediaBagValue, qs: JsonElement) = buildJsonObject {
        put("directory",JsonArray(b.mediaDirectory().map { (p,m,n) -> buildJsonArray { add(points(p));add(m);add(n) } }))
        put("items",JsonArray(b.mediaItems().map { (p,m,bs) -> buildJsonArray { add(points(p));add(m);add(bs.toHexString()) } }))
        put("shown",b.show());put("lookups",JsonArray(qs.jsonArray.map { b[chars(it)]?.let(::item) ?: JsonNull }))
    }
    private fun shape(n: PersistentTextMap.Node<Long>?): JsonElement = if (n==null) JsonNull else buildJsonArray {
        add(n.size);add(n.key);add(n.value);add(shape(n.left));add(shape(n.right))
    }
    private fun run(a: JsonArray): JsonElement = when(a[0].s()) {
        "uri" -> {
            val raw=buildString { chars(a[1]).points().forEach(::appendCodePoint) }
            val parsed=MediaBagUri.parse(raw)
            buildJsonObject {
                put("known",MediaBagUri.isKnown(text(a[1])))
                put("parsed",parsed?.let { buildJsonArray { add(it.scheme+":");add(JsonArray(it.path.codePoints().toArray().map(::JsonPrimitive))) } } ?: JsonNull)
            }
        }
        "bag" -> fields(bag(a[1]),a[2])
        "delete" -> bag(a[1]).let { b -> buildJsonObject { put("before",fields(b,a[3]));put("after",fields(b.delete(chars(a[2])),a[3])) } }
        "union" -> {
            val x=bag(a[1]);val y=bag(a[2])
            buildJsonObject { put("left",fields(x,a[3]));put("right",fields(y,a[3]));put("combined",fields(x+y,a[3])) }
        }
        "times" -> fields(bag(a[1]).repeat(BigInteger(a[2].s())),a[3])
        "map" -> {
            var m=PersistentTextMap.empty<Long>();val saved=mutableListOf(m)
            for(op in a[1].jsonArray) {
                val o=op.jsonArray
                m=when(o[0].s()) {
                    "insert" -> m.insert(text(o[1]),o[2].jsonPrimitive.long)
                    "delete" -> m.delete(text(o[1]))
                    "union" -> m + o[1].jsonArray.fold(PersistentTextMap.empty<Long>()) { r,p -> p.jsonArray.let { r.insert(text(it[0]),it[1].jsonPrimitive.long) } }
                    else -> error(o[0])
                };saved.add(m)
            }
            JsonArray(saved.map { shape(it.root) })
        }
        "item" -> {
            val i=MediaItem(text(a[1]),chars(a[2]),a[3].s().hexToByteArray())
            buildJsonObject { put("item",item(i));put("shown",i.show(a[4].jsonPrimitive.long)) }
        }
        "compare" -> {
            fun make(v: JsonElement)=v.jsonArray.let { MediaItem(text(it[0]),chars(it[1]),it[2].s().hexToByteArray()) }
            val x=make(a[1]);val y=make(a[2])
            assertSame(if(x<=y) x else y,x.minimum(y));assertSame(if(x<=y) y else x,x.maximum(y))
            buildJsonObject {
                put("eq",x==y);put("compare",if(x<y) "LT" else if(x>y) "GT" else "EQ")
                put("lt",x<y);put("le",x<=y);put("gt",x>y);put("ge",x>=y);put("min",item(x.minimum(y)));put("max",item(x.maximum(y)))
            }
        }
        "demand" -> {
            val p=chars(a[1]);val b=MediaBagValue.empty.insertDeferred(p,optional(a[2])) { error("media payload") }
            when(a[3].s()) {
                "mime" -> b[p]?.mimeType?.let(::JsonPrimitive) ?: JsonNull
                "path" -> b[p]?.pathRaw?.let(::points) ?: JsonNull
                "delete" -> JsonPrimitive(b.delete(p).show())
                "directory" -> JsonPrimitive(b.mediaDirectory().joinToString(",","[","]") { (p,m,n) ->
                    "(${io.heapy.kotdoc.formats.HaskellString.quoteCodePoints(p.points())},${io.heapy.kotdoc.formats.HaskellString.quote(m)},$n)" })
                "show" -> JsonPrimitive(b.show())
                else -> error(a[3])
            }
        }
        else -> error(a[0])
    }
    @TestFactory
    fun originalObservations(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/media-state/data.jsonl.gz")))
        .bufferedReader().use { reader ->
            reader.readLine()
            reader.lineSequence().mapIndexed { i,line -> DynamicTest.dynamicTest("MediaBag state $i") {
                val c=Json.parseToJsonElement(line).jsonObject;val a=c.getValue("request").jsonArray
                val actual=try { run(a) } catch(e: IllegalStateException) { buildJsonObject { put("exception",e.message!!) } }
                    catch(e: IllegalArgumentException) { buildJsonObject { put("exception",e.message!!) } }
                assertEquals(c.getValue("expected"),actual,a.toString().take(500))
            } }.toList()
        }
    @Test
    fun snapshotsShareItemsAndDoNotForceSafePayloads() {
        val facade=MediaBag();facade.insert("a.png",null,byteArrayOf(1,2))
        val old=facade.snapshot();val oldItem=old["a.png"]
        facade.insert("b.png",null,byteArrayOf(3));facade.delete("a.png")
        assertSame(oldItem,old["a.png"]);assertNull(facade["a.png"]);assertEquals(1L,old.count)
        val other=MediaBag(old);other.clear();assertEquals(1L,old.count)
        var calls=0;val data=byteArrayOf(4,5)
        val delayed=old.insertDeferred(CharacterString("safe.bin".codePoints().toArray()),"application/unknown") { calls++;data }
        assertEquals(0,calls);assertEquals("safe.bin",delayed["safe.bin"]!!.path);assertEquals(0,calls)
        assertContentEquals(data,delayed["safe.bin"]!!.contents);assertEquals(1,calls)
        data[0]=99;assertContentEquals(byteArrayOf(4,5),delayed["safe.bin"]!!.contents);assertEquals(1,calls)
        val returned=delayed["safe.bin"]!!.contents;returned[0]=88;assertContentEquals(byteArrayOf(4,5),delayed["safe.bin"]!!.contents)
    }
}
