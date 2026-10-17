package io.heapy.kotdoc.crypto
import java.io.File
import kotlinx.serialization.json.*
object Pkcs15BoundaryReplay {
 private data object NoDraw:CryptoDrg<NoDraw>{override fun randomBytesGenerate(length:Int):Pair<ByteArray,NoDraw> = error("unexpected-draw")}
 fun observe(o:JsonObject):JsonElement {
  val k=o.getValue("length").jsonPrimitive.long;val bytes=ByteArray(o.getValue("messageLength").jsonPrimitive.int){7}
  return try{val r=if(o.getValue("op").jsonPrimitive.content=="signature")CryptoRsaPkcs15.padSignature(k,bytes)else CryptoPseudoRandom.withDRG(NoDraw,CryptoRsaPkcs15.padAction(lazyOf(k),lazyOf(bytes),CryptoDrgOperations{_,_->error("unexpected-draw")})).first.value;JsonPrimitive(when(r){is CryptoRsaPkcs15.Result.Failure->r.error.name;is CryptoRsaPkcs15.Result.Success->"Right"})}catch(t:Throwable){buildJsonObject{put("exception",t.message?.substringBefore('\n')?:t.javaClass.simpleName)}}
 }
 @JvmStatic fun main(args:Array<String>){var n=0;for(v in Json.parseToJsonElement(File(args[0]).readText()).jsonArray){val r=v.jsonObject;val got=observe(r.getValue("request").jsonObject);check(got==r["expected"]){"row$n expected=${r["expected"]} actual=$got"};n++};println("PASS $n original PKCS15 root-only virtual bounds")}
}
