package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
import java.io.File
object RsaTypesGenericProof {
 fun checkRow(rows:JsonArray,row:JsonObject):Boolean {
   val q=row.getValue("request").jsonObject;val expected=row.getValue("expected");val actual=RsaTypesReplay.observe(q)
   if(actual==expected)return true
   val allowed=q["type"]?.jsonPrimitive?.content=="PrivateKey"&&q["op"]?.jsonPrimitive?.content in listOf("genericToRoot","genericToShow")&&q["repErrors"]==JsonArray(listOf(JsonPrimitive("LR"),JsonPrimitive("R")))
   val witness=rows.map{it.jsonObject}.single{w->val x=w.getValue("request").jsonObject;x["type"]==q["type"]&&x["op"]==JsonPrimitive("genericToDebug")&&x["repErrors"]==q["repErrors"]}.getValue("expected")
   check(allowed&&actual==witness&&expected==buildJsonObject{put("exception","rep:R")}){"expected=$expected actual=$actual"}
   return false
 }
 @JvmStatic fun main(args:Array<String>){val rows=Json.parseToJsonElement(File(args[0]).readText()).jsonArray;val exact=rows.count{checkRow(rows,it.jsonObject)};check(rows.size-exact==2);println("PASS Generic101 observations: $exact exact; 2 named multi-bottom context alternatives (raw expected retained)")}
}
