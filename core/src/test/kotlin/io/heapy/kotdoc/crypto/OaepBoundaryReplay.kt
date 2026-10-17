package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
object OaepBoundaryReplay {
 fun observe(r:JsonObject):JsonObject {
  val result=try{val p=CryptoMaskGen.plan(r.getValue("length").jsonPrimitive.int,r.getValue("digest").jsonPrimitive.int);val n=p.maximumCounter-1;buildJsonObject{put("chunks",p.chunks);put("remainder",p.remainder);put("maximum",p.maximumCounter);put("last",JsonArray(if(p.maximumCounter<=0)emptyList()else listOf(24,16,8,0).map{JsonPrimitive(((n ushr it)and 255).toInt())}))}}catch(t:Throwable){buildJsonObject{put("exception",t.message?:t.javaClass.simpleName)}}
  return buildJsonObject{put("result",result);put("maskCalls",JsonArray(emptyList()))}
 }
}
