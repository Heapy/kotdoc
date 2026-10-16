package io.heapy.kotdoc.ast

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals

/** Exact original QCGen seed replay; expected trees are actual source Arbitrary outputs. */
class OriginalSeededTypesArbitraryTest {
    @TestFactory fun originalPandocGenerators(): List<DynamicTest> {
        val stream = javaClass.getResourceAsStream("/pandoc-types/properties.jsonl.gz")!!
        return GZIPInputStream(stream).bufferedReader().use { it.readLines() }.map { line ->
            val r = Json.parseToJsonElement(line).jsonObject
            val seed = r.getValue("seed").jsonPrimitive.long
            val size = r.getValue("size").jsonPrimitive.int
            DynamicTest.dynamicTest("original Pandoc seed=$seed size=$size") {
                assertEquals(PandocJson.decode(r.getValue("input")),
                    OriginalSeededTypesArbitrary.pandoc().run(OriginalQuickCheck.Seed.fromInt(seed),size))
            }
        }
    }
}
