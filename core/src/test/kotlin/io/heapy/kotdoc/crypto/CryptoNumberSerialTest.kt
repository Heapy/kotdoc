package io.heapy.kotdoc.crypto

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.util.stream.Stream
import java.util.zip.GZIPInputStream

class CryptoNumberSerialTest {
    private val zero = BigInteger.ZERO
    private val one = BigInteger.ONE
    private fun JsonObject.text(k: String) = get(k)?.jsonPrimitive?.content ?: ""
    private fun argument(r: JsonObject, k: String): Lazy<BigInteger> = lazy {
        val v=r.text(k)
        if(v.startsWith("bottom:")) error(v.removePrefix("bottom:"))
        if(v.isEmpty()) zero else v.toBigInteger()
    }
    private fun bytes(r: JsonObject) = r.getValue("bytes").jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray()
    private fun json(a: ByteArray) = JsonArray(a.map { JsonPrimitive(it.toInt() and 255) })
    private fun integer(n: BigInteger) = buildJsonObject { put("integer",n.toString()) }
    private fun integer(n: Int) = integer(BigInteger.valueOf(n.toLong()))
    private fun octets(part: String, bs: ByteArray) = buildJsonObject {
        if(part=="length") put("length",bs.size) else put("bytes",json(bs))
    }
    private fun pair(part: String, p: Pair<BigInteger,BigInteger>): JsonElement = when(part) {
        "constructor" -> JsonPrimitive("pair")
        "first" -> integer(p.first)
        "second" -> integer(p.second)
        else -> buildJsonObject { put("pair",JsonArray(listOf(p.first,p.second).map { JsonPrimitive(it.toString()) })) }
    }
    private fun property(value: Boolean) = buildJsonObject { put("property",value) }
    private fun observe(r: JsonObject): JsonElement {
        val part=r.text("part")
        if(part=="ignore") return JsonPrimitive("ignored")
        val a=argument(r,"a"); val b=argument(r,"b"); val len=lazy { argument(r,"length").value.toInt() }
        val serial=if(r.text("endian")=="le") CryptoSerializeLE else CryptoSerialize
        return when(r.text("op")) {
            "numBits" -> integer(CryptoNumberBasic.numBits(a.value))
            "numBytes" -> integer(CryptoNumberBasic.numBytes(a.value))
            "log2" -> integer(CryptoNumberBasic.log2(a.value))
            "sqrti" -> pair(part,CryptoNumberBasic.sqrti(a.value))
            "asPowerOf2AndOdd" -> {
                val p=CryptoNumberBasic.asPowerOf2AndOdd(a.value)
                pair(part,BigInteger.valueOf(p.first.toLong()) to p.second)
            }
            "gcde" -> {
                val p=CryptoNumberBasic.gcdeDeferred(a,b)
                when(part) {
                    "constructor" -> JsonPrimitive("triple")
                    "first" -> integer(p.x)
                    "second" -> integer(p.y)
                    "third" -> integer(p.gcd)
                    else -> buildJsonObject { put("triple",JsonArray(listOf(p.x,p.y,p.gcd).map { JsonPrimitive(it.toString()) })) }
                }
            }
            "areEven" -> {
                val sequence=sequence {
                    for(v in r.getValue("values").jsonArray) yield(argument(buildJsonObject { put("n",v) },"n"))
                    if(r.text("tail")=="bottom") error("tail")
                }
                buildJsonObject { put("boolean",CryptoNumberBasic.areEvenDeferred(sequence)) }
            }
            "i2osp" -> octets(part,serial.i2osp(a.value))
            "i2ospOf" -> when(val result=serial.i2ospOfDeferred(len,a)) {
                CryptoMaybe.None -> buildJsonObject { put("maybe","none") }
                is CryptoMaybe.Some -> buildJsonObject {
                    put("maybe","some")
                    if(part!="constructor") put("value",octets(part,result.value))
                }
            }
            "i2ospOf_" -> octets(part,serial.i2ospOfUnsafeDeferred(len,a))
            "os2ip" -> integer(serial.os2ip(bytes(r)))
            "rawWrite","rawWriteOf","rawRead" -> {
                val data=byteArrayOf(17,34,51)+bytes(r)+byteArrayOf(68,85,102,119)
                if(r.text("op")=="rawRead") buildJsonObject {
                    put("integer",serial.rawOs2ip(data,3,len.value).toString()); put("buffer",json(data))
                } else {
                    val ret=serial.rawWriteDeferred(a,data,3,len,r.text("op")=="rawWriteOf")
                    buildJsonObject { put("ret",ret); put("buffer",json(data)) }
                }
            }
            "numberProperty" -> {
                val n=a.value
                property(when(r.text("kind")) {
                    "num-bits" -> {
                        val i=n.toInt(); val p=one.shiftLeft(i)
                        CryptoNumberBasic.numBits(p-one)==i && CryptoNumberBasic.numBits(p)==i+1 && CryptoNumberBasic.numBits(p+(p-one))==i+1
                    }
                    "num-bits2" -> !n.testBit(CryptoNumberBasic.numBits(n)) && n.testBit(CryptoNumberBasic.numBits(n)-1)
                    "num-bytes" -> {
                        var v=n; var count=0
                        while(v>zero) { count++; v/=BigInteger.valueOf(256) }
                        CryptoNumberBasic.numBytes(n)==count
                    }
                    "num-bytes-small" -> listOf(0L,1L,255L,256L,257L,65535L,65536L).map { CryptoNumberBasic.numBytes(BigInteger.valueOf(it)) }==listOf(0,1,1,2,2,2,3)
                    "as-power-of-2-and-odd" -> { val p=CryptoNumberBasic.asPowerOf2AndOdd(n); n==one.shiftLeft(p.first)*p.second }
                    "marshalling-be" -> n==CryptoSerialize.os2ip(CryptoSerialize.i2osp(n))
                    "marshalling-le" -> n==CryptoSerializeLE.os2ip(CryptoSerializeLE.i2osp(n))
                    "be-rev-le" -> n==CryptoSerializeLE.os2ip(CryptoSerialize.i2osp(n).reversedArray())
                    "be-rev-le-40" -> n==CryptoSerializeLE.os2ip(CryptoSerialize.i2ospOf_(40,n).reversedArray())
                    "le-rev-be" -> n==CryptoSerialize.os2ip(CryptoSerializeLE.i2osp(n).reversedArray())
                    "le-rev-be-40" -> n==CryptoSerialize.os2ip(CryptoSerializeLE.i2ospOf_(40,n).reversedArray())
                    else -> error("unknown property")
                })
            }
            else -> error("unknown operation")
        }
    }

    @TestFactory
    fun upstream(): Stream<DynamicTest> {
        val reader=GZIPInputStream(javaClass.getResourceAsStream("/crypto-number-serial/data.jsonl.gz")!!).bufferedReader()
        reader.readLine(); var index=0
        return reader.lines().map { line ->
            val id=index++
            DynamicTest.dynamicTest("$id crypton number/serialization") {
                val row=Json.parseToJsonElement(line).jsonObject
                val actual=try { observe(row.getValue("request").jsonObject) } catch(e: RuntimeException) {
                    buildJsonObject { put("fatal",e.message?.lineSequence()?.first() ?: e.javaClass.simpleName) }
                }
                assertEquals(row.getValue("expected"),actual,"$id ${row.text("group")} ${row.getValue("request")}")
            }
        }.onClose { reader.close() }
    }
}
