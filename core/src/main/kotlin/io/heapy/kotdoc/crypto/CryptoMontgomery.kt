// crypton 2.0.1 cbits/crypton_{bignum.h,powm.c}.
// Full upstream notice: licenses/crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto

/** Fixed-width unsigned 64-bit limbs, least significant first. */
internal object CryptoMontgomery {
    internal class Audit {
        var multiplications = 0L
        var squares = 0L
        var reductions = 0L
        var windows = 0L
        var tableReads = 0L
        var tableAddressHash = 0L
        var workspaceCleared = false
        var exponentCleared = false
    }

    private fun carry(a: Long, b: Long, sum: Long) = ((a and b) or ((a or b) and sum.inv())) ushr 63

    fun subtract(r: LongArray, ro: Int, a: LongArray, ao: Int, b: LongArray, bo: Int, n: Int): Long {
        var borrow = 0L
        for (i in 0..<n) {
            val ai = a[ao + i]; val bi = b[bo + i]
            val d = ai - bi - borrow
            borrow = ((ai.inv() and bi) or ((ai xor bi).inv() and d)) ushr 63
            r[ro + i] = d
        }
        return borrow
    }

    fun add(r: LongArray, ro: Int, a: LongArray, ao: Int, b: LongArray, bo: Int, n: Int): Long {
        var c = 0L
        for (i in 0..<n) {
            val ai = a[ao + i]; val bi = b[bo + i]
            val first = ai + bi
            val sum = first + c
            c = carry(ai, bi, first) or carry(first, c, sum)
            r[ro + i] = sum
        }
        return c
    }

    fun shiftLeft(a: LongArray, offset: Int, n: Int): Long {
        var c = 0L
        for (i in 0..<n) {
            val v = a[offset + i]
            a[offset + i] = (v shl 1) or c
            c = v ushr 63
        }
        return c
    }

    fun select(r: LongArray, ro: Int, a: LongArray, ao: Int, b: LongArray, bo: Int, take: Long, n: Int) {
        val mask = -take
        for (i in 0..<n) r[ro + i] = (a[ao + i] and mask) or (b[bo + i] and mask.inv())
    }

    fun equalityMask(a: Long, b: Long): Long {
        val d = a xor b
        return -(((d or -d) ushr 63) xor 1L)
    }

    fun n0(m0: Long): Long {
        var inverse = 1L
        repeat(6) { inverse *= 2L - m0 * inverse }
        return -inverse
    }

    fun addMultiply(t: LongArray, to: Int, a: LongArray, ao: Int, n: Int, b: Long): Long {
        var c = 0L
        for (i in 0..<n) {
            val ai = a[ao + i]
            val low = ai * b
            val high = Math.unsignedMultiplyHigh(ai, b)
            val ti = t[to + i]
            val first = low + ti
            val sum = first + c
            val next = high + carry(low, ti, first) + carry(first, c, sum)
            t[to + i] = sum
            c = next
        }
        return c
    }

    fun reduce(r: LongArray, ro: Int, t: LongArray, to: Int, m: LongArray, mo: Int, n0: Long, n: Int, audit: Audit? = null) {
        audit?.let { it.reductions++ }
        var c = 0L
        for (i in 0..<n) {
            val u = t[to + i] * n0
            val addition = addMultiply(t, to + i, m, mo, n, u)
            val ti = t[to + n + i]
            val first = ti + addition
            val sum = first + c
            c = carry(ti, addition, first) or carry(first, c, sum)
            t[to + n + i] = sum
        }
        val borrow = subtract(r, ro, t, to + n, m, mo, n)
        select(r, ro, r, ro, t, to + n, (c or (borrow xor 1L)) and 1L, n)
    }

    fun multiply(r: LongArray, ro: Int, a: LongArray, ao: Int, b: LongArray, bo: Int, m: LongArray, mo: Int, n0: Long, n: Int, t: LongArray, to: Int, audit: Audit? = null) {
        audit?.let { it.multiplications++ }
        t.fill(0, to, to + 2 * n)
        for (i in 0..<n) t[to + n + i] = addMultiply(t, to + i, a, ao, n, b[bo + i])
        reduce(r, ro, t, to, m, mo, n0, n, audit)
    }

    fun square(r: LongArray, ro: Int, a: LongArray, ao: Int, m: LongArray, mo: Int, n0: Long, n: Int, t: LongArray, to: Int, audit: Audit? = null) {
        audit?.let { it.squares++ }
        t.fill(0, to, to + 2 * n)
        for (i in 0..<n - 1) t[to + n + i] = addMultiply(t, to + i + i + 1, a, ao + i + 1, n - 1 - i, a[ao + i])
        shiftLeft(t, to, 2 * n)
        var c = 0L
        for (i in 0..<n) {
            val ai = a[ao + i]
            val low = ai * ai
            val high = Math.unsignedMultiplyHigh(ai, ai)
            val ti = t[to + i + i]
            val first = low + ti
            val sum = first + c
            val upper = high + carry(low, ti, first) + carry(first, c, sum)
            t[to + i + i] = sum
            val next = t[to + i + i + 1]
            val upperSum = next + upper
            t[to + i + i + 1] = upperSum
            c = carry(next, upper, upperSum)
        }
        reduce(r, ro, t, to, m, mo, n0, n, audit)
    }

    fun r2(r: LongArray, ro: Int, m: LongArray, mo: Int, n: Int, tmp: LongArray, to: Int) {
        var bits = 0
        for (i in n downTo 1) {
            if (m[mo + i - 1] != 0L) {
                bits = (i - 1) * 64 + (64 - java.lang.Long.numberOfLeadingZeros(m[mo + i - 1]))
                break
            }
        }
        r.fill(0, ro, ro + n)
        if (bits == 0) return
        r[ro + (bits - 1) / 64] = 1L shl ((bits - 1) % 64)
        repeat(2 * n * 64 - (bits - 1)) {
            val c = shiftLeft(r, ro, n)
            val borrow = subtract(tmp, to, r, ro, m, mo, n)
            select(r, ro, tmp, to, r, ro, (c or (borrow xor 1L)) and 1L, n)
        }
    }

    fun fromBigEndian(r: LongArray, ro: Int, n: Int, bytes: ByteArray) = fromBigEndian(r, ro, n, CryptoByteSpan(bytes))
    fun fromBigEndian(r: LongArray, ro: Int, n: Int, bytes: CryptoByteSpan): Int {
        r.fill(0, ro, ro + n)
        for (i in 0..<bytes.size) {
            val byte = bytes[bytes.size - 1 - i].toLong() and 255L
            if (i / 8 >= n) {
                if (byte != 0L) return 1
            } else r[ro + i / 8] = r[ro + i / 8] or (byte shl (8 * (i % 8)))
        }
        return 0
    }

    fun toBigEndian(dst: ByteArray, a: LongArray, ao: Int, n: Int) = toBigEndian(CryptoByteSpan(dst), a, ao, n)
    fun toBigEndian(dst: CryptoByteSpan, a: LongArray, ao: Int, n: Int) {
        for (i in 0..<dst.size) dst[dst.size - 1 - i] = if (i / 8 < n) (a[ao + i / 8] ushr (8 * (i % 8))).toByte() else 0
    }

    /** C crypton_powm_sec contract: output has modulus.size bytes, base is already reduced. */
    fun powInto(out: ByteArray, base: ByteArray, exponent: ByteArray, modulus: ByteArray, audit: Audit? = null, allocate: (Int) -> LongArray = { LongArray(it) }): Int =
        powSpans(CryptoByteSpan(out), CryptoByteSpan(base), CryptoByteSpan(exponent), CryptoByteSpan(modulus), audit, allocate)

    fun powSpans(out: CryptoByteSpan, base: CryptoByteSpan, exponent: CryptoByteSpan, modulus: CryptoByteSpan, audit: Audit? = null, allocate: (Int) -> LongArray = { LongArray(it) }): Int {
        val n = (modulus.size + 7) / 8
        if (modulus.size == 0 || n == 0 || modulus[modulus.size - 1].toInt() and 1 == 0) return 1
        require(out.size == modulus.size)
        val space = try { allocate(23 * n) } catch (_: OutOfMemoryError) { return 1 }
        val m = 0; val r2 = n; val acc = 2 * n; val sel = 3 * n; val prod = 4 * n; val t = 5 * n; val table = 7 * n
        fun clear() { space.fill(0); audit?.workspaceCleared = space.all { it == 0L } }
        if (fromBigEndian(space, m, n, modulus) != 0) { clear(); return 1 }
        r2(space, r2, space, m, n, space, t)
        val n0 = n0(space[m])
        space[table] = 1L
        multiply(space, acc, space, table, space, r2, space, m, n0, n, space, t, audit)
        space.copyInto(space, table, acc, acc + n)
        if (fromBigEndian(space, sel, n, base) != 0) { clear(); return 1 }
        multiply(space, table + n, space, sel, space, r2, space, m, n0, n, space, t, audit)
        for (k in 2..<16) multiply(space, table + k * n, space, table + (k - 1) * n, space, table + n, space, m, n0, n, space, t, audit)
        space.copyInto(space, acc, table, table + n)
        for (i in exponent.size * 2 downTo 1) {
            audit?.let { it.windows++ }
            val nibble = i - 1
            val w = (exponent[exponent.size - 1 - nibble / 2].toLong() ushr (4 * (nibble % 2))) and 15L
            repeat(4) {
                square(space, sel, space, acc, space, m, n0, n, space, t, audit)
                space.copyInto(space, acc, sel, sel + n)
            }
            space.fill(0, sel, sel + n)
            for (k in 0..<16) {
                val mask = equalityMask(k.toLong(), w)
                for (l in 0..<n) {
                    audit?.let { it.tableReads++; it.tableAddressHash = (it.tableAddressHash * 0x100000001b3L) xor (k * n + l).toLong() }
                    space[sel + l] = space[sel + l] or (space[table + k * n + l] and mask)
                }
            }
            multiply(space, prod, space, acc, space, sel, space, m, n0, n, space, t, audit)
            space.copyInto(space, acc, prod, prod + n)
        }
        space.fill(0, sel, sel + n); space[sel] = 1L
        multiply(space, prod, space, acc, space, sel, space, m, n0, n, space, t, audit)
        toBigEndian(out, space, prod, n)
        clear()
        return 0
    }
}
