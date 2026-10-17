package io.heapy.kotdoc.crypto

import kotlinx.serialization.json.*

/** Independent source observations demand WHNF, not only Show/field projections. */
object RsaRootReference {
 private data class Probe(val mode:String,val step:Int):CryptoDrg<Probe>{override fun randomBytesGenerate(length:Int):Pair<ByteArray,Probe> =error("dictionary required")}
 fun assertRow(line:String) {
  val row=Json.parseToJsonElement(line).jsonObject
  val mode=row.getValue("mode").jsonPrimitive.content
  val n=row.getValue("n").jsonPrimitive.content.toBigInteger()
  val projection=row.getValue("projection").jsonPrimitive.content
  val calls=mutableListOf<Int>()
  val rsa=CryptoRsa(CryptoNumberPrime(CryptoProbabilistic{error("secret")}))
  val callback=mode.startsWith("callback")
  val ops=CryptoDrgOperations<Probe>{length,state->
   val current=state.value;calls+=length
   if(current.mode=="root")error("pair ${current.step}")
   cryptoRandomMemo{if(current.mode=="bytes")error("bytes ${current.step}");ByteArray(length){1}} to
    cryptoRandomMemo{if(current.mode=="state")error("state ${current.step}");Probe(current.mode,current.step+1)}
  }
  val action=rsa.generateBlinderAction(lazyOf(n),ops)
  val pair=if(callback)null else CryptoPseudoRandom.withDRG(lazyOf(Probe(mode,0)),action)
  val value=if(callback)rsa.generateBlinderDeferred(n){length->calls+=length;ByteArray(length){mode.removePrefix("callback").toInt().toByte()}}else pair!!.first
  fun root():String{value.value;return "42"}
  fun state():String{val s=pair!!.second.value;return "Probe \"${s.mode}\" ${s.step}"}
  val operations:List<()->String> =when(projection){
   "root"->listOf(::root)
   "r"->listOf({value.value.r.toString()})
   "inverse"->listOf({value.value.inverse.toString()})
   "show"->listOf({value.value.toString()})
   "state"->listOf(::state)
   "ignore"->listOf({if(callback)"42"else CryptoPseudoRandom.withDRG(lazyOf(Probe(mode,0)),action.map{42}).first.value.toString()})
   "repeatedRoot"->listOf(::root,::root,{value.value.r.toString()},{value.value.inverse.toString()})
   "stateRootState"->listOf(::state,::root,::state)
   else->error(projection)
  }
  var failure:Throwable?=null
  val results=operations.map { operation->
   val result=try{operation()}catch(e:Exception){
    if(projection=="repeatedRoot"){if(failure==null)failure=e else check(failure===e){"failure not shared"}}
    e.message?:error("exception without message")
   }
   buildJsonObject{put("value",result);put("calls",JsonArray(calls.map{JsonPrimitive(it)}))}
  }
  if(projection=="repeatedRoot"&&failure==null){
   check(value.value===value.value){"successful value not shared"}
   check(value.value.r===value.value.r&&value.value.inverse===value.value.inverse){"strict fields not shared"}
  }
  check(JsonArray(results)==row.getValue("results")){"$line\ngot ${JsonArray(results)}"}
 }
}
