package io.heapy.kotdoc.crypto
import java.io.File
fun main(args:Array<String>) {
 var count=0
 for(line in File(args[0]).useLines{it.toList()}) {
  val x=line.split('\t')
  try {
   if(x[0]=="mask"){
    val op=x[1].toInt();val a=x[2].toULong().toLong();val b=x[3].toULong().toLong()
    val actual=when(op){0->CryptoSha32.msb(a.toInt()).toLong() and 0xffffffffL;1->CryptoSha32.lt(a.toInt(),b.toInt()).toLong() and 0xffffffffL;2->CryptoSha32.ge(a.toInt(),b.toInt()).toLong() and 0xffffffffL;3->CryptoSha32.isZero(a.toInt()).toLong() and 0xffffffffL;4->CryptoSha32.eq(a.toInt(),b.toInt()).toLong() and 0xffffffffL;5->CryptoSha32.msb64(a);else->CryptoSha32.lt64(a,b)}
    check(actual.toULong().toString()==x[4]){"mask $actual != ${x[4]}"}
   }else{
    val alg=CryptoSha32.Algorithm.valueOf(x[0]);val op=x[1].toInt();val len=x[2].toInt();val arg=x[3].toLong().toInt()
    val ctx=x[4].hexToByteArray();val data=x[5].hexToByteArray();val mode=if(x.size>10)x[10].toInt()else 0;val out=when(mode){1->data;2,3->ctx;else->x[6].hexToByteArray()};val outpos=if(x.size>11)x[11].toInt()else 16;val c=CryptoSha32.Context(alg,ctx,16);val input=CryptoByteSpan(data,if(x.size>12)x[12].toInt()else 16,len);val dst=CryptoByteSpan(out,outpos,out.size-outpos)
    when(op){0,9->CryptoSha32.init(c);1,10->CryptoSha32.update(c,input);2,11->CryptoSha32.finalize(c,dst);3,12->CryptoSha32.finalizePrefix(c,input,arg,dst);4,5->CryptoSha32.chunk(c,data,16);6->CryptoSha32.incrementSize(c,arg).copyInto(out,16);7->CryptoSha32.selectDigest(c,dst,arg);8->{val n=CryptoSha32.getIndex(c);for(i in 0..<4)out[16+i]=(n ushr (8*i)).toByte()}}
    check(ctx.toHexString()==x[7]){"context"};check(data.toHexString()==x[8]){"input"};check(out.toHexString()==x[9]){"out"}
   }
  }catch(e:Throwable){throw AssertionError("row $count ${x.take(4)}: ${e.message}",e)}
  count++
 }
 println("PASS $count native observations")
}
