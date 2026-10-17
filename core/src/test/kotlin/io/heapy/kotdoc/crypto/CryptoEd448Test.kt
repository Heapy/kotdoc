package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.crypto.CryptoEcc.getOrThrow
import kotlinx.serialization.json.*
import java.io.File
import java.util.zip.GZIPInputStream
internal object Ed448PublicReplay {
 private fun bytes(r:JsonObject,k:String="bytes")=r[k]?.jsonArray?.map{it.jsonPrimitive.int.toByte()}?.toByteArray()?:ByteArray(0)
 private fun json(b:ByteArray)=JsonArray(b.map{JsonPrimitive(it.toInt()and 255)})
 private fun value(v:Any):JsonObject {val b=when(v){is CryptoEd448.SecretKey->v.toByteArray();is CryptoEd448.PublicKey->v.toByteArray();is CryptoEd448.Signature->v.toByteArray();else->error("type")};return buildJsonObject{put("bytes",json(b));put("show",v.toString())}}
 private fun <T> fail(v:CryptoEcc.Failable<T>,render:(T)->JsonElement):JsonElement=when(v){is CryptoEcc.Failable.Passed->buildJsonObject{put("value",render(v.value))};is CryptoEcc.Failable.Failed->buildJsonObject{put("error",v.error.name)}}
 fun observe(r:JsonObject,bits:Int,sb:Int,vector:Boolean):JsonElement=try {
  val api=CryptoEd448(bits,sb,vector)
  fun secret(k:String="bytes")=api.secretKey(bytes(r,k)).getOrThrow()
  fun public(k:String="bytes")=api.publicKey(bytes(r,k)).getOrThrow()
  fun signature(k:String="bytes")=api.signature(bytes(r,k)).getOrThrow()
  fun rng():JsonElement {val calls=mutableListOf<Int>();val key=api.generateSecretKey{calls+=it;bytes(r)};return buildJsonObject{put("value",value(key));put("requests",JsonArray(calls.map(::JsonPrimitive)))}}
  val prec=r["precedence"]?.jsonPrimitive?.int?:0
  fun <T> demand(name:String,action:()->T)=lazy{if(r["bottom_$name"]?.jsonPrimitive?.boolean==true)error("$name-bottom");action()}
  when(r.getValue("op").jsonPrimitive.content){
   "sizes"->JsonArray(listOf(CryptoEd448.publicKeySize,CryptoEd448.secretKeySize,CryptoEd448.signatureSize).map(::JsonPrimitive))
   "secretKey"->fail(api.secretKey(bytes(r)),::value)
   "publicKey"->fail(api.publicKey(bytes(r)),::value)
   "signature"->fail(api.signature(bytes(r)),::value)
   "secretShow"->JsonPrimitive(secret().show(prec))
   "publicShow"->JsonPrimitive(public().show(prec))
   "signatureShow"->JsonPrimitive(signature().show(prec))
   "debugShow"->JsonPrimitive(secret().debugShow())
   "secretEq"->JsonPrimitive(secret()==secret("other"))
   "publicEq"->JsonPrimitive(public()==public("other"))
   "signatureEq"->JsonPrimitive(signature()==signature("other"))
   "secretForce"->{secret().force();JsonNull}
   "publicForce"->{public().force();JsonNull}
   "signatureForce"->{signature().force();JsonNull}
   "generate"->rng()
   "generateThrow"->{api.generateSecretKey{error("rng-failure")};error("unreachable")}
   "toPublic"->value(api.toPublic(secret()))
   "sign"->value(api.sign(secret(),public("point"),bytes(r,"message")))
   "unsafeSign"->value(api.unsafeSign(secret(),public("point"),bytes(r,"message")))
   "verify"->JsonPrimitive(api.verify(public("point"),bytes(r,"message"),signature("signature")))
   "signVerify"->{val key=secret();val actual=api.toPublic(key);val point=public("point");val message=bytes(r,"message");val safe=api.sign(key,point,message);val unsafe=api.unsafeSign(key,point,message);buildJsonObject{put("public",value(actual));put("safe",value(safe));put("unsafe",value(unsafe));put("safeValid",api.verify(actual,message,safe));put("unsafeValid",api.verify(actual,message,unsafe))}}
   "ownership"->{val b=bytes(r);val source=b.copyOf();val key=api.secretKey(source.copyOfRange(0,57)).getOrThrow();val point=api.publicKey(source.copyOfRange(0,57)).getOrThrow();val sig=api.signature(source).getOrThrow();source.fill(99);buildJsonObject{put("secret",value(key));put("public",value(point));put("signature",value(sig))}}
   "secretBottomShow"->JsonPrimitive(api.secretShowDeferred(lazy{error("secret-bottom")},prec))
   "publicBottomShow"->JsonPrimitive(api.publicShowDeferred(lazy{error("public-bottom")}))
   "signatureBottomShow"->JsonPrimitive(api.signatureShowDeferred(lazy{error("signature-bottom")}))
   "secretBottomForce"->{api.secretForceDeferred(lazy{error("secret-bottom")});JsonNull}
   "publicBottomForce"->{api.publicForceDeferred(lazy{error("public-bottom")});JsonNull}
   "signatureBottomForce"->{api.signatureForceDeferred(lazy{error("signature-bottom")});JsonNull}
   "signDemand"->value(api.signDeferred(demand("secret"){secret()},demand("public"){public("point")},demand("message"){bytes(r,"message")}))
   "unsafeSignDemand"->value(api.unsafeSignDeferred(demand("secret"){secret()},demand("public"){public("point")},demand("message"){bytes(r,"message")}))
   "verifyDemand"->JsonPrimitive(api.verifyDeferred(demand("public"){public("point")},demand("message"){bytes(r,"message")},demand("signature"){signature("signature")}))
   else->error("operation")
  }
 }catch(e:IllegalStateException){buildJsonObject{put("exception",e.message?:"")}}
 @JvmStatic fun main(args:Array<String>){var n=0;GZIPInputStream(File(args[0]).inputStream()).bufferedReader().useLines{lines->lines.drop(1).forEach{val row=Json.parseToJsonElement(it).jsonObject;for((bits,sb,vector)in listOf(Triple(32,32,false),Triple(64,64,false),Triple(64,32,false),Triple(64,32,true))){val actual=observe(row.getValue("request").jsonObject,bits,sb,vector);check(actual==row.getValue("expected")){"row$n/$bits/$sb/$vector request=${row.getValue("request")} expected=${row.getValue("expected")} actual=$actual"}};n++;if(n%1000==0)println("PASS$n")}};println("PASS$n publicEd448 rows / ${n*4} fourprofile observations")}
}

class CryptoEd448Test {
 private val profiles=listOf(Triple(32,32,false),Triple(64,64,false),Triple(64,32,false),Triple(64,32,true))
 private val lines by lazy {GZIPInputStream(javaClass.getResourceAsStream("/crypto-ed448-public/data.jsonl.gz")!!).bufferedReader().use{it.readLines()}}
 private val manifest by lazy {Json.parseToJsonElement(lines.first()).jsonObject}
 @org.junit.jupiter.api.TestFactory
 fun originalPublicModuleObservations():java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> = profiles.flatMap{(bits,sb,vector)->lines.drop(1).mapIndexed{index,line->org.junit.jupiter.api.DynamicTest.dynamicTest("$bits/$sb/$vector/$index"){
  val row=Json.parseToJsonElement(line).jsonObject;org.junit.jupiter.api.Assertions.assertEquals(row.getValue("expected"),Ed448PublicReplay.observe(row.getValue("request").jsonObject,bits,sb,vector),row.getValue("request").toString())
 }}}.stream()
 @org.junit.jupiter.api.TestFactory
 fun sourcePins():java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> = manifest.getValue("sources").jsonObject.map{(path,expected)->org.junit.jupiter.api.DynamicTest.dynamicTest(path){
  org.junit.jupiter.api.Assertions.assertEquals(expected.jsonPrimitive.content,java.security.MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),path).readBytes()).toHexString())
 }}.stream()
 @org.junit.jupiter.api.Test
 fun ownedSnapshotsAndRngEffects() {
  for((bits,sb,vector)in profiles){
   val api=CryptoEd448(bits,sb,vector);val source=ByteArray(57){it.toByte()};val expected=source.copyOf();val secret=api.secretKey(source).getOrThrow();val public=api.publicKey(source).getOrThrow();source.fill(99)
   org.junit.jupiter.api.Assertions.assertArrayEquals(expected,secret.toByteArray());org.junit.jupiter.api.Assertions.assertArrayEquals(expected,public.toByteArray())
   val wide=ByteArray(114){it.toByte()};val sigExpected=wide.copyOf();val signature=api.signature(wide).getOrThrow();wide.fill(99);org.junit.jupiter.api.Assertions.assertArrayEquals(sigExpected,signature.toByteArray())
   secret.toByteArray().fill(0);public.toByteArray().fill(0);signature.toByteArray().fill(0);secret.withByteArray{it.fill(0)};public.withByteArray{it.fill(0)};signature.withByteArray{it.fill(0)}
   org.junit.jupiter.api.Assertions.assertArrayEquals(expected,secret.toByteArray());org.junit.jupiter.api.Assertions.assertArrayEquals(expected,public.toByteArray());org.junit.jupiter.api.Assertions.assertArrayEquals(sigExpected,signature.toByteArray())
   val random=ByteArray(57){255.toByte()};var calls=0;val generated=api.generateSecretKey{org.junit.jupiter.api.Assertions.assertEquals(57,it);calls++;random};org.junit.jupiter.api.Assertions.assertEquals(1,calls);org.junit.jupiter.api.Assertions.assertTrue(random.all{it==255.toByte()});random.fill(0);org.junit.jupiter.api.Assertions.assertTrue(generated.toByteArray().all{it==255.toByte()})
   var failures=0;org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java){api.generateSecretKey{failures++;error("rng-failure")}};org.junit.jupiter.api.Assertions.assertEquals(1,failures)
   val message=ByteArray(137){it.toByte()};val messageBefore=message.copyOf();val wrong=api.publicKey(ByteArray(57)).getOrThrow();val signed=api.sign(secret,wrong,message);org.junit.jupiter.api.Assertions.assertTrue(api.verify(api.toPublic(secret),message,signed));org.junit.jupiter.api.Assertions.assertArrayEquals(expected,secret.toByteArray());org.junit.jupiter.api.Assertions.assertArrayEquals(messageBefore,message)
  }
 }
 @org.junit.jupiter.api.Test
 fun exactSharedErrorIdentityAndPriorBehavior() {
  val api=CryptoEd448()
  org.junit.jupiter.api.Assertions.assertSame(CryptoEcc.Error.CryptoError_SecretKeyStructureInvalid,(api.secretKey(ByteArray(0))as CryptoEcc.Failable.Failed).error)
  org.junit.jupiter.api.Assertions.assertSame(CryptoEcc.Error.CryptoError_SecretKeyStructureInvalid,(api.signature(ByteArray(0))as CryptoEcc.Failable.Failed).error)
  org.junit.jupiter.api.Assertions.assertSame(CryptoEcc.Error.CryptoError_PublicKeySizeInvalid,(api.publicKey(ByteArray(0))as CryptoEcc.Failable.Failed).error)
  org.junit.jupiter.api.Assertions.assertSame(CryptoEcc.Error.CryptoError_SecretKeySizeInvalid,(CryptoCurve448().secretKey(ByteArray(0))as CryptoEcc.Failable.Failed).error)
  org.junit.jupiter.api.Assertions.assertSame(CryptoEcc.Error.CryptoError_SecretKeySizeInvalid,(CryptoCurve25519().secretKey(ByteArray(0))as CryptoEcc.Failable.Failed).error)
 }
 @org.junit.jupiter.api.Test
 fun fullOriginalSuiteAndExports() {
  val matrix=Json.parseToJsonElement(File(System.getProperty("kotdoc.root"),"tools/ed448-public-reference/SourceMatrix.json").readText()).jsonObject
  org.junit.jupiter.api.Assertions.assertEquals(3,matrix.getValue("types").jsonArray.size);org.junit.jupiter.api.Assertions.assertEquals(3,matrix.getValue("sizes").jsonObject.size);org.junit.jupiter.api.Assertions.assertEquals(8,matrix.getValue("functions").jsonArray.size)
  org.junit.jupiter.api.Assertions.assertEquals(9056,manifest.getValue("cases").jsonPrimitive.int);org.junit.jupiter.api.Assertions.assertEquals(25,manifest.getValue("originalExamples").jsonPrimitive.int);org.junit.jupiter.api.Assertions.assertEquals(8,manifest.getValue("literalVectors").jsonPrimitive.int)
  org.junit.jupiter.api.Assertions.assertEquals(32,lines.drop(1).count{"\"kat\":" in it});org.junit.jupiter.api.Assertions.assertEquals(288,lines.drop(1).count{"\"op\":\"generate\"" in it})
 }
}
