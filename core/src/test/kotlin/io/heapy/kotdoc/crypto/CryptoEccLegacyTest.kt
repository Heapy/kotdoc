package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.x509.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.*
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import java.util.stream.Stream
import java.util.zip.GZIPInputStream

class CryptoEccLegacyTest {
    private val zero=BigInteger.ZERO
    private val one=BigInteger.ONE
    private val lines by lazy { GZIPInputStream(javaClass.getResourceAsStream("/crypto-ecc-legacy/data.jsonl.gz")!!).bufferedReader().use { it.readLines() } }
    private val manifest by lazy { Json.parseToJsonElement(lines.first()).jsonObject }
    private fun JsonObject.text(k: String)=getValue(k).jsonPrimitive.content
    private fun JsonObject.integer(k: String)=text(k).toBigInteger()
    private fun point(p: EccPoint): JsonElement=when(p) { X509EcPoint.Infinity->JsonNull;is X509EcPoint.Affine->JsonArray(listOf(JsonPrimitive(p.x.toString()),JsonPrimitive(p.y.toString()))) }
    private fun pt(r: JsonObject,k: String): EccPoint { val v=r.getValue(k);return if(v==JsonNull) X509EcPoint.Infinity else v.jsonArray.let { X509EcPoint.Affine(it[0].jsonPrimitive.content.toBigInteger(),it[1].jsonPrimitive.content.toBigInteger()) } }
    private fun bytes(b: ByteArray)=JsonArray(b.map { JsonPrimitive(it.toInt() and 255) })
    private fun doubleAndAdd(c: EccCurve,k: BigInteger,p: EccPoint): EccPoint {var acc: EccPoint=X509EcPoint.Infinity;for(i in k.bitLength()-1 downTo 0) {acc=CryptoEccPrim.pointDouble(c,acc);if(k.testBit(i))acc=CryptoEccPrim.pointAdd(c,acc,p)};return acc}
    private fun property(c: EccCurve,r: JsonObject): Boolean {
        val a=r.integer("a");val b=r.integer("b");val p=pt(r,"p");val q=pt(r,"q");val n=c.common.order;val g=c.common.generator
        return when(r.text("family")) {
            "weight-add"->CryptoEccPrim.pointBaseMul(c,a+b)==CryptoEccPrim.pointAdd(c,CryptoEccPrim.pointBaseMul(c,a),CryptoEccPrim.pointBaseMul(c,b))
            "point-add"->CryptoEccPrim.pointBaseMul(c,(a+b).mod(n))==CryptoEccPrim.pointAdd(c,CryptoEccPrim.pointBaseMul(c,a),CryptoEccPrim.pointBaseMul(c,b))
            "point-negate-add"->CryptoEccPrim.pointAdd(c,p,CryptoEccPrim.pointNegate(c,p))==X509EcPoint.Infinity
            "point-negate-negate"->CryptoEccPrim.pointNegate(c,CryptoEccPrim.pointNegate(c,p))==p
            "point-mul-mul"->CryptoEccPrim.pointMul(c,a*b,p)==CryptoEccPrim.pointMul(c,a,CryptoEccPrim.pointMul(c,b,p))
            "point-mul-matches-double-and-add"->{val k=one+a.mod(n-one);CryptoEccPrim.pointMul(c,k,g)==doubleAndAdd(c,k,g)}
            "scalar-arithmetic"->CryptoEccPrim.scalarAdd(c,a,b)==(a+b).mod(n) && CryptoEccPrim.scalarMul(c,a,b)==(a*b).mod(n)
            "double-scalar-mult"->CryptoEccPrim.pointAddTwoMuls(c,a,p,b,q)==CryptoEccPrim.pointAdd(c,doubleAndAdd(c,a,p),doubleAndAdd(c,b,q))
            else->error("property family")
        }
    }
    private fun observe(r: JsonObject): JsonElement=try {
        val c=CryptoEccTypes.getCurveByName(EccCurveName.valueOf(r.text("curve")))
        fun a()=r.integer("a")
        fun b()=r.integer("b")
        fun p()=pt(r,"p")
        fun q()=pt(r,"q")
        fun num(n: BigInteger?)=n?.let {JsonPrimitive(it.toString())}?:JsonNull
        when(val op=r.text("op")) {
            "curve"->buildJsonObject {put("show",c.toString());put("bits",CryptoEccTypes.curveSizeBits(c));put("modulus",c.modulus.toString());put("a",c.common.a.toString());put("b",c.common.b.toString());put("g",point(c.common.generator));put("n",c.common.order.toString());put("h",c.common.cofactor.toString())}
            "scalarAdd"->num(CryptoEccPrim.scalarAdd(c,a(),b()))
            "scalarMul"->num(CryptoEccPrim.scalarMul(c,a(),b()))
            "scalarInverse"->num(CryptoEccPrim.scalarInverse(c,a()))
            "pointAdd"->point(CryptoEccPrim.pointAdd(c,p(),q()))
            "pointDouble"->point(CryptoEccPrim.pointDouble(c,p()))
            "pointNegate"->point(CryptoEccPrim.pointNegate(c,p()))
            "pointBaseMul"->point(CryptoEccPrim.pointBaseMul(c,a()))
            "pointMul"->point(CryptoEccPrim.pointMul(c,a(),p()))
            "pointAddTwoMuls"->point(CryptoEccPrim.pointAddTwoMuls(c,a(),p(),b(),q()))
            "pointDecompose"->CryptoEccPrim.pointDecompose(c,p())?.let {buildJsonObject {put("index",it.index.toString());put("residue",it.residue.toString());put("parity",it.parity)}}?:JsonNull
            "pointCompose"->CryptoEccPrim.pointCompose(c,a(),b(),r.getValue("parity").jsonPrimitive.boolean)?.let(::point)?:JsonNull
            "isPointAtInfinity"->JsonPrimitive(CryptoEccPrim.isPointAtInfinity(p()))
            "isPointValid"->JsonPrimitive(CryptoEccPrim.isPointValid(c,p()))
            "isPointInSubgroup"->JsonPrimitive(CryptoEccPrim.isPointInSubgroup(c,p()))
            "calculatePublic"->point(CryptoEccDh.calculatePublic(c,a()))
            "tryGetShared"->when(val x=CryptoEccDh.tryGetShared(c,a(),p())) {is CryptoEccDh.Result.Failed->buildJsonObject {put("error",x.error.name)};is CryptoEccDh.Result.Passed->buildJsonObject {put("value",bytes(x.key.toByteArray()))}}
            "getShared"->bytes(CryptoEccDh.getShared(c,a(),p()).toByteArray())
            "generatePrivate","scalarGenerate"->{val payloads=r.getValue("payloads").jsonArray.iterator();val requests=mutableListOf<Int>();val rng:(Int)->ByteArray={requests+=it;payloads.next().jsonArray.map {it.jsonPrimitive.int.toByte()}.toByteArray()};val n=if(op=="generatePrivate")CryptoEccDh.generatePrivate(c,rng)else CryptoEccPrim.scalarGenerate(c,rng);buildJsonObject {put("value",n.toString());put("requests",JsonArray(requests.map(::JsonPrimitive)))}}
            "originalProperty"->JsonPrimitive(property(c,r))
            else->error(op)
        }
    }catch(e: IllegalStateException) {buildJsonObject {put("exception",e.message?:"")}}
    @TestFactory fun independentOriginalObservations(): Stream<DynamicTest> = lines.drop(1).mapIndexed {i,line->val row=Json.parseToJsonElement(line).jsonObject;DynamicTest.dynamicTest("$i ${row.text("group")}") {assertEquals(row.getValue("expected"),observe(row.getValue("request").jsonObject),row.getValue("request").toString())}}.stream()
    @TestFactory fun sourcePins(): Stream<DynamicTest> = manifest.getValue("sources").jsonObject.map {(p,h)->DynamicTest.dynamicTest(p) {val f=File(System.getProperty("kotdoc.root"),p);assertEquals(h.jsonPrimitive.content,MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") {(it.toInt()and 255).toString(16).padStart(2,'0')})}}.stream()
    @TestFactory fun originalCommentedSecOids(): Stream<DynamicTest> = manifest.getValue("inventory").jsonObject.getValue("oids").jsonObject.map {(name,oid)->DynamicTest.dynamicTest(name) {
        assertEquals(oid.jsonArray.map {it.jsonPrimitive.content.toBigInteger()},CryptoEccTypes.curveOid(EccCurveName.valueOf(name)))
    }}.stream()
    @Test fun completeOriginalSuiteAndSubgroupInventory() {
        val inv=manifest.getValue("inventory").jsonObject;assertEquals(33,inv.getValue("curves").jsonArray.size);assertEquals(20,inv.getValue("subgroups").jsonArray.size)
        val names=inv.getValue("subgroups").jsonArray.map {it.jsonObject.text("curve")};assertEquals(18,names.count {it.startsWith("SEC_t")});assertTrue(names.containsAll(listOf("SEC_p112r2","SEC_p128r2")))
        val suite=manifest.getValue("originalSuite").jsonObject;assertEquals(0,suite.getValue("failures").jsonPrimitive.int);assertEquals(7,suite.getValue("properties").jsonPrimitive.int);assertEquals(140,suite.getValue("propertyChecks").jsonPrimitive.int)
    }
}
