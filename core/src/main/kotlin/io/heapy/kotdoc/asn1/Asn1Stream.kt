// Data.ASN1.Stream, crypton-asn1-types 0.4.1; Vincent Hanquez 2010-2013.
// Full notice: licenses/crypton-asn1-types-BSD-3-Clause.txt.
package io.heapy.kotdoc.asn1

class Asn1Representation(val value: Asn1Value, events: List<Asn1Event>) {
    private val storedEvents = events.toList()
    val events: List<Asn1Event> get() = storedEvents.toList()
    override fun equals(other: Any?) = other is Asn1Representation && value == other.value && storedEvents == other.storedEvents
    override fun hashCode() = 31 * value.hashCode() + storedEvents.hashCode()
    override fun toString() = "($value,${events.joinToString(",", "[", "]")})"
}

object Asn1Stream {
    fun getConstructedEnd(depth: Long, values: List<Asn1Value>): Pair<List<Asn1Value>, List<Asn1Value>> {
        var current = depth
        for ((at, value) in values.withIndex()) when (value) {
            is Asn1Value.Start -> current++
            is Asn1Value.End -> {
                if (current == 0L) return values.take(at) to values.drop(at + 1)
                current--
            }
            else -> Unit
        }
        return values.toList() to emptyList()
    }
    fun getConstructedEndRepr(values: List<Asn1Representation>): Pair<List<Asn1Representation>, List<Asn1Representation>> {
        if (values.isEmpty()) return emptyList<Asn1Representation>() to emptyList()
        if (values.first().value !is Asn1Value.Start) return values.take(1) to values.drop(1)
        var depth = 1L
        for (at in 1..<values.size) {
            when (values[at].value) { is Asn1Value.Start -> depth++; is Asn1Value.End -> depth--; else -> Unit }
            if (depth == 0L) return values.take(at + 1) to values.drop(at + 1)
        }
        return values.toList() to emptyList()
    }
}
