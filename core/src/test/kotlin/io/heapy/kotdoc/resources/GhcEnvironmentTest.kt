package io.heapy.kotdoc.resources

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import com.sun.jna.Pointer
import io.heapy.kotdoc.http.HttpBytes
import io.heapy.kotdoc.http.HttpProxyEnvironment
import io.heapy.kotdoc.parsing.RegexCharacters
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.zip.GZIPInputStream

internal object EnvironmentTestMemory {
    fun <A> records(values: List<ByteArray>?, action: (Pointer?)->A): A {
        if(values==null) return action(null)
        val strings=values.map {value -> Memory((value.size+1).toLong()).also {m -> m.clear();if(value.isNotEmpty()) m.write(0,value,0,value.size)} }
        try {
            return Memory((strings.size+1L)*Native.POINTER_SIZE).use {array ->
                array.clear();strings.forEachIndexed {i,p -> array.setPointer(i.toLong()*Native.POINTER_SIZE,p)}
                action(array)
            }
        } finally {strings.forEach {it.close()}}
    }
    fun points(s: String) = JsonArray(s.codePoints().toArray().map(::JsonPrimitive))
    fun points(xs: IntArray) = JsonArray(xs.map {JsonPrimitive(it.toUInt().toLong())})
    fun pairs(xs: List<Pair<String,String>>) = JsonArray(xs.map {JsonArray(listOf(points(it.first),points(it.second)))})
    fun rawPairs(xs: List<Pair<IntArray,IntArray>>) = JsonArray(xs.map {JsonArray(listOf(points(it.first),points(it.second)))})
}

internal interface EnvironmentTestKernel : com.sun.jna.win32.StdCallLibrary {
    fun SetEnvironmentVariableW(name: com.sun.jna.WString,value: com.sun.jna.WString?): Int
}

/** Child process only: replacing environ never affects the Gradle/JUnit worker. */
object GhcEnvironmentChild {
    @JvmStatic fun main(args: Array<String>) {
        val records=Json.parseToJsonElement(String(Base64.getDecoder().decode(args[0]),Charsets.UTF_8)).let {v ->
            if(v is JsonNull) null else v.jsonArray.map {row -> row.jsonArray.map {it.jsonPrimitive.int.toByte()}.toByteArray()}
        }
        System.getenv() // Initialize the JVM cache before changing the OS block.
        GhcEnvironment.getEnvironment() // Initialize native library/locale/decoder first.
        val libc=NativeLibrary.getInstance(Platform.C_LIBRARY_NAME)
        val address=if(Platform.isMac()) libc.getFunction("_NSGetEnviron").invokePointer(emptyArray()) else libc.getGlobalVariableAddress("environ")
        val original=address.getPointer(0)
        val result=EnvironmentTestMemory.records(records) {block ->
            try {
                address.setPointer(0,block)
                EnvironmentTestMemory.rawPairs(GhcEnvironment.getEnvironmentCodePoints())
            } finally {address.setPointer(0,original)}
        }
        println(result)
    }
}

class GhcEnvironmentTest {
    private fun bytes(v: JsonElement) = v.jsonArray.map {it.jsonPrimitive.int.toByte()}.toByteArray()
    private fun ioError() = buildJsonObject {put("error","io")}
    private fun utf8(cp: Int): ByteArray = when {
        cp<128 -> byteArrayOf(cp.toByte())
        cp<0x800 -> byteArrayOf((0xc0 or (cp shr 6)).toByte(),(0x80 or (cp and 63)).toByte())
        cp<0x10000 -> byteArrayOf((0xe0 or (cp shr 12)).toByte(),(0x80 or ((cp shr 6) and 63)).toByte(),(0x80 or (cp and 63)).toByte())
        else -> byteArrayOf((0xf0 or (cp shr 18)).toByte(),(0x80 or ((cp shr 12) and 63)).toByte(),(0x80 or ((cp shr 6) and 63)).toByte(),(0x80 or (cp and 63)).toByte())
    }
    private fun observe(v: JsonObject): JsonElement {
        val locale=v["locale"]?.jsonPrimitive?.content ?: return observeRaw(v)
        val set=NativeLibrary.getInstance(Platform.C_LIBRARY_NAME).getFunction("setlocale")
        val category=if(Platform.isMac()) 2 else 0 // LC_CTYPE.
        val previous=set.invokePointer(arrayOf<Any?>(category,null)).getString(0,"US-ASCII")
        assertNotNull(set.invokePointer(arrayOf<Any>(category,locale)),"Oracle locale unavailable: $locale")
        return try {observeRaw(v)} finally {assertNotNull(set.invokePointer(arrayOf<Any>(category,previous)))}
    }
    private fun observeRaw(v: JsonObject): JsonElement = try {
        when(v.getValue("api").jsonPrimitive.content) {
            "decode" -> {
                val decoder=GhcEnvironmentEncoding(v.getValue("encoding").jsonPrimitive.content)
                JsonArray(v.getValue("bytes").jsonArray.map {JsonArray(listOf(EnvironmentTestMemory.points(decoder.decode(bytes(it)))))})
            }
            "range" -> {
                val start=v.getValue("start").jsonPrimitive.int;val count=v.getValue("count").jsonPrimitive.int
                val input=java.io.ByteArrayOutputStream()
                for(cp in start..<start+count) input.write(utf8(cp))
                val decoded=GhcEnvironmentEncoding("UTF-8").decode(input.toByteArray())
                val buffer=ByteBuffer.allocate(decoded.size*4).order(ByteOrder.LITTLE_ENDIAN)
                decoded.forEach(buffer::putInt)
                val digest=MessageDigest.getInstance("SHA-256").digest(buffer.array()).joinToString("") {"%02x".format(it.toInt() and 255)}
                buildJsonObject {put("count",decoded.size);put("sha256",digest)}
            }
            "posix","live" -> {
                val records=v.getValue("records").takeUnless {it is JsonNull}?.jsonArray?.map(::bytes)
                EnvironmentTestMemory.records(records) {block ->
                    EnvironmentTestMemory.rawPairs(GhcEnvironment.posixEnvironmentCodePoints({block}) {GhcEnvironmentEncoding(v.getValue("encoding").jsonPrimitive.content)})
                }
            }
            "windows" -> {
                val units=v.getValue("units").takeUnless {it is JsonNull}?.jsonArray?.map {it.jsonPrimitive.int}
                val memory=units?.let {Memory((it.size+16L)*2).also {m ->m.clear();it.forEachIndexed {i,c ->m.setShort(i*2L,c.toShort())}}}
                try {
                    var frees=0
                    val api=object : GhcWindowsEnvironmentApi {
                        override fun GetEnvironmentStringsW(): Pointer? = memory
                        override fun FreeEnvironmentStringsW(block: Pointer?): Int {assertSame(memory,block);frees++;return 0}
                    }
                    val result=GhcEnvironment.windowsEnvironment(api)
                    assertEquals(1,frees)
                    EnvironmentTestMemory.pairs(result)
                } finally {memory?.close()}
            }
            else -> error(v)
        }
    } catch(_: IOException) {ioError()}

    @TestFactory
    fun originalEnvironment(): List<DynamicTest> {
        val platform=if(Platform.isMac()) "macos-aarch64" else "linux-x86_64"
        return GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/ghc-environment/$platform.jsonl.gz"))).bufferedReader().useLines {lines ->
            lines.drop(1).mapIndexedNotNull {i,line ->
                val row=Json.parseToJsonElement(line).jsonObject;val request=row.getValue("request").jsonObject
                if(Platform.isWindows() && request["posixOnly"]?.jsonPrimitive?.boolean==true) null else
                DynamicTest.dynamicTest("$i ${row.getValue("group").jsonPrimitive.content}") {assertEquals(row.getValue("expected"),observe(request))}
            }.toList()
        }
    }
    @Test
    fun `null POSIX block does not demand filesystem encoding and native failures keep identity`() {
        assertEquals(emptyList<Pair<String,String>>(),GhcEnvironment.posixEnvironment({null}) {error("must not read encoding")})
        val get=IOException("get failed");val free=IOException("free failed");var freed=false
        val failingGet=object : GhcWindowsEnvironmentApi {
            override fun GetEnvironmentStringsW(): Pointer? = throw get
            override fun FreeEnvironmentStringsW(block: Pointer?): Int {freed=true;return 0}
        }
        assertSame(get,assertThrows(IOException::class.java) {GhcEnvironment.windowsEnvironment(failingGet)});assertFalse(freed)
        val failingFree=object : GhcWindowsEnvironmentApi {
            override fun GetEnvironmentStringsW(): Pointer? = null
            override fun FreeEnvironmentStringsW(block: Pointer?): Int = throw free
        }
        assertSame(free,assertThrows(IOException::class.java) {GhcEnvironment.windowsEnvironment(failingFree)})
    }

    @TestFactory
    fun actualOperatingSystem(): List<DynamicTest> = if(Platform.isWindows()) listOf(
        DynamicTest.dynamicTest("live Kernel32 environment updates bypass cached JVM map and preserve original wide scan") {windowsNative()},
    ) else listOf(
        DynamicTest.dynamicTest("isolated JVM reads physical ordered duplicates malformed bytes and null environ") {posixNative()},
        DynamicTest.dynamicTest("native setenv updates are visible to HTTP proxy construction after JVM cache initialization") {proxyNative()},
    )

    private fun posixNative() {
        val rows=listOf(listOf(65,61,255),listOf(65,61,50),listOf(61,51),listOf(110,111,110,101),listOf(66,61,196,128))
        val input=JsonArray(rows.map {JsonArray(it.map(::JsonPrimitive))})
        val expected=Json.parseToJsonElement("[[[65],[56575]],[[65],[50]],[[],[51]],[[110,111,110,101],[]],[[66],[256]]]")
        assertEquals(expected,child(input));assertEquals(JsonArray(emptyList()),child(JsonNull))
    }
    private fun child(records: JsonElement): JsonElement {
        val classes=listOf(GhcEnvironmentChild::class.java,GhcEnvironment::class.java,RegexCharacters::class.java,
            JsonElement::class.java,kotlinx.serialization.KSerializer::class.java,kotlin.Unit::class.java,Native::class.java)
        val locations=classes.map {Path.of(it.protectionDomain.codeSource.location.toURI()).toString()}.toMutableList()
        System.getProperty("kotdoc.root")?.let {locations.add(Path.of(it,"core/src/main/resources").toString())}
        val java=Path.of(System.getProperty("java.home"),"bin","java").toString()
        val p=ProcessBuilder(java,"-cp",locations.distinct().joinToString(File.pathSeparator),GhcEnvironmentChild::class.java.name,
            Base64.getEncoder().encodeToString(records.toString().toByteArray())).redirectErrorStream(true).start()
        val out=p.inputStream.readAllBytes().toString(Charsets.UTF_8)
        assertEquals(0,p.waitFor(),out)
        return Json.parseToJsonElement(out.trim())
    }
    private fun proxyNative() {
        val libc=NativeLibrary.getInstance(Platform.C_LIBRARY_NAME)
        val set=libc.getFunction("setenv");val unset=libc.getFunction("unsetenv")
        val key="kotdoc_proxy_${UUID.randomUUID().toString().replace("-","")}"
        System.getenv() // Warm the cached JVM map; the unique variable is absent.
        val environment = {HttpProxyEnvironment.systemEnvironment().filter {it.first==key}}
        try {
            assertEquals(0,set.invokeInt(arrayOf<Any>(key,"first:80",1)))
            val first=HttpProxyEnvironment.envHelper(key,environment)
            assertEquals("first",first(HttpBytes.pack("example.com"))?.proxy?.host?.latin1())
            assertEquals(0,set.invokeInt(arrayOf<Any>(key,"",1)))
            assertEquals("",HttpProxyEnvironment.systemEnvironment().first {it.first==key}.second) // Original T12494 getEnvironment assertion.
            assertEquals(0,set.invokeInt(arrayOf<Any>(key,"second:81",1)))
            assertEquals("first",first(HttpBytes.pack("example.com"))?.proxy?.host?.latin1())
            assertEquals("second",HttpProxyEnvironment.envHelper(key,environment)(HttpBytes.pack("example.com"))?.proxy?.host?.latin1())
        } finally {assertEquals(0,unset.invokeInt(arrayOf(key)))}
    }
    private fun windowsNative() {
        val api=Native.load("Kernel32",EnvironmentTestKernel::class.java)
        val key="KOTDOC_ENV_${UUID.randomUUID().toString().replace("-","")}"
        val extra="KOTDOC_TAIL_${UUID.randomUUID().toString().replace("-","")}"
        System.getenv()
        try {
            assertNotEquals(0,api.SetEnvironmentVariableW(com.sun.jna.WString(key),com.sun.jna.WString("first")))
            assertEquals("first",GhcEnvironment.getEnvironment().first {it.first==key}.second)
            assertNotEquals(0,api.SetEnvironmentVariableW(com.sun.jna.WString(key),com.sun.jna.WString("")))
            assertEquals("",GhcEnvironment.getEnvironment().first {it.first==key}.second) // Original T12494 getter assertion.
            assertNotEquals(0,api.SetEnvironmentVariableW(com.sun.jna.WString(key),com.sun.jna.WString("Ā$extra=😀")))
            val snapshot=GhcEnvironment.getEnvironment()
            assertEquals("Ā$extra=😀",snapshot.first {it.first==key}.second)
            assertEquals("😀",snapshot.first {it.first==extra}.second)
        } finally {assertNotEquals(0,api.SetEnvironmentVariableW(com.sun.jna.WString(key),null))}
    }
}
