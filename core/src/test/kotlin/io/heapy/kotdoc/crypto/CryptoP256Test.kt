package io.heapy.kotdoc.crypto

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import java.util.stream.Stream
import java.util.zip.GZIPInputStream

internal object P256PublicFixtures {
    val lines: List<String> by lazy { GZIPInputStream(javaClass.getResourceAsStream("/crypto-p256-public/data.jsonl.gz")!!).bufferedReader().use{it.readLines()} }
    val manifest by lazy { Json.parseToJsonElement(lines.first()).jsonObject }
    fun JsonObject.text(k: String)=getValue(k).jsonPrimitive.content
    private fun bytes(a: ByteArray)=JsonArray(a.map{JsonPrimitive(it.toInt() and 255)})
    private fun integer(r: JsonObject,k: String)=r.text(k).toBigInteger()
    private fun data(r: JsonObject)=r.getValue("bytes").jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray()
    private fun scalar(p: CryptoP256,a: CryptoP256.Scalar)=buildJsonObject {
        put("binary",bytes(p.scalarToBinary(a)));put("integer",p.scalarToInteger(a).toString());put("show",a.show());put("raw",a.withByteArray{bytes(it)})
    }
    private fun point(p: CryptoP256,a: CryptoP256.Point)=buildJsonObject {
        val(x,y)=p.pointToIntegers(a);put("binary",bytes(p.pointToBinary(a)));put("x",x.toString());put("y",y.toString());put("show",a.show());put("infinity",p.pointIsAtInfinity(a));put("valid",p.pointIsValid(a))
    }
    private fun <T> failable(value: CryptoP256.Failable<T>,convert:(T)->JsonElement):JsonObject=when(value){
        is CryptoP256.Failable.Passed->buildJsonObject{put("value",convert(value.value))}
        is CryptoP256.Failable.Failed->buildJsonObject{put("error",value.error.name)}
    }
    private fun s(p:CryptoP256,r:JsonObject,k:String):CryptoP256.Scalar=when(val x=p.scalarFromInteger(integer(r,k))){is CryptoP256.Failable.Passed->x.value;is CryptoP256.Failable.Failed->error(x.error.name)}
    private fun pt(p:CryptoP256,r:JsonObject,k:String):CryptoP256.Point{val a=r.getValue(k).jsonArray;return p.pointFromIntegers(a[0].jsonPrimitive.content.toBigInteger(),a[1].jsonPrimitive.content.toBigInteger())}
    fun observe(p:CryptoP256,r:JsonObject):JsonElement=try {
        when(val op=r.text("op")){
            "scalarGenerateThrow"->scalar(p,p.scalarGenerate({error("rng-failure")}))
            "scalarFromBinary"->failable(p.scalarFromBinary(data(r))){scalar(p,it)}
            "scalarFromInteger"->failable(p.scalarFromInteger(integer(r,"a"))){scalar(p,it)}
            "scalarZero"->scalar(p,p.scalarZero)
            "scalarN"->scalar(p,p.scalarN)
            "scalar"->scalar(p,s(p,r,"a"))
            "scalarReduce"->scalar(p,p.scalarReduce(s(p,r,"a")))
            "scalarAdd"->scalar(p,p.scalarAdd(s(p,r,"a"),s(p,r,"b")))
            "scalarSub"->scalar(p,p.scalarSub(s(p,r,"a"),s(p,r,"b")))
            "scalarMul"->scalar(p,p.scalarMul(s(p,r,"a"),s(p,r,"b")))
            "scalarInv"->scalar(p,p.scalarInv(s(p,r,"a")))
            "scalarInvSafe"->scalar(p,p.scalarInvSafe(s(p,r,"a")))
            "scalarCmp"->JsonPrimitive(when(p.scalarCmp(s(p,r,"a"),s(p,r,"b"))){-1->"LT";0->"EQ";else->"GT"})
            "scalarIsZero"->JsonPrimitive(p.scalarIsZero(s(p,r,"a")))
            "scalarEq"->JsonPrimitive(s(p,r,"a")==s(p,r,"b"))
            "scalarShow"->JsonPrimitive(s(p,r,"a").show(integer(r,"precedence").toInt()))
            "scalarForce"->{s(p,r,"a").force();JsonNull}
            "scalarGenerate"->{val ns=mutableListOf<Int>();val a=p.scalarGenerate({ns+=it;data(r)});buildJsonObject{put("value",scalar(p,a));put("requests",JsonArray(ns.map(::JsonPrimitive)))}}
            "pointBase"->point(p,p.pointBase)
            "toPoint"->point(p,p.toPoint(s(p,r,"a")))
            "pointFromIntegers"->point(p,p.pointFromIntegers(integer(r,"x"),integer(r,"y")))
            "unsafePointFromBinary"->failable(p.unsafePointFromBinary(data(r))){point(p,it)}
            "pointFromBinary"->failable(p.pointFromBinary(data(r))){point(p,it)}
            "point"->point(p,pt(p,r,"p"))
            "pointAdd"->point(p,p.pointAdd(pt(p,r,"p"),pt(p,r,"q")))
            "pointNegate"->point(p,p.pointNegate(pt(p,r,"p")))
            "pointMul"->point(p,p.pointMul(s(p,r,"a"),pt(p,r,"p")))
            "pointDh"->bytes(p.pointDh(s(p,r,"a"),pt(p,r,"p")))
            "pointsMulVarTime"->point(p,p.pointsMulVarTime(s(p,r,"a"),s(p,r,"b"),pt(p,r,"p")))
            "pointIsValid"->JsonPrimitive(p.pointIsValid(pt(p,r,"p")))
            "pointIsAtInfinity"->JsonPrimitive(p.pointIsAtInfinity(pt(p,r,"p")))
            "pointX"->p.pointX(pt(p,r,"p"))?.let{scalar(p,it)}?:JsonNull
            "pointShow"->JsonPrimitive(pt(p,r,"p").show(integer(r,"precedence").toInt()))
            "pointEq"->JsonPrimitive(pt(p,r,"p")==pt(p,r,"q"))
            "pointForce"->{pt(p,r,"p").force();JsonNull}
            "originalInvSafeMul"->{val a=s(p,r,"a");scalar(p,p.scalarMul(a,p.scalarInvSafe(a)))}
            "originalMarshalPoint"->{val a=p.pointFromIntegers(integer(r,"a"),integer(r,"b"));failable(p.unsafePointFromBinary(p.pointToBinary(a))){point(p,it)}}
            "originalPointAdd"->point(p,p.pointAdd(p.toPoint(s(p,r,"a")),p.toPoint(s(p,r,"b"))))
            "originalPointAddInfinity"->point(p,p.pointAdd(p.pointFromIntegers(BigInteger.ZERO,BigInteger.ZERO),p.toPoint(s(p,r,"a"))))
            "originalPointAddInverse"->{val a=p.toPoint(s(p,r,"a"));point(p,p.pointAdd(a,p.pointNegate(a)))}
            "originalPointNegate"->point(p,p.pointNegate(p.toPoint(s(p,r,"a"))))
            "originalPointMul"->point(p,p.pointMul(p.scalarReduce(s(p,r,"a")),p.toPoint(p.scalarReduce(s(p,r,"b")))))
            else->error(op)
        }
    } catch(e:IllegalStateException){buildJsonObject{put("exception",e.message?:"")}}
    fun observations(original:Boolean):Stream<DynamicTest>{
        val tests=mutableListOf<DynamicTest>()
        for((index,line)in lines.drop(1).withIndex()){
            val c=Json.parseToJsonElement(line).jsonObject;val group=c.text("group")
            if(group.startsWith("original-")!=original)continue
            val r=c.getValue("request").jsonObject
            for(bits in listOf(32,64))tests+=DynamicTest.dynamicTest("$index/$bits $group ${r.text("op")}"){
                assertEquals(c.getValue("expected"),observe(CryptoP256(bits),r),r.toString())
            }
        }
        return tests.stream()
    }
}
class CryptoP256Test {
    @TestFactory fun independentPublicObservations()=P256PublicFixtures.observations(false)
    @TestFactory fun sourcePins():Stream<DynamicTest>{
        val root=File(System.getProperty("kotdoc.root"))
        return P256PublicFixtures.manifest.getValue("sources").jsonObject.map{(path,expected)->DynamicTest.dynamicTest(path){
            val hash=MessageDigest.getInstance("SHA-256").digest(File(root,path).readBytes()).joinToString(""){(it.toInt() and 255).toString(16).padStart(2,'0')}
            assertEquals(expected.jsonPrimitive.content,hash,path)
        }}.stream()
    }
    @Test fun immutableOwnershipAndInfinityIdentity(){for(bits in listOf(32,64)){
        val p=CryptoP256(bits);val input=ByteArray(32){it.toByte()};val copy=input.copyOf();val s=(p.scalarFromBinary(input)as CryptoP256.Failable.Passed).value
        input.fill(0);assertArrayEquals(copy,p.scalarToBinary(s));p.scalarToBinary(s).fill(0);assertArrayEquals(copy,p.scalarToBinary(s));s.withByteArray{it.fill(0)};assertArrayEquals(copy,p.scalarToBinary(s))
        val bytes=p.pointToBinary(p.pointBase);val q=(p.unsafePointFromBinary(bytes)as CryptoP256.Failable.Passed).value;bytes.fill(0);assertEquals(p.pointBase,q);p.pointToBinary(q).fill(0);assertEquals(p.pointBase,q)
        val zero=p.pointFromIntegers(BigInteger.ZERO,BigInteger.ZERO);assertSame(q,p.pointAdd(zero,q));assertSame(q,p.pointAdd(q,zero));assertSame(zero,p.pointAdd(zero,zero))
        val audit=CryptoP256.Audit();val dh=p.pointDh(p.scalarN,q,audit);assertArrayEquals(ByteArray(32),dh);assertEquals(listOf("allocate-output:32","allocate-scrubbed:64","serialize-x:32","release-scrubbed:64"),audit.effects);assertArrayEquals(ByteArray(64),audit.temporaryContents.last())
    }}
    @Test fun randomCallbackEffects(){val p=CryptoP256();for(size in listOf(0,31,32,33)){
        var calls=0;val requests=mutableListOf<Int>();try{p.scalarGenerate({calls++;requests+=it;ByteArray(size)})}catch(e:IllegalStateException){assertEquals("scalarGenerate: assumption failed",e.message)};assertEquals(1,calls);assertEquals(listOf(32),requests)
    };val failure=IllegalStateException("provider failure");assertSame(failure,assertThrows(IllegalStateException::class.java){p.scalarGenerate({throw failure})})}
}
