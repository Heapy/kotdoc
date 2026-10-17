package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.x509.*
import java.io.File
import java.math.BigInteger
import kotlinx.serialization.json.*
object Pkcs15CallbackReplay {
 fun observe(o:JsonObject):JsonObject {
  fun text(k:String)=o[k]?.jsonPrimitive?.content?:""
  fun number(k:String)=o[k]?.jsonPrimitive?.int?:0
  fun bytes(k:String)=o[k]?.jsonArray?.map{it.jsonPrimitive.int.toByte()}?.toByteArray()?:ByteArray(0)
  val draws=mutableListOf<Int>();val mode=text("mode")
  val callback:(Int)->Lazy<ByteArray> ={n->val index=draws.size;draws+=n;if(index>=number("budget"))error("rng-budget");if(mode=="callbackBottom")error("callback-bottom");cryptoRandomMemo{if(mode=="bytesBottom")error("bytes-bottom");ByteArray(maxOf(0,n)){if(mode=="zero"||(mode=="retrySuccess"&&index<number("retries")))0 else 7}}}
  val k=number("size");val key=X509RsaPrivateKey(X509RsaPublicKey(k.toLong(),BigInteger.ONE.shiftLeft(8*maxOf(0,k))-BigInteger.ONE,BigInteger.ONE),BigInteger.ONE,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO)
  val message=cryptoRandomMemo{if(o["messageBottom"]?.jsonPrimitive?.boolean==true)error("message-bottom");bytes("message")};val rsa=CryptoRsa(CryptoNumberPrime(CryptoProbabilistic{error("unused entropy")}));val hash=CryptoRsaPkcs15.algorithm(CryptoRsaPkcs15.Algorithm.SHA1)
  val outcome=cryptoRandomMemo{when(text("operation")){
   "encrypt"->CryptoRsaPkcs15.encryptIo(lazyOf(key.publicKey),message,callback)
   "decryptSafer"->CryptoRsaPkcs15.decryptSaferIo(rsa,lazyOf(key),lazyOf(bytes("cipher")),callback)
   "signSafer"->CryptoRsaPkcs15.signSaferIo(rsa,lazyOf(hash),lazyOf(key),message,callback)
   else->CryptoRsaPkcs15.padIo(lazyOf(k.toLong()),message,callback)
  }}
  // Execute the IO action once, then independently demand its returned value.
  val error=try{outcome.value;null}catch(t:Throwable){t}
  val value=cryptoRandomMemo<JsonElement>{if(error!=null)throw error;when(text("projection")){
   "ignore"->JsonPrimitive(42)
   "root"->{outcome.value.value;JsonPrimitive(42)}
   else->when(val r=outcome.value.value){is CryptoRsaPkcs15.Result.Failure->buildJsonObject{put("error",r.error.name)};is CryptoRsaPkcs15.Result.Success->buildJsonObject{put("bytes",JsonArray(r.bytes().map{JsonPrimitive(it.toInt()and 255)}))}}
  }}
  val results=List(2){try{value.value}catch(t:Throwable){buildJsonObject{put("exception",t.message?.substringBefore('\n')?:t.javaClass.simpleName)}}}
  return buildJsonObject{put("results",JsonArray(results));put("draws",JsonArray(draws.map(::JsonPrimitive)))}
 }
 @JvmStatic fun main(args:Array<String>){var n=0;for(v in Json.parseToJsonElement(File(args[0]).readText()).jsonArray){val r=v.jsonObject;val got=observe(r.getValue("request").jsonObject);check(got==r["expected"]){"row$n request=${r["request"]} expected=${r["expected"]} actual=$got"};n++};println("PASS $n original PKCS15 IO callback observations")}
}
