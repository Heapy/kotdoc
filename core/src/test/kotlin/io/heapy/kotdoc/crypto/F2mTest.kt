package io.heapy.kotdoc.crypto

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.util.stream.Stream
import java.util.zip.GZIPInputStream

class F2mTest {
    private val zero = BigInteger.ZERO
    private val one = BigInteger.ONE
    private fun n(x: Int) = BigInteger.valueOf(x.toLong())
    private fun JsonObject.text(k: String) = getValue(k).jsonPrimitive.content
    private fun integer(value: BigInteger) = buildJsonObject { put("integer", value.toString()) }
    private fun maybe(value: F2mMaybe, part: String) = buildJsonObject {
        when (value) {
            F2mMaybe.None -> put("maybe", "none")
            is F2mMaybe.Some -> { put("maybe", "some"); if (part != "constructor") put("value", value.value.toString()) }
        }
    }
    private fun observe(r: JsonObject): JsonElement {
        val part = r["part"]?.jsonPrimitive?.content ?: "value"
        if (part == "ignore") return JsonPrimitive("ignored")
        fun arg(key: String): Lazy<BigInteger> = lazy {
            val text = r[key]?.jsonPrimitive?.content ?: "0"
            if (text.startsWith("bottom:")) error(text.removePrefix("bottom:"))
            text.toBigInteger()
        }
        val f = arg("f"); val a = arg("a"); val b = arg("b")
        return when (r.text("op")) {
            "add" -> integer(F2m.addF2mDeferred(a, b))
            "mod" -> integer(F2m.modF2mDeferred(f, a))
            "mul" -> integer(F2m.mulF2mDeferred(f, a, b))
            "rawSquare" -> integer(F2m.squareF2mRaw(a.value))
            "square" -> integer(F2m.squareF2mDeferred(f, a))
            "pow" -> integer(F2m.powF2mDeferred(f, a, b))
            "sqrt" -> integer(F2m.sqrtF2mDeferred(f, a))
            "inv" -> maybe(F2m.invF2mDeferred(f, a), part)
            "div" -> maybe(F2m.divF2mDeferred(f, a, b), part)
            "quadratic" -> maybe(F2m.quadraticF2mDeferred(f, a), part)
            "property" -> buildJsonObject { put("property", property(r.text("index").toInt(), arg("m").value, a.value, b.value, arg("c").value)) }
            else -> error("unknown operation")
        }
    }

    // All 29 active upstream properties, with inputs and independent outcomes
    // evaluated by mechanically extracted, unchanged Haskell expressions.
    private fun property(index: Int, m: BigInteger, a: BigInteger, b: BigInteger, c: BigInteger): Boolean {
        fun add(a: BigInteger, b: BigInteger) = F2m.addF2m(a, b)
        fun mod(a: BigInteger) = F2m.modF2m(m, a)
        fun mul(a: BigInteger, b: BigInteger) = F2m.mulF2m(m, a, b)
        fun pow(a: BigInteger, b: BigInteger) = F2m.powF2m(m, a, b)
        fun inv(a: BigInteger) = F2m.invF2m(m, a)
        fun div(a: BigInteger, b: BigInteger) = F2m.divF2m(m, a, b)
        fun some(a: BigInteger) = F2mMaybe.Some(lazyOf(a))
        fun map(value: F2mMaybe, f: (BigInteger) -> BigInteger): F2mMaybe = when (value) {
            F2mMaybe.None -> F2mMaybe.None
            is F2mMaybe.Some -> F2mMaybe.Some(lazy { f(value.value) })
        }
        return when (index) {
            0 -> add(a, b) == add(b, a)
            1 -> add(add(a, b), c) == add(a, add(b, c))
            2 -> add(a, zero) == a
            3 -> add(a, a) == zero
            4 -> add(a, b).and(b) == add(a.and(b), b)
            5 -> mod(a) == mod(mod(a))
            6 -> mod(a) < one.shiftLeft(m.bitLength() - 1)
            7 -> { val upper = one.shiftLeft(m.bitLength() - 1) - one; mod(add(m, upper)) == upper }
            8 -> mod(a) >= zero
            9 -> mod(m) == zero
            10 -> add(mod(a), mod(b)) == mod(add(a, b))
            11 -> mul(a, b) == mul(b, a)
            12 -> mul(mul(a, b), c) == mul(a, mul(b, c))
            13 -> mul(a, one) == mod(a)
            14 -> mul(a, zero) == zero
            15 -> mul(a, add(b, c)) == add(mul(a, b), mul(a, c))
            16 -> mul(a, a) == F2m.squareF2m(m, a)
            17 -> (0..65535).all { x -> F2m.squareF2m(n(65581), F2m.sqrtF2m(n(65581), n(x))) == n(x) }
            18 -> pow(a, n(2)) == F2m.squareF2m(m, a)
            19 -> pow(a, one) == mod(a)
            20 -> pow(a, zero) == mod(one)
            21 -> pow(mul(a, b), c) == mul(pow(a, c), pow(b, c))
            22 -> pow(a, b + c) == mul(pow(a, b), pow(a, c))
            23 -> pow(a, b * c) == pow(pow(a, b), c)
            24 -> when (val inverse = inv(a)) { F2mMaybe.None -> true; is F2mMaybe.Some -> mul(inverse.value, a) == mod(one) }
            25 -> a < n(2) || F2m.invF2m(add(F2m.squareF2mRaw(a), one), a) == some(a)
            26 -> div(one, a) == inv(a)
            27 -> div(a, b) == map(inv(b)) { mul(a, it) }
            28 -> inv(b) === F2mMaybe.None || div(mul(a, b), b) == some(mod(a))
            else -> error("unknown property")
        }
    }

    @TestFactory
    fun upstream(): Stream<DynamicTest> {
        val input = GZIPInputStream(javaClass.getResourceAsStream("/crypto-f2m/data.jsonl.gz")!!).bufferedReader()
        input.readLine() // provenance checked independently by import-f2m-fixtures.py
        var index = 0
        return input.lines().map { line ->
            val id = index++
            DynamicTest.dynamicTest("$id crypton F2m") {
                val row = Json.parseToJsonElement(line).jsonObject
                val request = row.getValue("request").jsonObject
                val actual = try { observe(request) } catch (e: RuntimeException) {
                    buildJsonObject { put("fatal", e.message.orEmpty().lineSequence().first()) }
                }
                assertEquals(row.getValue("expected"), actual, "$id ${row.text("group")} $request")
                if (row.text("group") == "original-exhaustive-GF16-square-roots") {
                    val root = actual.jsonObject.text("integer").toBigInteger()
                    assertEquals(request.text("a").toBigInteger(), F2m.squareF2m(n(65581), root))
                }
                if (request.text("op") == "property") assertTrue(actual.jsonObject.getValue("property").jsonPrimitive.boolean)
            }
        }.onClose { input.close() }
    }
}
