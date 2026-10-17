package io.heapy.kotdoc.crypto
import java.io.File
import kotlinx.serialization.json.*
object HmacReplay {
 private fun bytes(v:JsonElement)=v.jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray()
 private fun json(a:ByteArray)=JsonArray(a.map{JsonPrimitive(it.toInt() and 255)})
 fun observe(r:JsonObject):JsonObject{
  if(r["equalOnly"]?.jsonPrimitive?.boolean==true)return buildJsonObject{put("equal",CryptoHmac.Mac(bytes(r.getValue("key")))==CryptoHmac.Mac(bytes(r.getValue("other"))))}
  val name=r.getValue("algorithm").jsonPrimitive.content
  val alg=if(name.startsWith("SHAKE"))CryptoHmacAlgorithms.shake(name.substringAfter("SHAKE").substringBefore('_').toInt(),name.substringAfter('_').toInt())else CryptoHmacAlgorithms.algorithm(CryptoHmacAlgorithms.Algorithm.valueOf(name))
  val key=bytes(r.getValue("key"));val chunks=r.getValue("chunks").jsonArray.map(::bytes);val all=chunks.fold(byteArrayOf()){a,b->a+b}
  val one=CryptoHmac.hmac(alg,key,all);val states=mutableListOf<CryptoHmac.Mac>();var ctx=CryptoHmac.initialize(alg,key);states+=CryptoHmac.finalize(ctx)
  for(c in chunks){ctx=CryptoHmac.update(ctx,c);states+=CryptoHmac.finalize(ctx)}
  val other=CryptoHmac.hmac(alg,key,r["other"]?.let(::bytes)?:byteArrayOf())
  return buildJsonObject{put("mac",json(one.bytes()));put("incremental",JsonArray(states.map{json(it.bytes())}));put("lazy",json(CryptoHmac.hmacLazy(alg,key,chunks).bytes()));put("digest",json(one.hmacGetDigest().bytes()));put("equalSelf",one==one);put("equalOther",one==other)
   if(r["publicDigest"]?.jsonPrimitive?.boolean==true){put("contextLengths",JsonArray(listOf(JsonPrimitive(ctx.outer.bytes().size),JsonPrimitive(ctx.inner.bytes().size))));put("digestShow",one.hmacGetDigest().toString());put("digestCompare",when{one.hmacGetDigest()<other.hmacGetDigest()->"LT";one.hmacGetDigest()>other.hmacGetDigest()->"GT";else->"EQ"});val parsed=CryptoHmac.readsDigest(alg,r.getValue("read").jsonPrimitive.content);put("reads",JsonArray(if(parsed==null)emptyList()else listOf(buildJsonObject{put("bytes",json(parsed.first.bytes()));put("rest",parsed.second)})));put("fromLengths",JsonArray(listOf(0,maxOf(0,alg.digestSize-1),alg.digestSize,alg.digestSize+1).map{JsonPrimitive(CryptoHmac.digestFromByteString(alg,ByteArray(it))!=null)}))}
  }
 }
 @JvmStatic fun main(args:Array<String>){val rows=Json.parseToJsonElement(File(args[0]).readText()).jsonArray;for((i,v)in rows.withIndex()){val row=v.jsonObject;val actual=observe(row.getValue("request").jsonObject);check(actual==row.getValue("expected")){"row$i request=${row["request"]} expected=${row["expected"]} actual=$actual"}};println("PASS ${rows.size} actual HMAC observations")}
}
