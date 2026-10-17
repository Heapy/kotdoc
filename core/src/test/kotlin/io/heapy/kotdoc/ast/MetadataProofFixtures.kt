package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import java.io.File
import java.util.zip.GZIPInputStream
internal object MetadataProofFixtures {
 fun root():File {var root=File(System.getProperty("kotdoc.root",".")).absoluteFile;while(!File(root,"upstream/pandoc-types/pandoc-types.cabal").exists())root=root.parentFile?:error("Source checkout unavailable");return root}
 fun values(name:String):List<JsonElement>{val resource=javaClass.getResourceAsStream("/pandoc-types/metadata/$name.gz");val text=resource?.let{GZIPInputStream(it).bufferedReader().use{reader->reader.readText()}}?:File(root(),"tools/haskell-types-metadata-reference/$name").readText();return text.lineSequence().filter(String::isNotBlank).map(Json::parseToJsonElement).toList()}
 fun integrity(){val stream=javaClass.getResourceAsStream("/pandoc-types/metadata/source-manifest.json");val text=stream?.bufferedReader()?.use{it.readText()}?:File(root(),"tools/haskell-types-metadata-reference/source-manifest.json").readText();for((name,want)in Json.parseToJsonElement(text).jsonObject.getValue("sha256").jsonObject){val actual=java.security.MessageDigest.getInstance("SHA-256").digest(File(root(),name).readBytes()).joinToString(""){"%02x".format(it)};check(actual==want.jsonPrimitive.content){"Metadata source/artifact changed: $name"}}}
}
