package io.heapy.kotdoc.crypto

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.util.stream.Stream
import java.util.zip.GZIPInputStream

class CryptoModularTest {
    private fun JsonObject.text(k: String) = getValue(k).jsonPrimitive.content
    private fun JsonObject.int(k: String) = text(k).toInt()
    private fun words(r: JsonObject, key: String, n: Int) = LongArray(n) { r[key]?.jsonArray?.getOrNull(it)?.jsonPrimitive?.content?.toULong()?.toLong() ?: 0L }
    private fun wordJSON(a: LongArray) = JsonArray(a.map { JsonPrimitive(it.toULong().toString()) })
    private fun bytes(r: JsonObject, key: String) = r[key]?.jsonArray?.map { it.jsonPrimitive.int.toByte() }?.toByteArray() ?: byteArrayOf()
    private fun byteJSON(a: ByteArray) = JsonArray(a.map { JsonPrimitive(it.toInt() and 255) })
    private fun integer(a: BigInteger) = buildJsonObject { put("integer", a.toString()) }
    private fun maybe(a: CryptoMaybe<BigInteger>, part: String) = buildJsonObject {
        when (a) {
            CryptoMaybe.None -> put("maybe", "none")
            is CryptoMaybe.Some -> { put("maybe", "some"); if (part != "constructor") put("value", a.value.toString()) }
        }
    }

    private fun observe(r: JsonObject): JsonElement {
        when (r.text("op")) {
            "limb" -> {
                val n = r.int("n"); val a = words(r, "a", 2 * n); val b = words(r, "b", 2 * n); val m = words(r, "m", n)
                val out = when (r.text("alias")) { "a" -> a; "b" -> b; else -> LongArray(2 * n) { 0xa5a5a5a5a5a5a5a5uL.toLong() } }
                val t = LongArray(2 * n) { 0xa5a5a5a5a5a5a5a5uL.toLong() }
                val scalar = r.text("scalar").toULong().toLong()
                val code = when (r.int("kind")) {
                    0 -> CryptoMontgomery.subtract(out, 0, a, 0, b, 0, n)
                    1 -> CryptoMontgomery.add(out, 0, a, 0, b, 0, n)
                    2 -> { a.copyInto(out, 0, 0, n); CryptoMontgomery.shiftLeft(out, 0, n) }
                    3 -> { CryptoMontgomery.select(out, 0, a, 0, b, 0, scalar, n); 0L }
                    4 -> CryptoMontgomery.equalityMask(scalar, a[0])
                    5 -> CryptoMontgomery.n0(scalar)
                    6 -> { b.copyInto(out, 0, 0, n); CryptoMontgomery.addMultiply(out, 0, a, 0, n, scalar) }
                    7 -> { a.copyInto(t); CryptoMontgomery.reduce(out, 0, t, 0, m, 0, CryptoMontgomery.n0(m[0]), n); 0L }
                    8 -> { CryptoMontgomery.multiply(out, 0, a, 0, b, 0, m, 0, CryptoMontgomery.n0(m[0]), n, t, 0); 0L }
                    9 -> { CryptoMontgomery.square(out, 0, a, 0, m, 0, CryptoMontgomery.n0(m[0]), n, t, 0); 0L }
                    10 -> { CryptoMontgomery.r2(out, 0, m, 0, n, t, 0); 0L }
                    else -> error("unknown primitive")
                }
                return buildJsonObject { put("ret", code.toULong().toString()); put("limbs", wordJSON(out)); put("scratch", wordJSON(t)) }
            }
            "fromBE" -> {
                val n = r.int("n"); val out = LongArray(n) { 0xa5a5a5a5a5a5a5a5uL.toLong() }
                val code = CryptoMontgomery.fromBigEndian(out, 0, n, bytes(r, "bytes"))
                return buildJsonObject { put("ret", code); put("limbs", wordJSON(out)) }
            }
            "toBE" -> {
                val out = ByteArray(r.int("length")); CryptoMontgomery.toBigEndian(out, words(r, "a", r.int("n")), 0, r.int("n"))
                return buildJsonObject { put("bytes", byteJSON(out)) }
            }
            "powRaw" -> {
                val out = ByteArray(bytes(r, "modulus").size) { 165.toByte() }; val audit = CryptoMontgomery.Audit()
                val allocate: (Int) -> LongArray = if (r["allocationFailure"]?.jsonPrimitive?.content == "1") { { throw OutOfMemoryError("controlled workspace allocation failure") } } else { { LongArray(it) } }
                val code = CryptoMontgomery.powInto(out, bytes(r, "base"), bytes(r, "exponent"), bytes(r, "modulus"), audit, allocate)
                val stats = longArrayOf(audit.multiplications, audit.squares, audit.windows, audit.tableReads, audit.tableAddressHash, if (audit.workspaceCleared) 1 else 0)
                assertEquals(audit.multiplications + audit.squares, audit.reductions)
                return buildJsonObject { put("bytes", byteJSON(out)); put("ret", code); put("stats", wordJSON(stats)) }
            }
        }
        val part = r["part"]?.jsonPrimitive?.content ?: "value"
        if (part == "ignore") return JsonPrimitive("ignored")
        fun arg(k: String): Lazy<BigInteger> = lazy {
            val v = r[k]?.jsonPrimitive?.content ?: "0"
            if (v.startsWith("bottom:")) error(v.removePrefix("bottom:"))
            v.toBigInteger()
        }
        val g = arg("g"); val e = arg("e"); val m = arg("m")
        return when (r.text("op")) {
            "expFast" -> integer(CryptoModular.expFastDeferred(g, e, m))
            "expSafe" -> integer(CryptoModular.expSafeDeferred(g, e, m))
            "inverse" -> maybe(CryptoModular.inverseDeferred(g, m), part)
            "inverseSafe" -> maybe(CryptoModular.inverseSafeDeferred(g, m), part)
            "inverseCoprimes" -> integer(CryptoModular.inverseCoprimesDeferred(g, m))
            "inverseFermat" -> integer(CryptoModular.inverseFermatDeferred(g, m))
            "jacobi" -> maybe(CryptoModular.jacobiDeferred(g, m), part)
            "squareRoot" -> maybe(CryptoModular.squareRootDeferred(m, g), part)
            "numberProperty" -> property(r.text("kind"), g.value, e.value, m.value)
            else -> error("unknown operation")
        }
    }

    private fun property(kind: String, g: BigInteger, e: BigInteger, m: BigInteger): JsonObject {
        val result = when (kind) {
            "exponentiation/agrees with the fast one", "exponentiation/agrees with the fast one on a modulus a key would have", "exponentiation/agrees with the fast one on the corners" -> CryptoModular.expSafe(g, e, m) == CryptoModular.expFast(g, e, m)
            "exponentiation/agrees with repetitive squaring on the corners" -> {
                val b = g.mod(m)
                var acc = BigInteger.ONE
                for (i in e.bitLength() - 1 downTo 0) acc = (acc * acc * if (e.testBit(i)) b else BigInteger.ONE).mod(m)
                CryptoModular.expSafe(g, e, m) == acc
            }
            "inverse/agrees with the plain inverse on the corners", "inverse/agrees with the plain inverse", "inverse/agrees with the plain inverse on a prime modulus" -> CryptoModular.inverseSafe(g, m) == CryptoModular.inverse(g, m)
            "inverse/inverts" -> {
                val reduced = g.mod(m)
                if (reduced == BigInteger.ZERO) return buildJsonObject { put("discard", true) }
                when (val value = CryptoModular.inverseSafe(reduced, m)) { CryptoMaybe.None -> false; is CryptoMaybe.Some -> (reduced * value.value).mod(m) == BigInteger.ONE }
            }
            "squareRoot" -> {
                val root = CryptoModular.squareRoot(m, g)
                when (val symbol = CryptoModular.jacobi(g, m)) {
                    CryptoMaybe.None -> error("invalid jacobi result")
                    is CryptoMaybe.Some -> when (symbol.value) {
                        BigInteger.ONE -> root is CryptoMaybe.Some && (root.value * root.value).mod(m) == g
                        -BigInteger.ONE -> root === CryptoMaybe.None
                        else -> error("invalid jacobi result")
                    }
                }
            }
            else -> error("unknown property")
        }
        return buildJsonObject { put("property", result) }
    }

    @TestFactory
    fun upstream(): Stream<DynamicTest> {
        val input = GZIPInputStream(javaClass.getResourceAsStream("/crypto-modular/data.jsonl.gz")!!).bufferedReader()
        input.readLine()
        var index = 0
        return input.lines().map { line ->
            val id = index++
            DynamicTest.dynamicTest("$id crypton modular arithmetic") {
                val row = Json.parseToJsonElement(line).jsonObject
                val actual = try { observe(row.getValue("request").jsonObject) } catch (e: RuntimeException) {
                    buildJsonObject {
                        put("fatal", e.message.orEmpty().lineSequence().first())
                        when (e) { is CoprimesAssertionError -> put("kind", "coprime"); is ModulusAssertionError -> put("kind", "modulus") }
                    }
                }
                assertEquals(row.getValue("expected"), actual, "$id ${row.text("group")} ${row.getValue("request")}")
            }
        }.onClose { input.close() }
    }

    @Test
    fun secureDispatcherRoundsExponentWidthAndClearsWorkspace() {
        val m = BigInteger.ONE.shiftLeft(512).subtract(BigInteger.valueOf(569))
        for (e in listOf(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE.shiftLeft(63), BigInteger.ONE.shiftLeft(64))) {
            val audit = CryptoMontgomery.Audit()
            val actual = CryptoModular.expSafeAudited(lazyOf(BigInteger.TWO), lazyOf(e), lazyOf(m), audit)
            assertEquals(CryptoModular.expFast(BigInteger.TWO, e, m), actual)
            assertEquals(if (e == BigInteger.ZERO) 0 else if (e.bitLength() <= 64) 16 else 32, audit.windows.toInt())
            assertTrue(audit.workspaceCleared)
            assertTrue(audit.exponentCleared)
        }
    }

    @Test
    fun workspaceAllocationFailureFallsBackAndStillClearsExponent() {
        val audit = CryptoMontgomery.Audit()
        var allocations = 0
        val value = CryptoModular.expSafeAudited(lazyOf(BigInteger.TWO), lazyOf(BigInteger.valueOf(173)), lazyOf(BigInteger.valueOf(65537)), audit) {
            allocations++
            throw OutOfMemoryError("controlled workspace allocation failure")
        }
        assertEquals(CryptoModular.expFast(BigInteger.TWO, BigInteger.valueOf(173), BigInteger.valueOf(65537)), value)
        assertEquals(1, allocations)
        assertTrue(audit.exponentCleared)
        assertFalse(audit.workspaceCleared) // allocation did not produce a workspace
        assertEquals(0L, audit.windows)
    }
}
