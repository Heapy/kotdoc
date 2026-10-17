package io.heapy.kotdoc.crypto
internal object Sha32TraceObserve {fun observe(line:String){
  val x=line.split('\t');val alg=CryptoSha32.Algorithm.valueOf(x[0]);val ctx=x[1].hexToByteArray();val input=x[2].hexToByteArray();val trace=mutableListOf<Long>()
  CryptoSha32.observe={label,i,v->trace+=if(label=="schedule")999L else i.toLong();trace+=v.size.toLong();trace+=v.map{it.toLong() and 0xffffffffL}}
  try{CryptoSha32.chunk(CryptoSha32.Context(alg,ctx,16),input,16)}finally{CryptoSha32.observe=null}
  check(ctx.toHexString()==x[3]){"trace context"};check(trace.joinToString(",")==x[4]){"trace"}
}}
