package io.heapy.kotdoc.ast
import java.io.File
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.*
internal object ManyDerivedProof {
 private val raw=mutableMapOf<String,List<String>>()
 private val named=mutableMapOf<String,List<Pair<String,JsonObject>>>()
 @Synchronized fun lines(name:String):List<String> = raw.getOrPut(name){
  val resource=javaClass.getResourceAsStream("/pandoc-types/many-derived/$name.gz")
  if(resource!=null)GZIPInputStream(resource).bufferedReader().use{it.readLines()}
  else File(System.getProperty("kotdoc.root","."),"tools/haskell-types-many-derived-reference/$name").readLines()
 }
 @Synchronized fun named(name:String,only:String?=null):List<Pair<String,JsonObject>> {
  val all=named.getOrPut(name){val rows=lines(name);require(rows.size%2==0);rows.indices.step(2).map{i->Json.parseToJsonElement(rows[i]).jsonPrimitive.content to Json.parseToJsonElement(rows[i+1]).jsonObject}.also{require(it.map{r->r.first}.distinct().size==it.size)}}
  return if(only==null)all else all.filter{it.first==only}.also{require(it.size==1){"Unknown original observation $only"}}
 }
}
