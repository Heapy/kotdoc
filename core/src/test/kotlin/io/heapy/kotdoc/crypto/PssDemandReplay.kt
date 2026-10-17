package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.x509.*
import kotlinx.serialization.json.*
import java.math.BigInteger
import java.io.File
object PssDemandReplay {
 fun observe(o:JsonObject):JsonArray{
  val faults=o.getValue("faults").jsonArray.map{it.jsonPrimitive.content};fun fault(k:String){if(k in faults)error(k)}
  fun bytes(k:String)=o[k]?.jsonArray?.map{it.jsonPrimitive.int.toByte()}?.toByteArray()?:ByteArray(0)
  val hash=CryptoHmacAlgorithms.algorithm(CryptoHmacAlgorithms.Algorithm.SHA1);val dict=CryptoRsaPss.Dictionary(hash)
  val params=cryptoRandomMemo{fault("params");CryptoRsaPss.Params(dict,cryptoRandomMemo{fault("mask");CryptoRsaPss.MaskAlgorithm{seed,length->CryptoMaskGen.mgf1Deferred(hash,seed,cryptoRandomMemo{Math.toIntExact(length.value)})}},cryptoRandomMemo{fault("saltLength");20L},cryptoRandomMemo{fault("trailer");188.toByte()},cryptoRandomMemo{fault("hash");Unit})}
  val pub=X509RsaPublicKey.deferred({fault("size");128L},{fault("n");BigInteger.ONE.shiftLeft(1024)-BigInteger.ONE},{fault("e");BigInteger.ONE});val key=cryptoRandomMemo{fault("key");X509RsaPrivateKey(pub,X509SecretIntegers(listOf(cryptoRandomMemo{fault("d");BigInteger.ONE})+List(5){lazyOf(BigInteger.ZERO)}))}
  val salt=cryptoRandomMemo{fault("salt");bytes("salt")};val message=cryptoRandomMemo{fault("message");bytes("message")};val signature=cryptoRandomMemo{fault("signature");bytes("signature")};val digest=cryptoRandomMemo{fault("digest");CryptoMaskGen.hash(hash,ByteArray(0))};val blinder=cryptoRandomMemo<CryptoRsa.Blinder?>{fault("blinder");null}
  val value=cryptoRandomMemo{val op=o.getValue("op").jsonPrimitive.content;val r=when(op){
   "signRoot","signBytes"->CryptoRsaPss.signWithSaltDeferred(dict,salt,blinder,params,key,message)
   "digestRoot","digestBytes"->CryptoRsaPss.signDigestWithSaltDeferred(dict,salt,blinder,params,key,digest)
   "verify"->{CryptoRsaPss.verifyDeferred(dict,params,cryptoRandomMemo{key.value.publicKey},message,signature);return@cryptoRandomMemo "42"}
   "verifyDigest"->{CryptoRsaPss.verifyDigestDeferred(dict,params,cryptoRandomMemo{key.value.publicKey},digest,signature);return@cryptoRandomMemo "42"}
   else->error("unknown op")
  };if(op.endsWith("Bytes")&&r is CryptoRsaPss.Result.Success)r.bytes();"42"}
  return JsonArray(List(2){try{JsonPrimitive(value.value)}catch(t:Throwable){JsonPrimitive(t.message?.substringBefore('\n')?:t.javaClass.simpleName)}})
 }
 @JvmStatic fun main(args:Array<String>){var n=0;for(v in Json.parseToJsonElement(File(args[0]).readText()).jsonArray){val r=v.jsonObject;val got=observe(r.getValue("request").jsonObject);check(got==r["expected"]){"row$n request=${r["request"]} expected=${r["expected"]} actual=$got"};n++};println("PASS $n original PSS paired-demand rows")}
}
