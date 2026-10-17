package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
import java.io.File
import java.util.zip.GZIPInputStream
import org.junit.jupiter.api.*

object DrgDemandReplay {
    private class Custom(val mode: String,val events: MutableList<String>,val token: Int): CryptoDrg<Custom> {
        override fun randomBytesGenerate(length: Int): Pair<ByteArray,Custom> = error("use custom dictionary")
    }
    private val operations=CryptoDrgOperations<Custom>{n,generator->
        val g=generator.value;g.events+="pair:$n"
        if(g.mode=="root")error("pair demanded")
        lazy {g.events+="value";if(g.mode=="value")error("value demanded");byteArrayOf(1,2)} to
            lazy {g.events+="state";if(g.mode=="state")error("state demanded");Custom(g.mode,g.events,g.token+n)}
    }
    private fun pair(name: String,g: Custom): Pair<Lazy<Int>,Lazy<Custom>> {
        fun draw(n: Int)=CryptoPseudoRandom.getRandomBytes(n,operations)
        fun pure(n: Int)=CryptoPseudoRandom.pure<Custom,Int>(n)
        fun bad()=CryptoPseudoRandom<Custom,ByteArray>{g.events+="action";error("action demanded")}
        val action=when(name) {
            "withCallbackGood"->return CryptoRandom.withRandomBytes(lazyOf(g),7,operations){g.events+="mapper";42}
            "withCallbackBad"->return CryptoRandom.withRandomBytes<Custom,Int>(lazyOf(g),7,operations){g.events+="mapper";error("mapper demanded")}
            "mapCallbackGood"->draw(7).map{g.events+="mapper";42}
            "mapCallbackBad"->draw(7).map<Int>{g.events+="mapper";error("mapper demanded")}
            "applyCallbackBad"->CryptoPseudoRandom.apply(CryptoPseudoRandom.pure<Custom,(Lazy<ByteArray>)->Int>({g.events+="mapper";error("mapper demanded")}),draw(7))
            "bindCallbackGood"->draw(7).flatMap{g.events+="binder";pure(42)}
            "withIgnore"->return CryptoRandom.withRandomBytes(lazyOf(g),7,operations){42}
            "withDemand"->return CryptoRandom.withRandomBytes(lazyOf(g),7,operations){it.value.size}
            "get","mapDemand"->draw(7).map{it.value.size}
            "mapIgnore"->draw(7).map{42}
            "applyIgnore"->CryptoPseudoRandom.apply(CryptoPseudoRandom.pure<Custom,(Lazy<ByteArray>)->Int>({42}),draw(7))
            "applyDemand"->CryptoPseudoRandom.apply(CryptoPseudoRandom.pure<Custom,(Lazy<ByteArray>)->Int>({it.value.size}),draw(7))
            "applyTwoIgnore"->CryptoPseudoRandom.apply(draw(3).map{{_:Lazy<ByteArray>->42}},draw(7))
            "applyFunctionBad"->CryptoPseudoRandom.apply(CryptoPseudoRandom.pureDeferred<Custom,(Lazy<ByteArray>)->Int>(lazy{error("function demanded")}),draw(7))
            "bindIgnore"->draw(7).flatMap{pure(42)}
            "bindLazyDemand"->draw(7).flatMap{b->CryptoPseudoRandom.pureDeferred<Custom,Int>(lazy{b.value.size})}
            "bindStrictDemand"->draw(7).flatMap{pure(it.value.size)}
            "bindTwoIgnore"->draw(3).flatMap{draw(7).map{42}}
            "bindFunctionBad"->draw(7).flatMap< Int >{g.events+="bind";error("bind demanded")}
            "mapActionIgnore"->bad().map{42}
            "mapActionDemand"->bad().map{it.value.size}
            "bindActionIgnore"->bad().flatMap{pure(42)}
            "applyArgumentRoot"->CryptoPseudoRandom.apply(CryptoPseudoRandom.pure<Custom,(Lazy<ByteArray>)->Int>({42}),bad())
            "pure"->pure(42)
            else->error(name)
        }
        return CryptoRandom.withDRG(lazyOf(g),action)
    }
    fun observe(r: JsonObject): JsonElement {
        val events=mutableListOf<String>();val g=Custom(r.getValue("mode").jsonPrimitive.content,events,0)
        val pair=pair(r.getValue("operation").jsonPrimitive.content,g)
        return JsonArray(r.getValue("projections").jsonArray.map{projection->
            val result=try {(if(projection.jsonPrimitive.content=="value")pair.first.value else pair.second.value.token).toString()}catch(e:IllegalStateException){e.message!!.lineSequence().first()}
            buildJsonObject{put("result",result);put("events",JsonArray(events.map{JsonPrimitive(it)}))}
        })
    }
    @JvmStatic fun main(args:Array<String>){var n=0;GZIPInputStream(File(args[0]).inputStream()).bufferedReader().useLines{it.drop(1).forEach{line->val row=Json.parseToJsonElement(line).jsonObject;check(row.getValue("expected")==observe(row.getValue("request").jsonObject)){"row$n $row actual=${observe(row.getValue("request").jsonObject)}"};n++}};println("PASS $n independent custom DRG projection/sharing probes")}
}
class CryptoRandomDemandTest {
    private val lines by lazy{GZIPInputStream(javaClass.getResourceAsStream("/crypto-chacha-drg-demand/data.jsonl.gz")!!).bufferedReader().readLines()}
    @TestFactory fun originalCustomDictionaryProbes()=lines.drop(1).mapIndexed{i,line->DynamicTest.dynamicTest("custom/$i"){val row=Json.parseToJsonElement(line).jsonObject;Assertions.assertEquals(row.getValue("expected"),DrgDemandReplay.observe(row.getValue("request").jsonObject),row.getValue("request").toString())}}.stream()
    @Test fun originalExpectationCorpusRemainsUnchanged() {
        val root=File(System.getProperty("kotdoc.root"));val baseline=Json.parseToJsonElement(File(root,"tools/chacha-drg-reference/demand-fix/PreviousReview.json").readText()).jsonObject.getValue("unchangedOriginalCorpus").jsonObject
        Assertions.assertEquals(baseline.getValue("sha256").jsonPrimitive.content,java.security.MessageDigest.getInstance("SHA-256").digest(File(root,baseline.getValue("path").jsonPrimitive.content).readBytes()).toHexString())
    }
    @TestFactory fun sourcePins()=Json.parseToJsonElement(lines.first()).jsonObject.getValue("sources").jsonObject.map{(p,h)->DynamicTest.dynamicTest(p){Assertions.assertEquals(h.jsonPrimitive.content,java.security.MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),p).readBytes()).toHexString())}}.stream()
}
