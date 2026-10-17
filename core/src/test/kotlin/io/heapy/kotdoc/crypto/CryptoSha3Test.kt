package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
internal object Sha3Replay {
 private fun bytes(v:JsonElement)=v.jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray()
 private fun json(v:ByteArray)=JsonArray(v.map{JsonPrimitive(it.toInt()and 255)})
 fun observe(r:JsonObject):JsonObject {
  if(r["chunkPlan"]?.jsonPrimitive?.boolean==true){val offsets=mutableListOf<JsonElement>();val sizes=mutableListOf<JsonElement>();CryptoDecafShake256.forChunks(r.getValue("length").jsonPrimitive.long){offset,size->offsets.add(JsonPrimitive(offset));sizes.add(JsonPrimitive(size))};return buildJsonObject{put("offsets",JsonArray(offsets));put("lengths",JsonArray(sizes))}}
  if(r["digest"]?.jsonPrimitive?.boolean==true){
   val chunks=r.getValue("chunks").jsonArray.map(::bytes);val security=r.getValue("securityBits").jsonPrimitive.int;val bits=r.getValue("outputBits").jsonPrimitive.int;val pad=r.getValue("pad").jsonPrimitive.int
   if(r["literal"]!=null)return buildJsonObject{put("digest",json(CryptoSha3.digest(chunks.fold(ByteArray(0)){a,b->a+b},security,bits,pad)))}
   val alg=CryptoSha3.Shake(security,bits);val ctx=alg.newContext();chunks.forEach{alg.update(ctx,CryptoByteSpan(it))};val output=ByteArray(alg.digestSize);if(pad==4)alg.cshakeFinalize(ctx,CryptoByteSpan(output))else alg.finalize(ctx,CryptoByteSpan(output))
   check(output.contentEquals(CryptoSha3.digest(chunks.fold(ByteArray(0)){a,b->a+b},security,bits,pad)))
   return buildJsonObject{put("digest",json(output));put("outputBits",alg.outputBits);put("blockSize",alg.blockSize);put("digestSize",alg.digestSize);put("contextSize",alg.contextSize);put("show",alg.toString())}
  }
  if(r["hash"]?.jsonPrimitive?.boolean==true){CryptoSha3.observe=null;val data=bytes(r.getValue("bytes"));val length=r.getValue("length").jsonPrimitive.int;val size=maxOf(data.size,length);val input=byteArrayOf(17)+data+ByteArray(size-data.size){165.toByte()}+byteArrayOf(34);val output=if(r.getValue("alias").jsonPrimitive.boolean)input else byteArrayOf(17)+ByteArray(length){165.toByte()}+byteArrayOf(34);CryptoDecafShake256.hash(CryptoByteSpan(output,1,length),CryptoByteSpan(input,1,data.size));return buildJsonObject{put("source",json(input));put("output",json(output))}}
  val ctx=CryptoSha3.Context(ByteArray(440){if(it<32)17 else if(it>=408)34 else 81},32);val contexts=mutableListOf<JsonElement>();val buffers=mutableListOf<JsonElement>();val digest=MessageDigest.getInstance("SHA-256");var count=0;val states=mutableListOf<JsonElement>()
  CryptoSha3.observe={label,index,state->val b=ByteBuffer.allocate(216).order(ByteOrder.LITTLE_ENDIAN);b.put(label.toByteArray());b.position(8);b.putInt(index);b.putInt(0);state.forEach(b::putLong);digest.update(b.array());count++;if(r["trace"]?.jsonPrimitive?.boolean==true)states.add(buildJsonObject{put("label",label);put("index",index);put("state",JsonArray(state.map{JsonPrimitive(it.toULong().toString(16).padStart(16,'0'))}))})}
  try {for(value in r.getValue("steps").jsonArray){val step=value.jsonObject;val op=step.getValue("op").jsonPrimitive.content;val data=step["bytes"]?.let(::bytes)?:ByteArray(0);val length=step["length"]?.jsonPrimitive?.int?:data.size;val alignment=step["alignment"]?.jsonPrimitive?.int?:0;val arg=step["arg"]?.jsonPrimitive?.int?:0;val body=maxOf(data.size,length,if(op in listOf("finalize","keccakFinalize"))arg/8 else 0);val buffer=ByteArray(32+alignment){17}+data+ByteArray(body-data.size){165.toByte()}+ByteArray(32){34};val span=CryptoByteSpan(buffer,32+alignment,length)
   when(op){"init"->CryptoSha3.init(ctx,arg);"update"->CryptoSha3.update(ctx,span);"finalizePad"->CryptoSha3.finalizePad(ctx,arg);"output"->CryptoSha3.output(ctx,span);"finalize"->CryptoSha3.finalize(ctx,arg,span);"finalizeShake"->CryptoSha3.finalizeShake(ctx);"finalizeCshake"->CryptoSha3.finalizeCshake(ctx);"keccakInit"->CryptoSha3.keccakInit(ctx,arg);"keccakUpdate"->CryptoSha3.keccakUpdate(ctx,span);"keccakFinalize"->CryptoSha3.keccakFinalize(ctx,arg,span);"decafInit"->CryptoDecafShake256.init(ctx);"decafUpdate"->CryptoDecafShake256.update(ctx,span);"decafOutput"->CryptoDecafShake256.output(ctx,span);"decafFinal"->CryptoDecafShake256.final(ctx,span);"decafDestroy"->CryptoDecafShake256.destroy(ctx);else->error(op)}
   contexts.add(json(ctx.data));buffers.add(json(buffer))
  }}finally{CryptoSha3.observe=null}
  return buildJsonObject{put("contexts",JsonArray(contexts));put("buffers",JsonArray(buffers));put("trace",buildJsonObject{put("count",count);put("sha256",digest.digest().toHexString());if(r["trace"]?.jsonPrimitive?.boolean==true)put("states",JsonArray(states))})}
 }
 @JvmStatic fun main(args:Array<String>){var n=0;GZIPInputStream(File(args[0]).inputStream()).bufferedReader().useLines{lines->lines.drop(1).forEach{val row=Json.parseToJsonElement(it).jsonObject;val actual=observe(row.getValue("request").jsonObject);check(actual==row.getValue("expected")){"row$n request=${row.getValue("request")} expected=${row.getValue("expected")} actual=$actual"};n++;if(n%1000==0)println("PASS$n")}};println("PASS$n raw/state/round/stream/alias rows")}
}

class CryptoSha3Test {
 private val fixture by lazy {GZIPInputStream(javaClass.getResourceAsStream("/crypto-sha3/data.jsonl.gz")!!).bufferedReader().use {it.readLines()}}
 private val manifest by lazy {Json.parseToJsonElement(fixture.first()).jsonObject}
 @org.junit.jupiter.api.TestFactory
 fun originalObservations():java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> = fixture.drop(1).mapIndexed {index,line->
  org.junit.jupiter.api.DynamicTest.dynamicTest("original/$index") {
   val row=Json.parseToJsonElement(line).jsonObject
   org.junit.jupiter.api.Assertions.assertEquals(row.getValue("expected"),Sha3Replay.observe(row.getValue("request").jsonObject),"row $index")
  }
 }.stream()
 @org.junit.jupiter.api.TestFactory
 fun pinnedSources():java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> = manifest.getValue("sources").jsonObject.map {(path,hash)->
  org.junit.jupiter.api.DynamicTest.dynamicTest(path) {
   org.junit.jupiter.api.Assertions.assertEquals(hash.jsonPrimitive.content,MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),path).readBytes()).toHexString())
  }
 }.stream()
 @org.junit.jupiter.api.Test
 fun completePortableMatrixAndOriginalPartition() {
  val matrix=Json.parseToJsonElement(File(System.getProperty("kotdoc.root"),"tools/sha3-reference/SourceMatrix.json").readText()).jsonObject
  org.junit.jupiter.api.Assertions.assertEquals(17,matrix.getValue("portableDefinitions").jsonArray.size)
  org.junit.jupiter.api.Assertions.assertEquals(10,matrix.getValue("algorithms").jsonArray.size)
  matrix.getValue("constants").jsonObject.values.forEach {org.junit.jupiter.api.Assertions.assertEquals(24,it.jsonPrimitive.int)}
  org.junit.jupiter.api.Assertions.assertEquals(13257,fixture.size)
  org.junit.jupiter.api.Assertions.assertEquals(30,fixture.drop(1).count {"\"literal\":" in it})
  org.junit.jupiter.api.Assertions.assertEquals(20,fixture.drop(1).count {"\"chunkPlan\":true" in it})
  org.junit.jupiter.api.Assertions.assertTrue(File(System.getProperty("kotdoc.root"),"tools/sha3-reference/OriginalSuite.txt").readText().contains("48 examples, 0 failures"))
 }
}
