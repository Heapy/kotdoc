package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
object Ripemd160AccessReplay {
 fun observe(r:JsonObject):JsonObject {
  val events=mutableListOf<String>();fun fault(label:String){if(r["accessFault"]==JsonPrimitive(label))error("$label-fault")}
  val data=r.getValue("seed").jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray()
  val access=object:CryptoHmac.ByteArrayAccess {
   override val size get()=data.size.also{fault("key-length")}
   override fun <A> withBytes(action:(ByteArray)->Lazy<A>):Lazy<A>{events+="key-enter";fault("key-before");val value=if(r["twiceKey"]==JsonPrimitive(true)){action(data);events+="key-again";action(data)}else action(data);events+="key-exit";fault("key-after");return if(r["bottomKeyResult"]==JsonPrimitive(true))lazy{error("key-result-bottom")}else value}
  }
  val a=CryptoRipemd160Hash.Algorithm.RIPEMD160;val action=lazy{runCatching{when(r.getValue("operation").jsonPrimitive.content){"hashUpdateRoot"->CryptoRipemd160Hash.hashUpdateAccess(CryptoRipemd160Hash.hashInit(a),access);"hashUpdatesRoot"->CryptoRipemd160Hash.hashUpdatesAccess(CryptoRipemd160Hash.hashInit(a),listOf(access,access));"mutableUpdate"->CryptoRipemd160Hash.hashMutableUpdateAccess(CryptoRipemd160Hash.hashMutableInit(a),access);"digestMaybeRoot"->CryptoRipemd160Hash.digestFromAccess(a,access);"digestRoot","digestBytes"->CryptoRipemd160Hash.digestFromAccess(a,access)?.force()};42}}
  val results=List(2){try{action.value.getOrThrow().toString()}catch(t:Throwable){t.message?:t.javaClass.simpleName}}
  return buildJsonObject{put("results",JsonArray(results.map(::JsonPrimitive)));put("events",JsonArray(events.map(::JsonPrimitive)))}
 }
}
