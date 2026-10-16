package io.heapy.kotdoc.resources

import com.sun.jna.Memory
import com.sun.jna.Platform
import com.sun.jna.Pointer
import io.heapy.kotdoc.errors.GhcIOException
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream

private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content
private fun JsonObject.number(key: String, default: Int = 0) = get(key)?.jsonPrimitive?.long?.toInt() ?: default
private fun JsonObject.flag(key: String, default: Boolean = false) = get(key)?.jsonPrimitive?.boolean ?: default
private fun JsonObject.numbers(key: String) = getValue(key).jsonArray.map { it.jsonPrimitive.long.toInt() }.toIntArray()
private fun ints(values: Iterable<Int>) = JsonArray(values.map(::JsonPrimitive))
private fun points(values: IntArray) = JsonArray(values.map { JsonPrimitive(it.toUInt().toLong()) })
private class WinFailure(val location: String, val code: UInt) : RuntimeException()

/** Same controlled effects as CodePageEffects.hs. GHC's algorithms are not
 * implemented here: production code must produce every cursor and call trace. */
private class CodePageEffects(private var config: JsonObject) : GhcCodePageApi {
    val trace = mutableListOf<JsonElement>()
    fun setOption(key: String, value: JsonElement) { config = buildJsonObject { config.forEach { (k, v) -> put(k, v) }; put(key, value) } }
    private var error = 0u
    private fun record(action: JsonObjectBuilder.() -> Unit) { trace.add(buildJsonObject(action)) }
    override fun getConsoleCP(): UInt = config.number("console").toUInt().also { result -> record { put("api", "GetConsoleCP"); put("result", result.toLong()) } }
    override fun getACP(): UInt = config.number("acp", 1252).toUInt().also { result -> record { put("api", "GetACP"); put("result", result.toLong()) } }
    override fun getCPInfo(cp: UInt, output: Pointer): Boolean {
        val max = config.number("max", 2).toUInt(); error = config.number("infoError").toUInt()
        GhcCpInfo(max, intArrayOf(63, 0), intArrayOf(129, 132) + IntArray(10)).poke(output)
        record { put("api", "GetCPInfo"); put("cp", cp.toLong()); put("max", max.toLong()); put("error", error.toLong()) }
        return error == 0u
    }
    override fun getLastError(): UInt = error.also { result -> record { put("api", "GetLastError"); put("result", result.toLong()) } }
    override fun failWith(location: String, error: UInt): Nothing = throw WinFailure(location, error)
    override fun isDBCSLeadByte(cp: UInt, byte: Int): Boolean = (byte in 129..132).also { result -> record {
        put("api", "IsDBCSLeadByteEx"); put("cp", cp.toLong()); put("byte", byte); put("result", result)
    } }
    private fun decode(bytes: IntArray): List<Int>? = buildList {
        var i = 0
        while (i < bytes.size) {
            val x = bytes[i++]
            when {
                x < 128 -> add(x)
                x == 254 -> add(0x20ac)
                x == 253 -> add(0xd800)
                x in 129..132 -> {
                    if (i >= bytes.size || bytes[i] !in 64..127) return null
                    val y = bytes[i++] - 64
                    when (x) { 129 -> add(0x400 + y); 130 -> { add(0xd83d); add(0xde00 + y) }; 131 -> add(0x8000 + y); else -> { add(0xdc00); add(65) } }
                }
                else -> return null
            }
        }
    }
    override fun multiByteToWideChar(cp: UInt, flags: UInt, input: Pointer, count: Int, output: Pointer, capacity: Int): Int {
        val xs = IntArray(count) { input.getByte(it.toLong()).toInt() and 255 }
        val mapped = decode(xs)
        error = config.number("decodeError").toUInt()
        if (error == 0u) error = if (mapped == null) 1113u else if (mapped.size > capacity) 122u else 0u
        val out = if (error == 0u) checkNotNull(mapped) else emptyList()
        if (error != 0u && config.flag("dirty") && capacity > 0) output.setShort(0, 0xbeef.toShort())
        out.forEachIndexed { i, point -> output.setShort(i * 2L, point.toShort()) }
        record { put("api", "MultiByteToWideChar"); put("cp", cp.toLong()); put("flags", flags.toLong()); put("input", ints(xs.asIterable())); put("count", count); put("capacity", capacity); put("result", out.size); put("error", error.toLong()) }
        return out.size
    }
    override fun wideCharToMultiByte(cp: UInt, flags: UInt, input: Pointer, count: Int, output: Pointer, capacity: Int, defaultChar: Pointer?, usedDefault: Pointer): Int {
        val xs = IntArray(count) { input.getShort(it * 2L).toInt() and 65535 }; val initial = usedDefault.getInt(0) != 0
        var defaulted = false
        val mapped = buildList {
            var i = 0
            while (i < xs.size) {
                val x = xs[i++]
                when {
                    x < 128 -> add(x)
                    x == 0x20ac -> add(254)
                    x in 0x400..0x43f -> { add(129); add(x - 0x400 + 64) }
                    x in 0x8000..0x803f -> { add(131); add(x - 0x8000 + 64) }
                    x == 0xd83d && i < xs.size && xs[i] in 0xde00..0xde3f -> { add(130); add(xs[i++] - 0xde00 + 64) }
                    else -> { add(63); defaulted = true }
                }
            }
        }
        error = config.number("encodeError").toUInt()
        if (error == 0u && mapped.size > capacity) error = 122u
        val out = if (error == 0u) mapped else emptyList()
        if (error != 0u && config.flag("dirty") && capacity > 0) output.setByte(0, 0xee.toByte())
        out.forEachIndexed { i, b -> output.setByte(i.toLong(), b.toByte()) }; usedDefault.setInt(0, if (defaulted) 1 else 0)
        record { put("api", "WideCharToMultiByte"); put("cp", cp.toLong()); put("flags", flags.toLong()); put("input", ints(xs.asIterable())); put("count", count); put("capacity", capacity); put("defaultNull", defaultChar == null); put("initialDefault", initial); put("defaulted", defaulted); put("result", out.size); put("error", error.toLong()) }
        return out.size
    }
}

class GhcCodePageTest {
    private fun capture(action: () -> JsonElement): JsonElement = try { action() }
    catch (e: WinFailure) { buildJsonObject { put("error", "win32"); put("location", e.location); put("code", e.code.toLong()) } }
    catch (e: GhcIOException) { LookupTestMemory.ioError(e) }
    catch (e: IllegalStateException) { buildJsonObject { put("error", "fatal"); put("message", e.message!!.lineSequence().first().replace(Regex("0x[0-9a-f]+@buf"), "<ptr>@buf")) } }

    private fun whole(v: JsonObject, api: GhcCodePageApi): JsonElement {
        val mode = GhcCodingFailureMode.valueOf(v.text("mode"))
        val encoding = if (v.flag("named")) GhcTextEncoding.mkTextEncodingWithMode(v.text("name"), mode, api)
            else GhcCodePages.encoding(v.number("cp").toUInt(), mode, api)
        val values = v.getValue("batches").jsonArray.map { batch -> capture {
            val xs = batch.jsonArray.map { it.jsonPrimitive.long.toInt() }.toIntArray()
            when (v.text("direction")) {
                "decode" -> points(encoding.decode(xs.map(Int::toByte).toByteArray()))
                "encode" -> { val before = xs.copyOf(); val bytes = encoding.encode(xs, v.flag("terminate", true)); assertArrayEquals(before, xs); ints(bytes.map { it.toInt() and 255 }) }
                "representable" -> JsonArray(xs.map { JsonPrimitive(encoding.charIsRepresentable(it)) })
                else -> error(v)
            }
        } }
        return buildJsonObject { put("name", encoding.textEncodingName); put("values", JsonArray(values)) }
    }
    private fun step(v: JsonObject, api: GhcCodePageApi): JsonElement {
        val encoding = v.text("direction") == "encode"; val xs = v.numbers("input")
        val cp = v.number("cp").toUInt(); val size = v.number("capacity")
        return withCodeBuffer(if (encoding) 4 else 1, xs.size + 64) { backing ->
            val input = backing.copy(size = xs.size, left = v.number("left"), right = v.number("right"))
            xs.forEachIndexed(input::put)
            repeat(64) { input.put(xs.size + it, 90) }
            withCodeBuffer(if (encoding) 1 else 4, size, v.number("outLeft"), v.number("outRight"), true) { output ->
                repeat(size) { output.put(it, if (encoding) 0xcc else 0xcccc) }
                val table = GhcCodePages.tables[cp]
                val code = if (encoding) table?.let { it::encode } ?: GhcCodePageApiBuffers.encoder(cp, api)
                    else table?.let { it::decode } ?: GhcCodePageApiBuffers.decoder(cp, api)
                val result = code(input, output)
                fun metadata(b: GhcCodeBuffer) = ints(listOf(b.size, b.left, b.right))
                buildJsonObject { put("why", result.why.name); put("input", metadata(result.input)); put("output", metadata(result.output)); put("values", points(IntArray(result.output.elements) { result.output.read(it + result.output.left) })) }
            }
        }
    }
    private fun observe(v: JsonObject): JsonElement {
        val api = CodePageEffects(v)
        val value = capture { when (v.text("api")) {
            "utf16" -> {
                val encoding = v.text("direction") == "encode"; val xs = v.numbers("input"); val size = v.number("capacity")
                withCodeBuffer(if (encoding) 4 else 2, xs.size, v.number("left"), v.number("right")) { input ->
                    xs.forEachIndexed(input::put)
                    withCodeBuffer(if (encoding) 2 else 4, size, v.number("outLeft"), v.number("outRight"), true) { output ->
                        repeat(size) { output.put(it, 0xcccc) }
                        val step = if (encoding) GhcCodePageUtf16.encode(input, output) else GhcCodePageUtf16.decode(input, output)
                        fun metadata(b: GhcCodeBuffer) = ints(listOf(b.size, b.left, b.right))
                        buildJsonObject { put("why", step.why.name); put("input", metadata(step.input)); put("output", metadata(step.output)); put("values", points(IntArray(step.output.elements) { step.output.read(it + step.output.left) })) }
                    }
                }
            }
            "view" -> withCodeBuffer(2, v.number("size"), v.number("left"), v.number("right")) { buffer ->
                val input = buffer.copy(offset = 123u)
                val output = if (v.text("direction") == "byte") input.byteView() else input.copy(unit = 1).cwcharView()
                buildJsonObject { put("buffer", ints(listOf(output.size, output.left, output.right))); put("offset", output.offset.toLong()) }
            }
            "locale" -> {
                val locale = GhcLocaleCodePage(api)
                JsonArray(v.getValue("actions").jsonArray.map { action -> capture {
                    val a = action.jsonObject
                    when (a.text("op")) {
                        "console", "acp" -> { api.setOption(a.text("op"), a.getValue("value")); JsonNull }
                        "direct" -> JsonPrimitive(GhcCodePages.getCurrentCodePage(api).toLong())
                        "name" -> JsonPrimitive(locale.encoding(GhcCodingFailureMode.valueOf(a.text("mode"))).textEncodingName)
                        "decode" -> points(locale.encoding(GhcCodingFailureMode.valueOf(a.text("mode"))).decode(a.numbers("bytes").map(Int::toByte).toByteArray()))
                        else -> error(a)
                    }
                } })
            }
            "whole" -> whole(v, api)
            "step" -> step(v, api)
            "current" -> JsonPrimitive(GhcCodePages.getCurrentCodePage(api).toLong())
            "table" -> {
                val table = GhcCodePages.tables.getValue(v.number("cp").toUInt())
                buildJsonObject { put("decode", points(IntArray(256, table::decodePoint))); put("encode", ints((0..table.maximum).map { table.encodePoint(it) ?: -1 })); put("max", table.maximum) }
            }
            "cpinfo" -> Memory(20).use { memory ->
                GhcCpInfo(v.number("max").toUInt(), v.numbers("default"), v.numbers("lead")).poke(memory)
                val info = GhcCpInfo.peek(memory)
                buildJsonObject { put("size", GhcCpInfo.size); put("alignment", 4); put("max", info.maxCharSize.toLong()); put("default", ints(info.defaultChar.asIterable())); put("lead", ints(info.leadByte.asIterable())) }
            }
            else -> error(v)
        } }
        return buildJsonObject { put("value", value); put("trace", JsonArray(api.trace)) }
    }
    private fun nativeWindows(): List<DynamicTest> = if (!Platform.isWindows()) emptyList() else listOf(
        DynamicTest.dynamicTest("actual Kernel32 CP932 multibyte conversion and failure recovery") {
            val bytes = byteArrayOf(0x93.toByte(), 0xfa.toByte(), 0x96.toByte(), 0x7b, 0x8c.toByte(), 0xea.toByte())
            val points = "日本語".codePoints().toArray()
            val encoding = GhcTextEncoding.mkTextEncoding("CP932")
            assertEquals("CP932", encoding.textEncodingName)
            assertArrayEquals(points, encoding.decode(bytes)); assertArrayEquals(bytes, encoding.encode(points))
            assertThrows(GhcIOException::class.java) { encoding.decode(byteArrayOf(0x81.toByte())) }
            assertThrows(GhcIOException::class.java) { encoding.encode(intArrayOf(0x1f600)) }
            assertArrayEquals(intArrayOf(), GhcTextEncoding.mkTextEncoding("CP932//IGNORE").decode(byteArrayOf(0x81.toByte())))
            assertArrayEquals(intArrayOf(0xfffd), GhcTextEncoding.mkTextEncoding("CP932//TRANSLIT").decode(byteArrayOf(0x81.toByte())))
            assertArrayEquals(intArrayOf(0xdc81), GhcTextEncoding.mkTextEncoding("CP932//ROUNDTRIP").decode(byteArrayOf(0x81.toByte())))
            assertArrayEquals(byteArrayOf(63), GhcTextEncoding.mkTextEncoding("CP932//TRANSLIT").encode(intArrayOf(0x1f600)))
            assertArrayEquals(byteArrayOf(0xff.toByte()), GhcTextEncoding.mkTextEncoding("CP932//ROUNDTRIP").encode(intArrayOf(0xdcff)))
        },
        DynamicTest.dynamicTest("native invalid CP descriptor is lazy but empty CString opens and fails") {
            val encoding = GhcTextEncoding.mkTextEncoding("CP4294967295")
            assertEquals("CP4294967295", encoding.textEncodingName)
            for (encode in listOf(false, true)) {
                val error = assertThrows(GhcIOException::class.java) { if (encode) encoding.encode(intArrayOf()) else encoding.decode(byteArrayOf()) }
                assertEquals("GetCPInfo 4294967295".codePoints().toArray().toList(), error.error.location.points().toList())
                assertTrue(error.error.description.points().isNotEmpty())
            }
        },
        DynamicTest.dynamicTest("native initial locale filesystem foreign select one current Windows code page") {
            val cp = GhcCodePages.getCurrentCodePage(GhcNativeCodePageApi)
            val name = GhcCodePages.encoding(cp, GhcCodingFailureMode.Error).textEncodingName
            val initials = listOf(GhcEncodings.initLocaleEncoding, GhcEncodings.initFileSystemEncoding, GhcEncodings.initForeignEncoding)
            assertEquals(listOf(GhcCodingFailureMode.Error, GhcCodingFailureMode.Roundtrip, GhcCodingFailureMode.Ignore), initials.map { it.failureMode })
            initials.forEach { assertEquals(name, it.textEncodingName); assertArrayEquals(intArrayOf(65), it.decode(byteArrayOf(65))); assertArrayEquals(byteArrayOf(65), it.encode(intArrayOf(65))) }
        },
    )
    @TestFactory fun originalWindowsCodePages(): List<DynamicTest> =
        GZIPInputStream(javaClass.getResourceAsStream("/ghc-codepage/data.jsonl.gz")!!).bufferedReader().use { reader ->
            val metadata = Json.parseToJsonElement(reader.readLine()).jsonObject
            val cases = reader.lineSequence().map { Json.parseToJsonElement(it).jsonObject }.toList()
            assertEquals(metadata.number("cases"), cases.size)
            cases.mapIndexed { i, c -> DynamicTest.dynamicTest("$i ${c.text("group")}") { assertEquals(c.getValue("expected"), observe(c.getValue("request").jsonObject)) } } + nativeWindows()
        }
}
