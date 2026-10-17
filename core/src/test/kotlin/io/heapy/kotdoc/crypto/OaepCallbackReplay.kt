package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.x509.*
import java.math.BigInteger
import java.io.File
import kotlinx.serialization.json.*
object OaepCallbackReplay {
 fun observe(r:JsonObject):JsonObject {
  fun text(k:String)=r[k]?.jsonPrimitive?.content?:""
  fun bytes(k:String)=r[k]?.jsonArray?.map{it.jsonPrimitive.int.toByte()}?.toByteArray()?:ByteArray(0)
  val draws=mutableListOf<Int>();val mode=text("mode");val k=r.getValue("size").jsonPrimitive.int
  val key=X509RsaPublicKey(k.toLong(),BigInteger.ONE.shiftLeft(8*maxOf(0,k))-BigInteger.ONE,BigInteger.ONE)
  val privateKey=X509RsaPrivateKey(key,BigInteger.ONE,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO)
  val params=CryptoRsaOaep.defaultParams(CryptoHmacAlgorithms.algorithm(CryptoHmacAlgorithms.Algorithm.SHA1))
  val rsa=CryptoRsa(CryptoNumberPrime(CryptoProbabilistic{error("unused entropy")}))
  val message=cryptoRandomMemo{if(r["messageBottom"]==JsonPrimitive(true))error("message-bottom");bytes("message")}
  // Host callback result bytes are strict. A source returned ByteString bottom is
  // represented separately by Deferred seed providers in the demand corpus.
  val callback:(Int)->Lazy<ByteArray> = {n->draws+=n;if(mode=="callbackBottom")error("callback-bottom");cryptoRandomMemo{if(mode=="bytesBottom")error("bytes-bottom");ByteArray(maxOf(0,n)){if(mode=="zero")0 else if(mode=="ff"||(mode=="retrySuccess"&&draws.size<100))255.toByte()else 1}}}
  val result=runCatching{if(text("operation")=="encrypt")CryptoRsaOaep.encryptIo(params,lazyOf(key),message,callback)else CryptoRsaOaep.decryptSaferIo(rsa,params,lazyOf(privateKey),lazyOf(bytes("cipher")),callback)}
  val evaluation=cryptoRandomMemo<JsonElement>{val deferred=result.getOrThrow();when(text("projection")){
   "ignore"->JsonPrimitive(42)
   "root"->{deferred.value;JsonPrimitive(42)}
   else->when(val v=deferred.value){is CryptoRsaOaep.Result.Failure->buildJsonObject{put("error",v.error.name)};is CryptoRsaOaep.Result.Success->buildJsonObject{put("bytes",JsonArray(v.bytes().map{JsonPrimitive(it.toInt() and 255)}))}}
  }}
  val results=List(2){try{evaluation.value}catch(t:Throwable){buildJsonObject{put("exception",t.message?.substringBefore('\n')?:t.javaClass.simpleName)}}}
  return buildJsonObject{put("results",JsonArray(results));put("draws",JsonArray(draws.map(::JsonPrimitive)))}
 }
 @JvmStatic fun main(args:Array<String>){var count=0;for(v in Json.parseToJsonElement(File(args[0]).readText()).jsonArray){val r=v.jsonObject;val actual=observe(r.getValue("request").jsonObject);check(actual==r["expected"]){"row$count expected=${r["expected"]} actual=$actual request=${r["request"]}"};count++};println("PASS $count original callback rows")}
}
