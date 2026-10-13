// Codec.Picture.Metadata: Copyright (c) 2011 Vincent Berthoux.
// Translated from JuicyPixels 3.3.9, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.images

import io.heapy.kotdoc.formats.HaskellDouble
import io.heapy.kotdoc.formats.HaskellString

internal enum class ImageSourceFormat { SourceJpeg, SourceGif, SourceBitmap, SourceTiff, SourcePng, SourceHDR, SourceTGA }
internal sealed interface ImageColorSpace {
    data object SRGB : ImageColorSpace
    data class WindowsBitmapColorSpace(val bytes: List<Int>) : ImageColorSpace { override fun toString() = metadataShow(this) }
    data class ICCProfile(val bytes: List<Int>) : ImageColorSpace { override fun toString() = metadataShow(this) }
}
internal sealed interface ImageMetadataValue {
    data class IntValue(val value: Long) : ImageMetadataValue { override fun toString() = metadataShow(this) }
    data class DoubleValue(val value: Double) : ImageMetadataValue {
        override fun toString() = metadataShow(this)
        override fun equals(other: Any?) = other is DoubleValue && value == other.value
        override fun hashCode() = if (value == 0.0) 0 else value.hashCode()
    }
    data class StringValue(val value: String) : ImageMetadataValue { override fun toString() = metadataShow(this) }
}
internal sealed class ImageMetadataKey<T>(val label: String) {
    data object Gamma : ImageMetadataKey<Double>("Gamma")
    data object ColorSpace : ImageMetadataKey<ImageColorSpace>("ColorSpace")
    data object Format : ImageMetadataKey<ImageSourceFormat>("Format")
    data object DpiX : ImageMetadataKey<ULong>("DpiX")
    data object DpiY : ImageMetadataKey<ULong>("DpiY")
    data object Width : ImageMetadataKey<ULong>("Width")
    data object Height : ImageMetadataKey<ULong>("Height")
    data object Title : ImageMetadataKey<String>("Title")
    data object Description : ImageMetadataKey<String>("Description")
    data object Author : ImageMetadataKey<String>("Author")
    data object Copyright : ImageMetadataKey<String>("Copyright")
    data object Software : ImageMetadataKey<String>("Software")
    data object Comment : ImageMetadataKey<String>("Comment")
    data object Disclaimer : ImageMetadataKey<String>("Disclaimer")
    data object Source : ImageMetadataKey<String>("Source")
    data object Warning : ImageMetadataKey<String>("Warning")
    data class Exif(val tag: ExifTag) : ImageMetadataKey<ExifData>("Exif")
    data class Unknown(val name: String) : ImageMetadataKey<ImageMetadataValue>("Unknown")
    final override fun toString(): String = when (this) {
        is Exif -> "Exif " + if (tag is ExifTag.Unknown) "($tag)" else tag.toString()
        is Unknown -> "Unknown ${HaskellString.quote(name)}"
        else -> label
    }
}
internal class ImageMetadataEntry<T> private constructor(val key: ImageMetadataKey<T>, private val stored: Lazy<T>) {
    constructor(key: ImageMetadataKey<T>, value: T) : this(key, lazyOf(value))
    val value: T get() = stored.value
    override fun equals(other: Any?) = other is ImageMetadataEntry<*> && key == other.key && value == other.value
    override fun hashCode() = 31 * key.hashCode() + (value?.hashCode() ?: 0)
    override fun toString(): String {
        return "$key :=> ${metadataShow(value, 10)}"
    }
    companion object {
        fun <T> deferred(key: ImageMetadataKey<T>, value: () -> T) = ImageMetadataEntry(key, lazy(value))
    }
}
internal fun metadataShow(value: Any?, precedence: Int = 0): String {
    fun constructor(name: String, argument: String): String = "$name $argument".let { if (precedence > 10) "($it)" else it }
    fun number(s: String) = if (precedence > 6 && s.startsWith('-')) "($s)" else s
    fun bytes(xs: List<Int>) = HaskellString.quote(xs.map(Int::toByte).toByteArray().toString(Charsets.ISO_8859_1))
    return when (value) {
        is String -> HaskellString.quote(value)
        is Double -> number(HaskellDouble.show(value))
        is ImageMetadataValue.IntValue -> constructor("Int", metadataShow(value.value, 11))
        is ImageMetadataValue.DoubleValue -> constructor("Double", metadataShow(value.value, 11))
        is ImageMetadataValue.StringValue -> constructor("String", HaskellString.quote(value.value))
        is ImageColorSpace.WindowsBitmapColorSpace -> constructor("WindowsBitmapColorSpace", bytes(value.bytes))
        is ImageColorSpace.ICCProfile -> constructor("ICCProfile", bytes(value.bytes))
        ImageColorSpace.SRGB -> "SRGB"
        is ExifData -> value.toString().let { if (value != ExifData.None && precedence > 10) "($it)" else it }
        is Long -> number(value.toString())
        else -> value.toString()
    }
}

/** Ordered, heterogeneous storage with the original prepend/delete-first semantics. */
internal class ImageMetadatas private constructor(val entries: List<ImageMetadataEntry<*>>) {
    @Suppress("UNCHECKED_CAST")
    fun <T> lookup(key: ImageMetadataKey<T>): T? = entries.firstOrNull { it.key == key }?.value as T?
    fun delete(key: ImageMetadataKey<*>): ImageMetadatas {
        val at = entries.indexOfFirst { it.key == key }
        return if (at < 0) this else ImageMetadatas(entries.take(at) + entries.drop(at + 1))
    }
    fun <T> insert(key: ImageMetadataKey<T>, value: T) = ImageMetadatas(listOf(ImageMetadataEntry(key, value)) + delete(key).entries)
    fun <T> insertLazy(key: ImageMetadataKey<T>, value: () -> T) = ImageMetadatas(listOf(ImageMetadataEntry.deferred(key, value)) + delete(key).entries)
    operator fun plus(right: ImageMetadatas): ImageMetadatas = right.entries.fold(this) { acc, entry -> acc.prepend(entry) }
    private fun <T> prepend(entry: ImageMetadataEntry<T>) = ImageMetadatas(listOf(entry) + delete(entry.key).entries)
    fun <T> fold(initial: T, operation: (T, ImageMetadataEntry<*>) -> T): T = entries.fold(initial, operation)
    fun <T> foldMap(empty: T, append: (T, T) -> T, map: (ImageMetadataEntry<*>) -> T): T = fold(empty) { acc, entry -> append(acc, map(entry)) }
    fun extractExif(): List<Pair<ExifTag, ExifData>> = entries.mapNotNull { entry ->
        (entry.key as? ImageMetadataKey.Exif)?.let { it.tag to (entry.value as ExifData) }
    }
    override fun toString() = entries.joinToString(",", "Metadatas {getMetadatas = [", "]}")
    companion object {
        val empty = ImageMetadatas(emptyList())
        fun <T> singleton(key: ImageMetadataKey<T>, value: T) = ImageMetadatas(listOf(ImageMetadataEntry(key, value)))
        fun dpi(value: ULong) = ImageMetadatas(listOf(ImageMetadataEntry(ImageMetadataKey.DpiY, value), ImageMetadataEntry(ImageMetadataKey.DpiX, value)))
        fun size(width: Long, height: Long) = ImageMetadatas(listOf(ImageMetadataEntry(ImageMetadataKey.Width, width.toULong()), ImageMetadataEntry(ImageMetadataKey.Height, height.toULong())))
        fun basic(format: ImageSourceFormat, width: Long, height: Long) = ImageMetadatas(listOf(ImageMetadataEntry(ImageMetadataKey.Format, format)) + size(width, height).entries)
        fun simple(format: ImageSourceFormat, width: Long, height: Long, dpiX: Long, dpiY: Long) = ImageMetadatas(basic(format, width, height).entries + listOf(ImageMetadataEntry(ImageMetadataKey.DpiX, dpiX.toULong()), ImageMetadataEntry(ImageMetadataKey.DpiY, dpiY.toULong())))
        fun dotsPerMeterToInch(value: ULong) = value * 254uL / 10000uL
        fun dotsPerInchToMeter(value: ULong) = value * 10000uL / 254uL
        fun dotsPerCentimeterToInch(value: ULong) = value * 254uL / 100uL
    }
}
