// Time.Timezone / Time.Utils, time-hourglass 0.3.0; Vincent Hanquez 2014.
// Complete notice: licenses/time-hourglass-BSD-3-Clause.txt.
package io.heapy.kotdoc.time

import java.math.BigInteger

interface HourglassTimezone {
    val offset: Long
    val name: String get() {
        val absolute = if (offset < 0) -offset else offset
        val hours = Math.floorDiv(absolute, 60L); val minutes = Math.floorMod(absolute, 60L)
        fun pad(value: Long) = if (value < 10) "0$value" else value.toString()
        return (if (offset > 0) "+" else "-") + pad(hours) + ":" + pad(minutes)
    }
}
data object HourglassUTC : HourglassTimezone {
    override val offset = 0L
    override val name = "UTC"
    override fun toString() = "UTC"
}
data class HourglassTimezoneMinutes(override val offset: Long) : HourglassTimezone, Comparable<HourglassTimezoneMinutes> {
    override fun compareTo(other: HourglassTimezoneMinutes) = offset.compareTo(other.offset)
    override fun toString() = "TimezoneMinutes ${if (offset < 0) "($offset)" else offset.toString()}"
}
object HourglassPadding {
    fun pad2(value: BigInteger): String {
        if (value.signum() < 0) error("Prelude.undefined")
        return (value % BigInteger.valueOf(100)).toString().padStart(2, '0')
    }
    fun pad3(value: BigInteger): String {
        if (value >= BigInteger.valueOf(1000)) error("Prelude.undefined")
        return if (value >= BigInteger.valueOf(100)) value.toString() else if (value >= BigInteger.TEN) "0$value" else "00$value"
    }
    fun pad4(value: BigInteger): String {
        if (value.signum() < 0) error("Prelude.undefined")
        return value.toString().padStart(4, '0')
    }
    fun padN(width: Long, value: BigInteger): String {
        if (value.signum() < 0) error("Prelude.undefined")
        val text = value.toString()
        if (text.length.toLong() >= width) return text
        require(width <= Int.MAX_VALUE) { "String exceeds JVM array capacity" }
        return text.padStart(width.toInt(), '0')
    }
}
