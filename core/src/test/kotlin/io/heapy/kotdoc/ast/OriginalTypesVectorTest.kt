package io.heapy.kotdoc.ast

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.*

class OriginalTypesVectorTest {
    private val q=OriginalQuickCheck.chooseInt(-7,259)
    private fun records(name: String): List<JsonObject> = GZIPInputStream(
        javaClass.getResourceAsStream("/pandoc-types/$name.jsonl.gz")!!
    ).bufferedReader().use { it.readLines() }.map { Json.parseToJsonElement(it).jsonObject }
    @TestFactory fun originalVectorValues(): List<DynamicTest> = records("vector").map { r ->
        DynamicTest.dynamicTest("vector count=${r["count"]} seed=${r["seed"]} size=${r["size"]}") {
            val count=r.getValue("count").jsonPrimitive.int
            val seed=r.getValue("seed").jsonPrimitive.long
            val size=r.getValue("size").jsonPrimitive.int
            val actual=OriginalQuickCheck.vector(count,q).run(OriginalQuickCheck.Seed.fromInt(seed),size)
            assertEquals(r.getValue("length").jsonPrimitive.int,actual.size)
            val positions=r.getValue("positions").jsonArray.map { it.jsonPrimitive.int }
            assertEquals(r.getValue("expected").jsonArray.map { it.jsonPrimitive.int },positions.map { actual[it] })
            if(r.getValue("complete").jsonPrimitive.boolean) assertEquals(r.getValue("all").jsonArray.map { it.jsonPrimitive.int },actual)
        }
    }
    @Test fun nativeDemandAndMemoization() {
        val seed=OriginalQuickCheck.Seed.fromInt(611)
        val stages=mutableListOf<Triple<String,Int?,Int>>()
        var calls=0
        val xs=OriginalQuickCheck.vector(100000,OriginalQuickCheck.Gen { r,n -> calls++; q.run(r,n) }).run(seed,30)
        fun stage(name: String, f:()->Int) { stages+=Triple(name,runCatching(f).getOrNull(),calls) }
        stage("length-before-elements") { xs.size }
        stage("last-element") { xs[99999] }
        stage("same-last-element") { xs[99999] }
        stage("first-element") { xs[0] }
        calls=0
        val failure=IllegalStateException("original vector element failure")
        val ys=OriginalQuickCheck.vector<Int>(100000,OriginalQuickCheck.Gen { _,_ -> calls++; throw failure }).run(seed,30)
        stage("failure-spine-length") { ys.size }
        stage("first-failure") { ys[0] }
        stage("same-first-failure") { ys[0] }
        assertSame(failure,assertFails { ys[0] })
        calls=0
        val zs=OriginalQuickCheck.vector<Int?>(100000,OriginalQuickCheck.Gen { _,_ -> calls++; null }).run(seed,30)
        stage("nullable-length") { zs.size }
        stage("nullable-head") { zs[0] ?: 7 }
        stage("same-nullable-head") { zs[0] ?: 7 }
        val expected=records("vector-demand").map { Triple(it.getValue("name").jsonPrimitive.content,it.getValue("value").jsonPrimitive.intOrNull,it.getValue("calls").jsonPrimitive.int) }
        assertEquals(expected,stages)
    }
    @Test fun vectorBoundsAndRunIsolation() {
        var calls=0
        val gen=OriginalQuickCheck.vector(Int.MAX_VALUE,OriginalQuickCheck.Gen { _,_ -> ++calls })
        val seed=OriginalQuickCheck.Seed.fromInt(611)
        val first=gen.run(seed,0); val second=gen.run(seed,0)
        assertEquals(Int.MAX_VALUE,first.size); assertEquals(0,calls)
        assertFailsWith<IndexOutOfBoundsException> { first[-1] }
        assertFailsWith<IndexOutOfBoundsException> { first[Int.MAX_VALUE] }
        assertEquals(0,calls)
        assertEquals(1,first[0]); assertEquals(1,first[0]); assertEquals(2,second[0])
        assertEquals(2,calls)
    }
}
