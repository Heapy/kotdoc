// Original Builder observations/predicate, ©2010–2023 John MacFarlane; BSD-3-Clause.
package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OriginalTypesBuildersTest {
 private val root=File(System.getProperty("kotdoc.root","."))
 private fun lines(name:String):List<String> = javaClass.getResourceAsStream("/pandoc-types/builders/$name.gz")?.let{GZIPInputStream(it).bufferedReader().use{reader->reader.readLines()}}?:File(root,"tools/haskell-types-builder-reference/$name").readLines()
 @TestFactory fun originalFinite():List<DynamicTest>{
  val requests=lines("requests.jsonl");val expected=lines("responses.jsonl");check(requests.size==17630&&requests.size==expected.size)
  return requests.indices.map{i->DynamicTest.dynamicTest("original Builder finite $i"){assertEquals(Json.parseToJsonElement(expected[i]),builderExecute(Json.parseToJsonElement(requests[i]).jsonObject))}}
 }
 @TestFactory fun originalDemand():List<DynamicTest> = lines("demand-native.jsonl").chunked(2).map{(name,value)->val probe=Json.parseToJsonElement(name).jsonPrimitive.content;DynamicTest.dynamicTest("original Builder demand $probe"){assertEquals(Json.parseToJsonElement(value),builderDemandExecute(probe))}}
 @TestFactory fun originalLegacyDelegations():List<DynamicTest>{
  val expected=lines("demand-native.jsonl").chunked(2).associate{(name,value)->Json.parseToJsonElement(name).jsonPrimitive.content to Json.parseToJsonElement(value)}
  return legacyBuilderObservations().map{(name,observe)->DynamicTest.dynamicTest("original Builder legacy $name"){assertEquals(expected.getValue(name),JsonPrimitive(observe().toString()))}}
 }
 @TestFactory fun originalFigurePredicate():List<DynamicTest>{
  val resource=javaClass.getResourceAsStream("/pandoc-types/remaining-properties.jsonl.gz")?:File(root,"core/src/test/resources/pandoc-types/remaining-properties.jsonl.gz").inputStream()
  return GZIPInputStream(resource).bufferedReader().use{it.readLines()}.filter{Json.parseToJsonElement(it).jsonObject.getValue("kind").jsonPrimitive.content=="figure"}.map{wire->
   val r=Json.parseToJsonElement(wire).jsonObject
   DynamicTest.dynamicTest("source Builder p_figureRepresentation ${r["seed"]}/${r["size"]}/${r["witness"]}"){
    val caption=r.getValue("input").jsonArray.map(PandocJson::decodeInline)
    val input=PandocBuilders.fromInlines(Inlines(caption))
    val actual=PandocBuilders.toBlocks(PandocBuilders.simpleFigureWith(PandocBuilders.attr{Attr("test")},{input},{"url"},{"title"})).values
    assertEquals(r.getValue("expected").jsonArray.map(PandocJson::decodeBlock),actual)
    // Exact original predicate after independent original expected comparison.
    val right=listOf(Block.Figure(Attr(),Caption(null,if(caption.isEmpty())emptyList()else listOf(Block.Plain(caption))),listOf(Block.Plain(listOf(Inline.Image(Attr("test"),emptyList(),Target("url","title")))))))
    val native=r.getValue("predicate").jsonPrimitive.boolean
    assertTrue(native);assertEquals(native,actual==right)
   }
  }
 }
 @Test fun deferredSingleSharingAndLegacyConstructor(){
  var cellCalls=0;var sizeCalls=0
  lateinit var single:ManyTree.Single<GenericNode>
  val leaf=ManyCell.Leaf{BuilderStreams.nodes("Inline","Space",emptyList())}
  single=ManyTree.Single({cellCalls++;leaf}){sizeCalls++;single.cell.size}
  assertEquals(0,cellCalls);assertEquals(0,sizeCalls)
  assertTrue(single.cell===leaf);repeat(3){assertEquals(1L,single.size)}
  assertEquals(1,cellCalls);assertEquals(1,sizeCalls)
  val legacy=ManyTree.Single<GenericNode>({error("unused cell")},7L)
  assertEquals(7L,legacy.size)
  val failure=IllegalStateException("single");var failures=0
  lateinit var failed:ManyTree.Single<GenericNode>
  failed=ManyTree.Single({failures++;throw failure}){failed.cell.size}
  repeat(3){val actual=try{failed.size;error("Expected failure")}catch(e:IllegalStateException){e};assertTrue(actual===failure)}
  assertEquals(1,failures)
 }
 @Test fun sourceIntegrity(){
  val m=Json.parseToJsonElement(File(root,"tools/haskell-types-builder-reference/source-manifest.json").readText()).jsonObject
  for((path,want)in m.getValue("sha256").jsonObject){val hash=MessageDigest.getInstance("SHA-256").digest(File(root,path).readBytes()).joinToString(""){"%02x".format(it)};assertEquals(want.jsonPrimitive.content,hash,path)}
 }
}
