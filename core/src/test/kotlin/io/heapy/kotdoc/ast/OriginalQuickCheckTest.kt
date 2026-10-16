package io.heapy.kotdoc.ast

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals

class OriginalQuickCheckTest {
    private fun state(x: OriginalQuickCheck.Seed) = JsonArray(listOf(x.state,x.gamma).map { Json.parseToJsonElement(it.toULong().toString()) })
    @TestFactory fun originalSeededOperations(): List<DynamicTest> {
        val stream = javaClass.getResourceAsStream("/pandoc-types/quickcheck.jsonl.gz")!!
        return GZIPInputStream(stream).bufferedReader().use { it.readLines() }.map { line ->
            val r = Json.parseToJsonElement(line).jsonObject
            val seed = r.getValue("seed").jsonPrimitive.long
            val size = r.getValue("size").jsonPrimitive.int
            DynamicTest.dynamicTest("seed=$seed size=$size") {
                val root = OriginalQuickCheck.Seed.fromInt(seed)
                val (left,right) = root.split()
                val q = OriginalQuickCheck.chooseInt(-7,259)
                fun <A> run(g: OriginalQuickCheck.Gen<A>) = g.run(root,size)
                fun number(name: String, x: Number) = assertEquals(r.getValue(name), Json.parseToJsonElement(x.toString()),name)
                fun numbers(name: String, xs: List<Int>) = assertEquals(r.getValue(name),JsonArray(xs.map { JsonPrimitive(it) }),name)
                assertEquals(r.getValue("state"),state(root)); assertEquals(r.getValue("left"),state(left)); assertEquals(r.getValue("right"),state(right))
                number("chooseInt",run(q)); number("choose",run(OriginalQuickCheck.choose(-7,259)))
                number("map",run(q.map { it+10 }))
                numbers("bind",run(q.bind { a -> q.bind { b -> OriginalQuickCheck.pure(listOf(a,b)) } }))
                numbers("apply",run(OriginalQuickCheck.zip(q,q) { a,b -> listOf(a,b) }))
                numbers("vector",run(OriginalQuickCheck.vector(10,q)))
                numbers("list",run(OriginalQuickCheck.list(q))); numbers("list1",run(OriginalQuickCheck.list(q,true)))
                number("integer",run(OriginalQuickCheck.integral()))
                number("positive",run(OriginalQuickCheck.suchThat(OriginalQuickCheck.integral()) { it>0 }))
            }
        }
    }
}
