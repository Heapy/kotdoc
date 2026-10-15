// GHC.Internal.Float.fromRat'' / GHC.Internal.Real Ratio round, GHC 9.12.2.
// Copyright University of Glasgow and contributors; BSD-3-Clause, licenses/.
package io.heapy.kotdoc.math

import java.math.BigInteger

/** Double specialization, retaining exact rational scaling and ties-to-even. */
object MathNumbers {
    fun toDouble(value: MathRational): Double {
        val n=value.numerator.abs();val d=value.denominator
        if(n.signum()==0) return 0.0
        val p0=maxOf(-1021,n.bitLength()-d.bitLength())
        var a=n;var b=d
        if(p0<53) a=a.shiftLeft(53-p0) else if(p0>53) b=b.shiftLeft(p0-53)
        var exponent=p0-53
        if(b.shiftLeft(53)<=a) {b=b.shiftLeft(1);exponent++}
        val (q,r)=a.divideAndRemainder(b)
        val cmp=r.shiftLeft(1).compareTo(b)
        val rounded=if(cmp>0 || cmp==0 && q.testBit(0)) q+BigInteger.ONE else q
        val result=Math.scalb(rounded.toDouble(),exponent)
        return if(value.numerator.signum()<0) -result else result
    }
    fun roundToInteger(value: MathRational): BigInteger {
        val (q,r)=value.numerator.abs().divideAndRemainder(value.denominator)
        val cmp=r.shiftLeft(1).compareTo(value.denominator)
        val rounded=if(cmp>0 || cmp==0 && q.testBit(0)) q+BigInteger.ONE else q
        return if(value.numerator.signum()<0) -rounded else rounded
    }
    fun roundToInt64(value: MathRational): Long=roundToInteger(value).toLong()
}
