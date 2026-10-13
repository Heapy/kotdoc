// Codec.Picture.Jpg.Internal.Metadata and Tiff.Internal.Metadata:
// Copyright (c) 2011 Vincent Berthoux, JuicyPixels 3.3.9, BSD-3-Clause.
package io.heapy.kotdoc.images

internal object ImageExifMetadata {
    val exifOffsetIfd = ImageFileDirectory(ExifTag.Known.TagExifOffset, IfdType.TypeLong, 1, 0)
    fun extractJfif(jfif: JpgJFIFApp0): ImageMetadatas {
        if (jfif.unit == JFifUnit.JFifUnitUnknown) return ImageMetadatas.empty
        fun dpi(value: Int) = if (jfif.unit == JFifUnit.JFifPixelsPerInch) value.toULong() else ImageMetadatas.dotsPerCentimeterToInch(value.toULong())
        return ImageMetadatas.empty.insert(ImageMetadataKey.DpiY, dpi(jfif.dpiY)).insert(ImageMetadataKey.DpiX, dpi(jfif.dpiX))
    }
    fun encodeJfif(metadata: ImageMetadatas): List<JpgFrame> {
        val x = metadata.lookup(ImageMetadataKey.DpiX) ?: return emptyList()
        val y = metadata.lookup(ImageMetadataKey.DpiY) ?: return emptyList()
        return listOf(JpgFrame.Jfif(JpgJFIFApp0(JFifUnit.JFifPixelsPerInch, (x and 65535u).toInt(), (y and 65535u).toInt(), null)))
    }
    private fun field(tag: ExifTag, data: ExifData): ImageFileDirectory {
        fun result(type: IfdType, count: Long = 1, offset: Long = 0, extended: ExifData = ExifData.None) = ImageFileDirectory(tag, type, count, offset, extended)
        return when (data) {
            is ExifData.ShortValue -> result(IfdType.TypeShort, offset = data.value.toLong() shl 16)
            is ExifData.LongValue -> result(IfdType.TypeLong, offset = data.value)
            is ExifData.Shorts -> if (data.values.size == 2) result(IfdType.TypeShort, 2, (data.values[0].toLong() shl 16) or data.values[1].toLong())
                else result(IfdType.TypeShort, data.values.size.toLong(), extended = data)
            is ExifData.Longs -> if (data.values.size == 1) result(IfdType.TypeLong, offset = data.values[0])
                else result(IfdType.TypeLong, data.values.size.toLong(), extended = data)
            is ExifData.StringValue -> result(IfdType.TypeAscii, data.bytes.size.toLong(), extended = data)
            is ExifData.Undefined -> if (data.bytes.size > 4) result(IfdType.TypeUndefined, data.bytes.size.toLong(), extended = data)
                else {
                    // The source shifts Word8 before widening, with 64-bit primitive shift counts.
                    var offset = 0L
                    data.bytes.forEachIndexed { i, value -> offset = offset or ((value.toLong() shl (4 - 8 * i)) and 255) }
                    result(IfdType.TypeUndefined, data.bytes.size.toLong(), offset.toUInt().toLong())
                }
            is ExifData.Rational -> result(IfdType.TypeRational, extended = data)
            is ExifData.SignedRational -> result(IfdType.TypeSignedRational, extended = data)
            ExifData.None -> error("Impossible - typeOfData : ExifNone")
            is ExifData.Ifd -> error("Impossible - typeOfData : ExifIFD")
        }
    }
    fun encodeTiff(metadata: ImageMetadatas): List<ImageFileDirectory> = buildList {
        for ((tag, key) in listOf(ExifTag.Known.TagCopyright to ImageMetadataKey.Copyright, ExifTag.Known.TagArtist to ImageMetadataKey.Author,
            ExifTag.Known.TagDocumentName to ImageMetadataKey.Title, ExifTag.Known.TagImageDescription to ImageMetadataKey.Description, ExifTag.Known.TagSoftware to ImageMetadataKey.Software)) {
            metadata.lookup(key)?.let { text -> add(field(tag, ExifData.StringValue(text.codePoints().toArray().map { it and 255 }))) }
        }
        metadata.extractExif().forEach { (tag, value) -> add(field(tag, value)) }
    }.sortedBy { it.identifier.code }

    fun extractTiff(fields: List<ImageFileDirectory>): ImageMetadatas {
        fun stringMetadata(f: ImageFileDirectory): ImageMetadatas {
            fun <T> one(key: ImageMetadataKey<T>, value: T) = ImageMetadatas.singleton(key, value)
            val text = (f.extended as? ExifData.StringValue)?.bytes?.map(Int::toByte)?.toByteArray()?.toString(Charsets.ISO_8859_1)
            val key = when (f.identifier) {
                ExifTag.Known.TagArtist -> ImageMetadataKey.Author
                ExifTag.Known.TagCopyright -> ImageMetadataKey.Copyright
                ExifTag.Known.TagDocumentName -> ImageMetadataKey.Title
                ExifTag.Known.TagImageDescription -> ImageMetadataKey.Description
                ExifTag.Known.TagSoftware -> ImageMetadataKey.Software
                else -> null
            }
            if (key != null && text != null) return one(key, text)
            return when (f.identifier) {
                ExifTag.Known.TagExifOffset -> (f.extended as? ExifData.Ifd)?.values?.fold(ImageMetadatas.empty) { acc, (tag, value) -> acc.insert(ImageMetadataKey.Exif(tag), value) } ?: ImageMetadatas.empty
                ExifTag.Known.TagImageWidth -> one(ImageMetadataKey.Width, f.offset.toULong())
                ExifTag.Known.TagImageLength -> one(ImageMetadataKey.Height, f.offset.toULong())
                ExifTag.Known.TagOrientation -> one(ImageMetadataKey.Exif(f.identifier), ExifData.ShortValue((f.offset and 65535).toInt()))
                ExifTag.Known.TagMake, ExifTag.Known.TagModel -> one(ImageMetadataKey.Exif(f.identifier), f.extended)
                is ExifTag.Unknown -> one(ImageMetadataKey.Exif(f.identifier), f.extended)
                else -> ImageMetadatas.empty
            }
        }
        // List foldMap is right-associated; union reverses the right operand's entry order.
        val strings = fields.foldRight(ImageMetadatas.empty) { f, rest -> stringMetadata(f) + rest }.insert(ImageMetadataKey.Format, ImageSourceFormat.SourceTiff)
        val unit = fields.firstOrNull { it.identifier == ExifTag.Known.TagResolutionUnit }?.takeIf { it.type == IfdType.TypeShort }?.offset
        var dpi = ImageMetadatas.empty
        if (unit == 2L || unit == 3L) {
            for ((tag, key) in listOf(ExifTag.Known.TagYResolution to ImageMetadataKey.DpiY, ExifTag.Known.TagXResolution to ImageMetadataKey.DpiX)) {
                val ratio = fields.firstOrNull { it.identifier == tag }?.extended as? ExifData.Rational ?: continue
                val value = (ratio.numerator / ratio.denominator).toULong()
                dpi = dpi.insert(key, if (unit == 3L) ImageMetadatas.dotsPerCentimeterToInch(value) else value)
            }
        }
        return dpi + strings
    }
}
