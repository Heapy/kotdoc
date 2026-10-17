package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.x509.*
import java.math.BigInteger
import java.io.File
import kotlinx.serialization.json.*
object OaepRandomReplay {
 data class Drg(val index:Int):CryptoDrg<Drg>{override fun randomBytesGenerate(length:Int):Pair<ByteArray,Drg> = error("dictionary only")}
 fun observe(r:JsonObject):JsonObject {
  fun text(k:String)=r[k]?.jsonPrimitive?.content?:""
  fun bytes(k:String)=r[k]?.jsonArray?.map{it.jsonPrimitive.int.toByte()}?.toByteArray()?:ByteArray(0)
  val draws=mutableListOf<Int>();val mode=text("mode")
  val ops=CryptoDrgOperations<Drg>{length,g->
   if(mode=="pairBottom")error("pair-bottom")
   val index=if(mode=="generatorBottom")error("generator-bottom")else g.value.index
   draws+=length
   if(mode=="fieldsBottom")cryptoRandomMemo<ByteArray>{error("bytes-bottom")} to cryptoRandomMemo<Drg>{error("state-bottom")}else lazyOf(ByteArray(maxOf(0,length)){if(mode=="zero")0 else if(mode=="ff"||(mode=="retrySuccess"&&index<100))255.toByte()else(index+it).toByte()}) to lazyOf(Drg(index+1))
  }
  val k=r.getValue("size").jsonPrimitive.int
  val key=X509RsaPublicKey(k.toLong(),BigInteger.ONE.shiftLeft(8*maxOf(0,k))-BigInteger.ONE,BigInteger.ONE)
  val privateKey=X509RsaPrivateKey(key,BigInteger.ONE,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO)
  val params=CryptoRsaOaep.defaultParams(CryptoHmacAlgorithms.algorithm(CryptoHmacAlgorithms.Algorithm.SHA1))
  val rsa=CryptoRsa(CryptoNumberPrime(CryptoProbabilistic{error("unused entropy")}))
  val message=cryptoRandomMemo{if(r["messageBottom"]==JsonPrimitive(true))error("message-bottom");bytes("message")}
  val action=if(text("operation")=="encrypt")CryptoRsaOaep.encryptAction(params,lazyOf(key),message,ops)else CryptoRsaOaep.decryptSaferAction(rsa,params,lazyOf(privateKey),lazyOf(bytes("cipher")),ops)
  val pair=CryptoPseudoRandom.withDRG(Drg(1),action)
  val evaluation=cryptoRandomMemo<JsonElement>{when(text("projection")){
   "ignore"->JsonPrimitive(CryptoPseudoRandom.withDRG(Drg(1),action.map{42}).first.value)
   "root"->{pair.first.value;JsonPrimitive(42)}
   "state"->JsonPrimitive(pair.second.value.index)
   else->when(val v=pair.first.value){is CryptoRsaOaep.Result.Failure->buildJsonObject{put("error",v.error.name)};is CryptoRsaOaep.Result.Success->buildJsonObject{put("bytes",JsonArray(v.bytes().map{JsonPrimitive(it.toInt() and 255)}))}}
  }}
  val results=List(2){try{evaluation.value}catch(t:Throwable){buildJsonObject{put("exception",t.message?.substringBefore('\n')?:t.javaClass.simpleName)}}}
  return buildJsonObject{put("results",JsonArray(results));put("draws",JsonArray(draws.map(::JsonPrimitive)))}
 }
 @JvmStatic fun main(args:Array<String>){var count=0;for(v in Json.parseToJsonElement(File(args[0]).readText()).jsonArray){val r=v.jsonObject;val actual=observe(r.getValue("request").jsonObject);check(actual==r["expected"]){"row$count expected=${r["expected"]} actual=$actual request=${r["request"]}"};count++};println("PASS $count original RNG rows")}
}
