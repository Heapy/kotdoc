package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.x509.*
import kotlinx.serialization.json.*
import java.math.BigInteger
import java.io.File
object PssBoundaryReplay {
 fun observe(o:JsonObject):JsonElement {
  fun number(k:String,d:Long)=o[k]?.jsonPrimitive?.longOrNull?:d
  val bits=cryptoRandomMemo{if(o["bits"]?.jsonPrimitive?.content=="bottom")error("bits");number("bits",0)}
  val bytes=cryptoRandomMemo{if(o["bytes"]?.jsonPrimitive?.content=="bottom")error("bytes");o["input"]?.jsonArray?.map{it.jsonPrimitive.int.toByte()}?.toByteArray()?:ByteArray(0)}
  return try{when(o.getValue("op").jsonPrimitive.content){
   "normalize"->JsonArray(CryptoRsaPss.normalizeToKeySizeDeferred(bits,bytes).value.map{JsonPrimitive(it.toInt()and 255)})
   "guard"->{val dict=CryptoRsaPss.Dictionary(CryptoHmacAlgorithms.algorithm(CryptoHmacAlgorithms.Algorithm.SHA1)){number("hashLength",20)};val key=X509RsaPrivateKey(X509RsaPublicKey(number("length",128),BigInteger.ONE.shiftLeft(number("modulusBits",1024).toInt())-BigInteger.ONE,BigInteger.ONE),BigInteger.ONE,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO);val params=cryptoRandomMemo<CryptoRsaPss.Params>{error("unused-params")};val r=CryptoRsaPss.signWithSaltDeferred(dict,lazyOf(ByteArray(number("saltBytes",0).toInt()){7}),lazyOf(null),params,lazyOf(key),lazyOf(ByteArray(0)));JsonPrimitive(when(r){is CryptoRsaPss.Result.Failure->r.error.name;is CryptoRsaPss.Result.Success->"Right"})}
   else->JsonPrimitive(CryptoRsaPss.emTruncate(bits.value))
  }}catch(t:Throwable){buildJsonObject{put("exception",t.message?.substringBefore('\n')?:t.javaClass.simpleName)}}
 }
 @JvmStatic fun main(args:Array<String>){var n=0;for(v in Json.parseToJsonElement(File(args[0]).readText()).jsonArray){val r=v.jsonObject;val got=observe(r.getValue("request").jsonObject);check(got==r["expected"]){"row$n request=${r["request"]} expected=${r["expected"]} actual=$got"};n++};println("PASS $n original PSS private helpers/virtual bounds")}
}
