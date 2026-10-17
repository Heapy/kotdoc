package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
import java.io.File

/** Full original RSA computations with finite caller-controlled retry streams. */
object RsaRetryReference {
 private data class Probe(val mode:String,val retry:Int,val budget:Int,val fault:String,val at:Int,val index:Int):CryptoDrg<Probe>{override fun randomBytesGenerate(length:Int):Pair<ByteArray,Probe> = error("dictionary required")
  override fun toString()="Probe \"$mode\" $retry $budget \"$fault\" ${if(at<0)"($at)"else at} $index"
 }
 fun assertRow(line:String){
  val row=Json.parseToJsonElement(line).jsonObject
  fun text(k:String)=row.getValue(k).jsonPrimitive.content
  fun int(k:String)=row.getValue(k).jsonPrimitive.int
  val initial=Probe(text("mode"),int("retry"),int("budget"),text("fault"),int("at"),0)
  val calls=mutableListOf<Int>()
  val ops=CryptoDrgOperations<Probe>{length,state->
   val s=state.value;calls+=length
   if(s.index>=s.budget)error("budget")
   if(s.fault=="root"&&s.index==s.at)error("pair fault")
   val byte=when(s.mode){"outer"->if(s.index%2==0)0 else 2;"q"->0;"outerSuccess"->if(s.index<2*s.retry){if(s.index%2==0)0 else 2}else{if(s.index%2==0)2 else 32};"qSuccess"->if(s.index<=s.retry)0 else 2;else->error("mode")}
   cryptoRandomMemo{if(s.fault=="bytes"&&s.index==s.at)error("bytes fault");ByteArray(length){byte.toByte()}} to
    cryptoRandomMemo{if(s.fault=="state"&&s.index==s.at)error("state fault");s.copy(index=s.index+1)}
  }
  val rsa=CryptoRsa(CryptoNumberPrime(CryptoProbabilistic{error("secret")}))
  val e=when(initial.mode){"outer"->2;"outerSuccess"->3;else->17}.toBigInteger()
  val action=rsa.generateAction(lazyOf(2),lazyOf(e),ops)
  val pair=CryptoPseudoRandom.withDRG(lazyOf(initial),action)
  fun root():String{pair.first.value;return "42"}
  fun state()=pair.second.value.toString()
  fun key():String{val(p,k)=pair.first.value;return "(${p.size},${p.modulus},${p.exponent},${k.d},${k.p},${k.q},${k.dP},${k.dQ},${k.qinv})"}
  val projections:List<()->String> =when(text("projection")){"ignore"->listOf({CryptoPseudoRandom.withDRG(lazyOf(initial),action.map{42}).first.value.toString()});"root"->listOf(::root);"state"->listOf(::state);"key"->listOf(::key);"repeatRootState"->listOf(::root,::root,::state);else->error("projection")}
  val results=projections.map{p->val value=try{p()}catch(e:Exception){e.message?:error("message")};buildJsonObject{put("value",value);put("calls",JsonArray(calls.map{JsonPrimitive(it)}))}}
  check(JsonArray(results)==row.getValue("results")){"$line\ngot ${JsonArray(results)}"}
 }
 @JvmStatic fun main(args:Array<String>){var n=0;File(args[0]).forEachLine{assertRow(it);n++};println("PASS $n finite original RSA retry rows; max5000budget/2500successful retries")}
}
