// Copyright 2013 The Android Open Source Project; crypton 2.0.1 p256_ec.c.
// Full notice: licenses/crypton-p256-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto

/** Exact raw C point engine. Public Haskell infinity/validation policies are separate. */
internal class CryptoP256Points(val bits: Int) {
    private val field = CryptoP256Field(bits)
    private val words = CryptoP256Words(bits)
    val limbs = field.limbs
    private val digitMask = if (bits == 32) 0xffffffffL else -1L
    typealias Region = CryptoP256Words.Region
    class Point(val x: Region, val y: Region, val z: Region)
    class Audit(val traceLimit: Int = 0) {
        val operations = LongArray(16)
        val field = CryptoP256Field.Audit()
        val words = CryptoP256Words.Audit()
        val trace = mutableListOf<Pair<String, LongArray>>()
        var hash = 0xcbf29ce484222325uL.toLong()
        var events = 0L
        private fun byte(value: Int) { hash = (hash xor (value.toLong() and 255)) * 0x100000001b3L }
        fun event(name: String, values: LongArray) {
            name.toByteArray(Charsets.UTF_8).forEach { byte(it.toInt()) }; byte(0)
            repeat(4) { byte(values.size ushr (8 * it)) }
            for (value in values) repeat(8) { byte((value ushr (8 * it)).toInt()) }
            if (trace.size < traceLimit) trace += name to values.copyOf()
            events++
        }
        fun drain() {
            for ((name, values) in field.trace) event(name, values)
            field.trace.clear()
        }
        fun enter(index: Int) { operations[index]++ }
    }
    private fun fresh() = Region(LongArray(limbs))
    fun point() = Point(fresh(), fresh(), fresh())
    private fun one() = Region(field.constant("one"))
    private fun zero(a: Region) { a.zero(limbs) }
    private fun snap(audit: Audit?, label: String, vararg a: Region) {
        audit?.event(label, LongArray(a.size * limbs) { a[it / limbs][it % limbs] })
    }
    private fun square(out: Region, a: Region, audit: Audit?) { field.square(out, a, audit?.field); audit?.drain() }
    private fun mul(out: Region, a: Region, b: Region, audit: Audit?) { field.multiply(out, a, b, audit?.field); audit?.drain() }
    private fun sum(out: Region, a: Region, b: Region, audit: Audit?) { field.sum(out, a, b, audit?.field); audit?.drain() }
    private fun diff(out: Region, a: Region, b: Region, audit: Audit?) { field.difference(out, a, b, audit?.field); audit?.drain() }
    private fun scalar(out: Region, n: Int, audit: Audit?) { field.scalar(out, n, audit?.field); audit?.drain() }
    private fun assign(out: Region, a: Region, audit: Audit?) { field.assign(out, a, audit?.field); audit?.drain() }
    private fun isZero(a: Region, audit: Audit?): Boolean { val result = field.isZero(a, audit?.field); audit?.drain(); return result }
    private fun toMont(out: Region, a: Region, audit: Audit?) { field.toMontgomery(out, a, audit?.field); audit?.drain() }
    private fun fromMont(out: Region, a: Region, audit: Audit?) { field.fromMontgomery(out, a, audit?.field); audit?.drain() }
    private fun nonzeroMask(value: Long) = ((((value - 1) and digitMask) ushr (bits - 1)) - 1) and digitMask

    fun inverse(out: Region, a: Region, audit: Audit? = null) {
        audit?.enter(0)
        val t = fresh(); val t2 = fresh()
        val e2 = fresh(); val e4 = fresh(); val e8 = fresh(); val e16 = fresh(); val e32 = fresh(); val e64 = fresh()
        square(t, a, audit); mul(t, a, t, audit); assign(e2, t, audit)
        repeat(2) { square(t, t, audit) }; mul(t, t, e2, audit); assign(e4, t, audit)
        repeat(4) { square(t, t, audit) }; mul(t, t, e4, audit); assign(e8, t, audit)
        repeat(8) { square(t, t, audit) }; mul(t, t, e8, audit); assign(e16, t, audit)
        repeat(16) { square(t, t, audit) }; mul(t, t, e16, audit); assign(e32, t, audit)
        repeat(32) { square(t, t, audit) }; assign(e64, t, audit); mul(t, t, a, audit)
        repeat(192) { square(t, t, audit) }
        mul(t2, e64, e32, audit)
        repeat(16) { square(t2, t2, audit) }; mul(t2, t2, e16, audit)
        repeat(8) { square(t2, t2, audit) }; mul(t2, t2, e8, audit)
        repeat(4) { square(t2, t2, audit) }; mul(t2, t2, e4, audit)
        repeat(2) { square(t2, t2, audit) }; mul(t2, t2, e2, audit)
        repeat(2) { square(t2, t2, audit) }; mul(t2, t2, a, audit)
        mul(out, t2, t, audit)
        snap(audit, "inverse", out)
    }

    fun double(out: Point, a: Point, audit: Audit? = null) {
        audit?.enter(1)
        val delta = fresh(); val gamma = fresh(); val alpha = fresh(); val beta = fresh(); val tmp = fresh(); val tmp2 = fresh()
        square(delta, a.z, audit); square(gamma, a.y, audit); mul(beta, a.x, gamma, audit)
        sum(tmp, a.x, delta, audit); diff(tmp2, a.x, delta, audit); mul(alpha, tmp, tmp2, audit); scalar(alpha, 3, audit)
        sum(tmp, a.y, a.z, audit); square(tmp, tmp, audit); diff(tmp, tmp, gamma, audit); diff(out.z, tmp, delta, audit)
        scalar(beta, 4, audit); square(out.x, alpha, audit); diff(out.x, out.x, beta, audit); diff(out.x, out.x, beta, audit)
        diff(tmp, beta, out.x, audit); mul(tmp, alpha, tmp, audit); square(tmp2, gamma, audit); scalar(tmp2, 8, audit); diff(out.y, tmp, tmp2, audit)
        snap(audit, "double", out.x, out.y, out.z)
    }
    fun addMixed(out: Point, a: Point, bx: Region, by: Region, audit: Audit? = null) {
        audit?.enter(2)
        val zz = fresh(); val zzz = fresh(); val s2 = fresh(); val u2 = fresh(); val h = fresh(); val i = fresh(); val j = fresh(); val r = fresh(); val rr = fresh(); val v = fresh(); val tmp = fresh()
        square(zz, a.z, audit); sum(tmp, a.z, a.z, audit)
        mul(u2, bx, zz, audit); mul(zzz, a.z, zz, audit); mul(s2, by, zzz, audit)
        diff(h, u2, a.x, audit); sum(i, h, h, audit); square(i, i, audit); mul(j, h, i, audit)
        diff(r, s2, a.y, audit); sum(r, r, r, audit); mul(v, a.x, i, audit)
        mul(out.z, tmp, h, audit); square(rr, r, audit); diff(out.x, rr, j, audit); diff(out.x, out.x, v, audit); diff(out.x, out.x, v, audit)
        diff(tmp, v, out.x, audit); mul(out.y, tmp, r, audit); mul(tmp, a.y, j, audit); diff(out.y, out.y, tmp, audit); diff(out.y, out.y, tmp, audit)
        snap(audit, "mixed", out.x, out.y, out.z)
    }
    private fun addBody(out: Point, a: Point, b: Point, allowDouble: Boolean, audit: Audit?) {
        val zz1 = fresh(); val zzz1 = fresh(); val zz2 = fresh(); val zzz2 = fresh(); val s1 = fresh(); val s2 = fresh(); val u1 = fresh(); val u2 = fresh(); val h = fresh(); val i = fresh(); val j = fresh(); val r = fresh(); val rr = fresh(); val v = fresh(); val tmp = fresh()
        square(zz1, a.z, audit); square(zz2, b.z, audit); mul(u1, a.x, zz2, audit)
        sum(tmp, a.z, b.z, audit); square(tmp, tmp, audit); diff(tmp, tmp, zz1, audit); diff(tmp, tmp, zz2, audit)
        mul(zzz2, b.z, zz2, audit); mul(s1, a.y, zzz2, audit)
        mul(u2, b.x, zz1, audit); mul(zzz1, a.z, zz1, audit); mul(s2, b.y, zzz1, audit)
        diff(h, u2, u1, audit); val xEqual = allowDouble && isZero(h, audit)
        sum(i, h, h, audit); square(i, i, audit); mul(j, h, i, audit)
        diff(r, s2, s1, audit); val yEqual = allowDouble && isZero(r, audit)
        if (xEqual && yEqual) {
            audit?.event("add-double-branch", longArrayOf(1)); double(out, a, audit); return
        }
        if (allowDouble) audit?.event("add-double-branch", longArrayOf(0))
        sum(r, r, r, audit); mul(v, u1, i, audit)
        mul(out.z, tmp, h, audit); square(rr, r, audit); diff(out.x, rr, j, audit); diff(out.x, out.x, v, audit); diff(out.x, out.x, v, audit)
        diff(tmp, v, out.x, audit); mul(out.y, tmp, r, audit); mul(tmp, s1, j, audit); diff(out.y, out.y, tmp, audit); diff(out.y, out.y, tmp, audit)
    }
    fun add(out: Point, a: Point, b: Point, audit: Audit? = null) {
        audit?.enter(3); addBody(out, a, b, false, audit); snap(audit, "add", out.x, out.y, out.z)
    }
    fun addOrDouble(out: Point, a: Point, b: Point, audit: Audit? = null) {
        audit?.enter(4); addBody(out, a, b, true, audit); snap(audit, "add-or-double", out.x, out.y, out.z)
    }
    fun copyConditional(out: Region, a: Region, mask: Long, audit: Audit? = null) {
        audit?.enter(5)
        for (i in 0..<limbs) out[i] = (out[i] xor (mask and (a[i] xor out[i]))) and digitMask
        snap(audit, "copy", out)
    }
    fun selectAffine(outX: Region, outY: Region, table: Region, index: Long, audit: Audit? = null) {
        audit?.enter(6); zero(outX); zero(outY); var at = 0
        for (i in 1..<16) {
            var mask = i.toLong() xor index
            mask = mask or (mask ushr 2); mask = mask or (mask ushr 1); mask = ((mask and 1) - 1) and digitMask
            for (out in listOf(outX, outY)) for (j in 0..<limbs) {
                val value = table[at]; audit?.event("table-affine", longArrayOf(at.toLong(), value)); at++
                out[j] = (out[j] or (value and mask)) and digitMask
            }
        }
        snap(audit, "select-affine", outX, outY)
    }
    fun selectJacobian(out: Point, table: Region, index: Long, audit: Audit? = null) {
        audit?.enter(7); zero(out.x); zero(out.y); zero(out.z); var at = 3 * limbs
        for (i in 1..<16) {
            var mask = i.toLong() xor index
            mask = mask or (mask ushr 2); mask = mask or (mask ushr 1); mask = ((mask and 1) - 1) and digitMask
            for (coordinate in listOf(out.x, out.y, out.z)) for (j in 0..<limbs) {
                val value = table[at]; audit?.event("table-jacobian", longArrayOf(at.toLong(), value)); at++
                coordinate[j] = (coordinate[j] or (value and mask)) and digitMask
            }
        }
        snap(audit, "select-jacobian", out.x, out.y, out.z)
    }
    fun baseMult(out: Point, scalar: Region, audit: Audit? = null) {
        audit?.enter(8)
        var infinity = digitMask
        val px = fresh(); val py = fresh(); val t = point()
        zero(out.x); zero(out.y); zero(out.z)
        val table = Region(field.constant("precomputed"))
        for (i in 0..<32) {
            if (i != 0) double(out, out, audit)
            var offset = 0
            for (j in 0..32 step 32) {
                val bit0 = words.bit(scalar, 31 - i + j, audit?.words)
                val bit1 = words.bit(scalar, 95 - i + j, audit?.words)
                val bit2 = words.bit(scalar, 159 - i + j, audit?.words)
                val bit3 = words.bit(scalar, 223 - i + j, audit?.words)
                val index = bit0 or (bit1 shl 1) or (bit2 shl 2) or (bit3 shl 3)
                selectAffine(px, py, table.at(offset), index.toLong(), audit); offset += 30 * limbs
                addMixed(t, out, px, py, audit)
                copyConditional(out.x, px, infinity, audit); copyConditional(out.y, py, infinity, audit); copyConditional(out.z, one(), infinity, audit)
                val nonzero = nonzeroMask(index.toLong()); val mask = nonzero and infinity.inv()
                copyConditional(out.x, t.x, mask, audit); copyConditional(out.y, t.y, mask, audit); copyConditional(out.z, t.z, mask, audit)
                infinity = infinity and nonzero.inv()
                snap(audit, "base-window", out.x, out.y, out.z)
            }
        }
        snap(audit, "base-mult", out.x, out.y, out.z)
    }
    fun toAffine(outX: Region, outY: Region, a: Point, audit: Audit? = null) {
        audit?.enter(9)
        val inverse = fresh(); val squared = fresh()
        inverse(inverse, a.z, audit); square(squared, inverse, audit); mul(outX, a.x, squared, audit)
        mul(inverse, inverse, squared, audit); mul(outY, a.y, inverse, audit)
        snap(audit, "affine", outX, outY)
    }
    fun scalarMult(out: Point, x: Region, y: Region, scalar: Region, audit: Audit? = null) {
        audit?.enter(10)
        val p = point(); val t = point(); val precomp = Region(LongArray(16 * 3 * limbs))
        fun entry(i: Int) = Point(precomp.at(i * 3 * limbs), precomp.at((i * 3 + 1) * limbs), precomp.at((i * 3 + 2) * limbs))
        entry(1).x.copyFrom(x, limbs); entry(1).y.copyFrom(y, limbs); entry(1).z.copyFrom(one(), limbs)
        for (i in 2..<16 step 2) {
            double(entry(i), entry(i / 2), audit); addMixed(entry(i + 1), entry(i), x, y, audit)
        }
        audit?.event("precomp", precomp.data.copyOf())
        zero(out.x); zero(out.y); zero(out.z); var infinity = digitMask
        for (i in 0..<256 step 4) {
            if (i != 0) repeat(4) { double(out, out, audit) }
            val index = (words.bit(scalar, 255 - i, audit?.words) shl 3) or (words.bit(scalar, 254 - i, audit?.words) shl 2) or (words.bit(scalar, 253 - i, audit?.words) shl 1) or words.bit(scalar, 252 - i, audit?.words)
            selectJacobian(p, precomp, index.toLong(), audit); add(t, out, p, audit)
            copyConditional(out.x, p.x, infinity, audit); copyConditional(out.y, p.y, infinity, audit); copyConditional(out.z, p.z, infinity, audit)
            val nonzero = nonzeroMask(index.toLong()); val mask = nonzero and infinity.inv()
            copyConditional(out.x, t.x, mask, audit); copyConditional(out.y, t.y, mask, audit); copyConditional(out.z, t.z, mask, audit)
            infinity = infinity and nonzero.inv()
            snap(audit, "scalar-window", out.x, out.y, out.z)
        }
        snap(audit, "scalar-mult", out.x, out.y, out.z)
    }
    fun basePointMultiply(scalar: Region, outX: Region, outY: Region, audit: Audit? = null) {
        audit?.enter(11); val p = point(); val x = fresh(); val y = fresh()
        baseMult(p, scalar, audit); toAffine(x, y, p, audit); fromMont(outX, x, audit); fromMont(outY, y, audit)
    }
    fun pointsMultiplyVariable(s1: Region, s2: Region, x: Region, y: Region, outX: Region, outY: Region, audit: Audit? = null) {
        audit?.enter(12)
        if (words.zero(s1, audit?.words) && words.zero(s2, audit?.words)) {
            words.clear(outX, audit?.words); words.clear(outY, audit?.words); audit?.event("both-zero", longArrayOf()); return
        }
        val a = point(); val b = point(); val px = fresh(); val py = fresh()
        toMont(px, x, audit); toMont(py, y, audit); baseMult(a, s1, audit); scalarMult(b, px, py, s2, audit)
        if (words.zero(s2, audit?.words)) audit?.event("double-scalar-branch", longArrayOf(0))
        else if (words.zero(s1, audit?.words)) {
            audit?.event("double-scalar-branch", longArrayOf(1))
            a.x.copyFrom(b.x, limbs); a.y.copyFrom(b.y, limbs); a.z.copyFrom(b.z, limbs)
        } else {
            audit?.event("double-scalar-branch", longArrayOf(2)); addOrDouble(a, a, b, audit)
        }
        toAffine(px, py, a, audit); fromMont(outX, px, audit); fromMont(outY, py, audit)
    }
    fun pointAdd(x1: Region, y1: Region, x2: Region, y2: Region, outX: Region, outY: Region, audit: Audit? = null) {
        audit?.enter(13)
        val p = point(); val ax = fresh(); val ay = fresh(); val bx = fresh(); val by = fresh()
        toMont(ax, x1, audit); toMont(ay, y1, audit); toMont(bx, x2, audit); toMont(by, y2, audit)
        addOrDouble(p, Point(ax, ay, one()), Point(bx, by, one()), audit)
        toAffine(ax, ay, p, audit); fromMont(outX, ax, audit); fromMont(outY, ay, audit)
    }
    fun pointNegate(x: Region, y: Region, outX: Region, outY: Region, audit: Audit? = null) {
        audit?.enter(14); outX.copyFrom(x, words.digits); words.sub(words.p, y, outY, audit?.words)
    }
    fun pointMultiply(scalar: Region, x: Region, y: Region, outX: Region, outY: Region, audit: Audit? = null) {
        audit?.enter(15)
        val p = point(); val px = fresh(); val py = fresh()
        toMont(px, x, audit); toMont(py, y, audit); scalarMult(p, px, py, scalar, audit)
        toAffine(px, py, p, audit); fromMont(outX, px, audit); fromMont(outY, py, audit)
    }
}
