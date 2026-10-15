// Text.TeXMath and GHC Ratio arithmetic. Original notices in licenses/.
package io.heapy.kotdoc.math

import java.math.BigInteger

/** Exact Ratio Integer, reduced with a positive denominator. */
class MathRational(numerator: BigInteger, denominator: BigInteger = BigInteger.ONE) : Comparable<MathRational> {
    val numerator: BigInteger
    val denominator: BigInteger
    init {
        require(denominator.signum()!=0) { "Ratio has zero denominator" }
        val gcd=numerator.gcd(denominator)*BigInteger.valueOf(denominator.signum().toLong())
        this.numerator=numerator/gcd;this.denominator=denominator/gcd
    }
    constructor(numerator: Long,denominator: Long = 1):this(BigInteger.valueOf(numerator),BigInteger.valueOf(denominator))
    operator fun plus(other: MathRational)=MathRational(numerator*other.denominator+other.numerator*denominator,denominator*other.denominator)
    operator fun minus(other: MathRational)=MathRational(numerator*other.denominator-other.numerator*denominator,denominator*other.denominator)
    operator fun times(other: MathRational)=MathRational(numerator*other.numerator,denominator*other.denominator)
    override fun compareTo(other: MathRational)=(numerator*other.denominator).compareTo(other.numerator*denominator)
    override fun equals(other: Any?)=other is MathRational && numerator==other.numerator && denominator==other.denominator
    override fun hashCode()=31*numerator.hashCode()+denominator.hashCode()
    fun show(precedence: Int=0): String {
        val n=if(numerator.signum()<0) "($numerator)" else "$numerator"
        val s="$n % $denominator"
        return if(precedence>7) "($s)" else s
    }
    override fun toString()=show()
}
