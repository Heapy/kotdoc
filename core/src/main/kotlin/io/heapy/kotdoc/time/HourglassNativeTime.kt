// Time.Internal and cbits/unix.c, time-hourglass 0.3.0; Vincent Hanquez 2014.
// Win32 Time.hsc wrapper behavior is retained under licenses/Win32-BSD-3-Clause.txt.
// Complete hourglass notice: licenses/time-hourglass-BSD-3-Clause.txt.
package io.heapy.kotdoc.time

import com.sun.jna.*
import com.sun.jna.win32.StdCallLibrary
import io.heapy.kotdoc.errors.GhcIOException
import io.heapy.kotdoc.errors.GhcWindowsErrors
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.resources.GhcNativeWindowsLookup

interface HourglassNativeBackend {
    fun dateTimeFromUnixEpoch(value: HourglassElapsed): HourglassDateTime
    fun dateTimeFromUnixEpochP(value: HourglassElapsedP): HourglassDateTime
    fun systemGetTimezone(): HourglassTimezoneOffset
    fun systemGetElapsed(): HourglassElapsed
    fun systemGetElapsedP(): HourglassElapsedP
}
private fun nativeMonth(tag: Int): HourglassMonth {
    if (tag !in 0..11) error("Enum.toEnum{Month}: tag ($tag) is outside of enumeration's range (0,11)")
    return HourglassMonth.entries[tag]
}
interface HourglassPosixTimeApi {
    fun gmTimeR(seconds: Pointer, output: Pointer): Boolean
    fun localTimeR(seconds: Pointer, output: Pointer): Boolean
    fun clockCalendar(output: Pointer)
}
class HourglassPosixTime(private val api: HourglassPosixTimeApi) : HourglassNativeBackend {
    private fun dateTime(value: HourglassElapsed, nanos: HourglassNanoSeconds): HourglassDateTime = Memory(60).use { tm -> Memory(8).use { seconds ->
        seconds.setLong(0, value.seconds.value)
        if (!api.gmTimeR(seconds, tm)) error("gmTime failed")
        // The original adds 1900 in CInt before widening to machine Int.
        HourglassDateTime(HourglassDate((tm.getInt(20) + 1900).toLong(), nativeMonth(tm.getInt(16)), tm.getInt(12).toLong()),
            HourglassTimeOfDay(HourglassHours(tm.getInt(8).toLong()), HourglassMinutes(tm.getInt(4).toLong()), HourglassSeconds(tm.getInt(0).toLong()), nanos))
    } }
    override fun dateTimeFromUnixEpoch(value: HourglassElapsed) = dateTime(value, HourglassNanoSeconds(0))
    override fun dateTimeFromUnixEpochP(value: HourglassElapsedP) = dateTime(value.elapsed, value.nanoSeconds)
    override fun systemGetTimezone(): HourglassTimezoneOffset = Memory(60).use { tm -> Memory(8).use { seconds ->
        seconds.setLong(0, 0)
        if (!api.localTimeR(seconds, tm)) error("localTime failed")
        HourglassTimezoneOffset(Math.floorDiv(tm.getLong(40), 60L))
    } }
    override fun systemGetElapsed(): HourglassElapsed = Memory(16).use { time -> api.clockCalendar(time); HourglassElapsed(HourglassSeconds(time.getLong(0))) }
    override fun systemGetElapsedP(): HourglassElapsedP = Memory(16).use { time ->
        api.clockCalendar(time); HourglassElapsedP(HourglassElapsed(HourglassSeconds(time.getLong(0))), HourglassNanoSeconds(time.getLong(8)))
    }
}
interface HourglassWindowsTimeApi {
    fun fileTimeToSystemTime(fileTime: Pointer, systemTime: Pointer): Boolean
    fun getSystemTimeAsFileTime(output: Pointer)
    fun getTimeZoneInformation(output: Pointer): UInt
    fun getLastError(): UInt
    fun failWith(location: String, code: UInt): Nothing
}
class HourglassWindowsTime(private val api: HourglassWindowsTimeApi) : HourglassNativeBackend {
    private fun dateTime(value: HourglassElapsed, nanos: HourglassNanoSeconds): HourglassDateTime = Memory(8).use { ft -> Memory(16).use { st ->
        ft.setLong(0, ((value.seconds.value + 11_644_473_600).toULong() * 10_000_000uL).toLong())
        if (!api.fileTimeToSystemTime(ft, st)) api.failWith("fileTimeToSystemTime: FileTimeToSystemTime", api.getLastError())
        fun word(offset: Long) = st.getShort(offset).toInt() and 65535
        HourglassDateTime(HourglassDate(word(0).toLong(), nativeMonth((word(2) - 1) and 65535), word(6).toLong()),
            HourglassTimeOfDay(HourglassHours(word(8).toLong()), HourglassMinutes(word(10).toLong()), HourglassSeconds(word(12).toLong()), nanos))
    } }
    override fun dateTimeFromUnixEpoch(value: HourglassElapsed) = dateTime(value, HourglassNanoSeconds(0))
    override fun dateTimeFromUnixEpochP(value: HourglassElapsedP) = dateTime(value.elapsed, value.nanoSeconds)
    override fun systemGetTimezone(): HourglassTimezoneOffset = Memory(172).use { info ->
        val kind = api.getTimeZoneInformation(info)
        if (kind == UInt.MAX_VALUE) api.failWith("getTimeZoneInformation: GetTimeZoneInformation", api.getLastError())
        val bias = info.getInt(0) + when (kind) { 2u -> info.getInt(168); 1u -> info.getInt(84); else -> 0 }
        HourglassTimezoneOffset((-bias).toLong())
    }
    private fun fileTime() = Memory(8).use { ft -> api.getSystemTimeAsFileTime(ft); ft.getLong(0).toULong() }
    override fun systemGetElapsed(): HourglassElapsed {
        val seconds = (fileTime() / 10_000_000uL).toLong() - 11_644_473_600
        return HourglassElapsed(HourglassSeconds(seconds))
    }
    override fun systemGetElapsedP(): HourglassElapsedP {
        val ft = fileTime()
        return HourglassElapsedP(HourglassElapsed(HourglassSeconds((ft / 10_000_000uL).toLong() - 11_644_473_600)), HourglassNanoSeconds((ft % 10_000_000uL * 100uL).toLong()))
    }
}

internal object HourglassNativePosixTimeApi : HourglassPosixTimeApi {
    private val libc by lazy { NativeLibrary.getInstance(Platform.C_LIBRARY_NAME) }
    override fun gmTimeR(seconds: Pointer, output: Pointer) = libc.getFunction("gmtime_r").invokePointer(arrayOf(seconds, output)) != null
    override fun localTimeR(seconds: Pointer, output: Pointer) = libc.getFunction("localtime_r").invokePointer(arrayOf(seconds, output)) != null
    override fun clockCalendar(output: Pointer) {
        if (Platform.isMac()) Memory(4).use { clock -> Memory(8).use { time ->
            val host = libc.getFunction("mach_host_self").invokeInt(emptyArray())
            libc.getFunction("host_get_clock_service").invokeInt(arrayOf(host, 1, clock))
            val port = clock.getInt(0)
            libc.getFunction("clock_get_time").invokeInt(arrayOf(port, time))
            val task = libc.getGlobalVariableAddress("mach_task_self_").getInt(0)
            libc.getFunction("mach_port_deallocate").invokeInt(arrayOf(task, port))
            output.setLong(0, time.getInt(0).toUInt().toLong()); output.setLong(8, time.getInt(4).toUInt().toLong())
        } } else libc.getFunction("clock_gettime").invokeInt(arrayOf(0, output))
    }
}
internal interface HourglassKernelTime : StdCallLibrary {
    fun FileTimeToSystemTime(fileTime: Pointer, systemTime: Pointer): Int
    fun GetSystemTimeAsFileTime(output: Pointer)
    fun GetTimeZoneInformation(output: Pointer): Int
}
internal object HourglassNativeWindowsTimeApi : HourglassWindowsTimeApi {
    private val kernel by lazy { Native.load("Kernel32", HourglassKernelTime::class.java) }
    private val error = ThreadLocal.withInitial { 0u }
    override fun fileTimeToSystemTime(fileTime: Pointer, systemTime: Pointer): Boolean {
        val result = kernel.FileTimeToSystemTime(fileTime, systemTime); error.set(Native.getLastError().toUInt()); return result != 0
    }
    override fun getSystemTimeAsFileTime(output: Pointer) = kernel.GetSystemTimeAsFileTime(output)
    override fun getTimeZoneInformation(output: Pointer): UInt {
        val result = kernel.GetTimeZoneInformation(output); error.set(Native.getLastError().toUInt()); return result.toUInt()
    }
    override fun getLastError() = error.get()
    override fun failWith(location: String, code: UInt): Nothing = throw GhcIOException(GhcWindowsErrors.toError(CharacterStringChars.fromString(location), code, GhcNativeWindowsLookup))
}
object HourglassNativeTime {
    val system: HourglassNativeBackend by lazy { if (Platform.isWindows()) HourglassWindowsTime(HourglassNativeWindowsTimeApi) else HourglassPosixTime(HourglassNativePosixTimeApi) }
}
