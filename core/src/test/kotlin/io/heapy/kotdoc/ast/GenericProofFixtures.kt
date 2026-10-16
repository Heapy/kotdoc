package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
internal object GenericProofFixtures {
 private val cache=ConcurrentHashMap<String,List<JsonElement>>()
 fun values(name:String):List<JsonElement> = cache.computeIfAbsent(name){
  val resource=javaClass.getResourceAsStream("/pandoc-types/generic/$name.gz")
  val text=if(resource!=null)GZIPInputStream(resource).bufferedReader().use{it.readText()}else File("tools/haskell-types-generic-reference/$name").readText()
  text.lineSequence().filter(String::isNotBlank).map(Json::parseToJsonElement).toList()
 }
 fun integrity() {
  val resource=javaClass.getResourceAsStream("/pandoc-types/generic/source-manifest.json")
  val text=resource?.bufferedReader()?.use{it.readText()}?:File("tools/haskell-types-generic-reference/source-manifest.json").readText()
  val manifest=Json.parseToJsonElement(text).jsonObject
  var root=File("").absoluteFile
  while(!File(root,"upstream/pandoc-types/pandoc-types.cabal").exists())root=root.parentFile?:error("Source checkout unavailable")
  for((name,want)in manifest.getValue("sha256").jsonObject){
   val actual=java.security.MessageDigest.getInstance("SHA-256").digest(File(root,name).readBytes()).joinToString(""){"%02x".format(it)}
   check(actual==want.jsonPrimitive.content){"Generic source/artifact hash changed: $name"}
  }
 }
 fun rows(name:String)=values(name).map{it.jsonObject}
}
