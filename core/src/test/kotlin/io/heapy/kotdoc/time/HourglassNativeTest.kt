package io.heapy.kotdoc.time

import com.sun.jna.Platform
import io.heapy.kotdoc.errors.GhcIOException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

class HourglassNativeTest {
    @Test
    fun actualCurrentClockAndSystemWrappers() {
        val system = HourglassSystem()
        val before = Instant.now().epochSecond
        val seconds = system.timeCurrent().seconds.value
        val precise = system.timeCurrentP()
        val date = system.dateCurrent()
        val local = system.localDateCurrent()
        val atUTC = system.localDateCurrentAt(HourglassTimezoneOffset.UTC)
        val after = Instant.now().epochSecond
        for (value in listOf(seconds,precise.elapsed.seconds.value,HourglassCalendar.dateTimeToUnixEpoch(date).seconds.value,HourglassCalendar.dateTimeToUnixEpoch(atUTC.value).seconds.value)) {
            assertTrue(value in (before-1)..(after+1), "native current time outside observation bracket: $value")
        }
        assertTrue(precise.nanoSeconds.value in 0..999_999_999L)
        if (Platform.isWindows()) assertEquals(0L,precise.nanoSeconds.value % 100)
        assertTrue(local.value.date.month.ordinal in 0..11)
        // The value depends on host configuration; exact bias/epoch-zero behavior
        // and call order are checked against both original model executables.
        system.timezoneCurrent()
    }
    @Test
    fun actualNativeFailureMapping() {
        val backend = HourglassNativeTime.system
        if (Platform.isWindows()) {
            // This FILETIME has its high bit set, rejected by FileTimeToSystemTime.
            val error = assertThrows(GhcIOException::class.java) { backend.dateTimeFromUnixEpoch(HourglassElapsed(HourglassSeconds(910_692_730_086))) }
            assertTrue(error.message.contains("fileTimeToSystemTime: FileTimeToSystemTime"))
        } else {
            for (seconds in listOf(Long.MIN_VALUE,Long.MAX_VALUE)) {
                val error = assertThrows(IllegalStateException::class.java) { backend.dateTimeFromUnixEpoch(HourglassElapsed(HourglassSeconds(seconds))).date }
                assertEquals("gmTime failed",error.message)
            }
        }
    }
}
