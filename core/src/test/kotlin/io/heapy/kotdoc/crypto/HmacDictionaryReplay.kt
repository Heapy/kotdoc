package io.heapy.kotdoc.crypto
import java.io.File
import kotlinx.serialization.json.*
object HmacDictionaryReplay {
 fun observe(r:JsonObject):JsonObject{
  fun number(k:String)=r.getValue(k).jsonPrimitive.int
  fun flag(k:String)=r[k]?.jsonPrimitive?.boolean?:false
  fun bytes(k:String)=r.getValue(k).jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray()
  val events=mutableListOf<String>();var count=0
  fun fault(name:String){if(r.getValue("fault").jsonPrimitive.content==name)error("$name-fault")}
  val alg=object:CryptoHmac.HashAlgorithm{
   override val blockSize:Int get(){fault("blockSize");return number("block")}
   override val digestSize:Int get(){fault("digestSize");return number("digest")}
   override val contextSize:Int get(){fault("contextSize");return 8}
   override fun initialize(context:ByteArray){val label="init${++count}";events+=label;fault(label);for(i in 0..<8)context[i]=i.toByte()}
   override fun update(context:ByteArray,bytes:ByteArray){events+="update:"+bytes.joinToString(",","[","]"){(it.toInt() and 255).toString()};fault("update");var acc=context[0].toInt() and 255;for(b in bytes)acc=(acc*33+(b.toInt() and 255)) and 255;context[0]=acc.toByte()}
   override fun finalize(context:ByteArray,output:ByteArray){events+="finalize";fault("finalize");for(i in output.indices)output[i]=(context[0].toInt()+17*i).toByte()}
   private fun unitResult(name:String):Lazy<Unit> = if(r["unitBottom"]?.jsonPrimitive?.content in listOf(name,"all"))lazy{error("$name-result-bottom")}else lazyOf(Unit)
   override fun initializeResult(context:ByteArray):Lazy<Unit>{initialize(context);return unitResult("init")}
   override fun updateResult(context:ByteArray,bytes:ByteArray):Lazy<Unit>{update(context,bytes);return unitResult("update")}
   override fun finalizeResult(context:ByteArray,output:ByteArray):Lazy<Unit>{finalize(context,output);return unitResult("finalize")}
  }
  val key=lazy{if(flag("keyBottom"))error("key-bottom")else bytes("key")};val msg=lazy{if(flag("messageBottom"))error("message-bottom")else bytes("message")}
  val accessMode="accessFault" in r
  fun access(label:String)=object:CryptoHmac.ByteArrayAccess{
   private val data=bytes(if(label=="key")"key"else "message")
   private fun accessFault(name:String){if(r["accessFault"]?.jsonPrimitive?.content==name)error("$name-fault")}
   override val size:Int get(){accessFault("$label-length");return data.size}
   override fun <A> withBytes(action:(ByteArray)->Lazy<A>):Lazy<A>{
    events+="$label-enter";accessFault("$label-before")
    val result=if(flag(if(label=="key")"twiceKey"else "twiceMessage")){action(data);events+="$label-again";action(data)}else action(data)
    events+="$label-exit";accessFault("$label-after")
    return if(flag(if(label=="key")"bottomKeyResult"else "bottomMessageResult"))lazy{error("$label-result-bottom")}else result
   }
  }
  val keyAccess=lazy{access("key")};val messageAccess=lazy{access("message")}
  val ctx=if(accessMode)CryptoHmac.deferInitializeAccess(alg,keyAccess)else CryptoHmac.deferInitialize(alg,key)
  fun tail(first:ByteArray)=lazy<Iterable<ByteArray>>{Iterable{object:Iterator<ByteArray>{var pos=0;override fun hasNext()=pos<2;override fun next():ByteArray=if(pos++==0)first else error("tail-bottom")}}}
  val action:Lazy<Any?> = when(r.getValue("operation").jsonPrimitive.content){
    "ignored"->lazyOf(if(accessMode)CryptoHmac.deferHmacAccess(alg,keyAccess,messageAccess)else CryptoHmac.deferHmac(alg,key,msg))
    "initialize"->ctx
    "update"->if(accessMode)CryptoHmac.deferUpdateAccess(ctx,messageAccess)else CryptoHmac.deferUpdate(ctx,msg)
    "finalize"->CryptoHmac.deferFinalize(ctx)
    "hmac"->if(accessMode)CryptoHmac.deferHmacAccess(alg,keyAccess,messageAccess)else CryptoHmac.deferHmac(alg,key,msg)
    "updatesAccess"->CryptoHmac.deferUpdatesAccess(ctx,lazy{listOf(messageAccess.value,messageAccess.value)})
    "hmacLazyAccess"->CryptoHmac.deferHmacLazyAccess(alg,keyAccess,lazy{listOf(bytes("message"))})
    "outerBottom"->CryptoHmac.deferContext(alg,lazy{error("outer-bottom")},lazy{error("inner-bottom")})
    "innerBottom"->CryptoHmac.deferContext(alg,lazy{CryptoHmac.hashContext(ByteArray(alg.contextSize).also{alg.initializeResult(it)})},lazy{error("inner-bottom")})
    "equalBottom"->CryptoHmac.deferEquals(lazy{error("left-bottom")},lazy{error("right-bottom")})
    "macBottom"->CryptoHmac.deferMac(lazy{error("digest-bottom")})
    "getDigestBottom"->CryptoHmac.deferGetDigest(lazy{error("mac-bottom")})
    "updatesTail"->CryptoHmac.deferUpdates(ctx,tail(byteArrayOf(1)))
    "updatesEmptyTail"->CryptoHmac.deferUpdates(ctx,tail(byteArrayOf()))
    "updatesMessageBottom"->CryptoHmac.deferUpdates(ctx,lazy{Iterable{object:Iterator<ByteArray>{var done=false;override fun hasNext()=!done;override fun next():ByteArray{done=true;error("message-bottom")}}}})
    "updatesListBottom"->CryptoHmac.deferUpdates(ctx,lazy{error("list-bottom")})
    else->error("operation")
  }
  val results=List(r["repeat"]?.jsonPrimitive?.int?:1){try{action.value;"42"}catch(t:Throwable){t.message?:t.javaClass.name}}
  return buildJsonObject{put("result",results.first());if("repeat" in r)put("results",JsonArray(results.map(::JsonPrimitive)));put("events",JsonArray(events.map(::JsonPrimitive)))}

 }
 @JvmStatic fun main(args:Array<String>){val rows=Json.parseToJsonElement(File(args[0]).readText()).jsonArray;for((i,v)in rows.withIndex()){val row=v.jsonObject;val actual=observe(row.getValue("request").jsonObject);check(actual==row.getValue("expected")){"row$i ${row["request"]} expected=${row["expected"]} actual=$actual"}};println("PASS ${rows.size} original custom dictionary probes")}
}
