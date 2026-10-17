package io.heapy.kotdoc.crypto
import java.io.File
import java.math.BigInteger
import kotlinx.serialization.json.*
object RsaDemandReplay {
 data class Probe(val mode:String,val step:Int):CryptoDrg<Probe>{override fun randomBytesGenerate(length:Int):Pair<ByteArray,Probe> = error("dictionary required")}
 @JvmStatic fun main(args:Array<String>) {
  var count=0
  val pattern=Regex("\\(\"([^\"]+)\",(-?[0-9]+),\"([^\"]+)\",(.*)\\)")
  File(args[0]).readLines().forEach { line->
   val m=pattern.matchEntire(line)?:error(line)
   val mode=m.groupValues[1];val n=m.groupValues[2].toBigInteger();val projection=m.groupValues[3]
   val expected=Json.parseToJsonElement(m.groupValues[4]).jsonPrimitive.content
   val ops=CryptoDrgOperations<Probe>{size,state->
    val current=state.value
    if(current.mode=="root")error("pair root")
    cryptoRandomMemo{if(mode=="bytes")error("bytes field");ByteArray(size){1}} to cryptoRandomMemo{if(mode=="state")error("state field");Probe(mode,state.value.step+1)}
   }
   val rsa=CryptoRsa(CryptoNumberPrime(CryptoProbabilistic{ByteArray(32){it.toByte()}}))
   val action=rsa.generateBlinderAction(lazyOf(n),ops)
   val got=try {
    when(projection){
     "ignore"->CryptoPseudoRandom.withDRG(lazyOf(Probe(mode,0)),action.map{42}).first.value.toString()
     "state"->{val s=CryptoPseudoRandom.withDRG(lazyOf(Probe(mode,0)),action).second.value;"Probe \"${s.mode}\" ${s.step}"}
     else->CryptoPseudoRandom.withDRG(lazyOf(Probe(mode,0)),action).first.value.toString()
    }
   }catch(e:Exception){e.message?:error("message")}
   check(got==expected){"$line\ngot $got"};count++
  }
  println("PASS $count independent custom-dictionary demand rows")
 }
}
