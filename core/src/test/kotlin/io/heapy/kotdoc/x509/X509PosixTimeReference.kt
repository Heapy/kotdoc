package io.heapy.kotdoc.x509

import io.heapy.kotdoc.asn1.Asn1BinaryEncoding
import io.heapy.kotdoc.asn1.Asn1Primitives
import io.heapy.kotdoc.time.*
import kotlinx.serialization.json.*

/** Captured upstream POSIX environment, independent of the current host's calendar API. */
internal object X509PosixTimeReference : HourglassNativeBackend {
    private val model by lazy {
        val data = javaClass.getResourceAsStream("/x509-posix-time/data.json")!!.bufferedReader().use { it.readText() }
        Json.parseToJsonElement(data).jsonObject.getValue("model").jsonObject
    }
    fun binary() = Asn1BinaryEncoding(Asn1Primitives(HourglassFormat(this)))
    override fun dateTimeFromUnixEpoch(value: HourglassElapsed) = dateTimeFromUnixEpochP(HourglassElapsedP(value, HourglassNanoSeconds(0)))
    override fun dateTimeFromUnixEpochP(value: HourglassElapsedP): HourglassDateTime {
        fun fields(): List<Long> {
            val row = model.getValue(value.elapsed.seconds.value.toString())
            if (row is JsonObject) error(row.getValue("fatal").jsonPrimitive.content)
            return row.jsonArray.map { it.jsonPrimitive.long }
        }
        val row by lazy { fields() }
        return HourglassDateTime.deferred(
            { HourglassDate(row[0], HourglassMonth.entries[row[1].toInt()], row[2]) },
            { HourglassTimeOfDay(HourglassHours(row[3]), HourglassMinutes(row[4]), HourglassSeconds(row[5]), value.nanoSeconds) }
        )
    }
    override fun systemGetTimezone(): HourglassTimezoneOffset = error("unexpected time-zone query")
    override fun systemGetElapsed(): HourglassElapsed = error("unexpected clock query")
    override fun systemGetElapsedP(): HourglassElapsedP = error("unexpected clock query")
}
