package io.heapy.kotdoc.archive

import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.logging.assertLogJsonEquals
import io.heapy.kotdoc.templates.PartialPaths
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.nio.file.Path
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlin.test.*

/** Original library expected fields/bytes/errors, including all original Pandoc archives. */
class ZipCodecTest {
    private fun chars(a: JsonElement) = CharacterString(a.jsonArray.map { it.jsonPrimitive.int }.toIntArray())
    private fun bytes(a: JsonElement) = a.jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray()
    private fun raw(a: CharacterString) = JsonArray(a.points().map(::JsonPrimitive))
    private fun raw(a: ByteArray) = JsonArray(a.map { JsonPrimitive(it.toInt() and 255) })
    private fun platform(a: JsonElement) = if (a.jsonPrimitive.boolean) PartialPaths.Windows else PartialPaths.Posix
    private fun entry(a: JsonElement): ZipEntry {
        val v=a.jsonArray
        return ZipEntry(chars(v[0]),ZipCompression.valueOf(v[1].jsonPrimitive.content),if(v[2]==JsonNull) ZipEncryption.NoEncryption else ZipEncryption.PKWAREEncryption(v[2].jsonPrimitive.int),
            BigInteger(v[3].jsonPrimitive.content),v[4].jsonPrimitive.long,v[5].jsonPrimitive.long,v[6].jsonPrimitive.long,bytes(v[7]),bytes(v[8]),v[9].jsonPrimitive.int,v[10].jsonPrimitive.int,v[11].jsonPrimitive.long,bytes(v[12]))
    }
    private fun shape(e: ZipEntry, hash: Boolean = false) = buildJsonArray {
        add(raw(e.path));add(e.compression.name);add((e.encryption as? ZipEncryption.PKWAREEncryption)?.control?.let(::JsonPrimitive) ?: JsonNull)
        add(e.modified.toString());add(e.crc);add(e.compressedSize);add(e.uncompressedSize);add(raw(e.extra));add(raw(e.comment))
        add(e.versionMadeBy);add(e.internalAttributes);add(e.externalAttributes);add(if(hash) JsonPrimitive(sha(e.data)) else raw(e.data))
    }
    private fun archive(a: JsonElement): ZipArchive = a.jsonArray.let { ZipArchive(it[0].jsonArray.map(::entry),if(it[1]==JsonNull) null else bytes(it[1]),bytes(it[2])) }
    private fun shape(a: ZipArchive) = buildJsonArray { add(JsonArray(a.entries.map { shape(it) }));add(a.signature?.let(::raw) ?: JsonNull);add(raw(a.comment)) }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()
    private fun failure(name: String,detail: String) = buildJsonObject { put(name,detail) }
    private fun run(a: JsonArray): JsonElement = try { when(a[0].jsonPrimitive.content) {
        "paths" -> {
            val p=platform(a[1]);val drive=a[4].jsonPrimitive.boolean
            JsonArray((a[2].jsonPrimitive.int..a[3].jsonPrimitive.int).map { cp ->
                val path=if(drive) intArrayOf(cp)+":/dir/file".codePoints().toArray()
                    else "prefix/".codePoints().toArray()+intArrayOf(cp)+"/file".codePoints().toArray()
                raw(ZipCodec.normalize(CharacterString(path),p))
            })
        }
        "entry" -> shape(ZipCodec.toEntry(chars(a[2]),BigInteger(a[3].jsonPrimitive.content),bytes(a[4]),platform(a[1])))
        "encode" -> raw(ZipCodec.encode(archive(a[2]),platform(a[1])))
        "decode" -> when(val r=ZipCodec.decode(bytes(a[1]))) {
            is ZipDecodeResult.Failure -> failure("error",r.error)
            is ZipDecodeResult.Success -> buildJsonObject { put("archive",shape(r.archive));put("consumed",r.consumed) }
        }
        "source" -> {
            val path=Path.of(System.getProperty("kotdoc.root")).resolve(a[1].jsonPrimitive.content)
            when(val r=ZipCodec.decode(Files.readAllBytes(path))) {
                is ZipDecodeResult.Failure -> failure("error",r.error)
                is ZipDecodeResult.Success -> buildJsonObject {
                    put("entries",JsonArray(r.archive.entries.map { shape(it,true) }));put("signature",r.archive.signature?.let(::raw) ?: JsonNull)
                    put("comment",raw(r.archive.comment));put("consumed",r.consumed);put("encodedSha",sha(ZipCodec.encode(r.archive,PartialPaths.Posix)))
                    put("contents",JsonArray(r.archive.entries.map { e -> try { failure("sha",sha(ZipCodec.fromEntry(e))) } catch(e: ZipDecodeException) { failure("exception",e.message) } }))
                }
            }
        }
        "contents" -> raw(ZipCodec.fromEntry(entry(a[1])))
        "decrypt" -> ZipCodec.fromEncryptedEntry(chars(a[1]),entry(a[2]))?.let(::raw) ?: JsonNull
        "symlink" -> entry(a[1]).let { buildJsonArray { add(it.symbolicLink);add(it.cMode);add(ZipCodec.symbolicLinkTarget(it)?.let(::raw) ?: JsonNull) } }
        "ops" -> {
            var ar=archive(a[2]);val p=platform(a[1])
            a[3].jsonArray.forEach { op -> val v=op.jsonArray;ar=if(v[0].jsonPrimitive.content=="add") ar.add(entry(v[1]),p) else ar.delete(chars(v[1]),p) }
            shape(ar)
        }
        "find" -> archive(a[2]).find(chars(a[3]),platform(a[1]))?.let { shape(it) } ?: JsonNull
        else -> error("request")
    } } catch(e: ZipDecodeException) { failure("exception",e.message) } catch(e: ZipLimitException) { failure("exception",e.message) }
    @TestFactory
    fun originalZipObservations(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/zip-codec/data.jsonl.gz"))).bufferedReader().use { r ->
        r.readLine();r.lineSequence().mapIndexed { i,line ->
            var transport: String?=line
            DynamicTest.dynamicTest("ZIP ${i+1}") {
                val c=Json.parseToJsonElement(checkNotNull(transport)).jsonObject;transport=null
                assertLogJsonEquals(c.getValue("expected"),run(c.getValue("request").jsonArray))
            }
        }.toList()
    }
    @Test
    fun entriesAndArchivesOwnTheirBytesAndMembership() {
        val b=byteArrayOf(1);val p=CharacterString(intArrayOf(65))
        val e=ZipEntry(p,ZipCompression.NoCompression,crc=1,compressedSize=1,uncompressedSize=1,extra=b,comment=b,data=b)
        val es=mutableListOf(e);val a=ZipArchive(es,b,b);b[0]=9;es.clear();e.data[0]=8;a.signature?.fill(7)
        assertContentEquals(byteArrayOf(1),e.data);assertContentEquals(byteArrayOf(1),e.extra);assertContentEquals(byteArrayOf(1),a.signature)
        assertSame(e,a.entries.single())
        val duplicate=ZipEntry(p,ZipCompression.NoCompression,crc=1,compressedSize=1,uncompressedSize=1,extra=byteArrayOf(1),comment=byteArrayOf(1),data=byteArrayOf(1))
        assertEquals(e,duplicate);assertEquals(e.hashCode(),duplicate.hashCode())
    }
    @Test
    fun entryCountLimitIsCheckedBeforeSerializingEntries() {
        val e=ZipCodec.toEntry(CharacterString(intArrayOf(65)),BigInteger.ZERO,byteArrayOf(),PartialPaths.Posix)
        val error=assertFailsWith<ZipLimitException> { ZipCodec.encode(ZipArchive(List(65535) { e })) }
        assertEquals("Zip64NotSupported \"65535 or more entries require ZIP64\"",error.message)
    }
}
