package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.crypto.CryptoEcc.getOrThrow
import io.heapy.kotdoc.crypto.CryptoEccSimpleTypes.Tag
import io.heapy.kotdoc.crypto.CryptoEccSimpleTypes.Curve
import io.heapy.kotdoc.crypto.CryptoEccSimpleTypes.Scalar
import io.heapy.kotdoc.crypto.CryptoEccSimpleTypes.Point
import io.heapy.kotdoc.x509.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.*
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import java.util.stream.Stream
import java.util.zip.GZIPInputStream

class CryptoEccModernPrimeTest {
 private val lines by lazy {GZIPInputStream(javaClass.getResourceAsStream("/crypto-ecc-modern-prime/data.jsonl.gz")!!).bufferedReader().use {it.readLines()}}
 private val manifest by lazy {Json.parseToJsonElement(lines.first()).jsonObject}
 private fun JsonObject.text(k: String)=getValue(k).jsonPrimitive.content
 private fun JsonObject.integer(k: String)=text(k).toBigInteger()
 private fun raw(r: JsonObject,k: String): EccPoint {val v=r.getValue(k);return if(v==JsonNull)X509EcPoint.Infinity else v.jsonArray.let {X509EcPoint.Affine(it[0].jsonPrimitive.content.toBigInteger(),it[1].jsonPrimitive.content.toBigInteger())}}
 private fun pt(p: EccPoint): JsonElement=when(p) {X509EcPoint.Infinity->JsonNull;is X509EcPoint.Affine->JsonArray(listOf(JsonPrimitive(p.x.toString()),JsonPrimitive(p.y.toString())))}
 private fun bs(b: ByteArray)=JsonArray(b.map {JsonPrimitive(it.toInt()and 255)})
 private fun bytes(r: JsonObject)=r.getValue("bytes").jsonArray.map {it.jsonPrimitive.int.toByte()}.toByteArray()
 private fun num(n: BigInteger)=JsonPrimitive(n.toString())
 private fun <T> fail(x: CryptoEcc.Failable<T>,f: (T)->JsonElement): JsonElement=when(x) {is CryptoEcc.Failable.Passed->buildJsonObject {put("value",f(x.value))};is CryptoEcc.Failable.Failed->buildJsonObject {put("error",x.error.name)}}
 private fun simple(r: JsonObject): JsonElement {
  val c=CryptoEccSimpleTypes.forName(EccCurveName.valueOf(r.text("curve")))
  fun s(k: String)=Scalar<Tag>(r.integer(k))
  fun p(k: String)=Point<Tag>(raw(r,k))
  return when(r.text("op")) {
   "type"->buildJsonObject {val cc=c.parameters;put("type",c.type.toString());put("parameters",cc.toString());put("bits",c.sizeBits);put("bytes",c.sizeBytes);put("a",cc.a.toString());put("b",cc.b.toString());put("g",pt(cc.generator.raw));put("n",cc.order.toString());put("h",cc.cofactor.toString())}
   "scalarFromInteger"->fail(CryptoEccSimplePrim.scalarFromInteger(c,r.integer("a"))) {num(it.integer)}
   "pointFromIntegers"->fail(CryptoEccSimplePrim.pointFromIntegers(c,r.integer("a"),r.integer("b"))) {pt(it.raw)}
   "pointAdd"->pt(CryptoEccSimplePrim.pointAdd(c,p("p"),p("q")).raw)
   "pointNegate"->pt(CryptoEccSimplePrim.pointNegate(c,p("p")).raw)
   "pointDouble"->pt(CryptoEccSimplePrim.pointDouble(c,p("p")).raw)
   "pointBaseMul"->pt(CryptoEccSimplePrim.pointBaseMul(c,s("a")).raw)
   "pointMul"->pt(CryptoEccSimplePrim.pointMul(c,s("a"),p("p")).raw)
   "pointAddTwoMuls"->pt(CryptoEccSimplePrim.pointAddTwoMuls(c,s("a"),p("p"),s("b"),p("q")).raw)
   "isPointAtInfinity"->JsonPrimitive(CryptoEccSimplePrim.isPointAtInfinity(p("p")))
   "isPointValid"->JsonPrimitive(CryptoEccSimplePrim.isPointValid(c,r.integer("a"),r.integer("b")))
   "isPointInSubgroup"->JsonPrimitive(CryptoEccSimplePrim.isPointInSubgroup(c,p("p")))
   "scalarShow"->JsonPrimitive(s("a").show(r.integer("precedence").toInt()))
   "pointShow"->JsonPrimitive(p("p").toString())
   "scalarGenerate"->rng(r,{CryptoEccSimplePrim.scalarGenerate(c,it)}) {num(it.integer)}
   else->error("simple operation")
  }
 }
 private fun <T> rng(r: JsonObject,action: ((Int)->ByteArray)->T,snapshot: (T)->JsonElement): JsonElement {
  val payloads=r.getValue("payloads").jsonArray.iterator();val requests=mutableListOf<Int>()
  val x=action {n->requests+=n;payloads.next().jsonArray.map {it.jsonPrimitive.int.toByte()}.toByteArray()}
  return buildJsonObject {put("value",snapshot(x));put("requests",JsonArray(requests.map(::JsonPrimitive)))}
 }
 private fun <P,S> pv(c: CryptoEcc.EllipticCurve<P,S>,p: P): JsonElement=try {buildJsonObject {put("binary",bs(c.encodePoint(p)))}} catch(e: IllegalStateException) {buildJsonObject {put("encodingException",e.message?:"")}}
 private fun <P,S> pvLazy(c: CryptoEcc.EllipticCurve<P,S>,action: ()->P): JsonElement=try {pv(c,action())} catch(e: IllegalStateException) {buildJsonObject {put("encodingException",e.message?:"")}}
 private fun <P,S> property(c: CryptoEcc.PrimeCurve<P,S>,r: JsonObject): Boolean {
  val s=c.scalarFromInteger(r.integer("a")).getOrThrow();val t=c.scalarFromInteger(r.integer("b")).getOrThrow();val u=c.scalarFromInteger(r.integer("s")).getOrThrow();val base=c.pointBaseSmul(u)
  return when(r.text("family")) {
   "decodePoint.encodePoint==id"->c.decodePoint(c.encodePoint(base))==CryptoEcc.Failable.Passed(base)
   "ECDH commutes"->{val alice=c.scalarToPoint(s);val bob=c.scalarToPoint(t);val ab=c.ecdh(s,bob);val ba=c.ecdh(t,alice);ab==ba && ab==CryptoEcc.Failable.Passed(c.ecdhRaw(s,bob)) && ba==CryptoEcc.Failable.Passed(c.ecdhRaw(t,alice))}
   "decodeScalar.encodeScalar==id"->c.decodeScalar(c.encodeScalar(u))==CryptoEcc.Failable.Passed(u)
   "scalarFromInteger.scalarToInteger==id"->c.scalarFromInteger(c.scalarToInteger(u))==CryptoEcc.Failable.Passed(u)
   "(a + b).P = a.P + b.P"->c.pointSmul(c.scalarAdd(s,t),base)==c.pointAdd(c.pointSmul(s,base),c.pointSmul(t,base))
   "(a * b).P = a.(b.P)"->c.pointSmul(c.scalarMul(s,t),base)==c.pointSmul(s,c.pointSmul(t,base))
   else->error("property family")
  }
 }
 private fun <P,S> modern(c: CryptoEcc.PrimeCurve<P,S>,r: JsonObject,point: (String)->P): JsonElement {
  fun s(k: String)=c.scalarFromInteger(r.integer(k)).getOrThrow()
  return when(r.text("op")) {
   "sizes"->buildJsonObject {put("bits",c.sizeBits);put("orderBits",c.orderBits)}
   "scalarFromInteger"->fail(c.scalarFromInteger(r.integer("a"))) {num(c.scalarToInteger(it))}
   "decodeScalar"->fail(c.decodeScalar(bytes(r))) {num(c.scalarToInteger(it))}
   "encodeScalar"->bs(c.encodeScalar(s("a")))
   "decodePoint"->fail(c.decodePoint(bytes(r))) {pv(c,it)}
   "encodePoint"->bs(c.encodePoint(point("p")))
   "scalarToPoint"->pvLazy(c) {c.scalarToPoint(s("a"))}
   "pointBaseSmul"->pvLazy(c) {c.pointBaseSmul(s("a"))}
   "pointAdd"->pvLazy(c) {c.pointAdd(point("p"),point("q"))}
   "pointNegate"->pvLazy(c) {c.pointNegate(point("p"))}
   "pointSmul"->pvLazy(c) {c.pointSmul(s("a"),point("p"))}
   "pointsSmulVarTime"->pvLazy(c) {c.pointsSmulVarTime(s("a"),s("b"),point("p"))}
   "scalarAdd"->num(c.scalarToInteger(c.scalarAdd(s("a"),s("b"))))
   "scalarMul"->num(c.scalarToInteger(c.scalarMul(s("a"),s("b"))))
   "ecdh"->fail(c.ecdh(s("a"),point("p"))) {bs(it.toByteArray())}
   "ecdhRaw"->bs(c.ecdhRaw(s("a"),point("p")).toByteArray())
   "generateScalar"->rng(r,c::generateScalar) {num(c.scalarToInteger(it))}
   "generateKeyPair"->rng(r,c::generateKeyPair) {buildJsonObject {put("public",pv(c,it.public));put("private",c.scalarToInteger(it.private).toString())}}
   "property"->JsonPrimitive(property(c,r))
   else->error("modern operation")
  }
 }
 private fun observe(r: JsonObject): JsonElement=try {
  if(r.text("layer")=="simple") simple(r) else when(r.text("curve")) {
   "P256"->modern(CryptoEcc.P256,r) {k->when(val p=raw(r,k)) {X509EcPoint.Infinity->CryptoP256().pointFromIntegers(BigInteger.ZERO,BigInteger.ZERO);is X509EcPoint.Affine->CryptoP256().pointFromIntegers(p.x,p.y)}}
   "P384"->modern(CryptoEcc.P384,r) {Point<CryptoEccSimpleTypes.SEC_p384r1>(raw(r,it))}
   "P521"->modern(CryptoEcc.P521,r) {Point<CryptoEccSimpleTypes.SEC_p521r1>(raw(r,it))}
   else->error("modern curve")
  }
 }catch(e: IllegalStateException) {buildJsonObject {put("exception",e.message?:"")}}
 @TestFactory fun independentOriginalObservations(): Stream<DynamicTest> = lines.drop(1).mapIndexed {i,line->val row=Json.parseToJsonElement(line).jsonObject;DynamicTest.dynamicTest("$i ${row.text("group")}") {assertEquals(row.getValue("expected"),observe(row.getValue("request").jsonObject),row.getValue("request").toString())}}.stream()
 @TestFactory fun sourcePins(): Stream<DynamicTest> = manifest.getValue("sources").jsonObject.map {(p,h)->DynamicTest.dynamicTest(p) {assertEquals(h.jsonPrimitive.content,MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),p).readBytes()).joinToString("") {(it.toInt()and 255).toString(16).padStart(2,'0')})}}.stream()
 @Test fun explicitSuitePartition() {
  val p=manifest.getValue("partition").jsonObject;assertEquals(27,p.getValue("supportedPointVectors").jsonPrimitive.int);assertEquals(8,p.getValue("pendingPointVectors").jsonPrimitive.int);assertEquals(9,p.getValue("pendingWeakPointVectors").jsonPrimitive.int);assertEquals(2,p.getValue("supportedECDHKATs").jsonPrimitive.int)
  val suite=manifest.getValue("originalSuite").jsonObject;assertEquals(52,suite.getValue("examples").jsonPrimitive.int);assertEquals(0,suite.getValue("failures").jsonPrimitive.int);assertEquals(6,suite.getValue("properties").jsonPrimitive.int)
 }
 @Test fun secretOwnershipMonoidAndStrictKeyPair() {
  val data=byteArrayOf(1,2,3);val s=CryptoEcc.SharedSecret(data);data.fill(0);s.toByteArray().fill(0);assertArrayEquals(byteArrayOf(1,2,3),s.toByteArray());assertEquals(s,s+CryptoEcc.SharedSecret.empty());assertEquals(s,CryptoEcc.SharedSecret.empty()+s);assertArrayEquals(byteArrayOf(1,2,3,1,2,3),(s+s).toByteArray())
  var calls=0;assertThrows(IllegalStateException::class.java) {CryptoEcc.P256.generateKeyPair {calls++;ByteArray(it)}};assertEquals(1,calls)
 }
}
