package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
import java.io.File
import java.util.zip.GZIPInputStream
import org.junit.jupiter.api.*

object ChaChaPublicReplay {
    private fun JsonObject.bytes(name: String)=getValue(name).jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray()
    private fun bytes(value: ByteArray)=JsonArray(value.map{JsonPrimitive(it.toInt() and 255)})
    private fun counter(state: CryptoChaCha.ChaChaState<*>)=buildJsonObject{put("counter64",state.getCounter64().toString());put("counter32",state.getCounter32().toString())}
    private fun JsonObject.int(name: String,default: Int=0)=get(name)?.jsonPrimitive?.int?:default
    private fun demand(name: String): JsonElement {
        fun badState()=lazy<CryptoChaCha.State>{error("Prelude.undefined")}
        fun badSimple()=lazy<CryptoChaCha.StateSimple>{error("Prelude.undefined")}
        fun badBytes()=lazy<ByteArray>{error("Prelude.undefined")}
        fun badInt()=lazy<Int>{error("Prelude.undefined")}
        return when(name) {
            "generate-zero"->bytes(CryptoChaCha.generateDeferred(badState(),0).first)
            "generate-negative"->bytes(CryptoChaCha.generateDeferred(badState(),-1).first)
            "combine-empty"->bytes(CryptoChaCha.combineDeferred(badState(),byteArrayOf()).first)
            "initialize-key"->{CryptoChaCha.initializeDeferred(badInt(),lazyOf(byteArrayOf()),badBytes());JsonNull}
            "initialize-nonce"->{CryptoChaCha.initializeDeferred(badInt(),lazyOf(ByteArray(16)),lazyOf(byteArrayOf()));JsonNull}
            "initialize-rounds"->{CryptoChaCha.initialize(7,ByteArray(16),ByteArray(8));JsonNull}
            "initializeX-key"->{CryptoChaCha.initializeXDeferred(badInt(),lazyOf(byteArrayOf()),badBytes());JsonNull}
            "initializeX-nonce"->{CryptoChaCha.initializeXDeferred(badInt(),lazyOf(ByteArray(32)),lazyOf(byteArrayOf()));JsonNull}
            "initializeSimple-seed"->{CryptoChaCha.initializeSimple(byteArrayOf());JsonNull}
            "block-invalid"->{CryptoChaCha.generateSimpleBlockDeferred(7,badSimple());JsonNull}
            "simple-zero-bottom"->{CryptoChaCha.generateSimple(badSimple().value,0);JsonNull}
            else->error(name)
        }
    }
    fun observe(r: JsonObject): JsonElement = try {
        val kind=r.getValue("kind").jsonPrimitive.content
        if(kind=="demand") demand(r.getValue("name").jsonPrimitive.content) else {
            val simple=kind=="simple"
            var state=if(!simple)if(kind=="x")CryptoChaCha.initializeX(r.int("rounds"),r.bytes("key"),r.bytes("nonce"))else CryptoChaCha.initialize(r.int("rounds"),r.bytes("key"),r.bytes("nonce"))else null
            var ss=if(simple)CryptoChaCha.initializeSimple(r.bytes("seed"))else null
            val initial=counter(state?:ss!!)
            val observations=(r["steps"]?.jsonArray?:JsonArray(emptyList())).map{element->
                val step=element.jsonObject;val previous=counter(state?:ss!!);val previousProbe=if(simple)CryptoChaCha.generateSimple(ss!!,33).first else CryptoChaCha.generate(state!!,65).first;val op=step["op"]?.jsonPrimitive?.content?:"generate"
                val output: ByteArray
                if(simple) {
                    val old=ss!!
                    when(op) {
                        "set64"->{ss=old.setCounter64(step.getValue("value").jsonPrimitive.content.toULong());output=byteArrayOf()}
                        "set32"->{ss=old.setCounter32(step.getValue("value").jsonPrimitive.content.toUInt());output=byteArrayOf()}
                        else->{val pair=if(op=="block")CryptoChaCha.generateSimpleBlock(step.int("rounds",8),old)else CryptoChaCha.generateSimple(old,step.int("length"));output=pair.first;ss=pair.second}
                    }
                }else {
                    val old=state!!
                    when(op) {
                        "set64"->{state=old.setCounter64(step.getValue("value").jsonPrimitive.content.toULong());output=byteArrayOf()}
                        "set32"->{state=old.setCounter32(step.getValue("value").jsonPrimitive.content.toUInt());output=byteArrayOf()}
                        else->{val pair=if(op=="combine")CryptoChaCha.combine(old,step.bytes("bytes"))else CryptoChaCha.generate(old,step.int("length"));output=pair.first;state=pair.second}
                    }
                }
                buildJsonObject{put("bytes",bytes(output));put("state",counter(state?:ss!!));put("previous",previous);put("previousProbe",bytes(previousProbe))}
            }
            buildJsonObject{put("initial",initial);put("steps",JsonArray(observations))}
        }
    }catch(e: IllegalStateException){buildJsonObject{put("error",e.message!!.lineSequence().first())}}
    @JvmStatic fun main(args: Array<String>) {var n=0;GZIPInputStream(File(args[0]).inputStream()).bufferedReader().useLines{lines->lines.drop(1).forEach{line->val row=Json.parseToJsonElement(line).jsonObject;check(row.getValue("expected")==observe(row.getValue("request").jsonObject)){"row$n $row actual=${observe(row.getValue("request").jsonObject)}"};n++}};println("PASS $n public ChaCha observations")}
}
class CryptoChaChaTest {
    private val lines by lazy{GZIPInputStream(javaClass.getResourceAsStream("/crypto-chacha-public/data.jsonl.gz")!!).bufferedReader().readLines()}
    @TestFactory fun originalObservations()=lines.drop(1).mapIndexed{i,line->DynamicTest.dynamicTest("public/$i"){val row=Json.parseToJsonElement(line).jsonObject;Assertions.assertEquals(row.getValue("expected"),ChaChaPublicReplay.observe(row.getValue("request").jsonObject),row.getValue("request").toString())}}.stream()
    @TestFactory fun sourcePins()=Json.parseToJsonElement(lines.first()).jsonObject.getValue("sources").jsonObject.map{(path,expected)->DynamicTest.dynamicTest(path){Assertions.assertEquals(expected.jsonPrimitive.content,java.security.MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),path).readBytes()).toHexString())}}.stream()
    @Test fun ownershipAndIdentity() {
        val key=ByteArray(32){it.toByte()};val nonce=ByteArray(12){(it+17).toByte()};val state=CryptoChaCha.initialize(20,key,nonce);val expected=CryptoChaCha.generate(state,129).first
        key.fill(0);nonce.fill(0);Assertions.assertArrayEquals(expected,CryptoChaCha.generate(state,129).first)
        Assertions.assertSame(state,CryptoChaCha.generate(state,0).second);Assertions.assertSame(state,CryptoChaCha.generate(state,-1).second);Assertions.assertSame(state,CryptoChaCha.combine(state,byteArrayOf()).second)
        val set=state.setCounter64(state.getCounter64());Assertions.assertNotSame(state,set)
        val output=CryptoChaCha.generate(state,129).first;output.fill(0);Assertions.assertArrayEquals(expected,CryptoChaCha.generate(state,129).first)
        val seed=ByteArray(50){it.toByte()};val simple=CryptoChaCha.initializeSimple(seed);val bytes=CryptoChaCha.generateSimple(simple,33).first;seed.fill(0)
        Assertions.assertArrayEquals(bytes,CryptoChaCha.generateSimple(simple,33).first);Assertions.assertNotSame(simple,CryptoChaCha.generateSimple(simple,0).second)
        val a=CryptoChaCha.generateSimple(simple,1);val b=CryptoChaCha.generateSimple(a.second,15);Assertions.assertFalse((a.first+b.first).contentEquals(CryptoChaCha.generateSimple(simple,16).first))
        val message=ByteArray(129){it.toByte()};val original=message.copyOf();CryptoChaCha.combine(state,message);Assertions.assertArrayEquals(original,message)
        Assertions.assertThrows(NegativeArraySizeException::class.java){CryptoChaCha.generateSimple(simple,-1)}
        state.force();simple.force()
    }
}
