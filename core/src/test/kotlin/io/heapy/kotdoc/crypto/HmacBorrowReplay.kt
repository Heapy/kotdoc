package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
object HmacBorrowReplay {
 fun observe(r:JsonObject):JsonObject{
  fun bytes(k:String)=r.getValue(k).jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray()
  fun json(b:ByteArray)=JsonArray(b.map{JsonPrimitive(it.toInt() and 255)})
  val mac=CryptoHmac.hmac(CryptoHmacAlgorithms.algorithm(CryptoHmacAlgorithms.Algorithm.SHA256),bytes("key"),bytes("message"));val before=mac.bytes()
  mac.withBytes{it[0]=(it[0].toInt() xor 255).toByte();lazyOf(Unit)}
  val first=mac.bytes();val getter=mac.hmacGetDigest().bytes()
  mac.hmacGetDigest().withBytes{it[1]=(it[1].toInt() xor 255).toByte();lazyOf(Unit)}
  val second=mac.bytes();val copied=ByteArray(mac.size+4){99};mac.copyBytesTo(copied,2)
  return buildJsonObject{put("before",json(before));put("first",json(first));put("getter",json(getter));put("second",json(second));put("copied",json(copied));put("equalsOriginal",mac==CryptoHmac.Mac(before))}
 }
}
