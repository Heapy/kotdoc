// Original Text.Pandoc.Builder observations; copyright (c) 2006-2023 John MacFarlane.
// BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.ast

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals

class OriginalTypesTablesTest {
    private val root=File(System.getProperty("kotdoc.root", "."))
    private fun lines(name:String):List<String> = javaClass.getResourceAsStream("/pandoc-types/tables/$name.gz")?.let {
        GZIPInputStream(it).bufferedReader().use { reader -> reader.readLines() }
    } ?: File(root,"tools/haskell-types-table-reference/$name").readLines()

    @TestFactory fun originalFiniteResponses():List<DynamicTest> {
        val requests=lines("requests.jsonl");val expected=lines("responses.jsonl")
        check(requests.size==17559 && requests.size==expected.size)
        return requests.indices.map { index -> DynamicTest.dynamicTest("original table finite $index") {
            assertEquals(Json.parseToJsonElement(expected[index]),tableExecute(Json.parseToJsonElement(requests[index]).jsonObject))
        } }
    }
    @TestFactory fun originalDemandResponses():List<DynamicTest> = lines("demand-native.jsonl").chunked(2).map { (name,value) ->
        val probe=Json.parseToJsonElement(name).jsonPrimitive.content
        DynamicTest.dynamicTest("original table demand $probe") { assertEquals(Json.parseToJsonElement(value),tableDemandExecute(probe)) }
    }
    @TestFactory fun originalLegacyDelegation():List<DynamicTest> {
        val expected=lines("demand-native.jsonl").chunked(2).associate { (name,value) -> Json.parseToJsonElement(name).jsonPrimitive.content to Json.parseToJsonElement(value) }
        return legacyTableObservations().map { (name,probe) -> DynamicTest.dynamicTest("original table legacy $name") { assertEquals(expected.getValue(name),JsonPrimitive(probe())) } }
    }
    @Test fun sourceIntegrity() {
        val manifest=Json.parseToJsonElement(File(root,"tools/haskell-types-table-reference/source-manifest.json").readText()).jsonObject
        for((path,expected)in manifest.getValue("sha256").jsonObject) {
            val actual=MessageDigest.getInstance("SHA-256").digest(File(root,path).readBytes()).joinToString(""){"%02x".format(it)}
            assertEquals(expected.jsonPrimitive.content,actual,path)
        }
    }
}
