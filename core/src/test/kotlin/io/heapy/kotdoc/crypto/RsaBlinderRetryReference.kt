package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
import java.io.File
object RsaBlinderRetryReference {
 private data class Probe(val mode:String,val index:Int):CryptoDrg<Probe>{override fun randomBytesGenerate(length:Int):Pair<ByteArray,Probe> =error("dictionary required");override fun toString()="Probe \"$mode\" $index"}
 fun assertRow(line:String){
  val row=Json.parseToJsonElement(line).jsonObject;val mode=row.getValue("mode").jsonPrimitive.content;val n=row.getValue("n").jsonPrimitive.content.toBigInteger();val projection=row.getValue("projection").jsonPrimitive.content
  val calls=mutableListOf<Int>()
  val ops=CryptoDrgOperations<Probe>{length,state->
   val s=state.value;calls+=length
   if(mode=="rootFault"&&s.index==50)error("pair fault")
   cryptoRandomMemo{if(mode=="bytesFault"&&s.index==50)error("bytes fault");ByteArray(length){if(mode=="reject"||s.index<99)(-1).toByte()else 1}} to
    cryptoRandomMemo{if(mode=="stateFault"&&s.index==99)error("state fault");Probe(mode,s.index+1)}
  }
  val rsa=CryptoRsa(CryptoNumberPrime(CryptoProbabilistic{error("secret")}));val action=rsa.generateBlinderAction(lazyOf(n),ops);val pair=CryptoPseudoRandom.withDRG(lazyOf(Probe(mode,0)),action)
  fun root():String{pair.first.value;return "42"}
  fun state()=pair.second.value.toString()
  val projections:List<()->String> =when(projection){"ignore"->listOf({CryptoPseudoRandom.withDRG(lazyOf(Probe(mode,0)),action.map{42}).first.value.toString()});"root"->listOf(::root);"state"->listOf(::state);"repeatRootState"->listOf(::root,::root,::state);else->error("projection")}
  val results=projections.map{p->val value=try{p()}catch(e:Exception){e.message?:error("message")};buildJsonObject{put("value",value);put("calls",JsonArray(calls.map{JsonPrimitive(it)}))}}
  check(JsonArray(results)==row.getValue("results")){"$line\ngot ${JsonArray(results)}"}
 }
 @JvmStatic fun main(args:Array<String>){var n=0;File(args[0]).forEachLine{assertRow(it);n++};println("PASS $n finite original blinder retry rows")}
}
