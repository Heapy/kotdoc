package io.heapy.kotdoc.crypto
internal object Md5TraceObserve {fun observe(line:String){
  val x=line.split('\t');val alg=CryptoMd5.Algorithm.valueOf(x[0]);val ctx=x[1].hexToByteArray();val input=x[2].hexToByteArray();val trace=mutableListOf<Long>()
  CryptoMd5.observe={label,i,v->trace+=if(label=="schedule")999L else i.toLong();trace+=v.size.toLong();trace+=v.map{it.toLong() and 0xffffffffL}}
  try{CryptoMd5.chunk(CryptoMd5.Context(alg,ctx,16),input,16)}finally{CryptoMd5.observe=null}
  check(ctx.toHexString()==x[3]){"trace context"};check(trace.joinToString(",")==x[4]){"trace"}
}}
