package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
class OriginalTypesMetadataTest {
 @Test fun `pinned metadata source dictionaries fixtures and production`()=MetadataProofFixtures.integrity()
 @TestFactory fun `all original metadata dictionaries helpers and algebra`():List<DynamicTest>{val requests=MetadataProofFixtures.values("requests.jsonl");val expected=MetadataProofFixtures.values("responses.jsonl");check(requests.size==expected.size);return requests.zip(expected).map{(r,e)->val row=r.jsonObject;dynamicTest(row.getValue("case").jsonPrimitive.content){val actual=metadataExecute(row);check(actual==e){"${row["case"]}: $actual != $e"}}}}
 @TestFactory fun `original metadata demand custom dictionaries and source class defaults`():List<DynamicTest> = MetadataProofFixtures.values("demand-native.jsonl").chunked(2).map{(n,e)->val name=n.jsonPrimitive.content;dynamicTest(name){val actual=metadataDemandExecute(name);check(actual==e){"$name: $actual != $e"}}}
 @TestFactory fun `legacy metadata helpers preserve the exact original observations`():List<DynamicTest>{
  val expected=MetadataProofFixtures.values("demand-native.jsonl").chunked(2).associate{it[0].jsonPrimitive.content to it[1]}
  fun badBlocks():List<Block> = GenericList.cons({error("head")},{error("tail")})
  val probes=listOf<Pair<String,()->Any>>(
   "doc-title-block-head-vs-tail" to {docTitle(mapOf("title" to MetaValue.MetaBlocks(badBlocks())))[0]},
   "doc-date-block-head-vs-tail" to {docDate(mapOf("date" to MetaValue.MetaBlocks(badBlocks())))[0]},
   "doc-authors-first-group" to {val values=GenericList.cons({MetaValue.MetaString("s")},{GenericList.cons({MetaValue.MetaInlines(listOf(Inline.Str("i")))},{GenericList.cons<MetaValue>({error("tail-value")},{GenericList.empty()})})});listOf(docAuthors(mapOf("author" to MetaValue.MetaList(values)))[0])}
  )
  return probes.map{(name,probe)->dynamicTest(name){val actual=try{JsonPrimitive(AstShow.show(probe()))}catch(e:RuntimeException){buildJsonObject{put("exception",e.message.orEmpty())}};check(actual==expected.getValue(name)){"$actual != ${expected.getValue(name)}"}}}
 }
 @Test fun `source map adapter obeys structural JVM Map and Entry contracts`(){
  val expected=mapOf("x" to MetaValue.MetaBool(true),"y" to MetaValue.MetaString("value"))
  val first=MetadataMap.fromMap(expected);val second=MetadataMap.fromMap(expected.toMap())
  check(first==expected&&expected==first&&first==second)
  check(first.hashCode()==expected.hashCode()&&first.hashCode()==second.hashCode())
  check(first.entries==expected.entries&&expected.entries==first.entries)
  check(first.entries.hashCode()==expected.entries.hashCode())
  check(hashSetOf<Map<String,MetaValue>>(first).contains(expected))
 }
 @Test fun `metadata lazy values and source roots are shared`(){
  var converted=0;val dictionary=ToMetaValue<Int>{converted++;MetaValue.MetaBool(it()>0)}
  val value=MetadataConversions.list(dictionary).toMetaValue{listOf(1)} as MetaValue.MetaList
  check(converted==0);check(value.values[0]===value.values[0]&&converted==1)
  var roots=0;val set=PandocMetadata.meta.setMeta({"x"},{1},dictionary){roots++;MetadataMap.empty()}
  val first=set();check(first===set()&&roots==1);check(first["x"]===first["x"]&&converted==2)
 }
}
