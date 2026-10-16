package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.*
import java.io.File
import java.security.MessageDigest
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import java.math.BigInteger
class CryptoEdwards25519Test {
 private val lines by lazy {GZIPInputStream(javaClass.getResourceAsStream("/crypto-edwards25519-public/data.jsonl.gz")!!).bufferedReader().use{it.readLines()}}
 private val manifest by lazy {Json.parseToJsonElement(lines.first()).jsonObject}
 private val rows by lazy {lines.drop(1).map{Json.parseToJsonElement(it).jsonObject}}
 private fun JsonObject.text(k: String)=getValue(k).jsonPrimitive.content
 private fun encoded(bytes: ByteArray)=JsonArray(bytes.map{JsonPrimitive(it.toInt()and 255)})
 private fun <T> value(f: CryptoEcc.Failable<T>): T=when(f){is CryptoEcc.Failable.Passed->f.value;is CryptoEcc.Failable.Failed->error(f.error.name)}
 internal fun observe(r: JsonObject,bits: Int): JsonElement {
  val api=CryptoEdwards25519(bits);val modern=CryptoEcc.Edwards25519
  fun bytes(key: String)=r[key]?.jsonArray?.map{it.jsonPrimitive.int.toByte()}?.toByteArray()?:ByteArray(0)
  fun scalar(key: String)=value(api.scalarDecodeLong(bytes(key)))
  fun point(key: String)=value(api.pointDecode(bytes(key)))
  fun sv(s: CryptoEdwards25519.Scalar)=buildJsonObject{put("bytes",encoded(api.scalarEncode(s)));put("show",s.show())}
  fun pv(p: CryptoEdwards25519.Point)=buildJsonObject{put("bytes",encoded(api.pointEncode(p)));put("show",p.show());put("prime",api.pointHasPrimeOrder(p))}
  fun <T> failable(f: CryptoEcc.Failable<T>,render: (T)->JsonElement)=when(f){is CryptoEcc.Failable.Passed->buildJsonObject{put("value",render(f.value))};is CryptoEcc.Failable.Failed->buildJsonObject{put("error",f.error.name)}}
  fun rng(render: ((Int)->ByteArray)->JsonElement): JsonElement {val requests=mutableListOf<Int>();val v=render{requests.add(it);bytes("bytes")};return buildJsonObject{put("value",v);put("requests",JsonArray(requests.map(::JsonPrimitive)))}}
  val precedence=r["precedence"]?.jsonPrimitive?.int?:0
  try{return when(r.text("op")){
   "scalarDecodeLong"->failable(api.scalarDecodeLong(bytes("bytes")),::sv)
   "scalarEncode"->encoded(api.scalarEncode(scalar("bytes")))
   "scalarShow"->JsonPrimitive(api.scalarShowDeferred(lazy{scalar("bytes")},precedence))
   "scalarEq"->JsonPrimitive(scalar("bytes")==scalar("other"))
   "scalarAdd"->sv(api.scalarAdd(scalar("bytes"),scalar("other")))
   "scalarMul"->sv(api.scalarMul(scalar("bytes"),scalar("other")))
   "scalarGenerate"->rng{sv(api.scalarGenerate(it))}
   "generateThrow"->{api.scalarGenerate{error("rng-failure")};error("unreachable")}
   "pointDecode"->failable(api.pointDecode(bytes("bytes")),::pv)
   "pointEncode"->encoded(api.pointEncode(point("point")))
   "pointShow"->JsonPrimitive(point("point").show(precedence))
   "pointEq"->JsonPrimitive(point("point")==point("otherPoint"))
   "toPoint"->pv(api.toPoint(scalar("bytes")))
   "pointHasPrimeOrder"->JsonPrimitive(api.pointHasPrimeOrder(point("point")))
   "pointNegate"->pv(api.pointNegate(point("point")))
   "pointAdd"->pv(api.pointAdd(point("point"),point("otherPoint")))
   "pointDouble"->pv(api.pointDouble(point("point")))
   "pointMul"->pv(api.pointMul(scalar("bytes"),point("point")))
   "pointMulByCofactor"->pv(api.pointMulByCofactor(point("point")))
   "pointsMulVarTime"->pv(api.pointsMulVarTime(scalar("bytes"),scalar("other"),point("point")))
   "force"->{scalar("bytes").force();point("point").force();JsonNull}
   "scalarShowBottom"->JsonPrimitive(api.scalarShowDeferred(lazy{error("scalar-bottom")},precedence))
   "pointShowBottom"->JsonPrimitive(api.pointShowDeferred(lazy{error("point-bottom")}))
   "scalarForceBottom"->{api.scalarForceDeferred(lazy{error("scalar-bottom")});JsonNull}
   "pointForceBottom"->{api.pointForceDeferred(lazy{error("point-bottom")});JsonNull}
   "sizes"->JsonArray(listOf(JsonPrimitive(modern.sizeBits),JsonPrimitive(modern.orderBits)))
   "decodeScalar"->failable(modern.decodeScalar(bytes("bytes")),::sv)
   "decodePoint"->failable(modern.decodePoint(bytes("bytes")),::pv)
   "encodeScalar"->encoded(modern.encodeScalar(scalar("bytes")))
   "encodePoint"->encoded(modern.encodePoint(point("point")))
   "scalarFromInteger"->failable(modern.scalarFromInteger(BigInteger(r.text("integer"))),::sv)
   "scalarToInteger"->JsonPrimitive(modern.scalarToInteger(scalar("bytes")).toString())
   "scalarToPoint"->pv(modern.scalarToPoint(scalar("bytes")))
   "pointBaseSmul"->pv(modern.pointBaseSmul(scalar("bytes")))
   "pointSmul"->pv(modern.pointSmul(scalar("bytes"),point("point")))
   "modernPointAdd"->pv(modern.pointAdd(point("point"),point("otherPoint")))
   "modernPointNegate"->pv(modern.pointNegate(point("point")))
   "modernScalarAdd"->sv(modern.scalarAdd(scalar("bytes"),scalar("other")))
   "modernScalarMul"->sv(modern.scalarMul(scalar("bytes"),scalar("other")))
   "pointsSmulVarTime"->pv(modern.pointsSmulVarTime(scalar("bytes"),scalar("other"),point("point")))
   "generateScalar"->rng{sv(modern.generateScalar(it))}
   "generateKeyPair"->rng{random->val pair=modern.generateKeyPair(random);buildJsonObject{put("public",pv(pair.public));put("private",sv(pair.private))}}
   "property","modernProperty"->{
    val a=scalar("bytes");val b=scalar("other");val c=scalar("third");fun small(v: Int)=value(api.scalarDecodeLong(byteArrayOf(v.toByte())))
    val z=api.toPoint(small(0));val family=r.text("family").toInt()
    val result=if(r.text("op")=="modernProperty"){val p=modern.pointBaseSmul(c);when(family){0->value(modern.decodeScalar(modern.encodeScalar(a)))==a;1->value(modern.scalarFromInteger(modern.scalarToInteger(a)))==a;2->modern.pointSmul(modern.scalarAdd(a,b),p)==modern.pointAdd(modern.pointSmul(a,p),modern.pointSmul(b,p));3->modern.pointSmul(modern.scalarMul(a,b),p)==modern.pointSmul(a,modern.pointSmul(b,p));else->error("family")}}
    else when(family){
     0->value(api.scalarDecodeLong(api.scalarEncode(a)))==a
     1->api.scalarAdd(small(0),a)==a && api.scalarAdd(a,small(0))==a
     2->api.scalarAdd(a,api.scalarAdd(b,c))==api.scalarAdd(api.scalarAdd(a,b),c)
     3->api.scalarAdd(a,b)==api.scalarAdd(b,a)
     4->api.scalarMul(small(0),a)==small(0) && api.scalarMul(a,small(0))==small(0)
     5->api.scalarMul(small(1),a)==a && api.scalarMul(a,small(1))==a
     6->api.scalarMul(a,api.scalarMul(b,c))==api.scalarMul(api.scalarMul(a,b),c)
     7->api.scalarMul(a,b)==api.scalarMul(b,a)
     8->api.scalarMul(a,api.scalarAdd(b,c))==api.scalarAdd(api.scalarMul(a,b),api.scalarMul(a,c)) && api.scalarMul(api.scalarAdd(b,c),a)==api.scalarAdd(api.scalarMul(b,a),api.scalarMul(c,a))
     9->point("point").let{value(api.pointDecode(api.pointEncode(it)))==it}
     10->point("point").let{api.pointEncode(value(api.pointDecode(api.pointEncode(it)))).contentEquals(api.pointEncode(it))}
     11->point("point").let{api.pointAdd(z,it)==it && api.pointAdd(it,z)==it}
     12->api.pointAdd(point("point"),api.pointAdd(point("otherPoint"),point("thirdPoint")))==api.pointAdd(api.pointAdd(point("point"),point("otherPoint")),point("thirdPoint"))
     13->api.pointAdd(point("point"),point("otherPoint"))==api.pointAdd(point("otherPoint"),point("point"))
     14->point("point").let{api.pointAdd(it,api.pointNegate(it))==z}
     15->point("point").let{api.pointAdd(it,it)==api.pointDouble(it)}
     16->point("point").let{api.pointMul(small(8),it)==api.pointMulByCofactor(it)}
     17->api.pointHasPrimeOrder(point("prime"))
     18->api.pointMul(small(0),point("point"))==z
     19->point("point").let{api.pointMul(small(1),it)==it}
     20->point("point").let{api.pointMul(small(2),it)==api.pointDouble(it)}
     21->point("point").let{api.pointHasPrimeOrder(it)==(api.pointNegate(it)==api.pointMul(value(modern.scalarFromInteger(BigInteger.ONE.shiftLeft(252)+BigInteger("27742317777372353535851937790883648492"))),it))}
     22->api.pointMul(a,api.toPoint(b))==api.pointMul(b,api.toPoint(a))
     23->point("prime").let{api.pointAdd(api.pointMul(a,it),api.pointMul(b,it))==api.pointMul(api.scalarAdd(a,b),it)}
     24->api.pointAdd(api.toPoint(a),api.pointMul(b,point("point")))==api.pointsMulVarTime(a,b,point("point"))
     else->error("family")
    };JsonPrimitive(result)
   }
   else->error(r.text("op"))
  }}catch(e: IllegalStateException){return buildJsonObject{put("exception",e.message?:"")}}
 }
 @TestFactory fun originalPublicObservations(): Stream<DynamicTest> = rows.flatMapIndexed{i,row->listOf(32,64).map{bits->DynamicTest.dynamicTest("$bits/$i ${row.getValue("request").jsonObject.text("op")}"){assertEquals(row.getValue("expected"),observe(row.getValue("request").jsonObject,bits),row.getValue("request").toString())}}}.stream()
 @TestFactory fun sourcePins(): Stream<DynamicTest> = manifest.getValue("sources").jsonObject.map{(p,h)->DynamicTest.dynamicTest(p){assertEquals(h.jsonPrimitive.content,MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),p).readBytes()).toHexString())}}.stream()
 @Test fun originalSixLiteralExamples(){for(bits in listOf(32,64)){val api=CryptoEdwards25519(bits)
  fun sc(v: Int)=value(api.scalarDecodeLong(byteArrayOf(v.toByte())))
  fun base(v: Int)=api.toPoint(sc(v))
  val vectors=listOf(11 to "1337036ac32d8f30d4589c3c1c595812ce0fff40e37c6f5a97ab213f318290ad",123 to "c4b800c87010f9468303deea876503e886bfde1900e9e846fd4c3cd09c1cbc9f",134 to "5120abe03ca2af66c77ca320f0b21fb556f6b65fdd7e3264c14a30d97bf7a76f")
  for((v,hex)in vectors)assertEquals(value(api.pointDecode(hex.hexToByteArray())),base(v))
  assertEquals(base(134),api.pointAdd(base(123),base(11)))
  assertEquals(sc(0),value(api.scalarDecodeLong("edd3f55c1a631258d69cf7a2def9de1400000000000000000000000000000010".hexToByteArray())))
  val torsion=value(api.pointDecode(byteArrayOf(199.toByte(),23,106,112,61,77,216.toByte(),79,186.toByte(),60,11,118,13,16,103,15,42,32,83,250.toByte(),44,57,204.toByte(),198.toByte(),78,199.toByte(),253.toByte(),119,146.toByte(),172.toByte(),3,122)))
  assertNotEquals(base(0),api.pointMul(sc(4),torsion));assertEquals(base(0),api.pointMul(sc(8),torsion))
 }}
 @Test fun immutableOwnershipAndEffects(){for(bits in listOf(32,64)){val api=CryptoEdwards25519(bits);val source=ByteArray(64){it.toByte()};val s=value(api.scalarDecodeLong(source));val before=api.scalarEncode(s);source.fill(0);assertArrayEquals(before,api.scalarEncode(s));val wire=api.scalarEncode(s);wire.fill(0);assertArrayEquals(before,api.scalarEncode(s))
  val p=api.toPoint(s);val encoded=api.pointEncode(p);val q=value(api.pointDecode(encoded));encoded.fill(0);assertEquals(p,q);val escaped=api.pointEncode(q);escaped.fill(0);assertEquals(p,q)
  val requests=mutableListOf<Int>();val entropy=ByteArray(64);val zero=api.scalarGenerate{requests.add(it);entropy};entropy.fill(1);assertEquals(listOf(64),requests);assertArrayEquals(ByteArray(32),api.scalarEncode(zero));assertTrue(api.pointHasPrimeOrder(api.toPoint(zero)))
  for(r in listOf(api.pointAdd(p,api.toPoint(zero)),api.pointDouble(p),api.pointNegate(p),api.pointMul(s,p),api.pointMulByCofactor(p),api.pointsMulVarTime(s,s,p)))assertNotSame(p,r)
  assertNotSame(s,api.scalarAdd(s,zero));assertNotSame(s,api.scalarMul(s,zero));assertArrayEquals(before,api.scalarEncode(s));assertEquals(p,q)
 }}
 @Test fun exactModernCapabilityMatrix(){val ed: Any=CryptoEcc.Edwards25519;assertEquals("Curve_Edwards25519",ed.toString());assertTrue(ed is CryptoEcc.EllipticCurve<*,*>);assertTrue(ed is CryptoEcc.EllipticCurveArith<*,*>);assertTrue(ed is CryptoEcc.EllipticCurveBasepointArith<*,*>);assertFalse(ed is CryptoEcc.EllipticCurveDH<*,*>);assertTrue(CryptoEcc.P256 is CryptoEcc.PrimeCurve<*,*>);assertTrue(CryptoEcc.P384 is CryptoEcc.PrimeCurve<*,*>);assertTrue(CryptoEcc.P521 is CryptoEcc.PrimeCurve<*,*>);assertFalse((CryptoEcc.X25519 as Any)is CryptoEcc.EllipticCurveArith<*,*>)}
 @Test fun corpusIntegrity(){fun canonical(v: JsonElement): JsonElement=when(v){is JsonObject->JsonObject(v.toSortedMap().mapValues{canonical(it.value)});is JsonArray->JsonArray(v.map{canonical(it)});else->v}
  assertEquals(manifest.text("caseHash"),MessageDigest.getInstance("SHA-256").digest(canonical(JsonArray(rows)).toString().toByteArray()).toHexString());assertEquals(25,manifest.text("originalProperties").toInt());assertEquals(6,manifest.text("originalLiterals").toInt())
 }
}
