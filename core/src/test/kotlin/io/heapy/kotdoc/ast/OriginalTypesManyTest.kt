package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/** Every expected response is preserved from the actual pinned Haskell dictionary. */
class OriginalTypesManyTest {
 @Test fun `pinned original dictionaries fixtures and Kotlin source`()=ManyProofFixtures.integrity()
 @TestFactory fun `source Many methods defaults specialized algebra and sequence shapes`():List<DynamicTest> =
  ManyProofFixtures.streams.flatMap{stream->
   val requests=ManyProofFixtures.values("$stream-requests.jsonl");val expected=ManyProofFixtures.values("$stream-responses.jsonl")
   check(requests.size==expected.size)
   requests.zip(expected).map{(request,want)->val row=request.jsonObject;dynamicTest("$stream-${row.getValue("case").jsonPrimitive.content}"){val actual=manyExecute(row);check(actual==want){"$row: $actual != $want"}}}
  }
 @TestFactory fun `source partial fields callback demand shared repetition and dictionary direction`():List<DynamicTest> =
  ManyProofFixtures.values("demand-native.jsonl").chunked(2).map{pair->val name=pair[0].jsonPrimitive.content;dynamicTest(name){val actual=manyDemandExecute(name);check(actual==pair[1]){"$name: $actual != ${pair[1]}"}}}
}
