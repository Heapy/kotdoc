package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.x509.*
import kotlinx.serialization.json.*
import java.math.BigInteger
import java.io.File
object PssCallbackReplay {
 fun observe(o:JsonObject):JsonObject {
  fun text(k:String,d:String="")=o[k]?.jsonPrimitive?.content?:d
  fun bottom(k:String){if(text("bottom")==k)error(k)}
  val mode=text("mode");val draws=mutableListOf<Long>()
  val callback=CryptoRsaPss.SourceRandomCallback{length->
   val index=draws.size;if(mode=="callbackBeforeLength")error("callback-before-length")
   val n=if(mode=="ignoreLength")-777L else length.value;draws+=n
   if(index>=text("budget","8").toInt())error("rng-budget")
   if(mode=="callbackBottom")error("callback-bottom")
   cryptoRandomMemo{if(mode=="bytesBottom")error("bytes-bottom");when(mode){"ignoreLength"->byteArrayOf(9,8);"short"->ByteArray(maxOf(0,n.toInt()-1)){7};"long"->ByteArray(maxOf(0,n.toInt()+1)){7};"zero"->ByteArray(maxOf(0,n.toInt()));else->ByteArray(maxOf(0,n.toInt())){7}}}
  }
  val k=text("size","64").toInt();val pub=X509RsaPublicKey.deferred({bottom("size");k.toLong()},{bottom("n");BigInteger.ONE.shiftLeft(8*maxOf(0,k))-BigInteger.ONE},{BigInteger.ONE});val key=cryptoRandomMemo{bottom("key");X509RsaPrivateKey(pub,BigInteger.ONE,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO)}
  val hash=CryptoHmacAlgorithms.algorithm(CryptoHmacAlgorithms.Algorithm.SHA1);val dict=CryptoRsaPss.Dictionary(hash);val params=cryptoRandomMemo{bottom("params");CryptoRsaPss.Params(dict,lazyOf(CryptoRsaPss.MaskAlgorithm{seed,length->CryptoMaskGen.mgf1Deferred(hash,seed,cryptoRandomMemo{Math.toIntExact(length.value)})}),cryptoRandomMemo{bottom("saltLength");text("saltLength","20").toLong()},cryptoRandomMemo{bottom("trailer");188.toByte()})}
  val message=cryptoRandomMemo{bottom("message");byteArrayOf(1,2,3)};val digest=cryptoRandomMemo{bottom("digest");CryptoMaskGen.hash(hash,ByteArray(0))};val rsa=CryptoRsa(CryptoNumberPrime(CryptoProbabilistic{error("unused entropy")}))
  val outcome=cryptoRandomMemo{when(text("operation","sign")){
   "signDigest"->CryptoRsaPss.signDigestSourceIo(dict,lazyOf(null),params,key,digest,callback)
   "signSafer"->CryptoRsaPss.signSaferSourceIo(dict,rsa,params,key,message,callback)
   "signDigestSafer"->CryptoRsaPss.signDigestSaferSourceIo(dict,rsa,params,key,digest,callback)
   else->CryptoRsaPss.signSourceIo(dict,lazyOf(null),params,key,message,callback)
  }}
  val failure=try{outcome.value;null}catch(t:Throwable){t}
  val value=cryptoRandomMemo<JsonElement>{if(failure!=null)throw failure;when(text("projection")){
   "ignore"->JsonPrimitive(42)
   "root"->{outcome.value.value;JsonPrimitive(42)}
   else->when(val r=outcome.value.value){is CryptoRsaPss.Result.Failure->buildJsonObject{put("error",r.error.name)};is CryptoRsaPss.Result.Success->buildJsonObject{put("bytes",JsonArray(r.bytes().map{JsonPrimitive(it.toInt()and 255)}))}}
  }}
  val results=List(2){try{value.value}catch(t:Throwable){buildJsonObject{put("exception",t.message?.substringBefore('\n')?:t.javaClass.simpleName)}}}
  return buildJsonObject{put("results",JsonArray(results));put("draws",JsonArray(draws.map(::JsonPrimitive)))}
 }
 @JvmStatic fun main(args:Array<String>){var n=0;for(v in Json.parseToJsonElement(File(args[0]).readText()).jsonArray){val r=v.jsonObject;val got=observe(r.getValue("request").jsonObject);check(got==r["expected"]){"row$n request=${r["request"]} expected=${r["expected"]} actual=$got"};n++};println("PASS $n original PSS IO callback observations")}
}
