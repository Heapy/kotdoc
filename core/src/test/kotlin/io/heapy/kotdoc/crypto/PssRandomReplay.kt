package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.x509.*
import kotlinx.serialization.json.*
import java.math.BigInteger
import java.io.File
object PssRandomReplay {
 data class Drg(val index:Int):CryptoDrg<Drg>{override fun randomBytesGenerate(length:Int):Pair<ByteArray,Drg> = error("dictionary only")}
 fun observe(o:JsonObject):JsonObject {
  fun text(k:String,d:String="")=o[k]?.jsonPrimitive?.content?:d
  fun num(k:String,d:Int)=text(k,"$d").toInt()
  fun integer(k:String,d:BigInteger)=text(k,d.toString()).toBigInteger()
  fun bottom(k:String){if(text("bottom")==k)error(k)}
  val draws=mutableListOf<Long>();val mode=text("mode","normal")
  val ops=CryptoRsaPss.SourceRandomOperations<Drg>{length,previous->
   if(mode=="pairBottom")error("pair-bottom");val i=previous.value.index
   val n=if(mode=="ignoreLength"){-777L}else length.value;draws+=n
   if(i>=num("budget",8))error("rng-budget")
   val raw=cryptoRandomMemo{when(mode){"fieldsBottom","bytesBottom"->error("bytes-bottom");"ignoreLength"->byteArrayOf(9,8);"empty"->ByteArray(0);"zero"->ByteArray(maxOf(0,n.toInt()));"short"->ByteArray(maxOf(0,n.toInt()-1)){7};"long"->ByteArray(maxOf(0,n.toInt()+1)){7};"longThenNormal"->ByteArray(maxOf(0,n.toInt()+if(i<=num("retries",99))1 else 0)){7};"retrySuccess"->ByteArray(maxOf(0,n.toInt())){if(i<=num("retries",2))0 else 7};else->ByteArray(maxOf(0,n.toInt())){7}}}
   raw to cryptoRandomMemo{if(mode in listOf("fieldsBottom","stateBottom"))error("state-bottom");Drg(i+1)}
  }
  val k=num("size",64);val pub=X509RsaPublicKey.deferred({bottom("size");k.toLong()},{bottom("n");integer("n",BigInteger.ONE.shiftLeft(8*maxOf(0,k))-BigInteger.ONE)},{integer("e",BigInteger.ONE)})
  val key=cryptoRandomMemo{bottom("key");X509RsaPrivateKey(pub,integer("d",BigInteger.ONE),integer("p",BigInteger.ZERO),integer("q",BigInteger.ZERO),integer("dP",BigInteger.ZERO),integer("dQ",BigInteger.ZERO),integer("qinv",BigInteger.ZERO))}
  val hash=CryptoHmacAlgorithms.algorithm(CryptoHmacAlgorithms.Algorithm.SHA1);val dict=CryptoRsaPss.Dictionary(hash);val params=cryptoRandomMemo{bottom("params");CryptoRsaPss.Params(dict,lazyOf(CryptoRsaPss.MaskAlgorithm{seed,length->CryptoMaskGen.mgf1Deferred(hash,seed,cryptoRandomMemo{Math.toIntExact(length.value)})}),cryptoRandomMemo{bottom("saltLength");text("saltLength","20").toLong()},cryptoRandomMemo{bottom("trailer");188.toByte()})}
  val message=cryptoRandomMemo{bottom("message");o["message"]?.jsonArray?.map{it.jsonPrimitive.int.toByte()}?.toByteArray()?:byteArrayOf(1,2,3)};val digest=cryptoRandomMemo{bottom("digest");CryptoMaskGen.hash(hash,ByteArray(0))};val rsa=CryptoRsa(CryptoNumberPrime(CryptoProbabilistic{error("unused entropy")}))
  val action=when(text("op","sign")){"signDigest"->CryptoRsaPss.signDigestSourceAction(dict,lazyOf(null),params,key,digest,ops);"signSafer"->CryptoRsaPss.signSaferSourceAction(dict,rsa,params,key,message,ops);"signDigestSafer"->CryptoRsaPss.signDigestSaferSourceAction(dict,rsa,params,key,digest,ops);else->CryptoRsaPss.signSourceAction(dict,lazyOf(null),params,key,message,ops)}
  val initial=cryptoRandomMemo{if(mode=="generatorBottom")error("generator-bottom");Drg(1)};val pair=CryptoPseudoRandom.withDRG(initial,action)
  val value=cryptoRandomMemo<JsonElement>{when(text("projection","bytes")){
   "ignore"->JsonPrimitive(CryptoPseudoRandom.withDRG(initial,action.map{42}).first.value)
   "root"->JsonPrimitive(if(pair.first.value is CryptoRsaPss.Result.Failure)"Left"else"Right")
   "state"->JsonPrimitive(pair.second.value.index)
   else->when(val r=pair.first.value){is CryptoRsaPss.Result.Failure->buildJsonObject{put("error",r.error.name)};is CryptoRsaPss.Result.Success->buildJsonObject{put("bytes",JsonArray(r.bytes().map{JsonPrimitive(it.toInt()and 255)}))}}
  }}
  val results=List(2){try{value.value}catch(t:Throwable){buildJsonObject{put("exception",t.message?.substringBefore('\n')?:t.javaClass.simpleName)}}}
  return buildJsonObject{put("results",JsonArray(results));put("draws",JsonArray(draws.map(::JsonPrimitive)))}
 }
 @JvmStatic fun main(args:Array<String>){var n=0;for(v in Json.parseToJsonElement(File(args[0]).readText()).jsonArray){val r=v.jsonObject;val got=observe(r.getValue("request").jsonObject);check(got==r["expected"]){"row$n request=${r["request"]} expected=${r["expected"]} actual=$got"};n++};println("PASS $n original PSS pure RNG observations")}
}
