package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
class OriginalTypesCountTest {
 @Test fun `pinned source dictionaries fixtures and Kotlin adapters`()=CountProofFixtures.integrity()
 @TestFactory fun `all three original Num Enum dictionaries and legacy AST bridges`():List<DynamicTest>{val requests=CountProofFixtures.values("requests.jsonl");val expected=CountProofFixtures.values("responses.jsonl");check(requests.size==expected.size);return requests.zip(expected).map{(r,want)->val row=r.jsonObject;dynamicTest(row.getValue("case").jsonPrimitive.content){val actual=countExecute(row);check(actual==want){"$r: $actual != $want"}}}}
 @TestFactory fun `original non-strict newtype patterns and Num Enum demand`():List<DynamicTest> = CountProofFixtures.values("demand-native.jsonl").chunked(2).map{pair->val name=pair[0].jsonPrimitive.content;dynamicTest(name){val actual=countDemandExecute(name);check(actual==pair[1]){"$name: $actual != ${pair[1]}"}}}
 @Test fun `source thunk adapters share constructed fields and matched roots`(){
  var fields=0;val value=PandocCounts.RowSpan.construct{fields++;7L};check(value().value==7L&&value().value==7L&&fields==1)
  var roots=0;val matched=PandocCounts.RowSpan.match({roots++;RowSpan(9)}){it()+it()};check(matched==18L&&roots==1)
 }
}
