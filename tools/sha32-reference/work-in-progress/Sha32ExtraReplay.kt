package io.heapy.kotdoc.crypto
import java.io.File
object Sha32ExtraReplay {
 @JvmStatic fun main(args:Array<String>){
  traceMain(args[0]);var count=0
  for(line in File(args[1]).readLines()){
   val x=line.split('\t').map{it.toULong().toLong()};val c=CryptoSha32.PrefixControl(x[0].toInt(),x[1].toInt(),x[2]);val p=x[3]
   val actual=listOf(c.n.toLong() and 0xffffffffL,c.initialIndex.toLong(),c.padLength.toLong(),c.limit,if(c.continues(p))1L else 0L,if(c.hasInput(p))1L else 0L,c.inputMask(p).toLong() and 0xffffffffL,c.firstPaddingMask(p).toLong() and 0xffffffffL,c.sizeMask(p).toLong() and 0xffffffffL,c.outputMask(p).toLong() and 0xffffffffL)
   check(actual==x.drop(4)){"boundary$count $actual != ${x.drop(4)}"};count++
  };println("PASS $count virtual prefix controls")
 }
}
