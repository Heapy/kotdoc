package io.heapy.kotdoc.logging

import kotlinx.serialization.json.*
import kotlin.test.*

/** Captured CLI fixtures were parsed as JSON; compare exact numeric values without losing type. */
internal fun assertLogJsonEquals(expected: JsonElement,actual: JsonElement,path: String = "$") {
    when(expected) {
        is JsonObject -> {
            assertTrue(actual is JsonObject,path);assertEquals(expected.keys,actual.keys,path)
            expected.forEach { (key,value) -> assertLogJsonEquals(value,actual.getValue(key),"$path.$key") }
        }
        is JsonArray -> {
            assertTrue(actual is JsonArray,path);assertEquals(expected.size,actual.size,path)
            expected.indices.forEach { assertLogJsonEquals(expected[it],actual[it],"$path[$it]") }
        }
        is JsonPrimitive -> if(expected!=JsonNull && !expected.isString && expected.booleanOrNull==null) {
            assertTrue(actual is JsonPrimitive && actual!=JsonNull && !actual.isString && actual.booleanOrNull==null,path)
            assertEquals(0,expected.content.toBigDecimal().compareTo(actual.jsonPrimitive.content.toBigDecimal()),path)
        } else assertEquals(expected,actual,path)
    }
}
