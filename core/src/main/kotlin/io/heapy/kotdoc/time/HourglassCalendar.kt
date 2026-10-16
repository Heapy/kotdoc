// Time.Calendar / Time.Diff, time-hourglass 0.3.0; Vincent Hanquez 2014.
// Complete notice: licenses/time-hourglass-BSD-3-Clause.txt.
package io.heapy.kotdoc.time

object HourglassCalendar {
    fun isLeapYear(year: Long) = year % 4 == 0L && (year % 100 != 0L || year % 400 == 0L)
    private val normal = longArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
    private val leap = longArrayOf(0, 31, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335)
    private val days = longArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    fun daysInMonth(year: Long, month: HourglassMonth) = if (month == HourglassMonth.February && isLeapYear(year)) 29L else days[month.ordinal]
    private fun daysUntilMonth(year: Long, month: HourglassMonth) = (if (isLeapYear(year)) leap else normal)[month.ordinal]
    private fun daysBeforeYear(year: Long): Long {
        val previous = year - 1
        return previous * 365 + Math.floorDiv(previous, 4L) - Math.floorDiv(previous, 100L) + Math.floorDiv(previous, 400L)
    }
    private fun daysOfDate(date: HourglassDate) = daysBeforeYear(date.year) + daysUntilMonth(date.year, date.month) + date.day
    fun getDayOfTheYear(date: HourglassDate) = daysUntilMonth(date.year, date.month) + date.day
    fun getWeekDay(date: HourglassDate) = HourglassWeekDay.entries[Math.floorMod(daysOfDate(date), 7L).toInt()]
    fun dateToUnixEpoch(date: HourglassDate) = HourglassElapsed(HourglassSeconds((daysOfDate(date) - 719_163) * 86_400))
    fun timeOfDayToSeconds(time: HourglassTimeOfDay) = HourglassSeconds(time.hour.value * 3600 + time.minute.value * 60 + time.second.value)
    fun dateTimeToUnixEpoch(value: HourglassDateTime) = dateToUnixEpoch(value.date) + HourglassElapsed(timeOfDayToSeconds(value.time))
    fun dateTimeFromUnixEpoch(value: HourglassElapsed, backend: HourglassNativeBackend = HourglassNativeTime.system) = backend.dateTimeFromUnixEpoch(value)
    fun dateTimeFromUnixEpochP(value: HourglassElapsedP, backend: HourglassNativeBackend = HourglassNativeTime.system) = backend.dateTimeFromUnixEpochP(value)
    fun dateFromUnixEpoch(value: HourglassElapsed, backend: HourglassNativeBackend = HourglassNativeTime.system) = backend.dateTimeFromUnixEpoch(value).date
    fun dateAddPeriod(date: HourglassDate, period: HourglassPeriod): HourglassDate {
        val totalMonths = date.month.ordinal.toLong() + period.months
        var year = date.year + period.years + Math.floorDiv(totalMonths, 12L)
        var month = Math.floorMod(totalMonths, 12L).toInt()
        var day = date.day + period.days
        while (true) {
            if (day <= 0) {
                if (month == 0) { month = 11; year-- } else month--
                day += daysInMonth(year, HourglassMonth.entries[month])
            } else {
                val monthDays = daysInMonth(year, HourglassMonth.entries[month])
                if (day <= monthDays) return HourglassDate(year, HourglassMonth.entries[month], day)
                day -= monthDays
                if (month == 11) { month = 0; year++ } else month++
            }
        }
    }
    fun elapsedTimeAddSeconds(value: HourglassElapsed, seconds: HourglassSeconds) = value + HourglassElapsed(seconds)
    fun elapsedTimeAddSecondsP(value: HourglassElapsedP, seconds: HourglassSeconds) = HourglassElapsedP(elapsedTimeAddSeconds(value.elapsed, seconds), value.nanoSeconds)
}
