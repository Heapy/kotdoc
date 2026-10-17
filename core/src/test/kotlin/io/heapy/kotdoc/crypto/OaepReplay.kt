package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.x509.*
import java.math.BigInteger
import java.io.File
import kotlinx.serialization.json.*
object OaepReplay {
 fun observe(r:JsonObject):JsonObject {
  fun number(k:String,d:Int)=r[k]?.jsonPrimitive?.int?:d
  fun text(k:String,d:String)=r[k]?.jsonPrimitive?.content?:d
  fun bytes(k:String)=r[k]?.jsonArray?.map{it.jsonPrimitive.int.toByte()}?.toByteArray()?:ByteArray(0)
  fun integer(k:String,d:BigInteger)=BigInteger(text(k,d.toString()))
  fun array(b:ByteArray)=JsonArray(b.map{JsonPrimitive(it.toInt() and 255)})
  val events=mutableListOf<JsonElement>()
  val result=try {
   val hash=CryptoHmacAlgorithms.algorithm(CryptoHmacAlgorithms.Algorithm.valueOf(text("algorithm","SHA1")))
   val mask=CryptoMaskGen.Algorithm{seed,n->events+=buildJsonObject{put("length",n);put("seed",array(seed))};when(text("mask","default")){"empty"->ByteArray(0);"short"->ByteArray(maxOf(0,n-1)){7};"long"->ByteArray(maxOf(0,n+1)){7};"zero"->ByteArray(maxOf(0,n));"error"->error("mask error");else->CryptoMaskGen.mgf1(hash,seed,n)}}
   val params=CryptoRsaOaep.Params(hash,mask,lazy{if(text("labelMode","none")=="none")null else bytes("label")})
   val op=text("op","encrypt");val k=number("size",128)
   val n=if(op=="decryptEncoded")BigInteger.ONE.shiftLeft(8*maxOf(0,k))-BigInteger.ONE else integer("n",defaultN)
   val key=X509RsaPublicKey(k.toLong(),n,if(op=="decryptEncoded")BigInteger.ONE else integer("e",BigInteger.valueOf(65537)))
   val privateKey=X509RsaPrivateKey(key,if(op=="decryptEncoded")BigInteger.ONE else integer("d",defaultD),integer("p",BigInteger.ZERO),integer("q",BigInteger.ZERO),integer("dP",BigInteger.ZERO),integer("dQ",BigInteger.ZERO),integer("qinv",BigInteger.ZERO))
   if(op=="mgf")buildJsonObject{put("bytes",array(CryptoMaskGen.mgf1(hash,bytes("seed"),number("length",0))))}else {
    val value=if(op=="encrypt")CryptoRsaOaep.encryptWithSeed(bytes("seed"),params,key,bytes("message"))else CryptoRsaOaep.decrypt(null,params,privateKey,bytes("cipher"))
    when(value){is CryptoRsaOaep.Result.Failure->buildJsonObject{put("error",value.error.name)};is CryptoRsaOaep.Result.Success->buildJsonObject{put("bytes",array(value.bytes()))}}
   }
  }catch(t:Throwable){buildJsonObject{put("exception",t.message?.substringBefore('\n')?:t.javaClass.simpleName)}}
  return buildJsonObject{put("result",result);put("maskCalls",JsonArray(events))}
 }
 private val defaultN=BigInteger("a8b3b284af8eb50b387034a860f146c4919f318763cd6c5598c8ae4811a1e0abc4c7e0b082d693a5e7fced675cf4668512772c0cbc64a742c6c630f533c8cc72f62ae833c40bf25842e984bb78bdbf97c0107d55bdb662f5c4e0fab9845cb5148ef7392dd3aaff93ae1e6b667bb3d4247616d4f5ba10d4cfd226de88d39f16fb",16)
 private val defaultD=BigInteger("53339cfdb79fc8466a655c7316aca85c55fd8f6dd898fdaf119517ef4f52e8fd8e258df93fee180fa0e4ab29693cd83b152a553d4ac4d1812b8b9fa5af0e7f55fe7304df41570926f3311f15c4d65a732c483116ee3d3d2d0af3549ad9bf7cbfb78ad884f84d5beb04724dc7369b31def37d0cf539e9cfcdd3de653729ead5d1",16)
 @JvmStatic fun main(args:Array<String>){var count=0;for(v in Json.parseToJsonElement(File(args[0]).readText()).jsonArray){val r=v.jsonObject;val actual=observe(r.getValue("request").jsonObject);check(actual==r["expected"]){"row$count expected=${r["expected"]} actual=$actual request=${r["request"]}"};count++};println("PASS $count original OAEP/MGF rows")}
}
