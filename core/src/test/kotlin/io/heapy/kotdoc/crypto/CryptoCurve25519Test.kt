package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.crypto.CryptoEcc.getOrThrow
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.*
import java.io.File
import java.security.MessageDigest
import java.util.stream.Stream
import java.util.zip.GZIPInputStream

class CryptoCurve25519Test {
 private val lines by lazy {GZIPInputStream(javaClass.getResourceAsStream("/crypto-curve25519-public/data.jsonl.gz")!!).bufferedReader().use {it.readLines()}}
 private val manifest by lazy {Json.parseToJsonElement(lines.first()).jsonObject}
 private fun JsonObject.text(k: String)=getValue(k).jsonPrimitive.content
 private fun data(r: JsonObject,k: String="bytes")=r[k]?.jsonArray?.map {it.jsonPrimitive.int.toByte()}?.toByteArray()?:ByteArray(0)
 private fun bs(v: ByteArray)=JsonArray(v.map {JsonPrimitive(it.toInt() and 255)})
 private fun value(v: Any): JsonObject {val b=when(v){is CryptoCurve25519.SecretKey->v.toByteArray();is CryptoCurve25519.PublicKey->v.toByteArray();is CryptoCurve25519.DhSecret->v.toByteArray();else->error("value")};return buildJsonObject {put("bytes",bs(b));put("show",v.toString())}}
 private fun <T> fail(v: CryptoEcc.Failable<T>,f: (T)->JsonElement): JsonElement=when(v){is CryptoEcc.Failable.Passed->buildJsonObject {put("value",f(v.value))};is CryptoEcc.Failable.Failed->buildJsonObject {put("error",v.error.name)}}
 private fun observe(r: JsonObject,bits: Int): JsonElement=try {
  val raw=CryptoCurve25519(bits);val c=CryptoEcc.X25519
  fun secret(k: String="bytes")=raw.secretKey(data(r,k)).getOrThrow()
  fun point(k: String="bytes")=raw.publicKey(data(r,k)).getOrThrow()
  fun shared()=raw.dhSecret(data(r)).getOrThrow()
  fun <T> rng(action: ((Int)->ByteArray)->T,render: (T)->JsonElement): JsonElement {val requests=mutableListOf<Int>();val v=action {requests+=it;data(r)};return buildJsonObject {put("value",render(v));put("requests",JsonArray(requests.map(::JsonPrimitive)))}}
  val prec=r["precedence"]?.jsonPrimitive?.int?:0
  val result=when(r.text("op")) {
   "secretKey"->fail(raw.secretKey(data(r)),::value)
   "publicKey"->fail(raw.publicKey(data(r)),::value)
   "dhSecret"->fail(raw.dhSecret(data(r)),::value)
   "secretShow"->JsonPrimitive(secret().show(prec))
   "publicShow"->JsonPrimitive(point().show(prec))
   "dhShow"->JsonPrimitive(shared().show(prec))
   "debugShow"->JsonPrimitive(secret().debugShow())
   "secretEq"->JsonPrimitive(secret()==secret("other"))
   "publicEq"->JsonPrimitive(point()==point("other"))
   "dhEq"->JsonPrimitive(shared()==raw.dhSecret(data(r,"other")).getOrThrow())
   "force"->{secret().force();point().force();shared().force();JsonNull}
   "generate"->rng(raw::generateSecretKey,::value)
   "generateThrow"->{raw.generateSecretKey {error("rng-failure")};error("unreachable")}
   "toPublic"->value(raw.toPublic(secret()))
   "dh"->value(raw.dh(point("point"),secret()))
   "modernSizes"->JsonPrimitive(c.sizeBits)
   "decodePoint"->fail(c.decodePoint(data(r)),::value)
   "decodeScalar"->fail(c.decodeScalar(data(r)),::value)
   "encodePoint"->bs(c.encodePoint(point()))
   "encodeScalar"->bs(c.encodeScalar(secret()))
   "scalarToPoint"->value(c.scalarToPoint(secret()))
   "generateScalar"->rng(c::generateScalar,::value)
   "generateKeyPair"->rng(c::generateKeyPair){buildJsonObject {put("public",value(it.public));put("private",value(it.private))}}
   "ecdhRaw"->bs(c.ecdhRaw(secret(),point("point")).toByteArray())
   "ecdh"->fail(c.ecdh(secret(),point("point"))){bs(it.toByteArray())}
   "pointRoundtrip"->{val p=c.scalarToPoint(secret());JsonPrimitive(c.decodePoint(c.encodePoint(p))==CryptoEcc.Failable.Passed(p))}
   "commutes"->{val a=secret();val b=secret("other");val ap=c.scalarToPoint(a);val bp=c.scalarToPoint(b);val ab=c.ecdh(a,bp);val ba=c.ecdh(b,ap);JsonPrimitive(ab==ba && ab==CryptoEcc.Failable.Passed(c.ecdhRaw(a,bp)) && ba==CryptoEcc.Failable.Passed(c.ecdhRaw(b,ap)))}
   else->error(r.text("op"))
  }
  r["katExpected"]?.let {assertEquals(it,result.jsonObject.getValue("bytes"))}
  result
 }catch(e: IllegalStateException){buildJsonObject {put("exception",e.message?:"")}}
 @TestFactory fun originalModuleObservations(): Stream<DynamicTest> = listOf(32,64).flatMap {bits->lines.drop(1).mapIndexed {i,line->val row=Json.parseToJsonElement(line).jsonObject;val r=row.getValue("request").jsonObject;DynamicTest.dynamicTest("$bits/$i ${row.text("group")}"){assertEquals(row.getValue("expected"),observe(r,bits),r.toString())}}}.stream()
 @TestFactory fun sourcePins(): Stream<DynamicTest> = manifest.getValue("sources").jsonObject.map {(p,h)->DynamicTest.dynamicTest(p){assertEquals(h.jsonPrimitive.content,MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),p).readBytes()).toHexString())}}.stream()
 @Test fun sourceCapabilityMatrix() {
  for(c in listOf(CryptoEcc.P256,CryptoEcc.P384,CryptoEcc.P521))for(cap in listOf(CryptoEcc.EllipticCurve::class.java,CryptoEcc.EllipticCurveDH::class.java,CryptoEcc.EllipticCurveArith::class.java,CryptoEcc.EllipticCurveBasepointArith::class.java))assertTrue(cap.isInstance(c))
  assertTrue(CryptoEcc.EllipticCurve::class.java.isInstance(CryptoEcc.X25519));assertTrue(CryptoEcc.EllipticCurveDH::class.java.isInstance(CryptoEcc.X25519))
  assertFalse(CryptoEcc.EllipticCurveArith::class.java.isInstance(CryptoEcc.X25519));assertFalse(CryptoEcc.EllipticCurveBasepointArith::class.java.isInstance(CryptoEcc.X25519))
  val methods=CryptoEcc.X25519::class.java.methods.map {it.name}.toSet();assertTrue(methods.intersect(setOf("pointAdd","pointNegate","pointSmul","pointBaseSmul","scalarFromInteger","scalarAdd","scalarMul","getOrderBits")).isEmpty())
 }
 @Test fun ownedBytesAndRandomEffects() {
  for(bits in listOf(32,64)) {
   val x=CryptoCurve25519(bits);val source=ByteArray(32){it.toByte()};val original=source.copyOf();val s=x.secretKey(source).getOrThrow();val p=x.publicKey(source).getOrThrow();val d=x.dhSecret(source).getOrThrow();source.fill(99)
   assertArrayEquals(original,s.toByteArray());assertArrayEquals(original,p.toByteArray());assertArrayEquals(original,d.toByteArray())
   s.toByteArray().fill(0);p.toByteArray().fill(0);d.toByteArray().fill(0);s.withByteArray {it.fill(0)};p.withByteArray {it.fill(0)};d.withByteArray {it.fill(0)}
   assertArrayEquals(original,s.toByteArray());assertArrayEquals(original,p.toByteArray());assertArrayEquals(original,d.toByteArray())
   val random=ByteArray(32){255.toByte()};var calls=0;val generated=x.generateSecretKey {assertEquals(32,it);calls++;random};assertEquals(1,calls);assertTrue(random.all {it==255.toByte()});val expected=random.copyOf();expected[0]=248.toByte();expected[31]=127;assertArrayEquals(expected,generated.toByteArray());random.fill(0);assertArrayEquals(expected,generated.toByteArray())
   var failures=0;assertThrows(IllegalStateException::class.java){x.generateSecretKey {failures++;error("rng-failure")}};assertEquals(1,failures)
   val secretBefore=s.toByteArray();x.toPublic(s);val zero=x.publicKey(ByteArray(32)).getOrThrow();assertTrue(x.dh(zero,s).toByteArray().all {it==0.toByte()});assertArrayEquals(secretBefore,s.toByteArray())
  }
 }
 @Test fun exactOriginalSuitePartition() {assertEquals(4,manifest.getValue("originalExamples").jsonPrimitive.int);val p=manifest.getValue("modernPartition").jsonObject;assertEquals(4,p.getValue("decodeVectors").jsonPrimitive.int);assertEquals(7,p.getValue("weakPoints").jsonPrimitive.int);assertEquals(2,p.getValue("propertyFamilies").jsonPrimitive.int);assertEquals(13,p.getValue("examples").jsonPrimitive.int);assertEquals(4,lines.drop(1).count {"\"kat\":" in it})}
}
