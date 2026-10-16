// Data.ASN1.BinaryEncoding / Encoding, crypton-asn1-encoding 0.10.0.
// Vincent Hanquez 2010-2013; licenses/crypton-asn1-encoding-BSD-3-Clause.txt.
package io.heapy.kotdoc.asn1

class Asn1Exception(val error: Asn1Error): RuntimeException(error.show())
enum class Asn1EncodingRules { BER, DER }
class Asn1BinaryEncoding(private val prim: Asn1Primitives = Asn1Primitives()) {
    fun decode(bytes: ByteArray, rules: Asn1EncodingRules = Asn1EncodingRules.BER) = decodeChunks(sequenceOf(bytes),rules)
    fun decodeChunks(bytes: Sequence<ByteArray>, rules: Asn1EncodingRules = Asn1EncodingRules.BER): Asn1Result<Sequence<Asn1Value>> = when (val r = decodeRepresentationChunks(bytes,rules)) {
        is Asn1Result.Failure -> r
        is Asn1Result.Success -> Asn1Result.Success(r.value.map { it.value })
    }
    fun decodeRepresentation(bytes: ByteArray, rules: Asn1EncodingRules = Asn1EncodingRules.BER) = decodeRepresentationChunks(sequenceOf(bytes),rules)
    fun decodeRepresentationChunks(bytes: Sequence<ByteArray>, rules: Asn1EncodingRules = Asn1EncodingRules.BER): Asn1Result<Sequence<Asn1Representation>> = when (val parsed = Asn1Raw.parseChunks(bytes)) {
        is Asn1Result.Failure -> parsed
        is Asn1Result.Success -> Asn1Result.Success(decodeEvents(parsed.value,rules))
    }
    fun decodeEvents(events: List<Asn1Event>, rules: Asn1EncodingRules = Asn1EncodingRules.BER): Sequence<Asn1Representation> {
        val input = events.toList()
        return sequence {
            val stack = ArrayDeque<Asn1ConstructionType>(); var at = 0
            while (at < input.size) {
                val event = input[at]
                when {
                    event is Asn1Event.Header && event.header.constructed && input.getOrNull(at+1) == Asn1Event.ConstructionBegin -> {
                        if (rules == Asn1EncodingRules.DER) checkDer(event.header)?.let { throw Asn1Exception(it) }
                        val header = event.header
                        val kind = when {
                            header.kind == Asn1Class.Universal && header.tag == 16L -> Asn1ConstructionType.Sequence
                            header.kind == Asn1Class.Universal && header.tag == 17L -> Asn1ConstructionType.Set
                            else -> Asn1ConstructionType.Container(header.kind,header.tag)
                        }
                        stack.addLast(kind); at += 2
                        yield(Asn1Representation(Asn1Value.Start(kind),listOf(event,Asn1Event.ConstructionBegin)))
                    }
                    event is Asn1Event.Header && !event.header.constructed && input.getOrNull(at+1) is Asn1Event.Primitive -> {
                        if (rules == Asn1EncodingRules.DER) checkDer(event.header)?.let { throw Asn1Exception(it) }
                        val payload = input[at+1] as Asn1Event.Primitive
                        val value = when (val r = prim.decodePrimitive(event.header,payload.bytes)) { is Asn1Result.Failure -> throw Asn1Exception(r.error); is Asn1Result.Success -> r.value }
                        at += 2; yield(Asn1Representation(value,listOf(event,payload)))
                    }
                    event == Asn1Event.ConstructionEnd && stack.isNotEmpty() -> {
                        at++; yield(Asn1Representation(Asn1Value.End(stack.removeLast()),listOf(event)))
                    }
                    else -> throw Asn1Exception(Asn1Error.StreamUnexpectedSituation(event.toString()))
                }
            }
        }
    }
    fun encodeDerEvents(values: List<Asn1Value>) = Asn1Encoded.list(prim,values,true).events
    fun encodeDerChunks(values: List<Asn1Value>): Sequence<ByteArray> = sequence {
        for (event in encodeDerEvents(values)) when (event) {
            is Asn1Event.Header -> yield(Asn1Raw.putHeader(event.header))
            is Asn1Event.Primitive -> if (event.bytes.isNotEmpty()) yield(event.bytes)
            Asn1Event.ConstructionBegin, Asn1Event.ConstructionEnd -> Unit
        }
    }
    fun encodeDer(values: List<Asn1Value>) = java.io.ByteArrayOutputStream().also { out -> encodeDerChunks(values).forEach(out::write) }.toByteArray()
    private fun checkDer(header: Asn1Header): Asn1Error? = when (val len = header.length) {
        Asn1Length.Indefinite -> Asn1Error.PolicyFailed("DER","indefinite length not allowed")
        is Asn1Length.Short -> null
        is Asn1Length.LongForm -> when {
            len.octets == 1L -> if (len.value < 128) Asn1Error.PolicyFailed("DER","long length should be a short length") else null
            else -> {
                fun power(n: Long): Long { if (n < 0) error("Negative exponent"); return if (n >= 64) 0 else 1L shl n.toInt() }
                if (len.value >= power((len.octets-1)*8) && len.value < power(len.octets*8)) null else Asn1Error.PolicyFailed("DER","long length is not shortest")
            }
        }
    }
}
