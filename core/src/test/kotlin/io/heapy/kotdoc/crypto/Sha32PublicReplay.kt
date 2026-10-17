package io.heapy.kotdoc.crypto
import java.io.File
import kotlinx.serialization.json.*
internal object Sha32PublicReplay {
 private fun bytes(v:JsonElement)=v.jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray()
 private fun json(v:ByteArray)=JsonArray(v.map{JsonPrimitive(it.toInt() and 255)})
 fun observe(r:JsonObject):JsonObject {
 fun number(o:JsonObject,k:String,default:Int=0)=o[k]?.jsonPrimitive?.long?.toInt()?:default
        if(r["mutable"]?.jsonPrimitive?.boolean==true){
            val alg=CryptoSha32Hash.Algorithm.valueOf(r.getValue("algorithm").jsonPrimitive.content)
            val ctx=CryptoSha32Hash.hashMutableInitWithDeferred(alg,lazy{error("algorithm-bottom")})
            for(v in r.getValue("chunks").jsonArray)CryptoSha32Hash.hashMutableUpdate(ctx,bytes(v))
            val a=CryptoSha32Hash.hashMutableFinalize(ctx);val b=CryptoSha32Hash.hashMutableFinalize(ctx);CryptoSha32Hash.hashMutableReset(ctx);val c=CryptoSha32Hash.hashMutableFinalize(ctx)
            return buildJsonObject{put("first",json(a.bytes()));put("second",json(b.bytes()));put("reset",json(c.bytes()))}
        }
        if(r["digest"]?.jsonPrimitive?.boolean==true) {
            val alg=CryptoSha32Hash.Algorithm.valueOf(r.getValue("algorithm").jsonPrimitive.content)
            var ctx=if(r["witnessBottom"]?.jsonPrimitive?.boolean==true)CryptoSha32Hash.hashInitWithDeferred(alg,lazy{error("algorithm-bottom")})else CryptoSha32Hash.hashInit(alg);for(chunk in r.getValue("chunks").jsonArray)ctx=CryptoSha32Hash.hashUpdate(ctx,bytes(chunk))
            val digest=if("prefix" in r)CryptoSha32Hash.hashFinalizePrefix(ctx,bytes(r.getValue("end")),number(r,"prefix"))else CryptoSha32Hash.hashFinalize(ctx)
            return buildJsonObject {
                put("digest",json(digest.bytes()))
                if(r["public"]?.jsonPrimitive?.boolean==true) {
                    put("witnessDigest",json(CryptoSha32Hash.hashWithDeferred(alg,lazy{error("algorithm-bottom")},r.getValue("chunks").jsonArray.fold(byteArrayOf()){a,v->a+bytes(v)}).bytes()));put("show",digest.toString());put("blockSize",alg.blockSize);put("digestSize",alg.digestSize);put("contextSize",alg.contextSize);put("context",json(ctx.debugBytes().copyOf(if(alg==CryptoSha32Hash.Algorithm.SHA1)96 else 168)))
                    val parsed=CryptoSha32Hash.readsDigest(alg,r.getValue("read").jsonPrimitive.content)
                    put("reads",JsonArray(if(parsed==null)emptyList()else listOf(buildJsonObject{put("bytes",json(parsed.first.bytes()));put("rest",parsed.second)})))
                    put("fromLengths",JsonArray(listOf(0,alg.digestSize-1,alg.digestSize,alg.digestSize+1).map{JsonPrimitive(CryptoSha32Hash.digestFromByteString(alg,ByteArray(it))!=null)}));val other=parsed?.first?:digest;put("equal",digest==other);put("compare",when{digest<other->"LT";digest>other->"GT";else->"EQ"})
                }
            }
        }
error("request")
 }
 @JvmStatic fun main(args:Array<String>){val rows=Json.parseToJsonElement(File(args[0]).readText()).jsonArray;for((i,v)in rows.withIndex()){val row=v.jsonObject;val actual=observe(row.getValue("request").jsonObject);check(actual==row.getValue("expected")){"row$i expected=${row["expected"]} actual=$actual"}};println("PASS ${rows.size} public observations")}
}
