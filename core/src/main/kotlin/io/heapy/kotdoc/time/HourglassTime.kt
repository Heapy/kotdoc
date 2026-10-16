// Time.Time / Time.LocalTime, time-hourglass 0.3.0; Vincent Hanquez 2014.
// Complete notice: licenses/time-hourglass-BSD-3-Clause.txt.
package io.heapy.kotdoc.time

@JvmInline value class HourglassCTime(val value: Long) : Comparable<HourglassCTime> {
    override fun compareTo(other: HourglassCTime) = value.compareTo(other.value)
    override fun toString() = value.toString()
}
interface HourglassTimeable<A> {
    fun getElapsedP(value: A): HourglassElapsedP
    fun getElapsed(value: A): HourglassElapsed = getElapsedP(value).elapsed
    fun getNanoSeconds(value: A): HourglassNanoSeconds = getElapsedP(value).nanoSeconds
    fun getDateTime(value: A): HourglassDateTime
    fun getDate(value: A): HourglassDate = getDateTime(value).date
    // The pinned GHC build applies timeGetTimeOfDay/Date polymorphically:
    // its `const` RHS does not constrain the input type. DateTime has a
    // separate monomorphic rewrite, represented by the codec override below.
    fun getTimeOfDay(value: A): HourglassTimeOfDay = HourglassTimeCodecs.ZERO_TIME
}
interface HourglassTime<A> : HourglassTimeable<A> {
    fun fromElapsedP(value: HourglassElapsedP): A
    fun fromElapsed(value: HourglassElapsed): A = fromElapsedP(HourglassElapsedP(value, HourglassNanoSeconds(0)))
}
class HourglassTimeCodecs(private val native: () -> HourglassNativeBackend = { HourglassNativeTime.system }) {
    private abstract inner class Codec<A> : HourglassTime<A> {
        override fun getDateTime(value: A) = native().dateTimeFromUnixEpochP(getElapsedP(value))
    }
    val cTime: HourglassTime<HourglassCTime> = object : Codec<HourglassCTime>() {
        override fun getElapsedP(value: HourglassCTime) = HourglassElapsedP(getElapsed(value), HourglassNanoSeconds(0))
        override fun getElapsed(value: HourglassCTime) = HourglassElapsed(HourglassSeconds(value.value))
        override fun getNanoSeconds(value: HourglassCTime) = HourglassNanoSeconds(0)
        override fun fromElapsedP(value: HourglassElapsedP) = HourglassCTime(value.elapsed.seconds.value)
    }
    val elapsed: HourglassTime<HourglassElapsed> = object : Codec<HourglassElapsed>() {
        override fun getElapsedP(value: HourglassElapsed) = HourglassElapsedP(value, HourglassNanoSeconds(0))
        override fun getElapsed(value: HourglassElapsed) = value
        override fun getNanoSeconds(value: HourglassElapsed) = HourglassNanoSeconds(0)
        override fun fromElapsedP(value: HourglassElapsedP) = value.elapsed
        override fun fromElapsed(value: HourglassElapsed) = value
    }
    val elapsedP: HourglassTime<HourglassElapsedP> = object : Codec<HourglassElapsedP>() {
        override fun getElapsedP(value: HourglassElapsedP) = value
        override fun getNanoSeconds(value: HourglassElapsedP) = value.nanoSeconds
        override fun fromElapsedP(value: HourglassElapsedP) = value
    }
    val date: HourglassTime<HourglassDate> = object : Codec<HourglassDate>() {
        override fun getElapsedP(value: HourglassDate) = HourglassElapsedP(HourglassCalendar.dateToUnixEpoch(value), HourglassNanoSeconds(0))
        override fun fromElapsedP(value: HourglassElapsedP) = native().dateTimeFromUnixEpoch(value.elapsed).date
        override fun getDateTime(value: HourglassDate) = HourglassDateTime(value, ZERO_TIME)
        override fun getDate(value: HourglassDate) = value
        override fun getTimeOfDay(value: HourglassDate) = ZERO_TIME
    }
    val dateTime: HourglassTime<HourglassDateTime> = object : Codec<HourglassDateTime>() {
        override fun getElapsedP(value: HourglassDateTime) = HourglassElapsedP(getElapsed(value), getNanoSeconds(value))
        override fun getElapsed(value: HourglassDateTime) = HourglassCalendar.dateTimeToUnixEpoch(value)
        override fun getNanoSeconds(value: HourglassDateTime) = value.time.nanoSecond
        override fun fromElapsedP(value: HourglassElapsedP) = native().dateTimeFromUnixEpochP(value)
        override fun getDateTime(value: HourglassDateTime) = value
        override fun getDate(value: HourglassDateTime) = value.date
        override fun getTimeOfDay(value: HourglassDateTime) = value.time
    }
    fun <E : HourglassEpoch> since(epoch: E): HourglassTime<HourglassElapsedSince<E>> = object : Codec<HourglassElapsedSince<E>>() {
        override fun getElapsedP(value: HourglassElapsedSince<E>) = HourglassElapsedP(getElapsed(value), HourglassNanoSeconds(0))
        override fun getElapsed(value: HourglassElapsedSince<E>) = HourglassElapsed(HourglassSeconds(value.seconds.value + epoch.diffToUnix.value))
        override fun getNanoSeconds(value: HourglassElapsedSince<E>) = HourglassNanoSeconds(0)
        override fun fromElapsedP(value: HourglassElapsedP) = HourglassElapsedSince(epoch, HourglassSeconds(value.elapsed.seconds.value - epoch.diffToUnix.value))
    }
    fun <E : HourglassEpoch> sinceP(epoch: E): HourglassTime<HourglassElapsedSinceP<E>> = object : Codec<HourglassElapsedSinceP<E>>() {
        override fun getElapsedP(value: HourglassElapsedSinceP<E>) = HourglassElapsedP(HourglassElapsed(HourglassSeconds(value.elapsed.seconds.value + epoch.diffToUnix.value)), value.nanoSeconds)
        override fun getNanoSeconds(value: HourglassElapsedSinceP<E>) = value.nanoSeconds
        override fun fromElapsedP(value: HourglassElapsedP) = HourglassElapsedSinceP(HourglassElapsedSince(epoch, HourglassSeconds(value.elapsed.seconds.value - epoch.diffToUnix.value)), value.nanoSeconds)
    }
    companion object { val ZERO_TIME = HourglassTimeOfDay(HourglassHours(0), HourglassMinutes(0), HourglassSeconds(0), HourglassNanoSeconds(0)) }
}
object HourglassTimeOperations {
    fun <A> genericDateTime(value: A, time: HourglassTimeable<A>, native: HourglassNativeBackend = HourglassNativeTime.system) = native.dateTimeFromUnixEpochP(time.getElapsedP(value))
    @Suppress("UNUSED_PARAMETER")
    fun <A> genericTimeOfDay(value: A, time: HourglassTimeable<A>): HourglassTimeOfDay = HourglassTimeCodecs.ZERO_TIME
    fun <A, B> convertGeneric(value: A, from: HourglassTimeable<A>, to: HourglassTime<B>) = to.fromElapsedP(from.getElapsedP(value))
    @Suppress("UNCHECKED_CAST")
    fun <A, B> convert(value: A, from: HourglassTimeable<A>, to: HourglassTime<B>): B =
        if (from === to) value as B else to.fromElapsedP(from.getElapsedP(value))
    fun <A, I> add(value: A, interval: I, time: HourglassTime<A>, units: HourglassTimeInterval<I>) = time.fromElapsedP(HourglassCalendar.elapsedTimeAddSecondsP(time.getElapsedP(value), units.toSeconds(interval)))
    fun <A, B> diff(first: A, second: B, firstTime: HourglassTimeable<A>, secondTime: HourglassTimeable<B>) = (firstTime.getElapsed(first) - secondTime.getElapsed(second)).seconds
    fun <A, B> diffP(first: A, second: B, firstTime: HourglassTimeable<A>, secondTime: HourglassTimeable<B>): Pair<HourglassSeconds, HourglassNanoSeconds> {
        val result = firstTime.getElapsedP(first) - secondTime.getElapsedP(second)
        return result.elapsed.seconds to result.nanoSeconds
    }
}
class HourglassLocalTime<A>(val value: A, val timezone: HourglassTimezoneOffset) {
    override fun equals(other: Any?) = other is HourglassLocalTime<*> && timezone == other.timezone && value == other.value
    override fun hashCode() = 31 * timezone.hashCode() + (value?.hashCode() ?: 0)
    override fun toString() = "$value$timezone"
    fun <B> map(transform: (A) -> B) = HourglassLocalTime(transform(value), timezone)
    fun toGlobal(time: HourglassTime<A>): A = if (timezone.minutes == 0L) value else time.fromElapsedP(HourglassCalendar.elapsedTimeAddSecondsP(time.getElapsedP(value), HourglassSeconds(-timezone.toSeconds().value)))
    fun setTimezone(next: HourglassTimezoneOffset, time: HourglassTime<A>): HourglassLocalTime<A> {
        val diff = next.toSeconds().value - timezone.toSeconds().value
        return if (diff == 0L) this else HourglassLocalTime(time.fromElapsedP(HourglassCalendar.elapsedTimeAddSecondsP(time.getElapsedP(value), HourglassSeconds(diff))), next)
    }
    fun <B> convert(from: HourglassTimeable<A>, to: HourglassTime<B>) = HourglassLocalTime(HourglassTimeOperations.convert(value, from, to), timezone)
    companion object {
        fun <A> fromGlobal(value: A) = HourglassLocalTime(value, HourglassTimezoneOffset.UTC)
        fun <A : Comparable<A>> compare(first: HourglassLocalTime<A>, second: HourglassLocalTime<A>, time: HourglassTime<A>): Int =
            if (first.timezone == second.timezone) first.value.compareTo(second.value) else first.toGlobal(time).compareTo(second.toGlobal(time))
    }
}
