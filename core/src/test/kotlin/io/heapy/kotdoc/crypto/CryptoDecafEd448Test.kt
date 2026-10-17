package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
import java.io.File
import java.util.zip.GZIPInputStream
internal object Ed448Replay {
 fun observe(r:JsonObject):JsonObject {
  fun data(k:String)=r[k]?.jsonArray?.map{it.jsonPrimitive.int.toByte()}?.toByteArray()?:ByteArray(0)
  val engine=CryptoDecafEd448(r.getValue("bits").jsonPrimitive.int,r.getValue("scalarBits").jsonPrimitive.int,r.getValue("vectorMasks32").jsonPrimitive.boolean)
  val inputs=linkedMapOf("out" to (r["out"]?.let{data("out")}?:ByteArray(114){165.toByte()}),"secret" to data("secret"),"public" to data("public"),"signature" to data("signature"),"keypair" to(r["keypair"]?.let{data("keypair")}?:data("secret")+data("public")),"message" to data("message"),"context" to data("context"),"ctx" to ByteArray(344){81})
  val frames=inputs.mapValuesTo(linkedMapOf()){(name,bytes)->val size=if(name=="ctx")344 else maxOf(114,bytes.size);ByteArray(32){17}+bytes+ByteArray(size-bytes.size){165.toByte()}+ByteArray(32){34}}
  r["alias"]?.takeIf{it!=JsonNull}?.jsonPrimitive?.content?.let{frames["out"]=frames.getValue(it)}
  fun span(k:String)=CryptoByteSpan(frames.getValue(k),32,inputs.getValue(k).size)
  val out=span("out");val secret=span("secret");val public=span("public");val signature=span("signature");val kp=span("keypair");val ctx=CryptoSha3.Context(frames.getValue("ctx"),32);val message=span("message");val context=span("context");val ph=r["prehashed"]?.jsonPrimitive?.int?:0;val fp=r["forPrehash"]?.jsonPrimitive?.int?:0
  if(r["prehash"]?.jsonPrimitive?.boolean==true){engine.prehashInit(ctx);val chunks=r["prehashChunks"]?.jsonArray;if(chunks==null)engine.prehashUpdate(ctx,message)else chunks.forEach{val v=it.jsonArray.map{b->b.jsonPrimitive.int.toByte()}.toByteArray();engine.prehashUpdate(ctx,CryptoByteSpan(v))}}
  var ret=0
  when(r.getValue("op").jsonPrimitive.content){
   "clamp"->engine.clamp(out)
   "hashInitWithDom"->engine.hashInitWithDom(ctx,ph,fp,context)
   "prehashInit"->engine.prehashInit(ctx)
   "convertPrivateKeyToX448"->engine.convertPrivateKeyToX448(out,secret)
   "derivePublicKey"->engine.derivePublicKey(out,secret)
   "signInternal"->engine.signInternal(out,secret,public,message,ph,context)
   "sign"->engine.sign(out,secret,public,message,ph,context)
   "signPrehash"->engine.signPrehash(out,secret,public,ctx,context)
   "deriveKeypair"->engine.deriveKeypair(kp,secret)
   "keypairExtractPublicKey"->engine.keypairExtractPublicKey(out,kp)
   "keypairExtractPrivateKey"->engine.keypairExtractPrivateKey(out,kp)
   "keypairDestroy"->engine.keypairDestroy(kp)
   "keypairSign"->engine.keypairSign(out,kp,message,ph,context)
   "keypairSignPrehash"->engine.keypairSignPrehash(out,kp,ctx,context)
   "verify"->ret=engine.verify(signature,public,message,ph,context)
   "verifyPrehash"->ret=engine.verifyPrehash(signature,public,ctx,context)
   "prehashUpdate"->engine.prehashUpdate(ctx,message)
   "prehashDestroy"->engine.prehashDestroy(ctx)
   else->error("operation")
  }
  return buildJsonObject{put("ret",ret);put("frames",buildJsonObject{frames.forEach{(k,v)->put(k,JsonArray(v.map{JsonPrimitive(it.toInt()and 255)}))}})}
 }
 @JvmStatic fun main(args:Array<String>){var count=0;GZIPInputStream(File(args[0]).inputStream()).bufferedReader().useLines{lines->lines.drop(1).forEach{val row=Json.parseToJsonElement(it).jsonObject;val actual=observe(row.getValue("request").jsonObject);check(actual==row.getValue("expected")){"row$count request=${row.getValue("request")} expected=${row.getValue("expected")} actual=$actual"};count++;if(count%100==0)println("PASS$count")}};println("PASS$count Ed448 rawcore rows")}
}

class CryptoDecafEd448Test {
 private val lines by lazy {GZIPInputStream(javaClass.getResourceAsStream("/crypto-decaf-ed448/data.jsonl.gz")!!).bufferedReader().use {it.readLines()}}
 private val manifest by lazy {Json.parseToJsonElement(lines.first()).jsonObject}
 @org.junit.jupiter.api.TestFactory
 fun originalCoreObservations():java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> = lines.drop(1).mapIndexed{index,line->org.junit.jupiter.api.DynamicTest.dynamicTest("core/$index"){
  val row=Json.parseToJsonElement(line).jsonObject;org.junit.jupiter.api.Assertions.assertEquals(row.getValue("expected"),Ed448Replay.observe(row.getValue("request").jsonObject),"row $index")
 }}.stream()
 @org.junit.jupiter.api.TestFactory
 fun sourcePins():java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> = manifest.getValue("sources").jsonObject.map{(path,expected)->org.junit.jupiter.api.DynamicTest.dynamicTest(path){
  val bytes=File(System.getProperty("kotdoc.root"),path).readBytes();org.junit.jupiter.api.Assertions.assertEquals(expected.jsonPrimitive.content,java.security.MessageDigest.getInstance("SHA-256").digest(bytes).toHexString())
 }}.stream()
 @org.junit.jupiter.api.Test
 fun fullSourceMatrixAndOriginalPartition() {
  val root=File(System.getProperty("kotdoc.root"));val matrix=Json.parseToJsonElement(File(root,"tools/decaf-ed448-reference/Ed448SourceMatrix.json").readText()).jsonObject
  org.junit.jupiter.api.Assertions.assertEquals(16,matrix.getValue("definitions").jsonArray.size)
  org.junit.jupiter.api.Assertions.assertEquals(3,matrix.getValue("definitions").jsonArray.count{it.jsonObject.getValue("private").jsonPrimitive.boolean})
  org.junit.jupiter.api.Assertions.assertEquals(18,manifest.getValue("operations").jsonArray.size)
  org.junit.jupiter.api.Assertions.assertEquals(9885,lines.size)
  org.junit.jupiter.api.Assertions.assertEquals(96,lines.drop(1).count{"\"kat\":" in it})
  org.junit.jupiter.api.Assertions.assertTrue(File(root,"tools/decaf-ed448-reference/OriginalSuite.txt").readText().contains("25 examples, 0 failures"))
 }
 @org.junit.jupiter.api.Test
 fun mismatchedPrehashPublicKeyFatalBoundary() {
  for((bits,sb,vector)in listOf(Triple(32,32,false),Triple(64,64,false),Triple(64,32,false),Triple(64,32,true))) {
   val core=CryptoDecafEd448(bits,sb,vector);val ctx=CryptoSha3.Context(ByteArray(344));core.prehashInit(ctx);val before=ctx.data.copyOf();val out=ByteArray(114){81};
   val error=org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java){core.signPrehash(CryptoByteSpan(out),CryptoByteSpan(ByteArray(57)),CryptoByteSpan(ByteArray(57)),ctx,CryptoByteSpan(ByteArray(0)))}
   org.junit.jupiter.api.Assertions.assertEquals("original abort: mismatched Ed448 public key",error.message)
   org.junit.jupiter.api.Assertions.assertArrayEquals(before,ctx.data);org.junit.jupiter.api.Assertions.assertTrue(out.all{it==81.toByte()})
  }
 }
}
