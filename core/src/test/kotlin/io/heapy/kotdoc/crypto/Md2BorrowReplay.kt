package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
object Md2BorrowReplay {
 fun observe(r:JsonObject):JsonObject {
  val input=r.getValue("message").jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray();val a=CryptoMd2Hash.Algorithm.MD2
  fun array(b:ByteArray)=JsonArray(b.map{JsonPrimitive(it.toInt() and 255)})
  fun fill(b:CryptoHmac.ByteArrayAccess){b.withBytes{it.fill(123,56,96);lazyOf(Unit)}}
  fun flip(b:CryptoHmac.ByteArrayAccess,i:Int){b.withBytes{it[i]=(it[i]+1).toByte();lazyOf(Unit)}}
  val c=CryptoMd2Hash.hashUpdate(CryptoMd2Hash.hashInit(a),byteArrayOf(42)+input);val nonempty=CryptoMd2Hash.hashUpdate(c,byteArrayOf(7));val empty=CryptoMd2Hash.hashUpdate(c,ByteArray(0));fill(c);val before=c.debugBytes();flip(c,24);val after=c.debugBytes();val n=CryptoMd2Hash.hashFinalize(nonempty).bytes();val e=CryptoMd2Hash.hashFinalize(empty).bytes()
  val digest=CryptoMd2Hash.hash(a,input);val original=digest.bytes();flip(digest,0);val changed=digest.bytes();val copy=ByteArray(20){99};digest.copyBytesTo(copy,2)
  val mutable=CryptoMd2Hash.hashMutableInit(a);fill(mutable);CryptoMd2Hash.hashMutableUpdate(mutable,input);flip(mutable,24);val mutated=CryptoMd2Hash.hashMutableFinalize(mutable).bytes();CryptoMd2Hash.hashMutableReset(mutable);val reset=mutable.debugBytes()
  return buildJsonObject{put("before",array(before));put("after",array(after));put("nonemptyCopy",array(n));put("emptyAlias",array(e));put("digestOriginal",array(original));put("digestChanged",array(changed));put("copy",array(copy));put("mutableDigest",array(mutated));put("resetContext",array(reset))}
 }
}
