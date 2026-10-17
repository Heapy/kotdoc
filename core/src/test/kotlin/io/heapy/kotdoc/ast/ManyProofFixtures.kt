package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import java.io.File
import java.util.zip.GZIPInputStream
internal object ManyProofFixtures {
 val streams=listOf("core","construction","algebra","numeric","traversal")
 fun values(name:String):List<JsonElement>{
  val resource=javaClass.getResourceAsStream("/pandoc-types/many/$name.gz")
  val text=resource?.let{GZIPInputStream(it).bufferedReader().use{reader->reader.readText()}}?:File(root(),"tools/haskell-types-many-reference/$name").readText()
  return text.lineSequence().filter(String::isNotBlank).map(Json::parseToJsonElement).toList()
 }
 fun root():File {var root=File(System.getProperty("kotdoc.root",".")).absoluteFile;while(!File(root,"upstream/pandoc-types/pandoc-types.cabal").exists())root=root.parentFile?:error("Source checkout unavailable");return root}
 fun integrity(){
  val resource=javaClass.getResourceAsStream("/pandoc-types/many/source-manifest.json")
  val text=resource?.bufferedReader()?.use{it.readText()}?:File(root(),"tools/haskell-types-many-reference/source-manifest.json").readText()
  for((name,want)in Json.parseToJsonElement(text).jsonObject.getValue("sha256").jsonObject){
   val digest=java.security.MessageDigest.getInstance("SHA-256").digest(File(root(),name).readBytes()).joinToString(""){"%02x".format(it)}
   check(digest==want.jsonPrimitive.content){"Many source/artifact hash changed: $name"}
  }
 }
}
