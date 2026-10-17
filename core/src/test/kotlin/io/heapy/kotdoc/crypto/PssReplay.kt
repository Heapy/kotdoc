package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.x509.*
import kotlinx.serialization.json.*
import java.math.BigInteger
import java.io.File
object PssReplay {
 fun algorithm(name:String)=try{CryptoRsaPkcs15.algorithm(CryptoRsaPkcs15.Algorithm.valueOf(name)).hash}catch(e:IllegalArgumentException){CryptoHmacAlgorithms.algorithm(CryptoHmacAlgorithms.Algorithm.valueOf(name))}
 fun observe(o:JsonObject):JsonObject {
  fun text(k:String,d:String="")=o[k]?.jsonPrimitive?.content?:d
  fun number(k:String,d:Int)=text(k,"$d").toInt()
  fun integer(k:String,d:BigInteger)=text(k,d.toString()).toBigInteger()
  fun bytes(k:String)=o[k]?.jsonArray?.map{it.jsonPrimitive.int.toByte()}?.toByteArray()?:ByteArray(0)
  fun array(b:ByteArray)=JsonArray(b.map{JsonPrimitive(it.toInt()and 255)})
  val k=number("size",128);val pub=X509RsaPublicKey(k.toLong(),integer("n",BigInteger.ONE.shiftLeft(8*maxOf(0,k))-BigInteger.ONE),integer("e",BigInteger.ONE));val key=X509RsaPrivateKey(pub,integer("d",BigInteger.ONE),integer("p",BigInteger.ZERO),integer("q",BigInteger.ZERO),integer("dP",BigInteger.ZERO),integer("dQ",BigInteger.ZERO),integer("qinv",BigInteger.ZERO))
  val hash=algorithm(text("algorithm","SHA1"));val params=CryptoRsaPss.Params(hash,lazyOf(CryptoRsaPss.MaskAlgorithm{seed,length->CryptoMaskGen.mgf1Deferred(hash,seed,cryptoRandomMemo{Math.toIntExact(length.value)})}),cryptoRandomMemo{number("saltLength",hash.digestSize).toLong()},lazyOf(number("trailer",188).toByte()));val digest=cryptoRandomMemo{bytes("digest").also{if(it.size!=hash.digestSize)error("bad-digest-length")}}
  val blinder=if(text("blinder","none")=="none")null else CryptoRsa.Blinder(integer("r",BigInteger.ONE),integer("rinv",BigInteger.ONE));val rsa=CryptoRsa(CryptoNumberPrime(CryptoProbabilistic{error("unused entropy")}))
  fun view(r:CryptoRsaPss.Result)=when(r){is CryptoRsaPss.Result.Failure->buildJsonObject{put("error",r.error.name)};is CryptoRsaPss.Result.Success->buildJsonObject{put("bytes",array(r.bytes()))}}
  fun random(action:CryptoPseudoRandom<CryptoChaChaDrg,CryptoRsaPss.Result>):JsonObject{val seed=number("seed",0);val g=CryptoChaChaDrg.initialize(ByteArray(40){((seed+it)%256).toByte()});val pair=CryptoPseudoRandom.withDRG(g,action);return buildJsonObject{put("value",view(pair.first.value));put("next",array(pair.second.value.randomBytesGenerate(65).first))}}
  return try{when(text("op","signWithSalt")){
   "signWithSalt"->view(CryptoRsaPss.signWithSalt(bytes("salt"),blinder,params,key,bytes("message")))
   "signDigestWithSalt"->view(CryptoRsaPss.signDigestWithSaltDeferred(params.dictionary,lazyOf(bytes("salt")),lazyOf(blinder),lazyOf(params),lazyOf(key),digest))
   "sign"->random(CryptoRsaPss.signAction(params.dictionary,lazyOf(blinder),lazyOf(params),lazyOf(key),lazyOf(bytes("message")),CryptoChaChaDrg.operations))
   "signDigest"->random(CryptoRsaPss.signDigestAction(params.dictionary,lazyOf(blinder),lazyOf(params),lazyOf(key),digest,CryptoChaChaDrg.operations))
   "signSafer"->random(CryptoRsaPss.signSaferAction(params.dictionary,rsa,lazyOf(params),lazyOf(key),lazyOf(bytes("message")),CryptoChaChaDrg.operations))
   "signDigestSafer"->random(CryptoRsaPss.signDigestSaferAction(params.dictionary,rsa,lazyOf(params),lazyOf(key),digest,CryptoChaChaDrg.operations))
   "verify"->buildJsonObject{put("valid",CryptoRsaPss.verify(params,pub,bytes("message"),bytes("signature")))}
   "verifyDigest"->buildJsonObject{put("valid",CryptoRsaPss.verifyDigestDeferred(params.dictionary,lazyOf(params),lazyOf(pub),digest,lazyOf(bytes("signature"))))}
   else->error("unknown op")
  }}catch(t:Throwable){buildJsonObject{put("exception",t.message?.substringBefore('\n')?:t.javaClass.simpleName)}}
 }
 @JvmStatic fun main(args:Array<String>){var n=0;for(v in Json.parseToJsonElement(File(args[0]).readText()).jsonArray){val r=v.jsonObject;val got=observe(r.getValue("request").jsonObject);check(got==r["expected"]){"row$n request=${r["request"]} expected=${r["expected"]} actual=$got"};n++};println("PASS $n original PSS rows")}
}
