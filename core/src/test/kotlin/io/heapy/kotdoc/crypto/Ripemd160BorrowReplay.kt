package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
object Ripemd160BorrowReplay {
 fun observe(r:JsonObject):JsonObject {
  val input=r.getValue("message").jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray();val a=CryptoRipemd160Hash.Algorithm.RIPEMD160
  fun array(b:ByteArray)=JsonArray(b.map{JsonPrimitive(it.toInt() and 255)})
  fun fill(b:CryptoHmac.ByteArrayAccess){b.withBytes{it.fill(123,96,128);lazyOf(Unit)}}
  fun flip(b:CryptoHmac.ByteArrayAccess,i:Int){b.withBytes{it[i]=(it[i]+1).toByte();lazyOf(Unit)}}
  val c=CryptoRipemd160Hash.hashUpdate(CryptoRipemd160Hash.hashInit(a),byteArrayOf(42)+input);val nonempty=CryptoRipemd160Hash.hashUpdate(c,byteArrayOf(7));val empty=CryptoRipemd160Hash.hashUpdate(c,ByteArray(0));fill(c);val before=c.debugBytes();flip(c,72);val after=c.debugBytes();val n=CryptoRipemd160Hash.hashFinalize(nonempty).bytes();val e=CryptoRipemd160Hash.hashFinalize(empty).bytes()
  val digest=CryptoRipemd160Hash.hash(a,input);val original=digest.bytes();flip(digest,0);val changed=digest.bytes();val copy=ByteArray(24){99};digest.copyBytesTo(copy,2)
  val mutable=CryptoRipemd160Hash.hashMutableInit(a);fill(mutable);CryptoRipemd160Hash.hashMutableUpdate(mutable,input);flip(mutable,72);val mutated=CryptoRipemd160Hash.hashMutableFinalize(mutable).bytes();CryptoRipemd160Hash.hashMutableReset(mutable);val reset=mutable.debugBytes()
  return buildJsonObject{put("before",array(before));put("after",array(after));put("nonemptyCopy",array(n));put("emptyAlias",array(e));put("digestOriginal",array(original));put("digestChanged",array(changed));put("copy",array(copy));put("mutableDigest",array(mutated));put("resetContext",array(reset))}
 }
}
