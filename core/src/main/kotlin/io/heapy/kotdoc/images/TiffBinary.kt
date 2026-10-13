// Codec.Picture.Tiff.Internal.Types: Copyright (c) 2011 Vincent Berthoux.
// Translated from JuicyPixels 3.3.9, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.images

internal object TiffBinary {
    private fun word32(value: Long) = value and 0xffff_ffffL
    fun readWord(input: JpegInput, endian: TiffEndian, size: Int): Long {
        val bytes = input.take(size)
        var value = 0L
        for (i in bytes.indices) {
            val index = if (endian == TiffEndian.EndianBig) i else bytes.lastIndex - i
            value = (value shl 8) or (bytes[index].toLong() and 255)
        }
        return value
    }
    fun putWord(out: JpegOutput, endian: TiffEndian, size: Int, value: Long) {
        repeat(size) { i -> out.u8((value ushr (8 * if (endian == TiffEndian.EndianBig) size - i - 1 else i)).toInt()) }
    }
    fun endian(input: JpegInput): TiffEndian = when (input.u16()) {
        0x4949 -> TiffEndian.EndianLittle
        0x4d4d -> TiffEndian.EndianBig
        else -> input.fail("Invalid endian tag value")
    }
    fun put(out: JpegOutput, endian: TiffEndian) { out.u16(if (endian == TiffEndian.EndianLittle) 0x4949 else 0x4d4d) }
    fun header(input: JpegInput): TiffHeader {
        val endian = endian(input)
        if (readWord(input, endian, 2) != 42L) input.fail("Invalid TIFF magic number")
        return TiffHeader(endian, readWord(input, endian, 4))
    }
    fun put(out: JpegOutput, header: TiffHeader) {
        put(out, header.endian); putWord(out, header.endian, 2, 42); putWord(out, header.endian, 4, header.offset)
    }
    fun type(input: JpegInput, endian: TiffEndian): IfdType =
        IfdType.entries.getOrNull(readWord(input, endian, 2).toInt() - 1) ?: input.fail("Invalid TIF directory type")
    fun field(input: JpegInput, endian: TiffEndian): ImageFileDirectory = ImageFileDirectory(
        ExifTag.fromWord16(readWord(input, endian, 2).toInt()), type(input, endian), readWord(input, endian, 4), readWord(input, endian, 4))
    fun put(out: JpegOutput, endian: TiffEndian, field: ImageFileDirectory) {
        putWord(out, endian, 2, field.identifier.code.toLong()); putWord(out, endian, 2, field.type.code.toLong())
        putWord(out, endian, 4, field.count); putWord(out, endian, 4, field.offset)
    }
    fun directory(input: JpegInput, endian: TiffEndian): List<ImageFileDirectory> {
        val count = readWord(input, endian, 2).toInt()
        val fields = List(count) { field(input, endian) }
        readWord(input, endian, 4) // Original consumes but does not follow the next-directory pointer.
        return fields
    }
    fun putDirectory(out: JpegOutput, endian: TiffEndian, fields: List<ImageFileDirectory>) {
        putWord(out, endian, 2, fields.size.toLong()); fields.forEach { put(out, endian, it) }; putWord(out, endian, 4, 0)
    }
    private fun clean(endian: TiffEndian, field: ImageFileDirectory) =
        if (endian == TiffEndian.EndianBig && field.count == 1L && field.type == IfdType.TypeShort)
            field.copy(offset = field.offset ushr 16) else field

    fun extended(input: JpegInput, endian: TiffEndian, maximum: Long, root: ImageFileDirectory): ExifData {
        data class Pending(val fields: List<ImageFileDirectory>, var index: Int = 1, val values: MutableList<Pair<ExifTag, ExifData>> = mutableListOf())
        val stack = ArrayDeque<Pending>()
        var field = root
        while (true) {
            var value: ExifData
            if (field.identifier == ExifTag.Known.TagExifOffset && field.type == IfdType.TypeLong && field.count == 1L) {
                if (field.offset >= word32(maximum) || input.position.toLong() > field.offset) value = ExifData.None
                else {
                    val delta = field.offset - input.position
                    if (delta > Int.MAX_VALUE) input.fail("not enough bytes")
                    input.skip(delta.toInt())
                    val fields = directory(input, endian).sortedBy { it.offset }.map { clean(endian, it) }.sortedBy { it.offset }
                    if (fields.isNotEmpty()) {
                        stack.addLast(Pending(fields))
                        field = fields[0]
                        continue
                    }
                    value = ExifData.Ifd(emptyList())
                }
            } else value = primitiveExtended(input, endian, maximum, field)
            while (true) {
                if (stack.isEmpty()) return value
                val parent = stack.last()
                parent.values.add(parent.fields[parent.index - 1].identifier to value)
                if (parent.index < parent.fields.size) {
                    field = parent.fields[parent.index++]
                    break
                }
                stack.removeLast()
                value = ExifData.Ifd(parent.values)
            }
        }
    }

    private fun primitiveExtended(input: JpegInput, endian: TiffEndian, maximum: Long, field: ImageFileDirectory): ExifData {
        fun aligned(read: () -> ExifData): ExifData {
            if (field.offset >= word32(maximum) || input.position.toLong() > field.offset) return ExifData.None
            val delta = field.offset - input.position
            if (delta > Int.MAX_VALUE) input.fail("not enough bytes")
            input.skip(delta.toInt())
            return read()
        }
        fun bytes(): List<Int> {
            if (field.count > Int.MAX_VALUE) input.fail("not enough bytes")
            return input.take(field.count.toInt()).map { it.toInt() and 255 }
        }
        fun numbers(size: Int): List<Long> = buildList {
            var remaining = field.count
            while (remaining-- > 0) add(readWord(input, endian, size))
        }
        fun immediateBytes(): List<Int> {
            val out = JpegOutput(); putWord(out, endian, 4, field.offset)
            return out.result().take(field.count.toInt()).map { it.toInt() and 255 }
        }
        return when {
            field.type == IfdType.TypeUndefined -> if (field.count > 4) aligned { ExifData.Undefined(bytes()) } else ExifData.Undefined(immediateBytes())
            field.type == IfdType.TypeAscii -> if (field.count > 4) aligned { ExifData.StringValue(bytes()) } else ExifData.StringValue(immediateBytes())
            field.type == IfdType.TypeShort && field.count == 2L -> {
                val halves = listOf((field.offset ushr 16).toInt(), (field.offset and 65535).toInt())
                ExifData.Shorts(if (endian == TiffEndian.EndianLittle) halves.reversed() else halves)
            }
            field.type == IfdType.TypeRational && field.count == 1L -> aligned {
                // The original explicitly uses little endian here, even for a big-endian TIFF.
                ExifData.Rational(readWord(input, TiffEndian.EndianLittle, 4), readWord(input, TiffEndian.EndianLittle, 4))
            }
            field.type == IfdType.TypeSignedRational && field.count == 1L -> aligned {
                ExifData.SignedRational(readWord(input, TiffEndian.EndianLittle, 4).toInt(), readWord(input, TiffEndian.EndianLittle, 4).toInt())
            }
            field.type == IfdType.TypeShort && field.count == 1L -> ExifData.ShortValue((field.offset and 65535).toInt())
            field.type == IfdType.TypeShort && field.count > 2L -> aligned { ExifData.Shorts(numbers(2).map(Long::toInt)) }
            field.type == IfdType.TypeLong && field.count == 1L -> ExifData.LongValue(field.offset)
            field.type == IfdType.TypeLong && field.count > 1L -> aligned { ExifData.Longs(numbers(4)) }
            else -> ExifData.None
        }
    }
    private fun fetchExtended(input: JpegInput, endian: TiffEndian, maximum: Long, fields: List<ImageFileDirectory>) =
        fields.map { it.copy(extended = extended(input, endian, maximum, it)) }

    fun putExtended(out: JpegOutput, endian: TiffEndian, value: ExifData) {
        fun padded(bytes: List<Int>) { bytes.forEach(out::u8); if (bytes.size % 2 != 0) out.u8(0) }
        when (value) {
            ExifData.None, is ExifData.LongValue, is ExifData.ShortValue, is ExifData.Ifd -> Unit
            is ExifData.StringValue -> padded(value.bytes)
            is ExifData.Undefined -> padded(value.bytes)
            is ExifData.Shorts -> value.values.forEach { putWord(out, endian, 2, it.toLong()) }
            is ExifData.Longs -> value.values.forEach { putWord(out, endian, 4, it) }
            is ExifData.Rational -> { putWord(out, endian, 4, value.numerator); putWord(out, endian, 4, value.denominator) }
            is ExifData.SignedRational -> { putWord(out, endian, 4, value.numerator.toLong()); putWord(out, endian, 4, value.denominator.toLong()) }
        }
    }
    fun document(input: JpegInput, raw: ByteArray = input.bytes): TiffDocument {
        val header = header(input)
        val delta = header.offset - input.position
        if (delta > Int.MAX_VALUE) input.fail("not enough bytes")
        input.skip(delta.toInt())
        val fields = directory(input, header.endian).sortedBy { it.offset }.map { clean(header.endian, it) }
        return TiffDocument(header, listOf(fetchExtended(input, header.endian, raw.size.toLong(), fields)))
    }
    fun setupOffsets(initialOffset: Long, fields: List<ImageFileDirectory>): Pair<Long, List<ImageFileDirectory>> {
        var current = word32(initialOffset + fields.size * 12L + 6)
        val result = fields.map { field ->
            val value = field.extended
            val size: Long? = when {
                field.identifier == ExifTag.Known.TagExifOffset -> 0L
                value is ExifData.StringValue -> value.bytes.size.toLong() + value.bytes.size % 2
                value is ExifData.Undefined -> value.bytes.size.toLong() + value.bytes.size % 2
                value is ExifData.Longs && value.values.size > 1 -> value.values.size * 4L
                value is ExifData.Shorts && value.values.size > 2 -> value.values.size * 2L
                else -> null
            }
            if (size == null) field else field.copy(offset = current).also { current = word32(current + size) }
        }
        return current to result
    }
    fun put(out: JpegOutput, raw: ByteArray, document: TiffDocument) {
        put(out, document.header); out.bytes(raw)
        var offset = document.header.offset
        val directories = document.directories.map { fields ->
            val (next, adjusted) = setupOffsets(offset, fields.sortedBy { it.identifier.code })
            offset = next
            adjusted
        }
        for (fields in directories) {
            putDirectory(out, document.header.endian, fields)
            fields.forEach { putExtended(out, document.header.endian, it.extended) }
        }
    }
    fun planar(input: JpegInput, value: Long): TiffPlanarConfiguration = when (value) {
        0L, 1L -> TiffPlanarConfiguration.PlanarConfigContig
        2L -> TiffPlanarConfiguration.PlanarConfigSeparate
        else -> input.fail("Unknown planar constant ($value)")
    }
    fun compression(input: JpegInput, value: Long): TiffCompression =
        (if (value == 0L) TiffCompression.CompressionNone else TiffCompression.entries.firstOrNull { it.code.toLong() == value }) ?: input.fail("Unknown compression scheme $value")
    fun sampleFormat(input: JpegInput, value: Long): TiffSampleFormat =
        TiffSampleFormat.entries.firstOrNull { it.code.toLong() == value } ?: input.fail("Undefined data format ($value)")
    fun colorspace(input: JpegInput, value: Long): TiffColorspace =
        TiffColorspace.entries.firstOrNull { it.code.toLong() == value } ?: input.fail("Unrecognized color space $value")
    fun predictor(input: JpegInput, value: Long): TiffPredictor = when (value) {
        1L -> TiffPredictor.PredictorNone
        2L -> TiffPredictor.PredictorHorizontalDifferencing
        else -> input.fail("Unknown predictor ($value)")
    }
}
