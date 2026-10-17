package io.heapy.kotdoc.crypto
internal object Md5RawReplay {
 fun observe(line:String){
  val x=line.split('\t')
  try {
   if(x[0]=="mask"){
    val op=x[1].toInt();val a=x[2].toULong().toLong();val b=x[3].toULong().toLong()
    val actual=when(op){0->CryptoMd5.msb(a.toInt()).toLong() and 0xffffffffL;1->CryptoMd5.lt(a.toInt(),b.toInt()).toLong() and 0xffffffffL;2->CryptoMd5.ge(a.toInt(),b.toInt()).toLong() and 0xffffffffL;3->CryptoMd5.isZero(a.toInt()).toLong() and 0xffffffffL;4->CryptoMd5.eq(a.toInt(),b.toInt()).toLong() and 0xffffffffL;5->CryptoMd5.msb64(a);else->CryptoMd5.lt64(a,b)}
    check(actual.toULong().toString()==x[4]){"mask $actual != ${x[4]}"}
   }else{
    val alg=CryptoMd5.Algorithm.valueOf(x[0]);val op=x[1].toInt();val len=x[2].toInt();val arg=x[3].toLong().toInt()
    val ctx=x[4].hexToByteArray();val data=x[5].hexToByteArray();val mode=if(x.size>10)x[10].toInt()else 0;val out=when(mode){1->data;2,3->ctx;else->x[6].hexToByteArray()};val outpos=if(x.size>11)x[11].toInt()else 16;val c=CryptoMd5.Context(alg,ctx,16);val input=CryptoByteSpan(data,if(x.size>12)x[12].toInt()else 16,len);val dst=CryptoByteSpan(out,outpos,out.size-outpos)
    when(op){0,9->CryptoMd5.init(c);1,10->CryptoMd5.update(c,input);2,11->CryptoMd5.finalize(c,dst);3,12->CryptoMd5.finalizePrefix(c,input,arg,dst);4,5->CryptoMd5.chunk(c,data,16);6->CryptoMd5.incrementSize(c,arg).copyInto(out,16);7->CryptoMd5.selectDigest(c,dst,arg);8->{val n=CryptoMd5.getIndex(c);for(i in 0..<4)out[16+i]=(n ushr (8*i)).toByte()}}
    check(ctx.toHexString()==x[7]){"context"};check(data.toHexString()==x[8]){"input"};check(out.toHexString()==x[9]){"out"}
   }
  }catch(e:Throwable){throw AssertionError("row ${x.take(4)}: ${e.message}",e)}
}
}
