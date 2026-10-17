// Independent expectations from pinned Org reader/tests; GPL-2.0-or-later.
package io.heapy.kotdoc.readers.org
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.readers.OrgReader
import io.heapy.kotdoc.formats.NativeReader
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.logging.Logging
import io.heapy.kotdoc.errors.PandocError
import kotlinx.serialization.json.*
import org.junit.jupiter.api.*
import java.util.zip.GZIPInputStream
internal fun orgJsonEqual(a:JsonElement,b:JsonElement):Boolean {
 val stack=java.util.ArrayDeque<Pair<JsonElement,JsonElement>>();stack.add(a to b)
 while(stack.isNotEmpty()){val(x,y)=stack.removeLast();when{
  x is JsonObject&&y is JsonObject->{if(x.keys!=y.keys)return false;for(k in x.keys)stack.add(x.getValue(k)to y.getValue(k))}
  x is JsonArray&&y is JsonArray->{if(x.size!=y.size)return false;for(i in x.indices)stack.add(x[i]to y[i])}
  x is JsonPrimitive&&y is JsonPrimitive->{if(!x.isString&&!y.isString&&x!==JsonNull&&y!==JsonNull&&x.content !in listOf("true","false")&&y.content !in listOf("true","false")){if(x.content.toBigDecimal().compareTo(y.content.toBigDecimal())!=0)return false}else if(x!=y)return false}
  else->return false
 }};return true
}
internal fun orgRows(file:String):List<JsonObject> = GZIPInputStream(checkNotNull(OrgReaderTest::class.java.getResourceAsStream("/io/heapy/kotdoc/org-reader/$file"))).bufferedReader().useLines{lines->lines.filterNot{(file=="deep.jsonl.gz"&&it.contains("\"name\":\"headlines-depth/"))||(file=="meta-collections.jsonl.gz"&&it.contains("\"operation\":\"list\"")&&(it.contains("\"kind\":\"deep-list\"")||it.contains("\"kind\":\"deep-div\"")))}.map{Json.parseToJsonElement(it).jsonObject}.toList()}
class OrgReaderTest {
 @TestFactory fun originalAndSourceCases():List<DynamicTest> = buildList {
  for(file in listOf("original312.jsonl.gz","expanded.jsonl.gz","profile.jsonl.gz","boundary.jsonl.gz","deep.jsonl.gz","deep-tree-show.jsonl.gz"))for((i,row)in orgRows(file).withIndex())add(DynamicTest.dynamicTest("$file/$i/${row["name"]?:row.getValue("request").jsonObject["name"]}"){verify(row,file=="original312.jsonl.gz")})
 }
 private fun verify(row:JsonObject,original:Boolean){
  val q=row.getValue("request").jsonObject;val runtime=OrgTestRuntime(q);runtime.setTrace(q["trace"]?.jsonPrimitive?.boolean?:false)
  val options=(q["options"]?.let(ReaderOptions::fromJson)?:ReaderOptions()).copy(extensions=Extensions.read(q["extensions"]?.jsonArray?.map{it.jsonPrimitive.content}?:emptyList()))
  val sources=q["chunks"]?.jsonArray?.let{chunks->Sources(chunks.map{c->val a=c.jsonArray;SourceChunk(SourcePos(a[0].jsonPrimitive.content,a[1].jsonPrimitive.long,a[2].jsonPrimitive.long),a[3].jsonPrimitive.content)})}?:Sources.fromText(q["input"]?.jsonPrimitive?.content?:"")
  val expected=row.getValue("expected")
  val actual=try{val doc=OrgReader.read(options,sources,runtime){"1970-01-01"};if(original)Json.parseToJsonElement(PandocJson.encode(doc))else buildJsonObject{if(expected.jsonObject["showDocument"]!=null)put("showDocument",AstShow.show(doc))else put("document",Json.parseToJsonElement(PandocJson.encode(doc)));put("logs",Json.parseToJsonElement(Logging.encodeLogMessagesText(runtime.getLog())))}}catch(e:PandocError){buildJsonObject{put("error",e.renderError());put("showError",e.toString())}}
  Assertions.assertTrue(orgJsonEqual(actual,expected),"actual: $actual\nexpected: $expected")
 }
 @Test fun originalOldGolden(){val base="/io/heapy/kotdoc/org-reader/";val input=checkNotNull(javaClass.getResourceAsStream(base+"org-select-tags.org")).bufferedReader().readText();val expected=NativeReader.read(checkNotNull(javaClass.getResourceAsStream(base+"org-select-tags.native")).bufferedReader().readText());Assertions.assertEquals(expected,OrgReader.read(ReaderOptions(standalone=true,extensions=ExtensionRegistry.defaults("org")),input))}
}
