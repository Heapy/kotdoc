package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.x509.*
import java.math.BigInteger
import java.io.File
import kotlinx.serialization.json.*
object Pkcs15RandomReplay {
 data class Drg(val index:Int):CryptoDrg<Drg>{override fun randomBytesGenerate(length:Int):Pair<ByteArray,Drg> = error("dictionary only")}
 fun observe(o:JsonObject):JsonObject {
  fun text(k:String,d:String="")=o[k]?.jsonPrimitive?.content?:d
  fun number(k:String,d:Int)=text(k,"$d").toInt()
  fun bytes(k:String)=o[k]?.jsonArray?.map{it.jsonPrimitive.int.toByte()}?.toByteArray()?:if(k=="message")byteArrayOf(1,2,3)else ByteArray(0)
  val mode=text("mode","normal");val draws=mutableListOf<Int>()
  val ops=CryptoDrgOperations<Drg>{n,previous->
   if(mode=="pairBottom")error("pair-bottom")
   val i=previous.value.index;draws+=n
   if(i>=number("budget",8))error("rng-budget")
   val raw=cryptoRandomMemo{when(mode){"fieldsBottom","bytesBottom"->error("bytes-bottom");"zero"->ByteArray(maxOf(0,n));"empty"->ByteArray(0);"short"->ByteArray(maxOf(0,n-1)){7};"long"->ByteArray(maxOf(0,n+1)){7};"retrySuccess"->ByteArray(maxOf(0,n)){if(i<=number("retries",2))0 else 7};else->ByteArray(maxOf(0,n)){7}}}
   raw to cryptoRandomMemo{if(mode in listOf("fieldsBottom","stateBottom"))error("state-bottom");Drg(i+1)}
  }
  val k=number("size",32);val key=X509RsaPrivateKey(X509RsaPublicKey(k.toLong(),BigInteger.ONE.shiftLeft(8*maxOf(0,k))-BigInteger.ONE,BigInteger.ONE),BigInteger.ONE,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO)
  val message=cryptoRandomMemo{if(text("bottom")=="message")error("message-bottom");bytes("message")};val input=lazyOf(bytes("input"))
  val rsa=CryptoRsa(CryptoNumberPrime(CryptoProbabilistic{error("unused entropy")}));val hash=CryptoRsaPkcs15.algorithm(CryptoRsaPkcs15.Algorithm.SHA1)
  val action=when(text("op","pad")){"encrypt"->CryptoRsaPkcs15.encryptAction(lazyOf(key.publicKey),message,ops);"signSafer"->CryptoRsaPkcs15.signSaferAction(rsa,lazyOf(hash),lazyOf(key),message,ops);"decryptSafer"->CryptoRsaPkcs15.decryptSaferAction(rsa,lazyOf(key),input,ops);else->CryptoRsaPkcs15.padAction(lazyOf(k.toLong()),message,ops)}
  val initial=cryptoRandomMemo{if(mode=="generatorBottom")error("generator-bottom");Drg(1)};val pair=CryptoPseudoRandom.withDRG(initial,action)
  val value=cryptoRandomMemo<JsonElement>{when(text("projection","bytes")){
   "ignore"->JsonPrimitive(CryptoPseudoRandom.withDRG(initial,action.map{42}).first.value)
   "root"->JsonPrimitive(if(pair.first.value is CryptoRsaPkcs15.Result.Failure)"Left"else"Right")
   "state"->JsonPrimitive(pair.second.value.index)
   else->when(val r=pair.first.value){is CryptoRsaPkcs15.Result.Failure->buildJsonObject{put("error",r.error.name)};is CryptoRsaPkcs15.Result.Success->buildJsonObject{put("bytes",JsonArray(r.bytes().map{JsonPrimitive(it.toInt()and 255)}))}}
  }}
  val results=List(2){try{value.value}catch(t:Throwable){buildJsonObject{put("exception",t.message?.substringBefore('\n')?:t.javaClass.simpleName)}}}
  return buildJsonObject{put("results",JsonArray(results));put("draws",JsonArray(draws.map(::JsonPrimitive)))}
 }
 @JvmStatic fun main(args:Array<String>){var n=0;for(v in Json.parseToJsonElement(File(args[0]).readText()).jsonArray){val r=v.jsonObject;val actual=observe(r.getValue("request").jsonObject);check(actual==r["expected"]){"row$n request=${r["request"]} expected=${r["expected"]} actual=$actual"};n++};println("PASS $n original PKCS15 custom RNG rows")}
}
