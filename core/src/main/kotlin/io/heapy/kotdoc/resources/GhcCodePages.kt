// GHC 9.12.2 IO.Encoding.CodePage and generated Table.
// University of Glasgow; complete licenses/GHC-base-BSD-3-Clause.txt.
package io.heapy.kotdoc.resources

import io.heapy.kotdoc.formats.HaskellNumber
import kotlinx.serialization.json.*
import java.util.zip.GZIPInputStream

/** The NOINLINE currentCodePage is shared by all three initial encodings. */
internal class GhcLocaleCodePage(private val api: GhcCodePageApi) {
    private val current by ghcEncodingLazy { GhcCodePages.getCurrentCodePage(api) }
    fun encoding(mode: GhcCodingFailureMode) = GhcCodePages.encoding(current, mode, api)
}

internal object GhcCodePages {
    val locale = GhcLocaleCodePage(GhcNativeCodePageApi)
    fun getCurrentCodePage(api: GhcCodePageApi): UInt = api.getConsoleCP().let { if (it > 0u) it else api.getACP() }
    fun parseNumber(normalized: String): UInt? {
        if (!normalized.startsWith("CP")) return null
        val number = normalized.substring(2)
        // reads must leave the empty remainder, unlike read/safeRead.
        if (number.isEmpty() || HaskellNumber.isSpace(number.last())) return null
        return HaskellNumber.readInteger(number)?.toInt()?.toUInt()
    }
    fun encoding(cp: UInt, mode: GhcCodingFailureMode, api: GhcCodePageApi = GhcNativeCodePageApi): GhcTextEncoding {
        val builtin = when (cp) { 65001u -> "UTF-8"; 1200u -> "UTF-16LE"; 1201u -> "UTF-16BE"; 12000u -> "UTF-32LE"; 12001u -> "UTF-32BE"; else -> null }
        if (builtin != null) return GhcTextEncoding.mkTextEncodingWithMode(builtin, mode)
        val table = tables[cp]
        val codec = if (table != null) GhcBufferCStringCodec(mode, { table::decode }, { table::encode }) else
            GhcBufferCStringCodec(mode, { GhcCodePageApiBuffers.decoder(cp, api) }, { GhcCodePageApiBuffers.encoder(cp, api) })
        return GhcTextEncoding.fromCodec("CP$cp", mode, codec)
    }
    internal val tables: Map<UInt, GhcSingleByteCodePage> by lazy {
        GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/io/heapy/kotdoc/resources/codepages.json.gz"))).bufferedReader().use { reader ->
            val data = Json.parseToJsonElement(reader.readText()).jsonObject
            val bits = data.getValue("blockBitSize").jsonPrimitive.int
            data.getValue("tables").jsonObject.map { (key, value) ->
                val fields = value.jsonObject
                fun bytes(name: String) = fields.getValue(name).jsonArray.map { it.jsonPrimitive.int }.toIntArray()
                key.toUInt() to GhcSingleByteCodePage(bits, bytes("decoderArray"), bytes("encoderIndices"), bytes("encoderValues"), fields.getValue("encoderMax").jsonPrimitive.int)
            }.toMap()
        }
    }
}

internal class GhcSingleByteCodePage(private val bits: Int, private val decoder: IntArray,
    private val indices: IntArray, private val values: IntArray, internal val maximum: Int) {
    // indexInt16OffAddr# sign-extends both table indices and decoded Char slots.
    private fun signed16(array: IntArray, index: Int) = (array[index * 2] or (array[index * 2 + 1] shl 8)).toShort().toInt()
    internal fun decodePoint(byte: Int): Int = signed16(decoder, byte)
    internal fun encodePoint(point: Int): Int? = if (point.toUInt() > maximum.toUInt()) null else values[signed16(indices, point ushr bits) + (point and ((1 shl bits) - 1))]
    private fun convert(encoding: Boolean, input: GhcCodeBuffer, output: GhcCodeBuffer): GhcCodingStep {
        var i = input.left; var o = output.right
        fun done(why: GhcCodingProgress) = GhcCodingStep(why, input.remove(i - input.left), output.copy(right = o))
        while (true) {
            if (o >= output.size) return done(GhcCodingProgress.OutputUnderflow)
            if (i >= input.right) return done(GhcCodingProgress.InputUnderflow)
            val point = input.read(i)
            val converted = if (encoding) {
                encodePoint(point) ?: return done(GhcCodingProgress.InvalidSequence)
            } else decodePoint(point)
            if (converted == 0 && point != 0) return done(GhcCodingProgress.InvalidSequence)
            output.put(o++, converted); i++
        }
    }
    fun decode(input: GhcCodeBuffer, output: GhcCodeBuffer) = convert(false, input, output)
    fun encode(input: GhcCodeBuffer, output: GhcCodeBuffer) = convert(true, input, output)
}
