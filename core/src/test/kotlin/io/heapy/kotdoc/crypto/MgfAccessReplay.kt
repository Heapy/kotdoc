package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.x509.*
import java.math.BigInteger
import java.io.File
import kotlinx.serialization.json.*
object MgfAccessReplay {
 fun observe(r:JsonObject):JsonObject {
  fun number(k:String)=r[k]?.jsonPrimitive?.int?:0
  fun text(k:String)=r[k]?.jsonPrimitive?.content?:""
  fun <T> demand(k:String,value:()->T):Lazy<T> = cryptoRandomMemo{if(r[k+"Bottom"]==JsonPrimitive(true))error("\"$k\"-bottom");value()}
  fun bytes(k:String)=demand(k){r[k]?.jsonArray?.map{it.jsonPrimitive.int.toByte()}?.toByteArray()?:ByteArray(0)}
  val events=mutableListOf<String>();var inits=0
  fun fault(label:String){if(text("fault")==label)error("$label-fault")}
  val d=number("digest")
  val a=object:CryptoHmac.HashAlgorithm{
   override val blockSize get()=8.also{fault("blockSize")}
   override val digestSize get()=d.also{fault("digestSize")}
   override val contextSize get()=8.also{fault("contextSize")}
   override fun initialize(context:ByteArray){inits++;events+="init$inits";fault("init$inits");for(i in context.indices)context[i]=i.toByte()}
   override fun update(context:ByteArray,bytes:ByteArray){events+="update:"+bytes.joinToString(",","[","]"){(it.toInt() and 255).toString()};fault("update");var x=context[0];for(b in bytes)x=(x*33+b).toByte();context[0]=x}
   override fun finalize(context:ByteArray,output:ByteArray){events+="finalize";fault("finalize");for(i in output.indices)output[i]=(context[0]+17*i).toByte()}
  }
  fun accessFault(label:String){if(text("accessFault")==label)error("$label-fault")}
  val seed=object:CryptoHmac.ByteArrayAccess {
   val raw=bytes("seed").value
   override val size get()=raw.size.also{accessFault("key-length")}
   override fun <A> withBytes(action:(ByteArray)->Lazy<A>):Lazy<A>{
    events+="key-enter";accessFault("key-before")
    val result=if(r["twiceKey"]==JsonPrimitive(true)){action(raw);events+="key-again";action(raw)}else action(raw)
    events+="key-exit";accessFault("key-after")
    return if(r["bottomKeyResult"]==JsonPrimitive(true))cryptoRandomMemo{error("key-result-bottom")}else result
   }
  }
  val action=cryptoRandomMemo{CryptoMaskGen.mgf1(a,seed,number("length"));42}
  val results=List(2){try{action.value.toString()}catch(t:Throwable){t.message?.substringBefore('\n')?:t.javaClass.simpleName}}
  return buildJsonObject{put("results",JsonArray(results.map(::JsonPrimitive)));put("events",JsonArray(events.map(::JsonPrimitive)))}
 }
 @JvmStatic fun main(args:Array<String>){var count=0;for(v in Json.parseToJsonElement(File(args[0]).readText()).jsonArray){val r=v.jsonObject;val actual=observe(r.getValue("request").jsonObject);check(actual==r["expected"]){"row$count expected=${r["expected"]} actual=$actual request=${r["request"]}"};count++};println("PASS $count original demand rows")}
}
