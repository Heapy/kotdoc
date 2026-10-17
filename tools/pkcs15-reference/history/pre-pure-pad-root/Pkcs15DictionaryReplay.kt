package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.x509.*
import kotlinx.serialization.json.*
import java.math.BigInteger
import java.io.File
object Pkcs15DictionaryReplay {
 fun observe(o:JsonObject):JsonObject{
  fun text(k:String,d:String="")=o[k]?.jsonPrimitive?.content?:d
  fun bytes()=o["message"]?.jsonArray?.map{it.jsonPrimitive.int.toByte()}?.toByteArray()?:byteArrayOf(1,2,3)
  val events=mutableListOf<String>()
  fun fault(s:String){if(text("fault")==s)error(s)}
  fun bottom(s:String){if(text("bottom")==s)error(s)}
  fun returned(s:String)=cryptoRandomMemo{if(text("returnedBottom")==s)error("$s-result");Unit}
  val raw=object:CryptoHmac.HashAlgorithm{
   override val blockSize get()=8.also{fault("blockSize")}
   override val digestSize get()=4.also{fault("digestSize")}
   override val contextSize get()=8.also{fault("contextSize")}
   override fun initialize(context:ByteArray){events+="init";fault("init");context.fill(0)}
   override fun update(context:ByteArray,bytes:ByteArray){events+="update:${bytes.size}";fault("update");for(b in bytes){context[0]=(context[0]+b).toByte();context[1]=(context[1]+1).toByte()}}
   override fun finalize(context:ByteArray,output:ByteArray){events+="finalize";fault("finalize");context.copyInto(output,0,0,4)}
   override fun initializeResult(context:ByteArray):Lazy<Unit>{initialize(context);return returned("init")}
   override fun updateResult(context:ByteArray,bytes:ByteArray):Lazy<Unit>{update(context,bytes);return returned("update")}
   override fun finalizeResult(context:ByteArray,output:ByteArray):Lazy<Unit>{finalize(context,output);return returned("finalize")}
  }
  val dictionary=CryptoRsaPkcs15.HashAlgorithmASN1(raw,{digest->cryptoRandomMemo{events+="asn1";fault("asn1");when(text("encoding","prefix")){"ignore"->byteArrayOf(9,8,7);"empty"->ByteArray(0);"raw"->digest.value;else->byteArrayOf(9,8)+digest.value}}},cryptoRandomMemo{bottom("witness");Unit})
  val size=cryptoRandomMemo{bottom("size");text("size","32").toLong()}
  val key=cryptoRandomMemo{bottom("key");X509RsaPrivateKey(X509RsaPublicKey.deferred({size.value},{BigInteger.ONE.shiftLeft(8*maxOf(0,size.value.toInt()))-BigInteger.ONE},{BigInteger.ONE}),BigInteger.ONE,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO)}
  val hash=cryptoRandomMemo{bottom("hash");if(text("hashMode","hash")=="none")null else dictionary}
  val message=cryptoRandomMemo{bottom("message");bytes()}
  fun array(b:ByteArray)=JsonArray(b.map{JsonPrimitive(it.toInt()and 255)})
  fun view(r:CryptoRsaPkcs15.Result)=when(r){is CryptoRsaPkcs15.Result.Failure->buildJsonObject{put("error",r.error.name)};is CryptoRsaPkcs15.Result.Success->buildJsonObject{put("bytes",array(r.bytes()))}}
  val value=cryptoRandomMemo<JsonObject>{when(text("op","sign")){
   "root"->{CryptoRsaPkcs15.signDeferred(lazyOf(null),hash,key,message);buildJsonObject{put("root",true)}}
   "make"->view(CryptoRsaPkcs15.makeSignature(hash,size,message))
   "encode"->buildJsonObject{put("bytes",array(dictionary.digestASN1(cryptoRandomMemo{CryptoMaskGen.hash(raw,message.value)}).value))}
   else->view(CryptoRsaPkcs15.signDeferred(lazyOf(null),hash,key,message))
  }}
  val results=List(2){try{value.value}catch(t:Throwable){buildJsonObject{put("exception",t.message?.substringBefore('\n')?:t.javaClass.simpleName)}}}
  return buildJsonObject{put("results",JsonArray(results));put("events",JsonArray(events.map(::JsonPrimitive)))}
 }
 @JvmStatic fun main(args:Array<String>){var n=0;for(v in Json.parseToJsonElement(File(args[0]).readText()).jsonArray){val r=v.jsonObject;val actual=observe(r.getValue("request").jsonObject);check(actual==r["expected"]){"row$n request=${r["request"]} expected=${r["expected"]} actual=$actual"};n++};println("PASS $n original PKCS15 dictionary rows")}
}
