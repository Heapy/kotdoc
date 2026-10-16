package io.heapy.kotdoc.x509

import io.heapy.kotdoc.asn1.*
import io.heapy.kotdoc.pem.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.nio.file.Files
import java.nio.file.Path
import java.io.IOException
import java.util.zip.GZIPInputStream

class X509CertificateStoreTest {
    private fun JsonObject.text(k: String)=getValue(k).jsonPrimitive.content
    private fun JsonObject.count()=getValue("count").jsonPrimitive.int
    private fun bytes(v: JsonElement)=v.jsonArray.map {it.jsonPrimitive.int.toByte()}.toByteArray()
    private fun byteJSON(v: ByteArray)=JsonArray(v.map {JsonPrimitive(it.toInt() and 255)})
    private fun dn(v: JsonElement): X509DistinguishedName = if(v==JsonNull) X509DistinguishedName.deferred {error("dn demanded")} else X509DistinguishedName(v.jsonArray.map {
        val a=it.jsonArray
        X509DnAttribute.deferred({if(a[0]==JsonNull) error("oid demanded") else a[0].jsonArray.map {n->n.jsonPrimitive.content.toBigInteger()}},Asn1CharacterString.deferred(Asn1StringEncoding.entries[a[1].jsonPrimitive.int]) {if(a[2]==JsonNull) error("content demanded") else bytes(a[2])})
    })
    private fun certificate(v: JsonElement): X509SignedCertificate {
        if(v==JsonNull) error("certificate demanded")
        val a=v.jsonArray
        val c=X509Certificate.deferred({error("version demanded")},{if(a[1]==JsonNull) error("integer demanded") else a[1].jsonPrimitive.content.toBigInteger()},{error("signature demanded")},{error("issuer demanded")},{error("validity demanded")},{dn(a[0])},{error("key demanded")},{error("extensions demanded")})
        return X509SignedExact(lazyOf(X509Signed.deferred({c},{error("signature demanded")},{byteArrayOf()})),{error("raw demanded")},{error("encoding demanded")})
    }
    private fun store(v: JsonElement): X509CertificateStore {
        if(v is JsonPrimitive) error("store demanded")
        val o=v.jsonObject
        return when(o.text("op")) {
            "empty" -> X509CertificateStore.empty()
            "make" -> X509CertificateStore.makeDeferred {sequence {
                for(c in o.getValue("certs").jsonArray) yield(lazy {certificate(c)})
                if(o.getValue("tailError").jsonPrimitive.boolean) error("tail demanded")
            }}
            "append" -> store(o.getValue("left"))+store(o.getValue("right"))
            else -> {
                val items=o.getValue("stores").jsonArray
                fun fold(index: Int): X509CertificateStore {
                    if(index==items.size) {
                        if(o.getValue("tailError").jsonPrimitive.boolean) error("tail demanded")
                        return X509CertificateStore.empty()
                    }
                    val rest=fold(index+1);return store(items[index])+rest
                }
                fold(0)
            }
        }
    }
    private fun storeOutput(v: JsonObject,source: ()->X509CertificateStore): JsonElement {
        val s by lazy(source)
        return when(v.text("part")) {
        "empty" -> JsonPrimitive(s.isEmpty())
        "findTag" -> JsonPrimitive(s.findCertificateCell(lazy {dn(v.getValue("dn"))})!=null)
        "find" -> s.findCertificateCell(lazy {dn(v.getValue("dn"))})?.let {JsonPrimitive(X509.getCertificate(it.value).serial.toString())} ?: JsonNull
        "spine" -> JsonPrimitive(sequence {yieldAll(s.certificateCells())}.take(v.count()).count())
        else -> JsonArray(sequence {yieldAll(s.certificateCells())}.take(v.count()).toList().map {JsonPrimitive(X509.getCertificate(it.value).serial.toString())})
    }
    }
    private fun keyOutput(k: X509PrivateKey)=buildJsonObject {put("shown",k.toString());put("der",byteJSON(X509PrivateKeyCodec.encodeBytes(k)))}
    private fun signedOutput(c: X509SignedCertificate)=byteJSON(c.encodeSignedObject())
    private fun <A> listOutput(v: JsonObject,items: Sequence<A>,json: (A)->JsonElement): JsonElement {
        val prefix=items.take(v.count()).toList()
        return if(v.text("part")=="spine") JsonPrimitive(prefix.size) else JsonArray(prefix.map(json))
    }
    private fun observe(v: JsonObject): JsonElement=try {
        when(v.text("api")) {
            "store" -> storeOutput(v) {store(v.getValue("store"))}
            "keys" -> listOutput(v,X509Memory.readKeyFileFromMemory(bytes(v.getValue("bytes"))),::keyOutput)
            "signed" -> listOutput(v,X509Memory.readSignedCertificates(bytes(v.getValue("bytes"))),::signedOutput)
            "sameKeys" -> JsonPrimitive(X509Memory.readKeyFileFromMemory(bytes(v.getValue("bytes"))).toList()==X509Memory.readKeyFileFromMemory(bytes(v.getValue("other"))).toList())
            "pemToKey" -> when(val parsed=Pem.parse(bytes(v.getValue("bytes")))) {
                is PemParseResult.Failure -> JsonNull
                is PemParseResult.Success -> {
                    var acc=sequenceOf(lazyOf<X509PrivateKey?>(null))
                    for(p in parsed.sections) acc=X509Memory.pemToKey(acc,p)
                    JsonArray(acc.take(v.count()).toList().map {it.value?.let(::keyOutput) ?: JsonNull})
                }
            }
            else -> files(v) {path->when(v.text("part")) {
                "store" -> X509CertificateStore.read(path)?.let {JsonArray(it.certificates().toList().map(::signedOutput))} ?: JsonNull
                "certificates" -> JsonArray(X509CertificateStore.readCertificates(path).toList().map(::signedOutput))
                "keys" -> JsonArray(X509File.readKeyFile(path).toList().map(::keyOutput))
                else -> JsonArray(X509File.readSignedCertificates(path).toList().map(::signedOutput))
            }}
        }
    } catch(e: RuntimeException) {buildJsonObject {put("fatal",e.message.orEmpty().lineSequence().first())}}
    private fun files(v: JsonObject,action: (Path)->JsonElement): JsonElement {
        val dir=Files.createTempDirectory("kotdoc-store-")
        try {
            for(f in v.getValue("files").jsonArray) {val a=f.jsonArray;val path=dir.resolve(a[0].jsonPrimitive.content);if(a[1]==JsonNull) Files.createDirectory(path) else Files.write(path,bytes(a[1]))}
            val path=dir.resolve(v.text("path"))
            return try {action(path)} catch(e: IOException) {
                buildJsonObject {put("fatal",if(!Files.exists(path)) "file missing" else if(Files.isDirectory(path)) "file is directory" else throw e)}
            }
        } finally {Files.walk(dir).use {it.sorted(Comparator.reverseOrder()).forEach(Files::delete)}}
    }
    @TestFactory fun originalObservations(): List<DynamicTest> = GZIPInputStream(javaClass.getResourceAsStream("/x509-store/data.jsonl.gz")!!).bufferedReader().use {r->
        val m=Json.parseToJsonElement(r.readLine()).jsonObject
        val cases=r.lineSequence().map {Json.parseToJsonElement(it).jsonObject}.toList()
        assertEquals(m.getValue("cases").jsonPrimitive.int,cases.size)
        cases.mapIndexed {i,c->DynamicTest.dynamicTest("$i ${c.text("group")}") {assertEquals(c.getValue("expected"),observe(c.getValue("request").jsonObject),c.getValue("request").toString())}}
    }
    @Test fun ownedInputsAndMemoizedCertificateSpines() {
        val input=mutableListOf<X509SignedCertificate>()
        val owned=X509CertificateStore.make(input)
        input.add(certificate(JsonArray(listOf(JsonArray(emptyList()),JsonPrimitive("9")))))
        assertTrue(owned.isEmpty())
        var count=0
        val cached=X509CertificateStore.makeDeferred {sequence {count++;yield(lazy {certificate(JsonArray(listOf(JsonArray(emptyList()),JsonPrimitive("1"))))})}}
        repeat(2) {assertEquals(1,cached.certificates().count())};assertEquals(1,count)
        assertFalse((X509CertificateStore.empty()+X509CertificateStore.empty()).isEmpty())
        val dir=Files.createTempDirectory("kotdoc-store-type-")
        try {
            val regular=Files.createFile(dir.resolve("regular"))
            assertTrue(X509CertificateStore.doesFileExist(regular))
            assertFalse(X509CertificateStore.doesFileExist(dir))
            assertFalse(X509CertificateStore.doesFileExist(dir.resolve("missing")))
            if(java.io.File.separatorChar=='/') assertTrue(X509CertificateStore.doesFileExist(Path.of("/dev/null")))
        } finally {Files.walk(dir).use {it.sorted(Comparator.reverseOrder()).forEach(Files::delete)}}
    }
}
