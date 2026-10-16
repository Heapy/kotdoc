// Time.Types, time-hourglass 0.3.0; Vincent Hanquez 2014.
// Full notice: licenses/time-hourglass-BSD-3-Clause.txt.
package io.heapy.kotdoc.time

@JvmInline value class HourglassNanoSeconds(val value: Long) : Comparable<HourglassNanoSeconds> {
    override fun compareTo(other: HourglassNanoSeconds) = value.compareTo(other.value)
    override fun toString() = "${value}ns"
}
@JvmInline value class HourglassSeconds(val value: Long) : Comparable<HourglassSeconds> {
    override fun compareTo(other: HourglassSeconds) = value.compareTo(other.value)
    override fun toString() = "${value}s"
}
@JvmInline value class HourglassMinutes(val value: Long) : Comparable<HourglassMinutes> {
    override fun compareTo(other: HourglassMinutes) = value.compareTo(other.value)
    override fun toString() = "${value}m"
}
@JvmInline value class HourglassHours(val value: Long) : Comparable<HourglassHours> {
    override fun compareTo(other: HourglassHours) = value.compareTo(other.value)
    override fun toString() = "${value}h"
}
enum class HourglassMonth { January, February, March, April, May, June, July, August, September, October, November, December }
data class HourglassDate(val year: Long, val month: HourglassMonth, val day: Long) : Comparable<HourglassDate> {
    override fun compareTo(other: HourglassDate) = compareValuesBy(this, other, HourglassDate::year, HourglassDate::month, HourglassDate::day)
    override fun toString() = "Date {dateYear = $year, dateMonth = $month, dateDay = $day}"
}
data class HourglassTimeOfDay(val hour: HourglassHours, val minute: HourglassMinutes, val second: HourglassSeconds, val nanoSecond: HourglassNanoSeconds) : Comparable<HourglassTimeOfDay> {
    override fun compareTo(other: HourglassTimeOfDay) = compareValuesBy(this, other, HourglassTimeOfDay::hour, HourglassTimeOfDay::minute, HourglassTimeOfDay::second, HourglassTimeOfDay::nanoSecond)
    override fun toString() = "TimeOfDay {todHour = $hour, todMin = $minute, todSec = $second, todNSec = $nanoSecond}"
}
class HourglassDateTime private constructor(private val delayedDate: Lazy<HourglassDate>, private val delayedTime: Lazy<HourglassTimeOfDay>) : Comparable<HourglassDateTime> {
    constructor(date: HourglassDate, time: HourglassTimeOfDay): this(lazyOf(date),lazyOf(time))
    val date get() = delayedDate.value
    val time get() = delayedTime.value
    operator fun component1() = date
    operator fun component2() = time
    fun copy(date: HourglassDate = this.date, time: HourglassTimeOfDay = this.time) = HourglassDateTime(date,time)
    override fun equals(other: Any?) = other is HourglassDateTime && date == other.date && time == other.time
    override fun hashCode() = 31*date.hashCode()+time.hashCode()
    override fun compareTo(other: HourglassDateTime) = compareValuesBy(this, other, HourglassDateTime::date, HourglassDateTime::time)
    override fun toString() = "DateTime {dtDate = $date, dtTime = $time}"
    companion object {
        fun deferred(date: () -> HourglassDate, time: () -> HourglassTimeOfDay) = HourglassDateTime(lazy(date),lazy(time))
    }
}
@JvmInline value class HourglassTimezoneOffset(val minutes: Long) : Comparable<HourglassTimezoneOffset> {
    fun toSeconds() = HourglassSeconds(minutes * 60)
    override fun compareTo(other: HourglassTimezoneOffset) = minutes.compareTo(other.minutes)
    override fun toString(): String {
        val absolute = if (minutes < 0) -minutes else minutes
        val hours = Math.floorDiv(absolute, 60L)
        val remaining = Math.floorMod(absolute, 60L)
        fun pad2(value: Long): String {
            if (value < 0) error("Prelude.undefined")
            return (value % 100).toString().padStart(2, '0')
        }
        return (if (minutes < 0) "-" else "+") + pad2(hours) + pad2(remaining)
    }
    companion object { val UTC = HourglassTimezoneOffset(0) }
}
