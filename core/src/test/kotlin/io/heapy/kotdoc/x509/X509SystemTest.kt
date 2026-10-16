package io.heapy.kotdoc.x509

import com.sun.jna.*
import io.heapy.kotdoc.asn1.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.stream.Stream
import kotlin.streams.asStream

internal object X509SystemFixtures {
    fun certificate(): ByteArray = GZIPInputStream(javaClass.getResourceAsStream("/x509-store/data.jsonl.gz")!!).bufferedReader().use {r->
        r.readLine()
        val c=r.lineSequence().map {Json.parseToJsonElement(it).jsonObject}.first {it["group"]?.jsonPrimitive?.content=="original-memory-certificate-read"}
        val pem=c.getValue("request").jsonObject.getValue("bytes").jsonArray.map {it.jsonPrimitive.int.toByte()}.toByteArray().toString(Charsets.US_ASCII)
        java.util.Base64.getDecoder().decode(pem.lineSequence().filter {it.isNotBlank() && !it.startsWith("---")}.joinToString(""))
    }
    fun bytes(v: JsonElement)=v.jsonArray.map {it.jsonPrimitive.int.toByte()}.toByteArray()
    fun byteJSON(v: ByteArray)=JsonArray(v.map {JsonPrimitive(it.toInt() and 255)})
}
class X509SystemTest {
    private fun observe(v: JsonObject): JsonElement {
        val trace=mutableListOf<JsonElement>();val calls=mutableMapOf<String,Int>()
        fun record(stage: String,vararg values: JsonElement) {
            trace.add(JsonArray(values.toList()));val n=(calls[stage] ?: 0)+1;calls[stage]=n
            for(f in v.getValue("failures").jsonArray) {val a=f.jsonArray
                if(a[0].jsonPrimitive.content==stage && a[1].jsonPrimitive.int==n) when(a[2].jsonPrimitive.content) {
                    "io" -> throw IOException("IO failure")
                    "asn1" -> throw Asn1Exception(Asn1Error.TypeDecodingFailed("effect"))
                    else -> error("effect failure")
                }
            }
        }
        fun text(s: String)=JsonPrimitive(s)
        fun fakeStore(value: JsonElement): X509CertificateStore?=when {
            value==JsonNull -> null
            value is JsonPrimitive -> if(value.content=="rootError") X509CertificateStore.deferred {error("store demanded")} else error("store result demanded")
            else -> X509CertificateStore.makeDeferred {value.jsonArray.asSequence().map {raw->lazy {
                if(raw==JsonNull) error("certificate demanded")
                when(val r=X509.decodeSignedCertificate(X509SystemFixtures.bytes(raw))) {
                    is Asn1ParseResult.Success -> r.value
                    is Asn1ParseResult.Failure -> error(buildString {r.message.points().forEach(::appendCodePoint)})
                }
            }}}
        }
        val effects=object: X509SystemEffects {
            override fun lookupEnv(name: String): String? {
                record("lookup:$name",text("lookup"),text(name))
                return v.getValue("environment").jsonObject[name]?.takeIf {it!=JsonNull}?.jsonPrimitive?.content
            }
            override fun getEnv(name: String): String {
                record("get:$name",text("get"),text(name))
                return v.getValue("environment").jsonObject[name]?.takeIf {it!=JsonNull}?.jsonPrimitive?.content ?: throw IOException("missing environment variable")
            }
            override fun readCertificateStore(path: String): Lazy<X509CertificateStore?> {
                record("read:$path",text("read"),text(path))
                return lazy {fakeStore(v.getValue("stores").jsonObject[path] ?: JsonNull)}
            }
            override fun startMacProcess(command: List<String>): X509MacCertificateProcess {
                record("process",text("process"),JsonArray(command.map(::text)),JsonPrimitive(true))
                return object: X509MacCertificateProcess {
                    override fun outputChunks()=sequenceOf(X509SystemFixtures.bytes(v.getValue("output")))
                    override fun waitFor(): Int {record("wait",text("wait"));return v.getValue("exit").jsonPrimitive.int}
                }
            }
            override val windows=object: X509WindowsCertificateApi {
                override fun openRoot(): Pointer? {
                    record("open",text("open"),JsonArray("ROOT".map {JsonPrimitive(it.code)}),JsonPrimitive(true))
                    return if(v.getValue("open").jsonPrimitive.boolean) Pointer.createConstant(1) else null
                }
                override fun next(store: Pointer,previous: Pointer?): Pointer? {
                    val index=if(previous==null) -1 else Pointer.nativeValue(previous).toInt()-2
                    record("enum",text("enum"),if(index<0) JsonNull else JsonPrimitive(index))
                    return if(index+1>=v.getValue("contexts").jsonArray.size) null else Pointer.createConstant((index+3).toLong())
                }
                override fun context(pointer: Pointer): X509WindowsCertificateContext {
                    val c=v.getValue("contexts").jsonArray[Pointer.nativeValue(pointer).toInt()-2].jsonObject
                    return X509WindowsCertificateContext(c.getValue("encoding").jsonPrimitive.long.toUInt()) {X509SystemFixtures.bytes(c.getValue("bytes"))}
                }
                override fun close(store: Pointer) {record("close",text("close"),JsonPrimitive(0))}
            }
        }
        val result=try {
            val platform=when(v.getValue("platform").jsonPrimitive.content) {"unix"->X509SystemPlatform.Unix;"mac"->X509SystemPlatform.MacOS;else->X509SystemPlatform.Windows}
            val s=X509SystemLoader(platform,effects).getSystemCertificateStore()
            val value=when(v.getValue("part").jsonPrimitive.content) {
                "ignore" -> JsonNull
                "empty" -> JsonPrimitive(s.isEmpty())
                "count" -> JsonPrimitive(s.certificates().count())
                else -> JsonArray(s.certificates().toList().map {X509SystemFixtures.byteJSON(it.encodeSignedObject())})
            }
            buildJsonObject {put("right",value)}
        } catch(e: IOException) {buildJsonObject {put("io",e.message)}}
          catch(e: RuntimeException) {buildJsonObject {put("fatal",e.message.orEmpty().lineSequence().first())}}
        return buildJsonObject {put("result",result);put("trace",JsonArray(trace))}
    }
    @TestFactory fun originalObservations(): Stream<DynamicTest> {
        val reader=GZIPInputStream(javaClass.getResourceAsStream("/x509-system/data.jsonl.gz")!!).bufferedReader()
        val expected=Json.parseToJsonElement(reader.readLine()).jsonObject.getValue("cases").jsonPrimitive.int
        // Retain source lines, not a second complete JSON tree for every DER byte.
        return sequence {
            var count=0
            reader.use {r->
                for(line in r.lineSequence()) {
                    val index=count++
                    yield(DynamicTest.dynamicTest("$index original-system-X509") {
                        val c=Json.parseToJsonElement(line).jsonObject
                        assertEquals(c.getValue("expected"),observe(c.getValue("request").jsonObject),c.getValue("group").jsonPrimitive.content+" "+c.getValue("request"))
                    })
                }
            }
            assertEquals(expected,count)
        }.asStream().onClose(reader::close)
    }
    @Test fun crypt32LayoutCopiesBeforeEnumerationReleasesTheContext() {
        val bytes=X509SystemFixtures.certificate();val data=Memory(bytes.size.toLong());data.write(0,bytes,0,bytes.size)
        val context=Memory(40);context.clear();context.setInt(0,1);context.setPointer(Native.POINTER_SIZE.toLong(),data);context.setInt((2*Native.POINTER_SIZE).toLong(),bytes.size)
        val calls=mutableListOf<String>();var n=0
        val functions=object: X509Crypt32Functions {
            override fun CertOpenSystemStoreW(provider: Pointer?,name: WString): Pointer {assertNull(provider);assertEquals("ROOT",name.toString());calls.add("open");return Pointer.createConstant(1)}
            override fun CertEnumCertificatesInStore(store: Pointer,previous: Pointer?): Pointer? {
                calls.add("enum");if(n++==0) {assertNull(previous);return context}
                assertEquals(context,previous);data.clear();return null
            }
            override fun CertCloseStore(store: Pointer,flags: Int): Int {assertEquals(0,flags);calls.add("close");return 0}
        }
        val effects=object: X509SystemEffects by X509NativeSystemEffects {
            override fun lookupEnv(name: String): String?=null
            override val windows=X509Crypt32CertificateApi(functions)
        }
        val loaded=X509SystemLoader(X509SystemPlatform.Windows,effects).getSystemCertificateStore()
        assertEquals(listOf("open","enum","enum","close"),calls)
        assertArrayEquals(bytes,loaded.certificates().single().encodeSignedObject())
        context.setInt(0,0x10001);context.setPointer(Native.POINTER_SIZE.toLong(),null);context.setInt((2*Native.POINTER_SIZE).toLong(),-1)
        assertEquals(65537u,X509Crypt32CertificateApi(functions).context(context).encoding)
    }
}
