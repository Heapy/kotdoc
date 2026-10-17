package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
import java.io.File
import java.math.BigInteger
import java.util.zip.GZIPInputStream
import org.junit.jupiter.api.*

object DrgReplay {
    private fun JsonObject.bytes(k: String)=getValue(k).jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray()
    private fun JsonObject.number(k: String,d: Int=0)=get(k)?.jsonPrimitive?.int?:d
    private fun JsonObject.text(k: String,d: String="")=get(k)?.jsonPrimitive?.content?:d
    private fun json(b: ByteArray)=JsonArray(b.map{JsonPrimitive(it.toInt()and 255)})
    private fun generator(r: JsonObject): CryptoChaChaDrg=when(r.text("seedMode","bytes")) {
        "words"->{val w=r.getValue("words").jsonArray.map{it.jsonPrimitive.content.toULong()};CryptoRandom.drgNewTest(w[0],w[1],w[2],w[3],w[4])}
        "integer"->CryptoRandom.drgNewSeed(CryptoRandom.seedFromInteger(BigInteger(r.text("integer","0"))))
        else->CryptoChaChaDrg.initialize(r.bytes("seed"))
    }
    private class Traced(val generator: CryptoChaChaDrg,val trace: List<Int>): CryptoDrg<Traced> {
        override fun randomBytesGenerate(length: Int): Pair<ByteArray,Traced> {val p=generator.randomBytesGenerate(length);return p.first to Traced(p.second,trace+length)}
    }
    private val tracedOps=CryptoDrgOperations<Traced>{n,g->val p=lazy{g.value.randomBytesGenerate(n)};lazy{p.value.first}to lazy{p.value.second}}
    private fun <G:CryptoDrg<G>> action(r: JsonObject,ops: CryptoDrgOperations<G>): CryptoPseudoRandom<G,List<ByteArray>> {
        fun draw(n: Int)=CryptoPseudoRandom.getRandomBytes(n,ops)
        return when(r.text("action","sequence")) {
            "pure"->CryptoPseudoRandom.pure(listOf(byteArrayOf(42)))
            "map"->draw(r.number("length",17)).map{listOf(it.value.reversedArray())}
            "ignoreMap"->draw(r.number("length",17)).map{listOf(byteArrayOf(42))}
            "apply"->CryptoPseudoRandom.apply(draw(r.number("length",17)).map{a->{b: Lazy<ByteArray>->listOf(a.value+b.value)}},draw(r.number("other",33)))
            "bind"->draw(r.number("length",1)).flatMap{a->val bytes=a.value;draw(if(bytes.isEmpty())0 else(bytes[0].toInt()and 255)%65).map{b->listOf(bytes,b.value)}}
            else->(r["lengths"]?.jsonArray?:JsonArray(emptyList())).fold(CryptoPseudoRandom.pure<G,List<ByteArray>>(emptyList())){acc,n->acc.flatMap{a->draw(n.jsonPrimitive.int).map{b->a.value+listOf(b.value)}}}
        }
    }
    private fun values(a: List<ByteArray>)=JsonArray(a.map{json(it)})
    private fun seedJson(seed: CryptoRandom.Seed)=buildJsonObject{put("bytes",json(seed.toByteArray()));put("integer",CryptoRandom.seedToInteger(seed).toString())}
    private fun demand(name: String): JsonElement {
        val bad=lazy<CryptoChaChaDrg>{error("Prelude.undefined")}
        val ops=CryptoChaChaDrg.operations
        return when(name) {
            "drg-zero"->json(CryptoChaChaDrg.generateDeferred(0,bad).first)
            "drg-negative"->json(CryptoChaChaDrg.generateDeferred(-1,bad).first)
            "prob-ignore"->JsonPrimitive(CryptoProbabilistic{error("entropy demanded")}.probabilisticFromDeferred(lazy{error("material demanded")},lazyOf(CryptoPseudoRandom.pure<CryptoChaChaDrg,Int>(42))))
            "prob-zero"->json(CryptoProbabilistic{error("entropy demanded")}.probabilisticFromDeferred(lazy{error("material demanded")},lazyOf(CryptoPseudoRandom.getRandomBytes(0,ops))))
            "with-ignore"->JsonPrimitive(CryptoRandom.withRandomBytes(bad,17,ops){42}.first.value)
            "pure-ignore"->JsonPrimitive(CryptoRandom.withDRG(bad,CryptoPseudoRandom.pure<CryptoChaChaDrg,Int>(42)).first.value)
            "apply-ignore"->JsonPrimitive(CryptoRandom.withDRG(bad,CryptoPseudoRandom.apply(CryptoPseudoRandom.pure<CryptoChaChaDrg,(Lazy<ByteArray>)->Int>({42}),CryptoPseudoRandom.getRandomBytes(17,ops))).first.value)
            "bind-ignore"->JsonPrimitive(CryptoRandom.withDRG(bad,CryptoPseudoRandom.getRandomBytes(17,ops).flatMap{CryptoPseudoRandom.pure<CryptoChaChaDrg,Int>(42)}).first.value)
            "map-ignore"->JsonPrimitive(CryptoPseudoRandom.withDRG(bad,CryptoPseudoRandom.getRandomBytes(17,ops).map{42}).first.value)
            else->error(name)
        }
    }
    fun observe(r: JsonObject): JsonElement=try {
        when(r.text("mode","drg")) {
            "seedInteger"->seedJson(CryptoRandom.seedFromInteger(BigInteger(r.text("integer","0"))))
            "seedBinary"->when(val seed=CryptoRandom.seedFromBinary(r.bytes("seed"))) {is CryptoEcc.Failable.Passed->seedJson(seed.value);is CryptoEcc.Failable.Failed->buildJsonObject{put("error",seed.error.name)}}
            "insecureSeedFormula"->buildJsonObject{put("bytes",json(CryptoRandom.seedNew({r.bytes("entropy")},true).toByteArray()))}
            "seedNew"->{var g=generator(r);val trace=mutableListOf<Int>();val seed=CryptoRandom.seedNew({n->trace+=n;g.randomBytesGenerate(n).let{g=it.second;it.first}},r["insecure"]?.jsonPrimitive?.boolean?:false);buildJsonObject{put("bytes",json(seed.toByteArray()));put("trace",JsonArray(trace.map{JsonPrimitive(it)}));put("next",json(g.randomBytesGenerate(65).first))}}
            "seedProvider"->{val trace=mutableListOf<Int>();val seed=CryptoRandom.seedNew({n->trace+=n;r.bytes("entropy")},r["insecure"]?.jsonPrimitive?.boolean?:false);buildJsonObject{put("bytes",json(seed.toByteArray()));put("trace",JsonArray(trace.map{JsonPrimitive(it)}))}}
            "drgNew"->{var g=generator(r);val trace=mutableListOf<Int>();val drg=CryptoRandom.drgNew({n->trace+=n;g.randomBytesGenerate(n).let{g=it.second;it.first}},r["insecure"]?.jsonPrimitive?.boolean?:false);buildJsonObject{put("bytes",json(drg.randomBytesGenerate(129).first));put("trace",JsonArray(trace.map{JsonPrimitive(it)}));put("next",json(g.randomBytesGenerate(65).first))}}
            "withRandomBytes"->{val initial=Traced(generator(r),emptyList());val pair=CryptoRandom.withRandomBytes(lazyOf(initial),r.number("length"),tracedOps){b->if(r["ignore"]?.jsonPrimitive?.boolean==true)byteArrayOf(42)else b.value.reversedArray()};val value=pair.first.value;val final=pair.second.value;buildJsonObject{put("value",json(value));put("trace",JsonArray(final.trace.map{JsonPrimitive(it)}));put("next",json(final.generator.randomBytesGenerate(65).first))}}
            "probabilistic"->values(CryptoProbabilistic{n->check(n==32);r.bytes("secret")}.probabilisticFrom(r.bytes("material"),action(r,CryptoChaChaDrg.operations)))
            "demand"->demand(r.text("name"))
            else->{val initial=generator(r);val result=CryptoPseudoRandom.withDRG(Traced(initial,emptyList()),action(r,tracedOps));buildJsonObject{put("value",values(result.first.value));put("trace",JsonArray(result.second.value.trace.map{JsonPrimitive(it)}));put("next",json(result.second.value.generator.randomBytesGenerate(65).first));put("previous",json(initial.randomBytesGenerate(65).first))}}
        }
    }catch(e:IllegalStateException){buildJsonObject{put("error",e.message!!.lineSequence().first())}}
    @JvmStatic fun main(args:Array<String>){var n=0;GZIPInputStream(File(args[0]).inputStream()).bufferedReader().useLines{it.drop(1).forEach{line->val row=Json.parseToJsonElement(line).jsonObject;val actual=observe(row.getValue("request").jsonObject);check(actual==row.getValue("expected")){"row$n $row actual=$actual"};n++}};println("PASS $n DRG/Probabilistic observations")}
}
class CryptoRandomTest {
    private val lines by lazy{GZIPInputStream(javaClass.getResourceAsStream("/crypto-chacha-drg/data.jsonl.gz")!!).bufferedReader().readLines()}
    @TestFactory fun originalObservations()=lines.drop(1).mapIndexed{i,line->DynamicTest.dynamicTest("source/$i"){val row=Json.parseToJsonElement(line).jsonObject;Assertions.assertEquals(row.getValue("expected"),DrgReplay.observe(row.getValue("request").jsonObject),row.getValue("request").toString())}}.stream()
    @TestFactory fun sourcePins()=Json.parseToJsonElement(lines.first()).jsonObject.getValue("sources").jsonObject.map{(p,h)->DynamicTest.dynamicTest(p){Assertions.assertEquals(h.jsonPrimitive.content,java.security.MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),p).readBytes()).toHexString())}}.stream()
    @Test fun ownershipLifetimeAndLazyState() {
        val seed=ByteArray(40){it.toByte()};val g=CryptoChaChaDrg.initialize(seed);val expected=g.randomBytesGenerate(65).first;seed.fill(0);Assertions.assertArrayEquals(expected,g.randomBytesGenerate(65).first)
        Assertions.assertSame(g,g.randomBytesGenerate(0).second);Assertions.assertSame(g,g.randomBytesGenerate(-1).second)
        val out=g.randomBytesGenerate(65).first;out.fill(0);Assertions.assertArrayEquals(expected,g.randomBytesGenerate(65).first)
        val original=ByteArray(40){it.toByte()};val s=(CryptoRandom.seedFromBinary(original)as CryptoEcc.Failable.Passed).value;original.fill(0);val copy=s.toByteArray();copy.fill(0);Assertions.assertEquals(BigInteger(1,ByteArray(40){it.toByte()}),CryptoRandom.seedToInteger(s))
        val requests=mutableListOf<Int>();val entropy=ByteArray(32){(it+41).toByte()};val process=CryptoProbabilistic{n->requests+=n;entropy}
        val pure=CryptoPseudoRandom.pure<CryptoChaChaDrg,Int>(42);Assertions.assertEquals(42,process.probabilisticFrom(byteArrayOf(),pure));Assertions.assertTrue(requests.isEmpty())
        val draw=CryptoPseudoRandom.getRandomBytes(129,CryptoChaChaDrg.operations);val a=process.probabilisticFrom(byteArrayOf(1),draw);entropy.fill(0)
        Assertions.assertArrayEquals(a,process.probabilisticFrom(byteArrayOf(1),draw));Assertions.assertFalse(a.contentEquals(process.probabilisticFrom(byteArrayOf(2),draw)));Assertions.assertEquals(listOf(32),requests)
        val noState=lazy<CryptoChaChaDrg>{error("state forced")};val ignored=CryptoPseudoRandom.apply(CryptoPseudoRandom.pure<CryptoChaChaDrg,(Lazy<ByteArray>)->Int>({42}),CryptoPseudoRandom.getRandomBytes(7,CryptoChaChaDrg.operations));Assertions.assertEquals(42,CryptoPseudoRandom.withDRG(noState,ignored).first.value)
        val bind=CryptoPseudoRandom.getRandomBytes(7,CryptoChaChaDrg.operations).flatMap{CryptoPseudoRandom.pure<CryptoChaChaDrg,Int>(42)};Assertions.assertEquals(42,CryptoPseudoRandom.withDRG(noState,bind).first.value)
        g.force()
    }
}
