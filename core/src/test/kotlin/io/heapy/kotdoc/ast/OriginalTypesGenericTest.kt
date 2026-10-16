package io.heapy.kotdoc.ast

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Test

/** Expectations are raw responses from the pinned original Generic module. */
class OriginalTypesGenericTest {
    @Test fun `pinned original sources fixtures and Kotlin adapters`()=GenericProofFixtures.integrity()
    @TestFactory
    fun `original four-function core predicates`():List<DynamicTest> {
        val requests=GenericProofFixtures.rows("core-requests.jsonl")
        val expected=GenericProofFixtures.values("core-responses.jsonl")
        return requests.zip(expected).map{(request,want)->
            val probe=request.getValue("probe").jsonPrimitive.content
            dynamicTest(probe){check(genericCoreExecute(probe)==want){"$probe: ${genericCoreExecute(probe)} != $want"}}
        }
    }
    @TestFactory
    fun `all source constructors Data Typeable fields and four functions`():List<DynamicTest> =
        GenericProofFixtures.rows("constructor-requests.jsonl").mapIndexed{index,request->
            dynamicTest("example-$index-${request.getValue("kind").jsonPrimitive.content}"){genericConstructorCase(index)}
        }
    @TestFactory
    fun `source target type aliases containers and AST adapters`():List<DynamicTest> =
        GenericProofFixtures.rows("target-requests.jsonl").mapIndexed{index,request->
            dynamicTest("target-$index-example-${request.getValue("example")}-${request.getValue("target").jsonPrimitive.content}"){genericTargetCase(index)}
        }
    @TestFactory
    fun `original custom Data partial fields and monadic demand`():List<DynamicTest> {
        val requests=GenericProofFixtures.rows("demand-requests.jsonl")
        val expected=GenericProofFixtures.values("demand-responses.jsonl")
        return requests.zip(expected).map{(request,want)->
            val probe=request.getValue("probe").jsonPrimitive.content
            dynamicTest(probe){
                val actual=try{JsonPrimitive(genericDemandExecute(probe))}catch(error:Throwable){buildJsonObject{put("exception",error.message?:error.javaClass.simpleName)}}
                check(actual==want){"$probe: $actual != $want"}
            }
        }
    }
    @TestFactory
    fun `original finite deep traversal and effects`():List<DynamicTest> {
        val requests=GenericProofFixtures.rows("deep-requests.jsonl")
        val expected=GenericProofFixtures.rows("deep-responses.jsonl")
        return requests.zip(expected).map{(request,want)->
            dynamicTest("${request["shape"]?:"list"}-depth-${request.getValue("depth")}"){check(genericDeepExecute(request)==want){"deep $request"}}
        }
    }
}
