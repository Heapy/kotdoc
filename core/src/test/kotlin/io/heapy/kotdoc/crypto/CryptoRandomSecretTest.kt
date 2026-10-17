package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
import java.io.File
import java.util.zip.GZIPInputStream
import org.junit.jupiter.api.*

object DrgSecretReplay {
    fun observe(mode: String): JsonElement {
        var calls=0;val entropy=ByteArray(32){it.toByte()};var firstError: Throwable?=null
        val process=CryptoProbabilistic {
            calls++
            if(mode=="success" || (mode=="recover"&&calls>1))entropy else throw IllegalStateException("entropy failure")
        }
        val ops=CryptoChaChaDrg.operations
        val names=listOf("pure-before","zero-before","ignored-before","first","different","repeat","pure-after","zero-after","ignored-after","last")
        return JsonArray(names.map{name->
            val material=when(name){"different"->lazyOf(byteArrayOf(2));"last"->lazyOf(ByteArray(127){(it+1).toByte()});"first","repeat"->lazyOf(byteArrayOf());else->lazy{error("unused material demanded")}}
            val action: CryptoPseudoRandom<CryptoChaChaDrg,ByteArray> = when {
                name.startsWith("pure")->CryptoPseudoRandom.pure(byteArrayOf(42))
                name.startsWith("zero")->CryptoPseudoRandom.getRandomBytes(0,ops)
                name.startsWith("ignored")->CryptoPseudoRandom.getRandomBytes(1,ops).map{byteArrayOf(42)}
                else->CryptoPseudoRandom.getRandomBytes(if(name=="repeat")33 else if(name=="last")65 else 1,ops)
            }
            val value=try {
                val bytes=process.probabilisticFromDeferred(material,lazyOf(action))
                buildJsonObject{put("bytes",JsonArray(bytes.map{JsonPrimitive(it.toInt()and 255)}))}
            }catch(e:IllegalStateException) {
                if(firstError==null)firstError=e
                buildJsonObject{put("error",e.message);put("sameAsFirst",firstError===e)}
            }
            // A host-owned mutable entropy response cannot mutate retained secret storage.
            if(name=="first")entropy.fill(0)
            buildJsonObject{put("operation",name);put("value",value);put("entropyCalls",calls)}
        })
    }
}
class CryptoRandomSecretTest {
    private val lines by lazy{GZIPInputStream(javaClass.getResourceAsStream("/crypto-chacha-drg-secret/data.jsonl.gz")!!).bufferedReader().readLines()}
    @TestFactory fun originalProcessSecretProbes()=lines.drop(1).map{line->val row=Json.parseToJsonElement(line).jsonObject;val mode=row.getValue("request").jsonObject.getValue("mode").jsonPrimitive.content;DynamicTest.dynamicTest(mode){Assertions.assertEquals(row.getValue("expected"),DrgSecretReplay.observe(mode))}}.stream()
    @Test fun previousExpectedCorporaUnchanged() {
        val root=File(System.getProperty("kotdoc.root"));val baselines=Json.parseToJsonElement(File(root,"tools/chacha-drg-reference/secret-fix/PreviousReview.json").readText()).jsonObject.getValue("unchangedFixtures").jsonObject
        for((p,h)in baselines)Assertions.assertEquals(h.jsonPrimitive.content,java.security.MessageDigest.getInstance("SHA-256").digest(File(root,p).readBytes()).toHexString())
    }
    @TestFactory fun sourcePins()=Json.parseToJsonElement(lines.first()).jsonObject.getValue("sources").jsonObject.map{(p,h)->DynamicTest.dynamicTest(p){Assertions.assertEquals(h.jsonPrimitive.content,java.security.MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),p).readBytes()).toHexString())}}.stream()
}
