package io.heapy.kotdoc.resources

import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import io.heapy.kotdoc.http.*
import io.heapy.kotdoc.errors.GhcIOException
import io.heapy.kotdoc.errors.GhcIoError
import io.heapy.kotdoc.errors.GhcPosixErrno
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import io.heapy.kotdoc.layout.CharacterString
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream

internal object EncodingGlobalsObserve {
    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content
    private fun JsonObject.points(key: String) = getValue(key).jsonArray.map { it.jsonPrimitive.int }.toIntArray()
    fun raise(s: String): Nothing = if (s.startsWith("io:")) throw GhcIOException(GhcIoError.userError(s)) else throw AssertionError(s)
    private fun capture(action: () -> JsonElement): JsonElement = try { action() }
        catch (e: GhcIOException) { LookupTestMemory.ioError(e) }
        catch (e: AssertionError) { buildJsonObject { put("error", "fatal"); put("message", e.message!!) } }
    private fun points(xs: IntArray) = JsonArray(xs.map(::JsonPrimitive))
    private fun bytes(xs: ByteArray) = JsonArray(xs.map { JsonPrimitive(it.toInt() and 255) })
    fun controlled(v: JsonObject): JsonElement {
        val events = mutableListOf<String>()
        var charset = v.text("charset")
        val charsetName by ghcEncodingLazy {
            events.add("charset"); v["charsetFailure"]?.jsonPrimitive?.content?.let(::raise); charset
        }
        fun initial(slot: String, mode: GhcCodingFailureMode): GhcTextEncoding {
            events.add("initial:$slot"); return GhcTextEncoding.mkTextEncodingWithMode(charsetName, mode)
        }
        val state = GhcEncodingState({ initial("locale", GhcCodingFailureMode.Error) },
            { initial("filesystem", GhcCodingFailureMode.Roundtrip) }, { initial("foreign", GhcCodingFailureMode.Ignore) }, v.getValue("windows").jsonPrimitive.boolean)
        val saved = mutableMapOf<String, GhcTextEncoding>()
        fun get(slot: String): GhcTextEncoding = when (slot) {
            "locale" -> state.getLocaleEncoding(); "filesystem" -> state.getFileSystemEncoding(); "foreign" -> state.getForeignEncoding(); else -> error(slot)
        }
        fun init(slot: String): GhcTextEncoding = when (slot) {
            "locale" -> state.initLocaleEncoding; "filesystem" -> state.initFileSystemEncoding; "foreign" -> state.initForeignEncoding; else -> error(slot)
        }
        fun set(slot: String, enc: GhcTextEncoding) { when (slot) {
            "locale" -> state.setLocaleEncoding(enc); "filesystem" -> state.setFileSystemEncoding(enc); "foreign" -> state.setForeignEncoding(enc); else -> error(slot)
        } }
        fun select(a: JsonObject): GhcTextEncoding = a["saved"]?.jsonPrimitive?.content?.let(saved::getValue) ?: when (a.text("source")) {
            "current" -> get(a.text("slot")); "initial" -> init(a.text("slot")); "argv" -> state.argvEncoding(); else -> error(a)
        }
        val results = v.getValue("actions").jsonArray.map { value -> capture {
            val a = value.jsonObject
            when (a.text("op")) {
                "read" -> { saved[a.text("key")] = select(a); JsonNull }
                "install" -> { set(a.text("slot"), GhcTextEncoding.deferred {
                    events.add("installed:${a.text("tag")}"); a["failure"]?.jsonPrimitive?.content?.let(::raise)
                    GhcTextEncoding.mkTextEncoding(a.text("encoding"))
                }); JsonNull }
                "restoreInitial" -> { set(a.text("slot"), init(a.text("slot"))); JsonNull }
                "charset" -> { charset = a.text("value"); JsonNull }
                "name" -> JsonPrimitive(select(a).textEncodingName)
                "decode" -> points(select(a).decode(a.points("bytes").map(Int::toByte).toByteArray()))
                "encode" -> bytes(select(a).encode(a.points("points"), a.getValue("terminate").jsonPrimitive.boolean))
                "representable" -> JsonPrimitive(select(a).charIsRepresentable(a.getValue("point").jsonPrimitive.int))
                else -> error(a)
            }
        } }
        return buildJsonObject { put("results", JsonArray(results)); put("events", JsonArray(events.map(::JsonPrimitive))) }
    }
    private fun get(slot: String): GhcTextEncoding = when (slot) {
        "locale" -> GhcEncodings.getLocaleEncoding(); "filesystem" -> GhcEncodings.getFileSystemEncoding(); "foreign" -> GhcEncodings.getForeignEncoding(); else -> error(slot)
    }
    private fun set(slot: String, enc: GhcTextEncoding) { when (slot) {
        "locale" -> GhcEncodings.setLocaleEncoding(enc); "filesystem" -> GhcEncodings.setFileSystemEncoding(enc); "foreign" -> GhcEncodings.setForeignEncoding(enc); else -> error(slot)
    } }
    fun resolverBindings(): JsonElement {
        val old = GhcEncodings.getForeignEncoding()
        val abi = NetworkSocketAbi.currentPosix
        val memory = NetworkSocketMemory(abi)
        val address = NetworkSocketAddress.Inet(443u, 0x0100007fu)
        try {
            return Memory(abi.info("size").toLong()).use { info -> Memory(memory.size(address).toLong()).use { socket -> Memory(2).use { canon ->
                canon.setByte(0, 255.toByte()); canon.setByte(1, 0)
                memory.poke(socket, address)
                memory.pokeInfo(info, NetworkAddressInfo.defaultHints(abi).copy(family = abi.family("AF_INET"), address = address))
                info.setPointer(abi.info("addr").toLong(), socket); info.setPointer(abi.info("canon").toLong(), canon)
                var sent = byteArrayOf(); var changeDuringQuery = false; var frees = 0
                val functions = object : NetworkNameFunctions {
                    override fun getAddrInfo(host: Pointer?, service: Pointer?, hints: Pointer?, result: PointerByReference): Int {
                        sent = host!!.getByteArray(0, host.indexOf(0, 0).toInt())
                        if (changeDuringQuery) GhcEncodings.setForeignEncoding(GhcTextEncoding.mkTextEncoding("ISO-8859-1"))
                        result.value = info; return 0
                    }
                    override fun freeAddrInfo(pointer: Pointer?) { check(pointer == info); frees++ }
                    override fun gaiStrError(code: Int): Pointer = error("unused")
                }
                // Construct both consumers before any writes. Their defaults must
                // fetch the encoding again for each independent CString action.
                val resolver = NetworkNameResolver(abi, functions, initializeSockets = {}, darwin = Platform.isMac())
                val rows = mutableListOf<JsonElement>()
                for (name in listOf("UTF-8//IGNORE", "ISO-8859-1//IGNORE", "ASCII//TRANSLIT", "ASCII//TRANSLIT")) {
                    changeDuringQuery = rows.size == 3
                    GhcEncodings.setForeignEncoding(GhcTextEncoding.mkTextEncoding(name))
                    val result = resolver.getAddrInfo(null, CharacterString(intArrayOf(255, 0xdcff)), null).single()
                    rows.add(JsonArray(listOf(bytes(sent), points(result.canonicalName!!.points()))))
                }
                GhcEncodings.setForeignEncoding(GhcTextEncoding.mkTextEncoding("ASCII//IGNORE"))
                val first = memory.peekInfo(info).canonicalName!!.points()
                GhcEncodings.setForeignEncoding(GhcTextEncoding.mkTextEncoding("ISO-8859-1"))
                val second = memory.peekInfo(info).canonicalName!!.points()
                buildJsonObject { put("resolver", JsonArray(rows)); put("memory", JsonArray(listOf(points(first), points(second)))); put("frees", frees) }
            } } }
        } finally { GhcEncodings.setForeignEncoding(old) }
    }
    fun windowsWide(): JsonElement {
        val old = listOf("locale", "filesystem", "foreign").associateWith(::get)
        val poison = GhcTextEncoding.deferred { throw AssertionError("wide API must not demand byte encodings") }
        val api = Native.load("Kernel32", EnvironmentTestKernel::class.java)
        val key = "KOTDOC_WIDE_${UUID.randomUUID().toString().replace("-", "")}"; val value = "Русский"
        try {
            old.keys.forEach { set(it, poison) }
            check(api.SetEnvironmentVariableW(com.sun.jna.WString(key), com.sun.jna.WString(value)) != 0)
            val lookup = GhcEnvironmentLookup.getEnv(CharacterString(key.codePoints().toArray()))
            val environment = GhcEnvironment.getEnvironment().first { it.first == key }.second
            val argv = GhcEncodings.argvEncoding().decode(byteArrayOf(0xc3.toByte(), 0xbf.toByte()))
            return buildJsonObject {
                put("lookup", points(lookup.points())); put("environment", environment); put("argv", points(argv))
                put("settingsUnchanged", old.keys.all { get(it) === poison })
            }
        } finally {
            api.SetEnvironmentVariableW(com.sun.jna.WString(key), null)
            old.forEach { (slot, enc) -> set(slot, enc) }
        }
    }
    /** Called only by the child process that owns its environ and global refs. */
    fun native(v: JsonObject): JsonElement {
        val old = listOf("locale", "filesystem", "foreign").associateWith(::get)
        val libc = NativeLibrary.getInstance(Platform.C_LIBRARY_NAME)
        val address = if (Platform.isMac()) libc.getFunction("_NSGetEnviron").invokePointer(emptyArray()) else libc.getGlobalVariableAddress("environ")
        val previous = address.getPointer(0)
        val records = v.getValue("records").takeUnless { it is JsonNull }?.jsonArray?.map { it.jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray() }
        return EnvironmentTestMemory.records(records) { block ->
            try {
                address.setPointer(0, block)
                val results = v.getValue("actions").jsonArray.map { value -> capture {
                    val a = value.jsonObject
                    when (a.text("op")) {
                        "set" -> { set(a.text("slot"), GhcTextEncoding.mkTextEncoding(a.text("encoding"))); JsonNull }
                        "setDeferred" -> { set(a.text("slot"), GhcTextEncoding.deferred {
                            a["failure"]?.jsonPrimitive?.content?.let(::raise)
                            if (a["swapSecondWithThird"]?.jsonPrimitive?.boolean == true) {
                                val current = address.getPointer(0)
                                current.setPointer(Native.POINTER_SIZE.toLong(), current.getPointer(Native.POINTER_SIZE * 2L))
                            }
                            a["during"]?.jsonPrimitive?.content?.let { set(a.text("slot"), GhcTextEncoding.mkTextEncoding(it)) }
                            GhcTextEncoding.mkTextEncoding(a.text("encoding"))
                        }); JsonNull }
                        "name" -> JsonPrimitive(get(a.text("slot")).textEncodingName)
                        "environment" -> EnvironmentTestMemory.rawPairs(GhcEnvironment.getEnvironmentCodePoints())
                        "lookup" -> GhcEnvironmentLookup.lookupEnv(CharacterString(a.points("name")))?.let { points(it.points()) } ?: JsonNull
                        "errno" -> LookupTestMemory.ioError(GhcIOException(GhcPosixErrno.toError(Chars.fromString("global-errno"), a.getValue("code").jsonPrimitive.int)))
                        "foreignEncode" -> bytes(GhcForeignCString.currentPosix().encode(a.points("points"), false))
                        "foreignDecode" -> points(GhcForeignCString.currentPosix().decode(a.points("bytes").map(Int::toByte).toByteArray()))
                        else -> error(a)
                    }
                } }
                buildJsonObject { put("results", JsonArray(results)) }
            } finally { address.setPointer(0, previous); old.forEach { (slot, enc) -> set(slot, enc) } }
        }
    }
}

object GhcEncodingGlobalsChild {
    @JvmStatic fun main(args: Array<String>) { System.`in`.bufferedReader().lineSequence().forEach { line ->
        val request = Json.parseToJsonElement(line).jsonObject
        println(when (request["api"]?.jsonPrimitive?.content) {
            "wide" -> EncodingGlobalsObserve.windowsWide()
            "resolverBindings" -> EncodingGlobalsObserve.resolverBindings()
            else -> EncodingGlobalsObserve.native(request)
        })
    } }
}

class GhcEncodingGlobalsTest {
    private fun child(requests: List<JsonObject>): List<JsonElement> {
        val input = Files.createTempFile("kotdoc-encoding-globals-in-", ".jsonl")
        val output = Files.createTempFile("kotdoc-encoding-globals-out-", ".jsonl")
        try {
            Files.writeString(input, requests.joinToString("\n", postfix = "\n"))
            val locations = listOf(GhcEncodingGlobalsChild::class.java, GhcEncodings::class.java, JsonElement::class.java,
                kotlinx.serialization.KSerializer::class.java, kotlin.Unit::class.java, Native::class.java)
                .map { Path.of(it.protectionDomain.codeSource.location.toURI()).toString() }.toMutableList()
            System.getProperty("kotdoc.root")?.let { locations.add(Path.of(it, "core/src/main/resources").toString()) }
            val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
            val p = ProcessBuilder(java, "-cp", locations.distinct().joinToString(File.pathSeparator), GhcEncodingGlobalsChild::class.java.name)
                .redirectInput(input.toFile()).redirectOutput(output.toFile()).redirectErrorStream(true).start()
            try {
                assertTrue(p.waitFor(45, TimeUnit.SECONDS), "isolated encoding observer timed out")
                val text = Files.readString(output)
                assertEquals(0, p.exitValue(), text)
                return text.lineSequence().filter(String::isNotBlank).map(Json::parseToJsonElement).toList().also { assertEquals(requests.size, it.size) }
            } finally { if (p.isAlive) { p.destroyForcibly(); p.waitFor(3, TimeUnit.SECONDS) } }
        } finally { Files.deleteIfExists(input); Files.deleteIfExists(output) }
    }
    @TestFactory fun originalEncodingGlobals(): List<DynamicTest> =
        GZIPInputStream(javaClass.getResourceAsStream("/ghc-encoding-globals/data.jsonl.gz")!!).bufferedReader().use { reader ->
            val metadata = Json.parseToJsonElement(reader.readLine()).jsonObject
            val cases = reader.lineSequence().map { Json.parseToJsonElement(it).jsonObject }.toList()
            assertEquals(metadata.getValue("cases").jsonPrimitive.int, cases.size)
            val native = cases.filter { it.getValue("request").jsonObject.getValue("api").jsonPrimitive.content == "native" }
            val observed by lazy { child(native.map { it.getValue("request").jsonObject }) }
            var nativeIndex = 0
            cases.mapIndexedNotNull { i, c ->
                val request = c.getValue("request").jsonObject
                if (request.getValue("api").jsonPrimitive.content == "native") {
                    val index = nativeIndex++
                    if (Platform.isWindows()) null else DynamicTest.dynamicTest("$i ${c.getValue("group").jsonPrimitive.content}") { assertEquals(c.getValue("expected"), observed[index]) }
                } else DynamicTest.dynamicTest("$i ${c.getValue("group").jsonPrimitive.content}") { assertEquals(c.getValue("expected"), EncodingGlobalsObserve.controlled(request)) }
            }
        }

    @TestFactory fun nativePlatformBinding(): List<DynamicTest> = listOf(
        if (Platform.isWindows()) DynamicTest.dynamicTest("Kernel32 wide APIs and UTF8 argv ignore poisoned byte-encoding globals") {
            val expected = buildJsonObject {
                put("lookup", JsonArray("Русский".codePoints().toArray().map(::JsonPrimitive)))
                put("environment", "Русский"); put("argv", JsonArray(listOf(JsonPrimitive(255)))); put("settingsUnchanged", true)
            }
            assertEquals(listOf(expected), child(listOf(buildJsonObject { put("api", "wide") })))
        } else DynamicTest.dynamicTest("default resolver and canonical-name memory binding follow shared encoding changes") {
            val expected = Json.parseToJsonElement("""{"resolver":[[[195,191],[]],[[255],[255]],[[63,63],[65533]],[[63,63],[255]]],"memory":[[],[255]],"frees":4}""")
            assertEquals(listOf(expected), child(listOf(buildJsonObject { put("api", "resolverBindings") })))
        }
    )

    @Test fun `concurrent demand shares failure while replacement does not wait for old CAF`() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val start = CountDownLatch(1)
        val calls = AtomicInteger(); val failure = GhcIOException(GhcIoError.userError("one evaluation"))
        val state = GhcEncodingState({ error("unused") }, { calls.incrementAndGet(); entered.countDown(); check(release.await(10, TimeUnit.SECONDS)); throw failure }, { error("unused") }, false)
        val saved = state.getFileSystemEncoding(); assertEquals(0, calls.get())
        val pool = Executors.newFixedThreadPool(8)
        try {
            val pending = List(8) { pool.submit<Throwable> { start.await(); assertThrows(GhcIOException::class.java) { saved.decode(byteArrayOf(65)) } } }
            start.countDown(); assertTrue(entered.await(5, TimeUnit.SECONDS))
            val replacement = GhcTextEncoding.mkTextEncoding("ASCII")
            state.setFileSystemEncoding(replacement)
            assertSame(replacement, state.getFileSystemEncoding()); assertSame(replacement, state.argvEncoding())
            release.countDown(); pending.forEach { assertSame(failure, it.get(10, TimeUnit.SECONDS)) }
            assertEquals(1, calls.get())
            state.setFileSystemEncoding(state.initFileSystemEncoding)
            assertSame(saved, state.getFileSystemEncoding()); assertSame(failure, assertThrows(GhcIOException::class.java) { saved.textEncodingName })
        } finally { release.countDown(); pool.shutdownNow(); assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS)) }
    }
}
