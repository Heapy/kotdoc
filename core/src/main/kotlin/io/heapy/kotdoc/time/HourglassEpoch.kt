// Time.Epoch, time-hourglass 0.3.0; Vincent Hanquez 2014.
// Complete notice: licenses/time-hourglass-BSD-3-Clause.txt.
package io.heapy.kotdoc.time

import java.math.BigInteger

interface HourglassEpoch {
    val name: String
    val diffToUnix: HourglassSeconds
}
data object HourglassUnixEpoch : HourglassEpoch { override val name = "unix"; override val diffToUnix = HourglassSeconds(0); override fun toString() = "UnixEpoch" }
data object HourglassWindowsEpoch : HourglassEpoch { override val name = "windows"; override val diffToUnix = HourglassSeconds(-11_644_473_600); override fun toString() = "WindowsEpoch" }
data object HourglassMJDEpoch : HourglassEpoch { override val name = "Modified Julian Date"; override val diffToUnix = HourglassSeconds(-3_506_716_800); override fun toString() = "MJDEpoch" }
class HourglassElapsedSince<E : HourglassEpoch>(val epoch: E, val seconds: HourglassSeconds) : Comparable<HourglassElapsedSince<E>> {
    override fun equals(other: Any?) = other is HourglassElapsedSince<*> && epoch::class == other.epoch::class && seconds == other.seconds
    override fun hashCode() = seconds.hashCode()
    override fun compareTo(other: HourglassElapsedSince<E>) = seconds.compareTo(other.seconds)
    override fun toString() = "ElapsedSince $seconds"
    operator fun plus(other: HourglassElapsedSince<E>) = HourglassElapsedSince(epoch, HourglassSeconds(seconds.value + other.seconds.value))
    operator fun minus(other: HourglassElapsedSince<E>) = HourglassElapsedSince(epoch, HourglassSeconds(seconds.value - other.seconds.value))
    operator fun times(other: HourglassElapsedSince<E>) = HourglassElapsedSince(epoch, HourglassSeconds(seconds.value * other.seconds.value))
    operator fun unaryMinus() = HourglassElapsedSince(epoch, HourglassSeconds(-seconds.value))
    fun absolute() = HourglassElapsedSince(epoch, HourglassSeconds(kotlin.math.abs(seconds.value)))
    fun signum() = HourglassElapsedSince(epoch, HourglassSeconds(seconds.value.compareTo(0).toLong()))
    fun <F : HourglassEpoch> convert(target: F) = HourglassElapsedSince(target, HourglassSeconds(seconds.value + (epoch.diffToUnix.value - target.diffToUnix.value)))
}
data class HourglassElapsedSinceP<E : HourglassEpoch>(val elapsed: HourglassElapsedSince<E>, val nanoSeconds: HourglassNanoSeconds) : Comparable<HourglassElapsedSinceP<E>> {
    override fun compareTo(other: HourglassElapsedSinceP<E>) = compareValuesBy(this, other, { it.elapsed }, { it.nanoSeconds })
    override fun toString() = "ElapsedSinceP ($elapsed) $nanoSeconds"
    operator fun plus(other: HourglassElapsedSinceP<E>) = HourglassElapsedSinceP(elapsed + other.elapsed, HourglassNanoSeconds(nanoSeconds.value + other.nanoSeconds.value))
    operator fun minus(other: HourglassElapsedSinceP<E>) = HourglassElapsedSinceP(elapsed - other.elapsed, HourglassNanoSeconds(nanoSeconds.value - other.nanoSeconds.value))
    operator fun times(other: HourglassElapsedSinceP<E>) = HourglassElapsedSinceP(elapsed * other.elapsed, HourglassNanoSeconds(nanoSeconds.value * other.nanoSeconds.value))
    operator fun unaryMinus() = HourglassElapsedSinceP(-elapsed, nanoSeconds)
    fun absolute() = HourglassElapsedSinceP(elapsed.absolute(), nanoSeconds)
    fun signum() = HourglassElapsedSinceP(elapsed.signum(), nanoSeconds)
    fun toRational() = HourglassElapsedP(HourglassElapsed(elapsed.seconds), nanoSeconds).toRational()
    fun <F : HourglassEpoch> convert(target: F) = HourglassElapsedSinceP(elapsed.convert(target), nanoSeconds)
    companion object {
        fun <E : HourglassEpoch> fromInteger(epoch: E, value: BigInteger) = HourglassElapsedSinceP(HourglassElapsedSince(epoch, HourglassSeconds(value.toLong())), HourglassNanoSeconds(0))
    }
}
