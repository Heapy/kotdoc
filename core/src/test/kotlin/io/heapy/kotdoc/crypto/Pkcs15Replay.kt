package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.x509.*
import java.math.BigInteger
import java.io.File
import kotlinx.serialization.json.*
object Pkcs15Replay {
 fun observe(o:JsonObject):JsonObject {
  fun text(k:String,d:String="")=o[k]?.jsonPrimitive?.content?:d
  fun number(k:String,d:Int)=text(k,"$d").toInt()
  fun bytes(k:String)=o[k]?.jsonArray?.map{it.jsonPrimitive.int.toByte()}?.toByteArray()?:ByteArray(0)
  fun integer(k:String,d:BigInteger)=text(k,d.toString()).toBigInteger()
  fun array(b:ByteArray)=JsonArray(b.map{JsonPrimitive(it.toInt()and 255)})
  val length=number("size",128);val pub=X509RsaPublicKey(length.toLong(),integer("n",BigInteger.ONE.shiftLeft(8*maxOf(0,length))-BigInteger.ONE),integer("e",BigInteger.ONE))
  val key=X509RsaPrivateKey(pub,integer("d",BigInteger.ONE),integer("p",BigInteger.ZERO),integer("q",BigInteger.ZERO),integer("dP",BigInteger.ZERO),integer("dQ",BigInteger.ZERO),integer("qinv",BigInteger.ZERO))
  val algorithm=if(text("hashMode","hash")=="none")null else CryptoRsaPkcs15.algorithm(CryptoRsaPkcs15.Algorithm.valueOf(text("algorithm","SHA1")))
  val blinder=if(text("blinder","none")=="none")null else CryptoRsa.Blinder(integer("r",BigInteger.ONE),integer("rinv",BigInteger.ONE))
  val rsa=CryptoRsa(CryptoNumberPrime(CryptoProbabilistic{error("unused entropy")}))
  fun view(r:CryptoRsaPkcs15.Result)=when(r){is CryptoRsaPkcs15.Result.Failure->buildJsonObject{put("error",r.error.name)};is CryptoRsaPkcs15.Result.Success->buildJsonObject{put("bytes",array(r.bytes()))}}
  fun random(action:CryptoPseudoRandom<CryptoChaChaDrg,CryptoRsaPkcs15.Result>):JsonObject{
   val seed=number("seed",0);val generator=CryptoChaChaDrg.initialize(ByteArray(40){((seed+it)%256).toByte()});val pair=CryptoPseudoRandom.withDRG(generator,action)
   return buildJsonObject{put("value",view(pair.first.value));put("next",array(pair.second.value.randomBytesGenerate(65).first))}
  }
  return try{when(text("op","sign")){
   "padSignature"->view(CryptoRsaPkcs15.padSignature(length.toLong(),bytes("message")))
   "unpad"->view(CryptoRsaPkcs15.unpad(bytes("input")))
   "pad"->random(CryptoRsaPkcs15.padAction(lazyOf(length.toLong()),lazyOf(bytes("message")),CryptoChaChaDrg.operations))
   "encrypt"->random(CryptoRsaPkcs15.encryptAction(lazyOf(pub),lazyOf(bytes("message")),CryptoChaChaDrg.operations))
   "decrypt"->view(CryptoRsaPkcs15.decrypt(blinder,key,bytes("cipher")))
   "decryptSafer"->random(CryptoRsaPkcs15.decryptSaferAction(rsa,lazyOf(key),lazyOf(bytes("cipher")),CryptoChaChaDrg.operations))
   "sign"->view(CryptoRsaPkcs15.sign(blinder,algorithm,key,bytes("message")))
   "signSafer"->random(CryptoRsaPkcs15.signSaferAction(rsa,lazyOf(algorithm),lazyOf(key),lazyOf(bytes("message")),CryptoChaChaDrg.operations))
   "verify"->buildJsonObject{put("valid",CryptoRsaPkcs15.verify(algorithm,pub,bytes("message"),bytes("signature")))}
   else->error("unknown op")
  }}catch(t:Throwable){buildJsonObject{put("exception",t.message?.substringBefore('\n')?:t.javaClass.simpleName)}}
 }
 @JvmStatic fun main(args:Array<String>){var count=0;for(v in Json.parseToJsonElement(File(args[0]).readText()).jsonArray){val row=v.jsonObject;val actual=observe(row.getValue("request").jsonObject);check(actual==row["expected"]){"row$count request=${row["request"]} expected=${row["expected"]} actual=$actual"};count++};println("PASS $count original PKCS15 rows")}
}
