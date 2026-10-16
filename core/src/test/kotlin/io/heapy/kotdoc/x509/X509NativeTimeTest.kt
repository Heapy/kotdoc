package io.heapy.kotdoc.x509

import io.heapy.kotdoc.asn1.*
import io.heapy.kotdoc.errors.GhcIOException
import io.heapy.kotdoc.time.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class X509NativeTimeTest {
    @Test
    fun nativeFormattingPreservesNormalizationAndPlatformRangeErrors() {
        fun time(y: Long, m: HourglassMonth, d: Long, h: Long = 0, mi: Long = 0, s: Long = 0) =
            Asn1Value.Time(Asn1TimeType.TimeGeneralized,
                HourglassDateTime(HourglassDate(y, m, d), HourglassTimeOfDay(HourglassHours(h), HourglassMinutes(mi), HourglassSeconds(s), HourglassNanoSeconds(0))),
                HourglassTimezoneOffset.UTC)
        val invalid = time(2050, HourglassMonth.February, 29, 25, -1, 61)
        val normalized = byteArrayOf(24, 15) + "20500302010001Z".toByteArray(Charsets.US_ASCII)
        assertArrayEquals(normalized, Asn1BinaryEncoding().encodeDer(listOf(invalid)))
        assertArrayEquals(normalized, X509PosixTimeReference.binary().encodeDer(listOf(invalid)))
        val early = time(1, HourglassMonth.January, 1)
        val reference = X509PosixTimeReference.binary().encodeDer(listOf(early))
        if (System.getProperty("os.name").startsWith("Windows")) {
            val failure = assertThrows(GhcIOException::class.java) { Asn1BinaryEncoding().encodeDer(listOf(early)) }
            assertTrue(failure.message.orEmpty().contains("FileTimeToSystemTime"))
        } else {
            assertArrayEquals(reference, Asn1BinaryEncoding().encodeDer(listOf(early)))
        }
    }
}
