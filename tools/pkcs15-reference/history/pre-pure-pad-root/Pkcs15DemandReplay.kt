package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.x509.*
import kotlinx.serialization.json.*
import java.math.BigInteger
import java.io.File
object Pkcs15DemandReplay {
 fun observe(o:JsonObject):JsonArray{
  fun text(k:String,d:String="")=o[k]?.jsonPrimitive?.content?:d
  val faults=o["faults"]!!.jsonArray.map{it.jsonPrimitive.content}
  fun fault(k:String){if(k in faults)error(k)}
  fun bytes(k:String)=o[k]?.jsonArray?.map{it.jsonPrimitive.int.toByte()}?.toByteArray()?:ByteArray(0)
  val size=cryptoRandomMemo{fault("size");128L}
  val pub=X509RsaPublicKey.deferred({size.value},{fault("n");BigInteger.ONE.shiftLeft(1024)-BigInteger.ONE},{fault("e");BigInteger.ONE})
  val key=cryptoRandomMemo{fault("key");X509RsaPrivateKey(pub,X509SecretIntegers(listOf(cryptoRandomMemo{fault("d");BigInteger.ONE})+List(5){lazyOf(BigInteger.ZERO)}))}
  val hash=cryptoRandomMemo{
   fault("hash")
   if(text("hashMode","hash")=="none")null else {
    val a=CryptoRsaPkcs15.algorithm(CryptoRsaPkcs15.Algorithm.SHA1)
    CryptoRsaPkcs15.HashAlgorithmASN1(a.hash,a.digestASN1,cryptoRandomMemo{fault("witness");Unit})
   }
  }
  val message=cryptoRandomMemo{fault("message");bytes("message")};val input=cryptoRandomMemo{fault("input");bytes("input")}
  val blinder=cryptoRandomMemo<CryptoRsa.Blinder?>{fault("blinder");null}
  val evaluation=cryptoRandomMemo{
   val result=when(text("op")){
    "padSignatureRoot","padSignatureBytes"->CryptoRsaPkcs15.padSignatureDeferred(size,message)
    "signRoot","signBytes"->CryptoRsaPkcs15.signDeferred(blinder,hash,key,message)
    "decryptRoot","decryptBytes"->CryptoRsaPkcs15.decryptDeferred(blinder,key,input)
    "verify"->{CryptoRsaPkcs15.verifyDeferred(hash,cryptoRandomMemo{key.value.publicKey},message,input);return@cryptoRandomMemo "42"}
    else->error("unknown op")
   }
   if(text("op").endsWith("Bytes")&&result is CryptoRsaPkcs15.Result.Success)result.bytes()
   "42"
  }
  return JsonArray(List(2){try{JsonPrimitive(evaluation.value)}catch(t:Throwable){JsonPrimitive(t.message?.substringBefore('\n')?:t.javaClass.simpleName)}})
 }
 @JvmStatic fun main(args:Array<String>){var n=0;for(v in Json.parseToJsonElement(File(args[0]).readText()).jsonArray){val r=v.jsonObject;val actual=observe(r.getValue("request").jsonObject);check(actual==r["expected"]){"row$n request=${r["request"]} expected=${r["expected"]} actual=$actual"};n++};println("PASS $n original PKCS15 demand rows")}
}
