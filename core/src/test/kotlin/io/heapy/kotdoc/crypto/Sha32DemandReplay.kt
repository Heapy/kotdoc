package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
internal object Sha32DemandReplay {
 fun observe(row:JsonObject):String {
  val alg=CryptoSha32Hash.Algorithm.valueOf(row.getValue("algorithm").jsonPrimitive.content)
  val ctx=lazy{CryptoSha32Hash.hashInit(alg)};val bad=lazy<CryptoSha32Hash.Context>{error("context-bottom")};val bytes=lazy<ByteArray>{error("bytes-bottom")};val length=lazy<Int>{error("length-bottom")}
  fun chunks(first:ByteArray)=lazy<Iterable<ByteArray>>{Iterable{object:Iterator<ByteArray>{var pos=0;override fun hasNext()=pos<2;override fun next():ByteArray=if(pos++==0)first else error("tail-bottom")}}}
  return try{
   when(row.getValue("case").jsonPrimitive.content){
    "ignored-witness"->CryptoSha32Hash.deferHashInit(alg,lazy{error("algorithm-bottom")}).value
    "ignored-finalize"->CryptoSha32Hash.deferHashFinalize(bad)
    "finalize-root"->CryptoSha32Hash.deferHashFinalize(bad).value
    "empty-update-root"->CryptoSha32Hash.deferHashUpdate(bad,lazyOf(byteArrayOf())).value
    "update-bytes-first"->CryptoSha32Hash.deferHashUpdate(bad,bytes).value
    "updates-empty-tail"->CryptoSha32Hash.deferHashUpdates(bad,chunks(byteArrayOf())).value
    "updates-context-before-tail"->CryptoSha32Hash.deferHashUpdates(bad,chunks(byteArrayOf(1))).value
    "updates-good-context-tail"->CryptoSha32Hash.deferHashUpdates(ctx,chunks(byteArrayOf(1))).value
    "prefix-context-first"->CryptoSha32Hash.deferHashFinalizePrefix(bad,bytes,length).value
    "prefix-bytes-first"->CryptoSha32Hash.deferHashFinalizePrefix(ctx,bytes,length).value
    "prefix-length"->CryptoSha32Hash.deferHashFinalizePrefix(ctx,lazyOf(byteArrayOf()),length).value
    "ignored-prefix"->CryptoSha32Hash.deferHashFinalizePrefix(bad,bytes,length)
    else->error("case")
   };"42"
  }catch(t:Throwable){t.message?:t.javaClass.name}
 }
}
