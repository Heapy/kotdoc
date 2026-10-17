package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
internal object Md5DemandReplay {
 fun observe(row:JsonObject):String {
  val alg=CryptoMd5Hash.Algorithm.valueOf(row.getValue("algorithm").jsonPrimitive.content)
  val ctx=lazy{CryptoMd5Hash.hashInit(alg)};val bad=lazy<CryptoMd5Hash.Context>{error("context-bottom")};val bytes=lazy<ByteArray>{error("bytes-bottom")};val length=lazy<Int>{error("length-bottom")}
  fun chunks(first:ByteArray)=lazy<Iterable<ByteArray>>{Iterable{object:Iterator<ByteArray>{var pos=0;override fun hasNext()=pos<2;override fun next():ByteArray=if(pos++==0)first else error("tail-bottom")}}}
  return try{
   when(row.getValue("case").jsonPrimitive.content){
    "ignored-witness"->CryptoMd5Hash.deferHashInit(alg,lazy{error("algorithm-bottom")}).value
    "ignored-finalize"->CryptoMd5Hash.deferHashFinalize(bad)
    "finalize-root"->CryptoMd5Hash.deferHashFinalize(bad).value
    "empty-update-root"->CryptoMd5Hash.deferHashUpdate(bad,lazyOf(byteArrayOf())).value
    "update-bytes-first"->CryptoMd5Hash.deferHashUpdate(bad,bytes).value
    "updates-empty-tail"->CryptoMd5Hash.deferHashUpdates(bad,chunks(byteArrayOf())).value
    "updates-context-before-tail"->CryptoMd5Hash.deferHashUpdates(bad,chunks(byteArrayOf(1))).value
    "updates-good-context-tail"->CryptoMd5Hash.deferHashUpdates(ctx,chunks(byteArrayOf(1))).value
    "prefix-context-first"->CryptoMd5Hash.deferHashFinalizePrefix(bad,bytes,length).value
    "prefix-bytes-first"->CryptoMd5Hash.deferHashFinalizePrefix(ctx,bytes,length).value
    "prefix-length"->CryptoMd5Hash.deferHashFinalizePrefix(ctx,lazyOf(byteArrayOf()),length).value
    "ignored-prefix"->CryptoMd5Hash.deferHashFinalizePrefix(bad,bytes,length)
    else->error("case")
   };"42"
  }catch(t:Throwable){t.message?:t.javaClass.name}
 }
}
