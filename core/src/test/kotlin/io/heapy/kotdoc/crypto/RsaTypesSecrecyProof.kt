package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.ast.*
import kotlinx.serialization.json.*
import java.io.File
object RsaTypesSecrecyProof {
 fun checkCase(source:JsonObject,row:JsonObject) {
  val p=source.getValue("public").jsonObject;val secrets=source.getValue("secrets").jsonArray.map{it.jsonPrimitive.content}
  val public=CryptoRsaTypes.PublicKey(p.getValue("size").jsonPrimitive.long,p.getValue("n").jsonPrimitive.content.toBigInteger(),p.getValue("e").jsonPrimitive.content.toBigInteger())
  val s=secrets.map{it.toBigInteger()};val private=CryptoRsaTypes.PrivateKey(public,s[0],s[1],s[2],s[3],s[4],s[5]);val pair=CryptoRsaTypes.KeyPair(private)
  val isPair=row.getValue("type").jsonPrimitive.content=="KeyPair";val show=if(isPair)pair.toString()else private.toString();val debug=if(isPair)pair.debugShow()else private.debugShow()
  val actual=when(row.getValue("assertion").jsonPrimitive.content){
   "showHides"->JsonArray(secrets.filter{it in show}.map(::JsonPrimitive))
   "debugReveals"->JsonArray(secrets.filter{it !in debug}.map(::JsonPrimitive))
   "readDebug"->JsonPrimitive(if(isPair){val r=CryptoRsaTypesRead.keyPair.readsPrec({0},ManyReadInput(debug));r.size==1&&r[0].value()==pair&&r[0].remainder().isSpaces()}else{val r=CryptoRsaTypesRead.privateKey.readsPrec({0},ManyReadInput(debug));r.size==1&&r[0].value()==private&&r[0].remainder().isSpaces()})
   else->error("assertion")}
  check(actual==row.getValue("expected")){"source Secrecy ${row["type"]}/${row["assertion"]} expected=${row["expected"]} actual=$actual"}
 }
 @JvmStatic fun main(args:Array<String>){val source=Json.parseToJsonElement(File(args[0]).readText()).jsonObject;source.getValue("rows").jsonArray.forEach{checkCase(source,it.jsonObject)};println("PASS six original RSA Secrecy assertions")}
}
