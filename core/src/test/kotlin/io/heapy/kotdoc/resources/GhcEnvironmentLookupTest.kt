package io.heapy.kotdoc.resources

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.WString
import io.heapy.kotdoc.errors.*
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

internal object LookupTestMemory {
    fun <A> bytes(value: JsonElement?, action: (Pointer?) -> A): A {
        if (value == null || value is JsonNull) return action(null)
        val bytes = value.jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray()
        return Memory(bytes.size + 1L).use { p -> p.clear(); if (bytes.isNotEmpty()) p.write(0, bytes, 0, bytes.size); action(p) }
    }
    fun <A> wide(value: JsonElement?, action: (Pointer?) -> A): A {
        if (value == null || value is JsonNull) return action(null)
        val units = value.jsonArray.map { it.jsonPrimitive.int }
        return Memory((units.size + 1L) * 2).use { p -> p.clear(); units.forEachIndexed { i, unit -> p.setShort(i * 2L, unit.toShort()) }; action(p) }
    }
    fun points(value: CharacterString) = JsonArray(value.points().map(::JsonPrimitive))
    fun ioError(e: GhcIOException) = buildJsonObject {
        val error = e.error
        put("error", "io"); put("type", error.type.shown); put("location", points(error.location)); put("description", points(error.description))
        put("errno", error.errno?.let(::JsonPrimitive) ?: JsonNull); put("filename", error.filename?.let(::points) ?: JsonNull)
    }
}

class GhcEnvironmentLookupTest {
    private fun raise(s: String): Nothing = if (s.startsWith("io:")) throw GhcIOException(GhcIoError.userError(s)) else throw AssertionError(s)
    private fun check(v: JsonObject, key: String) { v[key]?.jsonPrimitive?.content?.let(::raise) }
    private fun observe(v: JsonObject): JsonElement = LookupTestMemory.bytes(v["value"]) { value ->
        LookupTestMemory.wide(v["message"]) { message ->
            val events = mutableListOf<JsonElement>()
            val name = CharacterString(v["name"]?.jsonArray?.map { it.jsonPrimitive.int }?.toIntArray() ?: intArrayOf())
            val replies = ArrayDeque(v["replies"]?.jsonArray?.map { it.jsonObject } ?: emptyList())
            val codes = ArrayDeque(v["errors"]?.jsonArray?.map { it.jsonPrimitive.long.toUInt() } ?: emptyList())
            val windows = object: GhcWindowsLookupApi {
                override fun query(name: Pointer, buffer: Pointer, size: UInt): UInt {
                    val units = buildList { var i = 0L; while (true) { val unit = name.getShort(i).toInt() and 65535; if (unit == 0) break; add(unit); i += 2 } }
                    events.add(buildJsonObject { put("queryWide", JsonArray(units.map(::JsonPrimitive))); put("size", size.toLong()) })
                    check(v, "queryFailure")
                    val reply = replies.removeFirstOrNull() ?: buildJsonObject { put("result", 0) }
                    buffer.clear(size.toLong() * 2)
                    reply["units"]?.jsonArray?.take(size.toInt())?.forEachIndexed { i, unit -> buffer.setShort(i * 2L, unit.jsonPrimitive.int.toShort()) }
                    return reply.getValue("result").jsonPrimitive.long.toUInt()
                }
                override fun getLastError(): UInt {
                    val code = when (codes.size) { 0 -> 203u; 1 -> codes.first(); else -> codes.removeFirst() }
                    events.add(buildJsonObject { put("lastError", code.toLong()) }); return code
                }
                override fun getErrorMessage(code: UInt): Pointer? {
                    events.add(buildJsonObject { put("format", code.toLong()) }); check(v, "formatFailure"); return message
                }
                override fun localFree(pointer: Pointer) { assertEquals(message, pointer); events.add(JsonPrimitive("free")) }
            }
            val result = try {
                when (v.getValue("api").jsonPrimitive.content) {
                    "posixGet", "posixLookup" -> {
                        val foreign = {
                            val encoding = v.getValue("foreign").jsonPrimitive.content
                            events.add(buildJsonObject { put("foreign", encoding) }); check(v, "foreignFailure"); GhcForeignCString(encoding)
                        }
                        val filesystem = {
                            val encoding = v.getValue("filesystem").jsonPrimitive.content
                            events.add(buildJsonObject { put("filesystem", encoding) }); check(v, "filesystemFailure"); GhcTextEncoding.mkTextEncoding(encoding + "//ROUNDTRIP")
                        }
                        val query: (Pointer) -> Pointer? = { p ->
                            val bytes = p.getByteArray(0, p.indexOf(0, 0).toInt())
                            events.add(buildJsonObject { put("query", JsonArray(bytes.map { JsonPrimitive(it.toInt() and 255) })) })
                            check(v, "queryFailure"); value
                        }
                        val result = if (v.getValue("api").jsonPrimitive.content == "posixGet") GhcEnvironmentLookup.posixGet(name, foreign, filesystem, query)
                            else GhcEnvironmentLookup.posixLookup(name, foreign, filesystem, query)
                        result?.let(LookupTestMemory::points) ?: JsonNull
                    }
                    "windowsLookup" -> GhcEnvironmentLookup.windowsLookup(name, windows)?.let(LookupTestMemory::points) ?: JsonNull
                    "windowsGet" -> LookupTestMemory.points(GhcEnvironmentLookup.windowsGet(name, windows))
                    "windowsError" -> LookupTestMemory.ioError(GhcIOException(GhcWindowsErrors.toError(Chars.fromString("getEnv"), v.getValue("code").jsonPrimitive.long.toUInt(), windows)))
                    "map" -> JsonPrimitive(GhcWindowsErrors.mapErrno(v.getValue("code").jsonPrimitive.long.toUInt()).first)
                    else -> error(v)
                }
            } catch (e: GhcIOException) { LookupTestMemory.ioError(e) }
            catch (e: AssertionError) { buildJsonObject { put("error", "fatal"); put("message", e.message!!) } }
            buildJsonObject { put("result", result); put("events", JsonArray(events)) }
        }
    }
    @TestFactory fun originalLookupPrograms(): List<DynamicTest> =
        GZIPInputStream(javaClass.getResourceAsStream("/ghc-environment-lookup/data.jsonl.gz")!!).bufferedReader().use { reader ->
            val metadata = Json.parseToJsonElement(reader.readLine()).jsonObject
            val cases = reader.lineSequence().map { Json.parseToJsonElement(it).jsonObject }.toList()
            assertEquals(metadata.getValue("cases").jsonPrimitive.int, cases.size)
            cases.mapIndexed { i, c -> DynamicTest.dynamicTest("$i ${c.getValue("group").jsonPrimitive.content}") {
                assertEquals(c.getValue("expected"), observe(c.getValue("request").jsonObject))
            } }
        }
}

/** Child JVM owns environ replacement, so no other JUnit worker's native state changes. */
object GhcEnvironmentLookupChild {
    @JvmStatic fun main(args: Array<String>) {
        System.getenv()
        val libc = NativeLibrary.getInstance(Platform.C_LIBRARY_NAME)
        val locale = if (Platform.isMac()) "UTF-8" else "C.UTF-8"
        check(libc.getFunction("setlocale").invokePointer(arrayOf<Any>(if (Platform.isMac()) 2 else 0, locale)) != null)
        GhcEnvironmentLookup.lookupEnv(Chars.fromString("KOTDOC_LOOKUP_TEST"))
        val address = if (Platform.isMac()) libc.getFunction("_NSGetEnviron").invokePointer(emptyArray()) else libc.getGlobalVariableAddress("environ")
        val previous = address.getPointer(0)
        val records = listOf("KOTDOC_LOOKUP_TEST=".toByteArray() + byteArrayOf(-1, 97), "KOTDOC_LOOKUP_EMPTY=".toByteArray())
        val result = EnvironmentTestMemory.records(records) { block ->
            try {
                address.setPointer(0, block)
                val value = GhcEnvironmentLookup.getEnv(Chars.fromString("KOTDOC_LOOKUP_TEST"))
                val empty = GhcEnvironmentLookup.getEnv(Chars.fromString("KOTDOC_LOOKUP_EMPTY"))
                val absent = GhcEnvironmentLookup.lookupEnv(Chars.fromString("KOTDOC_LOOKUP_MISSING"))
                val missing = try { GhcEnvironmentLookup.getEnv(Chars.fromString("KOTDOC_LOOKUP_MISSING")); error("missing did not fail") } catch (e: GhcIOException) { e }
                val truncated = GhcEnvironmentLookup.getEnv(Chars.fromString("KOTDOC_LOOKUP_TEST\u0000ignored"))
                buildJsonObject {
                    put("value", LookupTestMemory.points(value)); put("empty", LookupTestMemory.points(empty)); put("absent", absent?.let(LookupTestMemory::points) ?: JsonNull)
                    put("missing", LookupTestMemory.ioError(missing)); put("truncated", LookupTestMemory.points(truncated))
                }
            } finally { address.setPointer(0, previous) }
        }
        println(result)
    }
}

class GhcEnvironmentLookupNativeTest {
    @Test fun liveOperatingSystemLookupBypassesTheJVMCache() {
        if (Platform.isWindows()) windows() else posix()
    }
    private fun posix() {
        val classes = listOf(GhcEnvironmentLookupChild::class.java, GhcEnvironmentLookup::class.java, JsonElement::class.java,
            kotlinx.serialization.KSerializer::class.java, Unit::class.java, Native::class.java)
        val classpath = classes.map { File(it.protectionDomain.codeSource.location.toURI()).absolutePath }.distinct().toMutableList()
        System.getProperty("kotdoc.root")?.let { classpath.add(File(it, "core/src/main/resources").absolutePath) }
        val java = File(System.getProperty("java.home"), "bin/java").absolutePath
        val process = ProcessBuilder(java, "-cp", classpath.joinToString(File.pathSeparator), GhcEnvironmentLookupChild::class.java.name).redirectErrorStream(true).start()
        assertTrue(process.waitFor(15, TimeUnit.SECONDS), "child environment lookup timed out")
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.exitValue(), output)
        val result = Json.parseToJsonElement(output.trim()).jsonObject
        assertEquals(JsonArray(listOf(JsonPrimitive(0xdcff), JsonPrimitive(97))), result["value"])
        assertEquals(result["value"], result["truncated"]); assertEquals(JsonArray(emptyList()), result["empty"]); assertEquals(JsonNull, result["absent"])
        val error = result.getValue("missing").jsonObject
        assertEquals("does not exist", error.getValue("type").jsonPrimitive.content)
        assertEquals(LookupTestMemory.points(Chars.fromString("getEnv")), error["location"])
        assertEquals(LookupTestMemory.points(Chars.fromString("KOTDOC_LOOKUP_MISSING")), error["filename"])
    }
    private fun windows() {
        val kernel = Native.load("Kernel32", EnvironmentTestKernel::class.java)
        val name = "KOTDOC_LOOKUP_Русский_😀_${UUID.randomUUID()}"
        val key = Chars.fromString(name)
        assertNull(System.getenv(name)); assertNull(GhcEnvironmentLookup.lookupEnv(key))
        val missing = assertThrows(GhcIOException::class.java) { GhcEnvironmentLookup.getEnv(key) }
        assertEquals(IoErrorType.NoSuchThing, missing.error.type); assertEquals(key, missing.error.filename)
        try {
            val value = "Русский😀".repeat(100)
            assertNotEquals(0, kernel.SetEnvironmentVariableW(WString(name), WString(value)))
            assertNull(System.getenv(name))
            assertEquals(Chars.fromString(value), GhcEnvironmentLookup.getEnv(key))
            assertEquals(Chars.fromString(value), GhcEnvironmentLookup.lookupEnv(key))
            assertNotEquals(0, kernel.SetEnvironmentVariableW(WString(name), WString("")))
            assertNull(GhcEnvironmentLookup.lookupEnv(key))
            val empty = assertThrows(GhcIOException::class.java) { GhcEnvironmentLookup.getEnv(key) }
            assertEquals(IoErrorType.InvalidArgument, empty.error.type); assertEquals(22, empty.error.errno)
            assertTrue(empty.error.description.points().isNotEmpty()); assertNull(empty.error.filename)
            assertEquals("Error 0xffffffff", Chars.build(GhcWindowsErrors.errorMessage(UInt.MAX_VALUE, GhcNativeWindowsLookup)))
        } finally { assertNotEquals(0, kernel.SetEnvironmentVariableW(WString(name), null)) }
        assertNull(GhcEnvironmentLookup.lookupEnv(key))
    }
}
