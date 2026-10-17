// Independent source observations; Pandoc authors, GPL-2.0-or-later.
package io.heapy.kotdoc.writers
import java.io.File
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Test
class AsciiDocListBoundaryTest {
 @TestFactory fun sourceLists():List<DynamicTest> {
  val root=File("../tools/haskell-asciidoc-list-reference/fixtures")
  val expected=listOf("list","adjacent-list","comma-list","table-list","null-title-list","attr-list","infinite-list").flatMap{file->GZIPInputStream(File(root,"$file.jsonl.gz").inputStream()).bufferedReader().use{it.readLines()}}.associate{val r=Json.parseToJsonElement(it).jsonObject;r.getValue("name").jsonPrimitive.content to r}
  return listOf(false,true).flatMap{source->(listBoundaryCases(source)+adjacentListCases(source)+commaListCases(source)+tableListCases(source)+nullTitleListCases(source)+attrListCases(source)+infiniteListCases(source)).map{(name,make)->DynamicTest.dynamicTest("$source/$name"){check(observeListBoundary(name,make)==expected[name]){name}}}}
 }
 @TestFactory fun commaWalkConstructors():List<DynamicTest> {
  val root=File("../tools/haskell-asciidoc-list-reference/fixtures")
  fun rows(name:String)=GZIPInputStream(File(root,"$name.jsonl.gz").inputStream()).bufferedReader().use{it.readLines().map{line->Json.parseToJsonElement(line).jsonObject}}
  val expected=rows("comma-walk-expected").associateBy{it.getValue("name").jsonPrimitive.content}
  return rows("comma-walk-requests").map{q->val name=q.getValue("name").jsonPrimitive.content;DynamicTest.dynamicTest("comma-walk/$name"){
   val input=q.getValue("inlines").jsonArray.map(io.heapy.kotdoc.ast.PandocJson::decodeInline)
   val actual=JsonArray(AsciiDocCommaWalk.inlines(input).map(io.heapy.kotdoc.ast.PandocJson::encodeInline))
   check(actual==expected.getValue(name).getValue("inlines")){name}
  }}
 }
 @TestFactory fun identifierHelpers():List<DynamicTest> {
  val expected=GZIPInputStream(File("../tools/haskell-asciidoc-list-reference/fixtures/identifier-boundary.jsonl.gz").inputStream()).bufferedReader().use{it.readLines()}.associate{val r=Json.parseToJsonElement(it).jsonObject;r.getValue("name").jsonPrimitive.content to r}
  return identifierBoundaryCases().map{(name,extension,make)->DynamicTest.dynamicTest(name){check(observeIdentifierBoundary(name,extension,make)==expected.getValue(name)){name}}}
 }
 @TestFactory fun borrowedHelpers():List<DynamicTest> {
  val expected=GZIPInputStream(File("../tools/haskell-asciidoc-list-reference/fixtures/borrowed-boundary.jsonl.gz").inputStream()).bufferedReader().use{it.readLines()}.associate{val r=Json.parseToJsonElement(it).jsonObject;r.getValue("name").jsonPrimitive.content to r}
  return borrowedBoundaryCases().map{(name,standalone,make)->DynamicTest.dynamicTest(name){check(observeBorrowedBoundary(name,standalone,make)==expected.getValue(name)){name}}}
 }
 @TestFactory fun identityConstructors():List<DynamicTest> {
  val root=File("../tools/haskell-asciidoc-list-reference/fixtures")
  fun rows(name:String)=GZIPInputStream(File(root,"$name.jsonl.gz").inputStream()).bufferedReader().use{it.readLines().map{line->Json.parseToJsonElement(line).jsonObject}}
  val expected=rows("identity-list-expected").associateBy{it.getValue("name").jsonPrimitive.content}
  return rows("comma-walk-requests").map{q->val name=q.getValue("name").jsonPrimitive.content;DynamicTest.dynamicTest("identity/$name"){
   val input=q.getValue("inlines").jsonArray.map(io.heapy.kotdoc.ast.PandocJson::decodeInline)
   val actual=JsonArray(AsciiDocIdentityLists.inlines(input).map(io.heapy.kotdoc.ast.PandocJson::encodeInline))
   check(actual==expected.getValue(name).getValue("inlines")){name}
  }}
 }
 @TestFactory fun protocolOnly():List<DynamicTest> {
  val expected=listOf("protocol-only","protocol-header").flatMap{file->GZIPInputStream(File("../tools/haskell-asciidoc-list-reference/fixtures/$file.jsonl.gz").inputStream()).bufferedReader().use{it.readLines()}}.associate{val r=Json.parseToJsonElement(it).jsonObject;r.getValue("name").jsonPrimitive.content to r}
  return (protocolOnlyCases()+protocolHeaderCase()).map{(name,make)->DynamicTest.dynamicTest(name){check(observeListBoundary(name,make)==expected.getValue(name)){name}}}
 }
 @TestFactory fun metadataOrder():List<DynamicTest> {
  val root=File("../tools/haskell-asciidoc-list-reference/fixtures")
  val expected=listOf("metadata-list","metadata-map-list").flatMap{file->GZIPInputStream(File(root,"$file.jsonl.gz").inputStream()).bufferedReader().use{it.readLines()}}.associate{val r=Json.parseToJsonElement(it).jsonObject;r.getValue("name").jsonPrimitive.content to r}
  val cases=listOf(false,true).flatMap{source->metadataListCases(source).map{(n,m)->Triple("$source/$n",n,m)}}+metadataMapCase().let{(n,m)->listOf(Triple(n,n,m))}
  return cases.map{(label,name,make)->DynamicTest.dynamicTest(label){check(observeMetadataBoundary(name,make)==expected.getValue(name)){name}}}
 }
 @TestFactory fun stringifyConstructors():List<DynamicTest> {
  val root=File("../tools/haskell-asciidoc-list-reference/fixtures")
  fun rows(name:String)=GZIPInputStream(File(root,"$name.jsonl.gz").inputStream()).bufferedReader().use{it.readLines().map{line->Json.parseToJsonElement(line).jsonObject}}
  val expected=rows("stringify-expected").associateBy{it.getValue("name").jsonPrimitive.content}
  return rows("stringify-requests").map{q->val name=q.getValue("name").jsonPrimitive.content;DynamicTest.dynamicTest("stringify/$name"){
   val input=q.getValue("blocks").jsonArray.map(io.heapy.kotdoc.ast.PandocJson::decodeBlock)
   check(asciiDocStringify(input)==expected.getValue(name).getValue("text").jsonPrimitive.content){name}
  }}
 }
 @Test fun cursorSharing() {
  var heads=0;var tails=0
  val input=object:AbstractList<Int>(),AsciiDocListSpine<Int>{
   override val size:Int get()=error("extent must not be used")
   override fun get(index:Int):Int=error("indexed ordinary boundary must not be used")
   override fun asciiDocCell()=AsciiDocListCell({heads++;17},{tails++;listOf(23)})
  }
  val values=AsciiDocPatternList(input)
  check(values.hasAt(0)&&heads==0&&tails==0)
  check(values.hasAt(1)&&heads==0&&tails==1)
  check(values[0]==17&&values[0]==17&&heads==1)
  check(values.dropFirst()[0]==23&&values.mapFirst{it+1}[0]==18)
  check(heads==1&&tails==1)
  var calls=0;val mapped=AsciiDocPatternList(asciiDocShapeMap(input){calls++;it+1})
  check(mapped.hasAt(1)&&calls==0)
  check(mapped[0]==18&&mapped[0]==18&&calls==1)
 }
}
