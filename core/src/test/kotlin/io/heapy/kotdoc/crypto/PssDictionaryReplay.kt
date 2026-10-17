package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.x509.*
import kotlinx.serialization.json.*
import java.math.BigInteger
import java.io.File
object PssDictionaryReplay {
 fun observe(o:JsonObject):JsonObject {
  fun text(k:String,d:String="")=o[k]?.jsonPrimitive?.content?:d
  fun bottom(k:String){if(text("bottom")==k)error(k)}
  fun fault(k:String){if(text("fault")==k)error(k)}
  fun bytes(k:String,default:ByteArray)=o[k]?.jsonArray?.map{it.jsonPrimitive.int.toByte()}?.toByteArray()?:default
  val events=mutableListOf<String>();fun returned(s:String)=cryptoRandomMemo{if(text("returnedBottom")==s)error("$s-result");Unit}
  val raw=object:CryptoHmac.HashAlgorithm {
   override val blockSize get()=8.also{fault("blockSize")}
   override val digestSize get():Int{if(text("strictWitness")=="yes")error("Prelude.undefined");fault("digestSize");return 4}
   override val contextSize get()=8.also{fault("contextSize")}
   override fun initialize(context:ByteArray){events+="init";fault("init");context.fill(0)}
   override fun update(context:ByteArray,bytes:ByteArray){events+="update:${bytes.size}";fault("update");for(b in bytes){context[0]=(context[0]+b).toByte();context[1]=(context[1]+1).toByte()}}
   override fun finalize(context:ByteArray,output:ByteArray){events+="finalize";fault("finalize");context.copyInto(output,0,0,4)}
   override fun initializeResult(context:ByteArray):Lazy<Unit>{initialize(context);return returned("init")}
   override fun updateResult(context:ByteArray,bytes:ByteArray):Lazy<Unit>{update(context,bytes);return returned("update")}
   override fun finalizeResult(context:ByteArray,output:ByteArray):Lazy<Unit>{finalize(context,output);return returned("finalize")}
  }
  val dict=CryptoRsaPss.Dictionary(raw){w->if(text("strictWitness")=="yes")w.value;fault("digestSize");4L}
  val mask=CryptoRsaPss.MaskAlgorithm{seed,length->cryptoRandomMemo{
   events+="mask";fault("mask");val mode=text("maskMode","normal")
   if(mode=="ignore")byteArrayOf(7,8)else{val n=length.value.toInt();events+="maskLength:$n";fault("maskLength");if(mode!="ignoreSeed")events+="seed:${seed.value.map{it.toInt()and 255}.joinToString(",","[","]")}"
    when(mode){"empty"->ByteArray(0);"short"->ByteArray(maxOf(0,n-1)){7};"long"->ByteArray(maxOf(0,n+1)){7};"valid"->ByteArray(maxOf(0,n-3))+byteArrayOf(1,9,8);else->ByteArray(maxOf(0,n)){7}}
   }
  }}
  val witness=cryptoRandomMemo{bottom("witness");Unit};val params=cryptoRandomMemo{bottom("params");CryptoRsaPss.Params(dict,cryptoRandomMemo{bottom("maskField");mask},cryptoRandomMemo{bottom("saltLength");2L},cryptoRandomMemo{bottom("trailer");188.toByte()},witness)}
  val size=cryptoRandomMemo{bottom("size");text("size","32").toLong()};val key=cryptoRandomMemo{bottom("key");X509RsaPrivateKey(X509RsaPublicKey.deferred({size.value},{BigInteger.ONE.shiftLeft(8*maxOf(0,size.value.toInt()))-BigInteger.ONE},{BigInteger.ONE}),BigInteger.ONE,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO)}
  val message=cryptoRandomMemo{bottom("message");bytes("message",byteArrayOf(1,2,3))};val salt=cryptoRandomMemo{bottom("salt");byteArrayOf(9,8)};val digest=cryptoRandomMemo{bottom("digest");CryptoMaskGen.hash(raw,ByteArray(0))};val signature=lazyOf(bytes("signature",ByteArray(0)))
  fun array(b:ByteArray)=JsonArray(b.map{JsonPrimitive(it.toInt()and 255)})
  fun view(r:CryptoRsaPss.Result)=when(r){is CryptoRsaPss.Result.Failure->buildJsonObject{put("error",r.error.name)};is CryptoRsaPss.Result.Success->buildJsonObject{put("bytes",array(r.bytes()))}}
  val value=cryptoRandomMemo<JsonObject>{when(text("op","sign")){
   "root"->{CryptoRsaPss.signWithSaltDeferred(dict,salt,lazyOf(null),params,key,message);buildJsonObject{put("root",true)}}
   "signDigest"->view(CryptoRsaPss.signDigestWithSaltDeferred(dict,salt,lazyOf(null),params,key,digest))
   "verify"->buildJsonObject{put("valid",CryptoRsaPss.verifyDeferred(dict,params,cryptoRandomMemo{key.value.publicKey},message,signature))}
   "defaultRoot"->{CryptoRsaPss.defaultParams(dict,witness);buildJsonObject{put("root",true)}}
   "defaultSalt"->buildJsonObject{put("saltLength",CryptoRsaPss.defaultParams(dict,witness).saltLength.value)}
   else->view(CryptoRsaPss.signWithSaltDeferred(dict,salt,lazyOf(null),params,key,message))
  }}
  val results=List(2){try{value.value}catch(t:Throwable){buildJsonObject{put("exception",t.message?.substringBefore('\n')?:t.javaClass.simpleName)}}}
  return buildJsonObject{put("results",JsonArray(results));put("events",JsonArray(events.map(::JsonPrimitive)))}
 }
 @JvmStatic fun main(args:Array<String>){var n=0;for(v in Json.parseToJsonElement(File(args[0]).readText()).jsonArray){val r=v.jsonObject;val got=observe(r.getValue("request").jsonObject);check(got==r["expected"]){"row$n request=${r["request"]} expected=${r["expected"]} actual=$got"};n++};println("PASS $n original PSS generic dictionary/MGF rows")}
}
