// Codec.Picture.Metadata.Exif and Tiff.Internal.Types: Copyright (c) 2011 Vincent Berthoux.
// Translated from JuicyPixels 3.3.9, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.images

import io.heapy.kotdoc.formats.HaskellString

internal sealed interface ExifTag {
    val code: Int
    enum class Known(override val code: Int) : ExifTag {
        TagPhotometricInterpretation(262),
        TagCompression(259),
        TagImageWidth(256),
        TagImageLength(257),
        TagXResolution(282),
        TagYResolution(283),
        TagResolutionUnit(296),
        TagRowPerStrip(278),
        TagStripByteCounts(279),
        TagStripOffsets(273),
        TagBitsPerSample(258),
        TagColorMap(320),
        TagTileWidth(322),
        TagTileLength(323),
        TagTileOffset(324),
        TagTileByteCount(325),
        TagSamplesPerPixel(277),
        TagArtist(315),
        TagDocumentName(269),
        TagSoftware(305),
        TagPlanarConfiguration(284),
        TagOrientation(274),
        TagSampleFormat(339),
        TagInkSet(332),
        TagSubfileType(255),
        TagFillOrder(266),
        TagYCbCrCoeff(529),
        TagYCbCrSubsampling(530),
        TagYCbCrPositioning(531),
        TagReferenceBlackWhite(532),
        TagXPosition(286),
        TagYPosition(287),
        TagExtraSample(338),
        TagImageDescription(270),
        TagPredictor(317),
        TagCopyright(33432),
        TagMake(271),
        TagModel(272),
        TagDateTime(306),
        TagGPSInfo(34853),
        TagLightSource(37384),
        TagFlash(37385),
        TagJpegProc(512),
        TagJPEGInterchangeFormat(513),
        TagJPEGInterchangeFormatLength(514),
        TagJPEGRestartInterval(515),
        TagJPEGLosslessPredictors(517),
        TagJPEGPointTransforms(518),
        TagJPEGQTables(519),
        TagJPEGDCTables(520),
        TagJPEGACTables(521),
        TagExifOffset(34665),
    }
    data class Unknown(override val code: Int) : ExifTag {
        init { require(code in 0..65535) }
        override fun toString() = "TagUnknown $code"
    }
    val isInIFD0: Boolean get() = code <= Known.TagCopyright.code || this == Known.TagExifOffset || this == Known.TagGPSInfo
    companion object {
        fun fromWord16(code: Int): ExifTag = Known.entries.firstOrNull { it.code == code } ?: Unknown(code)
        // Derived Haskell Ord uses constructor order; TIFF writing separately sorts numeric codes.
        fun compare(left: ExifTag, right: ExifTag): Int = when {
            left is Known && right is Known -> left.ordinal.compareTo(right.ordinal)
            left is Known -> -1
            right is Known -> 1
            else -> left.code.compareTo(right.code)
        }
    }
}

private fun <T> List<T>.shown() = joinToString(",", "[", "]")
private fun List<Int>.quotedBytes() = HaskellString.quote(map(Int::toByte).toByteArray().toString(Charsets.ISO_8859_1))
private fun Int.argument() = if (this < 0) "($this)" else toString()

internal sealed interface ExifData {
    data object None : ExifData { override fun toString() = "ExifNone" }
    data class LongValue(val value: Long) : ExifData { override fun toString() = "ExifLong $value" }
    data class ShortValue(val value: Int) : ExifData { override fun toString() = "ExifShort $value" }
    data class StringValue(val bytes: List<Int>) : ExifData { override fun toString() = "ExifString ${bytes.quotedBytes()}" }
    data class Undefined(val bytes: List<Int>) : ExifData { override fun toString() = "ExifUndefined ${bytes.quotedBytes()}" }
    data class Shorts(val values: List<Int>) : ExifData { override fun toString() = "ExifShorts ${values.shown()}" }
    data class Longs(val values: List<Long>) : ExifData { override fun toString() = "ExifLongs ${values.shown()}" }
    data class Rational(val numerator: Long, val denominator: Long) : ExifData { override fun toString() = "ExifRational $numerator $denominator" }
    data class SignedRational(val numerator: Int, val denominator: Int) : ExifData { override fun toString() = "ExifSignedRational ${numerator.argument()} ${denominator.argument()}" }
    data class Ifd(val values: List<Pair<ExifTag, ExifData>>) : ExifData {
        override fun equals(other: Any?): Boolean {
            if (other !is Ifd) return false
            val pending = ArrayDeque<Pair<ExifData, ExifData>>()
            pending.addLast(this to other)
            while (pending.isNotEmpty()) {
                val (left, right) = pending.removeLast()
                if (left === right) continue
                if (left is Ifd && right is Ifd) {
                    if (left.values.size != right.values.size) return false
                    for (index in left.values.indices) {
                        val a = left.values[index]; val b = right.values[index]
                        if (a.first != b.first) return false
                        pending.addLast(a.second to b.second)
                    }
                } else if (left is Ifd || right is Ifd || left != right) return false
            }
            return true
        }
        override fun hashCode(): Int {
            data class Frame(val node: Ifd, var index: Int = 0, var hash: Int = 1)
            val pending = ArrayDeque<Frame>()
            pending.addLast(Frame(this))
            while (true) {
                val frame = pending.last()
                if (frame.index == frame.node.values.size) {
                    pending.removeLast()
                    if (pending.isEmpty()) return frame.hash
                    val parent = pending.last()
                    parent.hash = 31 * parent.hash + 31 * parent.node.values[parent.index++].first.hashCode() + frame.hash
                } else {
                    val (tag, value) = frame.node.values[frame.index]
                    if (value is Ifd) pending.addLast(Frame(value))
                    else { frame.hash = 31 * frame.hash + 31 * tag.hashCode() + value.hashCode(); frame.index++ }
                }
            }
        }
        override fun toString(): String = buildString {
            val pending = ArrayDeque<Any>()
            pending.addLast(this@Ifd)
            while (pending.isNotEmpty()) when (val next = pending.removeLast()) {
                is Ifd -> {
                    append("ExifIFD [")
                    pending.addLast("]")
                    for (index in next.values.indices.reversed()) {
                        val (tag, value) = next.values[index]
                        if (index < next.values.lastIndex) pending.addLast(",")
                        pending.addLast(")"); pending.addLast(value); pending.addLast("($tag,")
                    }
                }
                else -> append(next.toString())
            }
        }
    }
}
internal enum class TiffEndian { EndianLittle, EndianBig }
internal data class TiffHeader(val endian: TiffEndian, val offset: Long) {
    override fun toString() = "TiffHeader {hdrEndianness = $endian, hdrOffset = $offset}"
}
internal enum class IfdType {
    TypeByte, TypeAscii, TypeShort, TypeLong, TypeRational, TypeSByte, TypeUndefined,
    TypeSignedShort, TypeSignedLong, TypeSignedRational, TypeFloat, TypeDouble;
    val code: Int get() = ordinal + 1
}
internal data class ImageFileDirectory(val identifier: ExifTag, val type: IfdType, val count: Long, val offset: Long, val extended: ExifData = ExifData.None) {
    override fun toString() = "ImageFileDirectory {ifdIdentifier = $identifier, ifdType = $type, ifdCount = $count, ifdOffset = $offset, ifdExtended = $extended}"
}
internal data class TiffDocument(val header: TiffHeader, val directories: List<List<ImageFileDirectory>>) {
    override fun toString() = "($header,${directories.map { it.shown() }.shown()})"
}
internal enum class TiffPlanarConfiguration(val code: Int) { PlanarConfigContig(1), PlanarConfigSeparate(2) }
internal enum class TiffCompression(val code: Int) { CompressionNone(1), CompressionModifiedRLE(2), CompressionLZW(5), CompressionJPEG(6), CompressionPackBit(32773) }
internal enum class TiffSampleFormat(val code: Int) { TiffSampleUint(1), TiffSampleInt(2), TiffSampleFloat(3), TiffSampleUnknown(4) }
internal enum class TiffColorspace(val code: Int) { TiffMonochromeWhite0(0), TiffMonochrome(1), TiffRGB(2), TiffPaleted(3), TiffTransparencyMask(4), TiffCMYK(5), TiffYCbCr(6), TiffCIELab(8) }
internal enum class TiffExtraSample(val code: Int) { ExtraSampleUnspecified(0), ExtraSampleAssociatedAlpha(1), ExtraSampleUnassociatedAlpha(2) }
internal enum class TiffPredictor { PredictorNone, PredictorHorizontalDifferencing }
