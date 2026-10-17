package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.x509.*
import kotlinx.serialization.json.*
import java.math.BigInteger
import java.io.File
import java.util.zip.GZIPInputStream

object RsaReplay {
 private fun JsonObject.text(name:String,default:String="")=get(name)?.jsonPrimitive?.content?:default
 private fun JsonObject.number(name:String,default:Int)=get(name)?.jsonPrimitive?.int?:default
 private fun arg(o:JsonObject,name:String,default:String)=cryptoRandomMemo{val s=o.text(name,default);if(s.startsWith("bottom:"))error(s.removePrefix("bottom:"))else s.toBigInteger()}
 private fun bytes(o:JsonObject,name:String):Lazy<ByteArray> =cryptoRandomMemo{val failure=o.text(name+"Bottom");if(failure.isNotEmpty())error(failure);o[name]?.jsonArray?.map{it.jsonPrimitive.int.toByte()}?.toByteArray()?:ByteArray(0)}
 private fun octets(b:ByteArray)=JsonArray(b.map{JsonPrimitive(it.toInt()and 255)})
 private fun public(key:X509RsaPublicKey)=buildJsonObject{put("size",key.size.toString());put("n",key.modulus.toString());put("e",key.exponent.toString())}
 private fun private(key:X509RsaPrivateKey)=buildJsonObject{put("public",public(key.publicKey));put("d",key.d.toString());put("p",key.p.toString());put("q",key.q.toString());put("dP",key.dP.toString());put("dQ",key.dQ.toString());put("qinv",key.qinv.toString())}
 private fun view(o:JsonObject,pair:Pair<X509RsaPublicKey,X509RsaPrivateKey>):JsonElement=when(o.text("part","all")){
  "public"->public(pair.first);"show"->JsonPrimitive(pair.second.toString());"d"->JsonPrimitive(pair.second.d.toString());"qinv"->JsonPrimitive(pair.second.qinv.toString());"constructor"->JsonPrimitive("pair")
  else->buildJsonObject{put("public",public(pair.first));put("private",private(pair.second))}
 }
 private fun blinder(b:CryptoRsa.Blinder)=buildJsonObject{put("r",b.r.toString());put("inverse",b.inverse.toString());put("show",b.toString())}
 class Traced(val drg:CryptoChaChaDrg,val count:Int,val budget:Int,val trace:MutableList<Pair<Int,ByteArray>>):CryptoDrg<Traced>{
  override fun randomBytesGenerate(length:Int):Pair<ByteArray,Traced>{
   if(count>=budget){trace+=length to ByteArray(0);error("rng budget")}
   val result=drg.randomBytesGenerate(length);trace+=length to result.first.copyOf();return result.first to Traced(result.second,count+1,budget,trace)
  }
 }
 fun observe(o:JsonObject):JsonElement {
  val outer=mutableListOf<Pair<Int,ByteArray>>();val inner=mutableListOf<Pair<Int,ByteArray>>()
  val rsa=CryptoRsa(CryptoNumberPrime(CryptoProbabilistic{ByteArray(32){it.toByte()}}){n,b->inner+=n to b})
  val pub=cryptoRandomMemo{X509RsaPublicKey.deferred({arg(o,"size","2").value.longValueExact()},{arg(o,"n","3233").value},{arg(o,"e","17").value})}
  val key=cryptoRandomMemo{X509RsaPrivateKey(pub.value,X509SecretIntegers(listOf(arg(o,"d","2753"),arg(o,"p","61"),arg(o,"q","53"),arg(o,"dP","53"),arg(o,"dQ","49"),arg(o,"qinv","38"))))}
  val b=cryptoRandomMemo{when(o.text("blinder","none")){"bottom"->error("blinder");"none"->null;else->CryptoRsa.Blinder.deferred{arg(o,"r","2").value to arg(o,"rinv","1617").value}}}
  val generator=cryptoRandomMemo{
   val words=o["words"]?.jsonArray
   val d=if(words!=null)CryptoChaChaDrg.initializeWords(words[0].jsonPrimitive.content.toULong(),words[1].jsonPrimitive.content.toULong(),words[2].jsonPrimitive.content.toULong(),words[3].jsonPrimitive.content.toULong(),words[4].jsonPrimitive.content.toULong())else CryptoChaChaDrg.initialize(ByteArray(40){(o.number("seed",0)+it).toByte()})
   Traced(d,0,o.number("budget",10000),outer)
  }
  val ops=CryptoDrgOperations<Traced>{n,g->val root=cryptoRandomMemo{g.value.randomBytesGenerate(n)};cryptoRandomMemo{root.value.first}to cryptoRandomMemo{root.value.second}}
  val value:JsonElement=try {when(o.text("op")){
   "with"->when(val k=rsa.generateWithDeferred(arg(o,"p","61"),arg(o,"q","53"),cryptoRandomMemo{arg(o,"size","2").value.longValueExact()},arg(o,"e","17"))){CryptoMaybe.None->JsonPrimitive("Nothing");is CryptoMaybe.Some->if(o.text("part")=="constructor")JsonPrimitive("Just")else view(o,k.value)}
   "ep"->octets(CryptoRsa.epDeferred(pub,bytes(o,"input")))
   "dp"->octets(CryptoRsa.dpDeferred(b,key,bytes(o,"input")))
   "slow"->octets(CryptoRsa.dpSlow(key,bytes(o,"input")))
   "fastNo"->octets(CryptoRsa.dpFastNoBlinder(key,bytes(o,"input")))
   "fast"->octets(CryptoRsa.dpFast(b.value?:error("blinder required"),key,bytes(o,"input")))
   "multiply"->JsonPrimitive(CryptoRsa.multiplicationDeferred(arg(o,"a","0"),arg(o,"b","0"),arg(o,"m","1")).toString())
   "generate","boundedGenerate"->{
    if(o.text("op")=="boundedGenerate"){var step=0;val limit=o.number("limit",8);rsa.generateAttemptObserver={if(step==limit)error("bounded RSA loop $step");step++}}
    val pair=CryptoPseudoRandom.withDRG(generator,rsa.generateAction(cryptoRandomMemo{arg(o,"size","2").value.intValueExact()},arg(o,"e","17"),ops))
    val next=pair.second.value.drg.randomBytesGenerate(65).first
    val output=if(o.text("part")=="constructor")JsonPrimitive("pair")else view(o,pair.first.value)
    buildJsonObject{put("value",output);put("next",octets(next))}
   }
   "blinder"->{val pair=CryptoPseudoRandom.withDRG(generator,rsa.generateBlinderAction(arg(o,"n","3233"),ops));val next=pair.second.value.drg.randomBytesGenerate(65).first;val output=if(o.text("part")=="state")JsonPrimitive("ignored")else blinder(pair.first.value);buildJsonObject{put("value",output);put("next",octets(next))}}
   else->error("request")
  }}catch(e:Exception){buildJsonObject{put("error",e.message?:e.javaClass.simpleName)}}
  fun trace(rows:List<Pair<Int,ByteArray>>)=JsonArray(rows.map{(n,b)->JsonArray(listOf(JsonPrimitive(n),octets(b)))})
  return buildJsonObject{put("value",value);put("outer",trace(outer));put("inner",trace(inner))}
 }
 @JvmStatic fun main(args:Array<String>){var count=0;GZIPInputStream(File(args[0]).inputStream()).bufferedReader().useLines{lines->lines.forEach{line->val row=Json.parseToJsonElement(line).jsonObject;val got=observe(row.getValue("request").jsonObject);check(got==row.getValue("expected")){"case$count ${row["request"]}\nexpected ${row["expected"]}\ngot $got"};count++}};println("PASS $count")}
}
