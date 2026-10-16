// Time.System, time-hourglass 0.3.0; Vincent Hanquez 2014.
// Complete notice: licenses/time-hourglass-BSD-3-Clause.txt.
package io.heapy.kotdoc.time

class HourglassSystem(private val backend: HourglassNativeBackend = HourglassNativeTime.system) {
    private val codecs = HourglassTimeCodecs { backend }
    fun timeCurrent() = backend.systemGetElapsed()
    fun timeCurrentP() = backend.systemGetElapsedP()
    fun dateCurrent() = backend.dateTimeFromUnixEpochP(timeCurrentP())
    fun timezoneCurrent() = backend.systemGetTimezone()
    fun localDateCurrent(): HourglassLocalTime<HourglassDateTime> {
        val timezone = timezoneCurrent()
        return localDateCurrentAt(timezone)
    }
    fun localDateCurrentAt(timezone: HourglassTimezoneOffset) = HourglassLocalTime.fromGlobal(dateCurrent()).setTimezone(timezone,codecs.dateTime)
}
