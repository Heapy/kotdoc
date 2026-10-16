// Time.Types / Time.Diff, time-hourglass 0.3.0; Vincent Hanquez 2014.
// Complete notice: licenses/time-hourglass-BSD-3-Clause.txt.
package io.heapy.kotdoc.time

import java.math.BigInteger

enum class HourglassWeekDay { Sunday, Monday, Tuesday, Wednesday, Thursday, Friday, Saturday }

class HourglassRational(numerator: BigInteger, denominator: BigInteger) : Comparable<HourglassRational> {
    val numerator: BigInteger
    val denominator: BigInteger
    init {
        if (denominator.signum() == 0) error("Ratio has zero denominator")
        val gcd = numerator.gcd(denominator) * BigInteger.valueOf(denominator.signum().toLong())
        this.numerator = numerator / gcd; this.denominator = denominator / gcd
    }
    override fun equals(other: Any?) = other is HourglassRational && numerator == other.numerator && denominator == other.denominator
    override fun hashCode() = 31 * numerator.hashCode() + denominator.hashCode()
    override fun compareTo(other: HourglassRational) = (numerator * other.denominator).compareTo(other.numerator * denominator)
    override fun toString() = "$numerator % $denominator"
}

@JvmInline value class HourglassElapsed(val seconds: HourglassSeconds) : Comparable<HourglassElapsed> {
    override fun compareTo(other: HourglassElapsed) = seconds.compareTo(other.seconds)
    override fun toString() = seconds.toString()
    operator fun plus(other: HourglassElapsed) = HourglassElapsed(HourglassSeconds(seconds.value + other.seconds.value))
    operator fun minus(other: HourglassElapsed) = HourglassElapsed(HourglassSeconds(seconds.value - other.seconds.value))
    operator fun times(other: HourglassElapsed) = HourglassElapsed(HourglassSeconds(seconds.value * other.seconds.value))
    operator fun unaryMinus() = HourglassElapsed(HourglassSeconds(-seconds.value))
    fun absolute() = HourglassElapsed(HourglassSeconds(kotlin.math.abs(seconds.value)))
    fun signum() = HourglassElapsed(HourglassSeconds(seconds.value.compareTo(0).toLong()))
}

data class HourglassElapsedP(val elapsed: HourglassElapsed, val nanoSeconds: HourglassNanoSeconds) : Comparable<HourglassElapsedP> {
    override fun compareTo(other: HourglassElapsedP) = compareValuesBy(this, other, HourglassElapsedP::elapsed, HourglassElapsedP::nanoSeconds)
    override fun toString() = "$elapsed.$nanoSeconds"
    operator fun plus(other: HourglassElapsedP): HourglassElapsedP {
        val ns = nanoSeconds.value + other.nanoSeconds.value
        val retained = Math.floorDiv(ns, 1_000_000_000L)
        return HourglassElapsedP(HourglassElapsed(HourglassSeconds(elapsed.seconds.value + other.elapsed.seconds.value + retained)), HourglassNanoSeconds(Math.floorMod(ns, 1_000_000_000L)))
    }
    operator fun minus(other: HourglassElapsedP): HourglassElapsedP {
        val ns = nanoSeconds.value - other.nanoSeconds.value
        val seconds = elapsed.seconds.value - other.elapsed.seconds.value
        return if (ns < 0) HourglassElapsedP(HourglassElapsed(HourglassSeconds(seconds - 1)), HourglassNanoSeconds(1_000_000_000 + ns))
        else HourglassElapsedP(HourglassElapsed(HourglassSeconds(seconds)), HourglassNanoSeconds(ns))
    }
    operator fun times(other: HourglassElapsedP) = HourglassElapsedP(elapsed * other.elapsed, HourglassNanoSeconds(nanoSeconds.value * other.nanoSeconds.value))
    operator fun unaryMinus() = HourglassElapsedP(-elapsed, nanoSeconds)
    fun absolute() = HourglassElapsedP(elapsed.absolute(), nanoSeconds)
    fun signum() = HourglassElapsedP(elapsed.signum(), nanoSeconds)
    fun toRational() = HourglassRational(BigInteger.valueOf(elapsed.seconds.value) * BILLION + BigInteger.valueOf(nanoSeconds.value), BILLION)
    companion object {
        private val BILLION = BigInteger.valueOf(1_000_000_000)
        fun normalized(seconds: HourglassSeconds, nanos: HourglassNanoSeconds): HourglassElapsedP = HourglassElapsedP(
            HourglassElapsed(HourglassSeconds(seconds.value + Math.floorDiv(nanos.value, 1_000_000_000L))), HourglassNanoSeconds(Math.floorMod(nanos.value, 1_000_000_000L)))
        fun fromInteger(value: BigInteger) = HourglassElapsedP(HourglassElapsed(HourglassSeconds(value.toLong())), HourglassNanoSeconds(0))
        fun fromRationalSeconds(value: HourglassRational): Pair<HourglassSeconds, HourglassNanoSeconds> {
            val (whole, remainder) = value.numerator.divideAndRemainder(value.denominator)
            return HourglassSeconds(whole.toLong()) to HourglassNanoSeconds((remainder * BILLION / value.denominator).toLong())
        }
        fun fromRational(value: HourglassRational): HourglassElapsedP {
            val (seconds, nanos) = fromRationalSeconds(value)
            return normalized(seconds, nanos)
        }
    }
}

data class HourglassPeriod(val years: Long, val months: Long, val days: Long) : Comparable<HourglassPeriod> {
    override fun compareTo(other: HourglassPeriod) = compareValuesBy(this, other, HourglassPeriod::years, HourglassPeriod::months, HourglassPeriod::days)
    override fun toString() = "Period {periodYears = $years, periodMonths = $months, periodDays = $days}"
    operator fun plus(other: HourglassPeriod) = HourglassPeriod(years + other.years, months + other.months, days + other.days)
    companion object { val ZERO = HourglassPeriod(0, 0, 0) }
}
data class HourglassDuration(val hours: HourglassHours, val minutes: HourglassMinutes, val seconds: HourglassSeconds, val nanoSeconds: HourglassNanoSeconds) : Comparable<HourglassDuration> {
    override fun compareTo(other: HourglassDuration) = compareValuesBy(this, other, HourglassDuration::hours, HourglassDuration::minutes, HourglassDuration::seconds, HourglassDuration::nanoSeconds)
    override fun toString() = "Duration {durationHours = $hours, durationMinutes = $minutes, durationSeconds = $seconds, durationNs = $nanoSeconds}"
    operator fun plus(other: HourglassDuration) = HourglassDuration(HourglassHours(hours.value + other.hours.value), HourglassMinutes(minutes.value + other.minutes.value), HourglassSeconds(seconds.value + other.seconds.value), HourglassNanoSeconds(nanoSeconds.value + other.nanoSeconds.value))
    fun flatten(): Pair<HourglassSeconds, HourglassNanoSeconds> = HourglassSeconds(hours.value * 3600 + minutes.value * 60 + seconds.value + Math.floorDiv(nanoSeconds.value, 1_000_000_000L)) to HourglassNanoSeconds(Math.floorMod(nanoSeconds.value, 1_000_000_000L))
    fun normalized(): HourglassDuration {
        val seconds = this.seconds.value + Math.floorDiv(nanoSeconds.value, 1_000_000_000L)
        val minutes = this.minutes.value + Math.floorDiv(seconds, 60L)
        return HourglassDuration(HourglassHours(hours.value + Math.floorDiv(minutes, 60L)), HourglassMinutes(Math.floorMod(minutes, 60L)), HourglassSeconds(Math.floorMod(seconds, 60L)), HourglassNanoSeconds(Math.floorMod(nanoSeconds.value, 1_000_000_000L)))
    }
    companion object { val ZERO = HourglassDuration(HourglassHours(0), HourglassMinutes(0), HourglassSeconds(0), HourglassNanoSeconds(0)) }
}

interface HourglassTimeInterval<A> {
    fun toSeconds(value: A): HourglassSeconds
    fun fromSeconds(value: HourglassSeconds): Pair<A, HourglassSeconds>
}
object HourglassIntervals {
    val seconds = object : HourglassTimeInterval<HourglassSeconds> {
        override fun toSeconds(value: HourglassSeconds) = value
        override fun fromSeconds(value: HourglassSeconds) = value to HourglassSeconds(0)
    }
    val nanoSeconds = object : HourglassTimeInterval<HourglassNanoSeconds> {
        override fun toSeconds(value: HourglassNanoSeconds) = HourglassSeconds(Math.floorDiv(value.value, 1_000_000_000L))
        override fun fromSeconds(value: HourglassSeconds) = HourglassNanoSeconds(value.value * 1_000_000_000) to HourglassSeconds(0)
    }
    val minutes = object : HourglassTimeInterval<HourglassMinutes> {
        override fun toSeconds(value: HourglassMinutes) = HourglassSeconds(value.value * 60)
        override fun fromSeconds(value: HourglassSeconds) = HourglassMinutes(Math.floorDiv(value.value, 60L)) to HourglassSeconds(Math.floorMod(value.value, 60L))
    }
    val hours = object : HourglassTimeInterval<HourglassHours> {
        override fun toSeconds(value: HourglassHours) = HourglassSeconds(value.value * 3600)
        override fun fromSeconds(value: HourglassSeconds) = HourglassHours(Math.floorDiv(value.value, 3600L)) to HourglassSeconds(Math.floorMod(value.value, 3600L))
    }
    val duration = object : HourglassTimeInterval<HourglassDuration> {
        override fun toSeconds(value: HourglassDuration) = value.flatten().first
        override fun fromSeconds(value: HourglassSeconds) = HourglassDuration(HourglassHours(0), HourglassMinutes(0), value, HourglassNanoSeconds(0)).normalized() to HourglassSeconds(0)
    }
}
